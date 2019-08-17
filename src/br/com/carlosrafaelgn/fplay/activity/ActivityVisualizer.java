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
package br.com.carlosrafaelgn.fplay.activity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.playback.context.VisualizerService;
import br.com.carlosrafaelgn.fplay.plugin.SongInfo;
import br.com.carlosrafaelgn.fplay.plugin.Visualizer;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgColorStateList;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.InterceptableLayout;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;

public final class ActivityVisualizer extends Activity implements br.com.carlosrafaelgn.fplay.plugin.VisualizerService.Observer, MainHandler.Callback, Player.PlayerObserver, Player.PlayerDestroyedObserver, View.OnClickListener, MenuItem.OnMenuItemClickListener, OnCreateContextMenuListener, View.OnTouchListener, Timer.TimerHandler {
	@SuppressLint("InlinedApi")
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static final class SystemUIObserver implements View.OnSystemUiVisibilityChangeListener {
		private View decor;
		private MainHandler.Callback callback;
		
		public SystemUIObserver(View decor, MainHandler.Callback callback) {
			this.decor = decor;
			this.callback = callback;
		}
		
		@Override
		public void onSystemUiVisibilityChange(int visibility) {
			if (decor == null)
				return;
			if (callback != null)
				MainHandler.sendMessage(callback, MSG_SYSTEM_UI_CHANGED, visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, 0);
		}
		
		public void hide() {
			if (decor == null)
				return;
			try {
				decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
						View.SYSTEM_UI_FLAG_FULLSCREEN |
						View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
						View.SYSTEM_UI_FLAG_LOW_PROFILE |
						View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
						View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
						View.SYSTEM_UI_FLAG_IMMERSIVE);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		
		public void show() {
			if (decor == null)
				return;
			try {
				decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
						View.SYSTEM_UI_FLAG_FULLSCREEN |
						View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
						//View.SYSTEM_UI_FLAG_LOW_PROFILE |
						View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
						//View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
						View.SYSTEM_UI_FLAG_IMMERSIVE);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		
		public void prepare() {
			show();
			if (decor == null)
				return;
			try {
				decor.setOnSystemUiVisibilityChangeListener(this);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		
		public void cleanup() {
			if (decor != null) {
				try {
					decor.setOnSystemUiVisibilityChangeListener(null);
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				decor = null;
			}
			callback = null;
		}
	}
	
	private static final int MSG_HIDE = 0x0400;
	private static final int MSG_SYSTEM_UI_CHANGED = 0x0401;
	private static final int MNU_ORIENTATION = 100;
	private static final int MNU_DUMMY = 101;
	private SongInfo songInfo;
	private Visualizer visualizer;
	private VisualizerService visualizerService;
	private UI.DisplayInfo info;
	private InterceptableLayout panelControls;
	private RelativeLayout panelTop;
	private LinearLayout panelSecondary;
	private BgButton btnGoBack, btnPrev, btnPlay, btnNext, btnMenu;
	private TextView lblTitle;
	private boolean visualizerViewFullscreen, visualizerRequiresHiddenControls, isWindowFocused, panelTopWasVisibleOk, visualizerPaused;
	private float panelTopAlpha;
	private int version, panelTopLastTime, panelTopHiding, requiredOrientation;
	private Object systemUIObserver;
	private Timer uiAnimTimer;
	private BgColorStateList buttonColor, lblColor;
	private ColorDrawable panelTopBackground;

	private void hideAllUIDelayed() {
		if (!visualizerRequiresHiddenControls)
			return;
		version++;
		MainHandler.removeMessages(this, MSG_HIDE);
		MainHandler.sendMessageAtTime(this, MSG_HIDE, version, 0, SystemClock.uptimeMillis() + 4000);
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void prepareSystemUIObserver() {
		if (!visualizerRequiresHiddenControls)
			return;
		if (systemUIObserver == null)
			systemUIObserver = new SystemUIObserver(getWindow().getDecorView(), this);
		((SystemUIObserver)systemUIObserver).prepare();
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void hideSystemUI() {
		if (visualizerRequiresHiddenControls && systemUIObserver != null)
			((SystemUIObserver)systemUIObserver).hide();
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void showSystemUI() {
		if (visualizerRequiresHiddenControls && systemUIObserver != null)
			((SystemUIObserver)systemUIObserver).show();
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void cleanupSystemUIObserver() {
		if (visualizerRequiresHiddenControls && systemUIObserver != null) {
			((SystemUIObserver)systemUIObserver).cleanup();
			systemUIObserver = null;
		}
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (btnMenu != null)
			CustomContextMenu.openContextMenu(btnMenu, this);
		return false;
	}

	private void updateTitle() {
		if (lblTitle == null)
			return;
		if (Player.localSong == null) {
			lblTitle.setText(getText(R.string.nothing_playing));
		} else {
			String txt = Player.getCurrentTitle(Player.isPreparing());
			if (Player.localSong.extraInfo != null && Player.localSong.extraInfo.length() > 0 && (Player.localSong.extraInfo.length() > 1 || Player.localSong.extraInfo.charAt(0) != '-'))
				txt += "\n" + Player.localSong.extraInfo;
			lblTitle.setText(txt);
		}
	}

	private void prepareViews(boolean updateInfo) {
		if (info == null)
			return;
		
		if (updateInfo)
			updateInfoWithConfiguration(null);

		panelTopHiding = 0;
		panelTopAlpha = 1.0f;

		lblTitle.setPadding(0, UI.controlSmallMargin, 0, UI.controlSmallMargin);

		//panelTop.setLayoutParams(new InterceptableLayout.LayoutParams(info.isLandscape ? InterceptableLayout.LayoutParams.WRAP_CONTENT : InterceptableLayout.LayoutParams.MATCH_PARENT, info.isLandscape ? InterceptableLayout.LayoutParams.MATCH_PARENT : InterceptableLayout.LayoutParams.WRAP_CONTENT));
		panelTop.setLayoutParams(new InterceptableLayout.LayoutParams(InterceptableLayout.LayoutParams.MATCH_PARENT, InterceptableLayout.LayoutParams.WRAP_CONTENT));
		final int margin = (info.isLandscape ? UI.defaultControlSize : 0);
		if (UI.extraSpacing)
			panelTop.setPadding(UI.controlMargin + margin, UI.controlMargin, UI.controlMargin + margin, UI.controlMargin);
		else
			panelTop.setPadding(margin, 0, margin, 0);
		
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)btnPrev.getLayoutParams();
		//lp.rightMargin = (info.isLandscape ? 0 : UI._16dp);
		//lp.bottomMargin = (info.isLandscape ? UI._16dp : 0);
		lp.rightMargin = UI.controlLargeMargin;
		lp.bottomMargin = 0;
		btnPrev.setLayoutParams(lp);
		btnPrev.setIcon(UI.ICON_PREV);
		
		lp = (LinearLayout.LayoutParams)btnPlay.getLayoutParams();
		//lp.rightMargin = (info.isLandscape ? 0 : UI._16dp);
		//lp.bottomMargin = (info.isLandscape ? UI._16dp : 0);
		lp.rightMargin = UI.controlLargeMargin;
		lp.bottomMargin = 0;
		btnPlay.setLayoutParams(lp);
		btnPlay.setIcon(Player.localPlaying ? UI.ICON_PAUSE : UI.ICON_PLAY);
		
		RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		//p.addRule(info.isLandscape ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		panelSecondary.setLayoutParams(p);
		panelSecondary.setOrientation(LinearLayout.HORIZONTAL);
		
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		//p.addRule(info.isLandscape ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		btnMenu.setLayoutParams(p);
		btnMenu.setIcon(UI.ICON_MENU_MORE);
		
		if (visualizer != null) {
			final InterceptableLayout.LayoutParams ip;
			if (visualizerViewFullscreen) {
				ip = new InterceptableLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
			} else {
				if (visualizerRequiresHiddenControls) {
					final Point pt = (Point)visualizer.getDesiredSize(info.usableScreenWidth, info.usableScreenHeight);
					ip = new InterceptableLayout.LayoutParams(pt.x, pt.y);
					ip.addRule(InterceptableLayout.CENTER_IN_PARENT, InterceptableLayout.TRUE);
				} else {
					ip = new InterceptableLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
					ip.addRule(InterceptableLayout.BELOW, R.id.panelTop);
					ip.addRule(InterceptableLayout.ALIGN_PARENT_BOTTOM, InterceptableLayout.TRUE);
				}
			}
			((View)visualizer).setLayoutParams(ip);
		}
		
		panelControls.requestLayout();
	}

	private void updateInfoWithConfiguration(Configuration configuration) {
		if (configuration == null)
			configuration = getResources().getConfiguration();
		if (configuration != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			info.getInfo(this, UI.dpToPxI(configuration.screenWidthDp), UI.dpToPxI(configuration.screenHeightDp));
		else
			info.getInfo(this, 0, 0);
	}

	//replace onKeyDown with dispatchKeyEvent + event.getAction() + event.getKeyCode()?!?!?!
	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
		//
		//Allowing applications to play nice(r) with each other: Handling remote control buttons
		//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
		//
		//...In a media playback application, this is used to react to headset button
		//presses when your activity doesn’t have the focus. For when it does, we override
		//the Activity.onKeyDown() or onKeyUp() methods for the user interface to trap the
		//headset button-related events...
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) {
				Player.handleMediaButton(keyCode);
				return true;
			}
			break;
		default:
			if ((event.getRepeatCount() == 0) && Player.handleMediaButton(keyCode))
				return true;
			break;
		}
		if (panelTop != null && !(panelTopWasVisibleOk = (panelTopHiding == 0 && panelTop.getVisibility() == View.VISIBLE)))
			showPanelTop(true);
		hideAllUIDelayed();
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		return (Player.isMediaButton(keyCode) || super.onKeyLongPress(keyCode, event));
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		return (Player.isMediaButton(keyCode) || super.onKeyUp(keyCode, event));
	}

	@SuppressLint("InlinedApi")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(null);

		if (UI.forcedLocale != UI.LOCALE_NONE)
			UI.reapplyForcedLocale(this);

		songInfo = new SongInfo();

		if (UI.colorState_text_visualizer_reactive == null) {
			buttonColor = new BgColorStateList(0xffffffff, UI.color_text_selected);
			lblColor = new BgColorStateList(0xffffffff, 0xffffffff);
		} else {
			buttonColor = new BgColorStateList(UI.colorState_text_visualizer_reactive.getDefaultColor(), UI.color_text_selected);
			lblColor = new BgColorStateList(UI.colorState_text_visualizer_reactive.getDefaultColor(), UI.colorState_text_visualizer_reactive.getDefaultColor());
		}

		isWindowFocused = true;

		info = new UI.DisplayInfo();

		//whenever the activity is being displayed, the volume keys must control
		//the music volume and nothing else!
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		Player.addDestroyedObserver(this);

		String name = null;
		final Intent si = getIntent();
		if (si != null && (name = si.getStringExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME)) != null) {
			if (!name.startsWith("br.com.carlosrafaelgn.fplay"))
				name = null;
		}
		if (name != null) {
			try {
				final Class<?> clazz = Class.forName(name);
				if (clazz != null) {
					try {
						updateInfoWithConfiguration(null);
						visualizer = (Visualizer)clazz.getConstructor(Activity.class, boolean.class, Intent.class).newInstance(this, info.isLandscape, si);
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}

		final boolean visualizerRequiresThread;
		if (visualizer != null) {
			requiredOrientation = visualizer.requiredOrientation();
			visualizerRequiresHiddenControls = visualizer.requiresHiddenControls();
			visualizerRequiresThread = (visualizer.requiredDataType() != Visualizer.DATA_NONE);
		} else {
			requiredOrientation = Visualizer.ORIENTATION_NONE;
			visualizerRequiresHiddenControls = false;
			visualizerRequiresThread = false;
		}

		setRequestedOrientation((requiredOrientation == Visualizer.ORIENTATION_NONE) ? (UI.visualizerPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) : (requiredOrientation == Visualizer.ORIENTATION_PORTRAIT ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));

		getWindow().setBackgroundDrawable(new ColorDrawable(UI.color_visualizer565));
		//keep the screen always on while the visualizer is active
		if (visualizerRequiresHiddenControls) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				//WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
				WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
			//getWindow().requestFeature(Window.FEATURE_NO_TITLE |
			//	Window.FEATURE_ACTION_MODE_OVERLAY);
		} else {
			if (UI.allowPlayerAboveLockScreen)
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
			else
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
			//Apparently, Bluetooth stops transmitting when the screen is off :(
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			//if (UI.keepScreenOn)
			//	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			//else
			//	getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			prepareSystemUIObserver();
		
		setContentView(R.layout.activity_visualizer);
		
		panelControls = findViewById(R.id.panelControls);
		panelControls.setOnClickListener(this);
		panelControls.setInterceptedTouchEventListener(this);
		panelTop = findViewById(R.id.panelTop);
		panelSecondary = findViewById(R.id.panelSecondary);
		btnGoBack = findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnPrev = findViewById(R.id.btnPrev);
		btnPrev.setOnClickListener(this);
		btnPlay = findViewById(R.id.btnPlay);
		btnPlay.setOnClickListener(this);
		btnNext = findViewById(R.id.btnNext);
		btnNext.setOnClickListener(this);
		btnNext.setIcon(UI.ICON_NEXT);
		btnMenu = findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		lblTitle = findViewById(R.id.lblTitle);
		UI.mediumText(lblTitle);
		updateTitle();

		//if (UI.extraSpacing)
		//	panelTop.setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);

		if (visualizer != null) {
			visualizerViewFullscreen = visualizer.isFullscreen();
			((View)visualizer).setOnClickListener(this);
			panelControls.addView((View)visualizer);
			panelTop.bringToFront();
		}
		
		prepareViews(true);

		if (visualizerRequiresHiddenControls) {
			panelTopBackground = new ColorDrawable(UI.color_visualizer565);
			panelTopBackground.setAlpha(255 >> 1);
			panelTop.setBackgroundDrawable(panelTopBackground);
		}
		btnGoBack.setTextColor(buttonColor);
		btnPrev.setTextColor(buttonColor);
		btnPlay.setTextColor(buttonColor);
		btnNext.setTextColor(buttonColor);
		btnMenu.setTextColor(buttonColor);
		lblTitle.setTextColor(lblColor);
		
		if (!btnGoBack.isInTouchMode())
			btnGoBack.requestFocus();

		visualizerService = null;
		if (visualizer != null) {
			visualizerPaused = false;
			visualizer.onActivityResume();
			if (!visualizerRequiresThread)
				visualizer.load();
			else
				visualizerService = new VisualizerService(visualizer, this);
		}

		uiAnimTimer = (visualizerRequiresHiddenControls ? new Timer(this, "UI Anim Timer", false, true, false) : null);
		
		hideAllUIDelayed();
	}
	
	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.clear();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (info == null)
			return;
		final boolean i = info.isLandscape;
		final int w = info.usableScreenWidth, h = info.usableScreenHeight;
		updateInfoWithConfiguration(newConfig);
		if (i != info.isLandscape || w != info.usableScreenWidth || h != info.usableScreenHeight) {
			final Visualizer v = visualizer;
			if (v != null)
				v.configurationChanged(info.isLandscape);
			prepareViews(false);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				prepareSystemUIObserver();
			hideAllUIDelayed();
			System.gc();
		}
	}

	@Override
	protected void onStop() {
		//changed from onPaused() to onStop()
		//https://developer.android.com/guide/topics/ui/multi-window.html#lifecycle
		//In multi-window mode, an app can be in the paused state and still be visible to the user.
		if (visualizer != null && !visualizerPaused) {
			visualizerPaused = true;
			visualizer.onActivityPause();
		}
		if (Player.observer == this)
			Player.observer = null;
		if (visualizerService != null)
			visualizerService.pause();
		Player.setAppNotInForeground(true);
		super.onStop();
	}
	
	@Override
	protected void onResume() {
		Player.setAppNotInForeground(false);
		Player.observer = this;
		if (visualizerService != null)
			visualizerService.resetAndResume();
		onPlayerChanged(Player.localSong, true, true, null);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			prepareSystemUIObserver();
		if (visualizer != null && visualizerPaused) {
			visualizerPaused = false;
			visualizer.onActivityResume();
		}
		super.onResume();
	}
	
	private void finalCleanup() {
		Player.removeDestroyedObserver(this);
		if (visualizerService != null) {
			visualizerService.destroy();
			visualizerService = null;
		} else if (visualizer != null) {
			visualizer.cancelLoading();
			visualizer.release();
			onFinalCleanup();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			cleanupSystemUIObserver();
		if (uiAnimTimer != null)
			uiAnimTimer.stop();
		info = null;
		panelControls = null;
		panelTop = null;
		panelSecondary = null;
		btnGoBack = null;
		btnPrev = null;
		btnPlay = null;
		btnNext = null;
		btnMenu = null;
		lblTitle = null;
		uiAnimTimer = null;
		buttonColor = null;
		lblColor = null;
		panelTopBackground = null;
		songInfo = null;
	}
	
	@Override
	protected void onDestroy() {
		finalCleanup();
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		final Visualizer v = visualizer;
		if (v != null)
			v.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onFailure() {
		UI.toast(R.string.visualizer_not_supported);
	}

	@Override
	public void onFinalCleanup() {
		if (visualizer != null) {
			if (!visualizerPaused) {
				visualizerPaused = true;
				visualizer.onActivityPause();
			}
			visualizer.releaseView();
			visualizer = null;
		}
	}

	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, boolean preparingHasChanged, Throwable ex) {
		if (visualizerService != null) {
			if (!songHasChanged && Player.localPlaying)
				visualizerService.resetAndResume();
			else
				visualizerService.resume();
			visualizerService.playingChanged();
		}
		if (btnPlay != null) {
			btnPlay.setText(Player.localPlaying ? UI.ICON_PAUSE : UI.ICON_PLAY);
			btnPlay.setContentDescription(getText(Player.localPlaying ? R.string.pause : R.string.play));
		}
		if (songHasChanged || preparingHasChanged)
			updateTitle();
		final Visualizer v = visualizer;
		final SongInfo s = songInfo;
		if (v != null && s != null)
			v.onPlayerChanged((currentSong == null) ? null : currentSong.info(s), songHasChanged, ex);
	}

	@Override
	public void onPlayerMetadataChanged(Song currentSong) {
		updateTitle();
	}

	@Override
	public void onPlayerControlModeChanged(boolean controlMode) {
	}
	
	@Override
	public void onPlayerGlobalVolumeChanged(int volume) {
	}
	
	@Override
	public void onPlayerAudioSinkChanged() {
		if (visualizerService != null)
			visualizerService.resetAndResume();
	}
	
	@Override
	public void onPlayerMediaButtonPrevious() {
	}
	
	@Override
	public void onPlayerMediaButtonNext() {
	}
	
	@Override
	public void onPlayerDestroyed() {
		finalCleanup();
		finish();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (info == null)
			return;
		if (UI.forcedLocale != UI.LOCALE_NONE)
			UI.reapplyForcedLocale(this);
		UI.prepare(menu);
		boolean firstItem = false;
		if (!UI.isChromebook && requiredOrientation == Visualizer.ORIENTATION_NONE) {
			menu.add(0, MNU_ORIENTATION, 0, UI.visualizerPortrait ? R.string.landscape : R.string.portrait)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_ORIENTATION));
			firstItem = true;
		}
		final Visualizer v = visualizer;
		if (v != null)
			v.onCreateContextMenu(menu);
		if (firstItem && menu.size() > 1)
			UI.separator(menu, 1, 0);
		if (menu.size() < 1)
			menu.add(0, MNU_DUMMY, 0, R.string.empty_list)
				.setOnMenuItemClickListener(this);
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		if (info == null)
			return true;
		switch (item.getItemId()) {
		case MNU_ORIENTATION:
			UI.visualizerPortrait = !UI.visualizerPortrait;
			setRequestedOrientation(UI.visualizerPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		}
		return true;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			finish();
		} else if (view == btnPrev) {
			Player.previous();
			if (visualizerService != null)
				visualizerService.resetAndResume();
		} else if (view == btnPlay) {
			Player.playPause();
		} else if (view == btnNext) {
			Player.next();
			if (visualizerService != null)
				visualizerService.resetAndResume();
		} else if (view == btnMenu) {
			onPrepareOptionsMenu(null);
		} else if (view == visualizer || view == panelControls) {
			if (visualizer != null && panelTopWasVisibleOk)
				visualizer.onClick();
		}
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		if ((isWindowFocused = hasFocus))
			hideAllUIDelayed();
		super.onWindowFocusChanged(hasFocus);
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_POINTER_DOWN:
			if (panelTop != null && !(panelTopWasVisibleOk = (panelTopHiding == 0 && panelTop.getVisibility() == View.VISIBLE)))
				showPanelTop(true);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				showSystemUI();
			hideAllUIDelayed();
			break;
		}
		return false;
	}
	
	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_HIDE:
			if (msg.arg1 != version || !isWindowFocused)
				break;
			showPanelTop(false);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				hideSystemUI();
			break;
		case MSG_SYSTEM_UI_CHANGED:
			final boolean show = (msg.arg1 == 0);
			showPanelTop(show);
			if (show)
				hideAllUIDelayed();
			break;
		}
		return true;
	}

	private void showPanelTop(boolean show) {
		if (panelTop == null || uiAnimTimer == null)
			return;
		if (show) {
			if (panelTop.getVisibility() != View.VISIBLE)
				panelTop.setVisibility(View.VISIBLE);
			panelTopHiding = 1;
			if (!uiAnimTimer.isAlive()) {
				panelTopLastTime = (int)SystemClock.uptimeMillis();
				uiAnimTimer.start(16);
			}
		} else {
			if (panelTop.getVisibility() == View.GONE)
				return;
			panelTopHiding = -1;
			if (!uiAnimTimer.isAlive()) {
				panelTopLastTime = (int)SystemClock.uptimeMillis();
				uiAnimTimer.start(16);
			}
		}
	}
	
	@Override
	public void handleTimer(Timer timer, Object param) {
		if (panelTop == null || uiAnimTimer == null || info == null)
			return;
		final int now = (int)SystemClock.uptimeMillis();
		final float delta = (float)(now - panelTopLastTime) * 0.001953125f;
		panelTopLastTime = now;
		if (panelTopHiding < 0) {
			panelTopAlpha -= delta;
			if (panelTopAlpha <= 0.0f) {
				panelTopHiding = 0;
				panelTopAlpha = 0.0f;
				uiAnimTimer.stop();
				panelTop.setVisibility(View.GONE);
			}
		} else {
			panelTopAlpha += delta;
			if (panelTopAlpha >= 1.0f) {
				panelTopHiding = 0;
				panelTopAlpha = 1.0f;
				uiAnimTimer.stop();
			}
		}
		
		if (panelTopAlpha != 0.0f) {
			final int c = (int)(255.0f * panelTopAlpha);
			if (panelTopBackground != null)
				panelTopBackground.setAlpha(c >> 1);
			buttonColor.setNormalColorAlpha(c);
			lblColor.setNormalColorAlpha(c);
			if (btnGoBack != null)
				btnGoBack.setTextColor(buttonColor);
			if (btnPrev != null)
				btnPrev.setTextColor(buttonColor);
			if (btnPlay != null)
				btnPlay.setTextColor(buttonColor);
			if (btnNext != null)
				btnNext.setTextColor(buttonColor);
			if (btnMenu != null)
				btnMenu.setTextColor(buttonColor);
			if (lblTitle != null)
				lblTitle.setTextColor(lblColor);
		}
	}
}
