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

#include "CommonNeon.h"

void commonProcessNeon(signed char* bfft, int deltaMillis, int opt) {
	float* fft = floatBuffer;
	const float* multiplier = floatBuffer + 256;
	unsigned char* processedData = (unsigned char*)(floatBuffer + 512);
	float* localPreviousM = previousM;

	const float coefNew = commonCoefNew * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;

	if ((opt & IGNORE_INPUT)) {
		for (int i = 0; i < 256; i += 8) {
			asm volatile (
				"vld1.32 {d0, d1}, [%[localPreviousM]]!\n" //q0 = previousM
				"vld1.32 {d2, d3}, [%[localPreviousM]]!\n" //q1 = previousM

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

				"vcvt.u32.f32 q0, q0\n" //q0 = (unsigned int)q0
				"vcvt.u32.f32 q1, q1\n" //q1 = (unsigned int)q1

				"vshrq.u32 q0, q0, #7\n" //q0 = q0 >> 7
				"vshrq.u32 q1, q1, #7\n" //q1 = q1 >> 7

				"vqmovn.u32 d4, q0\n" //d4 = (unsigned short)q0 [with saturation]
				"vqmovn.u32 d5, q1\n" //d5 = (unsigned short)q1 [with saturation]

				"vqmovn.u16 d0, q2\n" //d0 = (unsigned char)q2 [with saturation]

				"vst1.8 {d0}, [%[processedData]]!\n"

			: [processedData] "+r" (processedData), [fft] "+r" (fft), [localPreviousM] "+r" (localPreviousM)
			: [coefNew] "m" (coefNew), [coefOld] "m" (coefOld)
			: "cc", "r6", "q0", "q1", "q2", "q3", "q4", "q5", "q6", "q7");
		}
	} else {
		int* tmpBuffer = intBuffer;
		for (int i = 0; i < 256; i += 8) {
			//bfft[i] stores values from 0 to -128/127 (inclusive)
			tmpBuffer[0] = ((int*)bfft)[0];
			tmpBuffer[1] = ((int*)bfft)[1];
			tmpBuffer[2] = ((int*)bfft)[2];
			tmpBuffer[3] = ((int*)bfft)[3];
			asm volatile (
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

				"vcvt.u32.f32 q0, q0\n" //q0 = (unsigned int)q0
				"vcvt.u32.f32 q1, q1\n" //q1 = (unsigned int)q1

				"vshrq.u32 q0, q0, #7\n" //q0 = q0 >> 7
				"vshrq.u32 q1, q1, #7\n" //q1 = q1 >> 7

				"vqmovn.u32 d4, q0\n" //d4 = (unsigned short)q0 [with saturation]
				"vqmovn.u32 d5, q1\n" //d5 = (unsigned short)q1 [with saturation]

				"vqmovn.u16 d0, q2\n" //d0 = (unsigned char)q2 [with saturation]

				"vst1.8 {d0}, [%[processedData]]!\n"

			: [multiplier] "+r" (multiplier), [processedData] "+r" (processedData), [fft] "+r" (fft), [localPreviousM] "+r" (localPreviousM)
			: [tmpBuffer] "r" (tmpBuffer), [coefNew] "m" (coefNew), [coefOld] "m" (coefOld)
			: "cc", "r6", "q0", "q1", "q2", "q3", "q4", "q5", "q6", "q7");
			bfft += 16;
		}
	}
}
/*
static const int __0[] __attribute__((aligned(16))) = { 0, 0, 0, 0 };
static const int __32768[] __attribute__((aligned(16))) = { 32768, 32768, 32768, 32768 };
static int __tmp[4] __attribute__((aligned(16)));
static int __v[4] __attribute__((aligned(16)));
static int __v2[4] __attribute__((aligned(16)));

void processNeon(signed char* bfft, int deltaMillis) {
	float* const fft = floatBuffer;
	const float* const multiplier = floatBuffer + 256;

	const float coefNew = COEF_SPEED_DEF * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;

	//step 1: compute all magnitudes
	for (int i = barBins - 1; i >= 0; i--) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		const int amplSq = (re * re) + (im * im);
		float m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
	}

	int32x4_t _0 = vld1q_s32(__0), _32768 = vld1q_s32(__32768), _barH = { barH, barH, barH, barH }, _barH2 = vshrq_n_s32(_barH, 1), _colorIndex = { commonColorIndex, commonColorIndex, commonColorIndex, commonColorIndex };
	if (barW == 1 || !lerp) {
		for (int i = 0; i < barBins; i += 4) {
			//_v goes from 0 to 32768 (inclusive)
			int32x4_t _v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(vld1q_f32(fft + i))));
			vst1q_s32(__tmp, vaddq_s32(vshrq_n_s32(_v, 7), _colorIndex));
			_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
			int32x4_t _v2 = _v;
			_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
			vst1q_s32(__v, _v);
			vst1q_s32(__v2, vaddq_s32(_v2, _v));
			for (int j = 0; j < 4; j++) {
				const unsigned short color = COLORS[__tmp[j]];
				const int v = __v[j];
				const int v2 = __v2[j];
				unsigned short* currentBar = (unsigned short*)inf.bits;
				inf.bits = (void*)((unsigned short*)inf.bits + barW);
				int y = 0;
				switch (barW) {
				case 1:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 2:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 3:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar[2] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 4:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar[3] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar[2] = color;
						currentBar[3] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar[3] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				default:
					for (; y < v; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				}
			}
		}
	} else {
		float32x4_t _invBarW = { invBarW, invBarW, invBarW, invBarW }, _prev = { 0.0f, fft[0], fft[1], fft[2] };
		int originalBarIndex = 0;
		for (int i = 0; i < barBins; i += 4) {
			//process the four actual bars
			int barIndex = originalBarIndex;
			//_v goes from 0 to 32768 (inclusive)
			int32x4_t _v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(_prev)));
			vst1q_s32(__tmp, vaddq_s32(vshrq_n_s32(_v, 7), _colorIndex));
			_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
			int32x4_t _v2 = _v;
			_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
			vst1q_s32(__v, _v);
			vst1q_s32(__v2, vaddq_s32(_v2, _v));
			for (int j = 0; j < 4; j++) {
				//v goes from 0 to 32768 (inclusive)
				const unsigned short color = COLORS[__tmp[j]];
				const int v = __v[j];
				const int v2 = __v2[j];
				unsigned short* currentBar = (unsigned short*)inf.bits + barIndex;
				int y = 0;
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				barIndex += barW;
			}

			//now, process all the interpolated bars (the ones between the actual bars)
			float32x4_t _delta = vmulq_f32(vsubq_f32(vld1q_f32(fft + i), _prev), _invBarW);
			for (int b = 1; b < barW; b++) {
				//move to the next bar
				barIndex = originalBarIndex + b;
				_prev = vaddq_f32(_prev, _delta);
				//_v goes from 0 to 32768 (inclusive)
				_v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(_prev)));
				vst1q_s32(__tmp, vaddq_s32(vshrq_n_s32(_v, 7), _colorIndex));
				_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
				int32x4_t _v2 = _v;
				_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
				vst1q_s32(__v, _v);
				vst1q_s32(__v2, vaddq_s32(_v2, _v));
				for (int j = 0; j < 4; j++) {
					const unsigned short color = COLORS[__tmp[j]];
					const int v = __v[j];
					const int v2 = __v2[j];
					unsigned short* currentBar = (unsigned short*)inf.bits + barIndex;
					int y = 0;
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					barIndex += barW;
				}
			}
			originalBarIndex += (barW << 2);
			//it is ok to load data beyond index 255, as after the first fft's 256
			//elements there are the 256 multipliers ;)
			_prev = vld1q_f32(fft + (i + 3));
		}
	}
}
*/
