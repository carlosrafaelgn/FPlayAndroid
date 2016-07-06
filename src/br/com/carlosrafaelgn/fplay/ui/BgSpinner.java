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
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.ActivityBrowserView;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public class BgSpinner<E> extends TextView implements View.OnClickListener, DialogInterface.OnDismissListener {
	public interface OnItemSelectedListener<E> {
		void onItemSelected(BgSpinner<E> spinner, int position);
	}

	private static final class SpinnerList extends BaseList<FileSt> {
		public SpinnerList(FileSt[] items, int selectedPosition) {
			super(FileSt.class, 256);
			this.items = items;
			setSelection(selectedPosition, false);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			FileView view = (FileView)convertView;
			if (view == null)
				view = new FileView(Player.theApplication, null, false);
			view.setItemState(items[position], position, getItemState(position));
			return view;
		}

		@Override
		public int getViewHeight() {
			return FileView.getViewHeight();
		}
	}

	private int state, selectedPosition;
	private boolean dialogVisible;
	private E[] items;
	private OnItemSelectedListener<E> listener;
	private ActivityBrowserView oldActivityBrowserView;

	public BgSpinner(Context context) {
		super(context);
		init();
	}

	public BgSpinner(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public BgSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		selectedPosition = -1;
		super.setBackgroundResource(0);
		super.setDrawingCacheEnabled(false);
		super.setSingleLine(true);
		super.setEllipsize(TextUtils.TruncateAt.END);
		super.setTextColor(UI.colorState_text_listitem_static);
		super.setTypeface(UI.defaultTypeface);
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		super.setGravity(Gravity.CENTER_VERTICAL);
		super.setPadding(UI.controlLargeMargin, 0, UI.controlLargeMargin, 0);
		super.setFocusableInTouchMode(!UI.hasTouch);
		super.setFocusable(true);
		super.setMinimumWidth(UI.defaultControlSize);
		super.setMinimumHeight(UI.defaultControlSize);
		super.setOnClickListener(this);
	}

	@SuppressWarnings("unchecked")
	public void setItems(E[] items) {
		this.items = items;
		if (selectedPosition < 0)
			return;
		if (items == null)
			setSelectedItemPosition(-1);
		else if (selectedPosition >= items.length)
			setSelectedItemPosition(items.length - 1);
		else
			setText(items[selectedPosition].toString());
	}

	public int getSelectedItemPosition() {
		return selectedPosition;
	}

	public void setSelectedItemPosition(int selectedPosition) {
		if (items == null || selectedPosition < 0) {
			if (this.selectedPosition != -1) {
				setText("");
				this.selectedPosition = -1;
				if (listener != null)
					listener.onItemSelected(this, -1);
			}
			return;
		}
		if (selectedPosition >= items.length)
			selectedPosition = items.length - 1;
		if (this.selectedPosition != selectedPosition) {
			setText(items[selectedPosition].toString());
			this.selectedPosition = selectedPosition;
			if (listener != null)
				listener.onItemSelected(this, selectedPosition);
		}
	}

	public void setOnItemSelectedListener(OnItemSelectedListener<E> listener) {
		this.listener = listener;
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
	@ViewDebug.ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return false;
	}

	@Override
	public boolean hasOverlappingRendering() {
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_NUMPAD_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
			keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
			event = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), 232, event.getFlags(), event.getSource());
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_NUMPAD_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
			keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
			event = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), 232, event.getFlags(), event.getSource());
			break;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_NUMPAD_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
			keyCode = KeyEvent.KEYCODE_DPAD_CENTER;
			event = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), 232, event.getFlags(), event.getSource());
			break;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		getDrawingRect(UI.rect);
		final int color = ((state == 0) ? UI.color_dialog_detail : UI.color_dialog_detail_highlight);
		UI.rect.top = UI.rect.bottom - (state == 0 ? UI.strokeSize : UI.thickDividerSize);
		UI.fillRect(canvas, color);
		TextIconDrawable.drawIcon(canvas, UI.ICON_SPINNERARROW, UI.rect.right - UI.controlLargeMargin, UI.rect.bottom - UI.controlLargeMargin, UI.controlLargeMargin, color);
		super.onDraw(canvas);
	}

	@Override
	protected void onDetachedFromWindow() {
		items = null;
		listener = null;
		super.onDetachedFromWindow();
	}

	@Override
	public void onClick(View view) {
		if (items == null || items.length == 0 || dialogVisible)
			return;
		dialogVisible = true;
		oldActivityBrowserView = UI.browserActivity;
		UI.browserActivity = new ActivityBrowserView() {
			@Override
			public void loadingProcessChanged(boolean started) {
			}

			@Override
			public void processItemCheckboxClick(int position) {
			}

			@Override
			public void processItemClick(int position) {
			}

			@Override
			public void processItemLongClick(int position) {
			}

			@Override
			public CharSequence getTitle() {
				return null;
			}
		};
		FileView.updateExtraMargins(true);

		BgListView listView = new BgListView(getContext());
		listView.setScrollBarType((UI.browserScrollBarType == BgListView.SCROLLBAR_NONE) ? BgListView.SCROLLBAR_NONE : BgListView.SCROLLBAR_SYSTEM);

		BgDialog dialog = new BgDialog(getContext(), listView, null);
		dialog.setOnDismissListener(this);
		dialog.show();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		dialogVisible = false;
		UI.browserActivity = oldActivityBrowserView;
		oldActivityBrowserView = null;
	}
}
