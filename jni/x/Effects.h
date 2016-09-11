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
#define BAND_COUNT 5
#define COEF_SET_COUNT 8

#define EQUALIZER_ENABLED 1
#define BASSBOOST_ENABLED 2
#define VIRTUALIZER_ENABLED 4

//https://en.wikipedia.org/wiki/Dynamic_range_compression
//https://en.wikipedia.org/wiki/Dynamic_range_compression#Limiting
//as the article states, brick-wall limiting are harsh and unpleasant.. also... reducing the gain abruptly causes audible clicks!
#define GAIN_REDUCTION_PER_SECOND_DB -40.0 //-40.0dB/s
#define GAIN_RECOVERY_PER_SECOND_DB 0.25 //+0.25dB/s

static uint32_t bassBoostStrength, virtualizerStrength;
static int32_t equalizerGainInMillibels[BAND_COUNT], equalizerActuallyUsedGainInMillibels[BAND_COUNT];
static EFFECTPROC effectProc;
static float* effectsFloatSamplesOriginal;

uint32_t effectsEnabled, equalizerMaxBandCount, effectsGainEnabled;
int32_t effectsFramesBeforeRecoveringGain, effectsMinimumAmountOfFramesToReduce, effectsTemp[4] __attribute__((aligned(16)));
float effectsGainRecoveryOne[4] __attribute__((aligned(16))) = { 1.0f, 1.0f, 0.0f, 0.0f },
	effectsGainReductionPerFrame[4] __attribute__((aligned(16))),
	effectsGainRecoveryPerFrame[4] __attribute__((aligned(16))),
	effectsGainClip[4] __attribute__((aligned(16))),
	equalizerLastBandGain[4] __attribute__((aligned(16)));
EqualizerCoefs equalizerCoefs[BAND_COUNT - 1] __attribute__((aligned(16)));
EqualizerState equalizerStates[BAND_COUNT - 1] __attribute__((aligned(16)));
float *effectsFloatSamples;

#ifdef FPLAY_X86
static const uint32_t effectsAbsSample[4] __attribute__((aligned(16))) = { 0x7FFFFFFF, 0x7FFFFFFF, 0, 0 };
#else
extern void processEffectsNeon(int16_t* buffer, uint32_t sizeInFrames);
#endif

#include "Filter.h"

void updateEffectProc();

int32_t JNICALL getCurrentAutomaticEffectsGainInMB(JNIEnv* env, jclass clazz) {
	return ((effectsGainEnabled && effectsEnabled) ? (int32_t)(2000.0 * log10(effectsGainClip[0])) : 0);
}

void JNICALL enableAutomaticEffectsGain(JNIEnv* env, jclass clazz, uint32_t enabled) {
	::effectsGainEnabled = enabled;

	if (!enabled) {
		effectsGainClip[0] = 1.0f;
		effectsGainClip[1] = 1.0f;
		effectsGainClip[2] = 0.0f;
		effectsGainClip[3] = 0.0f;
		effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
		effectsMinimumAmountOfFramesToReduce = 0;
	}
}	

uint32_t JNICALL isAutomaticEffectsGainEnabled(JNIEnv* env, jclass clazz) {
	return effectsGainEnabled;
}

void resetEqualizer() {
	effectsGainClip[0] = 1.0f;
	effectsGainClip[1] = 1.0f;
	effectsGainClip[2] = 0.0f;
	effectsGainClip[3] = 0.0f;
	effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
	effectsMinimumAmountOfFramesToReduce = 0;

	memset(effectsTemp, 0, 4 * sizeof(int32_t));
	memset(equalizerStates, 0, (BAND_COUNT - 1) * sizeof(EqualizerState));
}

void destroyVirtualizer() {
}

void resetVirtualizer() {
}

void equalizerConfigChanged() {
	//this only happens in two moments: upon initialization and when the sample rate changes (even when the equalizer is not enabled!)

	if (dstSampleRate > (2 * 6000))
		equalizerMaxBandCount = 5;
	else
		equalizerMaxBandCount = 4; //Android's minimum allowed sample rate is 4000 Hz

	effectsGainReductionPerFrame[0] = (float)pow(10.0, GAIN_REDUCTION_PER_SECOND_DB / (double)(dstSampleRate * 20));
	effectsGainReductionPerFrame[1] = effectsGainReductionPerFrame[0];
	effectsGainRecoveryPerFrame[0] = (float)pow(10.0, GAIN_RECOVERY_PER_SECOND_DB / (double)(dstSampleRate * 20));
	effectsGainRecoveryPerFrame[1] = effectsGainRecoveryPerFrame[0];

	for (int32_t i = 0; i < BAND_COUNT; i++)
		computeFilter(i);

	resetEqualizer();
}

void recomputeVirtualizer() {
	//recompute the filter
}

void virtualizerConfigChanged() {
	//this only happens in two moments: upon initialization and when the sample rate changes

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
	equalizerMaxBandCount = BAND_COUNT;
	effectsFloatSamplesOriginal = 0;
	effectsFloatSamples = 0;
	effectsGainEnabled = 1;
	effectsGainReductionPerFrame[0] = 1.0f;
	effectsGainReductionPerFrame[1] = 1.0f;
	effectsGainReductionPerFrame[2] = 0.0f;
	effectsGainReductionPerFrame[3] = 0.0f;
	effectsGainRecoveryPerFrame[0] = 1.0f;
	effectsGainRecoveryPerFrame[1] = 1.0f;
	effectsGainRecoveryPerFrame[2] = 0.0f;
	effectsGainRecoveryPerFrame[3] = 0.0f;
	equalizerLastBandGain[0] = 1.0f;
	equalizerLastBandGain[1] = 1.0f;
	equalizerLastBandGain[2] = 0.0f;
	equalizerLastBandGain[3] = 0.0f;

	memset(equalizerGainInMillibels, 0, BAND_COUNT * sizeof(int32_t));
	memset(equalizerActuallyUsedGainInMillibels, 0, BAND_COUNT * sizeof(int32_t));
	memset(equalizerCoefs, 0, (BAND_COUNT - 1) * sizeof(EqualizerCoefs));

	resetEqualizer();
	resetVirtualizer();

	updateEffectProc();
}

void terminateEffects() {
	if (effectsFloatSamplesOriginal) {
		delete effectsFloatSamplesOriginal;
		effectsFloatSamplesOriginal = 0;
		effectsFloatSamples = 0;
	}
}

void processNull(int16_t* buffer, uint32_t sizeInFrames) {
	//nothing to be done :)
}

void processEffects(int16_t* buffer, uint32_t sizeInFrames) {
	if (effectsMinimumAmountOfFramesToReduce <= 0)
		effectsFramesBeforeRecoveringGain -= sizeInFrames;
	else
		effectsMinimumAmountOfFramesToReduce -= sizeInFrames;

	if (!(effectsEnabled & EQUALIZER_ENABLED)) {
		for (int32_t i = ((sizeInFrames << 1) - 1); i >= 0; i--)
			effectsFloatSamples[i] = (float)buffer[i];
	} else {
		const float lastBandGain = equalizerLastBandGain[0];
		for (int32_t i = ((sizeInFrames << 1) - 1); i >= 0; i--)
			effectsFloatSamples[i] = (float)buffer[i] * lastBandGain;

		//apply each filter in all samples before moving on to the next filter
		for (int32_t band = equalizerMaxBandCount - 2; band >= 0; band--) {
			//we will work with local copies, not with the original pointers
			const EqualizerCoefs* const equalizerCoef = &(equalizerCoefs[band]);
			const float b0 = equalizerCoef->b0L;
			const float b1 = equalizerCoef->b1L;
			const float _a1 = equalizerCoef->_a1L;
			const float b2 = equalizerCoef->b2L;
			const float _a2 = equalizerCoef->_a2L;
			EqualizerState equalizerState = equalizerStates[band];

			float* samples = effectsFloatSamples;

			for (int32_t i = sizeInFrames - 1; i >= 0; i--) {
				const float inL = samples[0];
				const float inR = samples[1];

				const float outL = (b0 * inL) + (b1 * equalizerState.x_n1_L) + (_a1 * equalizerState.y_n1_L) + (b2 * equalizerState.x_n2_L) + (_a2 * equalizerState.y_n2_L);
				const float outR = (b0 * inR) + (b1 * equalizerState.x_n1_R) + (_a1 * equalizerState.y_n1_R) + (b2 * equalizerState.x_n2_R) + (_a2 * equalizerState.y_n2_R);

				equalizerState.x_n2_L = equalizerState.x_n1_L;
				equalizerState.x_n2_R = equalizerState.x_n1_R;
				equalizerState.y_n2_L = equalizerState.y_n1_L;
				equalizerState.y_n2_R = equalizerState.y_n1_R;

				equalizerState.x_n1_L = inL;
				equalizerState.x_n1_R = inR;
				equalizerState.y_n1_L = outL;
				equalizerState.y_n1_R = outR;

				samples[0] = outL;
				samples[1] = outR;

				samples += 2;
			}

			equalizerStates[band] = equalizerState;
		}
	}

	//if ((effectsEnabled & BASSBOOST_ENABLED)) {
	//}

	//if ((effectsEnabled & VIRTUALIZER_ENABLED)) {
	//}

	float gainClip = effectsGainClip[0];
	float maxAbsSample = 0.0f;
	float* floatSamples = effectsFloatSamples;

	while ((sizeInFrames--)) {
		float inL = floatSamples[0] * gainClip;
		float inR = floatSamples[1] * gainClip;
		floatSamples += 2;

		if (effectsMinimumAmountOfFramesToReduce > 0) {
			gainClip *= effectsGainReductionPerFrame[0];
		} else if (effectsFramesBeforeRecoveringGain <= 0) {
			gainClip *= effectsGainRecoveryPerFrame[0];
			if (gainClip > 1.0f)
				gainClip = 1.0f;
		}

		//abs
		const uint32_t tmpAbsL = *((uint32_t*)&inL) & 0x7FFFFFFF;
		if (maxAbsSample < *((float*)&tmpAbsL))
			maxAbsSample = *((float*)&tmpAbsL);
		const uint32_t tmpAbsR = *((uint32_t*)&inL) & 0x7FFFFFFF;;
		if (maxAbsSample < *((float*)&tmpAbsR))
			maxAbsSample = *((float*)&tmpAbsR);

		const int32_t iL = (int32_t)inL;
		const int32_t iR = (int32_t)inR;
		buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (int16_t)iL));
		buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (int16_t)iR));

		buffer += 2;
	}

	if (!effectsGainEnabled) {
		effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
		effectsMinimumAmountOfFramesToReduce = 0;
		return;
	}

	effectsGainClip[0] = gainClip;
	if (maxAbsSample > MAX_ALLOWED_SAMPLE_VALUE) {
		effectsFramesBeforeRecoveringGain = dstSampleRate << 2; // wait some time before starting to recover the gain
		effectsMinimumAmountOfFramesToReduce = (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 3) >> 1;
	} else if (effectsMinimumAmountOfFramesToReduce <= 0) {
		if (effectsGainClip[0] >= 1.0f)
			effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
	}
}

void JNICALL enableEqualizer(JNIEnv* env, jclass clazz, uint32_t enabled) {
	if (enabled)
		effectsEnabled |= EQUALIZER_ENABLED;
	else
		effectsEnabled &= ~EQUALIZER_ENABLED;

	updateEffectProc();
}

uint32_t JNICALL isEqualizerEnabled(JNIEnv* env, jclass clazz) {
	return (effectsEnabled & EQUALIZER_ENABLED);
}

void JNICALL setEqualizerBandLevel(JNIEnv* env, jclass clazz, uint32_t band, int32_t level) {
	if (band >= BAND_COUNT)
		return;

	equalizerGainInMillibels[band] = ((level <= -DB_RANGE) ? -DB_RANGE : ((level >= DB_RANGE) ? DB_RANGE : level));

	for (int32_t i = 0; i < BAND_COUNT - 1; i++) {
		equalizerActuallyUsedGainInMillibels[i] = equalizerGainInMillibels[i] - equalizerGainInMillibels[i + 1];
		computeFilter(i);
	}

	equalizerActuallyUsedGainInMillibels[BAND_COUNT - 1] = equalizerGainInMillibels[BAND_COUNT - 1];
	computeFilter(BAND_COUNT - 1);

	memset(equalizerStates, 0, (BAND_COUNT - 1) * sizeof(EqualizerState));
}

void JNICALL setEqualizerBandLevels(JNIEnv* env, jclass clazz, jshortArray jlevels) {
	int16_t* const levels = (int16_t*)env->GetPrimitiveArrayCritical(jlevels, 0);
	if (!levels)
		return;

	for (int32_t i = 0; i < BAND_COUNT; i++)
		equalizerGainInMillibels[i] = ((levels[i] <= -DB_RANGE) ? -DB_RANGE : ((levels[i] >= DB_RANGE) ? DB_RANGE : levels[i]));

	env->ReleasePrimitiveArrayCritical(jlevels, levels, JNI_ABORT);

	for (int32_t i = 0; i < BAND_COUNT - 1; i++) {
		equalizerActuallyUsedGainInMillibels[i] = equalizerGainInMillibels[i] - equalizerGainInMillibels[i + 1];
		computeFilter(i);
	}

	equalizerActuallyUsedGainInMillibels[BAND_COUNT - 1] = equalizerGainInMillibels[BAND_COUNT - 1];
	computeFilter(BAND_COUNT - 1);

	memset(equalizerStates, 0, (BAND_COUNT - 1) * sizeof(EqualizerState));
}

void JNICALL enableBassBoost(JNIEnv* env, jclass clazz, uint32_t enabled) {
	if (enabled)
		effectsEnabled |= BASSBOOST_ENABLED;
	else
		effectsEnabled &= ~BASSBOOST_ENABLED;

	//recompute the entire filter (whether the bass boost is enabled or not)
	//for (int32_t i = 0; i < BAND_COUNT; i++)
	//	computeFilter(i);

	updateEffectProc();
}

uint32_t JNICALL isBassBoostEnabled(JNIEnv* env, jclass clazz) {
	return ((effectsEnabled & BASSBOOST_ENABLED) >> 1);
}

void JNICALL setBassBoostStrength(JNIEnv* env, jclass clazz, int32_t strength) {
	bassBoostStrength = ((strength <= 0) ? 0 : ((strength >= 1000) ? 1000 : strength));

	//recompute the filter if the bass boost is enabled
	if ((effectsEnabled & BASSBOOST_ENABLED)) {
	}
}

int32_t JNICALL getBassBoostRoundedStrength(JNIEnv* env, jclass clazz) {
	return bassBoostStrength;
}

void JNICALL enableVirtualizer(JNIEnv* env, jclass clazz, int32_t enabled) {
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

uint32_t JNICALL isVirtualizerEnabled(JNIEnv* env, jclass clazz) {
	return ((effectsEnabled & VIRTUALIZER_ENABLED) >> 2);
}

void JNICALL setVirtualizerStrength(JNIEnv* env, jclass clazz, int32_t strength) {
	virtualizerStrength = ((strength <= 0) ? 0 : ((strength >= 1000) ? 1000 : strength));

	//recompute the filter if the virtualizer is enabled
	if ((effectsEnabled & VIRTUALIZER_ENABLED))
		recomputeVirtualizer();
}

int32_t JNICALL getVirtualizerRoundedStrength(JNIEnv* env, jclass clazz) {
	return virtualizerStrength;
}

void updateEffectProc() {
	if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED | VIRTUALIZER_ENABLED))) {
#ifdef FPLAY_X86
		effectProc = processEffects;
#else
		effectProc = (neonMode ? processEffects : processEffects);
#endif
		if (!effectsFloatSamplesOriginal) {
			//MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 2, because audioTrack allows up to MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 2 frames
			effectsFloatSamplesOriginal = new float[4 + (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 2 * 2)];
			//align memory on a 16-byte boundary
			if (((size_t)effectsFloatSamplesOriginal & 15))
				effectsFloatSamples = (float*)((size_t)effectsFloatSamplesOriginal + 16 - ((size_t)effectsFloatSamplesOriginal & 15));
			else
				effectsFloatSamples = effectsFloatSamplesOriginal;
		}
	} else {
		effectProc = processNull;
		if (effectsFloatSamplesOriginal) {
			delete effectsFloatSamplesOriginal;
			effectsFloatSamplesOriginal = 0;
			effectsFloatSamples = 0;
		}
	}
}
