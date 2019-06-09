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
package br.com.carlosrafaelgn.fplay.visualizer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Message;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;

import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.plugin.SongInfo;
import br.com.carlosrafaelgn.fplay.plugin.Visualizer;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

/**
 * View class to get the Album art for a music file.
 */
public final class AlbumArtVisualizer extends View implements Visualizer, MainHandler.Callback, AlbumArtFetcher.AlbumArtFetcherListener {
	private static final int MSG_IMAGE_LOADED = 0x0600;

	private final Object sync;
	private final int absMax;
	private int width, height, minSize, color1, color2;
	private Point point;
	private Rect srcRect, dstRect;
	private AlbumArtFetcher albumArtFetcher;
	private ReleasableBitmapWrapper bmp;
	private long currentSongInfoId;
	private volatile int version;
	private volatile String nextPath;
	private volatile ReleasableBitmapWrapper nextBmp;

	/**
	 * Constructor for the class. It calculates the size of the album art
	 * depending on the device screen width and height with bounds
	 * set from 100 dp to 300 dp.
	 *
	 * @param activity The calling activity
	 * @param landscape N/A
	 * @param extras N/A
	 */
	public AlbumArtVisualizer(Activity activity, boolean landscape, Intent extras) {
		super(activity);
		sync = new Object();
		absMax = Math.max(UI.dpToPxI(100.0f), Math.min(Math.min(UI.dpToPxI(300.0f), (UI.usableScreenWidth * 80) / 100), (UI.usableScreenHeight * 80 / 100)));
		point = new Point();
		srcRect = new Rect();
		dstRect = new Rect();
		albumArtFetcher = new AlbumArtFetcher();
		color1 = UI.colorState_text_visualizer_static.getDefaultColor();
		color2 = ColorUtils.blend(color1, UI.color_visualizer565, 0.5f);
		currentSongInfoId = -1;
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public final boolean isOpaque() {
		return false;
	}

	//Runs on the MAIN thread
	@Override
	public void onActivityResult(int requestCode, int resultCode, Object intent) {
	}

	//Runs on the MAIN thread
	@Override
	public void onActivityPause() {
	}

	//Runs on the MAIN thread
	@Override
	public void onActivityResume() {
		postInvalidateDelayed(100);
	}

	//Runs on the MAIN thread
	@Override
	public void onCreateContextMenu(Object contextMenu) {
	}

	//Runs on the MAIN thread
	@Override
	public void onClick() {
	}

	private void releaseBitmapsAndSetCurrentSong(SongInfo songInfo) {
		synchronized (sync) {
			version++;
			currentSongInfoId = (songInfo == null ? -1 : songInfo.id);
			if (bmp != null) {
				bmp.release();
				bmp = null;
			}
			if (nextBmp != null) {
				nextBmp.release();
				nextBmp = null;
			}
		}
	}

	//Runs on the MAIN thread

	/**
	 *  Update the album art when song changes or queue ends.
	 * @param currentSongInfo Song that needs to be played
	 * @param songHasChanged Flag that indicates whether the current song has changed or not
	 * @param ex Exception
	 */
	@Override
	public void onPlayerChanged(SongInfo currentSongInfo, boolean songHasChanged, Throwable ex) {
		if (currentSongInfo == null) {
			releaseBitmapsAndSetCurrentSong(null);
			updateRects();
		} else if ((currentSongInfoId == -1 || currentSongInfoId != currentSongInfo.id) && albumArtFetcher != null) {
			releaseBitmapsAndSetCurrentSong(currentSongInfo);
			updateRects(); //force the album icon to be displayed
			nextPath = currentSongInfo.path;
			albumArtFetcher.getAlbumArtForFile(0, version, this);
		}
		invalidate();
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return false;
	}

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Object getDesiredSize(int availableWidth, int availableHeight) {
		point.x = availableWidth;
		point.y = availableHeight;
		return point;
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean requiresHiddenControls() {
		return false;
	}

	//Runs on ANY thread
	@Override
	public int requiredDataType() {
		return DATA_NONE;
	}

	//Runs on ANY thread
	@Override
	public int requiredOrientation() {
		return ORIENTATION_NONE;
	}

	//Runs on a SECONDARY thread
	@Override
	public void load() {
	}
	
	//Runs on ANY thread
	@Override
	public boolean isLoading() {
		return false;
	}
	
	//Runs on ANY thread
	@Override
	public void cancelLoading() {
	}
	
	//Runs on the MAIN thread
	@Override
	public void configurationChanged(boolean landscape) {
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void processFrame(boolean playing, byte[] waveform) {
	}
	
	//Runs on a SECONDARY thread
	@Override
	public void release() {
	}

	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
		releaseBitmapsAndSetCurrentSong(null);
		if (albumArtFetcher != null) {
			albumArtFetcher.stopAndCleanup();
			albumArtFetcher = null;
		}
		point = null;
		srcRect = null;
		dstRect = null;
		nextPath = null;
	}

	private void updateRects() {
		if (width <= 0 || height <= 0 || srcRect == null || dstRect == null)
			return;

		if (bmp == null) {
			//we will draw the icon instead of the bitmap
			dstRect.left = (width - minSize) >> 1;
			dstRect.top = (height - minSize) >> 1;
			dstRect.right = dstRect.left + minSize;
			dstRect.bottom = dstRect.top + minSize;
			return;
		}

		srcRect.left = 0;
		srcRect.top = 0;
		srcRect.right = bmp.width;
		srcRect.bottom = bmp.height;

		final int other;
		//if we are missing the size by a handful of pixels, let's just
		//stretch the image a little bit... ;)
		if (Math.abs(bmp.width - bmp.height) <= UI._4dp) {
			dstRect.left = (width - minSize) >> 1;
			dstRect.top = (height - minSize) >> 1;
			dstRect.right = dstRect.left + minSize;
			dstRect.bottom = dstRect.top + minSize;
		} else if (bmp.width > bmp.height) {
			other = (bmp.height * minSize) / bmp.width;
			dstRect.left = (width - minSize) >> 1;
			dstRect.top = (height - other) >> 1;
			dstRect.right = dstRect.left + minSize;
			dstRect.bottom = dstRect.top + other;
		} else {
			other = (bmp.width * minSize) / bmp.height;
			dstRect.left = (width - other) >> 1;
			dstRect.top = (height - minSize) >> 1;
			dstRect.right = dstRect.left + other;
			dstRect.bottom = dstRect.top + minSize;
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		width = w;
		height = h;
		minSize = ((width <= height) ? width : height);
		//add a 5% margin around the bitmap
		if (minSize > UI.controlLargeMargin)
			minSize -= ((minSize * 5) / 100);
		if (minSize > absMax)
			minSize = absMax;
		updateRects();
		postInvalidateDelayed(100);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (srcRect == null || dstRect == null)
			return;
		if (bmp != null) {
			canvas.drawBitmap(bmp.bitmap, srcRect, dstRect, null);
		} else {
			TextIconDrawable.drawIcon(canvas, UI.ICON_ALBUMART, dstRect.left + UI._1dp, dstRect.top + UI._1dp, minSize, color2);
			TextIconDrawable.drawIcon(canvas, UI.ICON_ALBUMART, dstRect.left, dstRect.top, minSize, color1);
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_IMAGE_LOADED:
			//Runs on the MAIN thread
			if (msg.arg1 != version)
				break;
			if (bmp != null) {
				bmp.release();
				bmp = null;
			}
			synchronized (sync) {
				version++;
				if (nextBmp == null || nextBmp.width <= 0 || nextBmp.height <= 0 || nextBmp.bitmap == null) {
					if (nextBmp != null)
						nextBmp.release();
				} else {
					bmp = nextBmp;
				}
				nextBmp = null;
			}
			updateRects();
			invalidate();
			break;
		}
		postInvalidateDelayed(100);
		return true;
	}

	//Runs on a SECONDARY thread

	/**
	 * Update next bitmap when the bitmap is available and send a message to
	 * the MainHandler.
	 *
	 * @param bitmap ReleasableBitmapWrapper for the next song
	 * @param requestId requestId for the bitmap
	 */
	@Override
	public void albumArtFetched(ReleasableBitmapWrapper bitmap, int requestId) {
		if (bitmap == null)
			return;
		synchronized (sync) {
			if (version == requestId) {
				bitmap.addRef();
				if (nextBmp != null)
					nextBmp.release();
				nextBmp = bitmap;
				MainHandler.sendMessage(this, MSG_IMAGE_LOADED, requestId, 0);
			}
		}
	}

	//Runs on a SECONDARY thread
	@Override
	public String albumArtUriForRequestId(int requestId) {
		return null;
	}

	//Runs on a SECONDARY thread
	@Override
	public Long albumIdForRequestId(int requestId) {
		return null;
	}

	//Runs on a SECONDARY thread
	@Override
	public String fileUriForRequestId(int requestId) {
		synchronized (sync) {
			return ((version == requestId) ? nextPath : null);
		}
	}

	//Runs on a SECONDARY thread
	@Override
	public long artistIdForRequestId(int requestId) {
		return 0;
	}
}
