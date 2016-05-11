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
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import br.com.carlosrafaelgn.fplay.playback.HttpStreamReceiver;
import br.com.carlosrafaelgn.fplay.playback.Player;

final class MediaCodecPlayer implements IMediaPlayer, Handler.Callback {
	private static final int MSG_HTTP_STREAM_RECEIVER_FILL_INITIAL_BUFFERS = 0x0100;
	private static final int MSG_HTTP_STREAM_RECEIVER_ERROR = 0x0101;
	private static final int MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE = 0x0102;
	private static final int MSG_HTTP_STREAM_RECEIVER_URL_UPDATED = 0x0103;
	private static final int MSG_HTTP_STREAM_RECEIVER_INFO = 0x0104;

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
		public int index, offsetInBytes, remainingBytes;
		public boolean streamOver;

		public void release() {
			remainingBytes = 0;
			if (player != null) {
				if (index >= 0) {
					player.releaseOutputBuffer(index);
					index = -1;
				}
				player = null;
			}
		}
	}

	private static Field fieldBackingArray, fieldArrayOffset;
	public static boolean isDirect;
	public static int needsSwap;
	public static boolean tryToProcessNonDirectBuffers;

	private volatile int state, currentPositionInMS, httpStreamReceiverVersion;
	private int sampleRate, channelCount, durationInMS, stateBeforeSeek;
	private MediaExtractor mediaExtractor;
	private MediaCodec mediaCodec;
	private Handler handler;
	private HttpStreamReceiver httpStreamReceiver;
	private String path;
	private ByteBuffer[] inputBuffers, outputBuffers;
	private boolean inputOver, outputOver, outputBuffersHaveBeenUsed, internetStream, buffering, httpStreamBufferingAfterPause;
	private MediaCodec.BufferInfo bufferInfo;
	private OnCompletionListener completionListener;
	private OnErrorListener errorListener;
	private OnInfoListener infoListener;
	private OnPreparedListener preparedListener;
	private OnSeekCompleteListener seekCompleteListener;

	public MediaCodecPlayer() {
		state = STATE_IDLE;
		durationInMS = -1;
		bufferInfo = new MediaCodec.BufferInfo();
	}

	@Override
	public String toString() {
		if (path == null)
			return super.toString();
		final int i = path.lastIndexOf('/');
		return ((i < 0) ? path : path.substring(i + 1)) + " - " + super.toString();
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

	boolean isInternetStream() {
		return internetStream;
	}

	private void fillInternetInputBuffers() throws IOException {
		int inputFrameSize, index;
		if (state == STATE_PREPARING || httpStreamBufferingAfterPause) {
			//initial buffers
			final int[] properties = new int[4]; //channel count, sample rate, bit rate, samples per frame
			while ((inputFrameSize = httpStreamReceiver.canReadMpegHeader(properties)) > 0 &&
				(index = mediaCodec.dequeueInputBuffer(INPUT_BUFFER_TIMEOUT_IN_US)) >= 0) {
				httpStreamReceiver.buffer.readArray(inputBuffers[index], 0, inputFrameSize);
				httpStreamReceiver.buffer.commitRead(inputFrameSize);
				mediaCodec.queueInputBuffer(index, 0, inputFrameSize, 0, 0);
				//currentPositionInMS is used as the counter of initially filled buffers
				currentPositionInMS++;
				//if we have filled up all input buffers, or if we have filled enough buffers
				//(according to the user setting), then we are good to go!
				if (currentPositionInMS >= inputBuffers.length ||
					(currentPositionInMS * properties[3]) >= ((sampleRate * Player.getSecondsBeforePlayback(Player.getSecondsBeforePlaybackIndex())) / 1000)) {
					currentPositionInMS = 0;
					if (httpStreamBufferingAfterPause) {
						httpStreamBufferingAfterPause = false;
						if (MediaContext.play(this))
							state = STATE_STARTED;
					} else {
						onPrepared();
					}
					return;
				}
			}
			//do it again
			handler.sendMessageAtTime(Message.obtain(handler, MSG_HTTP_STREAM_RECEIVER_FILL_INITIAL_BUFFERS, httpStreamReceiverVersion, 0), SystemClock.uptimeMillis() + 5);
		} else {
			//regular buffers
			while ((inputFrameSize = httpStreamReceiver.canReadMpegHeader(null)) > 0 &&
				(index = mediaCodec.dequeueInputBuffer(INPUT_BUFFER_TIMEOUT_IN_US)) >= 0) {
				httpStreamReceiver.buffer.readArray(inputBuffers[index], 0, inputFrameSize);
				httpStreamReceiver.buffer.commitRead(inputFrameSize);
				mediaCodec.queueInputBuffer(index, 0, inputFrameSize, 0, 0);
			}
		}
	}

	private void fillInputBuffers() throws IOException {
		if (inputOver)
			return;
		for (int i = 0; i < inputBuffers.length && !inputOver; i++) {
			final int index = mediaCodec.dequeueInputBuffer(INPUT_BUFFER_TIMEOUT_IN_US);
			if (index < 0)
				break;
			int size = mediaExtractor.readSampleData(inputBuffers[index], 0);
			if (size < 0) {
				inputOver = true;
				mediaCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
				break;
			} else {
				mediaCodec.queueInputBuffer(index, 0, size, 0, 0);
				mediaExtractor.advance();
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean handleMessage(Message msg) {
		if (msg.arg1 != httpStreamReceiverVersion || handler == null || httpStreamReceiver == null)
			return true;
		switch (msg.what) {
		case MSG_HTTP_STREAM_RECEIVER_FILL_INITIAL_BUFFERS:
			try {
				fillInternetInputBuffers();
			} catch (Throwable ex) {
				onError(ex, 0);
			}
			break;
		case MSG_HTTP_STREAM_RECEIVER_ERROR:
			onError(null, !Player.isConnectedToTheInternet() ? IMediaPlayer.ERROR_NOT_FOUND : msg.arg2);
			break;
		case MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE:
			if (msg.obj != null)
				onInfo(INFO_METADATA_UPDATE, 0, msg.obj);
			break;
		case MSG_HTTP_STREAM_RECEIVER_URL_UPDATED:
			if (msg.obj != null)
				onInfo(INFO_URL_UPDATE, 0, msg.obj);
			break;
		case MSG_HTTP_STREAM_RECEIVER_INFO:
			if (state != STATE_PREPARING && state != STATE_PAUSED) {
				onError(new IllegalStateException(), 0);
				break;
			}
			try {
				final int[] properties = (int[])msg.obj; //channel count, sample rate, bit rate, samples per frame
				channelCount = properties[0];
				sampleRate = properties[1];
				//only stereo files for now...
				if (channelCount != 2) {
					onError(new UnsupportedFormatException(), 0);
					break;
				}
				//we only support mpeg streams
				final String contentType = "audio/mpeg";
				mediaCodec = MediaCodec.createDecoderByType(contentType);
				final MediaFormat format = new MediaFormat();
				format.setString(MediaFormat.KEY_MIME, contentType);
				format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
				format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
				format.setInteger(MediaFormat.KEY_CHANNEL_MASK, (channelCount == 1) ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
				format.setInteger(MediaFormat.KEY_BIT_RATE, properties[2]);
				mediaCodec.configure(format, null, null, 0);
				mediaCodec.start();
				currentPositionInMS = 0;
				inputOver = false;
				outputOver = false;
				inputBuffers = mediaCodec.getInputBuffers();
				prepareOutputBuffers();
				fillInternetInputBuffers();
			} catch (Throwable ex) {
				onError(ex, 0);
			}
			break;
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	private void prepareOutputBuffers() {
		outputBuffers = mediaCodec.getOutputBuffers();
		//we will assume that all buffers are alike
		isDirect = outputBuffers[0].isDirect();
		needsSwap = ((outputBuffers[0].order() != ByteOrder.nativeOrder()) ? 1 : 0);
		if (!isDirect && (fieldBackingArray == null || fieldArrayOffset == null)) {
			//final byte[] backingArray;
			//final int arrayOffset;
			if (fieldBackingArray == null) {
				try {
					fieldBackingArray = outputBuffers[0].getClass().getField("backingArray");
					fieldBackingArray.setAccessible(true);
				} catch (Throwable ex) {
					fieldBackingArray = null;
				}
			}
			if (fieldArrayOffset == null) {
				try {
					fieldArrayOffset = outputBuffers[0].getClass().getField("arrayOffset");
					fieldArrayOffset.setAccessible(true);
				} catch (Throwable ex) {
					fieldArrayOffset = null;
				}
			}
		}
	}

	//**************************************************************
	//Methods nextOutputBuffer(), releaseOutputBuffer(), doSeek(),
	//resetDecoderIfOutputAlreadyUsed() and startedAsNext()
	//MUST be called from the playback thread: MediaContext.run()
	//**************************************************************

	void notifyUnderrun() {
		if (!buffering) {
			buffering = true;
			MediaContext.bufferingStart(this);
		}
	}

	@SuppressWarnings("deprecation")
	boolean nextOutputBuffer(OutputBuffer outputBuffer) throws IOException {
		if (outputOver) {
			outputBuffer.index = -1;
			outputBuffer.player = this;
			outputBuffer.remainingBytes = 0;
			outputBuffer.streamOver = true;
			return false;
		}
		if (internetStream)
			fillInternetInputBuffers();
		else
			fillInputBuffers();
		int index = mediaCodec.dequeueOutputBuffer(bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);
		IndexChecker:
		for (; ; ) {
			switch (index) {
			case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				MediaFormat format = mediaCodec.getOutputFormat();
				sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
				channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
				index = mediaCodec.dequeueOutputBuffer(bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);
				break;
			case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				prepareOutputBuffers();
				index = mediaCodec.dequeueOutputBuffer(bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);
				break;
			default:
				break IndexChecker;
			}
		}
		outputBuffer.index = index;
		outputBuffer.player = this;
		outputOver = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
		outputBuffer.streamOver = outputOver;
		if (index < 0) {
			outputBuffer.remainingBytes = 0;
			return false;
		}
		outputBuffersHaveBeenUsed = true;

		outputBuffer.byteBuffer = outputBuffers[index];
		outputBuffer.offsetInBytes = bufferInfo.offset;
		outputBuffer.remainingBytes = bufferInfo.size - outputBuffer.offsetInBytes;

		if (buffering) {
			buffering = false;
			MediaContext.bufferingEnd(this);
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
			if (httpStreamReceiver != null) {
				if (!httpStreamBufferingAfterPause) {
					httpStreamBufferingAfterPause = true;
					buffering = true;
					onInfo(INFO_BUFFERING_START, 0, null);
					if (!httpStreamReceiver.start())
						throw new IMediaPlayer.PermissionDeniedException();
				}
			} else if (MediaContext.resume(this)) {
				state = STATE_STARTED;
			}
			break;
		default:
			throw new IllegalStateException("start() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void pause() {
		switch (state) {
		case STATE_PAUSED:
			break;
		case STATE_STARTED:
			if (MediaContext.pause(this)) {
				state = STATE_PAUSED;
				if (httpStreamReceiver != null) {
					httpStreamReceiver.pause();
					resetMedia();
				}
			}
			break;
		default:
			throw new IllegalStateException("pause() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void setDataSource(String path) throws IOException {
		if (state != STATE_IDLE)
			throw new IllegalStateException("setDataSource() - player was in an invalid state: " + state);
		this.path = path;
		if ((internetStream = (path.startsWith("http:") || path.startsWith("https:") || path.startsWith("icy:")))) {
			durationInMS = -1;
			handler = new Handler(this);
			httpStreamReceiver = new HttpStreamReceiver(handler, MSG_HTTP_STREAM_RECEIVER_ERROR, 0, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, MSG_HTTP_STREAM_RECEIVER_URL_UPDATED, MSG_HTTP_STREAM_RECEIVER_INFO, ++httpStreamReceiverVersion, Player.getBytesBeforeDecoding(Player.getBytesBeforeDecodingIndex()), Player.getSecondsBeforePlayback(Player.getSecondsBeforePlaybackIndex()), 1, path);
		} else {
			final File file = new File(path);
			if (!file.isFile() || !file.exists() || file.length() <= 0)
				throw new FileNotFoundException(path);
			if (!file.canRead())
				throw new SecurityException(path);
		}
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
			//enforce our policy
			if (internetStream)
				throw new UnsupportedOperationException("internet streams must used prepareAsync()");
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
					//only stereo files for now...
					if (channelCount != 2)
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
			prepareOutputBuffers();
			fillInputBuffers();
			state = STATE_PREPARED;
			break;
		default:
			throw new IllegalStateException("prepare() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void prepareAsync() {
		switch (state) {
		case STATE_PREPARING:
		case STATE_PREPARED:
		case STATE_STARTED:
		case STATE_PAUSED:
			break;
		case STATE_INITIALIZED:
			//enforce our policy
			if (!internetStream)
				throw new UnsupportedOperationException("streams other than internet streams must used prepare()");
			buffering = true;
			state = STATE_PREPARING;
			onInfo(INFO_BUFFERING_START, 0, null);
			if (!httpStreamReceiver.start())
				throw new IMediaPlayer.PermissionDeniedException();
			break;
		default:
			throw new IllegalStateException("prepareAsync() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void seekToAsync(int msec) {
		if (internetStream) {
			onSeekComplete();
			return;
		}
		stateBeforeSeek = state;
		switch (state) {
		case STATE_STARTED:
			pause();
		case STATE_PAUSED:
		case STATE_PREPARED:
			state = STATE_SEEKING;
			if (!MediaContext.seekToAsync(this, msec))
				onSeekComplete();
			break;
		default:
			throw new IllegalStateException("seekTo() - player was in an invalid state: " + state);
		}
	}

	@Override
	public void release() {
		reset();
		bufferInfo = null;
		completionListener = null;
		errorListener = null;
		infoListener = null;
		preparedListener = null;
		seekCompleteListener = null;
		state = STATE_END;
	}

	private void resetMedia() {
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
		buffering = false;
		httpStreamBufferingAfterPause = false;
		inputOver = false;
		outputOver = false;
		bufferInfo.flags = 0;
		outputBuffersHaveBeenUsed = false;
		sampleRate = 0;
		channelCount = 0;
		currentPositionInMS = 0;
		durationInMS = -1;
		inputBuffers = null;
		outputBuffers = null;
	}

	private void resetInternal() {
		if (httpStreamReceiver != null) {
			httpStreamReceiverVersion++;
			httpStreamReceiver.release();
			httpStreamReceiver = null;
		}
		resetMedia();
		path = null;
		handler = null;
		internetStream = false;
		stateBeforeSeek = STATE_IDLE;
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
		return 1;
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
	public int getHttpPosition() {
		//by doing like this, we do not need to synchronize the access to httpStreamReceiver
		final HttpStreamReceiver receiver = httpStreamReceiver;
		return ((receiver != null) ? receiver.bytesReceivedSoFar : -1);
	}

	@Override
	public void setVolume(float leftVolume, float rightVolume) {
		MediaContext.setVolumeInMillibels((leftVolume <= 0.0001f) ? MediaContext.SL_MILLIBEL_MIN : ((leftVolume >= 1.0f) ? 0 : (int)(2000.0 * Math.log(leftVolume))));
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
	public void setOnCompletionListener(OnCompletionListener listener) {
		completionListener = listener;
	}

	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		errorListener = listener;
	}

	@Override
	public void setOnInfoListener(OnInfoListener listener) {
		infoListener = listener;
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		preparedListener = listener;
	}

	@Override
	public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
		seekCompleteListener = listener;
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

	void mediaServerDied() {
		onError(new MediaServerDiedException(), 0);
	}

	void onCompletion() {
		state = STATE_PLAYBACKCOMPLETED;
		if (completionListener != null)
			completionListener.onCompletion(this);
	}

	void onError(Throwable exception, int errorCode) {
		inputOver = true;
		outputOver = true;
		state = STATE_ERROR;
		if (httpStreamReceiver != null) {
			httpStreamReceiverVersion++;
			httpStreamReceiver.release();
			httpStreamReceiver = null;
		}
		if (errorListener != null)
			errorListener.onError(this,
				((exception != null) && (exception instanceof MediaServerDiedException)) ? ERROR_SERVER_DIED :
					ERROR_UNKNOWN,
				((exception == null) ? errorCode :
					((exception instanceof FileNotFoundException) ? ERROR_NOT_FOUND :
						((exception instanceof UnsupportedFormatException) ? ERROR_UNSUPPORTED_FORMAT :
							((exception instanceof IOException) ? ERROR_IO : ERROR_UNKNOWN)))));
	}

	void onInfo(int what, int extra, Object extraObject) {
		if (infoListener != null)
			infoListener.onInfo(this, what, extra, extraObject);
	}

	void onPrepared() {
		state = STATE_PREPARED;
		if (preparedListener != null)
			preparedListener.onPrepared(this);
	}

	void onSeekComplete() {
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
}
