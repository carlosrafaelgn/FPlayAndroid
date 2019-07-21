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
package br.com.carlosrafaelgn.fplay.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;

import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.list.SongList;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

public final class SongView extends View implements View.OnClickListener, View.OnLongClickListener, AlbumArtFetcher.AlbumArtFetcherListener, Handler.Callback {
	private Song song;
	private String ellipsizedTitle, ellipsizedExtraInfo, numberAndCount;
	private int state, width, lengthX, lengthWidth, numberAndCountX, numberAndCountWidth, position, requestId, bitmapLeftPadding;
	private SongList baseList;
	private Handler handler;
	private ReleasableBitmapWrapper albumArt;
	private boolean pendingAlbumArtRequest;

	private static int albumArtHeight, height, textX, titleY, extraY, currentX, currentY, leftMargin, topMargin,
		rightMargin, rightMarginForDrawing, numberAndCountColor, numberAndCountColorSelected, numberAndCountColorFocused, iconLeftPadding,
		titlesp;

	public static int getViewHeight() {
		final int bottomMargin;
		if (UI.is3D) {
			switch (UI.songListScrollBarType) {
			case BgListView.SCROLLBAR_LARGE:
				if (UI.scrollBarToTheLeft) {
					leftMargin = 0;
					rightMarginForDrawing = UI.controlSmallMargin;
				} else {
					leftMargin = UI.controlSmallMargin;
					rightMarginForDrawing = 0;
				}
				break;
			default:
				leftMargin = UI.controlSmallMargin;
				rightMarginForDrawing = UI.controlSmallMargin;
				break;
			}
			topMargin = UI.controlSmallMargin;
			rightMargin = rightMarginForDrawing + UI.strokeSize;
			bottomMargin = UI.strokeSize;
		} else {
			leftMargin = 0;
			topMargin = 0;
			rightMargin = 0;
			rightMarginForDrawing = 0;
			bottomMargin = 0;
		}
		final int titlespBox, titlespYinBox;
		if (UI.albumArtSongList && !UI.isLargeScreen && !UI.largeTextIs22sp) {
			titlesp = UI._18sp;
			titlespBox = UI._18spBox;
			titlespYinBox = UI._18spYinBox;
		} else {
			titlesp = UI._Headingsp;
			titlespBox = UI._HeadingspBox;
			titlespYinBox = UI._HeadingspYinBox;
		}
		albumArtHeight = (UI._1dp << 1) + (UI.verticalMargin << 1) + titlespBox + UI._14spBox;
		height = albumArtHeight + topMargin + bottomMargin;
		textX = leftMargin + UI.controlMargin + (UI.albumArtSongList ? height : 0);
		titleY = UI.verticalMargin + titlespYinBox + topMargin;
		extraY = UI.verticalMargin + UI._1dp + titlespBox + UI._14spYinBox + topMargin;
		currentY = height - UI.defaultControlContentsSize - UI.controlXtraSmallMargin - bottomMargin;
		iconLeftPadding = leftMargin + ((albumArtHeight - UI.defaultControlContentsSize) >> 1);
		return height;
	}

	public SongView(Context context) {
		super(context);
		setOnClickListener(this);
		setOnLongClickListener(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
		getViewHeight();
		numberAndCountColor = ColorUtils.blend(UI.color_text_listitem, UI.color_list, 0.5f);
		numberAndCountColorSelected = ColorUtils.blend(UI.color_text_selected, UI.color_selected, 0.5f);
		numberAndCountColorFocused = ColorUtils.blend(UI.color_text_selected, UI.color_focused, 0.5f);
		super.setDrawingCacheEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			super.setDefaultFocusHighlightEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			super.setPointerIcon(PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND));
	}

	private void processEllipsis() {
		final int w = lengthX - textX - UI.controlMargin;
		ellipsizedTitle = UI.ellipsizeText(song.title, titlesp, w, false);
		ellipsizedExtraInfo = UI.ellipsizeText(song.extraInfo, UI._14sp, (numberAndCount == null) ? w : (numberAndCountX - textX - UI.controlMargin), false);
	}

	public void updateIfCurrent() {
		if ((state & UI.STATE_CURRENT) != 0) {
			processEllipsis();
			invalidate();
		}
	}

	//apparently there have been changes to Android, and it is forbidden to override getContentDescription() now...
	//@Override
	//public CharSequence getContentDescription() {
	//	if (song != null)
	//		return song.title;
	//	return super.getContentDescription();
	//}

	//@Override
	//public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
	//	super.onInitializeAccessibilityEvent(event);
	//	event.setContentDescription(getContentDescription());
	//}

	public void setItemState(Song song, int position, int state, SongList baseList) {
		this.state = state; //(this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		this.position = position;
		this.baseList = baseList;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.song != song) {
			this.song = song;
			if (UI.isAccessibilityManagerEnabled)
				setContentDescription(song.title);
			final AlbumArtFetcher albumArtFetcher;
			if (UI.albumArtSongList && (albumArtFetcher = baseList.albumArtFetcher) != null) {
				if (pendingAlbumArtRequest) {
					//just to invalidate a possible response from albumArtFetcher
					pendingAlbumArtRequest = false;
					albumArtFetcher.cancelRequest(requestId, this);
					requestId++;
				}
				//a few devices detach views from the listview before recycling, hence attaching
				//them back to the window without recreating the object
				if (handler == null)
					handler = new Handler(Looper.getMainLooper(), this);
				if (albumArt != null) {
					albumArt.release();
					albumArt = null;
				}
				if (!song.isHttp && (song.albumId == null || song.albumId != 0L)) {
					requestId = albumArtFetcher.getNextRequestId();
					albumArt = albumArtFetcher.getAlbumArt(song.albumId, albumArtHeight, requestId, this);
					if (!(pendingAlbumArtRequest = (albumArt == null)))
						bitmapLeftPadding = leftMargin + ((albumArtHeight - albumArt.width) >> 1);
				}
			}
		} else if (!UI.displaySongNumberAndCount) {
			return;
		}
		lengthWidth = (song.isHttp ? UI._14spBox : UI.measureText(song.length, UI._14sp));
		lengthX = width - lengthWidth - UI.controlMargin - rightMargin;
		if (!UI.displaySongNumberAndCount || ((state & UI.STATE_CURRENT) != 0)) {
			numberAndCount = null;
		} else {
			numberAndCount = (position + 1) + " / " + baseList.getCount();
			numberAndCountWidth = UI.measureText(numberAndCount, UI._14sp);
			numberAndCountX = width - numberAndCountWidth - UI.controlMargin - rightMargin;
		}
		processEllipsis();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
		super.setBackground(null);
	}

	@SuppressWarnings("deprecation")
	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background) {
		super.setBackgroundDrawable(null);
	}

	@Override
	public void setBackgroundResource(int resid) {
		super.setBackgroundResource(0);
	}

	@Override
	public void setBackgroundColor(int color) {
		super.setBackgroundResource(0);
	}

	@Override
	public Drawable getBackground() {
		return null;
	}

	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return false;
	}

	@Override
	public boolean hasOverlappingRendering() {
		return (UI.is3D || (state != 0));
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, this);
	}

	@Override
	protected int getSuggestedMinimumHeight() {
		return height;
	}

	@Override
	public int getMinimumHeight() {
		return height;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), height);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (width != w) {
			width = w;
			currentX = w - UI.defaultControlContentsSize - rightMargin;
			lengthX = w - lengthWidth - UI.controlMargin - rightMargin;
			if (numberAndCount != null)
				numberAndCountX = w - numberAndCountWidth - UI.controlMargin - rightMargin;
			processEllipsis();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (ellipsizedTitle == null)
			return;
		final int txtColor = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem : UI.color_text_selected);
		final Rect rect = UI.rect;
		getDrawingRect(rect);
		rect.left += leftMargin;
		rect.top += topMargin;
		rect.right -= rightMarginForDrawing;
		UI.drawBgListItem(rect, canvas, state | ((state & UI.STATE_SELECTED & BgListView.extraState) >>> 2));
		if (UI.albumArtSongList) {
			if (albumArt != null && albumArt.bitmap != null)
				canvas.drawBitmap(albumArt.bitmap, bitmapLeftPadding, topMargin + ((albumArtHeight - albumArt.height) >> 1), null);
			else if (song.isHttp)
				TextIconDrawable.drawIcon(canvas, UI.ICON_RADIO, iconLeftPadding, topMargin + ((albumArtHeight - UI.defaultControlContentsSize) >> 1), UI.defaultControlContentsSize, ((state & ~UI.STATE_CURRENT) != 0) ? UI.color_text_selected : UI.color_text_listitem_secondary);
			else if (!pendingAlbumArtRequest)
				TextIconDrawable.drawIcon(canvas, UI.ICON_ALBUMART, iconLeftPadding, topMargin + ((albumArtHeight - UI.defaultControlContentsSize) >> 1), UI.defaultControlContentsSize, ((state & ~UI.STATE_CURRENT) != 0) ? UI.color_text_selected : UI.color_text_listitem_secondary);
		}
		if ((state & UI.STATE_CURRENT) != 0)
			TextIconDrawable.drawIcon(canvas, UI.ICON_FPLAY, currentX, currentY, UI.defaultControlContentsSize, ((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
		else if (numberAndCount != null)
			UI.drawText(canvas, numberAndCount, ((state & ~UI.STATE_CURRENT) == 0) ? numberAndCountColor : (((state & UI.STATE_FOCUSED) != 0 && (state & UI.STATE_HOVERED) == 0) ? numberAndCountColorFocused : numberAndCountColorSelected), UI._14sp, numberAndCountX, extraY);
		UI.drawText(canvas, ellipsizedTitle, txtColor, titlesp, textX, titleY);
		if (song.isHttp)
			TextIconDrawable.drawIcon(canvas, UI.ICON_RADIO, lengthX, UI.verticalMargin + topMargin, UI._14spBox, txtColor);
		else
			UI.drawText(canvas, song.length, txtColor, UI._14sp, lengthX, UI.verticalMargin + UI._14spYinBox + topMargin);
		UI.drawText(canvas, ellipsizedExtraInfo, txtColor, UI._14sp, textX, extraY);
	}

	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}

	@Override
	protected void onDetachedFromWindow() {
		//just to invalidate a possible response from albumArtFetcher
		pendingAlbumArtRequest = false;
		requestId++;
		handler = null;
		if (albumArt != null) {
			albumArt.release();
			albumArt = null;
		}
		song = null;
		ellipsizedTitle = null;
		ellipsizedExtraInfo = null;
		baseList = null;
		super.onDetachedFromWindow();
	}

	@Override
	public void onClick(View view) {
		BaseList.ItemClickListener itemClickListener;
		if (baseList != null && (itemClickListener = baseList.getItemClickListener()) != null)
			itemClickListener.onItemClicked(position);
	}

	@Override
	public boolean onLongClick(View view) {
		BaseList.ItemClickListener itemClickListener;
		if (baseList != null && (itemClickListener = baseList.getItemClickListener()) != null)
			itemClickListener.onItemLongClicked(position);
		return true;
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
			((event.getButtonState() & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_TERTIARY | MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0) &&
			(event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_UP)) {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
				onLongClick(this);
			return true;
		}
		return super.onGenericMotionEvent(event);
	}

	//Runs on a SECONDARY thread
	@Override
	public void albumArtFetched(ReleasableBitmapWrapper bitmap, int requestId) {
		//check if we have already been removed from the window
		final Handler h = handler;
		if (requestId != this.requestId || h == null)
			return;
		if (bitmap != null)
			bitmap.addRef();
		h.sendMessageAtTime(Message.obtain(h, requestId, bitmap), SystemClock.uptimeMillis());
	}

	//Runs on a SECONDARY thread
	@Override
	public String albumArtUriForRequestId(int requestId) {
		final Song song = this.song;
		return ((requestId == this.requestId && song != null) ? song.albumArtUri : null);
	}

	//Runs on a SECONDARY thread
	@Override
	public Long albumIdForRequestId(int requestId) {
		final Song song = this.song;
		return ((requestId == this.requestId && song != null) ? song.albumId : null);
	}

	//Runs on a SECONDARY thread
	@Override
	public String fileUriForRequestId(int requestId) {
		final Song song = this.song;
		return ((requestId == this.requestId && song != null) ? song.path : null);
	}

	//Runs on a SECONDARY thread
	@Override
	public long artistIdForRequestId(int requestId) {
		return 0;
	}

	//Runs on the MAIN thread
	@Override
	public boolean handleMessage(Message msg) {
		final ReleasableBitmapWrapper bitmap = (ReleasableBitmapWrapper)msg.obj;
		msg.obj = null;
		//check if we have already been removed from the window
		if (msg.what != requestId || handler == null || bitmap == null || song == null) {
			if (bitmap != null)
				bitmap.release();
			if (albumArt != null) {
				albumArt.release();
				albumArt = null;
			}
			if (msg.what == requestId) {
				pendingAlbumArtRequest = false;
				if (handler != null && song != null) {
					song.albumId = AlbumArtFetcher.zeroAlbumId;
					invalidate();
				}
			}
			return true;
		}
		pendingAlbumArtRequest = false;
		if (albumArt != null)
			albumArt.release();
		albumArt = bitmap;
		song.albumId = bitmap.albumId;
		song.albumArtUri = bitmap.albumArtUri;
		bitmapLeftPadding = leftMargin + ((albumArtHeight - bitmap.width) >> 1);
		invalidate();
		return true;
	}
}
