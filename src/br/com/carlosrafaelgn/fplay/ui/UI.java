//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013, Carlos Rafael Gimenes das Neves
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
package br.com.carlosrafaelgn.fplay.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;

//
//Unit conversions are based on:
//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/util/TypedValue.java
//
public final class UI {
	public static final int STATE_PRESSED = 1;
	public static final int STATE_FOCUSED = 2;
	public static final int STATE_CURRENT = 4;
	public static final int STATE_SELECTED = 8;
	public static final int STATE_MULTISELECTED = 16;
	public static final int STATE_CHECKED = 32;
	//Some of these colos are also duplicated in colors.xml
	public static final int color_transparent = 0x00000000;
	public static final int color_window = 0xff252525;
	public static final int color_bg = 0xff000000;
	public static final int color_bg_menu = 0xffffffff;
	public static final int color_text = 0xffffffff;
	public static final int color_text_secondary = 0xff959595;
	public static final int color_text_selected = 0xff000000;
	public static final int color_text_menu = 0xff000000;
	public static final int color_text_menu_icon = 0xff555555;
	public static final int color_selected = 0xffadd6fd;
	public static final int color_selected_multi = 0xff779aba;//60% #add6fd over #000000 (adjusted to comply with minimum contrast ratio according to WCAG 2.0)
	public static final int color_selected_grad_lt = 0xffd1e8ff;
	public static final int color_selected_grad_dk = 0xff5da2e3;//80bffa;
	public static final int color_selected_border = 0xff518ec2;
	public static final int color_selected_pressed = 0xffcfe1ff;
	public static final int color_selected_pressed_border = 0xff4981b0;//darker version of #518ec2
	public static final int color_current = 0xfffad35a;
	public static final int color_current_darker = 0xffaf943f;//70% #fad35a over #000000
	public static final int color_current_multi = 0xffadbbb1;//60% #add6fd over 70% #fad35a over #000000
	public static final int color_current_grad_lt = 0xfff7eb6a;
	public static final int color_current_grad_dk = 0xfffeb645;
	public static final int color_current_border = 0xffad9040;
	public static final int color_current_pressed = 0xffffeed4;//ffd99e;//db9f42;//f0a42d;//feb645;
	public static final int color_current_pressed_border = 0xff94671e;//darker version of #ad9040
	
	public static final String ICON_PREV = "<";
	public static final String ICON_PLAY = "P";
	public static final String ICON_PAUSE = "|";
	public static final String ICON_NEXT = ">";
	public static final String ICON_MENU = "N";
	public static final String ICON_LIST = "l";
	public static final String ICON_MOVE = "M";
	public static final String ICON_REMOVE = "R";
	public static final String ICON_UP = "^";
	public static final String ICON_GOBACK = "_";
	public static final String ICON_SAVE = "S";
	public static final String ICON_LOAD = "L";
	public static final String ICON_FAVORITE_ON = "#";
	public static final String ICON_FAVORITE_OFF = "*";
	public static final String ICON_ADD = "A";
	public static final String ICON_HOME = "H";
	public static final String ICON_LINK = "U";
	public static final String ICON_EQUALIZER = "E";
	public static final String ICON_SETTINGS = "s";
	public static final String ICON_VISUALIZER = "V";
	public static final String ICON_EXIT = "X";
	public static final String ICON_VOLUME0 = "0";
	public static final String ICON_VOLUME1 = "1";
	public static final String ICON_VOLUME2 = "2";
	public static final String ICON_VOLUME3 = "3";
	public static final String ICON_VOLUME4 = "4";
	public static final String ICON_DECREASE_VOLUME = "-";
	public static final String ICON_INCREASE_VOLUME = "+";
	public static final String ICON_FIND = "F";
	public static final String ICON_INFORMATION = "I";
	public static final String ICON_QUESTION = "?";
	public static final String ICON_EXCLAMATION = "!";
	public static final String ICON_SHUFFLE = "h";
	public static final String ICON_REPEAT = "t";
	public static final String ICON_DELETE = "D";
	public static final String ICON_RADIOCHK = "x";
	public static final String ICON_RADIOUNCHK = "o";
	public static final String ICON_GRIP = "G";
	
	public static final ColorStateList colorState_text_normal = new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { color_text_selected, color_text_selected, color_text });
	public static final ColorStateList colorState_text_sel = ColorStateList.valueOf(color_text_selected);
	public static final ColorStateList colorState_text_highlight = ColorStateList.valueOf(color_current);//color_selected_border);
	
	public static Typeface iconsTypeface;
	
	private static class Gradient {
		private static final Gradient[] gradients = new Gradient[16];
		private static int pos, count;
		public final boolean current, vertical;
		public final int size;
		public final LinearGradient gradient;
		
		private Gradient(boolean current, boolean vertical, int size) {
			this.current = current;
			this.vertical = vertical;
			this.size = size;
			this.gradient = (current ? new LinearGradient(0, 0, (vertical ? size : 0), (vertical ? 0 : size), color_current_grad_lt, color_current_grad_dk, Shader.TileMode.CLAMP) :
				new LinearGradient(0, 0, (vertical ? size : 0), (vertical ? 0 : size), color_selected_grad_lt, color_selected_grad_dk, Shader.TileMode.CLAMP));
		}
		
		public static LinearGradient getGradient(boolean current, boolean vertical, int size) {
			//a LRU algorithm could be implemented here...
			for (int i = count - 1; i >= 0; i--) {
				if (gradients[i].size == size && gradients[i].current == current && gradients[i].vertical == vertical)
					return gradients[i].gradient;
			}
			if (count < 16) {
				pos = count;
				count++;
			} else {
				pos = (pos + 1) & 15;
			}
			final Gradient g = new Gradient(current, vertical, size);
			gradients[pos] = g;
			return g.gradient;
		}
	}
	
	public static final Rect rect = new Rect();
	public static boolean isLandscape, isLargeScreen, isLowDpiScreen;
	public static int _1dp, _2dp, _4dp, _8dp, _16dp, _2sp, _4sp, _8sp, _22sp, _18sp, _14sp, _22spBox, _18spBox, _14spBox, _22spYinBox, _18spYinBox, _14spYinBox, defaultControlContentsSize, defaultControlSize, usableScreenWidth, usableScreenHeight, screenWidth, screenHeight;
	public static Bitmap icPrev, icPlay, icPause, icNext, icPrevNotif, icPlayNotif, icPauseNotif, icNextNotif;
	private static int _1dpStroke;
	private static float _1dpInset;
	
	private static String emptyListString;
    private static int emptyListStringHalfWidth;
	
	private static float density, scaledDensity, xdpi_1_72;
	
	private static final Paint strokePaint, fillPaint;
	private static final TextPaint textPaint;
	
	static {
		strokePaint = new Paint();
		strokePaint.setDither(false);
		strokePaint.setAntiAlias(false);
		strokePaint.setStyle(Paint.Style.STROKE);
		strokePaint.setStrokeCap(Cap.BUTT);
		fillPaint = new Paint();
		fillPaint.setDither(false);
		fillPaint.setAntiAlias(false);
		fillPaint.setStyle(Paint.Style.FILL);
		textPaint = new TextPaint();
		textPaint.setDither(false);
		textPaint.setAntiAlias(true);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTypeface(Typeface.SANS_SERIF);
		textPaint.setTextAlign(Paint.Align.LEFT);
		textPaint.setColor(color_text);
		textPaint.measureText("FPlay");
	}
	
	private static void initializeScreenDimensions(Display display, DisplayMetrics outDisplayMetrics) {
		display.getMetrics(outDisplayMetrics);
		screenWidth = outDisplayMetrics.widthPixels;
		screenHeight = outDisplayMetrics.heightPixels;
		usableScreenWidth = screenWidth;
		usableScreenHeight = screenHeight;
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void initializeScreenDimensions14(Display display, DisplayMetrics outDisplayMetrics) {
		try {
			screenWidth = (Integer)Display.class.getMethod("getRawWidth").invoke(display);
			screenHeight = (Integer)Display.class.getMethod("getRawHeight").invoke(display);
		} catch (Throwable ex) {
			initializeScreenDimensions(display, outDisplayMetrics);
			return;
		}
		display.getMetrics(outDisplayMetrics);
		usableScreenWidth = outDisplayMetrics.widthPixels;
		usableScreenHeight = outDisplayMetrics.heightPixels;
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private static void initializeScreenDimensions17(Display display, DisplayMetrics outDisplayMetrics) {
		display.getMetrics(outDisplayMetrics);
		usableScreenWidth = outDisplayMetrics.widthPixels;
		usableScreenHeight = outDisplayMetrics.heightPixels;
		display.getRealMetrics(outDisplayMetrics);
		screenWidth = outDisplayMetrics.widthPixels;
		screenHeight = outDisplayMetrics.heightPixels;
	}
	
	public static void initialize(Context context) {
		if (iconsTypeface == null)
			iconsTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/icons.ttf");
		final Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		final DisplayMetrics displayMetrics = new DisplayMetrics();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			initializeScreenDimensions17(display, displayMetrics);
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			initializeScreenDimensions14(display, displayMetrics);
		else
			initializeScreenDimensions(display, displayMetrics);
		density = displayMetrics.density;
		scaledDensity = displayMetrics.scaledDensity;
		xdpi_1_72 = displayMetrics.xdpi * (1.0f / 72.0f);
		//improved detection for tablets, based on:
		//http://developer.android.com/guide/practices/screens_support.html#DeclaringTabletLayouts
		//(There is also the solution at http://stackoverflow.com/questions/11330363/how-to-detect-device-is-android-phone-or-android-tablet
		//but the former link says it is deprecated...)
		isLargeScreen = ((screenWidth >= dpToPxI(600)) && (screenHeight >= dpToPxI(600)));
		isLandscape = (screenWidth >= screenHeight);
		isLowDpiScreen = (displayMetrics.densityDpi < 160);
		_1dp = dpToPxI(1);
		if (_1dp == 1) {
			_1dpStroke = 2;
			_1dpInset = 0;
		} else {
			_1dpStroke = _1dp;
			_1dpInset = ((float)_1dp * 0.5f);
		}
		_2dp = dpToPxI(2);
		_4dp = dpToPxI(4);
		_8dp = dpToPxI(8);
		_16dp = dpToPxI(16);
		_2sp = spToPxI(2);
		_4sp = spToPxI(4);
		_8sp = spToPxI(8);
		_22sp = spToPxI(22);
		_18sp = spToPxI(18);
		_14sp = spToPxI(14);
		defaultControlContentsSize = dpToPxI(32);
		defaultControlSize = defaultControlContentsSize + (UI._8sp << 1);
		strokePaint.setStrokeWidth(_1dpStroke);
		//Font Metrics in Java OR How, the hell, Should I Position This Font?!
		//http://blog.evendanan.net/2011/12/Font-Metrics-in-Java-OR-How-the-hell-Should-I-Position-This-Font
		textPaint.setTextSize(_22sp);
		final FontMetrics fm = textPaint.getFontMetrics();
		_22spBox = (int)(fm.descent - fm.ascent + 0.5f);
		_22spYinBox = _22spBox - (int)(fm.descent);
		textPaint.setTextSize(_18sp);
		textPaint.getFontMetrics(fm);
		_18spBox = (int)(fm.descent - fm.ascent + 0.5f);
		_18spYinBox = _18spBox - (int)(fm.descent);
		textPaint.setTextSize(_14sp);
		textPaint.getFontMetrics(fm);
		_14spBox = (int)(fm.descent - fm.ascent + 0.5f);
		_14spYinBox = _14spBox - (int)(fm.descent);
		emptyListString = context.getText(R.string.empty_list).toString();
		emptyListStringHalfWidth = measureText(emptyListString, _18sp) >> 1;
	}
	
	public static void preparePlaybackIcons(Context context) {
		if (icPrev != null)
			return;
		if (iconsTypeface == null)
			initialize(context);
	    final Canvas c = new Canvas();
	    textPaint.setTypeface(iconsTypeface);
		textPaint.setColor(0xff000000);
		textPaint.setTextSize(defaultControlContentsSize);
		icPrev = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icPrev);
	    c.drawText(ICON_PREV, 0, defaultControlContentsSize, textPaint);
	    icPlay = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icPlay);
	    c.drawText(ICON_PLAY, 0, defaultControlContentsSize, textPaint);
	    icPause = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icPause);
	    c.drawText(ICON_PAUSE, 0, defaultControlContentsSize, textPaint);
	    icNext = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icNext);
	    c.drawText(ICON_NEXT, 0, defaultControlContentsSize, textPaint);
	    //reset to the original state
	    textPaint.setTypeface(Typeface.SANS_SERIF);
		textPaint.setColor(color_text);
		textPaint.measureText("FPlay");
	}
	
	public static void prepareNotificationPlaybackIcons(Context context) {
		if (icPrevNotif != null)
			return;
		if (iconsTypeface == null)
			initialize(context);
	    final Canvas c = new Canvas();
	    textPaint.setTypeface(iconsTypeface);
		textPaint.setColor(0xffffffff);
		textPaint.setTextSize(defaultControlContentsSize);
		icPrevNotif = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icPrevNotif);
	    c.drawText(ICON_PREV, 0, defaultControlContentsSize, textPaint);
	    icPlayNotif = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icPlayNotif);
	    c.drawText(ICON_PLAY, 0, defaultControlContentsSize, textPaint);
	    icPauseNotif = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icPauseNotif);
	    c.drawText(ICON_PAUSE, 0, defaultControlContentsSize, textPaint);
	    icNextNotif = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
	    c.setBitmap(icNextNotif);
	    c.drawText(ICON_NEXT, 0, defaultControlContentsSize, textPaint);
	    //reset to the original state
	    textPaint.setTypeface(Typeface.SANS_SERIF);
		textPaint.setColor(color_text);
		textPaint.measureText("FPlay");
	}
	
	public static float pxToDp(float px) {
		return px / density;
	}
	
	public static float pxToSp(float px) {
		return px / scaledDensity;
	}
	
	public static float pxToPt(float px) {
		return px / xdpi_1_72;
	}
	
	public static float dpToPx(float dp) {
		return dp * density;
	}
	
	public static float spToPx(float sp) {
		return sp * scaledDensity;
	}
	
	public static float ptToPx(float pt) {
		return pt * xdpi_1_72;
	}
	
	public static int dpToPxI(float dp) {
		return (int)((dp * density) + 0.5f);
	}
	
	public static int spToPxI(float sp) {
		return (int)((sp * scaledDensity) + 0.5f);
	}
	
	public static int ptToPxI(float pt) {
		return (int)((pt * xdpi_1_72) + 0.5f);
	}
	
	public static String ellipsizeText(String text, int size, int width) {
		textPaint.setTextSize(size);
		return TextUtils.ellipsize(text, textPaint, width, TruncateAt.END).toString();
	}
	
	public static int measureText(String text, int size) {
		textPaint.setTextSize(size);
		return (int)(textPaint.measureText(text) + 0.5f);
	}
	
	public static void drawText(Canvas canvas, String text, int color, int size, int x, int y) {
		textPaint.setColor(color);
		textPaint.setTextSize(size);
		canvas.drawText(text, x, y, textPaint);
	}
	
	public static void drawEmptyListString(Canvas canvas) {
		//top and left must be 0 for this to work correctly
		textPaint.setColor(color_text_secondary);
		textPaint.setTextSize(_18sp);
		canvas.drawText(emptyListString, (UI.rect.right >> 1) - emptyListStringHalfWidth, (UI.rect.bottom >> 1) - (_18spBox >> 1) + _18spYinBox, textPaint);
	}
	
	public static void drawRect(Canvas canvas, int strokeColor, int fillColor, Rect rect) {
		if (fillColor != 0) {
			fillPaint.setColor(fillColor);
			canvas.drawRect(rect, fillPaint);
		}
		if (strokeColor != 0) {
			strokePaint.setColor(strokeColor);
			canvas.drawRect((float)rect.left + _1dpInset, (float)rect.top + _1dpInset, (float)rect.right - _1dpInset, (float)rect.bottom - _1dpInset, strokePaint);
		}
	}
	
	public static int getBorderColor(int state) {
		if ((state & STATE_PRESSED) != 0)
			return (((state & (STATE_FOCUSED | STATE_CURRENT)) != 0) ? color_current_pressed_border : color_selected_pressed_border);
		if ((state & (STATE_SELECTED | STATE_FOCUSED)) != 0)
			return (((state & (STATE_FOCUSED | STATE_CURRENT)) != 0) ? color_current_border : color_selected_border);
		return 0;
	}
	
	public static void drawBgBorderless(Canvas canvas, int state, Rect rect) {
		if (state == 0)
			return;
		if ((state & STATE_PRESSED) != 0) {
			fillPaint.setColor(((state & (STATE_FOCUSED | STATE_CURRENT)) != 0) ? color_current_pressed : color_selected_pressed);
			canvas.drawRect(rect, fillPaint);
		} else {
			if ((state & (STATE_SELECTED | STATE_FOCUSED)) != 0) {
				//rect.top MUST be 0 for the gradient to work properly
				fillPaint.setShader(Gradient.getGradient((state & (STATE_FOCUSED | STATE_CURRENT)) != 0, false, rect.bottom));
				canvas.drawRect(rect, fillPaint);
				fillPaint.setShader(null);
			} else if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(((state & STATE_CURRENT) != 0) ? color_current_multi : color_selected_multi);
				canvas.drawRect(rect, fillPaint);
			} else if ((state & STATE_CURRENT) != 0) {
				fillPaint.setColor(color_current_darker);
				canvas.drawRect(rect, fillPaint);
			}
		}
	}
	
	public static void drawBg(Canvas canvas, int state, Rect rect, boolean sideBorders) {
		if (state == 0)
			return;
		if ((state & STATE_PRESSED) != 0) {
			fillPaint.setColor(((state & (STATE_FOCUSED | STATE_CURRENT)) != 0) ? color_current_pressed : color_selected_pressed);
			canvas.drawRect(rect, fillPaint);
			strokePaint.setColor(((state & (STATE_FOCUSED | STATE_CURRENT)) != 0) ? color_current_pressed_border : color_selected_pressed_border);
			if (!sideBorders) {
				rect.left -= (_1dp << 1);
				rect.right += (_1dp << 1);
			}
			canvas.drawRect((float)rect.left + _1dpInset, (float)rect.top + _1dpInset, (float)rect.right - _1dpInset, (float)rect.bottom - _1dpInset, strokePaint);
			if (!sideBorders) {
				rect.left += (_1dp << 1);
				rect.right -= (_1dp << 1);
			}
		} else {
			if ((state & (STATE_SELECTED | STATE_FOCUSED)) != 0) {
				//rect.top MUST be 0 for the gradient to work properly
				fillPaint.setShader(Gradient.getGradient((state & (STATE_FOCUSED | STATE_CURRENT)) != 0, false, rect.bottom));
				canvas.drawRect(rect, fillPaint);
				fillPaint.setShader(null);
				strokePaint.setColor(0xffffffff);
				final float t = (float)(rect.top + _1dp) + _1dpInset;
				if (!sideBorders)
					canvas.drawLine(rect.left, t, rect.right, t, strokePaint);
				else
					canvas.drawLine((float)(rect.left + _1dp), t, (float)(rect.right - _1dp), t, strokePaint);
				strokePaint.setColor(((state & (STATE_FOCUSED | STATE_CURRENT)) != 0) ? color_current_border : color_selected_border);
				if (!sideBorders) {
					rect.left -= (_1dp << 1);
					rect.right += (_1dp << 1);
				}
				canvas.drawRect((float)rect.left + _1dpInset, (float)rect.top + _1dpInset, (float)rect.right - _1dpInset, (float)rect.bottom - _1dpInset, strokePaint);
				if (!sideBorders) {
					rect.left += (_1dp << 1);
					rect.right -= (_1dp << 1);
				}
			} else if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(((state & STATE_CURRENT) != 0) ? color_current_multi : color_selected_multi);
				canvas.drawRect(rect, fillPaint);
			} else if ((state & STATE_CURRENT) != 0) {
				fillPaint.setColor(color_current_darker);
				canvas.drawRect(rect, fillPaint);
			}
		}
	}
	
	public static int handleStateChanges(int state, boolean pressed, boolean focused, View view) {
		boolean r = false;
		final boolean op = ((state & UI.STATE_PRESSED) != 0), of = ((state & UI.STATE_FOCUSED) != 0);
		if (op != pressed) {
			if (pressed)
				state |= UI.STATE_PRESSED;
			else
				state &= ~UI.STATE_PRESSED;
			r = true;
		}
		if (of != focused) {
			if (focused)
				state |= UI.STATE_FOCUSED;
			else
				state &= ~UI.STATE_FOCUSED;
			r = true;
		}
		if (r)
			view.invalidate();
		return state;
	}
	
	public static void toast(Context context, Throwable ex) {
		String s = ex.getMessage();
		if (s != null && s.length() > 0)
			s = context.getText(R.string.error).toString() + " " + s;
		else
			s = context.getText(R.string.error).toString() + " " + ex.getClass().getName();
		toast(context, s);
	}
	
	public static void toast(Context context, int resId) {
		toast(context, context.getText(resId).toString());
	}
	
	@SuppressWarnings("deprecation")
	public static void toast(Context context, String text) {
		final Toast t = new Toast(context);
		final TextView v = new TextView(context);
		v.setTextAppearance(context, R.style.MediumText);
		v.setTextColor(UI.colorState_text_sel);
		v.setBackgroundDrawable(new BorderDrawable(color_current_border, color_current, true, true, true, true));
		v.setGravity(Gravity.CENTER);
		v.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		v.setPadding(_8dp, _8dp, _8dp, _8dp);
		v.setText(text);
		t.setView(v);
		t.setDuration(Toast.LENGTH_LONG);
		t.show();
	}
	
	public static void prepare(Menu menu) {
		final CustomContextMenu mnu = (CustomContextMenu)menu;
		try {
			mnu.setItemClassConstructor(BgButton.class.getConstructor(Context.class));
		} catch (NoSuchMethodException e) {
		}
		mnu.setBackground(new BorderDrawable(color_selected_border, color_bg_menu, true, true, true, true));
		mnu.setPadding(0);//_1dp + _2dp);
		mnu.setItemTextSizeInPixels(_22sp);
		mnu.setItemTextColor(colorState_text_sel);
		mnu.setItemGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
	}
	
	public static void separator(Menu menu, int groupId, int order) {
		((CustomContextMenu)menu).addSeparator(groupId, order, color_selected_border, _1dp, _8dp, _2dp, _8dp, _2dp);		
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void setNextFocusForwardId(View view, int nextFocusForwardId) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			view.setNextFocusForwardId(nextFocusForwardId);
	}
}
