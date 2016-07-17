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

#include "CommonNeon.h"

#ifdef FPLAY_ARM
#include <arm_neon.h>
#else
//https://developer.android.com/ndk/guides/abis.html#x86
//https://developer.android.com/ndk/guides/abis.html#86-64
//All x86 Android devices use at least Atom processors (all Atom processor have SSE, SSE2, SSE3 and SSSE3 support, and all 64-bits Atom have SSE4.1/4.2 support)
#include <xmmintrin.h> //SSE
#include <emmintrin.h> //SSE2
#include <pmmintrin.h> //SSE3
#include <tmmintrin.h> //SSSE3
#ifdef FPLAY_64_BITS
#include <smmintrin.h> //SSE4.1
#endif
//https://software.intel.com/sites/landingpage/IntrinsicsGuide/
static const int8_t evenOddIndices[16] __attribute__((aligned(16))) = { 0, 2, 4, 6, 8, 10, 12, 14, 1, 3, 5, 7, 9, 11, 13, 15 };
#endif

void commonProcessNeon(int8_t *bfft, int32_t deltaMillis, int32_t opt) {
	float *fft = floatBuffer;
	const float *multiplier = floatBuffer + QUARTER_FFT_SIZE;
	uint8_t *processedData = (uint8_t*)(floatBuffer + (QUARTER_FFT_SIZE << 1));
	float *previousM = ::previousM;

	float tmpCoefNew = commonCoefNew * (float)deltaMillis;
	if (tmpCoefNew > 1.0f)
		tmpCoefNew = 1.0f;
#ifdef FPLAY_ARM
	const float32x4_t coefNew = vdupq_n_f32(tmpCoefNew);
	const float32x4_t coefOld = vdupq_n_f32(1.0f - tmpCoefNew);
#else
	const __m128 coefNew = _mm_set1_ps(tmpCoefNew);
	const __m128 coefOld = _mm_set1_ps(1.0f - tmpCoefNew);
#endif

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
		//Even though x86 has only 8 128-bits registers, after examining the disassembly,
		//I confirmed all this code fits into 8 128-bits registers (after the compiler
		//gracefully reordered a few lines)! :)
		//(x86-64 has 16 registers, so, no worries)
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i += 8) {
			//when (opt & IGNORE_INPUT) we do not compute m (we use its previous value)
			__m128 m0 = _mm_load_ps(previousM);
			__m128 m1 = _mm_load_ps(previousM + 4);
			previousM += 8;

			__m128 old0 = _mm_load_ps(fft);
			__m128 old1 = _mm_load_ps(fft + 4);

			__m128 geq0 = _mm_cmple_ps(old0, m0); //geq = (m >= old)
			__m128 geq1 = _mm_cmple_ps(old1, m1);

			old0 = _mm_mul_ps(old0, coefOld); //old *= coefOld
			old1 = _mm_mul_ps(old1, coefOld);

			__m128 tmpMul = _mm_mul_ps(m0, coefNew); //old += m * coefNew
			old0 = _mm_add_ps(old0, tmpMul);
			tmpMul = _mm_mul_ps(m1, coefNew);
			old1 = _mm_add_ps(old1, tmpMul);

			//if m >= old, use m, otherwise, use old (which now contains (coefNew * m) + (coefOld * old))
			m0 = _mm_and_ps(m0, geq0);
			m1 = _mm_and_ps(m1, geq1);

			__m128 tmpFF = _mm_castsi128_ps(_mm_cmpeq_epi32(_mm_castps_si128(geq0), _mm_castps_si128(geq0))); //tmpFF = { 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff }
			geq0 = _mm_andnot_ps(geq0, tmpFF); //geq = ~geq
			geq1 = _mm_andnot_ps(geq1, tmpFF);

			old0 = _mm_and_ps(old0, geq0);
			old1 = _mm_and_ps(old1, geq1);

			m0 = _mm_or_ps(m0, old0);
			m1 = _mm_or_ps(m1, old1);

			_mm_store_ps(fft, m0);
			_mm_store_ps(fft + 4, m1);
			fft += 8;

			//m goes from 0 to 32768+ (inclusive)
			const __m128i v0 = _mm_srli_epi32(_mm_cvttps_epi32(m0), 7);
			const __m128i v1 = _mm_srli_epi32(_mm_cvttps_epi32(m1), 7);

			//since v goes from 0 to 256+ (inclusive) we do not need
			//to worry about this signed 32->16-bit conversion
			const __m128i v01_16 = _mm_packs_epi32(v0, v1);

			//16->8-bit conversion MUST be unsigned though
			const __m128i v01_8 = _mm_packus_epi16(v01_16, v01_16);

			//store 8 bytes at once
			_mm_store_sd((double*)processedData, _mm_castsi128_pd(v01_8));
			processedData += 8;
		}
#endif
	} else {
#ifdef FPLAY_ARM
		const float32x4_t eight = vdupq_n_f32(8.0f);
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
#ifdef FPLAY_64_BITS
		const __m128 eight = _mm_set1_ps(8.0f);
		const __m128i indices = _mm_load_si128((const __m128i*)evenOddIndices);
#endif
		//Even though x86 has only 8 128-bits registers, after examining the disassembly,
		//I confirmed all this code fits into 8 128-bits registers (after the compiler
		//gracefully reordered a few lines)! :)
		//(x86-64 has 16 registers, so, no worries)
		for (int32_t i = 0; i < QUARTER_FFT_SIZE; i += 8) {
			//all this initial code is just to compute m

			//bfft[i] stores values from 0 to -128/127 (inclusive)
			intBuffer[0] = ((int32_t*)bfft)[0];
			intBuffer[1] = ((int32_t*)bfft)[1];
			intBuffer[2] = ((int32_t*)bfft)[2];
			intBuffer[3] = ((int32_t*)bfft)[3];
			bfft += 16;

			//re_im_interleaved = { re, im, re, im, re, im, re, im, re, im, re, im, re, im, re, im }
			const __m128i re_im_interleaved = _mm_load_si128((const __m128i*)intBuffer);
#ifdef FPLAY_64_BITS
			//re_im = { re, re, re, re, re, re, re, re, im, im, im, im, im, im, im, im }
			const __m128i re_im = _mm_shuffle_epi8(re_im_interleaved, indices);
			const __m128i re16 = _mm_cvtepi8_epi16(re_im); //convert the lower 8 int8_t's into 8 int16_t's
			const __m128i im16 = _mm_cvtepi8_epi16(_mm_srli_si128(re_im, 8)); //convert the upper 8 int8_t's into 8 int16_t's
			const __m128 re0 = _mm_cvtepi32_ps(_mm_cvtepi16_epi32(re16)); //convert the lower 4 int16_t's into 4 int32_t's, and then into 4 float's
			const __m128 re1 = _mm_cvtepi32_ps(_mm_cvtepi16_epi32(_mm_srli_si128(re16, 8))); //convert the upper 4 int16_t's into 4 int32_t's, and then into 4 float's
			const __m128 im0 = _mm_cvtepi32_ps(_mm_cvtepi16_epi32(im16)); //convert the lower 4 int16_t's into 4 int32_t's, and then into 4 float's
			const __m128 im1 = _mm_cvtepi32_ps(_mm_cvtepi16_epi32(_mm_srli_si128(im16, 8))); //convert the upper 4 int16_t's into 4 int32_t's, and then into 4 float's
#else
			//re_im = { re, re, re, re, re, re, re, re, im, im, im, im, im, im, im, im }
			const __m128i re_im = _mm_shuffle_epi8(re_im_interleaved, _mm_load_si128((const __m128i*)evenOddIndices));
			//without SSE4.1 we must do all sign extensions by hand
			const __m128i tmpZero = _mm_setzero_si128();
			const __m128i tmpSignExtension = _mm_cmpgt_epi8(tmpZero, re_im); //tmpSignExtension = (0 > re_im ? 0xFF : 0)
			const __m128i re16 = _mm_unpacklo_epi8(re_im, tmpSignExtension); //convert the lower 8 int8_t's into 8 int16_t's
			const __m128i im16 = _mm_unpackhi_epi8(re_im, tmpSignExtension); //convert the upper 8 int8_t's into 8 int16_t's
			const __m128i tmpSignExtensionRe = _mm_cmpgt_epi16(tmpZero, re16); //tmpSignExtensionRe = (0 > re16 ? 0xFFFF : 0)
			const __m128 re0 = _mm_cvtepi32_ps(_mm_unpacklo_epi16(re16, tmpSignExtensionRe)); //convert the lower 4 int16_t's into 4 int32_t's, and then into 4 float's
			const __m128 re1 = _mm_cvtepi32_ps(_mm_unpackhi_epi16(re16, tmpSignExtensionRe)); //convert the upper 4 int16_t's into 4 int32_t's, and then into 4 float's
			const __m128i tmpSignExtensionIm = _mm_cmpgt_epi16(tmpZero, im16); //tmpSignExtensionIm = (0 > im16 ? 0xFFFF : 0)
			const __m128 im0 = _mm_cvtepi32_ps(_mm_unpacklo_epi16(im16, tmpSignExtensionIm)); //convert the lower 4 int16_t's into 4 int32_t's, and then into 4 float's
			const __m128 im1 = _mm_cvtepi32_ps(_mm_unpackhi_epi16(im16, tmpSignExtensionIm)); //convert the upper 4 int16_t's into 4 int32_t's, and then into 4 float's
#endif
			//according to https://developer.android.com/ndk/guides/abis.html#86-64
			//only x86-64 android devices have SSE4.1 (supporting integer multiplication)
			//that's why we had to convert the int's into float's up here
			//even when using SSE4.1 we cannot properly perform the required integer
			//multiplication (_mm_mul_epi32 does not fit and the docs say _mm_mullo_epi32 is
			//slower than _mm_mul_ps)

			//amplSq = (re * re) + (im * im)
			const __m128 amplSq0 = _mm_add_ps(_mm_mul_ps(re0, re0), _mm_mul_ps(im0, im0));
			const __m128 amplSq1 = _mm_add_ps(_mm_mul_ps(re1, re1), _mm_mul_ps(im1, im1));

#ifdef FPLAY_64_BITS
			const __m128 gt0 = _mm_cmplt_ps(eight, amplSq0); //gt = (amplSq > 8)
			const __m128 gt1 = _mm_cmplt_ps(eight, amplSq1);
#else
			const __m128 eight = _mm_set1_ps(8.0f);
			const __m128 gt0 = _mm_cmplt_ps(eight, amplSq0); //gt = (amplSq > 8)
			const __m128 gt1 = _mm_cmplt_ps(eight, amplSq1);
#endif
			//according to Intel docs, _mm_sqrt_ps computes sqrt(x) using all 23 bits,
			//whereas _mm_rsqrt_ps computes 1/sqrt(x) using fewer bits (but with a maximum
			//relative error of less than 1.5*2^-12)
			//since _mm_rsqrt_ps is way faster than _mm_sqrt_ps, I decided to compute x * 1/sqrt(x)
			__m128 m0 = _mm_mul_ps(amplSq0, _mm_rsqrt_ps(amplSq0));
			__m128 m1 = _mm_mul_ps(amplSq1, _mm_rsqrt_ps(amplSq1));

			const __m128 multiplier0 = _mm_load_ps(multiplier);
			const __m128 multiplier1 = _mm_load_ps(multiplier + 4);
			multiplier += 8;

			//this will zero out all m's <= 8 and will also remove any NaN's that may have
			//arisen from 1/sqrt(x), as 1/0 = Infinity, and x * Infinity = NaN
			m0 = _mm_and_ps(m0, gt0);
			m1 = _mm_and_ps(m1, gt1);

			m0 = _mm_mul_ps(m0, multiplier0); //m = ((amplSq <= 8) ? 0 : (multiplier * sqrtf(amplSq)));
			m1 = _mm_mul_ps(m1, multiplier1);

			_mm_store_ps(previousM, m0);
			_mm_store_ps(previousM + 4, m1);
			previousM += 8;

			//now that m has been computed, we can finally proceed and compute the
			//weighted/fitered amplitude (using our coefficients)
			//the code from this point on is the same one used inside if ((opt & IGNORE_INPUT))

			__m128 old0 = _mm_load_ps(fft);
			__m128 old1 = _mm_load_ps(fft + 4);

			__m128 geq0 = _mm_cmple_ps(old0, m0); //geq = (m >= old)
			__m128 geq1 = _mm_cmple_ps(old1, m1);

			old0 = _mm_mul_ps(old0, coefOld); //old *= coefOld
			old1 = _mm_mul_ps(old1, coefOld);

			__m128 tmpMul = _mm_mul_ps(m0, coefNew); //old += m * coefNew
			old0 = _mm_add_ps(old0, tmpMul);
			tmpMul = _mm_mul_ps(m1, coefNew);
			old1 = _mm_add_ps(old1, tmpMul);

			//if m >= old, use m, otherwise, use old (which now contains (coefNew * m) + (coefOld * old))
			m0 = _mm_and_ps(m0, geq0);
			m1 = _mm_and_ps(m1, geq1);

			__m128 tmpFF = _mm_castsi128_ps(_mm_cmpeq_epi32(_mm_castps_si128(geq0), _mm_castps_si128(geq0))); //tmpFF = { 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff }
			geq0 = _mm_andnot_ps(geq0, tmpFF); //geq = ~geq
			geq1 = _mm_andnot_ps(geq1, tmpFF);

			old0 = _mm_and_ps(old0, geq0);
			old1 = _mm_and_ps(old1, geq1);

			m0 = _mm_or_ps(m0, old0);
			m1 = _mm_or_ps(m1, old1);

			_mm_store_ps(fft, m0);
			_mm_store_ps(fft + 4, m1);
			fft += 8;

			//m goes from 0 to 32768+ (inclusive)
			const __m128i v0 = _mm_srli_epi32(_mm_cvttps_epi32(m0), 7);
			const __m128i v1 = _mm_srli_epi32(_mm_cvttps_epi32(m1), 7);

			//since v goes from 0 to 256+ (inclusive) we do not need
			//to worry about this signed 32->16-bit conversion
			const __m128i v01_16 = _mm_packs_epi32(v0, v1);

			//16->8-bit conversion MUST be unsigned though
			const __m128i v01_8 = _mm_packus_epi16(v01_16, v01_16);

			//store 8 bytes at once
			_mm_store_sd((double*)processedData, _mm_castsi128_pd(v01_8));
			processedData += 8;
		}
#endif
	}
}
