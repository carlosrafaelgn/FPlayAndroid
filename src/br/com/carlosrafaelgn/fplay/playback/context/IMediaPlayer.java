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

public interface IMediaPlayer {
	//what
	int ERROR_UNKNOWN = 1; //MediaPlayer.MEDIA_ERROR_UNKNOWN
	int ERROR_SERVER_DIED = 100; //MediaPlayer.MEDIA_ERROR_SERVER_DIED

	//extra
	int ERROR_NOT_FOUND = 1001; //1001 = internal constant (not used by original MediaPlayer class) used to indicate that the file has not been found
	int ERROR_MALFORMED = -1007; //MediaPlayer.MEDIA_ERROR_MALFORMED
	int ERROR_UNSUPPORTED_FORMAT = -1010; //MediaPlayer.MEDIA_ERROR_UNSUPPORTED
	int ERROR_IO = -1004; //MediaPlayer.MEDIA_ERROR_IO
	int ERROR_TIMED_OUT = -110; //MediaPlayer.MEDIA_ERROR_TIMED_OUT

	//what
	int INFO_BUFFERING_START = 701; //MediaPlayer.MEDIA_INFO_BUFFERING_START
	int INFO_BUFFERING_END = 702; //MediaPlayer.MEDIA_INFO_BUFFERING_END
	int INFO_METADATA_UPDATE = 802; //MediaPlayer.MEDIA_INFO_METADATA_UPDATE

	final class TimeoutException extends Exception {
		private static final long serialVersionUID = 4571328670214281144L;
	}

	final class MediaServerDiedException extends Exception {
		private static final long serialVersionUID = -902099312236606175L;
	}

	final class FocusException extends Exception {
		private static final long serialVersionUID = 6158088015763157546L;
	}

	final class PermissionDeniedException extends SecurityException {
		private static final long serialVersionUID = 8743650824658438278L;
	}

	final class UnsupportedFormatException extends IOException {
		private static final long serialVersionUID = 7845932937323727492L;
	}

	interface OnCompletionListener {
		void onCompletion(IMediaPlayer mp);
	}

	interface OnPreparedListener {
		void onPrepared(IMediaPlayer mp);
	}

	interface OnSeekCompleteListener {
		void onSeekComplete(IMediaPlayer mp);
	}

	interface OnBufferingUpdateListener {
		void onBufferingUpdate(IMediaPlayer mp, int percent);
	}

	interface OnInfoListener {
		boolean onInfo(IMediaPlayer mp, int what, int extra);
	}

	interface OnErrorListener {
		boolean onError(IMediaPlayer mp, int what, int extra);
	}

	void start();

	void stop();

	void pause();

	void setDataSource(String path) throws IOException;

	void prepare() throws IOException;

	void prepareAsync();

	void seekToAsync(int msec);

	void release();

	void reset();

	int getAudioSessionId();

	void setAudioSessionId(int sessionId);

	int getDuration();

	int getCurrentPosition();

	boolean isPlaying();

	void setVolume(float leftVolume, float rightVolume);

	void setAudioStreamType(int streamtype);

	void setWakeMode(Context context, int mode);

	void setNextMediaPlayer(IMediaPlayer next);

	void setOnBufferingUpdateListener(OnBufferingUpdateListener listener);

	void setOnCompletionListener(OnCompletionListener listener);

	void setOnErrorListener(OnErrorListener listener);

	void setOnInfoListener(OnInfoListener listener);

	void setOnPreparedListener(OnPreparedListener listener);

	void setOnSeekCompleteListener(OnSeekCompleteListener listener);
}
