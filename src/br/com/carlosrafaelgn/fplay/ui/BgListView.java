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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.ListAdapter;
import android.widget.ListView;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.NullDrawable;

public final class BgListView extends ListView {
	public static interface OnAttachedObserver {
		public void onBgListViewAttached(BgListView list);
	}
	
	public static interface OnBgListViewKeyDownObserver {
		public boolean onBgListViewKeyDown(BgListView bgListView, int keyCode, KeyEvent event);
	}
	
	private OnAttachedObserver attachedObserver;
	private OnBgListViewKeyDownObserver keyDownObserver;
	private boolean notified, attached, measured, sized, ignoreTouchMode;
	int extraState;
	
	public BgListView(Context context) {
		super(context);
		init();
	}
	
	public BgListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public BgListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
    @SuppressWarnings("deprecation")
	private void init() {
    	super.setSelector(new NullDrawable());
    	super.setDivider(null);
    	super.setDividerHeight(0);
    	super.setCacheColorHint(UI.color_list);
    	super.setHorizontalFadingEdgeEnabled(false);
    	super.setVerticalFadingEdgeEnabled(false);
    	super.setFadingEdgeLength(0);
    	super.setBackgroundDrawable(new BorderDrawable(0, UI._1dp, 0, 0));
    	super.setFocusable(true);
    	super.setFocusableInTouchMode(false);
    	//List color turns black while Scrolling
        //http://stackoverflow.com/questions/8531006/list-color-turns-black-while-scrolling
        //Remove shadow from top and bottom of ListView in android?
        //http://stackoverflow.com/questions/7106692/remove-shadow-from-top-and-bottom-of-listview-in-android
        //Changing the ListView shadow color and size
        //http://stackoverflow.com/questions/5627063/changing-the-listview-shadow-color-and-size
    }
    
    @SuppressWarnings("deprecation")
    public void setTopLeftBorders() {
    	super.setBackgroundDrawable(new BorderDrawable(UI._1dp, UI._1dp, 0, 0));
    }
    
    @SuppressWarnings("deprecation")
    public void setRightBorder() {
    	super.setBackgroundDrawable(new BorderDrawable(0, 0, UI._1dp, 0));
    }
    
    @SuppressWarnings("deprecation")
    public void setBottomBorder() {
    	super.setBackgroundDrawable(new BorderDrawable(0, 0, 0, UI._1dp));
    }
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
	}
	
	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background) {
	}
	
	@Override
	public void setBackgroundResource(int resid) {
	}
	
	@Override
	public void setBackgroundColor(int color) {
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return true;
	}
	
	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
		//massive workaround!!!
		//
		//ListView's onFocusChanged has a BUG:
		//it scrolls the view vertically when the view has top padding != 0
		//
		//according to the source files
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/AbsListView.java
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.3_r1/android/widget/AbsListView.java
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/ListView.java
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.3_r1/android/widget/ListView.java
		//if previouslyFocusedRect is null and the control is in touch mode,
		//nothing is done, and therefore, the scroll does not happen ;)
		extraState = (gainFocus ? UI.STATE_SELECTED : 0);
		ignoreTouchMode = true;
		super.onFocusChanged(gainFocus, direction, gainFocus ? null : previouslyFocusedRect);
		int s;
		final BaseList<?> a = (BaseList<?>)getAdapter();
		if (a != null) {
			final int count = a.getCount();
			s = a.getSelection();
			if (gainFocus) {
				if (s < 0 || s >= count) {
					s = getFirstVisiblePosition();
					if (s < 0)
						s = 0;
					if (s < count) {
						a.setSelection(s, true);
						if (s <= getFirstVisiblePosition() || s >= getLastVisiblePosition())
							centerItem(s, false);
					}
				}
			}
			if (s >= 0 && (s >= getFirstVisiblePosition() && s <= getLastVisiblePosition())) {
				final int c = s - getFirstVisiblePosition();
				if (c >= 0 && c < getChildCount())
					getChildAt(c).invalidate();
			}
		}
		ignoreTouchMode = false;
	}
	
	@Override
	@ExportedProperty
	public boolean isInTouchMode() {
		return (ignoreTouchMode | super.isInTouchMode());
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void smoothScroll(int position, int y) {
		smoothScrollToPositionFromTop(position, y);
	}
	
	public void centerItem(int position, boolean smoothly) {
		final View v = getChildAt(0);
		int y = (getHeight() >> 1);
		final int y2 = ((v == null) ? 0 : (v.getHeight() >> 1));
		y -= ((y2 == 0) ? UI._22spBox : y2);
		if (y < 0)
			y = 0;
		if (smoothly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			smoothScroll(position, y);
		else
			setSelectionFromTop(position, y);
	}
	
	public void setOnKeyDownObserver(OnBgListViewKeyDownObserver keyDownObserver) {
		this.keyDownObserver = keyDownObserver;
	}
	
	public void notifyMeWhenFirstAttached(OnAttachedObserver observer) {
		attachedObserver = observer;
		if (attached && measured && sized && attachedObserver != null) {
			notified = true;
			attachedObserver.onBgListViewAttached(this);
			attachedObserver = null;
		}
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		attached = true;
		if (!notified && measured && sized && attachedObserver != null) {
			notified = true;
			attachedObserver.onBgListViewAttached(this);
			attachedObserver = null;
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		measured = true;
		if (!notified && attached && sized && attachedObserver != null) {
			notified = true;
			attachedObserver.onBgListViewAttached(this);
			attachedObserver = null;
		}
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (w > 0 && h > 0) {
			sized = true;
			if (!notified && attached && measured && attachedObserver != null) {
				notified = true;
				attachedObserver.onBgListViewAttached(this);
				attachedObserver = null;
			}
		}
	}
	
	public int getNewPosition(int position, int keyCode, boolean allowWrap) {
		final ListAdapter a = getAdapter();
		if (a == null)
			return -1;
		int p;
		final int l = a.getCount() - 1;
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_UP:
			if (position > l || position < 0 || (allowWrap && position == 0))
				return l;
			return position - 1;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			if ((allowWrap && position == l) || position > l || position < 0)
				return 0;
			return position + 1;
		case KeyEvent.KEYCODE_PAGE_UP:
			if (position > l || position < 0 || (allowWrap && position == 0))
				return l;
			p = getLastVisiblePosition() - getFirstVisiblePosition();
			if (p > 1)
				p = position - (p - 1);
			else
				p = position - 1;
			return ((p <= 0) ? 0 : p);
		case KeyEvent.KEYCODE_PAGE_DOWN:
			if ((allowWrap && position == l) || position > l || position < 0)
				return 0;
			p = getLastVisiblePosition() - getFirstVisiblePosition();
			if (p > 1)
				p = position + p - 1;
			else
				p = position + 1;
			return ((p >= l) ? l : p);
		case KeyEvent.KEYCODE_MOVE_HOME:
			return 0;
		case KeyEvent.KEYCODE_MOVE_END:
			return l;
		}
		return -1;
	}
	
	private void defaultKeyDown(int keyCode) {
		final BaseList<?> a = (BaseList<?>)getAdapter();
		if (a == null)
			return;
		final int s = getNewPosition(a.getSelection(), keyCode, true);
		if (s >= 0 && s < a.getCount()) {
			a.setSelection(s, true);
			if (s <= getFirstVisiblePosition() || s >= getLastVisiblePosition())
				centerItem(s, false);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (isEnabled()) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_FORWARD_DEL:
			case KeyEvent.KEYCODE_PAGE_UP:
			case KeyEvent.KEYCODE_PAGE_DOWN:
			case KeyEvent.KEYCODE_MOVE_HOME:
			case KeyEvent.KEYCODE_MOVE_END:
				if (keyDownObserver == null || !keyDownObserver.onBgListViewKeyDown(this, keyCode, event))
					defaultKeyDown(keyCode);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		final ListAdapter a = getAdapter();
		if (a == null || a.getCount() == 0) {
			getDrawingRect(UI.rect);
			UI.drawEmptyListString(canvas);
		}
	}
}
