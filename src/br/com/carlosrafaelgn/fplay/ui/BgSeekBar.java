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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class BgSeekBar extends View {
	public static interface OnBgSeekBarChangeListener {
		public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys);
		public boolean onStartTrackingTouch(BgSeekBar seekBar);
		public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled);
	}
	
	private String text;
	private int width, height, filledSize, value, max, textWidth, textX, textY, textColor, textBgY, textSize, textSizeIdx, emptySpaceColor, keyIncrement;
	private float delta;
	private boolean forceBlack, vertical, tracking, drawTextFirst;
	private int state;
	private OnBgSeekBarChangeListener listener;
	
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
		max = 100;
		textSizeIdx = 0;
		setTextSizeIndex(2);
		setEmptySpaceColor(UI.color_bg);
		super.setClickable(true);
		super.setFocusable(true);
	}
	
	public void setForceBlack(boolean forceBlack) {
		this.forceBlack = forceBlack;
		invalidate();
	}
	
	public OnBgSeekBarChangeListener getOnBgSeekBarChangeListener() {
		return listener;
	}
	
	public void setOnBgSeekBarChangeListener(OnBgSeekBarChangeListener listener) {
		this.listener = listener;
	}
	
	public int getEmptySpaceColor() {
		return emptySpaceColor;
	}
	
	public void setEmptySpaceColor(int emptySpaceColor) {
		this.emptySpaceColor = emptySpaceColor;
		invalidate();
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
		if (textSizeIdx == index)
			return;
		switch (index) {
		case 2:
			textSizeIdx = 2;
			textSize = UI._22sp;
			textBgY = (UI.defaultControlSize >> 1) - (UI._22spBox >> 1);
			textY = textBgY + UI._22spYinBox;
			break;
		case 1:
			textSizeIdx = 1;
			textSize = UI._18sp;
			textBgY = (UI.defaultControlSize >> 1) - (UI._18spBox >> 1);
			textY = textBgY + UI._18spYinBox;
			break;
		default:
			textSizeIdx = 0;
			textSize = UI._14sp;
			textBgY = (UI.defaultControlSize >> 1) - (UI._14spBox >> 1);
			textY = textBgY + UI._14spYinBox;
			break;
		}
		updateTextWidth();
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
		if (filledSize < textWidth && filledSize < ((s >> 1) - (UI._18spBox >> 1))) {
			drawTextFirst = false;
			textColor = UI.color_text;
			textX = s - textWidth;
			if (textX < filledSize + UI._18spBox)
				textX = filledSize + UI._18spBox;
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
		}
	}
	
	private void updateBar() {
		final int total = (vertical ? height : width) - (UI._18spBox);
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
		return false;//(filledSize >= (vertical ? height : width));
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
		return UI.defaultControlSize;
	}
	
	@Override
	public int getMinimumWidth() {
		return UI.defaultControlSize;
	}
	
	@Override
	protected int getSuggestedMinimumHeight() {
		return UI.defaultControlSize;
	}
	
	@Override
	public int getMinimumHeight() {
		return UI.defaultControlSize;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		 setMeasuredDimension(resolveSize(UI.defaultControlSize, widthMeasureSpec), resolveSize(UI.defaultControlSize, heightMeasureSpec));
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		width = w;
		height = h;
		updateBar();
	}
	
	private void trackTouchEvent(float position) {
		final float total = (vertical ? height : width) - (UI._18spBox);
		if (total <= 0)
			return;
		if (vertical)
			position = (float)height - position - delta;
		else
			position -= delta;
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
			final float p = (vertical ? event.getY() : event.getX());
			delta = ((p >= filledSize && p < (filledSize + UI._18spBox)) ? (p - filledSize) : (UI._18spBox >> 1));
			trackTouchEvent(p);
			break;
	    case MotionEvent.ACTION_MOVE:
	    	if (!tracking)
	    		return true;
			if (getParent() != null)
				getParent().requestDisallowInterceptTouchEvent(true);
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
		final int right = UI.rect.right;
		final int bottom = UI.rect.bottom;
		final int color = UI.getBorderColor(state);
		UI.rect.top = UI._8dp;
		UI.rect.bottom -= UI._8dp;
		//don't even ask.... I know! But what can I do?!? I HATE smoothed 1px lines...
		//and some devices just CAN'T DRAW 1PX STROKES!!!
		UI.drawRect(canvas, 0, color, UI.rect);
		UI.rect.left = UI._1dp;
		UI.rect.top += UI._1dp;
		UI.rect.right = UI._1dp + filledSize;
		UI.rect.bottom -= UI._1dp;
		UI.drawBgBorderless(canvas, state, UI.rect);
		if (drawTextFirst)
			UI.drawText(canvas, text, forceBlack ? UI.color_text_selected : textColor, textSize, textX, textY);
		UI.rect.left = UI.rect.right;
		UI.rect.right = right - UI._1dp;
		UI.drawRect(canvas, 0, emptySpaceColor, UI.rect);
		if (!drawTextFirst)
			UI.drawText(canvas, text, forceBlack ? UI.color_text_selected : textColor, textSize, textX, textY);
		UI.rect.left = filledSize;
		UI.rect.top = 0;
		UI.rect.right = filledSize + UI._18spBox;
		UI.rect.bottom = bottom;
		UI.drawRect(canvas, 0, color, UI.rect);
		UI.rect.left += UI._1dp;
		UI.rect.top += UI._1dp;
		UI.rect.right -= UI._1dp;
		UI.rect.bottom -= UI._1dp;
		UI.drawBgBorderless(canvas, state, UI.rect);
		TextIconDrawable.drawIcon(canvas, UI.ICON_GRIP, filledSize + (UI._18spBox >> 1) - (UI._22sp >> 1), (bottom >> 1) - (UI._22sp >> 1), UI._22sp, color);
		if (vertical)
			canvas.restore();
	}
}
