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

//
// My version of the standard ArrayList<E> class!!! ;)
//

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package br.com.carlosrafaelgn.fplay.util;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

public final class TypedRawArrayList<E> extends AbstractList<E> implements Cloneable, Serializable, RandomAccess {
	/**
	 * The minimum amount by which the capacity of a TypedRawArrayList will increase.
	 * This tuning parameter controls a time-space tradeoff. This value (12)
	 * gives empirically good results and is arguably consistent with the
	 * RI's specified default initial capacity of 10: instead of 10, we start
	 * with 0 (sans allocation) and jump to 12.
	 */
	private static final int MIN_CAPACITY_INCREMENT = 12;

	/**
	 * The number of elements in this list.
	 */
	private int size;

	/**
	 * The elements in this list, followed by nulls.
	 */
	private transient E[] array;

	private final Class<E> clazz;

	/**
	 * Constructs a new instance of {@code TypedRawArrayList} with the specified
	 * initial capacity.
	 *
	 * @param capacity
	 *            the initial capacity of this {@code TypedRawArrayList}.
	 */
	@SuppressWarnings("unchecked")
	public TypedRawArrayList(Class<E> clazz, int capacity) {
		if (capacity < 0)
			throw new IllegalArgumentException("capacity < 0: " + capacity);
		this.clazz = clazz;
		array = (E[])Array.newInstance(clazz, capacity);
	}

	/**
	 * Constructs a new {@code TypedRawArrayList} instance with zero initial capacity.
	 */
	@SuppressWarnings({"unchecked", "unused"})
	public TypedRawArrayList(Class<E> clazz) {
		this.clazz = clazz;
		array = (E[])Array.newInstance(clazz, MIN_CAPACITY_INCREMENT);
	}

	@NonNull
	public E[] getRawArray() {
		return array;
	}

	/**
	 * Adds the specified object at the end of this {@code TypedRawArrayList}.
	 *
	 * @param object
	 *            the object to add.
	 * @return always true
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean add(E object) {
		E[] a = array;
		final int s = size;
		if (s == a.length) {
			final E[] newArray = (E[])Array.newInstance(clazz, s +
				(s < (MIN_CAPACITY_INCREMENT / 2) ?
					MIN_CAPACITY_INCREMENT : s >> 1));
			System.arraycopy(a, 0, newArray, 0, s);
			array = a = newArray;
		}
		a[s] = object;
		size = s + 1;
		modCount++;
		return true;
	}

	/**
	 * Inserts the specified object into this {@code TypedRawArrayList} at the specified
	 * location. The object is inserted before any previous element at the
	 * specified location. If the location is equal to the size of this
	 * {@code TypedRawArrayList}, the object is added at the end.
	 *
	 * @param index
	 *            the index at which to insert the object.
	 * @param object
	 *            the object to add.
	 * @throws IndexOutOfBoundsException
	 *             when {@code location < 0 || location > size()}
	 */
	@SuppressWarnings("unchecked")
	@Override public void add(int index, E object) {
		E[] a = array;
		final int s = size;
		if (index > s || index < 0)
			throwIndexOutOfBoundsException(index, s);
		if (s < a.length) {
			System.arraycopy(a, index, a, index + 1, s - index);
		} else {
			// assert s == a.length;
			final E[] newArray = (E[])Array.newInstance(clazz, s +
				(s < (MIN_CAPACITY_INCREMENT / 2) ?
					MIN_CAPACITY_INCREMENT : s >> 1));
			System.arraycopy(a, 0, newArray, 0, index);
			System.arraycopy(a, index, newArray, index + 1, s - index);
			array = a = newArray;
		}
		a[index] = object;
		size = s + 1;
		modCount++;
	}

	/*
	 * This method controls the growth of TypedRawArrayList capacities.  It represents
	 * a time-space tradeoff: we don't want to grow lists too frequently
	 * (which wastes time and fragments storage), but we don't want to waste
	 * too much space in unused excess capacity.
	 *
	 * NOTE: This method is inlined into {@link #add(Object)} for performance.
	 * If you change the method, change it there too!
	 */
	//private static int newCapacity(int currentCapacity) {
	//	int increment = (currentCapacity < (MIN_CAPACITY_INCREMENT / 2) ?
	//		MIN_CAPACITY_INCREMENT : currentCapacity >> 1);
	//	return currentCapacity + increment;
	//}

	/**
	 * Adds the objects in the specified array to this {@code TypedRawArrayList}.
	 *
	 * @param objects
	 *            the array of objects.
	 * @param length
	 *            the number of objects to copy.
	 * @return {@code true} if this {@code TypedRawArrayList} is modified, {@code false}
	 *         otherwise.
	 */
	@SuppressWarnings("unchecked")
	public boolean addAll(E[] objects, int length) {
		if (objects == null || length == 0)
			return false;
		E[] a = array;
		final int s = size;
		final int newSize = s + length; // If add overflows, arraycopy will fail
		if (newSize > a.length) {
			final int currentCapacity = newSize - 1;
			final E[] newArray = (E[])Array.newInstance(clazz, currentCapacity +
				(currentCapacity < (MIN_CAPACITY_INCREMENT / 2) ?
					MIN_CAPACITY_INCREMENT : currentCapacity >> 1)); // ~33% growth room
			System.arraycopy(a, 0, newArray, 0, s);
			array = a = newArray;
		}
		System.arraycopy(objects, 0, a, s, length);
		size = newSize;
		modCount++;
		return true;
	}

	/**
	 * Adds the objects in the specified array to this {@code TypedRawArrayList}.
	 *
	 * @param objects
	 *            the array of objects.
	 * @return {@code true} if this {@code TypedRawArrayList} is modified, {@code false}
	 *         otherwise.
	 */
	public boolean addAll(E[] objects) {
		return (objects != null && addAll(objects, objects.length));
	}

	/**
	 * Adds the objects in the specified collection to this {@code TypedRawArrayList}.
	 *
	 * @param collection
	 *            the collection of objects.
	 * @return {@code true} if this {@code TypedRawArrayList} is modified, {@code false}
	 *         otherwise.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean addAll(Collection<? extends E> collection) {
		return addAll(collection.toArray((E[])Array.newInstance(clazz, collection.size())));
	}

	/**
	 * Inserts the objects in the specified collection at the specified location
	 * in this List. The objects are added in the order they are returned from
	 * the collection's iterator.
	 *
	 * @param index
	 *            the index at which to insert.
	 * @param collection
	 *            the collection of objects.
	 * @return {@code true} if this {@code TypedRawArrayList} is modified, {@code false}
	 *         otherwise.
	 * @throws IndexOutOfBoundsException
	 *             when {@code location < 0 || location > size()}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean addAll(int index, @NonNull Collection<? extends E> collection) {
		final int s = size;
		if (index > s || index < 0)
			throwIndexOutOfBoundsException(index, s);
		final E[] newPart = collection.toArray((E[])Array.newInstance(clazz, collection.size()));
		final int newPartSize = newPart.length;
		if (newPartSize == 0)
			return false;
		E[] a = array;
		final int newSize = s + newPartSize; // If add overflows, arraycopy will fail
		if (newSize <= a.length) {
			System.arraycopy(a, index, a, index + newPartSize, s - index);
		} else {
			final int currentCapacity = newSize - 1;
			final E[] newArray = (E[])Array.newInstance(clazz, currentCapacity +
				(currentCapacity < (MIN_CAPACITY_INCREMENT / 2) ?
					MIN_CAPACITY_INCREMENT : currentCapacity >> 1)); // ~33% growth room
			System.arraycopy(a, 0, newArray, 0, index);
			System.arraycopy(a, index, newArray, index + newPartSize, s-index);
			array = a = newArray;
		}
		System.arraycopy(newPart, 0, a, index, newPartSize);
		size = newSize;
		modCount++;
		return true;
	}

	/**
	 * This method was extracted to encourage VM to inline callers.
	 * TODO: when we have a VM that can actually inline, move the test in here too!
	 */
	static void throwIndexOutOfBoundsException(int index, int size) {
		throw new IndexOutOfBoundsException("Invalid index " + index + ", size is " + size);
	}

	/**
	 * Removes all elements from this {@code TypedRawArrayList}, leaving it empty.
	 *
	 * @see #isEmpty
	 * @see #size
	 */
	@Override
	public void clear() {
		if (size != 0) {
			Arrays.fill(array, 0, size, null);
			size = 0;
			modCount++;
		}
	}

	/**
	 * Returns a new {@code TypedRawArrayList} with the same elements, the same size and
	 * the same capacity as this {@code TypedRawArrayList}.
	 *
	 * @return a shallow copy of this {@code TypedRawArrayList}
	 * @see java.lang.Cloneable
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Object clone() {
		try {
			final TypedRawArrayList<E> result = (TypedRawArrayList<E>)super.clone();
			result.array = array.clone();
			return result;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	/**
	 * Ensures that after this operation the {@code TypedRawArrayList} can hold the
	 * specified number of elements without further growing.
	 *
	 * @param minimumCapacity
	 *            the minimum capacity asked for.
	 */
	@SuppressWarnings("unchecked")
	public void ensureCapacity(int minimumCapacity) {
		if (array.length < minimumCapacity) {
			final E[] newArray = (E[])Array.newInstance(clazz, minimumCapacity);
			System.arraycopy(array, 0, newArray, 0, size);
			array = newArray;
			modCount++;
		}
	}

	@Override
	public E get(int index) {
		if (index >= size)
			throwIndexOutOfBoundsException(index, size);
		return array[index];
	}

	/**
	 * Returns the number of elements in this {@code TypedRawArrayList}.
	 *
	 * @return the number of elements in this {@code TypedRawArrayList}.
	 */
	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 * Searches this {@code TypedRawArrayList} for the specified object.
	 *
	 * @param object
	 *            the object to search for.
	 * @return {@code true} if {@code object} is an element of this
	 *         {@code TypedRawArrayList}, {@code false} otherwise
	 */
	@Override
	public boolean contains(Object object) {
		final E[] a = array;
		final int s = size;
		if (object != null) {
			for (int i = 0; i < s; i++) {
				if (object.equals(a[i]))
					return true;
			}
		} else {
			for (int i = 0; i < s; i++) {
				if (a[i] == null)
					return true;
			}
		}
		return false;
	}

	@Override
	public int indexOf(Object object) {
		final E[] a = array;
		final int s = size;
		if (object != null) {
			for (int i = 0; i < s; i++) {
				if (object.equals(a[i]))
					return i;
			}
		} else {
			for (int i = 0; i < s; i++) {
				if (a[i] == null)
					return i;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object object) {
		final E[] a = array;
		if (object != null) {
			for (int i = size - 1; i >= 0; i--) {
				if (object.equals(a[i]))
					return i;
			}
		} else {
			for (int i = size - 1; i >= 0; i--) {
				if (a[i] == null)
					return i;
			}
		}
		return -1;
	}

	/**
	 * Removes the object at the specified location from this list.
	 *
	 * @param index
	 *            the index of the object to remove.
	 * @return the removed object.
	 * @throws IndexOutOfBoundsException
	 *             when {@code location < 0 || location >= size()}
	 */
	@Override
	public E remove(int index) {
		final E[] a = array;
		int s = size;
		if (index >= s)
			throwIndexOutOfBoundsException(index, s);
		final E result = a[index];
		System.arraycopy(a, index + 1, a, index, --s - index);
		a[s] = null;  // Prevent memory leak
		size = s;
		modCount++;
		return result;
	}

	@Override
	public boolean remove(Object object) {
		final E[] a = array;
		int s = size;
		if (object != null) {
			for (int i = 0; i < s; i++) {
				if (object.equals(a[i])) {
					System.arraycopy(a, i + 1, a, i, --s - i);
					a[s] = null;  // Prevent memory leak
					size = s;
					modCount++;
					return true;
				}
			}
		} else {
			for (int i = 0; i < s; i++) {
				if (a[i] == null) {
					System.arraycopy(a, i + 1, a, i, --s - i);
					a[s] = null;  // Prevent memory leak
					size = s;
					modCount++;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void removeRange(int fromIndex, int toIndex) {
		if (fromIndex == toIndex)
			return;
		final E[] a = array;
		final int s = size;
		if (fromIndex >= s)
			throw new IndexOutOfBoundsException("fromIndex " + fromIndex + " >= size " + size);
		if (toIndex > s)
			throw new IndexOutOfBoundsException("toIndex " + toIndex + " > size " + size);
		if (fromIndex > toIndex)
			throw new IndexOutOfBoundsException("fromIndex " + fromIndex + " > toIndex " + toIndex);

		System.arraycopy(a, toIndex, a, fromIndex, s - toIndex);
		final int rangeSize = toIndex - fromIndex;
		Arrays.fill(a, s - rangeSize, s, null);
		size = s - rangeSize;
		modCount++;
	}

	/**
	 * Replaces the element at the specified location in this {@code TypedRawArrayList}
	 * with the specified object.
	 *
	 * @param index
	 *            the index at which to put the specified object.
	 * @param object
	 *            the object to add.
	 * @return the previous element at the index.
	 * @throws IndexOutOfBoundsException
	 *             when {@code location < 0 || location >= size()}
	 */
	@Override
	public E set(int index, E object) {
		final E[] a = array;
		if (index >= size)
			throwIndexOutOfBoundsException(index, size);
		E result = a[index];
		a[index] = object;
		return result;
	}

	/**
	 * Returns a new array containing all elements contained in this
	 * {@code TypedRawArrayList}.
	 *
	 * @return an array of the elements from this {@code TypedRawArrayList}
	 */
	@SuppressWarnings("unchecked")
	@NonNull
	@Override
	public E[] toArray() {
		final int s = size;
		E[] result = (E[])Array.newInstance(clazz, s);
		System.arraycopy(array, 0, result, 0, s);
		return result;
	}

	/**
	 * Returns an array containing all elements contained in this
	 * {@code TypedRawArrayList}. If the specified array is large enough to hold the
	 * elements, the specified array is used, otherwise an array of the same
	 * type is created. If the specified array is used and is larger than this
	 * {@code TypedRawArrayList}, the array element following the collection elements
	 * is set to null.
	 *
	 * @param contents
	 *            the array.
	 * @return an array of the elements from this {@code TypedRawArrayList}.
	 * @throws ArrayStoreException
	 *             when the type of an element in this {@code TypedRawArrayList} cannot
	 *             be stored in the type of the specified array.
	 */
	@SuppressWarnings({"unchecked", "SuspiciousSystemArraycopy"})
	@NonNull
	@Override
	public <T> T[] toArray(@NonNull T[] contents) {
		final int s = size;
		if (contents.length < s)
			contents = (T[])Array.newInstance(contents.getClass().getComponentType(), s);
		System.arraycopy(this.array, 0, contents, 0, s);
		if (contents.length > s)
			contents[s] = null;
		return contents;
	}

	/**
	 * Sets the capacity of this {@code TypedRawArrayList} to be the same as the current
	 * size.
	 *
	 * @see #size
	 */
	@SuppressWarnings("unchecked")
	public void trimToSize() {
		final int s = size;
		if (s == array.length)
			return;
		if (s == 0) {
			array = (E[])Array.newInstance(clazz, 0);
		} else {
			final E[] newArray = (E[])Array.newInstance(clazz, s);
			System.arraycopy(array, 0, newArray, 0, s);
			array = newArray;
		}
		modCount++;
	}

	@NonNull
	@Override
	public Iterator<E> iterator() {
		return new ArrayListIterator();
	}

	private class ArrayListIterator implements Iterator<E> {
		/** Number of elements remaining in this iteration */
		private int remaining = size;

		/** Index of element that remove() would remove, or -1 if no such elt */
		private int removalIndex = -1;

		/** The expected modCount value */
		private int expectedModCount = modCount;

		public boolean hasNext() {
			return remaining != 0;
		}

		public E next() {
			final TypedRawArrayList<E> ourList = TypedRawArrayList.this;
			final int rem = remaining;
			if (ourList.modCount != expectedModCount)
				throw new ConcurrentModificationException();
			if (rem == 0)
				throw new NoSuchElementException();
			remaining = rem - 1;
			return ourList.array[removalIndex = ourList.size - rem];
		}

		public void remove() {
			final E[] a = array;
			final int removalIdx = removalIndex;
			if (modCount != expectedModCount)
				throw new ConcurrentModificationException();
			if (removalIdx < 0)
				throw new IllegalStateException();
			System.arraycopy(a, removalIdx + 1, a, removalIdx, remaining);
			a[--size] = null;  // Prevent memory leak
			removalIndex = -1;
			expectedModCount = ++modCount;
		}
	}

	@Override
	public int hashCode() {
		final E[] a = array;
		int hashCode = 1;
		for (int i = 0, s = size; i < s; i++) {
			Object e = a[i];
			hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
		}
		return hashCode;
	}

	@SuppressWarnings("EqualsReplaceableByObjectsCall")
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof List))
			return false;
		final List<?> that = (List<?>) o;
		final int s = size;
		if (that.size() != s)
			return false;
		final E[] a = array;
		if (that instanceof RandomAccess) {
			for (int i = 0; i < s; i++) {
				final Object eThis = a[i];
				final Object ethat = that.get(i);
				if (eThis == null ? ethat != null : !eThis.equals(ethat))
					return false;
			}
		} else {  // Argument list is not random access; use its iterator
			final Iterator<?> it = that.iterator();
			for (int i = 0; i < s; i++) {
				final Object eThis = a[i];
				final Object eThat = it.next();
				if (eThis == null ? eThat != null : !eThis.equals(eThat))
					return false;
			}
		}
		return true;
	}

	private static final long serialVersionUID = 8683452581122892189L;

	private void writeObject(ObjectOutputStream stream) throws IOException {
		stream.defaultWriteObject();
		stream.writeInt(array.length);
		for (int i = 0; i < size; i++)
			stream.writeObject(array[i]);
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		int cap = stream.readInt();
		if (cap < size) {
			throw new InvalidObjectException(
				"Capacity: " + cap + " < size: " + size);
		}
		array = (cap == 0 ? (E[])Array.newInstance(clazz, 0) : (E[])Array.newInstance(clazz, cap));
		for (int i = 0; i < size; i++)
			array[i] = (E)stream.readObject();
	}
}
