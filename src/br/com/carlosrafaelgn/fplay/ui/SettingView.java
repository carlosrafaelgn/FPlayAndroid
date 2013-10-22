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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public final class SettingView extends RelativeLayout implements View.OnClickListener {
	private View.OnClickListener onClickListener;
	private final TextView textView, secondaryTextView;
	private final CheckBox checkBox;
	private String text, secondaryText;
	private boolean checked;
	private int state;
	
	public SettingView(Context context, String text, String secondaryText, boolean checkable, boolean checked) {
		super(context);
		super.setOnClickListener(this);
		setFocusable(true);
		setPadding(UI._8dp, UI._8sp << 1, UI._8dp, UI._8sp << 1);
		setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		p.addRule(ALIGN_PARENT_LEFT, TRUE);
		p.addRule((secondaryText == null) ? CENTER_VERTICAL : ALIGN_PARENT_TOP, TRUE);
		if (checkable)
			p.addRule(LEFT_OF, 3);
		else
			p.addRule(ALIGN_PARENT_RIGHT, TRUE);
		textView = new TextView(context);
		textView.setId(1);
		textView.setLayoutParams(p);
		textView.setTextColor(UI.colorState_text_normal);
		textView.setGravity(Gravity.LEFT);
		if (secondaryText != null) {
			p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			p.addRule(ALIGN_PARENT_LEFT, TRUE);
			p.addRule(BELOW, 1);
			if (checkable)
				p.addRule(LEFT_OF, 3);
			else
				p.addRule(ALIGN_PARENT_RIGHT, TRUE);
			secondaryTextView = new TextView(context);
			secondaryTextView.setId(2);
			secondaryTextView.setLayoutParams(p);
			secondaryTextView.setTextColor(UI.colorState_text_highlight);
			secondaryTextView.setGravity(Gravity.RIGHT);
		} else {
			secondaryTextView = null;
		}
		this.checked = (checked && checkable);
		if (checkable) {
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule(ALIGN_PARENT_RIGHT, TRUE);
			p.addRule(CENTER_VERTICAL, TRUE);
			p.leftMargin = UI._8dp;
			checkBox = new CheckBox(context);
			checkBox.setId(3);
			checkBox.setLayoutParams(p);
			checkBox.setChecked(checked);
			checkBox.setGravity(Gravity.CENTER);
			checkBox.setFocusable(false);
			checkBox.setOnClickListener(this);
			checkBox.setClickable(false);
			addView(checkBox);
		} else {
			checkBox = null;
		}
		if (UI.isLargeScreen) {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
			if (secondaryTextView != null)
				secondaryTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		} else {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			if (secondaryTextView != null)
				secondaryTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		}
		setText(text);
		setSecondaryText(secondaryText);
		addView(textView);
		if (secondaryTextView != null)
			addView(secondaryTextView);
	}
	
	@Override
	public void setOnClickListener(OnClickListener listener) {
		this.onClickListener = listener;
	}
	
	public String getText() {
		return text;
	}
	
	public void setText(String text) {
		this.text = text;
		textView.setText(text);
	}
	
	public String getSecondaryText() {
		return secondaryText;
	}
	
	public void setSecondaryText(String secondaryText) {
		if (secondaryTextView != null) {
			this.secondaryText = secondaryText;
			if (secondaryText == null || secondaryText.length() == 0) {
				secondaryTextView.setPadding(0, 0, 0, 0);
				secondaryTextView.setText("");
			} else {
				secondaryTextView.setPadding(0, UI._4sp, 0, 0);
				secondaryTextView.setText(secondaryText);
			}
		}
	}
	
	public boolean isCheckable() {
		return (checkBox != null);
	}
	
	public boolean isChecked() {
		return checked;
	}
	
	public void setChecked(boolean checked) {
		if (checkBox != null) {
			this.checked = checked;
		}
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
	public void invalidateDrawable(Drawable drawable) {
	}
	
	@Override
	protected boolean verifyDrawable(Drawable drawable) {
		return false;
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return (state != 0);
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		final boolean old = (state == 0);
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
		if (checkBox != null) {
			checkBox.setPressed((state & UI.STATE_PRESSED) != 0);
			checkBox.setSelected((state & UI.STATE_FOCUSED) != 0);
		}
		if ((state == 0) != old) {
			if (state == 0) {
				textView.setTextColor(UI.colorState_text_normal);
				if (secondaryTextView != null)
					secondaryTextView.setTextColor(UI.colorState_text_highlight);
			} else {
				textView.setTextColor(UI.colorState_text_sel);
				if (secondaryTextView != null)
					secondaryTextView.setTextColor(UI.colorState_text_sel);
			}
		}
	}
	
	//Android Custom Layout - onDraw() never gets called
	//http://stackoverflow.com/questions/13056331/android-custom-layout-ondraw-never-gets-called
	@Override
	protected void dispatchDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		UI.drawBg(canvas, state, UI.rect, true);
		super.dispatchDraw(canvas);
	}
	
	@Override
	public void onClick(View view) {
		if (checkBox != null) {
			if (view == checkBox) {
				checked = checkBox.isChecked();
			} else {
				checked = !checked;
				checkBox.setChecked(checked);
			}
		}
		if (onClickListener != null)
			onClickListener.onClick(this);
	}
}
