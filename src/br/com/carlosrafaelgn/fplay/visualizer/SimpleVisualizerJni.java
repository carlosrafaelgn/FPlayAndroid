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
import android.graphics.Point;
import android.view.Surface;
import android.view.SurfaceHolder;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public final class SimpleVisualizerJni extends VisualizerView implements SurfaceHolder.Callback, Visualizer {
	static {
		System.loadLibrary("SimpleVisualizerJni");
	}
	
	private static native void setFilter(float coefNew);
	private static native void init(float coefNew, int bgColor);
	private static native int prepareSurface(Surface surface);
	private static native void process(byte[] bfft, Surface surface, boolean lerp);
	
	private byte[] bfft;
	private final SlimLock lock;
	private Point point;
	private int currentFilter;
	private SurfaceHolder surfaceHolder;
	private Surface surface;
	
	public SimpleVisualizerJni(Context context, boolean landscape) {
		super(context);
		bfft = new byte[1024];
		init(0.5f, UI.color_visualizer565);
		lock = new SlimLock();
		point = new Point();
		currentFilter = 0;
		setClickable(true);
		setFocusable(false);
		surfaceHolder = getHolder();
		surfaceHolder.addCallback(this);
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
	
	//Runs on the MAIN thread
	@Override
	public VisualizerView getView() {
		return this;
	}
	
	//Runs on ANY thread
	@Override
	public int getDesiredPointCount() {
		return 1024;
	}
	
	//Runs on a SECONDARY thread
	@Override
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
	
	//Runs on the MAIN thread
	@Override
	public void configurationChanged(boolean landscape) {
		
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void processFrame(android.media.audiofx.Visualizer visualizer, int deltaMillis) {
		if (!lock.lockLowPriority())
			return;
		try {
			if (surface != null) {
				visualizer.getFft(bfft);
				process(bfft, surface, false);
			}
		} finally {
			lock.releaseLowPriority();
		}
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void release() {
		bfft = null;
	}
	
	//Runs on the MAIN thread (return value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return false;
	}
	
	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight) {
		point.x = ((availableWidth > availableHeight) ? ((availableWidth * 7) >> 3) : availableWidth) >> 8;
		point.y = ((availableWidth < availableHeight) ? availableWidth : availableHeight) >> 1;
		if (point.x < 1)
			point.x = 1;
		point.x <<= 8;
		if (point.x > availableWidth)
			point.x = availableWidth;
		point.y &= (~1); //make y always an even number
		return point;
	}
	
	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
		if (surfaceHolder != null) {
			surfaceHolder.removeCallback(this);
			surfaceHolder = null;
		}
		surface = null;
		point = null;
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		lock.lockHighPriority();
		surface = null;
		if (surfaceHolder != null) {
			surface = surfaceHolder.getSurface();
			if (surface != null) {
				if (prepareSurface(surface) < 0)
					surface = null;
			}
		}
		lock.releaseHighPriority();
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		lock.lockHighPriority();
		surface = null;
		lock.releaseHighPriority();
	}
}
