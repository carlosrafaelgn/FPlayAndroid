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
package br.com.carlosrafaelgn.fplay.playback;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaRouter;
import android.media.RemoteControlClient;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

import br.com.carlosrafaelgn.fplay.BuildConfig;
import br.com.carlosrafaelgn.fplay.ExternalReceiver;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.WidgetMain;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.list.SongList;
import br.com.carlosrafaelgn.fplay.playback.context.MediaContext;
import br.com.carlosrafaelgn.fplay.playback.context.MediaPlayerBase;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;
import br.com.carlosrafaelgn.fplay.util.TypedRawArrayList;
import br.com.carlosrafaelgn.fplay.visualizer.BluetoothVisualizerControllerJni;

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

//Many properties used to be private, but I decided to make them public, even though some of them
//still have their setters, after I found out ProGuard does not inline static setters (or at least
//I have not been able to figure out a way to do so....)

//************************************************************************************
//In a few devices, error -38 will happen on the MediaPlayer currently being played
//when adding breakpoints to Player Core Thread
//************************************************************************************

public final class Player extends Service implements AudioManager.OnAudioFocusChangeListener, MediaPlayerBase.OnErrorListener, MediaPlayerBase.OnSeekCompleteListener, MediaPlayerBase.OnPreparedListener, MediaPlayerBase.OnCompletionListener, MediaPlayerBase.OnInfoListener, ArraySorter.Comparer<FileSt> {
	public interface PlayerObserver {
		void onPlayerChanged(Song currentSong, boolean songHasChanged, boolean preparingHasChanged, Throwable ex);
		void onPlayerMetadataChanged(Song currentSong);
		void onPlayerControlModeChanged(boolean controlMode);
		void onPlayerGlobalVolumeChanged(int volume);
		void onPlayerAudioSinkChanged();
		void onPlayerMediaButtonPrevious();
		void onPlayerMediaButtonNext();
	}

	public interface PlayerTurnOffTimerObserver {
		void onPlayerTurnOffTimerTick();
		void onPlayerIdleTurnOffTimerTick();
	}

	public interface PlayerDestroyedObserver {
		void onPlayerDestroyed();
	}

	private static final int MSG_UPDATE_STATE = 0x0100;
	private static final int MSG_PAUSE = 0x0101;
	private static final int MSG_PLAYPAUSE = 0x0102;
	private static final int MSG_SEEKTO = 0x0103;
	private static final int MSG_SYNC_VOLUME = 0x0104;
	private static final int MSG_PREPARE_NEXT_SONG = 0x0105;
	private static final int MSG_OVERRIDE_VOLUME_MULTIPLIER = 0x0106;
	private static final int MSG_BECOMING_NOISY = 0x0107;
	private static final int MSG_AUDIO_SINK_CHANGED = 0x0108;
	private static final int MSG_INITIALIZATION_STEP = 0x0109;
	private static final int MSG_NEXT_MAY_HAVE_CHANGED = 0x010A;
	private static final int MSG_LIST_CLEARED = 0x010B;
	private static final int MSG_FOCUS_GAIN_TIMER = 0x010C;
	private static final int MSG_FADE_IN_VOLUME_TIMER = 0x010D;
	private static final int MSG_HEADSET_HOOK_TIMER = 0x010E;
	private static final int MSG_TURN_OFF_TIMER = 0x010F;
	private static final int MSG_IDLE_TURN_OFF_TIMER = 0x0110;
	private static final int MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER = 0x0111;
	private static final int MSG_SONG_LIST_DESERIALIZED = 0x0112;
	private static final int MSG_PRE_PLAY = 0x0113;
	private static final int MSG_POST_PLAY = 0x0114;
	private static final int MSG_ENABLE_EFFECTS = 0x0115;
	private static final int MSG_COMMIT_EQUALIZER = 0x0116;
	private static final int MSG_COMMIT_BASS_BOOST = 0x0117;
	private static final int MSG_COMMIT_VIRTUALIZER = 0x0118;
	private static final int MSG_COMMIT_ALL_EFFECTS = 0x0119;
	private static final int MSG_TURN_OFF_NOW = 0x011A;
	private static final int MSG_HTTP_STREAM_RECEIVER_ERROR = 0x011B;
	private static final int MSG_HTTP_STREAM_RECEIVER_PREPARED = 0x011C;
	private static final int MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE = 0x011D;
	private static final int MSG_HTTP_STREAM_RECEIVER_URL_UPDATED = 0x011E;
	private static final int MSG_ENABLE_EXTERNAL_FX = 0x011F;
	private static final int MSG_SET_BUFFER_CONFIG = 0x0120;
	private static final int MSG_ENABLE_AUTOMATIC_EFFECTS_GAIN = 0x0121;
	private static final int MSG_ENABLE_RESAMPLING = 0x0122;

	public static final int STATE_NEW = 0;
	public static final int STATE_INITIALIZING = 1;
	public static final int STATE_INITIALIZING_STEP2 = 2;
	public static final int STATE_ALIVE = 3;
	public static final int STATE_TERMINATING = 4;
	public static final int STATE_DEAD = 5;

	public static final int PLAYER_STATE_NEW = 0;
	public static final int PLAYER_STATE_PREPARING = 1;
	public static final int PLAYER_STATE_LOADED = 2;

	public static final int AUDIO_SINK_DEVICE = 1;
	public static final int AUDIO_SINK_WIRE = 2;
	public static final int AUDIO_SINK_BT = 4;
	public static final int AUDIO_SINK_WIRE_MIC = 8;

	public static final int VOLUME_MIN_DB = -4000;
	public static final int VOLUME_CONTROL_DB = 0;
	public static final int VOLUME_CONTROL_STREAM = 1;
	public static final int VOLUME_CONTROL_NONE = 2;
	public static final int VOLUME_CONTROL_PERCENT = 3;

	private static final int SILENCE_NORMAL = 0;
	private static final int SILENCE_FOCUS = 1;
	private static final int SILENCE_NONE = -1;

	public static final String ACTION_PREVIOUS = "br.com.carlosrafaelgn.FPlay.PREVIOUS";
	public static final String ACTION_PLAY_PAUSE = "br.com.carlosrafaelgn.FPlay.PLAY_PAUSE";
	public static final String ACTION_NEXT = "br.com.carlosrafaelgn.FPlay.NEXT";
	public static final String ACTION_EXIT = "br.com.carlosrafaelgn.FPlay.EXIT";
	public static String pathToPlayWhenStarting;
	private static String startCommand;

	public static volatile int state;
	private static Thread thread;
	private static Looper looper;
	public static Context theApplication; //once the app has been started, this is never null
	private static Player thePlayer;
	private static Handler handler, localHandler;
	private static AudioManager audioManager;
	private static NotificationManager notificationManager;
	private static TelephonyManager telephonyManager;
	public static final SongList songs = SongList.getInstance();

	//keep these instances here to prevent UI, MainHandler, Equalizer, BassBoost,
	//Virtualizer and ExternalFx classes from being garbage collected...
	public static MainHandler theMainHandler;
	public static final UI theUI = new UI();
	@SuppressWarnings("unused")
	public static final Equalizer theEqualizer = new Equalizer();
	@SuppressWarnings("unused")
	public static final BassBoost theBassBoost = new BassBoost();
	@SuppressWarnings("unused")
	public static final Virtualizer theVirtualizer = new Virtualizer();
	@SuppressWarnings("unused")
	public static final ExternalFx theExternalFx = new ExternalFx();

	public static boolean hasFocus, previousResetsAfterTheBeginning;
	public static int volumeStreamMax = 15, volumeControlType;
	private static boolean volumeDimmed;
	private static int volumeDB, volumeDBFading, silenceMode;
	private static float volumeMultiplier;

	private static int audioSink, audioSinkBeforeFocusLoss;
	static int audioSinkUsedInEffects;
	public static int localAudioSinkUsedInEffects;
	private static boolean audioSinkMicrophoneCheckDone;
	private static Method audioSinkMicrophoneCheckMethod;

	//These are only written/read from Core thread (except the volatile ones that are accessed from the main thread)
	private static int storedSongTime, howThePlayerStarted, playerState, nextPlayerState;
	private static volatile boolean resumePlaybackAfterFocusGain, postPlayPending;
	private static boolean playing, playerBuffering, playAfterSeeking, prepareNextAfterSeeking, reviveAlreadyTried, httpStreamReceiverActsLikePlayer, seekInProgress;
	private static Song song, nextSong, songScheduledForPreparation, nextSongScheduledForPreparation, songWhenFirstErrorHappened;
	private static MediaPlayerBase player, nextPlayer;
	public static volatile int audioSessionId;

	//keep these fields here, instead of in their respective activities, to allow them to survive
	//their activity's destruction (and even the class garbage collection)
	public static int positionToCenter = -1;
	public static boolean isMainActiveOnTop, alreadySelected;

	//These are only set in the main thread
	private static int localVolumeStream, localPlayerState;
	public static int localVolumeDB;
	public static boolean localPlaying;
	public static Song localSong;
	public static MediaPlayerBase localPlayer;

	private static class CoreHandler extends Handler {
		@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
		@Override
		public void dispatchMessage(@NonNull Message msg) {
			switch (msg.what) {
			case MSG_POST_PLAY:
				_postPlay(msg.arg1, (Song[])msg.obj);
				break;
			case MSG_BECOMING_NOISY:
				if (playing)
					_playPause();
				//this cleanup must be done, as sometimes, when changing between two output types,
				//the effects are lost...
				_fullCleanup();
				_checkAudioSink(false, false, false, true);
				break;
			case MSG_FADE_IN_VOLUME_TIMER:
				_processFadeInVolumeTimer(msg.arg1);
				break;
			case MSG_AUDIO_SINK_CHANGED:
				_checkAudioSink(msg.arg1 != 0, msg.arg2 != 0, true, true);
				break;
			case MSG_PAUSE:
				if (playing)
					_playPause();
				break;
			case MSG_PLAYPAUSE:
				_playPause();
				break;
			case MSG_SEEKTO:
				_seekTo(msg.arg1);
				break;
			case MSG_SYNC_VOLUME:
				_syncVolume();
				break;
			case MSG_PREPARE_NEXT_SONG:
				_prepareNextPlayer((Song)msg.obj);
				break;
			case MSG_FOCUS_GAIN_TIMER:
				_processFocusGainTimer();
				break;
			case MSG_OVERRIDE_VOLUME_MULTIPLIER:
				_overrideVolumeMultiplier(msg.arg1 != 0);
				break;
			case MSG_LIST_CLEARED:
				_fullCleanup();
				song = null;
				storedSongTime = -1;
				_updateState(false, null);
				break;
			case MSG_NEXT_MAY_HAVE_CHANGED:
				_nextMayHaveChanged((Song)msg.obj);
				break;
			case MSG_ENABLE_EFFECTS:
				if (!BuildConfig.X) {
					if (ExternalFx.isEnabled())
						ExternalFx._setEnabled(false);
					else
						ExternalFx._release();
					ExternalFx._setEnabled(false);
				}
				_enableEffects(msg.arg1, msg.arg2, (Runnable)msg.obj);
				break;
			case MSG_COMMIT_EQUALIZER:
				Equalizer._commit(msg.arg1, msg.arg2);
				break;
			case MSG_COMMIT_BASS_BOOST:
				BassBoost._commit(msg.arg2);
				break;
			case MSG_COMMIT_VIRTUALIZER:
				Virtualizer._commit(msg.arg2);
				break;
			case MSG_COMMIT_ALL_EFFECTS:
				if (msg.arg2 == audioSinkUsedInEffects)
					_reinitializeEffects();
				break;
			case MSG_ENABLE_EXTERNAL_FX:
				if (BuildConfig.X)
					break;

				if (msg.arg1 != 0) {
					Equalizer._release();
					BassBoost._release();
					Virtualizer._release();
					ExternalFx._initialize();
					ExternalFx._setEnabled(true);

					//if anything goes wrong while enabling ExternalFx, go back to the previous state
					if (ExternalFx.isEnabled() && ExternalFx.isSupported())
						break;
				}

				ExternalFx._setEnabled(false);
				_reinitializeEffects();

				if (msg.obj != null)
					MainHandler.postToMainThread((Runnable)msg.obj);
				break;
			case MSG_SONG_LIST_DESERIALIZED:
				_songListDeserialized(msg.obj);
				break;
			case MSG_HTTP_STREAM_RECEIVER_ERROR:
				_httpStreamReceiverError(msg.arg1, (msg.obj instanceof Throwable) ? (Throwable)msg.obj : null, msg.arg2);
				break;
			case MSG_HTTP_STREAM_RECEIVER_PREPARED:
				_httpStreamReceiverPrepared(msg.arg1);
				break;
			case MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE:
				if (localHandler != null)
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, msg.arg1, 0, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_HTTP_STREAM_RECEIVER_URL_UPDATED:
				if (msg.obj != null && msg.arg1 == httpStreamReceiverVersion)
					thePlayer.onInfo(player, MediaPlayerBase.INFO_URL_UPDATE, 0, msg.obj);
				break;
			case MSG_SET_BUFFER_CONFIG:
				MediaContext._setBufferConfig(msg.arg1);
				break;
			case MSG_ENABLE_AUTOMATIC_EFFECTS_GAIN:
				MediaContext._enableAutomaticEffectsGain(msg.arg1);
				if (msg.obj != null)
					MainHandler.postToMainThread((Runnable)msg.obj);
				break;
			case MSG_ENABLE_RESAMPLING:
				MediaContext._enableResampling(msg.arg1 != 0);
				break;
			}
		}
	}

	private static class CoreLocalHandler extends Handler {
		@Override
		public void dispatchMessage(@NonNull Message msg) {
			if (thePlayer == null || state > STATE_ALIVE)
				return;
			switch (msg.what) {
			case MSG_PRE_PLAY:
				prePlay(msg.arg1);
				break;
			case MSG_AUDIO_SINK_CHANGED:
				localAudioSinkUsedInEffects = audioSink;
				if (observer != null)
					observer.onPlayerAudioSinkChanged();
				break;
			case MSG_UPDATE_STATE:
				updateState(msg.arg1, (Object[])msg.obj);
				break;
			case MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER:
				registerMediaButtonEventReceiver();
				break;
			case MSG_INITIALIZATION_STEP:
				switch (state) {
				case STATE_INITIALIZING:
					state = STATE_INITIALIZING_STEP2;
					break;
				case STATE_INITIALIZING_STEP2:
					state = STATE_ALIVE;
					handler.sendMessageAtTime(Message.obtain(handler, MSG_SONG_LIST_DESERIALIZED, localSong), SystemClock.uptimeMillis());
					handler.sendMessageAtTime(Message.obtain(handler, MSG_OVERRIDE_VOLUME_MULTIPLIER, (volumeControlType != VOLUME_CONTROL_DB && volumeControlType != VOLUME_CONTROL_PERCENT) ? 1 : 0, 0), SystemClock.uptimeMillis());
					setTurnOffTimer(turnOffTimerSelectedMinutes);
					setIdleTurnOffTimer(idleTurnOffTimerSelectedMinutes);
					executeStartCommand();
					break;
				}
				break;
			case MSG_HEADSET_HOOK_TIMER:
				processHeadsetHookTimer();
				break;
			case MSG_TURN_OFF_TIMER:
				processTurnOffTimer();
				break;
			case MSG_IDLE_TURN_OFF_TIMER:
				idleTurnOffTimerSent = false;
				processIdleTurnOffTimer();
				break;
			case MSG_TURN_OFF_NOW:
				stopService();
				break;
			case MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE:
				if (msg.obj != null && msg.arg1 == httpStreamReceiverVersion)
					thePlayer.onInfo(localPlayer, MediaPlayerBase.INFO_METADATA_UPDATE, 0, msg.obj);
				break;
			}
		}
	}

	@Override
	public void onCreate() {
		thePlayer = this;
		theApplication = getApplicationContext();
		startService();
		startForeground(1, getNotification());
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}

	private static void executeStartCommand() {
		if (state == STATE_ALIVE) {
			if (pathToPlayWhenStarting != null) {
				if (songs.addPathAndForceScrollIntoView(pathToPlayWhenStarting, true))
					startCommand = null;
				pathToPlayWhenStarting = null;
			}
			if (startCommand != null) {
				switch (startCommand) {
				case ACTION_PREVIOUS:
					previous();
					break;
				case ACTION_PLAY_PAUSE:
					playPause();
					break;
				case ACTION_NEXT:
					next();
					break;
				case ACTION_EXIT:
					stopService();
					break;
				}
				startCommand = null;
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			final String command = intent.getAction();
			if (command != null) {
				startCommand = command;
				executeStartCommand();
			}
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public static boolean startService() {
		final boolean stateNew = (state == STATE_NEW);
		if (stateNew) {
			MainHandler.initialize();
			positionToCenter = -1;
			state = STATE_INITIALIZING;
			theApplication.startService(new Intent(theApplication, Player.class));
			theMainHandler = MainHandler.initialize();
			localHandler = new CoreLocalHandler();
			notificationManager = (NotificationManager)theApplication.getSystemService(NOTIFICATION_SERVICE);
			audioManager = (AudioManager)theApplication.getSystemService(AUDIO_SERVICE);
			telephonyManager = (TelephonyManager)theApplication.getSystemService(TELEPHONY_SERVICE);
			destroyedObservers = new TypedRawArrayList<>(PlayerDestroyedObserver.class, 4);
			stickyBroadcast = new Intent();
			loadConfig();
		}
		//when the player starts from the widget, startService is not called twice, it is
		//called only once, from within onCreate, when thePlayer is already != null
		if (thePlayer != null && thread == null) {
			createIntents();

			//These broadcast actions are registered here, instead of in the manifest file,
			//because a few tests showed that some devices will produce the notifications
			//faster this way, specially AUDIO_BECOMING_NOISY. Moreover, by registering here,
			//only one BroadcastReceiver is instantiated and used throughout the entire
			//application's lifecycle, whereas when the manifest is used, a different instance
			//is created to handle every notification! :)
			//The only exception is the MEDIA_BUTTON broadcast action, which MUST BE declared
			//in the application manifest according to the documentation of the method
			//registerMediaButtonEventReceiver!!! :(
			final IntentFilter filter = new IntentFilter("android.media.AUDIO_BECOMING_NOISY");
			//filter.addAction("android.intent.action.MEDIA_BUTTON");
			filter.addAction("android.intent.action.CALL_BUTTON");
			filter.addAction("android.intent.action.HEADSET_PLUG");
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				filter.addAction("android.media.ACTION_SCO_AUDIO_STATE_UPDATED");
			else
				filter.addAction("android.media.SCO_AUDIO_STATE_CHANGED");
			filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
			filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
			//HEADSET_STATE_CHANGED is based on: https://groups.google.com/forum/#!topic/android-developers/pN2k5_kFo4M
			filter.addAction("android.bluetooth.intent.action.HEADSET_STATE_CHANGED");
			externalReceiver = new ExternalReceiver();
			thePlayer.getApplicationContext().registerReceiver(externalReceiver, filter);

			registerMediaButtonEventReceiver();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				registerMediaRouter();

			thread = new Thread("Player Core Thread") {
				@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
				@Override
				public void run() {
					Looper.prepare();
					looper = Looper.myLooper();
					handler = new CoreHandler();
					_initializePlayers();
					Equalizer._checkSupport();
					BassBoost._checkSupport();
					Virtualizer._checkSupport();
					if (!BuildConfig.X)
						ExternalFx._checkSupport();
					_checkAudioSink(false, false, false, false);
					audioSinkUsedInEffects = audioSink;
					_reinitializeEffects();
					localHandler.sendEmptyMessageAtTime(MSG_INITIALIZATION_STEP, SystemClock.uptimeMillis());
					Looper.loop();
					if (song != null) {
						_storeSongTime();
						song = null;
					} else {
						storedSongTime = -1;
					}
					_fullCleanup();
					hasFocus = false;
					if (audioManager != null && thePlayer != null)
						audioManager.abandonAudioFocus(thePlayer);
					MediaContext._release();
				}
			};
			thread.start();

			while (handler == null)
				Thread.yield();

			songs.startDeserializingOrImportingFrom(null, true, false, false);
		}
		return stateNew;
	}

	public static void stopService() {
		if (state != STATE_ALIVE)
			return;
		state = STATE_TERMINATING;

		_releaseInternetObjects();

		looper.quit();

		try {
			thread.interrupt();
		} catch (Throwable ex) {
			//just ignore
		}

		try {
			//ANR is triggered after 5 seconds
			thread.join(3750);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}

		if (bluetoothVisualizerController != null)
			stopBluetoothVisualizer();

		unregisterMediaButtonEventReceiver();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && thePlayer != null)
			unregisterMediaRouter();

		if (destroyedObservers != null) {
			for (int i = destroyedObservers.size() - 1; i >= 0; i--)
				destroyedObservers.get(i).onPlayerDestroyed();
			destroyedObservers.clear();
			destroyedObservers = null;
		}

		if (thePlayer != null) {
			if (externalReceiver != null)
				thePlayer.getApplicationContext().unregisterReceiver(externalReceiver);
			saveConfig(true);
		}

		updateState(~0x27, new Object[] { null, null, null });

		/*if (restart && intentActivityHost != null) {
			//http://stackoverflow.com/questions/6609414/howto-programatically-restart-android-app
			((AlarmManager)thePlayer.getSystemService(ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 300, intentActivityHost);
		}*/

		thread = null;
		observer = null;
		turnOffTimerObserver = null;
		theMainHandler = null;
		looper = null;
		handler = null;
		localHandler = null;
		notification = null;
		notificationRemoteViews = null;
		notificationManager = null;
		audioManager = null;
		telephonyManager = null;
		externalReceiver = null;
		stickyBroadcast = null;
		intentActivityHost = null;
		intentPrevious = null;
		intentPlayPause = null;
		intentNext = null;
		intentExit = null;
		if (favoriteFolders != null) {
			favoriteFolders.clear();
			favoriteFolders = null;
		}
		radioStationCache = null;
		radioStationCacheShoutcast = null;
		localSong = null;
		localPlayer = null;
		stateLastSong = null;
		state = STATE_DEAD;
		if (thePlayer != null) {
			thePlayer.stopForeground(true);
			thePlayer.stopSelf();
			thePlayer = null;
		} else {
			System.exit(0);
		}
	}

	private static boolean prePlay(int how) {
		if (state != STATE_ALIVE || postPlayPending)
			return false;
		localSong = songs.getSongAndSetCurrent(how);
		final Song[] songArray = new Song[] { localSong, songs.possibleNextSong };
		songs.possibleNextSong = null;
		postPlayPending = true;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_POST_PLAY, how, 0, songArray), SystemClock.uptimeMillis());
		return true;
	}

	public static void previous() {
		if (previousResetsAfterTheBeginning &&
			localSong != null &&
			!localSong.isHttp &&
			(
				(
					(localPlayer == null || localPlayerState == PLAYER_STATE_NEW) &&
					storedSongTime >= 3000
				)
				||
				(
					localPlayer != null &&
					localPlayerState == PLAYER_STATE_LOADED &&
					localPlayer.getCurrentPosition() >= 3000
				)
			)) {
			seekTo(0);
			return;
		}
		prePlay(SongList.HOW_PREVIOUS);
	}

	public static boolean play(int index) {
		return prePlay(index);
	}

	public static void pause() {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_PAUSE), SystemClock.uptimeMillis());
	}

	public static void playPause() {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_PLAYPAUSE), SystemClock.uptimeMillis());
	}

	public static void next() {
		prePlay(SongList.HOW_NEXT_MANUAL);
	}

	public static void seekTo(int timeMS) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_SEEKTO, timeMS, 0), SystemClock.uptimeMillis());
	}

	public static void showStreamVolumeUI() {
		try {
			if (audioManager != null)
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static int setVolume(int volume) {
		if (state != STATE_ALIVE)
			return Integer.MIN_VALUE;
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			if (volume < 0)
				volume = 0;
			else if (volume > volumeStreamMax)
				volume = volumeStreamMax;
			final int oldVolume = localVolumeStream;
			localVolumeStream = volume;
			//try to unmute the device, without calling setStreamMute...
			try {
				if (audioManager != null)
					audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, (volume >= oldVolume) ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
			} catch (Throwable ex) {
				//too bad...
			}
			//apparently a few devices don't like to have the streamVolume changed from another thread....
			//maybe there is another reason for why it fails... I just haven't found yet :(
			try {
				if (audioManager != null)
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
			} catch (Throwable ex) {
				//too bad...
			}
			return volume;
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			if (volume <= VOLUME_MIN_DB)
				volume = VOLUME_MIN_DB;
			else if (volume > 0)
				volume = 0;
			if (localVolumeDB == volume)
				return Integer.MIN_VALUE;
			localVolumeDB = volume;
			break;
		default:
			showStreamVolumeUI();
			return Integer.MIN_VALUE;
		}
		handler.sendEmptyMessageAtTime(MSG_SYNC_VOLUME, SystemClock.uptimeMillis());
		return volume;
	}

	public static int decreaseVolume() {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			return setVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1);
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			//http://stackoverflow.com/a/4413073/3569421
			//(a/b)*b+(a%b) is equal to a
			//-350 % 200 = -150
			final int leftover = (Player.volumeDB % 200);
			return setVolume(Player.volumeDB + ((leftover == 0) ? -200 : (-200 - leftover)));
		}
		showStreamVolumeUI();
		return Integer.MIN_VALUE;
	}

	public static int increaseVolume() {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			return setVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1);
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			//http://stackoverflow.com/a/4413073/3569421
			//(a/b)*b+(a%b) is equal to a
			//-350 % 200 = -150
			final int leftover = (Player.volumeDB % 200);
			return setVolume(Player.volumeDB + ((leftover == 0) ? 200 : -leftover));
		}
		showStreamVolumeUI();
		return Integer.MIN_VALUE;
	}

	public static int getSystemStreamVolume() {
		try {
			if (audioManager != null)
				return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return 0;
	}

	public static void setVolumeInPercentage(int percentage) {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			setVolume((percentage * volumeStreamMax) / 100);
			break;
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			setVolume((percentage >= 100) ? 0 : ((percentage <= 0) ? VOLUME_MIN_DB : (((100 - percentage) * VOLUME_MIN_DB) / 100)));
			break;
		}
	}

	public static int getVolumeInPercentage() {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			final int vol = getSystemStreamVolume();
			return ((vol <= 0) ? 0 : ((vol >= volumeStreamMax) ? 100 : ((vol * 100) / volumeStreamMax)));
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			return ((localVolumeDB >= 0) ? 100 : ((localVolumeDB <= VOLUME_MIN_DB) ? 0 : (((VOLUME_MIN_DB - localVolumeDB) * 100) / VOLUME_MIN_DB)));
		}
		return 0;
	}

	public static void setVolumeControlType(int volumeControlType) {
		switch (volumeControlType) {
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
		case VOLUME_CONTROL_NONE:
			Player.volumeControlType = volumeControlType;
			if (handler != null)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_OVERRIDE_VOLUME_MULTIPLIER, (volumeControlType == VOLUME_CONTROL_NONE) ? 1 : 0, 0), SystemClock.uptimeMillis());
			break;
		default:
			try {
				if (audioManager != null)
					volumeStreamMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
				if (volumeStreamMax < 1)
					volumeStreamMax = 1;
				if (handler != null)
					handler.sendMessageAtTime(Message.obtain(handler, MSG_OVERRIDE_VOLUME_MULTIPLIER, 1, 0), SystemClock.uptimeMillis());
				Player.volumeControlType = VOLUME_CONTROL_STREAM;
			} catch (Throwable ex) {
				Player.volumeControlType = VOLUME_CONTROL_NONE;
				ex.printStackTrace();
			}
			break;
		}
	}

	public static void enableEffects(boolean equalizer, boolean bassBoost, boolean virtualizer, int audioSink, Runnable callback) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_ENABLE_EFFECTS, (equalizer ? 1 : 0) | (bassBoost ? 2 : 0) | (virtualizer ? 4 : 0), audioSink, callback), SystemClock.uptimeMillis());
	}

	public static void commitEqualizer(int band, int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_EQUALIZER, band, audioSink), SystemClock.uptimeMillis());
	}

	public static void commitBassBoost(int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_BASS_BOOST, 0, audioSink), SystemClock.uptimeMillis());
	}

	public static void commitVirtualizer(int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_VIRTUALIZER, 0, audioSink), SystemClock.uptimeMillis());
	}

	public static void commitAllEffects(int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_ALL_EFFECTS, 0, audioSink), SystemClock.uptimeMillis());
	}

	public static void enableExternalFx(boolean enabled, Runnable callback) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_ENABLE_EXTERNAL_FX, (enabled ? 8 : 0), 0, callback), SystemClock.uptimeMillis());
	}

	public static boolean isPreparing() {
		return (localPlayerState == PLAYER_STATE_PREPARING || playerBuffering);
	}

	public static String getCurrentTitle(boolean preparing) {
		return ((state == STATE_NEW) ? theApplication.getText(R.string.nothing_playing).toString() :
				((state < STATE_ALIVE) ? theApplication.getText(R.string.loading).toString() :
					((localSong == null) ? theApplication.getText(R.string.nothing_playing).toString() :
						(!preparing ? localSong.title :
							(theApplication.getText(R.string.loading) + " " + localSong.title)))));
	}

	public static int getPosition() {
		return ((httpStreamReceiver != null) ? -1 :
			((localPlayer != null && playerState == PLAYER_STATE_LOADED) ? localPlayer.getCurrentPosition() :
				((localSong == null) ? -1 :
					storedSongTime)));
	}

	public static int getHttpPosition() {
		//by doing like this, we do not need to synchronize the access to httpStreamReceiver
		final HttpStreamReceiver receiver = httpStreamReceiver;
		return ((receiver != null) ? receiver.bytesReceivedSoFar :
			((localPlayer != null) ? localPlayer.getHttpPosition() :
				-1));
	}

	public static int getSrcSampleRate() {
		return ((httpStreamReceiver != null) ? 0 :
			((localPlayer != null && playerState == PLAYER_STATE_LOADED) ? localPlayer.getSrcSampleRate() :
				0));
	}

	public static int getChannelCount() {
		return ((httpStreamReceiver != null) ? 0 :
			((localPlayer != null && playerState == PLAYER_STATE_LOADED) ? localPlayer.getChannelCount() :
				0));
	}

	private static MediaPlayerBase _createPlayer() {
		MediaPlayerBase mp = MediaContext.createMediaPlayer();
		//handled internally by the implementations of MediaPlayerBase
		//mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mp.setOnErrorListener(thePlayer);
		mp.setOnSeekCompleteListener(thePlayer);
		mp.setOnCompletionListener(thePlayer);
		mp.setOnInfoListener(thePlayer);
		mp.setWakeMode(thePlayer, PowerManager.PARTIAL_WAKE_LOCK);
		return mp;
	}

	private static void _startPlayer() {
		playAfterSeeking = false;
		playing = true;
		//when dimmed, decreased the volume by 20dB
		float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
		if (silenceMode != SILENCE_NONE) {
			final int increment = ((silenceMode == SILENCE_FOCUS) ? fadeInIncrementOnFocus : ((howThePlayerStarted == SongList.HOW_CURRENT || (song != null && song.isHttp)) ? fadeInIncrementOnPause : fadeInIncrementOnOther));
			if (increment > 30) {
				volumeDBFading = VOLUME_MIN_DB;
				multiplier = 0;
				handler.sendMessageAtTime(Message.obtain(handler, MSG_FADE_IN_VOLUME_TIMER, increment, 0), SystemClock.uptimeMillis() + 50);
			}
			silenceMode = SILENCE_NONE;
		}
		if (httpStreamReceiverActsLikePlayer) {
			httpStreamReceiver.setVolume(multiplier, multiplier);
			//httpStreamReceiver starts playing automatically (we just need to fix the volume, as
			//it always starts playing muted)
		} else if (player != null) {
			player.setVolume(multiplier, multiplier);
			player.start();
		}
		reviveAlreadyTried = false;
	}

	private static void _releasePlayer(MediaPlayerBase mediaPlayer) {
		mediaPlayer.setOnErrorListener(null);
		mediaPlayer.setOnPreparedListener(null);
		mediaPlayer.setOnSeekCompleteListener(null);
		mediaPlayer.setOnCompletionListener(null);
		mediaPlayer.setOnInfoListener(null);
		mediaPlayer.reset();
		mediaPlayer.release();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void _clearNextPlayer() {
		//there is no need to call setNextMediaPlayer(null) after calling reset()
		try {
			player.setNextMediaPlayer(null);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void _setNextPlayer() {
		try {
			player.setNextMediaPlayer(nextPlayer);
		} catch (Throwable ex) {
			//just ignore
		}
	}

	private static void _partialCleanup() {
		MediaContext._release();
		playerState = PLAYER_STATE_NEW;
		playerBuffering = false;
		if (player != null) {
			_releasePlayer(player);
			player = null;
		}
		//nextAlreadySetForPlaying = false;
		nextPlayerState = PLAYER_STATE_NEW;
		if (nextPlayer != null) {
			_releasePlayer(nextPlayer);
			nextPlayer = null;
		}
		playing = false;
		playAfterSeeking = false;
		prepareNextAfterSeeking = false;
		resumePlaybackAfterFocusGain = false;
		seekInProgress = false;
		songScheduledForPreparation = null;
		nextSongScheduledForPreparation = null;
		_releaseInternetObjects();
		if (handler != null) {
			handler.removeMessages(MSG_FOCUS_GAIN_TIMER);
			handler.removeMessages(MSG_FADE_IN_VOLUME_TIMER);
		}
		resetHeadsetHook();
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	private static void _fullCleanup() {
		_partialCleanup();
		silenceMode = SILENCE_NORMAL;
		nextSong = null;
		postPlayPending = false;
		Equalizer._release();
		BassBoost._release();
		Virtualizer._release();
		if (!BuildConfig.X)
			ExternalFx._release();
	}

	private static void _initializePlayers() {
		MediaContext._initialize();
		if (player == null) {
			player = _createPlayer();
			if (nextPlayer != null) {
				player.setAudioSessionId(nextPlayer.getAudioSessionId());
			} else {
				nextPlayer = _createPlayer();
				nextPlayer.setAudioSessionId(player.getAudioSessionId());
			}
		} else if (nextPlayer == null) {
			nextPlayer = _createPlayer();
			nextPlayer.setAudioSessionId(player.getAudioSessionId());
		}
		//store the latest audio session id
		audioSessionId = player.getAudioSessionId();
	}

	private static boolean _requestFocus() {
		if (thePlayer == null || audioManager == null || audioManager.requestAudioFocus(thePlayer, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			hasFocus = false;
			return false;
		}
		localHandler.sendEmptyMessageAtTime(MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER, SystemClock.uptimeMillis());
		hasFocus = true;
		volumeDimmed = false;
		return true;
	}

	private static void _storeSongTime() {
		if (song == null || song.isHttp)
			storedSongTime = -1;
		else if (player != null && playerState == PLAYER_STATE_LOADED)
			storedSongTime = player.getCurrentPosition();
	}

	private static void _prepareNextPlayer(Song song) {
		try {
			if (song == nextSongScheduledForPreparation && nextPlayer != null) {
				//Even though it happens very rarely, a few devices will freeze and produce an ANR
				//when calling setDataSource from the main thread :(
				nextPlayer.setDataSource(nextSongScheduledForPreparation.path);
				//I decided to stop calling prepareAsync for files
				nextPlayerState = PLAYER_STATE_PREPARING;
				nextPlayer.setOnPreparedListener(null);
				nextPlayer.prepare();
				thePlayer.onPrepared(nextPlayer);
			}
		} catch (Throwable ex) {
			nextPlayerState = PLAYER_STATE_NEW;
			ex.printStackTrace();
		}
	}

	private static void _scheduleNextPlayerForPreparation() {
		//nextAlreadySetForPlaying = false;
		prepareNextAfterSeeking = false;
		nextPlayerState = PLAYER_STATE_NEW;
		if (handler != null && song != null && nextSong != null && !song.isHttp && !nextSong.isHttp && nextPreparationEnabled && song.lengthMS > 10000 && nextSong.lengthMS > 10000) {
			handler.removeMessages(MSG_PREPARE_NEXT_SONG);
			nextSongScheduledForPreparation = nextSong;
			handler.sendMessageAtTime(Message.obtain(handler, MSG_PREPARE_NEXT_SONG, nextSong), SystemClock.uptimeMillis() + 5000);
		}
	}

	private static void _handleFailure(Throwable ex, boolean checkForPermission) {
		if (checkForPermission) {
			checkForPermission = false;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (thePlayer.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
					checkForPermission = true;
			}
		}
		if (songWhenFirstErrorHappened == song || howThePlayerStarted == SongList.HOW_CURRENT || howThePlayerStarted >= 0 || checkForPermission) {
			songWhenFirstErrorHappened = null;
			_updateState(false, checkForPermission ? new MediaPlayerBase.PermissionDeniedException() : ex);
		} else {
			//this used to be called only when howThePlayerStarted == SongList.HOW_NEXT_AUTO
			if (songWhenFirstErrorHappened == null)
				songWhenFirstErrorHappened = song;
			_updateState(false, null);
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, howThePlayerStarted, 0), SystemClock.uptimeMillis());
		}
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	private static void _postPlay(int how, Song[] songArray) {
		if (state != STATE_ALIVE)
			return;
		song = songArray[0];
		if (!hasFocus && !_requestFocus()) {
			if (how != SongList.HOW_CURRENT)
				storedSongTime = -1;
			_fullCleanup();
			_updateState(false, new MediaPlayerBase.FocusException());
			return;
		}
		//we must set this to false here, as the user could have manually
		//started playback before the focus timer had the chance to trigger
		resumePlaybackAfterFocusGain = false;
		if (song == null) {
			storedSongTime = -1;
			songWhenFirstErrorHappened = null;
			_fullCleanup();
			_updateState(false, null);
			return;
		}

		try {
			howThePlayerStarted = how;
			playerBuffering = false;
			if (playerState == PLAYER_STATE_PREPARING)
				//probably the user is changing songs too fast!
				_partialCleanup();
			if (player == null || nextPlayer == null || audioSinkUsedInEffects != audioSink) {
				_initializePlayers();
				_reinitializeEffects();
			}
			player.reset();
			postPlayPending = false;
			if (nextSong == song && how != SongList.HOW_CURRENT) {
				storedSongTime = -1;
				final MediaPlayerBase p = player;
				switch (nextPlayerState) {
				case PLAYER_STATE_LOADED:
					playerState = PLAYER_STATE_LOADED;
					player = nextPlayer;
					nextPlayer = p;
					nextSong = songArray[1];
					//if (!nextAlreadySetForPlaying || how != SongList.HOW_NEXT_AUTO) {
						_startPlayer();
					//} else {
					//	playing = true;
					//	//when dimmed, decreased the volume by 20dB
					//	final float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
					//	//do not call httpStreamReceiver.setVolume() here
					//	player.setVolume(multiplier, multiplier);
					//}
					songWhenFirstErrorHappened = null;
					_releaseInternetObjects();
					_scheduleNextPlayerForPreparation();
					_updateState(false, null);
					return;
				case PLAYER_STATE_PREPARING:
					//just wait for the next song to finish preparing
					playing = false;
					playerState = PLAYER_STATE_PREPARING;
					player = nextPlayer;
					nextPlayer = p;

					songScheduledForPreparation = song;

					nextPlayerState = PLAYER_STATE_NEW;
					nextPlayer.reset();
					nextSong = songArray[1];
					nextSongScheduledForPreparation = null;
					if (nextPreparationEnabled)
						handler.removeMessages(MSG_PREPARE_NEXT_SONG);

					_releaseInternetObjects();
					_updateState(false, null);
					return;
				}
			}
			playing = false;
			playerState = PLAYER_STATE_PREPARING;

			_releaseInternetObjects();

			if (how != SongList.HOW_CURRENT)
				storedSongTime = -1;

			if (song.path == null || song.path.length() == 0)
				throw new IOException();
			songScheduledForPreparation = song;

			nextPlayerState = PLAYER_STATE_NEW;
			nextPlayer.reset();
			nextSong = songArray[1];
			nextSongScheduledForPreparation = null;
			if (nextPreparationEnabled)
				handler.removeMessages(MSG_PREPARE_NEXT_SONG);

			//this is to make the player show the "Loading..." message properly, now that we are no
			//longer using prepareAsync
			_updateState(false, null);

			if (BuildConfig.X || !song.isHttp) {
				//Even though it happens very rarely, a few devices will freeze and produce an ANR
				//when calling setDataSource from the main thread :(
				player.setDataSource(song.path);
				if (song.isHttp) {
					//http songs are handled by the player in X mode
					player.setOnPreparedListener(thePlayer);
					player.prepareAsync();
				} else {
					//I decided to stop calling prepareAsync for files
					player.setOnPreparedListener(null);
					player.prepare();
					//onPrepared() will call _updateState when necessary
					thePlayer.onPrepared(player);
				}
			} else {
				_createInternetObjects();
				_updateState(false, null);
			}
		} catch (Throwable ex) {
			_fullCleanup();
			//clear the flag here to allow _handleFailure to move to the next song
			_handleFailure(ex, true);
		}
	}

	private static void _playPause() {
		if (state != STATE_ALIVE || playerState == PLAYER_STATE_PREPARING)
			return;
		try {
			//we must set this to false here, as the user could have manually
			//started playback before the focus timer had the chance to trigger
			resumePlaybackAfterFocusGain = false;
			if (playing) {
				playing = false;
				if (httpStreamReceiverActsLikePlayer)
					httpStreamReceiver.pause();
				else
					player.pause();
				silenceMode = SILENCE_NORMAL;
				_storeSongTime();
				_updateState(false, null);
			} else {
				if (song == null || playerState == PLAYER_STATE_NEW || !hasFocus) {
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_CURRENT, 0), SystemClock.uptimeMillis());
				} else {
					howThePlayerStarted = SongList.HOW_CURRENT;
					if (httpStreamReceiverActsLikePlayer) {
						//every time httpStreamReceiver starts, it is a preparation!
						playerBuffering = true;
						songScheduledForPreparation = song;
						httpStreamReceiver.start();
					} else {
						_startPlayer();
					}
					_updateState(false, null);
				}
			}
		} catch (Throwable ex) {
			_fullCleanup();
			_updateState(false, ex);
		}
	}

	private static void _seekTo(int timeMS) {
		if (state != STATE_ALIVE || playerState == PLAYER_STATE_PREPARING)
			return;
		storedSongTime = timeMS;
		if (playerState == PLAYER_STATE_LOADED) {
			playAfterSeeking = playing;
			playerState = PLAYER_STATE_PREPARING;
			try {
				if (playing) {
					playing = false;
					player.pause();
				}
				seekInProgress = true;
				player.seekToAsync(timeMS);
				_updateState(true, null);
			} catch (Throwable ex) {
				_fullCleanup();
				_updateState(false, ex);
			}
		} else {
			//just set storedSongTime and broadcast the change
			_updateState(true, null);
		}
	}

	private static void _processFadeInVolumeTimer(int increment) {
		volumeDBFading += increment;
		//magnitude = 10 ^ (dB/20)
		//x^p = a ^ (p * log a (x))
		//10^p = e ^ (p * log e (10))
		float multiplier = (float)Math.exp((double)volumeDBFading * 2.3025850929940456840179914546844 / 2000.0);
		boolean send = true;
		if (multiplier >= volumeMultiplier) {
			multiplier = volumeMultiplier;
			send = false;
		}
		//when dimmed, decreased the volume by 20dB
		if (volumeDimmed)
			multiplier *= 0.1f;
		if (handler != null && hasFocus && player != null && playerState == PLAYER_STATE_LOADED) {
			if (httpStreamReceiverActsLikePlayer) {
				httpStreamReceiver.setVolume(multiplier, multiplier);
			} else {
				try {
					player.setVolume(multiplier, multiplier);
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
			if (send)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_FADE_IN_VOLUME_TIMER, increment, 0), SystemClock.uptimeMillis() + 50);
		}
	}

	private static void _syncVolume() {
		if (state != STATE_ALIVE)
			return;
		if (volumeControlType == VOLUME_CONTROL_STREAM) {
			final int volumeStream = localVolumeStream;
			try {
				if (audioManager != null)
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volumeStream <= 0) ? 0 : ((volumeStream >= volumeStreamMax) ? volumeStreamMax : volumeStream)), 0);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			return;
		}
		if (volumeDB == localVolumeDB)
			return;
		volumeDB = localVolumeDB;
		volumeMultiplier = ((volumeDB <= VOLUME_MIN_DB) ? 0.0f : ((volumeDB >= 0) ? 1.0f : (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0)));
		//when dimmed, decreased the volume by 20dB
		final float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
		if (httpStreamReceiverActsLikePlayer)
			httpStreamReceiver.setVolume(multiplier, multiplier);
		if (player != null) {
			try {
				player.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (nextPlayer != null) {
			try {
				nextPlayer.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	private static void _overrideVolumeMultiplier(boolean toMax) {
		if (state != STATE_ALIVE)
			return;
		volumeMultiplier = ((toMax || (volumeDB >= 0)) ? 1.0f : ((volumeDB <= VOLUME_MIN_DB) ? 0.0f : (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0)));
		//when dimmed, decreased the volume by 20dB
		final float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
		if (httpStreamReceiverActsLikePlayer)
			httpStreamReceiver.setVolume(multiplier, multiplier);
		if (player != null) {
			try {
				player.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (nextPlayer != null) {
			try {
				nextPlayer.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	private static void _processFocusGainTimer() {
		if (state != STATE_ALIVE || !hasFocus)
			return;
		//someone else may have changed our values if the engine is shared
		localHandler.sendEmptyMessageAtTime(MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER, SystemClock.uptimeMillis());
		if (resumePlaybackAfterFocusGain) {
			//do not restart playback in scenarios like this (it really scares people!):
			//the person has answered a call, removed the headset, ended the call without
			//the headset plugged in, and then the focus came back to us
			if (audioSinkBeforeFocusLoss != AUDIO_SINK_DEVICE && audioSink == AUDIO_SINK_DEVICE) {
				resumePlaybackAfterFocusGain = false;
				_requestFocus();
			} else if (!playing) {
				localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_CURRENT, 0), SystemClock.uptimeMillis());
			} else {
				resumePlaybackAfterFocusGain = false;
			}
		}
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	private static void _enableEffects(int enabledFlags, int audioSink, Runnable callback) {
		audioSinkUsedInEffects = Player.audioSink;
		if (audioSinkUsedInEffects != audioSink) {
			//just change the state, as these settings will not be actually applied
			Equalizer._setEnabled((enabledFlags & 1) != 0, audioSink);
			BassBoost._setEnabled((enabledFlags & 2) != 0, audioSink);
			Virtualizer._setEnabled((enabledFlags & 4) != 0, audioSink);
			if (callback != null)
				MainHandler.postToMainThread(callback);
			return;
		}

		if (BuildConfig.X) {
			boolean enabled = ((enabledFlags & 1) != 0);
			if (enabled) {
				if (!Equalizer._isCreated() || enabled != Equalizer.isEnabled(audioSink)) {
					Equalizer._initialize();
					Equalizer._setEnabled(true, audioSink);
				}
			} else {
				if (Equalizer._isCreated())
					Equalizer._release();
				if (enabled != Equalizer.isEnabled(audioSink))
					Equalizer._setEnabled(false, audioSink);
			}

			enabled = ((enabledFlags & 2) != 0);
			if (enabled) {
				if (!BassBoost._isCreated() || enabled != BassBoost.isEnabled(audioSink)) {
					BassBoost._initialize();
					BassBoost._setEnabled(true, audioSink);
				}
			} else {
				if (BassBoost._isCreated())
					BassBoost._release();
				if (enabled != BassBoost.isEnabled(audioSink))
					BassBoost._setEnabled(false, audioSink);
			}

			enabled = ((enabledFlags & 4) != 0);
			if (enabled) {
				if (!Virtualizer._isCreated() || enabled != Virtualizer.isEnabled(audioSink)) {
					Virtualizer._initialize();
					Virtualizer._setEnabled(true, audioSink);
				}
			} else {
				if (Virtualizer._isCreated())
					Virtualizer._release();
				if (enabled != Virtualizer.isEnabled(audioSink))
					Virtualizer._setEnabled(false, audioSink);
			}

			if (callback != null)
				MainHandler.postToMainThread(callback);
			return;
		}

		//don't even ask.......
		//(a few devices won't disable one effect while the other effect is enabled)
		Equalizer._release();
		BassBoost._release();
		Virtualizer._release();
		if ((enabledFlags & 1) != 0) {
			Equalizer._initialize();
			Equalizer._setEnabled(true, audioSink);
		} else {
			Equalizer._setEnabled(false, audioSink);
		}
		if ((enabledFlags & 2) != 0) {
			BassBoost._initialize();
			BassBoost._setEnabled(true, audioSink);
		} else {
			BassBoost._setEnabled(false, audioSink);
		}
		if ((enabledFlags & 4) != 0) {
			Virtualizer._initialize();
			Virtualizer._setEnabled(true, audioSink);
		} else {
			Virtualizer._setEnabled(false, audioSink);
		}
		if (callback != null)
			MainHandler.postToMainThread(callback);
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	private static void _reinitializeEffects() {
		audioSinkUsedInEffects = audioSink;
		//don't even ask.......
		//(a few devices won't disable one effect while the other effect is enabled)
		Equalizer._release();
		BassBoost._release();
		Virtualizer._release();
		if (!BuildConfig.X) {
			ExternalFx._release();
			if (ExternalFx.isEnabled() && ExternalFx.isSupported()) {
				ExternalFx._initialize();
				ExternalFx._setEnabled(true);
				if (ExternalFx.isEnabled() && ExternalFx.isSupported())
					return;
			}
		}

		if (Equalizer.isEnabled(audioSinkUsedInEffects))
			Equalizer._initialize();
		if (BassBoost.isEnabled(audioSinkUsedInEffects))
			BassBoost._initialize();
		if (Virtualizer.isEnabled(audioSinkUsedInEffects))
			Virtualizer._initialize();

		if (Equalizer.isEnabled(audioSinkUsedInEffects))
			Equalizer._setEnabled(true, audioSinkUsedInEffects);
		if (BassBoost.isEnabled(audioSinkUsedInEffects))
			BassBoost._setEnabled(true, audioSinkUsedInEffects);
		if (Virtualizer.isEnabled(audioSinkUsedInEffects))
			Virtualizer._setEnabled(true, audioSinkUsedInEffects);
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (state != STATE_ALIVE)
			return;
		if (handler != null) {
			handler.removeMessages(MSG_FOCUS_GAIN_TIMER);
			handler.removeMessages(MSG_FADE_IN_VOLUME_TIMER);
		}
		resetHeadsetHook();
		volumeDimmed = false;
		switch (focusChange) {
		case AudioManager.AUDIOFOCUS_GAIN:
			if (!hasFocus) {
				hasFocus = true;
				if (handler != null)
					handler.sendMessageAtTime(Message.obtain(handler, MSG_FOCUS_GAIN_TIMER), SystemClock.uptimeMillis() + 1500);
			} else {
				//came here from AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
				if (httpStreamReceiverActsLikePlayer) {
					httpStreamReceiver.setVolume(volumeMultiplier, volumeMultiplier);
				} else if (player != null && playerState == PLAYER_STATE_LOADED) {
					try {
						player.setVolume(volumeMultiplier, volumeMultiplier);
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			//we just cannot replace resumePlaybackAfterFocusGain's value with
			//playing, because if a second focus loss occurred BEFORE _focusGain
			//had a chance to be called, then playing would be false, when in fact,
			//we want resumePlaybackAfterFocusGain to remain true (and the audio sink
			//must be untouched)
			//also because of that, we must take resumePlaybackAfterFocusGain into
			//consideration here, because _fullCleanup always sets it to false
			final boolean oldResumePlaybackAfterFocusGain = resumePlaybackAfterFocusGain;
			final boolean resume = (localPlaying || localPlayerState == PLAYER_STATE_PREPARING);
			hasFocus = false;
			_storeSongTime();
			_fullCleanup();
			silenceMode = SILENCE_FOCUS;
			if (resume || oldResumePlaybackAfterFocusGain)
				resumePlaybackAfterFocusGain = true;
			//change audioSinkBeforeFocusLoss only at the first time we lose focus!
			//(if we lose focus several times in row, before we actually have had
			//chance to play at least once, resume will be false)
			if (resume)
				audioSinkBeforeFocusLoss = audioSink;
			_updateState(false, null);
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			hasFocus = true;
			if (!doNotAttenuateVolume) {
				//when dimmed, decreased the volume by 20dB
				final float multiplier = volumeMultiplier * 0.1f;
				volumeDimmed = true;
				if (httpStreamReceiverActsLikePlayer) {
					httpStreamReceiver.setVolume(multiplier, multiplier);
				} else if (player != null && playerState == PLAYER_STATE_LOADED) {
					try {
						player.setVolume(multiplier, multiplier);
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}
			break;
		}
	}

	@Override
	public void onCompletion(MediaPlayerBase mediaPlayer) {
		if (state != STATE_ALIVE)
			return;
		if (playing && player == mediaPlayer)
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_NEXT_AUTO, 0), SystemClock.uptimeMillis());
	}

	@Override
	public boolean onError(MediaPlayerBase mediaPlayer, int what, int extra) {
		if (state != STATE_ALIVE)
			return true;
		if (mediaPlayer == nextPlayer || mediaPlayer == player) {
			if (what == MediaPlayerBase.ERROR_SERVER_DIED) {
				_storeSongTime();
				_fullCleanup();
				if (reviveAlreadyTried) {
					reviveAlreadyTried = false;
					_updateState(false, new MediaPlayerBase.MediaServerDiedException());
				} else {
					reviveAlreadyTried = true;
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_CURRENT, 0), SystemClock.uptimeMillis());
				}
			} else if (mediaPlayer == nextPlayer) {
				nextSong = null;
				nextPlayerState = PLAYER_STATE_NEW;
			} else {
				if (seekInProgress)
					storedSongTime = -1;
				_fullCleanup();
				final Throwable result = ((extra == MediaPlayerBase.ERROR_PERMISSION) ? new MediaPlayerBase.PermissionDeniedException() :
					((extra == MediaPlayerBase.ERROR_OUT_OF_MEMORY) ? new OutOfMemoryError() :
						((extra == MediaPlayerBase.ERROR_NOT_FOUND) ? new FileNotFoundException() :
							((extra == MediaPlayerBase.ERROR_TIMED_OUT) ? new MediaPlayerBase.TimeoutException() :
								((extra == MediaPlayerBase.ERROR_UNSUPPORTED_FORMAT) ? new MediaPlayerBase.UnsupportedFormatException() :
									new IOException())))));
				//_handleFailure used to be called only when howThePlayerStarted == SongList.HOW_NEXT_AUTO
				//and the song was being prepared
				if (howThePlayerStarted != SongList.HOW_CURRENT && howThePlayerStarted < 0)
					_handleFailure(result, false);
				else
					_updateState(false, result);
			}
		} else {
			_fullCleanup();
			_updateState(false, new Exception("Invalid MediaPlayer"));
		}
		return true;
	}

	@Override
	public boolean onInfo(MediaPlayerBase mediaPlayer, int what, int extra, Object extraObject) {
		if (mediaPlayer == player) {
			switch (what) {
			case MediaPlayerBase.INFO_BUFFERING_START:
				if (!playerBuffering) {
					playerBuffering = true;
					_updateState(true, null);
				}
				break;
			case MediaPlayerBase.INFO_BUFFERING_END:
				if (playerBuffering) {
					playerBuffering = false;
					_updateState(true, null);
				}
				break;
			case MediaPlayerBase.INFO_METADATA_UPDATE:
				//this message must be handled from the main thread
				if (MainHandler.isOnMainThread()) {
					if (extraObject instanceof HttpStreamReceiver.Metadata)
						httpStreamReceiverMetadataUpdate((HttpStreamReceiver.Metadata)extraObject);
				} else if (localHandler != null) {
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, httpStreamReceiverVersion, 0, extraObject), SystemClock.uptimeMillis());
				}
				break;
			case MediaPlayerBase.INFO_URL_UPDATE:
				if (state == STATE_ALIVE && song != null)
					song.path = extraObject.toString();
				break;
			}
		}
		return false;
	}

	@Override
	public void onPrepared(MediaPlayerBase mediaPlayer) {
		if (state != STATE_ALIVE)
			return;
		if (mediaPlayer == nextPlayer) {
			if (nextSongScheduledForPreparation == nextSong && nextSong != null) {
				nextSongScheduledForPreparation = null;
				nextPlayerState = PLAYER_STATE_LOADED;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
					_setNextPlayer();
			}
		} else if (mediaPlayer == player) {
			if (songScheduledForPreparation == song && song != null) {
				songScheduledForPreparation = null;
				playerState = PLAYER_STATE_LOADED;
				try {
					if (!hasFocus) {
						_fullCleanup();
						_updateState(false, null);
						return;
					}
					//when dimmed, decreased the volume by 20dB
					float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
					if (httpStreamReceiverActsLikePlayer)
						httpStreamReceiver.setVolume(multiplier, multiplier);
					else
						player.setVolume(multiplier, multiplier);
					if (storedSongTime < 0 || song.isHttp) {
						storedSongTime = -1;
						_startPlayer();
						_scheduleNextPlayerForPreparation();
					} else {
						playAfterSeeking = true;
						prepareNextAfterSeeking = true;
						playerState = PLAYER_STATE_PREPARING;
						seekInProgress = true;
						player.seekToAsync(storedSongTime);
					}
					songWhenFirstErrorHappened = null;
					_updateState(false, null);
				} catch (Throwable ex) {
					_fullCleanup();
					_handleFailure(ex, false);
				}
			}
		} else {
			_fullCleanup();
			_updateState(false, new Exception("Invalid MediaPlayer"));
		}
	}

	@Override
	public void onSeekComplete(MediaPlayerBase mediaPlayer) {
		if (state != STATE_ALIVE)
			return;
		if (mediaPlayer == player) {
			seekInProgress = false;
			try {
				playerState = PLAYER_STATE_LOADED;
				if (playAfterSeeking)
					_startPlayer();
				_updateState(false, null);
				if (prepareNextAfterSeeking)
					_scheduleNextPlayerForPreparation();
			} catch (Throwable ex) {
				_fullCleanup();
				_updateState(false, ex);
			}
		}
	}

	public static final int BUFFER_SIZE_500MS = 0x01;
	public static final int BUFFER_SIZE_1000MS = 0x00;
	public static final int BUFFER_SIZE_1500MS = 0x02;
	public static final int BUFFER_SIZE_2000MS = 0x03;
	public static final int BUFFER_SIZE_2500MS = 0x04;
	public static final int BUFFER_SIZE_MASK = 0x0F;

	public static final int FILL_THRESHOLD_25 = 0x10;
	public static final int FILL_THRESHOLD_50 = 0x20;
	public static final int FILL_THRESHOLD_75 = 0x30;
	public static final int FILL_THRESHOLD_100 = 0x00;
	public static final int FILL_THRESHOLD_MASK = 0xF0;

	public static final int FEATURE_PROCESSOR_ARM = 0x0001;
	public static final int FEATURE_PROCESSOR_NEON = 0x0002;
	public static final int FEATURE_PROCESSOR_X86 = 0x0004;
	public static final int FEATURE_PROCESSOR_SSE = 0x0008;
	public static final int FEATURE_PROCESSOR_SSE41 = 0x0010;
	public static final int FEATURE_PROCESSOR_64_BITS = 0x0020;
	public static final int FEATURE_DECODING_NATIVE = 0x0040;
	public static final int FEATURE_DECODING_DIRECT = 0x0080;

	public static int[] getCurrentPlaybackInfo() {
		return MediaContext.getCurrentPlaybackInfo();
	}

	public static int getFeatures() {
		return MediaContext.getFeatures();
	}

	public static int getCurrentAutomaticEffectsGainInMB() {
		return MediaContext.getCurrentAutomaticEffectsGainInMB();
	}

	public static int getBufferConfig() {
		return MediaContext.getBufferConfig();
	}

	public static void setBufferConfig(int bufferConfig) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_SET_BUFFER_CONFIG, bufferConfig, 0), SystemClock.uptimeMillis());
	}

	public static boolean isAutomaticEffectsGainEnabled() {
		return (MediaContext.isAutomaticEffectsGainEnabled() != 0);
	}

	public static void enableAutomaticEffectsGain(boolean enabled, Runnable callback) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_ENABLE_AUTOMATIC_EFFECTS_GAIN, enabled ? 1 : 0, 0, callback), SystemClock.uptimeMillis());
	}

	public static boolean isResamplingEnabled() {
		return MediaContext.isResamplingEnabled();
	}

	public static void enableResampling(boolean enabled) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_ENABLE_RESAMPLING, enabled ? 1 : 0, 0), SystemClock.uptimeMillis());
	}

	private static int httpStreamReceiverVersion, httpOptions;
	private static HttpStreamReceiver httpStreamReceiver;

	public static int getBytesBeforeDecoding(int index) {
		switch (index) {
		case 0:
			return (4 << 10);
		case 1:
			return (8 << 10);
		case 3:
			return (32 << 10);
		case 4:
			return (48 << 10);
		case 5:
			return (64 << 10);
		case 6:
			return (128 << 10);
		case 7:
			return (256 << 10);
		default:
			return (16 << 10);
		}
	}

	public static int getBytesBeforeDecodingIndex() {
		return (httpOptions & 0x0F);
	}

	public static void setBytesBeforeDecodingIndex(int bytesBeforeDecodingIndex) {
		httpOptions = (httpOptions & ~0x0F) | (bytesBeforeDecodingIndex & 0x0F);
	}

	public static int getMSBeforePlayback(int index) {
		switch (index) {
		case 0:
			return 500;
		case 2:
			return 1500;
		case 3:
			return 2000;
		case 4:
			return 2500;
		default:
			return 1000;
		}
	}

	public static int getMSBeforePlaybackIndex() {
		return ((httpOptions >>> 4) & 0x0F);
	}

	public static void setMSBeforePlayingIndex(int msBeforePlayingIndex) {
		httpOptions = (httpOptions & ~0xF0) | ((msBeforePlayingIndex & 0x0F) << 4);
	}

	private static void _httpStreamReceiverError(int version, Throwable exception, int errorCode) {
		if (state != STATE_ALIVE || version != httpStreamReceiverVersion || song == null || player == null || thePlayer == null)
			return;
		_releaseInternetObjects();
		if (!Player.isConnectedToTheInternet()) {
			exception = null;
			errorCode = MediaPlayerBase.ERROR_NOT_FOUND;
		}
		thePlayer.onError(player,
			((exception != null) && (exception instanceof MediaPlayerBase.MediaServerDiedException)) ? MediaPlayerBase.ERROR_SERVER_DIED :
				MediaPlayerBase.ERROR_UNKNOWN,
			((exception == null) ? errorCode :
				((exception instanceof MediaPlayerBase.TimeoutException) ? MediaPlayerBase.ERROR_TIMED_OUT :
					((exception instanceof MediaPlayerBase.PermissionDeniedException) ? MediaPlayerBase.ERROR_PERMISSION :
						((exception instanceof OutOfMemoryError) ? MediaPlayerBase.ERROR_OUT_OF_MEMORY :
							((exception instanceof FileNotFoundException) ? MediaPlayerBase.ERROR_NOT_FOUND :
								((exception instanceof MediaPlayerBase.UnsupportedFormatException) ? MediaPlayerBase.ERROR_UNSUPPORTED_FORMAT :
									((exception instanceof IOException) ? MediaPlayerBase.ERROR_IO : MediaPlayerBase.ERROR_UNKNOWN))))))));
	}

	private static void httpStreamReceiverMetadataUpdate(HttpStreamReceiver.Metadata metadata) {
		if (state != STATE_ALIVE || localSong == null || localPlayer == null || thePlayer == null)
			return;
		String title = metadata.streamTitle;
		int i;
		if ((i = title.indexOf("StreamTitle")) < 0)
			return;
		if ((i = title.indexOf('=', i + 11)) < 0)
			return;
		if ((i = title.indexOf('\'', i + 1)) < 0)
			return;
		final int firstChar = i + 1;
		int loopCount = 0;
		do {
			loopCount++;
			if ((i = title.indexOf('\'', i + 1)) < 0)
				return;
		} while (title.charAt(i - 1) == '\\');
		title = title.substring(firstChar, i).trim();
		if (loopCount > 1)
			title = title.replace("\\'", "\'");
		if (title.length() > 0) {
			//****** NEVER update the song's path! we need the original title in order to be able to resolve it again, later!
			if (metadata.icyName != null && metadata.icyName.length() > 0) {
				localSong.artist = metadata.icyName;
				localSong.extraInfo = metadata.icyName;
			}
			if (metadata.icyUrl != null && metadata.icyUrl.length() > 0)
				localSong.album = metadata.icyUrl;
			localSong.title = title;
			broadcastStateChange(getCurrentTitle(isPreparing()), isPreparing(), true);
			//this will force a serialization when closing the app (saving this update)
			songs.markAsChanged();
			if (observer != null)
				observer.onPlayerMetadataChanged(localSong);
		}
	}

	private static void _httpStreamReceiverPrepared(int version) {
		if (state != STATE_ALIVE || version != httpStreamReceiverVersion || song == null || player == null || thePlayer == null)
			return;
		playerBuffering = false;
		thePlayer.onPrepared(player);
	}

	private static void _createInternetObjects() throws IOException {
		//force the player to always start playing as if coming from a pause
		silenceMode = SILENCE_NORMAL;
		playerBuffering = true;
		httpStreamReceiver = new HttpStreamReceiver(handler, MSG_HTTP_STREAM_RECEIVER_ERROR, MSG_HTTP_STREAM_RECEIVER_PREPARED, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, MSG_HTTP_STREAM_RECEIVER_URL_UPDATED, 0, ++httpStreamReceiverVersion, getBytesBeforeDecoding(getBytesBeforeDecodingIndex()), getMSBeforePlayback(getMSBeforePlaybackIndex()), audioSessionId, song.path);
		if (httpStreamReceiver.start()) {
			if ((httpStreamReceiverActsLikePlayer = httpStreamReceiver.isPerformingFullPlayback))
				return;
			playerBuffering = false;
			//Even though it happens very rarely, a few devices will freeze and produce an ANR
			//when calling setDataSource from the main thread :(
			player.setDataSource(httpStreamReceiver.getLocalURL());
			player.setOnPreparedListener(thePlayer);
			player.prepareAsync();
		} else {
			//when start() returns false, this means we were unable to create the local server
			_releaseInternetObjects();
			throw new MediaPlayerBase.PermissionDeniedException();
		}
	}

	private static void _releaseInternetObjects() {
		if (httpStreamReceiver != null) {
			httpStreamReceiverVersion++;
			httpStreamReceiver.release();
			httpStreamReceiverActsLikePlayer = false;
			httpStreamReceiver = null;
		}
	}

	//I know this is far from "organized"... but this is the only way to prevent the
	//class BluetoothVisualizerController from being loading into memory unnecessarily!!!
	public static Object bluetoothVisualizerController;
	//bluetoothVisualizerConfig bits:
	//0 1 2 = size
	//3 4 = speed
	//5 6 7 8 = frames to skip (index)
	//9 = vu meter
	public static int bluetoothVisualizerLastErrorMessage, bluetoothVisualizerConfig, bluetoothVisualizerState;
	public static final int BLUETOOTH_VISUALIZER_STATE_INITIAL = 0;
	public static final int BLUETOOTH_VISUALIZER_STATE_CONNECTING = 1;
	public static final int BLUETOOTH_VISUALIZER_STATE_CONNECTED = 2;
	public static final int BLUETOOTH_VISUALIZER_STATE_TRANSMITTING = 3;

	public static boolean startBluetoothVisualizer(ActivityHost activity, boolean startTransmissionOnConnection) {
		if (state != STATE_ALIVE)
			return false;
		stopBluetoothVisualizer();
		final BluetoothVisualizerControllerJni b = new BluetoothVisualizerControllerJni(activity, startTransmissionOnConnection);
		if (bluetoothVisualizerLastErrorMessage == 0) {
			bluetoothVisualizerController = b;
			return true;
		}
		b.destroy();
		return false;
	}

	public static void stopBluetoothVisualizer() {
		bluetoothVisualizerState = BLUETOOTH_VISUALIZER_STATE_INITIAL;
		if (bluetoothVisualizerController != null) {
			final BluetoothVisualizerControllerJni b = (BluetoothVisualizerControllerJni)bluetoothVisualizerController;
			bluetoothVisualizerController = null;
			b.destroy();
		}
	}

	private static void updateBluetoothVisualizer(boolean songHasChanged) {
		if (bluetoothVisualizerController != null) {
			final BluetoothVisualizerControllerJni b = (BluetoothVisualizerControllerJni)bluetoothVisualizerController;
			if (!songHasChanged && localPlaying)
				b.resetAndResume();
			else
				b.resume();
			b.playingChanged();
		}
	}

	public static int getBluetoothVisualizerSize() {
		final int size = (bluetoothVisualizerConfig & 7);
		return ((size <= 0) ? 0 : ((size >= 6) ? 6 : size));
	}

	public static void setBluetoothVisualizerSize(int size) {
		bluetoothVisualizerConfig = (bluetoothVisualizerConfig & (~7)) | ((size <= 0) ? 0 : ((size >= 6) ? 6 : size));
	}

	public static int getBluetoothVisualizerSpeed() {
		final int speed = ((bluetoothVisualizerConfig >> 3) & 3);
		return ((speed <= 0) ? 0 : ((speed >= 2) ? 2 : 1));
	}

	public static void setBluetoothVisualizerSpeed(int speed) {
		bluetoothVisualizerConfig = (bluetoothVisualizerConfig & (~(3 << 3))) | (((speed <= 0) ? 0 : ((speed >= 2) ? 2 : 1)) << 3);
	}

	public static int getBluetoothVisualizerFramesPerSecond(int framesToSkipIndex) {
		//index          0, 1, 2, 3, 4, 5, 6, 7 , 8 , 9 , 10, 11
		//frames to skip 0, 1, 2, 3, 4, 5, 9, 11, 14, 19, 29, 59
		//frames/second  60,30,20,15,12,10, 6, 5 , 4 , 3 , 2 , 1
		return ((framesToSkipIndex <= 5) ? (60 / (framesToSkipIndex + 1)) : (12 - framesToSkipIndex));
	}

	public static int getBluetoothVisualizerFramesToSkip() {
		return (getBluetoothVisualizerFramesPerSecond(11 - getBluetoothVisualizerFramesToSkipIndex()) - 1);
	}

	public static int getBluetoothVisualizerFramesToSkipIndex() {
		final int framesToSkipIndex = ((bluetoothVisualizerConfig >> 5) & 15);
		return ((framesToSkipIndex <= 0) ? 0 : ((framesToSkipIndex >= 11) ? 11 : framesToSkipIndex));
	}

	public static void setBluetoothVisualizerFramesToSkipIndex(int framesToSkipIndex) {
		bluetoothVisualizerConfig = (bluetoothVisualizerConfig & (~(15 << 5))) | (((framesToSkipIndex <= 0) ? 0 : ((framesToSkipIndex >= 11) ? 11 : framesToSkipIndex)) << 5);
	}

	public static boolean isBluetoothUsingVUMeter() {
		return ((bluetoothVisualizerConfig & (1 << 9)) != 0);
	}

	public static void setBluetoothUsingVUMeter(boolean usingVUMeter) {
		if (usingVUMeter)
			bluetoothVisualizerConfig |= (1 << 9);
		else
			bluetoothVisualizerConfig &= ~(1 << 9);
	}

	//these options were deprecated on version 74 in favor of their bit equivalents
	private static final int OPT_VOLUME = 0x0000;
	//private static final int OPT_CONTROLMODE = 0x0001;
	private static final int OPT_LASTTIME = 0x0002;
	private static final int OPT_PATH = 0x0003;
	private static final int OPT_ORIGINALPATH = 0x0004;
	private static final int OPT_FAVORITEFOLDERCOUNT = 0x0005;
	//private static final int OPT_BASSBOOSTMODE = 0x0006;
	private static final int OPT_FADEININCREMENTONFOCUS = 0x0007;
	private static final int OPT_FADEININCREMENTONPAUSE = 0x0008;
	private static final int OPT_FADEININCREMENTONOTHER = 0x0009;
	//private static final int OPT_NEXTPREPARATION = 0x000a;
	//private static final int OPT_PLAYFOLDERCLEARSLIST = 0x000b;
	//private static final int OPT_KEEPSCREENON = 0x000c;
	private static final int OPT_FORCEDORIENTATION = 0x000d;
	//private static final int OPT_DISPLAYVOLUMEINDB = 0x000e;
	//private static final int OPT_DOUBLECLICKMODE = 0x000f;
	//these 3 will no longer be used!
	//private static final int OPT_MSGADDSHOWN = 0x0010;
	//private static final int OPT_MSGPLAYSHOWN = 0x0011;
	//private static final int OPT_MSGSTARTUPSHOWN = 0x0012;
	//private static final int OPT_MARQUEETITLE = 0x0013;
	private static final int OPT_VOLUMECONTROLTYPE = 0x0014;
	//private static final int OPT_BLOCKBACKKEY = 0x0015;
	private static final int OPT_TURNOFFTIMERCUSTOMMINUTES = 0x0016;
	//private static final int OPT_ISDIVIDERVISIBLE = 0x0017;
	//private static final int OPT_ISVERTICALMARGINLARGE = 0x0018;
	//private static final int OPT_HANDLECALLKEY = 0x0019;
	//private static final int OPT_PLAYWHENHEADSETPLUGGED = 0x001a;
	//private static final int OPT_USEALTERNATETYPEFACE = 0x001b;
	//private static final int OPT_GOBACKWHENPLAYINGFOLDERS = 0x001c;
	//private static final int OPT_RANDOMMODE = 0x001d;
	private static final int OPT_FORCEDLOCALE = 0x001e;
	private static final int OPT_THEME = 0x001f;
	private static final int OPT_MSGS = 0x0020;
	private static final int OPT_MSGSTARTUP = 0x0021;
	//private static final int OPT_WIDGETTRANSPARENTBG = 0x0022;
	private static final int OPT_WIDGETTEXTCOLOR = 0x0023;
	private static final int OPT_WIDGETICONCOLOR = 0x0024;
	private static final int OPT_CUSTOMCOLORS = 0x0025;
	private static final int OPT_LASTVERSIONCODE = 0x0026;
	//private static final int OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING = 0x0027;
	//private static final int OPT_WRAPAROUNDLIST = 0x0028;
	//private static final int OPT_EXTRASPACING = 0x0029;
	//private static final int OPT_OLDBROWSERBEHAVIOR = 0x002A;
	//private static final int OPT_VISUALIZERORIENTATION = 0x002B;
	private static final int OPT_SONGEXTRAINFOMODE = 0x002C;
	private static final int OPT_TURNOFFTIMERSELECTEDMINUTES = 0x002D;
	private static final int OPT_IDLETURNOFFTIMERCUSTOMMINUTES = 0x002E;
	private static final int OPT_IDLETURNOFFTIMERSELECTEDMINUTES = 0x002F;
	//private static final int OPT_FLAT = 0x0030;
	//private static final int OPT_ALBUMART = 0x0031;
	private static final int OPT_RADIOSEARCHTERM = 0x0032;
	private static final int OPT_RADIOLASTGENRE = 0x0033;
	private static final int OPT_TRANSITION = 0x0034;
	private static final int OPT_BLUETOOTHVISUALIZERCONFIG = 0x0035;
	private static final int OPT_HEADSETHOOKACTIONS = 0x0036;
	private static final int OPT_RADIOLASTGENRESHOUTCAST = 0x0037;
	private static final int OPT_HTTPOPTIONS = 0x0038;
	private static final int OPT_MEDIACONTEXTBUFFERCONFIG = 0x0039;

	//values 0x01xx are shared among all effects
	//static final int OPT_EQUALIZER_ENABLED = 0x0100;
	//static final int OPT_EQUALIZER_PRESET = 0x0101;
	static final int OPT_EQUALIZER_LEVELCOUNT = 0x0102;
	static final int OPT_EQUALIZER_LEVEL0 = 0x20000;
	static final int OPT_EQUALIZER_LEVEL0_WIRE = 0x21000;
	static final int OPT_EQUALIZER_LEVEL0_BT = 0x22000;
	static final int OPT_EQUALIZER_LEVEL0_WIRE_MIC = 0x23000;
	//static final int OPT_BASSBOOST_ENABLED = 0x0110;
	static final int OPT_BASSBOOST_STRENGTH = 0x0111;
	//static final int OPT_VIRTUALIZER_ENABLED = 0x0112;
	static final int OPT_VIRTUALIZER_STRENGTH = 0x0113;
	static final int OPT_BASSBOOST_STRENGTH_WIRE = 0x0114;
	static final int OPT_VIRTUALIZER_STRENGTH_WIRE = 0x0115;
	static final int OPT_BASSBOOST_STRENGTH_BT = 0x0116;
	static final int OPT_VIRTUALIZER_STRENGTH_BT = 0x0117;
	static final int OPT_BASSBOOST_STRENGTH_WIRE_MIC = 0x0118;
	static final int OPT_VIRTUALIZER_STRENGTH_WIRE_MIC = 0x0119;

	private static final int OPTBIT_CONTROLMODE = 0;
	private static final int OPTBIT_BASSBOOSTMODE = 1;
	private static final int OPTBIT_NEXTPREPARATION = 2;
	private static final int OPTBIT_PLAYFOLDERCLEARSLIST = 3;
	private static final int OPTBIT_KEEPSCREENON = 4;
	//volume control changed on version 71
	private static final int OPTBIT_DISPLAYVOLUMEINDB = 5;
	private static final int OPTBIT_VOLUMECONTROLTYPE0 = 5;
	private static final int OPTBIT_DOUBLECLICKMODE = 6;
	private static final int OPTBIT_MARQUEETITLE = 7;
	private static final int OPTBIT_FLAT = 8;
	private static final int OPTBIT_ALBUMART = 9;
	private static final int OPTBIT_BLOCKBACKKEY = 10;
	private static final int OPTBIT_ISDIVIDERVISIBLE = 11;
	private static final int OPTBIT_ISVERTICALMARGINLARGE = 12;
	private static final int OPTBIT_HANDLECALLKEY = 13;
	private static final int OPTBIT_PLAYWHENHEADSETPLUGGED = 14;
	private static final int OPTBIT_USEALTERNATETYPEFACE = 15;
	private static final int OPTBIT_GOBACKWHENPLAYINGFOLDERS = 16;
	private static final int OPTBIT_RANDOMMODE = 17;
	private static final int OPTBIT_WIDGETTRANSPARENTBG = 18;
	private static final int OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING = 19;
	private static final int OPTBIT_WRAPAROUNDLIST = 20;
	private static final int OPTBIT_EXTRASPACING = 21;
	//this bit has been recycled on version 71
	//private static final int OPTBIT_OLDBROWSERBEHAVIOR = 22;
	private static final int OPTBIT_VOLUMECONTROLTYPE1 = 22;
	static final int OPTBIT_EQUALIZER_ENABLED = 23;
	static final int OPTBIT_BASSBOOST_ENABLED = 24;
	static final int OPTBIT_VIRTUALIZER_ENABLED = 25;
	//this bit has been removed on version 83
	//private static final int OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES = 26;
	//this bit has been recycled on version 87
	static final int OPTBIT_EXTERNALFX_ENABLED = 26;
	private static final int OPTBIT_DO_NOT_ATTENUATE_VOLUME = 27;
	private static final int OPTBIT_SCROLLBAR_TO_THE_LEFT = 28;
	private static final int OPTBIT_SCROLLBAR_SONGLIST0 = 29;
	private static final int OPTBIT_SCROLLBAR_SONGLIST1 = 30;
	private static final int OPTBIT_SCROLLBAR_BROWSER0 = 31;
	private static final int OPTBIT_SCROLLBAR_BROWSER1 = 32;
	private static final int OPTBIT_LASTRADIOSEARCHWASBYGENRE = 33;
	private static final int OPTBIT_EXPANDSEEKBAR = 34;
	private static final int OPTBIT_REPEATONE = 35;
	private static final int OPTBIT_NOTFULLSCREEN = 36;
	private static final int OPTBIT_CONTROLS_TO_THE_LEFT = 37;
	private static final int OPTBIT_BORDERS = 38;
	private static final int OPTBIT_ANIMATIONS = 39;
	private static final int OPTBIT_VISUALIZER_PORTRAIT = 40;
	private static final int OPTBIT_REPEATNONE = 41;
	private static final int OPTBIT_TURNOFFPLAYLIST = 42;
	static final int OPTBIT_EQUALIZER_ENABLED_WIRE = 43;
	static final int OPTBIT_BASSBOOST_ENABLED_WIRE = 44;
	static final int OPTBIT_VIRTUALIZER_ENABLED_WIRE = 45;
	static final int OPTBIT_EQUALIZER_ENABLED_BT = 46;
	static final int OPTBIT_BASSBOOST_ENABLED_BT = 47;
	static final int OPTBIT_VIRTUALIZER_ENABLED_BT = 48;
	private static final int OPTBIT_3D = 49;
	private static final int OPTBIT_FOLLOW_CURRENT_SONG = 50;
	private static final int OPTBIT_ANNOUNCE_CURRENT_SONG = 51;
	private static final int OPTBIT_PLACE_TITLE_AT_THE_BOTTOM = 52;
	private static final int OPTBIT_PLAY_WITH_LONG_PRESS = 53;
	private static final int OPTBIT_AUTOMATIC_EFFECTS_GAIN = 54;
	private static final int OPTBIT_USE_OPENSL_ENGINE = 55;
	private static final int OPTBIT_RESAMPLING_ENABLED = 56;
	private static final int OPTBIT_PREVIOUS_RESETS_AFTER_THE_BEGINNING = 57;
	private static final int OPTBIT_CHROMEBOOK = 58;
	private static final int OPTBIT_LARGE_TEXT_IS_22SP = 59;
	static final int OPTBIT_EQUALIZER_ENABLED_WIRE_MIC = 60;
	static final int OPTBIT_BASSBOOST_ENABLED_WIRE_MIC = 61;
	static final int OPTBIT_VIRTUALIZER_ENABLED_WIRE_MIC = 62;
	private static final int OPTBIT_DISPLAY_SONG_NUMBER_AND_COUNT = 63;
	private static final int OPTBIT_ALLOW_LOCK_SCREEN = 64;
	private static final int OPTBIT_FILE_PREFETCH_SIZE0 = 65;
	private static final int OPTBIT_FILE_PREFETCH_SIZE1 = 66;

	private static final int OPT_FAVORITEFOLDER0 = 0x10000;

	private static Notification notification;
	private static RemoteViews notificationRemoteViews;
	private static boolean appNotInForeground, idleTurnOffTimerSent;
	private static long turnOffTimerOrigin, idleTurnOffTimerOrigin;
	private static HashSet<String> favoriteFolders;
	private static PendingIntent intentActivityHost, intentPrevious, intentPlayPause, intentNext, intentExit;
	private static int headsetHookActions, headsetHookPressCount, telephonyFeatureState;
	public static String path, originalPath, radioSearchTerm;
	public static boolean lastRadioSearchWasByGenre, nextPreparationEnabled, doNotAttenuateVolume, clearListWhenPlayingFolders, controlMode, bassBoostMode, handleCallKey, playWhenHeadsetPlugged, goBackWhenPlayingFolders, turnOffWhenPlaylistEnds, followCurrentSong, announceCurrentSong;
	public static int radioLastGenre, radioLastGenreShoutcast, fadeInIncrementOnFocus, fadeInIncrementOnPause, fadeInIncrementOnOther, turnOffTimerCustomMinutes, turnOffTimerSelectedMinutes, idleTurnOffTimerCustomMinutes, idleTurnOffTimerSelectedMinutes, filePrefetchSize;
	public static Object radioStationCache, radioStationCacheShoutcast;

	public static SerializableMap loadConfigFromFile() {
		final SerializableMap opts = SerializableMap.deserialize("_Player");
		return ((opts == null) ? new SerializableMap() : opts);
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	private static void loadConfig() {
		final SerializableMap opts = loadConfigFromFile();
		UI.lastVersionCode = opts.getInt(OPT_LASTVERSIONCODE, 0);
		volumeDB = opts.getInt(OPT_VOLUME);
		if (volumeDB < VOLUME_MIN_DB)
			volumeDB = VOLUME_MIN_DB;
		else if (volumeDB > 0)
			volumeDB = 0;
		localVolumeDB = volumeDB;
		volumeMultiplier = ((volumeDB <= VOLUME_MIN_DB) ? 0.0f : ((volumeDB >= 0) ? 1.0f : (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0)));
		path = opts.getString(OPT_PATH);
		originalPath = opts.getString(OPT_ORIGINALPATH);
		storedSongTime = opts.getInt(OPT_LASTTIME, -1);
		fadeInIncrementOnFocus = opts.getInt(OPT_FADEININCREMENTONFOCUS, 125);
		fadeInIncrementOnPause = opts.getInt(OPT_FADEININCREMENTONPAUSE, 125);
		fadeInIncrementOnOther = opts.getInt(OPT_FADEININCREMENTONOTHER, 0);
		UI.forcedOrientation = opts.getInt(OPT_FORCEDORIENTATION);
		turnOffTimerCustomMinutes = opts.getInt(OPT_TURNOFFTIMERCUSTOMMINUTES, 30);
		if (turnOffTimerCustomMinutes < 1)
			turnOffTimerCustomMinutes = 1;
		turnOffTimerSelectedMinutes = opts.getInt(OPT_TURNOFFTIMERSELECTEDMINUTES, 0);
		if (turnOffTimerSelectedMinutes < 0)
			turnOffTimerSelectedMinutes = 0;
		idleTurnOffTimerCustomMinutes = opts.getInt(OPT_IDLETURNOFFTIMERCUSTOMMINUTES, 30);
		if (idleTurnOffTimerCustomMinutes < 1)
			idleTurnOffTimerCustomMinutes = 1;
		idleTurnOffTimerSelectedMinutes = opts.getInt(OPT_IDLETURNOFFTIMERSELECTEDMINUTES, 0);
		if (idleTurnOffTimerSelectedMinutes < 0)
			idleTurnOffTimerSelectedMinutes = 0;
		UI.customColors = opts.getBuffer(OPT_CUSTOMCOLORS);
		UI.is3D = ((UI.lastVersionCode < 87) || opts.getBit(OPTBIT_3D, true));
		UI.setTheme(null, (UI.lastVersionCode < 87) ? UI.THEME_FPLAY_DARK : opts.getInt(OPT_THEME, UI.THEME_FPLAY_DARK));
		UI.msgs = opts.getInt(OPT_MSGS, 0);
		UI.msgStartup = opts.getInt(OPT_MSGSTARTUP, 0);
		UI.widgetTextColor = opts.getInt(OPT_WIDGETTEXTCOLOR, 0xff000000);
		UI.widgetIconColor = opts.getInt(OPT_WIDGETICONCOLOR, 0xff000000);
		bluetoothVisualizerConfig = opts.getInt(OPT_BLUETOOTHVISUALIZERCONFIG, 2 | (2 << 3) | (3 << 5));
		Song.extraInfoMode = opts.getInt(OPT_SONGEXTRAINFOMODE, Song.EXTRA_ARTIST);
		radioSearchTerm = opts.getString(OPT_RADIOSEARCHTERM);
		radioLastGenre = opts.getInt(OPT_RADIOLASTGENRE, 21);
		radioLastGenreShoutcast = opts.getInt(OPT_RADIOLASTGENRESHOUTCAST, 20);
		httpOptions = opts.getInt(OPT_HTTPOPTIONS, 0x00000012);
		MediaContext._setBufferConfig(opts.getInt(OPT_MEDIACONTEXTBUFFERCONFIG));
		UI.transitions = opts.getInt(OPT_TRANSITION, UI.deviceSupportsAnimations ? (UI.TRANSITION_ZOOM_FADE | (UI.TRANSITION_ZOOM_FADE << 8)) : 0);
		UI.setTransitions((UI.lastVersionCode < 90 && UI.transitions != 0) ? (UI.TRANSITION_ZOOM_FADE | (UI.TRANSITION_ZOOM_FADE << 8)) : UI.transitions);
		headsetHookActions = opts.getInt(OPT_HEADSETHOOKACTIONS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE | (KeyEvent.KEYCODE_MEDIA_NEXT << 8) | (KeyEvent.KEYCODE_MEDIA_PREVIOUS << 16));

		if (UI.lastVersionCode >= 90) {
			UI.isChromebook = opts.getBit(OPTBIT_CHROMEBOOK);
		} else {
			//http://stackoverflow.com/q/39784415/3569421
			try {
				UI.isChromebook = theApplication.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
			} catch (Throwable ex) {
				UI.isChromebook = ("chromium".equals(Build.BRAND) || "chromium".equals(Build.MANUFACTURER));
			}
		}

		//the volume control types changed on version 71
		if (UI.lastVersionCode <= 70 && UI.lastVersionCode != 0) {
			final int volumeControlType = opts.getInt(OPT_VOLUMECONTROLTYPE, UI.isTV ? VOLUME_CONTROL_NONE : VOLUME_CONTROL_STREAM);
			if (volumeControlType == VOLUME_CONTROL_DB)
				setVolumeControlType(opts.getBit(OPTBIT_DISPLAYVOLUMEINDB) ? VOLUME_CONTROL_DB : VOLUME_CONTROL_PERCENT);
			else
				setVolumeControlType(volumeControlType);
		} else {
			//load the volume control type the new way
			final int defVolumeControlType = ((UI.isTV || UI.isChromebook) ? VOLUME_CONTROL_NONE : VOLUME_CONTROL_STREAM);
			setVolumeControlType(opts.getBitI(OPTBIT_VOLUMECONTROLTYPE0, defVolumeControlType & 1) |
					(opts.getBitI(OPTBIT_VOLUMECONTROLTYPE1, defVolumeControlType >> 1) << 1));
		}

		//the concept of bit was added on version 38 (the old way was completely removed on version 87)
		controlMode = opts.getBit(OPTBIT_CONTROLMODE);
		bassBoostMode = opts.getBit(OPTBIT_BASSBOOSTMODE);
		nextPreparationEnabled = opts.getBit(OPTBIT_NEXTPREPARATION, true);
		clearListWhenPlayingFolders = opts.getBit(OPTBIT_PLAYFOLDERCLEARSLIST);
		UI.keepScreenOn = opts.getBit(OPTBIT_KEEPSCREENON);
		UI.doubleClickMode = opts.getBit(OPTBIT_DOUBLECLICKMODE);
		UI.marqueeTitle = opts.getBit(OPTBIT_MARQUEETITLE, true);
		UI.setFlat((UI.lastVersionCode < 87) || opts.getBit(OPTBIT_FLAT, true));
		UI.hasBorders = ((UI.lastVersionCode >= 87) && opts.getBit(OPTBIT_BORDERS, false));
		UI.animationEnabled = ((UI.lastVersionCode < 76 && UI.deviceSupportsAnimations) || opts.getBit(OPTBIT_ANIMATIONS, UI.deviceSupportsAnimations));
		UI.albumArt = opts.getBit(OPTBIT_ALBUMART, true);
		UI.blockBackKey = opts.getBit(OPTBIT_BLOCKBACKKEY, UI.isChromebook);
		UI.isDividerVisible = opts.getBit(OPTBIT_ISDIVIDERVISIBLE, true);
		UI.setVerticalMarginLarge(opts.getBit(OPTBIT_ISVERTICALMARGINLARGE, true)); //UI.isLargeScreen || !UI.isLowDpiScreen));
		handleCallKey = opts.getBit(OPTBIT_HANDLECALLKEY, true);
		playWhenHeadsetPlugged = opts.getBit(OPTBIT_PLAYWHENHEADSETPLUGGED, true);
		goBackWhenPlayingFolders = opts.getBit(OPTBIT_GOBACKWHENPLAYINGFOLDERS);
		UI.widgetTransparentBg = opts.getBit(OPTBIT_WIDGETTRANSPARENTBG);
		UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = opts.getBit(OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING);
		UI.wrapAroundList = opts.getBit(OPTBIT_WRAPAROUNDLIST);
		UI.extraSpacing = opts.getBit(OPTBIT_EXTRASPACING, UI.isTV || UI.isLargeScreen);//(UI.screenWidth >= UI.dpToPxI(600)) || (UI.screenHeight >= UI.dpToPxI(600)));
		//headsetHookDoublePressPauses = opts.getBit(OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES);
		doNotAttenuateVolume = opts.getBit(OPTBIT_DO_NOT_ATTENUATE_VOLUME);
		UI.scrollBarToTheLeft = opts.getBit(OPTBIT_SCROLLBAR_TO_THE_LEFT);
		UI.songListScrollBarType = (opts.getBitI(OPTBIT_SCROLLBAR_SONGLIST1, 0) << 1) | opts.getBitI(OPTBIT_SCROLLBAR_SONGLIST0, UI.isTV ? 0 : 1);
		if (UI.songListScrollBarType == BgListView.SCROLLBAR_INDEXED)
			UI.songListScrollBarType = BgListView.SCROLLBAR_LARGE;
		UI.browserScrollBarType = (opts.getBitI(OPTBIT_SCROLLBAR_BROWSER1, UI.isTV ? 0 : 1) << 1) | opts.getBitI(OPTBIT_SCROLLBAR_BROWSER0, 0);
		lastRadioSearchWasByGenre = opts.getBit(OPTBIT_LASTRADIOSEARCHWASBYGENRE, true);
		UI.expandSeekBar = ((UI.lastVersionCode >= 87) && opts.getBit(OPTBIT_EXPANDSEEKBAR));
		songs.setRepeatMode(opts.getBit(OPTBIT_REPEATONE) ? SongList.REPEAT_ONE : (opts.getBit(OPTBIT_REPEATNONE) ? SongList.REPEAT_NONE : SongList.REPEAT_ALL));
		songs.setRandomMode(opts.getBit(OPTBIT_RANDOMMODE));
		UI.notFullscreen = opts.getBit(OPTBIT_NOTFULLSCREEN);
		UI.controlsToTheLeft = opts.getBit(OPTBIT_CONTROLS_TO_THE_LEFT);
		UI.visualizerPortrait = opts.getBit(OPTBIT_VISUALIZER_PORTRAIT);
		turnOffWhenPlaylistEnds = opts.getBit(OPTBIT_TURNOFFPLAYLIST);
		followCurrentSong = opts.getBit(OPTBIT_FOLLOW_CURRENT_SONG, true);
		announceCurrentSong = opts.getBit(OPTBIT_ANNOUNCE_CURRENT_SONG);
		UI.placeTitleAtTheBottom = opts.getBit(OPTBIT_PLACE_TITLE_AT_THE_BOTTOM);
		UI.playWithLongPress = opts.getBit(OPTBIT_PLAY_WITH_LONG_PRESS, true);
		MediaContext._enableAutomaticEffectsGain(opts.getBitI(OPTBIT_AUTOMATIC_EFFECTS_GAIN, 1));
		MediaContext.useOpenSLEngine = opts.getBit(OPTBIT_USE_OPENSL_ENGINE);
		MediaContext._enableResampling(opts.getBit(OPTBIT_RESAMPLING_ENABLED));
		previousResetsAfterTheBeginning = opts.getBit(OPTBIT_PREVIOUS_RESETS_AFTER_THE_BEGINNING);
		UI.largeTextIs22sp = opts.getBit(OPTBIT_LARGE_TEXT_IS_22SP, UI.isLargeScreen && (UI.scaledDensity > UI.density));
		UI.setUsingAlternateTypefaceAndForcedLocale(opts.getBit(OPTBIT_USEALTERNATETYPEFACE), opts.getInt(OPT_FORCEDLOCALE, UI.LOCALE_NONE));
		UI.displaySongNumberAndCount = opts.getBit(OPTBIT_DISPLAY_SONG_NUMBER_AND_COUNT, UI.lastVersionCode < 92);
		UI.allowPlayerAboveLockScreen = opts.getBit(OPTBIT_ALLOW_LOCK_SCREEN, true);
		filePrefetchSize = getFilePrefetchSizeFromOptions((opts.getBitI(OPTBIT_FILE_PREFETCH_SIZE1, 0) << 1) | opts.getBitI(OPTBIT_FILE_PREFETCH_SIZE0, 0));

		int count = opts.getInt(OPT_FAVORITEFOLDERCOUNT);
		if (count > 0) {
			if (count > 128)
				count = 128;
			favoriteFolders = new HashSet<>(count);
			for (int i = count - 1; i >= 0; i--) {
				final String f = opts.getString(OPT_FAVORITEFOLDER0 + i);
				if (f != null && f.length() > 1)
					favoriteFolders.add(f);
			}
		} else {
			if (favoriteFolders != null)
				favoriteFolders.clear();
			favoriteFolders = null;
		}
		Equalizer.loadConfig(opts);
		BassBoost.loadConfig(opts);
		Virtualizer.loadConfig(opts);
		if (!BuildConfig.X)
			ExternalFx.loadConfig(opts);
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	public static void saveConfig(boolean saveSongs) {
		final SerializableMap opts = new SerializableMap(96);
		opts.put(OPT_LASTVERSIONCODE, UI.VERSION_CODE);
		opts.put(OPT_VOLUME, volumeDB);
		opts.put(OPT_PATH, path);
		opts.put(OPT_ORIGINALPATH, originalPath);
		opts.put(OPT_LASTTIME, storedSongTime);
		opts.put(OPT_FADEININCREMENTONFOCUS, fadeInIncrementOnFocus);
		opts.put(OPT_FADEININCREMENTONPAUSE, fadeInIncrementOnPause);
		opts.put(OPT_FADEININCREMENTONOTHER, fadeInIncrementOnOther);
		opts.put(OPT_FORCEDORIENTATION, UI.forcedOrientation);
		opts.put(OPT_TURNOFFTIMERCUSTOMMINUTES, turnOffTimerCustomMinutes);
		opts.put(OPT_TURNOFFTIMERSELECTEDMINUTES, turnOffTimerSelectedMinutes);
		opts.put(OPT_IDLETURNOFFTIMERCUSTOMMINUTES, idleTurnOffTimerCustomMinutes);
		opts.put(OPT_IDLETURNOFFTIMERSELECTEDMINUTES, idleTurnOffTimerSelectedMinutes);
		opts.put(OPT_CUSTOMCOLORS, UI.customColors);
		opts.put(OPT_THEME, UI.theme);
		opts.put(OPT_FORCEDLOCALE, UI.forcedLocale);
		opts.put(OPT_MSGS, UI.msgs);
		opts.put(OPT_MSGSTARTUP, UI.msgStartup);
		opts.put(OPT_WIDGETTEXTCOLOR, UI.widgetTextColor);
		opts.put(OPT_WIDGETICONCOLOR, UI.widgetIconColor);
		opts.put(OPT_BLUETOOTHVISUALIZERCONFIG, bluetoothVisualizerConfig);
		opts.put(OPT_SONGEXTRAINFOMODE, Song.extraInfoMode);
		opts.put(OPT_RADIOSEARCHTERM, radioSearchTerm);
		opts.put(OPT_RADIOLASTGENRE, radioLastGenre);
		opts.put(OPT_RADIOLASTGENRESHOUTCAST, radioLastGenreShoutcast);
		opts.put(OPT_HTTPOPTIONS, httpOptions);
		opts.put(OPT_MEDIACONTEXTBUFFERCONFIG, MediaContext.getBufferConfig());
		opts.put(OPT_TRANSITION, UI.transitions);
		opts.put(OPT_HEADSETHOOKACTIONS, headsetHookActions);
		opts.putBit(OPTBIT_CONTROLMODE, controlMode);
		opts.putBit(OPTBIT_BASSBOOSTMODE, bassBoostMode);
		opts.putBit(OPTBIT_NEXTPREPARATION, nextPreparationEnabled);
		opts.putBit(OPTBIT_PLAYFOLDERCLEARSLIST, clearListWhenPlayingFolders);
		opts.putBit(OPTBIT_KEEPSCREENON, UI.keepScreenOn);
		opts.putBit(OPTBIT_VOLUMECONTROLTYPE0, (volumeControlType & 1) != 0);
		opts.putBit(OPTBIT_VOLUMECONTROLTYPE1, (volumeControlType & 2) != 0);
		opts.putBit(OPTBIT_DOUBLECLICKMODE, UI.doubleClickMode);
		opts.putBit(OPTBIT_MARQUEETITLE, UI.marqueeTitle);
		opts.putBit(OPTBIT_FLAT, UI.isFlat);
		opts.putBit(OPTBIT_BORDERS, UI.hasBorders);
		opts.putBit(OPTBIT_ANIMATIONS, UI.animationEnabled);
		opts.putBit(OPTBIT_ALBUMART, UI.albumArt);
		opts.putBit(OPTBIT_BLOCKBACKKEY, UI.blockBackKey);
		opts.putBit(OPTBIT_3D, UI.is3D);
		opts.putBit(OPTBIT_ISDIVIDERVISIBLE, UI.isDividerVisible);
		opts.putBit(OPTBIT_ISVERTICALMARGINLARGE, UI.isVerticalMarginLarge);
		opts.putBit(OPTBIT_HANDLECALLKEY, handleCallKey);
		opts.putBit(OPTBIT_PLAYWHENHEADSETPLUGGED, playWhenHeadsetPlugged);
		opts.putBit(OPTBIT_USEALTERNATETYPEFACE, UI.isUsingAlternateTypeface);
		opts.putBit(OPTBIT_GOBACKWHENPLAYINGFOLDERS, goBackWhenPlayingFolders);
		opts.putBit(OPTBIT_RANDOMMODE, songs.isInRandomMode());
		opts.putBit(OPTBIT_WIDGETTRANSPARENTBG, UI.widgetTransparentBg);
		opts.putBit(OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING, UI.backKeyAlwaysReturnsToPlayerWhenBrowsing);
		opts.putBit(OPTBIT_WRAPAROUNDLIST, UI.wrapAroundList);
		opts.putBit(OPTBIT_EXTRASPACING, UI.extraSpacing);
		//opts.putBit(OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES, headsetHookDoublePressPauses);
		opts.putBit(OPTBIT_DO_NOT_ATTENUATE_VOLUME, doNotAttenuateVolume);
		opts.putBit(OPTBIT_SCROLLBAR_TO_THE_LEFT, UI.scrollBarToTheLeft);
		opts.putBit(OPTBIT_SCROLLBAR_SONGLIST0, (UI.songListScrollBarType & 1) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_SONGLIST1, (UI.songListScrollBarType & 2) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_BROWSER0, (UI.browserScrollBarType & 1) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_BROWSER1, (UI.browserScrollBarType & 2) != 0);
		opts.putBit(OPTBIT_LASTRADIOSEARCHWASBYGENRE, lastRadioSearchWasByGenre);
		opts.putBit(OPTBIT_EXPANDSEEKBAR, UI.expandSeekBar);
		opts.putBit(OPTBIT_REPEATONE, songs.getRepeatMode() == SongList.REPEAT_ONE);
		opts.putBit(OPTBIT_REPEATNONE, songs.getRepeatMode() == SongList.REPEAT_NONE);
		opts.putBit(OPTBIT_NOTFULLSCREEN, UI.notFullscreen);
		opts.putBit(OPTBIT_CONTROLS_TO_THE_LEFT, UI.controlsToTheLeft);
		opts.putBit(OPTBIT_VISUALIZER_PORTRAIT, UI.visualizerPortrait);
		opts.putBit(OPTBIT_TURNOFFPLAYLIST, turnOffWhenPlaylistEnds);
		opts.putBit(OPTBIT_FOLLOW_CURRENT_SONG, followCurrentSong);
		opts.putBit(OPTBIT_ANNOUNCE_CURRENT_SONG, announceCurrentSong);
		opts.putBit(OPTBIT_PLACE_TITLE_AT_THE_BOTTOM, UI.placeTitleAtTheBottom);
		opts.putBit(OPTBIT_PLAY_WITH_LONG_PRESS, UI.playWithLongPress);
		opts.putBit(OPTBIT_AUTOMATIC_EFFECTS_GAIN, MediaContext.isAutomaticEffectsGainEnabled() != 0);
		opts.putBit(OPTBIT_USE_OPENSL_ENGINE, MediaContext.useOpenSLEngine);
		opts.putBit(OPTBIT_RESAMPLING_ENABLED, MediaContext.isResamplingEnabled());
		opts.putBit(OPTBIT_PREVIOUS_RESETS_AFTER_THE_BEGINNING, previousResetsAfterTheBeginning);
		opts.putBit(OPTBIT_CHROMEBOOK, UI.isChromebook);
		opts.putBit(OPTBIT_LARGE_TEXT_IS_22SP, UI.largeTextIs22sp);
		opts.putBit(OPTBIT_DISPLAY_SONG_NUMBER_AND_COUNT, UI.displaySongNumberAndCount);
		opts.putBit(OPTBIT_ALLOW_LOCK_SCREEN, UI.allowPlayerAboveLockScreen);
		final int filePrefetchSizeOption = getOptionsFromFilePrefetchSize(filePrefetchSize);
		opts.putBit(OPTBIT_FILE_PREFETCH_SIZE0, (filePrefetchSizeOption & 1) != 0);
		opts.putBit(OPTBIT_FILE_PREFETCH_SIZE1, (filePrefetchSizeOption & 2) != 0);

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
		Virtualizer.saveConfig(opts);
		if (!BuildConfig.X)
			ExternalFx.saveConfig(opts);
		opts.serialize("_Player");
		if (saveSongs)
			songs.serialize();
	}

	private static void createIntents() {
		if (intentActivityHost == null) {
			Intent intent = new Intent(theApplication, ActivityHost.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			intentActivityHost = PendingIntent.getActivity(theApplication, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(theApplication, Player.class);
			intent.setAction(Player.ACTION_PREVIOUS);
			intentPrevious = PendingIntent.getService(theApplication, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(theApplication, Player.class);
			intent.setAction(Player.ACTION_PLAY_PAUSE);
			intentPlayPause = PendingIntent.getService(theApplication, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(theApplication, Player.class);
			intent.setAction(Player.ACTION_NEXT);
			intentNext = PendingIntent.getService(theApplication, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(theApplication, Player.class);
			intent.setAction(Player.ACTION_EXIT);
			intentExit = PendingIntent.getService(theApplication, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		}
	}

	public static RemoteViews prepareRemoteViews(RemoteViews views, boolean prepareButtons, boolean notification, boolean notificationFirstTime) {
		createIntents();

		views.setTextViewText(R.id.lblTitle, getCurrentTitle(isPreparing()));

		if (prepareButtons) {
			if (notification) {
				UI.prepareNotificationPlaybackIcons();
				views.setImageViewBitmap(R.id.btnPlay, localPlaying ? UI.icPauseNotif : UI.icPlayNotif);
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrevNotif);
				views.setImageViewBitmap(R.id.btnNext, UI.icNextNotif);
				views.setImageViewBitmap(R.id.btnExit, UI.icExitNotif);

				if (notificationFirstTime) {
					views.setOnClickPendingIntent(R.id.btnPrev, intentPrevious);
					views.setOnClickPendingIntent(R.id.btnPlay, intentPlayPause);
					views.setOnClickPendingIntent(R.id.btnNext, intentNext);

					views.setOnClickPendingIntent(R.id.btnExit, intentExit);
				}
			} else {
				if (localSong == null)
					views.setTextViewText(R.id.lblArtist, "-");
				else
					views.setTextViewText(R.id.lblArtist, localSong.extraInfo);

				views.setTextColor(R.id.lblTitle, UI.widgetTextColor);
				views.setTextColor(R.id.lblArtist, UI.widgetTextColor);

				views.setOnClickPendingIntent(R.id.lblTitle, intentActivityHost);
				views.setOnClickPendingIntent(R.id.lblArtist, intentActivityHost);

				UI.prepareWidgetPlaybackIcons();
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrev);
				views.setImageViewBitmap(R.id.btnPlay, localPlaying ? UI.icPause : UI.icPlay);
				views.setImageViewBitmap(R.id.btnNext, UI.icNext);

				views.setOnClickPendingIntent(R.id.btnPrev, intentPrevious);
				views.setOnClickPendingIntent(R.id.btnPlay, intentPlayPause);
				views.setOnClickPendingIntent(R.id.btnNext, intentNext);
			}
		}
		return views;
	}

	@SuppressWarnings("deprecation")
	private static Notification getNotification() {
		boolean firstTime = false;
		if (notification == null) {
			firstTime = true;
			notification = new Notification();
			notification.icon = R.drawable.ic_notification;
			notification.when = 0;
			notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
			notification.contentIntent = intentActivityHost;
			notificationRemoteViews = new RemoteViews(theApplication.getPackageName(), (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? R.layout.notification : R.layout.notification_simple);
			notification.contentView = notificationRemoteViews;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			//any need for this technique???
			//https://developer.android.com/about/versions/android-5.0-changes.html#BehaviorMediaControl
			//https://developer.android.com/reference/android/app/Notification.MediaStyle.html
			notification.visibility = Notification.VISIBILITY_PUBLIC;
			if (mediaSession != null) {
				if (notification.extras == null)
					notification.extras = new Bundle();
				notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, mediaSession.getSessionToken());
			}
		}
		prepareRemoteViews(notificationRemoteViews, Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN, true, firstTime);
		return notification;
	}

	public static void setControlMode(boolean controlMode) {
		Player.controlMode = controlMode;
		if (observer != null)
			observer.onPlayerControlModeChanged(controlMode);
	}

	@Override
	public int compare(FileSt a, FileSt b) {
		return a.name.compareToIgnoreCase(b.name);
	}

	public static void addFavoriteFolder(String path) {
		if (favoriteFolders == null)
			favoriteFolders = new HashSet<>();
		favoriteFolders.add(path);
	}

	public static void removeFavoriteFolder(String path) {
		if (favoriteFolders == null)
			return;
		favoriteFolders.remove(path);
		if (favoriteFolders.size() == 0)
			favoriteFolders = null;
	}

	public static boolean isFavoriteFolder(String path) {
		return (favoriteFolders != null && favoriteFolders.contains(path));
	}

	public static FileSt[] getFavoriteFolders(int extra) {
		FileSt[] ffs;
		final int count = (favoriteFolders == null ? 0 : favoriteFolders.size());
		if (count == 0)
			return new FileSt[extra];
		ffs = new FileSt[count + extra];
		int i = 0;
		for (String f : favoriteFolders) {
			final int idx = f.lastIndexOf('/');
			ffs[i] = new FileSt(f, (idx >= 0 && idx < (f.length() - 1)) ? f.substring(idx + 1) : f, null, FileSt.TYPE_FAVORITE);
			i++;
		}
		ArraySorter.sort(ffs, 0, ffs.length - extra, thePlayer);
		return ffs;
	}

	public static int getFilePrefetchSizeFromOptions(int options) {
		switch (options) {
		case 1:
			return 64 << 10;
		case 2:
			return 128 << 10;
		case 3:
			return 256 << 10;
		default:
			return 0;
		}
	}

	public static int getOptionsFromFilePrefetchSize(int filePrefetchSize) {
		switch (filePrefetchSize) {
		case 64 << 10:
			return 1;
		case 128 << 10:
			return 2;
		case 256 << 10:
			return 3;
		default:
			return 0;
		}
	}

	public static void setSelectionAfterAdding(int positionToSelect) {
		if (!alreadySelected) {
			alreadySelected = true;
			if (!songs.selecting && !songs.moving)
				songs.setSelection(positionToSelect, false);
			if (!isMainActiveOnTop)
				Player.positionToCenter = positionToSelect;
		}
	}

	public static void songListDeserialized(Song newCurrentSong, int forcePlayIdx, int positionToSelect, Throwable ex) {
		if (positionToSelect >= 0)
			setSelectionAfterAdding(positionToSelect);
		if (newCurrentSong != null)
			localSong = newCurrentSong;
		if (handler != null) {
			if (newCurrentSong != null && state >= STATE_ALIVE)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_SONG_LIST_DESERIALIZED, newCurrentSong), SystemClock.uptimeMillis());
			if (ex != null)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_SONG_LIST_DESERIALIZED, ex), SystemClock.uptimeMillis());
		}
		switch (state) {
		case STATE_INITIALIZING:
		case STATE_INITIALIZING_STEP2:
			localHandler.sendEmptyMessageAtTime(MSG_INITIALIZATION_STEP, SystemClock.uptimeMillis());
			break;
		case STATE_ALIVE:
			if (ex == null && forcePlayIdx >= 0)
				play(forcePlayIdx);
			break;
		}
	}

	public static HttpURLConnection createConnection(String url) throws Throwable {
		HttpURLConnection urlConnection = (HttpURLConnection)(new URL(url)).openConnection();
		urlConnection.setInstanceFollowRedirects(false);
		urlConnection.setConnectTimeout(30000);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(false);
		urlConnection.setReadTimeout(30000);
		urlConnection.setRequestMethod("GET");
		urlConnection.setUseCaches(false);
		return urlConnection;
	}

	public static boolean deviceHasTelephonyRadio() {
		if (telephonyFeatureState == 0) {
			telephonyFeatureState = 2;
			try {
				final PackageManager packageManager = theApplication.getPackageManager();
				if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
					packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA) ||
					packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM))
					telephonyFeatureState = 1;
			} catch (Throwable ex) {
				//just ignore
			}
		}
		return (telephonyFeatureState == 1);
	}

	public static boolean isConnectedToTheInternet() {
		if (thePlayer != null) {
			try {
				final ConnectivityManager mngr = (ConnectivityManager)thePlayer.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo info = mngr.getActiveNetworkInfo();
				return (info != null && info.isConnected());
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		return false;
	}

	@SuppressWarnings({"deprecation", "unused"})
	public static boolean isInternetConnectedViaWiFi() {
		if (thePlayer != null) {
			try {
				final ConnectivityManager mngr = (ConnectivityManager)thePlayer.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo infoMob = mngr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
				final NetworkInfo info = mngr.getActiveNetworkInfo();
				return (infoMob != null && info != null && !infoMob.isConnected() && info.isConnected());
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		return false;
	}

	private static void processTurnOffTimer() {
		if (state > STATE_ALIVE)
			return;
		localHandler.removeMessages(MSG_TURN_OFF_TIMER);
		if (turnOffTimerOrigin > 0) {
			final int secondsLeft = (turnOffTimerSelectedMinutes * 60) - (int)((SystemClock.elapsedRealtime() - turnOffTimerOrigin) / 1000);
			if (turnOffTimerObserver != null)
				turnOffTimerObserver.onPlayerTurnOffTimerTick();
			if (secondsLeft < 15) //less than half of our period
				stopService();
			else
				localHandler.sendEmptyMessageAtTime(MSG_TURN_OFF_TIMER, SystemClock.uptimeMillis() + 30000);
		}
	}

	public static void setTurnOffTimer(int minutes) {
		if (state > STATE_ALIVE)
			return;
		if (minutes > 0) {
			if (minutes != 60 && minutes != 90 && minutes != 120)
				turnOffTimerCustomMinutes = minutes;
			//we must use SystemClock.elapsedRealtime because uptimeMillis
			//does not take sleep time into account (and the user makes no
			//difference between the time spent during sleep and the one
			//while actually working)
			turnOffTimerOrigin = SystemClock.elapsedRealtime();
			turnOffTimerSelectedMinutes = minutes;
		} else {
			turnOffTimerOrigin = 0;
			turnOffTimerSelectedMinutes = 0;
		}
		processTurnOffTimer();
	}

	public static int getTurnOffTimerMinutesLeft() {
		if (turnOffTimerOrigin <= 0)
			return turnOffTimerSelectedMinutes;
		final int m = turnOffTimerSelectedMinutes - (int)((SystemClock.elapsedRealtime() - turnOffTimerOrigin) / 60000L);
		return ((m <= 0) ? 1 : m);
	}

	public static void setAppNotInForeground(boolean appNotInForeground) {
		if (state > STATE_ALIVE)
			return;
		if (Player.appNotInForeground != appNotInForeground) {
			Player.appNotInForeground = appNotInForeground;
			if (idleTurnOffTimerSelectedMinutes > 0)
				processIdleTurnOffTimer();
		}
	}

	private static void processIdleTurnOffTimer() {
		if (state > STATE_ALIVE)
			return;
		if (idleTurnOffTimerSelectedMinutes <= 0) {
			idleTurnOffTimerSent = false;
			localHandler.removeMessages(MSG_IDLE_TURN_OFF_TIMER);
			return;
		}
		boolean wasPlayingBeforeOngoingCall = false, sendMessage = false;
		final boolean idle = (!localPlaying && appNotInForeground);
		if (idle && telephonyManager != null) {
			//check for ongoing call
			try {
				if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
					wasPlayingBeforeOngoingCall = resumePlaybackAfterFocusGain;
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (!idle || wasPlayingBeforeOngoingCall) {
			if (idle) {
				//consider time spent in calls as active time, but keep checking,
				//because when the call ends, the audio focus could go to someone
				//else, rendering us actually idle!
				idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
				sendMessage = true;
			} else {
				idleTurnOffTimerOrigin = 0; //it's safe to reset the origin
			}
			if (turnOffTimerObserver != null)
				turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
		} else {
			if (idleTurnOffTimerOrigin > 0) {
				final int secondsLeft = (idleTurnOffTimerSelectedMinutes * 60) - (int)((SystemClock.elapsedRealtime() - idleTurnOffTimerOrigin) / 1000);
				if (turnOffTimerObserver != null)
					turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
				if (secondsLeft < 15) //less than half of our period
					stopService();
				else
					sendMessage = true;
			} else {
				idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
				sendMessage = true;
			}
		}
		if (sendMessage) {
			if (!idleTurnOffTimerSent) {
				idleTurnOffTimerSent = true;
				localHandler.sendEmptyMessageAtTime(MSG_IDLE_TURN_OFF_TIMER, SystemClock.uptimeMillis() + 30000);
			}
		} else {
			if (idleTurnOffTimerSent) {
				idleTurnOffTimerSent = false;
				localHandler.removeMessages(MSG_IDLE_TURN_OFF_TIMER);
			}
		}
	}

	public static void setIdleTurnOffTimer(int minutes) {
		if (state > STATE_ALIVE)
			return;
		idleTurnOffTimerOrigin = 0;
		idleTurnOffTimerSent = false;
		localHandler.removeMessages(MSG_IDLE_TURN_OFF_TIMER);
		if (minutes > 0) {
			if (minutes != 60 && minutes != 90 && minutes != 120)
				idleTurnOffTimerCustomMinutes = minutes;
			idleTurnOffTimerSelectedMinutes = minutes;
			processIdleTurnOffTimer();
		} else {
			idleTurnOffTimerSelectedMinutes = 0;
		}
	}

	public static int getIdleTurnOffTimerMinutesLeft() {
		if (idleTurnOffTimerOrigin <= 0)
			return idleTurnOffTimerSelectedMinutes;
		final int m = idleTurnOffTimerSelectedMinutes - (int)((SystemClock.elapsedRealtime() - idleTurnOffTimerOrigin) / 60000L);
		return ((m <= 0) ? 1 : m);
	}

	private static void _songListDeserialized(Object obj) {
		if (obj instanceof Throwable) {
			_updateState(false, (Throwable)obj);
		} else {
			song = (Song)obj;
			_updateState(true, null);
		}
	}

	private static Intent stickyBroadcast;
	private static ExternalReceiver externalReceiver;
	private static ComponentName mediaButtonEventReceiver;
	@SuppressWarnings("deprecation")
	private static RemoteControlClient remoteControlClient;
	private static MediaSession mediaSession;
	private static MediaMetadata.Builder mediaSessionMetadataBuilder;
	private static PlaybackState.Builder mediaSessionPlaybackStateBuilder;
	private static Object mediaRouterCallback;

	private static TypedRawArrayList<PlayerDestroyedObserver> destroyedObservers;
	public static PlayerTurnOffTimerObserver turnOffTimerObserver;
	public static PlayerObserver observer;

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void registerMediaRouter() {
		final MediaRouter mr = (MediaRouter)theApplication.getSystemService(MEDIA_ROUTER_SERVICE);
		if (mr != null) {
			mediaRouterCallback = new MediaRouter.Callback() {
				@Override
				public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteUngrouped(MediaRouter router, MediaRouter.RouteInfo info, MediaRouter.RouteGroup group) {
				}
				@Override
				public void onRouteSelected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteGrouped(MediaRouter router, MediaRouter.RouteInfo info, MediaRouter.RouteGroup group, int index) {
				}
				@Override
				public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo info) {
					if (info.getPlaybackStream() == AudioManager.STREAM_MUSIC) {
						//this actually works... nonetheless, I was not able to detected
						//which is the audio sink used by this route.... :(
						volumeStreamMax = info.getVolumeMax();
						if (volumeStreamMax < 1)
							volumeStreamMax = 1;
						audioSinkChanged(false, false);
					}
				}
				@Override
				public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo info) {
				}
			};
			mr.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_USER, (MediaRouter.Callback)mediaRouterCallback);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void unregisterMediaRouter() {
		final MediaRouter mr = (MediaRouter)theApplication.getSystemService(MEDIA_ROUTER_SERVICE);
		if (mediaRouterCallback != null && mr != null)
			mr.removeCallback((MediaRouter.Callback)mediaRouterCallback);
		mediaRouterCallback = null;
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private static void registerRemoteControlClientCallbacks() {
		remoteControlClient.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener() {
			@Override
			public long onGetPlaybackPosition() {
				return getPosition();
			}
		});
		remoteControlClient.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener() {
			@Override
			public void onPlaybackPositionUpdate(long pos) {
				seekTo((int)pos);
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private static void unregisterRemoteControlClientCallbacks() {
		remoteControlClient.setOnGetPlaybackPositionListener(null);
		remoteControlClient.setPlaybackPositionUpdateListener(null);
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void registerRemoteControlClient() {
		try {
			if (remoteControlClient == null) {
				final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
				mediaButtonIntent.setComponent(mediaButtonEventReceiver);
				final PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(thePlayer, 0, mediaButtonIntent, 0);
				remoteControlClient = new RemoteControlClient(mediaPendingIntent);
				remoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS | RemoteControlClient.FLAG_KEY_MEDIA_NEXT | RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_STOP);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
					registerRemoteControlClientCallbacks();
			}
			audioManager.registerRemoteControlClient(remoteControlClient);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void unregisterRemoteControlClient() {
		try {
			if (remoteControlClient != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
					unregisterRemoteControlClientCallbacks();
				audioManager.unregisterRemoteControlClient(remoteControlClient);
				remoteControlClient = null;
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void registerMediaSession() {
		try {
			if (mediaSession == null && thePlayer != null) {
				mediaSessionMetadataBuilder = new MediaMetadata.Builder();
				mediaSessionPlaybackStateBuilder = new PlaybackState.Builder();
				mediaSessionPlaybackStateBuilder.setActions(PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO);
				mediaSession = new MediaSession(thePlayer, "FPlay");
				mediaSession.setCallback(new MediaSession.Callback() {
					@Override
					public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
						final Object o = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
						if (o == null || !(o instanceof KeyEvent))
							return false;
						final KeyEvent e = (KeyEvent)o;
						if (e.getAction() == KeyEvent.ACTION_DOWN)
							handleMediaButton(e.getKeyCode());
						return true;
					}

					@Override
					public void onPlay() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY);
					}

					@Override
					public void onPause() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE);
					}

					@Override
					public void onSkipToNext() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);
					}

					@Override
					public void onSkipToPrevious() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
					}

					@Override
					public void onStop() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_STOP);
					}

					@Override
					public void onSeekTo(long pos) {
						seekTo((int)pos);
					}
				});
				mediaSession.setSessionActivity(intentActivityHost);
				mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1, SystemClock.elapsedRealtime()).build());
			}
			if (mediaSession != null)
				mediaSession.setActive(true);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void unregisterMediaSession() {
		try {
			if (mediaSession != null) {
				mediaSession.setActive(false);
				mediaSession.setCallback(null);
				mediaSession.release();
				mediaSession = null;
			}
			mediaSessionMetadataBuilder = null;
			mediaSessionPlaybackStateBuilder = null;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	public static void registerMediaButtonEventReceiver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			registerMediaSession();
		} else {
			if (mediaButtonEventReceiver == null)
				mediaButtonEventReceiver = new ComponentName(BuildConfig.APPLICATION_ID, "br.com.carlosrafaelgn.fplay.ExternalReceiver");
			if (audioManager != null) {
				audioManager.registerMediaButtonEventReceiver(mediaButtonEventReceiver);
				if (thePlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					registerRemoteControlClient();
			}
		}
	}

	@SuppressWarnings("deprecation")
	public static void unregisterMediaButtonEventReceiver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			unregisterMediaSession();
		} else {
			if (mediaButtonEventReceiver != null && audioManager != null) {
				if (remoteControlClient != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					unregisterRemoteControlClient();
				audioManager.unregisterMediaButtonEventReceiver(mediaButtonEventReceiver);
			}
			mediaButtonEventReceiver = null;
		}
	}

	public static void becomingNoisy() {
		if (handler != null)
			handler.sendEmptyMessageAtTime(MSG_BECOMING_NOISY, SystemClock.uptimeMillis());
	}

	public static void audioSinkChanged(boolean wiredHeadsetJustPlugged, boolean wiredHeadsetHasMicrophone) {
		if (handler != null)
			handler.sendMessageAtTime(Message.obtain(handler, MSG_AUDIO_SINK_CHANGED, wiredHeadsetJustPlugged ? 1 : 0, wiredHeadsetHasMicrophone ? 1 : 0), SystemClock.uptimeMillis());
	}

	public static void setHeadsetHookAction(int pressCount, int action) {
		switch (pressCount) {
		case 1:
			headsetHookActions = (headsetHookActions & ~0xFF) | (action & 0xFF);
			break;
		case 2:
			headsetHookActions = (headsetHookActions & ~0xFF00) | ((action & 0xFF) << 8);
			break;
		case 3:
			headsetHookActions = (headsetHookActions & ~0xFF0000) | ((action & 0xFF) << 16);
			break;
		}
	}

	public static int getHeadsetHookAction(int pressCount) {
		return ((headsetHookActions >> ((pressCount == 1) ? 0 : ((pressCount == 2) ? 8 : 16))) & 0xFF);
	}

	private static void resetHeadsetHook() {
		headsetHookPressCount = 0;
		if (localHandler != null)
			localHandler.removeMessages(MSG_HEADSET_HOOK_TIMER);
	}

	private static void processHeadsetHookTimer() {
		if (state != STATE_ALIVE)
			return;
		final int action = getHeadsetHookAction(headsetHookPressCount);
		resetHeadsetHook();
		switch (action) {
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			next();
			break;
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			previous();
			break;
		}
	}

	public static boolean isMediaButton(int keyCode) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_MEDIA_PLAY:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_BREAK:
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_STOP:
		case KeyEvent.KEYCODE_CALL:
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
		case KeyEvent.KEYCODE_MEDIA_REWIND:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			return true;
		}
		return false;
	}

	public static boolean handleMediaButton(int keyCode) {
		switch (keyCode) {
		//There are a few weird bluetooth headsets that despite having only one physical
		//play/pause button, will try to simulate individual PLAY and PAUSE events,
		//instead of sending the proper event PLAY_PAUSE... The problem is, they are not
		//always synchronized with the actual player state, and therefore, the user ends
		//up having to press that button twice for something to happen! :(
		case KeyEvent.KEYCODE_MEDIA_PLAY:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_BREAK:
			playPause();
			break;
		case KeyEvent.KEYCODE_HEADSETHOOK:
			if (localHandler != null) {
				localHandler.removeMessages(MSG_HEADSET_HOOK_TIMER);
				headsetHookPressCount++;
				if (headsetHookPressCount >= 3)
					processHeadsetHookTimer();
				else
					localHandler.sendEmptyMessageAtTime(MSG_HEADSET_HOOK_TIMER, SystemClock.uptimeMillis() + 500);
			}
			break;
		case KeyEvent.KEYCODE_MEDIA_STOP:
			pause();
			break;
		case KeyEvent.KEYCODE_CALL:
			if (!handleCallKey)
				return false;
			playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			next();
			if (observer != null)
				observer.onPlayerMediaButtonNext();
			break;
		case KeyEvent.KEYCODE_MEDIA_REWIND:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			previous();
			if (observer != null)
				observer.onPlayerMediaButtonPrevious();
			break;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			if (volumeControlType == VOLUME_CONTROL_STREAM) {
				keyCode = decreaseVolume();
				if (observer != null)
					observer.onPlayerGlobalVolumeChanged(keyCode);
				break;
			}
			break;
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (volumeControlType == VOLUME_CONTROL_STREAM) {
				keyCode = increaseVolume();
				if (observer != null)
					observer.onPlayerGlobalVolumeChanged(keyCode);
				break;
			}
			break;
		default:
			return false;
		}
		return true;
	}

	public static void listCleared() {
		if (state != STATE_ALIVE)
			return;
		handler.sendEmptyMessageAtTime(MSG_LIST_CLEARED, SystemClock.uptimeMillis());
	}

	public static void nextMayHaveChanged(Song possibleNextSong) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_NEXT_MAY_HAVE_CHANGED, possibleNextSong), SystemClock.uptimeMillis());
	}

	public static void addDestroyedObserver(PlayerDestroyedObserver observer) {
		if (destroyedObservers != null && !destroyedObservers.contains(observer))
			destroyedObservers.add(observer);
	}

	public static void removeDestroyedObserver(PlayerDestroyedObserver observer) {
		if (destroyedObservers != null)
			destroyedObservers.remove(observer);
	}

	private static boolean _checkAudioSinkMicrophone() {
		//AudioSystem.getDeviceConnectionState(DEVICE_OUT_WIRED_HEADSET,"") != AudioSystem.DEVICE_STATE_UNAVAILABLE
		//DEVICE_OUT_WIRED_HEADSET = 0x4
		//DEVICE_STATE_UNAVAILABLE = 0
		if (!audioSinkMicrophoneCheckDone) {
			audioSinkMicrophoneCheckDone = true;
			audioSinkMicrophoneCheckMethod = null;
			try {
				for (Method method : Player.class.getClassLoader().loadClass("android.media.AudioSystem").getMethods()) {
					//public static native int getDeviceConnectionState(int device, String device_address);
					if (method.getName().equals("getDeviceConnectionState")) {
						audioSinkMicrophoneCheckMethod = method;
						break;
					}
				}
			} catch (Throwable ex) {
				//just ignore
				ex.printStackTrace();
			}
		}
		if (audioSinkMicrophoneCheckMethod == null)
			return false;
		try {
			return ((int)audioSinkMicrophoneCheckMethod.invoke(null, 4, "") != 0);
		} catch (Throwable ex) {
			audioSinkMicrophoneCheckMethod = null;
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static int _checkAudioSinkViaRoute() {
		try {
			final MediaRouter router = (MediaRouter)theApplication.getSystemService(Context.MEDIA_ROUTER_SERVICE);
			final MediaRouter.RouteInfo info = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
			final String name = info.getName(theApplication).toString();

			//com.android.internal.R.string.default_audio_route_name_headphones = "Headphones"
			//com.android.internal.R.string.bluetooth_a2dp_audio_route_name = "Bluetooth audio"
			int id;
			String original;

			final Class<?> clazz = Class.forName("com.android.internal.R$string");

			Field field = clazz.getDeclaredField("bluetooth_a2dp_audio_route_name");
			field.setAccessible(true);
			id = (int)field.get(null);
			original = theApplication.getText(id).toString();
			if (name.equalsIgnoreCase(original))
				return AUDIO_SINK_BT;

			field = clazz.getDeclaredField("default_audio_route_name_headphones");
			field.setAccessible(true);
			id = (int)field.get(null);
			original = theApplication.getText(id).toString();
			if (name.equalsIgnoreCase(original))
				return AUDIO_SINK_WIRE;

			//Another possible hack (but fails sometimes)
			//Class<?> audioSystem = Class.forName("android.media.AudioSystem");
			//Method getd = audioSystem.getMethod("getDeviceConnectionState", int.class, String.class);
			//int x;
			//public static final int DEVICE_OUT_EARPIECE = 0x1;
			//public static final int DEVICE_OUT_SPEAKER = 0x2;
			//public static final int DEVICE_OUT_WIRED_HEADSET = 0x4;
			//public static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;
			//public static final int DEVICE_OUT_BLUETOOTH_SCO = 0x10;
			//public static final int DEVICE_OUT_BLUETOOTH_SCO_HEADSET = 0x20;
			//public static final int DEVICE_OUT_BLUETOOTH_SCO_CARKIT = 0x40;
			//public static final int DEVICE_OUT_BLUETOOTH_A2DP = 0x80;
			//public static final int DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES = 0x100;
			//public static final int DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER = 0x200;
			//public static final int DEVICE_STATE_UNAVAILABLE = 0;
			//public static final int DEVICE_STATE_AVAILABLE = 1;
			//x = (int)getd.invoke(null, 0x1, "");
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return 0;
	}

	@SuppressWarnings("deprecation")
	private static void _checkAudioSink(boolean wiredHeadsetJustPlugged, boolean wiredHeadsetHasMicrophone, boolean triggerNoisy, boolean reinitializeEffects) {
		if (audioManager == null)
			return;
		final int oldAudioSink = audioSink;
		//let the guessing begin!!! really, it is NOT possible to rely solely on
		//these AudioManager.isXXX() methods, neither on MediaRouter...
		//I would really be happy if things were as easy as the doc says... :(
		//https://developer.android.com/training/managing-audio/audio-output.html
		audioSink = 0;
		try {
			//isSpeakerphoneOn() has not actually returned true on any devices
			//I have tested so far, anyway.... leaving this here won't hurt...
			if (audioManager.isSpeakerphoneOn())
				audioSink = AUDIO_SINK_DEVICE;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			//apparently, devices tend to use the wired headset over bluetooth headsets
			if (audioSink == 0 && (wiredHeadsetJustPlugged || audioManager.isWiredHeadsetOn()))
				audioSink = AUDIO_SINK_WIRE;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			//this whole A2dp thing is still not enough, as isA2dpPlaying()
			//will return false if there is nothing playing, even in scenarios
			//where A2dp will certainly be used for playback later...
			/*if (audioManager.isBluetoothA2dpOn()) {
				//the device being on is not enough! we must be sure
				//if it is actually being used for transmission...
				if (thePlayer == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					if (audioSink == 0)
						audioSink = AUDIO_SINK_BT;
				} else {
					if (audioSink == 0 || isA2dpPlaying())
						audioSink = AUDIO_SINK_BT;
				}
			}*/
			if (audioSink == 0 && audioManager.isBluetoothA2dpOn())
				audioSink = AUDIO_SINK_BT;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		if (audioSink == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			audioSink = _checkAudioSinkViaRoute();
		if (audioSink == 0)
			audioSink = AUDIO_SINK_DEVICE;
		else if (audioSink == AUDIO_SINK_WIRE && (wiredHeadsetHasMicrophone || _checkAudioSinkMicrophone()))
			audioSink = AUDIO_SINK_WIRE_MIC;
		if (oldAudioSink != audioSink && oldAudioSink != 0) {
			switch (audioSink) {
			case AUDIO_SINK_WIRE:
			case AUDIO_SINK_WIRE_MIC:
				if (!playing && playWhenHeadsetPlugged) {
					if (!hasFocus) {
						try {
							if (telephonyManager != null && telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
								break;
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
					}
					if (reinitializeEffects && audioSinkUsedInEffects != audioSink && player != null) {
						reinitializeEffects = false;
						_reinitializeEffects();
					}
					_playPause();
				}
				break;
			case AUDIO_SINK_DEVICE:
				if (triggerNoisy) {
					//this cleanup must be done, as sometimes, when changing between two output types,
					//the effects are lost...
					if (playing)
						_playPause();
					_fullCleanup();
				}
				break;
			}
		}
		if (reinitializeEffects && audioSinkUsedInEffects != audioSink && player != null)
			_reinitializeEffects();
		//I am calling the observer even if no changes have been detected, because
		//I myself don't trust this code will correctly work as expected on every device....
		if (localHandler != null)
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_AUDIO_SINK_CHANGED, audioSink, 0), SystemClock.uptimeMillis());
	}

	private static void _nextMayHaveChanged(Song possibleNextSong) {
		if (nextSong != possibleNextSong && nextPreparationEnabled) {
			nextSong = possibleNextSong;
			if (playerState == PLAYER_STATE_LOADED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				_clearNextPlayer();
			if (nextPlayer != null)
				nextPlayer.reset();
			if (playerState == PLAYER_STATE_LOADED)
				_scheduleNextPlayerForPreparation();
		}
	}

	private static Song stateLastSong;
	private static boolean stateLastPlaying, stateLastPreparing;

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void broadcastStateChangeToRemoteControl(String title, boolean preparing, boolean titleOrSongHaveChanged) {
		try {
			if (localSong == null) {
				remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			} else {
				remoteControlClient.setPlaybackState(preparing ? RemoteControlClient.PLAYSTATE_BUFFERING : (localPlaying ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED));
				if (titleOrSongHaveChanged) {
					final RemoteControlClient.MetadataEditor ed = remoteControlClient.editMetadata(true);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, localSong.artist);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, localSong.album);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, localSong.track);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, localSong.lengthMS);
					//Oh!!!! METADATA_KEY_YEAR is only handled in API 19+ !!! :(
					//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.4_r1/android/media/MediaMetadataEditor.java#MediaMetadataEditor.0METADATA_KEYS_TYPE
					//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.1.2_r1/android/media/RemoteControlClient.java#RemoteControlClient.0METADATA_KEYS_TYPE_LONG
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
						ed.putLong(MediaMetadataRetriever.METADATA_KEY_YEAR, localSong.year);
					ed.apply();
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void broadcastStateChangeToMediaSession(String title, boolean preparing, boolean titleOrSongHaveChanged) {
		try {
			if (localSong == null) {
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1, SystemClock.elapsedRealtime()).build());
			} else {
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(preparing ? PlaybackState.STATE_BUFFERING : (localPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED), getPosition(), 1, SystemClock.elapsedRealtime()).build());
				if (titleOrSongHaveChanged) {
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, title);
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, localSong.artist);
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, localSong.album);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, localSong.track);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, localSong.lengthMS);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_YEAR, localSong.year);
					mediaSession.setMetadata(mediaSessionMetadataBuilder.build());
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	private static void broadcastStateChange(String title, boolean preparing, boolean titleOrSongHaveChanged) {
		notificationManager.notify(1, getNotification());
		WidgetMain.updateWidgets();
		//
		//perhaps, one day we should implement RemoteControlClient for better Bluetooth support...?
		//http://developer.android.com/reference/android/media/RemoteControlClient.html
		//https://android.googlesource.com/platform/packages/apps/Music/+/master/src/com/android/music/MediaPlaybackService.java
		//
		//http://stackoverflow.com/questions/15527614/send-track-informations-via-a2dp-avrcp
		//http://stackoverflow.com/questions/14536597/how-does-the-android-lockscreen-get-playing-song
		//http://stackoverflow.com/questions/10510292/how-to-get-current-music-track-info
		//
		//https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android-4.3_r0.9.1/src/com/android/bluetooth/a2dp/Avrcp.java
		//
		if (localSong == null) {
			stickyBroadcast.setAction("com.android.music.playbackcomplete");
			stickyBroadcast.removeExtra("id");
			stickyBroadcast.removeExtra("songid");
			stickyBroadcast.removeExtra("track");
			stickyBroadcast.removeExtra("artist");
			stickyBroadcast.removeExtra("album");
			stickyBroadcast.removeExtra("duration");
			//stickyBroadcast.removeExtra("position");
			stickyBroadcast.removeExtra("playing");
		} else {
			//apparently, a few 4.3 devices have an issue with com.android.music.metachanged....
			//stickyBroadcast.setAction(playbackHasChanged ? "com.android.music.playstatechanged" : "com.android.music.metachanged");
			stickyBroadcast.setAction("com.android.music.playstatechanged");
			stickyBroadcast.putExtra("id", localSong.id);
			stickyBroadcast.putExtra("songid", localSong.id);
			stickyBroadcast.putExtra("track", title);
			stickyBroadcast.putExtra("artist", localSong.artist);
			stickyBroadcast.putExtra("album", localSong.album);
			stickyBroadcast.putExtra("duration", (long)localSong.lengthMS);
			//stickyBroadcast.putExtra("position", (long)0);
			stickyBroadcast.putExtra("playing", localPlaying);
		}
		//thePlayer.sendBroadcast(stickyBroadcast);
		//maybe check if api >= 23, and if so, use sendBroadcast instead.....???
		thePlayer.sendStickyBroadcast(stickyBroadcast);
		if (remoteControlClient != null)
			broadcastStateChangeToRemoteControl(title, preparing, titleOrSongHaveChanged);
		if (mediaSession != null)
			broadcastStateChangeToMediaSession(title, preparing, titleOrSongHaveChanged);
	}

	private static void updateState(int arg1, Object[] objs) {
		localPlaying = ((arg1 & 0x04) != 0);
		localPlayerState = (arg1 & 0x03);
		localSong = (Song)objs[0];
		objs[0] = null;
		localPlayer = (MediaPlayerBase)objs[1];
		objs[1] = null;
		if (songs.okToTurnOffAfterReachingTheEnd) {
			songs.okToTurnOffAfterReachingTheEnd = false;
			//turn off if requested
			if (turnOffWhenPlaylistEnds)
				localHandler.sendEmptyMessageAtTime(MSG_TURN_OFF_NOW, SystemClock.uptimeMillis() + 100);
		}
		final boolean songHasChanged = ((arg1 & 0x08) != 0);
		//final boolean playbackHasChanged = ((arg1 & 0x10) != 0);
		final boolean preparing = ((arg1 & 0x20) != 0);
		final boolean preparingHasChanged = ((arg1 & 0x40) != 0);
		final String title = ((localSong == null) ? null : getCurrentTitle(preparing));
		broadcastStateChange(title, preparing, songHasChanged | preparingHasChanged);
		if (idleTurnOffTimerSelectedMinutes > 0)
			processIdleTurnOffTimer();
		if (bluetoothVisualizerController != null)
			updateBluetoothVisualizer(songHasChanged);
		Throwable ex = null;
		if (songHasChanged && announceCurrentSong && UI.accessibilityManager != null && UI.accessibilityManager.isEnabled() && state == STATE_ALIVE)
			UI.announceAccessibilityText(title);
		if (objs[2] != null) {
			ex = (Throwable)objs[2];
			objs[2] = null;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ex instanceof MediaPlayerBase.PermissionDeniedException) && observer != null && (observer instanceof ClientActivity))
				((ClientActivity)observer).getHostActivity().requestReadStoragePermission();
			final String msg = ex.getMessage();
			if (ex instanceof IllegalStateException) {
				UI.toast(R.string.error_state);
			} else if (ex instanceof MediaPlayerBase.UnsupportedFormatException) {
				UI.toast(R.string.error_unsupported_format);
			} else if (ex instanceof OutOfMemoryError) {
				UI.toast(R.string.error_try_smaller_buffer);
			} else if (ex instanceof FileNotFoundException) {
				UI.toast((localSong != null && localSong.isHttp) ?
					(!isConnectedToTheInternet() ? R.string.error_connection : R.string.error_server_not_found) :
						R.string.error_file_not_found);
			} else if (ex instanceof MediaPlayerBase.TimeoutException) {
				UI.toast(R.string.error_timeout);
			} else if (ex instanceof MediaPlayerBase.MediaServerDiedException) {
				UI.toast(R.string.error_server_died);
			} else if (ex instanceof SecurityException) {
				UI.toast(R.string.error_security);
			} else if (ex instanceof IOException) {
				int err = R.string.error_io;
				if (localSong != null) {
					if (localSong.isHttp) {
						err = (!isConnectedToTheInternet() ? R.string.error_connection : R.string.error_io);
					} else {
						try {
							if (!(new File(localSong.path)).exists())
								err = R.string.error_file_not_found;
						} catch (Throwable ex2) {
							err = R.string.error_file_not_found;
						}
					}
				}
				UI.toast(err);
			} else if (msg == null || msg.length() == 0) {
				UI.toast(R.string.error_playback);
			} else {
				final StringBuilder sb = new StringBuilder(thePlayer.getText(R.string.error_msg));
				sb.append(' ');
				sb.append(msg);
				UI.toast(sb);
			}
		}
		if (observer != null)
			observer.onPlayerChanged(localSong, songHasChanged, preparingHasChanged, ex);
	}

	private static void _updateState(boolean metaHasChanged, Throwable ex) {
		if (localHandler != null) {
			final boolean songHasChanged = (metaHasChanged || (stateLastSong != song));
			final boolean playbackHasChanged = (stateLastPlaying != playing);
			final boolean preparing = (playerState == PLAYER_STATE_PREPARING || playerBuffering);
			final boolean preparingHasChanged = (stateLastPreparing != preparing);
			if (!songHasChanged && !playbackHasChanged && !preparingHasChanged && ex == null)
				return;
			stateLastSong = song;
			stateLastPlaying = playing;
			stateLastPreparing = preparing;
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_UPDATE_STATE, playerState | (playing ? 0x04 : 0) | (songHasChanged ? 0x08 : 0) | (playbackHasChanged ? 0x10 : 0) | (preparing ? 0x20 : 0) | (preparingHasChanged ? 0x40 : 0), 0, new Object[] { song, player, ex }), SystemClock.uptimeMillis());
		}
	}
}
