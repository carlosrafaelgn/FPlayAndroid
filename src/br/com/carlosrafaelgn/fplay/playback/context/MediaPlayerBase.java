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

import java.io.IOException;

@SuppressWarnings({"unused"})
public abstract class MediaPlayerBase {
	protected static final int MSG_HTTP_STREAM_RECEIVER_ERROR = 0x0100;
	protected static final int MSG_HTTP_STREAM_RECEIVER_PREPARED = 0x0101;
	protected static final int MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE = 0x0102;
	protected static final int MSG_HTTP_STREAM_RECEIVER_URL_UPDATED = 0x0103;
	protected static final int MSG_HTTP_STREAM_RECEIVER_INFO = 0x0104;
	protected static final int MSG_HTTP_STREAM_RECEIVER_BUFFERING = 0x0105;

	//what
	public static final int ERROR_UNKNOWN = 1; //MediaPlayer.MEDIA_ERROR_UNKNOWN
	public static final int ERROR_SERVER_DIED = 100; //MediaPlayer.MEDIA_ERROR_SERVER_DIED

	//extra
	public static final int ERROR_NOT_FOUND = 1001; //internal constant (not used by the original MediaPlayer class) used to indicate that the file has not been found
	public static final int ERROR_OUT_OF_MEMORY = 1002; //internal constant (not used by the original MediaPlayer class) used to indicate that there was not enough memory to fulfill the request
	public static final int ERROR_PERMISSION = 1003; //internal constant (not used by the original MediaPlayer class) used to indicate that a security-related error has occurred
	//public static final int ERROR_MALFORMED = -1007; //MediaPlayer.MEDIA_ERROR_MALFORMED
	public static final int ERROR_UNSUPPORTED_FORMAT = -1010; //MediaPlayer.MEDIA_ERROR_UNSUPPORTED
	public static final int ERROR_IO = -1004; //MediaPlayer.MEDIA_ERROR_IO
	public static final int ERROR_TIMED_OUT = -110; //MediaPlayer.MEDIA_ERROR_TIMED_OUT

	//what
	public static final int INFO_BUFFERING_START = 701; //MediaPlayer.MEDIA_INFO_BUFFERING_START
	public static final int INFO_BUFFERING_END = 702; //MediaPlayer.MEDIA_INFO_BUFFERING_END
	public static final int INFO_METADATA_UPDATE = 802; //MediaPlayer.MEDIA_INFO_METADATA_UPDATE
	public static final int INFO_URL_UPDATE = -802; //internal constant (not used by the original MediaPlayer class)
	public static final int INFO_HTTP_PREPARED = -803; //internal constant (not used by the original MediaPlayer class)

	public static final class TimeoutException extends Exception {
		private static final long serialVersionUID = 4571328670214281144L;
	}

	public static final class MediaServerDiedException extends Exception {
		private static final long serialVersionUID = -902099312236606175L;
	}

	public static final class FocusException extends Exception {
		private static final long serialVersionUID = 6158088015763157546L;
	}

	public static final class PermissionDeniedException extends SecurityException {
		private static final long serialVersionUID = 8743650824658438278L;
	}

	public static final class UnsupportedFormatException extends IOException {
		private static final long serialVersionUID = 7845932937323727492L;
	}

	public interface OnCompletionListener {
		void onCompletion(MediaPlayerBase mp);
	}

	public interface OnPreparedListener {
		void onPrepared(MediaPlayerBase mp);
	}

	public interface OnSeekCompleteListener {
		void onSeekComplete(MediaPlayerBase mp);
	}

	public interface OnInfoListener {
		boolean onInfo(MediaPlayerBase mp, int what, int extra, Object extraObject);
	}

	public interface OnErrorListener {
		boolean onError(MediaPlayerBase mp, int what, int extra);
	}

	public abstract int getSrcSampleRate();

	public abstract int getChannelCount();

	public abstract void start();

	public abstract void pause();

	public abstract void setDataSource(String path) throws IOException;

	public abstract void prepare() throws IOException;

	public abstract void prepareAsync();

	public abstract void seekToAsync(int msec);

	public abstract void release();

	public abstract void reset();

	public abstract int getAudioSessionId();

	public abstract void setAudioSessionId(int sessionId);

	public abstract int getDuration();

	public abstract int getCurrentPosition();

	public abstract int getHttpPosition();

	public abstract int getHttpFilledBufferSize();

	public abstract void setVolume(float leftVolume, float rightVolume);

	public abstract void setWakeMode(Context context, int mode);

	public abstract void setNextMediaPlayer(MediaPlayerBase next);

	public abstract void setOnCompletionListener(OnCompletionListener listener);

	public abstract void setOnErrorListener(OnErrorListener listener);

	public abstract void setOnInfoListener(OnInfoListener listener);

	public abstract void setOnPreparedListener(OnPreparedListener listener);

	public abstract void setOnSeekCompleteListener(OnSeekCompleteListener listener);
}
