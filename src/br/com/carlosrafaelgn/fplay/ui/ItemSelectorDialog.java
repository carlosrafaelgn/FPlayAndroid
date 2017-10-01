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

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.list.BaseItem;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;

public final class ItemSelectorDialog<E extends ItemSelectorDialog.Item> implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener, DialogInterface.OnDismissListener, BaseList.ItemClickListener {
	public interface Observer<E extends Item> {
		void onItemSelectorDialogClosed(ItemSelectorDialog<E> itemSelectorDialog);
		void onItemSelectorDialogRefreshList(ItemSelectorDialog<E> itemSelectorDialog);
		void onItemSelectorDialogItemClicked(ItemSelectorDialog<E> itemSelectorDialog, int position, E item);
	}

	public static class Item extends BaseItem {
		public final FileSt fileSt;

		public Item(FileSt fileSt) {
			this.fileSt = fileSt;
		}

		@Override
		public String toString() {
			return fileSt.name;
		}
	}

	private static final class ItemList<E extends Item> extends BaseList<E> implements ArraySorter.Comparer<E> {
		public final int scrollBarType;

		public ItemList(Class<E> clazz) {
			super(clazz, 128);

			this.scrollBarType = ((UI.browserScrollBarType == BgListView.SCROLLBAR_NONE) ? BgListView.SCROLLBAR_NONE : BgListView.SCROLLBAR_SYSTEM);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			FileView view = (FileView)convertView;
			if (view == null)
				view = new FileView(Player.theApplication, false, false, scrollBarType);
			view.setItemState(items[position].fileSt, position, getItemState(position), this, null, scrollBarType);
			return view;
		}

		@Override
		public int getViewHeight() {
			return FileView.getViewHeight(false);
		}

		@Override
		public int compare(E a, E b) {
			return a.fileSt.name.compareToIgnoreCase(b.fileSt.name);
		}

		public void sort() {
			//synchronized (sync) {
			modificationVersion++;
			ArraySorter.sort(items, 0, this.count, this);
			current = -1;
			firstSel = -1;
			lastSel = -1;
			originalSel = -1;
			indexOfPreviouslyDeletedCurrentItem = -1;
			//}
			notifyDataSetChanged(-1, CONTENT_MOVED);
		}
	}

	private ItemList<E> itemList;
	private Observer<E> observer;
	private boolean progressBarVisible, cancelled;
	private CharSequence loadingMessage, connectingMessage;
	private TextView lblTitle;
	private BgProgressBar barWait;
	private BgListView listView;
	private BgDialog dialog;

	private ItemSelectorDialog(Activity activity, CharSequence title, CharSequence loadingMessage, CharSequence connectingMessage, Class<E> clazz, E[] initialElements, Observer<E> observer) {
		itemList = new ItemList<>(clazz);
		if (initialElements != null && initialElements.length > 0)
			itemList.add(0, initialElements, 0, initialElements.length);
		itemList.sort();
		this.observer = observer;

		final LinearLayout l = new LinearLayout(activity);
		final LinearLayout panelControls = new LinearLayout(activity);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			UI.removeSplitTouch(l);
			UI.removeSplitTouch(panelControls);
		}
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBaselineAligned(false);
		panelControls.setOrientation(LinearLayout.VERTICAL);
		panelControls.setBaselineAligned(false);
		UI.prepareControlContainer(panelControls, false, true, 0, 0, 0, 0);

		this.connectingMessage = connectingMessage;

		lblTitle = UI.createDialogTextView(activity, 0, this.loadingMessage = loadingMessage);
		lblTitle.setPadding(UI.dialogMargin, 0, UI.dialogMargin, UI.dialogMargin);
		lblTitle.setTextColor(UI.colorState_text_static);
		lblTitle.setVisibility(View.GONE);
		panelControls.addView(lblTitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		barWait = new BgProgressBar(activity);
		barWait.setVisibility(View.GONE);
		final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		p.leftMargin = UI.dialogMargin;
		p.rightMargin = UI.dialogMargin;
		p.bottomMargin = UI.dialogMargin;
		panelControls.addView(barWait, p);

		l.addView(panelControls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		listView = new BgListView(activity);
		listView.setScrollBarType(itemList.scrollBarType);
		l.addView(listView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		itemList.setItemClickListener(this);
		itemList.setObserver(listView);

		dialog = new BgDialog(activity, l, this);
		dialog.setTitle(title);
		dialog.setPositiveButton(R.string.refresh_list);
		dialog.setNegativeButton(R.string.cancel);
		dialog.setOnCancelListener(this);
		dialog.setOnDismissListener(this);
		dialog.setEmptyBackground();
		dialog.removeTitleBorder();
		dialog.show();
	}

	public static <T extends Item> ItemSelectorDialog<T> showDialog(Activity activity, CharSequence title, CharSequence loadingMessage, CharSequence connectingMessage, boolean progressBarVisible, Class<T> clazz, T[] initialElements, Observer<T> observer) {
		final ItemSelectorDialog<T> dialog = new ItemSelectorDialog<>(activity, title, loadingMessage, connectingMessage, clazz, initialElements, observer);
		dialog.showProgressBar(progressBarVisible);
		return dialog;
	}

	public void add(E item) {
		if (itemList != null)
			itemList.add(item, -1);
	}

	public void clear() {
		if (itemList != null)
			itemList.clear();
	}

	public void remove(int position) {
		if (itemList != null && position >= 0 && position < itemList.getCount()) {
			itemList.setSelection(position, true);
			itemList.removeSelection();
			itemList.setSelection(-1, true);
		}
	}

	public void dismiss() {
		if (dialog != null)
			dialog.dismiss();
	}

	public void cancel() {
		if (dialog != null)
			dialog.cancel();
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public int getCount() {
		return ((itemList != null) ? itemList.getCount() : 0);
	}

	public E getItem(int position) {
		return ((itemList != null) ? itemList.getItem(position) : null);
	}

	public void showProgressBar(boolean show) {
		if (progressBarVisible == show)
			return;
		progressBarVisible = show;
		final boolean forceTitle = (listView == null || listView.getVisibility() != View.VISIBLE);
		if (lblTitle != null)
			lblTitle.setVisibility((forceTitle || show) ? View.VISIBLE : View.GONE);
		if (barWait != null)
			barWait.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	public void showConnecting(boolean connecting) {
		if (dialog != null)
			dialog.showPositiveButton(!connecting);
		if (lblTitle != null) {
			lblTitle.setText(connecting ? connectingMessage : loadingMessage);
			showProgressBar(connecting);
		}
		if (listView != null)
			listView.setVisibility(connecting ? View.GONE : View.VISIBLE);
	}

	@Override
	public void onItemClicked(int position) {
		if (dialog != null && itemList != null && position >= 0 && position < itemList.getCount() && observer != null)
			observer.onItemSelectorDialogItemClicked(this, position, itemList.getItem(position));
	}

	@Override
	public void onItemLongClicked(int position) {
	}

	@Override
	public void onItemCheckboxClicked(int position) {
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case DialogInterface.BUTTON_POSITIVE:
			if (observer != null && !progressBarVisible)
				observer.onItemSelectorDialogRefreshList(this);
			break;
		case DialogInterface.BUTTON_NEGATIVE:
			if (this.dialog != null)
				this.dialog.cancel();
			break;
		}
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		if (this.dialog != null) {
			this.dialog = null;
			if (observer != null) {
				observer.onItemSelectorDialogClosed(this);
				observer = null;
			}
			if (listView != null) {
				listView.setAdapter(null);
				listView = null;
			}
			if (itemList != null) {
				itemList.setItemClickListener(null);
				itemList = null;
			}
			lblTitle = null;
			barWait = null;
			loadingMessage = null;
			connectingMessage = null;
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		if (this.dialog != null)
			cancelled = true;
		onDismiss(dialog);
	}
}
