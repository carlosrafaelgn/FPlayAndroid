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
#define DB_RANGE 2000 //+- 20dB (in millibels)
#define BAND_COUNT 10
#define COEF_SET_COUNT 8

static unsigned int equalizerEnabled, equalizerActualBandCount;
static int equalizerTemp[4] __attribute__((aligned(16)));
static float equalizerGainInDB[BAND_COUNT],
equalizerCoefs[2 * 4 * BAND_COUNT] __attribute__((aligned(16))),
//order for equalizerCoefs:
//0 band0 b0 L
//1 band0 b0 R
//2 band0 b1 L (which is also a1 in our case)
//3 band0 b1 R (which is also a1 in our case)
//4 band0 b2 L
//5 band0 b2 R
//6 band0 -a2 L
//7 band0 -a2 R
//8 band1 b0
//...
equalizerSamples[2 * 4 * BAND_COUNT] __attribute__((aligned(16)));

#include "Filter.h"

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
	//x86 or x86_64
	#include <pmmintrin.h>
#else //#elif defined(__arm__) || defined(__aarch64__)
	//arm
#endif

void resetEqualizer() {
	memset(equalizerTemp, 0, 4 * sizeof(int));
	memset(equalizerSamples, 0, 2 * 4 * BAND_COUNT * sizeof(float));
}

void equalizerConfigChanged() {
	if (sampleRate > (2 * 16000))
		equalizerActualBandCount = 10;
	else if (sampleRate > (2 * 8000))
		equalizerActualBandCount = 9;
	else if (sampleRate > (2 * 4000))
		equalizerActualBandCount = 8;
	else if (sampleRate > (2 * 2000))
		equalizerActualBandCount = 7;
	else
		equalizerActualBandCount = 6; //Android's minimum allowed sample rate is 4000 Hz

	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);

	resetEqualizer();
}

void initializeEqualizer() {
	equalizerEnabled = 0;
	memset(equalizerGainInDB, 0, BAND_COUNT * sizeof(float));
}

void processEqualizer(short* buffer, unsigned int sizeInFrames) {
#define x_n1_L samples[0]
#define x_n1_R samples[1]
#define y_n1_L samples[2]
#define y_n1_R samples[3]
#define x_n2_L samples[4]
#define x_n2_R samples[5]
#define y_n2_L samples[6]
#define y_n2_R samples[7]

	if (channelCount == 2) {
#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)

		//x86 or x86_64
		__m128 inLR, tmp2;
		_mm_xor_ps(inLR, inLR);
		_mm_xor_ps(tmp2, tmp2);
		while ((sizeInFrames--)) {
			float *samples = equalizerSamples;

			equalizerTemp[0] = (int)buffer[0];
			equalizerTemp[1] = (int)buffer[1];
			//inLR = { L, R, xxx, xxx }
			_mm_cvtpi32_ps(inLR, *((__m64*)equalizerTemp));

			//since this is a cascade filter, band0's output is band1's input and so on....
			for (int i = 0; i < equalizerActualBandCount; i++, samples += 8) {
				//y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
				//but, since our a2 was negated and b1 = a1, the formula becomes:
				//y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2)

				//b0_b1 = { b0, b0, b1, b1 }
				__m128 b0_b1 = _mm_load_ps(equalizerCoefs + (i << 3));
				//b2_a2 = { b2, b2, -a2, -a2 }
				__m128 b2_a2 = _mm_load_ps(equalizerCoefs + (i << 3) + 4);

				//local = { x_n1_L, x_n1_R, y_n1_L, y_n1_R }
				__m128 local = _mm_load_ps(samples);

				//tmp = { x_n1_L, x_n1_R, y_n1_L, y_n1_R }
				__m128 tmp = local;

				//tmp2 = { y_n1_L, y_n1_R, xxx, xxx }
				_mm_movehl_ps(tmp2, local);

				//tmp = { x_n1_L - y_n1_L, x_n1_R - y_n1_R, xxx, xxx }
				_mm_sub_ps(tmp, tmp2);

				//inLR = { L, R, x_n1_L - y_n1_L, x_n1_R - y_n1_R }
				_mm_movelh_ps(inLR, tmp);

				//tmp2 = { x_n2_L, x_n2_R, y_n2_L, y_n2_R }
				tmp2 = _mm_load_ps(samples + 4);
				
				//b0_b1 = { b0 * L, b0 * R, b1 * (x_n1_L - y_n1_L), b1 * (x_n1_R - y_n1_R) }
				_mm_mul_ps(b0_b1, inLR);
				
				//b2_a2 = { b2 * x_n2_L, b2 * x_n2_R, -a2 * y_n2_L, -a2 * y_n2_R }
				_mm_mul_ps(b2_a2, tmp2);
				
				//b0_b1 = { (b0 * L) + (b2 * x_n2_L), (b0 * R) + (b2 * x_n2_R), (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R) }
				_mm_add_ps(b0_b1, b2_a2);
				//tmp = { (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R), xxx, xxx }
				_mm_movehl_ps(tmp, b0_b1);
				
				//x_n2_L = local_x_n1_L;
				//x_n2_R = local_x_n1_R;
				//y_n2_L = local_y_n1_L;
				//y_n2_R = local_y_n1_R;
				_mm_store_ps(samples + 4, local);
				
				//tmp = { outL, outR, xxx, xxx }
				_mm_add_ps(tmp, b0_b1);

				//inLR = { L, R, outL, outR }
				_mm_movelh_ps(inLR, tmp);
				
				//x_n1_L = inL;
				//x_n1_R = inR;
				//y_n1_L = outL;
				//y_n1_R = outR;
				_mm_store_ps(samples, inLR);
				
				//inL = outL;
				//inR = outR;
				inLR = tmp;
			}

			//the final output is the last band's output (or its next band's input)
			//const int iL = (int)inL;
			//const int iR = (int)inR;
			__m128i iLR = _mm_cvtps_epi32(inLR);

			//buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (short)iL));
			//buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (short)iR));
			_mm_packs_epi32(iLR, iLR);
			*((int*)buffer) = _mm_cvtsi128_si32(iLR);

			buffer += 2;
		}

#else

		//arm
		while ((sizeInFrames--)) {
			float *samples = equalizerSamples;

			//since this is a cascade filter, band0's output is band1's input and so on....
			float inL = (float)buffer[0], inR = (float)buffer[1];
			for (int i = 0; i < equalizerActualBandCount; i++, samples += 8) {
				//y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
				//but, since our a2 was negated and b1 = a1, the formula becomes:
				//y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2)
				const float b0 = equalizerCoefs[(i << 3)];
				const float b1_a1 = equalizerCoefs[(i << 3) + 2];
				const float b2 = equalizerCoefs[(i << 3) + 4];
				const float a2 = equalizerCoefs[(i << 3) + 6];

				const float local_x_n1_L = x_n1_L;
				const float local_x_n1_R = x_n1_R;

				const float local_y_n1_L = y_n1_L;
				const float local_y_n1_R = y_n1_R;

				const float outL = (b0 * inL) + (b1_a1 * (local_x_n1_L - local_y_n1_L)) + (b2 * x_n2_L) + (a2 * y_n2_L);
				const float outR = (b0 * inR) + (b1_a1 * (local_x_n1_R - local_y_n1_R)) + (b2 * x_n2_R) + (a2 * y_n2_R);

				x_n2_L = local_x_n1_L;
				x_n2_R = local_x_n1_R;
				y_n2_L = local_y_n1_L;
				y_n2_R = local_y_n1_R;

				x_n1_L = inL;
				x_n1_R = inR;
				y_n1_L = outL;
				y_n1_R = outR;

				inL = outL;
				inR = outR;
			}

			//the final output is the last band's output (or its next band's input)
			const int iL = (int)inL;
			const int iR = (int)inR;
			buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (short)iL));
			buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (short)iR));
			buffer += 2;
		}

#endif
	} else {
		//no optimizations for mono!
	}
#undef x_n1_L
#undef x_n1_R
#undef y_n1_L
#undef y_n1_R
#undef x_n2_L
#undef x_n2_R
#undef y_n2_L
#undef y_n2_R
}

void JNICALL enableEqualizer(JNIEnv* env, jclass clazz, int enabled) {
	equalizerEnabled = enabled;
}

int JNICALL isEqualizerEnabled(JNIEnv* env, jclass clazz) {
	return equalizerEnabled;
}

void JNICALL setEqualizerBandLevel(JNIEnv* env, jclass clazz, unsigned int band, int level) {
	if (band >= BAND_COUNT)
		return;

	equalizerGainInDB[band] = (float)((level <= -DB_RANGE) ? -DB_RANGE : ((level >= DB_RANGE) ? DB_RANGE : level)) / 100.0f; //level is given in millibels

	//both the previous and the next bands depend on this one (if they exist)
	if (band > 0)
		computeFilter(band - 1);
	computeFilter(band);
	if (band < (equalizerActualBandCount - 1))
		computeFilter(band + 1);
}

void JNICALL setEqualizerBandLevels(JNIEnv* env, jclass clazz, jshortArray jlevels) {
	short* levels = (short*)env->GetPrimitiveArrayCritical(jlevels, 0);
	if (!levels)
		return;

	for (int i = 0; i < BAND_COUNT; i++)
		equalizerGainInDB[i] = (float)((levels[i] <= -DB_RANGE) ? -DB_RANGE : ((levels[i] >= DB_RANGE) ? DB_RANGE : levels[i])) / 100.0f; //level is given in millibels

	env->ReleasePrimitiveArrayCritical(jlevels, levels, JNI_ABORT);

	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);
}
