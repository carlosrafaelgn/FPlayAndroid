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
package br.com.carlosrafaelgn.fplay.list;

import android.database.DataSetObserver;
import android.widget.BaseAdapter;

import java.lang.reflect.Array;

import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.UI;

//All methods of this class MUST BE called from the main thread
public abstract class BaseList<E extends BaseItem> extends BaseAdapter {
	public interface BaseSectionIndexer {
		String[] getSectionStrings();
		int[] getSectionPositions();
	}

	public interface ItemClickListener {
		void onItemClicked(int position);
		void onItemLongClicked(int position);
		void onItemCheckboxClicked(int position);
	}

	public interface ActionListener {
		void onLoadingProcessChanged(boolean started);
	}

	public interface SelectionChangedListener<E extends BaseItem> {
		void onSelectionChanged(BaseList<E> list);
	}

	protected static final int MIN_CAPACITY_INCREMENT = 12;

	protected static final int SELECTION_CHANGED = 0;
	protected static final int LIST_CLEARED = 1;
	protected static final int CONTENT_MOVED = 2;
	protected static final int CONTENT_ADDED = 3;
	protected static final int CONTENT_REMOVED = 4;

	protected BgListView listObserver;
	protected DataSetObserver observer;
	private SelectionChangedListener<E> listener;
	private ItemClickListener itemClickListener;
	private ActionListener actionListener;
	protected final Object currentAndCountMutex;
	private final int maxCount;
	private final Class<E> clazz;
	protected E[] items;
	protected int count, current, firstSel, lastSel, originalSel, indexOfPreviouslyDeletedCurrentItem, modificationVersion;

	@SuppressWarnings("unchecked")
	public BaseList(Class<E> clazz, int maxCount) {
		this.currentAndCountMutex = new Object();
		this.maxCount = maxCount;
		this.clazz = clazz;
		this.items = (E[])Array.newInstance(clazz, MIN_CAPACITY_INCREMENT);
		this.current = -1;
		this.firstSel = -1;
		this.lastSel = -1;
		this.originalSel = -1;
		this.indexOfPreviouslyDeletedCurrentItem = -1;
	}

	protected void addingItems(int position, int count) { }

	protected void removingItems(int position, int count) { }

	protected void clearingItems() { }

	@SuppressWarnings("unchecked")
	protected void setCapacity(int capacity) {
		final E[] array;
		if (capacity >= count && (capacity > (array = items).length || capacity <= (array.length >> 2))) {
			if (capacity <= 0) capacity = MIN_CAPACITY_INCREMENT;
			final E[] newArray = (E[])Array.newInstance(clazz, capacity + (capacity >> 1));
			System.arraycopy(array, 0, newArray, 0, count);
			items = newArray;
		}
	}

	public final int getModificationVersion() {
		return modificationVersion;
	}

	public final void add(E item, int position) {
		if (count >= maxCount)
			return;

		if (firstSel != lastSel)
			setSelection(firstSel, firstSel, false, false);
		
		if (position < 0 || position >= count)
			position = count;

		//synchronized (currentAndCountMutex) {
			setCapacity(count + 1);

			modificationVersion++;
			if (count != position)
				System.arraycopy(items, position, items, position + 1, count - position);
			items[position] = item;
			count++;
			if (current >= position)
				current++;
			if (current >= count)
				current = count - 1;
			if (firstSel >= position)
				firstSel++;
			if (lastSel >= position)
				lastSel++;
			addingItems(position, 1);
		//}
		
		notifyDataSetChanged(-1, CONTENT_ADDED);
	}

	public final void add(int position, E[] items, int firstIndex, int count) {
		if ((this.count + count) >= maxCount)
			count = maxCount - this.count;

		if (count <= 0)
			return;
		
		if (position < 0 || position >= this.count)
			position = this.count;
		if (count > items.length)
			count = items.length;

		//synchronized (currentAndCountMutex) {
			setCapacity(this.count + count);

			modificationVersion++;
			if (this.count != position)
				System.arraycopy(this.items, position, this.items, position + count, this.count - position);
			System.arraycopy(items, firstIndex, this.items, position, count);
			this.count += count;
			if (current >= position)
				current += count;
			if (firstSel >= position)
				firstSel += count;
			if (lastSel >= position)
				lastSel += count;
			if (originalSel >= position)
				originalSel += count;
			addingItems(position, count);
		//}
		
		notifyDataSetChanged(-1, CONTENT_ADDED);
	}

	public final void clear() {
		//synchronized (currentAndCountMutex) {
			final int previousOriginalSel = originalSel;
			clearingItems();
			
			modificationVersion++;
			for (int i = items.length - 1; i >= 0; i--)
				items[i] = null;
			count = 0;
			current = -1;
			firstSel = -1;
			lastSel = -1;
			originalSel = -1;
			indexOfPreviouslyDeletedCurrentItem = -1;
		//}
		notifyDataSetChanged(-1, LIST_CLEARED);
		if (listener != null && previousOriginalSel != originalSel)
			listener.onSelectionChanged(this);
	}

	@SuppressWarnings("UnusedReturnValue")
	public final boolean removeSelection() {
		int position = firstSel, count = lastSel - firstSel + 1;
		if (position < 0 || position >= this.count || count <= 0)
			return false;
		
		if ((position + count) > this.count)
			count = this.count - position;
		if (count <= 0)
			return false;
		
		//synchronized (currentAndCountMutex) {
			final int previousOriginalSel = originalSel;
			if (firstSel != lastSel)
				setSelection(firstSel, firstSel, false, false);

			removingItems(position, count);
			
			modificationVersion++;
			
			System.arraycopy(items, position + count, items, position, (this.count - position - count));
			
			final int oldCount = this.count;
			this.count -= count;
			for (int i = this.count; i < oldCount; i++)
				items[i] = null;
			
			if (indexOfPreviouslyDeletedCurrentItem >= 0) {
				if (indexOfPreviouslyDeletedCurrentItem >= position && indexOfPreviouslyDeletedCurrentItem < (position + count))
					indexOfPreviouslyDeletedCurrentItem = position;
				else if (indexOfPreviouslyDeletedCurrentItem > position)
					indexOfPreviouslyDeletedCurrentItem -= count;
				if (indexOfPreviouslyDeletedCurrentItem >= this.count)
					indexOfPreviouslyDeletedCurrentItem = -1;
			}
			if (current >= position && current < (position + count)) {
				indexOfPreviouslyDeletedCurrentItem = position;
				current = -1;
			} else if (current > position) {
				current -= count;
			}
			if (current >= this.count)
				current = -1;
			if (firstSel >= this.count)
				firstSel = this.count - 1;
			//if (firstSel >= position && firstSel < (position + count)) {
			//	firstSel = -1;
			//	lastSel = -1;
			//} else if (firstSel > position) {
			//	firstSel -= count;
			//	lastSel = firstSel;
			//}
			lastSel = firstSel;
			originalSel = firstSel;

			setCapacity(this.count);
		//}

		notifyDataSetChanged(originalSel, CONTENT_REMOVED);

		if (listener != null && previousOriginalSel != originalSel)
			listener.onSelectionChanged(this);

		return true;
	}

	@SuppressWarnings("SuspiciousSystemArraycopy")
	public final void moveSelection(int to) {
		int from = firstSel, count = lastSel - firstSel + 1;
		if (from < 0 || to < 0 || count <= 0)
			return;
		if (count > this.count)
			count = this.count;
		if ((from + count) > this.count)
			count = this.count - from;
		if (count <= 0)
			return;
		if (to >= this.count)
			to = this.count - 1;
		if (to >= from && to < (from + count)) {
			if (originalSel != to) {
				originalSel = to;
				notifyDataSetChanged(-1, SELECTION_CHANGED);
			}
			return;
		}
		Object[] tmp = new Object[count];
		System.arraycopy(items, from, tmp, 0, count);
		//synchronized (currentAndCountMutex) {
			modificationVersion++;
			final int delta;
			if (to < from) {
				delta = to - from;
				System.arraycopy(items, to, items, to + count, from - to);
				System.arraycopy(tmp, 0, items, to, count);
			} else {
				delta = to - (from + count) + 1;
				System.arraycopy(items, from + count, items, from, delta);
				System.arraycopy(tmp, 0, items, from + delta, count);
			}
			if (current < from && current >= to)
				current += count;
			else if (current >= from && current < (from + count))
				current += delta;
			else if (current > from && current <= to)
				current -= count;
			firstSel += delta;
			lastSel += delta;
			originalSel = to;
		//}
		notifyDataSetChanged(-1, CONTENT_MOVED);
	}

	public final int getSelection() {
		return originalSel;
	}

	public final int getCurrentPosition() {
		return current;
	}

	public final int getFirstSelectedPosition() {
		return firstSel;
	}

	public final int getLastSelectedPosition() {
		return lastSel;
	}

	public final boolean isSelected(int position) {
		return (position >= 0 && position >= firstSel && position <= lastSel);
	}

	public final void setSelection(int position, boolean byUserInteraction) {
		setSelection(position, position, position, true, byUserInteraction);
	}

	public final void setSelection(int from, int to, boolean notifyChanged, boolean byUserInteraction) {
		setSelection(from, to, -1, notifyChanged, byUserInteraction);
	}

	public final void setSelection(int from, int to, int original, boolean notifyChanged, boolean byUserInteraction) {
		final int previousOriginalSel = originalSel;
		int gotoPosition = -1;
		firstSel = -1;
		lastSel = -1;
		if (from >= 0 && to >= 0) {
			if (from >= count)
				from = count - 1;
			if (to >= count)
				to = count - 1;
			if (from > to) {
				firstSel = to;
				lastSel = from;
			} else {
				firstSel = from;
				lastSel = to;
			}
			if (from == to && ((original < 0) || (from < 0))) {
				originalSel = from;
				gotoPosition = from;
			}
		} else {
			originalSel = -1;
		}
		if (original >= 0 && original >= firstSel && original <= lastSel) {
			originalSel = original;
			gotoPosition = original;
		}
		if (notifyChanged) {
			notifyDataSetChanged(byUserInteraction ? -1 : gotoPosition, SELECTION_CHANGED);
			if (listener != null && previousOriginalSel != originalSel)
				listener.onSelectionChanged(this);
		}
	}

	public final int indexOf(E item) {
		for (int i = 0; i < count; i++) {
			if (items[i] == item)
				return i;
		}
		return -1;
	}

	@Override
	public final int getCount() {
		return count;
	}

	@Override
	public final E getItem(int position) {
		return items[position];
	}

	@Override
	public final long getItemId(int position) {
		return items[position].id;
	}

	@Override
	public final boolean areAllItemsEnabled() {
		return true;
	}

	@Override
	public final int getItemViewType(int position) {
		return 0;
	}

	@Override
	public final int getViewTypeCount() {
		return 1;
	}

	@Override
	public final boolean hasStableIds() {
		return true;
	}

	@Override
	public final boolean isEnabled(int position) {
		return true;
	}

	@Override
	public final boolean isEmpty() {
		return (count == 0);
	}

	public final void setObserver(BgListView list) {
		//if (listObserver != null)
		//	listObserver.setAdapter(null);
		listObserver = list;
		if (list != null)
			list.setAdapter(this);
	}

	@Override
	public final void registerDataSetObserver(DataSetObserver observer) {
		//we only need to support one observer
		this.observer = observer;
	}

	@Override
	public final void unregisterDataSetObserver(DataSetObserver observer) {
		//we only need to support one observer
		this.observer = null;
	}

	public final ItemClickListener getItemClickListener() {
		return itemClickListener;
	}

	public final void setItemClickListener(ItemClickListener itemClickListener) {
		this.itemClickListener = itemClickListener;
	}

	public final ActionListener getActionListener() {
		return actionListener;
	}

	public final void setActionListener(ActionListener actionListener) {
		this.actionListener = actionListener;
	}

	public final void setSelectionChangedListener(SelectionChangedListener<E> listener) {
		this.listener = listener;
	}

	protected void notifyDataSetChanged(int gotoPosition, int whatHappened) {
		if (observer != null)
			observer.onChanged();
		if (listObserver != null && gotoPosition >= 0)
			listObserver.centerItem(gotoPosition);
	}

	public void notifyCheckedChanged() {
		notifyDataSetChanged(-1, SELECTION_CHANGED);
	}

	protected final int getItemState(int position) {
		return ((position == current) ? UI.STATE_CURRENT : 0) | ((position == originalSel) ? UI.STATE_SELECTED :
			((position >= firstSel && position <= lastSel) ? UI.STATE_MULTISELECTED : 0));
	}

	public abstract int getViewHeight();
}
