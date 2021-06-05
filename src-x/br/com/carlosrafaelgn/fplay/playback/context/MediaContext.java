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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;

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
	private static final int ACTION_START_VISUALIZER = 0x000B;
	private static final int ACTION_STOP_VISUALIZER = 0x000C;
	private static final int ACTION_ENABLE_RESAMPLING = 0x000D;
	private static final int ACTION_DISABLE_RESAMPLING = 0x000E;
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
	private static final Object engineSync = new Object();
	private static volatile boolean alive, waitToReceiveAction, requestSucceeded, initializationError, resamplingEnabled;
	private static volatile int requestedAction, requestedSeekMS;
	private static Message effectsMessage;
	private static int bufferConfig, nativeSampleRate, srcChannelCount, srcSampleRate;
	private static float gain = 1.0f;
	private static Handler handler;
	private static Thread thread;
	private static volatile MediaCodecPlayer playerRequestingAction, nextPlayerRequested, currentPlayerForReference;
	private static MediaContext theMediaContext;
	private static Engine engine;
	public static boolean useOpenSLEngine;
	final static boolean externalNativeLibraryAvailable;
	static boolean engineBlocks;

	static {
		System.loadLibrary("MediaContextJni");
		externalNativeLibraryAvailable = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) && (mediaCodecLoadExternalLibrary() == 0));
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
	private static native void getEqualizerFrequencyResponse(int bassBoostStrength, short[] levels, int frequencyCount, double[] frequencies, double[] gains);

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
	static native long mediaCodecSeek(long nativeObj, int msec, int totalMsec);
	static native void mediaCodecReleaseOutputBuffer(long nativeObj);
	static native void mediaCodecRelease(long nativeObj);
	static native int mediaCodecLoadExternalLibrary();

	private static native void audioTrackInitialize();
	private static native void audioTrackCreate(int dstSampleRate);
	private static native long audioTrackProcessNativeEffects(long nativeObj, int offsetInBytes, int sizeInFrames, ByteBuffer dstBuffer);
	private static native long audioTrackProcessEffects(byte[] srcArray, ByteBuffer srcBuffer, int offsetInBytes, int sizeInFrames, int needsSwap, byte[] dstArray, ByteBuffer dstBuffer);

	private static native int openSLInitialize();
	private static native int openSLCreate(int dstSampleRate, int bufferCount, int singleBufferSizeInFrames);
	private static native int openSLPlay();
	private static native int openSLPause();
	private static native int openSLStopAndFlush();
	private static native void openSLRelease();
	private static native void openSLTerminate();
	private static native void openSLSetVolumeInMillibels(int volumeInMillibels);
	private static native int openSLGetHeadPositionInFrames();
	private static native long openSLWriteNative(long nativeObj, int offsetInBytes, int sizeInFrames);
	private static native long openSLWrite(byte[] array, ByteBuffer buffer, int offsetInBytes, int sizeInFrames, int needsSwap);

	private static native int visualizerStart(int bufferSizeInFrames, int createIfNotCreated);
	private static native void visualizerStop();
	private static native void visualizerZeroOut();
	private static native void visualizerGetWaveform(byte[] waveform, int headPositionInFrames);

	private static abstract class Engine {
		static int getFramesPerBuffer(int dstSampleRate) {
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

			return framesPerBuffer;
		}

		public abstract int initialize();
		public abstract int create(int dstSampleRate);
		public abstract int recreateIfNeeded();
		public abstract int play();
		public abstract int pause();
		public abstract int stopAndFlush();
		public abstract void release();
		public abstract void terminate();
		public abstract void setVolume();
		public abstract void pauseFromOtherThreadIfBlocking();
		public abstract int getCurrentDstSampleRate();
		public abstract int getActualBufferSizeInFrames();
		public abstract int getSingleBufferSizeInFrames();
		public abstract int getHeadPositionInFrames();
		public abstract int getFillThresholdInFrames();
		public abstract void getVisualizerWaveform(byte[] waveform);
		public abstract int commitFinalFrames(int emptyFrames);
		public abstract int write(MediaCodecPlayer.OutputBuffer buffer, int emptyFrames);
	}

	private static final class AudioTrackEngine extends Engine {
		private static final class QueryableAudioTrack extends AudioTrack {
			@TargetApi(Build.VERSION_CODES.LOLLIPOP)
			public QueryableAudioTrack(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes, int mode) {
				super(attributes, format, bufferSizeInBytes, mode, AudioManager.AUDIO_SESSION_ID_GENERATE);
			}

			public QueryableAudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode) {
				super(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);
			}

			public int getActualBufferSizeInFrames() {
				return getNativeFrameCount();
			}
		}

		private QueryableAudioTrack audioTrack;
		private byte[] tempDstArray;
		private ByteBuffer tempDstBuffer;
		private boolean okToQuitIfFull;
		private int pendingOffsetInBytes, pendingDstFrames, singleBufferSizeInFrames, currentDstSampleRate;

		@Override
		public int initialize() {
			engineBlocks = true;
			//MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 3:
			//* 2 = short
			//* 2 = stereo
			//* 2 = extra space for resampling
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				tempDstBuffer = ByteBuffer.allocateDirect(MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 3);
				tempDstBuffer.order(ByteOrder.nativeOrder());
			} else {
				tempDstArray = new byte[MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 3];
			}

			audioTrackInitialize();
			return 0;
		}

		@Override
		public int create(int dstSampleRate) {
			release();

			if (dstSampleRate < 4000)
				dstSampleRate = 44100;

			singleBufferSizeInFrames = getFramesPerBuffer(dstSampleRate);

			//to be sure that singleBufferSizeInFrames still will be a valid multiple after
			//dividing by 2, we need to make sure singleBufferSizeInFrames is an even number
			while (((singleBufferSizeInFrames & 1) == 0) && singleBufferSizeInFrames >= (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 8))
				singleBufferSizeInFrames >>= 1;

			int bufferSizeInFrames;
			switch ((bufferConfig & Player.BUFFER_SIZE_MASK)) {
			case Player.BUFFER_SIZE_500MS:
				bufferSizeInFrames = dstSampleRate >> 1;
				break;
			case Player.BUFFER_SIZE_1500MS:
				bufferSizeInFrames = (dstSampleRate * 3) >> 1;
				break;
			case Player.BUFFER_SIZE_2000MS:
				bufferSizeInFrames = dstSampleRate << 1;
				break;
			case Player.BUFFER_SIZE_2500MS:
				bufferSizeInFrames = (dstSampleRate * 5) >> 1;
				break;
			default:
				bufferSizeInFrames = dstSampleRate;
				break;
			}

			final int minBufferSizeInFrames = AudioTrack.getMinBufferSize(dstSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) >> 2;
			if (bufferSizeInFrames < minBufferSizeInFrames)
				bufferSizeInFrames = minBufferSizeInFrames;

			//round, but keep roundedBufferSizeInFrames not too above bufferSizeInFrames
			//(also, make sure we have at least 2 buffers)
			final int bufferCount = bufferSizeInFrames / singleBufferSizeInFrames;
			final int roundedBufferSizeInFrames = Math.max(bufferCount, 2) * singleBufferSizeInFrames;
			bufferSizeInFrames = ((roundedBufferSizeInFrames >= bufferSizeInFrames) ?
				roundedBufferSizeInFrames :
				(((bufferSizeInFrames + (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 4)) / singleBufferSizeInFrames) * singleBufferSizeInFrames));

			currentDstSampleRate = dstSampleRate;
			audioTrackCreate(dstSampleRate);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				audioTrack = new QueryableAudioTrack(
					new AudioAttributes.Builder()
						.setLegacyStreamType(AudioManager.STREAM_MUSIC)
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.build(),
					new AudioFormat.Builder()
						.setSampleRate(dstSampleRate)
						.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
						.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
						.build(),
					bufferSizeInFrames << 2,
					AudioTrack.MODE_STREAM);
			} else {
				audioTrack = new QueryableAudioTrack(
					AudioManager.STREAM_MUSIC,
					dstSampleRate,
					AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT,
					bufferSizeInFrames << 2,
					AudioTrack.MODE_STREAM);
			}
			try {
				//apparently, there are times when audioTrack creation fails, but the constructor
				//above does not throw any exceptions (only getNativeFrameCount() throws exceptions
				//in such cases)
				visualizerStart(getActualBufferSizeInFrames(), 0);
			} catch (Throwable ex) {
				return AUDIO_TRACK_OUT_OF_MEMORY;
			}
			setVolume();
			return 0;
		}

		@Override
		public int recreateIfNeeded() {
			return (audioTrack != null ? 0 : create(currentDstSampleRate));
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
			return 0;
		}

		@Override
		public void release() {
			okToQuitIfFull = false;
			pendingOffsetInBytes = 0;
			pendingDstFrames = 0;
			if (audioTrack != null) {
				audioTrack.release();
				audioTrack = null;
				System.gc();
			}
		}

		@Override
		public void terminate() {
			release();
			tempDstArray = null;
			tempDstBuffer = null;
		}

		@Override
		public void setVolume() {
			if (audioTrack != null)
				audioTrack.setStereoVolume(gain, gain);
		}

		@Override
		public void pauseFromOtherThreadIfBlocking() {
			if (audioTrack != null)
				audioTrack.pause();
		}

		@Override
		public int getCurrentDstSampleRate() {
			return currentDstSampleRate;
		}

		@Override
		public int getActualBufferSizeInFrames() {
			return (audioTrack != null ? audioTrack.getActualBufferSizeInFrames() : 0);
		}

		@Override
		public int getSingleBufferSizeInFrames() {
			return singleBufferSizeInFrames;
		}

		@Override
		public int getHeadPositionInFrames() {
			return (audioTrack != null ? audioTrack.getPlaybackHeadPosition() : 0);
		}

		@Override
		public int getFillThresholdInFrames() {
			//the AudioTrack must only start playing when it returns 0
			return 0x7fffffff;
		}

		@Override
		public void getVisualizerWaveform(byte[] waveform) {
			if (audioTrack == null)
				Arrays.fill(waveform, (byte)0x80);
			else
				visualizerGetWaveform(waveform, audioTrack.getPlaybackHeadPosition());
		}

		@Override
		public int commitFinalFrames(int emptyFrames) {
			if (pendingDstFrames > 0)
				return write(null, emptyFrames);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				if (tempDstArray == null)
					tempDstArray = new byte[1024];
			}
			if (emptyFrames > 256)
				emptyFrames = 256;
			else if (emptyFrames <= 0)
				return 0;
			final int sizeInBytes = emptyFrames << 2;
			Arrays.fill(tempDstArray, 0, sizeInBytes, (byte)0);
			final int ret = audioTrack.write(tempDstArray, 0, sizeInBytes);
			if (ret < 0)
				return ret;
			//ret was in bytes, but we need to return frames
			return (ret >> 2);
		}

		@Override
		public int write(MediaCodecPlayer.OutputBuffer buffer, int emptyFrames) {
			//https://android.googlesource.com/platform/frameworks/av/+/master/media/libmedia/AudioTrack.cpp
			//after A LOT of testing, and after reading AudioTrack's native source
			//(method write()), I realized AudioTrack controls its buffers internally,
			//filling up several small buffers of size singleBufferSizeInFrames
			//therefore, we do not need to have such kind of control here, on the Java side!!!
			//
			//side note: even audioTrack.getPlaybackHeadPosition() increments in multiples of
			//singleBufferSizeInFrames!!!
			if (pendingDstFrames <= 0) {
				int sizeInFrames = buffer.remainingBytes >> srcChannelCount;

				if (sizeInFrames == 0)
					return 0;

				//we cannot let audioTrack block for too long (should it actually block)
				while (sizeInFrames > MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING)
					sizeInFrames >>= 1;

				final MediaCodecPlayer player = buffer.player;
				long dstSrcRet;
				if ((dstSrcRet =
						(player.isNativeMediaCodec() ?
							audioTrackProcessNativeEffects(player.getNativeObj(), buffer.offsetInBytes, sizeInFrames, tempDstBuffer) :
							audioTrackProcessEffects(buffer.byteArray, buffer.byteBuffer, buffer.offsetInBytes, sizeInFrames, MediaCodecPlayer.needsSwap, tempDstArray, tempDstBuffer)
						)) < 0)
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
			int sizeInBytes = pendingDstFrames << 2;

			int ret;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				tempDstBuffer.limit(pendingOffsetInBytes + sizeInBytes);
				tempDstBuffer.position(pendingOffsetInBytes);
				ret = audioTrack.write(tempDstBuffer, sizeInBytes, AudioTrack.WRITE_NON_BLOCKING);
			} else {
				//*** NEVER TRY TO RETURN 0 HERE, MANUALLY CONTROLLING WHETHER THE AUDIOTRACK IS FULL,
				//BEFORE MAKING SURE WE HAVE WRITTEN UP TO/PAST THE END OF IT FIRST!!!
				//*** THERE IS A BUG IN AUDIOTRACK, AND IT ONLY STARTS PLAYING IF SAMPLES ARE WRITTEN
				//UP TO/PAST THE END OF IT!
				if (okToQuitIfFull && pendingDstFrames > emptyFrames) {
					//I decided to stop returning 0 here, and to write any amount of bytes,
					//because of scenarios when the audiotrack is not actually playing (undetected
					//underrun situations...)

					//if the buffer appears to be full, instead of simply returning 0 at all times,
					//let's try to write something, even if not our whole buffer
					//if (emptyFrames <= (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING >> 1))
					//	return 0;

					sizeInBytes = emptyFrames << 2;
				}

				ret = audioTrack.write(tempDstArray, pendingOffsetInBytes, sizeInBytes);
			}
			if (ret <= 0) {
				if (ret < 0)
					return ret;
				okToQuitIfFull = true;
			}
			pendingOffsetInBytes += ret;
			//ret was in bytes, but we need to return frames
			ret >>= 2;
			pendingDstFrames -= ret;
			return ret;
		}
	}

	private static final class OpenSLEngine extends Engine {
		private int bufferSizeInFrames, singleBufferSizeInFrames, currentDstSampleRate;

		@Override
		public int initialize() {
			engineBlocks = false;
			return openSLInitialize();
		}

		@Override
		public int create(int dstSampleRate) {
			if (dstSampleRate < 4000)
				dstSampleRate = 44100;

			int framesPerBuffer = getFramesPerBuffer(dstSampleRate);
			//make sure framesPerBuffer is even, so singleBufferSizeInFrames will also be even
			if ((framesPerBuffer & 1) != 0)
				framesPerBuffer <<= 1;

			singleBufferSizeInFrames = framesPerBuffer;
			while (singleBufferSizeInFrames < ((4 * MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING) / 5))
				singleBufferSizeInFrames += framesPerBuffer;

			//to be sure that singleBufferSizeInFrames still will be a valid multiple after
			//dividing by 2, we need to make sure singleBufferSizeInFrames is an even number
			//(this limits the maximum size of a single buffer as an attempt to make the
			//visualizer not too laggy)
			while (((singleBufferSizeInFrames & 1) == 0) && singleBufferSizeInFrames > ((MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 3) / 2))
				singleBufferSizeInFrames >>= 1;

			switch ((bufferConfig & Player.BUFFER_SIZE_MASK)) {
			case Player.BUFFER_SIZE_500MS:
				bufferSizeInFrames = dstSampleRate >> 1;
				break;
			case Player.BUFFER_SIZE_1500MS:
				bufferSizeInFrames = (dstSampleRate * 3) >> 1;
				break;
			case Player.BUFFER_SIZE_2000MS:
				bufferSizeInFrames = dstSampleRate << 1;
				break;
			case Player.BUFFER_SIZE_2500MS:
				bufferSizeInFrames = (dstSampleRate * 5) >> 1;
				break;
			default:
				bufferSizeInFrames = dstSampleRate;
				break;
			}

			final int bufferCount;
			if (bufferSizeInFrames <= (singleBufferSizeInFrames << 1)) {
				//we need at least 2 buffers + 1 extra buffer (refer to OpenSL.h)
				bufferCount = 3;
			} else {
				//otherwise, make sure it is a multiple of our minimal buffer size and add 1 extra buffer (refer to OpenSL.h)
				bufferCount = 1 + (bufferSizeInFrames / singleBufferSizeInFrames);
			}
			bufferSizeInFrames = bufferCount * singleBufferSizeInFrames;

			currentDstSampleRate = dstSampleRate;
			final int ret = openSLCreate(dstSampleRate, bufferCount, singleBufferSizeInFrames);
			visualizerStart(bufferSizeInFrames, 0);
			setVolume();
			return ret;
		}

		@Override
		public int recreateIfNeeded() {
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
		public void pauseFromOtherThreadIfBlocking() {
		}

		@Override
		public int getCurrentDstSampleRate() {
			return currentDstSampleRate;
		}

		@Override
		public int getActualBufferSizeInFrames() {
			return bufferSizeInFrames;
		}

		@Override
		public int getSingleBufferSizeInFrames() {
			return singleBufferSizeInFrames;
		}

		@Override
		public int getHeadPositionInFrames() {
			return openSLGetHeadPositionInFrames();
		}

		@Override
		public int getFillThresholdInFrames() {
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
		public void getVisualizerWaveform(byte[] waveform) {
			visualizerGetWaveform(waveform, openSLGetHeadPositionInFrames());
		}

		@Override
		public int commitFinalFrames(int emptyFrames) {
			return (int)openSLWriteNative(0, 0, 0);
		}

		@Override
		public int write(MediaCodecPlayer.OutputBuffer buffer, int emptyFrames) {
			final MediaCodecPlayer player = buffer.player;
			long dstSrcRet;
			if ((dstSrcRet =
				(player.isNativeMediaCodec() ?
					openSLWriteNative(player.getNativeObj(), buffer.offsetInBytes, buffer.remainingBytes >> srcChannelCount) :
					openSLWrite(buffer.byteArray, buffer.byteBuffer, buffer.offsetInBytes, buffer.remainingBytes >> srcChannelCount, MediaCodecPlayer.needsSwap)
				)) > 0) {
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
		if (nativeSampleRate <= 0 || srcSampleRate == nativeSampleRate || !resamplingEnabled)
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
		updateSrcParams(srcSampleRate = player.getSrcSampleRate(), srcChannelCount = player.getChannelCount(), 1);
	}

	private static void updateNativeSrc(MediaCodecPlayer player) {
		if (player == null)
			return;
		updateSrcParams(srcSampleRate = player.getSrcSampleRate(), srcChannelCount = player.getChannelCount(), 0);
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

	@SuppressLint("WakelockTimeout")
	@Override
	public void run() {
		/*try {
			final int tid = Process.myTid();
			Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
			if (Process.getThreadPriority(tid) != Process.THREAD_PRIORITY_AUDIO)
				thread.setPriority(Thread.MAX_PRIORITY);
		} catch (Throwable ex) {
			try {*/
				thread.setPriority(Thread.MAX_PRIORITY - 1);
			/*} catch (Throwable ex2) {
				//just ignore
			}
		}*/

		final MediaCodecPlayer.OutputBuffer outputBuffer = new MediaCodecPlayer.OutputBuffer();
		MediaCodecPlayer currentPlayer = null, nextPlayer = null, sourcePlayer = null;
		outputBuffer.index = -1;
		int dstSampleRate = 0, lastHeadPositionInFrames = 0, bufferSizeInFrames = 0, fillThresholdInFrames = 0;
		long framesWritten = 0, framesPlayed = 0, nextFramesWritten = 0;
		boolean bufferConfigChanged = false;

		updateNativeSampleRate();

		synchronized (engineSync) {
			initializationError = (engine.initialize() != 0);
		}

		if (initializationError) {
			requestedAction = ACTION_NONE;
			synchronized (threadNotification) {
				threadNotification.notifyAll();
			}
			return;
		}

		final PowerManager.WakeLock wakeLock = ((PowerManager)Player.theApplication.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "fplayx:MediaContext WakeLock");
		wakeLock.setReferenceCounted(false);

		requestedAction = ACTION_NONE;

		synchronized (threadNotification) {
			threadNotification.notifyAll();
		}

		boolean paused = true, playPending = false;
		int framesWrittenBeforePlaying = 0, amountOfTimesNoFramesWereWritten = 0;
		while (alive) {
			if (paused || waitToReceiveAction) {
				MediaCodecPlayer seekPendingPlayer = null;
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
								currentPlayerForReference = currentPlayer;
								nextPlayer = null;
								sourcePlayer = currentPlayer;
								currentPlayer.resetDecoderIfOutputAlreadyUsed();
								framesWritten = currentPlayer.getCurrentPositionInFrames();
								framesPlayed = framesWritten;
								nextFramesWritten = 0;
								//some times, the native sample rate changes when the output
								//method changes (headset, speaker, HDMI, bluetooth...)
								updateNativeSampleRate();
								currentPlayer.updateDstSampleRate();
								if (nextPlayer != null)
									nextPlayer.updateDstSampleRate();
								if (!currentPlayer.isSrcConfigValid())
									throw new MediaPlayerBase.UnsupportedFormatException();
								if (dstSampleRate != currentPlayer.getDstSampleRate() || bufferConfigChanged) {
									bufferConfigChanged = false;
									dstSampleRate = currentPlayer.getDstSampleRate();
									synchronized (engineSync) {
										checkEngineResult(engine.create(dstSampleRate));
									}
								} else {
									synchronized (engineSync) {
										checkEngineResult(engine.recreateIfNeeded());
									}
								}
								bufferSizeInFrames = engine.getActualBufferSizeInFrames();
								fillThresholdInFrames = engine.getFillThresholdInFrames();
								playPending = true;
								amountOfTimesNoFramesWereWritten = 0;
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
											checkEngineResult(engine.recreateIfNeeded());
										}
										bufferSizeInFrames = engine.getActualBufferSizeInFrames();
										fillThresholdInFrames = engine.getFillThresholdInFrames();
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
									if ((framesWritten - framesPlayed) < 512) {
										playPending = true;
										amountOfTimesNoFramesWereWritten = 0;
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
									amountOfTimesNoFramesWereWritten = 0;
									framesWrittenBeforePlaying = 0;
									currentPlayer = null;
									currentPlayerForReference = null;
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
							case ACTION_START_VISUALIZER:
								synchronized (engineSync) {
									requestSucceeded = (visualizerStart(0, 1) == 0);
								}
								break;
							case ACTION_STOP_VISUALIZER:
								synchronized (engineSync) {
									visualizerStop();
								}
								requestSucceeded = true;
								break;
							case ACTION_ENABLE_RESAMPLING:
								resamplingEnabled = true;
								break;
							case ACTION_DISABLE_RESAMPLING:
								resamplingEnabled = false;
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
							amountOfTimesNoFramesWereWritten = 0;
							framesWrittenBeforePlaying = 0;
							currentPlayer = null;
							currentPlayerForReference = null;
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
							threadNotification.notifyAll();
						}
					}
				}
				if (seekPendingPlayer != null) {
					try {
						if (seekPendingPlayer == currentPlayer)
							sourcePlayer = currentPlayer;
						outputBuffer.release();
						updateNativeSrcAndReset(seekPendingPlayer);
						if (sourcePlayer == seekPendingPlayer) {
							synchronized (engineSync) {
								checkEngineResult(engine.recreateIfNeeded());
							}
							bufferSizeInFrames = engine.getActualBufferSizeInFrames();
							fillThresholdInFrames = engine.getFillThresholdInFrames();
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
						amountOfTimesNoFramesWereWritten = 0;
						framesWrittenBeforePlaying = 0;
						currentPlayer = null;
						currentPlayerForReference = null;
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

			try {
				if (paused)
					continue;

				//int tmpnow = (int)SystemClock.uptimeMillis();
				//System.out.println("@@@ getHeadPositionInFrames " + (tmpnow - now));
				//now = tmpnow;
				final int currentHeadPositionInFrames = engine.getHeadPositionInFrames();
				framesPlayed += (currentHeadPositionInFrames - lastHeadPositionInFrames);
				lastHeadPositionInFrames = currentHeadPositionInFrames;

				currentPlayer.setCurrentPositionInFrames(framesPlayed);

				if (outputBuffer.index < 0) {
					sourcePlayer.nextOutputBuffer(outputBuffer);
					if (outputBuffer.index < 0) {
						if (outputBuffer.index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							if (dstSampleRate != sourcePlayer.getDstSampleRate()) {
								if (sourcePlayer == nextPlayer) {
									//go back to currentPlayer and handle everything later
									outputBuffer.release();
									sourcePlayer = currentPlayer;
									updateNativeSrc(sourcePlayer);
									nextPlayer = null;
									nextFramesWritten = 0;
									continue;
								}

								//worst case!!! we will have to recreate everything!!!
								//sorry for just modifying a copy/paste from ACTION_PLAY... :(
								synchronized (engineSync) {
									engine.release();
								}
								outputBuffer.release();
								currentPlayer.resetDecoderIfOutputAlreadyUsed();
								framesWritten = currentPlayer.getCurrentPositionInFrames();
								framesPlayed = framesWritten;
								nextFramesWritten = 0;
								if (!currentPlayer.isSrcConfigValid())
									throw new MediaPlayerBase.UnsupportedFormatException();
								dstSampleRate = currentPlayer.getDstSampleRate();
								synchronized (engineSync) {
									checkEngineResult(engine.create(dstSampleRate));
								}
								bufferSizeInFrames = engine.getActualBufferSizeInFrames();
								fillThresholdInFrames = engine.getFillThresholdInFrames();
								playPending = true;
								amountOfTimesNoFramesWereWritten = 0;
								framesWrittenBeforePlaying = 0;
								bufferingStart(currentPlayer);
								updateNativeSrcAndReset(currentPlayer);
								lastHeadPositionInFrames = engine.getHeadPositionInFrames();
							} else {
								if (sourcePlayer == nextPlayer) {
									if (!sourcePlayer.isSrcConfigValid()) {
										//go back to currentPlayer and let the error be handled later
										outputBuffer.release();
										sourcePlayer = currentPlayer;
										updateNativeSrc(sourcePlayer);
										nextPlayer = null;
										nextFramesWritten = 0;
										continue;
									}
								}
								//just update all source params :D
								updateNativeSrc(sourcePlayer);
							}
							continue;
						} else {
							//fill the input only when there is no more output available
							sourcePlayer.fillInputBuffers();
						}

						boolean sleepNow = true;
						if (outputBuffer.streamOver) {
							int framesWrittenThisTime = 0;
							if (nextPlayer == null || sourcePlayer == nextPlayer) {
								//when the input stream is over and we do not have a nextPlayer to produce
								//new samples, we need to tell OpenSL to flush any pending data it had stored
								//if we were using AudioTrack, then we need to write 0 samples in order to
								//fill up the buffer, otherwise, if it was not playing, it would never start
								framesWrittenThisTime = engine.commitFinalFrames(bufferSizeInFrames - (int)(framesWritten - framesPlayed));
								if (framesWrittenThisTime < 0)
									throw new IOException("engine.write() returned " + framesWrittenThisTime);
							}

							if (playPending) {
								sleepNow = false;
								framesWrittenBeforePlaying += framesWrittenThisTime;
								if (framesWrittenThisTime == 0 || framesWrittenBeforePlaying >= fillThresholdInFrames) {
									//the song ended before we had a chance to start playing before, so do it now!
									playPending = false;
									amountOfTimesNoFramesWereWritten = 0;
									checkEngineResult(engine.play());
									bufferingEnd(sourcePlayer);
								}
							}
						}

						if (sleepNow) {
							try {
								synchronized (threadNotification) {
									//sleep only for a very brief period, do not use the standard sleep time!
									if (requestedAction == ACTION_NONE)
										threadNotification.wait(5);
								}
							} catch (Throwable ex) {
								//just ignore
							}
						}
					}
				}

				if (outputBuffer.remainingBytes > 0) {
					final int framesWrittenThisTime = engine.write(outputBuffer, bufferSizeInFrames - (int)(framesWritten - framesPlayed));
					if (framesWrittenThisTime < 0) {
						throw new IOException("engine.write() returned " + framesWrittenThisTime);
					} else if (framesWrittenThisTime == 0) {
						//apparently, if the engine starves for longer than a brief period, it does
						//not start playing automatically (even after filling up the entire buffer!)
						amountOfTimesNoFramesWereWritten++;
						if (playPending || amountOfTimesNoFramesWereWritten > 3) {
							//we have just filled the buffer, time to start playing
							playPending = false;
							amountOfTimesNoFramesWereWritten = 0;
							checkEngineResult(engine.play());
							bufferingEnd(sourcePlayer);
						}
						//the buffer was too full, let's use this time to start filling up the
						//next input buffers before waiting some time (discount the time spent
						//inside fillInputBuffers())
						int actualSleepTime = (int)SystemClock.uptimeMillis();
						//we cannot call nextOutputBuffer() here, so let's just release
						if (outputBuffer.remainingBytes <= 0)
							outputBuffer.release();
						//sourcePlayer.fillInputBuffers();
						try {
							actualSleepTime = 200 - ((int)SystemClock.uptimeMillis() - actualSleepTime);
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
						amountOfTimesNoFramesWereWritten = 0;
						if (sourcePlayer == currentPlayer)
							framesWritten += framesWrittenThisTime;
						else
							nextFramesWritten += framesWrittenThisTime;
						if (playPending) {
							framesWrittenBeforePlaying += framesWrittenThisTime;
							if (framesWrittenBeforePlaying >= fillThresholdInFrames) {
								//we have just filled the buffer, time to start playing
								playPending = false;
								checkEngineResult(engine.play());
								bufferingEnd(sourcePlayer);
							}
						}
					}
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
						currentPlayer.setCurrentPositionInFrames(currentPlayer.getDurationInFrames());
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
						synchronized (threadNotification) {
							currentPlayer = nextPlayer;
							if (currentPlayer != null)
								currentPlayer.startedAsNext();
							currentPlayerForReference = currentPlayer;
						}
						nextPlayer = null;
						nextFramesWritten = 0;
						sourcePlayer = currentPlayer;
						updateNativeSrc(sourcePlayer);
					} else if (framesWritten != 0) {
						//underrun!!!
						checkEngineResult(engine.pause());
						playPending = true;
						amountOfTimesNoFramesWereWritten = 0;
						framesWrittenBeforePlaying = 0;
						bufferingStart(currentPlayer);
						//give the decoder some time to decode something
						try {
							synchronized (threadNotification) {
								if (requestedAction == ACTION_NONE)
									threadNotification.wait(10);
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
					amountOfTimesNoFramesWereWritten = 0;
					currentPlayer = null;
					currentPlayerForReference = null;
					wakeLock.release();
				}
				handler.sendMessageAtTime(Message.obtain(handler, MSG_ERROR, new ErrorStructure(sourcePlayer, ex)), SystemClock.uptimeMillis());
				nextPlayer = null;
				nextFramesWritten = 0;
				sourcePlayer = currentPlayer;
				updateNativeSrc(sourcePlayer);
			}
		}

		wakeLock.release();
		synchronized (threadNotification) {
			currentPlayerForReference = null;
			threadNotification.notifyAll();
		}
	}

	@Override
	public boolean handleMessage(@NonNull Message msg) {
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

		synchronized (threadNotification) {
			if (requestedAction == ACTION_INITIALIZE) {
				try {
					threadNotification.wait();
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
			threadNotification.notifyAll();
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
			threadNotification.notifyAll();
			if (playerRequestingAction != null) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			if (engineBlocks && player == currentPlayerForReference) {
				synchronized (engineSync) {
					if (engine != null)
						engine.pauseFromOtherThreadIfBlocking();
				}
			}
			playerRequestingAction = player;
			requestedAction = ACTION_PAUSE;
			threadNotification.notifyAll();
			if (playerRequestingAction != null) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			threadNotification.notifyAll();
			if (playerRequestingAction != null) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			if (engineBlocks && player == currentPlayerForReference) {
				synchronized (engineSync) {
					if (engine != null)
						engine.pauseFromOtherThreadIfBlocking();
				}
			}
			playerRequestingAction = player;
			requestedSeekMS = msec;
			requestedAction = ACTION_SEEK;
			threadNotification.notifyAll();
			if (playerRequestingAction != null) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			threadNotification.notifyAll();
			if (playerRequestingAction != null) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			if (engineBlocks && player == currentPlayerForReference) {
				synchronized (engineSync) {
					if (engine != null)
						engine.pauseFromOtherThreadIfBlocking();
				}
			}
			playerRequestingAction = player;
			requestedAction = ACTION_RESET;
			threadNotification.notifyAll();
			if (playerRequestingAction != null) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			threadNotification.notifyAll();
			if (requestedAction == ACTION_EFFECTS) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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

	static void _getEqualizerFrequencyResponse(int bassBoostStrength, short[] levels, double[] frequencies, double[] gains) {
		getEqualizerFrequencyResponse(bassBoostStrength, levels, frequencies.length, frequencies, gains);
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

	static boolean startVisualizer() {
		if (!alive)
			return false;
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			requestedAction = ACTION_START_VISUALIZER;
			threadNotification.notifyAll();
			if (requestedAction == ACTION_START_VISUALIZER) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
		return requestSucceeded;
	}

	static void stopVisualizer() {
		if (!alive) {
			visualizerStop();
			return;
		}
		waitToReceiveAction = true;
		synchronized (threadNotification) {
			requestedAction = ACTION_STOP_VISUALIZER;
			threadNotification.notifyAll();
			if (requestedAction == ACTION_STOP_VISUALIZER) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
	}

	static void zeroOutVisualizer() {
		synchronized (engineSync) {
			if (alive)
				visualizerZeroOut();
		}
	}

	static void getVisualizerWaveform(byte[] waveform) {
		synchronized (engineSync) {
			if (alive && engine != null)
				engine.getVisualizerWaveform(waveform);
			else
				Arrays.fill(waveform, (byte)0x80);
		}
	}

	public static MediaPlayerBase createMediaPlayer() {
		return new MediaCodecPlayer();
	}

	public static int[] getCurrentPlaybackInfo() {
		final int dstSampleRate;
		final int nativeFramesPerBuffer = Engine.getFramesPerBuffer(nativeSampleRate);
		final int usedFramesPerBuffer;
		synchronized (engineSync) {
			dstSampleRate = ((engine != null) ? engine.getCurrentDstSampleRate() : srcSampleRate);
			usedFramesPerBuffer = ((engine != null) ? engine.getSingleBufferSizeInFrames() : nativeFramesPerBuffer);
		}
		return new int[] {
			nativeSampleRate,
			srcSampleRate,
			dstSampleRate,
			nativeFramesPerBuffer,
			usedFramesPerBuffer,
			(engine == null) ? 0 : ((engine instanceof AudioTrackEngine) ? 1 : 2)
		};
	}

	public static int getFeatures() {
		return (getProcessorFeatures() |
			(externalNativeLibraryAvailable && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Player.filePrefetchSize == 0) ? Player.FEATURE_DECODING_NATIVE : 0) |
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
			threadNotification.notifyAll();
			if (requestedAction == ACTION_UPDATE_BUFFER_CONFIG) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
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
			threadNotification.notifyAll();
			if (requestedAction == ACTION_DISABLE_EFFECTS_GAIN || requestedAction == ACTION_ENABLE_EFFECTS_GAIN) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
	}

	public static boolean isResamplingEnabled() {
		return resamplingEnabled;
	}

	public static void _enableResampling(boolean enabled) {
		if (!alive) {
			resamplingEnabled = enabled;
			return;
		}

		waitToReceiveAction = true;
		synchronized (threadNotification) {
			requestedAction = (enabled ? ACTION_ENABLE_RESAMPLING : ACTION_DISABLE_RESAMPLING);
			threadNotification.notifyAll();
			if (requestedAction == ACTION_ENABLE_RESAMPLING || requestedAction == ACTION_DISABLE_RESAMPLING) {
				try {
					threadNotification.wait(PLAYER_TIMEOUT);
				} catch (Throwable ex) {
					//just ignore
				}
			}
		}
	}
}
