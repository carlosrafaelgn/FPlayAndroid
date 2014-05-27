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
package br.com.carlosrafaelgn.fplay.visualizer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Bitmap.Config;
import android.graphics.Rect;

public final class SimpleVisualizerJni implements Visualizer {
	private static final class SimpleVisualizerView extends VisualizerView {
		//based on my WebAudio visualizer ;)
		//https://github.com/carlosrafaelgn/GraphicalFilterEditor/blob/master/Analyzer.js
		
		private final Rect rect;
		private int readIdx, writeIdx, readCount, writeCount, barW, barH;
		private int[][] points;
		private int currentFilter;
		private Bitmap bmp;
		
		public SimpleVisualizerView(Context context) {
			super(context);
			rect = new Rect();
			points = new int[][] { new int[256], new int[256], new int[256] };
			writeCount = points.length;
			currentFilter = 0;
			setClickable(true);
			setFocusable(false);
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
			if (bmp != null) {
				bmp.recycle();
				bmp = null;
			}
			bmp = Bitmap.createBitmap(rect.width(), rect.height(), Config.ARGB_8888);
		}
		
		@Override
		protected void onDraw(Canvas canvas) {
			final int[] pts = acquirePoints(true);
			if (bmp != null) {
				if (pts != null) {
					fill(bmp, barW, pts);
					releasePoints(true);
				}
				canvas.drawBitmap(bmp, rect.left, rect.top, null);
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
		public void release() {
			points[0] = null;
			points[1] = null;
			points[2] = null;
			points = null;
			if (bmp != null) {
				bmp.recycle();
				bmp = null;
			}
		}
		
		@Override
		public void run() {
			invalidate(rect);
		}
		
		@Override
		public boolean performClick() {
			currentFilter++;
			switch (currentFilter) {
			case 1:
				setFilter(1.0f);
				break;
			case 2:
				setFilter(0.25f);
				break;
			case 3:
				setFilter(0.5f);
				break;
			default:
				setFilter(0.75f);
				currentFilter = 0;
				break;
			}
			return super.performClick();
		}
	}
	
	static {
		System.loadLibrary("SimpleVisualizerJni");
	}
	
	private static native void setFilter(float filterNew);
	private static native void init(float filterNew);
	private static native void process(byte[] bfft, int[] pts);
	private static native int fill(Bitmap bmp, int barW, int[] pts);
	
	private SimpleVisualizerView visualizerView;
	private byte[] bfft;
	
	public SimpleVisualizerJni(Context context, boolean landscape) {
		visualizerView = new SimpleVisualizerView(context);
		bfft = new byte[1024];
		init(0.75f);
	}
	
	@Override
	//Runs on the MAIN thread
	public VisualizerView getView() {
		return visualizerView;
	}
	
	//Runs on ANY thread
	@Override
	public int getDesiredPointCount() {
		return 1024;
	}
	
	@Override
	//Runs on a SECONDARY thread
	public void load(Context context) {
		
	}
	
	//Runs on ANY thread
	@Override
	public boolean isLoading() {
		return false;
	}
	
	//Runs on ANY thread
	@Override
	public void cancelLoading() {
		
	}
	
	@Override
	//Runs on the MAIN thread
	public void configurationChanged(boolean landscape) {
		
	}
	
	@Override
	//Runs on a SECONDARY thread
	public void processFrame(android.media.audiofx.Visualizer visualizer, int deltaMillis) {
		if (visualizerView == null)
			return;
		final int[] pts = visualizerView.acquirePoints(false);
		if (pts == null)
			return;
		//fft format:
		//index  0   1    2  3  4  5  ..... n-2        n-1
		//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
		visualizer.getFft(bfft);
		process(bfft, pts);
		visualizerView.releasePoints(false);
	}
	
	@Override
	//Runs on a SECONDARY thread
	public void release() {
		visualizerView = null;
		bfft = null;
	}
}
