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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.ViewDebug.ExportedProperty;
import android.view.ViewGroup.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class BgButton extends Button {
	public interface OnPressingChangeListener {
		void onPressingChanged(BgButton button, boolean pressed);
	}

	private static String selected, unselected;

	private int state;
	private boolean checkable, checked, stretchable, hideBorders, forceDescription, ignoreNextClick;
	private String iconChecked, iconUnchecked, originalDescription;
	private CharSequence descriptionChecked, descriptionUnchecked;
	private TextIconDrawable checkBox;
	private OnPressingChangeListener pressingChangeListener;
	
	public BgButton(Context context) {
		super(context);
		init();
	}
	
	public BgButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public BgButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	private void init() {
		super.setBackgroundResource(0);
		super.setDrawingCacheEnabled(false);
		super.setTextColor(UI.colorState_text_reactive);
		super.setTypeface(UI.defaultTypeface);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		super.setGravity(Gravity.CENTER);
		super.setPadding(UI.controlMargin, UI.controlMargin, UI.controlMargin, UI.controlMargin);
		super.setFocusableInTouchMode(!UI.hasTouch);
		super.setFocusable(true);
		super.setMinimumWidth(UI.defaultControlSize);
		super.setMinimumHeight(UI.defaultControlSize);
		super.setLongClickable(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			super.setDefaultFocusHighlightEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			super.setPointerIcon(PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND));
		//Seriously?! Feature?!?!? :P
		//http://stackoverflow.com/questions/26958909/why-is-my-button-text-coerced-to-all-caps-on-lollipop
		super.setTransformationMethod(null);
		if (selected == null)
			selected = getContext().getText(R.string.selected).toString();
		if (unselected == null)
			unselected = getContext().getText(R.string.unselected).toString();
	}
	
	public void setHideBorders(boolean hideBorders) {
		this.hideBorders = hideBorders;
	}

	public void setDefaultHeight() {
		LayoutParams p = getLayoutParams();
		if (p == null)
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.width = LayoutParams.WRAP_CONTENT;
		p.height = UI.defaultControlSize;
		super.setLayoutParams(p);
		super.setPadding(UI.controlMargin, 0, UI.controlMargin, 0);
	}

	public void setDefaultHeightAndLargeMinimumWidth() {
		LayoutParams p = getLayoutParams();
		if (p == null)
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.height = UI.defaultControlSize;
		super.setMinimumWidth((UI.defaultControlSize * 3) >> 1);
		super.setLayoutParams(p);
		super.setPadding(UI.controlMargin, 0, UI.controlMargin, 0);
	}

	public void setIconNoChanges(String icon) {
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.defaultControlContentsSize);
		super.setTypeface(UI.iconsTypeface);
		super.setText(icon);
		super.setPadding(0, 0, 0, 0);
	}
	
	public void setIcon(String icon, boolean changeWidth, boolean changeHeight) {
		LayoutParams p = getLayoutParams();
		if (p == null)
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		if (changeWidth)
			p.width = UI.defaultControlSize;
		if (changeHeight)
			p.height = UI.defaultControlSize;
		super.setLayoutParams(p);
		setIconNoChanges(icon);
	}
	
	public void setIcon(String icon) {
		setIcon(icon, true, true);
	}

	/*public void removeIcon() {
		super.setTypeface(UI.defaultTypeface);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		setDefaultHeight();
	}*/

	public void formatAsCheckBox(String iconChecked, String iconUnchecked, boolean checked, boolean changeWidth, boolean changeHeight) {
		setIcon(checked ? iconChecked : iconUnchecked, changeWidth, changeHeight);
		this.iconChecked = iconChecked;
		this.iconUnchecked = iconUnchecked;
		this.checkable = true;
		this.checked = checked;
		updateContentDescription();
	}

	public void formatAsChildCheckBox(boolean checked, boolean changeWidth, boolean changeHeight) {
		formatAsCheckBox(UI.ICON_OPTCHK24, UI.ICON_OPTUNCHK24, checked, changeWidth, changeHeight);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.defaultCheckIconSize);
	}

	public void formatAsLabelledCheckBox() {
		checkable = true;
		checkBox = new TextIconDrawable(checked ? UI.ICON_OPTCHK24 : UI.ICON_OPTUNCHK24, getTextColors().getDefaultColor(), UI.defaultCheckIconSize);
		super.setCompoundDrawables(checkBox, null, null, null);
		updateContentDescription();
	}

	public void updateContentDescription() {
		if (!checkable)
			return;
		if (checked) {
			if (descriptionChecked != null) {
				setContentDescription(descriptionChecked);
				return;
			}
		} else if (descriptionUnchecked != null) {
			setContentDescription(descriptionUnchecked);
			return;
		}
		CharSequence c = super.getContentDescription();
		if (c == null)
			c = getText();
		if (originalDescription == null)
			originalDescription = c.toString();
		setContentDescription(originalDescription + UI.collon() + (checked ? selected : unselected));
	}

	public void setContentDescription(CharSequence descriptionChecked, CharSequence descriptionUnchecked) {
		this.descriptionChecked = descriptionChecked;
		this.descriptionUnchecked = descriptionUnchecked;
		updateContentDescription();
	}

	public void setOnPressingChangeListener(OnPressingChangeListener pressingChangeListener) {
		this.pressingChangeListener = pressingChangeListener;
		setLongClickable(false);
	}
	
	private void fixStretchableTextSize(int w, int h) {
		if (w <= 0 || h <= 0)
			return;
		w -= (getPaddingLeft() + getPaddingRight());
		h -= (getPaddingTop() + getPaddingBottom());
		if (w > h)
			w = h;
		else
			h = w;
		w = ((w * 3) >> 2); //75% of the smallest dimension
		if (w < UI.defaultControlContentsSize) {
			w = ((h >= UI.defaultControlContentsSize) ? UI.defaultControlContentsSize : (UI.defaultControlContentsSize >> 1));
		} else {
			//try to round up to a multiple of 16
			w = ((w + 8) & ~15);
			if (w > (UI.defaultControlContentsSize << 1))
				w = (UI.defaultControlContentsSize << 1);
		}
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, w);
	}

	public void setIconStretchable(boolean stretchable) {
		this.stretchable = stretchable;
		if (stretchable)
			fixStretchableTextSize(getWidth(), getHeight());
	}

	public void toggle() {
		setChecked(!checked);
	}
	
	public boolean isChecked() {
		return checked;
	}
	
	public void setChecked(boolean checked) {
		checkable = true;
		if (this.checked != checked) {
			this.checked = checked;
			if (checkBox != null) {
				checkBox.setIcon(checked ? UI.ICON_OPTCHK24 : UI.ICON_OPTUNCHK24);
				invalidate();
			} else {
				super.setText(checked ? iconChecked : iconUnchecked);
			}
			updateContentDescription();
		}
	}

	@SuppressLint("WrongConstant")
	@Override
	public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
		if (forceDescription) {
			forceDescription = false;
			event.setEventType(32768); //AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768
		}
		return super.dispatchPopulateAccessibilityEvent(event);
	}

	@Override
	public boolean performClick() {
		if (ignoreNextClick) {
			ignoreNextClick = false;
			return true;
		}
		if (checkable) {
			toggle();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				forceDescription = true;
		}
		return super.performClick();
	}

	@Override
	public boolean performLongClick() {
		if (getTypeface() == UI.iconsTypeface) {
			final CharSequence description = getContentDescription();
			if (description != null && description.length() > 0) {
				ignoreNextClick = true;
				UI.toast(description, this);
			}
		}
		return super.performLongClick();
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
		return (state != 0);
	}

	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_NUMPAD_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
			keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
			event = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), 232, event.getFlags(), event.getSource());
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_NUMPAD_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
			keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
			event = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), 232, event.getFlags(), event.getSource());
			break;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_NUMPAD_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
			keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
			event = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), 232, event.getFlags(), event.getSource());
			break;
		}
		return super.onKeyUp(keyCode, event);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_UP:
			if (ignoreNextClick) {
				if (event.getX() < 0.0f ||
					event.getX() >= (float)getWidth() ||
					event.getY() < 0.0f ||
					event.getY() >= (float)getHeight())
					ignoreNextClick = false;
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			ignoreNextClick = false;
			break;
		}
		return super.onTouchEvent(event);
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		final int old = (state & UI.STATE_PRESSED);
		//Apparently, using isPressed(), isFocused() and isHovered() produces results different from
		//the expected ones in a few keyboard-enabled devices and chromebooks
		//state = UI.handleStateChanges(state, isPressed(), isFocused() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && isHovered()), this);
		state = UI.handleStateChanges(state, this);
		if (pressingChangeListener != null && (state & UI.STATE_PRESSED) != old)
			pressingChangeListener.onPressingChanged(this, old == 0);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (stretchable)
			fixStretchableTextSize(w, h);
	}
	
	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		getDrawingRect(UI.rect);
		if (hideBorders)
			UI.drawBgBorderless(UI.rect, canvas, state);
		else
			UI.drawBg(UI.rect, canvas, state);
		super.onDraw(canvas);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		iconChecked = null;
		iconUnchecked = null;
		originalDescription = null;
		descriptionChecked = null;
		descriptionUnchecked = null;
		checkBox = null;
		pressingChangeListener = null;
		super.setCompoundDrawables(null, null, null, null);
		super.onDetachedFromWindow();
	}
}
