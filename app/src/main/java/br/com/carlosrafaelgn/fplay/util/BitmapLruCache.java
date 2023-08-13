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
// The original code can be found here:
// http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.4.2_r1/android/util/LruCache.java
//

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

package br.com.carlosrafaelgn.fplay.util;

import java.util.LinkedHashMap;
import java.util.Map;

// This class is not thread safe and its uses must be synchronized
@SuppressWarnings("NonAtomicOperationOnVolatileField")
public final class BitmapLruCache {
	private volatile int size;
	private final int maxSize;
	private final LinkedHashMap<Long, ReleasableBitmapWrapper> map;
	
	public BitmapLruCache(int maxSize) {
		this.maxSize = maxSize;
		this.map = new LinkedHashMap<>(0, 0.75f, true);
	}
	
	public ReleasableBitmapWrapper get(Long key) {
		return map.get(key);
	}
	
	public ReleasableBitmapWrapper put(Long key, ReleasableBitmapWrapper value) {
		ReleasableBitmapWrapper previous;
		
		size += value.size;
		
		previous = map.put(key, value);
		if (previous != null) {
			size -= previous.size;
			if (previous != value)
				previous.release();
		}
		
		trimToSize(maxSize);
		
		return previous;
	}
	
	public void trimToSize(int maxSize) {
		while (size > maxSize && size > 0 && !map.isEmpty()) {
			//Map.Entry<Long, Bitmap> toEvict = map.eldest();
			
			//According to this source file: http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b27/java/util/LinkedHashMap.java
			//the eldest entry is header.after
			//therefore, in order not to have to import all the classes needed by LinkedHashMap
			//just to create a new method (eldest()), we will believe all LinkedHashMap
			//implementations out there actually follow the proposed source code.
			//If they do so, then the eldest entry (header.after) is the first entry returned by
			//LinkedHashIterator.nextEntry() (which, in turn, is the value returned by EntryIterator.next())
			
			Map.Entry<Long, ReleasableBitmapWrapper> toEvict = map.entrySet().iterator().next();
			
			if (toEvict == null)
				break;
			
			final Long key = toEvict.getKey();
			final ReleasableBitmapWrapper value = toEvict.getValue();
			map.remove(key);
			if (value != null) {
				size -= value.size;
				value.release();
			}
		}
	}
	
	public ReleasableBitmapWrapper remove(Long key) {
		ReleasableBitmapWrapper previous;
		previous = map.remove(key);
		if (previous != null) {
			size -= previous.size;
			previous.release();
		}
		return previous;
	}
	
	public void evictAll() {
		trimToSize(-1); // -1 will evict 0-sized elements
	}
}
