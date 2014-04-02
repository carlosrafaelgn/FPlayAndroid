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
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
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

public final class ActivityVisualizer extends Activity implements Runnable, Player.PlayerObserver, Player.PlayerDestroyedObserver, View.OnClickListener {
	private static class VisualizerView extends View implements Runnable {
		//based on my WebAudio visualizer ;)
		//https://github.com/carlosrafaelgn/GraphicalFilterEditor/blob/master/Analyzer.js
		
		//to make the math easier COLORS has 257 int's (form 0 to 256) 
		private static final int[] COLORS = new int[] { 0xff000000, 0xff0b00b2, 0xff0c00b1, 0xff0e00af, 0xff0e00af, 0xff0f00ae, 0xff1000ad, 0xff1200ac, 0xff1300ab, 0xff1500ab, 0xff1600aa, 0xff1700a9, 0xff1900a8, 0xff1a00a6, 0xff1b00a6, 0xff1d00a4, 0xff1f00a3, 0xff2000a1, 0xff2200a1, 0xff2300a0, 0xff25009e, 0xff27009d, 0xff29009c, 0xff2b009a, 0xff2d0099, 0xff2e0098, 0xff300096, 0xff320095, 0xff340094, 0xff360092, 0xff380090, 0xff39008f, 0xff3c008e, 0xff3e008c, 0xff40008b, 0xff420089, 0xff440088, 0xff470086, 0xff480085, 0xff4b0083, 0xff4c0082, 0xff4f0080, 0xff51007f, 0xff54007c, 0xff56007c, 0xff57007a, 0xff5a0078, 0xff5c0076, 0xff5f0075, 0xff610073, 0xff640071, 0xff65006f, 0xff68006e, 0xff6b006c, 0xff6d006a, 0xff6f0069, 0xff710066, 0xff740065, 0xff760063, 0xff790062, 0xff7b0060, 0xff7d005e, 0xff80005c, 0xff82005b, 0xff850059, 0xff860057, 0xff890056, 0xff8c0054, 0xff8e0052, 0xff910050, 0xff93004f, 0xff96004d, 0xff97004b, 0xff9a0049, 0xff9c0048, 0xff9f0046, 0xffa10045, 0xffa40043, 0xffa60040, 0xffa8003f, 0xffaa003e, 0xffad003c, 0xffaf003a, 0xffb10039, 0xffb30037, 0xffb60035, 0xffb80034, 0xffba0032, 0xffbc0031, 0xffbe002e, 0xffc1002d, 0xffc3002c, 0xffc5002a, 0xffc70028, 0xffca0027, 0xffcb0025, 0xffce0024, 0xffcf0023, 0xffd10022, 0xffd30020, 0xffd6001e, 0xffd7001d, 0xffd9001b, 0xffdb001a, 0xffdd0019, 0xffdf0017, 0xffe10017, 0xffe20015, 0xffe40014, 0xffe60012, 0xffe70011, 0xffe90010, 0xffea000f, 0xffec000d, 0xffed000c, 0xffef000b, 0xfff1000b, 0xfff2000a, 0xfff40008, 0xfff50007, 0xfff60006, 0xfff70005, 0xfff90005, 0xfff90003, 0xfffb0003, 0xfffc0002, 0xfffd0001, 0xfffe0001, 0xffff0000, 0xffff0100, 0xffff0200, 0xffff0300, 0xffff0500, 0xffff0600, 0xffff0600, 0xffff0800, 0xffff0900, 0xffff0b00, 0xffff0c00, 0xffff0d00, 0xffff0f00, 0xffff1000, 0xffff1200, 0xffff1400, 0xffff1500, 0xffff1700, 0xffff1900, 0xffff1a00, 0xffff1c00, 0xffff1d00, 0xffff2000, 0xffff2200, 0xffff2300, 0xffff2500, 0xffff2700, 0xffff2900, 0xffff2b00, 0xffff2d00, 0xffff2f00, 0xffff3100, 0xffff3400, 0xffff3500, 0xffff3700, 0xffff3900, 0xffff3c00, 0xffff3e00, 0xffff4000, 0xffff4200, 0xffff4400, 0xffff4700, 0xffff4900, 0xffff4b00, 0xffff4e00, 0xffff5000, 0xffff5200, 0xffff5500, 0xffff5700, 0xffff5900, 0xffff5c00, 0xffff5e00, 0xffff6100, 0xffff6300, 0xffff6600, 0xffff6800, 0xffff6a00, 0xffff6c00, 0xffff6f00, 0xffff7200, 0xffff7400, 0xffff7700, 0xffff7900, 0xffff7c00, 0xffff7e00, 0xffff8000, 0xffff8300, 0xffff8500, 0xffff8700, 0xffff8a00, 0xffff8d00, 0xffff8f00, 0xffff9200, 0xffff9500, 0xffff9700, 0xffff9900, 0xffff9b00, 0xffff9e00, 0xffffa000, 0xffffa300, 0xffffa500, 0xffffa700, 0xffffa900, 0xffffac00, 0xffffae00, 0xffffb100, 0xffffb200, 0xffffb600, 0xffffb700, 0xffffba00, 0xffffbc00, 0xffffbe00, 0xffffc100, 0xffffc300, 0xffffc400, 0xffffc700, 0xffffc900, 0xffffcb00, 0xffffcd00, 0xffffcf00, 0xffffd100, 0xffffd300, 0xffffd500, 0xffffd700, 0xffffd900, 0xffffdb00, 0xffffdd00, 0xffffde00, 0xffffe000, 0xffffe100, 0xffffe400, 0xffffe500, 0xffffe700, 0xffffe900, 0xffffea00, 0xffffeb00, 0xffffed00, 0xffffef00, 0xfffff000, 0xfffff100, 0xfffff300, 0xfffff400, 0xfffff500, 0xfffff600, 0xfffff800, 0xfffff900, 0xfffffa00, 0xfffffb00, 0xfffffb00 };
		
		private final Rect rect, rectBar;
		private int readIdx, writeIdx, readCount, writeCount, barW, barH;
		private int[][] points;
		private Paint paint;
		
		public VisualizerView(Context context) {
			super(context);
			rect = new Rect();
			rectBar = new Rect();
			points = new int[][] { new int[256], new int[256], new int[256] };
			writeCount = points.length;
			paint = new Paint();
			paint.setStyle(Style.FILL);
			paint.setDither(false);
			paint.setAntiAlias(false);
		}
		
		@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
		@Override
		public void setBackground(Drawable background) {
			super.setBackground(null);
		}
		
		@Override
		@Deprecated
		public void setBackgroundDrawable(Drawable background) {
			super.setBackgroundDrawable(null);
		}
		
		@Override
		public void setBackgroundResource(int resid) {
			super.setBackgroundResource(0);
		}
		
		@Override
		public void setBackgroundColor(int color) {
			super.setBackgroundResource(0);
		}
		
		@Override
		public Drawable getBackground() {
			return null;
		}
		
		@Override
		@ExportedProperty(category = "drawing")
		public boolean isOpaque() {
			return true;
		}
		
		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);
			
			int size = ((w > h) ? ((w * 7) >> 3) : w);
			barH = ((w < h) ? w : h) >> 1;
			
			barW = size >> 8;
			if (barW < 1)
				barW = 1;
			size = barW << 8;
			rect.left = (w >> 1) - (size >> 1);
			if (rect.left < 0)
				rect.left = 0;
			rect.top = (h >> 1) - (barH >> 1);
			rect.right = rect.left + size;
			rect.bottom = rect.top + barH;
		}
		
		public void release() {
			points[0] = null;
			points[1] = null;
			points[2] = null;
			points = null;
		}
		
		@Override
		protected void onDraw(Canvas canvas) {
			final int[] pts = acquirePoints(true);
			if (pts != null) {
				canvas.drawColor(UI.color_visualizer);
				final int w = barW, h = barH;
				final Rect r = rectBar;
				final Paint p = paint;
				r.bottom = rect.top + h;
				r.left = rect.left;
				r.right = rect.left + w;
				for (int i = 0; i < 256; i++) {
					//pts[i] goes from 0 to 32768 (inclusive)
					final int v = pts[i];
					r.top = r.bottom - ((v * h) >>> 15);
					paint.setColor(COLORS[v >>> 7]);
					canvas.drawRect(r, p);
					r.left += w;
					r.right += w;
				}
				releasePoints(true);
			}
		}
		
		public int[] acquirePoints(boolean read) {
			int[] p = null;
			synchronized (points) {
				if (read) {
					if (readCount > 0) {
						p = points[readIdx];
						readCount--;
						readIdx++;
						if (readIdx > 2)
							readIdx = 0;
					}
				} else {
					if (writeCount > 0) {
						p = points[writeIdx];
						writeCount--;
						writeIdx++;
						if (writeIdx > 2)
							writeIdx = 0;
					}
				}
			}
			return p;
		}
		
		public void releasePoints(boolean read) {
			synchronized (points) {
				if (read) {
					if (writeCount < 3)
						writeCount++;
				} else {
					if (readCount < 3)
						readCount++;
				}
			}
		}
		
		@Override
		public void run() {
			invalidate(rect);
		}
	}
	
	private Visualizer visualizer;
	private RelativeLayout container, buttonContainer;
	private BgButton btnPrev, btnPlay, btnNext, btnBack;
	private VisualizerView visualizerView;
	private Timer timer;
	private byte[] bfft;
	private float[] fft, multiplier;
	private boolean landscape, lowDpi, visualizerFailed;
	
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
		visualizerView.setLayoutParams(pv);
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		buttonContainer.setLayoutParams(p);
	}
	
	private void initVisualizer(boolean startNow) {
		if (visualizer != null || visualizerFailed)
			return;
		try {
			int g = Player.getAudioSessionId();
			if (g < 0)
				return;
			visualizer = new Visualizer(g);
		} catch (Throwable ex) {
			visualizerFailed = true;
			UI.toast(getApplication(), R.string.visualizer_not_supported);
		}
		if (visualizer != null) {
			try {
				visualizer.setCaptureSize(1024);
				if (startNow) {
					visualizer.setEnabled(true);
					timer.start(16, false);
				}
			} catch (Throwable ex) {
				visualizerFailed = true;
				visualizer.release();
				visualizer = null;
				UI.toast(getApplication(), R.string.visualizer_not_supported);
			}
		}
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
		
		visualizerView = new VisualizerView(getApplication());
		visualizerFailed = false;
		
		buttonContainer.addView(btnPrev);
		buttonContainer.addView(btnPlay);
		buttonContainer.addView(btnNext);
		
		prepareViews();
		
		container.addView(visualizerView);
		container.addView(btnBack);
		container.addView(buttonContainer);
		setContentView(container);
		
		timer = new Timer(this, "Visualizer Timer", false, false, true);
		bfft = new byte[1024];
		fft = new float[256];
		multiplier = new float[256];
		//the 0.25 below is the counterpart of the 0.75 in run()
		for (int i = 0; i < 256; i++)
			multiplier[i] = (float)((((double)i + 100.0) / 101.0) * Math.exp(i / 300.0) * 0.25);
		
		initVisualizer(false);
		
		if (UI.useVisualizerButtonsInsideList) {
			btnBack.setInsideList(true);
			btnPrev.setInsideList(true);
			btnPlay.setInsideList(true);
			btnNext.setInsideList(true);
		}
		if (!btnBack.isInTouchMode())
			btnBack.requestFocus();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		final boolean i = landscape;
		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		landscape = (metrics.widthPixels >= metrics.heightPixels);
		lowDpi = (metrics.densityDpi < 160);
		if (i != landscape) {
			prepareViews();
			container.requestLayout();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		Player.observer = null;
		timer.stopAndWait();
		if (visualizer != null)
			visualizer.setEnabled(false);
	}
	
	@Override
	protected void onResume() {
		Player.observer = this;
		if (visualizer != null) {
			visualizer.setEnabled(true);
			timer.start(16, false);
		}
		onPlayerChanged(Player.getCurrentSong(), true, null);
		super.onResume();
	}
	
	private void finalCleanup() {
		Player.removeDestroyedObserver(this);
		if (timer != null) {
			timer.stopAndWait();
			timer = null;
		}
		if (visualizer != null) {
			visualizer.release();
			visualizer = null;
		}
		if (visualizerView != null) {
			visualizerView.release();
			visualizerView = null;
		}
	}
	
	@Override
	protected void onDestroy() {
		finalCleanup();
		super.onDestroy();
	}
	
	/*@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			break;
		case MotionEvent.ACTION_MOVE:
			break;
		case MotionEvent.ACTION_UP:
			//Player.playPause();
			break;
		case MotionEvent.ACTION_CANCEL:
			break;
		}
		return super.onTouchEvent(event);
	}*/
	
	@Override
	public void run() {
		final int[] pts = visualizerView.acquirePoints(false);
		if (pts != null) {
			//fft format:
			//index  0   1    2  3  4  5  ..... n-2        n-1
			//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
			
			visualizer.getFft(bfft);
			//we are not drawing/analyzing the last bin (Nyquist)
			fft[0] = (multiplier[0] * (float)bfft[0]) + (0.75f * fft[0]);
			//fft[i] stores values from 0 to -128/127 (inclusive)
			//pts[i] goes from 0 to 32768 (inclusive)
			int v = (int)(fft[0] * 256.0f);
			if (v < 0)
				v = -v;
			if (v > 32768)
				v = 32768;
			pts[0] = v;
			for (int i = 1; i < 256; i++) {
				int re = (int)bfft[i << 1];
				re *= re;
				int im = (int)bfft[(i << 1) + 1];
				im *= im;
				float m = (multiplier[i] * (float)Math.sqrt((double)(re + im))) + (0.75f * fft[i]);
				fft[i] = m;
				v = (int)(m * 256.0f);
				if (v > 32768)
					v = 32768;
				else if (v < 0)
					v = 0;
				pts[i] = v;
			}
			visualizerView.releasePoints(false);
			MainHandler.postToMainThread(visualizerView);
		}
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
			initVisualizer(true);
		} else if (view == btnPlay) {
			Player.playPause();
			initVisualizer(true);
		} else if (view == btnNext) {
			Player.next();
			initVisualizer(true);
		} else if (view == btnBack) {
			finish();
		}
	}
}
