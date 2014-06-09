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

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;
import br.com.carlosrafaelgn.fplay.visualizer.Visualizer;
import br.com.carlosrafaelgn.fplay.visualizer.VisualizerView;

public final class ActivityVisualizer extends Activity implements Runnable, Player.PlayerObserver, Player.PlayerDestroyedObserver, View.OnClickListener, MenuItem.OnMenuItemClickListener, OnCreateContextMenuListener {
	private static final int MNU_ORIENTATION = 100;
	private android.media.audiofx.Visualizer fxVisualizer;
	private Visualizer visualizer;
	private UI.DisplayInfo info;
	private RelativeLayout panelControls, panelTop, panelBottom;
	private LinearLayout panelSecondary;
	private BgButton btnGoBack, btnPrev, btnPlay, btnNext, btnMenu;
	private VisualizerView visualizerView;
	private volatile boolean alive, paused, reset, visualizerReady;
	private boolean fxVisualizerFailed, visualizerViewFullscreen;
	private int fxVisualizerAudioSessionId;
	private Timer timer;
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (btnMenu != null)
			CustomContextMenu.openContextMenu(btnMenu, this);
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			final ActionBar b = getActionBar();
			if (b != null)
				b.setDisplayHomeAsUpEnabled(true);
		}
	}
	
	private void prepareViews() {
		if (info == null)
			return;
		
		RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams)panelTop.getLayoutParams();
		p.width = (info.isLandscape ? RelativeLayout.LayoutParams.WRAP_CONTENT : RelativeLayout.LayoutParams.MATCH_PARENT);
		p.height = (info.isLandscape ? RelativeLayout.LayoutParams.MATCH_PARENT : RelativeLayout.LayoutParams.WRAP_CONTENT);
		panelTop.setLayoutParams(p);
		
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
		p.addRule(info.isLandscape ? RelativeLayout.RIGHT_OF : RelativeLayout.BELOW, R.id.panelTop);
		panelBottom.setLayoutParams(p);
		
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)btnPrev.getLayoutParams();
		lp.rightMargin = (info.isLandscape ? 0 : UI._16dp);
		lp.bottomMargin = (info.isLandscape ? UI._16dp : 0);
		btnPrev.setLayoutParams(lp);
		btnPrev.setIcon(UI.ICON_PREV);
		
		lp = (LinearLayout.LayoutParams)btnPlay.getLayoutParams();
		lp.rightMargin = (info.isLandscape ? 0 : UI._16dp);
		lp.bottomMargin = (info.isLandscape ? UI._16dp : 0);
		btnPlay.setLayoutParams(lp);
		btnPlay.setIcon(Player.isPlaying() ? UI.ICON_PAUSE : UI.ICON_PLAY);
		
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		p.addRule(info.isLandscape ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		p.addRule(info.isLandscape ? RelativeLayout.CENTER_VERTICAL : RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		panelSecondary.setLayoutParams(p);
		panelSecondary.setOrientation(info.isLandscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
		
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		p.addRule(info.isLandscape ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(info.isLandscape ? RelativeLayout.ALIGN_PARENT_BOTTOM : RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		btnMenu.setLayoutParams(p);
		btnMenu.setIcon(UI.ICON_MENU);
		
		if (visualizerView != null) {
			if (visualizerViewFullscreen) {
				p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
			} else {
				final int margin = UI.defaultControlSize + (UI.extraSpacing ? (UI._8dp << 1) : 0);
				int w, h;
				if (info.isLandscape) {
					w = info.usableScreenWidth - margin;
					h = info.usableScreenHeight;
				} else {
					w = info.usableScreenWidth;
					h = info.usableScreenHeight - margin;
				}
				final Point pt = visualizerView.getDesiredSize(w, h);
				p = new RelativeLayout.LayoutParams(pt.x, pt.y);
				p.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
			}
			visualizerView.setLayoutParams(p);
		}
		
		panelControls.requestLayout();
	}
	
	private boolean initFxVisualizer() {
		try {
			final int g = Player.getAudioSessionId();
			if (g < 0)
				return true;
			if (fxVisualizer != null) {
				if (fxVisualizerAudioSessionId == g) {
					try {
						fxVisualizer.setEnabled(true);
						return true;
					} catch (Throwable ex) {
					}
				}
				try {
					fxVisualizer.release();
				} catch (Throwable ex) {
					fxVisualizer = null;
				}
			}
			fxVisualizer = new android.media.audiofx.Visualizer(g);
			fxVisualizerAudioSessionId = g;
		} catch (Throwable ex) {
			fxVisualizerFailed = true;
			fxVisualizer = null;
			fxVisualizerAudioSessionId = -1;
			return false;
		}
		if (fxVisualizer != null) {
			try {
				fxVisualizer.setCaptureSize((visualizer == null) ? Visualizer.MAX_POINTS : visualizer.getDesiredPointCount());
				fxVisualizer.setEnabled(true);
				return true;
			} catch (Throwable ex) {
				fxVisualizerFailed = true;
				fxVisualizer.release();
				fxVisualizer = null;
				fxVisualizerAudioSessionId = -1;
			}
		}
		return false;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getWindow().setBackgroundDrawable(new ColorDrawable(UI.color_visualizer565));
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		setRequestedOrientation((UI.visualizerOrientation == 0) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		//whenever the activity is being displayed, the volume keys must control
		//the music volume and nothing else!
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		Player.addDestroyedObserver(this);
		
		setupActionBar();
		
		setContentView(R.layout.activity_visualizer);
		
		info = new UI.DisplayInfo();
		info.getInfo(this);
		
		panelControls = (RelativeLayout)findViewById(R.id.panelControls);
		panelTop = (RelativeLayout)findViewById(R.id.panelTop);
		panelBottom = (RelativeLayout)findViewById(R.id.panelBottom);
		panelSecondary = (LinearLayout)findViewById(R.id.panelSecondary);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnPrev = (BgButton)findViewById(R.id.btnPrev);
		btnPrev.setOnClickListener(this);
		btnPlay = (BgButton)findViewById(R.id.btnPlay);
		btnPlay.setOnClickListener(this);
		btnNext = (BgButton)findViewById(R.id.btnNext);
		btnNext.setOnClickListener(this);
		btnNext.setIcon(UI.ICON_NEXT);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		
		if (UI.extraSpacing)
			panelTop.setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);
		
		String name = null;
		Class<?> clazz = null;
		final Intent si = getIntent();
		if (si != null && (name = si.getStringExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME)) != null) {
			if (!name.startsWith("br.com.carlosrafaelgn.fplay"))
				name = null;
		}
		if (name != null) {
			try {
				clazz = Class.forName(name);
			} catch (Throwable ex) {
			}
		}
		if (clazz != null) {
			try {
				visualizer = (Visualizer)clazz.getConstructor(Context.class, boolean.class).newInstance(getApplication(), info.isLandscape);
			} catch (Throwable ex) {
				clazz = null;
			}
		}
		
		if (visualizer != null) {
			visualizerView = visualizer.getView();
			visualizerViewFullscreen = visualizerView.isFullscreen();
			if (visualizerView != null)
				panelBottom.addView(visualizerView);
		}
		
		prepareViews();
		
		if (UI.useVisualizerButtonsInsideList) {
			btnGoBack.setTextColor(UI.colorState_text_listitem_reactive);
			btnPrev.setTextColor(UI.colorState_text_listitem_reactive);
			btnPlay.setTextColor(UI.colorState_text_listitem_reactive);
			btnNext.setTextColor(UI.colorState_text_listitem_reactive);
			btnMenu.setTextColor(UI.colorState_text_listitem_reactive);
		}
		if (!btnGoBack.isInTouchMode())
			btnGoBack.requestFocus();
		
		alive = true;
		reset = true;
		paused = false;
		visualizerReady = false;
		fxVisualizerFailed = false;
		fxVisualizerAudioSessionId = -1;
		timer = new Timer(new Runnable() {
			private int lastTime = 0;
			@Override
			public void run() {
				if (alive) {
					if (paused) {
						try {
							if (fxVisualizer != null)
								fxVisualizer.setEnabled(false);
						} catch (Throwable ex) {
						}
						if (timer != null)
							timer.pause();
						return;
					}
					if (reset || fxVisualizer == null) {
						reset = false;
						if (!initFxVisualizer()) {
							MainHandler.postToMainThread(ActivityVisualizer.this);
							return;
						}
						if (!visualizerReady && alive && visualizer != null) {
							visualizer.load(getApplication());
							visualizerReady = true;
						}
					}
					if (fxVisualizer != null && visualizer != null) {
						final int now = (int)SystemClock.uptimeMillis();
						final int deltaMillis = (int)(now - lastTime);
						lastTime = now;
						visualizer.processFrame(fxVisualizer, ((deltaMillis >= 32) || (deltaMillis <= 0)) ? 32 : deltaMillis);
					}
				}
				if (!alive) {
					if (timer != null) {
						timer.stop();
						timer.release();
						timer = null;
					}
					if (visualizer != null) {
						visualizer.release();
						visualizer = null;
					}
					if (fxVisualizer != null) {
						try {
							fxVisualizer.setEnabled(false);
						} catch (Throwable ex) {
						}
						try {
							fxVisualizer.release();
						} catch (Throwable ex) {
						}
						fxVisualizer = null;
					}
					MainHandler.postToMainThread(ActivityVisualizer.this);
					System.gc();
				}
			}
		}, "Visualizer Thread", false, false, true);
		timer.start(16);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (info == null)
			return;
		final boolean i = info.isLandscape;
		final int w = info.usableScreenWidth, h = info.usableScreenHeight;
		info.getInfo(this);
		if (i != info.isLandscape || w != info.usableScreenWidth || h != info.usableScreenHeight) {
			if (visualizer != null)
				visualizer.configurationChanged(info.isLandscape);
			prepareViews();
			System.gc();
		}
	}
	
	private void resumeTimer() {
		//I decided to keep the visualizer paused only when the Activity is paused...
		//in all other cases, let the visualizer run free! :)
		//this behavior is specially useful for more complex visualizations
		paused = false;
		if (timer != null)
			timer.resume();
		//paused = !Player.isPlaying();
		//if (!paused && timer != null)
		//	timer.resume();
	}
	
	@Override
	protected void onPause() {
		Player.observer = null;
		paused = true;
		//return to the default screen settings when paused  
		if (UI.keepScreenOn)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		Player.observer = this;
		reset = true;
		resumeTimer();
		onPlayerChanged(Player.getCurrentSong(), true, null);
		//keep the screen always on while the visualizer is active
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		super.onResume();
	}
	
	private void finalCleanup() {
		Player.removeDestroyedObserver(this);
		alive = false;
		if (visualizer != null)
			visualizer.cancelLoading();
		resumeTimer();
		info = null;
		panelControls = null;
		panelTop = null;
		panelBottom = null;
		panelSecondary = null;
		btnGoBack = null;
		btnPrev = null;
		btnPlay = null;
		btnNext = null;
		btnMenu = null;
	}
	
	@Override
	protected void onDestroy() {
		finalCleanup();
		super.onDestroy();
	}
	
	@Override
	public void run() {
		if (fxVisualizerFailed) {
			fxVisualizerFailed = false;
			UI.toast(getApplication(), R.string.visualizer_not_supported);
		}
		//perform the final cleanup
		if (!alive && visualizerView != null) {
			visualizerView.releaseView();
			visualizerView = null;
		}
	}
	
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
		if (!songHasChanged)
			reset = true;
		resumeTimer();
		if (btnPlay != null) {
			btnPlay.setText(Player.isPlaying() ? UI.ICON_PAUSE : UI.ICON_PLAY);
			btnPlay.setContentDescription(getText(Player.isPlaying() ? R.string.pause : R.string.play));
		}
	}
	
	@Override
	public void onPlayerControlModeChanged(boolean controlMode) {
	}
	
	@Override
	public void onPlayerGlobalVolumeChanged() {
	}
	
	@Override
	public void onPlayerAudioSinkChanged(int audioSink) {
		reset = true;
		resumeTimer();
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
		UI.prepare(menu);
		menu.add(0, MNU_ORIENTATION, 0, (UI.visualizerOrientation == 0) ? R.string.landscape : R.string.portrait)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_ORIENTATION));
		if (visualizer != null)
			visualizer.onCreateContextMenu(menu);
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		if (info == null)
			return true;
		switch (item.getItemId()) {
		case MNU_ORIENTATION:
			UI.visualizerOrientation = ((UI.visualizerOrientation == 0) ? 1 : 0);
			setRequestedOrientation((UI.visualizerOrientation == 0) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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
			reset = true;
			resumeTimer();
		} else if (view == btnPlay) {
			Player.playPause();
		} else if (view == btnNext) {
			Player.next();
			reset = true;
			resumeTimer();
		} else if (view == btnMenu) {
			onPrepareOptionsMenu(null);
		}
	}
}
