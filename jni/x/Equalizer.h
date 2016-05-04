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

static unsigned int equalizerEnabled, equalizerCoefSet, equalizerActualBandCount;
static float equalizerGainInDB[BAND_COUNT], equalizerB0[BAND_COUNT], equalizerB1_A1[BAND_COUNT], equalizerB2[BAND_COUNT], equalizerA2[BAND_COUNT],
equalizerSamples[2 * 4 * BAND_COUNT];

#include "Filter.h"

void resetEqualizer() {
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
#define x_n2_L samples[2]
#define x_n2_R samples[3]
#define y_n1_L samples[4]
#define y_n1_R samples[5]
#define y_n2_L samples[6]
#define y_n2_R samples[7]
	if (channelCount == 2) {
		while ((sizeInFrames--)) {
			float *samples = equalizerSamples;

			//since this is a cascade filter, band0's output is band1's input and so on....
			float inL = (float)buffer[0], inR = (float)buffer[1];
			for (int i = 0; i < equalizerActualBandCount; i++, samples += 8) {
				//y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
				const float b0 = equalizerB0[i];
				const float b1_a1 = equalizerB1_A1[i];
				const float b2 = equalizerB2[i];
				const float a2 = equalizerA2[i];

				const float local_x_n1_L = x_n1_L;
				const float local_x_n1_R = x_n1_R;

				const float local_y_n1_L = y_n1_L;
				const float local_y_n1_R = y_n1_R;

				const float outL = (b0 * inL) + (b1_a1 * local_x_n1_L) + (b2 * x_n2_L) - (b1_a1 * local_y_n1_L) - (a2 * y_n2_L);
				const float outR = (b0 * inR) + (b1_a1 * local_x_n1_R) + (b2 * x_n2_R) - (b1_a1 * local_y_n1_R) - (a2 * y_n2_R);

				x_n2_L = local_x_n1_L;
				x_n2_R = local_x_n1_R;

				x_n1_L = inL;
				x_n1_R = inR;

				y_n2_L = local_y_n1_L;
				y_n2_R = local_y_n1_R;

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
	} else {

	}
#undef x_n1_L
#undef x_n2_L
#undef y_n1_L
#undef y_n2_L
#undef x_n1_R
#undef x_n2_R
#undef y_n1_R
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
