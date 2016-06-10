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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class BgFlexLayout extends ViewGroup {
	public static class LayoutParams extends ViewGroup.MarginLayoutParams {
		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
		}

		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public LayoutParams(ViewGroup.LayoutParams p) {
			super(p);
		}

		public LayoutParams(ViewGroup.MarginLayoutParams source) {
			super(source);
		}

		public LayoutParams(LayoutParams source) {
			super(source);
		}
	}

	private int flexSize;
	private View flexChild;

	public BgFlexLayout(Context context) {
		this(context, null);
	}

	public BgFlexLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public BgFlexLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void setFlexChild(View flexChild) {
		this.flexChild = flexChild;
	}

	@Override
	public LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new BgFlexLayout.LayoutParams(getContext(), attrs);
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new BgFlexLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
	}

	@Override
	protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new BgFlexLayout.LayoutParams(p);
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof BgFlexLayout.LayoutParams;
	}

	@Override
	public CharSequence getAccessibilityClassName() {
		return BgFlexLayout.class.getName();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int count = getChildCount();
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

		//how much space we have in our hands
		final int availableHeight = ((heightMode == MeasureSpec.UNSPECIFIED) ?
			Integer.MAX_VALUE :
			MeasureSpec.getSize(heightMeasureSpec)) - getPaddingTop() - getPaddingBottom();

		int childState = 0, contentsWidth = 0, contentsSizeWithoutFlex = 0;

		flexSize = 0;

		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);

			if (child == null || child.getVisibility() == GONE)
				continue;

			final BgFlexLayout.LayoutParams lp = (BgFlexLayout.LayoutParams)child.getLayoutParams();

			measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, contentsSizeWithoutFlex);

			int width = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
			if (width < 0) width = 0;
			int height = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
			if (height < 0) height = 0;

			if (contentsWidth < width)
				contentsWidth = width;

			childState = combineMeasuredStates(childState, child.getMeasuredState());

			if (child == flexChild)
				flexSize = height;
			else
				contentsSizeWithoutFlex += height;
		}

		contentsSizeWithoutFlex = Math.max(contentsSizeWithoutFlex, getSuggestedMinimumHeight());
		if ((contentsSizeWithoutFlex + flexSize) > availableHeight) {
			flexSize = availableHeight - contentsSizeWithoutFlex;
			if (flexSize < 0) flexSize = 0;
		}

		int height = contentsSizeWithoutFlex + flexSize;
		if (height > availableHeight) height = availableHeight;

		setMeasuredDimension(resolveSizeAndState(Math.max(contentsWidth + getPaddingLeft() + getPaddingRight(), getSuggestedMinimumWidth()), widthMeasureSpec, childState),
			resolveSizeAndState(height, heightMeasureSpec, 0));
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int childLeft = getPaddingLeft();
		final int childRight = r - l - getPaddingRight();
		final int availableWidth = childRight - childLeft;

		final int count = getChildCount();

		int childTop = getPaddingTop();
		int availableHeight = b - t - childTop - getPaddingBottom();

		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);

			if (child == null || child.getVisibility() == GONE)
				continue;

			final BgFlexLayout.LayoutParams lp = (BgFlexLayout.LayoutParams)child.getLayoutParams();

			final int widthAvailableForThisChild = availableWidth - lp.leftMargin - lp.rightMargin;

			int childHeight = ((child == flexChild) ? flexSize : child.getMeasuredHeight());
			int usedChildHeight = childHeight + lp.topMargin + lp.bottomMargin;
			if (usedChildHeight > availableHeight) {
				usedChildHeight = availableHeight;
				childHeight = usedChildHeight - lp.topMargin - lp.bottomMargin;
				if (childHeight < 0) childHeight = 0;
			}

			//int gravity = lp.gravity;
			//if (gravity <= 0) gravity = this.gravity;

			child.layout(childLeft + lp.leftMargin,
				childTop + lp.topMargin,
				childLeft + lp.leftMargin + widthAvailableForThisChild,
				childTop + lp.topMargin + childHeight);

			childTop += usedChildHeight;
			availableHeight -= usedChildHeight;
		}
	}
}
