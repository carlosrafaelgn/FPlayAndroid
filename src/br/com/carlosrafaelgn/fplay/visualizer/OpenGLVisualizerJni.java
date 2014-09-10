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

import java.util.Arrays;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Message;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.ViewDebug.ExportedProperty;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class OpenGLVisualizerJni extends GLSurfaceView implements GLSurfaceView.Renderer, GLSurfaceView.EGLConfigChooser, Visualizer, VisualizerView, MenuItem.OnMenuItemClickListener, MainHandler.Callback {
	public static final int MNU_COLOR = MNU_VISUALIZER + 1, MNU_SPEED0 = MNU_VISUALIZER + 2, MNU_SPEED1 = MNU_VISUALIZER + 3, MNU_SPEED2 = MNU_VISUALIZER + 4;	
	
	private byte[] bfft;
	private volatile boolean supported, alerted, okToRender;
	private volatile int error;
	private int colorIndex, speed;
	
	public OpenGLVisualizerJni(Context context, boolean landscape) {
		super(context);
		bfft = new byte[2048];
		setClickable(true);
		setFocusable(false);
		colorIndex = 257;
		speed = 1;
		
		final ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
		final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
		if (configurationInfo.reqGlEsVersion < 0x20000)
			supported = false; //watch out for the emulator BUG! http://stackoverflow.com/questions/7190240/eclipse-and-opengl-es-2-0-info-reqglesversion-is-zero
		else
			supported = true;
		
		setEGLContextClientVersion(2);
		setEGLConfigChooser(this);
		setRenderer(this);
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		if (!supported)
			MainHandler.sendMessage(this, 0);
	}
	
	@Override
	public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
		int[] num_config = new int[1];
		int[] value = new int[1], dv = new int[1], av = new int[1];
		EGLConfig[] configs = new EGLConfig[32];
		EGLConfig cfg = null;
		int rgb = 0, actualRGB = 0, a = Integer.MAX_VALUE, d = Integer.MAX_VALUE;
		boolean nat = false, actualNat = false;
		if (egl.eglGetConfigs(display, configs, 32, num_config) && num_config[0] > 0) {
			for (int i = 0; i < num_config[0]; i++) {
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_RENDERABLE_TYPE, value);
				//EGL_OPENGL_ES2_BIT = 4
				if ((value[0] & 4) == 0) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_SURFACE_TYPE, value);
				if ((value[0] & EGL10.EGL_WINDOW_BIT) == 0) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_RED_SIZE, value);
				if (value[0] < 4) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_GREEN_SIZE, value);
				if (value[0] < 4) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_BLUE_SIZE, value);
				if (value[0] < 4) continue;
				if (value[0] < actualRGB) continue;
				rgb = value[0];
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_NATIVE_RENDERABLE, value);
				nat = (value[0] == 1);
				if (!nat && actualNat) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_DEPTH_SIZE, dv);
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_ALPHA_SIZE, av);
				if (dv[0] <= d && av[0] <= a) {
					actualRGB = rgb;
					actualNat = nat;
					d = dv[0];
					a = av[0];
					cfg = configs[i];
				} else if (dv[0] < d) {
					actualRGB = rgb;
					actualNat = nat;
					d = dv[0];
					cfg = configs[i];
				} else if (av[0] < a) {
					actualRGB = rgb;
					actualNat = nat;
					a = av[0];
					cfg = configs[i];
				}
			}
		}
		if (cfg == null || !supported) {
			supported = false;
			MainHandler.sendMessage(this, 0);
		}
		return cfg;
	}
	
	// Runs on a secondary thread
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		if (!supported)
			return;
		if ((error = SimpleVisualizerJni.glOnSurfaceCreated(UI.color_visualizer)) != 0) {
			supported = false;
			okToRender = false;
			MainHandler.sendMessage(this, 0);
		}
	}
	
	// Runs on a secondary thread
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		if (!supported)
			return;
		okToRender = true;
	}
	
	// Runs on the main thread
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		//is it really necessary to call any cleanup code??????
		okToRender = false;
		super.surfaceDestroyed(holder);
	}
	
	// Runs on a secondary thread
	@Override
	public void onDrawFrame(GL10 gl) {
		if (okToRender)
			SimpleVisualizerJni.glDrawFrame();
	}
	
	@Override
	public boolean handleMessage(Message msg) {
		if (!alerted) {
			okToRender = false;
			alerted = true;
			UI.toast(getContext(), (error != 0) ? ("OpenGL " + error + " :(") : "Not supported :(");
		}
		return true;
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
			SimpleVisualizerJni.glChangeColorIndex(colorIndex);
			break;
		case MNU_SPEED0:
			speed = 0;
			SimpleVisualizerJni.glChangeSpeed(0);
			break;
		case MNU_SPEED1:
			speed = 1;
			SimpleVisualizerJni.glChangeSpeed(1);
			break;
		case MNU_SPEED2:
			speed = 2;
			SimpleVisualizerJni.glChangeSpeed(2);
			break;
		}
		return true;
	}
	
	//Runs on the MAIN thread
	@Override
	public VisualizerView getView() {
		return this;
	}
	
	//Runs on the MAIN thread
	@Override
	public void onCreateContextMenu(ContextMenu menu) {
		UI.separator(menu, 1, 0);
		menu.add(1, MNU_COLOR, 1, (colorIndex == 0) ? R.string.green : R.string.blue)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_THEME));
		UI.separator(menu, 1, 2);
		menu.add(2, MNU_SPEED0, 0, "LoSpeed")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed != 1 && speed != 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED1, 1, "MedSpeed")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 1) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED2, 2, "HiSpeed")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
	}
	
	//Runs on the MAIN thread
	@Override
	public void onClick() {
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
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing, int deltaMillis) {
		if (okToRender) {
			//WE MUST NEVER call any method from visualizer
			//while the player is not actually playing
			if (!playing)
				Arrays.fill(bfft, 0, 1024, (byte)0);
			else
				visualizer.getFft(bfft);
			SimpleVisualizerJni.glProcess(bfft);
			requestRender();
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
		return true;
	}
	
	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight) {
		return new Point(availableWidth, availableHeight);
	}
	
	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
	}

}
