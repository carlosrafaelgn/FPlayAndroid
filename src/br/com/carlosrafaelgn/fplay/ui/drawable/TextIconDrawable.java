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
package br.com.carlosrafaelgn.fplay.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import br.com.carlosrafaelgn.fplay.ui.UI;

public final class TextIconDrawable extends Drawable {
	private static final TextPaint paint;
	private int[] stateSet;
	private int state;
	private final int size, y, color;
	private final boolean outsideMenu;
	private String icon;
	
	static {
		paint = new TextPaint();
		paint.setDither(false);
		paint.setAntiAlias(true);
		paint.setStyle(Paint.Style.FILL);
		paint.setTypeface(UI.iconsTypeface);
		paint.setTextAlign(Paint.Align.LEFT);
		paint.setColor(UI.color_menu_icon);
	}
	
	public static void drawIcon(Canvas canvas, String icon, int x, int y, int size, int color) {
		paint.setColor(color);
		paint.setTextSize(size);
		canvas.drawText(icon, x, y + size, paint);
	}
	
	public TextIconDrawable(String icon) {
		this.icon = icon;
		this.size = UI.defaultCheckIconSize;
		this.y = UI.defaultCheckIconSize >> 1;
		this.outsideMenu = false;
		this.color = UI.color_menu_icon;
		this.stateSet = super.getState();
		super.setBounds(0, 0, UI.defaultCheckIconSize + UI._8sp + UI._8sp + 1, UI.defaultCheckIconSize);
	}
	
	public TextIconDrawable(String icon, int color) {
		this.icon = icon;
		this.size = UI.defaultCheckIconSize;
		this.y = UI.defaultCheckIconSize >> 1;
		this.outsideMenu = true;
		this.color = color;
		this.stateSet = super.getState();
		super.setBounds(0, 0, UI.defaultCheckIconSize + UI._8sp, UI.defaultCheckIconSize);
	}
	
	public TextIconDrawable(String icon, int color, int size) {
		this.icon = icon;
		this.size = size;
		this.y = size >> 1;
		this.outsideMenu = true;
		this.color = color;
		this.stateSet = super.getState();
		super.setBounds(0, 0, size + UI._8sp, size);
	}
	
	public String getIcon() {
		return icon;
	}
	
	public void setIcon(String icon) {
		this.icon = icon;
		invalidateSelf();
	}
	
	@Override
	public void draw(Canvas canvas) {
		final Rect rect = getBounds();
		paint.setColor((state == 0) ? color : UI.color_text_selected);
		paint.setTextSize(size);
		canvas.drawText(icon, rect.left, rect.top + ((rect.bottom - rect.top) >> 1) + y, paint);
		if (!outsideMenu) {
			UI.rect.left = rect.right - UI._8sp - 1;
			UI.rect.right = UI.rect.left + UI.strokeSize;
			UI.rect.top = rect.top + UI._2dp;
			UI.rect.bottom = rect.bottom - UI._2dp;
			paint.setColor((state == 0) ? UI.color_menu_border : UI.color_text_selected);
			canvas.drawRect(UI.rect, paint);
		}
	}
	
	@Override
	public int[] getState() {
		return stateSet;
	}
	
	@Override
	public boolean setState(int[] stateSet) {
		this.stateSet = stateSet;
		int newState = 0;
		if (stateSet != null) {
			for (int i = stateSet.length - 1; i >= 0; i--) {
				switch (stateSet[i]) {
				case android.R.attr.state_focused:
					newState |= UI.STATE_FOCUSED;
					break;
				case android.R.attr.state_pressed:
					newState |= UI.STATE_PRESSED;
					break;
				}
			}
		}
		if (state == newState)
			return false;
		state = newState;
		invalidateSelf();
		return true;
	}
	
	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
	
	@Override
	public void setAlpha(int alpha) {
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
	}
	
	@Override
	public boolean isStateful() {
		return true;
	}
	
	@Override
	public int getAlpha() {
		return 255;
	}
	
	@Override
	public boolean isAutoMirrored() {
		return true;
	}
	
	@Override
	public int getIntrinsicWidth() {
		return size + UI._8dp + (outsideMenu ? 0 : (UI._8dp + 1));
	}
	
	@Override
	public int getIntrinsicHeight() {
		return size;
	}
	
	@Override
	public int getMinimumWidth() {
		return size + UI._8dp + (outsideMenu ? 0 : (UI._8dp + 1));
	}
	
	@Override
	public int getMinimumHeight() {
		return size;
	}
}
