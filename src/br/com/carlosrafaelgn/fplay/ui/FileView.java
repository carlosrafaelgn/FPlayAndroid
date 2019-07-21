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
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.view.ViewParent;
import android.widget.LinearLayout;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ReleasableBitmapWrapper;

public final class FileView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener, AlbumArtFetcher.AlbumArtFetcherListener, Handler.Callback {
	private AlbumArtFetcher albumArtFetcher;
	private Handler handler;
	private ReleasableBitmapWrapper albumArt;
	private FileSt file;
	private BgButton btnCheckbox;
	private String icon, ellipsizedName, secondaryText, ellipsizedSecondaryText, albumStr, albumsStr, trackStr, tracksStr;
	private boolean pendingAlbumArtRequest, checkBoxVisible;
	private final boolean hasCheckbox, force2D;
	private int state, width, position, requestId, bitmapLeftPadding, leftPadding, secondaryTextWidth, scrollBarType, leftMargin, rightMargin, rightMarginForDrawing;
	private final int height, usableHeight, iconY, nameYNoSecondary, nameY, secondaryY, topMargin, bottomMargin;
	private BaseList<?> baseList;

	public static int getViewHeight(boolean force2D) {
		final int topMargin, bottomMargin;
		if (!force2D && UI.is3D) {
			topMargin = UI.controlSmallMargin;
			bottomMargin = UI.strokeSize;
		} else {
			topMargin = 0;
			bottomMargin = 0;
		}
		return (UI.verticalMargin << 1) + Math.max(UI.defaultControlContentsSize, UI._Largesp + UI._14sp) + topMargin + bottomMargin;
	}

	public FileView(Context context) {
		this(context, true, false, UI.browserScrollBarType);
	}

	public FileView(Context context, boolean hasCheckbox, boolean force2D, int scrollBarType) {
		super(context);

		this.scrollBarType = scrollBarType;
		this.hasCheckbox = hasCheckbox;
		this.checkBoxVisible = hasCheckbox;
		this.force2D = force2D;

		updateHorizontalMargins();
		if (!force2D && UI.is3D) {
			topMargin = UI.controlSmallMargin;
			bottomMargin = UI.strokeSize;
		} else {
			topMargin = 0;
			bottomMargin = 0;
		}
		height = getViewHeight(force2D);
		usableHeight = height - (topMargin + bottomMargin);

		iconY = topMargin + ((usableHeight - UI.defaultControlContentsSize) >> 1);
		nameYNoSecondary = topMargin + ((usableHeight - UI._LargespBox) >> 1) + UI._LargespYinBox;
		final int sec = ((usableHeight - (UI._LargespBox + UI.controlMargin + UI._14spBox)) >> 1);
		nameY = topMargin + sec + UI._LargespYinBox;
		secondaryY = topMargin + sec + UI._LargespBox + UI._14spYinBox + UI.controlMargin;

		albumStr = context.getText(R.string.albumL).toString();
		albumsStr = " " + context.getText(R.string.albumsL);
		trackStr = context.getText(R.string.trackL).toString();
		tracksStr = " " + context.getText(R.string.tracksL);
		setOnClickListener(this);
		setOnLongClickListener(this);
		setBaselineAligned(false);
		setGravity(Gravity.END);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
		if (hasCheckbox) {
			LayoutParams p;
			btnCheckbox = new BgButton(context);
			btnCheckbox.setHideBorders(true);
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			p.leftMargin = UI.controlMargin;
			p.topMargin = topMargin;
			p.rightMargin = rightMargin;
			p.bottomMargin = bottomMargin;
			btnCheckbox.setLayoutParams(p);
			addView(btnCheckbox);
			btnCheckbox = null; //let setItemState() format the button...
		} else {
			btnCheckbox = null;
		}
		super.setDrawingCacheEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			super.setDefaultFocusHighlightEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			super.setPointerIcon(PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND));
	}

	private void processEllipsis() {
		ellipsizedName = UI.ellipsizeText(file.name, UI._Largesp, width - leftPadding - (checkBoxVisible ? UI.defaultControlSize : 0) - UI.controlMargin - rightMargin, true);
		if (secondaryText != null) {
			final int w = (checkBoxVisible ? (UI.defaultControlSize + UI.controlSmallMargin) : UI.controlMargin);
			ellipsizedSecondaryText = UI.ellipsizeText(secondaryText, UI._14sp, width - leftPadding - ((icon == null) ? 0 : UI.defaultControlContentsSize) - w - rightMargin, true);
			secondaryTextWidth = w + UI.measureText(ellipsizedSecondaryText, UI._14sp);
		}
	}

	private void updateHorizontalMargins() {
		final int extraLeftMargin, extraRightMargin;
		if (scrollBarType == BgListView.SCROLLBAR_INDEXED) {
			if (UI.scrollBarToTheLeft) {
				extraLeftMargin = UI.controlSmallMargin;
				extraRightMargin = 0;
			} else {
				extraLeftMargin = 0;
				extraRightMargin = UI.controlSmallMargin;
			}
		} else {
			extraLeftMargin = 0;
			extraRightMargin = 0;
		}
		if (!force2D && UI.is3D) {
			switch (scrollBarType) {
			case BgListView.SCROLLBAR_INDEXED:
			case BgListView.SCROLLBAR_LARGE:
				if (UI.scrollBarToTheLeft) {
					leftMargin = extraLeftMargin;
					rightMarginForDrawing = UI.controlSmallMargin + extraRightMargin;
				} else {
					leftMargin = UI.controlSmallMargin + extraLeftMargin;
					rightMarginForDrawing = extraRightMargin;
				}
				break;
			default:
				leftMargin = UI.controlSmallMargin + extraLeftMargin;
				rightMarginForDrawing = UI.controlSmallMargin + extraRightMargin;
				break;
			}
			rightMargin = rightMarginForDrawing + UI.strokeSize;
		} else {
			leftMargin = 0;
			rightMargin = 0;
			rightMarginForDrawing = 0;
		}
	}

	public void refreshItem() {
		//tiny workaround to force complete execution of setItemState()
		checkBoxVisible = !checkBoxVisible;
		setItemState(file, position, state, baseList, albumArtFetcher, scrollBarType);
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
		return initial + " " + file.name + UI.collon() + ctx.getText(file.isChecked ? R.string.selected : R.string.unselected);
	}

	public void setItemState(FileSt file, int position, int state, BaseList<?> baseList, AlbumArtFetcher albumArtFetcher, int scrollBarType) {
		if (file == null)
			return;
		final int specialType = file.specialType;
		final boolean showCheckbox = (hasCheckbox && ((specialType == 0) || (specialType == FileSt.TYPE_ALBUM) || (specialType == FileSt.TYPE_ALBUM_ITEM) || (specialType == FileSt.TYPE_ARTIST)));
		boolean specialTypeChanged = ((this.file != null) && (this.file.specialType != specialType));
		this.position = position;
		this.baseList = baseList;
		//a few devices detach views from the listview before recycling, hence attaching
		//them back to the window without recreating the object
		if (btnCheckbox == null && hasCheckbox && getChildCount() > 0 && (getChildAt(0) instanceof BgButton)) {
			specialTypeChanged = true;
			btnCheckbox = (BgButton)getChildAt(0);
			btnCheckbox.formatAsChildCheckBox(false, true, false);
			btnCheckbox.setContentDescription(getContext().getText(R.string.unselect), getContext().getText(R.string.select));
			btnCheckbox.setOnClickListener(this);
		}
		if (btnCheckbox != null) {
			if (specialTypeChanged || this.file != file || (this.state & UI.STATE_SELECTED) != (state & UI.STATE_SELECTED))
				btnCheckbox.setTextColor(((state != 0) || (specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
			btnCheckbox.setChecked(file.isChecked);
			if (this.scrollBarType != scrollBarType) {
				this.scrollBarType = scrollBarType;
				updateHorizontalMargins();

				final LayoutParams p = (LayoutParams)btnCheckbox.getLayoutParams();
				p.leftMargin = UI.controlMargin;
				p.topMargin = topMargin;
				p.rightMargin = rightMargin;
				p.bottomMargin = bottomMargin;
				btnCheckbox.setLayoutParams(p);
			}
		} else if (this.scrollBarType != scrollBarType) {
			this.scrollBarType = scrollBarType;
			updateHorizontalMargins();
		}
		this.state = state; //(this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.file == file && checkBoxVisible == showCheckbox)
			return;
		//refer to the comment above (about views being detached from the listview)
		if (albumArtFetcher != null) {
			this.albumArtFetcher = albumArtFetcher;
			if (handler == null)
				handler = new Handler(Looper.getMainLooper(), this);
		}
		secondaryText = null;
		ellipsizedSecondaryText = null;
		this.file = file;
		if (UI.isAccessibilityManagerEnabled)
			setContentDescription(makeContextDescription(hasCheckbox, getContext(), file));
		if (checkBoxVisible != showCheckbox) {
			checkBoxVisible = showCheckbox;
			if (btnCheckbox != null)
				btnCheckbox.setVisibility(showCheckbox ? View.VISIBLE : View.GONE);
		}
		if (albumArtFetcher != null) {
			if (pendingAlbumArtRequest) {
				pendingAlbumArtRequest = false;
				albumArtFetcher.cancelRequest(requestId, this);
			}
			requestId = albumArtFetcher.getNextRequestId();
		}
		if (albumArt != null) {
			albumArt.release();
			albumArt = null;
		}
		int albumCount, trackCount;
		if (file.isDirectory) {
			ReleasableBitmapWrapper newAlbumArt;
			switch (specialType) {
			case FileSt.TYPE_ARTIST:
				albumCount = file.albums;
				trackCount = file.tracks;
				if (albumCount >= 1 && trackCount >= 1)
					secondaryText = ((albumCount == 1) ? albumStr : (Integer.toString(albumCount) + albumsStr)) + " / " + ((trackCount == 1) ? trackStr : (Integer.toString(trackCount) + tracksStr));
				icon = UI.ICON_MIC;
				newAlbumArt = null;
				if (UI.albumArt && albumArtFetcher != null) {
					if ((file.albumId != null && file.albumId != 0L) || (file.albumId == null && file.artistIdForAlbumArt != 0)) {
						newAlbumArt = albumArtFetcher.getAlbumArt(file.albumId, usableHeight, requestId, this);
						pendingAlbumArtRequest = (newAlbumArt == null);
					}
				}
				albumArt = newAlbumArt;
				break;
			case FileSt.TYPE_ALBUM_ROOT:
				icon = UI.ICON_ALBUMART;
				break;
			case FileSt.TYPE_ALBUM:
				trackCount = file.tracks;
				if (trackCount >= 1)
					secondaryText = ((trackCount == 1) ? trackStr : (Integer.toString(trackCount) + tracksStr));
			case FileSt.TYPE_ALBUM_ITEM:
				icon = UI.ICON_ALBUMART;
				newAlbumArt = null;
				if (UI.albumArt && albumArtFetcher != null && file.albumId != null && file.albumId != 0L) {
					newAlbumArt = albumArtFetcher.getAlbumArt(file.albumId, usableHeight, requestId, this);
					pendingAlbumArtRequest = (newAlbumArt == null);
				}
				albumArt = newAlbumArt;
				break;
			case FileSt.TYPE_ICECAST:
				icon = UI.ICON_ICECAST;
				if (getContext() != null)
					secondaryText = getContext().getText(R.string.radio_directory).toString();
				break;
			case FileSt.TYPE_SHOUTCAST:
				icon = UI.ICON_SHOUTCAST;
				if (getContext() != null)
					secondaryText = getContext().getText(R.string.radio_directory).toString();
				break;
			case FileSt.TYPE_FPLAY_REMOTE_LIST:
				icon = UI.ICON_SHARE;
				break;
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
			default:
				icon = UI.ICON_FOLDER;
				break;
			}
			if (albumArt != null) {
				bitmapLeftPadding = leftMargin + ((usableHeight - albumArt.width) >> 1);
				leftPadding = leftMargin + (usableHeight + UI.controlMargin);
			} else {
				switch (specialType) {
				case FileSt.TYPE_ARTIST:
				case FileSt.TYPE_ALBUM:
				case FileSt.TYPE_ALBUM_ITEM:
					if (UI.albumArt) {
						bitmapLeftPadding = leftMargin + ((usableHeight - UI.defaultControlContentsSize) >> 1);
						leftPadding = leftMargin + (usableHeight + UI.controlMargin);
						break;
					}
				default:
					bitmapLeftPadding = leftMargin + UI.controlMargin;
					leftPadding = leftMargin + UI.defaultControlSize;
					break;
				}
			}
		} else {
			icon = null;
			leftPadding = leftMargin + UI.controlMargin;
			if (file.songInfo != null && file.songInfo.artist != null)
				secondaryText = file.songInfo.artist;
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
		final boolean old = (state == 0);
		state = UI.handleStateChanges(state, this) & (~UI.STATE_FOCUSED);
		if ((state == 0) != old && btnCheckbox != null)
			btnCheckbox.setTextColor(((state != 0) || ((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM))) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
	}

	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
		if (gainFocus) {
			final ViewParent parent = getParent();
			if (parent instanceof View)
				((View)parent).requestFocus();
		}
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
		setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), height);
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
		//Apparently, a few devices actually call dispatchDraw() with a null canvas...?!?!
		if (canvas == null || ellipsizedName == null)
			return;
		final Rect rect = UI.rect;
		getDrawingRect(rect);
		rect.left += leftMargin;
		rect.top += topMargin;
		rect.right -= rightMarginForDrawing;
		final int specialType = ((file == null) ? 0 : file.specialType);
		int st = state | ((state & UI.STATE_SELECTED & BgListView.extraState) >>> 2);
		if (specialType == FileSt.TYPE_ALBUM_ITEM)
			st |= UI.STATE_SELECTED;
		if (force2D)
			UI.drawBgListItem2D(rect, canvas, st);
		else
			UI.drawBgListItem(rect, canvas, st);
		if (albumArt != null && albumArt.bitmap != null)
			canvas.drawBitmap(albumArt.bitmap, bitmapLeftPadding, topMargin + ((usableHeight - albumArt.height) >> 1), null);
		else if (icon != null)
			TextIconDrawable.drawIcon(canvas, icon, bitmapLeftPadding, iconY, UI.defaultControlContentsSize, (st != 0) ? UI.color_text_selected : UI.color_text_listitem_secondary);
		if (ellipsizedSecondaryText == null) {
			UI.drawText(canvas, ellipsizedName, (st != 0) ? UI.color_text_selected : UI.color_text_listitem, UI._Largesp, leftPadding, nameYNoSecondary);
		} else {
			UI.drawText(canvas, ellipsizedName, (st != 0) ? UI.color_text_selected : UI.color_text_listitem, UI._Largesp, leftPadding, nameY);
			UI.drawText(canvas, ellipsizedSecondaryText, (st != 0) ? UI.color_text_selected : UI.color_text_listitem_secondary, UI._14sp, width - secondaryTextWidth - rightMargin, secondaryY);
		}
		super.dispatchDraw(canvas);
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
		file = null;
		btnCheckbox = null;
		ellipsizedName = null;
		secondaryText = null;
		ellipsizedSecondaryText = null;
		baseList = null;
		super.onDetachedFromWindow();
	}

	@Override
	public void onClick(View view) {
		BaseList.ItemClickListener itemClickListener;
		if (checkBoxVisible && view == btnCheckbox) {
			file.isChecked = btnCheckbox.isChecked();
			if (baseList != null && (itemClickListener = baseList.getItemClickListener()) != null)
				itemClickListener.onItemCheckboxClicked(position);
			if (UI.isAccessibilityManagerEnabled)
				setContentDescription(makeContextDescription(hasCheckbox, getContext(), file));
		} else {
			if (baseList != null && (itemClickListener = baseList.getItemClickListener()) != null)
				itemClickListener.onItemClicked(position);
		}
	}

	@Override
	public boolean onLongClick(View view) {
		BaseList.ItemClickListener itemClickListener;
		if (baseList != null && (itemClickListener = baseList.getItemClickListener()) != null)
			itemClickListener.onItemLongClicked(position);
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
		final FileSt file = this.file;
		return ((requestId == this.requestId && file != null) ? file.albumArtUri : null);
	}

	//Runs on a SECONDARY thread
	@Override
	public Long albumIdForRequestId(int requestId) {
		final FileSt file = this.file;
		return ((requestId == this.requestId && file != null) ? file.albumId : null);
	}

	//Runs on a SECONDARY thread
	@Override
	public String fileUriForRequestId(int requestId) {
		return null;
	}

	//Runs on a SECONDARY thread
	@Override
	public long artistIdForRequestId(int requestId) {
		final FileSt file = this.file;
		return ((requestId == this.requestId && file != null) ? file.artistIdForAlbumArt : 0);
	}

	//Runs on the MAIN thread
	@Override
	public boolean handleMessage(Message msg) {
		final ReleasableBitmapWrapper bitmap = (ReleasableBitmapWrapper)msg.obj;
		msg.obj = null;
		//check if we have already been removed from the window
		if (msg.what != requestId || handler == null || bitmap == null || file == null) {
			if (bitmap != null)
				bitmap.release();
			if (albumArt != null) {
				albumArt.release();
				albumArt = null;
			}
			if (msg.what == requestId) {
				pendingAlbumArtRequest = false;
				if (handler != null && file != null) {
					file.albumId = AlbumArtFetcher.zeroAlbumId;
					invalidate();
				}
			}
			return true;
		}
		pendingAlbumArtRequest = false;
		if (albumArt != null)
			albumArt.release();
		albumArt = bitmap;
		file.albumId = bitmap.albumId;
		file.albumArtUri = bitmap.albumArtUri;
		bitmapLeftPadding = leftMargin + ((usableHeight - bitmap.width) >> 1);
		invalidate();
		return true;
	}
}
