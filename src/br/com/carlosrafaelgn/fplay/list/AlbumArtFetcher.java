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

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;

import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.BitmapLruCache;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

//Good information:
//http://developer.android.com/training/displaying-bitmaps/manage-memory.html
//http://developer.android.com/training/displaying-bitmaps/cache-bitmap.html
//http://developer.android.com/reference/android/util/LruCache.html
public final class AlbumArtFetcher implements Runnable, Handler.Callback {
	public interface AlbumArtFetcherListener {
		//Runs on a SECONDARY thread
		void albumArtFetched(ReleasableBitmapWrapper bitmap, int requestId);

		//Runs on a SECONDARY thread
		String albumArtUriForRequestId(int requestId);

		//Runs on a SECONDARY thread
		Long albumIdForRequestId(int requestId);

		//Runs on a SECONDARY thread
		String fileUriForRequestId(int requestId);

		//Runs on a SECONDARY thread
		long artistIdForRequestId(int requestId);
	}

	public static final Long zeroAlbumId = 0L;

	private final Object sync;
	private final BitmapFactory.Options opts;
	private final ContentResolver contentResolver;
	private final String audioDataSelection, albumIdSelection;
	private final String[] albumArtProjection, artistAlbumArtProjection, audioAlbumIdProjection, tempSelection;
	private volatile BitmapLruCache cache;
	private byte[] tempStorage;
	private Canvas canvas;
	private Paint paint;
	private Rect srcR, dstR;
	private Handler handler;
	private Looper looper;
	private int nextRequestId;

	/**
	 * Setup LRU cache, canvas for bitmap
	 * set maximum memory limit for cache to 8 MB
	 * and start a new thread to fetch bitmap.
	 */
	public AlbumArtFetcher() {
		sync = new Object();
		opts = new BitmapFactory.Options();
		contentResolver = Player.theApplication.getContentResolver();
		audioDataSelection = MediaStore.Audio.AudioColumns.DATA + "=?";
		albumIdSelection = MediaStore.Audio.Albums._ID + "=?";
		albumArtProjection = new String[] { MediaStore.Audio.Albums.ALBUM_ART };
		//ALBUM_ID can only be used when not accessing albums directly, because when we are
		//accessing albums, either general albums or the albums of a specific artist, _ID
		//MUST be used!!!
		artistAlbumArtProjection = new String[] { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART };
		audioAlbumIdProjection = new String[] { MediaStore.Audio.Albums.ALBUM_ID };
		tempSelection = new String[1];
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
		final Thread thread = new Thread(this, "Album Art Fetcher Thread");
		thread.setDaemon(true);
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
	
	//Runs on a SECONDARY thread

	/**
	 * Get the URI for a file's album art and fetch it into
	 * a bitmap, add item to cache.
	 * @param msg AlbumArtFetcherListener as a Message object
	 */
	@Override
	@SuppressWarnings("ConstantConditions")
	public boolean handleMessage(Message msg) {
		final AlbumArtFetcherListener listener = (AlbumArtFetcherListener)msg.obj;
		final Canvas canvas = this.canvas;
		final Paint paint = this.paint;
		msg.obj = null;
		String albumArtUri, fileUri = null;
		Long albumId;
		long artistId = 0;
		Bitmap bitmap = null, bitmap2 = null;
		ReleasableBitmapWrapper w;

		if (listener == null)
			return true;

		if (canvas == null || paint == null) {
			listener.albumArtFetched(null, msg.what);
			return true;
		}

		albumArtUri = listener.albumArtUriForRequestId(msg.what);
		if ((albumId = listener.albumIdForRequestId(msg.what)) == null &&
			(fileUri = listener.fileUriForRequestId(msg.what)) == null &&
			(artistId = listener.artistIdForRequestId(msg.what)) == 0) {
			listener.albumArtFetched(null, msg.what);
			return true;
		}

		try {
			if (fileUri != null) {
				tempSelection[0] = fileUri;
				final Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioAlbumIdProjection, audioDataSelection, tempSelection, null);
				if (cursor != null) {
					if (cursor.moveToNext())
						albumId = cursor.getLong(0);
					cursor.close();
				}
			}
			if (albumId != null) {
				if (opts.mCancel)
					return true;

				synchronized (sync) {
					if (cache != null && (w = cache.get(albumId)) != null) {
						listener.albumArtFetched(w, msg.what);
						return true;
					}
				}

				if (albumArtUri == null) {
					tempSelection[0] = Long.toString(albumId);
					final Cursor cursor = contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumArtProjection, albumIdSelection, tempSelection, null);
					if (cursor != null) {
						if (cursor.moveToNext())
							albumArtUri = cursor.getString(0);
						cursor.close();

						if (opts.mCancel)
							return true;
					}
				}
			}
			if ((albumId == null || albumArtUri == null) && artistId != 0 && contentResolver != null) {
				//try to fetch the first album for this artist
				final Cursor cursor = contentResolver.query(MediaStore.Audio.Artists.Albums.getContentUri("external", artistId), artistAlbumArtProjection, null, null, null);
				if (cursor != null) {
					while (albumArtUri == null && !opts.mCancel && cursor.moveToNext()) {
						albumId = cursor.getLong(0);
						albumArtUri = cursor.getString(1);
					}
					cursor.close();
				}
				if (albumId == null || albumArtUri == null) {
					listener.albumArtFetched(null, msg.what);
					return true;
				}
				synchronized (sync) {
					if (cache != null && (w = cache.get(albumId)) != null) {
						listener.albumArtFetched(w, msg.what);
						return true;
					}
				}
			}

			if (albumId == null || albumArtUri == null) {
				listener.albumArtFetched(null, msg.what);
				return true;
			}

			if (opts.mCancel)
				return true;

			opts.inJustDecodeBounds = true;
			opts.inTempStorage = tempStorage;
			BitmapFactory.decodeFile(albumArtUri, opts);
			int ss = 0;
			if (msg.arg1 > 0) {
				int s = ((opts.outWidth >= opts.outHeight) ? opts.outWidth : opts.outHeight);
				do {
					ss++;
					s >>= 1;
				} while (s > msg.arg1);
			} else {
				ss = 1;
			}
			//opts.inInputShareable = false;
			opts.inPreferQualityOverSpeed = false;
			opts.inJustDecodeBounds = false;
			opts.inScaled = false;
			opts.inDensity = 0;
			opts.inTargetDensity = 0;
			opts.inPreferredConfig = Bitmap.Config.RGB_565;
			opts.inSampleSize = 1 << (ss - 1);
			if (opts.mCancel)
				return true;
			bitmap = BitmapFactory.decodeFile(albumArtUri, opts);
			//I decided to do all this work here, because Bitmap.createScaledBitmap()
			//creates a lot of temporary objects every time it is called, including
			//a Canvas and a Paint
			if (msg.arg1 <= 0 || (opts.outWidth == msg.arg1 && opts.outHeight == msg.arg1)) {
				w = new ReleasableBitmapWrapper(bitmap, albumId, albumArtUri);
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
				bitmap2 = Bitmap.createBitmap(dstR.right, dstR.bottom, Bitmap.Config.RGB_565);
				canvas.setBitmap(bitmap2);
				canvas.drawBitmap(bitmap, srcR, dstR, paint);
				bitmap.recycle();
				w = new ReleasableBitmapWrapper(bitmap2, albumId, albumArtUri);
			}

			synchronized (sync) {
				if (cache != null) {
					cache.put(albumId, w);
					listener.albumArtFetched(w, msg.what);
				}
			}
		} catch (Throwable ex) {
			try {
				if (bitmap != null)
					bitmap.recycle();
			} catch (Throwable ex2) {
				ex2.printStackTrace();
			}
			try {
				if (bitmap2 != null)
					bitmap2.recycle();
			} catch (Throwable ex2) {
				ex2.printStackTrace();
			}
			listener.albumArtFetched(null, msg.what);
			ex.printStackTrace();
			return true;
		}

		return true;
	}

	//Runs on the MAIN thread
	public ReleasableBitmapWrapper getAlbumArt(Long albumId, int desiredSize, int requestId, AlbumArtFetcherListener listener) {
		synchronized (sync) {
			if (cache == null)
				return null;
			if (albumId != null) {
				final ReleasableBitmapWrapper bitmap = cache.get(albumId);
				if (bitmap != null) {
					bitmap.addRef();
					return bitmap;
				}
			}
		}
		if (handler != null)
			handler.sendMessageAtTime(Message.obtain(handler, requestId, desiredSize, 0, listener), SystemClock.uptimeMillis());
		return null;
	}

	//Runs on the MAIN thread
	public void getAlbumArtForFile(int desiredSize, int requestId, AlbumArtFetcherListener listener) {
		if (handler != null) {
			//wait before actually trying to fetch the albumart, as this request could
			//soon be cancelled, for example, in a ListView being scrolled fast
			handler.sendMessageAtTime(Message.obtain(handler, requestId, desiredSize, 0, listener), SystemClock.uptimeMillis());
		}
	}

	//Runs on the MAIN thread
	public void cancelRequest(int requestId, AlbumArtFetcherListener listener) {
		if (handler != null)
			handler.removeMessages(requestId, listener);
	}
	
	//Runs on the MAIN thread

	/**
	 * Clean the cache and clear the memory allocated
	 * to the canvas.
	 */
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

	//Runs on the MAIN thread

	/**
	 * Generates a suggested requestId to be used
	 * with getAlbumArt()
	 */
	public int getNextRequestId() {
		return ++nextRequestId;
	}
}
