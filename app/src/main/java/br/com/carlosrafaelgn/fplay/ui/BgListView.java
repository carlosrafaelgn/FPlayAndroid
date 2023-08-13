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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

import br.com.carlosrafaelgn.fplay.list.BaseItem;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.NullDrawable;

public final class BgListView extends ListView implements ListView.OnScrollListener {
	public interface OnAttachedObserver {
		void onBgListViewAttached(BgListView list);
	}
	
	public interface OnBgListViewKeyDownObserver {
		boolean onBgListViewKeyDown(BgListView list, int keyCode);
	}

	private static final int SCROLLBAR_INVALID = -1;
	public static final int SCROLLBAR_SYSTEM = 0;
	public static final int SCROLLBAR_LARGE = 1;
	public static final int SCROLLBAR_INDEXED = 2;
	public static final int SCROLLBAR_NONE = 3;
	
	private OnAttachedObserver attachedObserver;
	private OnBgListViewKeyDownObserver keyDownObserver;
	private OnClickListener emptyListClickListener;
	private OnScrollListener scrollListener;
	private StaticLayout emptyLayout;
	private BaseList<? extends BaseItem> adapter;
	private boolean notified, attached, measured, sized, ignoreTouchMode, ignorePadding, tracking, touching;
	private int backgroundColor, leftPadding, topPadding, rightPadding, bottomPadding, scrollBarType, scrollBarWidth, scrollBarThumbTop, scrollBarThumbHeight,
		scrollBarTop, scrollBarLeft, scrollBarBottom, viewWidth, viewHeight, contentsHeight, itemHeight, itemCount, scrollBarThumbOffset, scrollState, dividerHeight;
	private String[] sections;
	private int[] sectionPositions;
	public boolean skipUpDownTranslation;
	public static int extraState;

	public BgListView(Context context) {
		super(context);
		init(false);
	}

	public BgListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(false);
	}

	public BgListView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(false);
	}

	public BgListView(Context context, boolean force2D) {
		super(context);
		init(force2D);
	}

	private void init(boolean force2D) {
		//to make the first execution of setScrollBarType() always run
		scrollBarType = SCROLLBAR_INVALID;
		extraState = 0;
		super.setSelector(new NullDrawable());
		if ((!force2D && UI.is3D) || !UI.isDividerVisible) {
			backgroundColor = UI.color_list_bg;
			dividerHeight = 0;
			super.setDivider(null);
			super.setDividerHeight(0);
		} else {
			backgroundColor = UI.color_list_original;
			dividerHeight = UI.strokeSize;
			super.setDivider(new ColorDrawable(UI.color_divider));
			super.setDividerHeight(UI.strokeSize);
		}
		super.setCacheColorHint(backgroundColor);
		super.setHorizontalFadingEdgeEnabled(false);
		super.setVerticalFadingEdgeEnabled(false);
		//super.setFadingEdgeLength(0);
		super.setFocusableInTouchMode(!UI.hasTouch);
		super.setFocusable(true);

		super.setScrollingCacheEnabled(false);
		super.setDrawingCacheEnabled(false);
		super.setChildrenDrawingCacheEnabled(false);
		super.setAnimationCacheEnabled(false);
		
		ignorePadding = true;
		super.setHorizontalScrollBarEnabled(false);
		super.setVerticalScrollBarEnabled(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			setVerticalScrollBarPosition();
		ignorePadding = false;
		super.setBackgroundDrawable(new ColorDrawable(backgroundColor));
		super.setOverscrollHeader(null); //Motorola bug!!! :P
		super.setOverscrollFooter(null); //Motorola bug!!! :P
		setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
		UI.prepareEdgeEffect(this, UI.PLACEMENT_WINDOW);
		//List color turns black while Scrolling
		//http://stackoverflow.com/questions/8531006/list-color-turns-black-while-scrolling
		//Remove shadow from top and bottom of ListView in android?
		//http://stackoverflow.com/questions/7106692/remove-shadow-from-top-and-bottom-of-listview-in-android
		//Changing the ListView shadow color and size
		//http://stackoverflow.com/questions/5627063/changing-the-listview-shadow-color-and-size
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			super.setDefaultFocusHighlightEnabled(false);
	}

	//massive workaround!!!
	//
	//according to this:
	//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.3.3_r1/android/widget/AdapterView.java#AdapterView.setFocusable%28boolean%29
	//AdapterView overrides setFocusable and setFocusableInTouchMode in a way I don't like...
	//it calls View.setFocusable with false if the adapter is empty!!!
	//therefore, the only way to make it focusable at any time, is to pretend to be in filtermode at all times!
	@Override
	protected boolean isInFilterMode() {
		return true;
	}

	//apparently there have been changes to Android, and it is forbidden to override getContentDescription() now...
	//@Override
	//public CharSequence getContentDescription() {
	//	return null;
	//}

	//@Override
	//@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	//public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
	//	super.onInitializeAccessibilityNodeInfo(info);
	//	info.setClassName("br.com.carlosrafaelgn.fplay.activity.ActivityHost");
	//	if (itemCount == 0) {
	//		info.setText(emptyLayout == null ? "" : emptyLayout.getText());
	//	} else {
	//		info.setText("");
	//	}
	//}

	private void updateContentDescription() {
		if (!UI.isAccessibilityManagerEnabled)
			return;
		setContentDescription((itemCount == 0 && emptyLayout != null) ? emptyLayout.getText() : null);
	}

	public void setTopBorder() {
		super.setBackgroundDrawable(new BorderDrawable(UI.color_highlight, backgroundColor, 0, UI.thickDividerSize, 0, 0));
		setPadding(0, UI.thickDividerSize, 0, 0);
		UI.offsetTopEdgeEffect(this);
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
	public boolean hasOverlappingRendering() {
		return true;
	}

	private void invalidateItem(int position) {
		if (position >= 0 && (position >= getFirstVisiblePosition() && position <= getLastVisiblePosition())) {
			final int c = position - getFirstVisiblePosition();
			if (c >= 0 && c < getChildCount())
				getChildAt(c).invalidate();
		}
	}

	private void invalidateSelectedItem() {
		if (adapter != null)
			invalidateItem(adapter.getCurrentPosition());
	}

	@Override
	protected void drawableStateChanged() {
		int state = 0;

		if (!isInTouchMode()) {
			final int[] states = getDrawableState();
			if (states != null) {
				for (int i = states.length - 1; i >= 0; i--) {
					if (states[i] == android.R.attr.state_focused) {
						state = UI.STATE_SELECTED;
						break;
					}
				}
			}
		}

		if (extraState != state) {
			extraState = state;
			invalidateSelectedItem();
		}

		super.drawableStateChanged();
	}

	@Override
	public void onTouchModeChanged(boolean isInTouchMode) {
		super.onTouchModeChanged(isInTouchMode);

		extraState = ((isInTouchMode || !isFocused()) ? 0 : UI.STATE_SELECTED);

		invalidateSelectedItem();
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
		ignoreTouchMode = true;
		super.onFocusChanged(gainFocus, direction, gainFocus ? null : previouslyFocusedRect);
		int s;
		if (adapter != null) {
			s = adapter.getSelection();
			if (gainFocus) {
				//do not change to itemCount!
				final int ic = adapter.getCount();
				if (s < 0 || s >= ic) {
					s = getFirstVisiblePosition();
					if (s < 0)
						s = 0;
					if (s < ic) {
						adapter.setSelection(s, true);
						if (s <= getFirstVisiblePosition() || s >= getLastVisiblePosition())
							centerItem(s);
					}
				}
			}
			invalidateItem(s);
		}
		ignoreTouchMode = false;
	}
	
	@Override
	@ExportedProperty
	public boolean isInTouchMode() {
		return (ignoreTouchMode | super.isInTouchMode());
	}
	
	public boolean changingCurrentWouldScareUser() {
		return (
				(!isInTouchMode()) ||
				touching ||
				(scrollState != SCROLL_STATE_IDLE) ||
				(UI.isAccessibilityManagerEnabled)
		);
	}

	/*@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void smoothScroll(int position, int y) {
		smoothScrollToPositionFromTop(position, y);
	}

	public void centerItemSmoothly(int position) {
		//do not change to itemCount!
		if (position < 0 || adapter == null || position >= adapter.getCount())
			return;
		int y = ((viewHeight - bottomPadding - topPadding) - itemHeight) >> 1;
		if (y < 0)
			y = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			smoothScroll(position, y);
		else
			setSelectionFromTop(position, y);
	}*/

	public void centerItem(int position) {
		//do not change to itemCount!
		if (position < 0 || adapter == null || position >= adapter.getCount())
			return;
		int y = ((viewHeight - bottomPadding - topPadding) - itemHeight) >> 1;
		if (y < 0)
			y = 0;
		setSelectionFromTop(position, y);
	}

	public View getViewForPosition(int position) {
		position -= getFirstVisiblePosition();
		if (position < 0 || position >= getChildCount())
			return null;
		return getChildAt(position);
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
	
	public void setEmptyListOnClickListener(OnClickListener listener) {
		emptyListClickListener = listener;
	}
	
	public void setCustomEmptyText(CharSequence text) {
		if (text == null) {
			emptyLayout = null;
		} else {
			UI.textPaint.setTextSize(UI._Headingsp);
			emptyLayout = new StaticLayout(text, UI.textPaint, (viewWidth < (UI.controlLargeMargin << 1)) ? 0 : (viewWidth - (UI.controlLargeMargin << 1)), Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
		}
	}
	
	@Override
	protected void onAttachedToWindow() {
		ignorePadding = true;
		super.onAttachedToWindow();
		attached = true;
		if (!notified && measured && sized && attachedObserver != null) {
			notified = true;
			attachedObserver.onBgListViewAttached(this);
			attachedObserver = null;
		}
		ignorePadding = false;
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
			viewWidth = w;
			viewHeight = h;
			if (!UI.scrollBarToTheLeft)
				//scrollBarLeft = w - (rightPadding + scrollBarWidth);
				scrollBarLeft = w - scrollBarWidth;
			sized = true;
			if (!notified && attached && measured && attachedObserver != null) {
				notified = true;
				attachedObserver.onBgListViewAttached(this);
				attachedObserver = null;
			}
			if (emptyLayout != null && emptyLayout.getWidth() != w)
				setCustomEmptyText(emptyLayout.getText());
			switch (scrollBarType) {
			case SCROLLBAR_LARGE:
				scrollBarTop = topPadding + UI.controlSmallMargin;
				scrollBarBottom = h - bottomPadding - UI.controlSmallMargin;
				updateScrollBarThumb();
				break;
			case SCROLLBAR_INDEXED:
				scrollBarTop = topPadding;
				scrollBarBottom = h - bottomPadding;
				updateScrollBarIndices(false);
				break;
			}
		}
	}
	
	@SuppressWarnings("DuplicateExpressions")
	public int getNewPosition(int position, int keyCode, boolean allowWrap) {
		int p;
		final int l = itemCount - 1;
		switch (keyCode) {
		case UI.KEY_UP:
		case UI.KEY_LEFT:
			if (position > l || position < 0 || (allowWrap && position == 0))
				return l;
			return position - 1;
		case UI.KEY_DOWN:
		case UI.KEY_RIGHT:
			if ((allowWrap && position == l) || position > l || position < 0)
				return 0;
			return position + 1;
		case UI.KEY_PAGE_UP:
			if (position > l || position < 0 || (allowWrap && position == 0))
				return l;
			p = getLastVisiblePosition() - getFirstVisiblePosition();
			p = ((p > 1) ? (position - (p - 1)) : (position - 1));
			return Math.max(p, 0);
		case UI.KEY_PAGE_DOWN:
			if ((allowWrap && position == l) || position > l || position < 0)
				return 0;
			p = getLastVisiblePosition() - getFirstVisiblePosition();
			p = ((p > 1) ? (position + p - 1) : (position + 1));
			return Math.min(p, l);
		case UI.KEY_HOME:
			return 0;
		case UI.KEY_END:
			return l;
		}
		return -1;
	}
	
	private void invalidateScrollBar() {
		invalidate(scrollBarLeft, scrollBarTop, scrollBarLeft + scrollBarWidth, scrollBarBottom);
	}
	
	private void cancelTracking() {
		if (tracking) {
			tracking = false;
			if (attached)
				super.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 10, MotionEvent.ACTION_CANCEL, 0, 0, 0));
			if (scrollBarType == SCROLLBAR_INDEXED)
				invalidate();
			else
				invalidateScrollBar();
		}
	}

	private void setSelectionAtTheTop(int position) {
		if (getFirstVisiblePosition() == position) {
			final View firstChild = getChildAt(0);
			if (firstChild != null && firstChild.getTop() == 0)
				return;
		}
		setSelectionFromTop(position, 0);
	}

	private void trackTouchEvent(int y) {
		if (adapter == null)
			return;
		final int count = adapter.getCount();
		if (count <= 0)
			return;
		final int sbh = (scrollBarBottom - scrollBarTop), vh = (viewHeight - bottomPadding - topPadding);
		y -= scrollBarThumbOffset + scrollBarTop;
		int f;
		if (y <= 0) {
			scrollBarThumbTop = scrollBarTop;
			f = 0;
		} else if (y >= (sbh - scrollBarThumbHeight)) {
			scrollBarThumbTop = sbh - scrollBarThumbHeight + scrollBarTop;
			f = count - 1;
		} else {
			scrollBarThumbTop = y + scrollBarTop;
			int t = (scrollBarThumbTop * (contentsHeight - vh)) / (sbh - scrollBarThumbHeight);
			f = t / (itemHeight + dividerHeight);
			if (f >= count)
				f = count - 1;
		}
		invalidateScrollBar();
		setSelectionAtTheTop(f);
	}
	
	private void trackIndexedTouchEvent(int y) {
		if (scrollBarThumbHeight == 0 || sectionPositions == null)
			return;
		y = (y - scrollBarTop) / scrollBarThumbHeight;
		if (y < 0)
			y = 0;
		if (y >= sectionPositions.length)
			y = sectionPositions.length - 1;
		if (scrollBarThumbTop == y || y < 0)
			return;
		scrollBarThumbTop = y;
		y = sectionPositions[y];
		invalidateScrollBar();
		if (adapter != null && y >= 0 && y < adapter.getCount())
			setSelectionAtTheTop(y);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			if (scrollBarThumbHeight == 0) break;
			final int x = (int)event.getX();
			if (x >= scrollBarLeft && x < (scrollBarLeft + scrollBarWidth)) {
				tracking = true;
				if (scrollState == SCROLL_STATE_FLING) {
					//simulate a simple 10ms tap, to stop the fling 
					super.onTouchEvent(event);
					super.onTouchEvent(MotionEvent.obtain(event.getDownTime(), event.getDownTime() + 10, MotionEvent.ACTION_UP, event.getX(), event.getY(), event.getMetaState()));
					scrollState = SCROLL_STATE_IDLE;
				}
				final int y = (int)event.getY();
				if (scrollBarType == SCROLLBAR_LARGE) {
					scrollBarThumbOffset = ((y < scrollBarThumbTop || y >= (scrollBarThumbTop + scrollBarThumbHeight)) ? (scrollBarThumbHeight >> 1) : (y - scrollBarThumbTop));
					trackTouchEvent(y);
				} else {
					invalidate();
					scrollBarThumbTop = -1;
					trackIndexedTouchEvent(y);
				}
				if (getParent() != null)
					getParent().requestDisallowInterceptTouchEvent(true);
				playSoundEffect(SoundEffectConstants.CLICK);
				return true;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (!tracking)
				break;
			if (scrollBarType == SCROLLBAR_LARGE)
				trackTouchEvent((int)event.getY());
			else
				trackIndexedTouchEvent((int)event.getY());
			return true;
		case MotionEvent.ACTION_UP:
			touching = false;
			if (tracking) {
				tracking = false;
				if (scrollBarType == SCROLLBAR_LARGE)
					trackTouchEvent((int)event.getY());
				else
					invalidate();
				event.setLocation(scrollBarLeft + UI._1dp, event.getY());
			} else if (emptyListClickListener != null && itemCount == 0) {
				playSoundEffect(SoundEffectConstants.CLICK);
				emptyListClickListener.onClick(this);
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			touching = false;
			if (tracking) {
				tracking = false;
				if (scrollBarType == SCROLLBAR_LARGE)
					invalidateScrollBar();
				else
					invalidate();
			}
			break;
		default:
			if (tracking)
				return true;
			break;
		}
		return super.onTouchEvent(event);
	}

	private void updateScrollBarSectionForFirstPosition() {
		final int first = getFirstVisiblePosition();
		if (first <= 0 || sections == null) {
			scrollBarThumbTop = 0;
			return;
		}
		int s = 0, e = sections.length - 1, m = 0;
		while (s <= e) {
			m = ((e + s) >> 1);
			if (first == sectionPositions[m]) {
				scrollBarThumbTop = m;
				return;
			}
			else if (first < sectionPositions[m])
				e = m - 1;
			else
				s = m + 1;
		}
		if (first < sectionPositions[m])
			m--;
		if (m < 0)
			m = 0;
		else if (m >= sections.length)
			m = sections.length - 1;
		scrollBarThumbTop = m;
	}
	
	private void updateScrollBarThumb() {
		final int sbh = (scrollBarBottom - scrollBarTop);
		if (itemCount == 0 || sbh <= 0) {
			scrollBarThumbHeight = 0;
		} else {
			final View v = getChildAt(0);
			if (v == null) {
				scrollBarThumbHeight = 0;
				return;
			}
			final int vh = (viewHeight - bottomPadding - topPadding);
			contentsHeight = (itemCount * itemHeight);
			if (dividerHeight != 0 && itemCount > 1)
				contentsHeight += (itemCount - 1) * dividerHeight;
			if (contentsHeight <= vh) {
				scrollBarThumbHeight = 0;
				return;
			}
			scrollBarThumbHeight = (sbh * vh) / contentsHeight;
			if (scrollBarThumbHeight < UI.controlMargin)
				scrollBarThumbHeight = UI.controlMargin;
			scrollBarThumbTop = scrollBarTop + (((sbh - scrollBarThumbHeight) * ((getFirstVisiblePosition() * (itemHeight + dividerHeight)) - v.getTop())) / (contentsHeight - vh));
		}
	}
	
	private void updateScrollBarIndices(boolean updateEverything) {
		if (updateEverything) {
			if (adapter == null || !(adapter instanceof BaseList.BaseSectionIndexer)) {
				sections = null;
				sectionPositions = null;
			} else {
				final BaseList.BaseSectionIndexer a = (BaseList.BaseSectionIndexer)adapter;
				sections = a.getSectionStrings();
				sectionPositions = a.getSectionPositions();
				if (sections == null || sectionPositions == null || sections.length != sectionPositions.length || sections.length == 0) {
					sections = null;
					sectionPositions = null;
				}
			}
		}
		if (sections != null) {
			//let's reuse a few variables... ;)
			scrollBarThumbHeight = (scrollBarBottom - scrollBarTop) / sections.length;
			contentsHeight = scrollBarThumbHeight - (UI._1dp << 1);
			if (contentsHeight > (scrollBarWidth - UI._1dp))
				contentsHeight = (scrollBarWidth - UI._1dp);
			if (contentsHeight < (UI._1dp << 1))
				contentsHeight = (UI._1dp << 1);
			UI.textPaint.setTextSize(contentsHeight);
			final Paint.FontMetrics fm = UI.textPaint.getFontMetrics();
			final int box = (int)(fm.descent - fm.ascent + 0.5f);
			scrollBarThumbOffset = ((scrollBarThumbHeight - box) >> 1) + (box - (int)fm.descent);
		} else {
			scrollBarThumbHeight = 0;
			contentsHeight = 0;
			scrollBarThumbOffset = 0;
		}
		updateScrollBarSectionForFirstPosition();
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setVerticalScrollBarPosition() {
		super.setVerticalScrollbarPosition(UI.scrollBarToTheLeft ? SCROLLBAR_POSITION_LEFT : SCROLLBAR_POSITION_RIGHT);
	}

	public int getScrollBarType() {
		return scrollBarType;
	}

	public void setScrollBarType(int scrollBarType) {
		cancelTracking();
		switch (scrollBarType) {
		case SCROLLBAR_LARGE:
			if (this.scrollBarType == SCROLLBAR_LARGE)
				return;
			sections = null;
			sectionPositions = null;
			ignorePadding = true;
			super.setVerticalScrollBarEnabled(false);
			super.setOnScrollListener(this);
			ignorePadding = false;
			this.scrollBarType = SCROLLBAR_LARGE;
			scrollBarTop = topPadding + UI.controlSmallMargin;
			scrollBarBottom = viewHeight - bottomPadding - UI.controlSmallMargin;
			scrollBarWidth = (UI.defaultControlSize >> 1);
			updateScrollBarThumb();
			break;
		case SCROLLBAR_INDEXED:
			if (this.scrollBarType == SCROLLBAR_INDEXED)
				return;
			ignorePadding = true;
			super.setVerticalScrollBarEnabled(false);
			super.setOnScrollListener(this);
			ignorePadding = false;
			this.scrollBarType = SCROLLBAR_INDEXED;
			scrollBarTop = topPadding;
			scrollBarBottom = viewHeight - bottomPadding;
			scrollBarWidth = (UI.defaultControlSize >> 1);
			updateScrollBarIndices(true);
			break;
		case SCROLLBAR_NONE:
			if (this.scrollBarType == SCROLLBAR_NONE)
				return;
			sections = null;
			sectionPositions = null;
			ignorePadding = true;
			super.setOnScrollListener(scrollListener);
			super.setVerticalScrollBarEnabled(false);
			ignorePadding = false;
			this.scrollBarType = SCROLLBAR_NONE;
			scrollBarWidth = 0;
			break;
		default:
			if (this.scrollBarType == SCROLLBAR_SYSTEM)
				return;
			sections = null;
			sectionPositions = null;
			ignorePadding = true;
			super.setOnScrollListener(scrollListener);
			super.setVerticalScrollBarEnabled(true);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				setVerticalScrollBarPosition();
			ignorePadding = false;
			this.scrollBarType = SCROLLBAR_SYSTEM;
			scrollBarWidth = 0;
			UI.tryToChangeScrollBarThumb(this, UI.color_divider);
			break;
		}
		ignorePadding = true;
		if (UI.scrollBarToTheLeft) {
			scrollBarLeft = 0;//leftPadding;
			super.setPadding(leftPadding + scrollBarWidth, topPadding, rightPadding, bottomPadding);
		} else {
			scrollBarLeft = viewWidth - scrollBarWidth;//(viewWidth - (rightPadding + scrollBarWidth));
			super.setPadding(leftPadding, topPadding, rightPadding + scrollBarWidth, bottomPadding);
		}
		ignorePadding = false;
		UI.removeInternalPaddingForEdgeEffect(this);
	}

	@Override
	public void setOnScrollListener(OnScrollListener l) {
		scrollListener = l;
		if (scrollBarType == SCROLLBAR_NONE || scrollBarType == SCROLLBAR_SYSTEM)
			super.setOnScrollListener(l);
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		if (ignorePadding)
			return;
		if (UI.scrollBarToTheLeft) {
			scrollBarLeft = 0;
			super.setPadding((leftPadding = left) + scrollBarWidth, (topPadding = top), (rightPadding = right), (bottomPadding = bottom));
		} else {
			scrollBarLeft = viewWidth - scrollBarWidth;
			super.setPadding((leftPadding = left), (topPadding = top), (rightPadding = right) + scrollBarWidth, (bottomPadding = bottom));
		}
		UI.removeInternalPaddingForEdgeEffect(this);
	}
	
	private void defaultKeyDown(int keyCode) {
		if (adapter == null)
			return;
		final int s = getNewPosition(adapter.getSelection(), keyCode, true);
		if (s >= 0 && s < itemCount) {
			adapter.setSelection(s, true);
			if (s <= getFirstVisiblePosition() || s >= getLastVisiblePosition())
				centerItem(s);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
		if (isEnabled()) {
			switch (keyCode) {
			case UI.KEY_UP:
			case UI.KEY_DOWN:
				if (extraState == 0) {
					extraState = UI.STATE_SELECTED;
					invalidateSelectedItem();
				}
				if (skipUpDownTranslation)
					break;
				//change the key to make sure the focus goes
				//somewhere else when the list is empty, or when the
				//selection is not set to wrap around and it is at
				//the top/bottom of the list
				if (adapter == null || itemCount == 0) {
					keyCode = ((keyCode == UI.KEY_UP) ? UI.KEY_LEFT : UI.KEY_RIGHT);
				} else if (!UI.wrapAroundList) {
					if (keyCode == UI.KEY_UP) {
						if (adapter.getSelection() == 0)
							keyCode = (UI.KEY_LEFT);
					} else if (adapter.getSelection() == (itemCount - 1)) {
						keyCode = (UI.KEY_RIGHT);
					}
				}
				break;
			case KeyEvent.KEYCODE_TAB:
				keyCode = UI.KEY_RIGHT;
			case UI.KEY_LEFT:
			case UI.KEY_RIGHT:
			case UI.KEY_DEL:
			case UI.KEY_EXTRA:
			case UI.KEY_HOME:
			case UI.KEY_END:
			case UI.KEY_PAGE_UP:
			case UI.KEY_PAGE_DOWN:
				if (extraState == 0) {
					extraState = UI.STATE_SELECTED;
					invalidateSelectedItem();
				}
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_NUMPAD_ENTER:
			case KeyEvent.KEYCODE_BUTTON_START:
			case KeyEvent.KEYCODE_BUTTON_A:
			case KeyEvent.KEYCODE_BUTTON_B:
				keyCode = UI.KEY_ENTER;
				if (emptyListClickListener != null && itemCount == 0)
					emptyListClickListener.onClick(this);
				break;
			case KeyEvent.KEYCODE_DEL:
				keyCode = UI.KEY_DEL;
				break;
			case KeyEvent.KEYCODE_BUTTON_SELECT:
			case KeyEvent.KEYCODE_BUTTON_X:
			case KeyEvent.KEYCODE_BUTTON_Y:
				keyCode = UI.KEY_EXTRA;
				break;
			default:
				if ((keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) || (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9))
					keyCode = UI.KEY_EXTRA;
				else
					return super.onKeyDown(keyCode, event);
				break;
			}
			if (keyDownObserver == null || !keyDownObserver.onBgListViewKeyDown(this, keyCode))
				defaultKeyDown(keyCode);
			return true;
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}
	
	/*@Override
	protected void dispatchDraw(Canvas canvas) {
		//Apparently, a few devices actually call dispatchDraw() with a null canvas...?!?!
		if (canvas == null)
			return;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			super.dispatchDraw(canvas);
		} else {
			//LAME!!!!! There is a bug in API 10 and the padding
			//IS NOT properly accounted for and the children are not clipped!!!
			canvas.save();
			final Rect rect = UI.rect;
			getDrawingRect(rect);
			rect.top += getPaddingTop();
			rect.bottom -= getPaddingBottom();
			canvas.clipRect(rect);
			super.dispatchDraw(canvas);
			canvas.restore();
		}
	}*/
	
	@Override
	public void setAdapter(ListAdapter adapter) {
		this.adapter = (BaseList<? extends BaseItem>)adapter;
		itemHeight = ((adapter == null) ? UI.defaultControlSize : this.adapter.getViewHeight());
		cancelTracking();
		itemCount = ((adapter == null) ? 0 : adapter.getCount());
		updateContentDescription();
		switch (scrollBarType) {
		case SCROLLBAR_LARGE:
			updateScrollBarThumb();
			break;
		case SCROLLBAR_INDEXED:
			updateScrollBarIndices(true);
			break;
		}
		super.setAdapter(adapter);
	}
	
	@Override
	protected void handleDataChanged() {
		final int last = itemCount;
		itemCount = ((adapter == null) ? 0 : adapter.getCount());
		if (itemCount == 0 || last > itemCount)
			cancelTracking();
		if (last == 0 || itemCount == 0)
			updateContentDescription();
		switch (scrollBarType) {
		case SCROLLBAR_LARGE:
			updateScrollBarThumb();
			break;
		case SCROLLBAR_INDEXED:
			updateScrollBarIndices(true);
			break;
		}
		super.handleDataChanged();
	}
	
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (scrollListener != null)
			scrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		if (tracking)
			return;
		switch (scrollBarType) {
		case SCROLLBAR_LARGE:
			updateScrollBarThumb();
			break;
		case SCROLLBAR_INDEXED:
			updateScrollBarSectionForFirstPosition();
			break;
		}
	}
	
	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (scrollListener != null)
			scrollListener.onScrollStateChanged(view, scrollState);
		this.scrollState = scrollState;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		//Apparently, a few devices actually call dispatchDraw() with a null canvas...?!?!
		if (canvas == null)
			return;
		super.dispatchDraw(canvas);
		if (itemCount == 0) {
			if (emptyLayout != null) {
				final float x = (float)((viewWidth - emptyLayout.getWidth()) >> 1);
				final float y = (float)((viewHeight - emptyLayout.getHeight()) >> 1);
				canvas.translate(x, y);
				UI.textPaint.setColor(UI.color_text_listitem_disabled);
				UI.textPaint.setTextSize(UI._Headingsp);
				emptyLayout.draw(canvas);
				canvas.translate(-x, -y);
			}
		} else {
			final Rect rect = UI.rect;
			switch (scrollBarType) {
			case SCROLLBAR_INDEXED:
				if (sections != null) {
					UI.textPaint.setColor(UI.color_text_highlight);
					UI.textPaint.setTextSize(contentsHeight);
					UI.textPaint.setTextAlign(Paint.Align.CENTER);
					final float l;
					if (tracking) {
						if (UI.scrollBarToTheLeft) {
							rect.left = scrollBarLeft;
							l = (float)(rect.left + UI.defaultControlContentsSize + (UI.defaultControlContentsSize >> 1) + (scrollBarWidth >> 1));
						} else {
							rect.left = scrollBarLeft - UI.defaultControlContentsSize - (UI.defaultControlContentsSize >> 1);
							l = (float)(rect.left + (scrollBarWidth >> 1));
						}
						rect.right = rect.left + UI.defaultControlContentsSize + (UI.defaultControlContentsSize >> 1) + scrollBarWidth;
					} else {
						rect.left = scrollBarLeft;
						rect.right = scrollBarLeft + scrollBarWidth;
						l = (float)(scrollBarLeft + (scrollBarWidth >> 1));
					}
					rect.top = scrollBarTop;
					rect.bottom = scrollBarBottom;
					UI.fillRect(rect, canvas, UI.color_highlight);
					int i;
					for (i = 0; i < scrollBarThumbTop; i++) {
						canvas.drawText(sections[i], l, (float)(rect.top + scrollBarThumbOffset), UI.textPaint);
						rect.top += scrollBarThumbHeight;
					}
					rect.bottom = rect.top + scrollBarThumbHeight;
					UI.textPaint.setColor(UI.color_text_listitem);
					UI.fillRect(rect, canvas, backgroundColor);
					canvas.drawText(sections[i], l, (float)(rect.top + scrollBarThumbOffset), UI.textPaint);
					UI.textPaint.setColor(UI.color_text_highlight);
					rect.top = rect.bottom;
					i++;
					for (; i < sections.length; i++) {
						canvas.drawText(sections[i], l, (float)(rect.top + scrollBarThumbOffset), UI.textPaint);
						rect.top += scrollBarThumbHeight;
					}
					UI.textPaint.setTextAlign(Paint.Align.LEFT);
				}
				break;
			case SCROLLBAR_LARGE:
				rect.left = scrollBarLeft + ((scrollBarWidth - UI.strokeSize) >> 1);
				rect.right = rect.left + UI.strokeSize;
				rect.top = scrollBarTop + UI.controlSmallMargin;
				rect.bottom = scrollBarBottom - UI.controlSmallMargin;
				UI.fillRect(rect, canvas, UI.color_divider);
				if (scrollBarThumbHeight > 0) {
					rect.left = scrollBarLeft + UI.controlSmallMargin;
					rect.top = scrollBarThumbTop;
					rect.right = scrollBarLeft + scrollBarWidth - UI.controlSmallMargin;
					rect.bottom = rect.top + scrollBarThumbHeight;
					UI.fillRect(rect, canvas, tracking ? UI.color_divider_pressed : UI.color_divider);
				}
				break;
			}
		}
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		//there is no need to call super.onDraw() here, as ListView does
		//not override this method... therefore, it does nothing!
		//super.onDraw(canvas);
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		cancelTracking();
		super.onWindowFocusChanged(hasWindowFocus);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		attachedObserver = null;
		keyDownObserver = null;
		emptyListClickListener = null;
		scrollListener = null;
		emptyLayout = null;
		sections = null;
		sectionPositions = null;
		setAdapter(null);
		super.onDetachedFromWindow();
	}
}
