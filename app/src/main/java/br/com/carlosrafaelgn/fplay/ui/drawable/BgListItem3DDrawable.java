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
import android.support.annotation.NonNull;

import br.com.carlosrafaelgn.fplay.ui.UI;

public final class BgListItem3DDrawable extends Drawable {
	private boolean hasBounds;

	@Override
	public ConstantState getConstantState() {
		return null;
	}

	@Override
	public int getChangingConfigurations() {
		return 0;
	}

	@Override
	public void setBounds(@NonNull Rect bounds) {
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
	public void draw(@NonNull Canvas canvas) {
		if (!hasBounds)
			canvas.drawColor(UI.color_list);
		else
			UI.drawBgListItem(getBounds(), canvas, 0);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {
	}

	@Override
	public int getAlpha() {
		return 255;
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
	}

	@Override
	public boolean isStateful() {
		return false;
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
