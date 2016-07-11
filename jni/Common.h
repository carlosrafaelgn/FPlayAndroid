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

#include "SimpleMutex.h"
#include "CommonNeon.h"
#include "FixedFFT.h"
#include "FFTNR.h"
#include <time.h>

//for the alignment:
//https://gcc.gnu.org/onlinedocs/gcc-3.2/gcc/Variable-Attributes.html

float floatBuffer[(QUARTER_FFT_SIZE * 2) + (QUARTER_FFT_SIZE / 4)] __attribute__((aligned(16)));
float previousM[QUARTER_FFT_SIZE] __attribute__((aligned(16)));
float commonCoefNew;
int32_t intBuffer[8] __attribute__((aligned(16)));

//to make the math easier COLORS has 257 int's (from 0 to 256) for each different color
static const uint16_t COLORS[] __attribute__((aligned(16))) = { /*Blue */ 0x0000, 0x0816, 0x0816, 0x0815, 0x0815, 0x0815, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1815, 0x1814, 0x1814, 0x1814, 0x1814, 0x2014, 0x2014, 0x2014, 0x2013, 0x2013, 0x2813, 0x2813, 0x2813, 0x2813, 0x3012, 0x3012, 0x3012, 0x3012, 0x3812, 0x3811, 0x3811, 0x3811, 0x4011, 0x4011, 0x4011, 0x4010, 0x4810, 0x4810, 0x4810, 0x4810, 0x500f, 0x500f, 0x500f, 0x500f, 0x580f, 0x580e, 0x580e, 0x600e, 0x600e, 0x600d, 0x680d, 0x680d, 0x680d, 0x680d, 0x700c, 0x700c, 0x700c, 0x780c, 0x780c, 0x780b, 0x800b, 0x800b, 0x800b, 0x800a, 0x880a, 0x880a, 0x880a, 0x900a, 0x9009, 0x9009, 0x9009, 0x9809, 0x9809, 0x9808, 0xa008, 0xa008, 0xa008, 0xa807, 0xa807, 0xa807, 0xa807, 0xb007, 0xb006, 0xb006, 0xb806, 0xb806, 0xb806, 0xb805, 0xc005, 0xc005, 0xc005, 0xc005, 0xc804, 0xc804, 0xc804, 0xc804, 0xd004, 0xd004, 0xd003, 0xd003, 0xd803, 0xd803, 0xd803, 0xd802, 0xe002, 0xe002, 0xe002, 0xe002, 0xe002, 0xe802, 0xe801, 0xe801, 0xe801, 0xe801, 0xf001, 0xf001, 0xf001, 0xf000, 0xf000, 0xf000, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf820, 0xf820, 0xf820, 0xf840, 0xf840, 0xf840, 0xf860, 0xf860, 0xf860, 0xf880, 0xf880, 0xf8a0, 0xf8a0, 0xf8a0, 0xf8c0, 0xf8c0, 0xf8e0, 0xf8e0, 0xf900, 0xf900, 0xf900, 0xf920, 0xf920, 0xf940, 0xf940, 0xf960, 0xf960, 0xf980, 0xf9a0, 0xf9a0, 0xf9a0, 0xf9c0, 0xf9e0, 0xf9e0, 0xfa00, 0xfa00, 0xfa20, 0xfa20, 0xfa40, 0xfa40, 0xfa60, 0xfa80, 0xfa80, 0xfaa0, 0xfaa0, 0xfac0, 0xfae0, 0xfae0, 0xfb00, 0xfb00, 0xfb20, 0xfb40, 0xfb40, 0xfb60, 0xfb60, 0xfb80, 0xfba0, 0xfba0, 0xfbc0, 0xfbe0, 0xfbe0, 0xfc00, 0xfc00, 0xfc20, 0xfc20, 0xfc40, 0xfc60, 0xfc60, 0xfc80, 0xfca0, 0xfca0, 0xfcc0, 0xfcc0, 0xfce0, 0xfd00, 0xfd00, 0xfd20, 0xfd20, 0xfd40, 0xfd60, 0xfd60, 0xfd80, 0xfd80, 0xfda0, 0xfda0, 0xfdc0, 0xfde0, 0xfde0, 0xfe00, 0xfe00, 0xfe20, 0xfe20, 0xfe40, 0xfe40, 0xfe60, 0xfe60, 0xfe80, 0xfe80, 0xfea0, 0xfea0, 0xfec0, 0xfec0, 0xfee0, 0xfee0, 0xff00, 0xff00, 0xff20, 0xff20, 0xff20, 0xff40, 0xff40, 0xff40, 0xff60, 0xff60, 0xff80, 0xff80, 0xff80, 0xffa0, 0xffa0, 0xffa0, 0xffc0, 0xffc0, 0xffc0, 0xffc0, 0xffc0,
																/*Green*/ 0x0000, 0x0000, 0x0000, 0x0020, 0x0020, 0x0040, 0x0040, 0x0060, 0x0060, 0x0080, 0x00a0, 0x00a0, 0x00c0, 0x00e0, 0x00e0, 0x0100, 0x0120, 0x0140, 0x0160, 0x0160, 0x0180, 0x01a0, 0x01c0, 0x01e0, 0x01e0, 0x0200, 0x0220, 0x0240, 0x0260, 0x0280, 0x0280, 0x02a0, 0x02c0, 0x02e0, 0x0300, 0x0320, 0x0340, 0x0360, 0x0360, 0x0380, 0x03a0, 0x03c0, 0x03e0, 0x0400, 0x0400, 0x0420, 0x0440, 0x0440, 0x0460, 0x0480, 0x0480, 0x04a0, 0x04c0, 0x04c0, 0x04c0, 0x04e0, 0x04e0, 0x0ce0, 0x0d00, 0x0d00, 0x0d00, 0x1520, 0x1520, 0x1540, 0x1540, 0x1d40, 0x1d60, 0x1d60, 0x2580, 0x2580, 0x25a0, 0x2da0, 0x2da0, 0x2dc0, 0x35c0, 0x35e0, 0x35e0, 0x3e00, 0x3e00, 0x3e00, 0x4620, 0x4620, 0x4e40, 0x4e40, 0x4e60, 0x5660, 0x5660, 0x5e80, 0x5e80, 0x5ea0, 0x66a0, 0x66c0, 0x66c0, 0x6ec0, 0x6ee0, 0x76e0, 0x7700, 0x7f00, 0x7f00, 0x7f20, 0x8720, 0x8720, 0x8f40, 0x8f40, 0x8f40, 0x9760, 0x9760, 0x9f80, 0x9f80, 0x9f80, 0xa780, 0xa7a0, 0xafa0, 0xafa0, 0xafc0, 0xb7c0, 0xb7c0, 0xbfc0, 0xbfc0, 0xbfe0, 0xc7e0, 0xc7e0, 0xc7e0, 0xcfe0, 0xcfe0, 0xcfe0, 0xd7e0, 0xd7e0, 0xdfe0, 0xdfe0, 0xdfe0, 0xdfe0, 0xe7e0, 0xe7e0, 0xe7e0, 0xefe0, 0xefe0, 0xefe0, 0xefe0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffc0, 0xffc0, 0xffc0, 0xffa0, 0xffa0, 0xffa0, 0xff80, 0xff80, 0xff80, 0xff60, 0xff60, 0xff40, 0xff40, 0xff40, 0xff20, 0xff00, 0xff00, 0xfee0, 0xfee0, 0xfec0, 0xfec0, 0xfea0, 0xfea0, 0xfe80, 0xfe60, 0xfe60, 0xfe40, 0xfe20, 0xfe20, 0xfe00, 0xfe00, 0xfde0, 0xfdc0, 0xfda0, 0xfda0, 0xfd80, 0xfd60, 0xfd60, 0xfd40, 0xfd20, 0xfd00, 0xfd00, 0xfce0, 0xfcc0, 0xfca0, 0xfca0, 0xfc80, 0xfc60, 0xfc40, 0xfc40, 0xfc20, 0xfc00, 0xfbe0, 0xfbe0, 0xfbc0, 0xfba0, 0xfb80, 0xfb60, 0xfb60, 0xfb40, 0xfb20, 0xfb00, 0xfb00, 0xfae0, 0xfac0, 0xfaa0, 0xfaa0, 0xfa80, 0xfa60, 0xfa60, 0xfa40, 0xfa20, 0xfa00, 0xfa00, 0xf9e0, 0xf9c0, 0xf9c0, 0xf9a0, 0xf980, 0xf980, 0xf960, 0xf960, 0xf940, 0xf920, 0xf920, 0xf900, 0xf8e0, 0xf8e0, 0xf8c0, 0xf8c0, 0xf8a0, 0xf8a0, 0xf880, 0xf880, 0xf860, 0xf860, 0xf860, 0xf840, 0xf840, 0xf820, 0xf820, 0xf820, 0xf800, 0xf800, 0xf800, 0xf800 };
static float fftData[FFT_SIZE] __attribute__((aligned(16)));
//vuMeter ranges from 0 to 1, and should be mapped to (-40dB to 6.5dB)
//vuMeterUnfiltered ranges from 0 to over 1, and should be mapped to (-40dB to over 6.5dB)
static float rootMeanSquare, vuMeter, vuMeterUnfiltered, vuMeterFilterState[4];
static uint32_t commonColorIndex, commonColorIndexApplied, commonTime, commonTimeLimit, commonLastTime;
#ifdef FPLAY_ARM
static uint32_t neonMode, neonDone;
#endif

//Beat detection
#define BEAT_STATE_VALLEY 0
#define BEAT_STATE_PEAK 1
#define BEAT_MIN_RISE_THRESHOLD 40
uint32_t beatCounter, beatState, beatPeakOrValley, beatThreshold, beatDeltaMillis, beatSilenceDeltaMillis, beatSpeedBPM;
float beatFilteredInput;

uint32_t commonUptimeDeltaMillis(uint32_t* lastTime) {
	struct timespec t;
	t.tv_sec = 0;
	t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	*((uint32_t*)&t) = (uint32_t)((t.tv_sec * 1000) + (t.tv_nsec / 1000000));
	const uint32_t delta = *((uint32_t*)&t) - *lastTime;
	*lastTime = *((uint32_t*)&t);
	return ((delta >= 100) ? 100 : delta);
}

uint32_t commonUptimeMillis() {
	struct timespec t;
	t.tv_sec = 0;
	t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return (uint32_t)((t.tv_sec * 1000) + (t.tv_nsec / 1000000));
}

uint64_t commonUptimeNs() {
	struct timespec t;
	t.tv_sec = 0;
	t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return ((uint64_t)t.tv_sec * 1000000000) + (uint64_t)t.tv_nsec;
}

void commonSRand() {
	struct timespec t;
	t.tv_sec = 0;
	t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	srand((uint32_t)t.tv_nsec);
}

void JNICALL commonSetSpeed(JNIEnv* env, jclass clazz, int32_t speed) {
	switch (speed) {
	case 1:
		commonCoefNew = COEF_SPEED_1;
		break;
	case 2:
		commonCoefNew = COEF_SPEED_2;
		break;
	default:
		commonCoefNew = COEF_SPEED_0;
		break;
	}
}

void JNICALL commonSetColorIndex(JNIEnv* env, jclass clazz, int32_t jcolorIndex) {
	commonColorIndex = jcolorIndex;
}

int32_t JNICALL commonCheckNeonMode(JNIEnv* env, jclass clazz) {
#ifdef FPLAY_ARM
	if (neonDone == 1)
		return neonMode;
	//based on
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.c?r=2195
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.h?r=2195
	neonMode = 0;
	char cpuinfo[4096];
	int32_t cpuinfo_len = -1;
	int32_t fd = open("/proc/cpuinfo", O_RDONLY);
	if (fd >= 0) {
		do {
			cpuinfo_len = read(fd, cpuinfo, 4096);
		} while (cpuinfo_len < 0 && errno == EINTR);
		close(fd);
		if (cpuinfo_len > 0) {
			cpuinfo[cpuinfo_len] = 0;
			//look for the "\nFeatures: " line
			for (int32_t i = cpuinfo_len - 9; i >= 0; i--) {
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
	neonDone = 1;
	return neonMode;
#else
	return 0;
#endif
}

void JNICALL commonUpdateMultiplier(JNIEnv* env, jclass clazz, jboolean isVoice, jboolean hq) {
	float* const fft = floatBuffer;
	float* const multiplier = fft + QUARTER_FFT_SIZE;
	uint8_t* const processedData = (uint8_t*)(fft + (QUARTER_FFT_SIZE * 2));
	rootMeanSquare = 0.0f;
	vuMeter = 0.0f;
	vuMeterUnfiltered = 0.0f;
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
	if (isVoice) {
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i++) {
			fft[i] = 0;
			processedData[i] = 0;
			previousM[i] = 0;
			//const double d = 180.0 - exp(1.0 / (((double)i / 10000.0) + 0.187));
			//multiplier[i] = ((d <= 1.5) ? 1.5f : (float)(d / 111.0));
			const double d = 5.0 * (400.0 - exp(1.0 / (((double)i / 3700.0) + 0.165)));
			multiplier[i] = ((d <= 256.0) ? 256.0f : (float)d) / 114.0f;
			//multiplier[i] = 2.0f * expf((float)i / 128.0f);
		}
	} else {
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i++) {
			fft[i] = 0;
			processedData[i] = 0;
			previousM[i] = 0;
			//const double d = 180.0 - exp(1.0 / (((double)i / 10000.0) + 0.187));
			//multiplier[i] = ((d <= 1.5) ? 1.5f : (float)d);
			const double d = 5.0 * (400.0 - exp(1.0 / (((double)i / 3700.0) + 0.165)));
			multiplier[i] = ((d <= 256.0) ? 256.0f : (float)d);
			//multiplier[i] = 256.0f * expf((float)i / 128.0f);
			
			if (hq)
				multiplier[i] *= (1.0f / (float)(FFT_SIZE / 2));
		}
		if (hq) {
			multiplier[0] *= 0.5f;

			FFTNR::Initialize();
			//Hamming window
			//for (int32_t i = 0; i < FFT_SIZE; i++)
			//	fftWindow[i] = (float)(0.54 - (0.46 * cos(2.0 * 3.1415926535897932384626433832795 * (double)i / (double)(FFT_SIZE - 1))));
		}
	}
}

int32_t JNICALL commonProcess(JNIEnv* env, jclass clazz, jbyteArray jwaveform, int32_t opt) {
	const uint32_t deltaMillis = commonUptimeDeltaMillis(&commonLastTime);
	beatDeltaMillis += deltaMillis;
	beatSilenceDeltaMillis += deltaMillis;
	int32_t i;
	i = commonTime + deltaMillis;
	while (((uint32_t)i) >= commonTimeLimit)
		i -= commonTimeLimit;
	commonTime = i;
	
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	int8_t* bfft;
	if (!(opt & IGNORE_INPUT) || (opt & BLUETOOTH_PROCESSING)) {
		bfft = (int8_t*)env->GetPrimitiveArrayCritical(jwaveform, 0);
		if (!bfft)
			return 0;

		if (!(opt & IGNORE_INPUT)) {
			if ((opt & DATA_VUMETER)) {
				rootMeanSquare = sqrtf((float)doFft((uint8_t*)bfft, opt) * (1.0f / (float)(CAPTURE_SIZE)));
				//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
				bfft[1] = 0;
			} else if ((opt & DATA_FFT)) {
				if ((opt & DATA_FFT_HQ)) {
					for (int32_t i = 0; i < FFT_SIZE; i++)
						fftData[i] = (float)((int32_t)(((uint8_t*)bfft)[i]) - 128);
					FFTNR::Forward(fftData);
					//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
					fftData[1] = 0.0f;
				} else {
					doFft((uint8_t*)bfft, DATA_FFT);
					//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
					bfft[1] = 0;
				}
			}
		}
	} else {
		bfft = 0;
	}

	int32_t vuMeterI = 0;
	if ((opt & DATA_VUMETER)) {
		//vuMeterUnfiltered goes from 0 to over 1 (-40dB to over 6.5dB)
		vuMeterUnfiltered = ((rootMeanSquare > 0.42f) ? (((20.0f * log10f(rootMeanSquare / 42.0f)) + 40.0f) / 46.5f) : 0.0f);
		//2nd order Butterworth low pass filter, with cutoff frequency = 0.05 (in a scale from 0 to 0.5)
		const float vuOut = (0.020083366f * vuMeterUnfiltered) + //x
							(0.040166731f * vuMeterFilterState[0]) + //x-1
							(0.020083366f * vuMeterFilterState[1]) + //x-2
							(1.561018076f * vuMeterFilterState[2]) + //y-1
							(-0.641351538f * vuMeterFilterState[3]); //y-2

		vuMeterFilterState[1] = vuMeterFilterState[0];
		vuMeterFilterState[0] = vuMeterUnfiltered;
		vuMeterFilterState[3] = vuMeterFilterState[2];
		vuMeterFilterState[2] = vuOut;

		vuMeter = ((vuOut >= 1.0f) ? 1.0f : ((vuOut <= 0.0f) ? 0.0f : vuOut));
		vuMeterI = (int32_t)(vuMeter * 255.0f);

		if (!(opt & ~(IGNORE_INPUT | DATA_VUMETER))) {
			if (bfft)
				env->ReleasePrimitiveArrayCritical(jwaveform, bfft, JNI_ABORT);
			return 0;
		}
	}

	uint8_t* processedData = (uint8_t*)(floatBuffer + (QUARTER_FFT_SIZE << 1));

#ifdef FPLAY_ARM
if (!neonMode) {
	float* const fft = floatBuffer;
	const float* const multiplier = floatBuffer + QUARTER_FFT_SIZE;

	const float coefNew = commonCoefNew * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;

	if ((opt & IGNORE_INPUT)) {
		for (i = 0; i < QUARTER_FFT_SIZE; i++) {
			float m = previousM[i];
			const float old = fft[i];
			if (m < old)
				m = (coefNew * m) + (coefOld * old);
			fft[i] = m;
			//v goes from 0 to 32768+ (inclusive)
			const uint32_t v = ((uint32_t)m) >> 7;
			processedData[i] = ((v >= 255) ? 255 : (uint8_t)v);
		}
	} else if ((opt & DATA_FFT_HQ)) {
		for (i = 0; i < QUARTER_FFT_SIZE; i++) {
			//fftData[i] stores values from 0 to -128/127 (inclusive)
			const float re = fftData[i << 1];
			const float im = fftData[(i << 1) + 1];
			const float amplSq = (re * re) + (im * im);
			float m = ((amplSq <= 8.0f) ? 0.0f : (multiplier[i] * sqrtf(amplSq)));
			previousM[i] = m;
			const float old = fft[i];
			if (m < old)
				m = (coefNew * m) + (coefOld * old);
			fft[i] = m;
			//v goes from 0 to 32768+ (inclusive)
			const uint32_t v = ((uint32_t)m) >> 7;
			processedData[i] = ((v >= 255) ? 255 : (uint8_t)v);
		}
	} else {
		for (i = 0; i < QUARTER_FFT_SIZE; i++) {
			//bfft[i] stores values from 0 to -128/127 (inclusive)
			const int32_t re = (int32_t)bfft[i << 1];
			const int32_t im = (int32_t)bfft[(i << 1) + 1];
			const int32_t amplSq = (re * re) + (im * im);
			float m = ((amplSq <= 8) ? 0.0f : (multiplier[i] * sqrtf((float)amplSq)));
			previousM[i] = m;
			const float old = fft[i];
			if (m < old)
				m = (coefNew * m) + (coefOld * old);
			fft[i] = m;
			//v goes from 0 to 32768+ (inclusive)
			const uint32_t v = ((uint32_t)m) >> 7;
			processedData[i] = ((v >= 255) ? 255 : (uint8_t)v);
		}
	}
} else {
	commonProcessNeon(bfft, fftData, deltaMillis, opt);
}
#else
	// x86 Support for ARM NEON Intrinsics
	// x86 also uses this file -> https://developer.android.com/ndk/guides/x86.html
	commonProcessNeon(bfft, fftData, deltaMillis, opt);
#endif

	if ((opt & BEAT_DETECTION)) {
		//Beat detection (we are using a threshold of 25%)
		//processedData[0] = DC
		//processedData[1] = 1 * 44100/1024 = 43Hz
		//processedData[2] = 2 * 44100/1024 = 86Hz
		//processedData[3] = 3 * 44100/1024 = 129Hz
		//processedData[4] = 4 * 44100/1024 = 172Hz
		//processedData[5] = 5 * 44100/1024 = 215Hz
		//processedData[6] = 6 * 44100/1024 = 258Hz
		//processedData[7] = 7 * 44100/1024 = 301Hz
		//filter again
		beatFilteredInput = ((1.0f - 0.140625f) * beatFilteredInput) + (0.140625f * (float)processedData[(opt & BEAT_DETECTION) >> 12]);
		const uint32_t m = (uint32_t)beatFilteredInput;
		//__android_log_print(ANDROID_LOG_INFO, "JNI", "\t%d\t%d", (uint32_t)processedData[2], (uint32_t)processedData[3]);
		if (beatState == BEAT_STATE_VALLEY) {
			if (m < beatPeakOrValley) {
				//Just update the valley
				beatPeakOrValley = m;
				beatThreshold = ((m * 5) >> 2); //125%
				if (beatThreshold < BEAT_MIN_RISE_THRESHOLD)
					beatThreshold = BEAT_MIN_RISE_THRESHOLD;
			} else if (m >= beatThreshold && beatDeltaMillis >= 150) { //no more than 150 bpm
				//Check if we have crossed the threshold... Beat found! :D
				beatCounter++;
				//Average:
				//BPM = 60000 / beatDeltaMillis
				//BPM / 4 = 60000 / (4 * beatDeltaMillis)
				//BPM / 4 = 15000 / beatDeltaMillis
				//Since beatDeltaMillis accounts only for half of the period, we use 7500 instead of 15000
				//Let's not worry about division by 0 since beatDeltaMillis is incremented at the
				//beginning of this function (it would take 49 days and lot of concidence to reach 0!!!)
				beatSpeedBPM = ((beatSpeedBPM * 3) >> 2) + (7500 / beatDeltaMillis);
				//__android_log_print(ANDROID_LOG_INFO, "JNI", "%d %d", beatCounter >> 1, beatSpeedBPM);
				beatDeltaMillis = 0;
				beatSilenceDeltaMillis = 0;
				beatState = BEAT_STATE_PEAK;
				beatPeakOrValley = m;
				beatThreshold = ((m * 3) >> 2); //75%
			}
		} else {
			if (m > beatPeakOrValley) {
				//Just update the peak
				beatPeakOrValley = m;
				beatThreshold = ((m * 3) >> 2); //75%
			} else if (m <= beatThreshold && beatDeltaMillis >= 150) {
				//Check if we have crossed the threshold
				beatDeltaMillis = 0;
				beatState = BEAT_STATE_VALLEY;
				beatPeakOrValley = m;
				beatThreshold = ((m * 5) >> 2); //125%
				if (beatThreshold < BEAT_MIN_RISE_THRESHOLD)
                	beatThreshold = BEAT_MIN_RISE_THRESHOLD;
			}
		}
		if (beatSilenceDeltaMillis >= 2000) {
			beatSpeedBPM = (beatSpeedBPM >> 1); //decrease the speed by 50% after 2s without beats
			beatSilenceDeltaMillis = 0;
		}
	}

	opt &= BLUETOOTH_PROCESSING;
	if (!opt) {
		if (bfft)
			env->ReleasePrimitiveArrayCritical(jwaveform, bfft, JNI_ABORT);
		return 0;
	}


	//Bluetooth processing from here on


#define PACK_BIN(BIN) if ((BIN) == 0x01 || (BIN) == 0x1B) { *packet = 0x1B; packet[1] = ((uint8_t)(BIN) ^ 1); packet += 2; len += 2; } else { *packet = (uint8_t)(BIN); packet++; len++; }
	uint8_t* packet = (uint8_t*)bfft;
	int32_t len = 0, last;
	uint8_t avg;
	uint8_t b;
	packet[0] = 1; //SOH - Start of Heading
	packet[1] = (uint8_t)opt; //payload type
	//packet[2] and packet[3] are the payload length
	packet += 4;
	//PACK_BIN(beatCounter);
	//PACK_BIN(beatSpeedBPM);
	//PACK_BIN(vuMeter);
	//processedData stores the first 256 bins, out of the 512 captured by visualizer.getFft
	//which represents frequencies from DC to SampleRate / 4 (roughly from 0Hz to 11025Hz for a SR of 44100Hz)
	//
	//the mapping algorithms used in BLUETOOTH_BINS_4, BLUETOOTH_BINS_8, BLUETOOTH_BINS_16, BLUETOOTH_BINS_32,
	//BLUETOOTH_BINS_64 and in BLUETOOTH_BINS_128 were created empirically ;)
	switch (opt) {
	case BLUETOOTH_BINS_4:
		avg = MAX(processedData[0], processedData[1]);
		avg = MAX(avg, processedData[2]);
		avg = MAX(avg, processedData[3]);
		PACK_BIN(avg);
		i = 4;
		avg = processedData[i++];
		for (; i < 36; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 100; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 228; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		break;
	case BLUETOOTH_BINS_8:
		avg = MAX(processedData[0], processedData[1]);
		PACK_BIN(avg);
		avg = MAX(processedData[2], processedData[3]);
		PACK_BIN(avg);
		i = 4;
		avg = processedData[i++];
		for (; i < 20; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 36; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 68; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 100; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 164; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		avg = processedData[i++];
		for (; i < 228; i++)
			avg = MAX(avg, processedData[i]);
		PACK_BIN(avg);
		break;
	case BLUETOOTH_BINS_16:
		avg = MAX(processedData[0], processedData[1]);
		PACK_BIN(avg);
		avg = MAX(processedData[2], processedData[3]);
		PACK_BIN(avg);
		for (i = 4; i < 20; i += 4) {
			avg = MAX(processedData[i], processedData[i + 1]);
			avg = MAX(avg, processedData[i + 2]);
			avg = MAX(avg, processedData[i + 3]);
			PACK_BIN(avg);
		}
		for (last = 28; last <= 36; last += 8) {
			avg = processedData[i++];
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			PACK_BIN(avg);
		}
		for (last = 52; last <= 100; last += 16) {
			avg = processedData[i++];
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			PACK_BIN(avg);
		}
		for (last = 132; last <= 228; last += 32) {
			avg = processedData[i++];
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			PACK_BIN(avg);
		}
		break;
	case BLUETOOTH_BINS_32:
		b = processedData[0];
		PACK_BIN(b);
		b = processedData[1];
		PACK_BIN(b);
		b = processedData[2];
		PACK_BIN(b);
		b = processedData[3];
		PACK_BIN(b);
		for (i = 4; i < 20; i += 2) {
			avg = MAX(processedData[i], processedData[i + 1]);
			PACK_BIN(avg);
		}
		for (; i < 36; i += 4) {
			avg = MAX(processedData[i], processedData[i + 1]);
			avg = MAX(avg, processedData[i + 2]);
			avg = MAX(avg, processedData[i + 3]);
			PACK_BIN(avg);
		}
		for (last = 44; last <= 100; last += 8) {
			avg = processedData[i++];
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			PACK_BIN(avg);
		}
		for (last = 116; last <= 228; last += 16) {
			avg = processedData[i++];
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			PACK_BIN(avg);
		}
		break;
	case BLUETOOTH_BINS_64:
		for (i = 0; i < 20; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		for (; i < 36; i += 2) {
			avg = MAX(processedData[i], processedData[i + 1]);
			PACK_BIN(avg);
		}
		for (; i < 132; i += 4) {
			avg = MAX(processedData[i], processedData[i + 1]);
			avg = MAX(avg, processedData[i + 2]);
			avg = MAX(avg, processedData[i + 3]);
			PACK_BIN(avg);
		}
		for (last = 140; last <= 228; last += 8) {
			avg = processedData[i++];
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			PACK_BIN(avg);
		}
		break;
	case BLUETOOTH_BINS_128:
		for (i = 0; i < 36; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		for (; i < 184; i += 2) {
			avg = MAX(processedData[i], processedData[i + 1]);
			PACK_BIN(avg);
		}
		for (; i < 252; i += 4) {
			avg = MAX(processedData[i], processedData[i + 1]);
			avg = MAX(avg, processedData[i + 2]);
			avg = MAX(avg, processedData[i + 3]);
			PACK_BIN(avg);
		}
		break;
	case BLUETOOTH_BINS_256:
		for (i = 0; i < 256; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		break;
	default:
		env->ReleasePrimitiveArrayCritical(jwaveform, bfft, JNI_ABORT);
		return 0;
	}
#undef PACK_BIN
	//fill in the payload length
	((uint8_t*)bfft)[2] = (len & 0x7F) << 1; //lower 7 bits, left shifted by 1
	((uint8_t*)bfft)[3] = (len >> 6) & 0xFE; //upper 7 bits, left shifted by 1
	*packet = 4; //EOT - End of Transmission
	env->ReleasePrimitiveArrayCritical(jwaveform, bfft, 0);
	return len + 5;
}
