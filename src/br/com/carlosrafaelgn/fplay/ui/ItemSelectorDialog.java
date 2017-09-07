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
import android.view.View;

import br.com.carlosrafaelgn.fplay.util.TypedRawArrayList;

public final class ItemSelectorDialog<E> {
	public interface Listener<E> {
		void onItemSelectorDialogOpened(ItemSelectorDialog<E> itemSelectorDialog);
		void onItemSelectorDialogClosed(ItemSelectorDialog<E> itemSelectorDialog);
		void onItemSelectorDialogRefreshList(ItemSelectorDialog<E> itemSelectorDialog);
		void onItemSelectorDialogItemClicked(ItemSelectorDialog<E> itemSelectorDialog, int index, E item);
	}

	private TypedRawArrayList<E> list;
	private Listener<E> listener;
	private boolean progressBarVisible;
	private View loadingMessage, progressBar;

	private ItemSelectorDialog(Class<E> clazz, E[] initialElements, Listener<E> listener) {
		list = new TypedRawArrayList<>(clazz);
		if (initialElements != null && initialElements.length > 0)
			list.addAll(initialElements);
		this.listener = listener;
	}

	public static <E> ItemSelectorDialog<E> showDialog(Context context, CharSequence title, CharSequence loadingMessage, boolean progressBarVisible, Class<E> clazz, E[] initialElements, Listener<E> listener) {
		ItemSelectorDialog<E> dialog = new ItemSelectorDialog<>(clazz, initialElements, listener);
		dialog.showProgressBar(progressBarVisible);
		return null;
	}

	public void add(E item) {

	}

	public void clear() {

	}

	public boolean isProgressBarVisible() {
		return progressBarVisible;
	}

	public void showProgressBar(boolean show) {
		if (progressBarVisible == show)
			return;
		progressBarVisible = show;
		if (loadingMessage != null)
			loadingMessage.setVisibility(show ? View.VISIBLE : View.GONE);
		if (loadingMessage != null)
			loadingMessage.setVisibility(show ? View.VISIBLE : View.GONE);
	}
}
