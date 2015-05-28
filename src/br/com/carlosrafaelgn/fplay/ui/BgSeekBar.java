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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.view.accessibility.AccessibilityEvent;

import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class BgSeekBar extends View {
	public static interface OnBgSeekBarChangeListener {
		public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys);
		public boolean onStartTrackingTouch(BgSeekBar seekBar);
		public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled);
	}
	
	public static interface OnBgSeekBarDrawListener {
		public void onSizeChanged(BgSeekBar seekBar, int width, int height);
		public void onDraw(Canvas canvas, BgSeekBar seekBar, Rect rect, int filledSize);
	}
	
	private String additionalContentDescription, text;
	private int width, height, filledSize, value, max, textWidth, textX, textY, textColor, textBgY, textSize, textSizeIdx, keyIncrement;
	private boolean insideList, vertical, tracking, drawTextFirst, sliderMode;
	private int state, thumbWidth, size;
	private OnBgSeekBarChangeListener listener;
	private OnBgSeekBarDrawListener drawListener;
	
	public BgSeekBar(Context context) {
		super(context);
		init();
	}
	
	public BgSeekBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public BgSeekBar(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	private void init() {
		state = UI.STATE_SELECTED;
		text = "";
		max = 100;
		sliderMode = false;
		thumbWidth = (UI.defaultControlContentsSize * 3) >> 2;
		setTextSizeIndex(1);
		super.setDrawingCacheEnabled(false);
		super.setClickable(true);
		super.setFocusableInTouchMode(!UI.hasTouch);
		super.setFocusable(true);
	}
	
	public void setSliderMode(boolean sliderMode) {
		this.sliderMode = sliderMode;
		this.thumbWidth = (sliderMode ? (UI.strokeSize << 1) : ((UI.defaultControlContentsSize * 3) >> 2));
		setTextSizeIndex(textSizeIdx);
		updateBar();
		invalidate();
	}
	
	public void setInsideList(boolean insideList) {
		this.insideList = insideList;
		updateTextX();
		invalidate();
	}
	
	public OnBgSeekBarChangeListener getOnBgSeekBarChangeListener() {
		return listener;
	}
	
	public void setOnBgSeekBarChangeListener(OnBgSeekBarChangeListener listener) {
		this.listener = listener;
	}
	
	public OnBgSeekBarDrawListener getOnBgSeekBarDrawListener() {
		return drawListener;
	}
	
	public void setOnBgSeekBarDrawListener(OnBgSeekBarDrawListener listener) {
		this.drawListener = listener;
	}
	
	public int getKeyIncrement() {
		return keyIncrement;
	}
	
	public void setKeyIncrement(int keyIncrement) {
		this.keyIncrement = keyIncrement;
	}
	
	public int getTextSizeIndex() {
		return textSizeIdx;
	}
	
	public void setTextSizeIndex(int index) {
		size = UI.defaultControlSize;
		switch (index) {
		case 2:
			textSizeIdx = 2;
			textSize = UI._22sp;
			textBgY = (UI.defaultControlSize - UI._22spBox) >> 1;
			textY = textBgY + UI._22spYinBox;
			break;
		case 1:
			textSizeIdx = 1;
			textSize = UI._18sp;
			textBgY = (UI.defaultControlSize - UI._18spBox) >> 1;
			textY = textBgY + UI._18spYinBox;
			break;
		default:
			textSizeIdx = 0;
			textSize = UI._14sp;
			textBgY = (UI.defaultControlSize - UI._14spBox) >> 1;
			textY = textBgY + UI._14spYinBox;
			break;
		}
		updateTextWidth();
	}
	
	public void setSize(int size, int textSize, int bgY, int y) {
		this.size = size;
		this.textSizeIdx = -1;
		this.textSize = textSize;
		this.textBgY = bgY;
		this.textY = y;
		updateTextWidth();
	}

	@Override
	public CharSequence getContentDescription() {
		String d = "";
		if (additionalContentDescription != null)
			d = additionalContentDescription;
		if (text != null) {
			if (d.length() > 0)
				d += ": ";
			d += text;
		}
		return d;
	}

	public void setAdditionalContentDescription(String additionalContentDescription) {
		this.additionalContentDescription = additionalContentDescription;
	}
	
	public String getText() {
		return text;
	}
	
	public void setText(String text) {
		this.text = text;
		updateTextWidth();
	}
	
	public void setText(int resId) {
		this.text = getContext().getText(resId).toString();
		updateTextWidth();
	}
	
	private void updateTextX() {
		final int s = (vertical ? height : width);
		if (filledSize < textWidth && filledSize < ((s - thumbWidth) >> 1)) {
			drawTextFirst = false;
			textColor = (insideList ? UI.color_text_listitem : UI.color_text);
			textX = s - textWidth;
			if (textX < filledSize + thumbWidth)
				textX = filledSize + thumbWidth;
		} else {
			drawTextFirst = true;
			textColor = UI.color_text_selected;
			textX = UI._4dp;
		}
	}
	
	private void updateTextWidth() {
		textWidth = UI._4dp + ((text == null || text.length() == 0) ? 0 : UI.measureText(text, textSize));
		updateTextX();
		invalidate();
	}
	
	public boolean isTracking() {
		return tracking;
	}
	
	public boolean isVertical() {
		return this.vertical;
	}
	
	public void setVertical(boolean vertical) {
		this.vertical = vertical;
		updateBar();
	}
	
	public int getMax() {
		return max;
	}
	
	public void setMax(int max) {
		if (max < 1)
			max = 1;
		if (this.max != max) {
			this.max = max;
			if (value > max) {
				value = max;
				if (listener != null)
					listener.onValueChanged(this, value, false, false);
			}
			updateBar();
		}
	}
	
	public int getValue() {
		return value;
	}
	
	public void setValue(int value) {
		setValue(value, false, false);
	}

	@Override
	public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
		if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
			event.setEventType(32768); //AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768
		return super.dispatchPopulateAccessibilityEvent(event);
	}

	private void setValue(int value, boolean fromUser, boolean usingKeys) {
		if (value < 0)
			value = 0;
		else if (value > max)
			value = max;
		if (this.value != value) {
			this.value = value;
			if (listener != null)
				listener.onValueChanged(this, value, fromUser, usingKeys);
			updateBar();
			if (fromUser && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
		}
	}
	
	private void updateBar() {
		final int total = (vertical ? height : width) - thumbWidth;
		//filledSize = ((total > 0) ? (int)((float)(total * value) / (float)max) : 0);
		filledSize = ((total > 0) ? (int)((float)(total * value) / (float)max) : 0);
		updateTextX();
		invalidate();
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
		super.setBackground(null);
	}

	@SuppressWarnings("deprecation")
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
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
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
	protected int getSuggestedMinimumWidth() {
		return size;
	}
	
	@Override
	public int getMinimumWidth() {
		return size;
	}
	
	@Override
	protected int getSuggestedMinimumHeight() {
		return size;
	}
	
	@Override
	public int getMinimumHeight() {
		return size;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		 setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		width = w;
		height = h;
		updateBar();
		if (drawListener != null)
			drawListener.onSizeChanged(this, w, h);
	}
	
	private void trackTouchEvent(float position) {
		final float total = (vertical ? height : width) - thumbWidth;
		if (total <= 0)
			return;
		if (vertical)
			position = (float)(height - (thumbWidth >> 1)) - position;
		else
			position -= (float)(thumbWidth >> 1);
		setValue((int)(((float)max * position) / total), true, false);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled())
			return false;
		
		//Based on: http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/AbsSeekBar.java#AbsSeekBar.onTouchEvent%28android.view.MotionEvent%29
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			tracking = false;
			if (listener != null) {
				if (!listener.onStartTrackingTouch(this))
					return true;
			}
			tracking = true;
			setPressed(true);
			trackTouchEvent(vertical ? event.getY() : event.getX());
			if (getParent() != null)
				getParent().requestDisallowInterceptTouchEvent(true);
			playSoundEffect(SoundEffectConstants.CLICK);
			break;
		case MotionEvent.ACTION_MOVE:
			if (!tracking)
				return true;
			trackTouchEvent(vertical ? event.getY() : event.getX());
			break;
		case MotionEvent.ACTION_UP:
			setPressed(false);
			if (tracking) {
				trackTouchEvent(vertical ? event.getY() : event.getX());
				if (listener != null)
					listener.onStopTrackingTouch(this, false);
				tracking = false;
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			setPressed(false);
			invalidate();
			if (tracking) {
				if (listener != null)
					listener.onStopTrackingTouch(this, true);
				tracking = false;
			}
			break;
		}
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		//Base on: http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/AbsSeekBar.java#AbsSeekBar.onKeyDown%28int%2Candroid.view.KeyEvent%29
		if (isEnabled()) {
			int value = getValue();
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (vertical || keyIncrement <= 0)
					break;
				if (value > 0) {
					if (value >= keyIncrement)
						value -= keyIncrement;
					else
						value = 0;
					setValue(value, true, true);
				}
				return true;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (!vertical || keyIncrement <= 0)
					break;
				if (value > 0) {
					if (value >= keyIncrement)
						value -= keyIncrement;
					else
						value = 0;
					setValue(value, true, true);
				}
				return true;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (vertical || keyIncrement <= 0)
					break;
				if (value < max) {
					if ((value + keyIncrement) <= max)
						value += keyIncrement;
					else
						value = max;
					setValue(value, true, true);
				}
				return true;
			case KeyEvent.KEYCODE_DPAD_UP:
				if (!vertical || keyIncrement <= 0)
					break;
				if (value < max) {
					if ((value + keyIncrement) <= max)
						value += keyIncrement;
					else
						value = max;
					setValue(value, true, true);
				}
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		if (vertical) {
			canvas.save();
			canvas.translate(0, height);
			canvas.rotate(-90);
			final int tmp = UI.rect.right;
			UI.rect.right = UI.rect.bottom;
			UI.rect.bottom = tmp;
		}
		if (drawListener != null) {
			drawListener.onDraw(canvas, this, UI.rect, filledSize);
		} else {
			final int right = UI.rect.right;
			final int bottom = UI.rect.bottom;
			final int color = UI.getBorderColor(state);
			UI.rect.top = UI._8dp;
			UI.rect.bottom -= UI._8dp;
			UI.strokeRect(canvas, color, UI.strokeSize);
			if (UI.hasBorders) {
				UI.rect.left = UI.strokeSize;
				UI.rect.top += UI.strokeSize;
				UI.rect.bottom -= UI.strokeSize;
				UI.rect.right = UI.strokeSize + filledSize;
			} else {
				UI.rect.right = filledSize;
			}
			UI.drawBgBorderless(canvas, state, false);
			if (drawTextFirst)
				UI.drawText(canvas, text, textColor, textSize, textX, textY);
			UI.rect.left = UI.rect.right;
			UI.rect.right = right - UI.strokeSize;
			if (!drawTextFirst)
				UI.drawText(canvas, text, textColor, textSize, textX, textY);
			if (sliderMode) {
				TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDERTOP, filledSize + (thumbWidth >> 1) - UI._8dp, 0, UI._16dp, color);
				TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDERBOTTOM, filledSize + (thumbWidth >> 1) - UI._8dp, bottom - UI._8dp, UI._16dp, color);
			} else {
				if (UI.hasBorders) {
					UI.rect.top = 0;
					UI.rect.bottom = bottom;
					UI.rect.left = filledSize;
					UI.rect.right = filledSize + thumbWidth;
					UI.fillRect(canvas, color);
					UI.rect.left += UI.strokeSize;
					UI.rect.top += UI.strokeSize;
					UI.rect.right -= UI.strokeSize;
					UI.rect.bottom -= UI.strokeSize;
				} else {
					UI.rect.left = filledSize - UI.strokeSize;
					UI.rect.right = filledSize;
					UI.fillRect(canvas, color);
					UI.rect.top = 0;
					UI.rect.bottom = bottom;
					UI.rect.left = filledSize;
					UI.rect.right = filledSize + thumbWidth;
				}
				UI.drawBgBorderless(canvas, state, false);
				TextIconDrawable.drawIcon(canvas, UI.ICON_GRIP, filledSize, (bottom - thumbWidth) >> 1, thumbWidth, color);
			}
		}
		if (vertical)
			canvas.restore();
	}

	@Override
	protected void onDetachedFromWindow() {
		additionalContentDescription = null;
		text = null;
		listener = null;
		drawListener = null;
		super.onDetachedFromWindow();
	}
}
