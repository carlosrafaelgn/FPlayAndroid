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

#include <xmmintrin.h>     //SSE
#include <emmintrin.h>     //SSE2
#include <pmmintrin.h>     //SSE3
#include <tmmintrin.h>     //SSSE3
#include <smmintrin.h> //SSE4.1
//#include <nmmintrin.h> //SSE4.2

extern uint32_t resamplePendingAdvances, resampleCoeffLen, resampleCoeffIdx, resampleAdvanceIdx;
//extern float *resampleCoeff;
extern int32_t *resampleCoeffINT;
extern uint32_t *resampleAdvance;
//extern float resampleY[] __attribute__((aligned(16)));
extern int32_t resampleYINT[] __attribute__((aligned(16)));

uint32_t resampleLagrangeSSE41INT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		memmove(resampleYINT, resampleYINT + 2, 18 * sizeof(int32_t));
		resampleYINT[18] = (int32_t)srcBuffer[0];
		resampleYINT[19] = (int32_t)srcBuffer[1];

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	/*//NEON has 16 128-bit registers
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
		*((int32_t*)dstBuffer) = vget_lane_s32(vreinterpret_s32_s16(outI16), 0); //store L and R with a single instruction
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
	vst1q_s32(resampleYINT + 16, y8_y9);*/

	srcFramesUsed = usedSrc;
	return usedDst;
}
