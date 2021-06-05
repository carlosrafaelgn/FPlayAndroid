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

//https://en.wikipedia.org/wiki/Dynamic_range_compression
//https://en.wikipedia.org/wiki/Dynamic_range_compression#Limiting
//as the article states, brick-wall limiting are harsh and unpleasant.. also... reducing the gain abruptly causes audible clicks!
#define GAIN_REDUCTION_PER_SECOND_DB -40.0 //-40.0dB/s
#define GAIN_RECOVERY_PER_SECOND_DB 0.25 //+0.25dB/s

static uint32_t bassBoostStrength, virtualizerStrength;
static int32_t equalizerGainInMillibels[BAND_COUNT];
static EFFECTPROC effectProc;
static float* effectsFloatSamplesOriginal;

uint32_t effectsEnabled, equalizerMaxBandCount, effectsGainEnabled;
int32_t effectsFramesBeforeRecoveringGain,
	effectsMinimumAmountOfFramesToReduce,
	effectsTemp[4] __attribute__((aligned(16))),
	equalizerActuallyUsedGainInMillibels[BAND_COUNT];
float effectsGainRecoveryOne[4] __attribute__((aligned(16))) = { 1.0f, 1.0f, 0.0f, 0.0f },
	effectsGainReductionPerFrame[4] __attribute__((aligned(16))),
	effectsGainRecoveryPerFrame[4] __attribute__((aligned(16))),
	effectsGainClip[4] __attribute__((aligned(16))),
	equalizerLastBandGain[4] __attribute__((aligned(16)));
EqualizerCoefs equalizerCoefs[BAND_COUNT - 2] __attribute__((aligned(16)));
EqualizerState equalizerStates[BAND_COUNT - 2] __attribute__((aligned(16)));
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

void resetAutomaticEffectsGain() {
	effectsGainClip[0] = 1.0f;
	effectsGainClip[1] = 1.0f;
	effectsGainClip[2] = 0.0f;
	effectsGainClip[3] = 0.0f;
	effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
	effectsMinimumAmountOfFramesToReduce = 0;
}

uint32_t JNICALL isAutomaticEffectsGainEnabled(JNIEnv* env, jclass clazz) {
	return effectsGainEnabled;
}

void updateEqualizerGains(int32_t bandToReset) {
	if (!(effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED)))
		return;

	if (!(effectsEnabled & EQUALIZER_ENABLED)) {
		//only the bass boost is enabled (set all gains to 0, except for band 2 (0 - 125 Hz))
		//band 0 = pre
		equalizerActuallyUsedGainInMillibels[0] = 0;
		for (int32_t i = 1; i < equalizerMaxBandCount; i++) {
			equalizerActuallyUsedGainInMillibels[i] = ((i == 2) ? bassBoostStrength : 0);
			computeFilter(i, equalizerActuallyUsedGainInMillibels, equalizerLastBandGain, equalizerCoefs);
		}
	} else {
		const int32_t lastBand = equalizerMaxBandCount - 1;
		//band 0 = pre
		equalizerActuallyUsedGainInMillibels[0] = 0;
		for (int32_t i = 1; i < lastBand; i++) {
			//when enabled, add the bass boost to band 2 (0 - 125 Hz)
			equalizerActuallyUsedGainInMillibels[i] = ((i == 2 && (effectsEnabled & BASSBOOST_ENABLED)) ?
														(bassBoostStrength + equalizerGainInMillibels[i] - equalizerGainInMillibels[i + 1]) :
														(equalizerGainInMillibels[i] - equalizerGainInMillibels[i + 1]));
			computeFilter(i, equalizerActuallyUsedGainInMillibels, equalizerLastBandGain, equalizerCoefs);
		}

		//pre amp (band 0) is accounted for in the last band
		equalizerActuallyUsedGainInMillibels[lastBand] = equalizerGainInMillibels[lastBand] + equalizerGainInMillibels[0];
		computeFilter(lastBand, equalizerActuallyUsedGainInMillibels, equalizerLastBandGain, equalizerCoefs);
	}

	//Apparently, resetting only a few bands puts the entire equalizer in an
	//unstable state some times... :/
	//if (bandToReset < 1) //reset all bands
		memset(equalizerStates, 0, (BAND_COUNT - 2) * sizeof(EqualizerState));
	//else if (bandToReset == 1) //reset only the first band
	//	memset(equalizerStates, 0, sizeof(EqualizerState));
	//else //reset the given band + its previous one
	//	memset(equalizerStates + (bandToReset - 2), 0, 2 * sizeof(EqualizerState));
}

void resetEqualizer() {
	memset(effectsTemp, 0, 4 * sizeof(int32_t));
	memset(equalizerStates, 0, (BAND_COUNT - 2) * sizeof(EqualizerState));
}

void destroyVirtualizer() {
}

void resetVirtualizer() {
}

void equalizerConfigChanged() {
	//this only happens in two moments: upon initialization and when the sample rate changes (even when the equalizer is not enabled!)

	if (dstSampleRate > (2 * 6000))
		equalizerMaxBandCount = BAND_COUNT;
	else
		equalizerMaxBandCount = BAND_COUNT - 1; //Android's minimum allowed sample rate is 4000 Hz

	effectsGainReductionPerFrame[0] = (float)pow(10.0, GAIN_REDUCTION_PER_SECOND_DB / (double)(dstSampleRate * 20));
	effectsGainReductionPerFrame[1] = effectsGainReductionPerFrame[0];
	effectsGainRecoveryPerFrame[0] = (float)pow(10.0, GAIN_RECOVERY_PER_SECOND_DB / (double)(dstSampleRate * 20));
	effectsGainRecoveryPerFrame[1] = effectsGainRecoveryPerFrame[0];

	updateEqualizerGains(-1);
	resetAutomaticEffectsGain();
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
	memset(equalizerCoefs, 0, (BAND_COUNT - 2) * sizeof(EqualizerCoefs));

	resetAutomaticEffectsGain();
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
    //a few buggy devices change the audio sink so fast and so repeatedly,
    //that this case indeed happens... :(
	if (!effectsFloatSamples)
		return;

	if (effectsMinimumAmountOfFramesToReduce <= 0)
		effectsFramesBeforeRecoveringGain -= sizeInFrames;
	else
		effectsMinimumAmountOfFramesToReduce -= sizeInFrames;

	if (!(effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED)) || !equalizerActuallyUsedGainInMillibels[BAND_COUNT - 1]) {
		for (int32_t i = ((sizeInFrames << 1) - 1); i >= 0; i--)
			effectsFloatSamples[i] = (float)buffer[i];
	}

	if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
		if (equalizerActuallyUsedGainInMillibels[equalizerMaxBandCount - 1]) {
			const float lastBandGain = equalizerLastBandGain[0];
			for (int32_t i = ((sizeInFrames << 1) - 1); i >= 0; i--)
				effectsFloatSamples[i] = (float)buffer[i] * lastBandGain;
		}

		//apply each filter in all samples before moving on to the next filter (band 0 = pre)
		for (int32_t band = equalizerMaxBandCount - 2; band >= 1; band--) {
			//if this band has no gain at all, we can skip it completely (there is no need to worry about
			//equalizerStates[band - 1] because when any gain is changed, all states are zeroed out in updateEqualizerGains())
			if (!equalizerActuallyUsedGainInMillibels[band])
				continue;

			//we will work with local copies, not with the original pointers
			const EqualizerCoefs* const equalizerCoef = &(equalizerCoefs[band - 1]);
			const float b0 = equalizerCoef->b0L;
			const float b1 = equalizerCoef->b1L;
			const float _a1 = equalizerCoef->_a1L;
			const float b2 = equalizerCoef->b2L;
			const float _a2 = equalizerCoef->_a2L;
			EqualizerState equalizerState = equalizerStates[band - 1];

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

			equalizerStates[band - 1] = equalizerState;
		}
	}

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
		effectsFramesBeforeRecoveringGain = dstSampleRate << 2; //wait some time before starting to recover the gain
		effectsMinimumAmountOfFramesToReduce = (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 3) >> 1;
	} else if (effectsMinimumAmountOfFramesToReduce <= 0) {
		if (effectsGainClip[0] >= 1.0f)
			effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
	}
}

void JNICALL enableEqualizer(JNIEnv* env, jclass clazz, uint32_t enabled) {
	const uint32_t oldEffects = effectsEnabled;
	if (enabled)
		effectsEnabled |= EQUALIZER_ENABLED;
	else
		effectsEnabled &= ~EQUALIZER_ENABLED;

	if (!oldEffects && effectsEnabled)
		resetAutomaticEffectsGain();

	updateEqualizerGains(-1);
	updateEffectProc();
}

uint32_t JNICALL isEqualizerEnabled(JNIEnv* env, jclass clazz) {
	return (effectsEnabled & EQUALIZER_ENABLED);
}

void JNICALL setEqualizerBandLevel(JNIEnv* env, jclass clazz, uint32_t band, int32_t level) {
	if (band >= BAND_COUNT)
		return;

	equalizerGainInMillibels[band] = ((level <= -DB_RANGE) ? -DB_RANGE : ((level >= DB_RANGE) ? DB_RANGE : level));

	if ((effectsEnabled & EQUALIZER_ENABLED))
		updateEqualizerGains(band);
}

void JNICALL setEqualizerBandLevels(JNIEnv* env, jclass clazz, jshortArray jlevels) {
	int16_t* const levels = (int16_t*)env->GetPrimitiveArrayCritical(jlevels, 0);
	if (!levels)
		return;

	for (int32_t i = 0; i < BAND_COUNT; i++)
		equalizerGainInMillibels[i] = ((levels[i] <= -DB_RANGE) ? -DB_RANGE : ((levels[i] >= DB_RANGE) ? DB_RANGE : levels[i]));

	env->ReleasePrimitiveArrayCritical(jlevels, levels, JNI_ABORT);

	if ((effectsEnabled & EQUALIZER_ENABLED))
		updateEqualizerGains(-1);
}

void JNICALL getEqualizerFrequencyResponse(JNIEnv* env, jclass clazz, int32_t bassBoostStrength, jshortArray jlevels, int32_t frequencyCount, jdoubleArray jfrequencies, jdoubleArray jgains) {
	int16_t* const levels = (int16_t*)env->GetPrimitiveArrayCritical(jlevels, 0);
	if (!levels)
		return;

	int32_t equalizerGainInMillibels[BAND_COUNT], equalizerActuallyUsedGainInMillibels[BAND_COUNT];
	float equalizerLastBandGain[4];
	EqualizerCoefs equalizerCoefs[BAND_COUNT - 2];

	bassBoostStrength = ((bassBoostStrength <= 0) ? 0 : ((bassBoostStrength >= 1000) ? 1000 : bassBoostStrength));

	for (int32_t i = 0; i < BAND_COUNT; i++)
		equalizerGainInMillibels[i] = ((levels[i] <= -DB_RANGE) ? -DB_RANGE : ((levels[i] >= DB_RANGE) ? DB_RANGE : levels[i]));

	env->ReleasePrimitiveArrayCritical(jlevels, levels, JNI_ABORT);

	const int32_t lastBand = equalizerMaxBandCount - 1;
	//band 0 = pre
	equalizerActuallyUsedGainInMillibels[0] = 0;
	for (int32_t i = 1; i < lastBand; i++) {
		//when enabled, add the bass boost to band 2 (0 - 125 Hz)
		equalizerActuallyUsedGainInMillibels[i] = ((i == 2) ?
													(bassBoostStrength + equalizerGainInMillibels[i] - equalizerGainInMillibels[i + 1]) :
													(equalizerGainInMillibels[i] - equalizerGainInMillibels[i + 1]));
		computeFilter(i, equalizerActuallyUsedGainInMillibels, equalizerLastBandGain, equalizerCoefs);
	}

	//pre amp (band 0) is accounted for in the last band
	equalizerActuallyUsedGainInMillibels[lastBand] = equalizerGainInMillibels[lastBand] + equalizerGainInMillibels[0];
	computeFilter(lastBand, equalizerActuallyUsedGainInMillibels, equalizerLastBandGain, equalizerCoefs);

	double* const frequencies = (double*)env->GetPrimitiveArrayCritical(jfrequencies, 0);
	double* const gains = (double*)env->GetPrimitiveArrayCritical(jgains, 0);
	if (!frequencies || !gains) {
		if (frequencies)
			env->ReleasePrimitiveArrayCritical(jfrequencies, frequencies, JNI_ABORT);
		if (gains)
			env->ReleasePrimitiveArrayCritical(jgains, gains, JNI_ABORT);
		return;
	}

	const double initialGain = (double)equalizerLastBandGain[0];
	for (int32_t f = frequencyCount - 1; f >= 0; f--)
		gains[f] = initialGain;

	const double PI = 3.1415926535897932384626433832795;
	const double Fs = (double)dstSampleRate;

	for (int32_t f = frequencyCount - 1; f >= 0; f--) {
		const double w0 = 2.0 * PI * frequencies[f] / Fs;
		const double cosw0 = cos(w0);
		const double cos2w0 = cos(2.0 * w0);
		const double sinw0 = sin(w0);
		const double sin2w0 = sin(2.0 * w0);

		for (int32_t band = 1; band < lastBand; band++) {
			const EqualizerCoefs* const equalizerCoef = &(equalizerCoefs[band - 1]);
			const double b0 = equalizerCoef->b0L;
			const double b1 = equalizerCoef->b1L;
			const double b2 = equalizerCoef->b2L;
			const double a1 = -equalizerCoef->_a1L;
			const double a2 = -equalizerCoef->_a2L;
			const double t0 = b0 + (b1 * cosw0) + (b2 * cos2w0);
			const double t1 = -((b1 * sinw0) + (b2 * sin2w0));
			const double t2 = 1.0 + (a1 * cosw0) + (a2 * cos2w0);
			const double t3 = -((a1 * sinw0) + (a2 * sin2w0));
			gains[f] *= sqrt(((t0 * t0) + (t1 * t1)) / ((t2 * t2) + (t3 * t3)));
		}
	}

	env->ReleasePrimitiveArrayCritical(jfrequencies, frequencies, JNI_ABORT);

	for (int32_t f = frequencyCount - 1; f >= 0; f--)
		gains[f] = 20.0 * log10(gains[f]);

	env->ReleasePrimitiveArrayCritical(jgains, gains, 0);
}

void JNICALL enableBassBoost(JNIEnv* env, jclass clazz, uint32_t enabled) {
	const uint32_t oldEffects = effectsEnabled;
	if (enabled)
		effectsEnabled |= BASSBOOST_ENABLED;
	else
		effectsEnabled &= ~BASSBOOST_ENABLED;

	if (!oldEffects && effectsEnabled)
		resetAutomaticEffectsGain();

	updateEqualizerGains(2);
	updateEffectProc();
}

uint32_t JNICALL isBassBoostEnabled(JNIEnv* env, jclass clazz) {
	return ((effectsEnabled & BASSBOOST_ENABLED) >> 1);
}

void JNICALL setBassBoostStrength(JNIEnv* env, jclass clazz, int32_t strength) {
	bassBoostStrength = ((strength <= 0) ? 0 : ((strength >= 1000) ? 1000 : strength));

	if ((effectsEnabled & BASSBOOST_ENABLED))
		updateEqualizerGains(2);
}

int32_t JNICALL getBassBoostRoundedStrength(JNIEnv* env, jclass clazz) {
	return bassBoostStrength;
}

void JNICALL enableVirtualizer(JNIEnv* env, jclass clazz, int32_t enabled) {
	const uint32_t oldEffects = effectsEnabled;
	if (enabled)
		effectsEnabled |= VIRTUALIZER_ENABLED;
	else
		effectsEnabled &= ~VIRTUALIZER_ENABLED;

	if (!oldEffects && effectsEnabled)
		resetAutomaticEffectsGain();

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
		effectProc = (neonMode ? processEffectsNeon : processEffects);
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
