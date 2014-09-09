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
package br.com.carlosrafaelgn.fplay.ui;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import br.com.carlosrafaelgn.fplay.ActivityBrowserView;
import br.com.carlosrafaelgn.fplay.ActivityItemView;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;

//
//Unit conversions are based on:
//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/util/TypedValue.java
//
public final class UI {
	//VERSION_CODE must be kept in sync with AndroidManifest.xml
	public static final int VERSION_CODE = 46;
	
	public static final int STATE_PRESSED = 1;
	public static final int STATE_FOCUSED = 2;
	public static final int STATE_CURRENT = 4;
	public static final int STATE_SELECTED = 8;
	public static final int STATE_MULTISELECTED = 16;
	public static final int STATE_CHECKED = 32;
	
	public static final int LOCALE_NONE = 0;
	public static final int LOCALE_US = 1;
	public static final int LOCALE_PTBR = 2;
	public static final int LOCALE_RU = 3;
	public static final int LOCALE_UK = 4;
	public static final int LOCALE_ES = 5;
	public static final int LOCALE_MAX = 5;
	
	public static final int THEME_CUSTOM = -1;
	public static final int THEME_BLUE_ORANGE = 0;
	public static final int THEME_BLUE = 1;
	public static final int THEME_ORANGE = 2;
	//present the new dark/light theme to all those using the light theme ;)
	public static final int THEME_LIGHT = 4;
	public static final int THEME_DARK_LIGHT = 3;
	
	public static final int MSG_ADD = 0x0001;
	public static final int MSG_PLAY = 0x0002;
	
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
	public static final String ICON_OPTCHK = "q";
	public static final String ICON_OPTUNCHK = "Q";
	public static final String ICON_GRIP = "G";
	public static final String ICON_FPLAY = "♫";
	public static final String ICON_SLIDERTOP = "\"";
	public static final String ICON_SLIDERBOTTOM = "\'";
	public static final String ICON_RIGHT = "6";
	public static final String ICON_FADE = ";";
	public static final String ICON_DIAL = ":";
	public static final String ICON_HEADSET = "{";
	public static final String ICON_TRANSPARENT = "}";
	public static final String ICON_PALETTE = "[";
	public static final String ICON_LANGUAGE = "]";
	public static final String ICON_THEME = "\\";
	public static final String ICON_SPACELIST = "=";
	public static final String ICON_SPACEHEADER = "~";
	public static final String ICON_ORIENTATION = "@";
	public static final String ICON_DYSLEXIA = "$";
	public static final String ICON_SCREEN = "`";
	public static final String ICON_CLOCK = ".";
	public static final String ICON_DIVIDER = ",";
	public static final String ICON_MIC = "m";
	public static final String ICON_ALBUMART = "B";
	public static final String ICON_ALBUMART_OFF = "b";
	public static final String ICON_BLUETOOTH = "W";
	public static final String ICON_RADIO = "w";
	public static final String ICON_HAND = "a";
	public static final String ICON_SCROLLBAR = "c";
	public static final String ICON_SD = "d";
	public static final String ICON_FOLDER = "f";
	
	public static final int IDX_COLOR_WINDOW = 0;
	public static final int IDX_COLOR_CONTROL_MODE = 1;
	public static final int IDX_COLOR_VISUALIZER = 2;
	public static final int IDX_COLOR_LIST = 3;
	public static final int IDX_COLOR_MENU = 4;
	public static final int IDX_COLOR_MENU_ICON = 5;
	public static final int IDX_COLOR_MENU_BORDER = 6;
	public static final int IDX_COLOR_DIVIDER = 7;
	public static final int IDX_COLOR_HIGHLIGHT = 8;
	public static final int IDX_COLOR_TEXT_HIGHLIGHT = 9;
	public static final int IDX_COLOR_TEXT = 10;
	public static final int IDX_COLOR_TEXT_DISABLED = 11;
	public static final int IDX_COLOR_TEXT_LISTITEM = 12;
	public static final int IDX_COLOR_TEXT_LISTITEM_SECONDARY = 13;
	public static final int IDX_COLOR_TEXT_SELECTED = 14;
	public static final int IDX_COLOR_TEXT_MENU = 15;
	public static final int IDX_COLOR_SELECTED_GRAD_LT = 16;
	public static final int IDX_COLOR_SELECTED_GRAD_DK = 17;
	public static final int IDX_COLOR_SELECTED_BORDER = 18;
	public static final int IDX_COLOR_SELECTED_PRESSED = 19;
	public static final int IDX_COLOR_FOCUSED_GRAD_LT = 20;
	public static final int IDX_COLOR_FOCUSED_GRAD_DK = 21;
	public static final int IDX_COLOR_FOCUSED_BORDER = 22;
	public static final int IDX_COLOR_FOCUSED_PRESSED = 23;
	
	public static final int color_transparent = 0x00000000;
	public static int color_window;
	public static int color_control_mode;
	public static int color_visualizer, color_visualizer565;
	public static int color_list;
	public static int color_menu;
	public static int color_menu_icon;
	public static int color_menu_border;
	public static int color_divider;
	public static int color_highlight;
	public static int color_text_highlight;
	public static int color_text;
	public static int color_text_disabled;
	public static int color_text_listitem;
	public static int color_text_listitem_secondary;
	public static int color_text_selected;
	public static int color_text_menu;
	public static int color_text_title;
	public static int color_selected;
	public static int color_selected_multi;
	public static int color_selected_grad_lt;
	public static int color_selected_grad_dk;
	public static int color_selected_border;
	public static int color_selected_pressed;
	public static int color_selected_pressed_border;
	public static int color_focused;
	public static int color_focused_grad_lt;
	public static int color_focused_grad_dk;
	public static int color_focused_border;
	public static int color_focused_pressed;
	public static int color_focused_pressed_border;
	public static final int color_glow_dk = 0xff686868;
	public static final int color_glow_lt = 0xffffffff;
	public static ColorStateList colorState_text_white_reactive;
	public static ColorStateList colorState_text_menu_reactive;
	public static ColorStateList colorState_text_reactive;
	public static ColorStateList colorState_text_static;
	public static ColorStateList colorState_text_listitem_static;
	public static ColorStateList colorState_text_listitem_reactive;
	public static ColorStateList colorState_text_listitem_secondary_static;
	public static ColorStateList colorState_text_selected_static;
	public static ColorStateList colorState_text_title_static;
	public static ColorStateList colorState_highlight_static;
	public static ColorStateList colorState_text_highlight_static;
	public static ColorStateList colorState_text_highlight_reactive;
	
	public static Typeface iconsTypeface, defaultTypeface;
	
	public static final class DisplayInfo {
		public int usableScreenWidth, usableScreenHeight, screenWidth, screenHeight;
		public boolean isLargeScreen, isLandscape, isLowDpiScreen;
		public DisplayMetrics displayMetrics;
		
		private void initializeScreenDimensions(Display display, DisplayMetrics outDisplayMetrics) {
			display.getMetrics(outDisplayMetrics);
			screenWidth = outDisplayMetrics.widthPixels;
			screenHeight = outDisplayMetrics.heightPixels;
			usableScreenWidth = screenWidth;
			usableScreenHeight = screenHeight;
		}
		
		@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		private void initializeScreenDimensions14(Display display, DisplayMetrics outDisplayMetrics) {
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
		private void initializeScreenDimensions17(Display display, DisplayMetrics outDisplayMetrics) {
			display.getMetrics(outDisplayMetrics);
			usableScreenWidth = outDisplayMetrics.widthPixels;
			usableScreenHeight = outDisplayMetrics.heightPixels;
			display.getRealMetrics(outDisplayMetrics);
			screenWidth = outDisplayMetrics.widthPixels;
			screenHeight = outDisplayMetrics.heightPixels;
		}
		
		public void getInfo(Context context) {
			final Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
			displayMetrics = new DisplayMetrics();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
				initializeScreenDimensions17(display, displayMetrics);
			else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				initializeScreenDimensions14(display, displayMetrics);
			else
				initializeScreenDimensions(display, displayMetrics);
			//improved detection for tablets, based on:
			//http://developer.android.com/guide/practices/screens_support.html#DeclaringTabletLayouts
			//(There is also the solution at http://stackoverflow.com/questions/11330363/how-to-detect-device-is-android-phone-or-android-tablet
			//but the former link says it is deprecated...)
			//*** I decided to treat screens >= 500dp as large screens because there are
			//lots of 7" phones/tablets with resolutions starting at around 533dp ***
			final int _500dp = (int)((500.0f * displayMetrics.density) + 0.5f);
			isLargeScreen = ((screenWidth >= _500dp) && (screenHeight >= _500dp));
			isLandscape = (screenWidth >= screenHeight);
			isLowDpiScreen = (displayMetrics.densityDpi < 160);
		}
	}
	
	private static final class Gradient {
		private static final Gradient[] gradients = new Gradient[16];
		private static int pos, count;
		public final boolean focused, vertical;
		public final int size;
		public final LinearGradient gradient;
		
		private Gradient(boolean focused, boolean vertical, int size) {
			this.focused = focused;
			this.vertical = vertical;
			this.size = size;
			this.gradient = (focused ? new LinearGradient(0, 0, (vertical ? size : 0), (vertical ? 0 : size), color_focused_grad_lt, color_focused_grad_dk, Shader.TileMode.CLAMP) :
				new LinearGradient(0, 0, (vertical ? size : 0), (vertical ? 0 : size), color_selected_grad_lt, color_selected_grad_dk, Shader.TileMode.CLAMP));
		}
		
		public static void purgeAll() {
			for (int i = gradients.length - 1; i >= 0; i--)
				gradients[i] = null;
			pos = 0;
			count = 0;
		}
		
		public static LinearGradient getGradient(boolean focused, boolean vertical, int size) {
			//a LRU algorithm could be implemented here...
			for (int i = count - 1; i >= 0; i--) {
				if (gradients[i].size == size && gradients[i].focused == focused && gradients[i].vertical == vertical)
					return gradients[i].gradient;
			}
			if (count < 16) {
				pos = count;
				count++;
			} else {
				pos = (pos + 1) & 15;
			}
			final Gradient g = new Gradient(focused, vertical, size);
			gradients[pos] = g;
			return g.gradient;
		}
	}
	
	public static final Rect rect = new Rect();
	public static char decimalSeparator;
	public static boolean isLandscape, isLargeScreen, isLowDpiScreen, isDividerVisible, isVerticalMarginLarge, keepScreenOn, displayVolumeInDB, doubleClickMode,
		marqueeTitle, blockBackKey, widgetTransparentBg, useControlModeButtonsInsideList, useVisualizerButtonsInsideList, backKeyAlwaysReturnsToPlayerWhenBrowsing, wrapAroundList, oldBrowserBehavior, extraSpacing, flat, albumArt, scrollBarToTheLeft;
	public static int _1dp, _2dp, _4dp, _8dp, _16dp, _2sp, _4sp, _8sp, _16sp, _22sp, _18sp, _14sp, _22spBox, _IconBox, _18spBox, _14spBox, _22spYinBox, _18spYinBox, _14spYinBox,
		strokeSize, thickDividerSize, defaultControlContentsSize, defaultControlSize, usableScreenWidth, usableScreenHeight, screenWidth, screenHeight, densityDpi, forcedOrientation, visualizerOrientation, msgs, msgStartup, widgetTextColor, widgetIconColor, lastVersionCode, browserScrollBarType, songListScrollBarType;
	public static Bitmap icPrev, icPlay, icPause, icNext, icPrevNotif, icPlayNotif, icPauseNotif, icNextNotif, icExitNotif;
	public static byte[] customColors;
	//I know this is not the "proper" way of doing this... But this is the best way
	//to save memory and prevent a few memory leaks (considering this class is only
	//going to be used in this kind of project)
	public static ActivityItemView songActivity;
	public static ActivityBrowserView browserActivity;
	
	private static String emptyListString;
	private static int emptyListStringHalfWidth, forcedLocale, currentLocale, theme, createdWidgetIconColor;
	private static boolean alternateTypefaceActive, useAlternateTypeface, fullyInitialized;
	private static Toast internalToast;
	
	public static float density, scaledDensity, xdpi_1_72;
	
	public static final Paint fillPaint;
	public static final TextPaint textPaint;
	private static PorterDuffColorFilter glowFilter;
	//private static final PorterDuffColorFilter edgeFilter;
	
	static {
		fillPaint = new Paint();
		fillPaint.setDither(false);
		fillPaint.setAntiAlias(false);
		fillPaint.setStyle(Paint.Style.FILL);
		textPaint = new TextPaint();
		textPaint.setDither(false);
		textPaint.setAntiAlias(true);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTypeface(Typeface.DEFAULT);
		textPaint.setTextAlign(Paint.Align.LEFT);
		textPaint.setColor(color_text);
		textPaint.measureText("FPlay");
		//hide the edge!!! ;)
		//edgeFilter = new PorterDuffColorFilter(0, PorterDuff.Mode.CLEAR);
	}
	
	public static String formatIntAsFloat(int number, boolean useTwoDecimalPlaces, boolean removeDecimalPlacesIfExact) {
		int dec;
		if (useTwoDecimalPlaces) {
			dec = number % 100;
			number /= 100;
		} else {
			dec = number % 10;
			number /= 10;
		}
		if (removeDecimalPlacesIfExact && dec == 0)
			return Integer.toString(number);
		if (dec < 0)
			dec = -dec;
		return Integer.toString(number) + decimalSeparator + ((useTwoDecimalPlaces && (dec < 10)) ? ("0" + Integer.toString(dec)) : Integer.toString(dec));
	}
	
	public static void formatIntAsFloat(StringBuilder sb, int number, boolean useTwoDecimalPlaces, boolean removeDecimalPlacesIfExact) {
		int dec;
		if (useTwoDecimalPlaces) {
			dec = number % 100;
			number /= 100;
		} else {
			dec = number % 10;
			number /= 10;
		}
		sb.append(number);
		if (!removeDecimalPlacesIfExact || dec != 0) {
			if (dec < 0)
				dec = -dec;
			sb.append(decimalSeparator);
			if (useTwoDecimalPlaces && (dec < 10))
				sb.append('0');
			sb.append(dec);
		}
	}
	
	public static boolean isUsingAlternateTypeface() {
		return useAlternateTypeface;
	}
	
	public static void setUsingAlternateTypeface(Context context, boolean useAlternateTypeface) {
		UI.useAlternateTypeface = useAlternateTypeface;
		if (useAlternateTypeface && !isCurrentLocaleCyrillic()) {
			if (defaultTypeface == null || !alternateTypefaceActive) {
				alternateTypefaceActive = true;
				try {
					defaultTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/OpenDyslexicRegular.otf");
				} catch (Throwable ex) {
					UI.useAlternateTypeface = false;
					alternateTypefaceActive = false;
					defaultTypeface = Typeface.DEFAULT;
				}
			}
		} else {
			alternateTypefaceActive = false;
			defaultTypeface = Typeface.DEFAULT;
		}
		textPaint.setTypeface(defaultTypeface);
		//Font Metrics in Java OR How, the hell, Should I Position This Font?!
		//http://blog.evendanan.net/2011/12/Font-Metrics-in-Java-OR-How-the-hell-Should-I-Position-This-Font
		textPaint.setTextSize(_22sp);
		final Paint.FontMetrics fm = textPaint.getFontMetrics();
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
		emptyListStringHalfWidth = measureText(emptyListString, _22sp) >> 1;
		_IconBox = Math.min(spToPxI(24), _22spBox); //both descent and ascent of iconsTypeface are 0!
	}
	
	public static Locale getLocaleFromCode(int localeCode) {
		final Locale l = Locale.getDefault();
		switch (localeCode) {
		case LOCALE_US:
			if (!"en".equals(l.getLanguage()))
				return Locale.US;
			break;
		case LOCALE_PTBR:
			if (!"pt".equals(l.getLanguage()))
				return new Locale("pt", "BR");
			break;
		case LOCALE_RU:
			if (!"ru".equals(l.getLanguage()))
				return new Locale("ru", "RU");
			break;
		case LOCALE_UK:
			if (!"uk".equals(l.getLanguage()))
				return new Locale("uk");
			break;
		case LOCALE_ES:
			if (!"es".equals(l.getLanguage()))
				return new Locale("es");
			break;
		}
		return l;
	}
	
	public static String getLocaleDescriptionFromCode(Context context, int localeCode) {
		switch (localeCode) {
		case LOCALE_US:
			return "English";
		case LOCALE_PTBR:
			return "Português (Brasil)";
		case LOCALE_RU:
			return "Русский";
		case LOCALE_UK:
			return "Українська";
		case LOCALE_ES:
			return "Español";
		}
		return context.getText(R.string.standard_language).toString();
	}
	
	public static int getCurrentLocale(Context context) {
		try {
			final String l = context.getResources().getConfiguration().locale.getLanguage().toLowerCase(Locale.US);
			if ("pt".equals(l))
				return LOCALE_PTBR;
			if ("ru".equals(l))
				return LOCALE_RU;
			if ("uk".equals(l))
				return LOCALE_UK;
			if ("es".equals(l))
				return LOCALE_ES;
		} catch (Throwable ex) {
		}
		return LOCALE_US;
	}
	
	public static boolean isCurrentLocaleCyrillic() {
		return ((currentLocale == LOCALE_RU) || (currentLocale == LOCALE_UK));
	}
	
	public static int getForcedLocale() {
		return forcedLocale;
	}
	
	private static void updateDecimalSeparator(Context context) {
		try {
			final DecimalFormatSymbols d = new DecimalFormatSymbols(getLocaleFromCode(currentLocale));
			decimalSeparator = d.getDecimalSeparator();
		} catch (Throwable ex) {
			decimalSeparator = '.';
		}
	}
	
	public static boolean setForcedLocale(Context context, int localeCode) {
		if (localeCode < 0 || localeCode > LOCALE_MAX)
			localeCode = LOCALE_NONE;
		final Resources res = context.getResources();
		if (forcedLocale == 0 && localeCode == 0) {
			currentLocale = getCurrentLocale(context);
			updateDecimalSeparator(context);
			return false;
		}
		try {
			final Locale l = getLocaleFromCode(localeCode);
			final Configuration cfg = new Configuration(res.getConfiguration());
			cfg.locale = l;
			res.updateConfiguration(cfg, res.getDisplayMetrics());
			forcedLocale = localeCode;
			currentLocale = ((localeCode == 0) ? getCurrentLocale(context) : localeCode);
		} catch (Throwable ex) {
			currentLocale = getCurrentLocale(context);
		}
		updateDecimalSeparator(context);
		if (fullyInitialized) {
			setUsingAlternateTypeface(context, useAlternateTypeface);
			return true;
		}
		return false;
	}
	
	public static void setUsingAlternateTypefaceAndForcedLocale(Context context, boolean useAlternateTypeface, int localeCode) {
		UI.useAlternateTypeface = useAlternateTypeface;
		if (!setForcedLocale(context, localeCode))
			setUsingAlternateTypeface(context, useAlternateTypeface);
	}
	
	public static void loadWidgetRelatedSettings(Context context) {
		if (fullyInitialized)
			return;
		final SerializableMap opts = Player.loadConfigFromFile(context);
		//I know, this is ugly... I'll fix it one day...
		setForcedLocale(context, opts.getInt(0x001E, LOCALE_NONE));
		widgetTransparentBg = opts.getBoolean(0x0022, false);
		widgetTextColor = opts.getInt(0x0023, 0xff000000);
		widgetIconColor = opts.getInt(0x0024, 0xff000000);
	}
	
	public static void initialize(Context context) {
		fullyInitialized = true;
		if (iconsTypeface == null)
			iconsTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/icons.ttf");
		final DisplayInfo info = new DisplayInfo();
		info.getInfo(context);
		density = info.displayMetrics.density;
		densityDpi = info.displayMetrics.densityDpi;
		scaledDensity = info.displayMetrics.scaledDensity;
		xdpi_1_72 = info.displayMetrics.xdpi * (1.0f / 72.0f);
		screenWidth = info.screenWidth;
		screenHeight = info.screenHeight;
		usableScreenWidth = info.usableScreenWidth;
		usableScreenHeight = info.usableScreenHeight;
		isLargeScreen = info.isLargeScreen;
		isLandscape = info.isLandscape;
		isLowDpiScreen = info.isLowDpiScreen;
		//apparently, the display metrics returned by Resources.getDisplayMetrics()
		//is not the same as the one returned by Display.getMetrics()/getRealMetrics()
		final float sd = context.getResources().getDisplayMetrics().scaledDensity;
		if (sd > 0)
			scaledDensity = sd;
		else if (scaledDensity <= 0)
			scaledDensity = 1.0f;
		
		_1dp = dpToPxI(1);
		strokeSize = (_1dp + 1) >> 1;
		thickDividerSize = dpToPxI(1.5f);// ((_1dp >= 2) ? _1dp : 2);
		if (thickDividerSize < 2) thickDividerSize = 2;
		if (thickDividerSize <= _1dp) thickDividerSize = _1dp + 1;
		_2dp = dpToPxI(2);
		_4dp = dpToPxI(4);
		_8dp = dpToPxI(8);
		_16dp = dpToPxI(16);
		_2sp = spToPxI(2);
		_4sp = spToPxI(4);
		_8sp = spToPxI(8);
		_16sp = spToPxI(16);
		_22sp = spToPxI(22);
		_18sp = spToPxI(18);
		_14sp = spToPxI(14);
		defaultControlContentsSize = dpToPxI(32);
		defaultControlSize = defaultControlContentsSize + (UI._8sp << 1);
		if (!setForcedLocale(context, forcedLocale))
			setUsingAlternateTypeface(context, useAlternateTypeface);
	}
	
	public static void prepareWidgetPlaybackIcons(Context context) {
		if (widgetIconColor != createdWidgetIconColor) {
			if (icPrev != null) {
				icPrev.recycle();
				icPrev = null;
			}
			if (icPlay != null) {
				icPlay.recycle();
				icPlay = null;
			}
			if (icPause != null) {
				icPause.recycle();
				icPause = null;
			}
			if (icNext != null) {
				icNext.recycle();
				icNext = null;
			}
		}
		if (icPrev != null)
			return;
		if (iconsTypeface == null)
			initialize(context);
		final Canvas c = new Canvas();
		textPaint.setTypeface(iconsTypeface);
		textPaint.setColor(widgetIconColor);
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
		createdWidgetIconColor = widgetIconColor;
		//reset to the original state
		textPaint.setTypeface(defaultTypeface);
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
		icExitNotif = Bitmap.createBitmap(defaultControlContentsSize, defaultControlContentsSize, Bitmap.Config.ARGB_8888);
		c.setBitmap(icExitNotif);
		c.drawText(ICON_EXIT, 0, defaultControlContentsSize, textPaint);
		//reset to the original state
		textPaint.setTypeface(defaultTypeface);
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
	
	public static CharSequence getThemeColorDescription(Context context, int idx) {
		switch (idx) {
		case 0:
			return context.getText(R.string.window_background);
		case 1:
			return context.getText(R.string.control_mode_background);
		case 2:
			return context.getText(R.string.visualizer_background);
		case 3:
		case 4:
			return context.getText(R.string.background);
		case 5:
			return context.getText(R.string.icon);
		case 6:
		case 18:
		case 22:
			return context.getText(R.string.border);
		case 7:
			return context.getText(R.string.divider);
		case 8:
			return context.getText(R.string.highlight_background);
		case 9:
			return context.getText(R.string.highlight_text);
		case 10:
			return context.getText(R.string.window_text);
		case 11:
			return context.getText(R.string.window_text_disabled);
		case 12:
		case 14:
		case 15:
			return context.getText(R.string.text);
		case 13:
			return context.getText(R.string.text_secondary);
		case 16:
		case 20:
			return context.getText(R.string.top_background);
		case 17:
		case 21:
			return context.getText(R.string.bottom_background);
		case 19:
		case 23:
			return context.getText(R.string.pressed_background);
		}
		return context.getText(R.string.no_info);
	}
	
	public static void serializeThemeColor(byte[] colors, int idx, int color) {
		colors[idx] = (byte)color;
		colors[idx + 1] = (byte)(color >>> 8);
		colors[idx + 2] = (byte)(color >>> 16);
	}
	
	public static int deserializeThemeColor(byte[] colors, int idx) {
		return 0xff000000 | (int)(colors[idx] & 0xff) | (int)((colors[idx + 1] & 0xff) << 8) | (int)((colors[idx + 2] & 0xff) << 16);
	}
	
	public static byte[] serializeThemeToArray() {
		final byte[] colors = new byte[72];
		serializeThemeColor(colors, 3 * IDX_COLOR_WINDOW, color_window);
		serializeThemeColor(colors, 3 * IDX_COLOR_CONTROL_MODE, color_control_mode);
		serializeThemeColor(colors, 3 * IDX_COLOR_VISUALIZER, color_visualizer);
		serializeThemeColor(colors, 3 * IDX_COLOR_LIST, color_list);
		serializeThemeColor(colors, 3 * IDX_COLOR_MENU, color_menu);
		serializeThemeColor(colors, 3 * IDX_COLOR_MENU_ICON, color_menu_icon);
		serializeThemeColor(colors, 3 * IDX_COLOR_MENU_BORDER, color_menu_border);
		serializeThemeColor(colors, 3 * IDX_COLOR_DIVIDER, color_divider);
		serializeThemeColor(colors, 3 * IDX_COLOR_HIGHLIGHT, color_highlight);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_HIGHLIGHT, color_text_highlight);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT, color_text);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_DISABLED, color_text_disabled);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_LISTITEM, color_text_listitem);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_LISTITEM_SECONDARY, color_text_listitem_secondary);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_SELECTED, color_text_selected);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_MENU, color_text_menu);
		serializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_GRAD_LT, color_selected_grad_lt);
		serializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_GRAD_DK, color_selected_grad_dk);
		serializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_BORDER, color_selected_border);
		serializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_PRESSED, color_selected_pressed);
		serializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_GRAD_LT, color_focused_grad_lt);
		serializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_GRAD_DK, color_focused_grad_dk);
		serializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_BORDER, color_focused_border);
		serializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_PRESSED, color_focused_pressed);
		return colors;
	}
	
	private static boolean deserializeThemeFromArray(byte[] colors) {
		if (colors == null || colors.length < 72)
			return false;
		color_window = deserializeThemeColor(colors, 3 * IDX_COLOR_WINDOW);
		color_control_mode = deserializeThemeColor(colors, 3 * IDX_COLOR_CONTROL_MODE);
		color_visualizer = deserializeThemeColor(colors, 3 * IDX_COLOR_VISUALIZER);
		color_list = deserializeThemeColor(colors, 3 * IDX_COLOR_LIST);
		color_menu = deserializeThemeColor(colors, 3 * IDX_COLOR_MENU);
		color_menu_icon = deserializeThemeColor(colors, 3 * IDX_COLOR_MENU_ICON);
		color_menu_border = deserializeThemeColor(colors, 3 * IDX_COLOR_MENU_BORDER);
		color_divider = deserializeThemeColor(colors, 3 * IDX_COLOR_DIVIDER);
		color_highlight = deserializeThemeColor(colors, 3 * IDX_COLOR_HIGHLIGHT);
		color_text_highlight = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_HIGHLIGHT);
		color_text = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT);
		color_text_disabled = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_DISABLED);
		color_text_listitem = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_LISTITEM);
		color_text_listitem_secondary = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_LISTITEM_SECONDARY);
		color_text_selected = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_SELECTED);
		color_text_menu = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_MENU);
		color_selected_grad_lt = deserializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_GRAD_LT);
		color_selected_grad_dk = deserializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_GRAD_DK);
		color_selected_border = deserializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_BORDER);
		color_selected_pressed = deserializeThemeColor(colors, 3 * IDX_COLOR_SELECTED_PRESSED);
		color_focused_grad_lt = deserializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_GRAD_LT);
		color_focused_grad_dk = deserializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_GRAD_DK);
		color_focused_border = deserializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_BORDER);
		color_focused_pressed = deserializeThemeColor(colors, 3 * IDX_COLOR_FOCUSED_PRESSED);
		if (customColors != colors) {
			if (customColors == null || customColors.length != 72)
				customColors = new byte[72];
			System.arraycopy(colors, 0, customColors, 0, 72);
		}
		return true;
	}
	
	private static void finishLoadingTheme(boolean custom) {
		//create a "safe 565" version for the visualizer background
		//http://stackoverflow.com/questions/2442576/how-does-one-convert-16-bit-rgb565-to-24-bit-rgb888
		//first convert to 565
		int r = ((color_visualizer >> 16) & 0xff) >> 3;
		int g = ((color_visualizer >> 8) & 0xff) >> 2;
		int b = (color_visualizer & 0xff) >> 3;
		//now upscale back to 888
		r = (r << 3) | (r >> 2);
		g = (g << 2) | (g >> 3);
		b = (b << 3) | (b >> 2);
		color_visualizer565 = 0xff000000 | (r << 16) | (g << 8) | b;
		
		color_selected = ColorUtils.blend(color_selected_grad_lt, color_selected_grad_dk, 0.5f);
		color_focused = ColorUtils.blend(color_focused_grad_lt, color_focused_grad_dk, 0.5f);
		color_selected_multi = ColorUtils.blend(color_selected, color_list, 0.7f);
		color_selected_pressed_border = color_selected_border;
		color_focused_pressed_border = color_focused_border;
		colorState_text_white_reactive = new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { color_text_selected, color_text_selected, 0xffffffff });
		colorState_text_menu_reactive = new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { color_text_selected, color_text_selected, color_text_menu });
		colorState_text_reactive = new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { color_text_selected, color_text_selected, color_text });
		colorState_text_static = ColorStateList.valueOf(color_text);
		colorState_text_listitem_static = ColorStateList.valueOf(color_text_listitem);
		colorState_text_listitem_reactive = new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { color_text_selected, color_text_selected, color_text_listitem });
		colorState_text_listitem_secondary_static = ColorStateList.valueOf(color_text_listitem_secondary);
		colorState_text_selected_static = ColorStateList.valueOf(color_text_selected);
		colorState_highlight_static = ColorStateList.valueOf(color_highlight);
		colorState_text_highlight_static = ColorStateList.valueOf(color_text_highlight);
		colorState_text_highlight_reactive = new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { color_text_selected, color_text_selected, color_text_highlight });
		if (!custom) {
			color_text_title = color_highlight;
			colorState_text_title_static = colorState_highlight_static;
			color_menu_border = color_selected_border;
			useControlModeButtonsInsideList = false;
			useVisualizerButtonsInsideList = false;
		}
		//choose the color with a nice contrast against the list background to be the glow color
		//the color is treated as SRC, and the bitmap is treated as DST
		glowFilter = new PorterDuffColorFilter((ColorUtils.contrastRatio(color_glow_dk, color_list) >= ColorUtils.contrastRatio(color_glow_lt, color_list)) ? color_glow_dk : color_glow_lt, PorterDuff.Mode.SRC_IN);
	}
	
	public static boolean loadCustomTheme() {
		if (!deserializeThemeFromArray(customColors)) {
			customColors = null;
			loadLightTheme();
			return false;
		}
		//check which color to use for the buttons in the main screen (when in control mode) and in the visualizer screen
		useControlModeButtonsInsideList = (ColorUtils.contrastRatio(color_control_mode, color_text_listitem) > ColorUtils.contrastRatio(color_control_mode, color_text));
		useVisualizerButtonsInsideList = (ColorUtils.contrastRatio(color_visualizer, color_text_listitem) > ColorUtils.contrastRatio(color_visualizer, color_text));
		finishLoadingTheme(true);
		//check which color to use for the title in the main screen
		final double crHighlight = ColorUtils.contrastRatio(color_window, color_highlight);
		final double crList = ColorUtils.contrastRatio(color_window, color_text_listitem_secondary);
		final double crText = ColorUtils.contrastRatio(color_window, color_text);
		if (crHighlight > 6.5 || (crHighlight >= crText && crHighlight >= crList)) {
			color_text_title = color_highlight;
			colorState_text_title_static = colorState_highlight_static;
		} else if (crList > 6.5 || (crList >= crText)) {
			color_text_title = color_text_listitem_secondary;
			colorState_text_title_static = colorState_text_listitem_secondary_static;
		} else {
			color_text_title = color_text;
			colorState_text_title_static = colorState_text_static;
		}
		return true;
	}
	
	public static void loadCommonColors(boolean invertSelectedAndFocus) {
		color_window = 0xff303030;
		color_control_mode = 0xff000000;
		color_visualizer = 0xff000000;
		color_list = 0xff080808;
		color_menu = 0xffffffff;
		color_menu_icon = 0xff555555;
		color_divider = 0xff3f3f3f; //0xff464646;
		color_highlight = 0xfffad35a;
		color_text_highlight = 0xff000000;
		color_text = 0xffffffff;
		color_text_disabled = 0xff8c8c8c; //959595;
		color_text_listitem = 0xffffffff;
		color_text_listitem_secondary = 0xfffad35a;
		color_text_selected = 0xff000000;
		color_text_menu = 0xff000000;
		if (invertSelectedAndFocus) {
			color_focused_grad_lt = 0xffd1e8ff;
			color_focused_grad_dk = 0xff5da2e3;
			color_focused_border = 0xff518ec2;
			color_focused_pressed = 0xffcfe1ff;
			color_selected_grad_lt = 0xfff7eb6a;
			color_selected_grad_dk = 0xfffeb645;
			color_selected_border = 0xffad9040;
			color_selected_pressed = 0xffffeed4;
		} else {
			color_selected_grad_lt = 0xffd1e8ff;
			color_selected_grad_dk = 0xff5da2e3;
			color_selected_border = 0xff518ec2;
			color_selected_pressed = 0xffcfe1ff;
			color_focused_grad_lt = 0xfff7eb6a;
			color_focused_grad_dk = 0xfffeb645;
			color_focused_border = 0xffad9040;
			color_focused_pressed = 0xffffeed4;
		}
	}
	
	public static void loadBlueOrangeTheme() {
		loadCommonColors(false);
		finishLoadingTheme(false);
	}
	
	public static void loadBlueTheme() {
		loadCommonColors(false);
		color_highlight = 0xff94c0ff;
		color_text_listitem_secondary = 0xff94c0ff;
		finishLoadingTheme(false);
	}
	
	public static void loadOrangeTheme() {
		loadCommonColors(true);
		finishLoadingTheme(false);
	}
	
	public static void loadLightTheme() {
		loadCommonColors(false);
		color_window = 0xffe0e0e0;
		color_control_mode = 0xffe0e0e0;
		color_visualizer = 0xffe0e0e0;
		color_list = 0xfff2f2f2;
		color_divider = 0xffb8b8b8; //0xffa0a0a0;
		color_highlight = 0xff0000f1;
		color_text_highlight = 0xffffffff;
		color_text = 0xff000000;
		color_text_listitem_secondary = 0xff0000f1;
		color_text_listitem = 0xff000000;
		finishLoadingTheme(false);
	}
	
	public static void loadDarkLightTheme() {
		loadCommonColors(false);
		color_list = 0xfff2f2f2;
		color_divider = 0xffb8b8b8; //0xffa0a0a0;
		color_text_listitem_secondary = 0xff0000f1;
		color_text_listitem = 0xff000000;
		finishLoadingTheme(false);
	}
	
	public static String getThemeString(Context context, int theme) {
		switch (theme) {
		case THEME_CUSTOM:
			return context.getText(R.string.custom).toString();
		case THEME_BLUE_ORANGE:
			return context.getText(R.string.blue_orange).toString();
		case THEME_BLUE:
			return context.getText(R.string.blue).toString();
		case THEME_ORANGE:
			return context.getText(R.string.orange).toString();
		case THEME_LIGHT:
			return context.getText(R.string.light).toString();
		default:
			return context.getText(R.string.dark_light).toString();
		}
	}
	
	public static int getTheme() {
		return theme;
	}
	
	public static void setTheme(Activity activity, int theme) {
		UI.theme = theme;
		Gradient.purgeAll();
		switch (theme) {
		case THEME_CUSTOM:
			loadCustomTheme();
			break;
		case THEME_BLUE_ORANGE:
			loadBlueOrangeTheme();
			break;
		case THEME_BLUE:
			loadBlueTheme();
			break;
		case THEME_ORANGE:
			loadOrangeTheme();
			break;
		case THEME_LIGHT:
			loadLightTheme();
			break;
		default:
			UI.theme = THEME_DARK_LIGHT;
			loadDarkLightTheme();
			break;
		}
		if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2)
			setAndroidThemeAccordingly13(activity);
	}
	
	public static boolean isAndroidThemeLight() {
		return ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) && (ColorUtils.relativeLuminance(color_list) >= 0.5));
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	public static void setAndroidThemeAccordingly13(Activity activity) {
		//Theme.DeviceDefault.Light.NoActionBar.Fullscreen appeared
		//only on API 14... :(
		//http://android-developers.blogspot.com.br/2012/01/holo-everywhere.html
		if (isAndroidThemeLight())
			activity.setTheme(android.R.style.Theme_Holo_Light_NoActionBar_Fullscreen);
		else
			activity.setTheme(android.R.style.Theme_Holo_NoActionBar_Fullscreen);
	}
	
	public static void setFlat(boolean flat) {
		UI.flat = flat;
		Gradient.purgeAll();
	}
	
	public static boolean isFlat() {
		return flat;
	}
	
	public static void showNextStartupMsg(final Activity activity) {
		if (msgStartup >= 10) {
			msgStartup = 10;
			return;
		}
		int title = R.string.new_setting;
		String content = "";
		if (msgStartup < 10) {
			msgStartup = 10;
			content = activity.getText(R.string.there_are_new_features).toString() + "\n- " + activity.getText(R.string.scrollbar).toString() + "\n\n" + activity.getText(R.string.check_it_out).toString();
			//content = activity.getText(R.string.startup_message).toString();
		}
		UI.prepareDialogAndShow((new AlertDialog.Builder(activity))
		.setTitle(activity.getText(title))
		.setMessage(content)
		.setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				showNextStartupMsg(activity);
			}
		})
		.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				showNextStartupMsg(activity);
			}
		})
		.create());
	}
	
	public static boolean showMsg(Activity activity, int msg) {
		if ((msgs & msg) != 0)
			return false;
		int title, content;
		switch (msg) {
		case MSG_ADD:
			title = R.string.add;
			content = R.string.msg_add;
			break;
		case MSG_PLAY:
			title = R.string.play;
			content = R.string.msg_play;
			break;
		default:
			return false;
		}
		UI.prepareDialogAndShow((new AlertDialog.Builder(activity))
		.setTitle(activity.getText(title))
		.setMessage(activity.getText(content))
		.setPositiveButton(R.string.got_it, null)
		.create());
		msgs |= msg;
		return true;
	}
	
	public static String ellipsizeText(String text, int size, int width, boolean truncateAtMiddle) {
		if (text == null)
			return "";
		if (width <= 1)
			return text;
		textPaint.setTextSize(size);
		return TextUtils.ellipsize(text, textPaint, width, truncateAtMiddle ? TruncateAt.MIDDLE : TruncateAt.END).toString();
	}
	
	public static int measureText(String text, int size) {
		if (text == null)
			return 0;
		textPaint.setTextSize(size);
		return (int)(textPaint.measureText(text) + 0.5f);
	}
	
	public static void drawText(Canvas canvas, String text, int color, int size, int x, int y) {
		textPaint.setColor(color);
		textPaint.setTextSize(size);
		canvas.drawText(text, x, y, textPaint);
	}
	
	public static void drawEmptyListString(Canvas canvas) {
		textPaint.setColor(color_text_disabled);
		textPaint.setTextSize(_22sp);
		canvas.drawText(emptyListString, (UI.rect.right >> 1) - emptyListStringHalfWidth, (UI.rect.bottom >> 1) - (_18spBox >> 1) + _18spYinBox, textPaint);
	}
	
	public static void fillRect(Canvas canvas, int fillColor) {
		fillPaint.setColor(fillColor);
		canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
	}
	
	public static void fillRect(Canvas canvas, int fillColor, int insetX, int insetY) {
		fillPaint.setColor(fillColor);
		canvas.drawRect(rect.left + insetX, rect.top + insetY, rect.right - insetX, rect.bottom - insetY, fillPaint);
	}
	
	public static void fillRect(Canvas canvas, Shader shader) {
		fillPaint.setShader(shader);
		canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
		fillPaint.setShader(null);
	}
	
	public static void fillRect(Canvas canvas, Shader shader, int insetX, int insetY) {
		fillPaint.setShader(shader);
		canvas.drawRect(rect.left + insetX, rect.top + insetY, rect.right - insetX, rect.bottom - insetY, fillPaint);
		fillPaint.setShader(null);
	}
	
	public static void strokeRect(Canvas canvas, int strokeColor, int thickness) {
		fillPaint.setColor(strokeColor);
		final int l = rect.left, t = rect.top, r = rect.right, b = rect.bottom;
		canvas.drawRect(l, t, r, t + thickness, fillPaint);
		canvas.drawRect(l, b - thickness, r, b, fillPaint);
		canvas.drawRect(l, t + thickness, l + thickness, b - thickness, fillPaint);
		canvas.drawRect(r - thickness, t + thickness, r, b - thickness, fillPaint);
	}
	
	public static int getBorderColor(int state) {
		if ((state & STATE_PRESSED) != 0)
			return (((state & STATE_FOCUSED) != 0) ? color_focused_pressed_border : color_selected_pressed_border);
		if ((state & STATE_FOCUSED) != 0)
			return color_focused_border;
		if ((state & STATE_SELECTED) != 0)
			return color_selected_border;
		return 0;
	}
	
	public static void drawBgBorderless(Canvas canvas, int state, boolean dividerAllowed) {
		dividerAllowed &= isDividerVisible;
		if (dividerAllowed)
			rect.bottom -= strokeSize;
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillRect(canvas, ((state & STATE_FOCUSED) != 0) ? color_focused_pressed : color_selected_pressed);
			} else if ((state & (STATE_SELECTED | STATE_FOCUSED)) != 0) {
				if (flat)
					fillRect(canvas, ((state & STATE_FOCUSED) != 0) ? color_focused : color_selected);
				else
					fillRect(canvas, Gradient.getGradient((state & STATE_FOCUSED) != 0, false, rect.bottom));
			} else if ((state & STATE_MULTISELECTED) != 0) {
				fillRect(canvas, color_selected_multi);
			}
		}
		if (dividerAllowed) {
			fillPaint.setColor(color_divider);
			final int top = rect.top;
			//rect.left += _8dp;
			//rect.right -= _8dp;
			rect.top = rect.bottom;
			rect.bottom += strokeSize;
			canvas.drawRect(rect, fillPaint);
			//rect.left -= _8dp;
			//rect.right += _8dp;
			rect.top = top;
		}
	}
	
	public static void drawBg(Canvas canvas, int state) {
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				strokeRect(canvas, ((state & STATE_FOCUSED) != 0) ? color_focused_pressed_border : color_selected_pressed_border, strokeSize);
				fillRect(canvas, ((state & STATE_FOCUSED) != 0) ? color_focused_pressed : color_selected_pressed, strokeSize, strokeSize);
			} else if ((state & (STATE_SELECTED | STATE_FOCUSED)) != 0) {
				strokeRect(canvas, ((state & STATE_FOCUSED) != 0) ? color_focused_border : color_selected_border, strokeSize);
				if (flat)
					fillRect(canvas, ((state & STATE_FOCUSED) != 0) ? color_focused : color_selected, strokeSize, strokeSize);
				else
					fillRect(canvas, Gradient.getGradient((state & STATE_FOCUSED) != 0, false, rect.bottom), strokeSize, strokeSize);
			} else if ((state & STATE_MULTISELECTED) != 0) {
				fillRect(canvas, color_selected_multi);
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
	
	public static void smallText(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _14sp);
		view.setTypeface(defaultTypeface);
	}
	
	public static void smallTextAndColor(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _14sp);
		view.setTextColor(colorState_text_static);
		view.setTypeface(defaultTypeface);
	}
	
	public static void mediumText(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _18sp);
		view.setTypeface(defaultTypeface);
	}
	
	public static void mediumTextAndColor(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _18sp);
		view.setTextColor(colorState_text_static);
		view.setTypeface(defaultTypeface);
	}
	
	public static void largeText(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _22sp);
		view.setTypeface(defaultTypeface);
	}
	
	public static void largeTextAndColor(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _22sp);
		view.setTextColor(colorState_text_static);
		view.setTypeface(defaultTypeface);
	}
	
	public static int getViewPaddingForLargeScreen() {
		return ((usableScreenWidth < usableScreenHeight) ? usableScreenWidth : usableScreenHeight) / (isLandscape ? 5 : 10);
	}
	
	public static void prepareViewPaddingForLargeScreen(View view, int topPadding, int bottomPadding) {
		final int p = getViewPaddingForLargeScreen();
		view.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
		view.setPadding(p, topPadding, p, bottomPadding);
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
	public static void prepareNotificationViewColors(TextView view) {
		view.setTextColor(UI.colorState_text_highlight_static);
		view.setBackgroundDrawable(new BorderDrawable(ColorUtils.blend(color_highlight, 0, 0.5f), color_highlight, strokeSize, strokeSize, strokeSize, strokeSize));
	}
	
	public static void toast(Context context, String text) {
		if (internalToast == null) {
			final Toast t = new Toast(context);
			final TextView v = new TextView(context);
			mediumText(v);
			prepareNotificationViewColors(v);
			v.setGravity(Gravity.CENTER);
			v.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			v.setPadding(_8dp, _8dp, _8dp, _8dp);
			t.setView(v);
			t.setDuration(Toast.LENGTH_LONG);
			internalToast = t;
		}
		((TextView)internalToast.getView()).setText(text);
		internalToast.show();
	}
	
	public static void prepare(Menu menu) {
		final CustomContextMenu mnu = (CustomContextMenu)menu;
		try {
			mnu.setItemClassConstructor(BgButton.class.getConstructor(Context.class));
		} catch (NoSuchMethodException e) {
		}
		mnu.setBackground(new BorderDrawable(color_menu_border, color_menu, strokeSize, strokeSize, strokeSize, strokeSize));
		mnu.setPadding(0);
		mnu.setItemTextSizeInPixels(_22sp);
		mnu.setItemTextColor(colorState_text_menu_reactive);
		mnu.setItemGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
	}
	
	public static void separator(Menu menu, int groupId, int order) {
		((CustomContextMenu)menu).addSeparator(groupId, order, color_menu_border, strokeSize, _8dp, _2dp, _8dp, _2dp);		
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void setNextFocusForwardId(View view, int nextFocusForwardId) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			view.setNextFocusForwardId(nextFocusForwardId);
	}
	
	public static void prepareDialogAndShow(final AlertDialog dialog) {
		if (alternateTypefaceActive) {
			//https://code.google.com/p/android/issues/detail?id=6360
			dialog.setOnShowListener(new DialogInterface.OnShowListener() {
				private void scanChildren(ViewGroup parent) {
					for (int i = parent.getChildCount(); i >= 0; i--) {
						final View v = parent.getChildAt(i);
						if (v instanceof ViewGroup)
							scanChildren((ViewGroup)v);
						else if (v instanceof TextView)
							((TextView)v).setTypeface(defaultTypeface);
					}
				}
				
				@Override
				public void onShow(DialogInterface dlg) {
					View v = dialog.findViewById(android.R.id.content);
					if (v != null && (v instanceof ViewGroup)) {
						scanChildren((ViewGroup)v);
					} else {
						//at least try to change the buttons...
						Button btn;
						if ((btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)) != null)
							btn.setTypeface(defaultTypeface);
						if ((btn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)) != null)
							btn.setTypeface(defaultTypeface);
						if ((btn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)) != null)
							btn.setTypeface(defaultTypeface);
					}
				}
			});
		}
		dialog.show();
	}
	
	public static void prepareEdgeEffectColor(Context context) {
		//
		//:D amazing hack/workaround, as explained here:
		//
		//http://evendanan.net/android/branding/2013/12/09/branding-edge-effect/
		Drawable glow, edge;
		try {
			glow = context.getResources().getDrawable(context.getResources().getIdentifier("overscroll_glow", "drawable", "android"));
			if (glow != null)
				//the color is treated as SRC, and the bitmap is treated as DST
				glow.setColorFilter(glowFilter);
		} catch (Throwable ex) {
		}
		try {
			edge = context.getResources().getDrawable(context.getResources().getIdentifier("overscroll_edge", "drawable", "android"));
			if (edge != null)
				//hide the edge!!! ;)
				edge.setColorFilter(glowFilter);//edgeFilter);
		} catch (Throwable ex) {
		}
	}
	
	@SuppressWarnings("deprecation")
	public static void prepareControlContainer(View view, boolean topBorder, boolean bottomBorder) {
		final int t = (topBorder ? thickDividerSize : 0);
		final int b = (bottomBorder ? thickDividerSize : 0);
		view.setBackgroundDrawable(new BorderDrawable(color_highlight, color_window, 0, t, 0, b, true));
		if (extraSpacing)
			view.setPadding(_8dp, _8dp + t, _8dp, _8dp + b);
		else
			view.setPadding(0, t, 0, b);
	}
	
	public static void prepareControlContainerPaddingOnly(View view, boolean topBorder, boolean bottomBorder) {
		final int t = (topBorder ? thickDividerSize : 0);
		final int b = (bottomBorder ? thickDividerSize : 0);
		if (extraSpacing)
			view.setPadding(_8dp, _8dp + t, _8dp, _8dp + b);
		else
			view.setPadding(0, t, 0, b);
	}
	
	@SuppressWarnings("deprecation")
	public static void prepareControlContainerWithoutRightPadding(View view, boolean topBorder, boolean bottomBorder) {
		final int t = (topBorder ? thickDividerSize : 0);
		final int b = (bottomBorder ? thickDividerSize : 0);
		view.setBackgroundDrawable(new BorderDrawable(color_highlight, color_window, 0, t, 0, b, true));
		if (extraSpacing)
			view.setPadding(_8dp, _8dp + t, 0, _8dp + b);
		else
			view.setPadding(0, t, 0, b);
	}
}
