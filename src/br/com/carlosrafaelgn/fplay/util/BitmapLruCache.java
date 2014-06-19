/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//
// The original code can be found here:
// http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.4.2_r1/android/util/LruCache.java
//

package br.com.carlosrafaelgn.fplay.util;

import java.util.LinkedHashMap;
import java.util.Map;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;

/**
 * A cache that holds strong references to a limited number of values. Each time
 * a value is accessed, it is moved to the head of a queue. When a value is
 * added to a full cache, the value at the end of that queue is evicted and may
 * become eligible for garbage collection.
 *
 * <p>If your cached values hold resources that need to be explicitly released,
 * override {@link #entryRemoved}.
 *
 * <p>If a cache miss should be computed on demand for the corresponding keys,
 * override {@link #create}. This simplifies the calling code, allowing it to
 * assume a value will always be returned, even when there's a cache miss.
 *
 * <p>By default, the cache size is measured in the number of entries. Override
 * {@link #sizeOf} to size the cache in different units. For example, this cache
 * is limited to 4MiB of bitmaps:
 * <pre>   {@code
 *   int cacheSize = 4 * 1024 * 1024; // 4MiB
 *   LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
 *	   protected int sizeOf(String key, Bitmap value) {
 *		   return value.getByteCount();
 *	   }
 *   }}</pre>
 *
 * <p>This class is thread-safe. Perform multiple cache operations atomically by
 * synchronizing on the cache: <pre>   {@code
 *   synchronized (cache) {
 *	 if (cache.get(key) == null) {
 *		 cache.put(key, value);
 *	 }
 *   }}</pre>
 *
 * <p>This class does not allow null to be used as a key or value. A return
 * value of null from {@link #get}, {@link #put} or {@link #remove} is
 * unambiguous: the key was not in the cache.
 *
 * <p>This class appeared in Android 3.1 (Honeycomb MR1); it's available as part
 * of <a href="http://developer.android.com/sdk/compatibility-library.html">Android's
 * Support Package</a> for earlier releases.
 */
public final class BitmapLruCache {
	private final LinkedHashMap<String, Bitmap> map;
	
	/** Size of this cache in units. Not necessarily the number of elements. */
	private int size;
	private final int maxSize;
	
	/**
	 * @param maxSize for caches that do not override {@link #sizeOf}, this is
	 *	 the maximum number of entries in the cache. For all other caches,
	 *	 this is the maximum sum of the sizes of the entries in this cache.
	 */
	public BitmapLruCache(int maxSize) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("maxSize <= 0");
		}
		this.maxSize = maxSize;
		this.map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
	}
	
	/**
	 * Returns the value for {@code key} if it exists in the cache or can be
	 * created by {@code #create}. If a value was returned, it is moved to the
	 * head of the queue. This returns null if a value is not cached and cannot
	 * be created.
	 */
	public Bitmap get(String key) {
		if (key == null) {
			throw new NullPointerException("key == null");
		}
		
		Bitmap mapValue;
		synchronized (this) {
			mapValue = map.get(key);
			return mapValue;
		}
	}
	
	/**
	 * Caches {@code value} for {@code key}. The value is moved to the head of
	 * the queue.
	 *
	 * @return the previous value mapped by {@code key}.
	 */
	public Bitmap put(String key, Bitmap value) {
		if (key == null || value == null) {
			throw new NullPointerException("key == null || value == null");
		}
		
		Bitmap previous;
		synchronized (this) {
			size += sizeOf(value);
			previous = map.put(key, value);
			if (previous != null) {
				size -= sizeOf(previous);
			}
		}
		
		if (previous != null && previous != value)
			previous.recycle();
		
		trimToSize(maxSize);
		return previous;
	}
	
	/**
	 * Remove the eldest entries until the total of remaining entries is at or
	 * below the requested size.
	 *
	 * @param maxSize the maximum size of the cache before returning. May be -1
	 *			to evict even 0-sized elements.
	 */
	public void trimToSize(int maxSize) {
		while (true) {
			String key;
			Bitmap value;
			synchronized (this) {
				if (size < 0 || (map.isEmpty() && size != 0)) {
					throw new IllegalStateException(getClass().getName()
							+ ".sizeOf() is reporting inconsistent results!");
				}
				
				if (size <= maxSize) {
					break;
				}
				
				//Map.Entry<String, Bitmap> toEvict = map.eldest();
				
				//According to this source file: http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b27/java/util/LinkedHashMap.java
				//the eldest entry is header.after
				//therefore, in order not to have to import all the classes needed by LinkedHashMap
				//just to create a new method (eldest()), we will believe all LinkedHashMap
				//implementations out there actually follow the proposed source code.
				//If they do so, then the eldest entry (header.after) is the first entry returned by
				//LinkedHashIterator.nextEntry() (which, in turn, is the value returned by EntryIterator.next())
				
				Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
				
				if (toEvict == null) {
					break;
				}
				
				key = toEvict.getKey();
				value = toEvict.getValue();
				map.remove(key);
				if (value != null)
					size -= sizeOf(value);
			}
			
			if (value != null)
				value.recycle();
		}
	}
	
	/**
	 * Removes the entry for {@code key} if it exists.
	 *
	 * @return the previous value mapped by {@code key}.
	 */
	public Bitmap remove(String key) {
		if (key == null) {
			throw new NullPointerException("key == null");
		}

		Bitmap previous;
		synchronized (this) {
			previous = map.remove(key);
			if (previous != null) {
				size -= sizeOf(previous);
			}
		}

		if (previous != null)
			previous.recycle();

		return previous;
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	private int sizeOf19(Bitmap value) {
		return value.getAllocationByteCount();
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	private int sizeOf12(Bitmap value) {
		return value.getByteCount();
	}
	
	/**
	 * Returns the size of the entry for {@code key} and {@code value} in
	 * user-defined units.  The default implementation returns 1 so that size
	 * is the number of entries and max size is the maximum number of entries.
	 *
	 * <p>An entry's size must not change while it is in the cache.
	 */
	public int sizeOf(Bitmap value) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			return sizeOf19(value);
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1)
			return sizeOf12(value);
		else
			return value.getRowBytes() * value.getHeight();
	}
	
	/**
	 * Clear the cache, calling {@link #entryRemoved} on each removed entry.
	 */
	public void evictAll() {
		trimToSize(-1); // -1 will evict 0-sized elements
	}
}
