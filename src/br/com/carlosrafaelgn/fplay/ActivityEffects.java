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
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.BassBoost;
import br.com.carlosrafaelgn.fplay.playback.Equalizer;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.playback.Virtualizer;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgSeekBar;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.FrequencyResponseView;
import br.com.carlosrafaelgn.fplay.ui.ObservableLinearLayout;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.BgListItem3DDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;
import br.com.carlosrafaelgn.fplay.util.Timer;

public final class ActivityEffects extends ClientActivity implements Timer.TimerHandler, Runnable, View.OnClickListener, ObservableLinearLayout.OnSizeChangeListener, BgSeekBar.OnBgSeekBarChangeListener, ActivityFileSelection.OnFileSelectionListener, Player.PlayerObserver {
	private static final int LevelThreshold = 100, MNU_ZEROPRESET = 100, MNU_LOADPRESET = 101, MNU_SAVEPRESET = 102, MNU_AUDIOSINK_DEVICE = 103, MNU_AUDIOSINK_WIRE = 104, MNU_AUDIOSINK_BT = 105, MNU_AUDIOSINK_WIRE_MIC = 106;
	private static final int ACG_UPDATE_INTERVAL = 500;
	private LinearLayout panelControls, panelEqualizer;
	private ObservableLinearLayout panelLabels, panelBars;
	private FrequencyResponseView frequencyResponseView;
	private ViewGroup panelSecondary;
	private BgButton chkEqualizer, chkBass, chkVirtualizer, chkAGC;
	private TextView txtAGC;
	private Timer tmrAGC;
	private BgButton btnGoBack, btnAudioSink, btnMenu, btnChangeEffect;
	private int min, max, audioSink, storedAudioSink;
	private int[] frequencies;
	private double[] desiredFrequencies, desiredFrequencyGainsDB;
	private boolean enablingEffect, screenConsideredLarge, resizingEq;
	private TextView[] labels;
	private BgSeekBar[] bars;
	private BgSeekBar barBass, barVirtualizer;
	private StringBuilder txtBuilder;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.audio_effects);
	}

	private String formatEqualizerDescription(int frequencyIndex) {
		if (BuildConfig.X) {
			switch (frequencyIndex) {
			case 0:
				return "Pre";
			case 1:
				return "31 / 62 Hz";
			case 2:
				return "125 Hz";
			case 3:
				return "250 Hz";
			case 4:
				return "500 / 1k Hz";
			case 5:
				return "2k / 4k Hz";
			default:
				return "8k / 16k Hz";
			}
		} else {
			final int frequency = frequencies[frequencyIndex];
			if (frequency < 1000)
				return frequency + " Hz";
			else
				return UI.formatIntAsFloat(frequency / 100, false, true) + "k Hz";
		}
	}

	private String formatEqualizerLabel(int frequencyIndex) {
		if (BuildConfig.X) {
			switch (frequencyIndex) {
			case 0:
				return "Pre";
			case 1:
				return "31\n62";
			case 2:
				return "125";
			case 3:
				return "250";
			case 4:
				return "500\n1k";
			case 5:
				return "2k\n4k";
			default:
				return "8k\n16k";
			}
		} else {
			final int frequency = frequencies[frequencyIndex];
			return ((frequency < 1000) ?
				Integer.toString(frequency) :
				UI.formatIntAsFloat(frequency / 100, false, true) + "k");
		}
	}

	private String formatEqualizerLevel(int level) {
		return ((level >= 0) ?
			("+" + UI.formatIntAsFloat(level / 10, false, false) ) :
			UI.formatIntAsFloat(level / 10, false, false)) + " dB";
	}

	private String formatBassBoost(int strength) {
		if (txtBuilder == null)
			return "";
		txtBuilder.delete(0, txtBuilder.length());
		BassBoost.getStrengthString(txtBuilder, strength);
		return txtBuilder.toString();
	}

	private String formatVirtualizer(int strength) {
		if (txtBuilder == null)
			return "";
		txtBuilder.delete(0, txtBuilder.length());
		txtBuilder.append(strength / 10);
		txtBuilder.append('%');
		return txtBuilder.toString();
	}

	private String getAudioSinkDescription(int audioSink, boolean ellipsize) {
		String description = ((Player.localAudioSinkUsedInEffects == audioSink) ? "» " : "");
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			description += getText(R.string.earphones).toString();
			break;
		case Player.AUDIO_SINK_WIRE_MIC:
			description += getText(R.string.headset).toString();
			break;
		case Player.AUDIO_SINK_BT:
			description += getText(R.string.bluetooth).toString();
			break;
		default:
			description += getText(R.string.loudspeaker).toString();
			break;
		}
		if (Player.localAudioSinkUsedInEffects == audioSink)
			description += " «";
		if (ellipsize) {
			final int availWidth = UI.usableScreenWidth -
				(UI.extraSpacing ? (UI.controlMargin << 1) : 0) - //extra spacing in the header
				(UI.defaultControlSize << 1) - //btnGoBack and btnMenu
				(UI.controlMargin << 1) - //btnAudioSink's padding
				UI.defaultControlContentsSize - //btnAudioSink's TextIconDrawable
				UI.controlMargin; //bonus :)
			description = UI.ellipsizeText(description, UI._18sp, availWidth, false);
		}
		return description;
	}

	@Override
	public View getNullContextMenuView() {
		return btnMenu;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		UI.prepare(menu);
		if (view == btnAudioSink) {
			menu.add(0, MNU_AUDIOSINK_DEVICE, 0, getAudioSinkDescription(Player.AUDIO_SINK_DEVICE, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_DEVICE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, MNU_AUDIOSINK_WIRE, 1, getAudioSinkDescription(Player.AUDIO_SINK_WIRE, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_WIRE) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, MNU_AUDIOSINK_WIRE_MIC, 2, getAudioSinkDescription(Player.AUDIO_SINK_WIRE_MIC, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_WIRE_MIC) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
			menu.add(0, MNU_AUDIOSINK_BT, 3, getAudioSinkDescription(Player.AUDIO_SINK_BT, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_BT) ? UI.ICON_RADIOCHK24 : UI.ICON_RADIOUNCHK24));
		} else {
			menu.add(0, MNU_ZEROPRESET, 0, R.string.zero_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_REMOVE24));
			menu.add(0, MNU_LOADPRESET, 1, R.string.load_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_LOAD24));
			menu.add(0, MNU_SAVEPRESET, 2, R.string.save_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_SAVE24));
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		if (!isLayoutCreated())
			return true;
		switch (item.getItemId()) {
		case MNU_ZEROPRESET:
			if (enablingEffect)
				break;
			for (int i = Equalizer.getBandCount() - 1; i >= 0; i--)
				Equalizer.setBandLevel(i, 0, audioSink);
			BassBoost.setStrength(0, audioSink);
			Virtualizer.setStrength(0, audioSink);
			Player.commitAllEffects(audioSink);
			updateEffects();
			break;
		case MNU_LOADPRESET:
			if (enablingEffect)
				break;
			startActivity(ActivityFileSelection.createPresetSelector(getText(R.string.load_preset), MNU_LOADPRESET, false, false, this), 0, null, false);
			break;
		case MNU_SAVEPRESET:
			if (enablingEffect)
				break;
			startActivity(ActivityFileSelection.createPresetSelector(getText(R.string.save_preset), MNU_SAVEPRESET, true, false, this), 0, null, false);
			break;
		case MNU_AUDIOSINK_DEVICE:
			if (enablingEffect)
				break;
			audioSink = Player.AUDIO_SINK_DEVICE;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		case MNU_AUDIOSINK_WIRE:
			if (enablingEffect)
				break;
			audioSink = Player.AUDIO_SINK_WIRE;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		case MNU_AUDIOSINK_WIRE_MIC:
			if (enablingEffect)
				break;
			audioSink = Player.AUDIO_SINK_WIRE_MIC;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		case MNU_AUDIOSINK_BT:
			if (enablingEffect)
				break;
			audioSink = Player.AUDIO_SINK_BT;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		}
		return true;
	}
	
	@Override
	public void onClick(View view) {
		if (!isLayoutCreated())
			return;
		if (view == btnGoBack) {
			finish(0, view, true);
		} else if (view == btnAudioSink) {
			CustomContextMenu.openContextMenu(btnAudioSink, this);
		} else if (view == btnMenu) {
			CustomContextMenu.openContextMenu(btnMenu, this);
		} else if (view == btnChangeEffect) {
			Player.bassBoostMode = !Player.bassBoostMode;
			prepareViewForMode(false);
		} else if (view == chkEqualizer) {
			if (enablingEffect)
				return;
			if (!Equalizer.isSupported()) {
				chkEqualizer.setChecked(false);
				UI.toast(R.string.effect_not_supported);
				return;
			}
			enablingEffect = true;
			Player.enableEffects(chkEqualizer.isChecked(), chkBass.isChecked(), chkVirtualizer.isChecked(), audioSink, this);
		} else if (view == chkBass) {
			if (enablingEffect)
				return;
			if (!BassBoost.isSupported()) {
				chkBass.setChecked(false);
				UI.toast(R.string.effect_not_supported);
				return;
			}
			enablingEffect = true;
			Player.enableEffects(chkEqualizer.isChecked(), chkBass.isChecked(), chkVirtualizer.isChecked(), audioSink, this);
		} else if (view == chkVirtualizer) {
			if (enablingEffect)
				return;
			if (!Virtualizer.isSupported()) {
				chkVirtualizer.setChecked(false);
				UI.toast(R.string.effect_not_supported);
				return;
			}
			enablingEffect = true;
			Player.enableEffects(chkEqualizer.isChecked(), chkBass.isChecked(), chkVirtualizer.isChecked(), audioSink, this);
		} else if (view == chkAGC) {
			if (enablingEffect)
				return;
			enablingEffect = true;
			Player.enableAutomaticEffectsGain(chkAGC.isChecked(), this);
		}
	}
	
	@Override
	public void run() {
		if (!isLayoutCreated())
			return;
		if (resizingEq) {
			resizingEq = false;

			final int bandCount = Equalizer.getBandCount();

			if (panelLabels == null || labels == null || panelBars == null || bars == null || bars.length < bandCount)
				return;

			final int availableWidth = panelBars.getWidth();

			int hMargin = ((UI.isLandscape || screenConsideredLarge) ? (UI.controlLargeMargin << 1) : UI.controlLargeMargin);

			while (hMargin > UI.controlXtraSmallMargin && ((bandCount * UI.defaultControlSize) + ((bandCount - 1) * hMargin)) > availableWidth)
				hMargin--;

			int size = UI.defaultControlSize;
			if (hMargin <= UI.controlXtraSmallMargin) {
				//oops... the bars didn't fit inside the panel... we must adjust their width!
				hMargin = UI.controlXtraSmallMargin;
				while (size > UI._4dp && ((bandCount * size) + ((bandCount - 1) * hMargin)) > availableWidth)
					size--;
			}

			for (int i = 0; i < bandCount; i++) {
				final BgSeekBar bar = bars[i];
				LinearLayout.LayoutParams p = (LinearLayout.LayoutParams)bar.getLayoutParams();
				if (i > 0)
					p.leftMargin = hMargin;
				bar.setSize(size, true);
				bar.setLayoutParams(p);

				final TextView label = labels[i];
				p = (LinearLayout.LayoutParams)label.getLayoutParams();
				if (i > 0)
					p.leftMargin = hMargin;
				p.width = size;
				label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(UI._14sp, bar.getTextSize()));
				label.setLayoutParams(p);
			}
		}

		if (enablingEffect) {
			//the effects have just been reset!
			enablingEffect = false;
			chkEqualizer.setChecked(Equalizer.isEnabled(audioSink));
			chkBass.setChecked(BassBoost.isEnabled(audioSink));
			chkVirtualizer.setChecked(Virtualizer.isEnabled(audioSink));
			if (chkAGC != null)
				chkAGC.setChecked(Player.isAutomaticEffectsGainEnabled());
		}
	}

	@Override
	public void handleTimer(Timer timer, Object param) {
		updateAGCText();
	}

	private void initBarsAndFrequencies(int bandCount) {
		clearBarsAndFrequencies();
		min = Equalizer.getMinBandLevel();
		max = Equalizer.getMaxBandLevel();
		frequencies = new int[bandCount];
		labels = new TextView[bandCount];
		bars = new BgSeekBar[bandCount];
		for (int i = bandCount - 1; i >= 0; i--)
			frequencies[i] = Equalizer.getBandFrequency(i);
	}
	
	private void clearBarsAndFrequencies() {
		frequencies = null;
		if (labels != null) {
			for (int i = labels.length - 1; i >= 0; i--)
				labels[i] = null;
			labels = null;
		}
		if (bars != null) {
			for (int i = bars.length - 1; i >= 0; i--)
				bars[i] = null;
			bars = null;
		}
	}

	private int barToActualBassBoost(int barValue) {
		return (BuildConfig.X ? (barValue * 50) : (barValue * 10));
	}

	private int actualToBarBassBoost(int actualValue) {
		return (BuildConfig.X ? (actualValue / 50) : (actualValue / 10));
	}

	private int barToActualVirtualizer(int barValue) {
		return barValue * 10;
	}

	private int actualToBarVirtualizer(int actualValue) {
		return actualValue / 10;
	}

	private void updateAGCText() {
		if (txtAGC == null || txtBuilder == null)
			return;
		final int currentGain = Player.getCurrentAutomaticEffectsGainInMB();
		txtBuilder.delete(0, txtBuilder.length());
		txtBuilder.append(getText(R.string.current_gain));
		txtBuilder.append(' ');
		if (currentGain > -100)
			txtBuilder.append('-');
		UI.formatIntAsFloat(txtBuilder, currentGain, true, false);
		txtBuilder.append(" dB");
		txtAGC.setText(txtBuilder.toString());
	}

	@Override
	protected void onCreate() {
		txtBuilder = new StringBuilder(32);
	}

	@Override
	protected void onCreateLayout(boolean firstCreation) {
		final boolean addLargeEmptySpaces;
		if (UI.isLargeScreen) {
			final int _800dp = UI.dpToPxI(800);
			if (UI.isLandscape) {
				screenConsideredLarge = (UI.usableScreenWidth >= UI.dpToPxI(600));
				addLargeEmptySpaces = (UI.usableScreenWidth >= _800dp);
			} else {
				screenConsideredLarge = (UI.usableScreenHeight >= _800dp);
				addLargeEmptySpaces = true;
			}
		} else {
			screenConsideredLarge = false;
			addLargeEmptySpaces = false;
		}

		setContentView(screenConsideredLarge ? R.layout.activity_effects_ls : R.layout.activity_effects);
		audioSink = (storedAudioSink <= 0 ? Player.localAudioSinkUsedInEffects : storedAudioSink);
		storedAudioSink = audioSink;
		panelControls = findViewById(R.id.panelControls);
		panelControls.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
		panelEqualizer = findViewById(R.id.panelEqualizer);
		panelSecondary = findViewById(R.id.panelSecondary);
		final ViewGroup panelScroll = findViewById(R.id.panelScroll);
		final ViewGroup panelScrollContents = findViewById(R.id.panelScrollContents);
		if (UI.is3D) {
			final int padding = (addLargeEmptySpaces ? UI.controlLargeMargin :
				(UI.isLowDpiScreen ? UI.controlSmallMargin :
					UI.controlMargin));
			panelEqualizer.setPadding(padding, padding, padding, padding);
			if (panelScroll != null && panelScrollContents != null) {
				panelScroll.setPadding(0, padding, 0, padding);
				panelScrollContents.setPadding(padding, 0, padding, 0);
			} else {
				panelSecondary.setPadding(padding, padding, padding, padding);
			}
			panelEqualizer.setBackgroundDrawable(new BgListItem3DDrawable());
			panelSecondary.setBackgroundDrawable(new BgListItem3DDrawable());
		}
		btnGoBack = findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnAudioSink = findViewById(R.id.btnAudioSink);
		btnAudioSink.setOnClickListener(this);
		btnAudioSink.setDefaultHeight();
		btnAudioSink.setCompoundDrawables(new TextIconDrawable(UI.ICON_SETTINGS, UI.color_text), null, null, null);
		chkEqualizer = findViewById(R.id.chkEqualizer);
		chkEqualizer.setOnClickListener(this);
		chkEqualizer.setTextColor(UI.colorState_text_listitem_reactive);
		chkEqualizer.formatAsLabelledCheckBox();
		chkBass = findViewById(R.id.chkBass);
		chkBass.setOnClickListener(this);
		chkBass.setTextColor(UI.colorState_text_listitem_reactive);
		chkBass.formatAsLabelledCheckBox();
		chkVirtualizer = findViewById(R.id.chkVirtualizer);
		chkVirtualizer.setOnClickListener(this);
		chkVirtualizer.setTextColor(UI.colorState_text_listitem_reactive);
		chkVirtualizer.formatAsLabelledCheckBox();
		if (BuildConfig.X) {
			chkAGC = findViewById(R.id.chkAGC);
			chkAGC.setOnClickListener(this);
			chkAGC.setTextColor(UI.colorState_text_listitem_reactive);
			chkAGC.formatAsLabelledCheckBox();
			chkAGC.setVisibility(View.VISIBLE);
			chkAGC.setChecked(Player.isAutomaticEffectsGainEnabled());
			txtAGC = findViewById(R.id.txtAGC);
			UI.mediumText(txtAGC);
			txtAGC.setTextColor(UI.colorState_text_listitem_static);
			txtAGC.setCompoundDrawables(new TextIconDrawable(UI.ICON_CLIP, UI.color_text_listitem_secondary), null, null, null);
			txtAGC.setVisibility(View.VISIBLE);
			updateAGCText();
			tmrAGC = new Timer(this, "AGC Update Timer", false, true, false);
			tmrAGC.start(ACG_UPDATE_INTERVAL);
		}
		btnMenu = findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		btnMenu.setIcon(UI.ICON_MENU_MORE);
		btnChangeEffect = findViewById(R.id.btnChangeEffect);
		if (btnChangeEffect != null) {
			btnChangeEffect.setOnClickListener(this);
			btnChangeEffect.setCompoundDrawables(new TextIconDrawable(UI.ICON_EQUALIZER, UI.color_text_listitem), null, null, null);
			btnChangeEffect.setMinimumHeight(UI.defaultControlSize);
			btnChangeEffect.setTextColor(UI.colorState_text_listitem_reactive);
		} else {
			Player.bassBoostMode = false;
		}
		barBass = findViewById(R.id.barBass);
		barBass.setTextSizeIndex(0);
		barBass.setMax(actualToBarBassBoost(BassBoost.getMaxStrength()));
		barBass.setValue(actualToBarBassBoost(BassBoost.getStrength(audioSink)));
		barBass.setKeyIncrement(1);
		barBass.setOnBgSeekBarChangeListener(this);
		barBass.setInsideList(true);
		barBass.setAdditionalContentDescription(getText(R.string.bass_boost).toString());
		barVirtualizer = findViewById(R.id.barVirtualizer);
		barVirtualizer.setTextSizeIndex(0);
		barVirtualizer.setMax(actualToBarVirtualizer(Virtualizer.getMaxStrength()));
		barVirtualizer.setValue(actualToBarVirtualizer(Virtualizer.getStrength(audioSink)));
		barVirtualizer.setKeyIncrement(1);
		barVirtualizer.setOnBgSeekBarChangeListener(this);
		barVirtualizer.setInsideList(true);
		barVirtualizer.setAdditionalContentDescription(getText(R.string.virtualization).toString());
		if (chkAGC != null) {
			barVirtualizer.setNextFocusRightId(R.id.chkAGC);
			barVirtualizer.setNextFocusDownId(R.id.chkAGC);
			UI.setNextFocusForwardId(barVirtualizer, R.id.chkAGC);
			if (screenConsideredLarge) {
				btnGoBack.setNextFocusLeftId(R.id.chkAGC);
				btnMenu.setNextFocusUpId(R.id.chkAGC);
			}
		}
		LinearLayout.LayoutParams lp;
		if (screenConsideredLarge) {
			if (addLargeEmptySpaces)
				UI.prepareViewPaddingBasedOnScreenWidth(panelControls, 0, 0, 0);
			else
				panelControls.setPadding(0, 0, 0, !UI.isLandscape ? UI.controlMargin : UI.controlLargeMargin);
			if (!UI.isLandscape) {
				panelControls.setOrientation(LinearLayout.VERTICAL);
				panelControls.setWeightSum(0);
				final int margin = (addLargeEmptySpaces ? (UI.controlLargeMargin << 1) : UI.controlMargin);
				lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				lp.leftMargin = margin;
				lp.topMargin = margin >> 1;
				lp.rightMargin = margin;
				lp.bottomMargin = margin;
				panelSecondary.setLayoutParams(lp);
				lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0);
				lp.weight = 1;
				lp.leftMargin = margin;
				lp.topMargin = (addLargeEmptySpaces ? margin : UI.controlLargeMargin);
				lp.rightMargin = margin;
				lp.bottomMargin = margin >> 1;
				panelEqualizer.setLayoutParams(lp);
			}
			if (!addLargeEmptySpaces || BuildConfig.X) {
				lp = (LinearLayout.LayoutParams)barBass.getLayoutParams();
				lp.bottomMargin = UI.controlLargeMargin;
				barBass.setLayoutParams(lp);
			}
		} else {
			if (UI.isLandscape) {
				lp = (LinearLayout.LayoutParams)btnChangeEffect.getLayoutParams();
				lp.topMargin = 0;
				btnChangeEffect.setLayoutParams(lp);
				lp = (LinearLayout.LayoutParams)chkEqualizer.getLayoutParams();
				lp.bottomMargin = 0;
				chkEqualizer.setLayoutParams(lp);
				lp = (LinearLayout.LayoutParams)chkBass.getLayoutParams();
				lp.bottomMargin = 0;
				chkBass.setLayoutParams(lp);
				lp = (LinearLayout.LayoutParams)barBass.getLayoutParams();
				lp.bottomMargin = UI.controlMargin;
				barBass.setLayoutParams(lp);
				lp = (LinearLayout.LayoutParams)chkVirtualizer.getLayoutParams();
				lp.bottomMargin = 0;
				chkVirtualizer.setLayoutParams(lp);
				if (chkAGC != null) {
					lp = (LinearLayout.LayoutParams)chkAGC.getLayoutParams();
					lp.topMargin = UI.controlMargin;
					chkAGC.setLayoutParams(lp);
				}
				UI.prepareViewPaddingBasedOnScreenWidth(panelControls, UI.controlLargeMargin, UI.thickDividerSize, 0);
			} else {
				UI.prepareViewPaddingBasedOnScreenWidth(panelControls, UI.controlMargin, UI.controlMargin, 0);
			}
		}
		UI.prepareControlContainer(findViewById(R.id.panelTop), false, true);
		prepareViewForMode(true);
	}

	@Override
	protected void onResume() {
		Player.observer = this;
		if (tmrAGC != null)
			tmrAGC.start(ACG_UPDATE_INTERVAL);
	}

	@Override
	protected void onPause() {
		if (Player.observer == this)
			Player.observer = null;
		if (tmrAGC != null)
			tmrAGC.stop();
	}

	@Override
	protected void onOrientationChanged() {
		onCleanupLayout();
		onCreateLayout(false);
	}

	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		panelControls = null;
		panelEqualizer = null;
		panelSecondary = null;
		panelLabels = null;
		panelBars = null;
		frequencyResponseView = null;
		chkEqualizer = null;
		chkBass = null;
		chkVirtualizer = null;
		chkAGC = null;
		txtAGC = null;
		if (tmrAGC != null) {
			tmrAGC.release();
			tmrAGC = null;
		}
		btnGoBack = null;
		btnMenu = null;
		btnChangeEffect = null;
		clearBarsAndFrequencies();
	}
	
	@Override
	protected void onDestroy() {
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (!isLayoutCreated() || !fromUser)
			return;
		if (seekBar == barBass) {
			BassBoost.setStrength(barToActualBassBoost(value), audioSink);
			seekBar.setText(formatBassBoost(BassBoost.getStrength(audioSink)));
		} else if (seekBar == barVirtualizer) {
			Virtualizer.setStrength(barToActualVirtualizer(value), audioSink);
			seekBar.setText(formatVirtualizer(Virtualizer.getStrength(audioSink)));
		} else if (bars != null && frequencies != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				if (seekBar == bars[i]) {
					int level = (50 * value) + min;
					if (!usingKeys && (level < LevelThreshold) && (level > -LevelThreshold)) {
						level = 0;
						seekBar.setValue(-min / 50);
					}
					Equalizer.setBandLevel(i, level, audioSink);
					seekBar.setText(formatEqualizerLevel(Equalizer.getBandLevel(i, audioSink)));
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
		if (!isLayoutCreated())
			return;
		if (seekBar == barBass) {
			Player.commitBassBoost(audioSink);
			updateFrequencyResponse();
		} else if (seekBar == barVirtualizer) {
			Player.commitVirtualizer(audioSink);
			updateFrequencyResponse();
		} else if (bars != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				if (seekBar == bars[i]) {
					Player.commitEqualizer(i, audioSink);
					updateFrequencyResponse();
					return;
				}
			}
		}
	}
	
	private void updateEffects() {
		if (chkEqualizer != null)
			chkEqualizer.setChecked(Equalizer.isEnabled(audioSink));
		if (bars != null && frequencies != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				final BgSeekBar bar = bars[i];
				if (bar != null) {
					final int level = Equalizer.getBandLevel(i, audioSink);
					bars[i].setText(formatEqualizerLevel(level));
					bars[i].setValue(((level < LevelThreshold) && (level > -LevelThreshold)) ? (-min / 50) : ((level - min) / 50));
				}
			}
		}
		if (chkBass != null)
			chkBass.setChecked(BassBoost.isEnabled(audioSink));
		if (barBass != null) {
			final int strength = BassBoost.getStrength(audioSink);
			barBass.setValue(actualToBarBassBoost(strength));
			barBass.setText(formatBassBoost(strength));
		}
		if (chkVirtualizer != null)
			chkVirtualizer.setChecked(Virtualizer.isEnabled(audioSink));
		if (barVirtualizer != null) {
			final int strength = Virtualizer.getStrength(audioSink);
			barVirtualizer.setValue(actualToBarVirtualizer(strength));
			barVirtualizer.setText(formatVirtualizer(strength));
		}
		if (btnAudioSink != null)
			btnAudioSink.setText(getAudioSinkDescription(audioSink, true));
		updateFrequencyResponse();
	}

	private void updateFrequencyResponse() {
		if (BuildConfig.X) {
			if (desiredFrequencies == null || desiredFrequencyGainsDB == null) {
				//31.25 x x x 62.5 x x x 125 x x x 250 ... 8000 x x x 16000
				//the frequency should double every 4 samples (each step increases by 2 ^ (1/4))
				desiredFrequencies = new double[37];
				desiredFrequencyGainsDB = new double[37];
				desiredFrequencies[0] = 31.25;
				for (int i = 1; i < desiredFrequencies.length; i++)
					desiredFrequencies[i] = desiredFrequencies[i - 1] * 1.189207115002721;
				//fix round errors for all known frequencies :)
				desiredFrequencies[20] = 1000;
				for (int i = 16; i >= 0; i -= 4)
					desiredFrequencies[i] = Math.floor(desiredFrequencies[i + 4] * 0.5);
				for (int i = 24; i < desiredFrequencies.length; i += 4)
					desiredFrequencies[i] = desiredFrequencies[i - 4] * 2;
			}
		} else {
			return;
		}

		if (frequencyResponseView != null) {
			Equalizer.getRequencyResponse(audioSink, BassBoost.isEnabled(audioSink) ? BassBoost.getStrength(audioSink) : 0, desiredFrequencies, desiredFrequencyGainsDB);
			frequencyResponseView.setValues(desiredFrequencies, desiredFrequencyGainsDB);
		}
	}

	@Override
	public void onSizeChange(ObservableLinearLayout view, final int w, int h, int oldw, int oldh) {
		//we cannot change the layout inside onSizeChange(), that's why we need to postpone it
		//to a time in the future (as soon as possible, though)
		resizingEq = true;
		MainHandler.postToMainThread(this);
	}

	private void prepareViewForMode(boolean isCreatingLayout) {
		updateEffects();
		if (Player.bassBoostMode) {
			UI.animationReset();
			UI.animationAddViewToHide(panelEqualizer);
			UI.animationAddViewToShow(panelSecondary);
			UI.animationCommit(isCreatingLayout, null);
			btnGoBack.setNextFocusDownId(R.id.chkBass);
			btnAudioSink.setNextFocusDownId(R.id.chkBass);
			btnMenu.setNextFocusRightId(R.id.chkBass);
			btnMenu.setNextFocusDownId(R.id.chkBass);
			UI.setNextFocusForwardId(btnMenu, R.id.chkBass);
			if (chkAGC != null) {
				btnChangeEffect.setNextFocusUpId(R.id.chkAGC);
				btnChangeEffect.setNextFocusLeftId(R.id.chkAGC);
			} else {
				btnChangeEffect.setNextFocusUpId(R.id.barVirtualizer);
				btnChangeEffect.setNextFocusLeftId(R.id.barVirtualizer);
			}
		} else {
			if (btnChangeEffect != null) {
				UI.animationReset();
				UI.animationAddViewToHide(panelSecondary);
				UI.animationAddViewToShow(panelEqualizer);
				UI.animationCommit(isCreatingLayout, null);
			}
			chkEqualizer.setChecked(Equalizer.isEnabled(audioSink));
			
			final int bandCount = Equalizer.getBandCount();
			if (labels == null || bars == null || frequencies == null || bars.length < bandCount || frequencies.length < bandCount)
				initBarsAndFrequencies(bandCount);

			final boolean frequencyResponseViewVisible = (BuildConfig.X && (!UI.isLandscape || screenConsideredLarge));

			if (panelLabels == null) {
				final Context ctx = getHostActivity();

				final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				layoutParams.bottomMargin = UI.controlMargin;
				frequencyResponseView = new FrequencyResponseView(getHostActivity());
				frequencyResponseView.setLayoutParams(layoutParams);
				frequencyResponseView.setVisibility(frequencyResponseViewVisible ? View.VISIBLE : View.GONE);
				panelEqualizer.addView(frequencyResponseView);
				updateFrequencyResponse();

				panelLabels = new ObservableLinearLayout(ctx);
				panelLabels.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				panelLabels.setGravity(Gravity.CENTER);
				panelLabels.setOrientation(LinearLayout.HORIZONTAL);

				panelBars = new ObservableLinearLayout(ctx);
				panelBars.setOnSizeChangeListener(this);
				panelBars.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
				panelBars.setGravity(Gravity.CENTER_HORIZONTAL);
				panelBars.setOrientation(LinearLayout.HORIZONTAL);

				for (int i = 0; i < bandCount; i++) {
					final TextView label = new TextView(ctx);
					labels[i] = label;
					label.setLayoutParams(new LinearLayout.LayoutParams(UI.defaultControlContentsSize, ViewGroup.LayoutParams.WRAP_CONTENT));
					UI.smallText(label);
					label.setTextColor(UI.colorState_text_listitem_static);
					label.setGravity(Gravity.CENTER_HORIZONTAL);
					label.setText(formatEqualizerLabel(i));

					panelLabels.addView(label);

					final int level = Equalizer.getBandLevel(i, audioSink);
					final BgSeekBar bar = new BgSeekBar(ctx);
					bars[i] = bar;
					bar.setVertical(true);
					bar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
					bar.setMax((max - min) / 50);
					bar.setAdditionalContentDescription(formatEqualizerDescription(i));
					bar.setText(formatEqualizerLevel(level));
					bar.setKeyIncrement(1);
					bar.setValue(((level < LevelThreshold) && (level > -LevelThreshold)) ? (-min / 50) : ((level - min) / 50));
					bar.setOnBgSeekBarChangeListener(this);
					bar.setInsideList(true);
					bar.setId(i + 1);
					bar.setNextFocusLeftId(i);
					bar.setNextFocusRightId(i + 2);
					UI.setNextFocusForwardId(bar, i + 2);
					bar.setNextFocusDownId(R.id.btnChangeEffect);
					bar.setNextFocusUpId(R.id.chkEqualizer);

					panelBars.addView(bar);
				}
				if (bars != null && bars.length > 0)
					bars[0].setNextFocusLeftId(R.id.chkEqualizer);
				panelEqualizer.addView(panelLabels);
				panelEqualizer.addView(panelBars);
			} else if (frequencyResponseView != null) {
				frequencyResponseView.setVisibility(frequencyResponseViewVisible ? View.VISIBLE : View.GONE);
			}

			if (btnChangeEffect != null) {
				if (bars != null && bars.length > 0) {
					bars[bandCount - 1].setNextFocusRightId(R.id.btnChangeEffect);
					UI.setNextFocusForwardId(bars[bandCount - 1], R.id.btnChangeEffect);
				}
				btnGoBack.setNextFocusDownId(R.id.chkEqualizer);
				btnAudioSink.setNextFocusDownId(R.id.chkEqualizer);
				btnMenu.setNextFocusRightId(R.id.chkEqualizer);
				btnMenu.setNextFocusDownId(R.id.chkEqualizer);
				UI.setNextFocusForwardId(btnMenu, R.id.chkEqualizer);
				btnChangeEffect.setNextFocusUpId(bandCount);
				btnChangeEffect.setNextFocusLeftId(bandCount);
			} else {
				if (bars != null && bars.length > 0) {
					bars[bandCount - 1].setNextFocusRightId(R.id.chkBass);
					UI.setNextFocusForwardId(bars[bandCount - 1], R.id.chkBass);
				}
				chkBass.setNextFocusLeftId(bandCount);
			}
			final int id = 1;
			chkEqualizer.setNextFocusRightId(id);
			chkEqualizer.setNextFocusDownId(id);
			UI.setNextFocusForwardId(chkEqualizer, 1);
		}
	}
	
	@Override
	public void onFileSelected(int id, FileSt file) {
		if (id == MNU_LOADPRESET) {
			final SerializableMap opts = SerializableMap.deserialize(file.path);
			if (opts != null) {
				Equalizer.deserialize(opts, audioSink);
				BassBoost.deserialize(opts, audioSink);
				Virtualizer.deserialize(opts, audioSink);
				Player.commitAllEffects(audioSink);
				updateEffects();
			}
		} else {
			final SerializableMap opts = new SerializableMap(Equalizer.getBandCount() << 1);
			Equalizer.serialize(opts, audioSink);
			BassBoost.serialize(opts, audioSink);
			Virtualizer.serialize(opts, audioSink);
			opts.serialize(file.path);
		}
	}

	@Override
	public void onAddClicked(int id, FileSt file) {
	}

	@Override
	public void onPlayClicked(int id, FileSt file) {
	}

	@Override
	public boolean onDeleteClicked(int id, FileSt file) {
		return false;
	}

	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, boolean preparingHasChanged, Throwable ex) {
	}

	@Override
	public void onPlayerMetadataChanged(Song currentSong) {
	}

	@Override
	public void onPlayerControlModeChanged(boolean controlMode) {
	}

	@Override
	public void onPlayerGlobalVolumeChanged(int volume) {
	}

	@Override
	public void onPlayerAudioSinkChanged(boolean firstNotification) {
		audioSink = Player.localAudioSinkUsedInEffects;
		storedAudioSink = audioSink;
		updateEffects();
	}

	@Override
	public void onPlayerMediaButtonPrevious() {
	}

	@Override
	public void onPlayerMediaButtonNext() {
	}
}
