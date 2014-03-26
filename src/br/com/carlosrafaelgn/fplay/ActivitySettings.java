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
package br.com.carlosrafaelgn.fplay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.ColorPickerView;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.SettingView;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivitySettings extends ClientActivity implements Player.PlayerTurnOffTimerObserver, View.OnClickListener, DialogInterface.OnClickListener, ColorPickerView.OnColorPickerViewListener {
	private final boolean colorMode;
	private boolean changed, checkingReturn;
	private BgButton btnGoBack, btnAbout;
	private EditText txtCustomMinutes;
	private LinearLayout panelSettings;
	private SettingView optLoadCurrentTheme, optUseAlternateTypeface, optAutoTurnOff, optKeepScreenOn, optTheme, optVolumeControlType, optIsDividerVisible, optIsVerticalMarginLarge, optForcedLocale, optWidgetTransparentBg, optWidgetTextColor, optWidgetIconColor, optHandleCallKey, optPlayWhenHeadsetPlugged, optBlockBackKey, optDoubleClickMode, optMarqueeTitle, optPrepareNext, optClearListWhenPlayingFolders, optGoBackWhenPlayingFolders, optForceOrientation, optFadeInFocus, optFadeInPause, optFadeInOther, lastMenuView;
	private SettingView[] colorViews;
	private int lastColorView;
	
	public ActivitySettings(boolean colorMode) {
		this.colorMode = colorMode;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (view == optAutoTurnOff) {
			lastMenuView = optAutoTurnOff;
			UI.prepare(menu);
			menu.add(0, 0, 0, R.string.never)
				.setOnMenuItemClickListener(this);
			UI.separator(menu, 0, 1);
			menu.add(1, Player.getTurnOffTimerCustomMinutes(), 0, getMinuteString(Player.getTurnOffTimerCustomMinutes()))
				.setOnMenuItemClickListener(this);
			UI.separator(menu, 1, 1);
			menu.add(2, 60, 0, getMinuteString(60))
				.setOnMenuItemClickListener(this);
			menu.add(2, 90, 1, getMinuteString(90))
				.setOnMenuItemClickListener(this);
			menu.add(2, 120, 2, getMinuteString(120))
				.setOnMenuItemClickListener(this);
			UI.separator(menu, 2, 4);
			menu.add(3, -2, 0, R.string.custom)
				.setOnMenuItemClickListener(this);
		} else if (view == optForcedLocale) {
			final Context ctx = getApplication();
			final int o = UI.getForcedLocale();
			lastMenuView = optForcedLocale;
			UI.prepare(menu);
			menu.add(0, UI.LOCALE_NONE, 0, R.string.standard_language)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.LOCALE_US, 0, UI.getLocaleDescriptionFromCode(ctx, UI.LOCALE_US))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_US) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.LOCALE_PTBR, 1, UI.getLocaleDescriptionFromCode(ctx, UI.LOCALE_PTBR))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_PTBR) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.LOCALE_RU, 2, UI.getLocaleDescriptionFromCode(ctx, UI.LOCALE_RU))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_RU) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.LOCALE_UK, 3, UI.getLocaleDescriptionFromCode(ctx, UI.LOCALE_UK))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.LOCALE_UK) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optTheme) {
			final Context ctx = getApplication();
			final int o = UI.getTheme();
			lastMenuView = optTheme;
			UI.prepare(menu);
			menu.add(0, UI.THEME_CUSTOM, 0, UI.getThemeString(ctx, UI.THEME_CUSTOM))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_CUSTOM) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.THEME_BLUE_ORANGE, 0, UI.getThemeString(ctx, UI.THEME_BLUE_ORANGE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_BLUE_ORANGE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_BLUE, 1, UI.getThemeString(ctx, UI.THEME_BLUE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_BLUE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_ORANGE, 2, UI.getThemeString(ctx, UI.THEME_ORANGE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_ORANGE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_LIGHT, 3, UI.getThemeString(ctx, UI.THEME_LIGHT))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_LIGHT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_DARK_LIGHT, 4, UI.getThemeString(ctx, UI.THEME_DARK_LIGHT))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_DARK_LIGHT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optVolumeControlType) {
			lastMenuView = optVolumeControlType;
			UI.prepare(menu);
			int o = Player.getVolumeControlType();
			if (o == Player.VOLUME_CONTROL_DB)
				o = (UI.displayVolumeInDB ? -1 : -2);
			menu.add(0, Player.VOLUME_CONTROL_STREAM, 0, R.string.volume_control_type_integrated)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == Player.VOLUME_CONTROL_STREAM) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, -1, 0, R.string.volume_control_type_decibels)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == -1) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, -2, 1, R.string.volume_control_type_percentage)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == -2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 1, 2);
			menu.add(2, Player.VOLUME_CONTROL_NONE, 0, R.string.noneM)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == Player.VOLUME_CONTROL_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optForceOrientation) {
			lastMenuView = optForceOrientation;
			UI.prepare(menu);
			final int o = UI.forcedOrientation;
			menu.add(0, 0, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, -1, 0, R.string.landscape)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o < 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, 1, 1, R.string.portrait)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o > 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int d = ((view == optFadeInFocus) ? Player.fadeInIncrementOnFocus : ((view == optFadeInPause) ? Player.fadeInIncrementOnPause : Player.fadeInIncrementOnOther));
			menu.add(0, 0, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d <= 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, 250, 0, R.string.dshort)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d >= 250) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, 125, 1, R.string.dmedium)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(((d >= 125) && (d < 250)) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, 62, 2, R.string.dlong)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(((d > 0) && (d < 125)) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		if (lastMenuView == optAutoTurnOff) {
			if (item.getItemId() >= 0) {
				Player.setTurnOffTimer(item.getItemId(), false);
				optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
			} else {
				final Context ctx = getHostActivity();
				final LinearLayout l = new LinearLayout(ctx);
				l.setOrientation(LinearLayout.VERTICAL);
				l.setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);
				TextView lbl = new TextView(ctx);
				lbl.setText(R.string.msg_turn_off);
				lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
				l.addView(lbl);
				txtCustomMinutes = new EditText(ctx);
				txtCustomMinutes.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
				txtCustomMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
				txtCustomMinutes.setSingleLine();
				final LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				p.topMargin = UI._8dp;
				txtCustomMinutes.setLayoutParams(p);
				txtCustomMinutes.setText(Integer.toString(Player.getTurnOffTimerCustomMinutes()));
				l.addView(txtCustomMinutes);
				UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
				.setTitle(R.string.msg_turn_off_title)
				.setView(l)
				.setPositiveButton(R.string.ok, this)
				.setNegativeButton(R.string.cancel, this)
				.create());
			}
		} else if (lastMenuView == optForcedLocale) {
			if (item.getItemId() != UI.getForcedLocale()) {
				UI.setForcedLocale(getApplication(), item.getItemId());
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (lastMenuView == optTheme) {
			if (item.getItemId() == UI.THEME_CUSTOM) {
				startActivity(new ActivitySettings(true));
				return true;
			}
			UI.setTheme(item.getItemId());
			getHostActivity().setWindowColor(UI.color_window);
			onCleanupLayout();
			onCreateLayout(false);
			System.gc();
		} else if (lastMenuView == optVolumeControlType) {
			switch (item.getItemId()) {
			case Player.VOLUME_CONTROL_STREAM:
				Player.setVolumeControlType(getApplication(), Player.VOLUME_CONTROL_STREAM);
				break;
			case -1:
				Player.setVolumeControlType(getApplication(), Player.VOLUME_CONTROL_DB);
				UI.displayVolumeInDB = true;
				break;
			case -2:
				Player.setVolumeControlType(getApplication(), Player.VOLUME_CONTROL_DB);
				UI.displayVolumeInDB = false;
				break;
			case Player.VOLUME_CONTROL_NONE:
				Player.setVolumeControlType(getApplication(), Player.VOLUME_CONTROL_NONE);
				break;
			}
			optVolumeControlType.setSecondaryText(getVolumeString());
		} else if (lastMenuView == optForceOrientation) {
			UI.forcedOrientation = item.getItemId();
			if (UI.forcedOrientation == 0)
				getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			else if (UI.forcedOrientation < 0)
				getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else
				getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			optForceOrientation.setSecondaryText(getOrientationString());
		} else if (lastMenuView == optFadeInFocus) {
			Player.fadeInIncrementOnFocus = item.getItemId();
			optFadeInFocus.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optFadeInPause) {
			Player.fadeInIncrementOnPause = item.getItemId();
			optFadeInPause.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optFadeInOther) {
			Player.fadeInIncrementOnOther = item.getItemId();
			optFadeInOther.setSecondaryText(getFadeInString(item.getItemId()));
		}
		lastMenuView = null;
		return true;
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
	
	private String getVolumeString() {
		switch (Player.getVolumeControlType()) {
		case Player.VOLUME_CONTROL_STREAM:
			return getText(R.string.volume_control_type_integrated).toString();
		case Player.VOLUME_CONTROL_DB:
			return getText(UI.displayVolumeInDB ? R.string.volume_control_type_decibels : R.string.volume_control_type_percentage).toString();
		default:
			return getText(R.string.noneM).toString();
		}
	}
	
	private String getOrientationString() {
		final int o = UI.forcedOrientation;
		return getText((o == 0) ? R.string.none : ((o < 0) ? R.string.landscape : R.string.portrait)).toString();
	}
	
	private String getFadeInString(int duration) {
		return getText((duration >= 250) ? R.string.dshort : ((duration >= 125) ? R.string.dmedium : ((duration > 0) ? R.string.dlong : R.string.none))).toString();
	}
	
	@SuppressWarnings("deprecation")
	private void addHeader(Context ctx, int resId, SettingView previousControl) {
		final TextView hdr = new TextView(ctx);
		hdr.setText(resId);
		hdr.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		hdr.setMaxLines(3);
		hdr.setPadding(UI._8dp, UI._8sp, UI._8dp, UI._8sp);
		if (UI.isLargeScreen)
			UI.largeText(hdr);
		else
			UI.mediumText(hdr);
		hdr.setTextColor(UI.colorState_text_highlight_static);
		hdr.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
		panelSettings.addView(hdr);
		if (previousControl != null)
			previousControl.setHidingSeparator(true);
	}
	
	private boolean cancelGoBack() {
		if (colorMode && changed) {
			checkingReturn = true;
			UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
			.setTitle(R.string.oops)
			.setMessage(R.string.discard_theme)
			.setPositiveButton(R.string.ok, this)
			.setNegativeButton(R.string.cancel, null)
			.create());
			return true;
		}
		return false;
	}
	
	private int validate() {
		//hard = -1
		//impossible = -2
		return 0;
	}
	
	private void applyTheme() {
		final byte[] colors = UI.serializeThemeToArray();
		for (int i = 0; i < colorViews.length; i++)
			UI.serializeThemeColor(colors, i * 3, colorViews[i].getColor());
		UI.customColors = colors;
		UI.setTheme(UI.THEME_CUSTOM);
		getHostActivity().setWindowColor(UI.color_window);
		changed = false;
		finish();
	}
	
	private void loadColors(boolean createControls, boolean forceCurrent) {
		final Context ctx = getHostActivity();
		final int[] colorOrder = new int[] { 7, 9, 8, 10, 11, 0, 1, 2, 12, 13, 3, 15, 6, 5, 4, 14, 18, 16, 17, 19, 22, 20, 21, 23 };
		final byte[] colors = ((UI.customColors != null && UI.customColors.length >= 72 && !forceCurrent) ? UI.customColors : UI.serializeThemeToArray());
		if (createControls)
			colorViews = new SettingView[colorOrder.length];
		for (int i = 0; i < colorOrder.length; i++) {
			final int idx = colorOrder[i];
			if (createControls)
				colorViews[idx] = new SettingView(ctx, UI.getThemeColorDescription(ctx, idx).toString(), null, false, false, true);
			colorViews[idx].setColor(UI.deserializeThemeColor(colors, idx * 3));
			colorViews[idx].setOnClickListener(this);
		}
		if (createControls) {
			optLoadCurrentTheme = new SettingView(ctx, getText(R.string.load_colors_from_current_theme).toString(), null, false, false, false);
			optLoadCurrentTheme.setOnClickListener(this);
			panelSettings.addView(optLoadCurrentTheme);
			for (int i = 0; i < colorOrder.length; i++) {
				final int idx = colorOrder[i];
				switch (i) {
				case 0:
					addHeader(ctx, R.string.general, optLoadCurrentTheme);
					break;
				case 8:
					addHeader(ctx, R.string.list2, colorViews[colorOrder[i - 1]]);
					break;
				case 11:
					addHeader(ctx, R.string.menu, colorViews[colorOrder[i - 1]]);
					break;
				case 15:
					addHeader(ctx, R.string.selection, colorViews[colorOrder[i - 1]]);
					break;
				case 20:
					addHeader(ctx, R.string.keyboard_focus, colorViews[colorOrder[i - 1]]);
					break;
				}
				panelSettings.addView(colorViews[idx]);
			}
		}
	}
	
	@Override
	public boolean onBackPressed() {
		return cancelGoBack();
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_settings);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnAbout = (BgButton)findViewById(R.id.btnAbout);
		btnAbout.setOnClickListener(this);
		if (!colorMode)
			btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_INFORMATION, TextIconDrawable.LOCATION_WINDOW), null, null, null);
		else
			btnAbout.setText(R.string.apply_theme);
		lastColorView = -1;
		
		final Context ctx = getHostActivity();
		
		final ScrollView list = (ScrollView)findViewById(R.id.list);
		list.setHorizontalFadingEdgeEnabled(false);
		list.setVerticalFadingEdgeEnabled(false);
		list.setFadingEdgeLength(0);
		list.setBackgroundDrawable(new BorderDrawable(0, UI.thickDividerSize, 0, 0));
		panelSettings = (LinearLayout)findViewById(R.id.panelSettings);
		
		if (colorMode) {
			loadColors(true, false);
		} else {
			if (!UI.isCurrentLocaleCyrillic()) {
				optUseAlternateTypeface = new SettingView(ctx, getText(R.string.opt_use_alternate_typeface).toString(), null, true, UI.isUsingAlternateTypeface(), false);
				optUseAlternateTypeface.setOnClickListener(this);
			}
			optAutoTurnOff = new SettingView(ctx, getText(R.string.opt_auto_turn_off).toString(), getAutoTurnOffString(), false, false, false);
			optAutoTurnOff.setOnClickListener(this);
			optKeepScreenOn = new SettingView(ctx, getText(R.string.opt_keep_screen_on).toString(), null, true, UI.keepScreenOn, false);
			optKeepScreenOn.setOnClickListener(this);
			optTheme = new SettingView(ctx, getText(R.string.color_theme).toString() + ":", UI.getThemeString(ctx, UI.getTheme()), false, false, false);
			optTheme.setOnClickListener(this);
			optVolumeControlType = new SettingView(ctx, getText(R.string.opt_volume_control_type).toString(), getVolumeString(), false, false, false);
			optVolumeControlType.setOnClickListener(this);
			optIsDividerVisible = new SettingView(ctx, getText(R.string.opt_is_divider_visible).toString(), null, true, UI.isDividerVisible, false);
			optIsDividerVisible.setOnClickListener(this);
			optIsVerticalMarginLarge = new SettingView(ctx, getText(R.string.opt_is_vertical_margin_large).toString(), null, true, UI.isVerticalMarginLarge, false);
			optIsVerticalMarginLarge.setOnClickListener(this);
			optForcedLocale = new SettingView(ctx, getText(R.string.opt_language).toString(), UI.getLocaleDescriptionFromCode(ctx, UI.getForcedLocale()), false, false, false);
			optForcedLocale.setOnClickListener(this);
			optWidgetTransparentBg = new SettingView(ctx, getText(R.string.transparent_background).toString(), null, true, UI.widgetTransparentBg, false);
			optWidgetTransparentBg.setOnClickListener(this);
			optWidgetTextColor = new SettingView(ctx, getText(R.string.text_color).toString(), null, false, false, true);
			optWidgetTextColor.setOnClickListener(this);
			optWidgetTextColor.setColor(UI.widgetTextColor);
			optWidgetIconColor = new SettingView(ctx, getText(R.string.icon_color).toString(), null, false, false, true);
			optWidgetIconColor.setOnClickListener(this);
			optWidgetIconColor.setColor(UI.widgetIconColor);
			optHandleCallKey = new SettingView(ctx, getText(R.string.opt_handle_call_key).toString(), null, true, Player.handleCallKey, false);
			optHandleCallKey.setOnClickListener(this);
			optPlayWhenHeadsetPlugged = new SettingView(ctx, getText(R.string.opt_play_when_headset_plugged).toString(), null, true, Player.playWhenHeadsetPlugged, false);
			optPlayWhenHeadsetPlugged.setOnClickListener(this);
			optBlockBackKey = new SettingView(ctx, getText(R.string.opt_block_back_key).toString(), null, true, UI.blockBackKey, false);
			optBlockBackKey.setOnClickListener(this);
			optDoubleClickMode = new SettingView(ctx, getText(R.string.opt_double_click_mode).toString(), null, true, UI.doubleClickMode, false);
			optDoubleClickMode.setOnClickListener(this);
			optMarqueeTitle = new SettingView(ctx, getText(R.string.opt_marquee_title).toString(), null, true, UI.marqueeTitle, false);
			optMarqueeTitle.setOnClickListener(this);
			optPrepareNext = new SettingView(ctx, getText(R.string.opt_prepare_next).toString(), null, true, Player.nextPreparationEnabled, false);
			optPrepareNext.setOnClickListener(this);
			optClearListWhenPlayingFolders = new SettingView(ctx, getText(R.string.opt_clear_list_when_playing_folders).toString(), null, true, Player.clearListWhenPlayingFolders, false);
			optClearListWhenPlayingFolders.setOnClickListener(this);
			optGoBackWhenPlayingFolders = new SettingView(ctx, getText(R.string.opt_go_back_when_playing_folders).toString(), null, true, Player.goBackWhenPlayingFolders, false);
			optGoBackWhenPlayingFolders.setOnClickListener(this);
			optForceOrientation = new SettingView(ctx, getText(R.string.opt_force_orientation).toString(), getOrientationString(), false, false, false);
			optForceOrientation.setOnClickListener(this);
			optFadeInFocus = new SettingView(ctx, getText(R.string.opt_fade_in_focus).toString(), getFadeInString(Player.fadeInIncrementOnFocus), false, false, false);
			optFadeInFocus.setOnClickListener(this);
			optFadeInPause = new SettingView(ctx, getText(R.string.opt_fade_in_pause).toString(), getFadeInString(Player.fadeInIncrementOnPause), false, false, false);
			optFadeInPause.setOnClickListener(this);
			optFadeInOther = new SettingView(ctx, getText(R.string.opt_fade_in_other).toString(), getFadeInString(Player.fadeInIncrementOnOther), false, false, false);
			optFadeInOther.setOnClickListener(this);
			
			panelSettings.addView(optAutoTurnOff);
			addHeader(ctx, R.string.hdr_display, optAutoTurnOff);
			panelSettings.addView(optKeepScreenOn);
			if (!UI.isCurrentLocaleCyrillic())
				panelSettings.addView(optUseAlternateTypeface);
			panelSettings.addView(optTheme);
			panelSettings.addView(optForceOrientation);
			panelSettings.addView(optIsDividerVisible);
			panelSettings.addView(optIsVerticalMarginLarge);
			panelSettings.addView(optForcedLocale);
			addHeader(ctx, R.string.widget, optForcedLocale);
			panelSettings.addView(optWidgetTransparentBg);
			panelSettings.addView(optWidgetTextColor);
			panelSettings.addView(optWidgetIconColor);
			addHeader(ctx, R.string.hdr_playback, optWidgetIconColor);
			panelSettings.addView(optPlayWhenHeadsetPlugged);
			panelSettings.addView(optHandleCallKey);
			panelSettings.addView(optVolumeControlType);
			panelSettings.addView(optFadeInFocus);
			panelSettings.addView(optFadeInPause);
			panelSettings.addView(optFadeInOther);
			addHeader(ctx, R.string.hdr_behavior, optFadeInOther);
			panelSettings.addView(optClearListWhenPlayingFolders);
			panelSettings.addView(optGoBackWhenPlayingFolders);
			panelSettings.addView(optBlockBackKey);
			panelSettings.addView(optDoubleClickMode);
			panelSettings.addView(optMarqueeTitle);
			panelSettings.addView(optPrepareNext);
		}
		
		if (UI.isLowDpiScreen && !UI.isLargeScreen)
			findViewById(R.id.panelControls).setPadding(0, 0, 0, 0);
		else
			btnAbout.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		btnAbout.setDefaultHeight();
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
		Player.turnOffTimerObserver = null;
	}
	
	@Override
	protected void onResume() {
		SongAddingMonitor.start(getHostActivity());
		Player.turnOffTimerObserver = this;
		if (optAutoTurnOff != null)
			optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
	}
	
	@Override
	protected void onCleanupLayout() {
		btnGoBack = null;
		btnAbout = null;
		panelSettings = null;
		optLoadCurrentTheme = null;
		optUseAlternateTypeface = null;
		optAutoTurnOff = null;
		optKeepScreenOn = null;
		optTheme = null;
		optVolumeControlType = null;
		optIsDividerVisible = null;
		optIsVerticalMarginLarge = null;
		optForcedLocale = null;
		optWidgetTransparentBg = null;
		optWidgetTextColor = null;
		optWidgetIconColor = null;
		optHandleCallKey = null;
		optPlayWhenHeadsetPlugged = null;
		optBlockBackKey = null;
		optDoubleClickMode = null;
		optMarqueeTitle = null;
		optPrepareNext = null;
		optClearListWhenPlayingFolders = null;
		optGoBackWhenPlayingFolders = null;
		optForceOrientation = null;
		optFadeInFocus = null;
		optFadeInPause = null;
		optFadeInOther = null;
		lastMenuView = null;
		if (colorViews != null) {
			for (int i = colorViews.length - 1; i >= 0; i--)
				colorViews[i] = null;
			colorViews = null;
		}
	}
	
	@Override
	public void onClick(View view) {
		if (colorViews != null) {
			for (int i = colorViews.length - 1; i >= 0; i--) {
				if (view == colorViews[i]) {
					lastColorView = i;
					ColorPickerView.showDialog(getHostActivity(), colorViews[i].getColor(), null, this);
					return;
				}
			}
		}
		if (view == btnGoBack) {
			if (!cancelGoBack())
				finish();
		} else if (view == optLoadCurrentTheme) {
			if (colorMode)
				loadColors(false, true);
		} else if (view == btnAbout) {
			if (colorMode) {
				checkingReturn = false;
				switch (validate()) {
				case -1:
					UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
					.setTitle(R.string.oops)
					.setMessage(R.string.hard_theme)
					.setPositiveButton(R.string.ok, this)
					.setNegativeButton(R.string.cancel, null)
					.create());
					return;
				case -2:
					UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
					.setTitle(R.string.oops)
					.setMessage(R.string.unreadable_theme)
					.setPositiveButton(R.string.got_it, null)
					.create());
					return;
				}
				applyTheme();
			} else {
				startActivity(new ActivityAbout());
			}
		} else if (!UI.isCurrentLocaleCyrillic() && view == optUseAlternateTypeface) {
			final boolean desired = optUseAlternateTypeface.isChecked();
			UI.setUsingAlternateTypeface(getHostActivity(), desired);
			if (UI.isUsingAlternateTypeface() != desired) {
				optUseAlternateTypeface.setChecked(UI.isUsingAlternateTypeface());
			} else {
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (view == optKeepScreenOn) {
			UI.keepScreenOn = optKeepScreenOn.isChecked();
			if (UI.keepScreenOn)
				addWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			else
				clearWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else if (view == optIsDividerVisible) {
			UI.isDividerVisible = optIsDividerVisible.isChecked();
			for (int i = panelSettings.getChildCount() - 1; i >= 0; i--) {
				final View v = panelSettings.getChildAt(i);
				if (v != null && (v instanceof SettingView))
					((SettingView)v).invalidate();
			}
		} else if (view == optIsVerticalMarginLarge) {
			UI.isVerticalMarginLarge = optIsVerticalMarginLarge.isChecked();
			for (int i = panelSettings.getChildCount() - 1; i >= 0; i--) {
				final View v = panelSettings.getChildAt(i);
				if (v != null && (v instanceof SettingView))
					((SettingView)v).refreshVerticalMargin();
			}
			panelSettings.requestLayout();
		} else if (view == optHandleCallKey) {
			Player.handleCallKey = optHandleCallKey.isChecked();
		} else if (view == optPlayWhenHeadsetPlugged) {
			Player.playWhenHeadsetPlugged = optPlayWhenHeadsetPlugged.isChecked();
		} else if (view == optWidgetTransparentBg) {
			UI.widgetTransparentBg = optWidgetTransparentBg.isChecked();
			WidgetMain.updateWidgets(getApplication());
		} else if (view == optWidgetTextColor) {
			ColorPickerView.showDialog(getHostActivity(), UI.widgetTextColor, view, this);
		} else if (view == optWidgetIconColor) {
			ColorPickerView.showDialog(getHostActivity(), UI.widgetIconColor, view, this);
		} else if (view == optBlockBackKey) {
			UI.blockBackKey = optBlockBackKey.isChecked();
		} else if (view == optDoubleClickMode) {
			UI.doubleClickMode = optDoubleClickMode.isChecked();
		} else if (view == optMarqueeTitle) {
			UI.marqueeTitle = optMarqueeTitle.isChecked();
		} else if (view == optPrepareNext) {
			Player.nextPreparationEnabled = optPrepareNext.isChecked();
		} else if (view == optClearListWhenPlayingFolders) {
			Player.clearListWhenPlayingFolders = optClearListWhenPlayingFolders.isChecked();
		} else if (view == optGoBackWhenPlayingFolders) {
			Player.goBackWhenPlayingFolders = optGoBackWhenPlayingFolders.isChecked();
		} else if (view == optAutoTurnOff || view == optTheme || view == optForcedLocale || view == optVolumeControlType || view == optForceOrientation || view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther) {
			CustomContextMenu.openContextMenu(view, this);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE) {
			if (colorMode) {
				if (checkingReturn) {
					changed = false;
					finish();
				} else {
					applyTheme();
				}
			} else if (txtCustomMinutes != null) {
				try {
					int m = Integer.parseInt(txtCustomMinutes.getText().toString());
					if (m > 0) {
						Player.setTurnOffTimer(m, true);
						optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
					}
				} catch (Throwable ex) {
				}
				txtCustomMinutes = null;
			}
		}
	}
	
	@Override
	public void onPlayerTurnOffTimerTick() {
		if (optAutoTurnOff != null)
			optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
	}
	
	@Override
	public void onColorPicked(ColorPickerView picker, View parentView, int color) {
		if (colorMode && lastColorView >= 0) {
			if (colorViews[lastColorView].getColor() != color) {
				changed = true;
				colorViews[lastColorView].setColor(color);
			}
			lastColorView = -1;
		} else if (parentView == optWidgetTextColor) {
			UI.widgetTextColor = color;
			optWidgetTextColor.setColor(color);
			WidgetMain.updateWidgets(getApplication());
		} else if (parentView == optWidgetIconColor) {
			UI.widgetIconColor = color;
			optWidgetIconColor.setColor(color);
			WidgetMain.updateWidgets(getApplication());
		}
	}
}
