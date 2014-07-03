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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.LinearLayout;
import br.com.carlosrafaelgn.fplay.ActivityFileView;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

public final class FileView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener, AlbumArtFetcher.AlbumArtFetcherListener, Handler.Callback {
	private ActivityFileView observerActivity;
	private AlbumArtFetcher albumArtFetcher;
	private Handler handler;
	private final int height, verticalMargin, usableHeight;
	private ReleasableBitmapWrapper bitmap, albumArt, closedFolderIcon, internalIcon, externalIcon, favoriteIcon, artistIcon, albumIcon;
	private FileSt file;
	private BgButton btnAdd, btnPlay;
	private String ellipsizedName, albumArtUri;
	private boolean buttonsVisible, pendingAlbumArtRequest;
	private final boolean hasButtons, buttonIsCheckbox;
	private int state, width, position, requestId, bitmapLeftPadding, leftPadding;
	
	public FileView(Context context, ActivityFileView observerActivity, AlbumArtFetcher albumArtFetcher, ReleasableBitmapWrapper closedFolderIcon, ReleasableBitmapWrapper internalIcon, ReleasableBitmapWrapper externalIcon, ReleasableBitmapWrapper favoriteIcon, ReleasableBitmapWrapper artistIcon, ReleasableBitmapWrapper albumIcon, boolean hasButtons, boolean buttonIsCheckbox) {
		super(context);
		this.observerActivity = observerActivity;
		this.albumArtFetcher = albumArtFetcher;
		if (albumArtFetcher != null)
			handler = new Handler(Looper.getMainLooper(), this);
		setOnClickListener(this);
		setOnLongClickListener(this);
		setGravity(Gravity.RIGHT);
		this.closedFolderIcon = closedFolderIcon;
		this.internalIcon = internalIcon;
		this.externalIcon = externalIcon;
		this.favoriteIcon = favoriteIcon;
		this.artistIcon = artistIcon;
		this.albumIcon = albumIcon;
		if (closedFolderIcon != null)
			closedFolderIcon.addRef();
		if (internalIcon != null)
			internalIcon.addRef();
		if (externalIcon != null)
			externalIcon.addRef();
		if (favoriteIcon != null)
			favoriteIcon.addRef();
		if (artistIcon != null)
			artistIcon.addRef();
		if (albumIcon != null)
			albumIcon.addRef();
		verticalMargin = (UI.isVerticalMarginLarge ? UI._16sp : UI._8sp);
		height = (verticalMargin << 1) + Math.max(UI.defaultControlContentsSize, UI._22spBox);
		usableHeight = height - (UI.isDividerVisible ? UI.strokeSize : 0);
		if (hasButtons) {
			LayoutParams p;
			if (!buttonIsCheckbox) {
				btnAdd = new BgButton(context);
				p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
				p.bottomMargin = (UI.isDividerVisible ? UI.strokeSize : 0);
				btnAdd.setLayoutParams(p);
				btnAdd.setIcon(UI.ICON_ADD, true, false);
				btnAdd.setContentDescription(context.getText(R.string.add));
				btnAdd.setOnClickListener(this);
				btnAdd.setTextColor(UI.colorState_text_selected_static);
				addView(btnAdd);
			}
			btnPlay = new BgButton(context);
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			p.leftMargin = UI._8dp;
			p.bottomMargin = (UI.isDividerVisible ? UI.strokeSize : 0);
			btnPlay.setLayoutParams(p);
			if (buttonIsCheckbox) {
				btnPlay.setIcon(UI.ICON_OPTCHK, UI.ICON_OPTUNCHK, false, true, true, false);
				btnPlay.setTextColor(UI.colorState_text_listitem_reactive);
			} else {
				btnPlay.setIcon(UI.ICON_PLAY, true, false);
				btnPlay.setContentDescription(context.getText(R.string.play));
				btnPlay.setTextColor(UI.colorState_text_selected_static);
			}
			btnPlay.setOnClickListener(this);
			addView(btnPlay);
		} else {
			btnAdd = null;
			btnPlay = null;
		}
		this.hasButtons = hasButtons;
		this.buttonIsCheckbox = buttonIsCheckbox;
		buttonsVisible = hasButtons;
	}
	
	private void processEllipsis() {
		ellipsizedName = UI.ellipsizeText(file.name, UI._22sp, width - leftPadding - (buttonsVisible ? (buttonIsCheckbox ? (UI.defaultControlContentsSize + (UI._8dp << 1)) : ((UI.defaultControlContentsSize << 1) + (UI._8dp << 2))) : 0) - UI._8dp);
	}
	
	public void refreshItem() {
		//tiny workaround ;)
		buttonsVisible = !buttonsVisible;
		setItemState(file, position, state);
	}
	
	public void setItemState(FileSt file, int position, int state) {
		if (file == null)
			return;
		final int w = getWidth();
		final int specialType = file.specialType;
		final boolean showButtons = (hasButtons && ((specialType == 0) || (specialType == FileSt.TYPE_ALBUM) || (specialType == FileSt.TYPE_ALBUM_ITEM) || (specialType == FileSt.TYPE_ARTIST)) && (buttonIsCheckbox || ((state & UI.STATE_SELECTED) != 0)));
		final boolean specialTypeChanged = ((this.file != null) && (this.file.specialType != specialType));
		this.position = position;
		if (buttonIsCheckbox && btnPlay != null) {
			if (specialTypeChanged || this.file != file || (this.state & UI.STATE_SELECTED) != (state & UI.STATE_SELECTED))
				//btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : ((specialType == FileSt.TYPE_ALBUM_ITEM) ? UI.colorState_text_highlight_reactive : UI.colorState_text_listitem_reactive));
				btnPlay.setTextColor(((state != 0) || (specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
				//btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : ((specialType == FileSt.TYPE_ALBUM_ITEM) ? UI.colorState_text_reactive : UI.colorState_text_listitem_reactive));
			btnPlay.setChecked(file.isChecked);
		}
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.file == file && buttonsVisible == showButtons && width == w)
			return;
		this.file = file;
		this.width = w;
		if (buttonsVisible != showButtons) {
			buttonsVisible = showButtons;
			if (btnAdd != null)
				btnAdd.setVisibility(showButtons ? View.VISIBLE : View.GONE);
			if (btnPlay != null)
				btnPlay.setVisibility(showButtons ? View.VISIBLE : View.GONE);
		}
		if (pendingAlbumArtRequest && albumArtFetcher != null) {
			pendingAlbumArtRequest = false;
			albumArtFetcher.cancelRequest(requestId, this);
		}
		requestId++;
		if (albumArt != null) {
			albumArt.release();
			albumArt = null;
		}
		if (file.isDirectory) {
			switch (specialType) {
			case FileSt.TYPE_INTERNAL_STORAGE:
				bitmap = internalIcon;
				break;
			case FileSt.TYPE_EXTERNAL_STORAGE:
				bitmap = externalIcon;
				break;
			case FileSt.TYPE_FAVORITE:
				bitmap = favoriteIcon;
				break;
			case FileSt.TYPE_ARTIST:
			case FileSt.TYPE_ARTIST_ROOT:
				bitmap = artistIcon;
				break;
			case FileSt.TYPE_ALBUM_ROOT:
				bitmap = albumIcon;
				break;
			case FileSt.TYPE_ALBUM:
			case FileSt.TYPE_ALBUM_ITEM:
				ReleasableBitmapWrapper newAlbumArt = null;
				if (UI.albumArt && albumArtFetcher != null && file.albumArt != null) {
					albumArtUri = file.albumArt;
					newAlbumArt = albumArtFetcher.getAlbumArt(albumArtUri, usableHeight, requestId, this);
					pendingAlbumArtRequest = (newAlbumArt == null);
				}
				albumArt = newAlbumArt;
				bitmap = ((newAlbumArt == null) ? albumIcon : newAlbumArt);
				break;
			default:
				bitmap = closedFolderIcon;
				break;
			}
		} else {
			bitmap = null;
		}
		setContentDescription(file.name);
		if (albumArt != null) {
			bitmapLeftPadding = (usableHeight - albumArt.width) >> 1;
			leftPadding = usableHeight + UI._8dp;
		} else {
			bitmapLeftPadding = UI._8dp;
			leftPadding = ((bitmap != null) ? (UI._8dp + bitmap.width + UI._8dp) : UI._8dp);
		}
		processEllipsis();
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
		super.setBackground(null);
	}
	
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
	public void invalidateDrawable(Drawable drawable) {
	}
	
	@Override
	protected boolean verifyDrawable(Drawable drawable) {
		return false;
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return (state != 0);
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		final boolean old = (state == 0);
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
		if (buttonIsCheckbox && (state == 0) != old && btnPlay != null)
			//btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : (((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_highlight_reactive : UI.colorState_text_listitem_reactive));
			btnPlay.setTextColor(((state != 0) || ((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM))) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
			//btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : (((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_reactive : UI.colorState_text_listitem_reactive));
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
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		setMeasuredDimension(resolveSize(0, widthMeasureSpec), height);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (width != w) {
			width = w;
			processEllipsis();
		}
	}
	
	//Android Custom Layout - onDraw() never gets called
	//http://stackoverflow.com/questions/13056331/android-custom-layout-ondraw-never-gets-called
	@Override
	protected void dispatchDraw(Canvas canvas) {
		getDrawingRect(UI.rect);
		final boolean albumItem = ((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM));
		if (albumItem && (state == 0))
			//canvas.drawColor(UI.color_highlight);
			canvas.drawColor(UI.color_selected);
			//canvas.drawColor(UI.color_window);
		UI.drawBg(canvas, state | ((state & UI.STATE_SELECTED & ((BgListView)getParent()).extraState) >>> 2), false, true);
		if (bitmap != null && bitmap.bitmap != null)
			canvas.drawBitmap(bitmap.bitmap, bitmapLeftPadding, (usableHeight >> 1) - (bitmap.height >> 1), null);
		//UI.drawText(canvas, ellipsizedName, (state != 0) ? UI.color_text_selected : (albumItem ? UI.color_text_highlight : UI.color_text_listitem), UI._22sp, leftPadding, (usableHeight >> 1) - (UI._22spBox >> 1) + UI._22spYinBox);
		UI.drawText(canvas, ellipsizedName, ((state != 0) || albumItem) ? UI.color_text_selected : UI.color_text_listitem, UI._22sp, leftPadding, (usableHeight >> 1) - (UI._22spBox >> 1) + UI._22spYinBox);
		//UI.drawText(canvas, ellipsizedName, (state != 0) ? UI.color_text_selected : (albumItem ? UI.color_text : UI.color_text_listitem), UI._22sp, leftPadding, (usableHeight >> 1) - (UI._22spBox >> 1) + UI._22spYinBox);
		super.dispatchDraw(canvas);
	}
	
	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}
	
	@Override
	protected void onDetachedFromWindow() {
		observerActivity = null;
		albumArtFetcher = null;
		handler = null;
		bitmap = null;
		if (albumArt != null) {
			albumArt.release();
			albumArt = null;
		}
		if (closedFolderIcon != null) {
			closedFolderIcon.release();
			closedFolderIcon = null;
		}
		if (internalIcon != null) {
			internalIcon.release();
			internalIcon = null;
		}
		if (externalIcon != null) {
			externalIcon.release();
			externalIcon = null;
		}
		if (favoriteIcon != null) {
			favoriteIcon.release();
			favoriteIcon = null;
		}
		if (artistIcon != null) {
			artistIcon.release();
			artistIcon = null;
		}
		if (albumIcon != null) {
			albumIcon.release();
			albumIcon = null;
		}
		file = null;
		btnAdd = null;
		btnPlay = null;
		ellipsizedName = null;
		albumArtUri = null;
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onClick(View view) {
		if (hasButtons && (view == btnAdd || view == btnPlay)) {
			if (buttonIsCheckbox && file != null && view == btnPlay)
				file.isChecked = btnPlay.isChecked();
			if (observerActivity != null)
				observerActivity.processItemButtonClick(position, view == btnAdd);
		} else {
			if (observerActivity != null)
				observerActivity.processItemClick(position);
		}
	}
	
	@Override
	public boolean onLongClick(View view) {
		if (observerActivity != null)
			observerActivity.processItemLongClick(position);
		return true;
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
		return ((requestId == this.requestId) ? albumArtUri : null);
	}
	
	//Runs on the MAIN thread
	@Override
	public boolean handleMessage(Message msg) {
		final ReleasableBitmapWrapper bitmap = (ReleasableBitmapWrapper)msg.obj;
		msg.obj = null;
		//check if we have already been removed from the window
		if (msg.what != requestId || albumArtFetcher == null || bitmap == null) {
			if (msg.what == requestId) {
				pendingAlbumArtRequest = false;
				albumArtUri = null;
			}
			if (bitmap != null)
				bitmap.release();
			return true;
		}
		pendingAlbumArtRequest = false;
		albumArtUri = null;
		if (albumArt != null)
			albumArt.release();
		albumArt = bitmap;
		this.bitmap = bitmap;
		bitmapLeftPadding = (usableHeight - bitmap.width) >> 1;
		leftPadding = usableHeight + UI._8dp;
		processEllipsis();
		invalidate();
		return true;
	}
}
