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
#include <jni.h>
#include <arm_neon.h>

#define FPLAY_ARM

#include "CommonNeon.h"

void commonProcessNeon(int8_t *bfft, int32_t deltaMillis, int32_t opt) {
	float *fft = floatBuffer;
	uint8_t *processedData = (uint8_t*)(floatBuffer + 512);
	float *localPreviousM = previousM;

	const float tmpCoefNew = commonCoefNew * (float)deltaMillis;
	const float32x4_t coefNew = vdupq_n_f32(tmpCoefNew);
	const float32x4_t coefOld = vdupq_n_f32(1.0f - tmpCoefNew);

	if ((opt & IGNORE_INPUT)) {
		for (int32_t i = 0; i < 256; i += 8) {
			float32x4_t previousM0 = vld1q_f32(localPreviousM);
			float32x4_t previousM1 = vld1q_f32(localPreviousM + 4);
			localPreviousM += 8;

			float32x4_t fftOld0 = vld1q_f32(fft);
			float32x4_t fftOld1 = vld1q_f32(fft + 4);

			uint32x4_t geq0 = vcgeq_f32(previousM0, fftOld0); //geq0 = (previousM0 >= fftOld0)
			uint32x4_t geq1 = vcgeq_f32(previousM1, fftOld1); //geq1 = (previousM1 >= fftOld1)
			
			fftOld0 = vmulq_f32(fftOld0, coefOld); //fftOld = old * coefOld
			fftOld1 = vmulq_f32(fftOld1, coefOld);

			fftOld0 = vmlaq_f32(fftOld0, previousM0, coefNew); //fftOld += m * coefNew
			fftOld1 = vmlaq_f32(fftOld1, previousM1, coefNew);

			//if previousM >= fftOld, use previousM, otherwise, use fftOld
			previousM0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(previousM0), geq0));
			previousM1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(previousM1), geq1));

			geq0 = vmvnq_u32(geq0); //geq0 = ~geq0
			geq1 = vmvnq_u32(geq1); //geq0 = ~geq0

			fftOld0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(fftOld0), geq0));
			fftOld1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(fftOld1), geq1));

			previousM0 = vreinterpretq_f32_u32(vorrq_u32(vreinterpretq_u32_f32(previousM0), vreinterpretq_u32_f32(fftOld0)));
			previousM1 = vreinterpretq_f32_u32(vorrq_u32(vreinterpretq_u32_f32(previousM1), vreinterpretq_u32_f32(fftOld1)));

			vst1q_f32(fft, previousM0);
			vst1q_f32(fft + 4, previousM1);
			fft += 8;

			uint32x4_t previousMI0 = vcvtq_u32_f32(previousM0);
			uint32x4_t previousMI1 = vcvtq_u32_f32(previousM1);

			previousMI0 = vshrq_n_u32(previousMI0, 7);
			previousMI1 = vshrq_n_u32(previousMI1, 7);

			vst1_u8(processedData, vqmovn_u16(vcombine_u16(vqmovn_u32(previousMI0), vqmovn_u32(previousMI1))));
			processedData += 8;
		}
	} else {
		const float *multiplierPtr = floatBuffer + 256;
		int32_t* tmpBuffer = intBuffer;
		for (int32_t i = 0; i < 256; i += 8) {
			//bfft[i] stores values from 0 to -128/127 (inclusive)
			tmpBuffer[0] = ((int32_t*)bfft)[0];
			tmpBuffer[1] = ((int32_t*)bfft)[1];
			tmpBuffer[2] = ((int32_t*)bfft)[2];
			tmpBuffer[3] = ((int32_t*)bfft)[3];
			
			//[0] = re re re re re re re re
			//[1] = im im im im im im im im
			const int8x8x2_t re_im = vld2_s8((int8_t*)tmpBuffer);

			const int16x8_t re16 = vmovl_s8(re_im.val[0]);
			const int16x8_t im16 = vmovl_s8(re_im.val[1]);

			int32x4_t re0 = vmovl_s16(vget_low_s16(re16));
			int32x4_t re1 = vmovl_s16(vget_high_s16(re16));
			int32x4_t im0 = vmovl_s16(vget_low_s16(im16));
			int32x4_t im1 = vmovl_s16(vget_high_s16(im16));
			
			/*asm volatile (
				//q6 = multiplier
				"vld1.32 {d12, d13}, [%[multiplier]]!\n"

				//d0 = re re re re re re re re
				//d1 = im im im im im im im im
				"vld2.8 {d0, d1}, [%[tmpBuffer]]\n"

				"vmovl.s8 q1, d1\n" //q1 (d2,d3) = im im im im im im im im (int16)
				"vmovl.s8 q0, d0\n" //q0 (d0,d1) = re re re re re re re re (int16)

				"vmovl.s16 q3, d3\n" //q3 = im im im im (int32)
				"vmovl.s16 q2, d2\n" //q2 = im im im im (int32)
				"vmovl.s16 q1, d1\n" //q1 = re re re re (int32)
				"vmovl.s16 q0, d0\n" //q0 = re re re re (int32)

				"vmul.i32 q0, q0, q0\n" //q0 = re * re
				"vmul.i32 q1, q1, q1\n" //q1 = re * re

				"vmla.i32 q0, q2, q2\n" //q0 = q0 + (im * im)
				"vmla.i32 q1, q3, q3\n" //q1 = q1 + (im * im)

				"movs r6, #8\n"
				"vdupq.32 q3, r6\n" //q3 = 8

				"vcgeq.s32 q4, q0, q3\n" //q4 = (q0 >= 8)
				"vcgeq.s32 q5, q1, q3\n" //q5 = (q1 >= 8)

				"vcvt.f32.s32 q0, q0\n" //q0 = (float)q0
				"vcvt.f32.s32 q1, q1\n" //q1 = (float)q1

				//inspired by:
				//https://code.google.com/p/math-neon/source/browse/trunk/math_sqrtfv.c?r=25
				//http://www.mikusite.de/pages/vfp_neon.htm

				//compute the sqrt of q0 (the more steps, the more precision!)
				"vrsqrteq.f32 q3, q0\n" //q3 = ~1/sqrt(q0)

				"vmulq.f32 q2, q3, q0\n"
				"vrsqrtsq.f32 q2, q2, q3\n"
				"vmulq.f32 q3, q3, q2\n"

				"vmulq.f32 q2, q3, q0\n"
				"vrsqrtsq.f32 q2, q2, q3\n"
				"vmulq.f32 q3, q3, q2\n"

				"vmulq.f32 q2, q3, q0\n"
				"vrsqrtsq.f32 q2, q2, q3\n"
				"vmulq.f32 q3, q3, q2\n"

				"vmulq.f32 q0, q0, q3\n" //q0 = q0 * 1/sqrt(q0) :)
				"vandq q0, q0, q4\n" //q0 = q0 & q4 (remove NaN's, as 1/0 = Infinity, and x * Infinity = NaN)
				"vmulq.f32 q0, q0, q6\n"

				//compute the sqrt of q1 (the more steps, the more precision!)
				"vrsqrteq.f32 q3, q1\n" //q3 = ~1/sqrt(q0)

				"vmulq.f32 q2, q3, q1\n"
				"vrsqrtsq.f32 q2, q2, q3\n"
				"vmulq.f32 q3, q3, q2\n"

				"vmulq.f32 q2, q3, q1\n"
				"vrsqrtsq.f32 q2, q2, q3\n"
				"vmulq.f32 q3, q3, q2\n"

				"vmulq.f32 q2, q3, q1\n"
				"vrsqrtsq.f32 q2, q2, q3\n"
				"vmulq.f32 q3, q3, q2\n"

				"vmulq.f32 q1, q1, q3\n" //q1 = q1 * 1/sqrt(q1) :)
				"vandq q1, q1, q5\n" //q1 = q1 & q5 (remove NaN's, as 1/0 = Infinity, and x * Infinity = NaN)
				"vmulq.f32 q1, q1, q6\n"

				"vst1.32 {d0, d1}, [%[localPreviousM]]!\n" //previousM = q0 (q0 is m)
				"vst1.32 {d2, d3}, [%[localPreviousM]]!\n" //previousM = q1 (q1 is m)

				"ldr r6, %[coefNew]\n"
				"vdupq.32 q4, r6\n" //q4 = coefNew

				"ldr r6, %[coefOld]\n"
				"vdupq.32 q5, r6\n" //q5 = coefOld

				"vld1.32 {d12, d13}, [%[fft]]\n" //q6 = fft (old)
				"adds %[fft], #16\n"
				"vld1.32 {d14, d15}, [%[fft]]\n" //q7 = fft (old)
				"subs %[fft], #16\n"

				"vcgeq.f32 q2, q0, q6\n" //q2 = (m >= old) (q0 >= q6)
				"vcgeq.f32 q3, q1, q7\n" //q3 = (m >= old) (q1 >= q7)

				"vmulq.f32 q6, q5, q6\n" //q6 = (coefOld * old)
				"vmulq.f32 q7, q5, q7\n" //q7 = (coefOld * old)

				"vmla.f32 q6, q4, q0\n" //q6 = q6 + (coefNew * m)
				"vmla.f32 q7, q4, q1\n" //q7 = q7 + (coefNew * m)

				//if q2 = 1, use q0, otherwise, use q6
				"vandq q0, q0, q2\n"
				"vmvn q2, q2\n" //q2 = ~q2
				"vandq q6, q6, q2\n"
				"vorrq q0, q0, q6\n"

				//if q3 = 1, use q1, otherwise, use q7
				"vandq q1, q1, q3\n"
				"vmvn q3, q3\n" //q3 = ~q3
				"vandq q7, q7, q3\n"
				"vorrq q1, q1, q7\n"

				"vst1.32 {d0, d1}, [%[fft]]!\n" //fft = q0 (q0 is m)
				"vst1.32 {d2, d3}, [%[fft]]!\n" //fft = q1 (q1 is m)

				"vcvt.u32.f32 q0, q0\n" //q0 = (uint32_t)q0
				"vcvt.u32.f32 q1, q1\n" //q1 = (uint32_t)q1

				"vshrq.u32 q0, q0, #7\n" //q0 = q0 >> 7
				"vshrq.u32 q1, q1, #7\n" //q1 = q1 >> 7

				"vqmovn.u32 d4, q0\n" //d4 = (uint16_t)q0 [with saturation]
				"vqmovn.u32 d5, q1\n" //d5 = (uint16_t)q1 [with saturation]

				"vqmovn.u16 d0, q2\n" //d0 = (uint8_t)q2 [with saturation]

				"vst1.8 {d0}, [%[processedData]]!\n"

			: [multiplier] "+r" (multiplier), [processedData] "+r" (processedData), [fft] "+r" (fft), [localPreviousM] "+r" (localPreviousM)
			: [tmpBuffer] "r" (tmpBuffer), [coefNew] "m" (coefNew), [coefOld] "m" (coefOld)
			: "cc", "r6", "q0", "q1", "q2", "q3", "q4", "q5", "q6", "q7");*/
			bfft += 16;
		}
	}
}
