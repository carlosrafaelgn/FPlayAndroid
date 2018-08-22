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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import br.com.carlosrafaelgn.fplay.ui.UI;

@SuppressWarnings("unused")
public final class BgShadowDrawable extends Drawable {
	public static final int SHADOW_DIALOG = 0;
	public static final int SHADOW_MENU = 1;
	public static final int SHADOW_TOAST = 2;
	public static final int SHADOW_SPINNER = 3;

	private static final Object sync = new Object();
	private static WeakReference<Bitmap> cacheOthers, cacheToast;

	private final Bitmap bitmap;
	private final Paint paint;
	private final int size, bitmapSize, shadowType;

	public BgShadowDrawable(Context context, int shadowType) {
		paint = new Paint();
		paint.setAntiAlias(false);
		paint.setFilterBitmap(false);
		paint.setDither(false);
		paint.setAlpha(shadowType == SHADOW_TOAST ? 127 : 255);
		int s = (shadowType == SHADOW_TOAST ? (UI.defaultControlContentsSize >> 1) : ((UI.defaultControlContentsSize << 1) / 3));
		//make sure the image has odd dimensions so we have a "middle pixel"
		s &= ~1;
		size = s;
		//the bitmap must be larger, so we can discard the inner area, just like a gaussian shadow
		s = (s + (s >> 2)) & ~1;
		final int bitmapSize = (s << 1) + 1;
		this.bitmapSize = bitmapSize;
		this.shadowType = shadowType;

		//noinspection SynchronizationOnLocalVariableOrMethodParameter
		synchronized (sync) {
			final WeakReference<Bitmap> cache = (shadowType == SHADOW_TOAST ? cacheToast : cacheOthers);
			Bitmap bmp;
			if (cache != null && (bmp = cache.get()) != null && !bmp.isRecycled()) {
				bitmap = bmp;
				return;
			}
			final String fileName = (shadowType == SHADOW_TOAST ? "img_shadow_toast.png" : "img_shadow.png");
			try {
				bmp = BitmapFactory.decodeFile((new File(context.getFilesDir(), fileName)).getAbsolutePath());
				if (bmp != null && (bmp.getWidth() != bitmapSize || bmp.getHeight() != bitmapSize)) {
					bmp.recycle();
					bmp = null;
				}
			} catch (Throwable ex) {
				bmp = null;
			}
			if (bmp == null) {
				final int[] shadow = new int[bitmapSize * bitmapSize];
				//create a gaussian shadow in realtime (since b is w2, w2d is also w2 and both w2 and h2
				//are all equal to s, we can optimize a little bit)
				//
				//final int w2 = s, h2 = s;
				//final double a = 150.0, w2d = w2, b = w2d, c = b * 0.29, inv_2c2 = -1.0 / (2.0 * c * c);
				final double a = 150.0, c = s * 0.29, inv_2c2 = -1.0 / (2.0 * c * c);
				//final double inv_dist = 1.0 / s;
				for (int y = 0; y <= s; y++) {
					final int yGauss = y - s;
					final int y2 = yGauss * yGauss;
					final int oppositeYOffsetInImage = (bitmapSize - y - 1) * bitmapSize;
					final int yOffsetInImage = y * bitmapSize;
					int lastX = 0;
					for (int x = 0; x <= s; x++) {
						final int xGauss = x - s;
						//optimization!
						//final double x_b = w2d - Math.Sqrt((xGauss * xGauss) + y2) - b;

						final double x_b = Math.sqrt((xGauss * xGauss) + y2);
						final int fx = (int)(a * Math.exp(x_b * x_b * inv_2c2)) << 24;

						//unfortunately, this way does not look so good :(
						//float x_b = (float)Math.sqrt((xGauss * xGauss) + y2) * inv_dist;
						//x_b = (x_b <= 0.0f ? 1.0f : (x_b >= 1.0f ? 0.0f : (1.0f - x_b)));
						//final int fx = (int)(a * (x_b * x_b * (3.0f - (2.0f * x_b)))) << 24;// | 0xff0000ff;

						final int oppositeX = bitmapSize - x - 1;
						shadow[x + yOffsetInImage] = fx;
						shadow[oppositeX + yOffsetInImage] = fx;
						shadow[oppositeX + oppositeYOffsetInImage] = fx;
						shadow[x + oppositeYOffsetInImage] = fx;
					}
				}
				bmp = Bitmap.createBitmap(shadow, bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
				OutputStream outputStream = null;
				try {
					outputStream = context.openFileOutput(fileName, 0);
					bmp.compress(Bitmap.CompressFormat.PNG, 0, outputStream);
				} catch (Throwable ex) {
					//just ignore...
					ex.printStackTrace();
				} finally {
					try {
						if (outputStream != null)
							outputStream.close();
					} catch (Throwable ex) {
						//just ignore...
					}
				}
			}
			bitmap = bmp;
			if (shadowType == SHADOW_TOAST)
				cacheToast = new WeakReference<>(bmp);
			else
				cacheOthers = new WeakReference<>(bmp);
		}
	}

	@Override
	public ConstantState getConstantState() {
		return null;
	}

	@Override
	public int getChangingConfigurations() {
		return 0;
	}

	@Override
	public void draw(Canvas canvas) {
		final Rect src = UI.rect;
		final Rect dst = getBounds();
		final int l = dst.left, t = dst.top, r = dst.right, b = dst.bottom;
		final int paddingSize = size;
		final int bitmapSize = this.bitmapSize;
		final int bitmapSize_2 = bitmapSize >> 1;

		//don't even bother trying to clip using difference or reverse_difference
		//http://stackoverflow.com/a/9213065/3569421

		//top-left
		src.left = 0;
		src.top = 0;
		src.right = bitmapSize_2;
		src.bottom = bitmapSize_2;
		dst.left = l;
		dst.top = t;
		dst.right = l + bitmapSize_2;
		dst.bottom = t + bitmapSize_2;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//middle-left
		src.top = bitmapSize_2;
		src.right = paddingSize;
		src.bottom = bitmapSize_2 + 1;
		dst.top = t + bitmapSize_2;
		dst.right = l + paddingSize;
		dst.bottom = b - bitmapSize_2;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//bottom-left
		src.top = bitmapSize_2 + 1;
		src.right = bitmapSize_2;
		src.bottom = bitmapSize;
		dst.top = b - bitmapSize_2;
		dst.right = l + bitmapSize_2;
		dst.bottom = b;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//bottom-center
		src.top = bitmapSize - paddingSize;
		src.left = bitmapSize_2;
		src.right = bitmapSize_2 + 1;
		dst.left = l + bitmapSize_2;
		dst.top = b - paddingSize;
		dst.right = r - bitmapSize_2;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//bottom-right
		src.left = bitmapSize_2 + 1;
		src.top = bitmapSize_2 + 1;
		src.right = bitmapSize;
		dst.left = r - bitmapSize_2;
		dst.top = b - bitmapSize_2;
		dst.right = r;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//middle-right
		src.left = bitmapSize - paddingSize;
		src.top = bitmapSize_2;
		src.bottom = bitmapSize_2 + 1;
		dst.left = r - paddingSize;
		dst.top = t + bitmapSize_2;
		dst.bottom = b - bitmapSize_2;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//top-right
		src.left = bitmapSize_2 + 1;
		src.top = 0;
		src.bottom = bitmapSize_2;
		dst.left = r - bitmapSize_2;
		dst.top = t;
		dst.bottom = t + bitmapSize_2;
		canvas.drawBitmap(bitmap, src, dst, paint);

		//top-center
		src.left = bitmapSize_2;
		src.right = bitmapSize_2 + 1;
		src.bottom = paddingSize;
		dst.left = l + bitmapSize_2;
		dst.right = r - bitmapSize_2;
		dst.bottom = t + paddingSize;
		canvas.drawBitmap(bitmap, src, dst, paint);

		dst.left = l;
		dst.top = t;
		dst.right = r;
		dst.bottom = b;
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
		paint.setAlpha(shadowType == SHADOW_TOAST ? (alpha >> 1) : alpha);
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
