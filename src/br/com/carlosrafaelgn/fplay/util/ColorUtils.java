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
	public static class HSV {
		public double h, s, v;
		
		public HSV() {
		}
		
		public HSV(double h, double s, double v) {
			this.h = h;
			this.s = s;
			this.v = v;
		}
		
		public void fromRGB(int rgb) {
			final double r = (double)((rgb >>> 16) & 0xff) / 255.0,
			g = (double)((rgb >>> 8) & 0xff) / 255.0,
			b = (double)(rgb & 0xff) / 255.0,
			max = Math.max(Math.max(r, g), b),
			min = Math.min(Math.min(r, g), b);
			h = 0;
			s = ((max == 0) ? 0 : ((max - min) / max));
			v = max;
			if (max != min) {
				if (max == r) {
					if (g >= b)
						h = (60.0 * ((g - b) / (max - min))) / 360.0;
					else
						h = ((60.0 * ((g - b) / (max - min))) + 360.0) / 360.0;
				} else if (max == g) {
					h = ((60.0 * ((b - r) / (max - min))) + 120.0) / 360.0;
				} else {
					h = ((60.0 * ((r - g) / (max - min))) + 240.0) / 360.0;
				}
			}
		}
		
		public int toRGB() {
			double h = 6.0 * this.h;
			final double v = this.v * 255.0;
			int hi, r, g, b, vi;
			if (h > 5.99) h = 0;
			hi = (int)h % 6;
			h -= (double)hi;
			r = (int)(v * (1.0 - s));
			g = (int)(v * (1.0 - (h * s)));
			b = (int)(v * (1.0 - ((1.0 - h) * s)));
			vi = (int)v;
			switch (hi) {
				case 1:
					b = r;
					r = g;
					g = vi;
					break;
				case 2:
					g = vi;
					break;
				case 3:
					b = vi;
					break;
				case 4:
					g = r;
					r = b;
					b = vi;
					break;
				case 5:
					b = g;
					g = r;
					r = vi;
					break;
				default:
					g = b;
					b = r;
					r = vi;
					break;
			}
			return 0xff000000 | (((r >= 255) ? 255 : r) << 16) | (((g >= 255) ? 255 : g) << 8) | ((b >= 255) ? 255 : b);
		}
	}
	
	public static int blend(int rgb1, int rgb2, float perc1) {
		int r1 = (rgb1 >>> 16) & 0xff;
		final int r2 = (rgb2 >>> 16) & 0xff;
		int g1 = (rgb1 >>> 8) & 0xff;
		final int g2 = (rgb2 >>> 8) & 0xff;
		int b1 = rgb1 & 0xff;
		final int b2 = rgb2 & 0xff;
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
		return 0xff000000 | (r1 << 16) | (g1 << 8) | b1;
	}
	
	public static double relativeLuminance(int rgb) {
		//http://www.w3.org/TR/2007/WD-WCAG20-TECHS-20070517/Overview.html#G18
		double RsRGB = (double)((rgb >>> 16) & 0xff) / 255.0,
		GsRGB = (double)((rgb >>> 8) & 0xff) / 255.0,
		BsRGB = (double)(rgb & 0xff) / 255.0,
		R, G, B;
		if (RsRGB <= 0.03928) R = RsRGB / 12.92; else R = Math.pow((RsRGB + 0.055) / 1.055, 2.4);
		if (GsRGB <= 0.03928) G = GsRGB / 12.92; else G = Math.pow((GsRGB + 0.055) / 1.055, 2.4);
		if (BsRGB <= 0.03928) B = BsRGB / 12.92; else B = Math.pow((BsRGB + 0.055) / 1.055, 2.4);
		return (0.2126 * R) + (0.7152 * G) + (0.0722 * B);
	}
	
	public static double contrastRatioL(double luminance1, double luminance2) {
		//http://www.w3.org/TR/2007/WD-WCAG20-TECHS-20070517/Overview.html#G18
		return (luminance1 >= luminance2) ? ((luminance1 + 0.05) / (luminance2 + 0.05)) : ((luminance2 + 0.05) / (luminance1 + 0.05));
	}
	
	public static double contrastRatio(int rgb1, int rgb2) {
		//http://www.w3.org/TR/2007/WD-WCAG20-TECHS-20070517/Overview.html#G18
		return ColorUtils.contrastRatioL(ColorUtils.relativeLuminance(rgb1), ColorUtils.relativeLuminance(rgb2));
	}
	
	public static int parseHexColor(String color) {
		try {
			if (color.charAt(0) == '#')
				color = color.substring(1);
			return 0xff000000 | (0x00ffffff & Integer.parseInt(color, 16));
		} catch (Throwable ex) {
			return 0;
		}
	}
	
	public static String toHexColor(int rgb) {
		final String s = "00000" + Integer.toHexString(rgb);
		return "#" + s.substring(s.length() - 6);
	}
}
