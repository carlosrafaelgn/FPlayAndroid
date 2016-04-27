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
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

final class MediaCodecPlayer implements IMediaPlayer {
	private static final int STATE_IDLE = 0;
	private static final int STATE_INITIALIZED = 1;
	private static final int STATE_PREPARING = 2;
	private static final int STATE_PREPARED = 3;
	private static final int STATE_STARTED = 4;
	private static final int STATE_PAUSED = 5;
	private static final int STATE_SEEKING = 6;
	private static final int STATE_PLAYBACKCOMPLETED = 7;
	private static final int STATE_ERROR = 8;
	private static final int STATE_END = 9;

	private static final int INPUT_BUFFER_TIMEOUT_IN_US = 2500;
	private static final int OUTPUT_BUFFER_TIMEOUT_IN_US = 35000;

	static final class OutputBuffer {
		public MediaCodecPlayer player;
		public ByteBuffer byteBuffer;
		public ShortBuffer shortBuffer;
		public int index, offset, size; //offset and size are either in shorts or bytes
		public boolean streamOver;

		public void release() {
			if (player != null) {
				if (index >= 0) {
					player.releaseOutputBuffer(index);
					index = -1;
				}
				player = null;
			}
		}
	}

	private volatile int state, currentPositionInMS;
	private int sampleRate, channelCount, durationInMS, stateBeforeSeek;
	private MediaExtractor mediaExtractor;
	private MediaCodec mediaCodec;
	private String path;
	private ByteBuffer[] inputBuffers, outputBuffers;
	private ShortBuffer[] outputBuffersAsShort;
	private boolean inputOver, outputOver, outputBuffersHaveBeenUsed;
	private MediaCodec.BufferInfo bufferInfo;
	//private OnBufferingUpdateListener bufferingUpdateListener;
	private OnCompletionListener completionListener;
	private OnErrorListener errorListener;
	//private OnInfoListener infoListener;
	//private OnPreparedListener preparedListener;
	private OnSeekCompleteListener seekCompleteListener;

	public MediaCodecPlayer() {
		state = STATE_IDLE;
		durationInMS = -1;
		bufferInfo = new MediaCodec.BufferInfo();
	}

	int getSampleRate() {
		return sampleRate;
	}

	int getChannelCount() {
		return channelCount;
	}

	boolean isOutputOver() {
		return outputOver;
	}

	private int fillInputBuffers() throws IOException {
		if (inputOver)
			return 0;
		int i;
		for (i = 0; i < inputBuffers.length && !inputOver; i++) {
			final int index = mediaCodec.dequeueInputBuffer(INPUT_BUFFER_TIMEOUT_IN_US);
			if (index < 0)
				break;
			int size = mediaExtractor.readSampleData(inputBuffers[index], 0);
			if (size < 0) {
				inputOver = true;
				mediaCodec.queueInputBuffer(index, 0, 0, mediaExtractor.getSampleTime(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
				break;
			} else {
				mediaCodec.queueInputBuffer(index, 0, size, mediaExtractor.getSampleTime(), 0);
				mediaExtractor.advance();
			}
		}
		return i;
	}

	//**************************************************************
	//Methods nextOutputBuffer(), releaseOutputBuffer(), doSeek(),
	//resetDecoderIfOutputAlreadyUsed() and startedAsNext()
	//MUST be called from the playback thread: MediaContext.run()
	//**************************************************************

	@SuppressWarnings("deprecation")
	boolean nextOutputBuffer(OutputBuffer outputBuffer) throws IOException {
		if (outputOver) {
			outputBuffer.index = -1;
			outputBuffer.player = this;
			outputBuffer.size = 0;
			outputBuffer.streamOver = true;
			return false;
		}
		fillInputBuffers();
		int index = mediaCodec.dequeueOutputBuffer(bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);
		INDEX_CHECKER:
		for (; ; ) {
			switch (index) {
			case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				MediaFormat format = mediaCodec.getOutputFormat();
				sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
				channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
				index = mediaCodec.dequeueOutputBuffer(bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);
				break;
			case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				outputBuffers = mediaCodec.getOutputBuffers();
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					outputBuffersAsShort = new ShortBuffer[outputBuffers.length];
					for (int i = outputBuffers.length - 1; i >= 0; i--)
						outputBuffersAsShort[i] = outputBuffers[i].asShortBuffer();
				}
				index = mediaCodec.dequeueOutputBuffer(bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);
				break;
			default:
				break INDEX_CHECKER;
			}
		}
		outputBuffer.index = index;
		outputBuffer.player = this;
		outputOver = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
		outputBuffer.streamOver = outputOver;
		if (index < 0) {
			outputBuffer.size = 0;
			return false;
		}
		outputBuffersHaveBeenUsed = true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			outputBuffer.byteBuffer = outputBuffers[index];
			outputBuffer.offset = bufferInfo.offset;
			outputBuffer.size = bufferInfo.size;
			outputBuffer.byteBuffer.position(0);
			outputBuffer.byteBuffer.limit(outputBuffer.size);
		} else {
			outputBuffer.shortBuffer = outputBuffersAsShort[index];
			outputBuffer.offset = bufferInfo.offset >> 1; //bytes to shorts
			outputBuffer.size = bufferInfo.size >> 1; //bytes to shorts
			outputBuffer.shortBuffer.position(0);
			outputBuffer.shortBuffer.limit(outputBuffer.size);
		}
		return true;
	}

	private void releaseOutputBuffer(int bufferIndex) {
		mediaCodec.releaseOutputBuffer(bufferIndex, false);
	}

	void doSeek(int msec) throws IOException {
		if (state != STATE_SEEKING)
			return;

		mediaCodec.flush();
		mediaExtractor.seekTo((long)msec * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
		inputOver = false;
		outputOver = false;
		bufferInfo.flags = 0;
		currentPositionInMS = (int)(mediaExtractor.getSampleTime() / 1000L);
		fillInputBuffers();
	}

	void resetDecoderIfOutputAlreadyUsed() throws IOException {
		//a rare case, in which this player had already started producing output buffers as the next
		//player, but became the current player due to a user interation, rather than due to the
		//completion of the previous current player
		if (outputBuffersHaveBeenUsed) {
			System.out.println("***RARE RESETING!!! " + this);
			final String tmp = path;
			resetInternal();
			setDataSource(tmp);
			prepare();
		}
	}

	void startedAsNext() {
		if (state != STATE_PREPARED)
			throw new IllegalStateException("startedAsNext() - player was in an invalid state: " + state);
		state = STATE_STARTED;
	}

	//**************************************************************
	//All methods below MUST be called from the player thread:
	//Player.CoreHandler.dispatchMessage()
	//or
	//MediaContext.handleMessage()
	//**************************************************************

	void playbackComplete() {
		state = STATE_PLAYBACKCOMPLETED;
		if (completionListener != null)
			completionListener.onCompletion(this);
	}

	void error(Throwable exception) {
		inputOver = true;
		outputOver = true;
		state = STATE_ERROR;
		if (errorListener != null)
			errorListener.onError(this, ERROR_UNKNOWN,
				(exception instanceof UnsupportedFormatException) ? ERROR_UNSUPPORTED_FORMAT :
					((exception instanceof IOException) ? ERROR_IO : ERROR_UNKNOWN));
	}

	void seekComplete() {
		if (stateBeforeSeek == STATE_STARTED) {
			state = STATE_PAUSED;
			start();
			if (state != STATE_STARTED)
				return;
		} else {
			state = stateBeforeSeek;
		}
		stateBeforeSeek = STATE_IDLE;
		if (seekCompleteListener != null)
			seekCompleteListener.onSeekComplete(this);
	}

	@Override
	public void start() {
		if (state == STATE_INITIALIZED) {
			try {
				prepare();
			} catch (Throwable ex) {
				throw new RuntimeException(ex);
			}
		}

		switch (state) {
		case STATE_PREPARED:
			if (MediaContext.play(this))
				state = STATE_STARTED;
			break;
		case STATE_STARTED:
			break;
		case STATE_PAUSED:
			if (MediaContext.resume(this))
				state = STATE_STARTED;
			break;
		default:
			throw new IllegalStateException("start() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void stop() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void pause() {
		switch (state) {
		case STATE_PAUSED:
			break;
		case STATE_STARTED:
			if (MediaContext.pause(this))
				state = STATE_PAUSED;
			break;
		default:
			throw new IllegalStateException("pause() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void setDataSource(String path) throws IOException {
		if (state != STATE_IDLE)
			throw new IllegalStateException("setDataSource() - player was in an invalid state: " + state);
		File file = new File(path);
		if (!file.isFile() || !file.exists() || file.length() <= 0)
			throw new FileNotFoundException(path);
		if (!file.canRead())
			throw new SecurityException(path);
		this.path = path;
		state = STATE_INITIALIZED;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void prepare() throws IOException {
		switch (state) {
		case STATE_PREPARING:
		case STATE_PREPARED:
		case STATE_STARTED:
		case STATE_PAUSED:
			break;
		case STATE_INITIALIZED:
			mediaExtractor = new MediaExtractor();
			mediaExtractor.setDataSource(path);
			final int numTracks = mediaExtractor.getTrackCount();
			int i;
			MediaFormat format = null;
			String mime = "";
			for (i = 0; i < numTracks; ++i) {
				format = mediaExtractor.getTrackFormat(i);
				mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.startsWith("audio/")) {
					mediaExtractor.selectTrack(i);
					sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
					channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
					if (channelCount != 1 && channelCount != 2)
						throw new UnsupportedFormatException();
					durationInMS = (int)(format.getLong(MediaFormat.KEY_DURATION) / 1000L);
					break;
				}
			}
			if (i >= numTracks)
				throw new UnsupportedFormatException();
			try {
				mediaCodec = MediaCodec.createDecoderByType(mime);
				mediaCodec.configure(format, null, null, 0);
			} catch (Throwable ex) {
				mediaCodec = null;
			}
			if (mediaCodec == null)
				throw new UnsupportedFormatException();
			mediaCodec.start();
			currentPositionInMS = 0;
			inputOver = false;
			outputOver = false;
			inputBuffers = mediaCodec.getInputBuffers();
			outputBuffers = mediaCodec.getOutputBuffers();
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				outputBuffersAsShort = new ShortBuffer[outputBuffers.length];
				for (i = outputBuffers.length - 1; i >= 0; i--)
					outputBuffersAsShort[i] = outputBuffers[i].asShortBuffer();
			}
			fillInputBuffers();
			state = STATE_PREPARED;
			break;
		default:
			throw new IllegalStateException("prepare() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void prepareAsync() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void seekToAsync(int msec) {
		stateBeforeSeek = state;
		switch (state) {
		case STATE_STARTED:
			pause();
		case STATE_PAUSED:
		case STATE_PREPARED:
			state = STATE_SEEKING;
			if (!MediaContext.seekToAsync(this, msec))
				seekComplete();
			break;
		default:
			throw new IllegalStateException("seekTo() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void release() {
		reset();
		bufferInfo = null;
		//bufferingUpdateListener = null;
		completionListener = null;
		errorListener = null;
		//infoListener = null;
		//preparedListener = null;
		seekCompleteListener = null;
		state = STATE_END;
	}

	private void resetInternal() {
		if (mediaExtractor != null) {
			try {
				mediaExtractor.release();
			} catch (Throwable ex) {
				//just ignore
			}
			mediaExtractor = null;
		}
		if (mediaCodec != null) {
			try {
				mediaCodec.stop();
			} catch (Throwable ex) {
				//just ignore
			}
			try {
				mediaCodec.release();
			} catch (Throwable ex) {
				//just ignore
			}
			mediaCodec = null;
		}
		path = null;
		inputOver = false;
		outputOver = false;
		bufferInfo.flags = 0;
		outputBuffersHaveBeenUsed = false;
		sampleRate = 0;
		channelCount = 0;
		currentPositionInMS = 0;
		durationInMS = -1;
		stateBeforeSeek = STATE_IDLE;
		inputBuffers = null;
		outputBuffers = null;
		state = STATE_IDLE;
	}

	@Override
	public void reset() {
		if (state == STATE_IDLE || state == STATE_END)
			return;
		MediaContext.reset(this);
		resetInternal();
	}

	@Override
	public int getAudioSessionId() {
		return MediaContext.getAudioSessionId();
	}

	@Override
	public void setAudioSessionId(int sessionId) {
		//unnecessary (handled in MediaContext)
	}

	@Override
	public int getDuration() {
		return durationInMS;
	}

	long getCurrentPositionInFrames() {
		return ((long)currentPositionInMS * (long)sampleRate) //ms * frames per second
			/ 1000L; //ms to second
	}

	void setCurrentPosition(int msec) {
		currentPositionInMS = msec;
	}

	@Override
	public int getCurrentPosition() {
		return currentPositionInMS;
	}

	@Override
	public boolean isPlaying() {
		return (state == STATE_STARTED);
	}

	@Override
	public void setVolume(float leftVolume, float rightVolume) {
		MediaContext.setStereoVolume(leftVolume, rightVolume);
	}

	@Override
	public void setAudioStreamType(int streamtype) {
		//unnecessary (handled in MediaContext)
	}

	@Override
	public void setWakeMode(Context context, int mode) {
		//unnecessary (handled in MediaContext)
	}

	@Override
	public void setNextMediaPlayer(IMediaPlayer next) {
		if (next == this)
			throw new IllegalArgumentException("this == next");
		final MediaCodecPlayer nextPlayer = (MediaCodecPlayer)next;
		if (nextPlayer != null && (nextPlayer.state != STATE_PREPARED || nextPlayer.durationInMS < 10000))
			throw new IllegalArgumentException("next is not prepared or its durationInMS is < 10000");
		MediaContext.setNextPlayer(this, nextPlayer);
	}

	@Override
	public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
		//bufferingUpdateListener = listener;
	}

	@Override
	public void setOnCompletionListener(OnCompletionListener listener) {
		completionListener = listener;
	}

	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		errorListener = listener;
	}

	@Override
	public void setOnInfoListener(OnInfoListener listener) {
		//infoListener = listener;
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		//preparedListener = listener;
	}

	@Override
	public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
		seekCompleteListener = listener;
	}
}
