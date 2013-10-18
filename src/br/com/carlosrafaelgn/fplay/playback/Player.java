//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013, Carlos Rafael Gimenes das Neves
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
package br.com.carlosrafaelgn.fplay.playback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.widget.RemoteViews;
import br.com.carlosrafaelgn.fplay.ExternalReceiver;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.WidgetMain;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.list.SongList;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;
import br.com.carlosrafaelgn.fplay.util.Timer;

//
//MediaPlayer CALLBACKS ARE CALLED ON THE THREAD WHERE THE PLAYER IS CREATED (WHICH
//IS THE MAIN THREAD, IN OUR CASE) THEREFORE, THERE IS NO NEED TO SYNCHRONIZE CALLS
//AS LONG AS previous(), play(), playPause() AND next() ARE ALWAYS CALLED FROM THE
//MAIN THREAD!!!
//http://stackoverflow.com/questions/3919089/do-callbacks-occur-on-the-main-ui-thread
//http://stackoverflow.com/questions/11066186/how-does-android-perform-callbacks-on-my-thread
//

//
//Android Audio: Play an MP3 file on an AudioTrack
//http://mindtherobot.com/blog/624/android-audio-play-an-mp3-file-on-an-audiotrack/
//
//Android Audio: Play a WAV file on an AudioTrack
//http://mindtherobot.com/blog/580/android-audio-play-a-wav-file-on-an-audiotrack/
//
//Equalizer
//http://developer.android.com/reference/android/media/audiofx/Equalizer.html
//
//Media Playback
//http://developer.android.com/guide/topics/media/mediaplayer.html
//
//MediaPlayer (including state diagram)
//http://developer.android.com/reference/android/media/MediaPlayer.html
//
//AudioTrack
//http://developer.android.com/reference/android/media/AudioTrack.html
//
//Allowing applications to play nice(r) with each other: Handling remote control buttons
//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
//
public final class Player extends Service implements Timer.TimerHandler, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener, ArraySorter.Comparer<FileSt> {
	public static interface PlayerObserver {
		public void onPlayerChanged(boolean onlyPauseChanged, Throwable ex);
		public void onPlayerControlModeChanged(boolean controlMode);
	}
	
	private static class TimeoutException extends Exception {
		private static final long serialVersionUID = 4571328670214281144L;
	}
	
	private static class MediaServerDiedException extends Exception {
		private static final long serialVersionUID = -902099312236606175L;
	}
	
	private static class FocusException extends Exception {
		private static final long serialVersionUID = 6158088015763157546L;
	}
	
	public static final String ACTION_PREVIOUS = "br.com.carlosrafaelgn.FPlay.PREVIOUS";
	public static final String ACTION_PLAY_PAUSE = "br.com.carlosrafaelgn.FPlay.PLAY_PAUSE";
	public static final String ACTION_NEXT = "br.com.carlosrafaelgn.FPlay.NEXT";
	public static final int MIN_VOLUME_DB = -4000;
	private static final int OPT_VOLUME = 0x0000;
	private static final int OPT_CONTROLMODE = 0x0001;
	private static final int OPT_LASTTIME = 0x0002;
	private static final int OPT_PATH = 0x0003;
	private static final int OPT_ORIGINALPATH = 0x0004;
	private static final int OPT_FAVORITEFOLDERCOUNT = 0x0005;
	private static final int OPT_BASSBOOSTMODE = 0x0006;
	private static final int OPT_FADEININCREMENTONFOCUS = 0x0007;
	private static final int OPT_FADEININCREMENTONPAUSE = 0x0008;
	private static final int OPT_FADEININCREMENTONOTHER = 0x0009;
	private static final int OPT_NEXTPREPARATION = 0x000A;
	private static final int OPT_PLAYFOLDERCLEARSLIST = 0x000B;
	private static final int OPT_KEEPSCREENON = 0x000C;
	private static final int OPT_FORCEDORIENTATION = 0x000D;
	private static final int OPT_DISPLAYVOLUMEINDB = 0x000E;
	private static final int OPT_DOUBLECLICKMODE = 0x000F;
	private static final int OPT_MSGADDSHOWN = 0x0010;
	private static final int OPT_MSGPLAYSHOWN = 0x0011;
	private static final int OPT_MSGSTARTUPSHOWN = 0x0012;
	private static final int OPT_MARQUEETITLE = 0x0013;
	private static final int OPT_FAVORITEFOLDER0 = 0x10000;
	private static final int SILENCE_NORMAL = 0;
	private static final int SILENCE_FOCUS = 1;
	private static final int SILENCE_NONE = -1;
	private static String startCommand;
	private static boolean playing, wasPlayingBeforeFocusLoss, currentSongLoaded, playAfterSeek, unpaused, currentSongPreparing, controlMode, startRequested, stopRequested, prepareNextOnSeek, nextPreparing, nextPrepared, nextAlreadySetForPlaying, initialized, deserialized, hasFocus, dimmedVolume, listLoaded, reviveAlreadyRetried;
	private static float volume = 1, actualVolume = 1, volumeDBMultiplier;
	private static int volumeDB, lastTime = -1, lastHow, silenceMode;
	private static Player thePlayer;
	private static Song currentSong, nextSong, firstError;
	private static MediaPlayer currentPlayer, nextPlayer;
	private static NotificationManager notificationManager;
	private static AudioManager audioManager;
	private static Timer focusDelayTimer, prepareDelayTimer, volumeTimer;
	private static ComponentName mediaButtonEventReceiver;
	public static PlayerObserver observer;
	public static final SongList songs = SongList.getInstance();
	//keep these instances here to prevent MainHandler, Equalizer and BassBoost
	//classes from being garbage collected...
	public static final MainHandler _mainHandler = new MainHandler();
	public static final Equalizer _equalizer = new Equalizer();
	public static final BassBoost _bassBoost = new BassBoost();
	//keep these three fields here, instead of in ActivityMain/ActivityBrowser,
	//so they will survive their respective activity's destruction
	//(and even the class garbage collection)
	private static HashSet<String> favoriteFolders;
	public static String path, originalPath;
	public static int lastCurrent = -1, listFirst = -1, listTop = 0, positionToSelect = -1, fadeInIncrementOnFocus, fadeInIncrementOnPause, fadeInIncrementOnOther, forcedOrientation;
	public static boolean isMainActiveOnTop, alreadySelected, bassBoostMode, nextPreparationEnabled, clearListWhenPlayingFolders, keepScreenOn, displayVolumeInDB, doubleClickMode, marqueeTitle, msgAddShown, msgPlayShown, msgStartupShown;
	
	private static void initialize(Context context) {
		if (!initialized) {
			initialized = true;
			MainHandler.initialize(context);
			if (notificationManager == null)
				notificationManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
			if (audioManager == null)
				audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);
			SerializableMap opts = SerializableMap.deserialize(context, "_Player");
			if (opts == null)
				opts = new SerializableMap();
			setVolumeDB(opts.getInt(OPT_VOLUME), true);
			controlMode = opts.getBoolean(OPT_CONTROLMODE);
			path = opts.getString(OPT_PATH);
			originalPath = opts.getString(OPT_ORIGINALPATH);
			lastTime = opts.getInt(OPT_LASTTIME, -1);
			bassBoostMode = opts.getBoolean(OPT_BASSBOOSTMODE);
			fadeInIncrementOnFocus = opts.getInt(OPT_FADEININCREMENTONFOCUS, 125);
			fadeInIncrementOnPause = opts.getInt(OPT_FADEININCREMENTONPAUSE, 125);
			fadeInIncrementOnOther = opts.getInt(OPT_FADEININCREMENTONOTHER, 0);
			nextPreparationEnabled = opts.getBoolean(OPT_NEXTPREPARATION, true);
			clearListWhenPlayingFolders = opts.getBoolean(OPT_PLAYFOLDERCLEARSLIST);
			keepScreenOn = opts.getBoolean(OPT_KEEPSCREENON, true);
			forcedOrientation = opts.getInt(OPT_FORCEDORIENTATION);
			displayVolumeInDB = opts.getBoolean(OPT_DISPLAYVOLUMEINDB);
			doubleClickMode = opts.getBoolean(OPT_DOUBLECLICKMODE);
			marqueeTitle = opts.getBoolean(OPT_MARQUEETITLE, true);
			msgAddShown = opts.getBoolean(OPT_MSGADDSHOWN);
			msgPlayShown = opts.getBoolean(OPT_MSGPLAYSHOWN);
			msgStartupShown = opts.getBoolean(OPT_MSGSTARTUPSHOWN);
			int count = opts.getInt(OPT_FAVORITEFOLDERCOUNT);
			if (count > 0) {
				if (count > 128)
					count = 128;
				favoriteFolders = new HashSet<String>(count);
				for (int i = count - 1; i >= 0; i--) {
					final String f = opts.getString(OPT_FAVORITEFOLDER0 + i);
					if (f != null && f.length() > 1)
						favoriteFolders.add(f);
				}
			}
			Equalizer.loadConfig(opts);
			BassBoost.loadConfig(opts);
			if (favoriteFolders == null)
				favoriteFolders = new HashSet<String>(8);
		}
		if (thePlayer != null) {
			registerMediaButtonEventReceiver();
			if (focusDelayTimer == null) {
				focusDelayTimer = new Timer(thePlayer, "Player Focus Delay Timer");
				focusDelayTimer.setHandledOnMain(true);
			}
			if (prepareDelayTimer == null) {
				prepareDelayTimer = new Timer(thePlayer, "Player Prepare Delay Timer");
				prepareDelayTimer.setHandledOnMain(true);
			}
			if (volumeTimer == null) {
				volumeTimer = new Timer(thePlayer, "Player Volume Timer");
				volumeTimer.setHandledOnMain(true);
				volumeTimer.setCompensatingForDelays(true);
			}
			if (!deserialized) {
				deserialized = true;
				songs.startDeserializing(context, null, true, false, false);
			}
		}
	}
	
	private static void terminate(Context context) {
		if (initialized) {
			if (focusDelayTimer != null) {
				focusDelayTimer.stop();
				focusDelayTimer = null;
			}
			if (prepareDelayTimer != null) {
				prepareDelayTimer.stop();
				prepareDelayTimer = null;
			}
			if (volumeTimer != null) {
				volumeTimer.stop();
				volumeTimer = null;
			}
			initialized = false;
			SerializableMap opts = new SerializableMap(32);
			opts.put(OPT_VOLUME, volumeDB);
			opts.put(OPT_CONTROLMODE, controlMode);
			opts.put(OPT_PATH, path);
			opts.put(OPT_ORIGINALPATH, originalPath);
			opts.put(OPT_LASTTIME, lastTime);
			opts.put(OPT_BASSBOOSTMODE, bassBoostMode);
			opts.put(OPT_FADEININCREMENTONFOCUS, fadeInIncrementOnFocus);
			opts.put(OPT_FADEININCREMENTONPAUSE, fadeInIncrementOnPause);
			opts.put(OPT_FADEININCREMENTONOTHER, fadeInIncrementOnOther);
			opts.put(OPT_NEXTPREPARATION, nextPreparationEnabled);
			opts.put(OPT_PLAYFOLDERCLEARSLIST, clearListWhenPlayingFolders);
			opts.put(OPT_KEEPSCREENON, keepScreenOn);
			opts.put(OPT_FORCEDORIENTATION, forcedOrientation);
			opts.put(OPT_DISPLAYVOLUMEINDB, displayVolumeInDB);
			opts.put(OPT_DOUBLECLICKMODE, doubleClickMode);
			opts.put(OPT_MARQUEETITLE, marqueeTitle);
			opts.put(OPT_MSGADDSHOWN, msgAddShown);
			opts.put(OPT_MSGPLAYSHOWN, msgPlayShown);
			opts.put(OPT_MSGSTARTUPSHOWN, msgStartupShown);
			if (favoriteFolders != null && favoriteFolders.size() > 0) {
				opts.put(OPT_FAVORITEFOLDERCOUNT, favoriteFolders.size());
				int i = 0;
				for (String f : favoriteFolders) {
					opts.put(OPT_FAVORITEFOLDER0 + i, f);
					i++;
				}
			} else {
				opts.put(OPT_FAVORITEFOLDERCOUNT, 0);
			}
			Equalizer.saveConfig(opts);
			BassBoost.saveConfig(opts);
			opts.serialize(context, "_Player");
			songs.serialize(context, null);
			abandonFocus();
		}
	}
	
	@Override
	public int compare(FileSt a, FileSt b) {
		return a.name.compareToIgnoreCase(b.name);
	}
	
	public static void addFavoriteFolder(String path) {
		synchronized (favoriteFolders) {
			favoriteFolders.add(path);
		}
	}
	
	public static void removeFavoriteFolder(String path) {
		synchronized (favoriteFolders) {
			favoriteFolders.remove(path);
		}
	}
	
	public static boolean isFavoriteFolder(String path) {
		synchronized (favoriteFolders) {
			return favoriteFolders.contains(path);
		}
	}
	
	public static FileSt[] getFavoriteFolders(int extra) {
		FileSt[] ffs;
		synchronized (favoriteFolders) {
			final int count = favoriteFolders.size();
			if (count == 0)
				return new FileSt[extra];
			ffs = new FileSt[count + extra];
			int i = 0;
			for (String f : favoriteFolders) {
				final int idx = f.lastIndexOf('/');
				ffs[i] = new FileSt(f, (idx >= 0 && idx < (f.length() - 1)) ? f.substring(idx + 1) : f, FileSt.TYPE_FAVORITE);
				i++;
			}
		}
		ArraySorter.sort(ffs, 0, ffs.length - extra, thePlayer);
		return ffs;
	}
	
	public static void setSelectionAfterAdding(int positionToSelect) {
		if (!alreadySelected) {
			alreadySelected = true;
			if (!songs.selecting && !songs.moving)
				songs.setSelection(positionToSelect, false);
			if (!isMainActiveOnTop)
				Player.positionToSelect = positionToSelect;
		}
	}
	
	private static void initializePlayers() {
		if (currentPlayer == null) {
			currentPlayer = createPlayer();
			if (nextPlayer != null) {
				currentPlayer.setAudioSessionId(nextPlayer.getAudioSessionId());
			} else {
				nextPlayer = createPlayer();
				nextPlayer.setAudioSessionId(currentPlayer.getAudioSessionId());
			}
		} else if (nextPlayer == null) {
			nextPlayer = createPlayer();
			nextPlayer.setAudioSessionId(currentPlayer.getAudioSessionId());
		}
		Equalizer.release();
		BassBoost.release();
		Equalizer.initialize(currentPlayer.getAudioSessionId());
		BassBoost.initialize(currentPlayer.getAudioSessionId());
	}
	
	private static boolean requestFocus() {
		if (thePlayer == null || audioManager == null || audioManager.requestAudioFocus(thePlayer, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			hasFocus = false;
			return false;
		}
		registerMediaButtonEventReceiver();
		hasFocus = true;
		return true;
	}
	
	private static void abandonFocus() {
		if (audioManager != null && thePlayer != null)
			audioManager.abandonAudioFocus(thePlayer);
		hasFocus = false;
	}
	
	private static void setLastTime() {
		try {
			if (currentPlayer != null && currentSongLoaded && playing)
				lastTime = currentPlayer.getCurrentPosition();
		} catch (Throwable ex) {
		}
	}
	
	private static void broadcastStateChange(boolean onlyPauseChanged) {
		//
		//perhaps, one day we should implement RemoteControlClient
		//for better Bluetooth support...? (as of Aug/2013, the native
		//player did not seem to implement it...)
		//http://developer.android.com/reference/android/media/RemoteControlClient.html
		//https://android.googlesource.com/platform/packages/apps/Music/+/master/src/com/android/music/MediaPlaybackService.java
		//
		//http://stackoverflow.com/questions/15527614/send-track-informations-via-a2dp-avrcp
		//http://stackoverflow.com/questions/14536597/how-does-the-android-lockscreen-get-playing-song
		//http://stackoverflow.com/questions/10510292/how-to-get-current-music-track-info
		if (currentSong == null) {
			thePlayer.sendBroadcast(new Intent("com.android.music.playbackcomplete"));
		} else {
			final Intent i = new Intent(onlyPauseChanged ? "com.android.music.playstatechanged" : "com.android.music.metachanged");
			i.putExtra("id", currentSong.id);
			i.putExtra("songid", currentSong.id);
	        i.putExtra("artist", currentSong.artist);
	        i.putExtra("album", currentSong.album);
	        i.putExtra("track", currentSong.title);
	        i.putExtra("duration", (long)currentSong.lengthMS);
	        i.putExtra("position", (long)0);
	        i.putExtra("playing", playing);
	        thePlayer.sendBroadcast(i);
		}
	}
	
	public static RemoteViews prepareRemoteViews(Context context, RemoteViews views, boolean prepareButtons, boolean notification) {
		if (currentSongPreparing)
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.loading));
		else if (currentSong == null)
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.nothing_playing));
		else
			views.setTextViewText(R.id.lblTitle, currentSong.title);
		
		if (prepareButtons) {
			if (notification) {
				UI.prepareNotificationPlaybackIcons(context);
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrevNotif);
				views.setImageViewBitmap(R.id.btnPlay, playing ? UI.icPauseNotif : UI.icPlayNotif);
				views.setImageViewBitmap(R.id.btnNext, UI.icNextNotif);
			} else {
				UI.preparePlaybackIcons(context);
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrev);
				views.setImageViewBitmap(R.id.btnPlay, playing ? UI.icPause : UI.icPlay);
				views.setImageViewBitmap(R.id.btnNext, UI.icNext);
			}
			
			Intent intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_PREVIOUS);
			views.setOnClickPendingIntent(R.id.btnPrev, PendingIntent.getService(context, 0, intent, 0));
			
			intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_PLAY_PAUSE);
			views.setOnClickPendingIntent(R.id.btnPlay, PendingIntent.getService(context, 0, intent, 0));
			
			intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_NEXT);
			views.setOnClickPendingIntent(R.id.btnNext, PendingIntent.getService(context, 0, intent, 0));
		}		
		return views;
	}
	
	public static PendingIntent getPendingIntent(Context context) {
		final Intent intent = new Intent(context, ActivityHost.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return PendingIntent.getActivity(context, 0, intent, 0);
	}
	
	private static Notification getNotification() {
		final Notification notification = new Notification();
		notification.icon = R.drawable.ic_notification;
		notification.when = 0;
		notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
		notification.contentIntent = getPendingIntent(thePlayer);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			notification.contentView = prepareRemoteViews(thePlayer, new RemoteViews(thePlayer.getPackageName(), R.layout.notification), true, true);
		else
			notification.contentView = prepareRemoteViews(thePlayer, new RemoteViews(thePlayer.getPackageName(), R.layout.notification_simple), false, true);
		return notification;
	}
	
	public static void onPlayerChanged(boolean onlyPauseChanged, Song newCurrentSong, Throwable ex) {
		if (thePlayer != null) {
			if (newCurrentSong != null)
				currentSong = newCurrentSong;
			notificationManager.notify(1, getNotification());
			WidgetMain.updateWidgets(thePlayer, onlyPauseChanged);
			broadcastStateChange(onlyPauseChanged);
			if (ex != null) {
				final String msg = ex.getMessage();
				if (ex instanceof IllegalStateException) {
					UI.toast(thePlayer, R.string.error_state);
				} else if (ex instanceof IOException) {
					UI.toast(thePlayer, R.string.error_io);
				} else if (ex instanceof FileNotFoundException) {
					UI.toast(thePlayer, R.string.error_file_not_found);
				} else if (ex instanceof TimeoutException) {
					UI.toast(thePlayer, R.string.error_timeout);
				} else if (ex instanceof MediaServerDiedException) {
					UI.toast(thePlayer, R.string.error_server_died);
				} else if (ex instanceof SecurityException) {
					UI.toast(thePlayer, R.string.error_security);
				} else if (msg == null || msg.length() == 0) {
					UI.toast(thePlayer, R.string.error_playback);
				} else {
					final StringBuilder sb = new StringBuilder(thePlayer.getText(R.string.error_msg));
					sb.append(msg);
					UI.toast(thePlayer, sb.toString());
				}
			}
			if (observer != null)
				observer.onPlayerChanged(onlyPauseChanged, ex);
		}
	}
	
	public static void onSongListDeserialized(Song newCurrentSong, int forcePlayIdx, int positionToSelect, Throwable ex) {
		if (positionToSelect >= 0)
			setSelectionAfterAdding(positionToSelect);
		onPlayerChanged(false, newCurrentSong, ex);
		listLoaded = true;
		executeStartCommand(forcePlayIdx);
	}
	
	private static void updateState(final boolean onlyPauseChanged, final Throwable ex) {
		if (MainHandler.isOnMainThread()) {
			onPlayerChanged(onlyPauseChanged, null, ex);
		} else {
			MainHandler.post(new Runnable() {
				@Override
				public void run() {
					onPlayerChanged(onlyPauseChanged, null, ex);
				}
			});
		}
	}
	
	private static MediaPlayer createPlayer() {
		MediaPlayer mp = new MediaPlayer();
		mp.setOnErrorListener(thePlayer);
		mp.setOnPreparedListener(thePlayer);
		mp.setOnSeekCompleteListener(thePlayer);
		mp.setOnCompletionListener(thePlayer);
		mp.setWakeMode(thePlayer, PowerManager.PARTIAL_WAKE_LOCK);
		return mp;
	}
	
	private static void preparePlayer(MediaPlayer mp, Song song) throws Throwable {
		mp.reset();
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
		if (song.path == null || song.path.length() == 0)
			throw new IOException();
		mp.setDataSource(song.path);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			clearNextPlayer(mp);
		mp.prepareAsync();
	}
	
	private static void startPlayer(MediaPlayer mp) {
		if (silenceMode != SILENCE_NONE) {
			final int incr = (unpaused ? fadeInIncrementOnPause : ((silenceMode == SILENCE_FOCUS) ? fadeInIncrementOnFocus : fadeInIncrementOnOther));
			if (incr > 30) {
				actualVolume = 0;
				volumeDBMultiplier = MIN_VOLUME_DB;
				volumeTimer.start(50, false, incr);
			}
		}
		unpaused = false;
		silenceMode = SILENCE_NONE;
		if (mp != null) {
			mp.setVolume(actualVolume, actualVolume);
			mp.start();
		}
	}
	
	private static void stopPlayer(MediaPlayer mp) {
		mp.reset();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			clearNextPlayer(mp);
	}
	
	private static void releasePlayer(MediaPlayer mp) {
		mp.setOnErrorListener(null);
		mp.setOnPreparedListener(null);
		mp.setOnSeekCompleteListener(null);
		mp.setOnCompletionListener(null);
		stopPlayer(mp);
		mp.release();
	}
	
	private static void releaseInternal() {
		Equalizer.release();
		BassBoost.release();
		currentSongLoaded = false;
		if (currentPlayer != null) {
			releasePlayer(currentPlayer);
			currentPlayer = null;
		}
		nextPreparing = false;
		nextPrepared = false;
		if (nextPlayer != null) {
			releasePlayer(nextPlayer);
			nextPlayer = null;
		}
	}
	
	private static void nextFailed(Song failedSong, int how, Throwable ex) {
		if (firstError == failedSong || how != SongList.HOW_NEXT_AUTO) {
			firstError = null;
			updateState(false, ex);
		} else {
			if (firstError == null)
				firstError = failedSong;
			updateState(false, null);
			MainHandler.post(new Runnable() {
				@Override
				public void run() {
					playInternal(SongList.HOW_NEXT_AUTO);
				}
			});
		}
	}
	
	private static boolean doPlayInternal(int how, Song song) {
		stopInternal(null);
		if (how != SongList.HOW_CURRENT)
			lastTime = -1;
		try {
			lastHow = how;
			preparePlayer(currentPlayer, song);
			currentSongPreparing = true;
		} catch (Throwable ex) {
			song.possibleNextSong = null;
			fullCleanup(song);
			nextFailed(song, how, ex);
			return false;
		}
		currentSong = song;
		return true;
	}
	
	private static void playInternal(int how) {
		if (!hasFocus && !requestFocus()) {
			unpaused = false;
			playing = false;
			wasPlayingBeforeFocusLoss = false;
			currentSongLoaded = false;
			updateState(false, new FocusException());
			return;
		}
		final Song s = songs.getSongAndSetCurrent(how);
		if (s == null) {
			lastTime = -1;
			firstError = null;
			fullCleanup(null);
			updateState(false, null);
			return;
		}
		if (currentPlayer == null || nextPlayer == null)
			initializePlayers();
		boolean prepareNext = false;
		if (nextSong == s && how != SongList.HOW_CURRENT) {
			boolean ok = false;
			lastTime = -1;
			if (nextPrepared) {
				//stopPlayer(currentPlayer); //####
				nextPrepared = false;
				try {
					if (!nextAlreadySetForPlaying || how != SongList.HOW_NEXT_AUTO)
						startPlayer(nextPlayer);
					else
						nextPlayer.setVolume(actualVolume, actualVolume);
					nextAlreadySetForPlaying = false;
					currentSongLoaded = true;
					firstError = null;
					stopPlayer(currentPlayer); //####
					prepareNext = true;
					ok = true;
					currentSongPreparing = false;
				} catch (Throwable ex) {
					s.possibleNextSong = null;
					fullCleanup(s);
					nextFailed(s, how, ex);
					return;
				}
			} else if (nextPreparing) {
				lastHow = how;
				currentSongLoaded = false;
				currentSongPreparing = true;
				ok = true;
			}
			currentSong = s;
			final MediaPlayer p = currentPlayer;
			currentPlayer = nextPlayer;
			nextPlayer = p;
			if (!ok && !doPlayInternal(how, s))
				return;
		} else {
			if (!doPlayInternal(how, s))
				return;
		}
		nextSong = s.possibleNextSong;
		s.possibleNextSong = null;
		if (nextSong == currentSong)
			nextSong = null;
		else if (prepareNext)
			processPreparation();
		playing = currentSongLoaded;
		//wasPlaying = true;
		updateState(false, null);
	}
	
	private static void stopInternal(Song newCurrentSong) {
		currentSong = newCurrentSong;
		nextSong = null;
		playing = false;
		currentSongLoaded = false;
		currentSongPreparing = false;
		prepareNextOnSeek = false;
		nextPreparing = false;
		nextPrepared = false;
		nextAlreadySetForPlaying = false;
		if (prepareDelayTimer != null)
			prepareDelayTimer.stop();
		if (currentPlayer != null)
			stopPlayer(currentPlayer);
		if (nextPlayer != null)
			stopPlayer(nextPlayer);
	}
	
	private static void fullCleanup(Song newCurrentSong) {
		wasPlayingBeforeFocusLoss = false;
		unpaused = false;
		silenceMode = SILENCE_NORMAL;
		stopInternal(newCurrentSong);
		Player.abandonFocus();
	}
	
	public static void becomingNoisy() {
		//this cleanup must be done, as sometimes, when changing between two output types,
		//the effects are lost...
		if (playing)
			playPause();
		stopInternal(currentSong);
		releaseInternal();
	}
	
	public static void previous() {
		if (thePlayer == null)
			return;
		playInternal(SongList.HOW_PREVIOUS);
	}
	
	public static void playPause() {
		if (thePlayer == null)
			return;
		if (currentSong == null || !currentSongLoaded || !hasFocus) {
			unpaused = true;
			playInternal(SongList.HOW_CURRENT);
		} else {
			try {
				if (playing) {
					currentPlayer.pause();
					silenceMode = SILENCE_NORMAL;
					setLastTime();
				} else {
					unpaused = true;
					startPlayer(currentPlayer);
				}
				playing = !playing;
				//wasPlaying = playing;
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				updateState(false, ex);
				return;
			}
			updateState(true, null);
		}
	}
	
	public static void play(int index) {
		if (thePlayer == null)
			return;
		playInternal(index);
	}
	
	public static void next() {
		if (thePlayer == null)
			return;
		playInternal(SongList.HOW_NEXT_MANUAL);
	}
	
	public static void seekTo(int timeMS, boolean play) {
		if (!currentSongLoaded) {
			lastTime = timeMS;
		} else if (currentPlayer != null) {
			lastTime = timeMS;
			playAfterSeek = play;
			currentSongPreparing = true;
			currentPlayer.seekTo(timeMS);
			updateState(false, null);
		}
	}
	
	public static void listCleared() {
		fullCleanup(null);
		lastTime = -1;
		updateState(false, null);
	}
	
	public static void nextMayHaveChanged(Song possibleNextSong) {
		if (nextSong != possibleNextSong && nextPreparationEnabled) {
			if (currentPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				clearNextPlayer(currentPlayer);
			if (nextPlayer != null)
				nextPlayer.reset();
			nextPrepared = false;
			nextPreparing = false;
			nextSong = possibleNextSong;
			processPreparation();
		}
	}
	
	public static int getVolumeDB() {
		return volumeDB;
	}
	
	public static void setVolumeDB(int volumeDB, boolean applyActualEqualizerVolume) {
		if (volumeDB <= MIN_VOLUME_DB) {
			volume = 0;
			Player.volumeDB = MIN_VOLUME_DB;
		} else if (volumeDB >= 0) {
			volume = 1;
			Player.volumeDB = 0;
		} else {
			//magnitude = 10 ^ (dB/20)
			//x^p = a ^ (p * log a (x))
			//10^p = e ^ (p * log e (10))
			volume = (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0);
			Player.volumeDB = volumeDB;
		}
		if (volumeTimer != null)
			volumeTimer.stop();
		//when dimmed, decreased the volume by 20dB
		actualVolume = (dimmedVolume ? (volume * 0.1f) : volume);
		if (applyActualEqualizerVolume) {
			try {
				if (currentPlayer != null && currentSongLoaded)
					currentPlayer.setVolume(actualVolume, actualVolume);
			} catch (Throwable ex) {
			}
		}
	}
	
	public static boolean isInitialized() {
		return initialized;
	}
	
	public static boolean isPlaying() {
		return playing;
	}
	
	public static boolean isFocused() {
		return hasFocus;
	}
	
	public static boolean isCurrentSongLoaded() {
		return currentSongLoaded;
	}
	
	public static boolean isCurrentSongPreparing() {
		return currentSongPreparing;
	}
	
	public static Song getCurrentSong() {
		return currentSong;
	}
	
	public static int getCurrentPosition() {
		return (((currentPlayer != null) && currentSongLoaded) ? currentPlayer.getCurrentPosition() : ((currentSong == null) ? -1 : lastTime));
	}
	
	public static int getAudioSessionId() {
		return ((currentPlayer == null) ? -1 : currentPlayer.getAudioSessionId());
	}
	
	public static boolean isControlMode() {
		return controlMode;
	}
	
	public static void setControlMode(boolean controlMode) {
		Player.controlMode = controlMode;
		if (observer != null)
			observer.onPlayerControlModeChanged(controlMode);
	}
	
	public static void startService(Context context) {
		if (thePlayer == null && !startRequested) {
			startRequested = true;
			initialize(context);
			context.startService(new Intent(context, Player.class));
		}
	}
	
	public static void stopService() {
		if (!stopRequested) {
			stopRequested = true;
			setLastTime();
			fullCleanup(null);
			releaseInternal();
			updateState(false, null); //to update the widget
			unregisterMediaButtonEventReceiver();
			if (thePlayer != null) {
				thePlayer.stopForeground(true);
				thePlayer.stopSelf();
				terminate(thePlayer);
			}
			thePlayer = null;
		}
	}
	
	public static Service getService() {
		return thePlayer;
	}
	
	@Override
	public void onCreate() {
		startRequested = false;
		stopRequested = false;
		thePlayer = this;
		initialize(this);
		initializePlayers();
		startForeground(1, getNotification());
		super.onCreate();
	}
	
	@Override
	public void onDestroy() {
		stopService();
		super.onDestroy();
	}
	
	private static void executeStartCommand(int forcePlayIdx) {
		if (forcePlayIdx >= 0) {
			play(forcePlayIdx);
			startCommand = null;
		} else if (startCommand != null && listLoaded) {
			if (startCommand.equals(ACTION_PREVIOUS))
				previous();
			else if (startCommand.equals(ACTION_PLAY_PAUSE))
				playPause();
			else if (startCommand.equals(ACTION_NEXT))
				next();
			startCommand = null;
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			startCommand = intent.getAction();
			executeStartCommand(-1);
		}
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public static boolean handleMediaButton(int keyCode) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_MEDIA_PLAY:
			if (!playing)
				playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_STOP:
			if (playing)
				playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			next();
			break;
		case KeyEvent.KEYCODE_MEDIA_REWIND:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			previous();
			break;
		default:
			return false;
		}
		return true;
	}
	
	public static void registerMediaButtonEventReceiver() {
		if (mediaButtonEventReceiver == null)
			mediaButtonEventReceiver = new ComponentName((thePlayer == null) ? "br.com.carlosrafaelgn.fplay" : thePlayer.getPackageName(), ExternalReceiver.class.getName());
		if (audioManager != null)
			audioManager.registerMediaButtonEventReceiver(mediaButtonEventReceiver);
	}
	
	public static void unregisterMediaButtonEventReceiver() {
		if (mediaButtonEventReceiver != null && audioManager != null)
			audioManager.unregisterMediaButtonEventReceiver(mediaButtonEventReceiver);
	}
	
	private static void processFocusGain() {
		//this method is called only when the player has recovered the focus,
		//and in this scenario, currentPlayer will be null
		//someone else may have changed our values if the engine is shared
		//final boolean equalizer = Equalizer.isEnabled();
		//final boolean bassboost = BassBoost.isEnabled();
		//Equalizer.setEnabled(false);
		//BassBoost.setEnabled(false);
		//Equalizer.setEnabled(equalizer);
		//BassBoost.setEnabled(bassboost);
		registerMediaButtonEventReceiver();
		if (wasPlayingBeforeFocusLoss) {
			//if (currentPlayer == null || !currentSongLoaded) {
				playInternal(SongList.HOW_CURRENT);
			//} else {
			//	try {
			//		playing = true;
			//		wasSeekingTo = lastTime;
			//		if (lastTime < 0) {
			//			startPlayer(currentPlayer);
			//		} else {
			//			playOnUserSeek = 0;
			//			currentSongPreparing = true;
			//			currentPlayer.seekTo(lastTime);
			//		}
			//		lastTime = -1;
			//	} catch (Throwable ex) {
			//		playing = false;
			//		wasPlaying = false;
			//		updateState(false, ex);
			//		return;
			//	}
			//	updateState(false, null);
			//}
		}
	}
	
	private static void processPreparation() {
		if (nextSong != null && !nextSong.isHttp && nextPreparationEnabled && currentSong.lengthMS > 10000 && nextSong.lengthMS > 10000)
			prepareDelayTimer.start(5000, true, nextSong);
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void clearNextPlayer(MediaPlayer player) {
		try {
			player.setNextMediaPlayer(null);
		} catch (Throwable ex) {
		}
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void setNextPlayer() {
		try {
			if (currentPlayer != null) {
				currentPlayer.setNextMediaPlayer(nextPlayer);
				nextAlreadySetForPlaying = true;
			}
		} catch (Throwable ex) {
			
		}
	}
	
	@Override
	public void handleTimer(Timer timer, Object param) {
		if (timer == focusDelayTimer) {
			if (thePlayer != null && hasFocus)
				processFocusGain();
		} else if (timer == prepareDelayTimer) {
			if (thePlayer != null && hasFocus && nextSong == param) {
				nextPreparing = false;
				nextPrepared = false;
				if (nextSong != null && !nextSong.isHttp) {
					try {
						preparePlayer(nextPlayer, nextSong);
						nextPreparing = true;
					} catch (Throwable ex) {
						nextSong = null;
						nextPlayer.reset();
					}
				}
			}
		} else if (timer == volumeTimer) {
			volumeDBMultiplier += ((Integer)param).intValue();
			if (volumeDBMultiplier >= 0) {
				timer.stop();
				volumeDBMultiplier = 0;
			}
			//magnitude = 10 ^ (dB/20)
			//x^p = a ^ (p * log a (x))
			//10^p = e ^ (p * log e (10))
			final float m = (float)Math.exp((double)volumeDBMultiplier * 2.3025850929940456840179914546844 / 2000.0);
			//when dimmed, decreased the volume by 20dB
			actualVolume = m * (dimmedVolume ? (volume * 0.1f) : volume);
			if (thePlayer != null && hasFocus && currentPlayer != null && currentSongLoaded) {
				try {
					currentPlayer.setVolume(actualVolume, actualVolume);
				} catch (Throwable ex) {
				}
			}
		}
	}
	
	@Override
	public void onAudioFocusChange(int focusChange) {
		if (focusDelayTimer != null)
			focusDelayTimer.stop();
		if (volumeTimer != null)
			volumeTimer.stop();
		actualVolume = volume;
		dimmedVolume = false;
		switch (focusChange) {
		case AudioManager.AUDIOFOCUS_GAIN:
			if (!hasFocus) {
				hasFocus = true;
				focusDelayTimer.start(1500, true);
			} else {
				//processFocusGain();
				//came here from AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
				if (currentPlayer != null && currentSongLoaded) {
					try {
						currentPlayer.setVolume(actualVolume, actualVolume);
					} catch (Throwable ex) {
					}
				}
			}
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			wasPlayingBeforeFocusLoss = playing;
			hasFocus = false;
			setLastTime();
			stopInternal(currentSong);
			silenceMode = SILENCE_FOCUS;
			releaseInternal();
			updateState(false, null);
			break;
		//sometimes, the player will give a MEDIA_ERROR_TIMED_OUT when gaining focus
		//back again after excuting the code below... Therefore, I decided to handle
		//transient losses just as normal losses
		//case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
		//	hasFocus = false;
		//	if (currentPlayer != null && playing) {
		//		playing = false;
		//		try {
		//			currentPlayer.pause();
		//			isQuiet = true;
		//			setLastTime();
		//		} catch (Throwable ex) {
		//		}
		//		updateState(true, null);
		//	}
		//	break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			hasFocus = true;
			//when dimmed, decreased the volume by 20dB
			actualVolume = volume * 0.1f;
			dimmedVolume = true;
			if (currentPlayer != null && currentSongLoaded) {
				try {
					currentPlayer.setVolume(actualVolume, actualVolume);
				} catch (Throwable ex) {
				}
			}
			break;
		}
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		if (mp == nextPlayer || mp == currentPlayer) {
			if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
				setLastTime();
				fullCleanup(currentSong);
				releaseInternal();
				if (reviveAlreadyRetried) {
					reviveAlreadyRetried = false;
					updateState(false, new MediaServerDiedException());
				} else {
					reviveAlreadyRetried = true;
					playInternal(SongList.HOW_CURRENT);
				}
			} else if (mp == nextPlayer) {
				nextSong = null;
				nextPreparing = false;
				nextPrepared = false;
			} else {
				fullCleanup(currentSong);
				updateState(false, (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) ? new TimeoutException() : new IOException());
			}
		} else {
			System.err.println("Invalid MediaPlayer");
			fullCleanup(currentSong);
			releaseInternal();
			updateState(false, new Exception("Invalid MediaPlayer"));
		}
		return true;
	}
	
	@Override
	public void onPrepared(MediaPlayer mp) {
		if (mp == nextPlayer) {
			nextPreparing = false;
			nextPrepared = true;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				setNextPlayer();
		} else if (mp == currentPlayer) {
			try {
				mp.setVolume(actualVolume, actualVolume);
				if (lastTime < 0 || currentSong.isHttp) {
					prepareNextOnSeek = false;
					currentSongPreparing = false;
					startPlayer(mp);
					lastTime = -1;
					playing = true;
					reviveAlreadyRetried = false;
				} else {
					playAfterSeek = true;
					prepareNextOnSeek = true;
					currentSongPreparing = true;
					mp.seekTo(lastTime);
				}
				//wasPlaying = true;
				currentSongLoaded = true;
				firstError = null;
				updateState(false, null);
				if (!prepareNextOnSeek)
					processPreparation();
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				nextFailed(currentSong, lastHow, ex);
			}
		} else {
			System.err.println("Invalid MediaPlayer");
			fullCleanup(currentSong);
			updateState(false, new Exception("Invalid MediaPlayer"));
		}
	}
	
	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (mp == currentPlayer) {
			try {
				currentSongPreparing = false;
				if (playAfterSeek) {
					startPlayer(mp);
					playing = true;
					reviveAlreadyRetried = false;
					//wasPlaying = true;
				}
				updateState(false, null);
				if (prepareNextOnSeek) {
					prepareNextOnSeek = false;
					processPreparation();
				}
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				updateState(false, ex);
			}
		}
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		if (playing)
			playInternal(SongList.HOW_NEXT_AUTO);
	}
}
