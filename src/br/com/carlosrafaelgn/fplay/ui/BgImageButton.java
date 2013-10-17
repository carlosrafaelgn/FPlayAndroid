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
import android.view.ViewDebug.ExportedProperty;
import android.widget.ImageButton;

public final class BgImageButton extends ImageButton {
	private boolean forceBlack;
	private Drawable white, black, whiteChecked, blackChecked;
	private int state;
	private boolean checkable, checked;
	
	public BgImageButton(Context context) {
		super(context);
		init();
	}
	
	public BgImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public BgImageButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	private void init() {
		super.setBackgroundResource(0);
		super.setScaleType(ScaleType.CENTER);
		super.setPadding(UI._8sp, UI._8sp, UI._8sp, UI._8sp);
	}
	
	public void setForceBlack(boolean forceBlack) {
		this.forceBlack = forceBlack;
		super.setImageDrawable((forceBlack || (state != 0)) ? (checked ? blackChecked : black) : (checked ? whiteChecked : white));
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
			super.setImageDrawable((forceBlack || (state != 0)) ? (checked ? blackChecked : black) : (checked ? whiteChecked : white));
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
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
		super.setImageDrawable((forceBlack || (state != 0)) ? (checked ? blackChecked : black) : (checked ? whiteChecked : white));
	}
	
	public void setImageDrawables(Drawable white, Drawable black) {
		this.white = white;
		this.black = black;
		this.whiteChecked = null;
		this.blackChecked = null;
		super.setImageDrawable((forceBlack || (state != 0)) ? black : white);
	}
	
	public void setImageDrawables(Drawable white, Drawable black, Drawable whiteChecked, Drawable blackChecked) {
		this.white = white;
		this.black = black;
		this.whiteChecked = whiteChecked;
		this.blackChecked = blackChecked;
		super.setImageDrawable((forceBlack || (state != 0)) ? (checked ? blackChecked : black) : (checked ? whiteChecked : white));
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		UI.drawBg(canvas, state, UI.rect, true);
		super.onDraw(canvas);
	}
}
