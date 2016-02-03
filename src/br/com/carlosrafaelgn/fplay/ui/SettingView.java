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
import android.support.annotation.NonNull;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.LinearLayout;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class SettingView extends View {
	private String icon, text, secondaryText;
	private int[] textLines;
	private boolean checkable, checked, hidingSeparator;
	private int state, color, secondaryTextWidth, width, height, textY, extraBorders;

	public SettingView(Context context, String icon, String text, String secondaryText, boolean checkable, boolean checked, boolean color) {
		super(context);
		setClickable(true);
		setFocusableInTouchMode(!UI.hasTouch);
		setFocusable(true);
		setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		this.icon = icon;
		this.text = text;
		this.secondaryText = secondaryText;
		this.checkable = (secondaryText == null && checkable && !color);
		this.checked = (this.checkable && checked);
		this.color = ((secondaryText == null && !checkable && color) ? 0xff000000 : 0);
		this.extraBorders = (UI.is3D ? UI.controlSmallMargin : 0);

		secondaryTextWidth = ((secondaryText != null) ? UI.measureText(secondaryText, UI._18sp) : 0);

		updateLayout();

		super.setDrawingCacheEnabled(false);
	}

	@Override
	public CharSequence getContentDescription() {
		if (secondaryText != null)
			return text + UI.collon() + secondaryText;
		else if (checkable)
			return text + UI.collon() + getContext().getText(checked ? R.string.yes : R.string.no);
		else if (color != 0)
			return text;
		return super.getContentDescription();
	}

	private void updateLayout() {
		height = extraBorders + (UI.verticalMargin << 1);
		int textHeight, usableWidth;

		usableWidth = width - (UI.controlMargin << 1) - (extraBorders << 1);
		if (icon != null)
			usableWidth -= (UI.defaultControlContentsSize + UI.menuMargin);
		if (checkable || color != 0)
			usableWidth -= (UI.defaultCheckIconSize + UI.controlMargin);

		final int lineCount;
		if (usableWidth <= 0 || text == null) {
			if (textLines == null || textLines.length < 1)
				textLines = new int[1];
			textLines[0] = -1;
			textHeight = UI._LargeItemspBox;
			lineCount = 1;
		} else {
			UI.textPaint.setTextSize(UI._LargeItemsp);
			final StaticLayout layout = new StaticLayout(text, UI.textPaint, usableWidth, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
			lineCount = layout.getLineCount();
			if (lineCount <= 1) {
				if (textLines == null || textLines.length < 3)
					textLines = new int[3];
				textLines[0] = 0;
				textLines[1] = text.length();
				textLines[2] = -1;
				textHeight = UI._LargeItemspBox;
			} else {
				if (textLines == null || textLines.length < ((lineCount << 1) + 1))
					textLines = new int[(lineCount << 1) + 1];
				textLines[lineCount << 1] = -1;
				for (int i = lineCount - 1; i >= 0; i--) {
					textLines[(i << 1)] = layout.getLineStart(i);
					textLines[(i << 1) + 1] = layout.getLineEnd(i);
				}
				textHeight = UI._LargeItemspBox * lineCount;
			}
		}
		height += Math.max(textHeight, UI.defaultControlContentsSize);
		if (secondaryTextWidth > 0) {
			height += UI._18spBox + ((lineCount <= 1) ? 0 : (UI._14spBox >> 2));
			textY = extraBorders + UI.verticalMargin + UI._LargeItemspYinBox;
		} else {
			textY = ((textHeight <= UI.defaultControlContentsSize) ?
				(((height - textHeight) >> 1) + UI._LargeItemspYinBox) :
				(extraBorders + UI.verticalMargin + UI._LargeItemspYinBox));
		}
	}

	public void updateVerticalMargin(int oldVerticalMargin) {
		height = height - (oldVerticalMargin << 1) + (UI.verticalMargin << 1);
		textY = textY - oldVerticalMargin + UI.verticalMargin;
		requestLayout();
	}

	public void setIcon(String icon) {
		this.icon = icon;
		invalidate();
	}

	public void setText(String text) {
		this.text = text;
		updateLayout();
		invalidate();
		requestLayout();
	}

	public void setSecondaryText(String secondaryText) {
		if (this.secondaryText != null && secondaryText != null) {
			this.secondaryText = secondaryText;
			secondaryTextWidth = UI.measureText(secondaryText, UI._18sp);
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

	@Override
	public boolean performClick() {
		if (checkable)
			checked = !checked;
		return super.performClick();
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
	public boolean onKeyUp(int keyCode, KeyEvent event) {
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
		final int w = resolveSize(0, widthMeasureSpec);
		if (width != w) {
			width = w;
			updateLayout();
		}
		setMeasuredDimension(w, height);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (textLines == null)
			return;
		getDrawingRect(UI.rect);
		final int txtColor = ((state == 0) ? UI.color_text_listitem : UI.color_text_selected);
		final int txtColorSecondary = ((state == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
		int x = UI.controlMargin + extraBorders;
		if (icon != null)
			x += (UI.defaultControlContentsSize + UI.menuMargin);
		if (UI.is3D)
			UI.drawBgListItem(canvas, state, !hidingSeparator, UI.controlSmallMargin, UI.controlSmallMargin);
		else
			UI.drawBgListItem(canvas, state, !hidingSeparator, x, UI.controlMargin);
		if (icon != null)
			TextIconDrawable.drawIcon(canvas, icon, UI.controlMargin + extraBorders, extraBorders + ((height - extraBorders - UI.defaultControlContentsSize) >> 1), UI.defaultControlContentsSize, txtColorSecondary);
		if (textLines != null) {
			int y = textY, i = 0, start;
			while ((start = textLines[i]) >= 0) {
				UI.drawText(canvas, text, start, textLines[i + 1], txtColor, UI._LargeItemsp, x, y);
				y += UI._LargeItemspBox;
				i += 2;
			}
		}
		if (secondaryText != null) {
			UI.drawText(canvas, secondaryText, txtColorSecondary, UI._18sp, width - secondaryTextWidth - UI.controlMargin - extraBorders, height - UI.verticalMargin - UI._18spBox + UI._18spYinBox);
		} else if (color != 0 || checkable) {
			UI.rect.left = width - UI.controlMargin - UI.defaultCheckIconSize - extraBorders;
			UI.rect.top = (height - UI.defaultCheckIconSize) >> 1;
			if (color != 0) {
				UI.rect.right = UI.rect.left + UI.defaultCheckIconSize;
				UI.rect.bottom = UI.rect.top + UI.defaultCheckIconSize;
				UI.fillRect(canvas, color);
			}
			TextIconDrawable.drawIcon(canvas, checked ? UI.ICON_OPTCHK : UI.ICON_OPTUNCHK, UI.rect.left, UI.rect.top, UI.defaultCheckIconSize, txtColor);
		}
	}

	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}

	@Override
	protected void onDetachedFromWindow() {
		icon = null;
		text = null;
		secondaryText = null;
		textLines = null;
		super.onDetachedFromWindow();
	}
}
