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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.HttpStreamReceiver;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.util.BufferedMediaDataSource;

final class MediaPlayerWrapper extends MediaPlayerBase implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener, Handler.Callback {
	private final MediaPlayer player;
	private volatile boolean prepared;
	private OnCompletionListener completionListener;
	private OnErrorListener errorListener;
	private OnInfoListener infoListener;
	private OnPreparedListener preparedListener;
	private OnSeekCompleteListener seekCompleteListener;

	private Handler httpHandler;
	private boolean httpStreamReceiverActsLikePlayer;
	private int httpStreamReceiverVersion;
	private HttpStreamReceiver httpStreamReceiver;

	public MediaPlayerWrapper() {
		player = new MediaPlayer();
	}

	@Override
	public int getSrcSampleRate() {
		return 0;
	}

	@Override
	public int getChannelCount() {
		return 0;
	}

	@Override
	public boolean start() {
		if (httpStreamReceiverActsLikePlayer) {
			//every time httpStreamReceiver starts, it is a preparation!
			if (httpStreamReceiver.start(Player.getBytesBeforeDecoding(Player.getBytesBeforeDecodingIndex())))
				return true;
		} else {
			player.start();
		}
		return false;
	}

	@Override
	public void pause() {
		if (httpStreamReceiverActsLikePlayer)
			httpStreamReceiver.pause();
		else
			player.pause();
	}

	@Override
	public void setDataSource(String path) throws IOException {
		prepared = false;
		if (Song.isPathHttp(path)) {
			if (infoListener != null)
				infoListener.onInfo(this, INFO_BUFFERING_START, 0, null);
			httpHandler = new Handler(this);
			httpStreamReceiver = new HttpStreamReceiver(httpHandler, MSG_HTTP_STREAM_RECEIVER_ERROR, MSG_HTTP_STREAM_RECEIVER_PREPARED, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, MSG_HTTP_STREAM_RECEIVER_URL_UPDATED, 0, MSG_HTTP_STREAM_RECEIVER_BUFFERING, ++httpStreamReceiverVersion, Player.getBytesBeforeDecoding(Player.getBytesBeforeDecodingIndex()), Player.getMSBeforePlayback(Player.getMSBeforePlaybackIndex()), Player.audioSessionId, path);
			if (httpStreamReceiver.start(Player.getBytesBeforeDecoding(Player.getBytesBeforeDecodingIndex()))) {
				if ((httpStreamReceiverActsLikePlayer = httpStreamReceiver.isPerformingFullPlayback))
					return;
				if (infoListener != null)
					infoListener.onInfo(this, INFO_BUFFERING_END, 0, null);
				//Even though it happens very rarely, a few devices will freeze and produce an ANR
				//when calling setDataSource from the main thread :(
				player.setDataSource(httpStreamReceiver.getLocalURL());
			} else {
				//when start() returns false, this means we were unable to create the local server
				reset();
				throw new MediaPlayerBase.PermissionDeniedException();
			}
		} else {
			if (path.startsWith("file:")) {
				final Uri uri = Uri.parse(path);
				path = uri.getPath();
			}
			if (path.charAt(0) == File.separatorChar) {
				final File file = new File(path);
				ParcelFileDescriptor fileDescriptor = null;
				final int filePrefetchSize = Player.filePrefetchSize;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && filePrefetchSize > 0) {
					player.setDataSource(new BufferedMediaDataSource(path, file.length(), filePrefetchSize));
				} else {
					try {
						fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
						player.setDataSource(fileDescriptor.getFileDescriptor(), 0, fileDescriptor.getStatSize());
					} finally {
						try {
							if (fileDescriptor != null)
								fileDescriptor.close();
						} catch (Throwable ex) {
							//just ignore
						}
					}
				}
			} else {
				player.setDataSource(path);
			}
		}
	}

	private void setAudioAttributes() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			player.setAudioAttributes(new AudioAttributes.Builder()
				.setLegacyStreamType(AudioManager.STREAM_MUSIC)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.build());
		} else {
			//noinspection deprecation
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		}
	}

	@Override
	public void prepare() throws IOException {
		if (httpStreamReceiverActsLikePlayer)
			return;
		setAudioAttributes();
		prepared = false;
		player.prepare();
		prepared = true;
	}

	@Override
	public void prepareAsync() {
		if (httpStreamReceiverActsLikePlayer)
			return;
		setAudioAttributes();
		prepared = false;
		player.prepareAsync();
	}

	@Override
	public void seekToAsync(int msec) {
		if (httpStreamReceiver == null)
			player.seekTo(msec);
	}

	@Override
	public void release() {
		prepared = false;
		if (httpStreamReceiver != null) {
			httpStreamReceiverActsLikePlayer = false;
			httpHandler = null;
			httpStreamReceiverVersion++;
			httpStreamReceiver.release();
			httpStreamReceiver = null;
		}
		player.release();
		completionListener = null;
		errorListener = null;
		infoListener = null;
		preparedListener = null;
		seekCompleteListener = null;
	}

	@Override
	public void reset() {
		prepared = false;
		if (httpStreamReceiver != null) {
			httpStreamReceiverActsLikePlayer = false;
			httpHandler = null;
			httpStreamReceiverVersion++;
			httpStreamReceiver.release();
			httpStreamReceiver = null;
		}
		try {
			player.reset();
		} catch (Throwable ex) {
			//we will just ignore, since it is a rare case
			//when Player class called reset() on a player that
			//had already been released!
		}
	}

	@Override
	public int getAudioSessionId() {
		return player.getAudioSessionId();
	}

	@Override
	public void setAudioSessionId(int sessionId) {
		player.setAudioSessionId(sessionId);
	}

	@Override
	public int getDuration() {
		//calling getDuration() while the player is not
		//fully prepared causes exception on a few devices,
		//and causes error (-38,0) on others
		if (!prepared || httpStreamReceiver != null)
			return -1;
		try {
			return player.getDuration();
		} catch (Throwable ex) {
			return -1;
		}
	}

	@Override
	public int getCurrentPosition() {
		//calling getCurrentPosition() while the player is not
		//fully prepared causes exception on a few devices,
		//and causes error (-38,0) on others
		if (!prepared || httpStreamReceiver != null)
			return -1;
		try {
			final int position = player.getCurrentPosition();
			//sometimes, when the player is still loading, player.getCurrentPosition() returns weird values!
			return ((position <= 10800000 || position <= player.getDuration()) ? position : -1);
		} catch (Throwable ex) {
			return -1;
		}
	}

	@Override
	public int getHttpPosition() {
		return (httpStreamReceiver != null ? httpStreamReceiver.bytesReceivedSoFar : -1);
	}

	@Override
	public int getHttpFilledBufferSize() {
		return (httpStreamReceiver != null ? httpStreamReceiver.getFilledBufferSize() : 0);
	}

	@Override
	public void setVolume(float leftVolume, float rightVolume) {
		if (httpStreamReceiver == null)
			player.setVolume(leftVolume, rightVolume);
		else
			httpStreamReceiver.setVolume(leftVolume, rightVolume);
	}

	@Override
	public void setWakeMode(Context context, int mode) {
		player.setWakeMode(context, mode);
	}

	@Override
	public void setOnCompletionListener(OnCompletionListener listener) {
		completionListener = listener;
		player.setOnCompletionListener((listener == null) ? null : this);
	}

	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		errorListener = listener;
		player.setOnErrorListener((listener == null) ? null : this);
	}

	@Override
	public void setOnInfoListener(OnInfoListener listener) {
		infoListener = listener;
		player.setOnInfoListener((listener == null) ? null : this);
	}

	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		preparedListener = listener;
		player.setOnPreparedListener((listener == null) ? null : this);
	}

	@Override
	public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
		seekCompleteListener = listener;
		player.setOnSeekCompleteListener((listener == null) ? null : this);
	}

	@Override
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void setNextMediaPlayer(MediaPlayerBase next) {
		if (httpStreamReceiver == null)
			player.setNextMediaPlayer((next == null) ? null : ((MediaPlayerWrapper)next).player);
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		prepared = false;
		if (completionListener != null)
			completionListener.onCompletion(this);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		prepared = false;
		return ((errorListener != null) && errorListener.onError(this, what, extra));
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		return ((infoListener != null) && infoListener.onInfo(this, what, extra, null));
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		prepared = true;
		if (preparedListener != null)
			preparedListener.onPrepared(this);
	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (seekCompleteListener != null)
			seekCompleteListener.onSeekComplete(this);
	}

	@Override
	public boolean handleMessage(Message msg) {
		if (msg.arg1 != httpStreamReceiverVersion)
			return true;
		switch (msg.what) {
		case MSG_HTTP_STREAM_RECEIVER_ERROR:
			//_httpStreamReceiverError(msg.arg1, (msg.obj instanceof Throwable) ? (Throwable)msg.obj : null, msg.arg2);
			if (Player.state != Player.STATE_ALIVE)
				break;
			reset();
			Throwable exception = ((msg.obj instanceof Throwable) ? (Throwable)msg.obj : null);
			int errorCode = msg.arg2;
			if (!Player.isConnectedToTheInternet()) {
				exception = null;
				errorCode = MediaPlayerBase.ERROR_NOT_FOUND;
			}
			if (errorListener != null)
				errorListener.onError(this,
					((exception != null) && (exception instanceof MediaPlayerBase.MediaServerDiedException)) ? MediaPlayerBase.ERROR_SERVER_DIED :
						MediaPlayerBase.ERROR_UNKNOWN,
					((exception == null) ? errorCode :
						((exception instanceof MediaPlayerBase.TimeoutException) ? MediaPlayerBase.ERROR_TIMED_OUT :
							((exception instanceof MediaPlayerBase.PermissionDeniedException) ? MediaPlayerBase.ERROR_PERMISSION :
								((exception instanceof OutOfMemoryError) ? MediaPlayerBase.ERROR_OUT_OF_MEMORY :
									((exception instanceof FileNotFoundException) ? MediaPlayerBase.ERROR_NOT_FOUND :
										((exception instanceof MediaPlayerBase.UnsupportedFormatException) ? MediaPlayerBase.ERROR_UNSUPPORTED_FORMAT :
											((exception instanceof IOException) ? MediaPlayerBase.ERROR_IO : MediaPlayerBase.ERROR_UNKNOWN))))))));
			break;
		case MSG_HTTP_STREAM_RECEIVER_PREPARED:
			if (infoListener != null) {
				if (httpStreamReceiverActsLikePlayer)
					prepared = true;
				infoListener.onInfo(this, INFO_BUFFERING_END, 0, null);
				infoListener.onInfo(this, INFO_HTTP_PREPARED, 0, null);
			}
			break;
		case MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE:
			if (msg.obj != null && infoListener != null)
				infoListener.onInfo(this, INFO_METADATA_UPDATE, 0, msg.obj);
			break;
		case MSG_HTTP_STREAM_RECEIVER_URL_UPDATED:
			if (msg.obj != null && infoListener != null)
				infoListener.onInfo(this, INFO_URL_UPDATE, 0, msg.obj);
			break;
		case MSG_HTTP_STREAM_RECEIVER_BUFFERING:
			if (infoListener != null)
				infoListener.onInfo(this, msg.arg2 == 0 ? INFO_BUFFERING_END : INFO_BUFFERING_START, 0, null);
			break;
		}
		return true;
	}
}
