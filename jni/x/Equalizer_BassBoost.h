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
#define DB_RANGE 1500 //+-15dB (in millibels)
#define BAND_COUNT 10
#define COEF_SET_COUNT 8

#define ENABLE_EQUALIZER 1
#define ENABLE_BASSBOOST 2
#define BASSBOOST_BAND_COUNT 3 //(31.25 Hz, 62.5 Hz and 125 Hz)

static unsigned int equalizerEnabled, bassBoostStrength;
static float equalizerGainRecoveryPerSecondInDB, equalizerGainInDB[BAND_COUNT];

unsigned int equalizerMaxBandCount;
int equalizerFramesBeforeRecoveringGain, equalizerTemp[4] __attribute__((aligned(16)));
float equalizerGainRecoveryOne[4] __attribute__((aligned(16))) = { 1.0f, 1.0f, 0.0f, 0.0f },
equalizerGainRecoveryPerFrame[4] __attribute__((aligned(16))),
equalizerGainClip[4] __attribute__((aligned(16))),
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

#ifdef FPLAY_X86
	#include <pmmintrin.h>
	//https://software.intel.com/sites/landingpage/IntrinsicsGuide/
#else
	extern void processEqualizerNeon(short* buffer, unsigned int sizeInFrames);
#endif

#include "Filter.h"

void resetEqualizer() {
	equalizerGainClip[0] = 1.0f;
	equalizerGainClip[1] = 1.0f;
	equalizerGainClip[2] = 0.0f;
	equalizerGainClip[3] = 0.0f;
	equalizerFramesBeforeRecoveringGain = 0x7FFFFFFF;
	memset(equalizerTemp, 0, 4 * sizeof(int));
	memset(equalizerSamples, 0, 2 * 4 * BAND_COUNT * sizeof(float));
}

void equalizerConfigChanged() {
	if (sampleRate > (2 * 16000))
		equalizerMaxBandCount = 10;
	else if (sampleRate > (2 * 8000))
		equalizerMaxBandCount = 9;
	else if (sampleRate > (2 * 4000))
		equalizerMaxBandCount = 8;
	else if (sampleRate > (2 * 2000))
		equalizerMaxBandCount = 7;
	else
		equalizerMaxBandCount = 6; //Android's minimum allowed sample rate is 4000 Hz

	equalizerGainRecoveryPerFrame[0] = (float)pow(10.0, (double)equalizerGainRecoveryPerSecondInDB / (double)(sampleRate * 20));
	equalizerGainRecoveryPerFrame[1] = equalizerGainRecoveryPerFrame[0];

	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);

	resetEqualizer();
}

void initializeEqualizer() {
	equalizerEnabled = 0;
	bassBoostStrength = 0;
	equalizerMaxBandCount = 0;
	equalizerGainRecoveryPerSecondInDB = 0.5f;
	equalizerGainRecoveryPerFrame[0] = 1.0f;
	equalizerGainRecoveryPerFrame[1] = 1.0f;
	equalizerGainRecoveryPerFrame[2] = 0.0f;
	equalizerGainRecoveryPerFrame[3] = 0.0f;

	memset(equalizerGainInDB, 0, BAND_COUNT * sizeof(float));

	resetEqualizer();
}

void processEqualizer(short* buffer, unsigned int sizeInFrames) {
	equalizerFramesBeforeRecoveringGain -= sizeInFrames;

#define x_n1_L samples[0]
#define x_n1_R samples[1]
#define y_n1_L samples[2]
#define y_n1_R samples[3]
#define x_n2_L samples[4]
#define x_n2_R samples[5]
#define y_n2_L samples[6]
#define y_n2_R samples[7]
	if (channelCount == 2) {
#ifdef FPLAY_X86
		__m128 gainClip = _mm_load_ps(equalizerGainClip);
		__m128 maxAbsSample, tmp2;
		maxAbsSample = _mm_xor_ps(maxAbsSample, maxAbsSample);
		tmp2 = _mm_xor_ps(tmp2, tmp2);

		while ((sizeInFrames--)) {
			float *samples = equalizerSamples;

			equalizerTemp[0] = (int)buffer[0];
			equalizerTemp[1] = (int)buffer[1];
			//inLR = { L, R, xxx, xxx }
			__m128 inLR;
			inLR = _mm_cvtpi32_ps(inLR, *((__m64*)equalizerTemp));

			//since this is a cascade filter, band0's output is band1's input and so on....
			for (int i = 0; i < equalizerMaxBandCount; i++, samples += 8) {
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
				tmp2 = _mm_movehl_ps(tmp2, local);

				//tmp = { x_n1_L - y_n1_L, x_n1_R - y_n1_R, xxx, xxx }
				tmp = _mm_sub_ps(tmp, tmp2);

				//inLR = { L, R, x_n1_L - y_n1_L, x_n1_R - y_n1_R }
				inLR = _mm_movelh_ps(inLR, tmp);

				//tmp2 = { x_n2_L, x_n2_R, y_n2_L, y_n2_R }
				tmp2 = _mm_load_ps(samples + 4);

				//b0_b1 = { b0 * L, b0 * R, b1 * (x_n1_L - y_n1_L), b1 * (x_n1_R - y_n1_R) }
				b0_b1 = _mm_mul_ps(b0_b1, inLR);

				//b2_a2 = { b2 * x_n2_L, b2 * x_n2_R, -a2 * y_n2_L, -a2 * y_n2_R }
				b2_a2 = _mm_mul_ps(b2_a2, tmp2);

				//b0_b1 = { (b0 * L) + (b2 * x_n2_L), (b0 * R) + (b2 * x_n2_R), (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R) }
				b0_b1 = _mm_add_ps(b0_b1, b2_a2);
				//tmp = { (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R), xxx, xxx }
				tmp = _mm_movehl_ps(tmp, b0_b1);

				//x_n2_L = local_x_n1_L;
				//x_n2_R = local_x_n1_R;
				//y_n2_L = local_y_n1_L;
				//y_n2_R = local_y_n1_R;
				_mm_store_ps(samples + 4, local);

				//tmp = { outL, outR, xxx, xxx }
				tmp = _mm_add_ps(tmp, b0_b1);

				//inLR = { L, R, outL, outR }
				inLR = _mm_movelh_ps(inLR, tmp);

				//x_n1_L = inL;
				//x_n1_R = inR;
				//y_n1_L = outL;
				//y_n1_R = outR;
				_mm_store_ps(samples, inLR);

				//inL = outL;
				//inR = outR;
				inLR = tmp;
			}

			//inL *= gainClip;
			//inR *= gainClip;
			inLR = _mm_mul_ps(inLR, gainClip);

			if (equalizerFramesBeforeRecoveringGain <= 0) {
				//gainClip *= equalizerGainRecoveryPerFrame[0];
				//if (gainClip > 1.0f)
				//	gainClip = 1.0f;
				gainClip = _mm_mul_ps(gainClip, *((__m128*)equalizerGainRecoveryPerFrame));
				gainClip = _mm_min_ps(gainClip, *((__m128*)equalizerGainRecoveryOne));
			}

			//instead of doing the classic a & 0x7FFFFFFF in order to achieve the abs value,
			//I decided to do this (which does not require external memory loads)
			//maxAbsSample = max(maxAbsSample, max(0 - inLR, inLR))
			__m128 zero;
			zero = _mm_xor_ps(zero, zero);
			maxAbsSample = _mm_max_ps(maxAbsSample, _mm_max_ps(_mm_sub_ps(zero, inLR), inLR));

			//the final output is the last band's output (or its next band's input)
			//const int iL = (int)inL;
			//const int iR = (int)inR;
			__m128i iLR = _mm_cvtps_epi32(inLR);

			//buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (short)iL));
			//buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (short)iR));
			iLR = _mm_packs_epi32(iLR, iLR);
			*((int*)buffer) = _mm_cvtsi128_si32(iLR);

			buffer += 2;
		}

		_mm_store_ps((float*)equalizerTemp, maxAbsSample);
		equalizerTemp[2] = 0;
		equalizerTemp[3] = 0;
		const float maxAbsSampleMono = ((((float*)equalizerTemp)[0] > ((float*)equalizerTemp)[1]) ? ((float*)equalizerTemp)[0] : ((float*)equalizerTemp)[1]);
		float gainClipMono;
		_mm_store_ss(&gainClipMono, gainClip);
		if (maxAbsSampleMono > 32768.0f) {
			const float newGainClip = 33000.0f / maxAbsSampleMono;
			if (newGainClip < gainClipMono) {
				equalizerGainClip[0] = newGainClip;
				equalizerGainClip[1] = newGainClip;
			} else {
				equalizerGainClip[0] = gainClipMono;
				equalizerGainClip[1] = gainClipMono;
			}
			equalizerFramesBeforeRecoveringGain = sampleRate << 2; //wait some time before starting to recover the gain
		} else if (equalizerFramesBeforeRecoveringGain <= 0) {
			equalizerGainClip[0] = gainClipMono;
			equalizerGainClip[1] = gainClipMono;
			equalizerFramesBeforeRecoveringGain = ((gainClipMono >= 1.0f) ? 0x7FFFFFFF : 0);
		}
#else
		if (neonMode) {
			processEqualizerNeon(buffer, sizeInFrames);
			return;
		}

		float gainClip = equalizerGainClip[0];
		float maxAbsSample = 0.0f;

		//no neon support... :(
		while ((sizeInFrames--)) {
			float *samples = equalizerSamples;

			//since this is a cascade filter, band0's output is band1's input and so on....
			float inL = (float)buffer[0], inR = (float)buffer[1];
			for (int i = 0; i < equalizerMaxBandCount; i++, samples += 8) {
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

			inL *= gainClip;
			inR *= gainClip;

			if (equalizerFramesBeforeRecoveringGain <= 0) {
				gainClip *= equalizerGainRecoveryPerFrame[0];
				if (gainClip > 1.0f)
					gainClip = 1.0f;
			}

			const float tmpAbsL = abs(inL);
			if (maxAbsSample < tmpAbsL)
				maxAbsSample = tmpAbsL;
			const float tmpAbsR = abs(inR);
			if (maxAbsSample < tmpAbsR)
				maxAbsSample = tmpAbsR;

			//the final output is the last band's output (or its next band's input)
			const int iL = (int)inL;
			const int iR = (int)inR;
			buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (short)iL));
			buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (short)iR));
			buffer += 2;
		}

		if (maxAbsSample > 32768.0f) {
			const float newGainClip = 33000.0f / maxAbsSample;
			if (newGainClip < gainClip) {
				equalizerGainClip[0] = newGainClip;
				equalizerGainClip[1] = newGainClip;
			} else {
				equalizerGainClip[0] = gainClip;
				equalizerGainClip[1] = gainClip;
			}
			equalizerFramesBeforeRecoveringGain = sampleRate << 2; //wait some time before starting to recover the gain
		} else if (equalizerFramesBeforeRecoveringGain <= 0) {
			equalizerGainClip[0] = gainClip;
			equalizerGainClip[1] = gainClip;
			equalizerFramesBeforeRecoveringGain = ((gainClip >= 1.0f) ? 0x7FFFFFFF : 0);
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
	if (enabled)
		equalizerEnabled |= ENABLE_EQUALIZER;
	else
		equalizerEnabled &= ~ENABLE_EQUALIZER;

	//recompute the filter if the bass boost is enabled
	if ((equalizerEnabled & ENABLE_BASSBOOST)) {
		for (int i = 0; i < BAND_COUNT; i++)
			computeFilter(i);
	}
}

int JNICALL isEqualizerEnabled(JNIEnv* env, jclass clazz) {
	return (equalizerEnabled & ENABLE_EQUALIZER);
}

void JNICALL setEqualizerBandLevel(JNIEnv* env, jclass clazz, unsigned int band, int level) {
	if (band >= BAND_COUNT)
		return;

	equalizerGainInDB[band] = (float)((level <= -DB_RANGE) ? -DB_RANGE : ((level >= DB_RANGE) ? DB_RANGE : level)) / 100.0f; //level is given in millibels

	//both the previous and the next bands depend on this one (if they exist)
	if (band > 0)
		computeFilter(band - 1);
	computeFilter(band);
	if (band < (equalizerMaxBandCount - 1))
		computeFilter(band + 1);
}

void JNICALL setEqualizerBandLevels(JNIEnv* env, jclass clazz, jshortArray jlevels) {
	short* const levels = (short*)env->GetPrimitiveArrayCritical(jlevels, 0);
	if (!levels)
		return;

	for (int i = 0; i < BAND_COUNT; i++)
		equalizerGainInDB[i] = (float)((levels[i] <= -DB_RANGE) ? -DB_RANGE : ((levels[i] >= DB_RANGE) ? DB_RANGE : levels[i])) / 100.0f; //level is given in millibels

	env->ReleasePrimitiveArrayCritical(jlevels, levels, JNI_ABORT);

	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);
}

void JNICALL enableBassBoost(JNIEnv* env, jclass clazz, int enabled) {
	if (enabled)
		equalizerEnabled |= ENABLE_BASSBOOST;
	else
		equalizerEnabled &= ~ENABLE_BASSBOOST;

	//recompute the entire filter (whether the bass boost is enabled or not)
	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);
}

int JNICALL isBassBoostEnabled(JNIEnv* env, jclass clazz) {
	return ((equalizerEnabled & ENABLE_BASSBOOST) >> 1);
}

void JNICALL setBassBoostStrength(JNIEnv* env, jclass clazz, int strength) {
	bassBoostStrength = ((strength <= 0) ? 0 : ((strength >= 1000) ? 1000 : strength));

	//recompute the filter if the bass boost is enabled
	if ((equalizerEnabled & ENABLE_BASSBOOST)) {
		for (int i = 0; i < BAND_COUNT; i++)
			computeFilter(i);
	}
}

int JNICALL getBassBoostRoundedStrength(JNIEnv* env, jclass clazz) {
	return bassBoostStrength;
}
