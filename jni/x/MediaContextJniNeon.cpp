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
#include <android/log.h>
#include <arm_neon.h>

#include "EffectsImplMacros.h"

#define EQUALIZER_ENABLED 1
#define BASSBOOST_ENABLED 2
#define VIRTUALIZER_ENABLED 4

extern uint32_t effectsEnabled, equalizerMaxBandCount, effectsGainEnabled, dstSampleRate;
extern int32_t effectsMustReduceGain, effectsFramesBeforeRecoveringGain, effectsTemp[] __attribute__((aligned(16)));
extern float effectsGainRecoveryOne[] __attribute__((aligned(16))),
effectsGainReductionPerFrame[] __attribute__((aligned(16))),
effectsGainRecoveryPerFrame[] __attribute__((aligned(16))),
effectsGainClip[] __attribute__((aligned(16))),
equalizerCoefs[] __attribute__((aligned(16))),
equalizerSamples[] __attribute__((aligned(16)));

//http://infocenter.arm.com/help/index.jsp?topic=/com.arm.doc.dui0491h/CIHJBEFE.html

void processEqualizerNeon(int16_t* srcBuffer, uint32_t sizeInFrames, int16_t* dstBuffer) {
	effectsFramesBeforeRecoveringGain -= sizeInFrames;

	float32x2_t gainClip = vld1_f32(effectsGainClip);
	float32x2_t maxAbsSample = vdup_n_f32(0.0f);

	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		effectsTemp[0] = (int32_t)srcBuffer[0];
		effectsTemp[1] = (int32_t)srcBuffer[1];
		//inLR = { L, R }
		float32x2_t inLR = vcvt_f32_s32(*((int32x2_t*)effectsTemp));

		equalizerNeon();

		floatToShortNeon();
	}

	footerNeon();
}

void processVirtualizerNeon(int16_t* srcBuffer, uint32_t sizeInFrames, int16_t* dstBuffer) {
	effectsFramesBeforeRecoveringGain -= sizeInFrames;

	float32x2_t gainClip = vld1_f32(effectsGainClip);
	float32x2_t maxAbsSample = vdup_n_f32(0.0f);

	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		effectsTemp[0] = (int32_t)srcBuffer[0];
		effectsTemp[1] = (int32_t)srcBuffer[1];
		//inLR = { L, R }
		float32x2_t inLR = vcvt_f32_s32(*((int32x2_t*)effectsTemp));

		virtualizerNeon();

		floatToShortNeon();
	}

	footerNeon();
}

void processEffectsNeon(int16_t* srcBuffer, uint32_t sizeInFrames, int16_t* dstBuffer) {
	effectsFramesBeforeRecoveringGain -= sizeInFrames;

	float32x2_t gainClip = vld1_f32(effectsGainClip);
	float32x2_t maxAbsSample = vdup_n_f32(0.0f);

	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		effectsTemp[0] = (int32_t)srcBuffer[0];
		effectsTemp[1] = (int32_t)srcBuffer[1];
		//inLR = { L, R }
		float32x2_t inLR = vcvt_f32_s32(*((int32x2_t*)effectsTemp));

		equalizerNeon();

		virtualizerNeon();

		floatToShortNeon();
	}

	footerNeon();
}

extern float resampleHermitePhase, resampleHermitePhaseIncrement;
extern float resampleHermiteY[] __attribute__((aligned(16)));
static int32_t resampleHermiteTemp[2] __attribute__((aligned(16)));

uint32_t resampleHermiteNeon(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	float32x2_t y0 = vld1_f32(resampleHermiteY);
	float32x2_t y1 = vld1_f32(resampleHermiteY + 2);
	float32x2_t y2 = vld1_f32(resampleHermiteY + 4);
	float32x2_t y3 = vld1_f32(resampleHermiteY + 6);
	
	while (usedDst < dstSizeInFrames) {
		//4-point hermite interpolation, with its polynom slightly optimized (reduced)
		//(both tension and bias were considered to be 0)
		const float x2 = resampleHermitePhase * resampleHermitePhase;
		const float x_1 = resampleHermitePhase - 1.0f;
		const float _2x = 2.0f * resampleHermitePhase;
		const float32x2_t a = vdup_n_f32((3.0f - _2x) * x2);
		const float32x2_t b = vdup_n_f32((_2x + 1.0f) * x_1 * x_1);
		const float32x2_t c = vdup_n_f32(0.5f * resampleHermitePhase * (resampleHermitePhase + 1.0f) * ((x2 * resampleHermitePhase) - 2.0f));
		const float32x2_t d = vdup_n_f32(0.5f * x_1 * x2);
		int32x2_t outI32 = vcvt_s32_f32(vmla_f32(vmla_f32(vmla_f32(vmul_f32(a, y2), b, y1), c, vsub_f32(y2, y0)), d, vsub_f32(y3, y1)));
		int16x4_t outI16 = vqmovn_s32(vcombine_s32(outI32, outI32));
		*dstBuffer++ = vget_lane_s16(outI16, 0);
		*dstBuffer++ = vget_lane_s16(outI16, 1);
		usedDst++;

		resampleHermitePhase += resampleHermitePhaseIncrement;

		while (resampleHermitePhase >= 1.0f) {
			resampleHermitePhase -= 1.0f;

			usedSrc++;
			srcBuffer += 2;

			if (usedSrc >= srcSizeInFrames) {
				vst1_f32(resampleHermiteY, y0);
				vst1_f32(resampleHermiteY + 2, y1);
				vst1_f32(resampleHermiteY + 4, y2);
				vst1_f32(resampleHermiteY + 6, y3);

				srcFramesUsed = usedSrc;
				return usedDst;
			}

			resampleHermiteTemp[0] = (int32_t)srcBuffer[0];
			resampleHermiteTemp[1] = (int32_t)srcBuffer[1];
			y3 = y2;
			y2 = y1;
			y1 = y0;
			y0 = vcvt_f32_s32(*((int32x2_t*)resampleHermiteTemp));
		}
	}

	vst1_f32(resampleHermiteY, y0);
	vst1_f32(resampleHermiteY + 2, y1);
	vst1_f32(resampleHermiteY + 4, y2);
	vst1_f32(resampleHermiteY + 6, y3);

	srcFramesUsed = usedSrc;
	return usedDst;
}
