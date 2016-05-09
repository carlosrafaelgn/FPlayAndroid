//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013-2014, Carlos Rafael Gimenes das Neves
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// The views and conclusions contained in the software and documentation are those
// of the authors and should not be interpreted as representing official policies,
// either expressed or implied, of the FreeBSD Project.
//
// https://github.com/carlosrafaelgn/FPlayAndroid
//
package br.com.carlosrafaelgn.fplay.playback.context;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;

import br.com.carlosrafaelgn.fplay.playback.Player;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class MediaContext implements Runnable, Handler.Callback {
	private static final int MSG_COMPLETION = 0x0100;
	private static final int MSG_ERROR = 0x0101;
	private static final int MSG_SEEKCOMPLETE = 0x0102;
	private static final int MSG_BUFFERUNDERRUN = 0x0103;
	private static final int MSG_BUFFERSNORMALIZED = 0x0104;

	private static final int ACTION_NONE = 0x0000;
	private static final int ACTION_PLAY = 0x0001;
	private static final int ACTION_PAUSE = 0x0002;
	private static final int ACTION_RESUME = 0x0003;
	private static final int ACTION_SEEK = 0x0004;
	private static final int ACTION_SETNEXT = 0x0005;
	private static final int ACTION_RESET = 0x0006;
	private static final int ACTION_INITIALIZE = 0xFFFF;

	private static final int PLAYER_TIMEOUT = 600000;

	private static final class ErrorStructure {
		public MediaCodecPlayer player;
		public Throwable exception;

		public ErrorStructure(MediaCodecPlayer player, Throwable exception) {
			this.player = player;
			this.exception = exception;
		}
	}

	private static final Object threadNotification = new Object();
	private static final Object notification = new Object();
	private static final Object openSLSync = new Object();
	private static volatile boolean alive, waitToReceiveAction, requestSucceeded, initializationError;
	private static volatile int requestedAction, requestedSeekMS;
	private static float leftVolume = 1.0f, rightVolume = 1.0f;
	private static Handler handler;
	private static Thread thread;
	private static volatile MediaCodecPlayer playerRequestingAction, nextPlayerRequested;
	private static MediaContext theMediaContext;

	static {
		System.loadLibrary("MediaContextJni");
	}

	private static native void resetFiltersAndWritePosition(int srcChannelCount);

	static native void enableEqualizer(int enabled);
	static native int isEqualizerEnabled();
	static native void setEqualizerBandLevel(int band, int level);
	static native void setEqualizerBandLevels(short[] levels);

	static native void enableBassBoost(int enabled);
	static native int isBassBoostEnabled();
	static native void setBassBoostStrength(int strength);
	static native int getBassBoostRoundedStrength();

	static native void enableVirtualizer(int enabled);
	static native int isVirtualizerEnabled();
	static native void setVirtualizerStrength(int strength);
	static native int getVirtualizerRoundedStrength();

	static native long startVisualization();
	static native long getVisualizationPtr();
	static native void stopVisualization();

	static native int openSLInitialize();
	static native int openSLCreate(int sampleRate, int bufferSizeInFrames);
	static native int openSLPlay();
	static native void openSLPause();
	static native void openSLStopAndFlush();
	static native void openSLRelease();
	static native void openSLTerminate();
	static native int openSLGetHeadPositionInFrames();
	static native int openSLWriteDirect(ByteBuffer buffer, int offsetInBytes, int sizeInBytes, int needsSwap);
	static native int openSLWrite(byte[] buffer, int offsetInBytes, int sizeInBytes, int needsSwap);

	private MediaContext() {
	}

	@SuppressWarnings("deprecation")
	private static int create(int sampleRate) {
		int bufferSizeInBytes = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		int bufferSizeInFrames = bufferSizeInBytes >> 2;

		final int oneSecondInFrames = 48000;
		//make sure it is a multiple of bufferSizeInFrames
		bufferSizeInFrames = ((oneSecondInFrames + bufferSizeInFrames - 1) / bufferSizeInFrames) * bufferSizeInFrames;

		synchronized (openSLSync) {
			final int res = openSLCreate(sampleRate, bufferSizeInFrames);
			if (res != 0)
				throw new IllegalStateException("openSLCreate() returned " + res);
			MediaCodecPlayer.tryToProcessNonDirectBuffers = true;
		}

		return bufferSizeInFrames;
	}

	@Override
	public void run() {
		thread.setPriority(Thread.MAX_PRIORITY);

		int sampleRate = 0;
		PowerManager.WakeLock wakeLock;
		MediaCodecPlayer currentPlayer = null, nextPlayer = null, sourcePlayer = null;
		MediaCodecPlayer.OutputBuffer outputBuffer = new MediaCodecPlayer.OutputBuffer();
		outputBuffer.index = -1;
		int bufferSizeInFrames = 0, lastHeadPositionInFrames = 0;
		long framesWritten = 0, framesPlayed = 0, nextFramesWritten = 0;

		initializationError = false;

		int ret = openSLInitialize();
		if (ret != 0) {
			//System.out.println("openSLInitialize() returned " + ret);
			initializationError = true;
			requestedAction = ACTION_NONE;
			synchronized (notification) {
				notification.notify();
			}
			return;
		}

		MediaCodecPlayer.tryToProcessNonDirectBuffers = true;

		wakeLock = ((PowerManager)Player.theApplication.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MediaContext WakeLock");
		wakeLock.setReferenceCounted(false);

		requestedAction = ACTION_NONE;

		synchronized (notification) {
			notification.notify();
		}

		boolean paused = true, playPending = false;
		while (alive) {
			if (paused || waitToReceiveAction) {
				synchronized (threadNotification) {
					if (requestedAction == ACTION_NONE) {
						//System.out.println("A");
						try {
							threadNotification.wait();
						} catch (Throwable ex) {
							//just ignore
						}
						//System.out.println("B");
					}

					if (!alive)
						break;

					if (requestedAction != ACTION_NONE) {
						//System.out.println("C " + requestedAction + " | " + playerRequestingAction + " | " + currentPlayer);
						waitToReceiveAction = false;
						boolean seekPending = false;
						requestSucceeded = false;
						try {
							//**** before calling notification.notify() we can safely assume the player thread
							//is halted while these actions take place, therefore the player objects are not
							//being used during this period
							switch (requestedAction) {
							case ACTION_PLAY:
								//System.out.println("D");
								openSLStopAndFlush();
								//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
									try {
										//why?!?!?!
										//WWWHHHHYYYYY?!?!?!?
										//https://groups.google.com/forum/#!msg/android-developers/zdgJ0QdvCAQ/o-0q8Bb5qRsJ
										Thread.sleep(100);
									} catch (Throwable ex) {
										//just ignore
									}
								//}
								//System.out.println("E");
								outputBuffer.release();
								currentPlayer = playerRequestingAction;
								nextPlayer = null;
								sourcePlayer = currentPlayer;
								currentPlayer.resetDecoderIfOutputAlreadyUsed();
								framesWritten = currentPlayer.getCurrentPositionInFrames();
								framesPlayed = currentPlayer.getCurrentPositionInFrames();
								nextFramesWritten = 0;
								//System.out.println("H");
								synchronized (openSLSync) {
									if (sampleRate != currentPlayer.getSampleRate()) {
										//System.out.println("I");
										sampleRate = currentPlayer.getSampleRate();
										bufferSizeInFrames = create(sampleRate);
									}
								}
								//System.out.println("J");
								playPending = true;
								resetFiltersAndWritePosition(currentPlayer.getChannelCount());
								lastHeadPositionInFrames = openSLGetHeadPositionInFrames();
								paused = false;
								requestSucceeded = true;
								wakeLock.acquire();
								//System.out.println("K");
								break;
							case ACTION_PAUSE:
								//System.out.println("L");
								if (playerRequestingAction == currentPlayer) {
									//System.out.println("M");
									openSLPause();
									paused = true;
									requestSucceeded = true;
									wakeLock.release();
									//System.out.println("N");
								} else {
									//System.out.println("O");
									throw new IllegalStateException("impossible to pause a player other than currentPlayer");
								}
								break;
							case ACTION_RESUME:
								//System.out.println("P");
								if (playerRequestingAction == currentPlayer) {
									//System.out.println("Q");
									playPending = true;
									paused = false;
									requestSucceeded = true;
									wakeLock.acquire();
									//System.out.println("R");
								} else {
									//System.out.println("S");
									throw new IllegalStateException("impossible to resume a player other than currentPlayer");
								}
								break;
							case ACTION_SEEK:
								//System.out.println("T");
								if (currentPlayer != null && currentPlayer != playerRequestingAction)
									throw new IllegalStateException("impossible to seek a player other than currentPlayer");
								if (!paused)
									throw new IllegalStateException("trying to seek while not paused");
								openSLStopAndFlush();
								seekPending = true;
								requestSucceeded = true;
								//System.out.println("U");
								break;
							case ACTION_SETNEXT:
								//System.out.println("V");
								if (currentPlayer == playerRequestingAction && nextPlayer != nextPlayerRequested) {
									//System.out.println("W");
									//if we had already started outputting nextPlayer's audio then it is too
									//late... just remove the nextPlayer
									if (currentPlayer.isOutputOver()) {
										//System.out.println("X");
										//go back to currentPlayer
										if (sourcePlayer == nextPlayer) {
											outputBuffer.release();
											sourcePlayer = currentPlayer;
										}
										nextPlayer = null;
										nextFramesWritten = 0;
										//System.out.println("Y");
									} else {
										//System.out.println("Z");
										nextPlayer = nextPlayerRequested;
										try {
											//System.out.println("AA");
											if (nextPlayer != null) {
												if (currentPlayer.isInternetStream() ||
													nextPlayer.isInternetStream() ||
													sampleRate != nextPlayer.getSampleRate()) {
													//System.out.println("AB");
													nextPlayer = null;
													nextFramesWritten = 0;
												}
												//System.out.println("AC");
												if (nextPlayer != null) {
													//System.out.println("AD");
													nextPlayer.resetDecoderIfOutputAlreadyUsed();
												}
												//System.out.println("AE");
											}
										} catch (Throwable ex) {
											//System.out.println("AF " + ex.getMessage());
											nextPlayer = null;
											nextFramesWritten = 0;
											handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(nextPlayer, ex)), SystemClock.uptimeMillis());
										}
									}
								}
								break;
							case ACTION_RESET:
								//System.out.println("AG");
								if (playerRequestingAction == currentPlayer) {
									//System.out.println("AH");
									synchronized (openSLSync) {
										openSLRelease();
										sampleRate = 0;
									}
									//System.out.println("AI");
									outputBuffer.release();
									paused = true;
									currentPlayer = null;
									nextPlayer = null;
									sourcePlayer = null;
									framesWritten = 0;
									framesPlayed = 0;
									nextFramesWritten = 0;
									wakeLock.release();
									//System.out.println("AL");
								} else if (playerRequestingAction == nextPlayer) {
									//System.out.println("AM");
									//go back to currentPlayer
									if (sourcePlayer == nextPlayer) {
										//System.out.println("AN");
										outputBuffer.release();
										sourcePlayer = currentPlayer;
									}
									//System.out.println("AO");
									nextPlayer = null;
									nextFramesWritten = 0;
									//System.out.println("AP");
								}
								requestSucceeded = true;
								break;
							}
						} catch (Throwable ex) {
							//System.out.println("AQ " + ex.getMessage());
							synchronized (openSLSync) {
								//System.out.println("AQQ");
								openSLRelease();
								sampleRate = 0;
							}
							//System.out.println("AS");
							try {
								outputBuffer.release();
							} catch (Throwable ex2) {
								//just ignore
							}
							//System.out.println("AT");
							paused = true;
							currentPlayer = null;
							nextPlayer = null;
							sourcePlayer = null;
							framesWritten = 0;
							framesPlayed = 0;
							nextFramesWritten = 0;
							wakeLock.release();
							handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(playerRequestingAction, ex)), SystemClock.uptimeMillis());
							continue;
						} finally {
							//System.out.println("AU");
							requestedAction = ACTION_NONE;
							playerRequestingAction = null;
							synchronized (notification) {
								notification.notify();
							}
							//System.out.println("AV");
						}
						if (seekPending) {
							//System.out.println("AW");
							//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
								try {
									//why?!?!?!
									//WWWHHHHYYYYY?!?!?!?
									//https://groups.google.com/forum/#!msg/android-developers/zdgJ0QdvCAQ/o-0q8Bb5qRsJ
									Thread.sleep(100);
								} catch (Throwable ex) {
									//just ignore
								}
							//}
							try {
								//System.out.println("AX");
								outputBuffer.release();
								lastHeadPositionInFrames = openSLGetHeadPositionInFrames();
								resetFiltersAndWritePosition(lastHeadPositionInFrames);
								//System.out.println("BA");
								if (nextPlayer != null) {
									//System.out.println("BB");
									try {
										//System.out.println("BC");
										nextPlayer.resetDecoderIfOutputAlreadyUsed();
									} catch (Throwable ex) {
										//System.out.println("BD " + ex.getMessage());
										nextPlayer = null;
										handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(nextPlayer, ex)), SystemClock.uptimeMillis());
									}
									nextFramesWritten = 0;
								}
								//System.out.println("BE");
								currentPlayer.doSeek(requestedSeekMS);
								//System.out.println("BF");
								//give the decoder some time to decode something
								try {
									Thread.sleep(50);
								} catch (Throwable ex) {
									//just ignore
								}
								//System.out.println("BG");
								handler.sendMessageAtTime(Message.obtain(handler, MSG_SEEKCOMPLETE, currentPlayer), SystemClock.uptimeMillis());
								framesWritten = currentPlayer.getCurrentPositionInFrames();
								framesPlayed = framesWritten;
								//System.out.println("BH");
							} catch (Throwable ex) {
								//System.out.println("BI " + ex.getMessage());
								synchronized (openSLSync) {
									//System.out.println("AQQ");
									openSLRelease();
									sampleRate = 0;
								}
								//System.out.println("AS");
								try {
									outputBuffer.release();
								} catch (Throwable ex2) {
									//just ignore
								}
								//System.out.println("AT");
								paused = true;
								currentPlayer = null;
								nextPlayer = null;
								sourcePlayer = null;
								framesWritten = 0;
								framesPlayed = 0;
								nextFramesWritten = 0;
								wakeLock.release();
								handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(currentPlayer, ex)), SystemClock.uptimeMillis());
								continue;
							}
						}
					}
				}
			}

			try {
				if (paused)
					continue;

				int bytesWrittenThisTime;
				final int currentHeadPositionInFrames = openSLGetHeadPositionInFrames();
				//System.out.println("BJ " + currentHeadPositionInFrames);
				framesPlayed += (currentHeadPositionInFrames - lastHeadPositionInFrames);
				lastHeadPositionInFrames = currentHeadPositionInFrames;

				//try not to block for too long (<< 2 = stereo, 16 bits per sample)
				final int freeBytesInBuffer = (bufferSizeInFrames - (int)(framesWritten - framesPlayed)) << 2;
				if (freeBytesInBuffer < 0) {
					//System.out.println("********* BK " + freeBytesInBuffer);
					//???
				} else if (freeBytesInBuffer < (256 * 4)) {
					//System.out.println("BL " + freeBytesInBuffer);
					if (playPending) {
						//System.out.println("XA");
						playPending = false;
						ret = openSLPlay();
						if (ret != 0)
							throw new IllegalStateException("openSLPlay() returned " + ret);
						//System.out.println("XB");
					}
					//the buffer was already too full!
					try {
						synchronized (threadNotification) {
							threadNotification.wait(20);
						}
						//System.out.println("BM");
						continue;
					} catch (Throwable ex) {
						//just ignore
					}
				} else if (playPending) {
					//System.out.println("YA " + (bufferSizeInFrames - (freeBytesInBuffer >> 2)) + " | " + bufferSizeInFrames);
					if ((bufferSizeInFrames - (freeBytesInBuffer >> 2)) >= (bufferSizeInFrames >> 3)) {
						//System.out.println("YB");
						playPending = false;
						ret = openSLPlay();
						if (ret != 0)
							throw new IllegalStateException("openSLPlay() returned " + ret);
						//System.out.println("YC");
					}
				}

				//System.out.println("BN " + framesWritten + " | " + framesPlayed);
				currentPlayer.setCurrentPosition((int)((framesPlayed * 1000L) / (long)sampleRate));

				if (outputBuffer.index < 0)
					sourcePlayer.nextOutputBuffer(outputBuffer);
				if (outputBuffer.remainingBytes > 0) {
					if (MediaCodecPlayer.isDirect)
						bytesWrittenThisTime = openSLWriteDirect(outputBuffer.byteBuffer, outputBuffer.offsetInBytes, outputBuffer.remainingBytes, MediaCodecPlayer.needsSwap);
					else
						throw new UnsupportedOperationException("NOT DIRECT!!!");
					if (bytesWrittenThisTime < 0) {
						throw new IOException("audioTrackWriteDirect() returned " + bytesWrittenThisTime);
					} else {
						outputBuffer.remainingBytes -= bytesWrittenThisTime;
						outputBuffer.offsetInBytes += bytesWrittenThisTime;
					}
				} else {
					bytesWrittenThisTime = 0;
				}

				//MediaCodecPlayer always outputs stereo frames with 16 bits per sample
				if (sourcePlayer == currentPlayer)
					framesWritten += bytesWrittenThisTime >> 2;
				else
					nextFramesWritten += bytesWrittenThisTime >> 2;

				if (outputBuffer.remainingBytes <= 0) {
					//System.out.println("BO");
					outputBuffer.release();
					if (outputBuffer.streamOver && sourcePlayer == currentPlayer) {
						//from now on, we will start outputting audio from the next player (if any)
						if (nextPlayer != null)
							sourcePlayer = nextPlayer;
					}
				}

				if (framesPlayed >= framesWritten) {
					//System.out.println("BP");
					if (currentPlayer.isOutputOver()) {
						//we are done with this player!
						currentPlayer.setCurrentPosition(currentPlayer.getDuration());
						if (nextPlayer == null) {
							//there is nothing else to do!
							synchronized (openSLSync) {
								openSLRelease();
								sampleRate = 0;
							}
							outputBuffer.release();
							paused = true;
							wakeLock.release();
						} else {
							//keep playing!!!
							framesPlayed -= framesWritten;
							framesWritten = nextFramesWritten;
						}
						handler.sendMessageAtTime(Message.obtain(handler, MSG_COMPLETION, currentPlayer), SystemClock.uptimeMillis());
						currentPlayer = nextPlayer;
						if (currentPlayer != null)
							currentPlayer.startedAsNext();
						nextPlayer = null;
						nextFramesWritten = 0;
						sourcePlayer = currentPlayer;
					} else if (framesWritten != 0) {
						//System.out.println("BQ");
						//underrun!!!
						currentPlayer.notifyUnderrun();
						//give the decoder some time to decode something
						try {
							Thread.sleep(50);
						} catch (Throwable ex) {
							//just ignore
						}
					}
				}
				//System.out.println("BR");
			} catch (Throwable ex) {
				//System.out.println("BS " + ex.getMessage());
				try {
					outputBuffer.release();
				} catch (Throwable ex2) {
					//just ignore
				}
				if (sourcePlayer == currentPlayer) {
					synchronized (openSLSync) {
						openSLRelease();
						sampleRate = 0;
					}
					paused = true;
					currentPlayer = null;
					wakeLock.release();
				}
				handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(sourcePlayer, ex)), SystemClock.uptimeMillis());
				nextPlayer = null;
				nextFramesWritten = 0;
				sourcePlayer = currentPlayer;
			}
		}

		stopVisualization();

		if (wakeLock != null)
			wakeLock.release();
		synchronized (notification) {
			notification.notify();
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		//System.out.println("handleMessage A " + msg.what);
		if (!alive)
			return true;
		//System.out.println("handleMessage B " + msg.what);
		switch (msg.what) {
		case MSG_COMPLETION:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).onCompletion();
			break;
		case MSG_ERROR:
			if (msg.obj instanceof ErrorStructure) {
				((ErrorStructure)msg.obj).player.onError(((ErrorStructure)msg.obj).exception, 0);
				((ErrorStructure)msg.obj).exception.printStackTrace();
			}
			break;
		case MSG_SEEKCOMPLETE:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).onSeekComplete();
			break;
		case MSG_BUFFERUNDERRUN:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).onInfo(IMediaPlayer.INFO_BUFFERING_START, 0, null);
			break;
		case MSG_BUFFERSNORMALIZED:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).onInfo(IMediaPlayer.INFO_BUFFERING_END, 0, null);
			break;
		}
		return true;
	}

	public static void _initialize() {
		if (theMediaContext != null)
			return;

		theMediaContext = new MediaContext();

		alive = true;
		requestedAction = ACTION_INITIALIZE;
		playerRequestingAction = null;
		handler = new Handler(theMediaContext);

		thread = new Thread(theMediaContext, "MediaContext Output Thread");
		thread.start();

		synchronized (notification) {
			if (requestedAction == ACTION_INITIALIZE) {
				try {
					notification.wait();
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}

		if (initializationError) {
			//Oops! something went wrong!
			_release();
		}
	}

	public static void _release() {
		alive = false;
		synchronized (threadNotification) {
			threadNotification.notify();
		}
		if (thread != null) {
			try {
				thread.join();
			} catch (Throwable ex) {
				//just ignore
			}
			thread = null;
		}
		openSLRelease();
		requestedAction = ACTION_NONE;
		handler = null;
		playerRequestingAction = null;
		theMediaContext = null;
		initializationError = false;
	}

	static boolean play(MediaCodecPlayer player) {
		//System.out.println("play A " + player);
		if (!alive) {
			//System.out.println("play B");
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		//System.out.println("play C");
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			//System.out.println("play D");
			playerRequestingAction = player;
			requestedAction = ACTION_PLAY;
			threadNotification.notify();
		}
		//System.out.println("play E");
		synchronized (notification) {
			//System.out.println("play F");
			if (playerRequestingAction != null) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
			//System.out.println("play G");
		}
		return requestSucceeded;
	}

	static boolean pause(MediaCodecPlayer player) {
		//System.out.println("pause A");
		if (!alive) {
			//System.out.println("pause B");
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		//System.out.println("pause C");
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			//System.out.println("pause D");
			playerRequestingAction = player;
			requestedAction = ACTION_PAUSE;
			threadNotification.notify();
		}
		//System.out.println("pause E");
		synchronized (notification) {
			//System.out.println("pause F");
			if (playerRequestingAction != null) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
			//System.out.println("pause G");
		}
		return requestSucceeded;
	}

	static boolean resume(MediaCodecPlayer player) {
		//System.out.println("resume A");
		if (!alive) {
			//System.out.println("resume B");
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		//System.out.println("resume C");
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			//System.out.println("resume D");
			playerRequestingAction = player;
			requestedAction = ACTION_RESUME;
			threadNotification.notify();
		}
		//System.out.println("resume E");
		synchronized (notification) {
			//System.out.println("resume F");
			if (playerRequestingAction != null) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
			//System.out.println("resume G");
		}
		return requestSucceeded;
	}

	static boolean seekToAsync(MediaCodecPlayer player, int msec) {
		if (!alive) {
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			playerRequestingAction = player;
			requestedSeekMS = msec;
			requestedAction = ACTION_SEEK;
			threadNotification.notify();
		}
		synchronized (notification) {
			if (playerRequestingAction != null) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
		return requestSucceeded;
	}

	static void setNextPlayer(MediaCodecPlayer player, MediaCodecPlayer nextPlayer) {
		if (!alive)
			return;
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			playerRequestingAction = player;
			nextPlayerRequested = nextPlayer;
			requestedAction = ACTION_SETNEXT;
			threadNotification.notify();
		}
		synchronized (notification) {
			if (playerRequestingAction != null) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
		nextPlayerRequested = null;
	}

	static void reset(MediaCodecPlayer player) {
		//System.out.println("reset A " + player);
		if (!alive)
			return;
		//System.out.println("reset B");
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			//System.out.println("reset C");
			playerRequestingAction = player;
			requestedAction = ACTION_RESET;
			threadNotification.notify();
			//System.out.println("reset D");
		}
		synchronized (notification) {
			//System.out.println("reset E");
			if (playerRequestingAction != null) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
			//System.out.println("reset F");
		}
	}

	static void bufferUnderrun(MediaCodecPlayer player) {
		if (!alive || handler == null)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_BUFFERUNDERRUN, player), SystemClock.uptimeMillis());
	}

	static void buffersNormalized(MediaCodecPlayer player) {
		if (!alive || handler == null)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_BUFFERSNORMALIZED, player), SystemClock.uptimeMillis());
	}

	@SuppressWarnings("deprecation")
	static void setStereoVolume(float leftVolume, float rightVolume) {
		MediaContext.leftVolume = leftVolume;
		MediaContext.rightVolume = rightVolume;
		synchronized (openSLSync) {
			//openSLSetVolume(leftVolume, rightVolume);
		}
	}

	public static IMediaPlayer createMediaPlayer() {
		return new MediaCodecPlayer();
	}
}
