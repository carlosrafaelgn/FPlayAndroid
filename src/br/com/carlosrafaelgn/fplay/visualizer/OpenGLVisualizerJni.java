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
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Message;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.ViewDebug.ExportedProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;

public final class OpenGLVisualizerJni extends GLSurfaceView implements GLSurfaceView.Renderer, GLSurfaceView.EGLContextFactory, GLSurfaceView.EGLWindowSurfaceFactory, Visualizer, VisualizerView, MenuItem.OnMenuItemClickListener, MainHandler.Callback {
	private static final int MNU_COLOR = MNU_VISUALIZER + 1, MNU_SPEED0 = MNU_VISUALIZER + 2, MNU_SPEED1 = MNU_VISUALIZER + 3, MNU_SPEED2 = MNU_VISUALIZER + 4;
	
	private static int GLVersion = -1;
	
	private byte[] bfft;
	private volatile boolean supported, alerted, okToRender;
	private volatile int error;
	private int colorIndex, speed;
	private EGLConfig config;
	
	public OpenGLVisualizerJni(Context context, Activity activity, boolean landscape) {
		super(context);
		bfft = new byte[2048];
		setClickable(true);
		setFocusable(false);
		colorIndex = 0;
		speed = 2;
		SimpleVisualizerJni.glChangeColorIndex(colorIndex);
		SimpleVisualizerJni.glChangeSpeed(speed);

		if (GLVersion != -1) {
			supported = (GLVersion >= 0x00020000);
			if (!supported)
				MainHandler.sendMessage(this, 0);
		}
		
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/opengl/GLSurfaceView.java
		//getHolder().setFormat(PixelFormat.RGB_565);
		//setEGLContextClientVersion(2);
		//setEGLConfigChooser(5, 6, 5, 0, 0, 0);
		setEGLContextFactory(this);
		setEGLWindowSurfaceFactory(this);
		setRenderer(this);
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}
	
	@Override
	public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object native_window) {
		try {
			EGLSurface s = egl.eglCreateWindowSurface(display, (this.config != null) ? this.config : config, native_window, null);
			return s;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	@Override
	public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
		if (egl != null && display != null && surface != null)
			egl.eglDestroySurface(display, surface);
	}
	
	@Override
	public EGLContext createContext(final EGL10 egl, final EGLDisplay display, EGLConfig config) {
		
		//https://www.khronos.org/registry/egl/sdk/docs/man/html/eglChooseConfig.xhtml
		//https://www.khronos.org/registry/egl/sdk/docs/man/html/eglCreateContext.xhtml
		
		egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
		this.config = null;
		//EGL_FALSE = 0
		//EGL_TRUE = 1
		//EGL_OPENGL_ES2_BIT = 4
		//EGL_CONTEXT_CLIENT_VERSION = 0x3098
		final EGLConfig[] configs = new EGLConfig[64], selectedConfigs = new EGLConfig[64];
		final int[] num_config = { 0 }, value = new int[1];
		final int[] none = { EGL10.EGL_NONE };
		final int[] v2 = { 0x3098, 2, EGL10.EGL_NONE };
		int selectedCount = 0;
		if (egl.eglGetConfigs(display, configs, 32, num_config) && num_config[0] > 0) {
			for (int i = 0; i < num_config[0]; i++) {
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_RENDERABLE_TYPE, value);
				if ((value[0] & 4) == 0) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_SURFACE_TYPE, value);
				if ((value[0] & EGL10.EGL_WINDOW_BIT) == 0) continue;
				//egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_COLOR_BUFFER_TYPE, value);
				//if (value[0] != EGL10.EGL_RGB_BUFFER) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_RED_SIZE, value);
				if (value[0] < 4) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_GREEN_SIZE, value);
				if (value[0] < 4) continue;
				egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_BLUE_SIZE, value);
				if (value[0] < 4) continue;
				selectedConfigs[selectedCount++] = configs[i];
			}
		}
		if (selectedCount == 0) {
			supported = false;
			MainHandler.sendMessage(this, 0);
			return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, none);
		}
		ArraySorter.sort(selectedConfigs, 0, selectedCount, new ArraySorter.Comparer<EGLConfig>() {
			@Override
			public int compare(EGLConfig a, EGLConfig b) {
				int x;
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_COLOR_BUFFER_TYPE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_COLOR_BUFFER_TYPE, value);
				//prefer rgb buffers
				if (x != value[0])
					return (x == EGL10.EGL_RGB_BUFFER) ? -1 : 1;
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_NATIVE_RENDERABLE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_NATIVE_RENDERABLE, value);
				//prefer native configs
				if (x != value[0])
					return (x != 0) ? -1 : 1;
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_SAMPLE_BUFFERS, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_SAMPLE_BUFFERS, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_SAMPLES, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_SAMPLES, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_BUFFER_SIZE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_BUFFER_SIZE, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_DEPTH_SIZE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_DEPTH_SIZE, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_STENCIL_SIZE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_STENCIL_SIZE, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_ALPHA_MASK_SIZE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_ALPHA_MASK_SIZE, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_ALPHA_SIZE, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_ALPHA_SIZE, value);
				//prefer smaller values
				if (x != value[0])
					return (x - value[0]);
				egl.eglGetConfigAttrib(display, a, EGL10.EGL_CONFIG_ID, value);
				x = value[0];
				egl.eglGetConfigAttrib(display, b, EGL10.EGL_CONFIG_ID, value);
				//prefer smaller values
				return (x - value[0]);
			}
		});
		//according to this:
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/opengl/GLSurfaceView.java#941
		//the native_window parameter in cretaeWindowSurface is this SurfaceHolder 
		final SurfaceHolder holder = getHolder();
		for (int i = 0; i < selectedCount; i++) {
			final int r, g, b;
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_BUFFER_SIZE, value);
			//System.out.print(i + " bs" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_SAMPLE_BUFFERS, value);
			//System.out.print(" sp" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_SAMPLES, value);
			//System.out.print(" s" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_DEPTH_SIZE, value);
			//System.out.print(" d" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_STENCIL_SIZE, value);
			//System.out.print(" st" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_ALPHA_SIZE, value);
			//System.out.print(" a" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_ALPHA_MASK_SIZE, value);
			//System.out.print(" am" + value[0]);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_RED_SIZE, value);
			r = value[0];
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_GREEN_SIZE, value);
			g = value[0];
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_BLUE_SIZE, value);
			b = value[0];
			//System.out.println(" rgb" + r + " " + g + " " + b);
			if (r != 8 || g != 8 || b != 8) {
				if (r != 5 || g != 6 || b != 5)
					continue;
			}
			EGLSurface surface = null;
			try {
				this.config = selectedConfigs[i];
				EGLContext ctx = egl.eglCreateContext(display, this.config, EGL10.EGL_NO_CONTEXT, v2);
				if (ctx == null || ctx == EGL10.EGL_NO_CONTEXT)
					ctx = egl.eglCreateContext(display, this.config, EGL10.EGL_NO_CONTEXT, none);
				if (ctx != null && ctx != EGL10.EGL_NO_CONTEXT) {
					//try to create a surface and make it current successfully
					//before confirming this is the right config/context
					holder.setFormat((r == 5) ? PixelFormat.RGB_565 : PixelFormat.RGBA_8888);
					surface = egl.eglCreateWindowSurface(display, this.config, holder, null);
					if (surface != null && surface != EGL10.EGL_NO_SURFACE) {
						//try to make current
						if (egl.eglMakeCurrent(display, surface, surface, ctx)) {
							//yes! this combination works!!!
							return ctx;
						}
						this.config = null;
					}
				}
			} catch (Throwable ex) {
			} finally {
				egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
				if (surface != null && surface != EGL10.EGL_NO_SURFACE)
					egl.eglDestroySurface(display, surface);
				surface = null;
			}
		}
		this.config = null;
		supported = false;
		MainHandler.sendMessage(this, 0);
		return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, none);
	}
	
	@Override
	public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
		if (egl != null && display != null && context != null)
			egl.eglDestroyContext(display, context);
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		SimpleVisualizerJni.glChangeColorIndex(colorIndex);
		SimpleVisualizerJni.glChangeSpeed(speed);
		if (GLVersion == -1) {
			supported = true;
			Process ifc = null;
			BufferedReader bis = null;
			try {
				ifc = Runtime.getRuntime().exec("getprop ro.opengles.version");
				bis = new BufferedReader(new InputStreamReader(ifc.getInputStream()));
				String line = bis.readLine();
				GLVersion = Integer.parseInt(line);
				supported = (GLVersion >= 0x00020000);
				if (!supported)
					MainHandler.sendMessage(this, 0);
			} catch (Throwable ex) {
			} finally {
				try {
					if (ifc != null)
						ifc.destroy();
				} catch (Throwable ex) {
				}
				try {
					if (bis != null)
						bis.close();
				} catch (Throwable ex) {
				}
			}
		}
		if (!supported)
			return;
		if ((error = SimpleVisualizerJni.glOnSurfaceCreated(UI.color_visualizer)) != 0) {
			supported = false;
			MainHandler.sendMessage(this, 0);
		}
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		if (!supported)
			return;
		SimpleVisualizerJni.glOnSurfaceChanged(width, height);
		okToRender = true;
	}
	
	//Runs on the MAIN thread
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		//is it really necessary to call any cleanup code??????
		okToRender = false;
		super.surfaceDestroyed(holder);
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void onDrawFrame(GL10 gl) {
		if (okToRender)
			SimpleVisualizerJni.glDrawFrame();
	}
	
	@Override
	public boolean handleMessage(Message msg) {
		if (!alerted) {
			alerted = true;
			final Context ctx = getContext();
			UI.toast(ctx, ctx.getText(R.string.sorry) + ((error != 0) ? (ctx.getText(R.string.opengl_error).toString() + error) : ctx.getText(R.string.opengl_not_supported).toString()) + " :(");
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
		final int id = item.getItemId();
		switch (id) {
		case MNU_COLOR:
			colorIndex = ((colorIndex == 0) ? 257 : 0);
			SimpleVisualizerJni.glChangeColorIndex(colorIndex);
			break;
		case MNU_SPEED0:
		case MNU_SPEED1:
		case MNU_SPEED2:
			speed = id - MNU_SPEED0;
			SimpleVisualizerJni.glChangeSpeed(speed);
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
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	}

	//Runs on the MAIN thread
	@Override
	public void onCreateContextMenu(ContextMenu menu) {
		final Context ctx = getContext();
		UI.separator(menu, 1, 0);
		menu.add(1, MNU_COLOR, 1, (colorIndex == 0) ? R.string.green : R.string.blue)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_THEME));
		UI.separator(menu, 1, 2);
		menu.add(2, MNU_SPEED0, 0, ctx.getText(R.string.speed) + ": 0")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed != 1 && speed != 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED1, 1, ctx.getText(R.string.speed) + ": 1")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 1) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED2, 2, ctx.getText(R.string.speed) + ": 2")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
	}
	
	//Runs on the MAIN thread
	@Override
	public void onClick() {
		drawNow = true;
	}

	//Runs on the MAIN thread
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
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
	volatile boolean drawNow;
	//Runs on a SECONDARY thread
	@Override
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing, int deltaMillis) {
		if (okToRender) {// && drawNow) {
			//WE MUST NEVER call any method from visualizer
			//while the player is not actually playing
			if (!playing)
				Arrays.fill(bfft, 0, 1024, (byte)0);
			else
				visualizer.getFft(bfft);
			SimpleVisualizerJni.glOrBTProcess(bfft, deltaMillis, 0);
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
