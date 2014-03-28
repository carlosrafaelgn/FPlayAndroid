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

import br.com.carlosrafaelgn.fplay.ui.UI;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public final class ColorDrawable extends Drawable {
	private int color, opacity, alpha;
	private boolean hasBounds;
	
	public ColorDrawable(int color) {
		setColor(color);
	}
	
	public int getColor() {
		return color;
	}
	
	public void setColor(int color) {
		this.color = color;
		this.alpha = ((color >>> 24) & 0xff);
		this.opacity = ((this.alpha == 0xff) ? PixelFormat.OPAQUE : ((this.alpha == 0) ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT));
	}
	
	@Override
	public void setBounds(Rect bounds) {
		if (bounds != null) {
			hasBounds = true;
			super.setBounds(bounds);
		} else {
			hasBounds = false;
		}
	}
	
	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		hasBounds = true;
		super.setBounds(left, top, right, bottom);
	}
	
	@Override
	public void draw(Canvas canvas) {
		if (!hasBounds)
			canvas.drawColor(color);
		else
			UI.fillRect(canvas, color, getBounds());
	}
	
	@Override
	public int getOpacity() {
		return opacity;
	}
	
	@Override
	public void setAlpha(int alpha) {
		color = (alpha << 24) | (color & 0x00ffffff);
		this.alpha = alpha;
		opacity = ((alpha == 0xff) ? PixelFormat.OPAQUE : ((alpha == 0) ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT));
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
		return true;
	}
	
	@Override
	public int getMinimumWidth() {
		return 0;
	}
	
	@Override
	public int getMinimumHeight() {
		return 0;
	}
}
