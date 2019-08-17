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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.BgEdgeEffect;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.BuildConfig;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.list.FileFetcher;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.drawable.BgShadowDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ScrollBarThumbDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;

//
//Unit conversions are based on:
//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/util/TypedValue.java
//
@SuppressWarnings({"unused", "WeakerAccess"})
public final class UI implements Animation.AnimationListener, Interpolator {
	//VERSION_CODE must be kept in sync with build.gradle
	public static final int VERSION_CODE = 121;
	//VERSION_NAME must be kept in sync with build.gradle
	public static final String VERSION_NAME = "v1.87";

	public static final int STATE_PRESSED = 1;
	public static final int STATE_FOCUSED = 2;
	public static final int STATE_CURRENT = 4;
	public static final int STATE_SELECTED = 8;
	public static final int STATE_MULTISELECTED = 16;
	public static final int STATE_HOVERED = 32;
	public static final int STATE_SELECTED_OR_HOVERED = STATE_SELECTED | STATE_HOVERED;

	public static final int LOCALE_NONE = 0;
	public static final int LOCALE_US = 1;
	public static final int LOCALE_PTBR = 2;
	public static final int LOCALE_RU = 3;
	public static final int LOCALE_UK = 4;
	public static final int LOCALE_ES = 5;
	public static final int LOCALE_DE = 6;
	public static final int LOCALE_FR = 7;
	public static final int LOCALE_ZH = 8;
	public static final int LOCALE_MAX = 8;
	
	public static final int THEME_CUSTOM = -1;
	public static final int THEME_BLUE_ORANGE = 0;
	public static final int THEME_BLUE = 1;
	public static final int THEME_ORANGE = 2;
	public static final int THEME_LIGHT = 4;
	public static final int THEME_DARK_LIGHT = 5;
	public static final int THEME_CREAMY = 6;
	public static final int THEME_FPLAY_2016 = 7;
	//present the new FPlay theme to all those using FPlay Old/Dark theme ;)
	public static final int THEME_FPLAY = 3;
	public static final int THEME_FPLAY_ICY = 8;
	public static final int THEME_FPLAY_DARK = 9;

	public static final int TRANSITION_NONE = 0;
	public static final int TRANSITION_FADE = 1;
	public static final int TRANSITION_ZOOM = 2;
	public static final int TRANSITION_DISSOLVE = 3;
	public static final int TRANSITION_SLIDE = 4;
	public static final int TRANSITION_SLIDE_SMOOTH = 5;
	public static final int TRANSITION_ZOOM_FADE = 6;
	public static final int TRANSITION_DURATION_FOR_ACTIVITIES_SLOW = 300;
	public static final int TRANSITION_DURATION_FOR_ACTIVITIES = 300; //200; //used to be 300
	public static final int TRANSITION_DURATION_FOR_VIEWS = 300; //200; //used to be 300

	public static final int MSG_ADD = 0x0001;
	public static final int MSG_PLAY = 0x0002;
	
	public static final String ICON_PREV = "<";
	public static final String ICON_PLAY = "P";
	public static final String ICON_PAUSE = "|";
	public static final String ICON_NEXT = ">";
	public static final String ICON_MENU_MORE = "N";
	public static final String ICON_LIST24 = "l";
	public static final String ICON_MOVE = "M";
	public static final String ICON_REMOVE24 = "R";
	public static final String ICON_UP = "^";
	public static final String ICON_GOBACK = "_";
	public static final String ICON_SAVE24 = "S";
	public static final String ICON_LOAD24 = "L";
	public static final String ICON_FAVORITE_ON = "#";
	public static final String ICON_FAVORITE_OFF = "*";
	public static final String ICON_ADD = "A";
	public static final String ICON_HOME = "H";
	public static final String ICON_LINK = "U";
	public static final String ICON_EQUALIZER = "E";
	public static final String ICON_SETTINGS = "s";
	public static final String ICON_VISUALIZER24 = "V";
	public static final String ICON_EXIT = "X";
	public static final String ICON_VOLUME0 = "0";
	public static final String ICON_VOLUME1 = "1";
	public static final String ICON_VOLUME2 = "2";
	public static final String ICON_VOLUME3 = "3";
	public static final String ICON_VOLUME4 = "4";
	public static final String ICON_VOLUMEHEADSET0 = "\ue904";
	public static final String ICON_VOLUMEHEADSET1 = "\ue900";
	public static final String ICON_VOLUMEHEADSET2 = "\ue901";
	public static final String ICON_VOLUMEHEADSET3 = "\ue902";
	public static final String ICON_VOLUMEHEADSET4 = "\ue903";
	public static final String ICON_VOLUMEBLUETOOTH0 = "\ue90b";
	public static final String ICON_VOLUMEBLUETOOTH1 = "\ue907";
	public static final String ICON_VOLUMEBLUETOOTH2 = "\ue908";
	public static final String ICON_VOLUMEBLUETOOTH3 = "\ue909";
	public static final String ICON_VOLUMEBLUETOOTH4 = "\ue90a";
	public static final String ICON_DECREASE_VOLUME = "-";
	public static final String ICON_INCREASE_VOLUME = "+";
	public static final String ICON_SEARCH = "F";
	public static final String ICON_INFORMATION = "I";
	public static final String ICON_QUESTION = "?";
	public static final String ICON_EXCLAMATION = "!";
	public static final String ICON_SHUFFLE24 = "h";
	public static final String ICON_REPEAT24 = "t";
	public static final String ICON_DELETE = "D";
	public static final String ICON_RADIOCHK24 = "x";
	public static final String ICON_RADIOUNCHK24 = "o";
	public static final String ICON_OPTCHK24 = "q";
	public static final String ICON_OPTUNCHK24 = "Q";
	public static final String ICON_GRIP = "G";
	public static final String ICON_FPLAY = "7";
	public static final String ICON_SLIDERTOP = "\"";
	public static final String ICON_SLIDERBOTTOM = "\'";
	public static final String ICON_RIGHT = "6";
	public static final String ICON_FADE = ";";
	public static final String ICON_DIAL = ":";
	public static final String ICON_HEADSET = "{";
	public static final String ICON_TRANSPARENT = "}";
	public static final String ICON_FLAT = "/";
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
	public static final String ICON_USB = "u";
	public static final String ICON_SEEKBAR = "5";
	public static final String ICON_TRANSITION = "%";
	public static final String ICON_REPEATONE24 = "y";
	public static final String ICON_ROOT = "(";
	public static final String ICON_3DPAN = ")";
	public static final String ICON_REPEATNONE = "Y";
	public static final String ICON_HEADSETHOOK1 = "i";
	public static final String ICON_HEADSETHOOK2 = "j";
	public static final String ICON_HEADSETHOOK3 = "k";
	public static final String ICON_VOLUMEEARPHONE0 = "\ue906";
	public static final String ICON_VOLUMEEARPHONE1 = "\ue905";
	public static final String ICON_VOLUMEEARPHONE2 = ICON_HEADSETHOOK1;
	public static final String ICON_VOLUMEEARPHONE3 = ICON_HEADSETHOOK2;
	public static final String ICON_VOLUMEEARPHONE4 = ICON_HEADSETHOOK3;
	public static final String ICON_ICECAST = "K";
	public static final String ICON_ICECASTTEXT = "O"; //height = 3.587 / 16
	public static final String ICON_SHOUTCAST = "J";
	public static final String ICON_SHOUTCASTTEXT = "T"; //height = 2.279 / 16
	public static final String ICON_PERCENTAGE = "8";
	public static final String ICON_CLIP = "C";
	public static final String ICON_SPINNERARROW = "e";
	public static final String ICON_LARGETEXTIS22SP = "n";
	public static final String ICON_FULLSCREEN = "p";
	public static final String ICON_RINGTONE = "9";
	public static final String ICON_CONFIG24 = "g";
	public static final String ICON_MENU = "z";
	public static final String ICON_EQUALIZER24 = "Z";
	public static final String ICON_CREATE = "&";
	public static final String ICON_MOVE24 = "r";
	public static final String ICON_FPLAY24 = "¡";
	public static final String ICON_REPEATNONE24 = "²";
	public static final String ICON_EXIT24 = "³";
	public static final String ICON_NUMBER = "¤";
	public static final String ICON_OK = "±";
	public static final String ICON_SHARE = "°";
	public static final String ICON_SHARE24 = "€";

	public static final int KEY_UP = KeyEvent.KEYCODE_DPAD_UP;
	public static final int KEY_DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
	public static final int KEY_LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
	public static final int KEY_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;
	public static final int KEY_ENTER = KeyEvent.KEYCODE_DPAD_CENTER;
	public static final int KEY_DEL = 112; //KeyEvent.KEYCODE_FORWARD_DEL;
	public static final int KEY_EXTRA = KeyEvent.KEYCODE_SPACE;
	public static final int KEY_HOME = 122; //KeyEvent.KEYCODE_MOVE_HOME;
	public static final int KEY_END = 123; //KeyEvent.KEYCODE_MOVE_END;
	public static final int KEY_PAGE_UP = KeyEvent.KEYCODE_PAGE_UP;
	public static final int KEY_PAGE_DOWN = KeyEvent.KEYCODE_PAGE_DOWN;

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
	public static final int IDX_COLOR_TEXT_LISTITEM_DISABLED = 11;
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

	public static final int PLACEMENT_WINDOW = 0;
	public static final int PLACEMENT_MENU = 1;
	public static final int PLACEMENT_ALERT = 2;

	public static int color_window;
	public static int color_control_mode;
	public static int color_visualizer, color_visualizer565;
	public static int color_list;
	public static int color_list_bg;
	public static int color_list_original;
	public static int color_list_shadow;
	public static int color_menu;
	public static int color_menu_icon;
	public static int color_menu_border;
	public static int color_divider;
	public static int color_divider_pressed;
	public static int color_highlight;
	public static int color_text_highlight;
	public static int color_text;
	public static int color_text_listitem_disabled;
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
	public static int color_glow;
	public static int color_dialog_detail;
	public static int color_dialog_detail_highlight;
	public static BgColorStateList colorState_text_white_reactive;
	public static BgColorStateList colorState_text_menu_reactive;
	public static BgColorStateList colorState_text_reactive;
	public static BgColorStateList colorState_text_static;
	public static BgColorStateList colorState_text_listitem_static;
	public static BgColorStateList colorState_text_listitem_reactive;
	public static BgColorStateList colorState_text_listitem_secondary_static;
	public static BgColorStateList colorState_text_selected_static;
	public static BgColorStateList colorState_text_title_static;
	public static BgColorStateList colorState_highlight_static;
	public static BgColorStateList colorState_text_highlight_static;
	public static BgColorStateList colorState_text_highlight_reactive;
	public static BgColorStateList colorState_text_control_mode_reactive;
	public static BgColorStateList colorState_text_visualizer_static;
	public static BgColorStateList colorState_text_visualizer_reactive;

	public static Typeface iconsTypeface, defaultTypeface;

	public static final class DisplayInfo {
		public int usableScreenWidth, usableScreenHeight, screenWidth, screenHeight;
		public boolean isLandscape, isLargeScreen, isLowDpiScreen;
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

		public void getInfo(Activity activityContext, int newUsableScreenWidth, int newUsableScreenHeight) {
			//calling getMetrics() from a display obtained through an activity handles multiwindow correctly
			//https://developer.android.com/reference/android/view/Display.html#getMetrics(android.util.DisplayMetrics)
			final Display display = ((activityContext != null) ? activityContext.getWindowManager() :
				((WindowManager)Player.theApplication.getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay();
			displayMetrics = new DisplayMetrics();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
				initializeScreenDimensions17(display, displayMetrics);
			else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				initializeScreenDimensions14(display, displayMetrics);
			else
				initializeScreenDimensions(display, displayMetrics);

			if (newUsableScreenWidth > 0 && newUsableScreenHeight > 0) {
				usableScreenWidth = newUsableScreenWidth;
				usableScreenHeight = newUsableScreenHeight;
			}

			//improved detection for tablets, based on:
			//http://developer.android.com/guide/practices/screens_support.html#DeclaringTabletLayouts
			//(There is also the solution at http://stackoverflow.com/questions/11330363/how-to-detect-device-is-android-phone-or-android-tablet
			//but the former link says it is deprecated...)
			//*** I decided to treat screens >= 500dp as large screens because there are
			//lots of 7" phones/tablets with resolutions starting at around 533dp ***
			final int _550dp = (int)((550.0f * displayMetrics.density) + 0.5f);
			final int _500dp = (int)((500.0f * displayMetrics.density) + 0.5f);
			isLandscape = (usableScreenWidth > usableScreenHeight);
			isLargeScreen = (
				((usableScreenWidth >= _550dp) && (usableScreenHeight >= _500dp)) ||
				((usableScreenHeight >= _550dp) && (usableScreenWidth >= _500dp))
			);
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
	public static final RectF rectF = new RectF();
	public static char decimalSeparator;
	public static boolean hasTouch, isLandscape, isTV, isLargeScreen, isScreenWidthLarge, isLowDpiScreen, deviceSupportsAnimations, is3D, isDividerVisible,
		isVerticalMarginLarge, keepScreenOn, doubleClickMode, marqueeTitle, blockBackKey, widgetTransparentBg, backKeyAlwaysReturnsToPlayerWhenBrowsing, wrapAroundList,
		extraSpacing, albumArt, albumArtSongList, visualizerPortrait, scrollBarToTheLeft, expandSeekBar, notFullscreen, controlsToTheLeft, hasBorders, placeTitleAtTheBottom, placeControlsAtTheBottom, playWithLongPress,
		isChromebook, largeTextIs22sp, displaySongNumberAndCount, allowPlayerAboveLockScreen, dimBackground;
	public static int _1dp, _4dp, _22sp, _18sp, _14sp, _22spBox, defaultCheckIconSize, _18spBox, _14spBox, _22spYinBox, _18spYinBox, _14spYinBox, _Largesp, _LargespBox, _LargespYinBox,
		_Headingsp, _HeadingspBox, _HeadingspYinBox, controlLargeMargin, controlMargin, controlSmallMargin, controlXtraSmallMargin, dialogMargin, dialogDropDownVerticalMargin, verticalMargin,
		menuMargin, strokeSize, thickDividerSize, defaultControlContentsSize, defaultControlSize, usableScreenWidth, usableScreenHeight, screenWidth, screenHeight, densityDpi, forcedOrientation,
		msgs, msgStartup, widgetTextColor, widgetIconColor, lastVersionCode, browserScrollBarType, songListScrollBarType;
	public static int[] lastViewCenterLocation = new int[2];
	public static Bitmap icPrev, icPlay, icPause, icNext, icPrevNotif, icPlayNotif, icPauseNotif, icNextNotif, icExitNotif;
	public static byte[] customColors;
	public static AccessibilityManager accessibilityManager;
	public static boolean isAccessibilityManagerEnabled;

	private static int currentLocale, createdWidgetIconColor;
	private static boolean alternateTypefaceActive, fullyInitialized;
	//private static Toast internalToast;

	//These guys used to be private, but I decided to make them public, even though they still have
	//their setters, after I found out ProGuard does not inline static setters (or at least I have
	//not been able to figure out a way to do so....)
	public static boolean isUsingAlternateTypeface, isFlat;
	public static int forcedLocale, theme, transitions;
	
	public static float density, scaledDensity, xdpi_1_72;
	
	public static final Paint fillPaint;
	public static final TextPaint textPaint;
	private static int glowFilterColor;
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
		//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
		//  edgeFilter = new PorterDuffColorFilter(0, PorterDuff.Mode.CLEAR);
	}

	public static String format(int resId, String p1) {
		//replace %s... faster than format() ;)
		return Player.theApplication.getText(resId).toString().replace("%s", p1);
	}

	public static String format(int resId, String p1, String p2) {
		//replace %s... faster than format() ;)
		return Player.theApplication.getText(resId).toString().replace("%1$s", p1).replace("%2$s", p2);
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

	public static void setUsingAlternateTypeface(boolean useAlternateTypeface) {
		isUsingAlternateTypeface = useAlternateTypeface;
		if (useAlternateTypeface && !dyslexiaFontSupportsCurrentLocale()) {
			if (defaultTypeface == null || !alternateTypefaceActive) {
				alternateTypefaceActive = true;
				try {
					defaultTypeface = Typeface.createFromAsset(Player.theApplication.getAssets(), "fonts/OpenDyslexicRegular.otf");
				} catch (Throwable ex) {
					isUsingAlternateTypeface = false;
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
		//* although the proper way would be using bottom and top, regular characters are visually better
		//centered (vertically, within the line), when using descent and ascent!!!
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
		if (largeTextIs22sp) {
			_Largesp = _22sp;
			_LargespBox = _22spBox;
			_LargespYinBox = _22spYinBox;
			_Headingsp = _22sp;
			_HeadingspBox = _22spBox;
			_HeadingspYinBox = _22spYinBox;
		} else {
			_Largesp = _18sp;
			_LargespBox = _18spBox;
			_LargespYinBox = _18spYinBox;
			if (isChromebook) {
				_Headingsp = _18sp;
				_HeadingspBox = _18spBox;
				_HeadingspYinBox = _18spYinBox;
			} else {
				_Headingsp = _22sp;
				_HeadingspBox = _22spBox;
				_HeadingspYinBox = _22spYinBox;
			}
		}
	}

	public static String punctuationSpace(String text) {
		return ((currentLocale == LOCALE_FR) ? (" " + text) : text);
	}

	public static String collon() {
		switch (currentLocale) {
		case LOCALE_FR:
			return " : ";
		case LOCALE_ZH:
			return "：";
		default:
			return ": ";
		}
	}

	public static String collonNoSpace() {
		switch (currentLocale) {
		case LOCALE_FR:
			return " :";
		case LOCALE_ZH:
			return "：";
		default:
			return ":";
		}
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
		case LOCALE_DE:
			if (!"de".equals(l.getLanguage()))
				return new Locale("de");
			break;
		case LOCALE_FR:
			if (!"fr".equals(l.getLanguage()))
				return new Locale("fr");
			break;
		case LOCALE_ZH:
			if (!"zh".equals(l.getLanguage()))
				return new Locale("zh");
			break;
		}
		return l;
	}
	
	public static String getLocaleDescriptionFromCode(int localeCode) {
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
		case LOCALE_DE:
			return "Deutsch";
		case LOCALE_FR:
			return "Français";
		case LOCALE_ZH:
			return "中文（简体）";
		}
		return Player.theApplication.getText(R.string.standard_language).toString();
	}
	
	@SuppressWarnings("deprecation")
	public static int getCurrentLocale() {
		try {
			final String l = Player.theApplication.getResources().getConfiguration().locale.getLanguage().toLowerCase(Locale.US);
			if ("pt".equals(l))
				return LOCALE_PTBR;
			if ("ru".equals(l))
				return LOCALE_RU;
			if ("uk".equals(l))
				return LOCALE_UK;
			if ("es".equals(l))
				return LOCALE_ES;
			if ("de".equals(l))
				return LOCALE_DE;
			if ("fr".equals(l))
				return LOCALE_FR;
			if ("zh".equals(l))
				return LOCALE_ZH;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return LOCALE_US;
	}
	
	public static boolean dyslexiaFontSupportsCurrentLocale() {
		return ((currentLocale == LOCALE_RU) || (currentLocale == LOCALE_UK) || (currentLocale == LOCALE_ZH));
	}

	private static void updateDecimalSeparator() {
		try {
			final DecimalFormatSymbols d = new DecimalFormatSymbols(getLocaleFromCode(currentLocale));
			decimalSeparator = d.getDecimalSeparator();
		} catch (Throwable ex) {
			decimalSeparator = '.';
		}
	}

	public static void reapplyForcedLocale(Activity activityContext) {
		setForcedLocale(activityContext, forcedLocale);
	}

	public static void reapplyForcedLocaleOnPlugins(Context context) {
		try {
			setForcedLocaleOnContexts(context, null, forcedLocale);
		} catch (Throwable ex) {
			//just ignore
		}
	}

	@SuppressWarnings("deprecation")
	private static void setForcedLocaleOnContexts(Context context, Activity activityContext, int localeCode) {
		final Locale l = getLocaleFromCode(localeCode);
		final Resources res = context.getResources();
		final Configuration cfg = new Configuration();
		cfg.locale = l;
		res.getConfiguration().updateFrom(cfg);
		res.updateConfiguration(res.getConfiguration(), res.getDisplayMetrics());
		if (activityContext != null) {
			final Resources res2 = activityContext.getResources();
			if (res != res2) {
				res2.getConfiguration().updateFrom(cfg);
				res2.updateConfiguration(res2.getConfiguration(), res2.getDisplayMetrics());
			}
		}
	}

	public static boolean setForcedLocale(Activity activityContext, int localeCode) {
		if (localeCode < 0 || localeCode > LOCALE_MAX)
			localeCode = LOCALE_NONE;
		if (forcedLocale == 0 && localeCode == 0) {
			currentLocale = getCurrentLocale();
			updateDecimalSeparator();
			return false;
		}
		final boolean dyslexiaSupported = dyslexiaFontSupportsCurrentLocale();
		try {
			forcedLocale = localeCode;
			currentLocale = ((localeCode == 0) ? getCurrentLocale() : localeCode);
			setForcedLocaleOnContexts(Player.theApplication, activityContext, localeCode);
		} catch (Throwable ex) {
			currentLocale = getCurrentLocale();
		}
		updateDecimalSeparator();
		if (fullyInitialized && isUsingAlternateTypeface && dyslexiaSupported != dyslexiaFontSupportsCurrentLocale()) {
			setUsingAlternateTypeface(true);
			return true;
		}
		return false;
	}
	
	public static void setUsingAlternateTypefaceAndForcedLocale(boolean useAlternateTypeface, int localeCode) {
		isUsingAlternateTypeface = useAlternateTypeface;
		if (!setForcedLocale(null, localeCode))
			setUsingAlternateTypeface(useAlternateTypeface);
	}
	
	public static void loadWidgetRelatedSettings(Context context) {
		if (fullyInitialized)
			return;
		//sometimes the first thing called is this method
		Player.theApplication = context.getApplicationContext();
		final SerializableMap opts = Player.loadConfigFromFile();
		//I know, this is ugly... I'll fix it one day...
		setForcedLocale(null, opts.getInt(0x001E, LOCALE_NONE));
		//widgetTransparentBg = opts.getBoolean(0x0022, false);
		widgetTransparentBg = opts.getBit(18);
		widgetTextColor = opts.getInt(0x0023, 0xff000000);
		widgetIconColor = opts.getInt(0x0024, 0xff000000);
	}

	public static void initialize(Activity activityContext, int newUsableScreenWidth, int newUsableScreenHeight) {
		final Resources resources = (activityContext != null ? activityContext.getResources() : Player.theApplication.getResources());
		final Configuration configuration = resources.getConfiguration();

		accessibilityManager = (AccessibilityManager)Player.theApplication.getSystemService(Context.ACCESSIBILITY_SERVICE);
		isAccessibilityManagerEnabled = (accessibilityManager != null && accessibilityManager.isEnabled());
		if (iconsTypeface == null)
			iconsTypeface = Typeface.createFromAsset(Player.theApplication.getAssets(), "fonts/icons.ttf");
		if (!fullyInitialized) {
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2)
					isTV = ((((UiModeManager)Player.theApplication.getSystemService(Context.UI_MODE_SERVICE)).getCurrentModeType() & Configuration.UI_MODE_TYPE_TELEVISION) != 0);
			} catch (Throwable ex) {
				//just ignore
			}
			try {
				hasTouch = ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) || Player.theApplication.getPackageManager().hasSystemFeature("android.hardware.touchscreen"));
			} catch (Throwable ex) {
				hasTouch = true;
				ex.printStackTrace();
			}
			fullyInitialized = true;
		}
		final DisplayInfo info = new DisplayInfo();
		info.getInfo(activityContext, newUsableScreenWidth, newUsableScreenHeight);
		density = info.displayMetrics.density;
		densityDpi = info.displayMetrics.densityDpi;
		scaledDensity = info.displayMetrics.scaledDensity;
		xdpi_1_72 = info.displayMetrics.xdpi * (1.0f / 72.0f);
		screenWidth = info.screenWidth;
		screenHeight = info.screenHeight;
		usableScreenWidth = info.usableScreenWidth;
		usableScreenHeight = info.usableScreenHeight;
		isScreenWidthLarge = (usableScreenWidth >= dpToPxI(550));
		isLargeScreen = (isTV || info.isLargeScreen);
		isLandscape = info.isLandscape;
		isLowDpiScreen = info.isLowDpiScreen;
		//let's do some guessing here... :/
		deviceSupportsAnimations = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) || ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) && (density >= 1.5f || isLargeScreen)));

		//apparently, the display metrics returned by Resources.getDisplayMetrics()
		//is not the same as the one returned by Display.getMetrics()/getRealMetrics()
		final float sd = resources.getDisplayMetrics().scaledDensity;
		if (sd > 0)
			scaledDensity = sd;
		else if (scaledDensity <= 0)
			scaledDensity = 1.0f;
		
		_1dp = dpToPxI(1);
		strokeSize = (_1dp + 1) >> 1;
		thickDividerSize = dpToPxI(1.5f);// ((_1dp >= 2) ? _1dp : 2);
		if (thickDividerSize < 2) thickDividerSize = 2;
		if (thickDividerSize <= _1dp) thickDividerSize = _1dp + 1;
		_4dp = dpToPxI(4);
		_22sp = spToPxI(22);
		_18sp = spToPxI(18);
		_14sp = spToPxI(14);
		controlLargeMargin = dpToPxI(16);
		controlMargin = controlLargeMargin >> 1;
		controlSmallMargin = controlLargeMargin >> 2;
		controlXtraSmallMargin = controlLargeMargin >> 3;
		menuMargin = controlMargin;
		dialogMargin = controlLargeMargin;
		if (isLargeScreen || !isLowDpiScreen)
			menuMargin += controlSmallMargin;
		dialogDropDownVerticalMargin = (dialogMargin * 3) >> 1;
		defaultControlContentsSize = dpToPxI(32);
		defaultControlSize = defaultControlContentsSize + (controlMargin << 1);
		defaultCheckIconSize = dpToPxI(24); //both descent and ascent of iconsTypeface are 0!
		if (!setForcedLocale(activityContext, forcedLocale))
			setUsingAlternateTypeface(isUsingAlternateTypeface);
		setVerticalMarginLarge(isVerticalMarginLarge);
	}
	
	public static void prepareWidgetPlaybackIcons() {
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
			initialize(null, 0, 0);
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
	
	public static void prepareNotificationPlaybackIcons() {
		if (icPrevNotif != null)
			return;
		if (iconsTypeface == null)
			initialize(null, 0, 0);
		final Canvas c = new Canvas();
		textPaint.setTypeface(iconsTypeface);
		//instead of guessing the color, try to fetch the actual one first
		int color = 0;
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				color = getAndroidThemeColor(android.R.style.TextAppearance_Material_Notification_Title, android.R.attr.textColor);
			else
				color = getAndroidThemeColor(android.R.style.TextAppearance_StatusBar_EventContent_Title, android.R.attr.textColor);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		if ((color & 0xff000000) == 0)
			color = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? 0xff999999 : 0xffffffff);
		textPaint.setColor(color);
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
	
	public static CharSequence getThemeColorDescription(int idx) {
		switch (idx) {
		case 0:
			return Player.theApplication.getText(R.string.window_background);
		case 1:
			return Player.theApplication.getText(R.string.control_mode_background);
		case 2:
			return Player.theApplication.getText(R.string.visualizer_background);
		case 3:
		case 4:
			return Player.theApplication.getText(R.string.background);
		case 5:
			return Player.theApplication.getText(R.string.icon);
		case 6:
		case 18:
		case 22:
			return Player.theApplication.getText(R.string.border);
		case 7:
			return Player.theApplication.getText(R.string.divider);
		case 8:
			return Player.theApplication.getText(R.string.highlight_background);
		case 9:
			return Player.theApplication.getText(R.string.highlight_text);
		case 10:
			return Player.theApplication.getText(R.string.window_text);
		case 11:
			return Player.theApplication.getText(R.string.text_disabled);
		case 12:
		case 14:
		case 15:
			return Player.theApplication.getText(R.string.text);
		case 13:
			return Player.theApplication.getText(R.string.text_secondary);
		case 16:
		case 20:
			return Player.theApplication.getText(R.string.top_background);
		case 17:
		case 21:
			return Player.theApplication.getText(R.string.bottom_background);
		case 19:
		case 23:
			return Player.theApplication.getText(R.string.pressed_background);
		}
		return Player.theApplication.getText(R.string.no_info);
	}
	
	public static void serializeThemeColor(byte[] colors, int idx, int color) {
		colors[idx] = (byte)color;
		colors[idx + 1] = (byte)(color >>> 8);
		colors[idx + 2] = (byte)(color >>> 16);
	}
	
	public static int deserializeThemeColor(byte[] colors, int idx) {
		return 0xff000000 | ((int)colors[idx] & 0xff) | (((int)colors[idx + 1] & 0xff) << 8) | (((int)colors[idx + 2] & 0xff) << 16);
	}
	
	public static byte[] serializeThemeToArray() {
		final byte[] colors = new byte[72];
		serializeThemeColor(colors, 3 * IDX_COLOR_WINDOW, color_window);
		serializeThemeColor(colors, 3 * IDX_COLOR_CONTROL_MODE, color_control_mode);
		serializeThemeColor(colors, 3 * IDX_COLOR_VISUALIZER, color_visualizer);
		serializeThemeColor(colors, 3 * IDX_COLOR_LIST, color_list_original);
		serializeThemeColor(colors, 3 * IDX_COLOR_MENU, color_menu);
		serializeThemeColor(colors, 3 * IDX_COLOR_MENU_ICON, color_menu_icon);
		serializeThemeColor(colors, 3 * IDX_COLOR_MENU_BORDER, color_menu_border);
		serializeThemeColor(colors, 3 * IDX_COLOR_DIVIDER, color_divider);
		serializeThemeColor(colors, 3 * IDX_COLOR_HIGHLIGHT, color_highlight);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_HIGHLIGHT, color_text_highlight);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT, color_text);
		serializeThemeColor(colors, 3 * IDX_COLOR_TEXT_LISTITEM_DISABLED, color_text_listitem_disabled);
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
		color_list_original = deserializeThemeColor(colors, 3 * IDX_COLOR_LIST);
		color_list = color_list_original;
		color_menu = deserializeThemeColor(colors, 3 * IDX_COLOR_MENU);
		color_menu_icon = deserializeThemeColor(colors, 3 * IDX_COLOR_MENU_ICON);
		color_menu_border = deserializeThemeColor(colors, 3 * IDX_COLOR_MENU_BORDER);
		color_divider = deserializeThemeColor(colors, 3 * IDX_COLOR_DIVIDER);
		color_highlight = deserializeThemeColor(colors, 3 * IDX_COLOR_HIGHLIGHT);
		color_text_highlight = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_HIGHLIGHT);
		color_text = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT);
		color_text_listitem_disabled = deserializeThemeColor(colors, 3 * IDX_COLOR_TEXT_LISTITEM_DISABLED);
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

	private static void finishLoadingTheme(boolean custom, boolean generateDivider) {
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
		color_selected_pressed_border = ColorUtils.blend(color_selected_border, color_selected_pressed, 0.7f);
		color_focused_pressed_border = ColorUtils.blend(color_focused_border, color_focused_pressed, 0.7f);
		colorState_text_white_reactive = new BgColorStateList(0xffffffff, color_text_selected);
		colorState_text_menu_reactive = new BgColorStateList(color_text_menu, color_text_selected);
		colorState_text_reactive = new BgColorStateList(color_text, color_text_selected);
		colorState_text_static = new BgColorStateList(color_text);
		colorState_text_listitem_static = new BgColorStateList(color_text_listitem);
		colorState_text_listitem_reactive = new BgColorStateList(color_text_listitem, color_text_selected);
		colorState_text_listitem_secondary_static = new BgColorStateList(color_text_listitem_secondary);
		colorState_text_selected_static = new BgColorStateList(color_text_selected);
		colorState_highlight_static = new BgColorStateList(color_highlight);
		colorState_text_highlight_static = new BgColorStateList(color_text_highlight);
		colorState_text_highlight_reactive = new BgColorStateList(color_text_highlight, color_text_selected);

		if (!custom) {
			if (theme == THEME_FPLAY_ICY) {
				color_text_title = color_text;
				colorState_text_title_static = colorState_text_static;
			} else {
				color_text_title = color_highlight;
				colorState_text_title_static = colorState_highlight_static;
			}
			color_menu_border = color_selected_border;
			colorState_text_control_mode_reactive = colorState_text_reactive;
			colorState_text_visualizer_static = colorState_text_static;
			colorState_text_visualizer_reactive = colorState_text_reactive;
		}

		color_list_original = color_list;
		if (ColorUtils.relativeLuminance(color_list_original) >= 0.6) {
			color_list = color_list_original;
			color_list_bg = (is3D ? ColorUtils.blend(color_list_original, 0xff000000, 0.9286f) : color_list_original);
			color_list_shadow = ColorUtils.blend(color_list_original, 0xff000000, 0.77777777f);
			if (generateDivider)
				color_divider = ColorUtils.blend(color_list_bg, 0xff000000, 0.7f);
			if (theme == THEME_FPLAY && is3D) {
				color_list = 0xffd9d9d9;
				color_list_original = 0xffd9d9d9;
			}
		} else {
			if (is3D)
				color_list = ColorUtils.blend(color_list_original, 0xffffffff, 0.9286f);
			color_list_bg = color_list_original;
			color_list_shadow = ColorUtils.blend(color_list_original, 0xffffffff, 0.77777777f);
			if (generateDivider)
				color_divider = ColorUtils.blend(color_list_bg, 0xffffffff, 0.7f);
		}

		color_divider_pressed = ColorUtils.blend(color_divider, color_text_listitem, 0.7f);

		color_glow = ((color_text_listitem_secondary != color_highlight) ? color_text_listitem_secondary :
			((ColorUtils.contrastRatio(color_window, color_list) >= 3.5) ? color_window :
				color_text_listitem));

		color_dialog_detail = color_divider;
		color_dialog_detail_highlight = color_text_listitem_secondary;

		//choose the color with a nice contrast against the list background to be the glow color
		//the color is treated as SRC, and the bitmap is treated as DST
		glowFilter = ((Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) ? new PorterDuffColorFilter(color_glow, PorterDuff.Mode.SRC_IN) : null);
	}
	
	private static boolean loadCustomTheme(ActivityHost activityHost) {
		if (!deserializeThemeFromArray(customColors)) {
			customColors = null;
			setTheme(activityHost, THEME_FPLAY);
			return false;
		}
		finishLoadingTheme(true, false);

		//check which color to use for the buttons in the main screen (when in control mode) and in the visualizer screen
		double maxRatio, ratio;

		//control mode
		maxRatio = ColorUtils.contrastRatio(color_control_mode, color_text);
		colorState_text_control_mode_reactive = colorState_text_reactive;
		ratio = ColorUtils.contrastRatio(color_control_mode, color_text_listitem);
		if (maxRatio < ratio) {
			maxRatio = ratio;
			colorState_text_control_mode_reactive = colorState_text_listitem_reactive;
		}
		//make the buttons visible, no matter what
		if (maxRatio < 4.0) {
			final int color = ((ColorUtils.contrastRatio(color_control_mode, 0xffffffff) > ColorUtils.contrastRatio(color_control_mode, 0xff000000)) ? 0xffffffff : 0xff000000);
			colorState_text_control_mode_reactive = new BgColorStateList(color, color_text_selected);
		}

		//visualizer
		maxRatio = ColorUtils.contrastRatio(color_visualizer, color_text);
		colorState_text_visualizer_static = colorState_text_static;
		colorState_text_visualizer_reactive = colorState_text_reactive;
		ratio = ColorUtils.contrastRatio(color_visualizer, color_text_listitem);
		if (maxRatio < ratio) {
			maxRatio = ratio;
			colorState_text_visualizer_static = colorState_text_listitem_static;
			colorState_text_visualizer_reactive = colorState_text_listitem_reactive;
		}
		//make the buttons visible, no matter what
		if (maxRatio < 4.0) {
			final int color = ((ColorUtils.contrastRatio(color_visualizer, 0xffffffff) > ColorUtils.contrastRatio(color_visualizer, 0xff000000)) ? 0xffffffff : 0xff000000);
			colorState_text_visualizer_static = new BgColorStateList(color, color);
			colorState_text_visualizer_reactive = new BgColorStateList(color, color_text_selected);
		}

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
	
	private static void loadCommonColors(boolean invertSelectedAndFocus) {
		color_window = 0xff303030;
		color_control_mode = 0xff000000;
		color_visualizer = 0xff000000;
		color_list = 0xff080808;
		color_menu = 0xffffffff;
		color_menu_icon = 0xff555555;
		//color_divider = 0xff3f3f3f;
		color_highlight = 0xfffad35a;
		color_text_highlight = 0xff000000;
		color_text = 0xffffffff;
		color_text_listitem_disabled = 0xff8c8c8c;
		color_text_listitem = 0xffffffff;
		color_text_listitem_secondary = 0xfffad35a;
		color_text_selected = 0xff000000;
		color_text_menu = 0xff000000;
		if (invertSelectedAndFocus) {
			color_focused_grad_lt = 0xffd1e8ff;
			color_focused_grad_dk = 0xff5798ff;//0xff5da2e3;
			color_focused_border = 0xff518ec2;
			color_focused_pressed = 0xffcfe1ff;
			color_selected_grad_lt = 0xfff7eb6a;
			color_selected_grad_dk = 0xfffdbb4a;//0xfffeb645;
			color_selected_border = 0xffad9040;
			color_selected_pressed = 0xffffeed4;
		} else {
			color_selected_grad_lt = 0xffd1e8ff;
			color_selected_grad_dk = 0xff5798ff;//0xff5da2e3;
			color_selected_border = 0xff518ec2;
			color_selected_pressed = 0xffcfe1ff;
			color_focused_grad_lt = 0xfff7eb6a;
			color_focused_grad_dk = 0xfffdbb4a;//0xfffeb645;
			color_focused_border = 0xffad9040;
			color_focused_pressed = 0xffffeed4;
		}
	}

	public static String getThemeString(int theme) {
		switch (theme) {
		case THEME_CUSTOM:
			return Player.theApplication.getText(R.string.custom).toString();
		case THEME_BLUE_ORANGE:
			return Player.theApplication.getText(R.string.blue_orange).toString();
		case THEME_BLUE:
			return Player.theApplication.getText(R.string.blue).toString();
		case THEME_ORANGE:
			return Player.theApplication.getText(R.string.orange).toString();
		case THEME_LIGHT:
			return Player.theApplication.getText(R.string.light).toString();
		case THEME_DARK_LIGHT:
			return Player.theApplication.getText(R.string.dark_light).toString();
		case THEME_CREAMY:
			return Player.theApplication.getText(R.string.creamy).toString();
		case THEME_FPLAY_2016:
			return "FPlay (2016)";
		case THEME_FPLAY_ICY:
			return "FPlay " + Player.theApplication.getText(R.string.icy).toString();
		case THEME_FPLAY_DARK:
			return "FPlay " + Player.theApplication.getText(R.string.dark).toString();
		default:
			return "FPlay";
		}
	}

	public static void setTheme(ActivityHost activityHost, int theme) {
		UI.theme = theme;
		Gradient.purgeAll();
		//internalToast = null;
		switch (theme) {
		case THEME_CUSTOM:
			loadCustomTheme(activityHost);
			break;
		case THEME_BLUE_ORANGE:
			loadCommonColors(false);
			finishLoadingTheme(false, true);
			break;
		case THEME_BLUE:
			loadCommonColors(false);
			color_highlight = 0xff94c0ff;
			color_text_listitem_secondary = 0xff94c0ff;
			finishLoadingTheme(false, true);
			break;
		case THEME_ORANGE:
			loadCommonColors(true);
			finishLoadingTheme(false, true);
			break;
		case THEME_LIGHT:
			loadCommonColors(false);
			color_window = 0xffe0e0e0;
			color_control_mode = 0xffe0e0e0;
			color_visualizer = 0xffe0e0e0;
			color_list = 0xfff2f2f2;
			color_highlight = 0xff0000f1;
			color_text_highlight = 0xffffffff;
			color_text = 0xff000000;
			color_text_listitem_secondary = 0xff0000f1;
			color_text_listitem = 0xff000000;
			finishLoadingTheme(false, true);
			color_menu_border = color_divider; //0xffc4c4c4;
			break;
		case THEME_DARK_LIGHT:
			loadCommonColors(false);
			color_list = 0xfff2f2f2;
			color_text_listitem_secondary = 0xff0000f1;
			color_text_listitem = 0xff000000;
			finishLoadingTheme(false, true);
			color_menu_border = color_divider; //0xffc4c4c4;
			break;
		case THEME_CREAMY:
			loadCommonColors(false);
			color_window = 0xff275a96;
			color_list = 0xfff9f6ea;
			color_divider = 0xffaabbcc;
			color_text_listitem_secondary = 0xff0052a8;
			color_text_listitem = 0xff000000;
			finishLoadingTheme(false, false);
			color_menu_border = color_divider;
			color_text_title = color_text;
			colorState_text_title_static = colorState_text_static;
			break;
		case THEME_FPLAY_2016:
			color_window = 0xff444abf;
			color_control_mode = 0xff000000;
			color_visualizer = 0xff000000;
			color_list = 0xfffcfcfc;
			color_menu = 0xfffcfcfc;
			color_menu_icon = 0xff555555;
			color_highlight = 0xffffcc66;
			color_text_highlight = 0xff000000;
			color_text = 0xffffffff;
			color_text_listitem_disabled = 0xff555555;
			color_text_listitem = 0xff000000;
			color_text_listitem_secondary = 0xff353be0;
			color_text_selected = 0xff000000;
			color_text_menu = 0xff000000;
			color_selected_grad_lt = 0xffffdd99;
			color_selected_grad_dk = 0xffffbb33;
			color_selected_border = 0xffce9731;
			color_selected_pressed = 0xffffe5b5;
			color_focused_grad_lt = 0xffd6d8ff;
			color_focused_grad_dk = 0xffaaafff;
			color_focused_border = 0xff696dbf;
			color_focused_pressed = 0xffe5e6ff;
			finishLoadingTheme(false, true);
			color_menu_border = color_divider; //0xffc4c4c4;
			color_text_title = color_text;
			colorState_text_title_static = colorState_text_static;
			break;
		case THEME_FPLAY_ICY:
			color_window = 0xff333333;
			color_control_mode = 0xff000000;
			color_visualizer = 0xff000000;
			color_list = 0xffcccccc;
			color_menu = 0xff333333;
			color_menu_icon = 0xff5599ff;
			color_highlight = 0xff5599ff;
			color_text_highlight = 0xff000000;
			color_text = 0xffffffff;
			color_text_listitem_disabled = 0xff555555;
			color_text_listitem = 0xff333333;
			color_text_listitem_secondary = 0xff0044aa;
			color_text_selected = 0xff000000;
			color_text_menu = 0xffaaccff;
			color_selected_grad_lt = 0xffaaccff;
			color_selected_grad_dk = 0xff5599ff;
			color_selected_border = 0xff0044aa;
			color_selected_pressed = 0xffaaccff;
			color_focused_grad_lt = 0xfff0f0f0;
			color_focused_grad_dk = 0xfff0f0f0;
			color_focused_border = 0xff888888;
			color_focused_pressed = 0xffffffff;
			finishLoadingTheme(false, true);
			color_menu_border = 0xff5599ff;
			break;
		default:
			if (theme == THEME_FPLAY_DARK) {
				color_window = 0xff3d3d5b;
				color_menu = 0xff3d3d5b;
				color_menu_icon = 0xffffcc66;
				color_text_menu = 0xffd6d8ff;
				color_text_listitem_secondary = 0xff3a40a8;
			} else {
				UI.theme = THEME_FPLAY;
				color_window = 0xff3344bb;
				//light up the menu a bit to make it look better without the dimmed background
				color_menu = 0xff223377; //0xff222244;
				color_menu_icon = 0xffffbb33;
				color_text_menu = 0xffbbddff; //0xffaaccff;
				color_text_listitem_secondary = 0xff0033cc;
			}
			color_control_mode = 0xff000000;
			color_visualizer = 0xff000000;
			color_list = 0xffcccccc;
			color_highlight = 0xffffcc66;
			color_text_highlight = 0xff000000;
			color_text = 0xffffffff;
			color_text_listitem_disabled = 0xff555555;
			color_text_listitem = 0xff000000;
			color_text_selected = 0xff000000;
			color_selected_grad_lt = 0xffffdd99;
			color_selected_grad_dk = 0xffffbb33;
			color_selected_border = 0xffce9731;
			color_selected_pressed = 0xffffe5b5;
			color_focused_grad_lt = 0xffaaafff;
			color_focused_grad_dk = 0xff878dff;
			color_focused_border = 0xff696dbf;
			color_focused_pressed = 0xffe5e6ff;
			finishLoadingTheme(false, true);
			color_menu_border = (theme == THEME_FPLAY_DARK ? 0xff808299 : 0xff0066ff);
			break;
		}
		if (activityHost != null)
			setAndroidThemeAccordingly(activityHost);
	}

	//public static boolean isAndroidThemeLight() {
	//	return ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) && (ColorUtils.relativeLuminance(color_menu) >= 0.5));
	//}

	public static void setAndroidThemeAccordingly(ActivityHost activityHost) {
		//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
		//	Player.theApplication.setTheme(isAndroidThemeLight() ? R.style.AppTheme : R.style.AppThemeDark);
		//	activityHost.setTheme(isAndroidThemeLight() ? R.style.AppTheme : R.style.AppThemeDark);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				activityHost.updateSystemColors(true);
		//}

		if (notFullscreen)
			activityHost.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		else
			activityHost.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	public static int getAndroidThemeColor(int style, int attribute) {
		final TypedArray array = Player.theApplication.getTheme().obtainStyledAttributes(style, new int[]{attribute});
		final int color = array.getColor(array.getIndex(0), 0);
		array.recycle();
		return color;
	}

	public static void setFlat(boolean flat) {
		isFlat = flat;
		Gradient.purgeAll();
	}

	public static void setTransitions(int transitions) {
		setTransition(transitions & 0xFF);
		setPopupTransition(transitions >>> 8);
	}

	public static void setTransition(int transition) {
		switch (transition) {
		case TRANSITION_ZOOM_FADE:
		case TRANSITION_SLIDE_SMOOTH:
		case TRANSITION_SLIDE:
		case TRANSITION_FADE:
		case TRANSITION_DISSOLVE:
		case TRANSITION_ZOOM:
			transitions = (transitions & (~0xFF)) | transition;
			break;
		default:
			transitions = (transitions & (~0xFF));
			break;
		}
	}

	public static void setPopupTransition(int transition) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			switch (transition) {
			case TRANSITION_ZOOM_FADE:
			case TRANSITION_SLIDE_SMOOTH:
			case TRANSITION_FADE:
			case TRANSITION_DISSOLVE:
				transitions = (transitions & (~0xFF00)) | (transition << 8);
				break;
			default:
				transitions = (transitions & (~0xFF00));
				break;
			}
		} else {
			switch (transition) {
			case TRANSITION_ZOOM_FADE:
				transitions = (transitions & (~0xFF00)) | (TRANSITION_SLIDE_SMOOTH << 8);
				break;
			case TRANSITION_SLIDE_SMOOTH:
			case TRANSITION_FADE:
				transitions = (transitions & (~0xFF00)) | (transition << 8);
				break;
			default:
				transitions = (transitions & (~0xFF00));
				break;
			}
		}
	}

	public static String getTransitionString(int transition) {
		switch (transition) {
		case TRANSITION_ZOOM_FADE:
			return Player.theApplication.getText(R.string.zoom).toString() + " + " + Player.theApplication.getText(R.string.fade).toString();
		case TRANSITION_SLIDE_SMOOTH:
			return Player.theApplication.getText(R.string.slide).toString() + " (" + Player.theApplication.getText(R.string.smooth).toString() + ")";
		case TRANSITION_SLIDE:
			return Player.theApplication.getText(R.string.slide).toString();
		case TRANSITION_FADE:
			return Player.theApplication.getText(R.string.fade).toString();
		case TRANSITION_DISSOLVE:
			return Player.theApplication.getText(R.string.dissolve).toString();
		case TRANSITION_ZOOM:
			return Player.theApplication.getText(R.string.zoom).toString();
		default:
			return Player.theApplication.getText(R.string.none).toString();
		}
	}

	public static void showNextStartupMsg(Context context) {
		if (msgStartup >= 36) {
			msgStartup = 36;
			return;
		}
		final int title = R.string.new_setting;
		msgStartup = 36;
		//final String content = context.getText(R.string.startup_message).toString() + "!\n\n" + context.getText(R.string.there_are_new_features).toString() + "\n- " + context.getText(R.string.expand_seek_bar).toString() + "\n\n" + context.getText(R.string.check_it_out).toString();
		//final String content = context.getText(R.string.there_are_new_features).toString() + "\n- " + context.getText(R.string.fullscreen).toString() + "\n- " + context.getText(R.string.transition).toString() + "\n- " + context.getText(R.string.color_theme).toString() + ": " + context.getText(R.string.creamy).toString() + "\n\n" + context.getText(R.string.check_it_out).toString();
		//final String content = context.getText(R.string.startup_message).toString();
		//final String content = context.getText(R.string.there_are_new_features).toString() + "\n- " + context.getText(R.string.color_theme).toString() + ": FPlay\n\n" + context.getText(R.string.visualizer).toString() + "! :D\n- Liquid Spectrum\n- Spinning Rainbow\n\n" + context.getText(R.string.check_it_out).toString();
		//final String content = "- " + context.getText(R.string.visualizer).toString() + ":\n" +  context.getText(R.string.album_art).toString() + "\nInto the Particles! :D\n\n- " + context.getText(R.string.color_theme).toString() + ":\nFPlay\n\n" + context.getText(R.string.check_it_out).toString();
		//final String content = context.getText(R.string.there_are_new_features).toString() + "\n- " + context.getText(R.string.accessibility) + "\n- 3D\n\n" + ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) ? context.getText(R.string.visualizer) + ":\n- Into the Particles (VR)\n\n" : "") + context.getText(R.string.startup_message).toString() + "\n- " + context.getText(R.string.loudspeaker).toString() + "\n- " + context.getText(R.string.earphones).toString() + "\n- " + context.getText(R.string.bluetooth).toString() + "\n\n" + context.getText(R.string.check_it_out).toString();
		//final String content = context.getText(R.string.there_are_new_features).toString() + ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "\n- " + context.getText(R.string.radio) : "") + "\n- " + context.getText(R.string.play_with_long_press) + "\n- " + context.getText(R.string.accessibility) + "\n- 3D\n\n" + context.getText(R.string.radio_directory) + punctuationSpace(":\n- SHOUTcast\n- Icecast\n\n") + context.getText(R.string.transition) + "\n- " + getTransitionString(TRANSITION_SLIDE_SMOOTH) + "\n\n" + ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) ? context.getText(R.string.visualizer) + punctuationSpace(":\n- Into the Particles (VR)\n\n") : "") + context.getText(R.string.startup_message).toString() + "\n- " + context.getText(R.string.loudspeaker).toString() + "\n- " + context.getText(R.string.earphones).toString() + "\n- " + context.getText(R.string.bluetooth).toString() + "\n\n" + context.getText(R.string.check_it_out).toString();
		/*final String content = context.getText(R.string.there_are_new_features) +
			"\n- " + context.getText(R.string.hdr_playback) + punctuationSpace(": ") + context.getText(R.string.previous_resets_after_the_beginning) +
			"\n- " + context.getText(R.string.hdr_playback) + punctuationSpace(": ") + context.getText(R.string.enable_external_fx) +
			"\n- " + context.getText(R.string.color_theme) + punctuationSpace(": FPlay ") + context.getText(R.string.dark) +
			((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "\n- " + context.getText(R.string.radio) : "") +
			"\n- " + context.getText(R.string.play_with_long_press) +
			"\n- " + context.getText(R.string.accessibility) +
			"\n- 3D\n\n" +
			context.getText(R.string.radio_directory) + punctuationSpace(":\n- SHOUTcast\n- Icecast\n\n") +
			context.getText(R.string.check_it_out).toString();*/
		final String content = //"- " + context.getText(R.string.ringtone) +
			//"\n\n" +
			context.getText(R.string.there_are_new_features) +
			"\n\n- " + context.getText(R.string.hdr_display) + punctuationSpace(": ") + context.getText(R.string.album_art) +
			"\n\n- " + context.getText(R.string.hdr_display) + punctuationSpace(": ") + context.getText(R.string.place_controls_at_the_bottom) +
			"\n\n" +
			context.getText(R.string.check_it_out).toString();
		final BgDialog dialog = new BgDialog(context, createDialogView(context, content), null);
		dialog.setTitle(title);
		dialog.setPositiveButton(R.string.got_it);
		dialog.show();
	}

	public static void setVerticalMarginLarge(boolean isVerticalMarginLarge) {
		UI.isVerticalMarginLarge = isVerticalMarginLarge;
		verticalMargin = (isVerticalMarginLarge ? controlLargeMargin : controlMargin);
	}

	public static boolean showMsg(Context context, int msg) {
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
		msgs |= msg;
		final BgDialog dialog = new BgDialog(context, createDialogView(context, context.getText(content)), null);
		dialog.setTitle(title);
		dialog.setPositiveButton(R.string.got_it);
		dialog.show();
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

	public static void drawText(Canvas canvas, String text, int start, int end, int color, int size, int x, int y) {
		textPaint.setColor(color);
		textPaint.setTextSize(size);
		canvas.drawText(text, start, end, x, y, textPaint);
	}

	public static void fillRect(Rect rect, Canvas canvas, int fillColor) {
		fillPaint.setColor(fillColor);
		canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
	}

	public static void fillRect(Rect rect, Canvas canvas, Shader shader) {
		fillPaint.setShader(shader);
		canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
		fillPaint.setShader(null);
	}

	public static void strokeRect(Rect rect, Canvas canvas, int strokeColor, int thickness) {
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
		if ((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0)
			return color_focused_border;
		if ((state & STATE_SELECTED_OR_HOVERED) != 0)
			return color_selected_border;
		return 0;
	}
	
	public static void drawBgBorderless(Rect rect,  Canvas canvas, int state) {
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_pressed : color_selected_pressed);
			} else if ((state & (STATE_SELECTED_OR_HOVERED | STATE_FOCUSED)) != 0) {
				if (isFlat) {
					fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused : color_selected);
				} else {
					fillPaint.setShader(Gradient.getGradient(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0), false, rect.bottom - rect.top));
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
					fillPaint.setShader(null);
					return;
				}
			} else { //if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(color_selected_multi);
			}
			canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
		}
	}

	public static void drawBgListItem2D(Rect rect, Canvas canvas, int state) {
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_pressed : color_selected_pressed);
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
			} else if ((state & (STATE_SELECTED_OR_HOVERED | STATE_FOCUSED)) != 0) {
				if (isFlat) {
					fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused : color_selected);
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
				} else {
					fillPaint.setShader(Gradient.getGradient(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0), false, rect.bottom));
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
					fillPaint.setShader(null);
				}
			} else { //if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(color_selected_multi);
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
			}
		}
	}

	public static void drawBgListItem(Rect rect,  Canvas canvas, int state) {
		if (!is3D) {
			drawBgListItem2D(rect, canvas, state);
			return;
		}
		fillPaint.setColor(color_list_shadow);
		//right shadow
		canvas.drawRect(rect.right - strokeSize, rect.top + strokeSize, rect.right, rect.bottom, fillPaint);
		//bottom shadow
		canvas.drawRect(rect.left + strokeSize, rect.bottom - strokeSize, rect.right - strokeSize, rect.bottom, fillPaint);
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_pressed : color_selected_pressed);
			} else if ((state & (STATE_SELECTED_OR_HOVERED | STATE_FOCUSED)) != 0) {
				if (isFlat) {
					fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused : color_selected);
				} else {
					fillPaint.setShader(Gradient.getGradient(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0), false, rect.bottom - strokeSize - rect.top));
					canvas.drawRect(rect.left, rect.top, rect.right - strokeSize, rect.bottom - strokeSize, fillPaint);
					fillPaint.setShader(null);
					return;
				}
			} else { //if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(color_selected_multi);
			}
		} else {
			fillPaint.setColor(color_list);
		}
		canvas.drawRect(rect.left, rect.top, rect.right - strokeSize, rect.bottom - strokeSize, fillPaint);
	}

	public static void drawBgListItem2DWithDivider(Rect rect, Canvas canvas, int state, boolean dividerAllowed, int dividerMarginLeft, int dividerMarginRight) {
		dividerAllowed &= isDividerVisible;
		if (dividerAllowed)
			rect.bottom -= strokeSize;
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_pressed : color_selected_pressed);
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
			} else if ((state & (STATE_SELECTED_OR_HOVERED | STATE_FOCUSED)) != 0) {
				if (isFlat) {
					fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused : color_selected);
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
				} else {
					fillPaint.setShader(Gradient.getGradient(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0), false, rect.bottom));
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
					fillPaint.setShader(null);
				}
			} else { //if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(color_selected_multi);
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
			}
		}
		if (dividerAllowed) {
			fillPaint.setColor(color_divider);
			canvas.drawRect(rect.left + dividerMarginLeft, rect.bottom, rect.right - dividerMarginRight, rect.bottom += strokeSize, fillPaint);
		}
	}

	public static void drawBgListItemWithDivider(Rect rect, Canvas canvas, int state, boolean dividerAllowed, int dividerMarginLeft, int dividerMarginRight) {
		if (!is3D) {
			drawBgListItem2DWithDivider(rect, canvas, state, dividerAllowed, dividerMarginLeft, dividerMarginRight);
			return;
		}
		fillPaint.setColor(color_list_shadow);
		//right shadow
		canvas.drawRect(rect.right - strokeSize, rect.top + strokeSize, rect.right, rect.bottom, fillPaint);
		//bottom shadow
		canvas.drawRect(rect.left + strokeSize, rect.bottom - strokeSize, rect.right - strokeSize, rect.bottom, fillPaint);
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_pressed : color_selected_pressed);
			} else if ((state & (STATE_SELECTED_OR_HOVERED | STATE_FOCUSED)) != 0) {
				if (isFlat) {
					fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused : color_selected);
				} else {
					fillPaint.setShader(Gradient.getGradient(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0), false, rect.bottom - strokeSize - rect.top));
					canvas.drawRect(rect.left, rect.top, rect.right - strokeSize, rect.bottom - strokeSize, fillPaint);
					fillPaint.setShader(null);
					return;
				}
			} else { //if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(color_selected_multi);
			}
		} else {
			fillPaint.setColor(color_list);
		}
		canvas.drawRect(rect.left, rect.top, rect.right - strokeSize, rect.bottom - strokeSize, fillPaint);
	}

	public static void drawBg(Rect rect, Canvas canvas, int state) {
		if ((state & ~STATE_CURRENT) != 0) {
			if ((state & STATE_PRESSED) != 0) {
				fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_pressed : color_selected_pressed);
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
				if (hasBorders) strokeRect(rect, canvas, ((state & STATE_FOCUSED) != 0) ? color_focused_pressed_border : color_selected_pressed_border, strokeSize);
			} else if ((state & (STATE_SELECTED_OR_HOVERED | STATE_FOCUSED)) != 0) {
				if (isFlat) {
					fillPaint.setColor(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused : color_selected);
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
				} else {
					fillPaint.setShader(Gradient.getGradient(((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0), false, rect.bottom - rect.top));
					canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
					fillPaint.setShader(null);
				}
				if (hasBorders) strokeRect(rect, canvas, ((state & STATE_FOCUSED) != 0 && (state & STATE_HOVERED) == 0) ? color_focused_border : color_selected_border, strokeSize);
			} else { //if ((state & STATE_MULTISELECTED) != 0) {
				fillPaint.setColor(color_selected_multi);
				canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, fillPaint);
			}
		}
	}
	
	public static int handleStateChanges(int state, boolean pressed, boolean focused, boolean hovered, View view) {
		boolean r = false;
		final boolean op = ((state & STATE_PRESSED) != 0), of = ((state & STATE_FOCUSED) != 0), oh = ((state & STATE_HOVERED) != 0);
		if (op != pressed) {
			if (pressed)
				state |= STATE_PRESSED;
			else
				state &= ~STATE_PRESSED;
			r = true;
		}
		if (of != focused) {
			if (focused)
				state |= STATE_FOCUSED;
			else
				state &= ~STATE_FOCUSED;
			r = true;
		}
		if (oh != hovered) {
			if (hovered)
				state |= STATE_HOVERED;
			else
				state &= ~STATE_HOVERED;
			r = true;
		}
		if (r)
			view.invalidate();
		return state;
	}

	public static int handleStateChanges(int state, View view) {
		//even using getDrawableState(), views inside scrollable parents
		//will "blink" briefly when mouse clicked, because Android treats
		//the mouse like browsers treat ontouchxxx (it only produces the
		//pressed state after some time, when it detects the mouse is
		//actually clicking and not scrolling...)
		boolean r = false;
		final int op = (state & STATE_PRESSED), of = (state & STATE_FOCUSED), oh = (state & STATE_HOVERED);
		final int[] states = view.getDrawableState();
		int pressed = 0, focused = 0, hovered = 0;
		if (states != null) {
			for (int i = states.length - 1; i >= 0; i--) {
				switch (states[i]) {
				case android.R.attr.state_pressed:
					pressed = STATE_PRESSED;
					break;
				case android.R.attr.state_focused:
					focused = STATE_FOCUSED;
					break;
				case android.R.attr.state_hovered:
					hovered = STATE_HOVERED;
					break;
				}
			}
		}
		if (op != pressed) {
			state = (state & ~STATE_PRESSED) | pressed;
			r = true;
		}
		if (of != focused) {
			state = (state & ~STATE_FOCUSED) | focused;
			r = true;
		}
		if (oh != hovered) {
			state = (state & ~STATE_HOVERED) | hovered;
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
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _Largesp);
		view.setTypeface(defaultTypeface);
	}
	
	public static void largeTextAndColor(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _Largesp);
		view.setTextColor(colorState_text_static);
		view.setTypeface(defaultTypeface);
	}

	public static void headingText(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _Headingsp);
		view.setTypeface(defaultTypeface);
	}

	public static void headingTextAndColor(TextView view) {
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, _Headingsp);
		view.setTextColor(colorState_text_static);
		view.setTypeface(defaultTypeface);
	}

	public static int getViewPaddingBasedOnScreenWidth(int defaultHorizontalPadding) {
		final int padding = (isScreenWidthLarge ? (usableScreenWidth / 7) : defaultHorizontalPadding);
		return ((padding > defaultHorizontalPadding) ? padding : defaultHorizontalPadding);
	}
	
	public static void prepareViewPaddingBasedOnScreenWidth(ViewGroup view, int defaultHorizontalPadding, int topPadding, int bottomPadding) {
		final int p = getViewPaddingBasedOnScreenWidth(defaultHorizontalPadding);
		view.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
		view.setPadding(p, topPadding, p, bottomPadding);
	}

	public static void shareText(String text) {
		Intent sharingIntent = new Intent(Intent.ACTION_SEND);
		sharingIntent.setType("text/plain");
		sharingIntent.putExtra(Intent.EXTRA_TEXT, text);
		sharingIntent = Intent.createChooser(sharingIntent, Player.theApplication.getText(R.string.share));
		if (sharingIntent != null)
			Player.theApplication.startActivity(sharingIntent);
	}

	public static void shareFile(String path) {
		Intent sharingIntent = new Intent(Intent.ACTION_SEND);
		sharingIntent.setType(FileFetcher.mimeType(path));
		sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
		sharingIntent = Intent.createChooser(sharingIntent, Player.theApplication.getText(R.string.share));
		if (sharingIntent != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				// Let's try to avoid creating a FileProvider, given that
				// we are not "leaking the private file URI", since it is
				// already a public/external URI anyway!!!
				// https://stackoverflow.com/a/42437379/3569421
				try {
					@SuppressWarnings("JavaReflectionMemberAccess")
					final Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
					m.invoke(null);
				} catch(Throwable ex){
					// Just ignore...
				}
			}
			Player.theApplication.startActivity(sharingIntent);
		}
	}

	public static String emoji(CharSequence text) {
		//check out strings.xml to understand why we need this...
		if (text == null)
			return null;
		return ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) ? text.toString() :
			text.toString()
			.replace(":(", "\uD83D\uDE22")
			.replace(":)", "\uD83D\uDE04")
			.replace(";)", "\uD83D\uDE09"));
	}

	public static void toast(Throwable ex) {
		String s = ex.getMessage();
		if (s != null && s.length() > 0)
			s = Player.theApplication.getText(R.string.error).toString() + " " + s;
		else
			s = Player.theApplication.getText(R.string.error).toString() + " " + ex.getClass().getName();
		toast(s, null);
	}
	
	public static void toast(int resId) {
		toast(Player.theApplication.getText(resId), null);
	}
	
	@SuppressWarnings("deprecation")
	private static void prepareNotificationViewColors(TextView view) {
		view.setTextColor(colorState_text_highlight_static);
		view.setBackgroundDrawable(hasBorders ? new BorderDrawable(ColorUtils.blend(color_highlight, 0, 0.5f), color_highlight, strokeSize, strokeSize, strokeSize, strokeSize) : new ColorDrawable(color_highlight));
	}

	@SuppressWarnings("deprecation")
	public static void toast(CharSequence text) {
		toast(text, null);
	}

	@SuppressWarnings("deprecation")
	public static void toast(CharSequence text, View descriptionPopupOwner) {
		//if (internalToast == null) {
		final Toast t = new Toast(Player.theApplication);
		final TextView v = new TextView(Player.theApplication);
		if (descriptionPopupOwner != null) {
			final int[] location = new int[2];
			descriptionPopupOwner.getLocationOnScreen(location);
			//try to place the toast above the control / to its left
			location[0] -= (controlLargeMargin << 1);
			if (location[0] < 0)
				location[0] = 0;
			final int height = (controlMargin << 1) + _14spBox;
			location[1] -= (height + controlMargin + _22sp); //22sp to make up for the status bar (sort of...)
			if (location[1] < 0)
				location[1] += height + controlMargin + descriptionPopupOwner.getHeight();
			t.setGravity(Gravity.TOP | Gravity.START, location[0], location[1]);
			smallText(v);
		} else {
			mediumText(v);
		}
		prepareNotificationViewColors(v);
		v.setGravity(Gravity.CENTER);
		v.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		v.setPadding(controlMargin, controlMargin, controlMargin, controlMargin);
		final LinearLayout linearLayout = new LinearLayout(Player.theApplication);
		linearLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setBackgroundDrawable(new BgShadowDrawable(Player.theApplication, BgShadowDrawable.SHADOW_TOAST));
		linearLayout.addView(v);
		t.setView(linearLayout);
		t.setDuration(descriptionPopupOwner != null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
		//	internalToast = t;
		//}
		((TextView)((ViewGroup)t.getView()).getChildAt(0)).setText(emoji(text));
		t.show();
	}

	@SuppressWarnings("deprecation")
	public static void customToast(CharSequence text, boolean longDuration, int textSize, int textColor, Drawable background) {
		final Toast t = new Toast(Player.theApplication);
		final TextView v = new TextView(Player.theApplication);
		v.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
		v.setTypeface(defaultTypeface);
		v.setTextColor(textColor);
		v.setBackgroundDrawable(background);
		v.setGravity(Gravity.CENTER);
		v.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		v.setPadding(controlMargin, controlMargin, controlMargin, controlMargin);
		v.setText(emoji(text));
		t.setView(v);
		t.setDuration(longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
		t.show();
	}

	public static void prepare(Menu menu) {
		final CustomContextMenu mnu = (CustomContextMenu)menu;
		try {
			mnu.setItemClassConstructor(BgButton.class.getConstructor(Context.class));
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		mnu.setBackground(hasBorders ? new BorderDrawable(color_menu_border, color_menu, strokeSize, strokeSize, strokeSize, strokeSize) : new ColorDrawable(color_menu));
		mnu.setPadding(0);
		mnu.setItemTextSizeInPixels(_Largesp);
		mnu.setItemTextColor(colorState_text_menu_reactive);
		mnu.setItemPadding(menuMargin);
		mnu.setItemGravity(Gravity.START | Gravity.CENTER_VERTICAL);
	}
	
	public static void separator(Menu menu, int groupId, int order) {
		((CustomContextMenu)menu).addSeparator(groupId, order, color_menu_border, strokeSize, defaultCheckIconSize + menuMargin + menuMargin, controlXtraSmallMargin, menuMargin, controlXtraSmallMargin);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void setNextFocusForwardId(View view, int nextFocusForwardId) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			view.setNextFocusForwardId(nextFocusForwardId);
	}

	public static TextView createDialogTextView(Context context, int id, CharSequence text) {
		final TextView textView = new TextView(context);
		if (id != 0)
			textView.setId(id);
		textView.setTypeface(defaultTypeface);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, _18sp);
		textView.setTextColor(colorState_text_listitem_static);
		if (text != null)
			textView.setText(text);
		return textView;
	}

	public static BgEditText createDialogEditText(Context context, int id, CharSequence text, CharSequence contentDescription, int inputType) {
		final BgEditText editText = new BgEditText(context);
		if (id != 0)
			editText.setId(id);
		editText.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
		editText.setContentDescription(contentDescription);
		editText.setInputType(inputType);
		if (text != null)
			editText.setText(text);
		return editText;
	}

	public static View createDialogView(Context context, CharSequence messageOnly) {
		if (messageOnly == null) {
			final LinearLayout l = new LinearLayout(context);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				removeSplitTouch(l);
			l.setOrientation(LinearLayout.VERTICAL);
			l.setPadding(dialogMargin, dialogMargin, dialogMargin, dialogMargin);
			l.setBaselineAligned(false);
			return l;
		}
		final ObservableScrollView scrollView = new ObservableScrollView(context, PLACEMENT_ALERT);
		final TextView txt = createDialogTextView(context, 0, emoji(messageOnly));
		txt.setPadding(dialogMargin, dialogMargin, dialogMargin, dialogMargin);
		scrollView.addView(txt);
		return scrollView;
	}

	public static void showDialogMessage(Context context, CharSequence title, CharSequence message, int buttonResId) {
		final BgDialog dialog = new BgDialog(context, createDialogView(context, message), null);
		dialog.setTitle(title);
		dialog.setNegativeButton(buttonResId);
		dialog.show();
	}

	public static void showDialogMessage(Context context, CharSequence title, CharSequence message, int positiveResId, int negativeResId, DialogInterface.OnClickListener clickListener, DialogInterface.OnDismissListener dismissListener) {
		final BgDialog dialog = new BgDialog(context, createDialogView(context, message), clickListener);
		dialog.setTitle(title);
		dialog.setPositiveButton(positiveResId);
		dialog.setNegativeButton(negativeResId);
		if (dismissListener != null)
			dialog.setOnDismissListener(dismissListener);
		dialog.show();
	}

	public static void storeViewCenterLocationForFade(View view) {
		if (view == null) {
			lastViewCenterLocation[0] = usableScreenWidth >> 1;
			lastViewCenterLocation[1] = usableScreenHeight >> 1;
		} else {
			view.getLocationOnScreen(lastViewCenterLocation);
			lastViewCenterLocation[0] += (view.getWidth() >> 1);
			lastViewCenterLocation[1] += (view.getHeight() >> 1);
		}
	}

	public static void preparePopupTransition(Dialog dialog) {
		final Window window = dialog.getWindow();
		if (window != null) {
			switch (transitions >>> 8) {
			case TRANSITION_ZOOM_FADE:
				window.setWindowAnimations(R.style.ZoomFadeAnimation);
				break;
			case TRANSITION_SLIDE_SMOOTH:
				window.setWindowAnimations(R.style.SlideSmoothAnimation);
				break;
			case TRANSITION_FADE:
				window.setWindowAnimations(R.style.FadeAnimation);
				break;
			case TRANSITION_DISSOLVE:
				window.setWindowAnimations(R.style.DissolveAnimation);
				break;
			default:
				window.setWindowAnimations(R.style.NoAnimation);
				break;
			}
		}
	}

	public static void removeInternalPaddingForEdgeEffect(AbsListView view) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return;
		try {
			final Method setOverScrollEffectPadding = AbsListView.class.getDeclaredMethod("setOverScrollEffectPadding", int.class, int.class);
			if (setOverScrollEffectPadding != null)
				setOverScrollEffectPadding.invoke(view, -view.getPaddingLeft(), -view.getPaddingRight());
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	public static void prepareEdgeEffect(ViewGroup view, int placement) {
		final int color = (placement == PLACEMENT_MENU ? color_menu_icon : color_glow);
		final Resources resources = view.getContext().getResources();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			try {
				if (glowFilter == null) {
					final Class<?> clazz = ((view instanceof ScrollView) ? ScrollView.class : AbsListView.class);
					Field mEdgeGlow;
					//EdgeEffect edgeEffect;
					mEdgeGlow = clazz.getDeclaredField("mEdgeGlowTop");
					boolean ok = false;
					if (mEdgeGlow != null) {
						ok = true;
						mEdgeGlow.setAccessible(true);
						/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							edgeEffect = (EdgeEffect)mEdgeGlow.get(view);
							if (edgeEffect == null) {
								edgeEffect = new EdgeEffect(context);
								mEdgeGlow.set(view, edgeEffect);
							}
							edgeEffect.setColor(color);
						} else*/ {
							mEdgeGlow.set(view, new BgEdgeEffect(Player.theApplication, color));
						}
					}
					mEdgeGlow = clazz.getDeclaredField("mEdgeGlowBottom");
					if (mEdgeGlow != null) {
						ok = true;
						mEdgeGlow.setAccessible(true);
						/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							edgeEffect = (EdgeEffect)mEdgeGlow.get(view);
							if (edgeEffect == null) {
								edgeEffect = new EdgeEffect(context);
								mEdgeGlow.set(view, edgeEffect);
							}
							edgeEffect.setColor(color);
						} else*/ {
							mEdgeGlow.set(view, new BgEdgeEffect(Player.theApplication, color));
						}
					}
					if (ok || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
						return;
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			} finally {
				view.setClipToPadding(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
			}
		}
		try {
			//if everything else fails, fall back to the old method!
			if (glowFilter == null || glowFilterColor != color) {
				glowFilterColor = color;
				glowFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
			}
			//
			//:D amazing hack/workaround, as explained here:
			//
			//http://evendanan.net/android/branding/2013/12/09/branding-edge-effect/
			Drawable drawable = resources.getDrawable(resources.getIdentifier("overscroll_glow", "drawable", "android"));
			if (drawable != null)
				//the color is treated as SRC, and the bitmap is treated as DST
				drawable.setColorFilter(glowFilter);
			drawable = resources.getDrawable(resources.getIdentifier("overscroll_edge", "drawable", "android"));
			if (drawable != null)
				//hide the edge!!! ;)
				drawable.setColorFilter(glowFilter);//edgeFilter);
		} catch (Throwable ex) {
			ex.printStackTrace();
		} finally {
			view.setClipToPadding(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
		}
	}

	@SuppressWarnings("deprecation")
	public static void offsetTopEdgeEffect(View view) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			try {
				if (glowFilter == null) {
					final Field mEdgeGlow = ((view instanceof ScrollView) ? ScrollView.class : AbsListView.class).getDeclaredField("mEdgeGlowTop");
					if (mEdgeGlow != null) {
						mEdgeGlow.setAccessible(true);
						final BgEdgeEffect edgeEffect = (BgEdgeEffect)mEdgeGlow.get(view);
						if (edgeEffect != null)
							edgeEffect.mOffsetY = thickDividerSize;
					}
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	@SuppressWarnings("deprecation")
	public static void disableEdgeEffect(Context context) {
		if (glowFilter == null)
			return;
		try {
			//http://evendanan.net/android/branding/2013/12/09/branding-edge-effect/
			final Resources resources = context.getResources();
			Drawable drawable = resources.getDrawable(resources.getIdentifier("overscroll_glow", "drawable", "android"));
			if (drawable != null)
				drawable.setColorFilter(null);
			drawable = resources.getDrawable(resources.getIdentifier("overscroll_edge", "drawable", "android"));
			if (drawable != null)
				drawable.setColorFilter(null);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	public static void reenableEdgeEffect(Context context) {
		if (glowFilter == null)
			return;
		try {
			//http://evendanan.net/android/branding/2013/12/09/branding-edge-effect/
			final Resources resources = context.getResources();
			Drawable drawable = resources.getDrawable(resources.getIdentifier("overscroll_glow", "drawable", "android"));
			if (drawable != null)
				drawable.setColorFilter(glowFilter);
			drawable = resources.getDrawable(resources.getIdentifier("overscroll_edge", "drawable", "android"));
			if (drawable != null)
				drawable.setColorFilter(glowFilter);//edgeFilter);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	public static void prepareControlContainer(View view, boolean topBorder, boolean bottomBorder, int leftPadding, int topPadding, int rightPadding, int bottomPadding) {
		final int t = (topBorder ? thickDividerSize : 0);
		final int b = (bottomBorder ? thickDividerSize : 0);
		view.setBackgroundDrawable(new BorderDrawable(color_highlight, color_window, 0, t, 0, b));
		view.setPadding(leftPadding, topPadding + t, rightPadding, bottomPadding + b);
	}

	@SuppressWarnings("deprecation")
	public static void prepareControlContainer(View view, boolean topBorder, boolean bottomBorder) {
		final int t = (topBorder ? thickDividerSize : 0);
		final int b = (bottomBorder ? thickDividerSize : 0);
		view.setBackgroundDrawable(new BorderDrawable(color_highlight, color_window, 0, t, 0, b));
		if (extraSpacing)
			view.setPadding(controlMargin, controlMargin + t, controlMargin, controlMargin + b);
		else
			view.setPadding(0, t, 0, b);
	}

	@SuppressWarnings("deprecation")
	public static void prepareControlContainerWithoutRightPadding(View view, boolean topBorder, boolean bottomBorder) {
		final int t = (topBorder ? thickDividerSize : 0);
		final int b = (bottomBorder ? thickDividerSize : 0);
		view.setBackgroundDrawable(new BorderDrawable(color_highlight, color_window, 0, t, 0, b));
		if (extraSpacing)
			view.setPadding(controlMargin, controlMargin + t, 0, controlMargin + b);
		else
			view.setPadding(0, t, 0, b);
	}

	public static void tryToChangeScrollBarThumb(View view, int color) {
		//this is not a simple workaround... it could be the mother of all workarounds out there! :)
		//http://stackoverflow.com/questions/21806852/change-the-color-of-scrollview-programmatically
		try {
			final Field mScrollCacheField = View.class.getDeclaredField("mScrollCache");
			mScrollCacheField.setAccessible(true);
			final Object mScrollCache = mScrollCacheField.get(view);

			final Field scrollBarField = mScrollCache.getClass().getDeclaredField("scrollBar");
			scrollBarField.setAccessible(true);
			final Object scrollBar = scrollBarField.get(mScrollCache);

			final Method method = scrollBar.getClass().getDeclaredMethod("setVerticalThumbDrawable", Drawable.class);
			method.setAccessible(true);

			//finally!!!
			method.invoke(scrollBar, new ScrollBarThumbDrawable(color));
		} catch (Throwable ex) {
			//well... apparently it did not work out as expected
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void removeSplitTouch(ViewGroup viewGroup) {
		viewGroup.setMotionEventSplittingEnabled(false);
	}

	public static void announceAccessibilityText(CharSequence text) {
		if (isAccessibilityManagerEnabled) {
			final AccessibilityEvent e = AccessibilityEvent.obtain();
			//I couldn't make AccessibilityEvent.TYPE_ANNOUNCEMENT work... even on Android 16+
			e.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
			e.setClassName("br.com.carlosrafaelgn.fplay.activity.ActivityHost");
			e.setPackageName(BuildConfig.APPLICATION_ID);
			e.getText().add(text);
			accessibilityManager.sendAccessibilityEvent(e);
		}
	}

	public interface AnimationPreShowViewHandler {
		void onAnimationPreShowView(View view);
	}

	private static final int ANIMATION_STATE_NONE = 0;
	private static final int ANIMATION_STATE_HIDING = 1;
	private static final int ANIMATION_STATE_SHOWING = 2;

	public static boolean animationEnabled;
	private static View animationFocusView;
	private static int animationHideCount, animationShowCount, animationState;
	private static View animationViewToShowFirst;
	private static View[] animationViewsToHideAndShow;
	//private static FastAnimator animationAnimatorShowFirst, animationAnimatorHide, animationAnimatorShow;
	private static Animation animationShowFirst, animationHide, animationShow;
	public static Runnable animationFinishedObserver;

	@Override
	public float getInterpolation(float input) {
		//making UI implement Interpolator saves us one class and one instance ;)

		//faster version of AccelerateDecelerateInterpolator using this sine approximation:
		//http://forum.devmaster.net/t/fast-and-accurate-sine-cosine/9648
		//we use the result of sin in the range -PI/2 to PI/2
		//input = (input * 3.14159265f) - 1.57079632f;
		//return 0.5f + (0.5f * ((1.27323954f * input) - (0.40528473f * input * (input < 0.0f ? -input : input))));

		//even faster version! using the Hermite interpolation (GLSL's smoothstep)
		//https://www.opengl.org/sdk/docs/man/html/smoothstep.xhtml
		return (input * input * (3.0f - (2.0f * input)));
	}

	public static Animation animationCreateAlpha(float fromAlpha, float toAlpha, int duration) {
		final Animation animation = new AlphaAnimation(fromAlpha, toAlpha);
		animation.setDuration(duration <= 0 ? TRANSITION_DURATION_FOR_VIEWS : duration);
		animation.setInterpolator(Player.theUI);
		animation.setRepeatCount(0);
		animation.setFillAfter(false);
		return animation;
	}

	private static void animationHandleTag(View view) {
		final Object tag;
		if ((tag = view.getTag()) != null) {
			if (tag instanceof AnimationPreShowViewHandler) {
				view.setTag(null);
				((AnimationPreShowViewHandler)tag).onAnimationPreShowView(view);
			} else if (tag instanceof CharSequence && view instanceof TextView) {
				view.setTag(null);
				((TextView)view).setText((CharSequence)tag);
			}
		}
	}

	private static void animationFinished(boolean abortAll) {
		boolean finished = (abortAll || (animationState == ANIMATION_STATE_SHOWING) || (animationShow == null));// && animationAnimatorShow == null));
		if (animationHideCount > 0 || animationShowCount > 0 || animationViewToShowFirst != null) {
			if (abortAll) {
				animationState = ANIMATION_STATE_NONE;
				if (animationShowFirst != null)
					animationShowFirst.cancel();
				//if (animationAnimatorShowFirst != null)
				//	animationAnimatorShowFirst.end();
				if (animationHide != null)
					animationHide.cancel();
				//if (animationAnimatorHide != null)
				//	animationAnimatorHide.end();
				if (animationShow != null)
					animationShow.cancel();
				//if (animationAnimatorShow != null)
				//	animationAnimatorShow.end();
			}
			if (abortAll || animationState == ANIMATION_STATE_HIDING) {
				for (int i = 0; i < animationHideCount; i++) {
					final View view = animationViewsToHideAndShow[i];
					if (view != null) {
						//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
							view.setAnimation(null);
						view.setVisibility(View.GONE);
						animationViewsToHideAndShow[i] = null;
					}
				}
				animationHideCount = 0;
				if (animationViewToShowFirst != null) {
					//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
						animationViewToShowFirst.setAnimation(null);
					animationViewToShowFirst = null;
				}
			}
			if (!finished) {
				finished = true;
				animationState = ANIMATION_STATE_SHOWING;
				for (int i = 0; i < animationShowCount; i++) {
					final View view = animationViewsToHideAndShow[16 + i];
					if (view != null) {
						if (view.getVisibility() != View.VISIBLE) {
							finished = false;
							animationHandleTag(view);
							view.setVisibility(View.VISIBLE);
							//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
								view.startAnimation(animationShow);
							//else
							//	view.setAlpha(0.0f);
						} else { //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							animationViewsToHideAndShow[16 + i] = null;
							//view.setAlpha(1.0f);
						}
					}
				}
				//if (!finished && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				//	animationAnimatorShow.start();
			}
			if (finished) {
				animationState = ANIMATION_STATE_NONE;
				for (int i = 0; i < animationShowCount; i++) {
					final View view = animationViewsToHideAndShow[16 + i];
					if (view != null) {
						//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
							view.setAnimation(null);
						//else
						//	view.setAlpha(1.0f);
						if (abortAll && view.getVisibility() != View.VISIBLE) {
							animationHandleTag(view);
							view.setVisibility(View.VISIBLE);
						}
						animationViewsToHideAndShow[i] = null;
					}
				}
				animationShowCount = 0;
			}
		}
		if (finished) {
			if (animationFocusView != null) {
				if (animationFocusView.isInTouchMode())
					animationFocusView.requestFocus();
				animationFocusView = null;
			}
			if (animationFinishedObserver != null) {
				final Runnable observer = animationFinishedObserver;
				animationFinishedObserver = null;
				observer.run();
			}
		}
	}

	public static void animationAddViewToHide(View view) {
		if (animationViewsToHideAndShow == null)
			animationViewsToHideAndShow = new View[32];
		else if (animationHideCount >= 16 && view != null && view.getVisibility() != View.GONE)
			return;
		animationViewsToHideAndShow[animationHideCount] = view;
		animationHideCount++;
	}

	public static void animationAddViewToShow(View view) {
		if (animationViewsToHideAndShow == null)
			animationViewsToHideAndShow = new View[32];
		else if (animationShowCount >= 16 && view != null && view.getVisibility() != View.VISIBLE)
			return;
		animationViewsToHideAndShow[16 + animationShowCount] = view;
		animationShowCount++;
	}

	public static void animationSetViewToShowFirst(View view) {
		animationViewToShowFirst = view;
	}

	public static void animationReset() {
		animationFinished(true);
	}

	//DO NOT CALL THIS WITH forceSkipAnimation = false IF ANY VIEWS ARE NOT YET ATTACHED TO A WINDOW!!!
	public static void animationCommit(boolean forceSkipAnimation, View focusView) {
		if (!animationEnabled || forceSkipAnimation) {
			for (int i = 0; i < animationHideCount; i++) {
				final View view = animationViewsToHideAndShow[i];
				if (view != null) {
					view.setVisibility(View.GONE);
					animationViewsToHideAndShow[i] = null;
				}
			}
			for (int i = 0; i < animationShowCount; i++) {
				final View view = animationViewsToHideAndShow[16 + i];
				if (view != null) {
					animationHandleTag(view);
					view.setVisibility(View.VISIBLE);
					animationViewsToHideAndShow[16 + i] = null;
				}
			}
			if (focusView != null && focusView.isInTouchMode())
				focusView.requestFocus();
			animationState = ANIMATION_STATE_NONE;
			animationHideCount = 0;
			animationShowCount = 0;
			animationViewToShowFirst = null;
			if (animationFinishedObserver != null) {
				final Runnable observer = animationFinishedObserver;
				animationFinishedObserver = null;
				observer.run();
			}
		//} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		//	animationCommit11(focusView);
		} else {
			if (animationShowFirst == null) {
				(animationShowFirst = animationCreateAlpha(0.0f, 1.0f, 0)).setAnimationListener(Player.theUI);
				(animationHide = animationCreateAlpha(1.0f, 0.0f, 0)).setAnimationListener(Player.theUI);
				(animationShow = animationCreateAlpha(0.0f, 1.0f, 0)).setAnimationListener(Player.theUI);
			} else {
				animationShowFirst.reset();
				animationHide.reset();
				animationShow.reset();
			}
			if (animationHideCount > 0 || animationShowCount > 0 || animationViewToShowFirst != null) {
				animationState = ANIMATION_STATE_HIDING;
				animationFocusView = focusView;
				boolean ok = false;
				if (animationViewToShowFirst != null) {
					ok = true;
					animationViewToShowFirst.startAnimation(animationShowFirst);
				}
				for (int i = 0; i < animationHideCount; i++) {
					final View view = animationViewsToHideAndShow[i];
					if (view != null && view.getVisibility() != View.GONE) {
						ok = true;
						view.startAnimation(animationHide);
					}
				}
				if (!ok)
					animationFinished(false);
			} else {
				animationState = ANIMATION_STATE_NONE;
				animationHideCount = 0;
				animationShowCount = 0;
				animationViewToShowFirst = null;
			}
		}
	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if (((animation == animationShowFirst || animation == animationHide) && animationState == ANIMATION_STATE_HIDING) ||
			(animation == animationShow && animationState == ANIMATION_STATE_SHOWING))
			animationFinished(false);
	}

	@Override
	public void onAnimationRepeat(Animation animation) {
	}

	@Override
	public void onAnimationStart(Animation animation) {
	}

	/*@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void animationCommit11(View focusView) {
		final View referenceView = (focusView != null ? focusView : (animationViewToShowFirst != null ? animationViewToShowFirst : (animationViewsToHideAndShow[0] != null ? animationViewsToHideAndShow[0] : animationViewsToHideAndShow[16])));
		if (animationAnimatorShowFirst == null) {
			animationAnimatorShowFirst = new FastAnimator(new FastAnimator.Observer() {
				@Override
				public void onUpdate(FastAnimator animator, float value) {
					if (animationViewToShowFirst != null)
						animationViewToShowFirst.setAlpha(value);
				}

				@Override
				public void onEnd(FastAnimator animator) {
					if (animationState == ANIMATION_STATE_HIDING)
						animationFinished(false);
				}
			}, 0, referenceView);
			animationAnimatorHide = new FastAnimator(new FastAnimator.Observer() {
				@Override
				public void onUpdate(FastAnimator animator, float value) {
					value = 1.0f - value;
					for (int i = 0; i < animationHideCount; i++) {
						final View view = animationViewsToHideAndShow[i];
						if (view != null && view.getVisibility() != View.GONE)
							view.setAlpha(value);
					}
				}

				@Override
				public void onEnd(FastAnimator animator) {
					if (animationState == ANIMATION_STATE_HIDING)
						animationFinished(false);
				}
			}, 0, referenceView);
			animationAnimatorShow = new FastAnimator(new FastAnimator.Observer() {
				@Override
				public void onUpdate(FastAnimator animator, float value) {
					for (int i = 0; i < animationShowCount; i++) {
						final View view = animationViewsToHideAndShow[16 + i];
						if (view != null)
							view.setAlpha(value);
					}
				}

				@Override
				public void onEnd(FastAnimator animator) {
					if (animationState == ANIMATION_STATE_SHOWING)
						animationFinished(false);
				}
			}, 0, referenceView);
		} else {
			animationAnimatorShowFirst.prepareToRestart(referenceView);
			animationAnimatorHide.prepareToRestart(referenceView);
			animationAnimatorShow.prepareToRestart(referenceView);
		}
		if (animationHideCount > 0 || animationShowCount > 0 || animationViewToShowFirst != null) {
			animationState = ANIMATION_STATE_HIDING;
			animationFocusView = focusView;
			boolean ok = false;
			if (animationViewToShowFirst != null) {
				ok = true;
				animationAnimatorShowFirst.start();
			}
			for (int i = 0; i < animationHideCount; i++) {
				final View view = animationViewsToHideAndShow[i];
				if (view != null && view.getVisibility() != View.GONE) {
					ok = true;
					animationAnimatorHide.start();
					break;
				}
			}
			if (!ok)
				animationFinished(false);
		} else {
			animationState = ANIMATION_STATE_NONE;
			animationHideCount = 0;
			animationShowCount = 0;
			animationViewToShowFirst = null;
		}
	}*/
}
