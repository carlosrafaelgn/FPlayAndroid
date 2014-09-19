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
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class RadioStationView extends View implements BgListView.BgListItem, View.OnClickListener, View.OnLongClickListener {
	private RadioStation station;
	private String ellipsizedTitle, ellipsizedOnAir;
	private String[] descriptionLines, tagsLines;
	private final int height, verticalMargin;
	private int state, width, position, descriptionY, tagsY;
	
	public RadioStationView(Context context) {
		super(context);
		setOnClickListener(this);
		setOnLongClickListener(this);
		verticalMargin = (UI.isVerticalMarginLarge ? UI._16sp : UI._8sp);
		descriptionLines = new String[4];
		tagsLines = new String[3];
		height = (verticalMargin << 1) + UI._22spBox + UI._2dp + UI._18spBox + (3 * UI._14spBox) + UI._4dp + (UI._14spBox << 1) + UI._4dp;
		super.setDrawingCacheEnabled(false);
	}
	
	private void processEllipsis() {
		if (width <= (UI._8dp << 1)) {
			ellipsizedTitle = "";
			ellipsizedOnAir = "";
			descriptionLines[0] = null;
			tagsLines[0] = null;
			return;
		}
		ellipsizedTitle = UI.ellipsizeText(station.title, UI._22sp, width - (UI._8dp << 1), true);
		ellipsizedOnAir = UI.ellipsizeText(station.onAir, UI._18sp, width - (UI._8dp << 1) - UI._18sp - UI._4dp, true);
		UI.textPaint.setTextSize(UI._14sp);
		
		//push the tags to the bottom!
		StaticLayout layout = new StaticLayout(station.tags, UI.textPaint, width - (UI._8dp << 1) - UI.defaultControlContentsSize - UI._4dp, Alignment.ALIGN_NORMAL, 1, 0, false);
		int i, visibleLines = Math.min(2, layout.getLineCount());
		for (i = 0; i < visibleLines; i++)
			tagsLines[i] = station.tags.substring(layout.getLineStart(i), layout.getLineEnd(i));
		tagsLines[i] = null;
		if (layout.getLineCount() > 2) {
			//ellipsize last line...
			if (tagsLines[1].length() > 2)
				tagsLines[1] = tagsLines[1].substring(0, tagsLines[1].length() - 2) + "\u2026";
			else
				tagsLines[1] += "\u2026";
		}
		tagsY = height - verticalMargin - (visibleLines * UI._14spBox) + UI._14spYinBox;
		
		//center the description vertically, considering all available space
		final int top = verticalMargin + UI._22spBox + UI._2dp + UI._18spBox, bottom = verticalMargin + (visibleLines * UI._14spBox);
		
		layout = new StaticLayout(station.description, UI.textPaint, width - (UI._8dp << 1), Alignment.ALIGN_NORMAL, 1, 0, false);
		visibleLines = Math.min(3, layout.getLineCount());
		for (i = 0; i < visibleLines; i++)
			descriptionLines[i] = station.description.substring(layout.getLineStart(i), layout.getLineEnd(i));
		descriptionLines[i] = null;
		if (layout.getLineCount() > 3) {
			//ellipsize last line...
			if (descriptionLines[2].length() > 2)
				descriptionLines[2] = descriptionLines[2].substring(0, descriptionLines[2].length() - 2) + "\u2026";
			else
				descriptionLines[2] += "\u2026";
		}
		descriptionY = top + ((height - top - bottom) >> 1) - ((visibleLines * UI._14spBox) >> 1) + UI._14spYinBox;
	}
	
	public void setItemState(RadioStation station, int position, int state) {
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		this.position = position;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.station == station)
			return;
		this.station = station;
		setContentDescription(station.title);
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
		return ((state & ~UI.STATE_CURRENT) != 0);
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
	public int predictHeight() {
		return height;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
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
	
	@Override
	protected void onDraw(Canvas canvas) {
		final int txtColor = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem : UI.color_text_selected);
		final int txtColor2 = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
		getDrawingRect(UI.rect);
		UI.drawBgBorderless(canvas, state | ((state & UI.STATE_SELECTED & ((BgListView)getParent()).extraState) >>> 2), true);
		UI.drawText(canvas, ellipsizedTitle, txtColor, UI._22sp, UI._8dp, verticalMargin + UI._22spYinBox);
		TextIconDrawable.drawIcon(canvas, UI.ICON_FPLAY, UI._8dp, verticalMargin + UI._22spBox + UI._2dp + (UI._18spBox >> 1) - (UI._18sp >> 1), UI._18sp, txtColor2);
		UI.drawText(canvas, ellipsizedOnAir, txtColor2, UI._18sp, UI._8dp + UI._18sp + UI._4dp, verticalMargin + UI._22spBox + UI._2dp + UI._18spYinBox);
		int i = 0, y = descriptionY;
		while (descriptionLines[i] != null) {
			UI.drawText(canvas, descriptionLines[i], txtColor, UI._14sp, UI._8dp, y);
			y += UI._14spBox;
			i++;
		}
		i = 0;
		y = tagsY;
		while (tagsLines[i] != null) {
			UI.drawText(canvas, tagsLines[i], txtColor2, UI._14sp, UI._8dp, y);
			y += UI._14spBox;
			i++;
		}
	}
	
	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}
	
	@Override
	protected void onDetachedFromWindow() {
		station = null;
		ellipsizedTitle = null;
		ellipsizedOnAir = null;
		descriptionLines = null;
		tagsLines = null;
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onClick(View view) {
		if (UI.browserActivity != null)
			UI.browserActivity.processItemClick(position);
	}
	
	@Override
	public boolean onLongClick(View view) {
		if (UI.browserActivity != null)
			UI.browserActivity.processItemLongClick(position);
		return true;
	}
}
