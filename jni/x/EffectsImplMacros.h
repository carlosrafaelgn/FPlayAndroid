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

typedef void (*EFFECTPROC)(int16_t* buffer, uint32_t sizeInFrames);

#define MAX_ALLOWED_SAMPLE_VALUE 31000.0f //31000/32768 = 0.946 = -0.48dB

#define x_n1_L samples[0]
#define x_n1_R samples[1]
#define y_n1_L samples[2]
#define y_n1_R samples[3]
#define x_n2_L samples[4]
#define x_n2_R samples[5]
#define y_n2_L samples[6]
#define y_n2_R samples[7]

#define equalizerX86() \
	/* since this is a cascade filter, band0's output is band1's input and so on.... */ \
	for (int32_t i = 0; i < equalizerMaxBandCount; i++, samples += 8) { \
		/* y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2) */ \
		/* but, since our a2 was negated and b1 = a1, the formula becomes: */ \
		/* y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2) */ \
		\
		/* local = { x_n1_L, x_n1_R, y_n1_L, y_n1_R } */ \
		__m128 local = _mm_load_ps(samples); \
		\
		/* local2 = { x_n2_L, x_n2_R, y_n2_L, y_n2_R } */ \
		__m128 local2 = _mm_load_ps(samples + 4); \
		\
		/* b0_b1 = { b0, b0, b1, b1 } */ \
		__m128 b0_b1 = _mm_load_ps(equalizerCoefs + (i << 3)); \
		/* b2_a2 = { b2, b2, -a2, -a2 } */ \
		__m128 b2_a2 = _mm_load_ps(equalizerCoefs + (i << 3) + 4); \
		\
		/*
		x_n2_L = local_x_n1_L;
		x_n2_R = local_x_n1_R;
		y_n2_L = local_y_n1_L;
		y_n2_R = local_y_n1_R; */ \
		_mm_store_ps(samples + 4, local); \
		\
		/* inLR = { L, R, x_n1_L, x_n1_R } */ \
		inLR = _mm_movelh_ps(inLR, local); \
		/* localI = { 0, 0, y_n1_L, y_n1_R } */ \
		__m128i localI = _mm_slli_si128(_mm_srli_si128(*((__m128i*)&local), 8), 8); \
		/* inLR = { L, R, x_n1_L - y_n1_L, x_n1_R - y_n1_R } */ \
		inLR = _mm_sub_ps(inLR, *((__m128*)&localI)); \
		\
		/* b0_b1 = { b0 * L, b0 * R, b1 * (x_n1_L - y_n1_L), b1 * (x_n1_R - y_n1_R) } */ \
		b0_b1 = _mm_mul_ps(b0_b1, inLR); \
		\
		/* b2_a2 = { b2 * x_n2_L, b2 * x_n2_R, -a2 * y_n2_L, -a2 * y_n2_R } */ \
		b2_a2 = _mm_mul_ps(b2_a2, local2); \
		\
		/* b0_b1 = { (b0 * L) + (b2 * x_n2_L), (b0 * R) + (b2 * x_n2_R), (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R) } */ \
		b0_b1 = _mm_add_ps(b0_b1, b2_a2); \
		/* inLR = { L, R, (b0 * L) + (b2 * x_n2_L), (b0 * R) + (b2 * x_n2_R) } */ \
		inLR = _mm_movelh_ps(inLR, b0_b1); \
		/* tmp = { 0, 0, (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R) } */ \
		__m128i b0_b1I = _mm_slli_si128(_mm_srli_si128(*((__m128i*)&b0_b1), 8), 8); \
		\
		/* inLR = { L, R, outL, outR } */ \
		inLR = _mm_add_ps(inLR, *((__m128*)&b0_b1I)); \
		\
		/*
		x_n1_L = inL;
		x_n1_R = inR;
		y_n1_L = outL;
		y_n1_R = outR;*/ \
		_mm_store_ps(samples, inLR); \
		\
		/*
		inL = outL;
		inR = outR; */ \
		inLR = _mm_movehl_ps(inLR, inLR); \
	}

#define virtualizerX86()

#ifdef FPLAY_32_BITS
#define floatToShortX86() \
	/*
	inL *= gainClip;
	inR *= gainClip; */ \
	inLR = _mm_mul_ps(inLR, gainClip); \
	\
	if (effectsMustReduceGain) { \
		effectsGainClip[0] *= effectsGainReductionPerFrame[0]; \
		effectsGainClip[1] = effectsGainClip[0]; \
		gainClip = _mm_load_ps(effectsGainClip); \
	} else if (effectsFramesBeforeRecoveringGain <= 0) { \
		effectsGainClip[0] *= effectsGainRecoveryPerFrame[0]; \
		if (effectsGainClip[0] > 1.0f) \
			effectsGainClip[0] = 1.0f; \
		effectsGainClip[1] = effectsGainClip[0]; \
		gainClip = _mm_load_ps(effectsGainClip); \
	} \
	\
	/*
	instead of doing the classic a & 0x7FFFFFFF in order to achieve the abs value,
	I decided to do this (which does not require external memory loads)
	maxAbsSample = max(maxAbsSample, max(0 - inLR, inLR)) */ \
	maxAbsSample = _mm_max_ps(maxAbsSample, _mm_max_ps(_mm_sub_ps(_mm_setzero_ps(), inLR), inLR)); \
	\
	/*
	the final output is the last band's output (or its next band's input)
	const int32_t iL = (int32_t)inL;
	const int32_t iR = (int32_t)inR; */ \
	__m128i iLR = _mm_cvtps_epi32(inLR); \
	\
	/*
	buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (int16_t)iL)); \
	buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (int16_t)iR)); */ \
	iLR = _mm_packs_epi32(iLR, iLR); \
	_mm_store_ss((float*)buffer, *((__m128*)&iLR)); \
	\
	buffer += 2;
#else
#define floatToShortX86() \
	/*
	inL *= gainClip;
	inR *= gainClip; */ \
	inLR = _mm_mul_ps(inLR, gainClip); \
	\
	/* gainClip *= effectsGainReductionPerFrame or effectsGainRecoveryPerFrame or 1.0f;
	if (gainClip > 1.0f)
		gainClip = 1.0f; */ \
	gainClip = _mm_mul_ps(gainClip, gainClipMul); \
	gainClip = _mm_min_ps(gainClip, one); \
	\
	/*
	maxAbsSample = max(maxAbsSample, abs(inLR)) */ \
	maxAbsSample = _mm_max_ps(maxAbsSample, _mm_and_ps(andAbs, inLR)); \
	\
	/*
	the final output is the last band's output (or its next band's input)
	const int32_t iL = (int32_t)inL;
	const int32_t iR = (int32_t)inR; */ \
	__m128i iLR = _mm_cvtps_epi32(inLR); \
	\
	/*
	buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (int16_t)iL)); \
	buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (int16_t)iR)); */ \
	iLR = _mm_packs_epi32(iLR, iLR); \
	_mm_store_ss((float*)buffer, *((__m128*)&iLR)); \
	\
	buffer += 2;
#endif
	
#define footerX86() \
	if (!effectsGainEnabled) { \
		effectsMustReduceGain = 0; \
		effectsFramesBeforeRecoveringGain = 0x7FFFFFFF; \
		return; \
	} \
	\
	_mm_store_ps(effectsGainClip, gainClip); \
	_mm_store_ps((float*)effectsTemp, maxAbsSample); \
	if (((float*)effectsTemp)[0] > MAX_ALLOWED_SAMPLE_VALUE || ((float*)effectsTemp)[1] > MAX_ALLOWED_SAMPLE_VALUE) { \
		effectsMustReduceGain = 1; \
		effectsFramesBeforeRecoveringGain = dstSampleRate << 2; /* wait some time before starting to recover the gain */ \
	} else { \
		effectsMustReduceGain = 0; \
		if (effectsGainClip[0] >= 1.0f) \
			effectsFramesBeforeRecoveringGain = 0x7FFFFFFF; \
	}
	
#define equalizerPlain() \
	/* since this is a cascade filter, band0's output is band1's input and so on.... */ \
	for (int32_t i = 0; i < equalizerMaxBandCount; i++, samples += 8) { \
		/*
		y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
		but, since our a2 was negated and b1 = a1, the formula becomes:
		y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2) */ \
		const float b0 = equalizerCoefs[(i << 3)]; \
		const float b1_a1 = equalizerCoefs[(i << 3) + 2]; \
		const float b2 = equalizerCoefs[(i << 3) + 4]; \
		const float a2 = equalizerCoefs[(i << 3) + 6]; \
		\
		const float local_x_n1_L = x_n1_L; \
		const float local_x_n1_R = x_n1_R; \
		\
		const float local_y_n1_L = y_n1_L; \
		const float local_y_n1_R = y_n1_R; \
		\
		const float outL = (b0 * inL) + (b1_a1 * (local_x_n1_L - local_y_n1_L)) + (b2 * x_n2_L) + (a2 * y_n2_L); \
		const float outR = (b0 * inR) + (b1_a1 * (local_x_n1_R - local_y_n1_R)) + (b2 * x_n2_R) + (a2 * y_n2_R); \
		\
		x_n2_L = local_x_n1_L; \
		x_n2_R = local_x_n1_R; \
		y_n2_L = local_y_n1_L; \
		y_n2_R = local_y_n1_R; \
		\
		x_n1_L = inL; \
		x_n1_R = inR; \
		y_n1_L = outL; \
		y_n1_R = outR; \
		\
		inL = outL; \
		inR = outR; \
	}

#define virtualizerPlain()

#define floatToShortPlain() \
	inL *= gainClip; \
	inR *= gainClip; \
	\
	if (effectsMustReduceGain) { \
		gainClip *= effectsGainReductionPerFrame[0]; \
	} else if (effectsFramesBeforeRecoveringGain <= 0) { \
		gainClip *= effectsGainRecoveryPerFrame[0]; \
		if (gainClip > 1.0f) \
			gainClip = 1.0f; \
	} \
	\
	const float tmpAbsL = abs(inL); \
	if (maxAbsSample < tmpAbsL) \
		maxAbsSample = tmpAbsL; \
	const float tmpAbsR = abs(inR); \
	if (maxAbsSample < tmpAbsR) \
		maxAbsSample = tmpAbsR; \
	\
	/* the final output is the last band's output (or its next band's input) */ \
	const int32_t iL = (int32_t)inL; \
	const int32_t iR = (int32_t)inR; \
	buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (int16_t)iL)); \
	buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (int16_t)iR)); \
	\
	buffer += 2;

#define footerPlain() \
	if (!effectsGainEnabled) { \
		effectsMustReduceGain = 0; \
		effectsFramesBeforeRecoveringGain = 0x7FFFFFFF; \
		return; \
	} \
	\
	effectsGainClip[0] = gainClip; \
	if (maxAbsSample > MAX_ALLOWED_SAMPLE_VALUE) { \
		effectsMustReduceGain = 1; \
		effectsFramesBeforeRecoveringGain = dstSampleRate << 2; /* wait some time before starting to recover the gain */ \
	} else { \
		effectsMustReduceGain = 0; \
		if (effectsGainClip[0] >= 1.0f) \
			effectsFramesBeforeRecoveringGain = 0x7FFFFFFF; \
	}

#define equalizerNeonOld() \
	/* since this is a cascade filter, band0's output is band1's input and so on.... */ \
	for (int32_t i = 0; i < equalizerMaxBandCount; i++, samples += 8) { \
		/*
		y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
		but, since our a2 was negated and b1 = a1, the formula becomes:
		y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2) */ \
		\
		/* b0_b1 = { b0, b0, b1, b1 } */ \
		float32x4_t b0_b1 = vld1q_f32(equalizerCoefs + (i << 3)); \
		/* b2_a2 = { b2, b2, -a2, -a2 } */ \
		float32x4_t b2_a2 = vld1q_f32(equalizerCoefs + (i << 3) + 4); \
		\
		/* local = { x_n1_L, x_n1_R, y_n1_L, y_n1_R } */ \
		float32x4_t local = vld1q_f32(samples); \
		\
		/* inLR4 = { L, R, x_n1_L - y_n1_L, x_n1_R - y_n1_R } */ \
		float32x4_t inLR4 = vcombine_f32(inLR, vsub_f32(vget_low_f32(local), vget_high_f32(local))); \
		\
		/* tmp2 = { x_n2_L, x_n2_R, y_n2_L, y_n2_R } */ \
		float32x4_t tmp2 = vld1q_f32(samples + 4); \
		\
		/* b0_b1 = { b0 * L, b0 * R, b1 * (x_n1_L - y_n1_L), b1 * (x_n1_R - y_n1_R) } */ \
		b0_b1 = vmulq_f32(b0_b1, inLR4); \
		\
		/* b2_a2 = { b2 * x_n2_L, b2 * x_n2_R, -a2 * y_n2_L, -a2 * y_n2_R } */ \
		b2_a2 = vmulq_f32(b2_a2, tmp2); \
		\
		/* b0_b1 = { (b0 * L) + (b2 * x_n2_L), (b0 * R) + (b2 * x_n2_R), (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R) } */ \
		b0_b1 = vaddq_f32(b0_b1, b2_a2); \
		\
		/*
		x_n2_L = local_x_n1_L;
		x_n2_R = local_x_n1_R;
		y_n2_L = local_y_n1_L;
		y_n2_R = local_y_n1_R; */ \
		vst1q_f32(samples + 4, local); \
		\
		/* outLR = { outL, outR } */ \
		float32x2_t outLR = vadd_f32(vget_low_f32(b0_b1), vget_high_f32(b0_b1)); \
		\
		/*
		x_n1_L = inL;
		x_n1_R = inR;
		y_n1_L = outL;
		y_n1_R = outR; */ \
		vst1q_f32(samples, vcombine_f32(inLR, outLR)); \
		\
		/*
		inL = outL;
		inR = outR; */ \
		inLR = outLR; \
	}

#define equalizerNeon() \
	/* since this is a cascade filter, band0's output is band1's input and so on.... */ \
	for (int32_t i = 0; i < equalizerMaxBandCount; i++, samples += 8) { \
		/*
		y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
		but, since our a2 was negated and b1 = a1, the formula becomes:
		y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2) */ \
		\
		/* local = { x_n1_L, x_n1_R, y_n1_L, y_n1_R } */ \
		const float32x4_t local = vld1q_f32(samples); \
		\
		/* local2 = { x_n2_L, x_n2_R, y_n2_L, y_n2_R } */ \
		const float32x4_t local2 = vld1q_f32(samples + 4); \
		\
		/* b0_b1 = { b0, b0, b1, b1 } */ \
		const float32x4_t b0_b1 = vld1q_f32(equalizerCoefs + (i << 3)); \
		/* b2_a2 = { b2, b2, -a2, -a2 } */ \
		const float32x4_t b2_a2 = vld1q_f32(equalizerCoefs + (i << 3) + 4); \
		\
		/* tmp4 = { b0 * L, b0 * R, b1 * (x_n1_L - y_n1_L), b1 * (x_n1_R - y_n1_R) } */ \
		float32x4_t tmp4 = vmulq_f32(b0_b1, vcombine_f32(inLR, vsub_f32(vget_low_f32(local), vget_high_f32(local)))); \
		/* tmp4 += { b2 * x_n2_L, b2 * x_n2_R, -a2 * y_n2_L, -a2 * y_n2_R } */ \
		tmp4 = vmlaq_f32(tmp4, b2_a2, local2); \
		\
		/*
		x_n2_L = local_x_n1_L;
		x_n2_R = local_x_n1_R;
		y_n2_L = local_y_n1_L;
		y_n2_R = local_y_n1_R; */ \
		vst1q_f32(samples + 4, local); \
		\
		/* outLR = { outL, outR } */ \
		const float32x2_t outLR = vadd_f32(vget_low_f32(tmp4), vget_high_f32(tmp4)); \
		\
		/*
		x_n1_L = inL;
		x_n1_R = inR;
		y_n1_L = outL;
		y_n1_R = outR; */ \
		vst1q_f32(samples, vcombine_f32(inLR, outLR)); \
		\
		/*
		inL = outL;
		inR = outR; */ \
		inLR = outLR; \
	}

#define floatToShortNeon() \
	/*
	inL *= gainClip;
	inR *= gainClip; */ \
	inLR = vmul_f32(inLR, gainClip); \
	\
	/* gainClip *= effectsGainReductionPerFrame or effectsGainRecoveryPerFrame or 1.0f;
	if (gainClip > 1.0f)
		gainClip = 1.0f; */ \
	gainClip = vmul_f32(gainClip, gainClipMul); \
	gainClip = vmin_f32(gainClip, one); \
	\
	maxAbsSample = vmax_f32(maxAbsSample, vabs_f32(inLR)); \
	\
	/*
	the final output is the last band's output (or its next band's input)
	const int32_t iL = (int32_t)inL;
	const int32_t iR = (int32_t)inR; */ \
	int32x2_t iLR = vcvt_s32_f32(inLR); \
	\
	/*
	buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (int16_t)iL));
	buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (int16_t)iR)); */ \
	int16x4_t iLRshort = vqmovn_s32(vcombine_s32(iLR, iLR)); \
	vst1_lane_s32((int32_t*)buffer, vreinterpret_s32_s16(iLRshort), 0); \
	\
	buffer += 2;

#define virtualizerNeon()

#define footerNeon() \
	if (!effectsGainEnabled) { \
		effectsMustReduceGain = 0; \
		effectsFramesBeforeRecoveringGain = 0x7FFFFFFF; \
		return; \
	} \
	\
	vst1_f32(effectsGainClip, gainClip); \
	vst1_f32((float*)effectsTemp, maxAbsSample); \
	if (((float*)effectsTemp)[0] > MAX_ALLOWED_SAMPLE_VALUE || ((float*)effectsTemp)[1] > MAX_ALLOWED_SAMPLE_VALUE) { \
		effectsMustReduceGain = 1; \
		effectsFramesBeforeRecoveringGain = dstSampleRate << 2; /* wait some time before starting to recover the gain */ \
	} else { \
		effectsMustReduceGain = 0; \
		if (effectsGainClip[0] >= 1.0f) \
			effectsFramesBeforeRecoveringGain = 0x7FFFFFFF; \
	}
