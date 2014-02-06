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
package br.com.carlosrafaelgn.fplay.util;

public final class ColorUtils {
	public static int blend(int rgb1, int rgb2, float perc1) {
		int r1 = (rgb1 >>> 16) & 0xFF;
		final int r2 = (rgb2 >>> 16) & 0xFF;
		int g1 = (rgb1 >>> 8) & 0xFF;
		final int g2 = (rgb2 >>> 8) & 0xFF;
		int b1 = rgb1 & 0xFF;
		final int b2 = rgb2 & 0xFF;
		final float perc2 = 1.0f - perc1;
		r1 = (int)(((float)r1 * perc1) + ((float)r2 * perc2));
		g1 = (int)(((float)g1 * perc1) + ((float)g2 * perc2));
		b1 = (int)(((float)b1 * perc1) + ((float)b2 * perc2));
		if (r1 > 255)
			r1 = 255;
		else if (r1 < 0)
			r1 = 0;
		if (g1 > 255)
			g1 = 255;
		else if (g1 < 0)
			g1 = 0;
		if (b1 > 255)
			b1 = 255;
		else if (b1 < 0)
			b1 = 0;
		return 0xFF000000 | (r1 << 16) | (g1 << 8) | b1;
	}
}
