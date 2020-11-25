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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewDebug.ExportedProperty;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;

import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class BgEditText extends EditText {
	private int state, colorNormal, colorFocused, extraTopPaddingForLastWidth, lastMeasuredWidth, textSize, textBox, textY, textMargin;
	private String contentDescription;
	private int[] contentDescriptionLineEndings;
	private boolean handleColorChangedL, handleColorChangedR, handleColorChangedC;

	public BgEditText(Context context) {
		super(context);
		init();
	}

	public BgEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public BgEditText(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		super.setBackgroundResource(0);
		super.setDrawingCacheEnabled(false);
		super.setGravity(Gravity.BOTTOM);
		super.setPadding(0, 0, 0, UI.thickDividerSize << 1);
		super.setTypeface(UI.defaultTypeface);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		super.setTextColor(UI.colorState_text_listitem_static);
		super.setHighlightColor(ColorUtils.blend(UI.color_dialog_detail_highlight, UI.color_list_original, 0.3f));
		setSmallContentDescription(false);
		setCursorColor(UI.color_dialog_detail_highlight);
		setColors(UI.color_dialog_detail, UI.color_dialog_detail_highlight);
		textMargin = (UI.isLargeScreen ? UI.controlMargin : UI.controlSmallMargin);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			super.setDefaultFocusHighlightEnabled(false);
	}

	public void setSmallContentDescription(boolean small) {
		if (small) {
			textSize = UI._14sp;
			textBox = UI._14spBox;
			textY = UI._14spYinBox;
		} else {
			textSize = UI._18sp;
			textBox = UI._18spBox;
			textY = UI._18spYinBox;
		}
	}

	@SuppressWarnings({"deprecation", "JavaReflectionMemberAccess"})
	private void setHandleColor(int color) {
		try {
			//https://developer.android.com/about/versions/10/non-sdk-q
			if (Build.VERSION.SDK_INT >= 29) {
				Drawable drawable = getTextSelectHandleLeft();
				if (drawable != null)
					drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
				drawable = getTextSelectHandle();
				if (drawable != null)
					drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
				drawable = getTextSelectHandleRight();
				if (drawable != null)
					drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
				return;
			}
			final Object editor;
			final Class<?> clazz;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				final Field fEditor = TextView.class.getDeclaredField("mEditor");
				fEditor.setAccessible(true);
				editor = fEditor.get(this);
				clazz = editor.getClass();
			} else {
				editor = this;
				clazz = TextView.class;
			}

			//http://stackoverflow.com/a/27307004/3569421
			if (!handleColorChangedL) {
				final Field fSelectHandleLeft = clazz.getDeclaredField("mSelectHandleLeft");
				fSelectHandleLeft.setAccessible(true);
				final Object l = fSelectHandleLeft.get(editor);
				if (l != null) {
					handleColorChangedL = true;
					((Drawable)l).setColorFilter(color, PorterDuff.Mode.SRC_IN);
				}
			}
			if (!handleColorChangedR) {
				final Field fSelectHandleRight = clazz.getDeclaredField("mSelectHandleRight");
				fSelectHandleRight.setAccessible(true);
				final Object r = fSelectHandleRight.get(editor);
				if (r != null) {
					handleColorChangedR = true;
					((Drawable)r).setColorFilter(color, PorterDuff.Mode.SRC_IN);
				}
			}
			if (!handleColorChangedC) {
				final Field fSelectHandleCenter = clazz.getDeclaredField("mSelectHandleCenter");
				fSelectHandleCenter.setAccessible(true);
				final Object c = fSelectHandleCenter.get(editor);
				if (c != null) {
					handleColorChangedC = true;
					((Drawable)c).setColorFilter(color, PorterDuff.Mode.SRC_IN);
				}
			}
		} catch (Throwable ex) {
			//just ignore
		}
	}

	@SuppressWarnings({"deprecation", "JavaReflectionMemberAccess"})
	private void setCursorColor(int color) {
		try {
			//https://developer.android.com/about/versions/10/non-sdk-q
			if (Build.VERSION.SDK_INT >= 29) {
				final Drawable drawable = getTextCursorDrawable();
				if (drawable != null)
					drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
				return;
			}
			//http://stackoverflow.com/a/26543290/3569421
			final Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
			fCursorDrawableRes.setAccessible(true);
			final int mCursorDrawableRes = fCursorDrawableRes.getInt(this);
			final Field fEditor = TextView.class.getDeclaredField("mEditor");
			fEditor.setAccessible(true);
			final Object editor = fEditor.get(this);
			final Class<?> clazz = editor.getClass();
			final Field fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");
			fCursorDrawable.setAccessible(true);
			final Drawable[] drawables = new Drawable[2];
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				drawables[0] = getContext().getDrawable(mCursorDrawableRes);
				drawables[1] = getContext().getDrawable(mCursorDrawableRes);
			} else {
				drawables[0] = getContext().getResources().getDrawable(mCursorDrawableRes);
				drawables[1] = getContext().getResources().getDrawable(mCursorDrawableRes);
			}
			drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
			drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
			fCursorDrawable.set(editor, drawables);

			setHandleColor(color);
		} catch (Throwable ex) {
			//just ignore
		}
	}

	public void setColors(int colorNormal, int colorFocused) {
		this.colorNormal = colorNormal;
		this.colorFocused = colorFocused;
	}

	@Override
	public int getPaddingLeft() {
		return 0;
	}

	@Override
	public int getPaddingTop() {
		return 0;
	}

	@Override
	public int getPaddingRight() {
		return 0;
	}

	@Override
	public int getPaddingBottom() {
		return 0;
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
	}

	@Override
	public void setContentDescription(CharSequence contentDescription) {
		this.contentDescription = ((contentDescription == null || contentDescription.length() == 0)? null : contentDescription.toString());
		extraTopPaddingForLastWidth = 0;
		lastMeasuredWidth = 0;
		contentDescriptionLineEndings = null;
		super.setContentDescription(this.contentDescription);
		requestLayout();
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
		return false;
	}

	@Override
	public boolean hasOverlappingRendering() {
		return false;
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = (UI.STATE_FOCUSED & UI.handleStateChanges(state, this));
	}

	private int countLines(int width) {
		final StaticLayout layout = new StaticLayout(contentDescription, UI.textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
		final int lines = layout.getLineCount();
		contentDescriptionLineEndings = new int[lines];
		for (int i = 0; i < lines; i++)
			contentDescriptionLineEndings[i] = layout.getLineEnd(i);
		return lines;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		final int width = getMeasuredWidth();
		int extraTopPadding;

		if (lastMeasuredWidth != width) {
			//add our bottom border + enough room for the description at the top

			extraTopPadding = 0;

			if (contentDescription != null) {
				UI.textPaint.setTextSize(textSize);
				if ((int)UI.textPaint.measureText(contentDescription) <= width) {
					contentDescriptionLineEndings = null;
					extraTopPadding += textBox + textMargin;
				} else {
					extraTopPadding += (textBox * countLines(width)) + textMargin;
				}
			}

			extraTopPaddingForLastWidth = extraTopPadding;
		} else {
			extraTopPadding = extraTopPaddingForLastWidth;
		}

		setMeasuredDimension(width, getMeasuredHeight() + extraTopPadding);
	}

	@Override
	public boolean performLongClick() {
		final boolean ret = super.performLongClick();
		setHandleColor(UI.color_dialog_detail_highlight);
		return ret;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		final boolean ret = super.onTouchEvent(event);
		setHandleColor(UI.color_dialog_detail_highlight);
		return ret;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		final Rect rect = UI.rect;
		getDrawingRect(rect);

		if (extraTopPaddingForLastWidth > 0) {
			rect.top += textY;
			if (contentDescriptionLineEndings != null) {
				UI.drawText(canvas, contentDescription, 0, contentDescriptionLineEndings[0], colorFocused, textSize, rect.left, rect.top);
				for (int i = 1; i < contentDescriptionLineEndings.length; i++) {
					rect.top += textBox;
					UI.drawText(canvas, contentDescription, contentDescriptionLineEndings[i - 1], contentDescriptionLineEndings[i], colorFocused, textSize, rect.left, rect.top);
				}
			} else if (contentDescription != null) {
				UI.drawText(canvas, contentDescription, colorFocused, textSize, rect.left, rect.top);
			}
		}

		rect.top = rect.bottom - (state == 0 ? UI.strokeSize : UI.thickDividerSize);
		UI.fillRect(rect, canvas, state == 0 ? colorNormal : colorFocused);

		super.onDraw(canvas);
	}

	@Override
	protected void onDetachedFromWindow() {
		contentDescription = null;
		super.onDetachedFromWindow();
	}
}
