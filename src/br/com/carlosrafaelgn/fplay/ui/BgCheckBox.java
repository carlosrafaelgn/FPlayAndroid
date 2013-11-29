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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewDebug.ExportedProperty;
import android.widget.CheckBox;

public final class BgCheckBox extends CheckBox {
	private int state, btnWidth, paddingL, paddingR;
	
	public BgCheckBox(Context context) {
		super(context);
		init();
	}
	
	public BgCheckBox(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public BgCheckBox(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	private void init() {
		super.setBackgroundResource(0);
		super.setTextColor(UI.colorState_text_normal);
		super.setTypeface(UI.defaultTypeface);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		super.setMinimumHeight(UI.defaultControlSize);
		super.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
		setPadding(UI._2sp, 0, UI._8sp, 0);
	}
	
	public void setForceBlack(boolean forceBlack) {
		super.setTextColor(forceBlack ? UI.colorState_text_sel : UI.colorState_text_normal);
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
	public void setButtonDrawable(Drawable d) {
		super.setButtonDrawable(d);
		//workaround based on:
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/CompoundButton.java#CompoundButton.setButtonDrawable%28android.graphics.drawable.Drawable%29
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/widget/CompoundButton.java#CompoundButton.setButtonDrawable%28android.graphics.drawable.Drawable%29
		//
		//the workaround is necessary as CheckBoxes behave differently on Jelly Bean (4.2)
		//http://stackoverflow.com/questions/4037795/android-spacing-between-checkbox-and-text
		this.btnWidth = ((d != null) ? d.getIntrinsicWidth() : 0);
	}
	
	@Override
	public int getPaddingLeft() {
		return paddingL;
	}
	
	@Override
	public int getPaddingRight() {
		return paddingR;
	}
	
	@Override
	public int getCompoundPaddingLeft() {
		return btnWidth + paddingL;
	}
	
	@Override
	public int getCompoundPaddingRight() {
		return paddingR;
	}
	
	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		super.setPadding(0, top, 0, bottom);
		paddingL = left;
		paddingR = right;
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		UI.drawBg(canvas, state, UI.rect, true);
		super.onDraw(canvas);
	}
}
