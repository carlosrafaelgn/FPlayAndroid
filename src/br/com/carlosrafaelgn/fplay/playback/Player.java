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
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteInfo;
import android.media.RemoteControlClient;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import br.com.carlosrafaelgn.fplay.ExternalReceiver;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.WidgetMain;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.list.SongList;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
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
public final class Player extends Service implements Timer.TimerHandler, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener, AudioManager.OnAudioFocusChangeListener, ArraySorter.Comparer<FileSt> {
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
		public void onPlayerIdleTurnOffTimerTick();
	}
	
	public static interface PlayerDestroyedObserver {
		public void onPlayerDestroyed();
	}
	
	private static final class TimeoutException extends Exception {
		private static final long serialVersionUID = 4571328670214281144L;
	}
	
	private static final class MediaServerDiedException extends Exception {
		private static final long serialVersionUID = -902099312236606175L;
	}
	
	private static final class FocusException extends Exception {
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
	private static final int MSG_UPDATE_META = 0x0102;
	private static final int MSG_INITIALIZATION_0 = 0x103;
	private static final int MSG_INITIALIZATION_1 = 0x104;
	private static final int MSG_INITIALIZATION_2 = 0x105;
	private static final int MSG_INITIALIZATION_3 = 0x106;
	private static final int MSG_INITIALIZATION_4 = 0x107;
	private static final int MSG_INITIALIZATION_5 = 0x108;
	private static final int MSG_INITIALIZATION_6 = 0x109;
	private static final int MSG_INITIALIZATION_7 = 0x10a;
	private static final int MSG_INITIALIZATION_8 = 0x10b;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_0 = 0x0110;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_1 = 0x0111;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_2 = 0x0112;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_3 = 0x0113;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_4 = 0x0114;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_5 = 0x0115;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_6 = 0x0116;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_7 = 0x0117;
	private static final int MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_8 = 0x0118;
	private static final int MSG_PREPARE_PLAYBACK_0 = 0x120;
	private static final int MSG_PREPARE_PLAYBACK_1 = 0x121;
	private static final int MSG_PREPARE_PLAYBACK_2 = 0x122;
	private static final int MSG_RESET_EFFECTS_0 = 0x130;
	private static final int MSG_RESET_EFFECTS_1 = 0x131;
	private static final int MSG_RESET_EFFECTS_2 = 0x132;
	private static final int MSG_RESET_EFFECTS_3 = 0x133;
	private static final int MSG_RESET_EFFECTS_4 = 0x134;
	private static final int MSG_RESET_EFFECTS_5 = 0x135;
	private static final int MSG_RESET_EFFECTS_6 = 0x136;
	private static final int MSG_RESET_EFFECTS_7 = 0x137;
	private static final int MSG_RESET_EFFECTS_8 = 0x138;
	private static final int MSG_TERMINATION_0 = 0x140;
	private static final int MSG_TERMINATION_1 = 0x141;
	private static final int MSG_TERMINATION_2 = 0x142;
	
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
	private static final int OPT_WRAPAROUNDLIST = 0x0028;
	private static final int OPT_EXTRASPACING = 0x0029;
	//private static final int OPT_OLDBROWSERBEHAVIOR = 0x002A;
	private static final int OPT_VISUALIZERORIENTATION = 0x002B;
	private static final int OPT_SONGEXTRAINFOMODE = 0x002C;
	private static final int OPT_TURNOFFTIMERSELECTEDMINUTES = 0x002D;
	private static final int OPT_IDLETURNOFFTIMERCUSTOMMINUTES = 0x002E;
	private static final int OPT_IDLETURNOFFTIMERSELECTEDMINUTES = 0x002F;
	private static final int OPT_FLAT = 0x0030;
	private static final int OPT_ALBUMART = 0x0031;
	private static final int OPT_RADIOSEARCHTERM = 0x0032;
	private static final int OPT_RADIOLASTGENRE = 0x0033;
	private static final int OPT_TRANSITION = 0x0034;
	
	//values 0x01xx are shared among all effects
	static final int OPT_EQUALIZER_ENABLED = 0x0100;
	static final int OPT_EQUALIZER_PRESET = 0x0101;
	static final int OPT_EQUALIZER_LEVELCOUNT = 0x0102;
	static final int OPT_EQUALIZER_LEVEL0 = 0x20000;
	static final int OPT_BASSBOOST_ENABLED = 0x0110;
	static final int OPT_BASSBOOST_STRENGTH = 0x0111;
	static final int OPT_VIRTUALIZER_ENABLED = 0x0112;
	static final int OPT_VIRTUALIZER_STRENGTH = 0x0113;
	
	private static final int OPTBIT_CONTROLMODE = 0;
	private static final int OPTBIT_BASSBOOSTMODE = 1;
	private static final int OPTBIT_NEXTPREPARATION = 2;
	private static final int OPTBIT_PLAYFOLDERCLEARSLIST = 3;
	private static final int OPTBIT_KEEPSCREENON = 4;
	private static final int OPTBIT_DISPLAYVOLUMEINDB = 5;
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
	//private static final int OPTBIT_OLDBROWSERBEHAVIOR = 22;
	static final int OPTBIT_EQUALIZER_ENABLED = 23;
	static final int OPTBIT_BASSBOOST_ENABLED = 24;
	static final int OPTBIT_VIRTUALIZER_ENABLED = 25;
	private static final int OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES = 26;
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
	
	private static final int OPT_FAVORITEFOLDER0 = 0x10000;
	
	private static final int SILENCE_NORMAL = 0;
	private static final int SILENCE_FOCUS = 1;
	private static final int SILENCE_NONE = -1;
	private static String startCommand;
	private static boolean lastPlaying, playing, wasPlayingBeforeFocusLoss, currentSongLoaded, playAfterSeek, unpaused, lastPreparing, currentSongPreparing, currentSongBuffering, controlMode,
		prepareNextOnSeek, nextPreparing, nextPrepared, nextAlreadySetForPlaying, deserialized, hasFocus, dimmedVolume, reviveAlreadyRetried, appIdle;
	private static float volume = 1, actualVolume = 1, volumeDBMultiplier;
	private static long turnOffTimerOrigin, idleTurnOffTimerOrigin, headsetHookLastTime;
	private static int volumeDB, lastTime = -1, lastHow, state, silenceMode, globalMaxVolume = 15, turnOffTimerCustomMinutes, turnOffTimerSelectedMinutes, idleTurnOffTimerCustomMinutes, idleTurnOffTimerSelectedMinutes, audioSink, audioSinkBeforeFocusLoss, volumeControlType;
	private static Player thePlayer;
	private static Song lastSong, currentSong, nextSong, firstError;
	private static MediaPlayer currentPlayer, nextPlayer;
	private static NotificationManager notificationManager;
	private static AudioManager audioManager;
	private static TelephonyManager telephonyManager;
	private static ExternalReceiver externalReceiver;
	private static Timer focusDelayTimer, prepareDelayTimer, volumeTimer, headsetHookTimer, turnOffTimer, idleTurnOffTimer;
	private static ComponentName mediaButtonEventReceiver;
	private static RemoteControlClient remoteControlClient;
	private static MediaSession mediaSession;
	private static MediaMetadata.Builder mediaSessionMetadataBuilder;
	private static PlaybackState.Builder mediaSessionPlaybackStateBuilder;
	private static Object mediaRouterCallback;
	private static Intent stickyBroadcast;
	private static Runnable effectsObserver;
	//private static BluetoothAdapter bluetoothAdapter;
	//private static BluetoothA2dp bluetoothA2dpProxy;
	private static ArrayList<PlayerDestroyedObserver> destroyedObservers;
	public static PlayerTurnOffTimerObserver turnOffTimerObserver;
	public static PlayerObserver observer;
	public static final SongList songs = SongList.getInstance();
	//keep these instances here to prevent UI, MainHandler, Equalizer, BassBoost,
	//Virtualizer and PresetReverb classes from being garbage collected...
	public static final UI _ui = new UI();
	public static MainHandler _mainHandler;
	public static PlayerHandler _playerHandler;
	public static final Equalizer _equalizer = new Equalizer();
	public static final BassBoost _bassBoost = new BassBoost();
	public static final Virtualizer _virtualizer = new Virtualizer();
	//public static final PresetReverb _presetReverb = new PresetReverb();
	//keep these three fields here, instead of in ActivityMain/ActivityBrowser,
	//so they will survive their respective activity's destruction
	//(and even the class garbage collection)
	private static HashSet<String> favoriteFolders;
	public static String path, originalPath, radioSearchTerm;
	public static int lastCurrent = -1, listFirst = -1, listTop = 0, positionToSelect = -1, radioLastGenre, fadeInIncrementOnFocus, fadeInIncrementOnPause, fadeInIncrementOnOther;
	public static boolean isMainActiveOnTop, alreadySelected, bassBoostMode, nextPreparationEnabled, clearListWhenPlayingFolders, goBackWhenPlayingFolders, handleCallKey, playWhenHeadsetPlugged, headsetHookDoublePressPauses, doNotAttenuateVolume, lastRadioSearchWasByGenre;
	
	public static SerializableMap loadConfigFromFile(Context context) {
		final SerializableMap opts = SerializableMap.deserialize(context, "_Player");
		return ((opts == null) ? new SerializableMap() : opts);
	}
	
	private static void loadConfig(Context context) {
		final SerializableMap opts = loadConfigFromFile(context);
		UI.lastVersionCode = opts.getInt(OPT_LASTVERSIONCODE, 0);
		setVolumeDB(opts.getInt(OPT_VOLUME));
		path = opts.getString(OPT_PATH);
		originalPath = opts.getString(OPT_ORIGINALPATH);
		lastTime = opts.getInt(OPT_LASTTIME, -1);
		fadeInIncrementOnFocus = opts.getInt(OPT_FADEININCREMENTONFOCUS, 125);
		fadeInIncrementOnPause = opts.getInt(OPT_FADEININCREMENTONPAUSE, 125);
		fadeInIncrementOnOther = opts.getInt(OPT_FADEININCREMENTONOTHER, 0);
		UI.forcedOrientation = opts.getInt(OPT_FORCEDORIENTATION);
		setVolumeControlType(context, opts.getInt(OPT_VOLUMECONTROLTYPE, VOLUME_CONTROL_STREAM));
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
		UI.customColors = opts.getBuffer(OPT_CUSTOMCOLORS, null);
		UI.setTheme(null, opts.getInt(OPT_THEME, UI.THEME_CREAMY));
		UI.msgs = opts.getInt(OPT_MSGS, 0);
		UI.msgStartup = opts.getInt(OPT_MSGSTARTUP, 0);
		UI.widgetTextColor = opts.getInt(OPT_WIDGETTEXTCOLOR, 0xff000000);
		UI.widgetIconColor = opts.getInt(OPT_WIDGETICONCOLOR, 0xff000000);
		UI.visualizerOrientation = opts.getInt(OPT_VISUALIZERORIENTATION, 0);
		Song.extraInfoMode = opts.getInt(OPT_SONGEXTRAINFOMODE, Song.EXTRA_ARTIST);
		radioSearchTerm = opts.getString(OPT_RADIOSEARCHTERM);
		radioLastGenre = opts.getInt(OPT_RADIOLASTGENRE, 21);
		UI.setTransition(opts.getInt(OPT_TRANSITION, UI.TRANSITION_FADE));
		//the concept of bit was added on version 38
		if (opts.hasBits() || UI.lastVersionCode == 0) {
			//load the bit flags the new way
			controlMode = opts.getBit(OPTBIT_CONTROLMODE);
			bassBoostMode = opts.getBit(OPTBIT_BASSBOOSTMODE);
			nextPreparationEnabled = opts.getBit(OPTBIT_NEXTPREPARATION, true);
			clearListWhenPlayingFolders = opts.getBit(OPTBIT_PLAYFOLDERCLEARSLIST);
			UI.keepScreenOn = opts.getBit(OPTBIT_KEEPSCREENON, true);
			UI.displayVolumeInDB = opts.getBit(OPTBIT_DISPLAYVOLUMEINDB);
			UI.doubleClickMode = opts.getBit(OPTBIT_DOUBLECLICKMODE);
			UI.marqueeTitle = opts.getBit(OPTBIT_MARQUEETITLE, true);
			UI.setFlat(opts.getBit(OPTBIT_FLAT, true));
			UI.albumArt = opts.getBit(OPTBIT_ALBUMART, true);
			UI.blockBackKey = opts.getBit(OPTBIT_BLOCKBACKKEY);
			UI.isDividerVisible = opts.getBit(OPTBIT_ISDIVIDERVISIBLE, true);
			UI.isVerticalMarginLarge = opts.getBit(OPTBIT_ISVERTICALMARGINLARGE, UI.isLargeScreen || !UI.isLowDpiScreen);
			handleCallKey = opts.getBit(OPTBIT_HANDLECALLKEY, true);
			playWhenHeadsetPlugged = opts.getBit(OPTBIT_PLAYWHENHEADSETPLUGGED, true);
			UI.setUsingAlternateTypefaceAndForcedLocale(context, opts.getBit(OPTBIT_USEALTERNATETYPEFACE), opts.getInt(OPT_FORCEDLOCALE, UI.LOCALE_NONE));
			goBackWhenPlayingFolders = opts.getBit(OPTBIT_GOBACKWHENPLAYINGFOLDERS);
			songs.setRandomMode(opts.getBit(OPTBIT_RANDOMMODE));
			UI.widgetTransparentBg = opts.getBit(OPTBIT_WIDGETTRANSPARENTBG);
			UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = opts.getBit(OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING);
			UI.wrapAroundList = opts.getBit(OPTBIT_WRAPAROUNDLIST);
			UI.extraSpacing = opts.getBit(OPTBIT_EXTRASPACING, (UI.screenWidth >= UI.dpToPxI(600)) || (UI.screenHeight >= UI.dpToPxI(600)));
			//UI.oldBrowserBehavior = opts.getBit(OPTBIT_OLDBROWSERBEHAVIOR);
			//new settings (cannot be loaded the old way)
			headsetHookDoublePressPauses = opts.getBit(OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES);
			doNotAttenuateVolume = opts.getBit(OPTBIT_DO_NOT_ATTENUATE_VOLUME);
			UI.scrollBarToTheLeft = opts.getBit(OPTBIT_SCROLLBAR_TO_THE_LEFT);
			UI.songListScrollBarType = (opts.getBitI(OPTBIT_SCROLLBAR_SONGLIST1, 0) << 1) | opts.getBitI(OPTBIT_SCROLLBAR_SONGLIST0, 1);
			if (UI.songListScrollBarType == BgListView.SCROLLBAR_INDEXED)
				UI.songListScrollBarType = BgListView.SCROLLBAR_LARGE;
			UI.browserScrollBarType = (opts.getBitI(OPTBIT_SCROLLBAR_BROWSER1, 1) << 1) | opts.getBitI(OPTBIT_SCROLLBAR_BROWSER0, 0);
			lastRadioSearchWasByGenre = opts.getBit(OPTBIT_LASTRADIOSEARCHWASBYGENRE, true);
			UI.expandSeekBar = opts.getBit(OPTBIT_EXPANDSEEKBAR, true);
			songs.setRepeatingOne(opts.getBit(OPTBIT_REPEATONE));
			UI.notFullscreen = opts.getBit(OPTBIT_NOTFULLSCREEN);
		} else {
			//load bit flags the old way
			controlMode = opts.getBoolean(OPT_CONTROLMODE);
			bassBoostMode = opts.getBoolean(OPT_BASSBOOSTMODE);
			nextPreparationEnabled = opts.getBoolean(OPT_NEXTPREPARATION, true);
			clearListWhenPlayingFolders = opts.getBoolean(OPT_PLAYFOLDERCLEARSLIST);
			UI.keepScreenOn = opts.getBoolean(OPT_KEEPSCREENON, true);
			UI.displayVolumeInDB = opts.getBoolean(OPT_DISPLAYVOLUMEINDB);
			UI.doubleClickMode = opts.getBoolean(OPT_DOUBLECLICKMODE);
			UI.marqueeTitle = opts.getBoolean(OPT_MARQUEETITLE, true);
			UI.setFlat(opts.getBoolean(OPT_FLAT, true));
			UI.albumArt = opts.getBoolean(OPT_ALBUMART, true);
			UI.blockBackKey = opts.getBoolean(OPT_BLOCKBACKKEY);
			UI.isDividerVisible = opts.getBoolean(OPT_ISDIVIDERVISIBLE, true);
			UI.isVerticalMarginLarge = opts.getBoolean(OPT_ISVERTICALMARGINLARGE, UI.isLargeScreen || !UI.isLowDpiScreen);
			handleCallKey = opts.getBoolean(OPT_HANDLECALLKEY, true);
			playWhenHeadsetPlugged = opts.getBoolean(OPT_PLAYWHENHEADSETPLUGGED, true);
			UI.setUsingAlternateTypefaceAndForcedLocale(context, opts.getBoolean(OPT_USEALTERNATETYPEFACE), opts.getInt(OPT_FORCEDLOCALE, UI.LOCALE_NONE));
			goBackWhenPlayingFolders = opts.getBoolean(OPT_GOBACKWHENPLAYINGFOLDERS);
			songs.setRandomMode(opts.getBoolean(OPT_RANDOMMODE));
			UI.widgetTransparentBg = opts.getBoolean(OPT_WIDGETTRANSPARENTBG);
			UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = opts.getBoolean(OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING);
			UI.wrapAroundList = opts.getBoolean(OPT_WRAPAROUNDLIST);
			UI.extraSpacing = opts.getBoolean(OPT_EXTRASPACING, (UI.screenWidth >= UI.dpToPxI(600)) || (UI.screenHeight >= UI.dpToPxI(600)));
			//UI.oldBrowserBehavior = opts.getBoolean(OPT_OLDBROWSERBEHAVIOR);
		}
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
		Virtualizer.loadConfig(opts);
		//PresetReverb.loadConfig(opts);
		if (favoriteFolders == null)
			favoriteFolders = new HashSet<String>(8);
	}
	
	public static void saveConfig(Context context, boolean saveSongs) {
		final SerializableMap opts = new SerializableMap(96);
		opts.put(OPT_LASTVERSIONCODE, UI.VERSION_CODE);
		opts.put(OPT_VOLUME, volumeDB);
		opts.put(OPT_PATH, path);
		opts.put(OPT_ORIGINALPATH, originalPath);
		opts.put(OPT_LASTTIME, lastTime);
		opts.put(OPT_FADEININCREMENTONFOCUS, fadeInIncrementOnFocus);
		opts.put(OPT_FADEININCREMENTONPAUSE, fadeInIncrementOnPause);
		opts.put(OPT_FADEININCREMENTONOTHER, fadeInIncrementOnOther);
		opts.put(OPT_FORCEDORIENTATION, UI.forcedOrientation);
		opts.put(OPT_VOLUMECONTROLTYPE, volumeControlType);
		opts.put(OPT_TURNOFFTIMERCUSTOMMINUTES, turnOffTimerCustomMinutes);
		opts.put(OPT_TURNOFFTIMERSELECTEDMINUTES, turnOffTimerSelectedMinutes);
		opts.put(OPT_IDLETURNOFFTIMERCUSTOMMINUTES, idleTurnOffTimerCustomMinutes);
		opts.put(OPT_IDLETURNOFFTIMERSELECTEDMINUTES, idleTurnOffTimerSelectedMinutes);
		opts.put(OPT_CUSTOMCOLORS, UI.customColors);
		opts.put(OPT_THEME, UI.getTheme());
		opts.put(OPT_FORCEDLOCALE, UI.getForcedLocale());
		opts.put(OPT_MSGS, UI.msgs);
		opts.put(OPT_MSGSTARTUP, UI.msgStartup);
		opts.put(OPT_WIDGETTEXTCOLOR, UI.widgetTextColor);
		opts.put(OPT_WIDGETICONCOLOR, UI.widgetIconColor);
		opts.put(OPT_VISUALIZERORIENTATION, UI.visualizerOrientation);
		opts.put(OPT_SONGEXTRAINFOMODE, Song.extraInfoMode);
		opts.put(OPT_RADIOSEARCHTERM, radioSearchTerm);
		opts.put(OPT_RADIOLASTGENRE, radioLastGenre);
		opts.put(OPT_TRANSITION, UI.getTransition());
		opts.putBit(OPTBIT_CONTROLMODE, controlMode);
		opts.putBit(OPTBIT_BASSBOOSTMODE, bassBoostMode);
		opts.putBit(OPTBIT_NEXTPREPARATION, nextPreparationEnabled);
		opts.putBit(OPTBIT_PLAYFOLDERCLEARSLIST, clearListWhenPlayingFolders);
		opts.putBit(OPTBIT_KEEPSCREENON, UI.keepScreenOn);
		opts.putBit(OPTBIT_DISPLAYVOLUMEINDB, UI.displayVolumeInDB);
		opts.putBit(OPTBIT_DOUBLECLICKMODE, UI.doubleClickMode);
		opts.putBit(OPTBIT_MARQUEETITLE, UI.marqueeTitle);
		opts.putBit(OPTBIT_FLAT, UI.isFlat());
		opts.putBit(OPTBIT_ALBUMART, UI.albumArt);
		opts.putBit(OPTBIT_BLOCKBACKKEY, UI.blockBackKey);
		opts.putBit(OPTBIT_ISDIVIDERVISIBLE, UI.isDividerVisible);
		opts.putBit(OPTBIT_ISVERTICALMARGINLARGE, UI.isVerticalMarginLarge);
		opts.putBit(OPTBIT_HANDLECALLKEY, handleCallKey);
		opts.putBit(OPTBIT_PLAYWHENHEADSETPLUGGED, playWhenHeadsetPlugged);
		opts.putBit(OPTBIT_USEALTERNATETYPEFACE, UI.isUsingAlternateTypeface());
		opts.putBit(OPTBIT_GOBACKWHENPLAYINGFOLDERS, goBackWhenPlayingFolders);
		opts.putBit(OPTBIT_RANDOMMODE, songs.isInRandomMode());
		opts.putBit(OPTBIT_WIDGETTRANSPARENTBG, UI.widgetTransparentBg);
		opts.putBit(OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING, UI.backKeyAlwaysReturnsToPlayerWhenBrowsing);
		opts.putBit(OPTBIT_WRAPAROUNDLIST, UI.wrapAroundList);
		opts.putBit(OPTBIT_EXTRASPACING, UI.extraSpacing);
		//opts.putBit(OPTBIT_OLDBROWSERBEHAVIOR, UI.oldBrowserBehavior);
		opts.putBit(OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES, headsetHookDoublePressPauses);
		opts.putBit(OPTBIT_DO_NOT_ATTENUATE_VOLUME, doNotAttenuateVolume);
		opts.putBit(OPTBIT_SCROLLBAR_TO_THE_LEFT, UI.scrollBarToTheLeft);
		opts.putBit(OPTBIT_SCROLLBAR_SONGLIST0, (UI.songListScrollBarType & 1) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_SONGLIST1, (UI.songListScrollBarType & 2) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_BROWSER0, (UI.browserScrollBarType & 1) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_BROWSER1, (UI.browserScrollBarType & 2) != 0);
		opts.putBit(OPTBIT_LASTRADIOSEARCHWASBYGENRE, lastRadioSearchWasByGenre);
		opts.putBit(OPTBIT_EXPANDSEEKBAR, UI.expandSeekBar);
		opts.putBit(OPTBIT_REPEATONE, songs.isRepeatingOne());
		opts.putBit(OPTBIT_NOTFULLSCREEN, UI.notFullscreen);
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
		//PresetReverb.saveConfig(opts);
		opts.serialize(context, "_Player");
		if (saveSongs)
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

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private static void registerRemoteControlClientCallbacks() {
		remoteControlClient.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener() {
			@Override
			public long onGetPlaybackPosition() {
				return getCurrentPosition();
			}
		});
		remoteControlClient.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener() {
			@Override
			public void onPlaybackPositionUpdate(long pos) {
				Player.seekTo((int)pos, (currentPlayer != null) && currentSongLoaded && playing);
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private static void unregisterRemoteControlClientCallbacks() {
		remoteControlClient.setOnGetPlaybackPositionListener(null);
		remoteControlClient.setPlaybackPositionUpdateListener(null);
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
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
					registerRemoteControlClientCallbacks();
			}
			audioManager.registerRemoteControlClient(remoteControlClient);
		} catch (Throwable ex) {
		}
	}
	
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
					public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
						if (mediaButtonIntent != null) {
							final Object o = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
							if (o == null || !(o instanceof KeyEvent))
								return false;
							final KeyEvent e = (KeyEvent)o;
							if (e.getAction() == KeyEvent.ACTION_DOWN)
								handleMediaButton(e.getKeyCode());
						}
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
						Player.seekTo((int)pos, (currentPlayer != null) && currentSongLoaded && playing);
					}
				});
				mediaSession.setSessionActivity(getPendingIntent(thePlayer));
				mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1, SystemClock.elapsedRealtime()).build());
			}
			mediaSession.setActive(true);
		} catch (Throwable ex) {
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
			mediaSessionPlaybackStateBuilder = null;
		} catch (Throwable ex) {
		}
	}

	public static void registerMediaButtonEventReceiver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			registerMediaSession();
		} else {
			if (mediaButtonEventReceiver == null)
				mediaButtonEventReceiver = new ComponentName("br.com.carlosrafaelgn.fplay", "br.com.carlosrafaelgn.fplay.ExternalReceiver");
			if (audioManager != null) {
				audioManager.registerMediaButtonEventReceiver(mediaButtonEventReceiver);
				if (thePlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					registerRemoteControlClient();
			}
		}
	}
	
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
	
	private static void initialize(Context context) {
		if (state == STATE_NEW) {
			state = STATE_INITIALIZING;
			_mainHandler = MainHandler.initialize();
			if (notificationManager == null)
				notificationManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
			if (audioManager == null)
				audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);
			if (telephonyManager == null)
				telephonyManager = (TelephonyManager)context.getSystemService(TELEPHONY_SERVICE);
			if (destroyedObservers == null)
				destroyedObservers = new ArrayList<Player.PlayerDestroyedObserver>(4);
			if (stickyBroadcast == null)
				stickyBroadcast = new Intent();
			loadConfig(context);
		}
		if (thePlayer != null) {
			if (_playerHandler == null)
				_playerHandler = new PlayerHandler(context);
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
			if (headsetHookTimer == null)
				headsetHookTimer = new Timer(thePlayer, "Headset Hook Timer", true, true, false);
			sendMessage(MSG_INITIALIZATION_0);
		}
	}
	
	private static void terminate() {
		if ((state == STATE_INITIALIZED || state == STATE_PREPARING_PLAYBACK) && !songs.isAdding()) {
			state = STATE_TERMINATING;
			setLastTime();
			fullCleanup(null);
			sendMessage(MSG_TERMINATION_0);
		}
	}
	
	public static boolean isConnectedToTheInternet() {
		if (thePlayer != null) {
			try {
				final ConnectivityManager mngr = (ConnectivityManager)thePlayer.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo info = mngr.getActiveNetworkInfo();
				return (info != null && info.isConnected());
			} catch (Throwable ex) {
			}
		}
		return false;
	}
	
	public static boolean isInternetConnectedViaWiFi() {
		if (thePlayer != null) {
			try {
				final ConnectivityManager mngr = (ConnectivityManager)thePlayer.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo infoMob = mngr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
				final NetworkInfo info = mngr.getActiveNetworkInfo();
				return (infoMob != null && info != null && !infoMob.isConnected() && info.isConnected());
			} catch (Throwable ex) {
			}
		}
		return false;
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
				ffs[i] = new FileSt(f, (idx >= 0 && idx < (f.length() - 1)) ? f.substring(idx + 1) : f, null, FileSt.TYPE_FAVORITE);
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
	
	/*public static void applyReverbToPlayers() {
		PresetReverb.applyToPlayer(currentPlayer);
		PesetReverb.applyToPlayer(currentPlayer);
	}
	
	public static void releaseReverbFromPlayers() {
		/*if (currentPlayer != null) {
			try {
				currentPlayer.attachAuxEffect(0);
			} catch (Throwable ex) {
			}
		}
		if (nextPlayer != null) {
			try {
				nextPlayer.attachAuxEffect(0);
			} catch (Throwable ex) {
			}
		}
	}*/
	
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
			if (currentSong != null && currentSong.isHttp)
				lastTime = -1;
			else if (currentPlayer != null && currentSongLoaded && playing)
				lastTime = currentPlayer.getCurrentPosition();
		} catch (Throwable ex) {
		}
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void broadcastStateChangeToRemoteControl(boolean preparing, boolean titleOrSongHaveChanged) {
		try {
			if (currentSong == null) {
				remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			} else {
				remoteControlClient.setPlaybackState(preparing ? RemoteControlClient.PLAYSTATE_BUFFERING : (playing ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED));
				if (titleOrSongHaveChanged) {
					final RemoteControlClient.MetadataEditor ed = remoteControlClient.editMetadata(true);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, preparing ? thePlayer.getText(R.string.loading).toString() : currentSong.title);
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

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void broadcastStateChangeToMediaSession(boolean preparing, boolean titleOrSongHaveChanged) {
		try {
			if (currentSong == null) {
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1, SystemClock.elapsedRealtime()).build());
			} else {
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(preparing ? PlaybackState.STATE_BUFFERING : (playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED), getCurrentPosition(), 1, SystemClock.elapsedRealtime()).build());
				if (titleOrSongHaveChanged) {
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, preparing ? thePlayer.getText(R.string.loading).toString() : currentSong.title);
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, currentSong.artist);
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, currentSong.album);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, currentSong.track);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, currentSong.lengthMS);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_YEAR, currentSong.year);
					mediaSession.setMetadata(mediaSessionMetadataBuilder.build());
				}
			}
		} catch (Throwable ex) {
		}
	}

	private static void broadcastStateChange(boolean playbackHasChanged, boolean preparing, boolean titleOrSongHaveChanged) {
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
			stickyBroadcast.setAction("com.android.music.playbackcomplete");
			stickyBroadcast.removeExtra("id");
			stickyBroadcast.removeExtra("songid");
			stickyBroadcast.removeExtra("track");
			stickyBroadcast.removeExtra("artist");
			stickyBroadcast.removeExtra("album");
			stickyBroadcast.removeExtra("duration");
			stickyBroadcast.removeExtra("position");
			stickyBroadcast.removeExtra("playing");
		} else {
			//apparently, a few 4.3 devices have an issue with com.android.music.metachanged....
			stickyBroadcast.setAction(playbackHasChanged ? "com.android.music.playstatechanged" : "com.android.music.metachanged");
			//stickyBroadcast.setAction("com.android.music.playstatechanged");
			stickyBroadcast.putExtra("id", currentSong.id);
			stickyBroadcast.putExtra("songid", currentSong.id);
			stickyBroadcast.putExtra("track", preparing ? thePlayer.getText(R.string.loading) : currentSong.title);
			stickyBroadcast.putExtra("artist", currentSong.artist);
			stickyBroadcast.putExtra("album", currentSong.album);
			stickyBroadcast.putExtra("duration", (long)currentSong.lengthMS);
			stickyBroadcast.putExtra("position", (long)0);
			stickyBroadcast.putExtra("playing", playing);
		}
		//thePlayer.sendBroadcast(stickyBroadcast);
		thePlayer.sendStickyBroadcast(stickyBroadcast);
		if (remoteControlClient != null)
			broadcastStateChangeToRemoteControl(preparing, titleOrSongHaveChanged);
		if (mediaSession != null)
			broadcastStateChangeToMediaSession(preparing, titleOrSongHaveChanged);
	}
	
	public static RemoteViews prepareRemoteViews(Context context, RemoteViews views, boolean prepareButtons, boolean notification) {
		if (currentSong == null)
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.nothing_playing));
		else if (isCurrentSongPreparing())
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.loading));
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
				if (currentSong == null)
					views.setTextViewText(R.id.lblArtist, "-");
				else
					views.setTextViewText(R.id.lblArtist, currentSong.extraInfo);
				
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
	
	private static PendingIntent getPendingIntent(Context context) {
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
	
	private static void onPlayerChanged(Song newCurrentSong, boolean metaHasChanged, Throwable ex) {
		if (thePlayer != null) {
			if (newCurrentSong != null)
				currentSong = newCurrentSong;
			final boolean songHasChanged = (metaHasChanged || (lastSong != currentSong));
			final boolean playbackHasChanged = (lastPlaying != playing);
			final boolean preparing = isCurrentSongPreparing();
			final boolean preparingHasChanged = (lastPreparing != preparing);
			if (!songHasChanged && !playbackHasChanged && !preparingHasChanged && ex == null)
				return;
			if (idleTurnOffTimer != null && idleTurnOffTimerSelectedMinutes > 0)
				processIdleTurnOffTimer();
			lastSong = currentSong;
			lastPlaying = playing;
			lastPreparing = preparing;
			notificationManager.notify(1, getNotification());
			WidgetMain.updateWidgets(thePlayer);
			broadcastStateChange(playbackHasChanged, preparing, songHasChanged | preparingHasChanged);
			if (ex != null) {
				final String msg = ex.getMessage();
				if (ex instanceof IllegalStateException) {
					UI.toast(thePlayer, R.string.error_state);
				} else if (ex instanceof IOException) {
					UI.toast(thePlayer, (currentSong != null && currentSong.isHttp && !isConnectedToTheInternet()) ? R.string.error_connection : R.string.error_io);
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
	
	private static void performFinalInitializationTasks() {
		state = STATE_INITIALIZED;
		setTurnOffTimer(turnOffTimerSelectedMinutes);
		setIdleTurnOffTimer(idleTurnOffTimerSelectedMinutes);
	}
	
	public static void onSongListDeserialized(Song newCurrentSong, int forcePlayIdx, int positionToSelect, Throwable ex) {
		if (positionToSelect >= 0)
			setSelectionAfterAdding(positionToSelect);
		onPlayerChanged(newCurrentSong, false, ex);
		switch (state) {
		case STATE_INITIALIZING_PENDING_LIST:
			performFinalInitializationTasks();
			break;
		case STATE_INITIALIZING:
			state = STATE_INITIALIZING_PENDING_ACTIONS;
			break;
		}
		executeStartCommand(forcePlayIdx);
	}
	
	private static MediaPlayer createPlayer() {
		MediaPlayer mp = new MediaPlayer();
		mp.setOnErrorListener(thePlayer);
		mp.setOnPreparedListener(thePlayer);
		mp.setOnSeekCompleteListener(thePlayer);
		mp.setOnCompletionListener(thePlayer);
		mp.setOnInfoListener(thePlayer);
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
		//PresetReverb.applyToPlayer(mp);
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
		mp.setOnInfoListener(null);
		stopPlayer(mp);
		mp.release();
	}
	
	private static void releaseInternal() {
		Equalizer.release();
		BassBoost.release();
		Virtualizer.release();
		//PresetReverb.release();
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
			sendMessage(MSG_UPDATE_STATE, ex);
		} else {
			if (firstError == null)
				firstError = failedSong;
			sendMessage(MSG_UPDATE_STATE, null);
			sendMessage(MSG_PLAY_NEXT_AUTO);
		}
	}
	
	private static boolean doPlayInternal(int how, Song song) {
		stopInternal(null);
		if (how != SongList.HOW_CURRENT)
			lastTime = -1;
		try {
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
		lastHow = how;
		currentSongBuffering = false;
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
					currentSongLoaded = false;
					currentSongPreparing = true;
					doInternal = 0;
				}
			}
			currentSong = song;
			final MediaPlayer p = currentPlayer;
			currentPlayer = nextPlayer;
			nextPlayer = p;
			sendMessageAtFrontOfQueue(MSG_PREPARE_PLAYBACK_1, how, doInternal | prepareNext, song);
		} else {
			stopPlayer(currentPlayer);
			sendMessageAtFrontOfQueue(MSG_PREPARE_PLAYBACK_1, how, 1 | prepareNext, song);
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
			sendMessageAtFrontOfQueue(MSG_PREPARE_PLAYBACK_2, prepareNext ? 1 : 0, 0, song);
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
		sendMessage(MSG_UPDATE_STATE, null);
	}
	
	private static Song playInternal(int how) {
		if (!hasFocus && !requestFocus()) {
			unpaused = false;
			playing = false;
			sendMessage(MSG_UPDATE_STATE, new FocusException());
			return null;
		}
		//we must set this to false here, as the user could have manually
		//started playback before the focus timer had the chance to trigger
		wasPlayingBeforeFocusLoss = false;
		final Song s = songs.getSongAndSetCurrent(how);
		if (s == null) {
			lastTime = -1;
			firstError = null;
			fullCleanup(null);
			sendMessage(MSG_UPDATE_STATE, null);
			return null;
		}
		if (currentPlayer == null || nextPlayer == null) {
			initializePlayers();
			state = STATE_PREPARING_PLAYBACK;
			sendMessage(MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_0, how, 0, s);
		} else {
			state = STATE_PREPARING_PLAYBACK;
			playInternal0(how, s);
		}
		return ((how == SongList.HOW_CURRENT) ? s : null);
	}
	
	private static void stopInternal(Song newCurrentSong) {
		currentSong = newCurrentSong;
		nextSong = null;
		playing = false;
		currentSongLoaded = false;
		currentSongPreparing = false;
		currentSongBuffering = false;
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
			//the user deleted the current song before it had a chance to load
			//therefore, ignore lastTime (as the song has changed)
			if (playInternal(SongList.HOW_CURRENT) != currentSong)
				lastTime = -1;
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
				sendMessage(MSG_UPDATE_STATE, ex);
				return false;
			}
			sendMessage(MSG_UPDATE_STATE, null);
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
			sendMessage(MSG_UPDATE_STATE, null);
		}
		return true;
	}
	
	public static void listCleared() {
		fullCleanup(null);
		lastTime = -1;
		sendMessage(MSG_UPDATE_STATE, null);
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
	
	public static void setTurnOffTimer(int minutes) {
		if (state == STATE_INITIALIZED || state == STATE_PREPARING_PLAYBACK) {
			turnOffTimerOrigin = 0;
			turnOffTimerSelectedMinutes = 0;
			if (turnOffTimer != null) {
				turnOffTimer.stop();
				turnOffTimer.release();
				turnOffTimer = null;
			}
			if (minutes > 0) {
				turnOffTimer = new Timer(thePlayer, "Turn Off Timer", true, true, false);
				if (minutes != 60 && minutes != 90 && minutes != 120)
					turnOffTimerCustomMinutes = minutes;
				//we must use SystemClock.elapsedRealtime because uptimeMillis
				//does not take sleep time into account (and the user makes no
				//difference between the time spent during sleep and the one
				//while actually working)
				turnOffTimerOrigin = SystemClock.elapsedRealtime();
				turnOffTimerSelectedMinutes = minutes;
				turnOffTimer.start(30000);
			}
		}
	}
	
	public static int getTurnOffTimerMinutesLeft() {
		if (turnOffTimerOrigin <= 0)
			return turnOffTimerSelectedMinutes;
		final int m = turnOffTimerSelectedMinutes - (int)((SystemClock.elapsedRealtime() - turnOffTimerOrigin) / 60000L);
		return ((m <= 0) ? 1 : m);
	}
	
	public static int getTurnOffTimerCustomMinutes() {
		return turnOffTimerCustomMinutes;
	}
	
	public static int getTurnOffTimerSelectedMinutes() {
		return turnOffTimerSelectedMinutes;
	}
	
	private static void processIdleTurnOffTimer() {
		final boolean timerShouldBeOn = (!playing && appIdle);
		if (idleTurnOffTimer.isAlive()) {
			if (!timerShouldBeOn) {
				idleTurnOffTimerOrigin = 0;
				idleTurnOffTimer.stop();
				if (turnOffTimerObserver != null)
					turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
			}
		} else {
			if (idleTurnOffTimerSelectedMinutes > 0) {
				if (timerShouldBeOn && (state == STATE_INITIALIZED || state == STATE_PREPARING_PLAYBACK)) {
					//refer to setIdleTurnOffTimer for more information
					idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
					idleTurnOffTimer.start(30000);
					if (turnOffTimerObserver != null)
						turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
				}
			} else {
				idleTurnOffTimerOrigin = 0;
				idleTurnOffTimerSelectedMinutes = 0;
				idleTurnOffTimer.stop();
				idleTurnOffTimer.release();
				idleTurnOffTimer = null;
				if (turnOffTimerObserver != null)
					turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
			}
		}
	}
	
	public static void setAppIdle(boolean appIdle) {
		if (Player.appIdle != appIdle) {
			Player.appIdle = appIdle;
			if (idleTurnOffTimer != null && idleTurnOffTimerSelectedMinutes > 0)
				processIdleTurnOffTimer();
		}
	}
	
	public static void setIdleTurnOffTimer(int minutes) {
		if (state == STATE_INITIALIZED || state == STATE_PREPARING_PLAYBACK) {
			idleTurnOffTimerOrigin = 0;
			idleTurnOffTimerSelectedMinutes = 0;
			if (idleTurnOffTimer != null) {
				idleTurnOffTimer.stop();
				idleTurnOffTimer.release();
				idleTurnOffTimer = null;
			}
			if (minutes > 0) {
				idleTurnOffTimer = new Timer(thePlayer, "Idle Turn Off Timer", true, true, false);
				if (minutes != 60 && minutes != 90 && minutes != 120)
					idleTurnOffTimerCustomMinutes = minutes;
				//we must use SystemClock.elapsedRealtime because uptimeMillis
				//does not take sleep time into account (and the user makes no
				//difference between the time spent during sleep and the one
				//while actually working)
				idleTurnOffTimerSelectedMinutes = minutes;
				if (!playing && appIdle) {
					idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
					idleTurnOffTimer.start(30000);
				}
			}
		}
	}
	
	public static int getIdleTurnOffTimerMinutesLeft() {
		if (idleTurnOffTimerOrigin <= 0)
			return idleTurnOffTimerSelectedMinutes;
		final int m = idleTurnOffTimerSelectedMinutes - (int)((SystemClock.elapsedRealtime() - idleTurnOffTimerOrigin) / 60000L);
		return ((m <= 0) ? 1 : m);
	}
	
	public static int getIdleTurnOffTimerCustomMinutes() {
		return idleTurnOffTimerCustomMinutes;
	}
	
	public static int getIdleTurnOffTimerSelectedMinutes() {
		return idleTurnOffTimerSelectedMinutes;
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
		return (currentSongPreparing || currentSongBuffering || (state == STATE_PREPARING_PLAYBACK));
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
	
	public static boolean startService(Context context) {
		if (thePlayer == null && state == STATE_NEW) {
			initialize(context);
			context.startService(new Intent(context, Player.class));
			return true;
		}
		return false;
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
			playPause();
			break;
		case KeyEvent.KEYCODE_HEADSETHOOK:
			if (headsetHookTimer == null) {
				playPause();
			} else {
				if (headsetHookLastTime != 0) {
					headsetHookTimer.stop();
					if ((SystemClock.uptimeMillis() - headsetHookLastTime) < 500) {
						headsetHookLastTime = 0;
						if (headsetHookDoublePressPauses)
							playPause();
						else
							next();
					}
				} else {
					headsetHookLastTime = SystemClock.uptimeMillis();
					headsetHookTimer.start(500);
				}
			}
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
	
	public static void resetEffects(Runnable observer) {
		if (state == STATE_INITIALIZED) {
			effectsObserver = observer;
			sendMessage(MSG_RESET_EFFECTS_0, 0, 0);
		}
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
		} else if (timer == headsetHookTimer) {
			if (thePlayer != null && headsetHookLastTime != 0) {
				headsetHookLastTime = 0;
				if (headsetHookDoublePressPauses)
					next();
				else
					playPause();
			}
		} else if (timer == turnOffTimer) {
			if (turnOffTimerOrigin > 0) {
				final int secondsLeft = (turnOffTimerSelectedMinutes * 60) - (int)((SystemClock.elapsedRealtime() - turnOffTimerOrigin) / 1000);
				if (turnOffTimerObserver != null)
					turnOffTimerObserver.onPlayerTurnOffTimerTick();
				if (secondsLeft < 15) //less than half of our period
					stopService();
				else
					turnOffTimer.start(30000);
			}
		} else if (timer == idleTurnOffTimer) {
			boolean wasPlayingBeforeOngoingCall = false;
			final boolean idle = (!playing && appIdle);
			if (idle && telephonyManager != null) {
				//check for ongoing call
				try {
					if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
						wasPlayingBeforeOngoingCall = wasPlayingBeforeFocusLoss;
				} catch (Throwable ex) {
				}
			}
			if (!idle || wasPlayingBeforeOngoingCall) {
				if (idle) {
					//consider time spent in calls as active time, but keep checking,
					//because when the call ends, the audio focus could go to someone
					//else, rendering us actually idle!
					idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
					idleTurnOffTimer.start(30000);
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
						idleTurnOffTimer.start(30000);
				}
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
		if (headsetHookTimer != null)
			headsetHookTimer.stop();
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
			sendMessage(MSG_UPDATE_STATE, null);
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
			if (!doNotAttenuateVolume) {
				//when dimmed, decreased the volume by 20dB
				actualVolume = volume * 0.1f;
				dimmedVolume = true;
				if (currentPlayer != null && currentSongLoaded) {
					try {
						currentPlayer.setVolume(actualVolume, actualVolume);
					} catch (Throwable ex) {
					}
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
					sendMessage(MSG_UPDATE_STATE, new MediaServerDiedException());
				} else {
					reviveAlreadyRetried = true;
					playInternal(SongList.HOW_CURRENT);
				}
			} else if (mp == nextPlayer) {
				nextSong = null;
				nextPreparing = false;
				nextPrepared = false;
			} else {
				final boolean prep = currentSongPreparing;
				fullCleanup(currentSong);
				if (prep && lastHow == SongList.HOW_NEXT_AUTO)
					//the error happened during currentSong's preparation
					nextFailed(currentSong, lastHow, (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) ? new TimeoutException() : new IOException());
				else
					sendMessage(MSG_UPDATE_STATE, (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) ? new TimeoutException() : new IOException());
			}
		} else {
			fullCleanup(currentSong);
			releaseInternal();
			sendMessage(MSG_UPDATE_STATE, new Exception("Invalid MediaPlayer"));
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
					sendMessage(MSG_UPDATE_STATE, null);
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
				sendMessage(MSG_UPDATE_STATE, null);
				if (!prepareNextOnSeek)
					processPreparation();
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				nextFailed(currentSong, lastHow, ex);
			}
		} else {
			fullCleanup(currentSong);
			sendMessage(MSG_UPDATE_STATE, new Exception("Invalid MediaPlayer"));
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
				sendMessage(MSG_UPDATE_STATE, null);
				if (prepareNextOnSeek) {
					prepareNextOnSeek = false;
					processPreparation();
				}
			} catch (Throwable ex) {
				fullCleanup(currentSong);
				sendMessage(MSG_UPDATE_STATE, ex);
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
	
	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		if (mp == currentPlayer) {
			switch (what) {
			case MediaPlayer.MEDIA_INFO_BUFFERING_START:
				if (!currentSongBuffering) {
					currentSongBuffering = true;
					sendMessage(MSG_UPDATE_META);
				}
				break;
			case MediaPlayer.MEDIA_INFO_BUFFERING_END:
				if (currentSongBuffering) {
					currentSongBuffering = false;
					sendMessage(MSG_UPDATE_META);
				}
				break;
			}
		}
		return false;
	}
	
	//All this PlayerHandler stuff was created to avoid creating new instances of the
	//former ObjHolder class, every time an object had to be sent in a message
	private static void sendMessage(int what) {
		_playerHandler.sendMessageAtTime(Message.obtain(_playerHandler, what), SystemClock.uptimeMillis());
	}
	
	private static void sendMessage(int what, Object obj) {
		_playerHandler.sendMessageAtTime(Message.obtain(_playerHandler, what, obj), SystemClock.uptimeMillis());
	}
	
	private static void sendMessage(int what, int arg1, int arg2) {
		_playerHandler.sendMessageAtTime(Message.obtain(_playerHandler, what, arg1, arg2), SystemClock.uptimeMillis());
	}
	
	private static void sendMessage(int what, int arg1, int arg2, Object obj) {
		_playerHandler.sendMessageAtTime(Message.obtain(_playerHandler, what, arg1, arg2, obj), SystemClock.uptimeMillis());
	}
	
	private static void sendMessageAtFrontOfQueue(int what, int arg1, int arg2, Object obj) {
		_playerHandler.sendMessageAtFrontOfQueue(Message.obtain(_playerHandler, what, arg1, arg2, obj));
	}
	
	private static final class PlayerHandler extends Handler {
		public PlayerHandler(Context context) {
			super(context.getMainLooper());
		}
		
		@Override
		public void dispatchMessage(Message msg) {
			switch (msg.what) {
			case MSG_UPDATE_STATE:
				onPlayerChanged(null, false, (Throwable)msg.obj);
				break;
			case MSG_PLAY_NEXT_AUTO:
				playInternal(SongList.HOW_NEXT_AUTO);
				break;
			case MSG_UPDATE_META:
				onPlayerChanged(null, true, null);
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_0:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				Equalizer.release();
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_1, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_1:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				BassBoost.release();
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_2, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_2:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				Virtualizer.release();
				//@@@ PresetReverb.release();
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_3, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_3:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				if (Equalizer.isEnabled() && currentPlayer != null)
					Equalizer.initialize(currentPlayer.getAudioSessionId());
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_4, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_4:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				if (Equalizer.isEnabled() && currentPlayer != null)
					Equalizer.setEnabled(true, true);
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_5, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_5:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				if (BassBoost.isEnabled() && currentPlayer != null)
					BassBoost.initialize(currentPlayer.getAudioSessionId());
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_6, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_6:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				if (BassBoost.isEnabled() && currentPlayer != null)
					BassBoost.setEnabled(true, true);
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_7, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_7:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				if (Virtualizer.isEnabled() && currentPlayer != null)
					Virtualizer.initialize(currentPlayer.getAudioSessionId());
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_8, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_PREPARE_EFFECTS_BEFORE_PLAYBACK_8:
				if (!hasFocus) {
					if (state == STATE_PREPARING_PLAYBACK)
						state = STATE_INITIALIZED;
					break;
				}
				if (Virtualizer.isEnabled() && currentPlayer != null)
					Virtualizer.setEnabled(true, true);
				sendMessageAtTime(Message.obtain(this, MSG_PREPARE_PLAYBACK_0, msg.arg1, msg.arg2, msg.obj), SystemClock.uptimeMillis());
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
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_1), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_1:
				initializePlayers();
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_2), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_2:
				if (currentPlayer != null)
					Equalizer.initialize(currentPlayer.getAudioSessionId());
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_3), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_3:
				Equalizer.release();
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_4), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_4:
				if (currentPlayer != null)
					BassBoost.initialize(currentPlayer.getAudioSessionId());
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_5), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_5:
				BassBoost.release();
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_6), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_6:
				if (currentPlayer != null)
					Virtualizer.initialize(currentPlayer.getAudioSessionId());
				sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_7), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_7:
				Virtualizer.release();
				//now that the effects have been initialized at least once, properly
				//create and enabled them as necessary!
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_3, 1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_INITIALIZATION_8:
				switch (state) {
				case STATE_INITIALIZING_PENDING_ACTIONS:
					performFinalInitializationTasks();
					break;
				case STATE_INITIALIZING:
					state = STATE_INITIALIZING_PENDING_LIST;
					break;
				}
				executeStartCommand(-1);
				break;
			case MSG_RESET_EFFECTS_0:
				//don't even ask.......
				//(a few devices won't disable one effect while the other effect is enabled)
				if (state == STATE_INITIALIZED)
					Equalizer.release();
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_1, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_1:
				if (state == STATE_INITIALIZED)
					BassBoost.release();
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_2, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_2:
				if (state == STATE_INITIALIZED)
					Virtualizer.release();
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_3, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_3:
				if (msg.arg1 != 0 || state == STATE_INITIALIZED) {
					if (Equalizer.isEnabled() && currentPlayer != null)
						Equalizer.initialize(currentPlayer.getAudioSessionId());
				}
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_4, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_4:
				if (msg.arg1 != 0 || state == STATE_INITIALIZED) {
					if (Equalizer.isEnabled() && currentPlayer != null)
						Equalizer.setEnabled(true, true);
				}
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_5, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_5:
				if (msg.arg1 != 0 || state == STATE_INITIALIZED) {
					if (BassBoost.isEnabled() && currentPlayer != null)
						BassBoost.initialize(currentPlayer.getAudioSessionId());
				}
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_6, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_6:
				if (msg.arg1 != 0 || state == STATE_INITIALIZED) {
					if (BassBoost.isEnabled() && currentPlayer != null)
						BassBoost.setEnabled(true, true);
				}
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_7, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_7:
				if (msg.arg1 != 0 || state == STATE_INITIALIZED) {
					if (Virtualizer.isEnabled() && currentPlayer != null)
						Virtualizer.initialize(currentPlayer.getAudioSessionId());
				}
				sendMessageAtTime(Message.obtain(this, MSG_RESET_EFFECTS_8, msg.arg1, 0), SystemClock.uptimeMillis());
				break;
			case MSG_RESET_EFFECTS_8:
				if (msg.arg1 != 0 || state == STATE_INITIALIZED) {
					if (Virtualizer.isEnabled() && currentPlayer != null)
						Virtualizer.setEnabled(true, true);
				}
				if (msg.arg1 != 0)
					sendMessageAtTime(Message.obtain(this, MSG_INITIALIZATION_8), SystemClock.uptimeMillis());
				else if (effectsObserver != null)
					MainHandler.postToMainThread(effectsObserver);
				effectsObserver = null;
				break;
			case MSG_TERMINATION_0:
				releaseInternal();
				sendMessageAtTime(Message.obtain(this, MSG_TERMINATION_1), SystemClock.uptimeMillis());
				break;
			case MSG_TERMINATION_1:
				onPlayerChanged(null, false, null); //to update the widget
				unregisterMediaButtonEventReceiver();
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && thePlayer != null)
					unregisterMediaRouter(thePlayer);
				//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && thePlayer != null)
				//	unregisterA2dpObserver(thePlayer);
				sendMessageAtTime(Message.obtain(this, MSG_TERMINATION_2), SystemClock.uptimeMillis());
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
					focusDelayTimer.release();
					focusDelayTimer = null;
				}
				if (prepareDelayTimer != null) {
					prepareDelayTimer.stop();
					prepareDelayTimer.release();
					prepareDelayTimer = null;
				}
				if (volumeTimer != null) {
					volumeTimer.stop();
					volumeTimer.release();
					volumeTimer = null;
				}
				if (headsetHookTimer != null) {
					headsetHookTimer.stop();
					headsetHookTimer.release();
					headsetHookTimer = null;
				}
				if (turnOffTimer != null) {
					turnOffTimer.stop();
					turnOffTimer.release();
					turnOffTimer = null;
				}
				if (idleTurnOffTimer != null) {
					idleTurnOffTimer.stop();
					idleTurnOffTimer.release();
					idleTurnOffTimer = null;
				}
				if (thePlayer != null) {
					if (externalReceiver != null)
						thePlayer.getApplicationContext().unregisterReceiver(externalReceiver);
					saveConfig(thePlayer, true);
				}
				observer = null;
				turnOffTimerObserver = null;
				notificationManager = null;
				audioManager = null;
				telephonyManager = null;
				externalReceiver = null;
				effectsObserver = null;
				stickyBroadcast = null;
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
			msg.obj = null;
		}
	}

}
