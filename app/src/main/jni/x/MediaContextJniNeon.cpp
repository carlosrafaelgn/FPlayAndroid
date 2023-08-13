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
#include <string.h>
#include <arm_neon.h>

#include "EffectsImplMacros.h"

extern uint32_t effectsEnabled, equalizerMaxBandCount, effectsGainEnabled, dstSampleRate;
extern int32_t effectsFramesBeforeRecoveringGain,
	effectsMinimumAmountOfFramesToReduce,
	effectsTemp[] __attribute__((aligned(16))),
	equalizerActuallyUsedGainInMillibels[];
extern float effectsGainRecoveryOne[] __attribute__((aligned(16))),
	effectsGainReductionPerFrame[] __attribute__((aligned(16))),
	effectsGainRecoveryPerFrame[] __attribute__((aligned(16))),
	effectsGainClip[] __attribute__((aligned(16))),
	equalizerLastBandGain[] __attribute__((aligned(16)));
extern EqualizerCoefs equalizerCoefs[] __attribute__((aligned(16)));
extern EqualizerState equalizerStates[] __attribute__((aligned(16)));
extern float *effectsFloatSamples;

//http://infocenter.arm.com/help/index.jsp?topic=/com.arm.doc.dui0491h/CIHJBEFE.html

void processEffectsNeon(int16_t* buffer, uint32_t sizeInFrames) {
	//a few buggy devices change the audio sink so fast and so repeatedly,
    //that this case indeed happens... :(
	if (!effectsFloatSamples)
		return;

	if (effectsMinimumAmountOfFramesToReduce <= 0)
		effectsFramesBeforeRecoveringGain -= sizeInFrames;
	else
		effectsMinimumAmountOfFramesToReduce -= sizeInFrames;

	if (!(effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED)) || !equalizerActuallyUsedGainInMillibels[BAND_COUNT - 1]) {
		const uint32_t sizeInShortsEven = (sizeInFrames & ~1) << 1; //each frame has 2 shorts!
		for (int32_t i = 0; i < sizeInShortsEven; i += 4) { //this loop processes 4 shorts (or 2 stereo frames - L R L R) at a time
			const int16x4_t bufferLRLRs16 = vld1_s16(buffer + i);
			const int32x4_t bufferLRLRs32 = vmovl_s16(bufferLRLRs16);
			vst1q_f32(effectsFloatSamples + i, vcvtq_f32_s32(bufferLRLRs32));
		}
		if ((sizeInFrames & 1)) {
			//last frame (there was an odd number of frames)
			effectsFloatSamples[(sizeInFrames << 1) - 2] = (float)buffer[(sizeInFrames << 1) - 2];
			effectsFloatSamples[(sizeInFrames << 1) - 1] = (float)buffer[(sizeInFrames << 1) - 1];
		}
	}

	if ((effectsEnabled & (EQUALIZER_ENABLED | BASSBOOST_ENABLED))) {
		if (equalizerActuallyUsedGainInMillibels[BAND_COUNT - 1]) {
			const float32x4_t lastBandGain = vdupq_n_f32(equalizerLastBandGain[0]);
			const uint32_t sizeInShortsEven = (sizeInFrames & ~1) << 1; //each frame has 2 shorts!
			for (int32_t i = 0; i < sizeInShortsEven; i += 4) { //this loop processes 4 shorts (or 2 stereo frames - L R L R) at a time
				const int16x4_t bufferLRLRs16 = vld1_s16(buffer + i);
				const int32x4_t bufferLRLRs32 = vmovl_s16(bufferLRLRs16);
				vst1q_f32(effectsFloatSamples + i, vmulq_f32(vcvtq_f32_s32(bufferLRLRs32), lastBandGain));
			}
			if ((sizeInFrames & 1)) {
				//last frame (there was an odd number of frames)
				effectsFloatSamples[(sizeInFrames << 1) - 2] = (float)buffer[(sizeInFrames << 1) - 2] * equalizerLastBandGain[0];
				effectsFloatSamples[(sizeInFrames << 1) - 1] = (float)buffer[(sizeInFrames << 1) - 1] * equalizerLastBandGain[0];
			}
		}

		//apply each filter in all samples before moving on to the next filter (band 0 = pre)
		for (int32_t band = equalizerMaxBandCount - 2; band >= 1; band--) {
			//if this band has no gain at all, we can skip it completely (there is no need to worry about
			//equalizerStates[band - 1] because when any gain is changed, all states are zeroed out in updateGains())
			if (!equalizerActuallyUsedGainInMillibels[band])
				continue;

			//we will work with local copies, not with the original pointers
			const EqualizerCoefs* const equalizerCoef = &(equalizerCoefs[band - 1]);
			const float32x2_t b0 = vld1_f32(&(equalizerCoef->b0L));
			const float32x4_t b1_a1 = vld1q_f32(&(equalizerCoef->b1L));
			const float32x4_t b2_a2 = vld1q_f32(&(equalizerCoef->b2L));

			EqualizerState* const equalizerState = &(equalizerStates[band - 1]);
			float32x4_t x_n1_y_n1 = vld1q_f32(&(equalizerState->x_n1_L));
			float32x4_t x_n2_y_n2 = vld1q_f32(&(equalizerState->x_n2_L));

			float* samples = effectsFloatSamples;

			for (int32_t i = sizeInFrames - 1; i >= 0; i--) {
				// { samples[0], samples[1] }
				const float32x2_t inLR = vld1_f32(samples);

				// { b1 + b2 L, b1 + b2 R, _a1 + _a2 L, _a1 + _a2 R }
				const float32x4_t tmp = vmlaq_f32(vmulq_f32(x_n1_y_n1, b1_a1), x_n2_y_n2, b2_a2);

				x_n2_y_n2 = x_n1_y_n1;

				// { b0 + b1 + b2 + _a1 + _a2 L, b0 + b1 + b2 + _a1 + _a2 R }
				const float32x2_t outLR = vadd_f32(vmla_f32(vget_low_f32(tmp), inLR, b0), vget_high_f32(tmp));

				// { samples[0], samples[1], outL, outR }
				x_n1_y_n1 = vcombine_f32(inLR, outLR);

				vst1_f32(samples, outLR);

				samples += 2;
			}

			vst1q_f32(&(equalizerState->x_n1_L), x_n1_y_n1);
			vst1q_f32(&(equalizerState->x_n2_L), x_n2_y_n2);
		}
	}

	//if ((effectsEnabled & VIRTUALIZER_ENABLED)) {
	//}

	float32x2_t gainClip = vld1_f32(effectsGainClip);
	float32x2_t maxAbsSample = vdup_n_f32(0.0f);
	const float32x2_t one = vld1_f32(effectsGainRecoveryOne);
	const float32x2_t gainClipMul = vld1_f32((effectsMinimumAmountOfFramesToReduce > 0) ? effectsGainReductionPerFrame : ((effectsFramesBeforeRecoveringGain <= 0) ? effectsGainRecoveryPerFrame : effectsGainRecoveryOne));		
	float* floatSamples = effectsFloatSamples;

	while ((sizeInFrames--)) {
		const float32x2_t inLR = vmul_f32(vld1_f32(floatSamples), gainClip);
		floatSamples += 2;

		//gainClip *= effectsGainReductionPerFrame or effectsGainRecoveryPerFrame or 1.0f;
		//if (gainClip > 1.0f)
		//	gainClip = 1.0f;
		gainClip = vmul_f32(gainClip, gainClipMul);
		gainClip = vmin_f32(gainClip, one);

		maxAbsSample = vmax_f32(maxAbsSample, vabs_f32(inLR));

		//const int32_t iL = (int32_t)inL;
		//const int32_t iR = (int32_t)inR;
		const int32x2_t iLR = vcvt_s32_f32(inLR);

		//buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (int16_t)iL));
		//buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (int16_t)iR));
		const int16x4_t iLRshort = vqmovn_s32(vcombine_s32(iLR, iLR));
		vst1_lane_s32((int32_t*)buffer, vreinterpret_s32_s16(iLRshort), 0);

		buffer += 2;
	}

	if (!effectsGainEnabled) {
		effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
		effectsMinimumAmountOfFramesToReduce = 0;
		return;
	}

	vst1_f32(effectsGainClip, gainClip);
	vst1_f32((float*)effectsTemp, maxAbsSample);
	if (((float*)effectsTemp)[0] > MAX_ALLOWED_SAMPLE_VALUE || ((float*)effectsTemp)[1] > MAX_ALLOWED_SAMPLE_VALUE) {
		effectsFramesBeforeRecoveringGain = dstSampleRate << 2; //wait some time before starting to recover the gain
		effectsMinimumAmountOfFramesToReduce = (MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING * 3) >> 1;
	} else if (effectsMinimumAmountOfFramesToReduce <= 0) {
		if (effectsGainClip[0] >= 1.0f)
			effectsFramesBeforeRecoveringGain = 0x7FFFFFFF;
	}
}

extern uint32_t resamplePendingAdvances, resampleCoeffLen, resampleCoeffIdx, resampleAdvanceIdx;
//extern float *resampleCoeff;
extern int32_t *resampleCoeffINT;
extern uint32_t *resampleAdvance;
//extern float resampleY[] __attribute__((aligned(16)));
extern int32_t resampleYINT[] __attribute__((aligned(16)));

/*uint32_t resampleLagrangeNeon(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		vst1_f32(resampleYINT, vld1_f32(resampleYINT + 2));
		vst1_f32(resampleYINT + 2, vld1_f32(resampleYINT + 4));
		vst1_f32(resampleYINT + 4, vld1_f32(resampleYINT + 6));
		vst1_f32(resampleYINT + 6, vld1_f32(resampleYINT + 8));
		vst1_f32(resampleYINT + 8, vld1_f32(resampleYINT + 10));
		vst1_f32(resampleYINT + 10, vld1_f32(resampleYINT + 12));
		vst1_f32(resampleYINT + 12, vld1_f32(resampleYINT + 14));
		vst1_f32(resampleYINT + 14, vld1_f32(resampleYINT + 16));
		vst1_f32(resampleYINT + 16, vld1_f32(resampleYINT + 18));
		effectsTemp[0] = (int32_t)srcBuffer[0];
		effectsTemp[1] = (int32_t)srcBuffer[1];
		vst1_f32(resampleY + 18, vcvt_f32_s32(*((int32x2_t*)effectsTemp)));

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	float32x2_t y0 = vld1_f32(resampleY);
	float32x2_t y1 = vld1_f32(resampleY + 2);
	float32x2_t y2 = vld1_f32(resampleY + 4);
	float32x2_t y3 = vld1_f32(resampleY + 6);
	float32x2_t y4 = vld1_f32(resampleY + 8);
	float32x2_t y5 = vld1_f32(resampleY + 10);
	float32x2_t y6 = vld1_f32(resampleY + 12);
	float32x2_t y7 = vld1_f32(resampleY + 14);
	float32x2_t y8 = vld1_f32(resampleY + 16);
	float32x2_t y9 = vld1_f32(resampleY + 18);

	while (usedDst < dstSizeInFrames) {
		const float* const coeff = resampleCoeff + resampleCoeffIdx;
		float32x2_t out = vmul_f32(y0, *((float32x2_t*)coeff));
		out = vmla_f32(out, y1, *((float32x2_t*)(coeff + 2)));
		out = vmla_f32(out, y2, *((float32x2_t*)(coeff + 4)));
		out = vmla_f32(out, y3, *((float32x2_t*)(coeff + 6)));
		out = vmla_f32(out, y4, *((float32x2_t*)(coeff + 8)));
		out = vmla_f32(out, y5, *((float32x2_t*)(coeff + 10)));
		out = vmla_f32(out, y6, *((float32x2_t*)(coeff + 12)));
		out = vmla_f32(out, y7, *((float32x2_t*)(coeff + 14)));
		out = vmla_f32(out, y8, *((float32x2_t*)(coeff + 16)));
		out = vmla_f32(out, y9, *((float32x2_t*)(coeff + 18)));
		const int32x2_t outI32 = vcvt_s32_f32(out);
		const int16x4_t outI16 = vqmovn_s32(vcombine_s32(outI32, outI32));
		*dstBuffer++ = vget_lane_s16(outI16, 0);
		*dstBuffer++ = vget_lane_s16(outI16, 1);
		usedDst++;

		resampleCoeffIdx += 20;
		resampleAdvanceIdx++;
		if (resampleCoeffIdx >= resampleCoeffLen) {
			resampleCoeffIdx = 0;
			resampleAdvanceIdx = 0;
		}
		resamplePendingAdvances = resampleAdvance[resampleAdvanceIdx];

		while (resamplePendingAdvances) {
			resamplePendingAdvances--;

			y0 = y1;
			y1 = y2;
			y2 = y3;
			y3 = y4;
			y4 = y5;
			y5 = y6;
			y6 = y7;
			y7 = y8;
			y8 = y9;
			effectsTemp[0] = (int32_t)srcBuffer[0];
			effectsTemp[1] = (int32_t)srcBuffer[1];
			y9 = vcvt_f32_s32(*((int32x2_t*)effectsTemp));

			usedSrc++;
			srcBuffer += 2;

			if (usedSrc >= srcSizeInFrames) {
				vst1_f32(resampleY, y0);
				vst1_f32(resampleY + 2, y1);
				vst1_f32(resampleY + 4, y2);
				vst1_f32(resampleY + 6, y3);
				vst1_f32(resampleY + 8, y4);
				vst1_f32(resampleY + 10, y5);
				vst1_f32(resampleY + 12, y6);
				vst1_f32(resampleY + 14, y7);
				vst1_f32(resampleY + 16, y8);
				vst1_f32(resampleY + 18, y9);

				srcFramesUsed = usedSrc;
				return usedDst;
			}
		}
	}

	vst1_f32(resampleY, y0);
	vst1_f32(resampleY + 2, y1);
	vst1_f32(resampleY + 4, y2);
	vst1_f32(resampleY + 6, y3);
	vst1_f32(resampleY + 8, y4);
	vst1_f32(resampleY + 10, y5);
	vst1_f32(resampleY + 12, y6);
	vst1_f32(resampleY + 14, y7);
	vst1_f32(resampleY + 16, y8);
	vst1_f32(resampleY + 18, y9);

	srcFramesUsed = usedSrc;
	return usedDst;
}*/

uint32_t resampleLagrangeNeonINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		vst1_s32(resampleYINT, vld1_s32(resampleYINT + 2));
		vst1_s32(resampleYINT + 2, vld1_s32(resampleYINT + 4));
		vst1_s32(resampleYINT + 4, vld1_s32(resampleYINT + 6));
		vst1_s32(resampleYINT + 6, vld1_s32(resampleYINT + 8));
		vst1_s32(resampleYINT + 8, vld1_s32(resampleYINT + 10));
		vst1_s32(resampleYINT + 10, vld1_s32(resampleYINT + 12));
		vst1_s32(resampleYINT + 12, vld1_s32(resampleYINT + 14));
		vst1_s32(resampleYINT + 14, vld1_s32(resampleYINT + 16));
		vst1_s32(resampleYINT + 16, vld1_s32(resampleYINT + 18));
		resampleYINT[18] = (int32_t)srcBuffer[0];
		resampleYINT[19] = (int32_t)srcBuffer[1];

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	//NEON has 16 128-bit registers
	//ya_yb set uses 5 of them and coeffa_coeffb set uses another 5
	//which leaves 6 128-bit registers left for the compiler to use
	//and, as a matter of fact, after tunning and reading the disassembly
	//lots of times, I noticed gcc does a pretty decent job at organizing
	//vget_low_x, vget_high_x and vcombine_s32
	//(under AArch64, NEON has 32 128-bit registers... even better!)
	int32x4_t y0_y1 = vld1q_s32(resampleYINT);
	int32x4_t y2_y3 = vld1q_s32(resampleYINT + 4);
	int32x4_t y4_y5 = vld1q_s32(resampleYINT + 8);
	int32x4_t y6_y7 = vld1q_s32(resampleYINT + 12);
	int32x4_t y8_y9 = vld1q_s32(resampleYINT + 16);

	while (usedDst < dstSizeInFrames) {
		const int32_t* const coeff = resampleCoeffINT + resampleCoeffIdx;
		const int32x4_t coeff0_coeff1 = vld1q_s32(coeff);
		const int32x4_t coeff2_coeff3 = vld1q_s32(coeff + 4);
		const int32x4_t coeff4_coeff5 = vld1q_s32(coeff + 8);
		const int32x4_t coeff6_coeff7 = vld1q_s32(coeff + 12);
		const int32x4_t coeff8_coeff9 = vld1q_s32(coeff + 16);
		int64x2_t out = vmovq_n_s64(0);
		out = vmlal_s32(out, vget_low_s32(y0_y1), vget_low_s32(coeff0_coeff1));
		out = vmlal_s32(out, vget_high_s32(y0_y1), vget_high_s32(coeff0_coeff1));
		out = vmlal_s32(out, vget_low_s32(y2_y3), vget_low_s32(coeff2_coeff3));
		out = vmlal_s32(out, vget_high_s32(y2_y3), vget_high_s32(coeff2_coeff3));
		out = vmlal_s32(out, vget_low_s32(y4_y5), vget_low_s32(coeff4_coeff5));
		out = vmlal_s32(out, vget_high_s32(y4_y5), vget_high_s32(coeff4_coeff5));
		out = vmlal_s32(out, vget_low_s32(y6_y7), vget_low_s32(coeff6_coeff7));
		out = vmlal_s32(out, vget_high_s32(y6_y7), vget_high_s32(coeff6_coeff7));
		out = vmlal_s32(out, vget_low_s32(y8_y9), vget_low_s32(coeff8_coeff9));
		out = vmlal_s32(out, vget_high_s32(y8_y9), vget_high_s32(coeff8_coeff9));
		const int32x2_t outI32 = vqmovn_s64(vshrq_n_s64(out, 30));
		const int16x4_t outI16 = vqmovn_s32(vcombine_s32(outI32, outI32));
		vst1_lane_s32((int32_t*)dstBuffer, vreinterpret_s32_s16(outI16), 0); //store L and R with a single instruction
		dstBuffer += 2;
		usedDst++;

		resampleCoeffIdx += 20;
		resampleAdvanceIdx++;
		if (resampleCoeffIdx >= resampleCoeffLen) {
			resampleCoeffIdx = 0;
			resampleAdvanceIdx = 0;
		}
		resamplePendingAdvances = resampleAdvance[resampleAdvanceIdx];

		while (resamplePendingAdvances) {
			resamplePendingAdvances--;

			effectsTemp[0] = (int32_t)srcBuffer[0];
			effectsTemp[1] = (int32_t)srcBuffer[1];
			y0_y1 = vcombine_s32(vget_high_s32(y0_y1), vget_low_s32(y2_y3));
			y2_y3 = vcombine_s32(vget_high_s32(y2_y3), vget_low_s32(y4_y5));
			y4_y5 = vcombine_s32(vget_high_s32(y4_y5), vget_low_s32(y6_y7));
			y6_y7 = vcombine_s32(vget_high_s32(y6_y7), vget_low_s32(y8_y9));
			y8_y9 = vcombine_s32(vget_high_s32(y8_y9), vld1_s32(effectsTemp));

			usedSrc++;
			srcBuffer += 2;

			if (usedSrc >= srcSizeInFrames) {
				vst1q_s32(resampleYINT, y0_y1);
				vst1q_s32(resampleYINT + 4, y2_y3);
				vst1q_s32(resampleYINT + 8, y4_y5);
				vst1q_s32(resampleYINT + 12, y6_y7);
				vst1q_s32(resampleYINT + 16, y8_y9);

				srcFramesUsed = usedSrc;
				return usedDst;
			}
		}
	}

	vst1q_s32(resampleYINT, y0_y1);
	vst1q_s32(resampleYINT + 4, y2_y3);
	vst1q_s32(resampleYINT + 8, y4_y5);
	vst1q_s32(resampleYINT + 12, y6_y7);
	vst1q_s32(resampleYINT + 16, y8_y9);

	srcFramesUsed = usedSrc;
	return usedDst;
}

extern uint32_t visualizerWriteOffsetInFrames, visualizerBufferSizeInFrames;
extern uint8_t* visualizerBuffer;
static const int8_t visualizerx80[8] __attribute__((aligned(16))) = { 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80 };

void visualizerWriteNeon(const int16_t* srcBuffer, uint32_t bufferSizeInFrames) {
	const uint32_t frameCountAtTheEnd = visualizerBufferSizeInFrames - visualizerWriteOffsetInFrames;
	uint8_t* dstBuffer = visualizerBuffer + visualizerWriteOffsetInFrames;
	uint32_t count = ((bufferSizeInFrames <= frameCountAtTheEnd) ? bufferSizeInFrames : frameCountAtTheEnd);
	const int8x8_t x80 = vld1_s8(visualizerx80);
	do {
		uint32_t i = count;
		while (i >= 8) {
			//[0] = L0 L1 L2 L3
			//[1] = R0 R1 R2 R3
			int16x4x2_t src = vld2_s16(srcBuffer); //srcBuffer is unaligned, so the performance here won't be the best as possible
			//[0] = L4 L5 L6 L7
			//[1] = R4 R5 R6 R7
			int16x4x2_t src2 = vld2_s16(srcBuffer + 8);

			int32x4_t left32_0 = vmovl_s16(src.val[0]);
			int32x4_t right32_0 = vmovl_s16(src.val[1]);
			int32x4_t left32_1 = vmovl_s16(src2.val[0]);
			int32x4_t right32_1 = vmovl_s16(src2.val[1]);

			left32_0 = vaddq_s32(left32_0, right32_0);
			left32_1 = vaddq_s32(left32_1, right32_1);

			left32_0 = vshrq_n_s32(left32_0, 9);
			left32_1 = vshrq_n_s32(left32_1, 9);
			
			int8x8_t left8 = vqmovn_s16(vcombine_s16(vqmovn_s32(left32_0), vqmovn_s32(left32_1)));
			
			vst1_s8((int8_t*)dstBuffer, veor_s8(left8, x80));			
			dstBuffer += 8;
			srcBuffer += 16;
			i -= 8;
		}
		while (i--) {
			*dstBuffer++ = (uint8_t)((((int32_t)srcBuffer[0] + (int32_t)srcBuffer[1]) >> 9) ^ 0x80); // >> 9 = 1 (average) + 8 (remove lower byte)
			srcBuffer += 2;
		}
		bufferSizeInFrames -= count;
		count = bufferSizeInFrames;
		dstBuffer = visualizerBuffer;
	} while (bufferSizeInFrames);
}
