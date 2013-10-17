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

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.SettingView;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivitySettings extends ClientActivity implements View.OnClickListener {
	private BgButton btnGoBack, btnAbout;
	private SettingView optKeepScreenOn, optDisplayVolumeInDB, optDoubleClickMode, optMarqueeTitle, optPrepareNext, optClearListWhenPlayingFolders, optForceOrientation, optFadeInFocus, optFadeInPause, optFadeInOther, lastMenuView;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (view == optForceOrientation) {
			lastMenuView = optForceOrientation;
			UI.prepare(menu);
			final int o = Player.forcedOrientation;
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
		if (lastMenuView == optForceOrientation) {
			Player.forcedOrientation = item.getItemId();
			if (Player.forcedOrientation == 0)
				getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			else if (Player.forcedOrientation < 0)
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
		
	private String getOrientationString() {
		final int o = Player.forcedOrientation;
		if (o == 0)
			return getText(R.string.none).toString();
		if (o < 0)
			return getText(R.string.landscape).toString();
		return getText(R.string.portrait).toString();
	}
	
	private String getFadeInString(int duration) {
		if (duration >= 250)
			return getText(R.string.dshort).toString();
		if (duration >= 125)
			return getText(R.string.dmedium).toString();
		if (duration > 0)
			return getText(R.string.dlong).toString();
		return getText(R.string.none).toString();
	}
	
	private void addSeparator(LinearLayout panelSettings, Context context) {
		final TextView t = new TextView(context);
		final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UI._1dp);
		p.setMargins(UI._8dp, UI._4sp, UI._8dp, UI._4sp);
		t.setLayoutParams(p);
		t.setBackgroundColor(UI.color_selected_border);
		t.setEnabled(false);
		panelSettings.addView(t);
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
		btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_INFORMATION, true), null, null, null);
		
		final Context ctx = getHostActivity();
		
		final ScrollView list = (ScrollView)findViewById(R.id.list);
		list.setBackgroundDrawable(new BorderDrawable(false));
		final LinearLayout panelSettings = (LinearLayout)findViewById(R.id.panelSettings);
		
		optKeepScreenOn = new SettingView(ctx, getText(R.string.opt_keep_screen_on).toString(), null, true, Player.keepScreenOn);
		optKeepScreenOn.setOnClickListener(this);
		optDisplayVolumeInDB = new SettingView(ctx, getText(R.string.opt_display_volume_in_db).toString(), null, true, Player.displayVolumeInDB);
		optDisplayVolumeInDB.setOnClickListener(this);
		optDoubleClickMode = new SettingView(ctx, getText(R.string.opt_double_click_mode).toString(), null, true, Player.doubleClickMode);
		optDoubleClickMode.setOnClickListener(this);
		optMarqueeTitle = new SettingView(ctx, getText(R.string.opt_marquee_title).toString(), null, true, Player.marqueeTitle);
		optMarqueeTitle.setOnClickListener(this);
		optPrepareNext = new SettingView(ctx, getText(R.string.opt_prepare_next).toString(), null, true, Player.nextPreparationEnabled);
		optPrepareNext.setOnClickListener(this);
		optClearListWhenPlayingFolders = new SettingView(ctx, getText(R.string.opt_clear_list_when_playing_folders).toString(), null, true, Player.clearListWhenPlayingFolders);
		optClearListWhenPlayingFolders.setOnClickListener(this);
		optForceOrientation = new SettingView(ctx, getText(R.string.opt_force_orientation).toString(), getOrientationString(), false, false);
		optForceOrientation.setOnClickListener(this);
		optFadeInFocus = new SettingView(ctx, getText(R.string.opt_fade_in_focus).toString(), getFadeInString(Player.fadeInIncrementOnFocus), false, false);
		optFadeInFocus.setOnClickListener(this);
		optFadeInPause = new SettingView(ctx, getText(R.string.opt_fade_in_pause).toString(), getFadeInString(Player.fadeInIncrementOnPause), false, false);
		optFadeInPause.setOnClickListener(this);
		optFadeInOther = new SettingView(ctx, getText(R.string.opt_fade_in_other).toString(), getFadeInString(Player.fadeInIncrementOnOther), false, false);
		optFadeInOther.setOnClickListener(this);
		
		CustomContextMenu.registerForContextMenu(optForceOrientation, this);
		CustomContextMenu.registerForContextMenu(optFadeInFocus, this);
		CustomContextMenu.registerForContextMenu(optFadeInPause, this);
		CustomContextMenu.registerForContextMenu(optFadeInOther, this);
		
		panelSettings.addView(optKeepScreenOn);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optDisplayVolumeInDB);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optDoubleClickMode);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optMarqueeTitle);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optPrepareNext);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optClearListWhenPlayingFolders);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optForceOrientation);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optFadeInFocus);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optFadeInPause);
		addSeparator(panelSettings, ctx);
		panelSettings.addView(optFadeInOther);
		
		if (UI.isLowDpiScreen)
			findViewById(R.id.panelControls).setPadding(0, 0, 0, 0);
		if (UI.isLargeScreen || !UI.isLowDpiScreen)
			btnAbout.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		btnAbout.setDefaultHeight();
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
	}
	
	@Override
	protected void onResume() {
		SongAddingMonitor.start(getHostActivity());
	}
	
	@Override
	protected void onCleanupLayout() {
		btnGoBack = null;
		btnAbout = null;
		optKeepScreenOn = null;
		optDisplayVolumeInDB = null;
		optDoubleClickMode = null;
		optMarqueeTitle = null;
		optPrepareNext = null;
		optClearListWhenPlayingFolders = null;
		optForceOrientation = null;
		optFadeInFocus = null;
		optFadeInPause = null;
		optFadeInOther = null;
		lastMenuView = null;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			finish();
		} else if (view == btnAbout) {
			startActivity(new ActivityAbout());
		} else if (view == optKeepScreenOn) {
			Player.keepScreenOn = optKeepScreenOn.isChecked();
			if (Player.keepScreenOn)
				addWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			else
				clearWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else if (view == optDisplayVolumeInDB) {
			Player.displayVolumeInDB = optDisplayVolumeInDB.isChecked();
		} else if (view == optDoubleClickMode) {
			Player.doubleClickMode = optDoubleClickMode.isChecked();
		} else if (view == optMarqueeTitle) {
			Player.marqueeTitle = optMarqueeTitle.isChecked();
		} else if (view == optPrepareNext) {
			Player.nextPreparationEnabled = optPrepareNext.isChecked();
		} else if (view == optClearListWhenPlayingFolders) {
			Player.clearListWhenPlayingFolders = optClearListWhenPlayingFolders.isChecked();
		} else if (view == optForceOrientation || view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther) {
			CustomContextMenu.openContextMenu(view, this);
		}
	}
}
