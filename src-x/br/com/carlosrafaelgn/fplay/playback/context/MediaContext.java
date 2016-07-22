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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import br.com.carlosrafaelgn.fplay.playback.Player;

public final class MediaContext implements Runnable, Handler.Callback {
	private static final int MSG_COMPLETION = 0x0100;
	private static final int MSG_ERROR = 0x0101;
	private static final int MSG_SEEKCOMPLETE = 0x0102;
	private static final int MSG_BUFFERINGSTART = 0x0103;
	private static final int MSG_BUFFERINGEND = 0x0104;
	private static final int MSG_EQUALIZER_ENABLE = 0x0105;
	private static final int MSG_EQUALIZER_BAND_LEVEL = 0x0106;
	private static final int MSG_EQUALIZER_BAND_LEVELS = 0x0107;
	private static final int MSG_BASSBOOST_ENABLE = 0x0108;
	private static final int MSG_BASSBOOST_STRENGTH = 0x0109;
	private static final int MSG_VIRTUALIZER_ENABLE = 0x010A;
	private static final int MSG_VIRTUALIZER_STRENGTH = 0x010B;

	private static final int ACTION_NONE = 0x0000;
	private static final int ACTION_PLAY = 0x0001;
	private static final int ACTION_PAUSE = 0x0002;
	private static final int ACTION_RESUME = 0x0003;
	private static final int ACTION_SEEK = 0x0004;
	private static final int ACTION_SETNEXT = 0x0005;
	private static final int ACTION_RESET = 0x0006;
	private static final int ACTION_EFFECTS = 0x0007;
	private static final int ACTION_UPDATE_BUFFER_CONFIG = 0x0008;
	private static final int ACTION_ENABLE_EFFECTS_GAIN = 0x0009;
	private static final int ACTION_DISABLE_EFFECTS_GAIN = 0x000A;
	private static final int ACTION_INITIALIZE = 0xFFFF;

	private static final int MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING = 1152;

	private static final int PLAYER_TIMEOUT = 30000;

	private static final int SL_MILLIBEL_MIN = -32768;
	private static final int AUDIO_TRACK_OUT_OF_MEMORY = 1000; //this error code is not used by OpenSL ES

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
	private static final Object engineSync = new Object();
	private static volatile boolean alive, waitToReceiveAction, requestSucceeded, initializationError;
	private static volatile int requestedAction, requestedSeekMS;
	private static Message effectsMessage;
	private static int bufferConfig, nativeSampleRate, srcChannelCount;
	private static float gain = 1.0f;
	private static Handler handler;
	private static Thread thread;
	private static volatile MediaCodecPlayer playerRequestingAction, nextPlayerRequested;
	private static MediaContext theMediaContext;
	private static Engine engine;
	public static boolean useOpenSLEngine;
	private static boolean hasExternalNativeLibrary;
	static boolean engineAcceptsNativeBuffers, engineNeedsFullBufferBeforeResuming;

	static {
		System.loadLibrary("MediaContextJni");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			hasExternalNativeLibrary = (mediaCodecLoadExternalLibrary() == 0);
	}

	private static native int getProcessorFeatures();

	private static native void updateSrcParams(int srcSampleRate, int srcChannelCount, int resetFiltersAndWritePosition);

	public static native int getCurrentAutomaticEffectsGainInMB();
	private static native void enableAutomaticEffectsGain(int enabled);
	public static native int isAutomaticEffectsGainEnabled();

	private static native void enableEqualizer(int enabled);
	static native int isEqualizerEnabled();
	private static native void setEqualizerBandLevel(int band, int level);
	private static native void setEqualizerBandLevels(short[] levels);

	private static native void enableBassBoost(int enabled);
	static native int isBassBoostEnabled();
	private static native void setBassBoostStrength(int strength);
	static native int getBassBoostRoundedStrength();

	private static native void enableVirtualizer(int enabled);
	static native int isVirtualizerEnabled();
	private static native void setVirtualizerStrength(int strength);
	static native int getVirtualizerRoundedStrength();

	static native int mediaCodecPrepare(int fd, long length, long[] outParams);
	static native int mediaCodecFillInputBuffers(long nativeObj);
	static native int mediaCodecNextOutputBuffer(long nativeObj);
	static native long mediaCodecSeek(long nativeObj, int msec);
	static native void mediaCodecReleaseOutputBuffer(long nativeObj);
	static native void mediaCodecRelease(long nativeObj);
	static native int mediaCodecLoadExternalLibrary();

	private static native void audioTrackInitialize();
	private static native void audioTrackCreate(int dstSampleRate);
	private static native long audioTrackProcessEffects(byte[] srcArray, ByteBuffer srcBuffer, int offsetInBytes, int sizeInFrames, int needsSwap, byte[] dstArray, ByteBuffer dstBuffer);

	private static native int openSLInitialize();
	private static native int openSLCreate(int dstSampleRate, int bufferSizeInFrames, int minBufferSizeInFrames);
	private static native int openSLPlay();
	private static native int openSLPause();
	private static native int openSLStopAndFlush();
	private static native void openSLRelease();
	private static native void openSLTerminate();
	private static native void openSLSetVolumeInMillibels(int volumeInMillibels);
	private static native int openSLGetHeadPositionInFrames();
	private static native long openSLWriteNative(long nativeObj, int offsetInBytes, int sizeInFrames);
	private static native long openSLWrite(byte[] array, ByteBuffer buffer, int offsetInBytes, int sizeInFrames, int needsSwap);

	private static abstract class Engine {
		public abstract int initialize();
		public abstract int create(int sampleRate, int bufferSizeInFrames, int minBufferSizeInFrames);
		public abstract int recreateIfNeeded(int sampleRate, int bufferSizeInFrames);
		public abstract int play();
		public abstract int pause();
		public abstract int stopAndFlush();
		public abstract void release();
		public abstract void terminate();
		public abstract void setVolume();
		public abstract int getActualBufferSizeInFrames();
		public abstract int getHeadPositionInFrames();
		public abstract int getFillThresholdInFrames(int bufferSizeInFrames);
		public abstract int commitFinalFrames(int emptyFrames);
		public abstract int writeNative(long nativeObj, MediaCodecPlayer.OutputBuffer buffer);
		public abstract int write(MediaCodecPlayer.OutputBuffer buffer, int emptyFrames);
	}

	private static final class AudioTrackEngine extends Engine {
		private static final class QueryableAudioTrack extends AudioTrack {
			public QueryableAudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode) {
				super(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);
			}

			@SuppressWarnings("deprecation")
			public int getActualBufferSizeInFrames() {
				return getNativeFrameCount();
			}
		}

		private QueryableAudioTrack audioTrack;
		private byte[] tempDstArray;
		private ByteBuffer tempDstBuffer;
		private boolean okToQuitIfFull;
		private int pendingOffsetInBytes, pendingDstFrames;

		@Override
		public int initialize() {
			engineAcceptsNativeBuffers = false;
			engineNeedsFullBufferBeforeResuming = true;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				tempDstBuffer = ByteBuffer.allocateDirect(MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 2);
				tempDstBuffer.order(ByteOrder.nativeOrder());
			} else {
				tempDstArray = new byte[MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 2];
			}

			audioTrackInitialize();
			return 0;
		}

		@Override
		public int create(int sampleRate, int bufferSizeInFrames, int minBufferSizeInFrames) {
			release();
			audioTrackCreate(sampleRate);
			audioTrack = new QueryableAudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInFrames << 2, AudioTrack.MODE_STREAM);
			pendingOffsetInBytes = 0;
			pendingDstFrames = 0;
			try {
				//apparently, there are times when audioTrack creation fails, but the constructor
				//above does not throw any exceptions (only getNativeFrameCount() throws exceptions
				//in such cases)
				getActualBufferSizeInFrames();
			} catch (Throwable ex) {
				return AUDIO_TRACK_OUT_OF_MEMORY;
			}
			setVolume();
			return 0;
		}

		@Override
		public int recreateIfNeeded(int sampleRate, int bufferSizeInFrames) {
			return (audioTrack != null ? 0 : create(sampleRate, bufferSizeInFrames, 0));
		}

		@Override
		public int play() {
			if (audioTrack != null)
				audioTrack.play();
			return 0;
		}

		@Override
		public int pause() {
			okToQuitIfFull = false;
			if (audioTrack != null)
				audioTrack.pause();
			return 0;
		}

		@Override
		public int stopAndFlush() {
			//after discovering SEVERAL bugs and bizarre return values, I decided to simply
			//destroy and recreate the AudioTrack every time, instead of pausing/flushing
			release();
			pendingOffsetInBytes = 0;
			pendingDstFrames = 0;
			return 0;
		}

		@Override
		public void release() {
			okToQuitIfFull = false;
			if (audioTrack != null) {
				audioTrack.release();
				audioTrack = null;
			}
		}

		@Override
		public void terminate() {
			release();
		}

		@SuppressWarnings("deprecation")
		@Override
		public void setVolume() {
			if (audioTrack != null)
				audioTrack.setStereoVolume(gain, gain);
		}

		@Override
		public int getActualBufferSizeInFrames() {
			return (audioTrack != null ? audioTrack.getActualBufferSizeInFrames() : 0);
		}

		@Override
		public int getHeadPositionInFrames() {
			return (audioTrack != null ? audioTrack.getPlaybackHeadPosition() : 0);
		}

		@Override
		public int getFillThresholdInFrames(int bufferSizeInFrames) {
			//the AudioTrack must only start playing when it returns 0
			return 0x7fffffff;
		}

		@Override
		public int commitFinalFrames(int emptyFrames) {
			if (pendingDstFrames > 0)
				return write(null, emptyFrames);
			Arrays.fill(tempDstArray, 0, 1024, (byte)0);
			audioTrack.write(tempDstArray, 0, 1024);
			//allow method run() to sleep
			return 0;
		}

		@Override
		public int writeNative(long nativeObj, MediaCodecPlayer.OutputBuffer buffer) {
			throw new UnsupportedOperationException("AudioTrackEngine does not support native writes");
		}

		@Override
		public int write(MediaCodecPlayer.OutputBuffer buffer, int emptyFrames) {
			if (pendingDstFrames <= 0) {
				int sizeInFrames = buffer.remainingBytes >> srcChannelCount;

				//we cannot let audioTrack block for too long (should it actually block)
				while (sizeInFrames > MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING)
					sizeInFrames >>= 1;

				//*** NEVER TRY TO RETURN 0 HERE, MANUALLY CONTROLLING WHETHER THE AUDIOTRACK IS FULL,
				//BEFORE MAKING SURE WE HAVE WRITTEN UP TO/PAST THE END OF IT!!!
				//*** THERE IS A BUG IN AUDIOTRACK, AND IT ONLY STARTS PLAYING IF SAMPLES ARE WRITTEN
				//UP TO/PAST THE END OF IT!

				if ((okToQuitIfFull && sizeInFrames >= emptyFrames) || sizeInFrames == 0)
					return 0;

				long dstSrcRet;
				if ((dstSrcRet = audioTrackProcessEffects(buffer.byteArray, buffer.byteBuffer, buffer.offsetInBytes, sizeInFrames, MediaCodecPlayer.needsSwap, tempDstArray, tempDstBuffer)) < 0)
					return (int)dstSrcRet;

				//dstFramesUsed -> low
				//srcFramesUsed -> high
				pendingDstFrames = (int)dstSrcRet;
				final int srcBytesUsed = (int)(dstSrcRet >>> 32) << srcChannelCount;
				buffer.remainingBytes -= srcBytesUsed;
				buffer.offsetInBytes += srcBytesUsed;
				pendingOffsetInBytes = 0;
			}

			//from here on, tempDstBuffer/Array will always contain stereo frames
			final int sizeInBytes = pendingDstFrames << 2;
			int ret;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				tempDstBuffer.limit(pendingOffsetInBytes + sizeInBytes);
				tempDstBuffer.position(pendingOffsetInBytes);
				ret = audioTrack.write(tempDstBuffer, sizeInBytes, AudioTrack.WRITE_BLOCKING);
			} else {
				ret = audioTrack.write(tempDstArray, pendingOffsetInBytes, sizeInBytes);
			}
			if (ret == 0)
				okToQuitIfFull = true;
			pendingOffsetInBytes += ret;
			//ret was in bytes, but we need to return frames
			ret >>= 2;
			pendingDstFrames -= ret;
			return ret;
		}
	}

	private static final class OpenSLEngine extends Engine {
		private int bufferSizeInFrames;

		@Override
		public int initialize() {
			engineAcceptsNativeBuffers = hasExternalNativeLibrary;
			engineNeedsFullBufferBeforeResuming = false;
			return openSLInitialize();
		}

		@Override
		public int create(int sampleRate, int bufferSizeInFrames, int minBufferSizeInFrames) {
			this.bufferSizeInFrames = bufferSizeInFrames;
			final int ret = openSLCreate(sampleRate, bufferSizeInFrames, minBufferSizeInFrames);
			setVolume();
			return ret;
		}

		@Override
		public int recreateIfNeeded(int sampleRate, int bufferSizeInFrames) {
			return 0;
		}

		@Override
		public int play() {
			return openSLPlay();
		}

		@Override
		public int pause() {
			return openSLPause();
		}

		@Override
		public int stopAndFlush() {
			return openSLStopAndFlush();
		}

		@Override
		public void release() {
			openSLRelease();
		}

		@Override
		public void terminate() {
			openSLTerminate();
		}

		@Override
		public void setVolume() {
			openSLSetVolumeInMillibels((gain <= 0.0001f) ? MediaContext.SL_MILLIBEL_MIN : ((gain >= 1.0f) ? 0 : (int)(2000.0 * Math.log(gain))));
		}

		@Override
		public int getActualBufferSizeInFrames() {
			return bufferSizeInFrames;
		}

		@Override
		public int getHeadPositionInFrames() {
			return openSLGetHeadPositionInFrames();
		}

		@Override
		public int getFillThresholdInFrames(int bufferSizeInFrames) {
			switch ((bufferConfig & Player.FILL_THRESHOLD_MASK)) {
			case Player.FILL_THRESHOLD_25:
				return (bufferSizeInFrames >> 2);
			case Player.FILL_THRESHOLD_50:
				return (bufferSizeInFrames >> 1);
			case Player.FILL_THRESHOLD_75:
				return ((bufferSizeInFrames * 3) >> 2);
			}
			return bufferSizeInFrames;
		}

		@Override
		public int commitFinalFrames(int emptyFrames) {
			return (int)openSLWriteNative(0, 0, 0);
		}

		@Override
		public int writeNative(long nativeObj, MediaCodecPlayer.OutputBuffer buffer) {
			long dstSrcRet;
			if ((dstSrcRet = openSLWriteNative(nativeObj, buffer.offsetInBytes, buffer.remainingBytes >> srcChannelCount)) > 0) {
				//dstFramesUsed -> low
				//srcFramesUsed -> high
				final int srcBytesUsed = (int)(dstSrcRet >>> 32) << srcChannelCount;
				buffer.remainingBytes -= srcBytesUsed;
				buffer.offsetInBytes += srcBytesUsed;
			}
			return (int)dstSrcRet;
		}

		@Override
		public int write(MediaCodecPlayer.OutputBuffer buffer, int emptyFrames) {
			long dstSrcRet;
			if ((dstSrcRet = openSLWrite(buffer.byteArray, buffer.byteBuffer, buffer.offsetInBytes, buffer.remainingBytes >> srcChannelCount, MediaCodecPlayer.needsSwap)) > 0) {
				//dstFramesUsed -> low
				//srcFramesUsed -> high
				final int srcBytesUsed = (int)(dstSrcRet >>> 32) << srcChannelCount;
				buffer.remainingBytes -= srcBytesUsed;
				buffer.offsetInBytes += srcBytesUsed;
			}
			return (int)dstSrcRet;
		}
	}

	private MediaContext() {
	}

	private static void updateNativeSampleRate() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			try {
				final AudioManager am = (AudioManager)Player.theApplication.getSystemService(Context.AUDIO_SERVICE);
				final int sampleRate = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
				if (sampleRate > 0) {
					nativeSampleRate = sampleRate;
					return;
				}
			} catch (Throwable ex) {
				//just ignore
			}
		}
		try {
			final int sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
			if (sampleRate > 0) {
				nativeSampleRate = sampleRate;
				return;
			}
		} catch (Throwable ex) {
			//just ignore
		}
		nativeSampleRate = 0;
	}

	static int getDstSampleRate(int srcSampleRate) {
		if (nativeSampleRate <= 0 || srcSampleRate == nativeSampleRate)
			return srcSampleRate; //no conversion (simply use srcSampleRate as dstSampleRate)

		//downsampling is only performed from 48000 Hz to 44100 Hz because we are not
		//applying any filters
		if ((srcSampleRate == 48000 && nativeSampleRate == 44100) ||
			(srcSampleRate >= 8000 && nativeSampleRate > srcSampleRate))
			return nativeSampleRate;

		return srcSampleRate; //no conversion (simply use srcSampleRate as dstSampleRate)
	}

	private static void updateNativeSrcAndReset(MediaCodecPlayer player) {
		if (player == null)
			return;
		updateSrcParams(player.getSrcSampleRate(), srcChannelCount = player.getChannelCount(), 1);
	}

	private static void updateNativeSrc(MediaCodecPlayer player) {
		if (player == null)
			return;
		updateSrcParams(player.getSrcSampleRate(), srcChannelCount = player.getChannelCount(), 0);
	}

	@SuppressWarnings("deprecation")
	private static long getBufferSizeInFrames(int dstSampleRate) {
		if (dstSampleRate < 4000)
			dstSampleRate = 44100;

		int framesPerBuffer = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			try {
				if (nativeSampleRate > 0) {
					final AudioManager am = (AudioManager)Player.theApplication.getSystemService(Context.AUDIO_SERVICE);
					framesPerBuffer = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
					framesPerBuffer = (int)Math.ceil((double)framesPerBuffer * (double)dstSampleRate / (double)nativeSampleRate);
				}
			} catch (Throwable ex) {
				//just ignore
				framesPerBuffer = 0;
			}
		}

		if (framesPerBuffer <= 0) {
			framesPerBuffer = AudioTrack.getMinBufferSize(dstSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) >> 2;
			if (framesPerBuffer <= 0)
				framesPerBuffer = 1024;
		}

		int singleBufferSizeInFrames = framesPerBuffer;
		while (singleBufferSizeInFrames < ((2 * MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING) / 3))
			singleBufferSizeInFrames += framesPerBuffer;

		int bufferSizeInFrames;
		switch ((bufferConfig & Player.BUFFER_SIZE_MASK)) {
		case Player.BUFFER_SIZE_500MS:
			bufferSizeInFrames = 48000 / 2;
			break;
		case Player.BUFFER_SIZE_1500MS:
			bufferSizeInFrames = (48000 * 3) / 2;
			break;
		case Player.BUFFER_SIZE_2000MS:
			bufferSizeInFrames = 48000 * 2;
			break;
		case Player.BUFFER_SIZE_2500MS:
			bufferSizeInFrames = (48000 * 5) / 2;
			break;
		default:
			bufferSizeInFrames = 48000;
			break;
		}

		int minBufferSizeInFrames = singleBufferSizeInFrames;
		//while (minBufferSizeInFrames < 4096)
		//	minBufferSizeInFrames += singleBufferSizeInFrames;
		while (minBufferSizeInFrames > 20000)
			minBufferSizeInFrames >>>= 1;

		if (bufferSizeInFrames <= (minBufferSizeInFrames << 1)) {
			//we need at least 2 buffers + 1 extra buffer (refer to OpenSL.h)
			bufferSizeInFrames = minBufferSizeInFrames * 3;
		} else {
			//otherwise, make sure it is a multiple of our minimal buffer size and add 1 extra buffer (refer to OpenSL.h)
			bufferSizeInFrames = (1 + ((bufferSizeInFrames + minBufferSizeInFrames - 1) / minBufferSizeInFrames)) * minBufferSizeInFrames;
		}

		return ((((long)minBufferSizeInFrames) << 32) | (long)bufferSizeInFrames);
	}

	private static void checkEngineResult(int result) {
		if (result == 0)
			return;
		if (result < 0)
			result = -result;
		if (result == AUDIO_TRACK_OUT_OF_MEMORY)
			throw new OutOfMemoryError();
		throw new IllegalStateException("The engine returned " + result);
	}

	private static void processEffectsAction() {
		if (effectsMessage == null)
			return;

		switch (effectsMessage.what) {
		case MSG_EQUALIZER_ENABLE:
			enableEqualizer(effectsMessage.arg1);
			break;
		case MSG_EQUALIZER_BAND_LEVEL:
			setEqualizerBandLevel(effectsMessage.arg1, effectsMessage.arg2);
			break;
		case MSG_EQUALIZER_BAND_LEVELS:
			setEqualizerBandLevels((short[])effectsMessage.obj);
			break;
		case MSG_BASSBOOST_ENABLE:
			enableBassBoost(effectsMessage.arg1);
			break;
		case MSG_BASSBOOST_STRENGTH:
			setBassBoostStrength(effectsMessage.arg1);
			break;
		case MSG_VIRTUALIZER_ENABLE:
			enableVirtualizer(effectsMessage.arg1);
			break;
		case MSG_VIRTUALIZER_STRENGTH:
			setVirtualizerStrength(effectsMessage.arg1);
			break;
		}

		effectsMessage.obj = null;
		effectsMessage = null;
	}

	@Override
	public void run() {
		thread.setPriority(Thread.MAX_PRIORITY);

		PowerManager.WakeLock wakeLock;
		MediaCodecPlayer currentPlayer = null, nextPlayer = null, sourcePlayer = null;
		MediaCodecPlayer.OutputBuffer outputBuffer = new MediaCodecPlayer.OutputBuffer();
		outputBuffer.index = -1;
		int dstSampleRate = 0, sleepTime = 0, lastHeadPositionInFrames = 0, bufferSizeInFrames = 0, fillThresholdInFrames = 0;
		long framesWritten = 0, framesPlayed = 0, nextFramesWritten = 0;
		boolean bufferConfigChanged = false;

		updateNativeSampleRate();

		synchronized (engineSync) {
			initializationError = (engine.initialize() != 0);
		}

		if (initializationError) {
			requestedAction = ACTION_NONE;
			synchronized (notification) {
				notification.notify();
			}
			return;
		}

		wakeLock = ((PowerManager)Player.theApplication.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MediaContext WakeLock");
		wakeLock.setReferenceCounted(false);

		requestedAction = ACTION_NONE;

		synchronized (notification) {
			notification.notify();
		}

		boolean paused = true, playPending = false;
		int framesWrittenBeforePlaying = 0;
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
						MediaCodecPlayer seekPendingPlayer = null;
						requestSucceeded = false;
						try {
							//**** before calling notification.notify() we can safely assume the player thread
							//is halted while these actions take place, therefore the player objects are not
							//being used during this period
							switch (requestedAction) {
							case ACTION_EFFECTS:
								processEffectsAction();
								break;
							case ACTION_PLAY:
								synchronized (engineSync) {
									checkEngineResult(engine.stopAndFlush());
								}
								outputBuffer.release();
								currentPlayer = playerRequestingAction;
								nextPlayer = null;
								sourcePlayer = currentPlayer;
								currentPlayer.resetDecoderIfOutputAlreadyUsed();
								framesWritten = currentPlayer.getCurrentPositionInFrames();
								framesPlayed = currentPlayer.getCurrentPositionInFrames();
								nextFramesWritten = 0;
								//some times, the native sample rate changes when the output
								//method changes (headset, speaker, HDMI, bluetooth...)
								updateNativeSampleRate();
								currentPlayer.updateDstSampleRate();
								if (nextPlayer != null)
									nextPlayer.updateDstSampleRate();
								if (dstSampleRate != currentPlayer.getDstSampleRate() || bufferConfigChanged) {
									bufferConfigChanged = false;
									dstSampleRate = currentPlayer.getDstSampleRate();
									final long temp = getBufferSizeInFrames(dstSampleRate);
									bufferSizeInFrames = (int)temp;
									final int minBufferSizeInFrames = (int)(temp >>> 32);
									fillThresholdInFrames = engine.getFillThresholdInFrames(bufferSizeInFrames);
									sleepTime = (minBufferSizeInFrames * 1000) / dstSampleRate;
									if (sleepTime > 30) sleepTime = 30;
									else if (sleepTime < 15) sleepTime = 15;
									synchronized (engineSync) {
										checkEngineResult(engine.create(dstSampleRate, bufferSizeInFrames, minBufferSizeInFrames));
									}
								} else {
									synchronized (engineSync) {
										checkEngineResult(engine.recreateIfNeeded(dstSampleRate, bufferSizeInFrames));
									}
								}
								bufferSizeInFrames = engine.getActualBufferSizeInFrames();
								playPending = true;
								framesWrittenBeforePlaying = 0;
								bufferingStart(currentPlayer);
								updateNativeSrcAndReset(currentPlayer);
								lastHeadPositionInFrames = engine.getHeadPositionInFrames();
								paused = false;
								requestSucceeded = true;
								wakeLock.acquire();
								break;
							case ACTION_PAUSE:
								if (playerRequestingAction == currentPlayer) {
									checkEngineResult(engine.pause());
									if (currentPlayer.isInternetStream()) {
										//"mini-reset" here
										synchronized (engineSync) {
											checkEngineResult(engine.stopAndFlush());
											checkEngineResult(engine.recreateIfNeeded(dstSampleRate, bufferSizeInFrames));
										}
										bufferSizeInFrames = engine.getActualBufferSizeInFrames();
										outputBuffer.release();
										framesWritten = 0;
										framesPlayed = 0;
										updateNativeSrcAndReset(currentPlayer);
										lastHeadPositionInFrames = engine.getHeadPositionInFrames();
									}
									paused = true;
									requestSucceeded = true;
									wakeLock.release();
								}
								break;
							case ACTION_RESUME:
								if (playerRequestingAction == currentPlayer) {
									if ((framesWritten - framesPlayed) < 512 || engineNeedsFullBufferBeforeResuming) {
										playPending = true;
										framesWrittenBeforePlaying = 0;
										bufferingStart(currentPlayer);
									} else {
										checkEngineResult(engine.play());
									}
									paused = false;
									requestSucceeded = true;
									wakeLock.acquire();
								}
								break;
							case ACTION_SEEK:
								if (currentPlayer != null && currentPlayer != playerRequestingAction)
									break;
								if (!paused)
									throw new IllegalStateException("trying to seek while not paused");
								synchronized (engineSync) {
									checkEngineResult(engine.stopAndFlush());
								}
								seekPendingPlayer = playerRequestingAction;
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
											updateNativeSrc(sourcePlayer);
										}
										nextPlayer = null;
										nextFramesWritten = 0;
									} else {
										nextPlayer = nextPlayerRequested;
										try {
											if (nextPlayer != null) {
												if (currentPlayer.isInternetStream() ||
													nextPlayer.isInternetStream() ||
													dstSampleRate != nextPlayer.getDstSampleRate()) {
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
									//releasing prevents clicks when changing tracks
									synchronized (engineSync) {
										engine.release();
										dstSampleRate = 0;
									}
									outputBuffer.release();
									paused = true;
									playPending = false;
									framesWrittenBeforePlaying = 0;
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
										updateNativeSrc(sourcePlayer);
									}
									nextPlayer = null;
									nextFramesWritten = 0;
								}
								requestSucceeded = true;
								break;
							case ACTION_UPDATE_BUFFER_CONFIG:
								bufferConfigChanged = true;
								break;
							case ACTION_ENABLE_EFFECTS_GAIN:
								enableAutomaticEffectsGain(1);
								break;
							case ACTION_DISABLE_EFFECTS_GAIN:
								enableAutomaticEffectsGain(0);
								break;
							}
						} catch (Throwable ex) {
							synchronized (engineSync) {
								engine.release();
								dstSampleRate = 0;
							}
							try {
								outputBuffer.release();
							} catch (Throwable ex2) {
								//just ignore
							}
							paused = true;
							playPending = false;
							framesWrittenBeforePlaying = 0;
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
						if (seekPendingPlayer != null) {
							try {
								outputBuffer.release();
								updateNativeSrcAndReset(seekPendingPlayer);
								if (sourcePlayer == seekPendingPlayer) {
									synchronized (engineSync) {
										checkEngineResult(engine.recreateIfNeeded(dstSampleRate, bufferSizeInFrames));
									}
									bufferSizeInFrames = engine.getActualBufferSizeInFrames();
								}
								lastHeadPositionInFrames = engine.getHeadPositionInFrames();
								if (nextPlayer != null) {
									try {
										nextPlayer.resetDecoderIfOutputAlreadyUsed();
									} catch (Throwable ex) {
										nextPlayer = null;
										handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(nextPlayer, ex)), SystemClock.uptimeMillis());
									}
									nextFramesWritten = 0;
								}
								framesWritten = seekPendingPlayer.doSeek(requestedSeekMS);
								framesPlayed = framesWritten;
								framesWrittenBeforePlaying = 0;
								handler.sendMessageAtTime(Message.obtain(handler, MSG_SEEKCOMPLETE, seekPendingPlayer), SystemClock.uptimeMillis());
							} catch (Throwable ex) {
								synchronized (engineSync) {
									engine.release();
									dstSampleRate = 0;
								}
								try {
									outputBuffer.release();
								} catch (Throwable ex2) {
									//just ignore
								}
								paused = true;
								playPending = false;
								framesWrittenBeforePlaying = 0;
								currentPlayer = null;
								nextPlayer = null;
								sourcePlayer = null;
								framesWritten = 0;
								framesPlayed = 0;
								nextFramesWritten = 0;
								wakeLock.release();
								handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(seekPendingPlayer, ex)), SystemClock.uptimeMillis());
								continue;
							}
						}
					}
				}
			}

			try {
				if (paused)
					continue;

				final int currentHeadPositionInFrames = engine.getHeadPositionInFrames();
				framesPlayed += (currentHeadPositionInFrames - lastHeadPositionInFrames);
				lastHeadPositionInFrames = currentHeadPositionInFrames;

				currentPlayer.setCurrentPosition((int)((framesPlayed * 1000L) / (long)dstSampleRate));

				if (outputBuffer.index < 0) {
					sourcePlayer.nextOutputBuffer(outputBuffer);
					if (outputBuffer.index < 0) {
						boolean sleepNow = true;
						if (outputBuffer.streamOver && nextPlayer == null) {
							//when the input stream is over and we do not have a nextPlayer to produce
							//new samples, we need to tell OpenSL to flush any pending data it had stored
							//if we were using AudioTrack, then we need to write 0 samples in order to
							//fill up the buffer, otherwise, if it was not playing, it would never start
							final int framesWrittenThisTime = engine.commitFinalFrames(bufferSizeInFrames - (int)(framesWritten - framesPlayed));
							if (framesWrittenThisTime < 0) {
								throw new IOException("engine.write() returned " + framesWrittenThisTime);
							} else if (framesWrittenThisTime > 0) {
								sleepNow = false;
							}
						}

						if (sleepNow) {
							try {
								synchronized (threadNotification) {
									if (requestedAction == ACTION_NONE)
										threadNotification.wait(sleepTime);
								}
							} catch (Throwable ex) {
								//just ignore
							}
						}
					}
				}

				if (outputBuffer.remainingBytes > 0) {
					final int framesWrittenThisTime;
					if (sourcePlayer.isNativeMediaCodec())
						framesWrittenThisTime = engine.writeNative(sourcePlayer.getNativeObj(), outputBuffer);
					else
						framesWrittenThisTime = engine.write(outputBuffer, bufferSizeInFrames - (int)(framesWritten - framesPlayed));
					if (framesWrittenThisTime < 0) {
						throw new IOException("engine.write() returned " + framesWrittenThisTime);
					} else if (framesWrittenThisTime == 0) {
						if (playPending) {
							//we have just filled the buffer, time to start playing
							playPending = false;
							checkEngineResult(engine.play());
							bufferingEnd(sourcePlayer);
						}
						//the buffer was too full, let's use this time to start filling up the
						//next input buffers before waiting some time (discount the time spent
						//inside fillInputBuffers())
						int actualSleepTime = (int)SystemClock.uptimeMillis();
						sourcePlayer.fillInputBuffers();
						try {
							actualSleepTime = sleepTime - ((int)SystemClock.uptimeMillis() - actualSleepTime);
							if (actualSleepTime > 0) {
								//wait(0) will block the thread until someone
								//calls notify() or notifyAll()
								synchronized (threadNotification) {
									if (requestedAction == ACTION_NONE)
										threadNotification.wait(actualSleepTime);
								}
							}
						} catch (Throwable ex) {
							//just ignore
						}
						continue;
					} else {
						if (sourcePlayer == currentPlayer) {
							framesWritten += framesWrittenThisTime;
							if (playPending) {
								framesWrittenBeforePlaying += framesWrittenThisTime;
								if (framesWrittenBeforePlaying >= fillThresholdInFrames) {
									//we have just filled the buffer, time to start playing
									playPending = false;
									checkEngineResult(engine.play());
									bufferingEnd(sourcePlayer);
								}
							}
						} else {
							nextFramesWritten += framesWrittenThisTime;
						}
					}
				} else if (playPending && currentPlayer.isOutputOver()) {
					//the song ended before we had a chance to start playing before, so do it now!
					playPending = false;
					checkEngineResult(engine.play());
					bufferingEnd(sourcePlayer);
				}

				if (outputBuffer.remainingBytes <= 0) {
					outputBuffer.release();
					if (outputBuffer.streamOver && sourcePlayer == currentPlayer) {
						//from now on, we will start outputting audio from the next player (if any)
						if (nextPlayer != null) {
							sourcePlayer = nextPlayer;
							updateNativeSrc(sourcePlayer);
						}
					}
				}

				if (framesPlayed >= framesWritten) {
					if (currentPlayer.isOutputOver()) {
						//we are done with this player!
						currentPlayer.setCurrentPosition(currentPlayer.getDuration());
						if (nextPlayer == null) {
							//there is nothing else to do!
							synchronized (engineSync) {
								engine.release();
								dstSampleRate = 0;
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
						updateNativeSrc(sourcePlayer);
					} else if (framesWritten != 0) {
						//underrun!!!
						checkEngineResult(engine.pause());
						playPending = true;
						framesWrittenBeforePlaying = 0;
						bufferingStart(currentPlayer);
						//give the decoder some time to decode something
						try {
							synchronized (threadNotification) {
								if (requestedAction == ACTION_NONE)
									threadNotification.wait(sleepTime);
							}
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
					synchronized (engineSync) {
						engine.release();
						dstSampleRate = 0;
					}
					paused = true;
					playPending = false;
					currentPlayer = null;
					wakeLock.release();
				}
				handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(sourcePlayer, ex)), SystemClock.uptimeMillis());
				nextPlayer = null;
				nextFramesWritten = 0;
				sourcePlayer = currentPlayer;
				updateNativeSrc(sourcePlayer);
			}
		}

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
		case MSG_BUFFERINGSTART:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).onInfo(MediaPlayerBase.INFO_BUFFERING_START, 0, null);
			break;
		case MSG_BUFFERINGEND:
			if (msg.obj instanceof MediaCodecPlayer)
				((MediaCodecPlayer)msg.obj).onInfo(MediaPlayerBase.INFO_BUFFERING_END, 0, null);
			break;
		}
		return true;
	}

	public static void _initialize() {
		if (theMediaContext != null)
			return;

		theMediaContext = new MediaContext();
		engine = (useOpenSLEngine ? new OpenSLEngine() : new AudioTrackEngine());

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
		synchronized (engineSync) {
			if (engine != null) {
				engine.terminate();
				engine = null;
			}
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

	static void bufferingStart(MediaCodecPlayer player) {
		if (!alive || handler == null)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_BUFFERINGSTART, player), SystemClock.uptimeMillis());
	}

	static void bufferingEnd(MediaCodecPlayer player) {
		if (!alive || handler == null)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_BUFFERINGEND, player), SystemClock.uptimeMillis());
	}

	static void setVolume(float gain) {
		synchronized (engineSync) {
			MediaContext.gain = gain;
			if (engine != null)
				engine.setVolume();
		}
	}

	private static void sendEffectsMessage(Message message) {
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			effectsMessage = message;
			requestedAction = ACTION_EFFECTS;
			threadNotification.notify();
		}
		synchronized (notification) {
			if (requestedAction == ACTION_EFFECTS) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
	}

	static void _enableEqualizer(int enabled) {
		if (!alive)
			enableEqualizer(enabled);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_EQUALIZER_ENABLE, enabled, 0));
	}

	static void _setEqualizerBandLevel(int band, int level) {
		if (!alive)
			setEqualizerBandLevel(band, level);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_EQUALIZER_BAND_LEVEL, band, level));
	}

	static void _setEqualizerBandLevels(short[] levels) {
		if (!alive)
			setEqualizerBandLevels(levels);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_EQUALIZER_BAND_LEVELS, levels));
	}

	static void _enableBassBoost(int enabled) {
		if (!alive)
			enableBassBoost(enabled);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_BASSBOOST_ENABLE, enabled, 0));
	}

	static void _setBassBoostStrength(int strength) {
		if (!alive)
			setBassBoostStrength(strength);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_BASSBOOST_STRENGTH, strength, 0));
	}

	static void _enableVirtualizer(int enabled) {
		if (!alive)
			enableVirtualizer(enabled);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_VIRTUALIZER_ENABLE, enabled, 0));
	}

	static void _setVirtualizerStrength(int strength) {
		if (!alive)
			setVirtualizerStrength(strength);
		else
			sendEffectsMessage(Message.obtain(handler, MSG_VIRTUALIZER_STRENGTH, strength, 0));
	}

	public static MediaPlayerBase createMediaPlayer() {
		return new MediaCodecPlayer();
	}

	public static int getFeatures() {
		return (getProcessorFeatures() |
			(engineAcceptsNativeBuffers ? Player.FEATURE_DECODING_NATIVE : 0) |
			(MediaCodecPlayer.isDirect ? Player.FEATURE_DECODING_DIRECT : 0));
	}

	public static int getBufferConfig() {
		return bufferConfig;
	}

	public static void _setBufferConfig(int bufferConfig) {
		MediaContext.bufferConfig = bufferConfig;

		if (!alive)
			return;

		waitToReceiveAction = true;
		synchronized (threadNotification) {
			requestedAction = ACTION_UPDATE_BUFFER_CONFIG;
			threadNotification.notify();
		}
		synchronized (notification) {
			if (requestedAction == ACTION_UPDATE_BUFFER_CONFIG) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
	}

	public static void _enableAutomaticEffectsGain(int enabled) {
		if (!alive) {
			enableAutomaticEffectsGain(enabled);
			return;
		}

		waitToReceiveAction = true;
		synchronized (threadNotification) {
			requestedAction = ((enabled == 0) ? ACTION_DISABLE_EFFECTS_GAIN : ACTION_ENABLE_EFFECTS_GAIN);
			threadNotification.notify();
		}
		synchronized (notification) {
			if (requestedAction == ACTION_DISABLE_EFFECTS_GAIN || requestedAction == ACTION_ENABLE_EFFECTS_GAIN) {
				try {
					notification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
	}
}
