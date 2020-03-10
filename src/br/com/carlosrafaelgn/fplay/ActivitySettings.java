//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013-2014-2014, Carlos Rafael Gimenes das Neves
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
package br.com.carlosrafaelgn.fplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.ExternalFx;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.playback.context.MediaContext;
import br.com.carlosrafaelgn.fplay.plugin.HttpTransmitter;
import br.com.carlosrafaelgn.fplay.plugin.WirelessVisualizer;
import br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin;
import br.com.carlosrafaelgn.fplay.plugin.PluginManager;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgDialog;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.ColorPickerView;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.ObservableScrollView;
import br.com.carlosrafaelgn.fplay.ui.SettingView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;

@SuppressWarnings("WeakerAccess")
public final class ActivitySettings extends ClientActivity implements Player.PlayerTurnOffTimerObserver, View.OnClickListener, DialogInterface.OnClickListener, ColorPickerView.OnColorPickerViewListener, ObservableScrollView.OnScrollListener, Runnable, MainHandler.Callback, PluginManager.Observer {
	public static final int MODE_NORMAL = 0;
	public static final int MODE_COLOR = 1;
	public static final int MODE_BLUETOOTH = 2;
	public static final int MODE_HTTP = 3;
	private static final int MSG_SAVE_CONFIG = 0x0900;
	private static final int MSG_REFRESH_BLUETOOTH = 0x0901;
	private static final int MSG_REFRESH_HTTP = 0x0902;
	private static final double MIN_THRESHOLD = 1.5; //waaaaaaaaaayyyyyyyy below W3C recommendations, so no one should complain about the app being "boring"
	private final int mode;
	private boolean changed, checkingReturn, configsChanged, lblTitleOk, startTransmissionOnConnection, tryingToEnableExternalFx, openHttpTransmitterOnConnection;
	private BgButton btnGoBack, btnBluetooth, btnAbout;
	private EditText txtCustomMinutes;
	private ObservableScrollView list;
	private TextView lblTitle;
	private RelativeLayout panelControls;
	private LinearLayout panelSettings;
	private ViewGroup viewForPadding;
	private SettingView firstViewAdded, lastViewAdded, optLoadCurrentTheme, optUseAlternateTypeface,
		optAutoTurnOff, optAutoIdleTurnOff, optAutoTurnOffPlaylist, optKeepScreenOn, optTheme, optFlat,
		optBorders, optPlayWithLongPress, optExpandSeekBar, optVolumeControlType, optDoNotAttenuateVolume,
		opt3D, optIsDividerVisible, optIsVerticalMarginLarge, optExtraSpacing, optPlaceTitleAtTheBottom,
		optForcedLocale, optPlacePlaylistToTheRight, optScrollBarToTheLeft, optScrollBarSongList,
		optScrollBarBrowser, optWidgetTransparentBg, optWidgetTextColor, optWidgetIconColor,
		optHandleCallKey, optHeadsetHook1, optHeadsetHook2, optHeadsetHook3, optExternalFx,
		optFileBufferSize, optPlayWhenHeadsetPlugged, optBlockBackKey, optBackKeyAlwaysReturnsToPlayerWhenBrowsing,
		optWrapAroundList, optDoubleClickMode, optMarqueeTitle, optPrepareNext,
		optClearListWhenPlayingFolders, optGoBackWhenPlayingFolders, optExtraInfoMode, optForceOrientation,
		optTransition, optPopupTransition, optAnimations, optNotFullscreen, optFadeInFocus, optFadeInPause,
		optFadeInOther, optBtMessage, optBtConnect, optBtStart, optBtFramesToSkip, optBtSize, optBtVUMeter,
		optBtSpeed, optAnnounceCurrentSong, optFollowCurrentSong, optBytesBeforeDecoding, optMSBeforePlayback,
		optBufferSize, optFillThreshold, optPlaybackEngine, optResampling, optPreviousResetsAfterTheBeginning,
		optLargeTextIs22sp, optDisplaySongNumberAndCount, optAllowLockScreen, optPlaceControlsAtTheBottom,
		optAlbumArtSongList, lastMenuView;
	private String btErrorMessage, httpAccessCode;
	private SettingView[] colorViews;
	private int lastColorView, currentHeader, btMessageText, btConnectText, btStartText, optBtSizeLastSize;
	private TextView[] headers;

	public ActivitySettings(int mode) {
		switch (mode) {
		case MODE_COLOR:
		case MODE_BLUETOOTH:
		case MODE_HTTP:
			this.mode = mode;
			break;
		default:
			this.mode = MODE_NORMAL;
			break;
		}
	}

	@Override
	public CharSequence getTitle() {
		switch (mode) {
		case MODE_COLOR:
			return getText(R.string.custom_color_theme);
		case MODE_BLUETOOTH:
			return "Bluetooth + Arduino";
		case MODE_HTTP:
			return getText(R.string.share);
		default:
			return getText(R.string.settings);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (view == optAutoTurnOff || view == optAutoIdleTurnOff) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int s = ((view == optAutoTurnOff) ? Player.turnOffTimerSelectedMinutes : Player.idleTurnOffTimerSelectedMinutes);
			final int c = ((view == optAutoTurnOff) ? Player.turnOffTimerCustomMinutes : Player.idleTurnOffTimerCustomMinutes);
			menu.add(0, 0, 0, R.string.never)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s <= 0 ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, c, 0, getMinuteString(c))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == c ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 1, 1);
			menu.add(2, 60, 0, getMinuteString(60))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == 60 ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(2, 90, 1, getMinuteString(90))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == 90 ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(2, 120, 2, getMinuteString(120))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == 120 ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 2, 4);
			menu.add(3, -2, 0, R.string.custom)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s != c && s != 60 && s != 90 && s != 120 && s > 0 ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optForcedLocale) {
			final int o = UI.forcedLocale;
			lastMenuView = optForcedLocale;
			UI.prepare(menu);
			menu.add(0, UI.LOCALE_NONE, 0, R.string.standard_language)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_NONE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.LOCALE_DE, 0, UI.getLocaleDescriptionFromCode(UI.LOCALE_DE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_DE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_US, 1, UI.getLocaleDescriptionFromCode(UI.LOCALE_US))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_US) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_ES, 2, UI.getLocaleDescriptionFromCode(UI.LOCALE_ES))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_ES) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_FR, 3, UI.getLocaleDescriptionFromCode(UI.LOCALE_FR))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_FR) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_PTBR, 4, UI.getLocaleDescriptionFromCode(UI.LOCALE_PTBR))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_PTBR) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_RU, 5, UI.getLocaleDescriptionFromCode(UI.LOCALE_RU))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_RU) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_UK, 6, UI.getLocaleDescriptionFromCode(UI.LOCALE_UK))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_UK) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.LOCALE_ZH, 7, UI.getLocaleDescriptionFromCode(UI.LOCALE_ZH))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_ZH) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optTheme) {
			final int o = UI.theme;
			lastMenuView = optTheme;
			UI.prepare(menu);
			menu.add(0, UI.THEME_CUSTOM, 0, UI.getThemeString(UI.THEME_CUSTOM))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_CUSTOM) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.THEME_FPLAY, 0, UI.getThemeString(UI.THEME_FPLAY))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_FPLAY) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_FPLAY_ICY, 1, UI.getThemeString(UI.THEME_FPLAY_ICY))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_FPLAY_ICY) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_FPLAY_DARK, 2, UI.getThemeString(UI.THEME_FPLAY_DARK))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_FPLAY_DARK) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_FPLAY_2016, 3, UI.getThemeString(UI.THEME_FPLAY_2016))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_FPLAY_2016) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_CREAMY, 4, UI.getThemeString(UI.THEME_CREAMY))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_CREAMY) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_DARK_LIGHT, 5, UI.getThemeString(UI.THEME_DARK_LIGHT))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_DARK_LIGHT) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_BLUE_ORANGE, 6, UI.getThemeString(UI.THEME_BLUE_ORANGE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_BLUE_ORANGE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_BLUE, 7, UI.getThemeString(UI.THEME_BLUE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_BLUE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_ORANGE, 8, UI.getThemeString(UI.THEME_ORANGE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_ORANGE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.THEME_LIGHT, 9, UI.getThemeString(UI.THEME_LIGHT))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_LIGHT) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optVolumeControlType) {
			lastMenuView = optVolumeControlType;
			UI.prepare(menu);
			menu.add(0, Player.VOLUME_CONTROL_STREAM, 0, R.string.volume_control_type_integrated)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, Player.VOLUME_CONTROL_DB, 0, R.string.volume_control_type_decibels)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_DB) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, Player.VOLUME_CONTROL_PERCENT, 1, R.string.volume_control_type_percentage)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_PERCENT) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 1, 2);
			menu.add(2, Player.VOLUME_CONTROL_NONE, 0, R.string.noneM)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_NONE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optExtraInfoMode) {
			lastMenuView = optExtraInfoMode;
			UI.prepare(menu);
			final int o = Song.extraInfoMode;
			for (int i = Song.EXTRA_ARTIST; i <= Song.EXTRA_ARTIST_ALBUM; i++) {
				menu.add(0, i, i, getExtraInfoModeString(i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == i) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		} else if (view == optForceOrientation) {
			lastMenuView = optForceOrientation;
			UI.prepare(menu);
			final int o = UI.forcedOrientation;
			menu.add(0, 0, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == 0) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, -1, 0, R.string.landscape)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o < 0) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, 1, 1, R.string.portrait)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o > 0) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optTransition) {
			lastMenuView = optTransition;
			UI.prepare(menu);
			final int o = UI.transitions & 0xFF;
			menu.add(0, UI.TRANSITION_NONE, 0, UI.getTransitionString(UI.TRANSITION_NONE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_NONE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.TRANSITION_ZOOM_FADE, 0, UI.getTransitionString(UI.TRANSITION_ZOOM_FADE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_ZOOM_FADE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_SLIDE_SMOOTH, 1, UI.getTransitionString(UI.TRANSITION_SLIDE_SMOOTH))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_SLIDE_SMOOTH) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_SLIDE, 2, UI.getTransitionString(UI.TRANSITION_SLIDE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_SLIDE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_FADE, 3, UI.getTransitionString(UI.TRANSITION_FADE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_FADE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_DISSOLVE, 4, UI.getTransitionString(UI.TRANSITION_DISSOLVE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_DISSOLVE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_ZOOM, 5, UI.getTransitionString(UI.TRANSITION_ZOOM))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_ZOOM) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optPopupTransition) {
			lastMenuView = optPopupTransition;
			UI.prepare(menu);
			final int o = UI.transitions >>> 8;
			menu.add(0, UI.TRANSITION_NONE, 0, UI.getTransitionString(UI.TRANSITION_NONE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_NONE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				menu.add(1, UI.TRANSITION_ZOOM_FADE, 0, UI.getTransitionString(UI.TRANSITION_ZOOM_FADE))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == UI.TRANSITION_ZOOM_FADE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_SLIDE_SMOOTH, 1, UI.getTransitionString(UI.TRANSITION_SLIDE_SMOOTH))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_SLIDE_SMOOTH) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, UI.TRANSITION_FADE, 2, UI.getTransitionString(UI.TRANSITION_FADE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.TRANSITION_FADE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				menu.add(1, UI.TRANSITION_DISSOLVE, 3, UI.getTransitionString(UI.TRANSITION_DISSOLVE))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == UI.TRANSITION_DISSOLVE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optScrollBarSongList || view == optScrollBarBrowser) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int d = ((view == optScrollBarSongList) ? UI.songListScrollBarType : UI.browserScrollBarType);
			if (view == optScrollBarBrowser)
				menu.add(0, BgListView.SCROLLBAR_INDEXED, 0, R.string.indexed_if_possible)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((d == BgListView.SCROLLBAR_INDEXED) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, BgListView.SCROLLBAR_LARGE, 1, R.string.large)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d == BgListView.SCROLLBAR_LARGE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, BgListView.SCROLLBAR_SYSTEM, 2, R.string.system_integrated)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d != BgListView.SCROLLBAR_INDEXED && d != BgListView.SCROLLBAR_LARGE && d != BgListView.SCROLLBAR_NONE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 3);
			menu.add(1, BgListView.SCROLLBAR_NONE, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d == BgListView.SCROLLBAR_NONE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int d = ((view == optFadeInFocus) ? Player.fadeInIncrementOnFocus : ((view == optFadeInPause) ? Player.fadeInIncrementOnPause : Player.fadeInIncrementOnOther));
			menu.add(0, 0, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d <= 0) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, 250, 0, R.string.dshort)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d >= 250) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, 125, 1, R.string.dmedium)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(((d >= 125) && (d < 250)) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, 62, 2, R.string.dlong)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(((d > 0) && (d < 125)) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optHeadsetHook1 || view == optHeadsetHook2 || view == optHeadsetHook3) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int pressCount = ((view == optHeadsetHook1) ? 1 : ((view == optHeadsetHook2) ? 2 : 3));
			final int action = Player.getHeadsetHookAction(pressCount);
			menu.add(0, 0, 0, R.string.nothing)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == 0) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			menu.add(1, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, getText(R.string.play) + "/" + getText(R.string.pause))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, KeyEvent.KEYCODE_MEDIA_NEXT, 1, R.string.next)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == KeyEvent.KEYCODE_MEDIA_NEXT) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(1, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 2, R.string.previous)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == KeyEvent.KEYCODE_MEDIA_PREVIOUS) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optBtSize) {
			lastMenuView = optBtSize;
			UI.prepare(menu);
			final int size = Player.getBluetoothVisualizerSize();
			for (int i = 0; i <= 6; i++) {
				menu.add(0, i, i, Integer.toString(1 << (2 + i)))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((size == i) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		} else if (view == optBtSpeed) {
			lastMenuView = optBtSpeed;
			UI.prepare(menu);
			final int speed = Player.getBluetoothVisualizerSpeed();
			for (int i = 0; i <= 2; i++) {
				menu.add(0, i, i, Integer.toString(3 - i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((speed == i) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		} else if (view == optBtFramesToSkip) {
			lastMenuView = optBtFramesToSkip;
			UI.prepare(menu);
			final int framesToSkip = Player.getBluetoothVisualizerFramesToSkipIndex();
			for (int i = 0; i <= 11; i++) {
				menu.add(0, i, i, Integer.toString(Player.getBluetoothVisualizerFramesPerSecond(i)))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((framesToSkip == i) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		} else if (view == optBytesBeforeDecoding) {
			lastMenuView = optBytesBeforeDecoding;
			UI.prepare(menu);
			final int bytesBeforeDecodingIndex = Player.getBytesBeforeDecodingIndex();
			for (int i = 0; i <= 7; i++) {
				menu.add(0, i, i, getBytesBeforeDecodingString(i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((bytesBeforeDecodingIndex == i) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		} else if (view == optMSBeforePlayback) {
			lastMenuView = optMSBeforePlayback;
			UI.prepare(menu);
			final int secondsBeforePlaybackIndex = Player.getMSBeforePlaybackIndex();
			for (int i = 0; i <= 4; i++) {
				menu.add(0, i, i, getSecondsBeforePlaybackString(i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((secondsBeforePlaybackIndex == i) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		} else if (view == optBufferSize) {
			lastMenuView = optBufferSize;
			UI.prepare(menu);
			final int bufferSizeIndex = (Player.getBufferConfig() & Player.BUFFER_SIZE_MASK);
			menu.add(0, Player.BUFFER_SIZE_500MS, 0, getBufferSizeString(Player.BUFFER_SIZE_500MS))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((bufferSizeIndex == Player.BUFFER_SIZE_500MS) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.BUFFER_SIZE_1000MS, 1, getBufferSizeString(Player.BUFFER_SIZE_1000MS))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((bufferSizeIndex == Player.BUFFER_SIZE_1000MS) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.BUFFER_SIZE_1500MS, 2, getBufferSizeString(Player.BUFFER_SIZE_1500MS))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((bufferSizeIndex == Player.BUFFER_SIZE_1500MS) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.BUFFER_SIZE_2000MS, 3, getBufferSizeString(Player.BUFFER_SIZE_2000MS))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((bufferSizeIndex == Player.BUFFER_SIZE_2000MS) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.BUFFER_SIZE_2500MS, 4, getBufferSizeString(Player.BUFFER_SIZE_2500MS))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((bufferSizeIndex == Player.BUFFER_SIZE_2500MS) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optFillThreshold) {
			lastMenuView = optFillThreshold;
			UI.prepare(menu);
			final int fillThresholdIndex = (Player.getBufferConfig() & Player.FILL_THRESHOLD_MASK);
			menu.add(0, Player.FILL_THRESHOLD_25, 0, getFillThresholdString(Player.FILL_THRESHOLD_25))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((fillThresholdIndex == Player.FILL_THRESHOLD_25) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.FILL_THRESHOLD_50, 1, getFillThresholdString(Player.FILL_THRESHOLD_50))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((fillThresholdIndex == Player.FILL_THRESHOLD_50) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.FILL_THRESHOLD_75, 2, getFillThresholdString(Player.FILL_THRESHOLD_75))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((fillThresholdIndex == Player.FILL_THRESHOLD_75) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, Player.FILL_THRESHOLD_100, 3, getFillThresholdString(Player.FILL_THRESHOLD_100))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((fillThresholdIndex == Player.FILL_THRESHOLD_100) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optPlaybackEngine) {
			lastMenuView = optPlaybackEngine;
			UI.prepare(menu);
			menu.add(0, 0, 0, getPlaybackEngineString(false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(!MediaContext.useOpenSLEngine ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, 1, 1, getPlaybackEngineString(true))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(MediaContext.useOpenSLEngine ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else if (view == optFileBufferSize) {
			lastMenuView = optFileBufferSize;
			UI.prepare(menu);
			menu.add(0, 0, 0, getFileBufferSizeString(Player.getFilePrefetchSizeFromOptions(0)))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.filePrefetchSize == 0) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			UI.separator(menu, 0, 1);
			for (int i = 1; i <= 3; i++) {
				final int filePrefetchSize = Player.getFilePrefetchSizeFromOptions(i);
				menu.add(1, i, i, getFileBufferSizeString(filePrefetchSize))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((Player.filePrefetchSize == filePrefetchSize) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			}
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		//NullPointerException reported at Play Store.... :/
		final ActivityHost ctx = getHostActivity();
		if (item == null || lastMenuView == null || ctx == null)
			return true;
		if (lastMenuView == optAutoTurnOff || lastMenuView == optAutoIdleTurnOff) {
			if (item.getItemId() >= 0) {
				if (lastMenuView == optAutoTurnOff) {
					Player.setTurnOffTimer(item.getItemId());
					optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
				} else {
					Player.setIdleTurnOffTimer(item.getItemId());
					optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
				}
			} else {
				final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);

				txtCustomMinutes = UI.createDialogEditText(ctx, 0, Integer.toString((lastMenuView == optAutoTurnOff) ? Player.turnOffTimerCustomMinutes : Player.idleTurnOffTimerCustomMinutes), getText(R.string.msg_turn_off), InputType.TYPE_CLASS_NUMBER);
				l.addView(txtCustomMinutes, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

				final BgDialog dialog = new BgDialog(ctx, l, this);
				dialog.setTitle(R.string.msg_turn_off_title);
				dialog.setPositiveButton(R.string.ok);
				dialog.setNegativeButton(R.string.cancel);
				dialog.show();
			}
		} else if (lastMenuView == optForcedLocale) {
			if (item.getItemId() != UI.forcedLocale) {
				UI.setForcedLocale(ctx, item.getItemId());
				WidgetMain.updateWidgets();
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (lastMenuView == optTheme) {
			if (item.getItemId() == UI.THEME_CUSTOM) {
				startActivity(new ActivitySettings(MODE_COLOR), 0, null, false);
			} else {
				UI.setTheme(ctx, item.getItemId());
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (lastMenuView == optVolumeControlType) {
			Player.setVolumeControlType(item.getItemId());
			optVolumeControlType.setSecondaryText(getVolumeString());
		} else if (lastMenuView == optExtraInfoMode) {
			Song.extraInfoMode = item.getItemId();
			optExtraInfoMode.setSecondaryText(getExtraInfoModeString(Song.extraInfoMode));
			Player.songs.updateExtraInfo();
			WidgetMain.updateWidgets();
		} else if (lastMenuView == optForceOrientation) {
			UI.forcedOrientation = item.getItemId();
			getHostActivity().setRequestedOrientation((UI.forcedOrientation == 0) ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ((UI.forcedOrientation < 0) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			optForceOrientation.setSecondaryText(getOrientationString());
		} else if (lastMenuView == optTransition) {
			UI.setTransition(item.getItemId());
			optTransition.setSecondaryText(UI.getTransitionString(item.getItemId()));
		} else if (lastMenuView == optPopupTransition) {
			UI.setPopupTransition(item.getItemId());
			optPopupTransition.setSecondaryText(UI.getTransitionString(item.getItemId()));
		} else if (lastMenuView == optScrollBarSongList) {
			UI.songListScrollBarType = item.getItemId();
			optScrollBarSongList.setSecondaryText(getScrollBarString(item.getItemId()));
		} else if (lastMenuView == optScrollBarBrowser) {
			UI.browserScrollBarType = item.getItemId();
			optScrollBarBrowser.setSecondaryText(getScrollBarString(item.getItemId()));
			list.updateVerticalScrollbar();
		} else if (lastMenuView == optFadeInFocus) {
			Player.fadeInIncrementOnFocus = item.getItemId();
			optFadeInFocus.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optFadeInPause) {
			Player.fadeInIncrementOnPause = item.getItemId();
			optFadeInPause.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optFadeInOther) {
			Player.fadeInIncrementOnOther = item.getItemId();
			optFadeInOther.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optHeadsetHook1 || lastMenuView == optHeadsetHook2 || lastMenuView == optHeadsetHook3) {
			final int pressCount = ((lastMenuView == optHeadsetHook1) ? 1 : ((lastMenuView == optHeadsetHook2) ? 2 : 3));
			Player.setHeadsetHookAction(pressCount, item.getItemId());
			lastMenuView.setSecondaryText(getHeadsetHookString(pressCount));
		} else if (lastMenuView == optBtSize) {
			Player.setBluetoothVisualizerSize(optBtSizeLastSize = item.getItemId());
			optBtSize.setSecondaryText(getSizeString());
			if (Player.bluetoothVisualizer != null)
				((WirelessVisualizer)Player.bluetoothVisualizer).syncSize();
		} else if (lastMenuView == optBtSpeed) {
			Player.setBluetoothVisualizerSpeed(item.getItemId());
			optBtSpeed.setSecondaryText(getSpeedString());
			if (Player.bluetoothVisualizer != null)
				((WirelessVisualizer)Player.bluetoothVisualizer).syncSpeed();
		} else if (lastMenuView == optBtFramesToSkip) {
			Player.setBluetoothVisualizerFramesToSkipIndex(item.getItemId());
			optBtFramesToSkip.setSecondaryText(getFramesToSkipString());
			if (Player.bluetoothVisualizer != null)
				((WirelessVisualizer)Player.bluetoothVisualizer).syncFramesToSkip();
		} else if (lastMenuView == optBytesBeforeDecoding) {
			Player.setBytesBeforeDecodingIndex(item.getItemId());
			optBytesBeforeDecoding.setSecondaryText(getBytesBeforeDecodingString(item.getItemId()));
		} else if (lastMenuView == optMSBeforePlayback) {
			Player.setMSBeforePlayingIndex(item.getItemId());
			optMSBeforePlayback.setSecondaryText(getSecondsBeforePlaybackString(item.getItemId()));
		} else if (lastMenuView == optBufferSize) {
			Player.setBufferConfig((Player.getBufferConfig() & ~Player.BUFFER_SIZE_MASK) | item.getItemId());
			optBufferSize.setSecondaryText(getBufferSizeString(item.getItemId()));
		} else if (lastMenuView == optFillThreshold) {
			Player.setBufferConfig((Player.getBufferConfig() & ~Player.FILL_THRESHOLD_MASK) | item.getItemId());
			optFillThreshold.setSecondaryText(getFillThresholdString(item.getItemId()));
		} else if (lastMenuView == optPlaybackEngine) {
			MediaContext.useOpenSLEngine = (item.getItemId() == 1);
			optPlaybackEngine.setSecondaryText(getPlaybackEngineString(MediaContext.useOpenSLEngine));
			//We must remove/add from the panel because its top position is reported to be 0 by a few
			//devices when not visible, messing up with getChildIndexAroundPosition()'s results
			//Since it has to be removed from the panel, SettingView.onDetachedFromWindow() is called,
			//cleaning up all its fields and thus, forcing us to recreate it before adding it back again...
			panelSettings.removeView(optFillThreshold);
			if (MediaContext.useOpenSLEngine)
				panelSettings.addView(optFillThreshold = createOptFillThreshold(), panelSettings.indexOfChild(optResampling));
		} else if (lastMenuView == optFileBufferSize) {
			Player.filePrefetchSize = Player.getFilePrefetchSizeFromOptions(item.getItemId());
			optFileBufferSize.setSecondaryText(getFileBufferSizeString(Player.filePrefetchSize));
		}
		configsChanged = true;
		return true;
	}

	private String getBytesBeforeDecodingString(int index) {
		return (Player.getBytesBeforeDecoding(index) >> 10) + " KiB";
	}

	private String getSecondsBeforePlaybackString(int index) {
		final int ms = Player.getMSBeforePlayback(index);
		final String fmt = UI.formatIntAsFloat(ms / 100, false, true) + " " + ((ms == 1000) ? getText(R.string.second) : getText(R.string.seconds));
		return ((index == 1) ? (fmt + " (" + getText(R.string.recommended) + ")") : fmt);
	}

	private String getMinuteString(int minutes) {
		if (minutes <= 0)
			return getText(R.string.never).toString();
		if (minutes == 1)
			return "1 " + getText(R.string.minute).toString();
		return minutes + " " + getText(R.string.minutes).toString();
	}
	
	private String getAutoTurnOffString() {
		return getMinuteString(Player.getTurnOffTimerMinutesLeft());
	}
	
	private String getAutoIdleTurnOffString() {
		return getMinuteString(Player.getIdleTurnOffTimerMinutesLeft());
	}
	
	private String getVolumeString() {
		switch (Player.volumeControlType) {
		case Player.VOLUME_CONTROL_DB:
			return getText(R.string.volume_control_type_decibels).toString();
		case Player.VOLUME_CONTROL_PERCENT:
			return getText(R.string.volume_control_type_percentage).toString();
		case Player.VOLUME_CONTROL_NONE:
			return getText(R.string.noneM).toString();
		default:
			return getText(R.string.volume_control_type_integrated).toString();
		}
	}
	
	private String getExtraInfoModeString(int extraMode) {
		switch (extraMode) {
		case Song.EXTRA_ARTIST:
			return getText(R.string.artist).toString();
		case Song.EXTRA_ALBUM:
			return getText(R.string.album).toString();
		case Song.EXTRA_TRACK_ARTIST:
			return getText(R.string.track) + "/" + getText(R.string.artist);
		case Song.EXTRA_TRACK_ALBUM:
			return getText(R.string.track) + "/" + getText(R.string.album);
		case Song.EXTRA_TRACK_ARTIST_ALBUM:
			return getText(R.string.track) + "/" + getText(R.string.artist) + "/" + getText(R.string.album);
		default:
			return getText(R.string.artist) + "/" + getText(R.string.album);
		}
	}
	
	private String getOrientationString() {
		final int o = UI.forcedOrientation;
		return getText((o == 0) ? R.string.none : ((o < 0) ? R.string.landscape : R.string.portrait)).toString();
	}
	
	private String getFadeInString(int duration) {
		return getText((duration >= 250) ? R.string.dshort : ((duration >= 125) ? R.string.dmedium : ((duration > 0) ? R.string.dlong : R.string.none))).toString();
	}
	
	private String getScrollBarString(int scrollBarType) {
		return getText(((scrollBarType == BgListView.SCROLLBAR_INDEXED) ? R.string.indexed_if_possible : ((scrollBarType == BgListView.SCROLLBAR_LARGE) ? R.string.large : ((scrollBarType == BgListView.SCROLLBAR_NONE) ? R.string.none : R.string.system_integrated)))).toString();
	}

	private String getFramesToSkipString() {
		return Integer.toString(Player.getBluetoothVisualizerFramesPerSecond(Player.getBluetoothVisualizerFramesToSkipIndex()));
	}

	private String getSizeString() {
		return Integer.toString(1 << (2 + Player.getBluetoothVisualizerSize()));
	}

	private String getSpeedString() {
		return Integer.toString(3 - Player.getBluetoothVisualizerSpeed());
	}

	private String getHeadsetHookString(int pressCount) {
		switch (Player.getHeadsetHookAction(pressCount)) {
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			return getText(R.string.play) + "/" + getText(R.string.pause);
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			return getText(R.string.next).toString();
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			return getText(R.string.previous).toString();
		}
		return getText(R.string.nothing).toString();
	}

	private String getBufferSizeString(int bufferConfig) {
		final int ms;
		boolean recommended = false;
		switch ((bufferConfig & Player.BUFFER_SIZE_MASK)) {
		case Player.BUFFER_SIZE_500MS:
			ms = 500;
			break;
		case Player.BUFFER_SIZE_1500MS:
			ms = 1500;
			break;
		case Player.BUFFER_SIZE_2000MS:
			ms = 2000;
			break;
		case Player.BUFFER_SIZE_2500MS:
			ms = 2500;
			break;
		default:
			recommended = true;
			ms = 1000;
			break;
		}
		final String fmt = UI.formatIntAsFloat(ms / 100, false, true) + " " + ((ms == 1000) ? getText(R.string.second) : getText(R.string.seconds));
		return (recommended ? (fmt + " (" + getText(R.string.recommended) + ")") : fmt);
	}

	private String getFillThresholdString(int bufferConfig) {
		switch ((bufferConfig & Player.FILL_THRESHOLD_MASK)) {
		case Player.FILL_THRESHOLD_25:
			return "25%";
		case Player.FILL_THRESHOLD_50:
			return "50%";
		case Player.FILL_THRESHOLD_75:
			return "75%";
		}
		return "100%";
	}

	private String getPlaybackEngineString(boolean useOpenSLEngine) {
		return (useOpenSLEngine ? "OpenSL ES" : "AudioTrack");
	}

	private String getFileBufferSizeString(int fileBufferSize) {
		return ((fileBufferSize == 0) ? getText(R.string.noneM).toString() : (fileBufferSize >>> 10) + " KiB");
	}

	@SuppressWarnings("deprecation")
	private void prepareHeader(TextView hdr) {
		hdr.setMaxLines(2);
		hdr.setEllipsize(TruncateAt.END);
		hdr.setPadding(UI.controlMargin, UI.controlMargin, UI.controlMargin, UI.controlMargin);
		UI.largeText(hdr);
		hdr.setTextColor(UI.colorState_text_highlight_static);
		hdr.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
	}
	
	private void addHeader(Context ctx, int resId, SettingView previousControl, int index) {
		final TextView hdr = new TextView(ctx);
		hdr.setText(resId);
		hdr.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		prepareHeader(hdr);
		headers[index] = hdr;
		hdr.setTag(index);
		panelSettings.addView(hdr);
		if (previousControl != null) {
			if (UI.is3D) {
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)previousControl.getLayoutParams();
				if (params == null)
					params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				params.setMargins(0, 0, 0, UI.controlSmallMargin);
				previousControl.setLayoutParams(params);
			} else {
				previousControl.setHidingSeparator(true);
			}
		}
	}

	private void addOption(SettingView view) {
		if (firstViewAdded == null)
			firstViewAdded = view;
		lastViewAdded = view;
		panelSettings.addView(view);
		view.setOnClickListener(this);
		view.setNextFocusLeftId(R.id.btnAbout);
		view.setNextFocusRightId(R.id.btnGoBack);
	}

	private boolean cancelGoBack() {
		if (mode == MODE_COLOR && changed) {
			checkingReturn = true;
			final BgDialog dialog = new BgDialog(getHostActivity(), UI.createDialogView(getHostActivity(), getText(R.string.discard_theme)), this);
			dialog.setTitle(R.string.oops);
			dialog.setPositiveButton(R.string.ok);
			dialog.setNegativeButton(R.string.cancel);
			dialog.show();
			return true;
		}
		return false;
	}
	
	private int validate() {
		//hard = -1
		//impossible = -2
		final int color_list = colorViews[UI.IDX_COLOR_LIST].getColor();
		final int color_text_listitem = colorViews[UI.IDX_COLOR_TEXT_LISTITEM].getColor();
		final int color_window = colorViews[UI.IDX_COLOR_WINDOW].getColor();
		final int color_text = colorViews[UI.IDX_COLOR_TEXT].getColor();
		final int color_selected_grad_lt = colorViews[UI.IDX_COLOR_SELECTED_GRAD_LT].getColor();
		final int color_selected_grad_dk = colorViews[UI.IDX_COLOR_SELECTED_GRAD_DK].getColor();
		final int color_text_selected = colorViews[UI.IDX_COLOR_TEXT_SELECTED].getColor();
		final int color_menu = colorViews[UI.IDX_COLOR_MENU].getColor();
		final int color_text_menu = colorViews[UI.IDX_COLOR_TEXT_MENU].getColor();
		final int color_highlight = colorViews[UI.IDX_COLOR_HIGHLIGHT].getColor();
		final int color_text_highlight = colorViews[UI.IDX_COLOR_TEXT_HIGHLIGHT].getColor();
		final int color_text_listitem_secondary = colorViews[UI.IDX_COLOR_TEXT_LISTITEM_SECONDARY].getColor();
		final double crList = ColorUtils.contrastRatio(color_list, color_text_listitem);
		final double crWindow = ColorUtils.contrastRatio(color_window, color_text);
		final double crSel = ColorUtils.contrastRatio(ColorUtils.blend(color_selected_grad_lt, color_selected_grad_dk, 0.5f), color_text_selected);
		final double crMenu = ColorUtils.contrastRatio(color_menu, color_text_menu);
		if (crList < MIN_THRESHOLD || crWindow < MIN_THRESHOLD || crSel < MIN_THRESHOLD || crMenu < MIN_THRESHOLD)
			return -2;
		if (crList < 6.5 || crWindow < 6.5 || crSel < 6.5 || crMenu < 6.5)
			return -1;
		//these colors are considered nonessential, therefore their thresholds are lower,
		//and only warnings are generated when they are violated
		final double crHighlight = ColorUtils.contrastRatio(color_highlight, color_text_highlight);
		final double crListSecondary = ColorUtils.contrastRatio(color_list, color_text_listitem_secondary);
		if (crHighlight < 5 || crListSecondary < 5)
			return -1;
		return 0;
	}
	
	private void applyTheme(View sourceView) {
		final byte[] colors = UI.serializeThemeToArray();
		for (int i = 0; i < colorViews.length; i++)
			UI.serializeThemeColor(colors, i * 3, colorViews[i].getColor());
		UI.customColors = colors;
		UI.setTheme(getHostActivity(), UI.THEME_CUSTOM);
		changed = false;
		finish(0, sourceView, true);
	}
	
	private void loadColors(boolean createControls, boolean forceCurrent) {
		final Context ctx = getHostActivity();
		final int[] colorOrder = new int[] {
				UI.IDX_COLOR_DIVIDER,
				UI.IDX_COLOR_TEXT_HIGHLIGHT,
				UI.IDX_COLOR_HIGHLIGHT,
				UI.IDX_COLOR_TEXT,
				UI.IDX_COLOR_WINDOW,
				UI.IDX_COLOR_CONTROL_MODE,
				UI.IDX_COLOR_VISUALIZER,
				UI.IDX_COLOR_TEXT_LISTITEM,
				UI.IDX_COLOR_TEXT_LISTITEM_DISABLED,
				UI.IDX_COLOR_TEXT_LISTITEM_SECONDARY,
				UI.IDX_COLOR_LIST,
				UI.IDX_COLOR_TEXT_MENU,
				UI.IDX_COLOR_MENU_BORDER,
				UI.IDX_COLOR_MENU_ICON,
				UI.IDX_COLOR_MENU,
				UI.IDX_COLOR_TEXT_SELECTED,
				UI.IDX_COLOR_SELECTED_BORDER,
				UI.IDX_COLOR_SELECTED_GRAD_LT,
				UI.IDX_COLOR_SELECTED_GRAD_DK,
				UI.IDX_COLOR_SELECTED_PRESSED,
				UI.IDX_COLOR_FOCUSED_BORDER,
				UI.IDX_COLOR_FOCUSED_GRAD_LT,
				UI.IDX_COLOR_FOCUSED_GRAD_DK,
				UI.IDX_COLOR_FOCUSED_PRESSED };
		final byte[] colors = ((UI.customColors != null && UI.customColors.length >= 72 && !forceCurrent) ? UI.customColors : UI.serializeThemeToArray());
		if (createControls)
			colorViews = new SettingView[colorOrder.length];
		for (int idx : colorOrder) {
			if (createControls)
				colorViews[idx] = new SettingView(ctx, null, UI.getThemeColorDescription(idx).toString(), null, false, false, true);
			colorViews[idx].setColor(UI.deserializeThemeColor(colors, idx * 3));
		}
		if (createControls) {
			optLoadCurrentTheme = new SettingView(ctx, UI.ICON_THEME, getText(R.string.load_colors_from_current_theme).toString(), null, false, false, false);
			int hIdx = 0;
			headers = new TextView[6];
			addHeader(ctx, R.string.color_theme, optLoadCurrentTheme, hIdx++);
			addOption(optLoadCurrentTheme);
			for (int i = 0; i < colorOrder.length; i++) {
				final int idx = colorOrder[i];
				switch (i) {
				case 0:
					addHeader(ctx, R.string.general, optLoadCurrentTheme, hIdx++);
					break;
				case 7:
					addHeader(ctx, R.string.list2, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				case 11:
					addHeader(ctx, R.string.menu, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				case 15:
					addHeader(ctx, R.string.selection, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				case 20:
					addHeader(ctx, R.string.keyboard_focus, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				}
				addOption(colorViews[idx]);
			}
			lblTitle.setVisibility(View.GONE);
			currentHeader = -1;
		}
	}

	private SettingView createOptFillThreshold() {
		return new SettingView(getHostActivity(), UI.ICON_PERCENTAGE, getText(R.string.percentage_to_decode_before_playback).toString(), getFillThresholdString(Player.getBufferConfig()), false, false, false);
	}

	@Override
	public boolean onBackPressed() {
		return (!isLayoutCreated() || cancelGoBack());
	}

	private void setListPadding() {
		//for lblTitle to look nice, we must have no paddings
		if (viewForPadding != null)
			UI.prepareViewPaddingBasedOnScreenWidth(viewForPadding, 0, 0, 0);
		if (lblTitle != null) {
			final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)lblTitle.getLayoutParams();
			lp.leftMargin = UI.getViewPaddingBasedOnScreenWidth(0);
			lp.rightMargin = lp.leftMargin;
			lblTitle.setLayoutParams(lp);
		}
	}

	@Override
	public void onPluginCreated(int id, FPlayPlugin plugin) {
		if (plugin == null || !isLayoutCreated() || Player.state != Player.STATE_ALIVE)
			return;

		switch (mode) {
		case MODE_BLUETOOTH:
			if (id != 1)
				break;
			WirelessVisualizer.create(plugin, getHostActivity(), startTransmissionOnConnection);
			refreshBluetoothStatus(false, false);
			break;
		case MODE_HTTP:
			if (id != 2)
				break;
			if (!HttpTransmitter.create(plugin, getHostActivity()) && Player.httpTransmitterLastErrorMessage == null)
				Player.httpTransmitterLastErrorMessage = getText(R.string.transmission_error).toString();
			refreshHttpStatus(false, false);
			break;
		}
	}

	private void startBluetoothVisualizer(boolean startTransmissionOnConnection) {
		this.startTransmissionOnConnection = startTransmissionOnConnection;
		PluginManager.createPlugin(getHostActivity(), WirelessVisualizer.PLUGIN_CLASS, WirelessVisualizer.PLUGIN_PACKAGE, WirelessVisualizer.PLUGIN_NAME, 1, this);
	}

	private void refreshBluetoothStatus(boolean postAgain, boolean ignoreLayoutStatus) {
		if ((!ignoreLayoutStatus && !isLayoutCreated()) || optBtMessage == null)
			return;
		if (Player.bluetoothVisualizerLastErrorMessage != null) {
			btErrorMessage = Player.bluetoothVisualizerLastErrorMessage;
			Player.bluetoothVisualizerLastErrorMessage = null;
			if (Player.backgroundMonitor != null)
				Player.backgroundMonitor.backgroundMonitorBluetoothEnded();
		}
		if (Player.bluetoothVisualizer != null) {
			btErrorMessage = null;
			if (Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING) {
				if (btMessageText != R.string.bt_scanning) {
					btMessageText = R.string.bt_scanning;
					optBtMessage.setText(getText(R.string.bt_scanning).toString());
				}
			} else {
				btMessageText = 0;
				optBtMessage.setText(getText(R.string.bt_packets_sent).toString() + " " + ((WirelessVisualizer)Player.bluetoothVisualizer).getSentPackets());
			}
		} else {
			if (btErrorMessage != null) {
				if (btMessageText != Integer.MAX_VALUE) {
					btMessageText = Integer.MAX_VALUE;
					optBtMessage.setText(UI.emoji(btErrorMessage));
				}
			} else if (btMessageText != R.string.bt_inactive) {
				btMessageText = R.string.bt_inactive;
				optBtMessage.setText(getText(R.string.bt_inactive).toString());
			}
		}
		if (optBtSize != null && optBtSizeLastSize != Player.getBluetoothVisualizerSize()) {
			optBtSizeLastSize = Player.getBluetoothVisualizerSize();
			optBtSize.setSecondaryText(getSizeString());
		}
		if (optBtConnect != null && optBtStart != null) {
			switch (Player.bluetoothVisualizerState) {
			case Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED:
			case Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING:
				if (btConnectText != R.string.bt_disconnect) {
					btConnectText = R.string.bt_disconnect;
					optBtConnect.setText(getText(R.string.bt_disconnect).toString());
				}
				if (Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING) {
					if (btStartText != R.string.bt_stop) {
						btStartText = R.string.bt_stop;
						optBtStart.setText(getText(R.string.bt_stop).toString());
						optBtStart.setIcon(UI.ICON_PAUSE);
					}
				} else {
					if (btStartText != R.string.bt_start) {
						btStartText = R.string.bt_start;
						optBtStart.setText(getText(R.string.bt_start).toString());
						optBtStart.setIcon(UI.ICON_PLAY);
					}
				}
				break;
			default:
				if (btConnectText != R.string.bt_connect) {
					btConnectText = R.string.bt_connect;
					optBtConnect.setText(getText(R.string.bt_connect).toString());
				}
				if (btStartText != R.string.bt_start) {
					btStartText = R.string.bt_start;
					optBtStart.setText(getText(R.string.bt_start).toString());
					optBtStart.setIcon(UI.ICON_PLAY);
				}
				break;
			}
		}
		if (postAgain)
			MainHandler.sendMessageAtTime(this, MSG_REFRESH_BLUETOOTH, 0, 0, SystemClock.uptimeMillis() + 1000);
	}

	private void startHttpTransmitter() {
		if (!Player.isConnectedToTheInternet()) {
			Player.httpTransmitterLastErrorMessage = getText(R.string.error_connection).toString();
			refreshHttpStatus(false, false);
			return;
		}

		if (!Player.isInternetConnectedViaWiFi()) {
			Player.httpTransmitterLastErrorMessage = getText(R.string.error_wifi).toString();
			refreshHttpStatus(false, false);
			return;
		}

		try {
			PluginManager.createPlugin(getHostActivity(), HttpTransmitter.PLUGIN_CLASS, HttpTransmitter.PLUGIN_PACKAGE, HttpTransmitter.PLUGIN_NAME, 2, this);
		} catch (Throwable ex) {
			Player.httpTransmitterLastErrorMessage = getText(!Player.isInternetConnectedViaWiFi() ?
				R.string.error_wifi :
				(!Player.isConnectedToTheInternet() ?
					R.string.error_connection :
					R.string.error_gen)).toString();
			refreshHttpStatus(false, false);
		}
	}

	private void openHttpTransmitter() {
		if (Player.httpTransmitter != null && httpAccessCode != null) {
			try {
				getHostActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://fplay.com.br?" + httpAccessCode)));
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	private void refreshHttpStatus(boolean postAgain, boolean ignoreLayoutStatus) {
		if ((!ignoreLayoutStatus && !isLayoutCreated()) || optBtMessage == null)
			return;
		if (Player.httpTransmitterLastErrorMessage != null) {
			btErrorMessage = Player.httpTransmitterLastErrorMessage;
			Player.httpTransmitterLastErrorMessage = null;
			if (Player.backgroundMonitor != null)
				Player.backgroundMonitor.backgroundMonitorHttpEnded();
		}
		if (Player.httpTransmitter != null) {
			btErrorMessage = null;
			if (btMessageText != Integer.MAX_VALUE - 1) {
				btMessageText = Integer.MAX_VALUE - 1;
				optBtMessage.setText(UI.format(R.string.transmission_details,
					((HttpTransmitter)Player.httpTransmitter).getAddress(),
					httpAccessCode = ((HttpTransmitter)Player.httpTransmitter).getEncodedAddress()));
				if (openHttpTransmitterOnConnection) {
					openHttpTransmitterOnConnection = false;
					openHttpTransmitter();
				}
			}
		} else {
			httpAccessCode = null;
			if (btErrorMessage != null) {
				if (btMessageText != Integer.MAX_VALUE) {
					btMessageText = Integer.MAX_VALUE;
					optBtMessage.setText(UI.emoji(btErrorMessage));
				}
			} else if (btMessageText != R.string.transmission_inactive) {
				btMessageText = R.string.transmission_inactive;
				optBtMessage.setText(getText(R.string.transmission_inactive).toString());
			}
		}
		if (optBtConnect != null) {
			if (Player.httpTransmitter == null) {
				if (btConnectText != R.string.bt_start) {
					btConnectText = R.string.bt_start;
					optBtConnect.setText(getText(R.string.bt_start).toString());
					optBtConnect.setIcon(UI.ICON_PLAY);
				}
			} else {
				if (btConnectText != R.string.bt_stop) {
					btConnectText = R.string.bt_stop;
					optBtConnect.setText(getText(R.string.bt_stop).toString());
					optBtConnect.setIcon(UI.ICON_PAUSE);
				}
			}
		}
		if (postAgain)
			MainHandler.sendMessageAtTime(this, MSG_REFRESH_HTTP, 0, 0, SystemClock.uptimeMillis() + 1000);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (mode != MODE_BLUETOOTH || optBtConnect == null)
			return;
		if (requestCode == WirelessVisualizer.REQUEST_ENABLE && resultCode == Activity.RESULT_OK)
			onClick(startTransmissionOnConnection ? optBtStart : optBtConnect);
	}

	@SuppressWarnings({ "deprecation", "PointlessBooleanExpression", "ConstantConditions" })
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_settings);
		btnGoBack = findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnBluetooth = findViewById(R.id.btnBluetooth);
		btnBluetooth.setOnClickListener(this);
		btnAbout = findViewById(R.id.btnAbout);
		btnAbout.setOnClickListener(this);
		boolean showBluetooth = false;
		if (mode == MODE_COLOR) {
			btnAbout.setText(R.string.apply_theme);
		} else if (mode == MODE_BLUETOOTH) {
			btnAbout.setText(R.string.tutorial);
			btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, UI.color_text), null, null, null);
		} else if (mode == MODE_HTTP) {
			btnAbout.setText(R.string.listen_online);
			btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, UI.color_text), null, null, null);
		} else {
			try {
				showBluetooth = (BluetoothAdapter.getDefaultAdapter() != null);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			if (showBluetooth) {
				btnBluetooth.setIcon(UI.ICON_BLUETOOTH);
				final TextView sep = findViewById(R.id.sep);
				final RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
				rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
				rp.addRule(RelativeLayout.LEFT_OF, R.id.btnAbout);
				rp.leftMargin = UI.controlMargin;
				rp.rightMargin = UI.controlMargin;
				sep.setLayoutParams(rp);
				sep.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
				sep.setVisibility(View.VISIBLE);
				btnBluetooth.setVisibility(View.VISIBLE);
			}
			btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_INFORMATION, UI.color_text), null, null, null);
		}

		btnGoBack.setNextFocusDownId(2);
		btnBluetooth.setNextFocusDownId(2);
		btnAbout.setNextFocusDownId(2);
		btnAbout.setNextFocusRightId(2);
		UI.setNextFocusForwardId(btnAbout, 2);

		btnGoBack.setNextFocusUpId(3);
		btnBluetooth.setNextFocusUpId(3);
		btnAbout.setNextFocusUpId(3);
		btnGoBack.setNextFocusLeftId(3);

		if (!showBluetooth) {
			UI.setNextFocusForwardId(btnGoBack, R.id.btnAbout);
			btnGoBack.setNextFocusRightId(R.id.btnAbout);
			btnAbout.setNextFocusLeftId(R.id.btnGoBack);
		} else {
			UI.setNextFocusForwardId(btnGoBack, R.id.btnBluetooth);
			btnGoBack.setNextFocusRightId(R.id.btnBluetooth);
			btnAbout.setNextFocusLeftId(R.id.btnBluetooth);
		}

		lastColorView = -1;
		
		final Context ctx = getHostActivity();

		list = findViewById(R.id.list);
		list.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
		panelControls = findViewById(R.id.panelControls);
		panelSettings = findViewById(R.id.panelSettings);
		lblTitle = findViewById(R.id.lblTitle);
		prepareHeader(lblTitle);

		viewForPadding = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? list : panelSettings);
		setListPadding();

		list.setOnScrollListener(this);
		if (mode == MODE_COLOR) {
			loadColors(true, false);
		} else if (mode == MODE_BLUETOOTH) {
			optBtSizeLastSize = Player.getBluetoothVisualizerSize();
			optBtMessage = new SettingView(ctx, UI.ICON_INFORMATION, "", null, false, false, false);
			optBtConnect = new SettingView(ctx, UI.ICON_BLUETOOTH, "", null, false, false, false);
			optBtStart = new SettingView(ctx, Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING ? UI.ICON_PAUSE : UI.ICON_PLAY, "", null, false, false, false);
			optBtFramesToSkip = new SettingView(ctx, UI.ICON_CLOCK, getText(R.string.bt_fps).toString(), getFramesToSkipString(), false, false, false);
			optBtSize = new SettingView(ctx, UI.ICON_VISUALIZER24, getText(R.string.bt_sample_count).toString(), getSizeString(), false, false, false);
			optBtVUMeter = new SettingView(ctx, UI.ICON_VISUALIZER24, getText(R.string.bt_vumeter).toString(), null, true, Player.isBluetoothUsingVUMeter(), false);
			optBtSpeed = new SettingView(ctx, UI.ICON_VISUALIZER24, getText(R.string.sustain).toString(), getSpeedString(), false, false, false);
			refreshBluetoothStatus(true, true);

			headers = new TextView[3];
			addHeader(ctx, R.string.information, optBtMessage, 0);
			addOption(optBtMessage);
			addHeader(ctx, R.string.general, optBtMessage, 1);
			addOption(optBtConnect);
			addOption(optBtStart);
			addHeader(ctx, R.string.settings, optBtStart, 2);
			//addOption(optBtVUMeter);
			addOption(optBtFramesToSkip);
			addOption(optBtSize);
			addOption(optBtSpeed);
			currentHeader = -1;
		} else if (mode == MODE_HTTP) {
			optBtMessage = new SettingView(ctx, UI.ICON_INFORMATION, "", null, false, false, false);
			optBtConnect = new SettingView(ctx, Player.httpTransmitter != null ? UI.ICON_PAUSE : UI.ICON_PLAY, "", null, false, false, false);
			optBtStart = new SettingView(ctx, UI.ICON_REPEAT24, getText(R.string.refresh_list).toString(), null, false, false, false);
			refreshHttpStatus(true, true);

			headers = new TextView[2];
			addHeader(ctx, R.string.information, optBtMessage, 0);
			addOption(optBtMessage);
			addHeader(ctx, R.string.general, optBtMessage, 1);
			addOption(optBtConnect);
			addOption(optBtStart);
			currentHeader = -1;
		} else {
			if (!UI.dyslexiaFontSupportsCurrentLocale()) {
				optUseAlternateTypeface = new SettingView(ctx, UI.ICON_DYSLEXIA, getText(R.string.opt_use_alternate_typeface).toString(), null, true, UI.isUsingAlternateTypeface, false);
			}
			optAutoTurnOff = new SettingView(ctx, UI.ICON_CLOCK, getText(R.string.opt_auto_turn_off).toString(), getAutoTurnOffString(), false, false, false);
			optAutoIdleTurnOff = new SettingView(ctx, UI.ICON_CLOCK, getText(R.string.opt_auto_idle_turn_off).toString(), getAutoIdleTurnOffString(), false, false, false);
			optAutoTurnOffPlaylist = new SettingView(ctx, UI.ICON_REPEATNONE, getText(R.string.opt_auto_turn_off_playlist).toString(), null, true, Player.turnOffWhenPlaylistEnds, false);
			optKeepScreenOn = new SettingView(ctx, UI.ICON_SCREEN, getText(R.string.opt_keep_screen_on).toString(), null, true, UI.keepScreenOn, false);
			optTheme = new SettingView(ctx, UI.ICON_THEME, getText(R.string.color_theme).toString() + UI.collonNoSpace(), UI.getThemeString(UI.theme), false, false, false);
			optFlat = new SettingView(ctx, UI.ICON_FLAT, getText(R.string.flat_details).toString(), null, true, UI.isFlat, false);
			optBorders = new SettingView(ctx, UI.ICON_TRANSPARENT, getText(R.string.borders).toString(), null, true, UI.hasBorders, false);
			optAlbumArtSongList = new SettingView(ctx, UI.ICON_ALBUMART, getText(R.string.album_art).toString(), null, true, UI.albumArtSongList, false);
			optPlayWithLongPress = new SettingView(ctx, UI.ICON_PLAY, getText(R.string.play_with_long_press).toString(), null, true, UI.playWithLongPress, false);
			optExpandSeekBar = new SettingView(ctx, UI.ICON_SEEKBAR, getText(R.string.expand_seek_bar).toString(), null, true, UI.expandSeekBar, false);
			optVolumeControlType = new SettingView(ctx, UI.ICON_VOLUME4, getText(R.string.opt_volume_control_type).toString(), getVolumeString(), false, false, false);
			optDoNotAttenuateVolume = new SettingView(ctx, UI.ICON_INFORMATION, getText(R.string.opt_do_not_attenuate_volume).toString(), null, true, Player.doNotAttenuateVolume, false);
			opt3D = new SettingView(ctx, UI.ICON_DIVIDER, "3D", null, true, UI.is3D, false);
			optIsDividerVisible = new SettingView(ctx, UI.ICON_DIVIDER, getText(R.string.opt_is_divider_visible).toString(), null, true, UI.isDividerVisible, false);
			optIsVerticalMarginLarge = new SettingView(ctx, UI.ICON_SPACELIST, getText(R.string.opt_is_vertical_margin_large).toString(), null, true, UI.isVerticalMarginLarge, false);
			optExtraSpacing = new SettingView(ctx, UI.ICON_SPACEHEADER, getText(R.string.opt_extra_spacing).toString(), null, true, UI.extraSpacing, false);
			if (!UI.isLargeScreen) {
				optPlaceTitleAtTheBottom = new SettingView(ctx, UI.ICON_SPACEHEADER, getText(R.string.place_title_at_the_bottom).toString(), null, true, UI.placeTitleAtTheBottom, false);
				optPlaceControlsAtTheBottom = new SettingView(ctx, UI.ICON_SPACEHEADER, getText(R.string.place_controls_at_the_bottom).toString(), null, true, UI.placeControlsAtTheBottom, false);
			}
			optForcedLocale = new SettingView(ctx, UI.ICON_LANGUAGE, getText(R.string.opt_language).toString(), UI.getLocaleDescriptionFromCode(UI.forcedLocale), false, false, false);
			optLargeTextIs22sp = new SettingView(ctx, UI.ICON_LARGETEXTIS22SP, getText(R.string.larger_text_size).toString(), null, true, UI.largeTextIs22sp, false);
			if (UI.isLargeScreen)
				optPlacePlaylistToTheRight = new SettingView(ctx, UI.ICON_HAND, getText(R.string.place_the_playlist_to_the_right).toString(), null, true, UI.controlsToTheLeft, false);
			optScrollBarToTheLeft = new SettingView(ctx, UI.ICON_HAND, getText(R.string.scrollbar_to_the_left).toString(), null, true, UI.scrollBarToTheLeft, false);
			optScrollBarSongList = new SettingView(ctx, UI.ICON_SCROLLBAR, getText(R.string.scrollbar_playlist).toString(), getScrollBarString(UI.songListScrollBarType), false, false, false);
			optScrollBarBrowser = new SettingView(ctx, UI.ICON_SCROLLBAR, getText(R.string.scrollbar_browser_type).toString(), getScrollBarString(UI.browserScrollBarType), false, false, false);
			optWidgetTransparentBg = new SettingView(ctx, UI.ICON_TRANSPARENT, getText(R.string.transparent_background).toString(), null, true, UI.widgetTransparentBg, false);
			optWidgetTextColor = new SettingView(ctx, UI.ICON_PALETTE, getText(R.string.text_color).toString(), null, false, false, true);
			optWidgetTextColor.setColor(UI.widgetTextColor);
			optWidgetIconColor = new SettingView(ctx, UI.ICON_PALETTE, getText(R.string.icon_color).toString(), null, false, false, true);
			optWidgetIconColor.setColor(UI.widgetIconColor);
			optPreviousResetsAfterTheBeginning = new SettingView(ctx, UI.ICON_PREV, getText(R.string.previous_resets_after_the_beginning).toString(), null, true, Player.previousResetsAfterTheBeginning, false);
			optHandleCallKey = new SettingView(ctx, UI.ICON_DIAL, getText(R.string.opt_handle_call_key).toString(), null, true, Player.handleCallKey, false);
			optHeadsetHook1 = new SettingView(ctx, UI.ICON_HEADSETHOOK1, getText(R.string.headset_hook_1).toString(), getHeadsetHookString(1), false, false, false);
			optHeadsetHook2 = new SettingView(ctx, UI.ICON_HEADSETHOOK2, getText(R.string.headset_hook_2).toString(), getHeadsetHookString(2), false, false, false);
			optHeadsetHook3 = new SettingView(ctx, UI.ICON_HEADSETHOOK3, getText(R.string.headset_hook_3).toString(), getHeadsetHookString(3), false, false, false);
			if (ExternalFx.isSupported())
				optExternalFx = new SettingView(ctx, UI.ICON_EQUALIZER, getText(R.string.enable_external_fx) + " " + getText(R.string.external_fx_warning), null, true, ExternalFx.isEnabled(), false);
			optFileBufferSize = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.file_prefetch_size).toString(), getFileBufferSizeString(Player.filePrefetchSize), false, false, false);
			optPlayWhenHeadsetPlugged = new SettingView(ctx, UI.ICON_EARPHONE, getText(R.string.opt_play_when_headset_plugged).toString(), null, true, Player.playWhenHeadsetPlugged, false);
			optBlockBackKey = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_block_back_key).toString(), null, true, UI.blockBackKey, false);
			optBackKeyAlwaysReturnsToPlayerWhenBrowsing = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_back_key_always_returns_to_player_when_browsing).toString(), null, true, UI.backKeyAlwaysReturnsToPlayerWhenBrowsing, false);
			optWrapAroundList = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_wrap_around_list).toString(), null, true, UI.wrapAroundList, false);
			optDoubleClickMode = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_double_click_mode).toString(), null, true, UI.doubleClickMode, false);
			optMarqueeTitle = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_marquee_title).toString(), null, true, UI.marqueeTitle, false);
			optPrepareNext = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_prepare_next).toString(), null, true, Player.nextPreparationEnabled, false);
			optClearListWhenPlayingFolders = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_clear_list_when_playing_folders).toString(), null, true, Player.clearListWhenPlayingFolders, false);
			optGoBackWhenPlayingFolders = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_go_back_when_playing_folders).toString(), null, true, Player.goBackWhenPlayingFolders, false);
			optExtraInfoMode = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.secondary_line_of_text).toString(), getExtraInfoModeString(Song.extraInfoMode), false, false, false);
			optDisplaySongNumberAndCount = new SettingView(ctx, UI.ICON_NUMBER, getText(R.string.display_song_number_and_count).toString(), null, true, UI.displaySongNumberAndCount, false);
			optAllowLockScreen = new SettingView(ctx, UI.ICON_SCREEN, getText(R.string.allow_lock_screen).toString(), null, true, UI.allowPlayerAboveLockScreen, false);
			optForceOrientation = new SettingView(ctx, UI.ICON_ORIENTATION, getText(R.string.opt_force_orientation).toString(), getOrientationString(), false, false, false);
			optTransition = new SettingView(ctx, UI.ICON_TRANSITION, getText(R.string.transition).toString(), UI.getTransitionString(UI.transitions & 0xFF), false, false, false);
			optPopupTransition = new SettingView(ctx, UI.ICON_TRANSITION, getText(R.string.transition_popup).toString(), UI.getTransitionString(UI.transitions >>> 8), false, false, false);
			optAnimations = new SettingView(ctx, UI.ICON_TRANSITION, getText(R.string.animations).toString(), null, true, UI.animationEnabled, false);
			optNotFullscreen = new SettingView(ctx, UI.ICON_FULLSCREEN, getText(R.string.fullscreen).toString(), null, true, !UI.notFullscreen, false);
			optFadeInFocus = new SettingView(ctx, UI.ICON_FADE, getText(R.string.opt_fade_in_focus).toString(), getFadeInString(Player.fadeInIncrementOnFocus), false, false, false);
			optFadeInPause = new SettingView(ctx, UI.ICON_FADE, getText(R.string.opt_fade_in_pause).toString(), getFadeInString(Player.fadeInIncrementOnPause), false, false, false);
			optFadeInOther = new SettingView(ctx, UI.ICON_FADE, getText(R.string.opt_fade_in_other).toString(), getFadeInString(Player.fadeInIncrementOnOther), false, false, false);
			optAnnounceCurrentSong = new SettingView(ctx, UI.ICON_MIC, getText(R.string.announce_current_song).toString(), null, true, Player.announceCurrentSong, false);
			optFollowCurrentSong = new SettingView(ctx, UI.ICON_SCROLLBAR, getText(R.string.follow_current_song).toString(), null, true, Player.followCurrentSong, false);
			optBytesBeforeDecoding = new SettingView(ctx, UI.ICON_RADIO, getText(R.string.bytes_before_decoding).toString(), getBytesBeforeDecodingString(Player.getBytesBeforeDecodingIndex()), false, false, false);
			if (!BuildConfig.X) {
				optMSBeforePlayback = new SettingView(ctx, UI.ICON_RADIO, getText(R.string.seconds_before_playback).toString(), getSecondsBeforePlaybackString(Player.getMSBeforePlaybackIndex()), false, false, false);
			} else {
				optBufferSize = new SettingView(ctx, UI.ICON_PLAY, getText(R.string.playback_buffer_length).toString(), getBufferSizeString(Player.getBufferConfig()), false, false, false);
				optFillThreshold = createOptFillThreshold();
				optPlaybackEngine = new SettingView(ctx, UI.ICON_FPLAY, getText(R.string.playback_engine).toString(), getPlaybackEngineString(MediaContext.useOpenSLEngine), false, false, false);
				optResampling = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.resample_track_to_native).toString(), null, true, Player.isResamplingEnabled(), false);
			}

			int hIdx = 0;
			headers = new TextView[BuildConfig.X ? 9 : ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? 8 : 7)];
			addHeader(ctx, R.string.msg_turn_off_title, optAutoTurnOffPlaylist, hIdx++);
			addOption(optAutoTurnOff);
			addOption(optAutoIdleTurnOff);
			addOption(optAutoTurnOffPlaylist);
			if (BuildConfig.X) {
				addHeader(ctx, R.string.performance, optAutoTurnOffPlaylist, hIdx++);
				addOption(optPlaybackEngine);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
					addOption(optFileBufferSize);
				addOption(optBufferSize);
				if (MediaContext.useOpenSLEngine)
					addOption(optFillThreshold);
				addOption(optResampling);
				addHeader(ctx, R.string.hdr_display, optResampling, hIdx++);
			} else {
				addHeader(ctx, R.string.hdr_display, optAutoTurnOffPlaylist, hIdx++);
			}
			if (!UI.isChromebook)
				addOption(optKeepScreenOn);
			addOption(optTheme);
			addOption(opt3D);
			addOption(optFlat);
			addOption(optBorders);
			if (!UI.is3D)
				addOption(optIsDividerVisible);
			addOption(optAlbumArtSongList);
			addOption(optExtraInfoMode);
			addOption(optDisplaySongNumberAndCount);
			addOption(optAllowLockScreen);
			if (!UI.isChromebook)
				addOption(optForceOrientation);
			addOption(optTransition);
			addOption(optPopupTransition);
			addOption(optAnimations);
			if (!UI.isChromebook)
				addOption(optNotFullscreen);
			addOption(optIsVerticalMarginLarge);
			addOption(optExtraSpacing);
			if (!UI.isLargeScreen) {
				addOption(optPlaceTitleAtTheBottom);
				addOption(optPlaceControlsAtTheBottom);
			}
			if (!UI.dyslexiaFontSupportsCurrentLocale())
				addOption(optUseAlternateTypeface);
			addOption(optForcedLocale);
			addHeader(ctx, R.string.accessibility, optForcedLocale, hIdx++);
			addOption(optLargeTextIs22sp);
			if (UI.isLargeScreen)
				addOption(optPlacePlaylistToTheRight);
			addOption(optAnnounceCurrentSong);
			addOption(optScrollBarToTheLeft);
			addHeader(ctx, R.string.scrollbar, optScrollBarToTheLeft, hIdx++);
			addOption(optFollowCurrentSong);
			addOption(optScrollBarSongList);
			addOption(optScrollBarBrowser);
			addHeader(ctx, R.string.widget, optScrollBarBrowser, hIdx++);
			addOption(optWidgetTransparentBg);
			addOption(optWidgetTextColor);
			addOption(optWidgetIconColor);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				addHeader(ctx, R.string.radio, optWidgetIconColor, hIdx++);
				addOption(optBytesBeforeDecoding);
				if (!BuildConfig.X)
					addOption(optMSBeforePlayback);
			}
			addHeader(ctx, R.string.hdr_playback, (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? (BuildConfig.X ? optBytesBeforeDecoding : optMSBeforePlayback) : optWidgetIconColor, hIdx++);
			if (ExternalFx.isSupported())
				addOption(optExternalFx);
			if (!BuildConfig.X && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
				addOption(optFileBufferSize);
			addOption(optPlayWhenHeadsetPlugged);
			addOption(optPreviousResetsAfterTheBeginning);
			addOption(optHandleCallKey);
			addOption(optHeadsetHook1);
			addOption(optHeadsetHook2);
			addOption(optHeadsetHook3);
			addOption(optPlayWithLongPress);
			addOption(optExpandSeekBar);
			addOption(optVolumeControlType);
			addOption(optDoNotAttenuateVolume);
			addOption(optFadeInFocus);
			addOption(optFadeInPause);
			addOption(optFadeInOther);
			addHeader(ctx, R.string.hdr_behavior, optFadeInOther, hIdx);
			addOption(optBackKeyAlwaysReturnsToPlayerWhenBrowsing);
			addOption(optClearListWhenPlayingFolders);
			addOption(optGoBackWhenPlayingFolders);
			if (!UI.isChromebook)
				addOption(optBlockBackKey);
			addOption(optWrapAroundList);
			addOption(optDoubleClickMode);
			addOption(optMarqueeTitle);
			addOption(optPrepareNext);
			lblTitle.setVisibility(View.GONE);
			currentHeader = -1;
		}
		//weird... I know... but at least lint stops complaining!
		final int id = 0;
		firstViewAdded.setId(id + 2);
		firstViewAdded.setNextFocusUpId(R.id.btnAbout);
		firstViewAdded = null;
		lastViewAdded.setId(id + 3);
		lastViewAdded.setNextFocusDownId(R.id.btnGoBack);
		lastViewAdded = null;

		UI.prepareControlContainer(panelControls, false, true);
		btnAbout.setDefaultHeight();
	}

	@Override
	protected void onPause() {
		if (mode == MODE_NORMAL)
			Player.turnOffTimerObserver = null;
	}
	
	@Override
	protected void onResume() {
		if (mode == MODE_NORMAL) {
			Player.turnOffTimerObserver = this;
			if (optAutoTurnOff != null)
				optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
			if (optAutoIdleTurnOff != null)
				optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
		}
	}
	
	@Override
	protected void onOrientationChanged() {
		setListPadding();
	}
	
	@Override
	protected void onCleanupLayout() {
		btnGoBack = null;
		btnBluetooth = null;
		btnAbout = null;
		list = null;
		lblTitle = null;
		panelControls = null;
		panelSettings = null;
		viewForPadding = null;
		if (headers != null) {
			for (int i = headers.length - 1; i >= 0; i--)
				headers[i] = null;
			headers = null;
		}
		optLoadCurrentTheme = null;
		optUseAlternateTypeface = null;
		optAutoTurnOff = null;
		optAutoIdleTurnOff = null;
		optAutoTurnOffPlaylist = null;
		optKeepScreenOn = null;
		optTheme = null;
		optFlat = null;
		optBorders = null;
		optAlbumArtSongList = null;
		optPlayWithLongPress = null;
		optExpandSeekBar = null;
		optVolumeControlType = null;
		optDoNotAttenuateVolume = null;
		opt3D = null;
		optIsDividerVisible = null;
		optIsVerticalMarginLarge = null;
		optExtraSpacing = null;
		optPlaceTitleAtTheBottom = null;
		optPlaceControlsAtTheBottom = null;
		optForcedLocale = null;
		optLargeTextIs22sp = null;
		optPlacePlaylistToTheRight = null;
		optScrollBarToTheLeft = null;
		optScrollBarSongList = null;
		optScrollBarBrowser = null;
		optWidgetTransparentBg = null;
		optWidgetTextColor = null;
		optWidgetIconColor = null;
		optHandleCallKey = null;
		optHeadsetHook1 = null;
		optHeadsetHook2 = null;
		optHeadsetHook3 = null;
		optExternalFx = null;
		optFileBufferSize = null;
		optPlayWhenHeadsetPlugged = null;
		optBlockBackKey = null;
		optBackKeyAlwaysReturnsToPlayerWhenBrowsing = null;
		optWrapAroundList = null;
		optDoubleClickMode = null;
		optMarqueeTitle = null;
		optPrepareNext = null;
		optClearListWhenPlayingFolders = null;
		optGoBackWhenPlayingFolders = null;
		optExtraInfoMode = null;
		optDisplaySongNumberAndCount = null;
		optAllowLockScreen = null;
		optForceOrientation = null;
		optTransition = null;
		optPopupTransition = null;
		optAnimations = null;
		optNotFullscreen = null;
		optFadeInFocus = null;
		optFadeInPause = null;
		optFadeInOther = null;
		optBtMessage = null;
		optBtConnect = null;
		optBtStart = null;
		optBtFramesToSkip = null;
		optBtSize = null;
		optBtVUMeter = null;
		optBtSpeed = null;
		optAnnounceCurrentSong = null;
		optFollowCurrentSong = null;
		optBytesBeforeDecoding = null;
		optMSBeforePlayback = null;
		optBufferSize = null;
		optFillThreshold = null;
		optPlaybackEngine = null;
		optResampling = null;
		optPreviousResetsAfterTheBeginning = null;
		lastMenuView = null;
		if (colorViews != null) {
			for (int i = colorViews.length - 1; i >= 0; i--)
				colorViews[i] = null;
			colorViews = null;
		}
	}
	
	@Override
	protected void onDestroy() {
		//give some time for the transition to happen before saving the configs
		if (mode == MODE_NORMAL && configsChanged)
			MainHandler.sendMessageAtTime(this, MSG_SAVE_CONFIG, 0, 0, SystemClock.uptimeMillis() + (UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW << 1));
	}
	
	@Override
	public void onClick(View view) {
		if (!isLayoutCreated())
			return;
		if (view == btnGoBack) {
			if (!cancelGoBack())
				finish(0, view, true);
			return;
		}
		if (view == btnBluetooth && mode == MODE_NORMAL) {
			try {
				getHostActivity().startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			return;
		}
		if (colorViews != null) {
			for (int i = colorViews.length - 1; i >= 0; i--) {
				if (view == colorViews[i]) {
					lastColorView = i;
					ColorPickerView.showDialog(getHostActivity(), colorViews[i].getColor(), null, true, this);
					return;
				}
			}
		} else if (mode == MODE_BLUETOOTH) {
			if (view == btnAbout) {
				try {
					getHostActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/carlosrafaelgn/FPlayArduino")));
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			} else if (view == optBtConnect) {
				if (Player.bluetoothVisualizer == null) {
					startBluetoothVisualizer(false);
				} else if (Player.bluetoothVisualizerState != Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING) {
					Player.stopBluetoothVisualizer();
					refreshBluetoothStatus(false, false);
				}
			} else if (view == optBtStart) {
				if (Player.bluetoothVisualizer != null) {
					switch (Player.bluetoothVisualizerState) {
					case Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED:
						((WirelessVisualizer)Player.bluetoothVisualizer).startTransmission();
						refreshBluetoothStatus(false, false);
						break;
					case Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING:
						((WirelessVisualizer)Player.bluetoothVisualizer).stopTransmission();
						refreshBluetoothStatus(false, false);
						break;
					}
				} else if (Player.bluetoothVisualizerState != Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING) {
					startBluetoothVisualizer(true);
				}
			} else if (view == optBtVUMeter) {
				Player.setBluetoothUsingVUMeter(optBtVUMeter.isChecked());
				if (Player.bluetoothVisualizer != null)
					((WirelessVisualizer)Player.bluetoothVisualizer).syncDataType();
			} else if (view == optBtSize || view == optBtSpeed || view == optBtFramesToSkip) {
				CustomContextMenu.openContextMenu(view, this);
			}
			return;
		} else if (mode == MODE_HTTP) {
			if (view == btnAbout) {
				if (Player.httpTransmitter != null && httpAccessCode != null) {
					openHttpTransmitter();
				} else {
					openHttpTransmitterOnConnection = true;
					startHttpTransmitter();
				}
			} else if (view == optBtMessage) {
				if (Player.httpTransmitter != null && httpAccessCode != null) {
					//Since fplay:// links are not properly rendered in most apps, let's just share
					//the access code itself
					UI.shareText(getHostActivity(), "https://fplay.com.br?" + httpAccessCode);
					//UI.shareText(getText(R.string.access_code) + UI.collon() + "fplay://" + httpAccessCode);
					//try {
					//	final ActivityHost activityHost = getHostActivity();
					//	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					//		//noinspection deprecation
					//		final android.text.ClipboardManager clipboard = (android.text.ClipboardManager)activityHost.getSystemService(ActivityHost.CLIPBOARD_SERVICE);
					//		clipboard.setText(httpAccessCode);
					//	} else {
					//		final android.content.ClipboardManager clipboard = (android.content.ClipboardManager)activityHost.getSystemService(ActivityHost.CLIPBOARD_SERVICE);
					//		clipboard.setPrimaryClip(android.content.ClipData.newPlainText(httpAccessCode, httpAccessCode));
					//	}
					//	UI.toast(getText(R.string.success));
					//} catch (Throwable ex) {
					//	UI.toast(getText(R.string.error_gen));
					//}
				}
			} else if (view == optBtConnect) {
				if (Player.httpTransmitter == null) {
					startHttpTransmitter();
				} else {
					Player.stopHttpTransmitter();
					refreshHttpStatus(false, false);
				}
			} else if (view == optBtStart) {
				if (Player.httpTransmitter == null)
					startHttpTransmitter();
				else
					((HttpTransmitter)Player.httpTransmitter).refreshList();
			}
			return;
		}
		if (view == optLoadCurrentTheme) {
			if (mode == MODE_COLOR)
				loadColors(false, true);
		} else if (view == btnAbout) {
			if (mode == MODE_COLOR) {
				checkingReturn = false;
				final BgDialog dialog;
				switch (validate()) {
				case -1:
					dialog = new BgDialog(getHostActivity(), UI.createDialogView(getHostActivity(), getText(R.string.hard_theme)), this);
					dialog.setTitle(R.string.oops);
					dialog.setPositiveButton(R.string.ok);
					dialog.setNegativeButton(R.string.cancel);
					dialog.show();
					return;
				case -2:
					UI.showDialogMessage(getHostActivity(), getText(R.string.oops), getText(R.string.unreadable_theme), R.string.got_it);
					return;
				}
				applyTheme(view);
			} else {
				startActivity(new ActivityAbout(), 0, view, true);
			}
		} else if (!UI.dyslexiaFontSupportsCurrentLocale() && view == optUseAlternateTypeface) {
			final boolean desired = optUseAlternateTypeface.isChecked();
			UI.setUsingAlternateTypeface(desired);
			if (UI.isUsingAlternateTypeface != desired) {
				optUseAlternateTypeface.setChecked(UI.isUsingAlternateTypeface);
			} else {
				onCleanupLayout();
				onCreateLayout(false);
				Player.songs.destroyAlbumArtFetcher();
				if (UI.albumArtSongList)
					Player.songs.syncAlbumArtFetcher();
				System.gc();
			}
		} else if (view == optKeepScreenOn) {
			UI.keepScreenOn = optKeepScreenOn.isChecked();
			if (UI.keepScreenOn)
				addWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			else
				clearWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else if (view == optNotFullscreen) {
			UI.notFullscreen = !optNotFullscreen.isChecked();
			if (UI.notFullscreen)
				clearWindowFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			else
				addWindowFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else if (view == opt3D) {
			UI.is3D = opt3D.isChecked();
			UI.setTheme(getHostActivity(), UI.theme);
			onCleanupLayout();
			onCreateLayout(false);
			System.gc();
		} else if (view == optIsDividerVisible) {
			UI.isDividerVisible = optIsDividerVisible.isChecked();
			if (!UI.is3D) {
				for (int i = panelSettings.getChildCount() - 1; i >= 0; i--) {
					final View v = panelSettings.getChildAt(i);
					if (v != null && (v instanceof SettingView))
						v.invalidate();
				}
			}
		} else if (view == optIsVerticalMarginLarge) {
			final int oldVerticalMargin = UI.verticalMargin;
			UI.setVerticalMarginLarge(optIsVerticalMarginLarge.isChecked());
			for (int i = panelSettings.getChildCount() - 1; i >= 0; i--) {
				final View v = panelSettings.getChildAt(i);
				if (v != null && (v instanceof SettingView))
					((SettingView)v).updateVerticalMargin(oldVerticalMargin);
			}
		} else if (view == optExtraSpacing) {
			UI.extraSpacing = optExtraSpacing.isChecked();
			onCleanupLayout();
			onCreateLayout(false);
			System.gc();
		} else if (view == optPlaceTitleAtTheBottom) {
			UI.placeTitleAtTheBottom = optPlaceTitleAtTheBottom.isChecked();
		} else if (view == optPlaceControlsAtTheBottom) {
			UI.placeControlsAtTheBottom = optPlaceControlsAtTheBottom.isChecked();
			if (UI.placeControlsAtTheBottom) {
				optPlaceTitleAtTheBottom.setChecked(false);
				UI.placeTitleAtTheBottom = false;
			}
		} else if (view == optFlat) {
			UI.setFlat(optFlat.isChecked());
			//onCleanupLayout();
			//onCreateLayout(false);
			System.gc();
		} else if (view == optBorders) {
			UI.hasBorders = optBorders.isChecked();
		} else if (view == optAlbumArtSongList) {
			UI.albumArtSongList = optAlbumArtSongList.isChecked();
			if (UI.albumArtSongList)
				Player.songs.syncAlbumArtFetcher();
			else
				Player.songs.destroyAlbumArtFetcher();
		} else if (view == optHandleCallKey) {
			Player.handleCallKey = optHandleCallKey.isChecked();
		} else if (view == optExternalFx) {
			if (tryingToEnableExternalFx)
				return;
			tryingToEnableExternalFx = true;
			Player.enableExternalFx(optExternalFx.isChecked(), this);
		} else if (view == optPlayWhenHeadsetPlugged) {
			Player.playWhenHeadsetPlugged = optPlayWhenHeadsetPlugged.isChecked();
		} else if (view == optLargeTextIs22sp) {
			UI.largeTextIs22sp = optLargeTextIs22sp.isChecked();
			UI.setUsingAlternateTypeface(UI.isUsingAlternateTypeface);
			onCleanupLayout();
			onCreateLayout(false);
			Player.songs.destroyAlbumArtFetcher();
			if (UI.albumArtSongList)
				Player.songs.syncAlbumArtFetcher();
			System.gc();
		} else if (view == optPlacePlaylistToTheRight) {
			UI.controlsToTheLeft = optPlacePlaylistToTheRight.isChecked();
		} else if (view == optScrollBarToTheLeft) {
			UI.scrollBarToTheLeft = optScrollBarToTheLeft.isChecked();
			list.updateVerticalScrollbar();
		} else if (view == optWidgetTransparentBg) {
			UI.widgetTransparentBg = optWidgetTransparentBg.isChecked();
			WidgetMain.updateWidgets();
		} else if (view == optWidgetTextColor) {
			ColorPickerView.showDialog(getHostActivity(), UI.widgetTextColor, view, true, this);
		} else if (view == optWidgetIconColor) {
			ColorPickerView.showDialog(getHostActivity(), UI.widgetIconColor, view, true, this);
		} else if (view == optBlockBackKey) {
			UI.blockBackKey = optBlockBackKey.isChecked();
		} else if (view == optBackKeyAlwaysReturnsToPlayerWhenBrowsing) {
			UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = optBackKeyAlwaysReturnsToPlayerWhenBrowsing.isChecked();
		} else if (view == optWrapAroundList) {
			UI.wrapAroundList = optWrapAroundList.isChecked();
		} else if (view == optDoubleClickMode) {
			UI.doubleClickMode = optDoubleClickMode.isChecked();
		} else if (view == optDoNotAttenuateVolume) {
			Player.doNotAttenuateVolume = optDoNotAttenuateVolume.isChecked();
		} else if (view == optMarqueeTitle) {
			UI.marqueeTitle = optMarqueeTitle.isChecked();
		} else if (view == optPrepareNext) {
			Player.nextPreparationEnabled = optPrepareNext.isChecked();
		} else if (view == optClearListWhenPlayingFolders) {
			Player.clearListWhenPlayingFolders = optClearListWhenPlayingFolders.isChecked();
		} else if (view == optGoBackWhenPlayingFolders) {
			Player.goBackWhenPlayingFolders = optGoBackWhenPlayingFolders.isChecked();
		} else if (view == optPlayWithLongPress) {
			UI.playWithLongPress = optPlayWithLongPress.isChecked();
		} else if (view == optExpandSeekBar) {
			UI.expandSeekBar = optExpandSeekBar.isChecked();
		} else if (view == optAnimations) {
			UI.animationEnabled = optAnimations.isChecked();
		} else if (view == optAutoTurnOffPlaylist) {
			Player.turnOffWhenPlaylistEnds = optAutoTurnOffPlaylist.isChecked();
		} else if (view == optAnnounceCurrentSong) {
			Player.announceCurrentSong = optAnnounceCurrentSong.isChecked();
		} else if (view == optFollowCurrentSong) {
			Player.followCurrentSong = optFollowCurrentSong.isChecked();
		} else if (view == optResampling) {
			Player.enableResampling(optResampling.isChecked());
		} else if (view == optPreviousResetsAfterTheBeginning) {
			Player.previousResetsAfterTheBeginning = optPreviousResetsAfterTheBeginning.isChecked();
		} else if (view == optDisplaySongNumberAndCount) {
			UI.displaySongNumberAndCount = optDisplaySongNumberAndCount.isChecked();
		} else if (view == optAllowLockScreen) {
			UI.allowPlayerAboveLockScreen = optAllowLockScreen.isChecked();
			if (UI.allowPlayerAboveLockScreen)
				//noinspection deprecation
				addWindowFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
			else
				//noinspection deprecation
				clearWindowFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		} else if (view == optAutoTurnOff || view == optAutoIdleTurnOff || view == optTheme ||
			view == optForcedLocale || view == optVolumeControlType || view == optExtraInfoMode ||
			view == optForceOrientation || view == optTransition || view == optPopupTransition ||
			view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther ||
			view == optScrollBarSongList || view == optScrollBarBrowser || view == optHeadsetHook1 ||
			view == optHeadsetHook2 || view == optHeadsetHook3 || view == optBytesBeforeDecoding ||
			view == optMSBeforePlayback || view == optBufferSize || view == optFillThreshold ||
			view == optPlaybackEngine || view == optFileBufferSize) {
			lastMenuView = null;
			CustomContextMenu.openContextMenu(view, this);
			return;
		}
		configsChanged = true;
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (!isLayoutCreated())
			return;
		if (which == AlertDialog.BUTTON_POSITIVE) {
			if (mode == MODE_COLOR) {
				if (checkingReturn) {
					changed = false;
					finish(0, null, false);
				} else {
					applyTheme(null);
				}
			} else if (txtCustomMinutes != null) {
				try {
					int m = Integer.parseInt(txtCustomMinutes.getText().toString());
					configsChanged = true;
					if (lastMenuView == optAutoTurnOff) {
						Player.setTurnOffTimer(m);
						optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
					} else {
						Player.setIdleTurnOffTimer(m);
						optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
					}
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				txtCustomMinutes = null;
			}
		}
		dialog.dismiss();
	}
	
	@Override
	public void onPlayerTurnOffTimerTick() {
		if (optAutoTurnOff != null)
			optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
	}
	
	@Override
	public void onPlayerIdleTurnOffTimerTick() {
		if (optAutoIdleTurnOff != null)
			optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
	}
	
	private void validateColor(int idx1, int idx2) {
		final boolean err = (ColorUtils.contrastRatio((idx1 == UI.IDX_COLOR_SELECTED_GRAD_LT) ? ColorUtils.blend(colorViews[UI.IDX_COLOR_SELECTED_GRAD_LT].getColor(), colorViews[UI.IDX_COLOR_SELECTED_GRAD_DK].getColor(), 0.5f) : colorViews[idx1].getColor(), colorViews[idx2].getColor()) < MIN_THRESHOLD);
		colorViews[idx1].showErrorView(err);
		colorViews[idx2].showErrorView(err);
		if (idx1 == UI.IDX_COLOR_SELECTED_GRAD_LT)
			colorViews[UI.IDX_COLOR_SELECTED_GRAD_DK].showErrorView(err);
	}
	
	@Override
	public void onColorPicked(ColorPickerView picker, View parentView, int color) {
		if (!isLayoutCreated())
			return;
		if (mode == MODE_COLOR && lastColorView >= 0) {
			if (colorViews[lastColorView].getColor() != color) {
				changed = true;
				colorViews[lastColorView].setColor(color);
				switch (lastColorView) {
				case UI.IDX_COLOR_WINDOW:
				case UI.IDX_COLOR_TEXT:
					validateColor(UI.IDX_COLOR_WINDOW, UI.IDX_COLOR_TEXT);
					break;
				case UI.IDX_COLOR_LIST:
				case UI.IDX_COLOR_TEXT_LISTITEM:
					validateColor(UI.IDX_COLOR_LIST, UI.IDX_COLOR_TEXT_LISTITEM);
					break;
				case UI.IDX_COLOR_SELECTED_GRAD_LT:
				case UI.IDX_COLOR_SELECTED_GRAD_DK:
				case UI.IDX_COLOR_TEXT_SELECTED:
					validateColor(UI.IDX_COLOR_SELECTED_GRAD_LT, UI.IDX_COLOR_TEXT_SELECTED);
					break;
				case UI.IDX_COLOR_MENU:
				case UI.IDX_COLOR_TEXT_MENU:
					validateColor(UI.IDX_COLOR_MENU, UI.IDX_COLOR_TEXT_MENU);
					break;
				}
			}
			lastColorView = -1;
		} else if (parentView == optWidgetTextColor) {
			UI.widgetTextColor = color;
			optWidgetTextColor.setColor(color);
			WidgetMain.updateWidgets();
		} else if (parentView == optWidgetIconColor) {
			UI.widgetIconColor = color;
			optWidgetIconColor.setColor(color);
			WidgetMain.updateWidgets();
		}
	}
	
	@Override
	public void onScroll(ObservableScrollView view, int l, int t, int oldl, int oldt) {
		if (!isLayoutCreated() || headers == null || panelSettings == null || lblTitle == null || oldt == t)
			return;
		if (t < 0)
			t = 0;
		int i = view.getPreviousChildIndexWithClass(TextView.class, t);
		if (i <= 0) {
			if (t == 0) {
				currentHeader = -1;
				lblTitle.setVisibility(View.GONE);
				return;
			} else {
				i = 0;
			}
		}
		i = (Integer)panelSettings.getChildAt(i).getTag();
		if (currentHeader < 0) {
			lblTitle.setVisibility(View.VISIBLE);
			lblTitle.bringToFront();
			//neat workaround to partially hide lblTitle ;)
			panelControls.bringToFront();
		}
		if (currentHeader != i) {
			currentHeader = i;
			lblTitle.setText(headers[i].getText());
		}
		RelativeLayout.LayoutParams lp;
		if (i < (headers.length - 1) && headers[i + 1].getTop() < (t + lblTitle.getHeight())) {
			lp = (RelativeLayout.LayoutParams)lblTitle.getLayoutParams();
			lp.topMargin = headers[i + 1].getTop() - (t + lblTitle.getHeight());
			lblTitle.setLayoutParams(lp);
			lblTitleOk = false;
		} else if (!lblTitleOk) {
			lp = (RelativeLayout.LayoutParams)lblTitle.getLayoutParams();
			lp.topMargin = 0;
			lblTitle.setLayoutParams(lp);
			lblTitleOk = true;
		}
	}
	
	@Override
	public void run() {
		if (tryingToEnableExternalFx) {
			tryingToEnableExternalFx = false;
			if (optExternalFx != null)
				optExternalFx.setChecked(ExternalFx.isEnabled() && ExternalFx.isSupported());
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_SAVE_CONFIG:
			if (Player.state < Player.STATE_TERMINATING)
				Player.saveConfig(false);
			break;
		case MSG_REFRESH_BLUETOOTH:
			refreshBluetoothStatus(true, false);
			break;
		case MSG_REFRESH_HTTP:
			refreshHttpStatus(true, false);
			break;
		}
		return true;
	}
}
