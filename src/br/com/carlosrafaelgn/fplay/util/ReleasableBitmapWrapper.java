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
package br.com.carlosrafaelgn.fplay.util;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;

public final class ReleasableBitmapWrapper {
	private volatile int ref;
	public volatile Bitmap bitmap;
	public final int width, height, size;
	
	public ReleasableBitmapWrapper(Bitmap bitmap) {
		this.ref = 1;
		this.bitmap = bitmap;
		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			size = sizeOf19(bitmap);
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
			size = sizeOf12(bitmap);
		else
			size = bitmap.getRowBytes() * bitmap.getHeight();
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private int sizeOf19(Bitmap bitmap) {
		return bitmap.getAllocationByteCount();
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	private int sizeOf12(Bitmap bitmap) {
		return bitmap.getByteCount();
	}
	
	public void addRef() {
		synchronized (this) {
			if (ref <= 0 || bitmap == null)
				return;
			ref++;
		}
	}
	
	public void release() {
		synchronized (this) {
			ref--;
			if (ref <= 0) {
				if (bitmap != null) {
					bitmap.recycle();
					bitmap = null;
				}
			}
		}
	}
}
