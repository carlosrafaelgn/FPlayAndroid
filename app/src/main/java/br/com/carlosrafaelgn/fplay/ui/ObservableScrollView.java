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

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

public final class ObservableScrollView extends ScrollView {
	public interface OnScrollListener {
		void onScroll(ObservableScrollView view, int l, int t, int oldl, int oldt);
	}

	private OnScrollListener listener;
	private int placement;

	public ObservableScrollView(Context context) {
		super(context);
		init(UI.PLACEMENT_WINDOW);
	}

	public ObservableScrollView(Context context, int placement) {
		super(context);
		init(placement);
	}

	public ObservableScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(UI.PLACEMENT_WINDOW);
	}

	public ObservableScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(UI.PLACEMENT_WINDOW);
	}

	private void init(int placement) {
		this.placement = placement;
		super.setHorizontalFadingEdgeEnabled(false);
		super.setVerticalFadingEdgeEnabled(false);
		//super.setFadingEdgeLength(0);
		super.setDrawingCacheEnabled(false);
		super.setChildrenDrawingCacheEnabled(false);
		super.setAnimationCacheEnabled(false);
		setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
		updateVerticalScrollbar();
		UI.prepareEdgeEffect(this, placement);
	}

	public void updateVerticalScrollbar() {
		if (UI.browserScrollBarType != BgListView.SCROLLBAR_NONE) {
			setVerticalScrollBarEnabled(true);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				setVerticalScrollbarPosition(UI.scrollBarToTheLeft ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
			UI.tryToChangeScrollBarThumb(this, placement == UI.PLACEMENT_MENU ? UI.color_menu_icon : UI.color_divider);
		} else {
			setVerticalScrollBarEnabled(false);
		}
	}
	
	public void setOnScrollListener(OnScrollListener listener) {
		this.listener = listener;
	}
	
	public int getChildIndexAroundPosition(int y) {
		final ViewGroup vg = (ViewGroup)getChildAt(0);
		final int c = vg.getChildCount();
		int s = 0, e = c - 1, m = 0;
		while (e >= s) {
			m = (e + s) >> 1;
			final View v = vg.getChildAt(m);
			if (v.getTop() <= y) {
				if (v.getBottom() <= y)
					s = m + 1;
				else
					return m;
			} else {
				e = m - 1;
			}
		}
		//not an exact match... maybe y is a coordinate over an empty space
		if (c <= 0)
			return -1;
		if (m <= 0)
			return 0;
		else if (m >= c)
			return c - 1;
		return m - 1;
	}
	
	public int getPreviousChildIndexWithClass(Class<? extends View> clazz, int startingY) {
		int i = getChildIndexAroundPosition(startingY);
		if (i >= 0) {
			final ViewGroup vg = (ViewGroup)getChildAt(0);
			do {
				final View v = vg.getChildAt(i);
				if (clazz.isInstance(v))
					return i;
				i--;
			} while (i >= 0);
		}
		return -1;
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		if (listener != null)
			listener.onScroll(this, l, t, oldl, oldt);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		listener = null;
		super.onDetachedFromWindow();
	}
}
