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

#ifdef FPLAY_X86
_NEON2SSE_INLINE void s16tof32(__m128i a, __m128 &lo, __m128 &hi)
{
	const __m128i zero = _mm_setzero_si128();
	const __m128i sign = _mm_cmpgt_epi16(zero, a);
	lo = _mm_cvtepi32_ps(_mm_unpacklo_epi16(a, sign));
	hi = _mm_cvtepi32_ps(_mm_unpackhi_epi16(a, sign));
}
#endif

void commonProcessNeon(int8_t *bfft, int32_t deltaMillis, int32_t opt) {
	float *fft = floatBuffer;
	const float *multiplier = floatBuffer + QUARTER_FFT_SIZE;
	uint8_t *processedData = (uint8_t*)(floatBuffer + (QUARTER_FFT_SIZE << 1));
	float *previousM = ::previousM;

	const float tmpCoefNew = commonCoefNew * (float)deltaMillis;
	const float32x4_t coefNew = vdupq_n_f32(tmpCoefNew);
	const float32x4_t coefOld = vdupq_n_f32(1.0f - tmpCoefNew);

	if ((opt & IGNORE_INPUT)) {
#ifdef FPLAY_ARM
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i += 8) {
			//when (opt & IGNORE_INPUT) we do not compute m (we use its previous value)
			float32x4_t m0 = vld1q_f32(previousM);
			float32x4_t m1 = vld1q_f32(previousM + 4);
			previousM += 8;

			float32x4_t old0 = vld1q_f32(fft);
			float32x4_t old1 = vld1q_f32(fft + 4);

			uint32x4_t geq0 = vcgeq_f32(m0, old0); //geq = (m >= old)
			uint32x4_t geq1 = vcgeq_f32(m1, old1);

			old0 = vmulq_f32(old0, coefOld); //old *= coefOld
			old1 = vmulq_f32(old1, coefOld);

			old0 = vmlaq_f32(old0, m0, coefNew); //old += m * coefNew
			old1 = vmlaq_f32(old1, m1, coefNew);

			//if m >= old, use m, otherwise, use old (which now contains (coefNew * m) + (coefOld * old))
			m0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(m0), geq0));
			m1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(m1), geq1));

			geq0 = vmvnq_u32(geq0); //geq = ~geq
			geq1 = vmvnq_u32(geq1);

			old0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(old0), geq0));
			old1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(old1), geq1));

			m0 = vreinterpretq_f32_u32(vorrq_u32(vreinterpretq_u32_f32(m0), vreinterpretq_u32_f32(old0)));
			m1 = vreinterpretq_f32_u32(vorrq_u32(vreinterpretq_u32_f32(m1), vreinterpretq_u32_f32(old1)));

			vst1q_f32(fft, m0);
			vst1q_f32(fft + 4, m1);
			fft += 8;

			const uint32x4_t mI0 = vshrq_n_u32(vcvtq_u32_f32(m0), 7);
			const uint32x4_t mI1 = vshrq_n_u32(vcvtq_u32_f32(m1), 7);

			vst1_u8(processedData, vqmovn_u16(vcombine_u16(vqmovn_u32(mI0), vqmovn_u32(mI1))));
			processedData += 8;
		}
#else
		//I know my data is aligned!!!
#define vld1q_f32 _mm_load_ps
#define vqmovn_s32(a) _mm_packs_epi32(a, a)
#define vqmovn2_s32(a, b) _mm_packs_epi32(a, b)
#define vqmovn_s16(a) _mm_packs_epi16(a, a)
#undef vst1_s8
#define vst1_s8(a, b) _mm_store_sd((double*)(a), *((__m128d*)&(b)))
#define int16x8_t __m128i
#define int8x8_t __m128i

		//Even though x86 has only 8 128-bits registers, after examining the disassembly,
		//I confirmed all this code fits into 8 128-bits registers! :)
		//(x86-64 has 16 registers, so, no worries)
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i += 8) {
			//when (opt & IGNORE_INPUT) we do not compute m (we use its previous value)
			float32x4_t m0 = vld1q_f32(previousM);
			float32x4_t m1 = vld1q_f32(previousM + 4);
			previousM += 8;

			float32x4_t old0 = vld1q_f32(fft);
			float32x4_t old1 = vld1q_f32(fft + 4);

			uint32x4_t geq0 = vcgeq_f32(m0, old0); //geq = (m >= old)
			uint32x4_t geq1 = vcgeq_f32(m1, old1);

			old0 = vmulq_f32(old0, coefOld); //old *= coefOld
			old1 = vmulq_f32(old1, coefOld);

			old0 = vmlaq_f32(old0, m0, coefNew); //old += m * coefNew
			old1 = vmlaq_f32(old1, m1, coefNew);

			//if m >= old, use m, otherwise, use old (which now contains (coefNew * m) + (coefOld * old))
			{
			const uint32x4_t tmp0 = vandq_u32(vreinterpretq_u32_f32(m0), geq0);
			const uint32x4_t tmp1 = vandq_u32(vreinterpretq_u32_f32(m1), geq1);
			m0 = vreinterpretq_f32_u32(tmp0);
			m1 = vreinterpretq_f32_u32(tmp1);
			}

			geq0 = vmvnq_u32(geq0); //geq = ~geq
			geq1 = vmvnq_u32(geq1); //geq = ~geq

			{
			const uint32x4_t tmp0 = vandq_u32(vreinterpretq_u32_f32(old0), geq0);
			const uint32x4_t tmp1 = vandq_u32(vreinterpretq_u32_f32(old1), geq1);
			old0 = vreinterpretq_f32_u32(tmp0);
			old1 = vreinterpretq_f32_u32(tmp1);
			}

			{
			const uint32x4_t tmp0 = vorrq_u32(vreinterpretq_u32_f32(m0), vreinterpretq_u32_f32(old0));
			const uint32x4_t tmp1 = vorrq_u32(vreinterpretq_u32_f32(m1), vreinterpretq_u32_f32(old1));
			m0 = vreinterpretq_f32_u32(tmp0);
			m1 = vreinterpretq_f32_u32(tmp1);
			}

			vst1q_f32(fft, m0);
			vst1q_f32(fft + 4, m1);
			fft += 8;

			//x86 does not have unsigned instructions... nonetheless, this should not worry ourselves
			//as m, fft, coefNew and coefOld are all positive numbers
			const int32x4_t mI0 = vshrq_n_s32(vcvtq_s32_f32(m0), 7);
			const int32x4_t mI1 = vshrq_n_s32(vcvtq_s32_f32(m1), 7);

			const int16x8_t mI0_16 = vqmovn2_s32(mI0, mI1);

			const int8x8_t mI0_8 = vqmovn_s16(mI0_16);

			vst1_s8(processedData, mI0_8);
			processedData += 8;
		}

#undef vld1q_f32
#undef vqmovn_s32
#undef vqmovn2_s32
#undef vqmovn_s16
#undef vst1_s8
#define vst1_s8(ptr,val) vst1_u8((uint8_t*)ptr,val)
#undef int16x8_t
#undef int8x8_t

#endif
	} else {
		const float32x4_t eight = vdupq_n_f32(8.0f);
#ifdef FPLAY_ARM
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i += 8) {
			//all this initial code is just to compute m

			//bfft[i] stores values from 0 to -128/127 (inclusive)
			intBuffer[0] = ((int32_t*)bfft)[0];
			intBuffer[1] = ((int32_t*)bfft)[1];
			intBuffer[2] = ((int32_t*)bfft)[2];
			intBuffer[3] = ((int32_t*)bfft)[3];
			bfft += 16;

			//[0] = re re re re re re re re
			//[1] = im im im im im im im im
			const int8x8x2_t re_im = vld2_s8((int8_t*)intBuffer);

			const int16x8_t re16 = vmovl_s8(re_im.val[0]);
			const int16x8_t im16 = vmovl_s8(re_im.val[1]);

			const int32x4_t re0 = vmovl_s16(vget_low_s16(re16));
			const int32x4_t re1 = vmovl_s16(vget_high_s16(re16));
			const int32x4_t im0 = vmovl_s16(vget_low_s16(im16));
			const int32x4_t im1 = vmovl_s16(vget_high_s16(im16));

			//amplSq = (re * re) + (im * im)
			const float32x4_t amplSq0 = vcvtq_f32_s32(vmlaq_s32(vmulq_s32(re0, re0), im0, im0));
			const float32x4_t amplSq1 = vcvtq_f32_s32(vmlaq_s32(vmulq_s32(re1, re1), im1, im1));

			const uint32x4_t gt0 = vcgtq_f32(amplSq0, eight); //gt = (amplSq > 8)
			const uint32x4_t gt1 = vcgtq_f32(amplSq1, eight);

			//inspired by:
			//https://code.google.com/p/math-neon/source/browse/trunk/math_sqrtfv.c?r=25
			//http://www.mikusite.de/pages/vfp_neon.htm

			//compute the sqrt of amplSq (the more steps, the more precision!)
			float32x4_t recSqrt0 = vrsqrteq_f32(amplSq0); //recSqrt = ~1/sqrt(amplSq)
			float32x4_t recSqrt1 = vrsqrteq_f32(amplSq1);

			float32x4_t tmp0 = vmulq_f32(recSqrt0, amplSq0);
			float32x4_t tmp1 = vmulq_f32(recSqrt1, amplSq1);
			tmp0 = vrsqrtsq_f32(tmp0, recSqrt0);
			tmp1 = vrsqrtsq_f32(tmp1, recSqrt1);
			recSqrt0 = vmulq_f32(recSqrt0, tmp0);
			recSqrt1 = vmulq_f32(recSqrt1, tmp1);

			tmp0 = vmulq_f32(recSqrt0, amplSq0);
			tmp1 = vmulq_f32(recSqrt1, amplSq1);
			tmp0 = vrsqrtsq_f32(tmp0, recSqrt0);
			tmp1 = vrsqrtsq_f32(tmp1, recSqrt1);
			recSqrt0 = vmulq_f32(recSqrt0, tmp0);
			recSqrt1 = vmulq_f32(recSqrt1, tmp1);

			tmp0 = vmulq_f32(recSqrt0, amplSq0);
			tmp1 = vmulq_f32(recSqrt1, amplSq1);
			tmp0 = vrsqrtsq_f32(tmp0, recSqrt0);
			tmp1 = vrsqrtsq_f32(tmp1, recSqrt1);
			recSqrt0 = vmulq_f32(recSqrt0, tmp0);
			recSqrt1 = vmulq_f32(recSqrt1, tmp1);

			//and finally, x / sqrt(x) = sqrt(x) !!! :)
			float32x4_t m0 = vmulq_f32(amplSq0, recSqrt0);
			float32x4_t m1 = vmulq_f32(amplSq1, recSqrt1);

			const float32x4_t multiplier0 = vld1q_f32(multiplier);
			const float32x4_t multiplier1 = vld1q_f32(multiplier + 4);
			multiplier += 8;

			//this will zero out all m's <= 8 and will also remove any NaN's that may have
			//arisen from 1/sqrt(x), as 1/0 = Infinity, and x * Infinity = NaN
			m0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(m0), gt0));
			m1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(m1), gt1));

			m0 = vmulq_f32(m0, multiplier0); //m = ((amplSq <= 8) ? 0 : (multiplier * sqrtf(amplSq)));
			m1 = vmulq_f32(m1, multiplier1);

			vst1q_f32(previousM, m0);
			vst1q_f32(previousM + 4, m1);
			previousM += 8;

			//now that m has been computed, we can finally proceed and compute the
			//weighted/fitered amplitude (using our coefficients)
			//the code from this point on is the same one used inside if ((opt & IGNORE_INPUT))
			float32x4_t old0 = vld1q_f32(fft);
			float32x4_t old1 = vld1q_f32(fft + 4);

			uint32x4_t geq0 = vcgeq_f32(m0, old0); //geq = (m >= old)
			uint32x4_t geq1 = vcgeq_f32(m1, old1);

			old0 = vmulq_f32(old0, coefOld); //old *= coefOld
			old1 = vmulq_f32(old1, coefOld);

			old0 = vmlaq_f32(old0, m0, coefNew); //old += m * coefNew
			old1 = vmlaq_f32(old1, m1, coefNew);

			//if m >= old, use m, otherwise, use old (which now contains (coefNew * m) + (coefOld * old))
			m0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(m0), geq0));
			m1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(m1), geq1));

			geq0 = vmvnq_u32(geq0); //geq = ~geq
			geq1 = vmvnq_u32(geq1);

			old0 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(old0), geq0));
			old1 = vreinterpretq_f32_u32(vandq_u32(vreinterpretq_u32_f32(old1), geq1));

			m0 = vreinterpretq_f32_u32(vorrq_u32(vreinterpretq_u32_f32(m0), vreinterpretq_u32_f32(old0)));
			m1 = vreinterpretq_f32_u32(vorrq_u32(vreinterpretq_u32_f32(m1), vreinterpretq_u32_f32(old1)));

			vst1q_f32(fft, m0);
			vst1q_f32(fft + 4, m1);
			fft += 8;

			uint32x4_t mI0 = vcvtq_u32_f32(m0);
			uint32x4_t mI1 = vcvtq_u32_f32(m1);

			mI0 = vshrq_n_u32(mI0, 7);
			mI1 = vshrq_n_u32(mI1, 7);

			vst1_u8(processedData, vqmovn_u16(vcombine_u16(vqmovn_u32(mI0), vqmovn_u32(mI1))));
			processedData += 8;
		}
#else
		//I know my data is aligned!!!
#define vld1q_f32 _mm_load_ps
#define vqmovn_s32(a) _mm_packs_epi32(a, a)
#define vqmovn2_s32(a, b) _mm_packs_epi32(a, b)
#define vqmovn_s16(a) _mm_packs_epi16(a, a)
#undef vst1_s8
#define vst1_s8(a, b) _mm_store_sd((double*)(a), *((__m128d*)&(b)))
#define int16x8_t __m128i
#define int8x8_t __m128i

		//Even though x86 has only 8 128-bits registers, after examining the disassembly,
		//I confirmed all this code fits into 8 128-bits registers! :)
		//(x86-64 has 16 registers, so, no worries)
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i += 8) {
			//all this initial code is just to compute m

			//bfft[i] stores values from 0 to -128/127 (inclusive)
			intBuffer[0] = ((int32_t*)bfft)[0];
			intBuffer[1] = ((int32_t*)bfft)[1];
			intBuffer[2] = ((int32_t*)bfft)[2];
			intBuffer[3] = ((int32_t*)bfft)[3];
			bfft += 16;

			//[0] = re re re re re re re re
			//[1] = im im im im im im im im
			const int8x8x2_t re_im = vld2_s8((int8_t*)intBuffer);

			const int16x8_t re16 = vmovl_s8(re_im.val[0]);
			const int16x8_t im16 = vmovl_s8(re_im.val[1]);

			//according to https://developer.android.com/ndk/guides/abis.html#86-64
			//only x86-64 android devices have SSE4.1 (supporting integer multiplication)
			//therefore, we must convert these int's to float's here
			float32x4_t re0, re1, im0, im1;
			s16tof32(re16, re0, re1);
			s16tof32(im16, im0, im1);

			//amplSq = (re * re) + (im * im)
			const float32x4_t amplSq0 = vmlaq_f32(vmulq_f32(re0, re0), im0, im0);
			const float32x4_t amplSq1 = vmlaq_f32(vmulq_f32(re1, re1), im1, im1);

			const uint32x4_t gt0 = vcgtq_f32(amplSq0, eight); //gt = (amplSq > 8)
			const uint32x4_t gt1 = vcgtq_f32(amplSq1, eight);

			//inspired by:
			//https://code.google.com/p/math-neon/source/browse/trunk/math_sqrtfv.c?r=25
			//http://www.mikusite.de/pages/vfp_neon.htm

			//compute the sqrt of amplSq (the more steps, the more precision!)
			float32x4_t recSqrt0 = vrsqrteq_f32(amplSq0); //recSqrt = ~1/sqrt(amplSq)
			float32x4_t recSqrt1 = vrsqrteq_f32(amplSq1);

			float32x4_t tmp0 = vmulq_f32(recSqrt0, amplSq0);
			float32x4_t tmp1 = vmulq_f32(recSqrt1, amplSq1);
			tmp0 = vrsqrtsq_f32(tmp0, recSqrt0);
			tmp1 = vrsqrtsq_f32(tmp1, recSqrt1);
			recSqrt0 = vmulq_f32(recSqrt0, tmp0);
			recSqrt1 = vmulq_f32(recSqrt1, tmp1);

			tmp0 = vmulq_f32(recSqrt0, amplSq0);
			tmp1 = vmulq_f32(recSqrt1, amplSq1);
			tmp0 = vrsqrtsq_f32(tmp0, recSqrt0);
			tmp1 = vrsqrtsq_f32(tmp1, recSqrt1);
			recSqrt0 = vmulq_f32(recSqrt0, tmp0);
			recSqrt1 = vmulq_f32(recSqrt1, tmp1);

			tmp0 = vmulq_f32(recSqrt0, amplSq0);
			tmp1 = vmulq_f32(recSqrt1, amplSq1);
			tmp0 = vrsqrtsq_f32(tmp0, recSqrt0);
			tmp1 = vrsqrtsq_f32(tmp1, recSqrt1);
			recSqrt0 = vmulq_f32(recSqrt0, tmp0);
			recSqrt1 = vmulq_f32(recSqrt1, tmp1);

			//and finally, x / sqrt(x) = sqrt(x) !!! :)
			float32x4_t m0 = vmulq_f32(amplSq0, recSqrt0);
			float32x4_t m1 = vmulq_f32(amplSq1, recSqrt1);

			const float32x4_t multiplier0 = vld1q_f32(multiplier);
			const float32x4_t multiplier1 = vld1q_f32(multiplier + 4);
			multiplier += 8;

			//this will zero out all m's <= 8 and will also remove any NaN's that may have
			//arisen from 1/sqrt(x), as 1/0 = Infinity, and x * Infinity = NaN
			{
			const uint32x4_t tmp0 = vandq_u32(vreinterpretq_u32_f32(m0), gt0);
			const uint32x4_t tmp1 = vandq_u32(vreinterpretq_u32_f32(m1), gt1);
			m0 = vreinterpretq_f32_u32(tmp0);
			m1 = vreinterpretq_f32_u32(tmp1);
			}

			m0 = vmulq_f32(m0, multiplier0); //m = ((amplSq <= 8) ? 0 : (multiplier * sqrtf(amplSq)));
			m1 = vmulq_f32(m1, multiplier1);

			vst1q_f32(previousM, m0);
			vst1q_f32(previousM + 4, m1);
			previousM += 8;

			//now that m has been computed, we can finally proceed and compute the
			//weighted/fitered amplitude (using our coefficients)
			//the code from this point on is the same one used inside if ((opt & IGNORE_INPUT))
			float32x4_t old0 = vld1q_f32(fft);
			float32x4_t old1 = vld1q_f32(fft + 4);

			uint32x4_t geq0 = vcgeq_f32(m0, old0); //geq = (m >= old)
			uint32x4_t geq1 = vcgeq_f32(m1, old1);

			old0 = vmulq_f32(old0, coefOld); //old *= coefOld
			old1 = vmulq_f32(old1, coefOld);

			old0 = vmlaq_f32(old0, m0, coefNew); //old += m * coefNew
			old1 = vmlaq_f32(old1, m1, coefNew);

			//if m >= old, use m, otherwise, use old (which now contains (coefNew * m) + (coefOld * old))
			{
			const uint32x4_t tmp0 = vandq_u32(vreinterpretq_u32_f32(m0), geq0);
			const uint32x4_t tmp1 = vandq_u32(vreinterpretq_u32_f32(m1), geq1);
			m0 = vreinterpretq_f32_u32(tmp0);
			m1 = vreinterpretq_f32_u32(tmp1);
			}

			geq0 = vmvnq_u32(geq0); //geq = ~geq
			geq1 = vmvnq_u32(geq1); //geq = ~geq

			{
			const uint32x4_t tmp0 = vandq_u32(vreinterpretq_u32_f32(old0), geq0);
			const uint32x4_t tmp1 = vandq_u32(vreinterpretq_u32_f32(old1), geq1);
			old0 = vreinterpretq_f32_u32(tmp0);
			old1 = vreinterpretq_f32_u32(tmp1);
			}

			{
			const uint32x4_t tmp0 = vorrq_u32(vreinterpretq_u32_f32(m0), vreinterpretq_u32_f32(old0));
			const uint32x4_t tmp1 = vorrq_u32(vreinterpretq_u32_f32(m1), vreinterpretq_u32_f32(old1));
			m0 = vreinterpretq_f32_u32(tmp0);
			m1 = vreinterpretq_f32_u32(tmp1);
			}

			vst1q_f32(fft, m0);
			vst1q_f32(fft + 4, m1);
			fft += 8;

			//x86 does not have unsigned instructions... nonetheless, this should not worry ourselves
			//as m, fft, coefNew and coefOld are all positive numbers
			const int32x4_t mI0 = vshrq_n_s32(vcvtq_s32_f32(m0), 7);
			const int32x4_t mI1 = vshrq_n_s32(vcvtq_s32_f32(m1), 7);

			const int16x8_t mI0_16 = vqmovn2_s32(mI0, mI1);

			const int8x8_t mI0_8 = vqmovn_s16(mI0_16);

			vst1_s8(processedData, mI0_8);
			processedData += 8;
		}

#undef vld1q_f32
#undef vqmovn_s32
#undef vqmovn2_s32
#undef vqmovn_s16
#undef vst1_s8
#define vst1_s8(ptr,val) vst1_u8((uint8_t*)ptr,val)
#undef int16x8_t
#undef int8x8_t

#endif
	}
}
