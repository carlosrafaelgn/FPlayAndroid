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

#include "EffectsImplMacros.h"

#define DB_RANGE 1500 //+-15dB (in millibels)
#define BAND_COUNT 10
#define COEF_SET_COUNT 8

#define EQUALIZER_ENABLED 1
#define BASSBOOST_ENABLED 2
#define VIRTUALIZER_ENABLED 4

#define BASSBOOST_BAND_COUNT 3 //(31.25 Hz, 62.5 Hz and 125 Hz)

static unsigned int bassBoostStrength, virtualizerStrength;
static float equalizerGainRecoveryPerSecondInDB, equalizerGainInDB[BAND_COUNT];
static EFFECTPROC effectProc;

unsigned int effectsEnabled, equalizerMaxBandCount, effectsGainEnabled;
int effectsFramesBeforeRecoveringGain, effectsTemp[4] __attribute__((aligned(16)));
float effectsGainRecoveryOne[4] __attribute__((aligned(16))) = { 1.0f, 1.0f, 0.0f, 0.0f },
effectsGainRecoveryPerFrame[4] __attribute__((aligned(16))),
effectsGainClip[4] __attribute__((aligned(16))),
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
	extern void processEffectsNeon(short* buffer, unsigned int sizeInFrames);
#endif

#include "Filter.h"

void updateEffectProc();

void resetEqualizer() {
	memset(effectsTemp, 0, 4 * sizeof(int));
	memset(equalizerSamples, 0, 2 * 4 * BAND_COUNT * sizeof(float));
}

void destroyVirtualizer() {
}

void resetVirtualizer() {
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

	effectsGainRecoveryPerFrame[0] = (float)pow(10.0, (double)equalizerGainRecoveryPerSecondInDB / (double)(sampleRate * 20));
	effectsGainRecoveryPerFrame[1] = effectsGainRecoveryPerFrame[0];
	//do not reset the gain between tracks in resetEqualizer() (reset it only if the config actually changes)
	effectsGainClip[0] = 1.0f;
	effectsGainClip[1] = 1.0f;
	effectsGainClip[2] = 0.0f;
	effectsGainClip[3] = 0.0f;
	effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;

	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);

	resetEqualizer();
}

void recomputeVirtualizer() {
	//recompute the filter
}

void virtualizerConfigChanged() {
	if (!(effectsEnabled & VIRTUALIZER_ENABLED))
		return;

	destroyVirtualizer();

	//recreate all internal structures

	//recompute the filter
	recomputeVirtualizer();

	resetVirtualizer();
}

void initializeEffects() {
	effectsEnabled = 0;
	bassBoostStrength = 0;
	virtualizerStrength = 0;
	equalizerMaxBandCount = 0;
	effectsGainEnabled = 1;
	equalizerGainRecoveryPerSecondInDB = 0.5f;
	effectsGainRecoveryPerFrame[0] = 1.0f;
	effectsGainRecoveryPerFrame[1] = 1.0f;
	effectsGainRecoveryPerFrame[2] = 0.0f;
	effectsGainRecoveryPerFrame[3] = 0.0f;
	effectsGainClip[0] = 1.0f;
	effectsGainClip[1] = 1.0f;
	effectsGainClip[2] = 0.0f;
	effectsGainClip[3] = 0.0f;
	effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;

	memset(equalizerGainInDB, 0, BAND_COUNT * sizeof(float));

	resetEqualizer();
	resetVirtualizer();

	updateEffectProc();
}

void processNull(short* buffer, unsigned int sizeInFrames) {
}

void processEqualizer(short* buffer, unsigned int sizeInFrames) {
	effectsFramesBeforeRecoveringGain -= sizeInFrames;

#ifdef FPLAY_X86
	__m128 gainClip = _mm_load_ps(effectsGainClip);
	__m128 maxAbsSample, tmp2;
	maxAbsSample = _mm_xor_ps(maxAbsSample, maxAbsSample);
	tmp2 = _mm_xor_ps(tmp2, tmp2);

	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		effectsTemp[0] = (int)buffer[0];
		effectsTemp[1] = (int)buffer[1];
		//inLR = { L, R, xxx, xxx }
		__m128 inLR;
		inLR = _mm_cvtpi32_ps(inLR, *((__m64*)effectsTemp));

		equalizerX86();

		floatToShortX86();
	}

	footerX86();
#else
	float gainClip = effectsGainClip[0];
	float maxAbsSample = 0.0f;

	//no neon support... :(
	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		float inL = (float)buffer[0], inR = (float)buffer[1];

		equalizerPlain();

		floatToShortPlain();
	}

	footerPlain();
#endif
}

void processEffects(short* buffer, unsigned int sizeInFrames) {
	effectsFramesBeforeRecoveringGain -= sizeInFrames;

#ifdef FPLAY_X86
	__m128 gainClip = _mm_load_ps(effectsGainClip);
	__m128 maxAbsSample, tmp2;
	maxAbsSample = _mm_xor_ps(maxAbsSample, maxAbsSample);
	tmp2 = _mm_xor_ps(tmp2, tmp2);

	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		effectsTemp[0] = (int)buffer[0];
		effectsTemp[1] = (int)buffer[1];
		//inLR = { L, R, xxx, xxx }
		__m128 inLR;
		inLR = _mm_cvtpi32_ps(inLR, *((__m64*)effectsTemp));

		if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
			equalizerX86();
		}

		//*** process virtualizer (inLR)

		floatToShortX86();
	}

	footerX86();
#else
	float gainClip = effectsGainClip[0];
	float maxAbsSample = 0.0f;

	//no neon support... :(
	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		float inL = (float)buffer[0], inR = (float)buffer[1];

		if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
			equalizerPlain();
		}

		//*** process virtualizer (inLR)

		floatToShortPlain();
	}

	footerPlain();
#endif
}

void JNICALL enableEqualizer(JNIEnv* env, jclass clazz, int enabled) {
	if (enabled)
		effectsEnabled |= EQUALIZER_ENABLED;
	else
		effectsEnabled &= ~EQUALIZER_ENABLED;

	//recompute the filter if the bass boost is enabled
	if ((effectsEnabled & BASSBOOST_ENABLED)) {
		for (int i = 0; i < BAND_COUNT; i++)
			computeFilter(i);
	}

	updateEffectProc();
}

int JNICALL isEqualizerEnabled(JNIEnv* env, jclass clazz) {
	return (effectsEnabled & EQUALIZER_ENABLED);
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
		effectsEnabled |= BASSBOOST_ENABLED;
	else
		effectsEnabled &= ~BASSBOOST_ENABLED;

	//recompute the entire filter (whether the bass boost is enabled or not)
	for (int i = 0; i < BAND_COUNT; i++)
		computeFilter(i);

	updateEffectProc();
}

int JNICALL isBassBoostEnabled(JNIEnv* env, jclass clazz) {
	return ((effectsEnabled & BASSBOOST_ENABLED) >> 1);
}

void JNICALL setBassBoostStrength(JNIEnv* env, jclass clazz, int strength) {
	bassBoostStrength = ((strength <= 0) ? 0 : ((strength >= 1000) ? 1000 : strength));

	//recompute the filter if the bass boost is enabled
	if ((effectsEnabled & BASSBOOST_ENABLED)) {
		for (int i = 0; i < BAND_COUNT; i++)
			computeFilter(i);
	}
}

int JNICALL getBassBoostRoundedStrength(JNIEnv* env, jclass clazz) {
	return bassBoostStrength;
}

void JNICALL enableVirtualizer(JNIEnv* env, jclass clazz, int enabled) {
	if (enabled)
		effectsEnabled |= VIRTUALIZER_ENABLED;
	else
		effectsEnabled &= ~VIRTUALIZER_ENABLED;

	//recreate the filter if the virtualizer is enabled
	if ((effectsEnabled & VIRTUALIZER_ENABLED))
		virtualizerConfigChanged();
	else
		destroyVirtualizer();

	updateEffectProc();
}

int JNICALL isVirtualizerEnabled(JNIEnv* env, jclass clazz) {
	return ((effectsEnabled & VIRTUALIZER_ENABLED) >> 2);
}

void JNICALL setVirtualizerStrength(JNIEnv* env, jclass clazz, int strength) {
	virtualizerStrength = ((strength <= 0) ? 0 : ((strength >= 1000) ? 1000 : strength));

	//recompute the filter if the virtualizer is enabled
	if ((effectsEnabled & VIRTUALIZER_ENABLED))
		recomputeVirtualizer();
}

int JNICALL getVirtualizerRoundedStrength(JNIEnv* env, jclass clazz) {
	return virtualizerStrength;
}

void updateEffectProc() {
#ifdef FPLAY_X86
	if ((effectsEnabled & VIRTUALIZER_ENABLED)) {
		effectProc = processEffects;
	} else if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
		effectProc = processEqualizer;
	} else {
		effectProc = processNull;
	}
#else
	if (neonMode) {
		if ((effectsEnabled & VIRTUALIZER_ENABLED)) {
			effectProc = processEffectsNeon;
		} else if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
			effectProc = processEqualizerNeon;
		} else {
			effectProc = processNull;
		}
	} else {
		if ((effectsEnabled & VIRTUALIZER_ENABLED)) {
			effectProc = processEffects;
		} else if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
			effectProc = processEqualizer;
		} else {
			effectProc = processNull;
		}
	}
#endif
}
