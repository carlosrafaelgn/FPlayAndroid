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
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
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
	private static final Object audioTrackSync = new Object();
	private static volatile boolean alive, waitToReceiveAction, requestSucceeded;
	private static volatile int requestedAction, requestedSeekMS;
	private static boolean initializationError;
	private static float leftVolume = 1.0f, rightVolume = 1.0f;
	private static Handler handler;
	private static Thread thread;
	private static AudioTrack audioTrack;
	private static volatile MediaCodecPlayer playerRequestingAction, nextPlayerRequested;
	private static MediaContext theMediaContext;

	static {
		System.loadLibrary("MediaContextJni");
	}

	private static native void setBufferSizeInFrames(int bufferSizeInFrames);
	private static native void configChanged(int channelCount, int sampleRate);
	private static native void resetFiltersAndWritePosition(int writePositionInFrames);

	static native void enableEqualizer(int enabled);
	static native int isEqualizerEnabled();
	static native void setEqualizerBandLevel(int band, int level);
	static native void setEqualizerBandLevels(short[] levels);

	static native void enableBassBoost(int enabled);
	static native int isBassBoostEnabled();

	static native long startVisualization();
	static native long getVisualizationPtr();
	static native void stopVisualization();

	static native int processDirectData(ByteBuffer buffer, int offsetInBytes, int sizeInBytes, int needsSwap);
	static native int processData(byte[] buffer, int offsetInBytes, int sizeInBytes, int needsSwap);

	private MediaContext() {
	}

	@SuppressWarnings("deprecation")
	private static int createAudioTrack() {
		//we will create an audio track with a 1-second length, supposing 48000 Hz (MediaCodecPlayer
		//always outputs stereo frames with 16 bits per sample)
		int bufferSizeInBytes = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		int bufferSizeInFrames = bufferSizeInBytes >> 2;

		final int oneSecondInFrames = 48000;
		//make sure it is a multiple of bufferSizeInFrames
		bufferSizeInFrames = ((oneSecondInFrames + bufferSizeInFrames - 1) / bufferSizeInFrames) * bufferSizeInFrames;
		bufferSizeInBytes = bufferSizeInFrames << 2;

		synchronized (audioTrackSync) {
			if (audioTrack != null) {
				try {
					audioTrack.pause();
				} catch (Throwable ex) {
					//just ignore
				}
				try {
					audioTrack.release();
				} catch (Throwable ex) {
					//just ignore
				}
			}
			try {
				audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);
				audioTrack.setStereoVolume(leftVolume, rightVolume);
			} catch (Throwable ex) {
				audioTrack = null;
			}
		}

		setBufferSizeInFrames(bufferSizeInFrames);

		return bufferSizeInFrames;
	}

	private static void pauseAndAbortAudioTrackWrite() {
		//http://developer.android.com/reference/android/media/AudioTrack.html#write(short[], int, int)
		//In streaming mode, the write will normally block until all the data has been enqueued for
		//playback, and will return a full transfer count. However, if the track is stopped or
		//paused on entry, or another thread interrupts the write by calling stop or pause, or an
		//I/O error occurs during the write, then the write may return a short transfer count.
		if (audioTrack != null) {
			try {
				audioTrack.pause();
			} catch (Throwable ex) {
				//just ignore
			}
		}
	}

	@Override
	public void run() {
		thread.setPriority(Thread.MAX_PRIORITY);

		int sampleRate = 44100; //AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
		PowerManager.WakeLock wakeLock;
		MediaCodecPlayer currentPlayer = null, nextPlayer = null, sourcePlayer = null;
		MediaCodecPlayer.OutputBuffer outputBuffer = new MediaCodecPlayer.OutputBuffer();
		outputBuffer.index = -1;
		short[] outputShortBuffer = new short[0];
		int lastHeadPositionInFrames = 0;
		long framesWritten = 0, framesPlayed = 0, nextFramesWritten = 0;

		final int bufferSizeInFrames = createAudioTrack();
		MediaCodecPlayer.tryToProcessNonDirectBuffers = true;

		configChanged(2, sampleRate);

		wakeLock = ((PowerManager)Player.theApplication.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MediaContext WakeLock");
		wakeLock.setReferenceCounted(false);

		requestedAction = ACTION_NONE;

		synchronized (notification) {
			notification.notify();
		}

		boolean paused = true;
		while (alive) {
			if (paused || waitToReceiveAction) {
				synchronized (threadNotification) {
					if (requestedAction == ACTION_NONE) {
						try {
							threadNotification.wait();
						} catch (Throwable ex) {
							//just ignore
						}
					}

					if (!alive)
						break;

					if (requestedAction != ACTION_NONE) {
						waitToReceiveAction = false;
						MediaCodecPlayer pendingSeekPlayer = null;
						requestSucceeded = false;
						try {
							//**** before calling notification.notify() we can safely assume the player thread
							//is halted while these actions take place, therefore the player objects are not
							//being used during this period
							switch (requestedAction) {
							case ACTION_PLAY:
								//audioTrack.pause();
								audioTrack.flush();
								outputBuffer.release();
								currentPlayer = playerRequestingAction;
								nextPlayer = null;
								sourcePlayer = currentPlayer;
								currentPlayer.resetDecoderIfOutputAlreadyUsed();
								framesWritten = currentPlayer.getCurrentPositionInFrames();
								framesPlayed = currentPlayer.getCurrentPositionInFrames();
								nextFramesWritten = 0;
								if (sampleRate != currentPlayer.getSampleRate()) {
									sampleRate = currentPlayer.getSampleRate();
									if (audioTrack.setPlaybackRate(sampleRate) != AudioTrack.SUCCESS)
										throw new IOException();
									configChanged(2, sampleRate);
									MediaCodecPlayer.tryToProcessNonDirectBuffers = true;
								}
								audioTrack.play();
								lastHeadPositionInFrames = audioTrack.getPlaybackHeadPosition();
								resetFiltersAndWritePosition(lastHeadPositionInFrames);
								paused = false;
								requestSucceeded = true;
								wakeLock.acquire();
								break;
							case ACTION_PAUSE:
								if (playerRequestingAction == currentPlayer) {
									//audioTrack.pause();
									paused = true;
									requestSucceeded = true;
									wakeLock.release();
								} else {
									throw new IllegalStateException("impossible to pause a player other than currentPlayer");
								}
								break;
							case ACTION_RESUME:
								if (playerRequestingAction == currentPlayer) {
									audioTrack.play();
									paused = false;
									requestSucceeded = true;
									wakeLock.acquire();
								} else {
									throw new IllegalStateException("impossible to resume a player other than currentPlayer");
								}
								break;
							case ACTION_SEEK:
								if (currentPlayer != null && currentPlayer != playerRequestingAction)
									throw new IllegalStateException("impossible to seek a player other than currentPlayer");
								if (!paused)
									throw new IllegalStateException("trying to seek while not paused");
								pendingSeekPlayer = playerRequestingAction;
								requestSucceeded = true;
								break;
							case ACTION_SETNEXT:
								if (currentPlayer == playerRequestingAction && nextPlayer != nextPlayerRequested) {
									//if we had already started outputting nextPlayer's audio then it is too
									//late... just remove the nextPlayer
									if (currentPlayer.isOutputOver()) {
										//go back to currentPlayer
										if (sourcePlayer == nextPlayer) {
											outputBuffer.release();
											sourcePlayer = currentPlayer;
										}
										nextPlayer = null;
										nextFramesWritten = 0;
									} else {
										nextPlayer = nextPlayerRequested;
										try {
											if (nextPlayer != null) {
												if (currentPlayer.isInternetStream() ||
													nextPlayer.isInternetStream() ||
													sampleRate != nextPlayer.getSampleRate()) {
													nextPlayer = null;
													nextFramesWritten = 0;
												}
												if (nextPlayer != null)
													nextPlayer.resetDecoderIfOutputAlreadyUsed();
											}
										} catch (Throwable ex) {
											nextPlayer = null;
											nextFramesWritten = 0;
											handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(nextPlayer, ex)), SystemClock.uptimeMillis());
										}
									}
								}
								break;
							case ACTION_RESET:
								if (playerRequestingAction == currentPlayer) {
									audioTrack.pause();
									if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
										try {
											//why?!?!?!
											//WWWHHHHYYYYY?!?!?!?
											//https://groups.google.com/forum/#!msg/android-developers/zdgJ0QdvCAQ/o-0q8Bb5qRsJ
											Thread.sleep(100);
										} catch (Throwable ex) {
											//just ignore
										}
									}
									audioTrack.flush();
									outputBuffer.release();
									paused = true;
									currentPlayer = null;
									nextPlayer = null;
									sourcePlayer = null;
									framesWritten = 0;
									framesPlayed = 0;
									nextFramesWritten = 0;
									wakeLock.release();
								} else if (playerRequestingAction == nextPlayer) {
									//go back to currentPlayer
									if (sourcePlayer == nextPlayer) {
										outputBuffer.release();
										sourcePlayer = currentPlayer;
									}
									nextPlayer = null;
									nextFramesWritten = 0;
								}
								requestSucceeded = true;
								break;
							}
						} catch (Throwable ex) {
							pendingSeekPlayer = null;
							try {
								audioTrack.pause();
							} catch (Throwable ex2) {
								//just ignore
							}
							try {
								audioTrack.flush();
							} catch (Throwable ex2) {
								//just ignore
							}
							try {
								outputBuffer.release();
							} catch (Throwable ex2) {
								//just ignore
							}
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
							requestedAction = ACTION_NONE;
							playerRequestingAction = null;
							synchronized (notification) {
								notification.notify();
							}
						}
						if (pendingSeekPlayer != null) {
							try {
								if (pendingSeekPlayer == currentPlayer) {
									if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
										try {
											//why?!?!?!
											//WWWHHHHYYYYY?!?!?!?
											//https://groups.google.com/forum/#!msg/android-developers/zdgJ0QdvCAQ/o-0q8Bb5qRsJ
											Thread.sleep(100);
										} catch (Throwable ex) {
											//just ignore
										}
									}
									audioTrack.flush();
									outputBuffer.release();
									lastHeadPositionInFrames = audioTrack.getPlaybackHeadPosition();
									resetFiltersAndWritePosition(lastHeadPositionInFrames);
									if (nextPlayer != null) {
										try {
											nextPlayer.resetDecoderIfOutputAlreadyUsed();
										} catch (Throwable ex) {
											nextPlayer = null;
											handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(nextPlayer, ex)), SystemClock.uptimeMillis());
										}
										nextFramesWritten = 0;
									}
								}
								pendingSeekPlayer.doSeek(requestedSeekMS);
								//give the decoder some time to decode something
								try {
									Thread.sleep(10);
								} catch (Throwable ex) {
									//just ignore
								}
								handler.sendMessageAtTime(Message.obtain(handler, MSG_SEEKCOMPLETE, pendingSeekPlayer), SystemClock.uptimeMillis());
								if (pendingSeekPlayer == currentPlayer) {
									framesWritten = currentPlayer.getCurrentPositionInFrames();
									framesPlayed = currentPlayer.getCurrentPositionInFrames();
								}
							} catch (Throwable ex) {
								if (pendingSeekPlayer == currentPlayer) {
									try {
										outputBuffer.release();
									} catch (Throwable ex2) {
										//just ignore
									}
									currentPlayer = null;
									nextPlayer = null;
									sourcePlayer = null;
									wakeLock.release();
								}
								handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(pendingSeekPlayer, ex)), SystemClock.uptimeMillis());
								continue;
							}
						}
					}
				}
			}

			try {
				if (paused)
					continue;

				final boolean zeroed;
				int bytesWrittenThisTime;

				final int currentHeadPositionInFrames = audioTrack.getPlaybackHeadPosition();
				framesPlayed += (currentHeadPositionInFrames - lastHeadPositionInFrames);
				lastHeadPositionInFrames = currentHeadPositionInFrames;

				//try not to block for too long (<< 2 = stereo, 16 bits per sample)
				int freeBytesInBuffer = (bufferSizeInFrames - (int)(framesWritten - framesPlayed)) << 2;
				if (freeBytesInBuffer < 0) {
					//???
					freeBytesInBuffer = (256 * 4);
				} else if (freeBytesInBuffer < (256 * 4)) {
					//the buffer was already too full!
					try {
						synchronized (threadNotification) {
							threadNotification.wait(5);
						}
						continue;
					} catch (Throwable ex) {
						//just ignore
					}
				}

				currentPlayer.setCurrentPosition((int)((framesPlayed * 1000L) / (long)sampleRate));

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (outputBuffer.index < 0)
						sourcePlayer.nextOutputBuffer(outputBuffer);
					if (outputBuffer.remaining > 0) {
						//using AudioTrack.WRITE_NON_BLOCKING apparently produces clicks when unpausing
						bytesWrittenThisTime = audioTrack.write(outputBuffer.byteBuffer, (outputBuffer.remaining < freeBytesInBuffer) ? outputBuffer.remaining : freeBytesInBuffer, AudioTrack.WRITE_BLOCKING);
						if (bytesWrittenThisTime < 0) {
							throw new IOException("audioTrack.write() returned " + bytesWrittenThisTime);
						} else {
							outputBuffer.remaining -= bytesWrittenThisTime;
							outputBuffer.offset += bytesWrittenThisTime;
						}
					} else {
						bytesWrittenThisTime = 0;
					}
					zeroed = (outputBuffer.remaining <= 0);
				} else {
					if (outputBuffer.index < 0) {
						if (sourcePlayer.nextOutputBuffer(outputBuffer)) {
							if ((outputBuffer.remaining + outputBuffer.offset) > outputShortBuffer.length)
								outputShortBuffer = new short[outputBuffer.remaining + outputBuffer.offset];
							outputBuffer.shortBuffer.get(outputShortBuffer, outputBuffer.offset, outputBuffer.remaining);
						}
					}
					if (outputBuffer.remaining > 0) {
						freeBytesInBuffer >>= 1; // from bytes to shorts
						bytesWrittenThisTime = audioTrack.write(outputShortBuffer, outputBuffer.offset, (outputBuffer.remaining < freeBytesInBuffer) ? outputBuffer.remaining : freeBytesInBuffer);
						if (bytesWrittenThisTime < 0) {
							throw new IOException("audioTrack.write() returned " + bytesWrittenThisTime);
						} else {
							outputBuffer.remaining -= bytesWrittenThisTime;
							outputBuffer.offset += bytesWrittenThisTime;
							bytesWrittenThisTime <<= 1; // << 1 to convert from shorts to bytes
						}
					} else {
						bytesWrittenThisTime = 0;
					}
					zeroed = (outputBuffer.remaining <= 0);
				}

				//MediaCodecPlayer always outputs stereo frames with 16 bits per sample
				if (sourcePlayer == currentPlayer)
					framesWritten += bytesWrittenThisTime >> 2;
				else
					nextFramesWritten += bytesWrittenThisTime >> 2;

				if (zeroed) {
					outputBuffer.release();
					if (outputBuffer.streamOver && sourcePlayer == currentPlayer) {
						//from now on, we will start outputting audio from the next player (if any)
						if (nextPlayer != null)
							sourcePlayer = nextPlayer;
					}
				}

				if (framesPlayed >= framesWritten) {
					if (currentPlayer.isOutputOver()) {
						//we are done with this player!
						currentPlayer.setCurrentPosition(currentPlayer.getDuration());
						if (nextPlayer == null) {
							//there is nothing else to do!
							audioTrack.pause();
							audioTrack.flush();
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
						//underrun!!!
						currentPlayer.notifyUnderrun();
						//give the decoder some time to decode something
						try {
							Thread.sleep(10);
						} catch (Throwable ex) {
							//just ignore
						}
					}
				}
			} catch (Throwable ex) {
				try {
					outputBuffer.release();
				} catch (Throwable ex2) {
					//just ignore
				}
				if (sourcePlayer == currentPlayer) {
					try {
						audioTrack.pause();
					} catch (Throwable ex2) {
						//just ignore
					}
					try {
						audioTrack.flush();
					} catch (Throwable ex2) {
						//just ignore
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
		if (!alive)
			return true;
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

		if (audioTrack == null) {
			//Oops! something went wrong!
			_release();
			initializationError = true;
		} else {
			initializationError = false;
		}
	}

	public static void _release() {
		alive = false;
		pauseAndAbortAudioTrackWrite();
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
		if (audioTrack != null) {
			audioTrack.release();
			audioTrack = null;
		}
		requestedAction = ACTION_NONE;
		handler = null;
		playerRequestingAction = null;
		theMediaContext = null;
		initializationError = false;
	}

	static boolean play(MediaCodecPlayer player) {
		if (!alive) {
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			pauseAndAbortAudioTrackWrite();
			playerRequestingAction = player;
			requestedAction = ACTION_PLAY;
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

	static boolean pause(MediaCodecPlayer player) {
		if (!alive) {
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			pauseAndAbortAudioTrackWrite();
			playerRequestingAction = player;
			requestedAction = ACTION_PAUSE;
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

	static boolean resume(MediaCodecPlayer player) {
		if (!alive) {
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			playerRequestingAction = player;
			requestedAction = ACTION_RESUME;
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

	static boolean seekToAsync(MediaCodecPlayer player, int msec) {
		if (!alive) {
			if (initializationError)
				player.mediaServerDied();
			return false;
		}
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			pauseAndAbortAudioTrackWrite();
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
		if (!alive)
			return;
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			playerRequestingAction = player;
			requestedAction = ACTION_RESET;
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
		synchronized (audioTrackSync) {
			if (audioTrack != null)
				audioTrack.setStereoVolume(leftVolume, rightVolume);
		}
	}

	static int getAudioSessionId() {
		synchronized (audioTrackSync) {
			return (audioTrack == null ? -1 : audioTrack.getAudioSessionId());
		}
	}

	public static IMediaPlayer createMediaPlayer() {
		return new MediaCodecPlayer();
	}

	public static IEqualizer createEqualizer() {
		return new Equalizer();
	}
}
