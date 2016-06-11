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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewDebug.ExportedProperty;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;

public final class BgEditText extends EditText {
	private int state, paddingLeft, paddingRight, colorNormal, colorFocused;
	private String contentDescription;

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
	}

	@SuppressWarnings("deprecation")
	public void setCursorColor(int color) {
		try {
			//http://stackoverflow.com/a/26543290/3569421
			final Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
			fCursorDrawableRes.setAccessible(true);
			final int mCursorDrawableRes = fCursorDrawableRes.getInt(this);
			Field fEditor = TextView.class.getDeclaredField("mEditor");
			fEditor.setAccessible(true);
			final Object editor = fEditor.get(this);
			final Class<?> clazz = editor.getClass();
			final Field fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");
			fCursorDrawable.setAccessible(true);
			final Drawable[] drawables = new Drawable[2];
			drawables[0] = getContext().getResources().getDrawable(mCursorDrawableRes);
			drawables[1] = getContext().getResources().getDrawable(mCursorDrawableRes);
			drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
			drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
			fCursorDrawable.set(editor, drawables);
		} catch (Throwable ex) {
			//just ignore
		}
	}

	public void setColors(int colorNormal, int colorFocused) {
		this.colorNormal = colorNormal;
		this.colorFocused = colorFocused;
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		super.setPadding((paddingLeft = left), (contentDescription == null) ? 0 : UI.dialogTextSizeBox, (paddingRight = right), UI.thickDividerSize + UI.strokeSize);
	}

	@Override
	public void setContentDescription(CharSequence contentDescription) {
		this.contentDescription = ((contentDescription == null || contentDescription.length() == 0)? null : contentDescription.toString());
		super.setContentDescription(this.contentDescription);
		super.setPadding(paddingLeft, (contentDescription == null) ? 0 : UI.dialogTextSizeBox, paddingRight, UI.thickDividerSize + UI.strokeSize);
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
		state = (UI.STATE_FOCUSED & UI.handleStateChanges(state, isPressed(), isFocused(), this));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		if (contentDescription != null)
			UI.drawText(canvas, contentDescription, colorFocused, UI._14sp, UI.rect.left, UI._14spYinBox);
		UI.rect.top = UI.rect.bottom - (state == 0 ? UI.strokeSize : UI.thickDividerSize);
		UI.fillRect(canvas, state == 0 ? colorNormal : colorFocused);
		super.onDraw(canvas);
	}

	@Override
	protected void onDetachedFromWindow() {
		contentDescription = null;
		super.onDetachedFromWindow();
	}
}
