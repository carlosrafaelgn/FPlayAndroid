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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewDebug.ExportedProperty;

import java.util.Arrays;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public final class SimpleVisualizerJni extends SurfaceView implements SurfaceHolder.Callback, Visualizer, VisualizerView, MenuItem.OnMenuItemClickListener {
	private static final int MNU_COLOR = MNU_VISUALIZER + 1, MNU_LORES = MNU_VISUALIZER + 2, MNU_HIRES = MNU_VISUALIZER + 3, MNU_VOICEPRINT = MNU_VISUALIZER + 4;
	
	static {
		System.loadLibrary("SimpleVisualizerJni");
	}

	static native void commonSetSpeed(int speed);
	static native void commonSetColorIndex(int colorIndex);
	static native int commonCheckNeonMode();
	static native void commonUpdateMultiplier(boolean isVoice);
	static native int commonProcess(byte[] bfft, int deltaMillis, int bt);

	private static native void setLerp(boolean lerp);
	private static native void init(int bgColor);
	private static native void terminate();
	private static native int prepareSurface(Surface surface);
	private static native void process(byte[] bfft, int deltaMillis, Surface surface);
	private static native void processVoice(byte[] bfft, Surface surface);

	static native int glOnSurfaceCreated(int bgColor, int type, int estimatedWidth, int estimatedHeight, int dp1OrLess);
	static native void glOnSurfaceChanged(int width, int height, int dp1OrLess);
	static native int glLoadBitmapFromJava(Bitmap bitmap);
	static native void glDrawFrame();
	static native void glReleaseView();

	private byte[] bfft;
	private final SlimLock lock;
	private Point point;
	private SurfaceHolder surfaceHolder;
	private int state, colorIndex;
	private boolean lerp, voice;
	private Surface surface;
	
	public SimpleVisualizerJni(Context context, Activity activity, boolean landscape, Intent extras) {
		super(context);
		bfft = new byte[2048];
		init(UI.color_visualizer565);
		lock = new SlimLock();
		point = new Point();
		setClickable(true);
		setFocusableInTouchMode(false);
		setFocusable(false);
		surfaceHolder = getHolder();
		surfaceHolder.addCallback(this);
		state = 0;
		colorIndex = 0;
		lerp = false;
		voice = false;
		setLerp(lerp);
		commonSetColorIndex(colorIndex);
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public final boolean isOpaque() {
		return true;
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		case MNU_COLOR:
			colorIndex = ((colorIndex == 0) ? 257 : 0);
			break;
		case MNU_LORES:
			lerp = false;
			voice = false;
			state = 0;
			break;
		case MNU_HIRES:
			lerp = true;
			voice = false;
			state = 1;
			break;
		case MNU_VOICEPRINT:
			lerp = false;
			voice = true;
			state = 2;
			break;
		}
		if (item.getItemId() != MNU_COLOR)
			commonUpdateMultiplier(voice);
		setLerp(lerp);
		commonSetColorIndex(colorIndex);
		return true;
	}
	
	//Runs on the MAIN thread
	@Override
	public VisualizerView getView() {
		return this;
	}

	//Runs on the MAIN thread
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	}

	//Runs on the MAIN thread
	@Override
	public void onCreateContextMenu(ContextMenu menu) {
		UI.separator(menu, 1, 0);
		menu.add(1, MNU_COLOR, 1, (colorIndex == 0) ? R.string.green : R.string.blue)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_THEME));
		UI.separator(menu, 1, 2);
		menu.add(2, MNU_LORES, 0, "LoRes")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((!voice && !lerp) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_HIRES, 1, "HiRes")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(lerp ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_VOICEPRINT, 2, "VoicePrint")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(voice ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
	}
	
	//Runs on the MAIN thread
	@Override
	public void onClick() {
		switch (state) {
		case 0:
			lerp = true;
			voice = false;
			state = 1;
			break;
		case 1:
			lerp = false;
			voice = true;
			state = 2;
			break;
		default:
			lerp = false;
			voice = false;
			state = 0;
			break;
		}
		commonUpdateMultiplier(voice);
		setLerp(lerp);
		commonSetColorIndex(colorIndex);
	}

	//Runs on the MAIN thread
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean requiresScreen() {
		return true;
	}

	//Runs on ANY thread (returned value MUST always be the same)
	@Override
	public int getDesiredPointCount() {
		return 1024;
	}

	//Runs on a SECONDARY thread
	@Override
	public void load(Context context) {
		commonCheckNeonMode();
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
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing, int deltaMillis) {
		if (!lock.lockLowPriority())
			return;
		try {
			if (surface != null) {
				//WE MUST NEVER call any method from visualizer
				//while the player is not actually playing
				if (!playing)
					Arrays.fill(bfft, 0, 1024, (byte)0);
				else
					visualizer.getFft(bfft);
				if (!voice)
					process(bfft, deltaMillis, surface);
				else
					processVoice(bfft, surface);
			}
		} finally {
			lock.releaseLowPriority();
		}
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void release() {
		bfft = null;
		terminate();
	}
	
	//Runs on the MAIN thread (returned value MUST always be the same)
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
		terminate();
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
