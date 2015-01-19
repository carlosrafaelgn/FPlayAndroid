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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.LinearLayout;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class SettingView extends View implements View.OnClickListener {
	/*if (color != 0)
		canvas.drawColor(color);
	TextIconDrawable.drawIcon(canvas, checked ? UI.ICON_OPTCHK : UI.ICON_OPTUNCHK, 0, 0, getWidth(), textColor);*/
	private View.OnClickListener onClickListener;
	private String icon, text, secondaryText;
	private boolean checkable, checked, hidingSeparator;
	private int state, color, secondaryTextWidth, width, height, textY;
	
	public SettingView(Context context, String icon, String text, String secondaryText, boolean checkable, boolean checked, boolean color) {
		super(context);
		super.setOnClickListener(this);
		setFocusable(true);
		setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		this.icon = icon;
		this.text = text;
		this.secondaryText = secondaryText;
		this.checkable = (secondaryText == null && checkable && !color);
		this.checked = (this.checkable && checked);
		this.color = ((secondaryText == null && !checkable && color) ? 0xff000000 : 0);

		secondaryTextWidth = ((secondaryText != null) ? UI.measureText(secondaryText, UI._18sp) : 0);

		updateLayout();

		super.setDrawingCacheEnabled(false);
	}

	@Override
	public CharSequence getContentDescription() {
		if (secondaryText != null)
			return text + ": " + secondaryText;
		else if (checkable)
			return text + ": " + getContext().getText(checked ? R.string.yes : R.string.no);
		else if (color != 0)
			return text;
		return super.getContentDescription();
	}

	private void updateLayout() {
		height = (UI.verticalMargin << 1);
		int textHeight, usableWidth;

		usableWidth = width - (UI._8dp << 1);
		if (icon != null)
			usableWidth -= (UI.defaultControlContentsSize + UI._8dp + UI.strokeSize + UI._8dp);
		if (checkable || color != 0)
			usableWidth -= (UI.defaultCheckIconSize + UI._8dp);

		if (usableWidth <= 0) {
			textHeight = UI._LargeItemspBox;
		} else {
			textHeight = UI._LargeItemspBox;
		}
		height += Math.max(textHeight, UI.defaultControlContentsSize);
		if (secondaryTextWidth > 0) {
			height += UI._18spBox;
			textY = UI.verticalMargin + UI._LargeItemspYinBox;
		} else {
			if (textHeight <= UI.defaultControlContentsSize)
				textY = (height >> 1) - (textHeight >> 1) + UI._LargeItemspYinBox;
			else
				textY = UI.verticalMargin + UI._LargeItemspYinBox;
		}
	}

	public void updateVerticalMargin() {
		//setPadding(UI._8dp + ((icon == null) ? 0 : (UI.defaultControlContentsSize + UI._8dp + UI._8dp)), UI.verticalMargin, UI._8dp, UI.verticalMargin);
	}
	
	@Override
	public void setOnClickListener(OnClickListener listener) {
		this.onClickListener = listener;
	}

	public void setSecondaryText(String secondaryText) {
		if (this.secondaryText != null) {
			this.secondaryText = secondaryText;
			invalidate();
		}
	}

	public boolean isChecked() {
		return (checkable && checked);
	}
	
	public void setChecked(boolean checked) {
		if (checkable) {
			this.checked = checked;
			invalidate();
		}
	}

	public void setHidingSeparator(boolean hidingSeparator) {
		this.hidingSeparator = hidingSeparator;
	}
	
	public int getColor() {
		return color;
	}
	
	public void setColor(int color) {
		if (this.color != 0) {
			this.color = (color | 0xff000000);
			invalidate();
		}
	}

	public void showErrorView(boolean show) {
		/*if (show) {
			if (errorView != null)
				return;
			final LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule((secondaryText == null) ? CENTER_VERTICAL : ALIGN_PARENT_TOP, TRUE);
			p.rightMargin = UI._8dp;
			errorView = new TextView(getContext());
			errorView.setId(4);
			errorView.setLayoutParams(p);
			errorView.setTypeface(UI.iconsTypeface);
			errorView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.defaultCheckIconSize);
			errorView.setTextColor(UI.colorState_highlight_static);
			errorView.setText(UI.ICON_REMOVE);
			addView(errorView);
			textView.setLayoutParams(getTextViewLayoutParams(true));
		} else {
			if (errorView == null)
				return;
			textView.setLayoutParams(getTextViewLayoutParams(false));
			removeView(errorView);
			errorView = null;
		}*/
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
		return false;//(state != 0);
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
	}

	@Override
	protected int getSuggestedMinimumHeight() {
		return height;
	}

	@Override
	public int getMinimumHeight() {
		return height;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(resolveSize(0, widthMeasureSpec), height);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (width != w) {
			width = w;
			updateLayout();
			if (height != h && !isInLayout())
				requestLayout();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		UI.drawBgBorderless(canvas, state, !hidingSeparator);
		if (icon != null) {
			UI.rect.top = (UI.rect.bottom >> 1) - (UI.defaultControlContentsSize >> 1);
			TextIconDrawable.drawIcon(canvas, icon, UI._8dp, UI.rect.top, UI.defaultControlContentsSize, (state == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
			UI.rect.left = UI._8dp + UI._8dp + UI.defaultControlContentsSize;
			UI.rect.right = UI.rect.left + UI.strokeSize;
			UI.rect.top += UI._2dp;
			UI.rect.bottom = UI.rect.top + UI.defaultControlContentsSize - UI._4dp;
			UI.fillRect(canvas, (state == 0) ? UI.color_divider : UI.color_text_selected);
		}
	}
	
	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}
	
	@Override
	protected void onDetachedFromWindow() {
		onClickListener = null;
		icon = null;
		text = null;
		secondaryText = null;
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onClick(View view) {
		if (checkable)
			checked = !checked;
		if (onClickListener != null)
			onClickListener.onClick(this);
	}
}
