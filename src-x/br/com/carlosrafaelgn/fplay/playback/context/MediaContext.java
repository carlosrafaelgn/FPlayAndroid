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

public final class MediaContext implements Runnable, Handler.Callback {
	private static final int MSG_PLAYBACKCOMPLETE = 0x0100;
	private static final int MSG_ERROR = 0x0101;
	private static final int MSG_SEEKCOMPLETE = 0x0102;

	private static final int ACTION_NONE = 0x0000;
	private static final int ACTION_PLAY = 0x0001;
	private static final int ACTION_PAUSE = 0x0002;
	private static final int ACTION_RESUME = 0x0003;
	private static final int ACTION_SEEK = 0x0004;
	private static final int ACTION_SETNEXT = 0x0005;
	private static final int ACTION_RESET = 0x0006;

	private static final int PLAYER_TIMEOUT = 600000;

	//16 bits per sample, stereo
	private static final int FRAME_SIZE_IN_BYTES = 4;

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
	private static volatile boolean alive, requestSucceeded;
	private static volatile int requestedAction, requestedSeekMS;
	private static Handler handler;
	private static Thread thread;
	private static volatile AudioTrack audioTrackUsedForVolumeChanges;
	private static volatile MediaCodecPlayer playerRequestingAction, nextPlayerRequested;
	private static MediaContext theMediaContext;

	private MediaContext() {
	}

	private static AudioTrack createAudioTrack(int outputSampleRate) {
		//use our maximum supported sample rate (48000 Hz)
		int bufferSizeInBytes = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		int bufferSizeInFrames = bufferSizeInBytes / FRAME_SIZE_IN_BYTES; //16 bits per sample, stereo

		final int twoSecondsInFrames = outputSampleRate << 1;
		//at least 2 seconds, but a multiple of bufferSizeInFrames
		bufferSizeInFrames = ((twoSecondsInFrames + bufferSizeInFrames - 1) / bufferSizeInFrames) * bufferSizeInFrames;
		bufferSizeInBytes = bufferSizeInFrames * FRAME_SIZE_IN_BYTES;

		return new AudioTrack(AudioManager.STREAM_MUSIC, outputSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);
	}

	@Override
	public void run() {
		thread.setPriority(Thread.MAX_PRIORITY);

		final int outputSampleRate = 44100; //AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
		AudioTrack audioTrack;
		PowerManager.WakeLock wakeLock;
		MediaCodecPlayer currentPlayer = null, nextPlayer = null, sourcePlayer = null;
		MediaCodecPlayer.OutputBuffer outputBuffer = new MediaCodecPlayer.OutputBuffer();
		outputBuffer.index = -1;
		short[] outputShortBuffer = new short[0];
		int lastHeadPositionInFrames = 0;
		long framesWritten = 0, framesPlayed = 0, nextFramesWritten = 0;

		audioTrack = createAudioTrack(outputSampleRate);

		wakeLock = ((PowerManager)Player.theApplication.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MediaContext WakeLock");
		wakeLock.setReferenceCounted(false);

		audioTrackUsedForVolumeChanges = audioTrack;

		synchronized (notification) {
			notification.notify();
		}

		boolean paused = true;
		while (alive) {
			if (paused) {
				synchronized (threadNotification) {
					if (requestedAction == ACTION_NONE) {
						try {
							threadNotification.wait();
						} catch (Throwable ex) {
							//just ignore
						}
					}
				}
				if (!alive)
					break;
			}

			if (requestedAction != ACTION_NONE) {
				MediaCodecPlayer pendingSeekPlayer = null;
				requestSucceeded = false;
				try {
					//**** before calling notification.notify() we can safely assume the player thread
					//is halted while these actions take place, therefore the player objects are not
					//being used during this period
					switch (requestedAction) {
					case ACTION_PLAY:
						System.out.println("ACTION_PLAY " + playerRequestingAction);
						audioTrack.pause();
						audioTrack.flush();
						outputBuffer.release();
						currentPlayer = playerRequestingAction;
						nextPlayer = null;
						sourcePlayer = currentPlayer;
						currentPlayer.resetDecoderIfOutputAlreadyUsed();
						lastHeadPositionInFrames = 0;
						framesWritten = currentPlayer.getCurrentPositionInFrames();
						framesPlayed = currentPlayer.getCurrentPositionInFrames();
						nextFramesWritten = 0;
						audioTrack.play();
						paused = false;
						requestSucceeded = true;
						wakeLock.acquire();
						break;
					case ACTION_PAUSE:
						System.out.println("ACTION_PAUSE " + playerRequestingAction);
						if (playerRequestingAction == currentPlayer) {
							System.out.println("ACTION_PAUSE OK!");
							audioTrack.pause();
							paused = true;
							requestSucceeded = true;
							wakeLock.release();
						}
						break;
					case ACTION_RESUME:
						System.out.println("ACTION_RESUME " + playerRequestingAction);
						if (playerRequestingAction == currentPlayer) {
							System.out.println("ACTION_RESUME OK!");
							audioTrack.play();
							paused = false;
							requestSucceeded = true;
							wakeLock.acquire();
						}
						break;
					case ACTION_SEEK:
						System.out.println("ACTION_SEEK " + playerRequestingAction);
						if (playerRequestingAction == nextPlayer)
							throw new IllegalStateException("trying to seek nextPlayer");
						if (!paused)
							throw new IllegalStateException("trying to seek while not paused");
						pendingSeekPlayer = playerRequestingAction;
						requestSucceeded = true;
						break;
					case ACTION_SETNEXT:
						System.out.println("ACTION_SETNEXT " + playerRequestingAction);
						if (currentPlayer == playerRequestingAction && nextPlayer != nextPlayerRequested) {
							System.out.println("ACTION_SETNEXT A");
							//if we had already started outputting nextPlayer's audio then it is too
							//late... just remove the nextPlayer
							if (currentPlayer.isOutputOver()) {
								System.out.println("ACTION_SETNEXT B");
								//go back to currentPlayer
								if (sourcePlayer == nextPlayer) {
									outputBuffer.release();
									sourcePlayer = currentPlayer;
								}
								nextPlayer = null;
								nextFramesWritten = 0;
							} else {
								System.out.println("ACTION_SETNEXT C");
								nextPlayer = nextPlayerRequested;
								try {
									if (nextPlayer != null)
										nextPlayer.resetDecoderIfOutputAlreadyUsed();
								} catch (Throwable ex) {
									nextPlayer = null;
									handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(nextPlayer, ex)), SystemClock.uptimeMillis());
								}
							}
						}
						break;
					case ACTION_RESET:
						System.out.println("ACTION_RESET " + playerRequestingAction);
						if (playerRequestingAction == currentPlayer) {
							System.out.println("ACTION_RESET OK!");
							audioTrack.pause();
							audioTrack.flush();
							paused = true;
							outputBuffer.release();
							currentPlayer = null;
							nextPlayer = null;
							sourcePlayer = null;
							lastHeadPositionInFrames = 0;
							framesWritten = 0;
							framesPlayed = 0;
							nextFramesWritten = 0;
							wakeLock.release();
						} else if (playerRequestingAction == nextPlayer) {
							System.out.println("ACTION_RESET NEXT OK!");
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
					audioTrack.pause();
					audioTrack.flush();
					paused = true;
					outputBuffer.release();
					currentPlayer = null;
					nextPlayer = null;
					sourcePlayer = null;
					lastHeadPositionInFrames = 0;
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
							audioTrack.flush();
							outputBuffer.release();
							lastHeadPositionInFrames = 0;
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
						handler.sendMessageAtTime(Message.obtain(handler, MSG_SEEKCOMPLETE, pendingSeekPlayer), SystemClock.uptimeMillis());
						System.out.println("ACTION_SEEK COMPLETE!");
						if (pendingSeekPlayer == currentPlayer) {
							framesWritten = currentPlayer.getCurrentPositionInFrames();
							framesPlayed = currentPlayer.getCurrentPositionInFrames();
						}
					} catch (Throwable ex) {
						if (pendingSeekPlayer == currentPlayer) {
							outputBuffer.release();
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

			try {
				if (paused)
					continue;

				final boolean zeroed;
				int bytesWrittenThisTime;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (outputBuffer.index < 0) {
						if (sourcePlayer.nextOutputBuffer(outputBuffer)) {
							//process equalizer/visualizer here using all information from outputBuffer.byteBuffer (position and remaining)
						} else {
							System.out.println("*** NO OUTPUT BUFFER A");
						}
					}
					if (outputBuffer.size > 0) {
						bytesWrittenThisTime = audioTrack.write(outputBuffer.byteBuffer, outputBuffer.size, AudioTrack.WRITE_BLOCKING);
						if (bytesWrittenThisTime < 0) {
							throw new IOException("audioTrack.write() returned " + bytesWrittenThisTime);
						} else {
							outputBuffer.size -= bytesWrittenThisTime;
						}
					} else {
						bytesWrittenThisTime = 0;
					}
					zeroed = (outputBuffer.size <= 0);
				} else {
					if (outputBuffer.index < 0) {
						if (sourcePlayer.nextOutputBuffer(outputBuffer)) {
							//process equalizer/visualizer here using all information from outputBuffer.byteBuffer (position and remaining)
						} else {
							System.out.println("*** NO OUTPUT BUFFER B");
						}
						if (outputBuffer.size > outputShortBuffer.length)
							outputShortBuffer = new short[outputBuffer.size];
						outputBuffer.shortBuffer.get(outputShortBuffer, 0, outputBuffer.size);
					}
					if (outputBuffer.size > 0) {
						bytesWrittenThisTime = audioTrack.write(outputShortBuffer, outputBuffer.offset, outputBuffer.size);
						if (bytesWrittenThisTime < 0) {
							throw new IOException("audioTrack.write() returned " + bytesWrittenThisTime);
						} else {
							outputBuffer.size -= bytesWrittenThisTime;
							outputBuffer.offset += bytesWrittenThisTime;
							bytesWrittenThisTime <<= 1; // << 1 to convert shorts to bytes
						}
					} else {
						bytesWrittenThisTime = 0;
					}
					zeroed = (outputBuffer.size <= 0);
				}

				// >> 2 to convert from bytes to frames (we are always writting 16 bits per sample, stereo)
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

				final int currentHeadPositionInFrames = audioTrack.getPlaybackHeadPosition();
				framesPlayed += (currentHeadPositionInFrames - lastHeadPositionInFrames);
				lastHeadPositionInFrames = currentHeadPositionInFrames;

				if (currentPlayer.isOutputOver() && framesPlayed >= framesWritten) {
					//we are done with this player!
					currentPlayer.setCurrentPosition(currentPlayer.getDuration());
					if (nextPlayer == null) {
						//there is nothing else to do!
						audioTrack.pause();
						audioTrack.flush();
						paused = true;
						outputBuffer.release();
						wakeLock.release();
					} else {
						//keep playing!!!
						framesPlayed -= framesWritten;
						framesWritten = nextFramesWritten;
					}
					handler.sendMessageAtTime(Message.obtain(handler, MSG_PLAYBACKCOMPLETE, currentPlayer), SystemClock.uptimeMillis());
					currentPlayer = nextPlayer;
					if (currentPlayer != null)
						currentPlayer.startedAsNext();
					nextPlayer = null;
					nextFramesWritten = 0;
					sourcePlayer = currentPlayer;
				} else {
					currentPlayer.setCurrentPosition((int)((framesPlayed * 1000L) / (long)outputSampleRate));
				}

			} catch (Throwable ex) {
				outputBuffer.release();
				if (sourcePlayer == currentPlayer) {
					audioTrack.pause();
					audioTrack.flush();
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

		if (audioTrack != null)
			audioTrack.release();
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
		case MSG_PLAYBACKCOMPLETE:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).playbackComplete();
			break;
		case MSG_ERROR:
			if (msg.obj instanceof ErrorStructure) {
				((ErrorStructure)msg.obj).player.error(((ErrorStructure)msg.obj).exception);
				((ErrorStructure)msg.obj).exception.printStackTrace();
			}
			break;
		case MSG_SEEKCOMPLETE:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).seekComplete();
			break;
		}
		return true;
	}

	public static void _initialize() {
		theMediaContext = new MediaContext();

		alive = true;
		requestedAction = ACTION_NONE;
		playerRequestingAction = null;
		handler = new Handler(theMediaContext);

		thread = new Thread(theMediaContext, "MediaContext Output Thread");
		thread.start();

		synchronized (notification) {
			if (audioTrackUsedForVolumeChanges == null) {
				try {
					notification.wait();
				} catch (Throwable ex) {
					//just ignore
				}
			}
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
		requestedAction = ACTION_NONE;
		handler = null;
		audioTrackUsedForVolumeChanges = null;
		playerRequestingAction = null;
		theMediaContext = null;
	}

	static boolean play(MediaCodecPlayer player) {
		if (!alive)
			return false;
		playerRequestingAction = player;
		requestedAction = ACTION_PLAY;
		synchronized (threadNotification) {
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
		if (!alive)
			return false;
		playerRequestingAction = player;
		requestedAction = ACTION_PAUSE;
		synchronized (threadNotification) {
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
		if (!alive)
			return false;
		playerRequestingAction = player;
		requestedAction = ACTION_RESUME;
		synchronized (threadNotification) {
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
		if (!alive)
			return false;
		playerRequestingAction = player;
		requestedSeekMS = msec;
		requestedAction = ACTION_SEEK;
		synchronized (threadNotification) {
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
		playerRequestingAction = player;
		nextPlayerRequested = nextPlayer;
		requestedAction = ACTION_SETNEXT;
		synchronized (threadNotification) {
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
		playerRequestingAction = player;
		requestedAction = ACTION_RESET;
		synchronized (threadNotification) {
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

	public static IMediaPlayer createMediaPlayer() {
		return new MediaCodecPlayer(audioTrackUsedForVolumeChanges);
	}

	public static IEqualizer createEqualizer() {
		return new Equalizer(Player.audioSessionId);
	}
}
