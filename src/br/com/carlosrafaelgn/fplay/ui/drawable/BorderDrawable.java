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
package br.com.carlosrafaelgn.fplay.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import br.com.carlosrafaelgn.fplay.ui.UI;

public final class BorderDrawable extends Drawable {
	private int strokeColor, fillColor, left, top, right, bottom, opacity;
	
	public BorderDrawable(int strokeColor, int fillColor, boolean left, boolean top, boolean right, boolean bottom) {
		init(strokeColor, fillColor, left, top, right, bottom);
	}
	
	public BorderDrawable(boolean sideBorders) {
		init(UI.color_current, UI.color_bg, sideBorders, true, false, false);
	}
	
	private void init(int strokeColor, int fillColor, boolean left, boolean top, boolean right, boolean bottom) {
		this.strokeColor = strokeColor;
		this.fillColor = fillColor;
		this.left = (left ? 0 : -(UI._1dp << 1));
		this.top = (top ? 0 : -(UI._1dp << 1));
		this.right = (right ? 0 : (UI._1dp << 1));
		this.bottom = (bottom ? 0 : (UI._1dp << 1));
		final int a = (fillColor & 0xFF000000);
		this.opacity = ((a == 0xFF000000) ? PixelFormat.OPAQUE : ((a == 0) ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT));
	}
	
	@Override
	public void draw(Canvas canvas) {
		final Rect rect = getBounds();
		rect.left += left;
		rect.top += top;
		rect.right += right;
		rect.bottom += bottom;
		UI.drawRect(canvas, strokeColor, fillColor, rect);
		rect.left -= left;
		rect.top -= top;
		rect.right -= right;
		rect.bottom -= bottom;
	}
	
	@Override
	public boolean getPadding(Rect padding) {
		padding.left = ((left == 0) ? UI._1dp : 0);
		padding.top = ((top == 0) ? UI._1dp : 0);
		padding.right = ((right == 0) ? UI._1dp : 0);
		padding.bottom = ((bottom == 0) ? UI._1dp : 0);
		return true;
	}
	
	@Override
	public int getOpacity() {
		return opacity;
	}
	
	@Override
	public void setAlpha(int alpha) {
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
	}
}
