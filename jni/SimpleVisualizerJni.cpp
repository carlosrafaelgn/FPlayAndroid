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
#include <jni.h>
#include <stdio.h>
#ifdef __ARM_NEON__
#include <errno.h>
#include <fcntl.h>
#include <machine/cpu-features.h>
#include <arm_neon.h>
#endif
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <string.h>
#include <math.h>


//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html


#define DEFSPEED (0.140625f / 16.0f)

//for the alignment:
//https://gcc.gnu.org/onlinedocs/gcc-3.2/gcc/Variable-Attributes.html

//to make the math easier COLORS has 257 int's (from 0 to 256) for each different color
static const unsigned short COLORS[] __attribute__((aligned(16))) = { 	/*Blue */ 0x0000, 0x0816, 0x0816, 0x0815, 0x0815, 0x0815, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1815, 0x1814, 0x1814, 0x1814, 0x1814, 0x2014, 0x2014, 0x2014, 0x2013, 0x2013, 0x2813, 0x2813, 0x2813, 0x2813, 0x3012, 0x3012, 0x3012, 0x3012, 0x3812, 0x3811, 0x3811, 0x3811, 0x4011, 0x4011, 0x4011, 0x4010, 0x4810, 0x4810, 0x4810, 0x4810, 0x500f, 0x500f, 0x500f, 0x500f, 0x580f, 0x580e, 0x580e, 0x600e, 0x600e, 0x600d, 0x680d, 0x680d, 0x680d, 0x680d, 0x700c, 0x700c, 0x700c, 0x780c, 0x780c, 0x780b, 0x800b, 0x800b, 0x800b, 0x800a, 0x880a, 0x880a, 0x880a, 0x900a, 0x9009, 0x9009, 0x9009, 0x9809, 0x9809, 0x9808, 0xa008, 0xa008, 0xa008, 0xa807, 0xa807, 0xa807, 0xa807, 0xb007, 0xb006, 0xb006, 0xb806, 0xb806, 0xb806, 0xb805, 0xc005, 0xc005, 0xc005, 0xc005, 0xc804, 0xc804, 0xc804, 0xc804, 0xd004, 0xd004, 0xd003, 0xd003, 0xd803, 0xd803, 0xd803, 0xd802, 0xe002, 0xe002, 0xe002, 0xe002, 0xe002, 0xe802, 0xe801, 0xe801, 0xe801, 0xe801, 0xf001, 0xf001, 0xf001, 0xf000, 0xf000, 0xf000, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf820, 0xf820, 0xf820, 0xf840, 0xf840, 0xf840, 0xf860, 0xf860, 0xf860, 0xf880, 0xf880, 0xf8a0, 0xf8a0, 0xf8a0, 0xf8c0, 0xf8c0, 0xf8e0, 0xf8e0, 0xf900, 0xf900, 0xf900, 0xf920, 0xf920, 0xf940, 0xf940, 0xf960, 0xf960, 0xf980, 0xf9a0, 0xf9a0, 0xf9a0, 0xf9c0, 0xf9e0, 0xf9e0, 0xfa00, 0xfa00, 0xfa20, 0xfa20, 0xfa40, 0xfa40, 0xfa60, 0xfa80, 0xfa80, 0xfaa0, 0xfaa0, 0xfac0, 0xfae0, 0xfae0, 0xfb00, 0xfb00, 0xfb20, 0xfb40, 0xfb40, 0xfb60, 0xfb60, 0xfb80, 0xfba0, 0xfba0, 0xfbc0, 0xfbe0, 0xfbe0, 0xfc00, 0xfc00, 0xfc20, 0xfc20, 0xfc40, 0xfc60, 0xfc60, 0xfc80, 0xfca0, 0xfca0, 0xfcc0, 0xfcc0, 0xfce0, 0xfd00, 0xfd00, 0xfd20, 0xfd20, 0xfd40, 0xfd60, 0xfd60, 0xfd80, 0xfd80, 0xfda0, 0xfda0, 0xfdc0, 0xfde0, 0xfde0, 0xfe00, 0xfe00, 0xfe20, 0xfe20, 0xfe40, 0xfe40, 0xfe60, 0xfe60, 0xfe80, 0xfe80, 0xfea0, 0xfea0, 0xfec0, 0xfec0, 0xfee0, 0xfee0, 0xff00, 0xff00, 0xff20, 0xff20, 0xff20, 0xff40, 0xff40, 0xff40, 0xff60, 0xff60, 0xff80, 0xff80, 0xff80, 0xffa0, 0xffa0, 0xffa0, 0xffc0, 0xffc0, 0xffc0, 0xffc0, 0xffc0,
																		/*Green*/ 0x0000, 0x0000, 0x0000, 0x0020, 0x0020, 0x0040, 0x0040, 0x0060, 0x0060, 0x0080, 0x00a0, 0x00a0, 0x00c0, 0x00e0, 0x00e0, 0x0100, 0x0120, 0x0140, 0x0160, 0x0160, 0x0180, 0x01a0, 0x01c0, 0x01e0, 0x01e0, 0x0200, 0x0220, 0x0240, 0x0260, 0x0280, 0x0280, 0x02a0, 0x02c0, 0x02e0, 0x0300, 0x0320, 0x0340, 0x0360, 0x0360, 0x0380, 0x03a0, 0x03c0, 0x03e0, 0x0400, 0x0400, 0x0420, 0x0440, 0x0440, 0x0460, 0x0480, 0x0480, 0x04a0, 0x04c0, 0x04c0, 0x04c0, 0x04e0, 0x04e0, 0x0ce0, 0x0d00, 0x0d00, 0x0d00, 0x1520, 0x1520, 0x1540, 0x1540, 0x1d40, 0x1d60, 0x1d60, 0x2580, 0x2580, 0x25a0, 0x2da0, 0x2da0, 0x2dc0, 0x35c0, 0x35e0, 0x35e0, 0x3e00, 0x3e00, 0x3e00, 0x4620, 0x4620, 0x4e40, 0x4e40, 0x4e60, 0x5660, 0x5660, 0x5e80, 0x5e80, 0x5ea0, 0x66a0, 0x66c0, 0x66c0, 0x6ec0, 0x6ee0, 0x76e0, 0x7700, 0x7f00, 0x7f00, 0x7f20, 0x8720, 0x8720, 0x8f40, 0x8f40, 0x8f40, 0x9760, 0x9760, 0x9f80, 0x9f80, 0x9f80, 0xa780, 0xa7a0, 0xafa0, 0xafa0, 0xafc0, 0xb7c0, 0xb7c0, 0xbfc0, 0xbfc0, 0xbfe0, 0xc7e0, 0xc7e0, 0xc7e0, 0xcfe0, 0xcfe0, 0xcfe0, 0xd7e0, 0xd7e0, 0xdfe0, 0xdfe0, 0xdfe0, 0xdfe0, 0xe7e0, 0xe7e0, 0xe7e0, 0xefe0, 0xefe0, 0xefe0, 0xefe0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffc0, 0xffc0, 0xffc0, 0xffa0, 0xffa0, 0xffa0, 0xff80, 0xff80, 0xff80, 0xff60, 0xff60, 0xff40, 0xff40, 0xff40, 0xff20, 0xff00, 0xff00, 0xfee0, 0xfee0, 0xfec0, 0xfec0, 0xfea0, 0xfea0, 0xfe80, 0xfe60, 0xfe60, 0xfe40, 0xfe20, 0xfe20, 0xfe00, 0xfe00, 0xfde0, 0xfdc0, 0xfda0, 0xfda0, 0xfd80, 0xfd60, 0xfd60, 0xfd40, 0xfd20, 0xfd00, 0xfd00, 0xfce0, 0xfcc0, 0xfca0, 0xfca0, 0xfc80, 0xfc60, 0xfc40, 0xfc40, 0xfc20, 0xfc00, 0xfbe0, 0xfbe0, 0xfbc0, 0xfba0, 0xfb80, 0xfb60, 0xfb60, 0xfb40, 0xfb20, 0xfb00, 0xfb00, 0xfae0, 0xfac0, 0xfaa0, 0xfaa0, 0xfa80, 0xfa60, 0xfa60, 0xfa40, 0xfa20, 0xfa00, 0xfa00, 0xf9e0, 0xf9c0, 0xf9c0, 0xf9a0, 0xf980, 0xf980, 0xf960, 0xf960, 0xf940, 0xf920, 0xf920, 0xf900, 0xf8e0, 0xf8e0, 0xf8c0, 0xf8c0, 0xf8a0, 0xf8a0, 0xf880, 0xf880, 0xf860, 0xf860, 0xf860, 0xf840, 0xf840, 0xf820, 0xf820, 0xf820, 0xf800, 0xf800, 0xf800, 0xf800 };
static float floatBuffer[(256 * 2) + (256 / 4)] __attribute__((aligned(16)));
static float invBarW;
static int barW, barH, barBins, barWidthInPixels, recreateVoice, colorIndex, lerp;
static unsigned short bgColor;
static unsigned short* voice, *alignedVoice;
#ifdef __ARM_NEON__
static unsigned int neonMode;
#endif

void JNICALL setLerpAndColorIndex(JNIEnv* env, jclass clazz, jboolean jlerp, int jcolorIndex) {
	lerp = (jlerp ? 1 : 0);
	colorIndex = jcolorIndex;
}

void JNICALL updateMultiplier(JNIEnv* env, jclass clazz, jboolean isVoice) {
	float* const fft = floatBuffer;
	float* const multiplier = fft + 256;
	if (isVoice) {
		for (int i = 0; i < 256; i++) {
			fft[i] = 0;
			multiplier[i] = 2.0f * expf((float)i / 128.0f);
		}
	} else {
		for (int i = 0; i < 256; i++) {
			fft[i] = 0;
			multiplier[i] = 256.0f * expf((float)i / 128.0f);
			//multiplier[i] = 1;//(((float)i + 100.0f) / 101.0f) * expf((float)i / 300.0f);
		}
	}
}

#include "OpenGLVisualizerJni.h"

void JNICALL init(JNIEnv* env, jclass clazz, int jbgColor) {
	voice = 0;
	recreateVoice = 0;
#ifdef __ARM_NEON__
	neonMode = 0;
#endif
	const unsigned int r = ((jbgColor >> 16) & 0xff) >> 3;
	const unsigned int g = ((jbgColor >> 8) & 0xff) >> 2;
	const unsigned int b = (jbgColor & 0xff) >> 3;
	bgColor = (unsigned short)((r << 11) | (g << 5) | b);
	updateMultiplier(env, clazz, 0);
}

int JNICALL checkNeonMode(JNIEnv* env, jclass clazz) {
#ifdef __ARM_NEON__
	//based on
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.c?r=2195
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.h?r=2195
	neonMode = 0;
	char cpuinfo[4096];
	int cpuinfo_len = -1;
	int fd = open("/proc/cpuinfo", O_RDONLY);
	if (fd >= 0) {
		do {
			cpuinfo_len = read(fd, cpuinfo, 4096);
		} while (cpuinfo_len < 0 && errno == EINTR);
		close(fd);
		if (cpuinfo_len > 0) {
			cpuinfo[cpuinfo_len] = 0;
			//look for the "\nFeatures: " line
			for (int i = cpuinfo_len - 9; i >= 0; i--) {
				if (memcmp(cpuinfo + i, "\nFeatures", 9) == 0) {
					i += 9;
					while (i < cpuinfo_len && (cpuinfo[i] == ' ' || cpuinfo[i] == '\t' || cpuinfo[i] == ':'))
						i++;
					cpuinfo_len -= 5;
					//now look for the " neon" feature
					while (i <= cpuinfo_len && cpuinfo[i] != '\n') {
						if (memcmp(cpuinfo + i, " neon", 5) == 0 ||
							memcmp(cpuinfo + i, "\tneon", 5) == 0) {
							neonMode = 1;
							break;
						}
						i++;
					}
					break;
				}
			}
			// __android_log_print(ANDROID_LOG_INFO, "JNI", "Neon mode: %d", neonMode);
		}
	}
	return neonMode;
#else
	return 0;
#endif
}

void JNICALL terminate(JNIEnv* env, jclass clazz) {
	if (voice) {
		free(voice);
		voice = 0;
	}
#ifdef __ARM_NEON__
	neonMode = 0;
#endif
}

int JNICALL prepareSurface(JNIEnv* env, jclass clazz, jobject surface) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return -1;
	int ret = -2;
	int w = ANativeWindow_getWidth(wnd), h = ANativeWindow_getHeight(wnd);
	if (w > 0 && h > 0) {
		barW = w >> 8;
		barH = h & (~1); //make the height always an even number
		if (barW < 1)
			barW = 1;
		const int size = barW << 8;
		invBarW = 1.0f / (float)barW;
		barBins = ((size > w) ? ((w < 256) ? (w & ~7) : 256) : 256);
		barWidthInPixels = barBins * barW;
		recreateVoice = 1;
		ret = ANativeWindow_setBuffersGeometry(wnd, barWidthInPixels, barH, WINDOW_FORMAT_RGB_565);
	}
	if (ret < 0) {
		invBarW = 1;
		barBins = 0;
		barWidthInPixels = 0;
		recreateVoice = 0;
	}
	ANativeWindow_release(wnd);
	return ret;
}

#ifdef __ARM_NEON__
void JNICALL processSimple(JNIEnv* env, jclass clazz, jbyteArray jbfft, int deltaMillis, jobject surface) {
#else
void JNICALL process(JNIEnv* env, jclass clazz, jbyteArray jbfft, int deltaMillis, jobject surface) {
#endif
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return;
	ANativeWindow_Buffer inf;
	if (ANativeWindow_lock(wnd, &inf, 0) < 0) {
		ANativeWindow_release(wnd);
		return;
	}
	if (inf.width != barWidthInPixels ||
		inf.height != barH) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to unsigned short
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetPrimitiveArrayCritical(jbfft, 0);
	if (!bfft) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	const float coefNew = DEFSPEED * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = bfft[0];
	float previous = 0;
	for (int i = 0; i < barBins; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im))));
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
		
		if (barW == 1 || !lerp) {
			//v goes from 0 to 32768 (inclusive)
			int v = (int)m;
			if (v < 0)
				v = 0;
			else if (v > 32768)
				v = 32768;
			
			const unsigned short color = COLORS[colorIndex + (v >> 7)];
			v = ((v * barH) >> 15);
			int v2 = v;
			v = (barH >> 1) - (v >> 1);
			v2 += v;
			unsigned short* currentBar = (unsigned short*)inf.bits;
			inf.bits = (void*)((unsigned short*)inf.bits + barW);
			
			int y = 0;
			switch (barW) {
			case 1:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			case 2:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			case 3:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar[2] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			case 4:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar[3] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar[2] = color;
					currentBar[3] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar[3] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			default:
				for (; y < v; y++) {
					for (int b = barW - 1; b >= 0; b--)
						currentBar[b] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					for (int b = barW - 1; b >= 0; b--)
						currentBar[b] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					for (int b = barW - 1; b >= 0; b--)
						currentBar[b] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			}
		} else {
			const float delta = (int)(m - previous) * invBarW;
			for (int i = 0; i < barW; i++) {
				previous += delta;
				/*if (previous < 0.0f)
					previous = 0.0f;
				else if (previous > 32768.0f)
					previous = 32768.0f;*/
				
				//v goes from 0 to 32768 (inclusive)
				int v = (int)previous;
				if (v < 0)
					v = 0;
				else if (v > 32768)
					v = 32768;
				
				const unsigned short color = COLORS[colorIndex + (v >> 7)];
				v = ((v * barH) >> 15);
				int v2 = v;
				v = (barH >> 1) - (v >> 1);
				v2 += v;
				unsigned short* currentBar = (unsigned short*)inf.bits;
				inf.bits = (void*)((unsigned short*)inf.bits + 1);
				
				int y = 0;
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
			}
		}
	}
	env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
	ANativeWindow_unlockAndPost(wnd);
	ANativeWindow_release(wnd);
}

#ifdef __ARM_NEON__
static const int __0[] __attribute__((aligned(16))) = { 0, 0, 0, 0 };
static const int __32768[] __attribute__((aligned(16))) = { 32768, 32768, 32768, 32768 };
static int __tmp[4] __attribute__((aligned(16)));
static int __v[4] __attribute__((aligned(16)));
static int __v2[4] __attribute__((aligned(16)));
void JNICALL processNeon(JNIEnv* env, jclass clazz, jbyteArray jbfft, int deltaMillis, jobject surface) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return;
	ANativeWindow_Buffer inf;
	if (ANativeWindow_lock(wnd, &inf, 0) < 0) {
		ANativeWindow_release(wnd);
		return;
	}
	if (inf.width != barWidthInPixels ||
		inf.height != barH) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to unsigned short
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetPrimitiveArrayCritical(jbfft, 0);
	if (!bfft) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	
	const float coefNew = DEFSPEED * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = bfft[0];
	
	//step 1: compute all magnitudes
	for (int i = barBins - 1; i >= 0; i--) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im))));
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
	}
	
	int32x4_t _0 = vld1q_s32(__0), _32768 = vld1q_s32(__32768), _barH = { barH, barH, barH, barH }, _barH2 = vshrq_n_s32(_barH, 1), _colorIndex = { colorIndex, colorIndex, colorIndex, colorIndex };
	if (barW == 1 || !lerp) {
		for (int i = 0; i < barBins; i += 4) {
			//_v goes from 0 to 32768 (inclusive)
			int32x4_t _v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(vld1q_f32(fft + i))));
			vst1q_s32(__tmp, vaddq_s32(vshrq_n_s32(_v, 7), _colorIndex));
			_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
			int32x4_t _v2 = _v;
			_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
			vst1q_s32(__v, _v);
			vst1q_s32(__v2, vaddq_s32(_v2, _v));
			for (int j = 0; j < 4; j++) {
				const unsigned short color = COLORS[__tmp[j]];
				const int v = __v[j];
				const int v2 = __v2[j];
				unsigned short* currentBar = (unsigned short*)inf.bits;
				inf.bits = (void*)((unsigned short*)inf.bits + barW);
				int y = 0;
				switch (barW) {
				case 1:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 2:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 3:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar[2] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 4:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar[3] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar[2] = color;
						currentBar[3] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar[3] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				default:
					for (; y < v; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				}
			}
		}
	} else {
		float32x4_t _invBarW = { invBarW, invBarW, invBarW, invBarW }, _prev = { 0.0f, fft[0], fft[1], fft[2] };
		int originalBarIndex = 0;
		for (int i = 0; i < barBins; i += 4) {
			//process the four actual bars
			int barIndex = originalBarIndex;
			//_v goes from 0 to 32768 (inclusive)
			int32x4_t _v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(_prev)));
			vst1q_s32(__tmp, vaddq_s32(vshrq_n_s32(_v, 7), _colorIndex));
			_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
			int32x4_t _v2 = _v;
			_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
			vst1q_s32(__v, _v);
			vst1q_s32(__v2, vaddq_s32(_v2, _v));
			for (int j = 0; j < 4; j++) {
				//v goes from 0 to 32768 (inclusive)
				const unsigned short color = COLORS[__tmp[j]];
				const int v = __v[j];
				const int v2 = __v2[j];
				unsigned short* currentBar = (unsigned short*)inf.bits + barIndex;
				int y = 0;
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				barIndex += barW;
			}
			
			//now, process all the interpolated bars (the ones between the actual bars)
			float32x4_t _delta = vmulq_f32(vsubq_f32(vld1q_f32(fft + i), _prev), _invBarW);
			for (int b = 1; b < barW; b++) {
				//move to the next bar
				barIndex = originalBarIndex + b;
				_prev = vaddq_f32(_prev, _delta);
				//_v goes from 0 to 32768 (inclusive)
				_v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(_prev)));
				vst1q_s32(__tmp, vaddq_s32(vshrq_n_s32(_v, 7), _colorIndex));
				_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
				int32x4_t _v2 = _v;
				_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
				vst1q_s32(__v, _v);
				vst1q_s32(__v2, vaddq_s32(_v2, _v));
				for (int j = 0; j < 4; j++) {
					const unsigned short color = COLORS[__tmp[j]];
					const int v = __v[j];
					const int v2 = __v2[j];
					unsigned short* currentBar = (unsigned short*)inf.bits + barIndex;
					int y = 0;
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					barIndex += barW;
				}
			}
			originalBarIndex += (barW << 2);
			//it is ok to load data beyond index 255, as after the first fft's 256
			//elements there are the 256 multipliers ;)
			_prev = vld1q_f32(fft + (i + 3));
		}
	}
	env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
	ANativeWindow_unlockAndPost(wnd);
	ANativeWindow_release(wnd);
}

void JNICALL process(JNIEnv* env, jclass clazz, jbyteArray jbfft, int deltaMillis, jobject surface) {
	if (neonMode)
		processNeon(env, clazz, jbfft, deltaMillis, surface);
	else
		processSimple(env, clazz, jbfft, deltaMillis, surface);
}
#endif

void JNICALL processVoice(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return;
	ANativeWindow_Buffer inf;
	if (ANativeWindow_lock(wnd, &inf, 0) < 0) {
		ANativeWindow_release(wnd);
		return;
	}
	if (inf.width != barWidthInPixels ||
		inf.height != barH) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	if (recreateVoice) {
		if (voice)
			free(voice);
		voice = (unsigned short*)malloc(((inf.stride * inf.height) << 1) + 16);
		unsigned char* al = (unsigned char*)voice;
		while (((unsigned int)al) & 15)
			al++;
		alignedVoice = (unsigned short*)al;
		recreateVoice = 0;
	}
	if (!voice) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to unsigned short
	
	int v = inf.stride * (inf.height - 1);
	memcpy(alignedVoice, (unsigned char*)alignedVoice + inf.stride, v);
	unsigned short* currentBar = (unsigned short*)((unsigned char*)alignedVoice + v);
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetPrimitiveArrayCritical(jbfft, 0);
	if (!bfft) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = bfft[0];
	float previous = 0;
	
	for (int i = 0; i < barBins; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		const float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im))));
		if (barW == 1) {
			//v goes from 0 to 32768 (inclusive)
			const int v = (int)m;
			*currentBar = COLORS[colorIndex + ((v <= 0) ? 0 : ((v >= 256) ? 256 : v))];
			currentBar++;
		} else {
			const float delta = (m - previous) * invBarW;
			for (int i = 0; i < barW; i++) {
				previous += delta;
				const int v = (int)m;
				/*if (v < 0) {
					v = 0;
					//previous = 0.0f;
				} else if (v > 256) {
					v = 256;
					//previous = 256.0f;
				}*/
				//*currentBar = COLORS[v];
				*currentBar = COLORS[colorIndex + ((v <= 0) ? 0 : ((v >= 256) ? 256 : v))];
				currentBar++;
			}
		}
	}
	env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
	memcpy(inf.bits, alignedVoice, inf.stride * inf.height);
	ANativeWindow_unlockAndPost(wnd);
	ANativeWindow_release(wnd);
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
	voice = 0;
	recreateVoice = 0;
#ifdef __ARM_NEON__
	neonMode = 0;
#endif
	JNINativeMethod methodTable[] = {
		{"setLerpAndColorIndex", "(ZI)V", (void*)setLerpAndColorIndex},
		{"updateMultiplier", "(Z)V", (void*)updateMultiplier},
		{"init", "(I)V", (void*)init},
		{"checkNeonMode", "()I", (void*)checkNeonMode},
		{"terminate", "()V", (void*)terminate},
		{"prepareSurface", "(Landroid/view/Surface;)I", (void*)prepareSurface},
		{"process", "([BILandroid/view/Surface;)V", (void*)process},
		{"processVoice", "([BLandroid/view/Surface;)V", (void*)processVoice},
		{"glOnSurfaceCreated", "(I)I", (void*)glOnSurfaceCreated},
		{"glOnSurfaceChanged", "(II)V", (void*)glOnSurfaceChanged},
		{"glProcess", "([BI)V", (void*)glProcess},
		{"glDrawFrame", "()V", (void*)glDrawFrame},
		{"glChangeColorIndex", "(I)V", (void*)glChangeColorIndex},
		{"glChangeSpeed", "(I)V", (void*)glChangeSpeed}
	};
	JNIEnv* env;
	if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
		return -1;
	jclass clazz = env->FindClass("br/com/carlosrafaelgn/fplay/visualizer/SimpleVisualizerJni");
	if (!clazz)
		return -1;
	env->RegisterNatives(clazz, methodTable, sizeof(methodTable) / sizeof(methodTable[0]));
	return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
}

}
