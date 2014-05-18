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
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import br.com.carlosrafaelgn.fplay.visualizer.SimpleVisualizer;
import br.com.carlosrafaelgn.fplay.visualizer.Visualizer;
import br.com.carlosrafaelgn.fplay.visualizer.VisualizerView;

public final class ActivityVisualizer extends Activity implements Runnable, Player.PlayerObserver, Player.PlayerDestroyedObserver, View.OnClickListener {
	private static final int MSG_PROCESS_FRAME = 0x01;
	private static final int MSG_STATE_CHANGED = 0x02;
	private static final int MSG_PLAYER_CHANGED = 0x03;
	
	private android.media.audiofx.Visualizer fxVisualizer;
	private Visualizer visualizer;
	private RelativeLayout container, buttonContainer;
	private BgButton btnPrev, btnPlay, btnNext, btnBack;
	private VisualizerView visualizerView;
	private boolean alive, paused;
	private boolean landscape, lowDpi, fxVisualizerFailed;
	private int fxVisualizerAudioSessionId;
	private Handler handler;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			final ActionBar b = getActionBar();
			if (b != null)
				b.setDisplayHomeAsUpEnabled(true);
		}
	}
	
	private void prepareViews() {
		RelativeLayout.LayoutParams p, pv;
		p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		p.addRule(landscape ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(landscape ? RelativeLayout.ALIGN_PARENT_BOTTOM : RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		btnNext.setLayoutParams(p);
		btnNext.setIcon(UI.ICON_NEXT);
		if (landscape) {
			p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.MATCH_PARENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			
			pv = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
			pv.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			pv.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
			pv.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			pv.addRule(RelativeLayout.RIGHT_OF, 1);
		} else {
			p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			
			pv = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
			pv.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			pv.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			pv.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			pv.addRule(RelativeLayout.ABOVE, 1);
		}
		if (visualizerView != null)
			visualizerView.setLayoutParams(pv);
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
				fxVisualizer.setCaptureSize(Visualizer.MAX_POINTS);
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
		
		getWindow().setBackgroundDrawable(new ColorDrawable(UI.color_visualizer));
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		if (UI.keepScreenOn)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (UI.forcedOrientation == 0)
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		else if (UI.forcedOrientation < 0)
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		else
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		//whenever the activity is being displayed, the volume keys must control
		//the music volume and nothing else!
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		Player.addDestroyedObserver(this);
		
		setupActionBar();
		
		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		landscape = (metrics.widthPixels >= metrics.heightPixels);
		lowDpi = (metrics.densityDpi < 160);
		
		container = new RelativeLayout(getApplication());
		container.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		
		btnBack = new BgButton(getApplication());
		RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT); 
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		p.addRule(UI.isLandscape ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		if (!UI.isLowDpiScreen || UI.isLargeScreen) {
			p.leftMargin = UI._8dp;
			p.topMargin = UI._8dp;
			p.rightMargin = UI._8dp;
		}
		btnBack.setLayoutParams(p);
		btnBack.setIcon(UI.ICON_GOBACK);
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
		if (lowDpi)
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
				visualizer = (Visualizer)clazz.getConstructor(Context.class, boolean.class, boolean.class).newInstance(getApplication(), landscape, lowDpi);
			} catch (Throwable ex) {
				clazz = null;
			}
		}
		if (visualizer == null)
			visualizer = new SimpleVisualizer(getApplication(), landscape, lowDpi);
		
		visualizerView = visualizer.getView();
		
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
		paused = false;
		fxVisualizerFailed = false;
		fxVisualizerAudioSessionId = -1;
		(new Thread(this, "Visualizer Thread")).start();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		final boolean i = landscape;
		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		landscape = (metrics.widthPixels >= metrics.heightPixels);
		lowDpi = (metrics.densityDpi < 160);
		if (i != landscape) {
			if (visualizer != null)
				visualizer.configurationChanged(landscape, lowDpi);
			prepareViews();
			container.requestLayout();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		Player.observer = null;
		paused = true;
		if (handler != null)
			handler.sendEmptyMessage(MSG_STATE_CHANGED);
	}
	
	@Override
	protected void onResume() {
		Player.observer = this;
		paused = false;
		if (handler != null)
			handler.sendEmptyMessage(MSG_STATE_CHANGED);
		onPlayerChanged(Player.getCurrentSong(), true, null);
		super.onResume();
	}
	
	private void finalCleanup() {
		Player.removeDestroyedObserver(this);
		alive = false;
		if (handler != null)
			handler.sendEmptyMessage(MSG_STATE_CHANGED);
	}
	
	@Override
	protected void onDestroy() {
		finalCleanup();
		super.onDestroy();
	}
	
	@Override
	public void run() {
		if (MainHandler.isOnMainThread()) {
			if (fxVisualizerFailed) {
				fxVisualizerFailed = false;
				UI.toast(getApplication(), R.string.visualizer_not_supported);
			}
			//perform the final cleanup
			if (!alive && visualizerView != null) {
				visualizerView.release();
				visualizerView = null;
			}
			return;
		}
		
		Looper.prepare();
		handler = new Handler(Looper.myLooper()) {
			private long lastTime = 0;
			
			@Override
			public void handleMessage(Message msg) {
				if (!alive) {
					Looper.myLooper().quit();
					return;
				}
				if (paused) {
					try {
						fxVisualizer.setEnabled(false);
					} catch (Throwable ex) {
					}
					return;
				}
				switch (msg.what) {
				case MSG_PROCESS_FRAME:
					if (fxVisualizer != null && visualizer != null) {
						final long now = SystemClock.uptimeMillis();
						int deltaMillis = (int)(now - lastTime);
						if (deltaMillis > 10) {
							lastTime = now;
							visualizer.processFrame(fxVisualizer, (deltaMillis >= 32) ? 32 : deltaMillis);
							if (visualizerView != null)
								MainHandler.postToMainThread(visualizerView);
						} else if (deltaMillis < 0) {
							lastTime = now;
						}
						handler.sendEmptyMessageAtTime(MSG_PROCESS_FRAME, now + 16);
					}
					break;
				case MSG_STATE_CHANGED:
				case MSG_PLAYER_CHANGED:
					if (alive) {
						initFxVisualizer();
						handler.sendEmptyMessageDelayed(MSG_PROCESS_FRAME, 16);
					} else {
						Looper.myLooper().quit();
					}
					break;
				}
			}
		};
		if (alive) {
			initFxVisualizer();
			if (alive) {
				if (visualizer != null)
					visualizer.init(getApplication());
				if (alive) {
					//send the first message that will start the chain of MSG_PROCESS_FRAME messages
					handler.sendEmptyMessageDelayed(MSG_PROCESS_FRAME, 16);
					Looper.loop();
				}
			}
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
		handler = null;
		MainHandler.postToMainThread(this);
	}
	
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
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
			if (handler != null)
				handler.sendEmptyMessage(MSG_PLAYER_CHANGED);
		} else if (view == btnPlay) {
			Player.playPause();
			if (handler != null)
				handler.sendEmptyMessage(MSG_PLAYER_CHANGED);
		} else if (view == btnNext) {
			Player.next();
			if (handler != null)
				handler.sendEmptyMessage(MSG_PLAYER_CHANGED);
		} else if (view == btnBack) {
			finish();
		}
	}
}
