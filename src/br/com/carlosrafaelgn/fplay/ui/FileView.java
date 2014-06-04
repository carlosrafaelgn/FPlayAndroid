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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.LinearLayout;
import br.com.carlosrafaelgn.fplay.ActivityFileView;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.FileSt;

public final class FileView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener {
	private ActivityFileView observerActivity;
	private final int height, verticalMargin;
	private Bitmap closedFolderIcon, internalIcon, externalIcon, favoriteIcon, artistIcon, albumIcon;
	private Bitmap bitmap;
	private FileSt file;
	private BgButton btnAdd, btnPlay;
	private String ellipsizedName;
	private boolean buttonsVisible;
	private final boolean hasButtons, buttonIsCheckbox;
	private int state, width, position;
	
	public FileView(Context context, ActivityFileView observerActivity, Drawable closedFolderIcon, Drawable internalIcon, Drawable externalIcon, Drawable favoriteIcon, Drawable artistIcon, Drawable albumIcon, boolean hasButtons, boolean buttonIsCheckbox) {
		super(context);
		this.observerActivity = observerActivity;
		setOnClickListener(this);
		setOnLongClickListener(this);
		setGravity(Gravity.RIGHT);
		this.closedFolderIcon = ((closedFolderIcon == null) ? null : ((BitmapDrawable)closedFolderIcon).getBitmap());
		this.internalIcon = ((internalIcon == null) ? null : ((BitmapDrawable)internalIcon).getBitmap());
		this.externalIcon = ((externalIcon == null) ? null : ((BitmapDrawable)externalIcon).getBitmap());
		this.favoriteIcon = ((favoriteIcon == null) ? null : ((BitmapDrawable)favoriteIcon).getBitmap());
		this.artistIcon = ((artistIcon == null) ? null : ((BitmapDrawable)artistIcon).getBitmap());
		this.albumIcon = ((albumIcon == null) ? null : ((BitmapDrawable)albumIcon).getBitmap());
		verticalMargin = (UI.isVerticalMarginLarge ? UI._16sp : UI._8sp);
		height = (verticalMargin << 1) + Math.max(UI.defaultControlContentsSize, UI._22spBox);
		if (hasButtons) {
			LayoutParams p;
			if (!buttonIsCheckbox) {
				btnAdd = new BgButton(context);
				p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
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
			if (UI.isVerticalMarginLarge) {
				if (btnAdd != null)
					btnAdd.setHeight(height);
				btnPlay.setHeight(height);
			}
		} else {
			btnAdd = null;
			btnPlay = null;
		}
		this.hasButtons = hasButtons;
		this.buttonIsCheckbox = buttonIsCheckbox;
		buttonsVisible = hasButtons;
	}
	
	private void processEllipsis() {
		ellipsizedName = UI.ellipsizeText(file.name, UI._22sp, width - ((bitmap != null) ? ((UI._8dp << 1) + UI.defaultControlContentsSize) : UI._8dp) - (buttonsVisible ? (buttonIsCheckbox ? (UI.defaultControlContentsSize + (UI._8dp << 1)) : ((UI.defaultControlContentsSize << 1) + (UI._8dp << 2))) : 0) - UI._8dp);
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
				btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : ((specialType == FileSt.TYPE_ALBUM_ITEM) ? UI.colorState_text_highlight_reactive : UI.colorState_text_listitem_reactive));
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
			case FileSt.TYPE_ALBUM:
			case FileSt.TYPE_ALBUM_ROOT:
			case FileSt.TYPE_ALBUM_ITEM:
				bitmap = albumIcon;
				break;
			default:
				bitmap = closedFolderIcon;
				break;
			}
		} else {
			bitmap = null;
		}
		setContentDescription(file.name);
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
			btnPlay.setTextColor((state != 0) ? UI.colorState_text_selected_static : (((file != null) && (file.specialType == FileSt.TYPE_ALBUM_ITEM)) ? UI.colorState_text_highlight_reactive : UI.colorState_text_listitem_reactive));
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
			canvas.drawColor(UI.color_highlight);
		UI.drawBg(canvas, state | ((state & UI.STATE_SELECTED & ((BgListView)getParent()).extraState) >>> 2), UI.rect, false, true);
		if (bitmap != null)
			canvas.drawBitmap(bitmap, UI._8dp, (UI.rect.bottom >> 1) - (UI.defaultControlContentsSize >> 1), null);
		UI.drawText(canvas, ellipsizedName, (state != 0) ? UI.color_text_selected : (albumItem ? UI.color_text_highlight : UI.color_text_listitem), UI._22sp, (bitmap != null) ? ((UI._8dp << 1) + UI.defaultControlContentsSize) : UI._8dp, (UI.rect.bottom >> 1) - (UI._22spBox >> 1) + UI._22spYinBox);
		super.dispatchDraw(canvas);
	}
	
	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}
	
	@Override
	protected void onDetachedFromWindow() {
		observerActivity = null;
		//do not recycle these bitmaps, as they are shared!
		closedFolderIcon = null;
		internalIcon = null;
		externalIcon = null;
		favoriteIcon = null;
		artistIcon = null;
		albumIcon = null;
		bitmap = null;
		file = null;
		btnAdd = null;
		btnPlay = null;
		ellipsizedName = null;
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
}