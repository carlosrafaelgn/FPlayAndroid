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
package br.com.carlosrafaelgn.fplay.util;

public final class ArraySorter {
	public interface Comparer<E> {
		int compare(E a, E b);
	}
	
	public static <E> void sort(E[] elements, int i, int n, Comparer<E> comparer) {
		if (n > 1) {
			E eA, eB;
			final int endB = i + n;
			if (n < 8) {
				//use insertion sort only for small arrays
				for (int x = i; x < endB; x++) {
					int j = x;
					while (j > i && comparer.compare(eA = elements[j - 1], eB = elements[j]) > 0) {
						elements[j - 1] = eB;
						elements[j--] = eA;
					}
				}
				return;
			}
			final int m = n >> 1;
			sort(elements, i, m, comparer);
			sort(elements, i + m, n - m, comparer);
			int iB = i + m;
			eA = elements[i];
			eB = elements[iB];
			for (;;) {
				if (comparer.compare(eA, eB) > 0) {
					System.arraycopy(elements, i, elements, i + 1, iB - i);
					elements[i] = eB;
					if ((++iB) >= endB || (++i) >= iB) break; //MUST INCREMENT iB BEFORE COMPARING i AND iB
					eB = elements[iB];
				} else {
					if ((++i) >= iB) break;
					eA = elements[i];
				}
			}
		}
	}
}
