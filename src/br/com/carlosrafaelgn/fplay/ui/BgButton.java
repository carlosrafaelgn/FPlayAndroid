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

import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewDebug.ExportedProperty;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;

public final class BgButton extends Button {
	public static interface OnPressingChangeListener {
		public void onPressingChanged(BgButton button, boolean pressed);
	}
	
	private int state;
	private boolean checkable, checked, stretchable;
	private String iconChecked, iconUnchecked;
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
	
	public BgButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	private void init() {
		super.setBackgroundResource(0);
		super.setTextColor(UI.colorState_text_reactive);
		super.setTypeface(UI.defaultTypeface);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		super.setGravity(Gravity.CENTER);
		super.setPadding(UI._8sp, UI._8sp, UI._8sp, UI._8sp);
		super.setFocusableInTouchMode(false);
		super.setFocusable(true);
	}
	
	public void setDefaultHeight() {
		LayoutParams p = getLayoutParams();
		if (p == null)
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.height = UI.defaultControlSize;
		super.setLayoutParams(p);
		super.setPadding(UI._8sp, 0, UI._8sp, 0);
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
	
	public void setIcon(String iconChecked, String iconUnchecked, boolean checked, boolean small, boolean changeWidth, boolean changeHeight) {
		setIcon(checked ? iconChecked : iconUnchecked, changeWidth, changeHeight);
		if (small)
			super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._IconBox);
		this.iconChecked = iconChecked;
		this.iconUnchecked = iconUnchecked;
		this.checkable = true;
		this.checked = checked;
	}
	
	public boolean isBehavingAsCheckBox() {
		return (checkBox != null);
	}
	
	public void setBehavingAsCheckBox(boolean behavingAsCheckBox) {
		if (!behavingAsCheckBox) {
			super.setCompoundDrawables(null, null, null, null);
			checkBox = null;
			checkable = false;
		} else {
			checkable = true;
			checkBox = new TextIconDrawable(checked ? UI.ICON_OPTCHK : UI.ICON_OPTUNCHK, getTextColors().getDefaultColor());
			super.setCompoundDrawables(checkBox, null, null, null);
		}
	}
	
	public OnPressingChangeListener getOnPressingChangeListener() {
		return pressingChangeListener;
	}
	
	public void setOnPressingChangeListener(OnPressingChangeListener pressingChangeListener) {
		this.pressingChangeListener = pressingChangeListener;
	}
	
	private void fixTextSize(int w, int h) {
		if (w <= 0 || h <= 0)
			return;
		w -= (getPaddingLeft() + getPaddingRight());
		h -= (getPaddingTop() + getPaddingBottom());
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, (w < h) ? w : h);
	}
	
	public boolean isIconStretchable() {
		return stretchable;
	}
	
	public void setIconStretchable(boolean stretchable) {
		this.stretchable = stretchable;
		if (stretchable)
			fixTextSize(getWidth(), getHeight());
	}
	
	public boolean isCheckable() {
		return checkable;
	}
	
	public void setCheckable(boolean checkable) {
		if (!checkable)
			setChecked(false);
		this.checkable = checkable;
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
				checkBox.setIcon(checked ? UI.ICON_OPTCHK : UI.ICON_OPTUNCHK);
				invalidate();
			} else {
				super.setText(checked ? iconChecked : iconUnchecked);
			}
		}
	}
	
	@Override
	public boolean performClick() {
		if (checkable)
			toggle();
		return super.performClick();
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
		return (state != 0);
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		final int old = (state & UI.STATE_PRESSED);
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
		if (pressingChangeListener != null && (state & UI.STATE_PRESSED) != old)
			pressingChangeListener.onPressingChanged(this, old == 0);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (stretchable)
			fixTextSize(w, h);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		UI.drawBg(canvas, state, true, false);
		super.onDraw(canvas);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		iconChecked = null;
		iconUnchecked = null;
		checkBox = null;
		pressingChangeListener = null;
		super.setCompoundDrawables(null, null, null, null);
		super.onDetachedFromWindow();
	}
}
