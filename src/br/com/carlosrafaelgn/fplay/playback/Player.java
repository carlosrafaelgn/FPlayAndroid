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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRouter;
import android.media.RemoteControlClient;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
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
public final class Player extends Service implements MainHandler.Callback, Timer.TimerHandler, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener, ArraySorter.Comparer<FileSt> {
	public static interface PlayerObserver {
		public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex);
		public void onPlayerControlModeChanged(boolean controlMode);
		public void onPlayerGlobalVolumeChanged();
		public void onPlayerAudioSinkChanged(int audioSink);
		public void onPlayerMediaButtonPrevious();
		public void onPlayerMediaButtonNext();
	}
	
	public static interface PlayerTurnOffTimerObserver {
		public void onPlayerTurnOffTimerTick();
	}
	
	public static interface PlayerDestroyedObserver {
		public void onPlayerDestroyed();
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
	
	public static final int STATE_NEW = 0;
	public static final int STATE_INITIALIZING = 1;
	public static final int STATE_INITIALIZING_PENDING_LIST = 2;
	public static final int STATE_INITIALIZING_PENDING_ACTIONS = 3;
	public static final int STATE_INITIALIZED = 4;
	public static final int STATE_PREPARING_PLAYBACK = 5;
	public static final int STATE_TERMINATING = 6;
	public static final int STATE_TERMINATED = 7;
	public static final int AUDIO_SINK_DEVICE = 1;
	public static final int AUDIO_SINK_WIRE = 2;
	public static final int AUDIO_SINK_BT = 4;
	public static final int VOLUME_CONTROL_DB = 0;
	public static final int VOLUME_CONTROL_STREAM = 1;
	public static final int VOLUME_CONTROL_NONE = 2;
	public static final String ACTION_PREVIOUS = "br.com.carlosrafaelgn.FPlay.PREVIOUS";
	public static final String ACTION_PLAY_PAUSE = "br.com.carlosrafaelgn.FPlay.PLAY_PAUSE";
	public static final String ACTION_NEXT = "br.com.carlosrafaelgn.FPlay.NEXT";
	public static final String ACTION_EXIT = "br.com.carlosrafaelgn.FPlay.EXIT";
	public static final int MIN_VOLUME_DB = -4000;
	private static final int MSG_UPDATE_STATE = 0x0100;
	private static final int MSG_PLAY_NEXT_AUTO = 0x0101;
	private static final int MSG_INITIALIZATION_0 = 0x102;
	private static final int MSG_INITIALIZATION_1 = 0x103;
	private static final int MSG_INITIALIZATION_2 = 0x104;
	private static final int MSG_INITIALIZATION_3 = 0x105;
	private static final int MSG_INITIALIZATION_4 = 0x106;
	private static final int MSG_INITIALIZATION_5 = 0x107;
	private static final int MSG_INITIALIZATION_6 = 0x108;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_0 = 0x0109;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_1 = 0x010a;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_2 = 0x010b;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_3 = 0x010c;
	private static final int MSG_PREPARE_PLAYBACK_0 = 0x10d;
	private static final int MSG_PREPARE_PLAYBACK_1 = 0x10e;
	private static final int MSG_PREPARE_PLAYBACK_2 = 0x10f;
	private static final int MSG_TERMINATION_0 = 0x110;
	private static final int MSG_TERMINATION_1 = 0x111;
	private static final int MSG_TERMINATION_2 = 0x112;
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
	private static final int OPT_NEXTPREPARATION = 0x000a;
	private static final int OPT_PLAYFOLDERCLEARSLIST = 0x000b;
	private static final int OPT_KEEPSCREENON = 0x000c;
	private static final int OPT_FORCEDORIENTATION = 0x000d;
	private static final int OPT_DISPLAYVOLUMEINDB = 0x000e;
	private static final int OPT_DOUBLECLICKMODE = 0x000f;
	//these will no longer be used!
	//private static final int OPT_MSGADDSHOWN = 0x0010;
	//private static final int OPT_MSGPLAYSHOWN = 0x0011;
	//private static final int OPT_MSGSTARTUPSHOWN = 0x0012;
	private static final int OPT_MARQUEETITLE = 0x0013;
	private static final int OPT_VOLUMECONTROLTYPE = 0x0014;
	private static final int OPT_BLOCKBACKKEY = 0x0015;
	private static final int OPT_TURNOFFTIMERCUSTOMMINUTES = 0x0016;
	private static final int OPT_ISDIVIDERVISIBLE = 0x0017;
	private static final int OPT_ISVERTICALMARGINLARGE = 0x0018;
	private static final int OPT_HANDLECALLKEY = 0x0019;
	private static final int OPT_PLAYWHENHEADSETPLUGGED = 0x001a;
	private static final int OPT_USEALTERNATETYPEFACE = 0x001b;
	private static final int OPT_GOBACKWHENPLAYINGFOLDERS = 0x001c;
	private static final int OPT_RANDOMMODE = 0x001d;
	private static final int OPT_FORCEDLOCALE = 0x001e;
	private static final int OPT_THEME = 0x001f;
	private static final int OPT_MSGS = 0x0020;
	private static final int OPT_MSGSTARTUP = 0x0021;
	private static final int OPT_WIDGETTRANSPARENTBG = 0x0022;
	private static final int OPT_WIDGETTEXTCOLOR = 0x0023;
	private static final int OPT_WIDGETICONCOLOR = 0x0024;
	private static final int OPT_CUSTOMCOLORS = 0x0025;
	private static final int OPT_LASTVERSIONCODE = 0x0026;
	private static final int OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING = 0x0027;
	private static final int OPT_FAVORITEFOLDER0 = 0x10000;
	private static final int SILENCE_NORMAL = 0;
	private static final int SILENCE_FOCUS = 1;
	private static final int SILENCE_NONE = -1;
	private static String startCommand;
	private static boolean lastPlaying, playing, wasPlayingBeforeFocusLoss, currentSongLoaded, playAfterSeek, unpaused, currentSongPreparing, controlMode,
		prepareNextOnSeek, nextPreparing, nextPrepared, nextAlreadySetForPlaying, deserialized, hasFocus, dimmedVolume, reviveAlreadyRetried;
	private static float volume = 1, actualVolume = 1, volumeDBMultiplier;
	private static int volumeDB, lastTime = -1, lastHow, state, silenceMode, globalMaxVolume = 15, turnOffTimerMinutesLeft, turnOffTimerCustomMinutes, audioSink, audioSinkBeforeFocusLoss, volumeControlType;
	private static Player thePlayer;
	private static Song lastSong, currentSong, nextSong, firstError;
	private static MediaPlayer currentPlayer, nextPlayer;
	private static NotificationManager notificationManager;
	private static AudioManager audioManager;
	private static TelephonyManager telephonyManager;
	private static ExternalReceiver externalReceiver;
	private static Timer focusDelayTimer, prepareDelayTimer, volumeTimer, turnOffTimer;
	private static ComponentName mediaButtonEventReceiver;
	private static RemoteControlClient remoteControlClient;
	private static Object mediaRouterCallback;
	//private static BluetoothAdapter bluetoothAdapter;
	//private static BluetoothA2dp bluetoothA2dpProxy;
	private static ArrayList<PlayerDestroyedObserver> destroyedObservers;
	public static PlayerTurnOffTimerObserver turnOffTimerObserver;
	public static PlayerObserver observer;
	public static final SongList songs = SongList.getInstance();
	//keep these instances here to prevent UI, MainHandler, Equalizer and BassBoost
	//classes from being garbage collected...
	public static final UI _ui = new UI();
	public static MainHandler _mainHandler;
	public static final Equalizer _equalizer = new Equalizer();
	public static final BassBoost _bassBoost = new BassBoost();
	//keep these three fields here, instead of in ActivityMain/ActivityBrowser,
	//so they will survive their respective activity's destruction
	//(and even the class garbage collection)
	private static HashSet<String> favoriteFolders;
	public static String path, originalPath;
	public static int lastCurrent = -1, listFirst = -1, listTop = 0, positionToSelect = -1, fadeInIncrementOnFocus, fadeInIncrementOnPause, fadeInIncrementOnOther;
	public static boolean isMainActiveOnTop, alreadySelected, bassBoostMode, nextPreparationEnabled, clearListWhenPlayingFolders, goBackWhenPlayingFolders, handleCallKey, playWhenHeadsetPlugged;
	
	public static SerializableMap loadConfigFromFile(Context context) {
		final SerializableMap opts = SerializableMap.deserialize(context, "_Player");
		return ((opts == null) ? new SerializableMap() : opts);
	}
	
	private static void loadConfig(Context context) {
		final SerializableMap opts = loadConfigFromFile(context);
		setVolumeDB(opts.getInt(OPT_VOLUME));
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
		UI.keepScreenOn = opts.getBoolean(OPT_KEEPSCREENON, true);
		UI.forcedOrientation = opts.getInt(OPT_FORCEDORIENTATION);
		UI.displayVolumeInDB = opts.getBoolean(OPT_DISPLAYVOLUMEINDB);
		UI.doubleClickMode = opts.getBoolean(OPT_DOUBLECLICKMODE);
		UI.marqueeTitle = opts.getBoolean(OPT_MARQUEETITLE, true);
		setVolumeControlType(context, opts.getInt(OPT_VOLUMECONTROLTYPE, VOLUME_CONTROL_STREAM));
		UI.blockBackKey = opts.getBoolean(OPT_BLOCKBACKKEY, false);
		turnOffTimerCustomMinutes = opts.getInt(OPT_TURNOFFTIMERCUSTOMMINUTES, 30);
		if (turnOffTimerCustomMinutes < 1)
			turnOffTimerCustomMinutes = 1;
		UI.isDividerVisible = opts.getBoolean(OPT_ISDIVIDERVISIBLE, true);
		UI.isVerticalMarginLarge = opts.getBoolean(OPT_ISVERTICALMARGINLARGE, UI.isLargeScreen || !UI.isLowDpiScreen);
		handleCallKey = opts.getBoolean(OPT_HANDLECALLKEY, true);
		playWhenHeadsetPlugged = opts.getBoolean(OPT_PLAYWHENHEADSETPLUGGED, true);
		UI.setUsingAlternateTypefaceAndForcedLocale(context, opts.getBoolean(OPT_USEALTERNATETYPEFACE, false), opts.getInt(OPT_FORCEDLOCALE, UI.LOCALE_NONE));
		UI.customColors = opts.getBuffer(OPT_CUSTOMCOLORS, null);
		UI.setTheme(opts.getInt(OPT_THEME, UI.THEME_DARK_LIGHT));
		goBackWhenPlayingFolders = opts.getBoolean(OPT_GOBACKWHENPLAYINGFOLDERS, false);
		songs.setRandomMode(opts.getBoolean(OPT_RANDOMMODE, false));
		UI.msgs = opts.getInt(OPT_MSGS, 0);
		UI.msgStartup = opts.getInt(OPT_MSGSTARTUP, 0);
		UI.widgetTransparentBg = opts.getBoolean(OPT_WIDGETTRANSPARENTBG, false);
		UI.widgetTextColor = opts.getInt(OPT_WIDGETTEXTCOLOR, 0xff000000);
		UI.widgetIconColor = opts.getInt(OPT_WIDGETICONCOLOR, 0xff000000);
		UI.lastVersionCode = opts.getInt(OPT_LASTVERSIONCODE, 0);
		UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = opts.getBoolean(OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING, false);
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
	
	private static void saveConfig(Context context) {
		final SerializableMap opts = new SerializableMap(32);
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
		opts.put(OPT_KEEPSCREENON, UI.keepScreenOn);
		opts.put(OPT_FORCEDORIENTATION, UI.forcedOrientation);
		opts.put(OPT_DISPLAYVOLUMEINDB, UI.displayVolumeInDB);
		opts.put(OPT_DOUBLECLICKMODE, UI.doubleClickMode);
		opts.put(OPT_MARQUEETITLE, UI.marqueeTitle);
		opts.put(OPT_VOLUMECONTROLTYPE, volumeControlType);
		opts.put(OPT_BLOCKBACKKEY, UI.blockBackKey);
		opts.put(OPT_TURNOFFTIMERCUSTOMMINUTES, turnOffTimerCustomMinutes);
		opts.put(OPT_ISDIVIDERVISIBLE, UI.isDividerVisible);
		opts.put(OPT_ISVERTICALMARGINLARGE, UI.isVerticalMarginLarge);
		opts.put(OPT_HANDLECALLKEY, handleCallKey);
		opts.put(OPT_PLAYWHENHEADSETPLUGGED, playWhenHeadsetPlugged);
		opts.put(OPT_USEALTERNATETYPEFACE, UI.isUsingAlternateTypeface());
		opts.put(OPT_CUSTOMCOLORS, UI.customColors);
		opts.put(OPT_THEME, UI.getTheme());
		opts.put(OPT_GOBACKWHENPLAYINGFOLDERS, goBackWhenPlayingFolders);
		opts.put(OPT_RANDOMMODE, songs.isInRandomMode());
		opts.put(OPT_FORCEDLOCALE, UI.getForcedLocale());
		opts.put(OPT_MSGS, UI.msgs);
		opts.put(OPT_MSGSTARTUP, UI.msgStartup);
		opts.put(OPT_WIDGETTRANSPARENTBG, UI.widgetTransparentBg);
		opts.put(OPT_WIDGETTEXTCOLOR, UI.widgetTextColor);
		opts.put(OPT_WIDGETICONCOLOR, UI.widgetIconColor);
		opts.put(OPT_LASTVERSIONCODE, UI.VERSION_CODE);
		opts.put(OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING, UI.backKeyAlwaysReturnsToPlayerWhenBrowsing);
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
	}
	
	/*@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void registerA2dpObserver(Context context) {
		bluetoothAdapter = null;
		bluetoothA2dpProxy = null;
		try {
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		} catch (Throwable ex) {
		}
		if (bluetoothAdapter == null)
			return;
		try {
			bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
				@Override
				public void onServiceDisconnected(int profile) {
				}
				@Override
				public void onServiceConnected(int profile, BluetoothProfile proxy) {
					if (profile != BluetoothProfile.A2DP)
						return;
					if (bluetoothA2dpProxy != null) {
						try {
							bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dpProxy);
						} catch (Throwable ex) {
						}
						bluetoothA2dpProxy = null;
					}
					bluetoothA2dpProxy = (BluetoothA2dp)proxy;
				}
			}, BluetoothProfile.A2DP);
		} catch (Throwable ex) {
		}
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static boolean isA2dpPlaying() {
		if (bluetoothA2dpProxy == null)
			return false;
		try {
			final List<BluetoothDevice> l = bluetoothA2dpProxy.getConnectedDevices();
			for (int i = l.size() - 1; i >= 0; i--) {
				if (bluetoothA2dpProxy.isA2dpPlaying(l.get(i)))
					return true;
			}
		} catch (Throwable ex) {
		}
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void unregisterA2dpObserver(Context context) {
		try {
			if (bluetoothAdapter != null && bluetoothA2dpProxy != null)
				bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dpProxy);
		} catch (Throwable ex) {
		}
		bluetoothAdapter = null;
		bluetoothA2dpProxy = null;
	}*/
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void registerMediaRouter(Context context) {
		final MediaRouter mr = (MediaRouter)context.getSystemService(MEDIA_ROUTER_SERVICE);
		if (mr != null) {
			mediaRouterCallback = new MediaRouter.Callback() {
				@Override
				public void onRouteVolumeChanged(MediaRouter router, RouteInfo info) {
				}
				@Override
				public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
				}
				@Override
				public void onRouteUngrouped(MediaRouter router, RouteInfo info, RouteGroup group) {
				}
				@Override
				public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
				}
				@Override
				public void onRouteRemoved(MediaRouter router, RouteInfo info) {
				}
				@Override
				public void onRouteGrouped(MediaRouter router, RouteInfo info, RouteGroup group, int index) {
				}
				@Override
				public void onRouteChanged(MediaRouter router, RouteInfo info) {
					if (info.getPlaybackStream() == AudioManager.STREAM_MUSIC) {
						//this actually works... nonetheless, I was not able to detected
						//which is the audio sink used by this route.... :(
						globalMaxVolume = info.getVolumeMax();
						audioSinkChanged(false);
					}
				}
				@Override
				public void onRouteAdded(MediaRouter router, RouteInfo info) {
				}
			};
			mr.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_USER, (MediaRouter.Callback)mediaRouterCallback);
		}
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void unregisterMediaRouter(Context context) {
		final MediaRouter mr = (MediaRouter)context.getSystemService(MEDIA_ROUTER_SERVICE);
		if (mediaRouterCallback != null && mr != null)
			mr.removeCallback((MediaRouter.Callback)mediaRouterCallback);
		mediaRouterCallback = null;
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void registerRemoteControlClient() {
		try {
			if (remoteControlClient == null) {
				final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
				mediaButtonIntent.setComponent(mediaButtonEventReceiver);
				final PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(thePlayer, 0, mediaButtonIntent, 0);
				remoteControlClient = new RemoteControlClient(mediaPendingIntent);
				remoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS | RemoteControlClient.FLAG_KEY_MEDIA_NEXT | RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_STOP);
			}
			audioManager.registerRemoteControlClient(remoteControlClient);
		} catch (Throwable ex) {
		}
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void unregisterRemoteControlClient() {
		try {
			audioManager.unregisterRemoteControlClient(remoteControlClient);
		} catch (Throwable ex) {
		}
	}
	
	public static void registerMediaButtonEventReceiver() {
		if (mediaButtonEventReceiver == null)
			mediaButtonEventReceiver = new ComponentName("br.com.carlosrafaelgn.fplay", "br.com.carlosrafaelgn.fplay.ExternalReceiver");
		if (audioManager != null) {
			audioManager.registerMediaButtonEventReceiver(mediaButtonEventReceiver);
			if (thePlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				registerRemoteControlClient();
		}
	}
	
	public static void unregisterMediaButtonEventReceiver() {
		if (mediaButtonEventReceiver != null && audioManager != null) {
			if (remoteControlClient != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				unregisterRemoteControlClient();
			audioManager.unregisterMediaButtonEventReceiver(mediaButtonEventReceiver);
		}
	}
	
	private static void initialize(Context context) {
		if (state == STATE_NEW) {
			state = STATE_INITIALIZING;
			_mainHandler = MainHandler.initialize(context);
			if (notificationManager == null)
				notificationManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
			if (audioManager == null)
				audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);
			if (telephonyManager == null)
				telephonyManager = (TelephonyManager)context.getSystemService(TELEPHONY_SERVICE);
			if (destroyedObservers == null)
				destroyedObservers = new ArrayList<Player.PlayerDestroyedObserver>(4);
			loadConfig(context);
		}
		if (thePlayer != null) {
			registerMediaButtonEventReceiver();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				registerMediaRouter(thePlayer);
			//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			//	registerA2dpObserver(thePlayer);
			checkAudioSink(false, false);
			if (focusDelayTimer == null)
				focusDelayTimer = new Timer(thePlayer, "Player Focus Delay Timer", true, true, false);
			if (prepareDelayTimer == null)
				prepareDelayTimer = new Timer(thePlayer, "Player Prepare Delay Timer", true, true, false);
			if (volumeTimer == null)
				volumeTimer = new Timer(thePlayer, "Player Volume Timer", false, true, true);
			if (turnOffTimer == null)
				turnOffTimer = new Timer(thePlayer, "Turn Off Timer", true, true, false);
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_0);
		}
	}
	
	private static void terminate() {
		if ((state == STATE_INITIALIZED || state == STATE_PREPARING_PLAYBACK) && !songs.isAdding()) {
			state = STATE_TERMINATING;
			setLastTime();
			fullCleanup(null);
			MainHandler.sendMessage(thePlayer, MSG_TERMINATION_0);
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
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void broadcastStateChangeToRemoteControl(boolean songHasChanged) {
		try {
			if (currentSong == null) {
				remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			} else {
				remoteControlClient.setPlaybackState(playing ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED);
				if (songHasChanged) {
					final RemoteControlClient.MetadataEditor ed = remoteControlClient.editMetadata(true);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, currentSong.title);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, currentSong.artist);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, currentSong.album);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, currentSong.track);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, currentSong.lengthMS);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_YEAR, currentSong.year);
					ed.apply();
				}
			}
		} catch (Throwable ex) {
		}
	}
	
	private static void broadcastStateChange(boolean playbackHasChanged, boolean songHasChanged) {
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
		if (currentSong == null) {
			thePlayer.sendStickyBroadcast(new Intent("com.android.music.playbackcomplete"));
		} else {
			//apparently, a few 4.3 devices have an issue with com.android.music.metachanged....
			final Intent i = new Intent(playbackHasChanged ? "com.android.music.playstatechanged" : "com.android.music.metachanged");
			//final Intent i = new Intent("com.android.music.playstatechanged");
			i.putExtra("id", currentSong.id);
			i.putExtra("songid", currentSong.id);
			i.putExtra("track", currentSong.title);
			i.putExtra("artist", currentSong.artist);
			i.putExtra("album", currentSong.album);
			i.putExtra("duration", (long)currentSong.lengthMS);
			i.putExtra("position", (long)0);
			i.putExtra("playing", playing);
			//thePlayer.sendBroadcast(i);
			thePlayer.sendStickyBroadcast(i);
		}
		if (remoteControlClient != null)
			broadcastStateChangeToRemoteControl(songHasChanged);
	}
	
	public static RemoteViews prepareRemoteViews(Context context, RemoteViews views, boolean prepareButtons, boolean notification) {
		if (currentSongPreparing)
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.loading));
		else if (currentSong == null)
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.nothing_playing));
		else
			views.setTextViewText(R.id.lblTitle, currentSong.title);
		
		if (prepareButtons) {
			Intent intent;
			
			if (notification) {
				UI.prepareNotificationPlaybackIcons(context);
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrevNotif);
				views.setImageViewBitmap(R.id.btnPlay, playing ? UI.icPauseNotif : UI.icPlayNotif);
				views.setImageViewBitmap(R.id.btnNext, UI.icNextNotif);
				views.setImageViewBitmap(R.id.btnExit, UI.icExitNotif);
				
				intent = new Intent(context, Player.class);
				intent.setAction(Player.ACTION_EXIT);
				views.setOnClickPendingIntent(R.id.btnExit, PendingIntent.getService(context, 0, intent, 0));
			} else {
				if (currentSongPreparing || currentSong == null)
					views.setTextViewText(R.id.lblArtist, "-");
				else
					views.setTextViewText(R.id.lblArtist, currentSong.artist);
				
				views.setTextColor(R.id.lblTitle, UI.widgetTextColor);
				views.setTextColor(R.id.lblArtist, UI.widgetTextColor);
				
				final PendingIntent p = getPendingIntent(context);
				views.setOnClickPendingIntent(R.id.lblTitle, p);
				views.setOnClickPendingIntent(R.id.lblArtist, p);
				
				UI.prepareWidgetPlaybackIcons(context);
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrev);
				views.setImageViewBitmap(R.id.btnPlay, playing ? UI.icPause : UI.icPlay);
				views.setImageViewBitmap(R.id.btnNext, UI.icNext);
			}
			
			intent = new Intent(context, Player.class);
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
	
	public static void onPlayerChanged(Song newCurrentSong, Throwable ex) {
		if (thePlayer != null) {
			if (newCurrentSong != null)
				currentSong = newCurrentSong;
			final boolean songHasChanged = (lastSong != currentSong);
			final boolean playbackHasChanged = (lastPlaying != playing);
			if (!songHasChanged && !playbackHasChanged && ex == null)
				return;
			lastSong = currentSong;
			lastPlaying = playing;
			notificationManager.notify(1, getNotification());
			WidgetMain.updateWidgets(thePlayer);
			broadcastStateChange(playbackHasChanged, songHasChanged);
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
				observer.onPlayerChanged(currentSong, songHasChanged, ex);
		}
	}
	
	public static void onSongListDeserialized(Song newCurrentSong, int forcePlayIdx, int positionToSelect, Throwable ex) {
		if (positionToSelect >= 0)
			setSelectionAfterAdding(positionToSelect);
		onPlayerChanged(newCurrentSong, ex);
		switch (state) {
		case STATE_INITIALIZING_PENDING_LIST:
			state = STATE_INITIALIZED;
			break;
		case STATE_INITIALIZING:
			state = STATE_INITIALIZING_PENDING_ACTIONS;
			break;
		}
		executeStartCommand(forcePlayIdx);
	}
	
	private static void updateState(Throwable ex) {
		//if (MainHandler.isOnMainThread())
		//	onPlayerChanged(null, ex);
		//else
		MainHandler.sendMessage(thePlayer, MSG_UPDATE_STATE, ex);
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
		if (silenceMode != SILENCE_NONE && volumeTimer != null) {
			final int incr = (unpaused ? fadeInIncrementOnPause : ((silenceMode == SILENCE_FOCUS) ? fadeInIncrementOnFocus : fadeInIncrementOnOther));
			if (incr > 30) {
				actualVolume = 0;
				volumeDBMultiplier = MIN_VOLUME_DB;
				volumeTimer.start(50, incr);
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
			updateState(ex);
		} else {
			if (firstError == null)
				firstError = failedSong;
			updateState(null);
			MainHandler.sendMessage(thePlayer, MSG_PLAY_NEXT_AUTO);
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
	
	private static void playInternal0(int how, Song song) {
		if (!hasFocus) {
			if (state == STATE_PREPARING_PLAYBACK)
				state = STATE_INITIALIZED;
			return;
		}
		int prepareNext = 0;
		if (nextSong == song && how != SongList.HOW_CURRENT) {
			int doInternal = 1;
			lastTime = -1;
			if (nextPrepared) {
				if (how != SongList.HOW_NEXT_AUTO)
					stopPlayer(currentPlayer);
				nextPrepared = false;
				try {
					if (!nextAlreadySetForPlaying || how != SongList.HOW_NEXT_AUTO)
						startPlayer(nextPlayer);
					else
						nextPlayer.setVolume(actualVolume, actualVolume);
					nextAlreadySetForPlaying = false;
					currentSongLoaded = true;
					firstError = null;
					if (how == SongList.HOW_NEXT_AUTO)
						stopPlayer(currentPlayer);
					prepareNext = 2;
					doInternal = 0;
					currentSongPreparing = false;
				} catch (Throwable ex) {
					song.possibleNextSong = null;
					fullCleanup(song);
					nextFailed(song, how, ex);
					return;
				}
			} else {
				stopPlayer(currentPlayer);
				if (nextPreparing) {
					lastHow = how;
					currentSongLoaded = false;
					currentSongPreparing = true;
					doInternal = 0;
				}
			}
			currentSong = song;
			final MediaPlayer p = currentPlayer;
			currentPlayer = nextPlayer;
			nextPlayer = p;
			MainHandler.sendMessageAtFrontOfQueue(thePlayer, MSG_PREPARE_PLAYBACK_1, how, doInternal | prepareNext, song);
		} else {
			stopPlayer(currentPlayer);
			MainHandler.sendMessageAtFrontOfQueue(thePlayer, MSG_PREPARE_PLAYBACK_1, how, 1 | prepareNext, song);
		}
	}
	
	private static void playInternal1(boolean doInternal, boolean prepareNext, int how, Song song) {
		if (!hasFocus) {
			if (state == STATE_PREPARING_PLAYBACK)
				state = STATE_INITIALIZED;
			return;
		}
		if (doInternal) {
			if (!doPlayInternal(how, song))
				return;
			MainHandler.sendMessageAtFrontOfQueue(thePlayer, MSG_PREPARE_PLAYBACK_2, prepareNext ? 1 : 0, 0, song);
		} else {
			playInternal2(prepareNext, song);
		}
	}
	
	private static void playInternal2(boolean prepareNext, Song song) {
		if (state == STATE_PREPARING_PLAYBACK)
			state = STATE_INITIALIZED;
		if (!hasFocus)
			return;
		nextSong = song.possibleNextSong;
		song.possibleNextSong = null;
		if (nextSong == currentSong)
			nextSong = null;
		else if (prepareNext)
			processPreparation();
		playing = currentSongLoaded;
		//wasPlaying = true;
		updateState(null);
	}
	
	private static void playInternal(int how) {
		if (!hasFocus && !requestFocus()) {
			unpaused = false;
			playing = false;
			updateState(new FocusException());
			return;
		}
		//we must set this to false here, as the user could have manually
		//started playback before the focus timer had the chance to trigger
		wasPlayingBeforeFocusLoss = false;
		final Song s = songs.getSongAndSetCurrent(how);
		if (s == null) {
			lastTime = -1;
			firstError = null;
			fullCleanup(null);
			updateState(null);
			return;
		}
		if (currentPlayer == null || nextPlayer == null) {
			initializePlayers();
			state = STATE_PREPARING_PLAYBACK;
			MainHandler.sendMessage(thePlayer, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_0, how, 0, s);
		} else {
			state = STATE_PREPARING_PLAYBACK;
			playInternal0(how, s);
		}
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
		if (state == STATE_PREPARING_PLAYBACK)
			state = STATE_INITIALIZED;
		wasPlayingBeforeFocusLoss = false;
		unpaused = false;
		silenceMode = SILENCE_NORMAL;
		stopInternal(newCurrentSong);
		abandonFocus();
	}
	
	@SuppressWarnings("deprecation")
	private static void checkAudioSink(boolean wiredHeadsetJustPlugged, boolean triggerNoisy) {
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
		}
		try {
			//apparently, devices tend to use the wired headset over bluetooth headsets
			if (audioSink == 0 && (wiredHeadsetJustPlugged || audioManager.isWiredHeadsetOn()))
				audioSink = AUDIO_SINK_WIRE;
		} catch (Throwable ex) {
		}
		try {
			//this whole A2dp thing is still not enough, as isA2dpPlaying()
			//will return false if there is nothing playing, even in scenarios
			//A2dp will certainly be used for playback later...
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
		}
		if (audioSink == 0)
			audioSink = AUDIO_SINK_DEVICE;
		if (oldAudioSink != audioSink && oldAudioSink != 0) {
			switch (audioSink) {
			case AUDIO_SINK_WIRE:
				if (!playing && playWhenHeadsetPlugged) {
					if (!hasFocus) {
						try {
							if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
								break;
						} catch (Throwable ex) {
						}
					}
					playPause();
				}
				break;
			case AUDIO_SINK_DEVICE:
				if (triggerNoisy)
					becomingNoisyInternal();
				break;
			}
		}
		//I am calling the observer even if no changes have been detected, because
		//I myself don't trust this code will correctly work as expected on every device....
		if (observer != null)
			observer.onPlayerAudioSinkChanged(audioSink);
	}
	
	private static void becomingNoisyInternal() {
		//this cleanup must be done, as sometimes, when changing between two output types,
		//the effects are lost...
		if (playing)
			playPause();
		stopInternal(currentSong);
		releaseInternal();
	}
	
	public static void becomingNoisy() {
		becomingNoisyInternal();
		checkAudioSink(false, false);
	}
	
	public static void audioSinkChanged(boolean wiredHeadsetJustPlugged) {
		checkAudioSink(wiredHeadsetJustPlugged, true);
	}
	
	public static boolean previous() {
		if (thePlayer == null || state != STATE_INITIALIZED)
			return false;
		playInternal(SongList.HOW_PREVIOUS);
		return true;
	}
	
	public static boolean playPause() {
		if (thePlayer == null || state != STATE_INITIALIZED)
			return false;
		if (currentSong == null || !currentSongLoaded || !hasFocus) {
			unpaused = true;
			playInternal(SongList.HOW_CURRENT);
		} else {
			try {
				//we must set this to false here, as the user could have manually
				//started playback before the focus timer had the chance to trigger
				wasPlayingBeforeFocusLoss = false;
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
				updateState(ex);
				return false;
			}
			updateState(null);
		}
		return true;
	}
	
	public static boolean play(int index) {
		if (thePlayer == null || state != STATE_INITIALIZED)
			return false;
		playInternal(index);
		return true;
	}
	
	public static boolean next() {
		if (thePlayer == null || state != STATE_INITIALIZED)
			return false;
		playInternal(SongList.HOW_NEXT_MANUAL);
		return true;
	}
	
	public static boolean seekTo(int timeMS, boolean play) {
		if (thePlayer == null || state != STATE_INITIALIZED)
			return false;
		if (!currentSongLoaded) {
			lastTime = timeMS;
		} else if (currentPlayer != null) {
			lastTime = timeMS;
			playAfterSeek = play;
			currentSongPreparing = true;
			currentPlayer.seekTo(timeMS);
			updateState(null);
		}
		return true;
	}
	
	public static void listCleared() {
		fullCleanup(null);
		lastTime = -1;
		updateState(null);
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
	
	public static boolean setVolumeDB(int volumeDB) {
		final boolean ret;
		if (volumeDB <= MIN_VOLUME_DB) {
			volume = 0;
			Player.volumeDB = MIN_VOLUME_DB;
			ret = false;
		} else if (volumeDB >= 0) {
			volume = 1;
			Player.volumeDB = 0;
			ret = false;
		} else {
			//magnitude = 10 ^ (dB/20)
			//x^p = a ^ (p * log a (x))
			//10^p = e ^ (p * log e (10))
			volume = (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0);
			Player.volumeDB = volumeDB;
			ret = true;
		}
		if (volumeTimer != null)
			volumeTimer.stop();
		//when dimmed, decreased the volume by 20dB
		actualVolume = (dimmedVolume ? (volume * 0.1f) : volume);
		try {
			if (currentPlayer != null && currentSongLoaded)
				currentPlayer.setVolume(actualVolume, actualVolume);
		} catch (Throwable ex) {
		}
		return ret;
	}
	
	public static int getStreamMaxVolume() {
		return globalMaxVolume;
	}
	
	public static void showStreamVolumeUI() {
		try {
			if (audioManager != null)
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
		} catch (Throwable ex) {
		}
	}
	
	public static boolean decreaseStreamVolume() {
		try {
			if (audioManager != null) {
				final int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volume <= 1) ? 0 : (volume - 1)), 0);
				return (volume > 1);
			}
		} catch (Throwable ex) {
		}
		return false;
	}
	
	public static boolean increaseStreamVolume() {
		try {
			if (audioManager != null) {
				final int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volume >= globalMaxVolume) ? globalMaxVolume : (volume + 1)), 0);
				return (volume < (globalMaxVolume- 1));
			}
		} catch (Throwable ex) {
		}
		return false;
	}
	
	public static int getStreamVolume() {
		try {
			if (audioManager != null)
				return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		} catch (Throwable ex) {
		}
		return 0;
	}
	
	public static void setStreamVolume(int volume) {
		try {
			if (audioManager != null) {
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volume < 0) ? 0 : ((volume > globalMaxVolume) ? globalMaxVolume : volume)), 0);
			}
		} catch (Throwable ex) {
		}
	}
	
	public static int getVolumeControlType() {
		return volumeControlType;
	}
	
	public static void setVolumeControlType(Context context, int volumeControlType) {
		switch (volumeControlType) {
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_NONE:
			Player.volumeControlType = volumeControlType;
			break;
		default:
			try {
				if (audioManager != null)
					globalMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
				setVolumeDB(0);
				Player.volumeControlType = VOLUME_CONTROL_STREAM;
			} catch (Throwable ex) {
			}
			break;
		}
	}
	
	public static void addDestroyedObserver(PlayerDestroyedObserver observer) {
		if (destroyedObservers != null && !destroyedObservers.contains(observer))
			destroyedObservers.add(observer);
	}
	
	public static void removeDestroyedObserver(PlayerDestroyedObserver observer) {
		if (destroyedObservers != null)
			destroyedObservers.remove(observer);
	}
	
	public static void setTurnOffTimer(int minutes, boolean saveCustom) {
		if (turnOffTimer != null) {
			turnOffTimerMinutesLeft = 0;
			turnOffTimer.stop();
			if (minutes > 0) {
				if (saveCustom)
					turnOffTimerCustomMinutes = minutes;
				turnOffTimerMinutesLeft = minutes;
				turnOffTimer.start(60000);
			}
		}
	}
	
	public static int getTurnOffTimerMinutesLeft() {
		return turnOffTimerMinutesLeft;
	}
	
	public static int getTurnOffTimerCustomMinutes() {
		return turnOffTimerCustomMinutes;
	}
	
	public static int getState() {
		return state;
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
		return (currentSongPreparing || (state == STATE_PREPARING_PLAYBACK));
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
		if (thePlayer == null && state == STATE_NEW) {
			initialize(context);
			context.startService(new Intent(context, Player.class));
		}
	}
	
	public static void stopService() {
		//if the service is requested to stop, even before it gets a chance to fully initialize
		//itself, then schedule its termination as a startCommand
		startCommand = ACTION_EXIT;
		terminate();
	}
	
	public static Service getService() {
		return thePlayer;
	}
	
	@Override
	public void onCreate() {
		//StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
		//	.detectAll()
		//	.penaltyLog()
		//	.build());
		//StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
		//	.detectAll()
		//	.penaltyLog()
		//	.build());
		thePlayer = this;
		initialize(this);
		startForeground(1, getNotification());
		super.onCreate();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}
	
	private static void executeStartCommand(int forcePlayIdx) {
		if (forcePlayIdx >= 0) {
			play(forcePlayIdx);
			startCommand = null;
		} else if (startCommand != null && state == STATE_INITIALIZED) {
			if (startCommand.equals(ACTION_PREVIOUS))
				previous();
			else if (startCommand.equals(ACTION_PLAY_PAUSE))
				playPause();
			else if (startCommand.equals(ACTION_NEXT))
				next();
			else if (startCommand.equals(ACTION_EXIT))
				stopService();
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
		//There are a few weird bluetooth headsets that despite having only one physical
		//play/pause button, will try to simulate individual PLAY and PAUSE events,
		//instead of sending the proper event PLAY_PAUSE... The problem is, they are not
		//always synchronized with the actual player state, and therefore, the user ends
		//up having to press that button twice for something to happen! :(
		case KeyEvent.KEYCODE_MEDIA_PLAY:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_HEADSETHOOK:
			playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_STOP:
			if (playing)
				playPause();
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
				decreaseStreamVolume();
				if (observer != null)
					observer.onPlayerGlobalVolumeChanged();
				break;
			}
			return false;
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (volumeControlType == VOLUME_CONTROL_STREAM) {
				increaseStreamVolume();
				if (observer != null)
					observer.onPlayerGlobalVolumeChanged();
				break;
			}
			return false;
		default:
			return false;
		}
		return true;
	}
	
	private static void processFocusGain() {
		//this method is called only when the player has recovered the focus,
		//and in this scenario, currentPlayer will be null
		//someone else may have changed our values if the engine is shared
		registerMediaButtonEventReceiver();
		if (wasPlayingBeforeFocusLoss) {
			//do not restart playback in scenarios like this (it really scares people!):
			//the person has answered a call, removed the headset, ended the call without
			//the headset plugged in, and then the focus came back to us
			if (audioSinkBeforeFocusLoss != AUDIO_SINK_DEVICE && audioSink == AUDIO_SINK_DEVICE) {
				wasPlayingBeforeFocusLoss = false;
				requestFocus();
			} else if (state == STATE_INITIALIZED && !playing) {
				playInternal(SongList.HOW_CURRENT);
			} else {
				wasPlayingBeforeFocusLoss = false;
			}
		}
	}
	
	private static void processPreparation() {
		if (prepareDelayTimer != null && currentSong != null && nextSong != null && !nextSong.isHttp && nextPreparationEnabled && currentSong.lengthMS > 10000 && nextSong.lengthMS > 10000)
			prepareDelayTimer.start(5000, nextSong);
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
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_UPDATE_STATE:
			onPlayerChanged(null, (Throwable)msg.obj);
			break;
		case MSG_PLAY_NEXT_AUTO:
			playInternal(SongList.HOW_NEXT_AUTO);
			break;
		case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_0:
			if (!hasFocus) {
				if (state == STATE_PREPARING_PLAYBACK)
					state = STATE_INITIALIZED;
				break;
			}
			Equalizer.release();
			MainHandler.sendMessage(thePlayer, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_1, msg.arg1, msg.arg2, msg.obj);
			break;
		case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_1:
			if (!hasFocus) {
				if (state == STATE_PREPARING_PLAYBACK)
					state = STATE_INITIALIZED;
				break;
			}
			BassBoost.release();
			MainHandler.sendMessage(thePlayer, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_2, msg.arg1, msg.arg2, msg.obj);
			break;
		case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_2:
			if (!hasFocus) {
				if (state == STATE_PREPARING_PLAYBACK)
					state = STATE_INITIALIZED;
				break;
			}
			try {
				if (Equalizer.isEnabled() && currentPlayer != null) {
					Equalizer.initialize(currentPlayer.getAudioSessionId());
					Equalizer.setEnabled(true);
				}
			} catch (Throwable ex) {
			}
			MainHandler.sendMessage(thePlayer, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_3, msg.arg1, msg.arg2, msg.obj);
			break;
		case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_3:
			if (!hasFocus) {
				if (state == STATE_PREPARING_PLAYBACK)
					state = STATE_INITIALIZED;
				break;
			}
			try {
				if (BassBoost.isEnabled() && currentPlayer != null) {
					BassBoost.initialize(currentPlayer.getAudioSessionId());
					BassBoost.setEnabled(true);
				}
			} catch (Throwable ex) {
			}
			MainHandler.sendMessage(thePlayer, MSG_PREPARE_PLAYBACK_0, msg.arg1, msg.arg2, msg.obj);
			break;
		case MSG_PREPARE_PLAYBACK_0:
			playInternal0(msg.arg1, (Song)msg.obj);
			break;
		case MSG_PREPARE_PLAYBACK_1:
			playInternal1((msg.arg2 & 1) != 0, (msg.arg2 & 2) != 0, msg.arg1, (Song)msg.obj);
			break;
		case MSG_PREPARE_PLAYBACK_2:
			playInternal2(msg.arg1 != 0, (Song)msg.obj);
			break;
		case MSG_INITIALIZATION_0:
			if (externalReceiver == null) {
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
			}
			if (!deserialized) {
				deserialized = true;
				songs.startDeserializing(thePlayer, null, true, false, false);
			}
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_1);
			break;
		case MSG_INITIALIZATION_1:
			initializePlayers();
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_2);
			break;
		case MSG_INITIALIZATION_2:
			if (currentPlayer != null)
				Equalizer.initialize(currentPlayer.getAudioSessionId());
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_3);
			break;
		case MSG_INITIALIZATION_3:
			if (!Equalizer.isEnabled())
				Equalizer.release();
			else
				Equalizer.setEnabled(true);
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_4);
			break;
		case MSG_INITIALIZATION_4:
			if (currentPlayer != null)
				BassBoost.initialize(currentPlayer.getAudioSessionId());
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_5);
			break;
		case MSG_INITIALIZATION_5:
			if (!BassBoost.isEnabled())
				BassBoost.release();
			else
				BassBoost.setEnabled(true);
			MainHandler.sendMessage(thePlayer, MSG_INITIALIZATION_6);
			break;
		case MSG_INITIALIZATION_6:
			switch (state) {
			case STATE_INITIALIZING_PENDING_ACTIONS:
				state = STATE_INITIALIZED;
				break;
			case STATE_INITIALIZING:
				state = STATE_INITIALIZING_PENDING_LIST;
				break;
			}
			executeStartCommand(-1);
			break;
		case MSG_TERMINATION_0:
			releaseInternal();
			MainHandler.sendMessage(thePlayer, MSG_TERMINATION_1);
			break;
		case MSG_TERMINATION_1:
			onPlayerChanged(null, null); //to update the widget
			unregisterMediaButtonEventReceiver();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && thePlayer != null)
				unregisterMediaRouter(thePlayer);
			//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && thePlayer != null)
			//	unregisterA2dpObserver(thePlayer);
			MainHandler.sendMessage(thePlayer, MSG_TERMINATION_2);
			break;
		case MSG_TERMINATION_2:
			if (destroyedObservers != null) {
				for (int i = destroyedObservers.size() - 1; i >= 0; i--)
					destroyedObservers.get(i).onPlayerDestroyed();
				destroyedObservers.clear();
				destroyedObservers = null;
			}
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
			if (turnOffTimer != null) {
				turnOffTimer.stop();
				turnOffTimer = null;
			}
			if (thePlayer != null) {
				if (externalReceiver != null) {
					thePlayer.getApplicationContext().unregisterReceiver(externalReceiver);
					externalReceiver = null;
				}
				saveConfig(thePlayer);
			}
			observer = null;
			turnOffTimerObserver = null;
			notificationManager = null;
			audioManager = null;
			telephonyManager = null;
			externalReceiver = null;
			mediaButtonEventReceiver = null;
			remoteControlClient = null;
			if (favoriteFolders != null) {
				favoriteFolders.clear();
				favoriteFolders = null;
			}
			state = STATE_TERMINATED;
			if (thePlayer != null) {
				thePlayer.stopForeground(true);
				thePlayer.stopSelf();
				thePlayer = null;
			} else {
				System.exit(0);
			}
			break;
		}
		return true;
	}
	
	@Override
	public void handleTimer(Timer timer, Object param) {
		if (state != STATE_INITIALIZED && state != STATE_PREPARING_PLAYBACK)
			return;
		if (timer == focusDelayTimer) {
			if (thePlayer != null && hasFocus)
				processFocusGain();
		} else if (timer == prepareDelayTimer) {
			if (thePlayer != null && hasFocus && nextSong == param) {
				nextPreparing = false;
				nextPrepared = false;
				if (nextPlayer != null && nextSong != null && !nextSong.isHttp) {
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
			volumeDBMultiplier += ((param == null) ? 125 : ((Integer)param).intValue());
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
		} else if (timer == turnOffTimer) {
			if (turnOffTimerMinutesLeft > 0) {
				turnOffTimerMinutesLeft--;
				if (turnOffTimerObserver != null)
					turnOffTimerObserver.onPlayerTurnOffTimerTick();
				if (turnOffTimerMinutesLeft <= 0)
					stopService();
				else
					turnOffTimer.start(60000);
			}
		}
	}
	
	@Override
	public void onAudioFocusChange(int focusChange) {
		if (state != STATE_INITIALIZED && state != STATE_PREPARING_PLAYBACK)
			return;
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
				if (focusDelayTimer != null)
					focusDelayTimer.start(1500);
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
			if (state == STATE_PREPARING_PLAYBACK)
				state = STATE_INITIALIZED;
			//we just cannot replace wasPlayingBeforeFocusLoss's value with
			//playing, because if a second focus loss occurred BEFORE focusDelayTimer
			//had a chance to trigger, then playing would be false, when in fact,
			//we want wasPlayingBeforeFocusLoss to remain true (and the audio sink
			//must be untouched)
			if (playing) {
				wasPlayingBeforeFocusLoss = true;
				audioSinkBeforeFocusLoss = audioSink;
			}
			hasFocus = false;
			setLastTime();
			stopInternal(currentSong);
			silenceMode = SILENCE_FOCUS;
			releaseInternal();
			updateState(null);
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
		if (state != STATE_INITIALIZED && state != STATE_PREPARING_PLAYBACK)
			return true;
		if (mp == nextPlayer || mp == currentPlayer) {
			if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
				setLastTime();
				fullCleanup(currentSong);
				releaseInternal();
				if (reviveAlreadyRetried) {
					reviveAlreadyRetried = false;
					updateState(new MediaServerDiedException());
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
				updateState((extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) ? new TimeoutException() : new IOException());
			}
		} else {
			fullCleanup(currentSong);
			releaseInternal();
			updateState(new Exception("Invalid MediaPlayer"));
		}
		return true;
	}
	
	@Override
	public void onPrepared(MediaPlayer mp) {
		if (state != STATE_INITIALIZED && state != STATE_PREPARING_PLAYBACK)
			return;
		if (mp == nextPlayer) {
			nextPreparing = false;
			nextPrepared = true;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				setNextPlayer();
		} else if (mp == currentPlayer) {
			try {
				if (!hasFocus) {
					fullCleanup(currentSong);
					updateState(null);
					return;
				}
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
				updateState(null);
				if (!prepareNextOnSeek)
					processPreparation();
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				nextFailed(currentSong, lastHow, ex);
			}
		} else {
			fullCleanup(currentSong);
			updateState(new Exception("Invalid MediaPlayer"));
		}
	}
	
	@Override
	public void onSeekComplete(MediaPlayer mp) {
		if (state != STATE_INITIALIZED && state != STATE_PREPARING_PLAYBACK)
			return;
		if (mp == currentPlayer) {
			try {
				currentSongPreparing = false;
				if (playAfterSeek) {
					startPlayer(mp);
					playing = true;
					reviveAlreadyRetried = false;
					//wasPlaying = true;
				}
				updateState(null);
				if (prepareNextOnSeek) {
					prepareNextOnSeek = false;
					processPreparation();
				}
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				updateState(ex);
			}
		}
	}
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		if (state != STATE_INITIALIZED && state != STATE_PREPARING_PLAYBACK)
			return;
		if (playing)
			playInternal(SongList.HOW_NEXT_AUTO);
	}
}
