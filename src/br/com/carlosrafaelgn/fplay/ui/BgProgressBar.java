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
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewParent;

import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class BgProgressBar extends View {
	private boolean attached, visible;
	private int width, virtualViewWidth, color, secondaryBgColorBlended;

	public BgProgressBar(Context context) {
		super(context);
		init();
	}

	public BgProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public BgProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		visible = true;
		setInsideList(false);
		super.setDrawingCacheEnabled(false);
		super.setClickable(false);
		super.setFocusable(false);
	}

	public void setInsideList(boolean insideList) {
		color = (insideList ? UI.color_focused : UI.color_selected);
		secondaryBgColorBlended = ColorUtils.blend(color, insideList ? UI.color_list_bg : UI.color_window, 0.35f);
		invalidate();
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
	@ViewDebug.ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return true;
	}

	@Override
	public boolean hasOverlappingRendering() {
		return false;
	}

	@Override
	public int getPaddingLeft() {
		return 0;
	}

	@Override
	public int getPaddingTop() {
		return 0;
	}

	@Override
	public int getPaddingRight() {
		return 0;
	}

	@Override
	public int getPaddingBottom() {
		return 0;
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
	}

	@Override
	protected int getSuggestedMinimumWidth() {
		return width;
	}

	@Override
	public int getMinimumWidth() {
		return width;
	}

	@Override
	protected int getSuggestedMinimumHeight() {
		return UI.controlSmallMargin;
	}

	@Override
	public int getMinimumHeight() {
		return UI.controlSmallMargin;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(UI.controlSmallMargin, heightMeasureSpec));
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		width = w >>> 2;
		virtualViewWidth = w + width;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (!attached || !visible || virtualViewWidth <= 0 || width <= 0)
			return;

		final Rect rect = UI.rect;
		getDrawingRect(rect);

		final int position;
		if (virtualViewWidth <= (UI.defaultControlContentsSize * 3))
			position = ((((int)SystemClock.uptimeMillis() % 1000) * virtualViewWidth) / 1000) - width;
		else
			position = ((((int)SystemClock.uptimeMillis() % 2000) * virtualViewWidth) / 2000) - width;

		if (position > 0) {
			rect.left = 0;
			rect.right = position;
			UI.fillRect(rect, canvas, secondaryBgColorBlended);
		}

		rect.left = position;
		rect.right = position + width;
		UI.fillRect(rect, canvas, color);

		if (rect.right < virtualViewWidth) {
			rect.left = rect.right;
			rect.right = virtualViewWidth;
			UI.fillRect(rect, canvas, secondaryBgColorBlended);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			postInvalidateOnAnimation();
		else
			postInvalidateDelayed(20);
	}

	@Override
	protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		visible = true;
		View view = this;
		while (view != null) {
			if (view.getVisibility() != View.VISIBLE) {
				visible = false;
				return;
			}
			final ViewParent p = view.getParent();
			view = ((p instanceof View) ? (View)p : null);
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		attached = true;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
			postInvalidateOnAnimation();
		else
			postInvalidateDelayed(20);
	}

	@Override
	protected void onDetachedFromWindow() {
		attached = false;
		super.onDetachedFromWindow();
	}
}
