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
package br.com.carlosrafaelgn.fplay.list;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.BitmapLruCache;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

public final class AlbumArtFetcher implements Runnable, Handler.Callback {
	public static interface AlbumArtFetcherListener {
		//Runs on a SECONDARY thread
		public void albumArtFetched(ReleasableBitmapWrapper bitmap, int requestId);
		
		//Runs on a SECONDARY thread
		public String albumArtUriForRequestId(int requestId);
	}
	
	private final Object sync;
	private final BitmapFactory.Options opts;
	private volatile BitmapLruCache cache;
	private byte[] tempStorage;
	private Canvas canvas;
	private Paint paint;
	private Rect srcR, dstR;
	private Handler handler;
	private Looper looper;
	
	public AlbumArtFetcher() {
		sync = new Object();
		opts = new BitmapFactory.Options();
		long max = Runtime.getRuntime().maxMemory();
		max >>= 4; //1/16
		//do not eat up more than 8 MiB
		if (max > (8 * 1024 * 1024))
			max = 8 * 1024 * 1024;
		cache = new BitmapLruCache((int)max);
		tempStorage = new byte[16384];
		canvas = new Canvas();
		paint = new Paint();
		paint.setAntiAlias(false);
		paint.setFilterBitmap(true);
		paint.setDither(false);
		srcR = new Rect();
		dstR = new Rect();
		(new Thread(this, "Album Art Fetcher Thread")).start();
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void run() {
		Looper.prepare();
		looper = Looper.myLooper();
		handler = new Handler(looper, this);
		Looper.loop();
	}
	
	//Runs on a SECONDARY thread
	@Override
	public boolean handleMessage(Message msg) {
		final AlbumArtFetcherListener listener = (AlbumArtFetcherListener)msg.obj;
		final Canvas c = canvas;
		final Paint p = paint;
		msg.obj = null;
		String uri;
		Bitmap b = null, b2 = null;
		ReleasableBitmapWrapper w = null;
		if (c == null || p == null || listener == null || (uri = listener.albumArtUriForRequestId(msg.what)) == null)
			return true;
		
		if (opts.mCancel)
			return true;
		try {
			opts.inJustDecodeBounds = true;
			opts.inTempStorage = tempStorage;
			BitmapFactory.decodeFile(uri, opts);
			int ss = 0;
			int s = ((opts.outWidth >= opts.outHeight) ? opts.outWidth : opts.outHeight);
			do {
				ss++;
				s >>= 1;
			} while (s > msg.arg1);
			opts.inInputShareable = false;
			opts.inPreferQualityOverSpeed = false;
			opts.inJustDecodeBounds = false;
			opts.inScaled = false;
			opts.inDensity = 0;
			opts.inTargetDensity = 0;
			opts.inPreferredConfig = Bitmap.Config.RGB_565;
			opts.inSampleSize = 1 << (ss - 1);
			if (opts.mCancel)
				return true;
			b = BitmapFactory.decodeFile(uri, opts);
			//I decided to do all this work here, because Bitmap.createScaledBitmap()
			//creates a lot of temporary objects every time it is called, including
			//a Canvas and a Paint
			if (opts.outWidth == msg.arg1 && opts.outHeight == msg.arg1) {
				w = new ReleasableBitmapWrapper(b);
			} else {
				srcR.right = opts.outWidth;
				srcR.bottom = opts.outHeight;
				if (srcR.right >= srcR.bottom) {
					dstR.right = msg.arg1;
					dstR.bottom = (srcR.bottom * msg.arg1) / srcR.right;
					//if we are missing the size by a handful of pixels, let's just
					//stretch the image a little bit... ;)
					if ((msg.arg1 - dstR.bottom) <= UI._4dp)
						dstR.bottom = msg.arg1;
				} else {
					dstR.bottom = msg.arg1;
					dstR.right = (srcR.right * msg.arg1) / srcR.bottom;
					//if we are missing the size by a handful of pixels, let's just
					//stretch the image a little bit... ;)
					if ((msg.arg1 - dstR.right) <= UI._4dp)
						dstR.right = msg.arg1;
				}
				b2 = Bitmap.createBitmap(dstR.right, dstR.bottom, Bitmap.Config.RGB_565);
				c.setBitmap(b2);
				c.drawBitmap(b, srcR, dstR, p);
				c.setBitmap(null);
				b.recycle();
				w = new ReleasableBitmapWrapper(b2);
			}
		} catch (Throwable ex) {
			c.setBitmap(null);
			if (b != null)
				b.recycle();
			if (b2 != null)
				b2.recycle();
			listener.albumArtFetched(null, msg.what);
			return true;
		}
		
		synchronized (sync) {
			if (cache != null) {
				cache.put(uri, w);
				listener.albumArtFetched(w, msg.what);
			}
		}
		
		return true;
	}
	
	//Runs on the MAIN thread
	public ReleasableBitmapWrapper getAlbumArt(String uri, int desiredSize, int requestId, AlbumArtFetcherListener listener) {
		ReleasableBitmapWrapper bitmap;
		synchronized (sync) {
			if (cache == null)
				return null;
			bitmap = cache.get(uri);
			if (bitmap != null) {
				bitmap.addRef();
				return bitmap;
			}
		}
		if (handler != null) {
			//wait before actually trying to fetch the albumart, as this request could
			//soon be cancelled, for example, in a ListView being scrolled fast
			handler.sendMessageAtTime(Message.obtain(handler, requestId, desiredSize, 0, listener), 100 + SystemClock.uptimeMillis());
		}
		return null;
	}
	
	//Runs on the MAIN thread
	public void cancelRequest(int requestId, AlbumArtFetcherListener listener) {
		if (handler != null)
			handler.removeMessages(requestId, listener);
	}
	
	//Runs on the MAIN thread
	public void stopAndCleanup() {
		synchronized (sync) {
			handler = null;
			if (cache != null) {
				cache.evictAll();
				cache = null;
			}
		}
		opts.mCancel = true;
		tempStorage = null;
		canvas = null;
		paint = null;
		if (looper != null) {
			looper.quit();
			looper = null;
		}
	}
}
