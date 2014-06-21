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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import br.com.carlosrafaelgn.fplay.util.BitmapLruCache;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

public final class AlbumArtFetcher implements Runnable, Handler.Callback {
	public static interface AlbumArtFetcherListener {
		public void albumArtFecthed(ReleasableBitmapWrapper albumArt, int requestId);
	}
	
	private final Object sync;
	private BitmapLruCache cache;
	private Thread thread;
	private Handler handler;
	private Looper looper;
	
	public AlbumArtFetcher() {
		sync = new Object();
		long max = Runtime.getRuntime().maxMemory();
		max >>= 4; //1/16
		//do not eat up more than 8 MiB
		if (max > (8 * 1024 * 1024))
			max = 8 * 1024 * 1024;
		cache = new BitmapLruCache((int)max);
		thread = new Thread(this, "Album Art Fetcher Thread");
		thread.start();
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void run() {
		Looper.prepare();
		looper = Looper.myLooper();
		handler = new Handler(looper, this);
		Looper.loop();
	}
	
	//Runs on a SECONDARY thread (except for negative messages,
	//that are handled on the main thread)
	@Override
	public boolean handleMessage(Message msg) {
		return false;
	}
	
	//Runs on the MAIN thread
	public ReleasableBitmapWrapper getAlbumArt(String uri, int requestId, AlbumArtFetcherListener listener) {
		ReleasableBitmapWrapper bitmap;
		synchronized (sync) {
			if (cache == null)
				return null;
			bitmap = cache.get(uri);
		}
		if (bitmap != null) {
			bitmap.addRef();
			return bitmap;
		} else if (handler != null) {
			//wait before actually trying to fetch the albumart,
			//as this request could soon be cancelled, for example,
			//in a ListView being scrolled fast
			handler.sendMessageAtTime(Message.obtain(handler, requestId, 0, 0, listener), 150 + SystemClock.uptimeMillis());
		}
		return null;
	}
	
	//Runs on the MAIN thread
	public void cancelRequest(int requestId) {
		if (handler != null)
			handler.removeMessages(requestId);
	}
	
	//Runs on the MAIN thread
	public void stopAndCleanup() {
		BitmapLruCache c = null;
		synchronized (sync) {
			c = cache;
			cache = null;
		}
		if (c != null) {
			c.evictAll();
			c = null;
		}
		if (looper != null) {
			looper.quit();
			looper = null;
		}
		handler = null;
	}
}
