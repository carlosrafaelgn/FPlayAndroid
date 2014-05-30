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
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;
import br.com.carlosrafaelgn.fplay.visualizer.Visualizer;
import br.com.carlosrafaelgn.fplay.visualizer.VisualizerView;

public final class ActivityVisualizer extends Activity implements Runnable, Player.PlayerObserver, Player.PlayerDestroyedObserver, View.OnClickListener {
	private android.media.audiofx.Visualizer fxVisualizer;
	private Visualizer visualizer;
	private RelativeLayout container, buttonContainer;
	private BgButton btnPrev, btnPlay, btnNext, btnBack;
	private VisualizerView visualizerView;
	private volatile boolean alive, paused, reset, visualizerReady;
	private boolean landscape, fxVisualizerFailed, visualizerViewFullscreen;
	private int fxVisualizerAudioSessionId;
	private Timer timer;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			final ActionBar b = getActionBar();
			if (b != null)
				b.setDisplayHomeAsUpEnabled(true);
		}
	}
	
	private void prepareViews() {
		RelativeLayout.LayoutParams p, pv = null;
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT); 
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		p.addRule(landscape ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		if (!UI.isLowDpiScreen || UI.isLargeScreen) {
			p.leftMargin = UI._8dp;
			p.topMargin = UI._8dp;
			p.rightMargin = UI._8dp;
			p.bottomMargin = UI._8dp;
		}
		btnBack.setLayoutParams(p);
		btnBack.setIcon(UI.ICON_GOBACK);
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		p.addRule(landscape ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(landscape ? RelativeLayout.ALIGN_PARENT_BOTTOM : RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		btnNext.setLayoutParams(p);
		btnNext.setIcon(UI.ICON_NEXT);
		if (landscape) {
			p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.MATCH_PARENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			
			if (visualizerViewFullscreen) {
				pv = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
				pv.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
				pv.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
				pv.addRule(RelativeLayout.LEFT_OF, 5);
				pv.addRule(RelativeLayout.RIGHT_OF, 1);
			}
		} else {
			p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			
			if (visualizerViewFullscreen) {
				pv = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
				pv.addRule(RelativeLayout.BELOW, 5);
				pv.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
				pv.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
				pv.addRule(RelativeLayout.ABOVE, 1);
			}
		}
		if (visualizerView != null) {
			if (!visualizerViewFullscreen) {
				final int margin = (UI.defaultControlSize << 1) + ((!UI.isLowDpiScreen || UI.isLargeScreen) ? (UI._8dp << 1) : 0);
				int w, h;
				if (landscape) {
					w = UI.screenWidth - margin;
					h = UI.screenHeight;
				} else {
					w = UI.screenWidth;
					h = UI.screenHeight - margin;
				}
				final Point pt = visualizerView.getDesiredSize(w, h);
				pv = new RelativeLayout.LayoutParams(pt.x, pt.y);
				pv.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
			}
			visualizerView.setLayoutParams(pv);
		}
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		buttonContainer.setLayoutParams(p);
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
		setRequestedOrientation((UI.forcedOrientation == 0) ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ((UI.forcedOrientation < 0) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
		//whenever the activity is being displayed, the volume keys must control
		//the music volume and nothing else!
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		Player.addDestroyedObserver(this);
		
		setupActionBar();
		
		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		landscape = (metrics.widthPixels >= metrics.heightPixels);
		
		container = new RelativeLayout(getApplication());
		container.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		
		btnBack = new BgButton(getApplication());
		RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT); 
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		p.addRule(landscape ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		if (!UI.isLowDpiScreen || UI.isLargeScreen) {
			p.leftMargin = UI._8dp;
			p.topMargin = UI._8dp;
			p.rightMargin = UI._8dp;
			p.bottomMargin = UI._8dp;
		}
		btnBack.setLayoutParams(p);
		btnBack.setOnClickListener(this);
		btnBack.setContentDescription(getText(R.string.go_back));
		btnBack.setId(5);
		btnBack.setNextFocusUpId(2);
		btnBack.setNextFocusLeftId(4);
		btnBack.setNextFocusDownId(2);
		btnBack.setNextFocusRightId(2);
		UI.setNextFocusForwardId(btnBack, 2);
		buttonContainer = new RelativeLayout(getApplication());
		buttonContainer.setId(1);
		if (UI.isLowDpiScreen && !UI.isLargeScreen)
			buttonContainer.setPadding(0, 0, 0, 0);
		else
			buttonContainer.setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);
		btnPrev = new BgButton(getApplication());
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		btnPrev.setLayoutParams(p);
		btnPrev.setIcon(UI.ICON_PREV);
		btnPrev.setOnClickListener(this);
		btnPrev.setContentDescription(getText(R.string.previous));
		btnPrev.setId(2);
		btnPrev.setNextFocusUpId(5);
		btnPrev.setNextFocusLeftId(5);
		btnPrev.setNextFocusDownId(5);
		btnPrev.setNextFocusRightId(3);
		UI.setNextFocusForwardId(btnPrev, 3);
		btnPlay = new BgButton(getApplication());
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		p.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		btnPlay.setLayoutParams(p);
		btnPlay.setIcon(UI.ICON_PLAY);
		btnPlay.setOnClickListener(this);
		btnPlay.setContentDescription(getText(R.string.play));
		btnPlay.setId(3);
		btnPlay.setNextFocusUpId(5);
		btnPlay.setNextFocusLeftId(2);
		btnPlay.setNextFocusDownId(5);
		btnPlay.setNextFocusRightId(4);
		UI.setNextFocusForwardId(btnPlay, 4);
		btnNext = new BgButton(getApplication());
		btnNext.setOnClickListener(this);
		btnNext.setContentDescription(getText(R.string.next));
		btnNext.setId(4);
		btnNext.setNextFocusUpId(5);
		btnNext.setNextFocusLeftId(3);
		btnNext.setNextFocusDownId(5);
		btnNext.setNextFocusRightId(5);
		UI.setNextFocusForwardId(btnNext, 5);
		
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
				visualizer = (Visualizer)clazz.getConstructor(Context.class, boolean.class).newInstance(getApplication(), landscape);
			} catch (Throwable ex) {
				clazz = null;
			}
		}
		
		if (visualizer != null) {
			visualizerView = visualizer.getView();
			visualizerViewFullscreen = visualizerView.isFullscreen();
		}
		
		buttonContainer.addView(btnPrev);
		buttonContainer.addView(btnPlay);
		buttonContainer.addView(btnNext);
		prepareViews();
		if (visualizerView != null)
			container.addView(visualizerView);
		container.addView(btnBack);
		container.addView(buttonContainer);
		setContentView(container);
		
		if (UI.useVisualizerButtonsInsideList) {
			btnBack.setTextColor(UI.colorState_text_listitem_reactive);
			btnPrev.setTextColor(UI.colorState_text_listitem_reactive);
			btnPlay.setTextColor(UI.colorState_text_listitem_reactive);
			btnNext.setTextColor(UI.colorState_text_listitem_reactive);
		}
		if (!btnBack.isInTouchMode())
			btnBack.requestFocus();
		
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
						//MainHandler.postToMainThread(visualizerView);
					}
				}
				if (!alive) {
					timer.stop();
					timer.release();
					timer = null;
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
		final boolean i = landscape;
		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		landscape = (metrics.widthPixels >= metrics.heightPixels);
		if (i != landscape) {
			if (visualizer != null)
				visualizer.configurationChanged(landscape);
			prepareViews();
			container.requestLayout();
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
		if (visualizer != null)
			visualizer.cancelLoading();
		alive = false;
		paused = false;
		if (timer != null)
			timer.resume();
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
	public void onClick(View view) {
		if (view == btnPrev) {
			Player.previous();
			reset = true;
			resumeTimer();
		} else if (view == btnPlay) {
			Player.playPause();
			reset = true;
			resumeTimer();
		} else if (view == btnNext) {
			Player.next();
			reset = true;
			resumeTimer();
		} else if (view == btnBack) {
			finish();
		}
	}
}
