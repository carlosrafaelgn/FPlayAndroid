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
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>

//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html

#define MAX(A,B) (((A) > (B)) ? (A) : (B))

#include "Common.h"
#include "OpenGLVisualizerJni.h"

static float invBarW;
static int32_t barW, barH, barBins, barWidthInPixels, recreateVoice, lerp;
static uint16_t bgColor;
static uint16_t* voice, *alignedVoice;

void JNICALL setLerp(JNIEnv* env, jclass clazz, jboolean jlerp) {
	lerp = (jlerp ? 1 : 0);
}

void JNICALL init(JNIEnv* env, jclass clazz, int32_t jbgColor) {
	voice = 0;
	recreateVoice = 0;
	commonColorIndex = 0;
	commonColorIndexApplied = 0;
	const uint32_t r = ((jbgColor >> 16) & 0xff) >> 3;
	const uint32_t g = ((jbgColor >> 8) & 0xff) >> 2;
	const uint32_t b = (jbgColor & 0xff) >> 3;
	bgColor = (uint16_t)((r << 11) | (g << 5) | b);
	commonUpdateMultiplier(env, clazz, 0, 0);
}

void JNICALL terminate(JNIEnv* env, jclass clazz) {
	if (voice) {
		free(voice);
		voice = 0;
	}
}

int32_t JNICALL prepareSurface(JNIEnv* env, jclass clazz, jobject surface) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return -1;
	int32_t ret = -2;
	int32_t w = ANativeWindow_getWidth(wnd), h = ANativeWindow_getHeight(wnd);
	if (w > 0 && h > 0) {
		barW = w >> 8;
		barH = h & (~1); //make the height always an even number
		if (barW < 1)
			barW = 1;
		const int32_t size = barW << 8;
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

void JNICALL process(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface, int32_t opt) {
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
	inf.stride <<= 1; //convert from pixels to uint16_t
	
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	int8_t* bfft;
	if (!(opt & IGNORE_INPUT)) {
		bfft = (int8_t*)env->GetPrimitiveArrayCritical(jbfft, 0);
		if (!bfft) {
			ANativeWindow_unlockAndPost(wnd);
			ANativeWindow_release(wnd);
			return;
		}
		doFft((uint8_t*)bfft, DATA_FFT);

		//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
		bfft[1] = 0;
	} else {
		bfft = 0;
	}

	float* const fft = floatBuffer;
	const float* const multiplier = floatBuffer + 256;

	const float coefNew = COEF_SPEED_DEF * (float)commonUptimeDeltaMillis(&commonLastTime);
	const float coefOld = 1.0f - coefNew;

	float previous = 0;
	for (int32_t i = 0; i < barBins; i++) {
		float m;
		if (bfft) {
			//bfft[i] stores values from 0 to -128/127 (inclusive)
			const int32_t re = (int32_t)bfft[i << 1];
			const int32_t im = (int32_t)bfft[(i << 1) + 1];
			const int32_t amplSq = (re * re) + (im * im);
			m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
			previousM[i] = m;
		} else {
			m = previousM[i];
		}
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
		
		if (barW == 1 || !lerp) {
			//v goes from 0 to 32768+ (inclusive)
			int32_t v = (int32_t)m;
			if (v > 32768)
				v = 32768;
			
			const uint16_t color = COLORS[commonColorIndex + (v >> 7)];
			v = ((v * barH) >> 15);
			int32_t v2 = v;
			v = (barH - v) >> 1;
			v2 += v;
			uint16_t* currentBar = (uint16_t*)inf.bits;
			inf.bits = (void*)((uint16_t*)inf.bits + barW);
			
			int32_t y = 0;
			switch (barW) {
			case 1:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				break;
			case 2:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				break;
			case 3:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar[2] = color;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				break;
			case 4:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar[3] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar[2] = color;
					currentBar[3] = color;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar[3] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				break;
			default:
				for (; y < v; y++) {
					for (int32_t b = barW - 1; b >= 0; b--)
						currentBar[b] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					for (int32_t b = barW - 1; b >= 0; b--)
						currentBar[b] = color;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					for (int32_t b = barW - 1; b >= 0; b--)
						currentBar[b] = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				break;
			}
		} else {
			const float delta = (int32_t)(m - previous) * invBarW;
			for (int32_t i = 0; i < barW; i++) {
				previous += delta;
				/*if (previous < 0.0f)
					previous = 0.0f;
				else if (previous > 32768.0f)
					previous = 32768.0f;*/
				
				//v goes from 0 to 32768 (inclusive)
				int32_t v = (int32_t)previous;
				if (v < 0)
					v = 0;
				else if (v > 32768)
					v = 32768;
				
				const uint16_t color = COLORS[commonColorIndex + (v >> 7)];
				v = ((v * barH) >> 15);
				int32_t v2 = v;
				v = (barH - v) >> 1;
				v2 += v;
				uint16_t* currentBar = (uint16_t*)inf.bits;
				inf.bits = (void*)((uint16_t*)inf.bits + 1);
				
				int32_t y = 0;
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (uint16_t*)((uint8_t*)currentBar + inf.stride);
				}
			}
		}
	}

	if (bfft)
		env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
	ANativeWindow_unlockAndPost(wnd);
	ANativeWindow_release(wnd);
}

void JNICALL processVoice(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface, int32_t opt) {
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
		voice = (uint16_t*)malloc(((inf.stride * inf.height) << 1) + 16);
		uint8_t* al = (uint8_t*)voice;
		while ((((size_t)al) & 15))
			al++;
		alignedVoice = (uint16_t*)al;
		recreateVoice = 0;
	}
	if (!voice) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to uint16_t
	
	int32_t v = inf.stride * (inf.height - 1);
	memcpy(alignedVoice, (uint8_t*)alignedVoice + inf.stride, v);
	uint16_t* currentBar = (uint16_t*)((uint8_t*)alignedVoice + v);
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	int8_t* bfft;
	if (!(opt & IGNORE_INPUT)) {
		bfft = (int8_t*)env->GetPrimitiveArrayCritical(jbfft, 0);
		if (!bfft) {
			ANativeWindow_unlockAndPost(wnd);
			ANativeWindow_release(wnd);
			return;
		}
		doFft((uint8_t*)bfft, DATA_FFT);

		//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
		bfft[1] = 0;
	} else {
		bfft = 0;
	}

	float previous = 0;
	
	for (int32_t i = 0; i < barBins; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		float m;
		if (bfft) {
			const int32_t re = (int32_t)bfft[i << 1];
			const int32_t im = (int32_t)bfft[(i << 1) + 1];
			const int32_t amplSq = (re * re) + (im * im);
			m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
			previousM[i] = m;
		} else {
			m = previousM[i];
		}
		if (barW == 1) {
			//v goes from 0 to 256+ (inclusive)
			const int32_t v = (int32_t)m;
			*currentBar = COLORS[commonColorIndex + ((v >= 256) ? 256 : v)];
			currentBar++;
		} else {
			const float delta = (m - previous) * invBarW;
			for (int32_t i = 0; i < barW; i++) {
				previous += delta;
				const int32_t v = (int32_t)previous;
				*currentBar = COLORS[commonColorIndex + ((v >= 256) ? 256 : v)];
				currentBar++;
			}
		}
	}
	if (bfft)
		env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
	memcpy(inf.bits, alignedVoice, inf.stride * inf.height);
	ANativeWindow_unlockAndPost(wnd);
	ANativeWindow_release(wnd);
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
	voice = 0;
	recreateVoice = 0;
	glResetState();
	commonTime = 0;
	commonTimeLimit = 0xffffffff;
	commonColorIndex = 0;
	commonColorIndexApplied = 0;
	commonCoefNew = 0.0f;
	rootMeanSquare = 0.0f;
	vuMeter = 0.0f;
	vuMeterUnfiltered = 0.f;
	vuMeterFilterState[0] = 0.0f;
	vuMeterFilterState[1] = 0.0f;
	vuMeterFilterState[2] = 0.0f;
	vuMeterFilterState[3] = 0.0f;
	beatCounter = 0;
	beatState = BEAT_STATE_VALLEY;
	beatPeakOrValley = 0;
	beatThreshold = BEAT_MIN_RISE_THRESHOLD;
	beatDeltaMillis = 0;
	beatSilenceDeltaMillis = 0;
	beatSpeedBPM = 0;
	beatFilteredInput = 0;
#ifdef FPLAY_ARM
	neonMode = 0;
	neonDone = 0;
#endif
	JNINativeMethod methodTable[] = {
		{"commonSetSpeed", "(I)V", (void*)commonSetSpeed},
		{"commonSetColorIndex", "(I)V", (void*)commonSetColorIndex},
		{"commonCheckNeonMode", "()I", (void*)commonCheckNeonMode},
		{"commonUpdateMultiplier", "(ZZ)V", (void*)commonUpdateMultiplier},
		{"commonProcess", "([BI)I", (void*)commonProcess},

		{"setLerp", "(Z)V", (void*)setLerp},
		{"init", "(I)V", (void*)init},
		{"terminate", "()V", (void*)terminate},
		{"prepareSurface", "(Landroid/view/Surface;)I", (void*)prepareSurface},
		{"process", "([BLandroid/view/Surface;I)V", (void*)process},
		{"processVoice", "([BLandroid/view/Surface;I)V", (void*)processVoice},

		{"glGetOESTexture", "()I", (void*)glGetOESTexture},
		{"glOnSurfaceCreated", "(IIIIII)I", (void*)glOnSurfaceCreated},
		{"glOnSurfaceChanged", "(IIIIII)V", (void*)glOnSurfaceChanged},
		{"glLoadBitmapFromJava", "(Landroid/graphics/Bitmap;)I", (void*)glLoadBitmapFromJava},
		{"glDrawFrame", "()V", (void*)glDrawFrame},
		{"glOnSensorReset", "()V", (void*)glOnSensorReset},
		{"glOnSensorData", "(JI[F)V", (void*)glOnSensorData},
		{"glSetImmersiveCfg", "(II)V", (void*)glSetImmersiveCfg},
		{"glReleaseView", "()V", (void*)glReleaseView}
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
	if (glSoundParticle) {
		delete glSoundParticle;
		glSoundParticle = 0;
	}
}

}
