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
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.LinearLayout;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class RadioStationView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener {
	private RadioStation station;
	private BgButton btnFavorite;
	private String ellipsizedTitle, ellipsizedOnAir;
	private String[] descriptionLines, tagsLines;
	private int state, width, position, descriptionY, tagsY;
	private BaseList<RadioStation> baseList;

	private static int height, textX, iconY, onAirY, leftMargin, topMargin, rightMargin, rightMarginForDrawing, bottomMargin;

	public static int getViewHeight() {
		if (UI.is3D) {
			switch (UI.browserScrollBarType) {
			case BgListView.SCROLLBAR_INDEXED:
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
		textX = leftMargin + UI.controlMargin;
		iconY = topMargin + UI.verticalMargin + UI._HeadingspBox + UI.controlXtraSmallMargin + ((UI._18spBox - UI._18sp) >> 1);
		onAirY = topMargin + UI.verticalMargin + UI._HeadingspBox + UI.controlXtraSmallMargin + UI._18spYinBox;
		return (height = topMargin + bottomMargin + UI.verticalMargin + UI._HeadingspBox + UI.controlXtraSmallMargin + UI._18spBox + (3 * UI._14spBox) + UI.controlSmallMargin + Math.max(UI.defaultControlSize + (UI.isDividerVisible ? (UI.controlXtraSmallMargin + UI.strokeSize) : UI.controlXtraSmallMargin), UI.verticalMargin + (UI._14spBox << 1)) + UI.controlSmallMargin);
	}

	public RadioStationView(Context context) {
		super(context);
		setOnClickListener(this);
		setOnLongClickListener(this);
		setBaselineAligned(false);
		setGravity(Gravity.END | Gravity.BOTTOM);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
		getViewHeight();
		descriptionLines = new String[4];
		tagsLines = new String[3];
		btnFavorite = new BgButton(context);
		btnFavorite.setContentDescription(context.getText(R.string.favorite));
		LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.rightMargin = rightMargin;
		p.bottomMargin = bottomMargin;
		btnFavorite.setLayoutParams(p);
		addView(btnFavorite);
		btnFavorite = null; //let setItemState() format the button...
		super.setDrawingCacheEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			super.setDefaultFocusHighlightEnabled(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			super.setPointerIcon(PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND));
	}

	private void processEllipsis() {
		//a few devices detach views from the listview before recycling, hence attaching
		//them back to the window without recreating the object
		if (descriptionLines == null)
			descriptionLines = new String[4];
		if (tagsLines == null)
			tagsLines = new String[3];

		final int hMargin = (UI.controlMargin << 1) + leftMargin + rightMargin;
		if (width <= hMargin) {
			ellipsizedTitle = "";
			ellipsizedOnAir = "";
			descriptionLines[0] = null;
			tagsLines[0] = null;
			return;
		}
		ellipsizedTitle = UI.ellipsizeText(station.title, UI._Headingsp, width - hMargin, true);
		ellipsizedOnAir = UI.ellipsizeText(station.onAir, UI._18sp, width - hMargin - UI._18sp - UI.controlSmallMargin, true);
		UI.textPaint.setTextSize(UI._14sp);
		
		//push the tags to the bottom!
		StaticLayout layout = new StaticLayout(station.tags, UI.textPaint, width - hMargin - UI.defaultControlSize, Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
		tagsY = height - bottomMargin - UI.verticalMargin - (visibleLines * UI._14spBox) + UI._14spYinBox;
		
		//center the description vertically, considering all available space
		final int top = topMargin + UI.verticalMargin + UI._HeadingspBox + UI.controlXtraSmallMargin + UI._18spBox, bottom = bottomMargin + Math.max(UI.defaultControlSize + UI.controlXtraSmallMargin, UI.verticalMargin + (visibleLines * UI._14spBox));
		
		layout = new StaticLayout(station.description, UI.textPaint, width - hMargin, Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
		visibleLines = Math.min(3, layout.getLineCount());
		for (i = 0; i < visibleLines; i++)
			descriptionLines[i] = station.description.substring(layout.getLineStart(i), layout.getLineEnd(i)).trim();
		descriptionLines[i] = null;
		if (layout.getLineCount() > 3) {
			//ellipsize last line...
			if (descriptionLines[2].length() > 2)
				descriptionLines[2] = descriptionLines[2].substring(0, descriptionLines[2].length() - 2) + "\u2026";
			else
				descriptionLines[2] += "\u2026";
		}
		descriptionY = top + (((height - top - bottom) - (visibleLines * UI._14spBox)) >> 1) + UI._14spYinBox;
	}

	public void refreshItemFavoriteButton() {
		if (station != null && btnFavorite != null)
			btnFavorite.setChecked(station.isFavorite);
	}

	public void setItemState(RadioStation station, int position, int state, BaseList<RadioStation> baseList) {
		this.position = position;
		//refer to the comment inside processEllipsis()
		if (btnFavorite == null && getChildCount() > 0 && (getChildAt(0) instanceof BgButton)) {
			btnFavorite = (BgButton)getChildAt(0);
			btnFavorite.setHideBorders(true);
			btnFavorite.formatAsCheckBox(UI.ICON_FAVORITE_ON, UI.ICON_FAVORITE_OFF, true, true, true);
			btnFavorite.setOnClickListener(this);
			btnFavorite.setTextColor((state != 0) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
		} else if (btnFavorite != null && (this.state & UI.STATE_SELECTED) != (state & UI.STATE_SELECTED)) {
			btnFavorite.setTextColor((state != 0) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
		}
		this.state = state; //(this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		this.baseList = baseList;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.station == station)
			return;
		this.station = station;
		if (UI.isAccessibilityManagerEnabled)
			setContentDescription(station.title);
		if (btnFavorite != null)
			btnFavorite.setChecked(station.isFavorite);
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
		state = UI.handleStateChanges(state, this);
		if ((state == 0) != old && btnFavorite != null)
			btnFavorite.setTextColor((state != 0) ? UI.colorState_text_selected_static : UI.colorState_text_listitem_reactive);
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
		if (canvas == null || ellipsizedTitle == null)
			return;
		final int txtColor = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem : UI.color_text_selected);
		final int txtColor2 = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
		final Rect rect = UI.rect;
		getDrawingRect(rect);
		rect.left += leftMargin;
		rect.top += topMargin;
		rect.right -= rightMarginForDrawing;
		UI.drawBgListItem(rect, canvas, state | ((state & UI.STATE_SELECTED & BgListView.extraState) >>> 2));
		UI.drawText(canvas, ellipsizedTitle, txtColor, UI._Headingsp, textX, topMargin + UI.verticalMargin + UI._HeadingspYinBox);
		TextIconDrawable.drawIcon(canvas, UI.ICON_FPLAY, textX, iconY, UI._18sp, txtColor2);
		UI.drawText(canvas, ellipsizedOnAir, txtColor2, UI._18sp, textX + UI._18sp + UI.controlSmallMargin, onAirY);
		int i = 0, y = descriptionY;
		while (descriptionLines[i] != null) {
			UI.drawText(canvas, descriptionLines[i], txtColor, UI._14sp, textX, y);
			y += UI._14spBox;
			i++;
		}
		i = 0;
		y = tagsY;
		while (tagsLines[i] != null) {
			UI.drawText(canvas, tagsLines[i], txtColor2, UI._14sp, textX, y);
			y += UI._14spBox;
			i++;
		}
		super.dispatchDraw(canvas);
	}

	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}

	@Override
	protected void onDetachedFromWindow() {
		station = null;
		btnFavorite = null;
		ellipsizedTitle = null;
		ellipsizedOnAir = null;
		descriptionLines = null;
		tagsLines = null;
		baseList = null;
		super.onDetachedFromWindow();
	}

	@Override
	public void onClick(View view) {
		BaseList.ItemClickListener itemClickListener;
		if (view == btnFavorite) {
			if (station != null)
				station.isFavorite = btnFavorite.isChecked();
			if (baseList != null && (itemClickListener = baseList.getItemClickListener()) != null)
				itemClickListener.onItemCheckboxClicked(position);
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
}
