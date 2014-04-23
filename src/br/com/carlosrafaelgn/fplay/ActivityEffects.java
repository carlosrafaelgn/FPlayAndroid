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
package br.com.carlosrafaelgn.fplay;

import android.content.Context;
import android.os.Message;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.playback.BassBoost;
import br.com.carlosrafaelgn.fplay.playback.Equalizer;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgSeekBar;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;

public class ActivityEffects extends ClientActivity implements MainHandler.Callback, View.OnClickListener, BgSeekBar.OnBgSeekBarChangeListener, ActivityFileSelection.OnFileSelectionListener {
	private static final int MSG_ENABLING_STEP_0 = 0x0300;
	private static final int MSG_ENABLING_STEP_1 = 0x0301;
	private static final int MSG_ENABLING_STEP_2 = 0x0302;
	private static final int MSG_ENABLING_STEP_3 = 0x0303;
	private static final int LevelThreshold = 100, MNU_ZEROPRESET = 100, MNU_LOADPRESET = 101, MNU_SAVEPRESET = 102;
	private RelativeLayout panelControls;
	private LinearLayout container;
	private BgButton chkEnable;
	private BgButton btnGoBack, btnMenu, btnChangeEffect;
	private TextView lblMsg;
	private int min, max;
	private int[] frequencies;
	private boolean enablingEffect;
	private BgSeekBar[] bars;
	private BgSeekBar barBass;
	private StringBuilder txtBuilder;
	
	private String format(int frequency, int level) {
		if (txtBuilder == null)
			return "";
		txtBuilder.delete(0, txtBuilder.length());
		txtBuilder.append(frequency / 1000);
		frequency = (frequency % 1000) / 10;
		if (frequency != 0) {
			txtBuilder.append('.');
			if (frequency < 10)
				txtBuilder.append('0');
			txtBuilder.append(frequency);
		}
		txtBuilder.append("Hz / ");
		if (level < 0) {
			txtBuilder.append('-');
			level = -level;
		}
		txtBuilder.append(level / 100);
		txtBuilder.append('.');
		level %= 100;
		if (level < 10)
			txtBuilder.append('0');
		txtBuilder.append(level);
		txtBuilder.append("dB");
		return txtBuilder.toString();
	}
	
	private String format(int strength) {
		if (txtBuilder == null)
			return "";
		txtBuilder.delete(0, txtBuilder.length());
		txtBuilder.append(strength / 10);
		strength %= 10;
		txtBuilder.append('.');
		txtBuilder.append(strength);
		txtBuilder.append("%");
		return txtBuilder.toString();
	}
	
	@Override
	public View getNullContextMenuView() {
		return btnMenu;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		UI.prepare(menu);
		if (!Player.bassBoostMode) {
			menu.add(0, MNU_ZEROPRESET, 0, R.string.zero_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_REMOVE));
			menu.add(0, MNU_LOADPRESET, 1, R.string.load_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_LOAD));
			menu.add(0, MNU_SAVEPRESET, 2, R.string.save_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_SAVE));
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		case MNU_ZEROPRESET:
			for (int i = Equalizer.getBandCount() - 1; i >= 0; i--)
				Equalizer.setBandLevel(i, 0, false);
			Equalizer.applyAllBandSettings();
			updateBars();
			break;
		case MNU_LOADPRESET:
			startActivity(new ActivityFileSelection(MNU_LOADPRESET, false, false, getText(R.string.item_preset).toString(), "#pset", this));
			break;
		case MNU_SAVEPRESET:
			startActivity(new ActivityFileSelection(MNU_SAVEPRESET, true, false, getText(R.string.item_preset).toString(), "#pset", this));
			break;
		}
		return true;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			finish();
		} else if (view == btnMenu) {
			CustomContextMenu.openContextMenu(btnMenu, this);
		} else if (view == btnChangeEffect) {
			Player.bassBoostMode = !Player.bassBoostMode;
			prepareViewForMode();
		} else if (view == chkEnable) {
			if (enablingEffect)
				return;
			enablingEffect = true;
			MainHandler.sendMessage(this, MSG_ENABLING_STEP_0);
		}
	}
	
	@Override
	protected void onCreate() {
		txtBuilder = new StringBuilder(32);
		min = Equalizer.getMinBandLevel();
		max = Equalizer.getMaxBandLevel();
		frequencies = new int[Equalizer.getBandCount()];
		bars = new BgSeekBar[frequencies.length];
		for (int i = frequencies.length - 1; i >= 0; i--)
			frequencies[i] = Equalizer.getBandFrequency(i);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_effects);
		panelControls = (RelativeLayout)findViewById(R.id.panelControls);
		panelControls.setBackgroundDrawable(new BorderDrawable(0, UI.thickDividerSize, 0, 0));
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		chkEnable = (BgButton)findViewById(R.id.chkEnable);
		chkEnable.setOnClickListener(this);
		chkEnable.setBehavingAsCheckBox(true);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		btnMenu.setIcon(UI.ICON_MENU);
		//CustomContextMenu.registerForContextMenu(btnMenu, this);
		btnChangeEffect = (BgButton)findViewById(R.id.btnChangeEffect);
		btnChangeEffect.setOnClickListener(this);
		btnChangeEffect.setCompoundDrawables(new TextIconDrawable(UI.ICON_EQUALIZER, UI.color_text_listitem, UI.defaultControlContentsSize), null, null, null);
		btnChangeEffect.setMinimumHeight(UI.defaultControlSize);
		btnChangeEffect.setTextColor(UI.colorState_text_listitem_reactive);
		barBass = (BgSeekBar)findViewById(R.id.barBass);
		barBass.setMax(BassBoost.getMaxStrength());
		barBass.setValue(BassBoost.getStrength());
		barBass.setKeyIncrement(BassBoost.getMaxStrength() / 50);
		barBass.setVertical(true);
		barBass.setOnBgSeekBarChangeListener(this);
		barBass.setInsideList(true);
		lblMsg = (TextView)findViewById(R.id.lblMsg);
		if (UI.isLargeScreen) {
			chkEnable.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
			btnChangeEffect.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
			lblMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		} else if (UI.isLowDpiScreen) {
			chkEnable.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			lblMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			btnChangeEffect.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			barBass.setTextSizeIndex(1);
		} else {
			lblMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		}
		if (UI.extraSpacing)
			findViewById(R.id.panelTop).setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);
		prepareViewForMode();
	}
	
	@Override
	protected void onOrientationChanged() {
		prepareViewForMode();
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
		panelControls = null;
		container = null;
		chkEnable = null;
		barBass = null;
		lblMsg = null;
		btnGoBack = null;
		btnMenu = null;
		btnChangeEffect = null;
		for (int i = frequencies.length - 1; i >= 0; i--)
			bars[i] = null;
	}
	
	@Override
	protected void onDestroy() {
		frequencies = null;
		bars = null;
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (!fromUser)
			return;
		if (seekBar == barBass) {
			BassBoost.setStrength(value, false);
			seekBar.setText(format(BassBoost.getStrength()));
		} else {
			for (int i = frequencies.length - 1; i >= 0; i--) {
				if (seekBar == bars[i]) {
					int level = (5 * value) + min;
					if (!usingKeys && (level <= LevelThreshold) && (level >= -LevelThreshold)) {
						level = 0;
						seekBar.setValue(-min / 5);
					}
					Equalizer.setBandLevel(i, level, false);
					seekBar.setText(format(frequencies[i], Equalizer.getBandLevel(i)));
					return;
				}
			}
		}
	}
	
	@Override
	public boolean onStartTrackingTouch(BgSeekBar seekBar) {
		return true;
	}
	
	@Override
	public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled) {
		if (seekBar == barBass) {
			//call getStrength() twice, as the value may change once
			//it is actually applied...
			BassBoost.setStrength(BassBoost.getStrength(), true);
			final int s = BassBoost.getStrength();
			seekBar.setValue(s);
			seekBar.setText(format(s));
		} else if (frequencies != null && bars != null) {
			for (int i = frequencies.length - 1; i >= 0; i--) {
				if (seekBar == bars[i]) {
					Equalizer.setBandLevel(i, Equalizer.getBandLevel(i), true);
					return;
				}
			}
		}
	}
	
	private void updateBars() {
		if (bars != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				final BgSeekBar bar = bars[i];
				if (bar != null) {
					final int level = Equalizer.getBandLevel(i);
					bars[i].setText(format(frequencies[i], level));
					bars[i].setValue(((level <= LevelThreshold) && (level >= -LevelThreshold)) ? (-min / 5) : ((level - min) / 5));
				}
			}
		}
	}
	
	private void prepareViewForMode() {
		RelativeLayout.LayoutParams rp;
		final Context ctx = getApplication();
		if (Player.bassBoostMode) {
			if (container != null)
				container.setVisibility(View.GONE);
			btnChangeEffect.setText(R.string.go_to_equalizer);
			chkEnable.setMaxWidth(getDecorViewWidth() - UI.defaultControlSize - (UI._8dp * 3));
			rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			chkEnable.setLayoutParams(rp);
			btnMenu.setVisibility(View.GONE);
			if (!BassBoost.isSupported()) {
				barBass.setVisibility(View.GONE);
				chkEnable.setVisibility(View.GONE);
				lblMsg.setText(R.string.bass_boost_not_supported);
				lblMsg.setVisibility(View.VISIBLE);
				
				btnGoBack.setNextFocusRightId(R.id.btnChangeEffect);
				btnGoBack.setNextFocusDownId(R.id.btnChangeEffect);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnChangeEffect);
				btnChangeEffect.setNextFocusUpId(R.id.btnGoBack);
				btnChangeEffect.setNextFocusLeftId(R.id.btnGoBack);
			} else if (!BassBoost.isStrengthSupported()) {
				barBass.setVisibility(View.GONE);
				chkEnable.setVisibility(View.VISIBLE);
				lblMsg.setText(R.string.bass_boost_strength_not_supported);
				lblMsg.setVisibility(View.VISIBLE);
				
				btnGoBack.setNextFocusRightId(R.id.chkEnable);
				btnGoBack.setNextFocusDownId(R.id.btnChangeEffect);
				UI.setNextFocusForwardId(btnGoBack, R.id.chkEnable);
				chkEnable.setNextFocusDownId(R.id.btnChangeEffect);
				chkEnable.setNextFocusRightId(R.id.btnChangeEffect);
				UI.setNextFocusForwardId(chkEnable, R.id.btnChangeEffect);
				btnChangeEffect.setNextFocusUpId(R.id.chkEnable);
				btnChangeEffect.setNextFocusLeftId(R.id.chkEnable);
			} else {
				lblMsg.setVisibility(View.GONE);
				rp = (RelativeLayout.LayoutParams)barBass.getLayoutParams();
				rp.topMargin = UI._8dp;
				rp.bottomMargin = UI._8dp;
				barBass.setLayoutParams(rp);
				barBass.setValue(BassBoost.getStrength());
				barBass.setText(format(BassBoost.getStrength()));
				barBass.setVisibility(View.VISIBLE);
				chkEnable.setText(R.string.enable_bass_boost);
				chkEnable.setVisibility(View.VISIBLE);
				chkEnable.setChecked(BassBoost.isEnabled());
				
				btnGoBack.setNextFocusRightId(R.id.chkEnable);
				btnGoBack.setNextFocusDownId(R.id.barBass);
				UI.setNextFocusForwardId(btnGoBack, R.id.chkEnable);
				chkEnable.setNextFocusDownId(R.id.barBass);
				chkEnable.setNextFocusRightId(R.id.barBass);
				UI.setNextFocusForwardId(chkEnable, R.id.barBass);
				btnChangeEffect.setNextFocusUpId(R.id.barBass);
				btnChangeEffect.setNextFocusLeftId(R.id.barBass);
			}
		} else {
			barBass.setVisibility(View.GONE);
			btnChangeEffect.setText(R.string.go_to_bass_boost);
			final int bandCount = Equalizer.getBandCount();
			if (!Equalizer.isSupported()) {
				if (container != null)
					container.setVisibility(View.GONE);
				chkEnable.setVisibility(View.GONE);
				btnMenu.setVisibility(View.GONE);
				lblMsg.setText(R.string.equalizer_not_supported);
				lblMsg.setVisibility(View.VISIBLE);
				
				btnGoBack.setNextFocusRightId(R.id.btnChangeEffect);
				btnGoBack.setNextFocusDownId(R.id.btnChangeEffect);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnChangeEffect);
				btnChangeEffect.setNextFocusUpId(R.id.btnGoBack);
				btnChangeEffect.setNextFocusLeftId(R.id.btnGoBack);
			} else {
				lblMsg.setVisibility(View.GONE);
				chkEnable.setText(R.string.enable_equalizer);
				chkEnable.setMaxWidth(getDecorViewWidth() - (UI.defaultControlSize << 1) - (UI._8dp << 2));
				chkEnable.setVisibility(View.VISIBLE);
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
				rp.addRule(RelativeLayout.LEFT_OF, R.id.btnMenu);
				chkEnable.setLayoutParams(rp);
				chkEnable.setChecked(Equalizer.isEnabled());
				btnMenu.setVisibility(View.VISIBLE);
				int hMargin = ((UI.isLandscape || UI.isLargeScreen) ? UI.spToPxI(32) : UI.spToPxI(16));
				final int screenW = getDecorViewWidth();
				while (hMargin > 0 &&
						((bandCount * UI.defaultControlSize) + ((bandCount - 1) * hMargin)) > screenW)
					hMargin--;
				if (container == null) {
					container = new LinearLayout(ctx);
					rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
					rp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
					rp.addRule(RelativeLayout.ABOVE, R.id.btnChangeEffect);
					container.setLayoutParams(rp);
					container.setOrientation(LinearLayout.HORIZONTAL);
					container.setGravity(Gravity.CENTER_HORIZONTAL);
					for (int i = 0; i < bandCount; i++) {
						final LinearLayout bandContainer = new LinearLayout(ctx);
						bandContainer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
						bandContainer.setOrientation(LinearLayout.HORIZONTAL);
						
						final int level = Equalizer.getBandLevel(i);
						
						final BgSeekBar bar = new BgSeekBar(ctx);
						bar.setVertical(true);
						final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
						if (i > 0)
							p.leftMargin = hMargin;
						p.topMargin = UI._8dp;
						p.bottomMargin = UI._8dp;
						bar.setLayoutParams(p);
						bar.setMax((max - min) / 5);
						bar.setText(format(frequencies[i], level));
						bar.setKeyIncrement(20);
						bar.setValue(((level <= LevelThreshold) && (level >= -LevelThreshold)) ? (-min / 5) : ((level - min) / 5));
						bar.setOnBgSeekBarChangeListener(this);
						bar.setInsideList(true);
						if (UI.isLowDpiScreen && !UI.isLargeScreen)
							bar.setTextSizeIndex(1);
						bars[i] = bar;
						bar.setId(i + 1);
						bar.setNextFocusLeftId(i);
						bar.setNextFocusRightId(i + 2);
						UI.setNextFocusForwardId(bar, i + 2);
						bar.setNextFocusDownId(R.id.btnChangeEffect);
						bar.setNextFocusUpId(R.id.btnMenu);
						
						bandContainer.addView(bar);
						container.addView(bandContainer);
					}
					bars[0].setNextFocusLeftId(R.id.btnMenu);
					bars[bandCount - 1].setNextFocusRightId(R.id.btnChangeEffect);
					UI.setNextFocusForwardId(bars[bandCount - 1], R.id.btnChangeEffect);
					panelControls.addView(container);
				} else {
					for (int i = 0; i < bandCount; i++) {
						final BgSeekBar bar = bars[i];
						final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
						if (i > 0)
							p.leftMargin = hMargin;
						p.topMargin = UI._8dp;
						p.bottomMargin = UI._8dp;
						bar.setLayoutParams(p);
					}
					container.setVisibility(View.VISIBLE);
				}
				btnGoBack.setNextFocusRightId(R.id.chkEnable);
				btnGoBack.setNextFocusDownId(1);
				UI.setNextFocusForwardId(btnGoBack, R.id.chkEnable);
				chkEnable.setNextFocusRightId(R.id.btnMenu);
				chkEnable.setNextFocusDownId(1);
				UI.setNextFocusForwardId(chkEnable, R.id.btnMenu);
				btnMenu.setNextFocusDownId(bandCount);
				btnMenu.setNextFocusRightId(1);
				UI.setNextFocusForwardId(btnMenu, 1);
				btnChangeEffect.setNextFocusUpId(bandCount);
				btnChangeEffect.setNextFocusLeftId(bandCount);
			}
		}
	}
	
	@Override
	public void onFileSelected(int id, String path, String name) {
		if (id == MNU_LOADPRESET) {
			final SerializableMap opts = SerializableMap.deserialize(getApplication(), path);
			if (opts != null) {
				Equalizer.deserializePreset(opts);
				Equalizer.applyAllBandSettings();
				updateBars();
			}
		} else {
			final SerializableMap opts = new SerializableMap(Equalizer.getBandCount());
			Equalizer.serializePreset(opts);
			opts.serialize(getApplication(), path);
		}
	}
	
	@Override
	public void onAddClicked(int id, String path, String name) {
	}
	
	@Override
	public void onPlayClicked(int id, String path, String name) {
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_ENABLING_STEP_0:
			//don't even ask.......
			//(a few devices won't disable one effect while the other effect is enabled)
			Equalizer.release();
			MainHandler.sendMessage(this, MSG_ENABLING_STEP_1);
			break;
		case MSG_ENABLING_STEP_1:
			BassBoost.release();
			MainHandler.sendMessage(this, MSG_ENABLING_STEP_2);
			break;
		case MSG_ENABLING_STEP_2:
			final boolean enableEqualizer = (Player.bassBoostMode ? Equalizer.isEnabled() : chkEnable.isChecked());
			if (enableEqualizer && Player.getAudioSessionId() != -1) {
				try {
					Equalizer.initialize(Player.getAudioSessionId());
				} catch (Throwable ex) {
				}
			}
			try {
				Equalizer.setEnabled(enableEqualizer);
			} catch (Throwable ex) {
			}
			MainHandler.sendMessage(this, MSG_ENABLING_STEP_3);
			break;
		case MSG_ENABLING_STEP_3:
			final boolean enableBassBoost = (Player.bassBoostMode ? chkEnable.isChecked() : BassBoost.isEnabled());
			if (enableBassBoost && Player.getAudioSessionId() != -1) {
				try {
					BassBoost.initialize(Player.getAudioSessionId());
				} catch (Throwable ex) {
				}
			}
			try {
				BassBoost.setEnabled(enableBassBoost);
			} catch (Throwable ex) {
			}
			if (Player.bassBoostMode) {
				//something might have gone wrong...
				if (chkEnable.isChecked() != BassBoost.isEnabled())
					chkEnable.setChecked(BassBoost.isEnabled());
			} else {
				//something might have gone wrong...
				if (chkEnable.isChecked() != Equalizer.isEnabled())
					chkEnable.setChecked(Equalizer.isEnabled());
			}
			enablingEffect = false;
			break;
		}
		return true;
	}
}
