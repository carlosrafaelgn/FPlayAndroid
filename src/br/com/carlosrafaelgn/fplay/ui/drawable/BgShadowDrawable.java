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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import br.com.carlosrafaelgn.fplay.ui.UI;

public final class BgShadowDrawable extends Drawable {
	private final Bitmap bitmap;
	private final Paint paint;
	private final int size;

	public BgShadowDrawable() {
		bitmap = Bitmap.createBitmap(new int[] {
			0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
			0x00000000, 0x30000000, 0x30000000, 0x30000000, 0x00000000,
			0x00000000, 0x30000000, 0x30000000, 0x30000000, 0x00000000,
			0x00000000, 0x30000000, 0x30000000, 0x30000000, 0x00000000,
			0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000
		}, 5, 5, Bitmap.Config.ARGB_8888);
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		paint.setDither(true);
		paint.setAlpha(255);
		size = (UI.defaultControlContentsSize << 1) / 3;
	}

	@Override
	public void draw(Canvas canvas) {
		final Rect bounds = getBounds();
		final int l = bounds.left, t = bounds.top, r = bounds.right, b = bounds.bottom;
		final int hiddenMargin = UI.defaultControlContentsSize - size;
		final int bitmapSize = (UI.defaultControlContentsSize * 5) >> 2;

		//don't even bother trying to clip using difference or reverse_difference
		//http://stackoverflow.com/a/9213065/3569421

		UI.rect.left = 0;
		UI.rect.top = 0;
		UI.rect.right = 2;
		UI.rect.bottom = 2;
		bounds.left = l - hiddenMargin;
		bounds.top = t - hiddenMargin;
		bounds.right = l + bitmapSize;
		bounds.bottom = t + bitmapSize;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.top = 2;
		UI.rect.bottom = 3;
		bounds.top = t + bitmapSize;
		bounds.bottom = b - bitmapSize;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.top = 3;
		UI.rect.bottom = 5;
		bounds.top = b - bitmapSize;
		bounds.bottom = b + hiddenMargin;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.left = 2;
		UI.rect.right = 3;
		bounds.left = l + bitmapSize;
		bounds.right = r - bitmapSize;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.left = 3;
		UI.rect.right = 5;
		bounds.left = r - bitmapSize;
		bounds.right = r + hiddenMargin;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.top = 2;
		UI.rect.bottom = 3;
		bounds.top = t + bitmapSize;
		bounds.bottom = b - bitmapSize;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.top = 0;
		UI.rect.bottom = 2;
		bounds.top = t - hiddenMargin;
		bounds.bottom = t + bitmapSize;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		UI.rect.left = 2;
		UI.rect.right = 3;
		bounds.left = l + bitmapSize;
		bounds.right = r - bitmapSize;
		canvas.drawBitmap(bitmap, UI.rect, bounds, paint);

		bounds.left = l;
		bounds.top = t;
		bounds.right = r;
		bounds.bottom = b;
	}

	@Override
	public boolean getPadding(@NonNull Rect padding) {
		padding.left = size;
		padding.top = size;
		padding.right = size;
		padding.bottom = size;
		return true;
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {
		paint.setAlpha(alpha);
	}

	@Override
	public int getAlpha() {
		return paint.getAlpha();
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
