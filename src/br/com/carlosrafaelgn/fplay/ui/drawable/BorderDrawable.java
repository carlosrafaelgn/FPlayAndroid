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
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import br.com.carlosrafaelgn.fplay.ui.UI;

public final class BorderDrawable extends Drawable {
	private int strokeColor, fillColor, opacity, alpha;
	private final int leftSize, topSize, rightSize, bottomSize;
	private final boolean ignorePadding;
	
	public BorderDrawable(int strokeColor, int fillColor, int leftSize, int topSize, int rightSize, int bottomSize) {
		changeColors(strokeColor, fillColor);
		this.leftSize = leftSize;
		this.topSize = topSize;
		this.rightSize = rightSize;
		this.bottomSize = bottomSize;
		this.ignorePadding = false;
	}
	
	public BorderDrawable(int strokeColor, int fillColor, int leftSize, int topSize, int rightSize, int bottomSize, boolean ignorePadding) {
		changeColors(strokeColor, fillColor);
		this.leftSize = leftSize;
		this.topSize = topSize;
		this.rightSize = rightSize;
		this.bottomSize = bottomSize;
		this.ignorePadding = ignorePadding;
	}
	
	public int getStrokeColor() {
		return strokeColor;
	}
	
	public int getFillColor() {
		return fillColor;
	}
	
	public void changeColors(int strokeColor, int fillColor) {
		this.strokeColor = strokeColor;
		this.fillColor = fillColor;
		this.alpha = ((fillColor >>> 24) & 0xff);
		this.opacity = ((this.alpha == 0xff) ? PixelFormat.OPAQUE : ((this.alpha == 0) ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT));
		invalidateSelf();
	}
	
	@Override
	public void draw(Canvas canvas) {
		final Rect rect = getBounds();
		UI.fillPaint.setColor(strokeColor);
		final int l = rect.left, t = rect.top, r = rect.right, b = rect.bottom;
		if (topSize != 0)
			canvas.drawRect(l, t, r, t + topSize, UI.fillPaint);
		if (bottomSize != 0)
			canvas.drawRect(l, b - bottomSize, r, b, UI.fillPaint);
		if (leftSize != 0)
			canvas.drawRect(l, t + topSize, l + leftSize, b - bottomSize, UI.fillPaint);
		if (rightSize != 0)
			canvas.drawRect(r - rightSize, t + topSize, r, b - bottomSize, UI.fillPaint);
		UI.fillPaint.setColor(fillColor);
		canvas.drawRect(l + leftSize, t + topSize, r - rightSize, b - bottomSize, UI.fillPaint);
	}
	
	@Override
	public boolean getPadding(Rect padding) {
		if (ignorePadding)
			return false;
		padding.left = leftSize;
		padding.top = topSize;
		padding.right = rightSize;
		padding.bottom = bottomSize;
		return true;
	}
	
	@Override
	public int getOpacity() {
		return opacity;
	}
	
	@Override
	public void setAlpha(int alpha) {
		changeColors((alpha << 24) | (strokeColor & 0x00ffffff), (alpha << 24) | (fillColor & 0x00ffffff));
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
	}
	
	@Override
	public boolean isStateful() {
		return false;
	}
	
	@Override
	public int getAlpha() {
		return alpha;
	}
	
	@Override
	public boolean isAutoMirrored() {
		return (leftSize == rightSize);
	}
	
	@Override
	public int getMinimumWidth() {
		return (ignorePadding ? 0 : (leftSize + rightSize));
	}
	
	@Override
	public int getMinimumHeight() {
		return (ignorePadding ? 0 : (topSize + bottomSize));
	}
}
