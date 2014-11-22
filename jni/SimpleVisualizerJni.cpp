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


#include "Common.h"
#include "OpenGLVisualizerJni.h"


#define DEFSPEED (0.140625f / 16.0f)

static float invBarW;
static int barW, barH, barBins, barWidthInPixels, recreateVoice, lerp;
static unsigned short bgColor;
static unsigned short* voice, *alignedVoice;

void JNICALL setLerp(JNIEnv* env, jclass clazz, jboolean jlerp) {
	lerp = (jlerp ? 1 : 0);
}

void JNICALL init(JNIEnv* env, jclass clazz, int jbgColor) {
	voice = 0;
	recreateVoice = 0;
	commonColorIndex = 0;
	commonColorIndexApplied = 0;
	const unsigned int r = ((jbgColor >> 16) & 0xff) >> 3;
	const unsigned int g = ((jbgColor >> 8) & 0xff) >> 2;
	const unsigned int b = (jbgColor & 0xff) >> 3;
	bgColor = (unsigned short)((r << 11) | (g << 5) | b);
	commonUpdateMultiplier(env, clazz, 0);
}

void JNICALL terminate(JNIEnv* env, jclass clazz) {
	if (voice) {
		free(voice);
		voice = 0;
	}
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
		const int amplSq = (re * re) + (im * im);
		float m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
		
		if (barW == 1 || !lerp) {
			//v goes from 0 to 32768+ (inclusive)
			int v = (int)m;
			if (v > 32768)
				v = 32768;
			
			const unsigned short color = COLORS[commonColorIndex + (v >> 7)];
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
				
				const unsigned short color = COLORS[commonColorIndex + (v >> 7)];
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
	bfft[1] = 0;
	
	//step 1: compute all magnitudes
	for (int i = barBins - 1; i >= 0; i--) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		const int amplSq = (re * re) + (im * im);
		float m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
	}
	
	int32x4_t _0 = vld1q_s32(__0), _32768 = vld1q_s32(__32768), _barH = { barH, barH, barH, barH }, _barH2 = vshrq_n_s32(_barH, 1), _colorIndex = { commonColorIndex, commonColorIndex, commonColorIndex, commonColorIndex };
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
		const int amplSq = (re * re) + (im * im);
		const float m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
		if (barW == 1) {
			//v goes from 0 to 256+ (inclusive)
			const int v = (int)m;
			*currentBar = COLORS[commonColorIndex + ((v >= 256) ? 256 : v)];
			currentBar++;
		} else {
			const float delta = (m - previous) * invBarW;
			for (int i = 0; i < barW; i++) {
				previous += delta;
				const int v = (int)previous;
				*currentBar = COLORS[commonColorIndex + ((v >= 256) ? 256 : v)];
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
	commonColorIndex = 0;
	commonColorIndexApplied = 0;
	neonMode = 0;
	neonDone = 0;
#endif
	JNINativeMethod methodTable[] = {
		{"commonSetSpeed", "(I)V", (void*)commonSetSpeed},
		{"commonSetColorIndex", "(I)V", (void*)commonSetColorIndex},
		{"commonCheckNeonMode", "()I", (void*)commonCheckNeonMode},
		{"commonUpdateMultiplier", "(Z)V", (void*)commonUpdateMultiplier},
		{"commonProcess", "([BII)I", (void*)commonProcess},

		{"setLerp", "(Z)V", (void*)setLerp},
		{"init", "(I)V", (void*)init},
		{"terminate", "()V", (void*)terminate},
		{"prepareSurface", "(Landroid/view/Surface;)I", (void*)prepareSurface},
		{"process", "([BILandroid/view/Surface;)V", (void*)process},
		{"processVoice", "([BLandroid/view/Surface;)V", (void*)processVoice},

		{"glOnSurfaceCreated", "(I)I", (void*)glOnSurfaceCreated},
		{"glOnSurfaceChanged", "(II)V", (void*)glOnSurfaceChanged},
		{"glDrawFrame", "()V", (void*)glDrawFrame},
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
