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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.view.accessibility.AccessibilityEvent;

import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class SongView extends View implements View.OnClickListener, View.OnLongClickListener {
	private Song song;
	private String ellipsizedTitle, ellipsizedExtraInfo, numberAndCount;
	private int state, width, lengthX, lengthWidth, numberAndCountX, numberAndCountWidth, position;
	private BaseList<Song> baseList;

	private static int height, textX, titleY, extraY, currentX, currentY, leftMargin, topMargin,
		rightMargin, rightMarginForDrawing, numberAndCountColor, numberAndCountColorSelected;

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
		height = (UI._1dp << 1) + (UI.verticalMargin << 1) + UI._HeadingspBox + UI._14spBox + topMargin + bottomMargin;
		textX = leftMargin + UI.controlMargin;
		titleY = UI.verticalMargin + UI._HeadingspYinBox + topMargin;
		extraY = UI.verticalMargin + UI._1dp + UI._HeadingspBox + UI._14spYinBox + topMargin;
		currentY = height - UI.defaultControlContentsSize - UI.controlXtraSmallMargin - bottomMargin;
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
		super.setDrawingCacheEnabled(false);
	}

	private void processEllipsis() {
		final int w = lengthX - textX - UI.controlMargin;
		ellipsizedTitle = UI.ellipsizeText(song.title, UI._Headingsp, w, false);
		ellipsizedExtraInfo = UI.ellipsizeText(song.extraInfo, UI._14sp, (numberAndCount == null) ? w : (numberAndCountX - textX - UI.controlMargin), false);
	}

	public void updateIfCurrent() {
		if ((state & UI.STATE_CURRENT) != 0) {
			processEllipsis();
			invalidate();
		}
	}

	@Override
	public CharSequence getContentDescription() {
		if (song != null)
			return song.title;
		return super.getContentDescription();
	}

	@Override
	public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
		super.onInitializeAccessibilityEvent(event);
		event.setContentDescription(getContentDescription());
	}

	public void setItemState(Song song, int position, int state, BaseList<Song> baseList) {
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		this.position = position;
		this.baseList = baseList;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.song == song && !UI.displaySongNumberAndCount)
			return;
		this.song = song;
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
		getDrawingRect(UI.rect);
		UI.rect.left += leftMargin;
		UI.rect.top += topMargin;
		UI.rect.right -= rightMarginForDrawing;
		UI.drawBgListItem(canvas, state | ((state & UI.STATE_SELECTED & BgListView.extraState) >>> 2));
		if ((state & UI.STATE_CURRENT) != 0)
			TextIconDrawable.drawIcon(canvas, UI.ICON_FPLAY, currentX, currentY, UI.defaultControlContentsSize, ((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
		else if (numberAndCount != null)
			UI.drawText(canvas, numberAndCount, ((state & ~UI.STATE_CURRENT) == 0) ? numberAndCountColor : numberAndCountColorSelected, UI._14sp, numberAndCountX, extraY);
		UI.drawText(canvas, ellipsizedTitle, txtColor, UI._Headingsp, textX, titleY);
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
}
