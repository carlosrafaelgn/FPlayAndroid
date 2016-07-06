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
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.view.accessibility.AccessibilityEvent;

import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class BgSeekBar extends View {
	public interface OnBgSeekBarChangeListener {
		void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys);
		boolean onStartTrackingTouch(BgSeekBar seekBar);
		void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled);
	}
	
	public interface OnBgSeekBarDrawListener {
		void onSizeChanged(BgSeekBar seekBar, int width, int height);
		void onDraw(Canvas canvas, BgSeekBar seekBar, Rect rect, int filledSize);
	}
	
	private String additionalContentDescription, text, icon;
	private boolean insideList, vertical, sliderMode;
	private int state, thumbWidth, width, height, filledSize, value, max, textWidth, textX, textY, textColor,
		textSize, keyIncrement, size, thumbMargin, trackingOffset, secondaryBgColor, secondaryBgColorBlended;
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
	
	public BgSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	private void init() {
		state = UI.STATE_SELECTED;
		text = "";
		max = 100;
		sliderMode = false;
		thumbWidth = (UI.defaultControlContentsSize * 3) >> 2;
		trackingOffset = Integer.MIN_VALUE;
		setTextSizeIndex(1);
		setInsideList(false);
		super.setDrawingCacheEnabled(false);
		super.setClickable(true);
		super.setFocusableInTouchMode(!UI.hasTouch);
		super.setFocusable(true);
	}

	private void updateSecondaryBgColorBlended() {
		final int state = (insideList ? (this.state ^ UI.STATE_FOCUSED) : this.state);
		secondaryBgColorBlended = ColorUtils.blend(((state & UI.STATE_PRESSED) != 0) ?
			(((state & UI.STATE_FOCUSED) != 0) ? UI.color_focused_pressed : UI.color_selected_pressed) :
				(((state & UI.STATE_FOCUSED) != 0) ? UI.color_focused : UI.color_selected),
					secondaryBgColor, 0.35f);
	}

	public void setSliderMode(boolean sliderMode) {
		this.sliderMode = sliderMode;
		this.thumbWidth = (sliderMode ? (UI.strokeSize << 1) : ((UI.defaultControlContentsSize * 3) >> 2));
		updateBar();
		invalidate();
	}
	
	public void setInsideList(boolean insideList) {
		this.insideList = insideList;
		secondaryBgColor = (insideList ? UI.color_list_bg : UI.color_window);
		updateSecondaryBgColorBlended();
		updateTextX();
		invalidate();
	}

	public void setOnBgSeekBarChangeListener(OnBgSeekBarChangeListener listener) {
		this.listener = listener;
	}

	public void setOnBgSeekBarDrawListener(OnBgSeekBarDrawListener listener) {
		this.drawListener = listener;
	}

	public void setKeyIncrement(int keyIncrement) {
		this.keyIncrement = keyIncrement;
	}

	public void setTextSizeIndex(int index) {
		size = UI.defaultControlSize;
		thumbMargin = UI.controlSmallMargin;
		switch (index) {
		case 2:
			textSize = UI._22sp;
			textY = ((UI.defaultControlSize - UI._22spBox) >> 1) + UI._22spYinBox;
			break;
		case 1:
			textSize = UI._18sp;
			textY = ((UI.defaultControlSize - UI._18spBox) >> 1) + UI._18spYinBox;
			break;
		default:
			textSize = UI._14sp;
			textY = ((UI.defaultControlSize - UI._14spBox) >> 1) + UI._14spYinBox;
			break;
		}
		updateTextWidth();
	}
	
	public void setSize(int size, boolean furtherDecreaseTextSize) {
		this.size = size;
		thumbMargin = //((size > (UI.defaultControlSize - UI._4dp)) ? UI.controlMargin :
						((size >= (UI.defaultControlSize >> 1)) ? UI.controlSmallMargin :
							0);//);
		textSize = size - (thumbMargin << 1) - (UI.strokeSize << 1) - (UI.controlXtraSmallMargin << 1); //(UI.controlSmallMargin << 1) is just to a decent margin
		if (furtherDecreaseTextSize)
			textSize >>= 1;
		/*if (textSize >= UI._22sp && size > (UI.defaultControlSize - UI._4dp)) {
			textSize = UI._22sp;
			textY = ((size - UI._22spBox) >> 1) + UI._22spYinBox;
		} else*/ if (textSize >= UI._18sp) {
			textSize = UI._18sp;
			textY = ((size - UI._18spBox) >> 1) + UI._18spYinBox;
		} else if (textSize >= UI._14sp) {
			textSize = UI._14sp;
			textY = ((size - UI._14spBox) >> 1) + UI._14spYinBox;
		} else {
			if (textSize < UI._4dp)
				textSize = UI._4dp;
			UI.textPaint.setTextSize(textSize);
			final Paint.FontMetrics fm = UI.textPaint.getFontMetrics();
			final int box = (int)(fm.descent - fm.ascent + 0.5f);
			final int yInBox = box - (int)(fm.descent);
			textY = ((size - box) >> 1) + yInBox;
		}
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

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = ((icon != null && icon.length() == 0) ? null : icon);
		updateTextWidth();
	}

	public String getText() {
		return text;
	}
	
	public void setText(String text) {
		this.text = ((text == null) ? "" : text);
		updateTextWidth();
	}
	
	public void setText(int resId) {
		final CharSequence text = getContext().getText(resId);
		this.text = ((text == null) ? "" : text.toString());
		updateTextWidth();
	}
	
	private void updateTextX() {
		final int s = (vertical ? height : width);
		if (filledSize < textWidth) {// && filledSize < ((s - thumbWidth) >> 1)) {
			textColor = (insideList ? UI.color_text_listitem : UI.color_text);
			textX = s - textWidth;
			if (textX < filledSize + thumbWidth)
				textX = filledSize + thumbWidth + UI.controlSmallMargin;
		} else {
			textColor = UI.color_text_selected;
			textX = UI.controlSmallMargin;
		}
	}
	
	private void updateTextWidth() {
		textWidth = UI.controlSmallMargin +
			((icon != null) ? (textSize + UI.controlSmallMargin) : 0) +
				((text == null || text.length() == 0) ? 0 : UI.measureText(text, textSize));
		updateTextX();
		invalidate();
	}
	
	public boolean isTracking() {
		return (trackingOffset != Integer.MIN_VALUE);
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
	public boolean hasOverlappingRendering() {
		return true;
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
		updateSecondaryBgColorBlended();
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
	
	private void trackTouchEvent(int position) {
		final int total = (vertical ? height : width) - 1 - thumbWidth;
		if (total <= 0)
			return;
		if (vertical)
			position = (height - 1 - (thumbWidth >> 1)) - position;
		else
			position -= (thumbWidth >> 1);
		setValue((int)((((float)max * (float)position) / (float)total) + 0.5f), true, false);
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (!isEnabled())
			return false;
		
		//Based on: http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/AbsSeekBar.java#AbsSeekBar.onTouchEvent%28android.view.MotionEvent%29
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			trackingOffset = Integer.MIN_VALUE;
			if (listener != null) {
				if (!listener.onStartTrackingTouch(this))
					return true;
			}
			//compute an offset (delta between the pointer and the center of the thumb),
			//because trackTouchEvent() always uses position as the center of the thumb
			int pos;
			if (vertical) {
				pos = height - 1 - (int)event.getY();
				//if the user clicks/touches outside the thumb, let trackTouchEvent() center it
				trackingOffset = ((sliderMode || pos < filledSize || pos >= (filledSize + thumbWidth - UI.strokeSize)) ?
					0 :
						(pos - (filledSize + (thumbWidth >> 1))));
				pos = height - 1 - pos;
			} else {
				pos = (int)event.getX();
				//if the user clicks/touches outside the thumb, let trackTouchEvent() center it
				trackingOffset = ((sliderMode || pos < filledSize || pos >= (filledSize + thumbWidth - UI.strokeSize)) ?
					0 :
						((filledSize + (thumbWidth >> 1)) - pos));
			}
			setPressed(true);
			trackTouchEvent(pos + trackingOffset);
			if (getParent() != null)
				getParent().requestDisallowInterceptTouchEvent(true);
			playSoundEffect(SoundEffectConstants.CLICK);
			break;
		case MotionEvent.ACTION_MOVE:
			if (trackingOffset != Integer.MIN_VALUE)
				trackTouchEvent((int)(vertical ? event.getY() : event.getX()) + trackingOffset);
			break;
		case MotionEvent.ACTION_UP:
			setPressed(false);
			if (trackingOffset != Integer.MIN_VALUE) {
				trackTouchEvent((int)(vertical ? event.getY() : event.getX()) + trackingOffset);
				if (listener != null)
					listener.onStopTrackingTouch(this, false);
				trackingOffset = Integer.MIN_VALUE;
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			setPressed(false);
			invalidate();
			if (trackingOffset != Integer.MIN_VALUE) {
				if (listener != null)
					listener.onStopTrackingTouch(this, true);
				trackingOffset = Integer.MIN_VALUE;
			}
			break;
		}
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
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

	@SuppressWarnings("SuspiciousNameCombination")
	@Override
	protected void onDraw(Canvas canvas) {
		if (text == null)
			return;
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
			final int state = (insideList ? (this.state ^ UI.STATE_FOCUSED) : this.state);
			final int right = UI.rect.right;
			final int bottom = UI.rect.bottom;
			final int color = UI.getBorderColor(state);
			UI.rect.top = thumbMargin;
			UI.rect.bottom -= thumbMargin;
			if (UI.hasBorders) {
				UI.strokeRect(canvas, color, UI.strokeSize);
				UI.rect.top += UI.strokeSize;
				UI.rect.bottom -= UI.strokeSize;
				UI.rect.left = filledSize + thumbWidth;
				UI.rect.right = right - UI.strokeSize;
				UI.fillRect(canvas, secondaryBgColorBlended);
				UI.rect.left = UI.strokeSize;
			} else {
				UI.rect.left = filledSize + thumbWidth;
				UI.rect.right = right;
				UI.fillRect(canvas, secondaryBgColorBlended);
				UI.rect.left = 0;
			}
			UI.rect.right = filledSize;
			UI.drawBgBorderless(canvas, state);
			if (icon == null) {
				UI.drawText(canvas, text, textColor, textSize, textX, textY);
			} else {
				TextIconDrawable.drawIcon(canvas, icon, textX, (UI.rect.bottom + UI.rect.top - textSize) >> 1, textSize, textColor);
				UI.drawText(canvas, text, textColor, textSize, textX + textSize + UI.controlSmallMargin, textY);
			}
			if (sliderMode) {
				TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDERTOP, filledSize + (thumbWidth >> 1) - UI.controlMargin, 0, UI.controlLargeMargin, color);
				TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDERBOTTOM, filledSize + (thumbWidth >> 1) - UI.controlMargin, bottom - UI.controlMargin, UI.controlLargeMargin, color);
			} else {
				if (UI.hasBorders) {
					UI.rect.top = 0;
					UI.rect.bottom = bottom;
					UI.rect.left = filledSize;
					UI.rect.right = filledSize + thumbWidth;
					UI.strokeRect(canvas, color, UI.strokeSize);
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
				UI.drawBgBorderless(canvas, state);
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
