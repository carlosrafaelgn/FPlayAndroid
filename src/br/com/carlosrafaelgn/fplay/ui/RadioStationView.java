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

import br.com.carlosrafaelgn.fplay.list.RadioStation;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.StaticLayout;
import android.text.Layout.Alignment;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;

public final class RadioStationView extends View implements View.OnClickListener, View.OnLongClickListener {
	private RadioStation station;
	private String ellipsizedTitle, ellipsizedOnAir, ellipsizedTags;
	private final int verticalMargin;
	private int state, width, height, descriptionHeight, position;
	private StaticLayout layout;
	
	public RadioStationView(Context context) {
		super(context);
		setOnClickListener(this);
		setOnLongClickListener(this);
		verticalMargin = (UI.isVerticalMarginLarge ? UI._16sp : UI._8sp);
	}
	
	private void processEllipsis(boolean keepCurrentLayout) {
		ellipsizedTitle = UI.ellipsizeText(station.title, UI._22sp, width - (UI._8dp << 1), false);
		ellipsizedOnAir = UI.ellipsizeText(station.onAir, UI._18sp, width - (UI._8dp << 1), false);
		ellipsizedTags = UI.ellipsizeText(station.tags, UI._14sp, width - (UI._8dp << 1), false);
		if (station.description == null || station.description.length() == 0) {
			layout = null;
			descriptionHeight = 0;
		} else {
			UI.textPaint.setTextSize(UI._14sp);
			layout = new StaticLayout(station.description, UI.textPaint, (width < (UI._8dp << 1)) ? 0 : (width - (UI._8dp << 1)), Alignment.ALIGN_NORMAL, 1, 0, false);
			descriptionHeight = layout.getHeight();
			if (descriptionHeight < UI._14spBox)
				descriptionHeight = UI._14spBox;
			descriptionHeight += UI._1dp;
		}
		final int h = (UI._1dp << 1) + UI._1dp + (verticalMargin << 1) + UI._22spBox + UI._18spBox + UI._14spBox + descriptionHeight;
		if (height != h) {
			height = h;
			if (!keepCurrentLayout)
				requestLayout();
		}
	}
	
	public void setItemState(RadioStation station, int position, int state) {
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		this.position = position;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.station == station)
			return;
		this.station = station;
		setContentDescription(station.title);
		processEllipsis(true);
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
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(resolveSize(0, widthMeasureSpec), height);
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (width != w) {
			width = w;
			processEllipsis(false);
		}
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		final int txtColor = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem : UI.color_text_selected);
		getDrawingRect(UI.rect);
		UI.drawBgBorderless(canvas, state | ((state & UI.STATE_SELECTED & ((BgListView)getParent()).extraState) >>> 2), true);
		UI.drawText(canvas, ellipsizedTitle, txtColor, UI._22sp, UI._8dp, verticalMargin + UI._22spYinBox);
		UI.drawText(canvas, ellipsizedOnAir, txtColor, UI._18sp, UI._8dp, verticalMargin + UI._22spBox + UI._1dp + UI._18spYinBox);
		if (layout != null) {
			UI.textPaint.setColor(txtColor);
			UI.textPaint.setTextSize(UI._14sp);
			final float y = (float)(verticalMargin + UI._22spBox + UI._18spBox + (UI._1dp << 1));
			canvas.translate(0, y);
			layout.draw(canvas);
			canvas.translate(0, -y);
		}
		UI.drawText(canvas, ellipsizedTags, txtColor, UI._14sp, UI._8dp, height - verticalMargin - UI._1dp - UI._14spBox + UI._14spYinBox);
	}
	
	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}
	
	@Override
	protected void onDetachedFromWindow() {
		station = null;
		ellipsizedTitle = null;
		ellipsizedOnAir = null;
		ellipsizedTags = null;
		layout = null;
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
