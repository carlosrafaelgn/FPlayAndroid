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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewDebug.ExportedProperty;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStream;
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
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;

public final class OpenGLVisualizerJni extends GLSurfaceView implements GLSurfaceView.Renderer, GLSurfaceView.EGLContextFactory, GLSurfaceView.EGLWindowSurfaceFactory, Visualizer, MenuItem.OnMenuItemClickListener, MainHandler.Callback, SensorEventListener {
	private static final int MNU_COLOR = MNU_VISUALIZER + 1, MNU_SPEED0 = MNU_VISUALIZER + 2, MNU_SPEED1 = MNU_VISUALIZER + 3, MNU_SPEED2 = MNU_VISUALIZER + 4, MNU_CHOOSE_IMAGE = MNU_VISUALIZER + 5, MNU_DIFFUSION0 = MNU_VISUALIZER + 6, MNU_DIFFUSION1 = MNU_VISUALIZER + 7, MNU_DIFFUSION2 = MNU_VISUALIZER + 8, MNU_DIFFUSION3 = MNU_VISUALIZER + 9, MNU_RISESPEED0 = MNU_VISUALIZER + 10, MNU_RISESPEED1 = MNU_VISUALIZER + 11, MNU_RISESPEED2 = MNU_VISUALIZER + 12, MNU_RISESPEED3 = MNU_VISUALIZER + 13;

	private static final int MSG_OPENGL_ERROR = 0x0600;
	private static final int MSG_CHOOSE_IMAGE = 0x0601;

	private static int GLVersion = -1;

	public static final String EXTRA_VISUALIZER_TYPE = "br.com.carlosrafaelgn.fplay.OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE";

	public static final int TYPE_SPECTRUM = 0;
	public static final int TYPE_LIQUID = 1;
	public static final int TYPE_SPIN = 2;
	public static final int TYPE_PARTICLE = 3;
	public static final int TYPE_IMMERSIVE_PARTICLE = 4;

	private final int type;
	private byte[] waveform;
	private volatile boolean supported, alerted, okToRender, imageChoosenAtLeastOnce;
	private volatile int error;
	private volatile Uri selectedUri;
	private boolean browsing;
	private int colorIndex, speed, viewWidth, viewHeight, diffusion, riseSpeed, ignoreInput;
	private EGLConfig config;
	private Activity activity;
	private SensorManager sensorManager;
	private WindowManager windowManager;
	private Sensor accel, magnetic;

	public OpenGLVisualizerJni(Context context, Activity activity, boolean landscape, Intent extras) {
		super(context);
		final int t = extras.getIntExtra(EXTRA_VISUALIZER_TYPE, TYPE_SPECTRUM);
		type = ((t < TYPE_LIQUID || t > TYPE_IMMERSIVE_PARTICLE) ? TYPE_SPECTRUM : t);
		waveform = new byte[Visualizer.CAPTURE_SIZE];
		setClickable(true);
		setFocusableInTouchMode(false);
		setFocusable(false);
		colorIndex = 0;
		speed = ((type == TYPE_LIQUID) ? 0 : 2);
		diffusion = 1;
		riseSpeed = 1;
		ignoreInput = 0;
		this.activity = activity;

		//initialize these with default values to be used in
		if (landscape) {
			viewWidth = 1024;
			viewHeight = 512;
		} else {
			viewWidth = 512;
			viewHeight = 1024;
		}

		if (GLVersion != -1) {
			supported = (GLVersion >= 0x00020000);
			if (!supported)
				MainHandler.sendMessage(this, MSG_OPENGL_ERROR);
		}

		try {
			windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		} catch (Throwable ex) {
			windowManager = null;
		}

		if (type == TYPE_IMMERSIVE_PARTICLE) {
			try {
				sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
			} catch (Throwable ex) {
				sensorManager = null;
			}
			try {
				accel = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
			} catch (Throwable ex) {
				accel = null;
			}
			if (accel == null) {
				try {
					accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				} catch (Throwable ex) {
					accel = null;
				}
			}
			try {
				magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			} catch (Throwable ex) {
				magnetic = null;
			}
			if (accel == null || magnetic == null) {
				sensorManager = null;
				accel = null;
				magnetic = null;
				UI.toast(activity, R.string.msg_no_sensors);
			} else {
				CharSequence originalText = activity.getText(R.string.msg_immersive);
				final int iconIdx = originalText.toString().indexOf('\u21BA');
				if (iconIdx >= 0) {
					final SpannableStringBuilder txt = new SpannableStringBuilder(originalText);
					txt.setSpan(new ImageSpan(new TextIconDrawable(UI.ICON_3DPAN, UI.colorState_text_visualizer_static.getDefaultColor(), UI._22sp, 0), DynamicDrawableSpan.ALIGN_BASELINE), iconIdx, iconIdx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					originalText = txt;
				}
				UI.customToast(activity, originalText, true, UI._22sp, UI.colorState_text_visualizer_static.getDefaultColor(), new ColorDrawable(0x7f000000 | (UI.color_visualizer565 & 0x00ffffff)));
			}
		}
		
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/opengl/GLSurfaceView.java
		//getHolder().setFormat(PixelFormat.RGB_565);
		//setEGLContextClientVersion(2);
		//setEGLConfigChooser(5, 6, 5, 0, 0, 0);
		setEGLContextFactory(this);
		setEGLWindowSurfaceFactory(this);
		setRenderer(this);
		setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			setPreserveEGLContext();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setPreserveEGLContext() {
		setPreserveEGLContextOnPause(false);
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
			MainHandler.sendMessage(this, MSG_OPENGL_ERROR);
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
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_SAMPLE_BUFFERS, value);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_SAMPLES, value);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_DEPTH_SIZE, value);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_STENCIL_SIZE, value);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_ALPHA_SIZE, value);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_ALPHA_MASK_SIZE, value);
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_RED_SIZE, value);
			r = value[0];
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_GREEN_SIZE, value);
			g = value[0];
			egl.eglGetConfigAttrib(display, selectedConfigs[i], EGL10.EGL_BLUE_SIZE, value);
			b = value[0];
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
		MainHandler.sendMessage(this, MSG_OPENGL_ERROR);
		return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, none);
	}
	
	@Override
	public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
		if (egl != null && display != null && context != null)
			egl.eglDestroyContext(display, context);
	}
	
	//Runs on a SECONDARY thread (A)
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		if (type == TYPE_SPECTRUM)
			SimpleVisualizerJni.commonSetColorIndex(colorIndex);
		SimpleVisualizerJni.commonSetSpeed(speed);
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
					MainHandler.sendMessage(this, MSG_OPENGL_ERROR);
			} catch (Throwable ex) {
			} finally {
				try {
					if (bis != null)
						bis.close();
				} catch (Throwable ex) {
				}
				try {
					if (ifc != null)
						ifc.destroy();
				} catch (Throwable ex) {
				}
			}
		}
		if (!supported)
			return;
		if ((error = SimpleVisualizerJni.glOnSurfaceCreated(UI.color_visualizer, type, UI.screenWidth, UI.screenHeight, (UI._1dp < 2) ? 1 : 0)) != 0) {
			supported = false;
			MainHandler.sendMessage(this, MSG_OPENGL_ERROR);
		}
	}
	
	//Runs on a SECONDARY thread (A)
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		if (!supported)
			return;
		//http://developer.download.nvidia.com/tegra/docs/tegra_android_accelerometer_v5f.pdf
		int rotation;
		if (windowManager != null) {
			try {
				rotation = windowManager.getDefaultDisplay().getRotation();
			} catch (Throwable ex) {
				//silly assumption for phones....
				rotation = ((width >= height) ? Surface.ROTATION_90 : Surface.ROTATION_0);
			}
		} else {
			//silly assumption for phones....
			rotation = ((width >= height) ? Surface.ROTATION_90 : Surface.ROTATION_0);
		}

		viewWidth = width;
		viewHeight = height;
		SimpleVisualizerJni.glOnSurfaceChanged(width, height, rotation, (UI._1dp < 2) ? 1 : 0);
		okToRender = true;
		/*if (type == TYPE_LIQUID && !imageChoosenAtLeastOnce) {
			imageChoosenAtLeastOnce = true;
			MainHandler.sendMessage(this, MSG_CHOOSE_IMAGE);
		}*/
	}
	
	//Runs on the MAIN thread
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		//is it really necessary to call any cleanup code for OpenGL in Android??????
		okToRender = false;
		super.surfaceDestroyed(holder);
		//some times surfaceDestroyed is called, but after resuming,
		//onSurfaceCreated is not called, only onSurfaceChanged is! :/
	}

	private void loadBitmap() {
		if (activity == null || selectedUri == null)
			return;

		/*String path = null;
		int orientation = 1;

		//Try to fetch the image's rotation from EXIF (this process needs the exact path)
		//Based on: http://stackoverflow.com/q/2169649/3569421
		try {
			final String[] projection = {MediaStore.Images.Media.DATA};
			final Cursor cursor = activity.managedQuery(selectedUri, projection, null, null, null);
			if (cursor != null) {
				final int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
				if (column_index >= 0) {
					cursor.moveToFirst();
					path = cursor.getString(column_index);
				}
			}
		} catch (Throwable ex) {
		}
		try {
			//OI FILE Manager
			if (path == null)
				path = selectedUri.getPath();
			orientation = (new ExifInterface(path)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
		} catch (Throwable ex) {
		}*/

		InputStream input = null;
		Bitmap bitmap = null;
		try {
			input = activity.getContentResolver().openInputStream(selectedUri);
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			bitmap = BitmapFactory.decodeStream(input, null, opts);
			input.close();
			input = null;

			final int maxDim = Math.max(320, Math.min(1024, Math.max(viewWidth, viewHeight)));

			opts.inSampleSize = 1;
			int largest = ((opts.outWidth >= opts.outHeight) ? opts.outWidth : opts.outHeight);
			while (largest > maxDim) {
				opts.inSampleSize <<= 1;
				largest >>= 1;
			}

			input = activity.getContentResolver().openInputStream(selectedUri);
			opts.inJustDecodeBounds = false;
			opts.inPreferredConfig = Bitmap.Config.RGB_565;
			opts.inDither = true;
			bitmap = BitmapFactory.decodeStream(input, null, opts);

			if (bitmap != null) {
				if (opts.outWidth != opts.outHeight && ((opts.outWidth > opts.outHeight) != (viewWidth > viewHeight))) {
					//rotate the image 90 degress
					final Matrix matrix = new Matrix();
					matrix.postRotate(-90);
					final Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
					if (bitmap != newBitmap && newBitmap != null) {
						bitmap.recycle();
						bitmap = newBitmap;
					}
					System.gc();
				}
				SimpleVisualizerJni.glLoadBitmapFromJava(bitmap);
			}
		} catch (Throwable ex) {
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Throwable ex) {
				}
			}
			if (bitmap != null)
				bitmap.recycle();
			System.gc();
		}
	}

	//Runs on a SECONDARY thread (A)
	@Override
	public void onDrawFrame(GL10 gl) {
		if (okToRender) {
			if (selectedUri != null) {
				loadBitmap();
				selectedUri = null;
			}
			SimpleVisualizerJni.glDrawFrame();
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_OPENGL_ERROR:
			if (!alerted) {
				alerted = true;
				if (activity == null)
					break;
				final Context ctx = activity.getApplication();
				UI.toast(ctx, ctx.getText(R.string.sorry) + " " + ((error != 0) ? (ctx.getText(R.string.opengl_error).toString() + ": " + error) : ctx.getText(R.string.opengl_not_supported).toString()) + " :(");
			}
			break;
		case MSG_CHOOSE_IMAGE:
			chooseImage();
			break;
		}
		return true;
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public final boolean isOpaque() {
		return true;
	}

	private void chooseImage() {
		//Based on: http://stackoverflow.com/a/20177611/3569421
		//Based on: http://stackoverflow.com/a/4105966/3569421
		if (activity != null && selectedUri == null && !browsing && okToRender) {
			browsing = true;
			imageChoosenAtLeastOnce = true;
			final Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			activity.startActivityForResult(intent, 1234);
		}
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		final int id = item.getItemId();
		switch (id) {
		case MNU_COLOR:
			colorIndex = ((colorIndex == 0) ? 257 : 0);
			SimpleVisualizerJni.commonSetColorIndex(colorIndex);
			break;
		case MNU_SPEED0:
		case MNU_SPEED1:
		case MNU_SPEED2:
			speed = id - MNU_SPEED0;
			SimpleVisualizerJni.commonSetSpeed(speed);
			break;
		case MNU_DIFFUSION0:
		case MNU_DIFFUSION1:
		case MNU_DIFFUSION2:
		case MNU_DIFFUSION3:
			diffusion = id - MNU_DIFFUSION0;
			SimpleVisualizerJni.glSetImmersiveCfg(diffusion, -1);
			break;
		case MNU_RISESPEED0:
		case MNU_RISESPEED1:
		case MNU_RISESPEED2:
		case MNU_RISESPEED3:
			riseSpeed = id - MNU_RISESPEED0;
			SimpleVisualizerJni.glSetImmersiveCfg(-1, riseSpeed);
			break;
		case MNU_CHOOSE_IMAGE:
			chooseImage();
			break;
		}
		return true;
	}

	//Runs on the MAIN thread
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1234) {
			browsing = false;
			if (resultCode == Activity.RESULT_OK)
				selectedUri = data.getData();
		}
	}

	//Runs on the MAIN thread
	public void onActivityPause() {
		if (sensorManager != null)
			sensorManager.unregisterListener(this);
	}

	//Runs on the MAIN thread
	public void onActivityResume() {
		if (sensorManager != null) {
			//SENSOR_DELAY_UI provides a nice refresh rate, but SENSOR_DELAY_GAME
			//provides an AWESOME refresh rate!
			sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
			sensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_GAME);
		}
	}

	//Runs on the MAIN thread
	@Override
	public void onCreateContextMenu(ContextMenu menu) {
		if (activity == null)
			return;
		final Context ctx = activity.getApplication();
		Menu s;
		switch (type) {
		case TYPE_LIQUID:
			UI.separator(menu, 1, 0);
			menu.add(1, MNU_CHOOSE_IMAGE, 1, R.string.choose_image)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_PALETTE));
			break;
		case TYPE_SPIN:
		case TYPE_PARTICLE:
			break;
		case TYPE_IMMERSIVE_PARTICLE:
			UI.separator(menu, 1, 0);
			s = menu.addSubMenu(1, 0, 1, ctx.getText(R.string.diffusion) + "\u2026")
				.setIcon(new TextIconDrawable(UI.ICON_SETTINGS));
			UI.prepare(s);
			s.add(0, MNU_DIFFUSION0, 0, ctx.getText(R.string.diffusion) + ": 0")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((diffusion == 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s.add(0, MNU_DIFFUSION1, 1, ctx.getText(R.string.diffusion) + ": 1")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((diffusion != 0 && diffusion != 2 && diffusion != 3) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s.add(0, MNU_DIFFUSION2, 2, ctx.getText(R.string.diffusion) + ": 2")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((diffusion == 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s.add(0, MNU_DIFFUSION3, 3, ctx.getText(R.string.diffusion) + ": 3")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((diffusion == 3) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s = menu.addSubMenu(1, 0, 2, ctx.getText(R.string.speed) + "\u2026")
				.setIcon(new TextIconDrawable(UI.ICON_SETTINGS));
			UI.prepare(s);
			s.add(0, MNU_RISESPEED0, 0, ctx.getText(R.string.speed) + ": 0")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((riseSpeed == 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s.add(0, MNU_RISESPEED1, 1, ctx.getText(R.string.speed) + ": 1")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((riseSpeed != 0 && riseSpeed != 2 && riseSpeed != 3) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s.add(0, MNU_RISESPEED2, 2, ctx.getText(R.string.speed) + ": 2")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((riseSpeed == 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			s.add(0, MNU_RISESPEED3, 3, ctx.getText(R.string.speed) + ": 3")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((riseSpeed == 3) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			break;
		default:
			UI.separator(menu, 1, 0);
			menu.add(1, MNU_COLOR, 1, (colorIndex == 0) ? R.string.green : R.string.blue)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_PALETTE));
			break;
		}
		UI.separator(menu, 2, 0);
		menu.add(2, MNU_SPEED0, 1, ctx.getText(R.string.sustain) + " 3")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed != 1 && speed != 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED1, 2, ctx.getText(R.string.sustain) + " 2")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 1) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED2, 3, ctx.getText(R.string.sustain) + " 1")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
	}
	
	//Runs on the MAIN thread
	@Override
	public void onClick() {
	}

	//Runs on the MAIN thread
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return true;
	}

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight) {
		return new Point(availableWidth, availableHeight);
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean requiresHiddenControls() {
		return true;
	}

	//Runs on ANY thread (returned value MUST always be the same)
	@Override
	public int dataTypeRequired() {
		return DATA_FFT;
	}
	
	//Runs on a SECONDARY thread (B)
	@Override
	public void load(Context context) {
		SimpleVisualizerJni.commonCheckNeonMode();
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

	//Runs on a SECONDARY thread (B)
	@Override
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing) {
		if (okToRender) {
			//We use ignoreInput, because sampling 1024, 60 times a seconds,
			//is useless, as there are only 44100 or 48000 samples in one second
			if (ignoreInput == 0) {
				//WE MUST NEVER call any method from visualizer
				//while the player is not actually playing
				if (!playing)
					Arrays.fill(waveform, 0, 1024, (byte)0x80);
				else
					visualizer.getWaveForm(waveform);
			}
			SimpleVisualizerJni.commonProcess(waveform, ignoreInput);
			ignoreInput ^= SimpleVisualizerJni.IgnoreInput;
			//requestRender();
		}
	}
	
	//Runs on a SECONDARY thread (B)
	@Override
	public void release() {
		waveform = null;
	}

	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
		windowManager = null;
		if (sensorManager != null) {
			sensorManager.unregisterListener(this);
			sensorManager = null;
			accel = null;
			magnetic = null;
		}
		activity = null;
		SimpleVisualizerJni.glReleaseView();
	}

	//Runs on the MAIN thread
	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		SimpleVisualizerJni.glOnSensorData((sensorEvent.sensor == accel) ? 1 : 2, sensorEvent.values);
	}

	//Runs on the MAIN thread
	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {
	}
}
