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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public final class BgFlexLayout extends ViewGroup {
	//this layout does not support both padding and margins!
	private int flexSize;
	private View flexChild;
	private boolean opaque;

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
	public boolean isOpaque() {
		//calling getChildAt().isOpaque() causes an ANR!!!
		return opaque;
	}

	public void setOpaque(boolean opaque) {
		this.opaque = opaque;
	}

	@Override
	public boolean hasOverlappingRendering() {
		return true;
	}

	@Override
	public LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
	}

	@Override
	protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new LayoutParams(p);
	}

	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return true;
	}

	@Override
	public CharSequence getAccessibilityClassName() {
		return BgFlexLayout.class.getName();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		final int count = getChildCount();
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if (heightMode == MeasureSpec.UNSPECIFIED)
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(0x3fffffff, (heightMode = MeasureSpec.AT_MOST));

		//how much space we have in our hands
		final int availableHeight = MeasureSpec.getSize(heightMeasureSpec);

		int pass = 0;
		int contentsWidth = 0, contentsHeight;

		int widthMeasureSpecForChildren = widthMeasureSpec;

		//if our parent has already requested a fixed size, we won't need to scan the children twice!
		if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
			pass = 2;
			contentsWidth = MeasureSpec.getSize(widthMeasureSpec);
		}

		do {
			if (pass == 1) {
				pass = 2;
				widthMeasureSpecForChildren = MeasureSpec.makeMeasureSpec(contentsWidth, MeasureSpec.EXACTLY);
			}

			flexSize = 0;

			contentsHeight = 0;

			int flexIndex = -1;

			for (int i = 0; i < count; i++) {
				final View child = getChildAt(i);

				if (child == null || child.getVisibility() == GONE)
					continue;

				if (child == flexChild) {
					flexIndex = i;
					continue;
				}

				int heightAvailableForChild = availableHeight - contentsHeight;
				if (heightAvailableForChild < 0) heightAvailableForChild = 0;

				final LayoutParams p = child.getLayoutParams();
				final int oldWidth = p.width;
				if (pass == 2)
					p.width = LayoutParams.MATCH_PARENT;
				measureChild(child, widthMeasureSpecForChildren, MeasureSpec.makeMeasureSpec(heightAvailableForChild, MeasureSpec.AT_MOST));
				p.width = oldWidth;

				final int width = child.getMeasuredWidth();
				final int height = child.getMeasuredHeight();

				if (pass != 2) {
					pass = 1;
					if (contentsWidth < width)
						contentsWidth = width;
				}

				contentsHeight += height;
			}

			if (flexIndex >= 0) {
				final View child = getChildAt(flexIndex);

				int heightAvailableForFlex = availableHeight - contentsHeight;
				if (heightAvailableForFlex < 0) heightAvailableForFlex = 0;

				final LayoutParams p = child.getLayoutParams();
				final int oldWidth = p.width;
				if (pass == 2)
					p.width = LayoutParams.MATCH_PARENT;
				//we use heightMode with the flex child, because if our parent has imposed a fixed
				//height upon us, then we must impose a fixed height upon the flex child, forcing it
				//to use up all the remaining space
				measureChild(child, widthMeasureSpecForChildren, MeasureSpec.makeMeasureSpec(heightAvailableForFlex, heightMode));
				p.width = oldWidth;

				final int width = child.getMeasuredWidth();
				flexSize = child.getMeasuredHeight();

				if (pass != 2) {
					pass = 1;
					if (contentsWidth < width)
						contentsWidth = width;
				}

				contentsHeight += flexSize;
			}

			setMeasuredDimension(contentsWidth,
				(pass == 2) ? contentsHeight :
				Math.max(contentsHeight, getSuggestedMinimumHeight()));

			if (pass == 1) {
				contentsWidth = Math.max(getMeasuredWidth(), getSuggestedMinimumWidth());
				if (contentsWidth < 0) contentsWidth = 0;
			}
		} while (pass != 2);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int childLeft = 0;

		final int count = getChildCount();

		int childTop = 0;

		for (int i = 0; i < count; i++) {
			final View child = getChildAt(i);

			if (child == null || child.getVisibility() == GONE)
				continue;

			final int childWidth = child.getMeasuredWidth();
			final int childHeight = ((child == flexChild) ? flexSize : child.getMeasuredHeight());

			//int gravity = lp.gravity;
			//if (gravity <= 0) gravity = this.gravity;

			child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

			childTop += childHeight;
		}
	}
}
