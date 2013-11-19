//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013, Carlos Rafael Gimenes das Neves
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
	private final Bitmap closedFolderIcon, internalIcon, externalIcon, favoriteIcon, artistIcon, albumIcon;
	private Bitmap bitmap;
	private final BgButton btnAdd, btnPlay;
	private String name, ellipsizedName;
	private boolean isDirectory, buttonsVisible;
	private final boolean hasButtons;
	private int state, position, width;
	
	public FileView(Context context, ActivityFileView observerActivity, Drawable closedFolderIcon, Drawable internalIcon, Drawable externalIcon, Drawable favoriteIcon, Drawable artistIcon, Drawable albumIcon, boolean hasButtons) {
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
		height = UI.defaultControlContentsSize + (verticalMargin << 1);
		if (hasButtons) {
			btnAdd = new BgButton(context);
			LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			btnAdd.setLayoutParams(p);
			btnAdd.setIcon(UI.ICON_ADD, true, false);
			btnAdd.setContentDescription(context.getText(R.string.add));
			btnAdd.setOnClickListener(this);
			btnAdd.setForceBlack(true);
			addView(btnAdd);
			btnPlay = new BgButton(context);
			p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
			p.leftMargin = UI._8dp;
			btnPlay.setLayoutParams(p);
			btnPlay.setIcon(UI.ICON_PLAY, true, false);
			btnPlay.setContentDescription(context.getText(R.string.play));
			btnPlay.setOnClickListener(this);
			btnPlay.setForceBlack(true);
			addView(btnPlay);
			if (UI.isVerticalMarginLarge) {
				btnAdd.setHeight(height);
				btnPlay.setHeight(height);
			}
		} else {
			btnAdd = null;
			btnPlay = null;
		}
		this.hasButtons = hasButtons;
		buttonsVisible = hasButtons;
	}
	
	private void processEllipsis() {
		ellipsizedName = UI.ellipsizeText(name, UI._22sp, width - ((bitmap != null) ? ((UI._8dp << 1) + UI.defaultControlContentsSize) : UI._8dp) - (buttonsVisible ? ((UI.defaultControlContentsSize << 1) + UI._8dp + (UI._8dp << 2)) : 0) - UI._8dp);
	}
	
	public void setItemState(String name, boolean isDirectory, int specialType, int position, int state) {
		final int w = getWidth();
		final boolean showButtons = (hasButtons && ((specialType == 0) || (specialType == FileSt.TYPE_ALBUM)) && ((state & UI.STATE_SELECTED) != 0));
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		//watch out, DO NOT use equals()!
		if (this.name == name && this.isDirectory == isDirectory && buttonsVisible == showButtons && width == w)
			return;
		this.width = w;
		this.name = name;
		this.isDirectory = isDirectory;
		this.position = position;
		if (buttonsVisible != showButtons) {
			buttonsVisible = showButtons;
			if (btnAdd != null)
				btnAdd.setVisibility(showButtons ? View.VISIBLE : View.GONE);
			if (btnPlay != null)
				btnPlay.setVisibility(showButtons ? View.VISIBLE : View.GONE);
		}
		if (isDirectory) {
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
				bitmap = albumIcon;
				break;
			default:
				bitmap = closedFolderIcon;
				break;
			}
		} else {
			bitmap = null;
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
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
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
		UI.drawBg(canvas, state, UI.rect, false);
		if (bitmap != null)
			canvas.drawBitmap(bitmap, UI._8dp, verticalMargin, null);
		UI.drawText(canvas, ellipsizedName, (state == 0) ? UI.color_text : UI.color_text_selected, UI._22sp, (bitmap != null) ? ((UI._8dp << 1) + UI.defaultControlContentsSize) : UI._8dp, (UI.rect.bottom >> 1) - (UI._22spBox >> 1) + UI._22spYinBox);
		super.dispatchDraw(canvas);
	}
	
	@Override
	public void onClick(View view) {
		if (hasButtons && (view == btnAdd || view == btnPlay)) {
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
