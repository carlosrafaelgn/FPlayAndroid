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

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

public final class FileView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener, AlbumArtFetcher.AlbumArtFetcherListener, Handler.Callback {
	private AlbumArtFetcher albumArtFetcher;
	private Handler handler;
	private ReleasableBitmapWrapper albumArt;
	private FileSt file;
	private BgButton btnCheckbox;
	private String icon, ellipsizedName;
	private boolean pendingAlbumArtRequest, checkBoxVisible;
	private final boolean hasCheckbox;
	private int state, width, position, requestId, bitmapLeftPadding, leftPadding;
	
	private static int height, usableHeight;

	public static int getViewHeight() {
		height = (UI.verticalMargin << 1) + Math.max(UI.defaultControlContentsSize, UI._LargeItemspBox);
		usableHeight = height - (UI.isDividerVisible ? UI.strokeSize : 0);
		return height;
	}

	public FileView(Context context, AlbumArtFetcher albumArtFetcher, boolean hasCheckbox) {
		super(context);
		this.albumArtFetcher = albumArtFetcher;
		if (albumArtFetcher != null)
			handler = new Handler(Looper.getMainLooper(), this);
		setOnClickListener(this);
		setOnLongClickListener(this);
		setBaselineAligned(false);
		setGravity(Gravity.RIGHT);
		getViewHeight();
		if (hasCheckbox) {
			LayoutParams p;
			btnCheckbox = new BgButton(context);
			btnCheckbox.setHideBorders(true);
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			p.leftMargin = UI._8dp;
			p.bottomMargin = (UI.isDividerVisible ? UI.strokeSize : 0);
			btnCheckbox.setLayoutParams(p);
			btnCheckbox.formatAsPlainCheckBox(false, true, false);
			btnCheckbox.setContentDescription(context.getText(R.string.unselect), context.getText(R.string.select));
			btnCheckbox.setTextColor(UI.colorState_text_listitem_reactive);
			btnCheckbox.setOnClickListener(this);
			addView(btnCheckbox);
		} else {
			btnCheckbox = null;
		}
		this.hasCheckbox = hasCheckbox;
		this.checkBoxVisible = hasCheckbox;
		super.setDrawingCacheEnabled(false);
	}
	
	private void processEllipsis() {
		ellipsizedName = UI.ellipsizeText(file.name, UI._LargeItemsp, width - leftPadding - (hasCheckbox ? (UI.defaultControlContentsSize + (UI._8dp << 1)) : 0) - UI._8dp, true);
	}
	
	public void refreshItem() {
		//tiny workaround to force complete execution of setItemState()
		checkBoxVisible = !checkBoxVisible;
		setItemState(file, position, state);
		invalidate();
	}

	public static String makeContextDescription(boolean addDetailedDescription, Context ctx, FileSt file) {
		if (!addDetailedDescription)
			return (file.isDirectory ? (ctx.getText(R.string.folder) + " " + file.name) : file.name);
		String initial = "";
		if (file.isDirectory) {
			switch (file.specialType) {
			case FileSt.TYPE_ARTIST:
				initial = ctx.getText(R.string.artist).toString();
				break;
			case FileSt.TYPE_ALBUM:
			case FileSt.TYPE_ALBUM_ITEM:
				initial = ctx.getText(R.string.album).toString();
				break;
			default:
				initial = ctx.getText(R.string.folder).toString();
				break;
			}
		}
		return initial + " " + file.name + ": " + ctx.getText(file.isChecked ? R.string.selected : R.string.unselected);
	}

	@Override
	public CharSequence getContentDescription() {
		if (file != null)
			return makeContextDescription(hasCheckbox, getContext(), file);
		return super.getContentDescription();
	}

	public void setItemState(FileSt file, int position, int state) {
		if (file == null)
			return;
		final int specialType = file.specialType;
		final boolean showCheckbox = (hasCheckbox && ((specialType == 0) || (specialType == FileSt.TYPE_ALBUM) || (specialType == FileSt.TYPE_ALBUM_ITEM) || (specialType == FileSt.TYPE_ARTIST)));
		final boolean specialTypeChanged = ((this.file != null) && (this.file.specialType != specialType));
		this.position = position;
		if (btnCheckbox != null) {
			if (specialTypeChanged || this.file != file || (this.state & UI.STATE_SELECTED) != (state & UI.STATE_SELECTED))
				//btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : ((specialType == FileSt.TYPE_ALBUM_ITEM) ? UI.colorState_text_highlight_reactive : UI.colorState_text_listitem_reactive));
				btnCheckbox.setTextColor(((state != 0) || (specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
				//btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : ((specialType == FileSt.TYPE_ALBUM_ITEM) ? UI.colorState_text_reactive : UI.colorState_text_listitem_reactive));
			btnCheckbox.setChecked(file.isChecked);
		}
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.file == file && checkBoxVisible == showCheckbox)
			return;
		this.file = file;
		if (checkBoxVisible != showCheckbox) {
			checkBoxVisible = showCheckbox;
			if (btnCheckbox != null)
				btnCheckbox.setVisibility(showCheckbox ? View.VISIBLE : View.GONE);
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
			ReleasableBitmapWrapper newAlbumArt;
			switch (specialType) {
			case FileSt.TYPE_INTERNAL_STORAGE:
				icon = UI.ICON_SCREEN;
				break;
			case FileSt.TYPE_ALL_FILES:
				icon = UI.ICON_ROOT;
				break;
			case FileSt.TYPE_EXTERNAL_STORAGE:
				icon = UI.ICON_SD;
				break;
			case FileSt.TYPE_EXTERNAL_STORAGE_USB:
				icon = UI.ICON_USB;
				break;
			case FileSt.TYPE_FAVORITE:
				icon = UI.ICON_FAVORITE_ON;
				break;
			case FileSt.TYPE_ARTIST_ROOT:
				icon = UI.ICON_MIC;
				break;
			case FileSt.TYPE_ARTIST:
				icon = UI.ICON_MIC;
				newAlbumArt = null;
				if (UI.albumArt && albumArtFetcher != null) {
					if (file.albumArt != null || file.artistIdForAlbumArt != 0) {
						newAlbumArt = albumArtFetcher.getAlbumArt(file, usableHeight, requestId, this);
						pendingAlbumArtRequest = (newAlbumArt == null);
					}
				}
				albumArt = newAlbumArt;
				break;
			case FileSt.TYPE_ALBUM_ROOT:
				icon = UI.ICON_ALBUMART;
				break;
			case FileSt.TYPE_ALBUM:
			case FileSt.TYPE_ALBUM_ITEM:
				icon = UI.ICON_ALBUMART;
				newAlbumArt = null;
				if (UI.albumArt && albumArtFetcher != null && file.albumArt != null) {
					newAlbumArt = albumArtFetcher.getAlbumArt(file, usableHeight, requestId, this);
					pendingAlbumArtRequest = (newAlbumArt == null);
				}
				albumArt = newAlbumArt;
				break;
			default:
				icon = UI.ICON_FOLDER;
				break;
			}
			if (albumArt != null) {
				bitmapLeftPadding = (usableHeight - albumArt.width) >> 1;
				leftPadding = usableHeight + UI._8dp;
			} else {
				switch (specialType) {
				case FileSt.TYPE_ARTIST:
				case FileSt.TYPE_ALBUM:
				case FileSt.TYPE_ALBUM_ITEM:
					if (UI.albumArt) {
						bitmapLeftPadding = (usableHeight - UI.defaultControlContentsSize) >> 1;
						leftPadding = usableHeight + UI._8dp;
						break;
					}
				default:
					bitmapLeftPadding = UI._8dp;
					leftPadding = UI._8dp + UI.defaultControlContentsSize + UI._8dp;
					break;
				}
			}
		} else {
			icon = null;
			leftPadding = UI._8dp;
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
		return false;//(state != 0);
	}
	
	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		final boolean old = (state == 0);
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
		if ((state == 0) != old && btnCheckbox != null)
			//btnCheckbox.setTextColor((state != 0) ? UI.colorState_text_selected_static : (((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_highlight_reactive : UI.colorState_text_listitem_reactive));
			btnCheckbox.setTextColor(((state != 0) || ((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM))) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
			//btnCheckbox.setTextColor((state != 0) ? UI.colorState_text_selected_static : (((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_reactive : UI.colorState_text_listitem_reactive));
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
			canvas.drawColor(UI.color_selected);
		final int st = state | ((state & UI.STATE_SELECTED & ((BgListView)getParent()).extraState) >>> 2);
		UI.drawBgBorderless(canvas, st, true);
		if (albumArt != null && albumArt.bitmap != null)
			canvas.drawBitmap(albumArt.bitmap, bitmapLeftPadding, (usableHeight - albumArt.height) >> 1, null);
		else if (icon != null)
			TextIconDrawable.drawIcon(canvas, icon, bitmapLeftPadding, (usableHeight - UI.defaultControlContentsSize) >> 1, UI.defaultControlContentsSize, ((st != 0) || albumItem) ? UI.color_text_selected : UI.color_text_listitem_secondary);
		UI.drawText(canvas, ellipsizedName, ((st != 0) || albumItem) ? UI.color_text_selected : UI.color_text_listitem, UI._LargeItemsp, leftPadding, ((usableHeight - UI._LargeItemspBox) >> 1) + UI._LargeItemspYinBox);
		super.dispatchDraw(canvas);
	}
	
	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}
	
	@Override
	protected void onDetachedFromWindow() {
		albumArtFetcher = null;
		handler = null;
		if (albumArt != null) {
			albumArt.release();
			albumArt = null;
		}
		file = null;
		btnCheckbox = null;
		ellipsizedName = null;
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onClick(View view) {
		if (checkBoxVisible && view == btnCheckbox) {
			file.isChecked = btnCheckbox.isChecked();
			if (UI.browserActivity != null)
				UI.browserActivity.processItemCheckboxClick(position);
		} else {
			if (UI.browserActivity != null)
				UI.browserActivity.processItemClick(position);
		}
	}
	
	@Override
	public boolean onLongClick(View view) {
		if (UI.browserActivity != null)
			UI.browserActivity.processItemLongClick(position);
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
	public FileSt fileForRequestId(int requestId) {
		return ((requestId == this.requestId) ? file : null);
	}
	
	//Runs on the MAIN thread
	@Override
	public boolean handleMessage(Message msg) {
		final ReleasableBitmapWrapper bitmap = (ReleasableBitmapWrapper)msg.obj;
		msg.obj = null;
		//check if we have already been removed from the window
		if (msg.what != requestId || albumArtFetcher == null || bitmap == null) {
			if (msg.what == requestId)
				pendingAlbumArtRequest = false;
			if (bitmap != null)
				bitmap.release();
			return true;
		}
		pendingAlbumArtRequest = false;
		if (albumArt != null)
			albumArt.release();
		albumArt = bitmap;
		bitmapLeftPadding = (usableHeight - bitmap.width) >> 1;
		invalidate();
		return true;
	}
}
