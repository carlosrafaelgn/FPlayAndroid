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

typedef uint32_t (*RESAMPLEPROC)(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);

#ifdef FPLAY_ARM
//extern uint32_t resampleLagrangeNeon(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);
extern uint32_t resampleLagrangeNeonINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);
#endif

uint32_t resamplePendingAdvances, resampleCoeffLen, resampleCoeffIdx, resampleAdvanceIdx;
//I gave up trying to optimize the integer version under x86 architecture...
//up to SSE4.2 it lacks SEVERAL integer-related features present in NEON
//(that's why I fell back to the float point version)
#ifdef FPLAY_X86
float *resampleCoeff;
#else
int32_t *resampleCoeffINT;
#endif
uint32_t *resampleAdvance;
#ifdef FPLAY_X86
float resampleY[20] __attribute__((aligned(16)));
static float *resampleCoeffOriginal;
#else
int32_t resampleYINT[20] __attribute__((aligned(16)));
static int32_t *resampleCoeffOriginalINT;
#endif
static RESAMPLEPROC resampleProc;

uint32_t resampleNull(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//nothing to be done but copying from source to destination
	if (srcSizeInFrames > dstSizeInFrames)
		srcSizeInFrames = dstSizeInFrames;
	memcpy(dstBuffer, srcBuffer, srcSizeInFrames << 2);
	srcFramesUsed = srcSizeInFrames;
	return srcSizeInFrames;
}

uint32_t resampleNullMono(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	if (srcSizeInFrames > dstSizeInFrames)
		srcSizeInFrames = dstSizeInFrames;
	else
		dstSizeInFrames = srcSizeInFrames;
	while (dstSizeInFrames--) {
		const int16_t i = *srcBuffer++;
		*dstBuffer++ = i;
		*dstBuffer++ = i;
	}
	srcFramesUsed = srcSizeInFrames;
	return srcSizeInFrames;
}

//------------------------------------------------------------------------
//the idea behind all this:
//
//use a 10th order polynomial to generate another waveform by
//interpolating the values between frames 4 and 5
//
//we are assuming x0 = 0, x1 = x1 ... x9 = 9, allowing us for a nice
//optimization
//
//past                                   future
//  y0 y1 y2 y3 y4  present  y5 y6 y7 y8 y9
//  x0 x1 x2 x3 x4  present  x5 x6 x7 x8 x9
//
//I decided to compute the coefficients using Lagrange polynomials:
//https://en.wikipedia.org/wiki/Lagrange_polynomial
//
//the results are WAAAAAYYYYYY better than cubic interpolation (using
//any technic you desire) and the code is far simpler than the codes
//using sinc, polyphase filter banks or dft/fft
//
//after several tests, I couldn't tell the difference between a sound
//interpolated using my interpolation with Lagrange polynomials, and one
//interpolated using those more sofisticated technics... well, at least
//not on the cases I tested:
//44100 <-> 48000
//32000 -> 44100/48000
//22050 -> 44100/48000
//16000 -> 44100/48000
//11025 -> 44100/48000
//8000  -> 44100/48000
//
//(nevertheless, I will keep testing... maybe I will have to give in and
//use those more complicated technics)
//
//since it would be too expensive to compute those coefficients on the
//fly, I decided to use a lookup table, created by resampleComputeCoeffs()
//
//I hope all this works well :)
//------------------------------------------------------------------------
#ifdef FPLAY_X86
uint32_t resampleLagrange(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		for (int32_t i = 0; i < 9; i++)
			((uint64_t*)resampleY)[i] = ((uint64_t*)resampleY)[i + 1];
		resampleY[18] = (float)srcBuffer[0];
		resampleY[19] = (float)srcBuffer[1];

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	__m128 y0_y1 = _mm_load_ps(resampleY);
	__m128 y2_y3 = _mm_load_ps(resampleY + 4);
	__m128 y4_y5 = _mm_load_ps(resampleY + 8);
	__m128 y6_y7 = _mm_load_ps(resampleY + 12);
	__m128 y8_y9 = _mm_load_ps(resampleY + 16);

	while (usedDst < dstSizeInFrames) {
		const float* const coeff = resampleCoeff + resampleCoeffIdx;
		__m128 outLR = _mm_mul_ps(_mm_load_ps(coeff), y0_y1);
		outLR = _mm_add_ps(outLR, _mm_mul_ps(_mm_load_ps(coeff + 4), y2_y3));
		outLR = _mm_add_ps(outLR, _mm_mul_ps(_mm_load_ps(coeff + 8), y4_y5));
		outLR = _mm_add_ps(outLR, _mm_mul_ps(_mm_load_ps(coeff + 12), y6_y7));
		outLR = _mm_add_ps(outLR, _mm_mul_ps(_mm_load_ps(coeff + 16), y8_y9));
		__m128 outLRhi;
		outLR = _mm_add_ps(outLR, _mm_movehl_ps(outLRhi, outLR));
		__m128i outLRI = _mm_cvtps_epi32(outLR);
		outLRI = _mm_packs_epi32(outLRI, outLRI);
		_mm_store_ss((float*)dstBuffer, *((__m128*)&outLRI));
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
			y0_y1 = _mm_shuffle_ps(y0_y1, y2_y3, 78); //78 = 0100 1110 (from hi to lo: b1 b0 a3 a2)
			y2_y3 = _mm_shuffle_ps(y2_y3, y4_y5, 78);
			y4_y5 = _mm_shuffle_ps(y4_y5, y6_y7, 78);
			y6_y7 = _mm_shuffle_ps(y6_y7, y8_y9, 78);
			__m128 inLR;
			inLR = _mm_cvtpi32_ps(inLR, *((__m64*)effectsTemp));
			y8_y9 = _mm_shuffle_ps(y8_y9, inLR, 78);

			usedSrc++;
			srcBuffer += 2;

			if (usedSrc >= srcSizeInFrames) {
				_mm_store_ps(resampleY, y0_y1);
				_mm_store_ps(resampleY + 4, y2_y3);
				_mm_store_ps(resampleY + 8, y4_y5);
				_mm_store_ps(resampleY + 12, y6_y7);
				_mm_store_ps(resampleY + 16, y8_y9);

				srcFramesUsed = usedSrc;
				return usedDst;
			}
		}
	}

	_mm_store_ps(resampleY, y0_y1);
	_mm_store_ps(resampleY + 4, y2_y3);
	_mm_store_ps(resampleY + 8, y4_y5);
	_mm_store_ps(resampleY + 12, y6_y7);
	_mm_store_ps(resampleY + 16, y8_y9);

	srcFramesUsed = usedSrc;
	return usedDst;
}
#else
uint32_t resampleLagrangeINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		for (int32_t i = 0; i < 9; i++)
			((uint64_t*)resampleYINT)[i] = ((uint64_t*)resampleYINT)[i + 1];
		resampleYINT[18] = (int32_t)srcBuffer[0];
		resampleYINT[19] = (int32_t)srcBuffer[1];

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	while (usedDst < dstSizeInFrames) {
		const int32_t* const coeff = resampleCoeffINT + resampleCoeffIdx;
		//although I could not find a built-in/intrinsic function to perform
		//int64 = int32 * int32, I noticed (by reading the disassembly)
		//that GCC does a really good job at optimizing all these multiplications,
		//and that this type cast actually generates an assembly code corresponding to
		//int64 = int32 * int32
		int64_3232 outL;
		outL.v = (
			(((int64_t)resampleYINT[0] * (int64_t)coeff[0]) +
			((int64_t)resampleYINT[2] * (int64_t)coeff[2]) +
			((int64_t)resampleYINT[4] * (int64_t)coeff[4]) +
			((int64_t)resampleYINT[6] * (int64_t)coeff[6]) +
			((int64_t)resampleYINT[8] * (int64_t)coeff[8]) +
			((int64_t)resampleYINT[10] * (int64_t)coeff[10]) +
			((int64_t)resampleYINT[12] * (int64_t)coeff[12]) +
			((int64_t)resampleYINT[14] * (int64_t)coeff[14]) +
			((int64_t)resampleYINT[16] * (int64_t)coeff[16]) +
			((int64_t)resampleYINT[18] * (int64_t)coeff[18])) << 2
		);
		*dstBuffer++ = ((outL.h >= 32767) ? 32767 : ((outL.h <= -32768) ? -32768 : (int16_t)outL.h));
		int64_3232 outR;
		outR.v = (
			(((int64_t)resampleYINT[1] * (int64_t)coeff[1]) +
			((int64_t)resampleYINT[3] * (int64_t)coeff[3]) +
			((int64_t)resampleYINT[5] * (int64_t)coeff[5]) +
			((int64_t)resampleYINT[7] * (int64_t)coeff[7]) +
			((int64_t)resampleYINT[9] * (int64_t)coeff[9]) +
			((int64_t)resampleYINT[11] * (int64_t)coeff[11]) +
			((int64_t)resampleYINT[13] * (int64_t)coeff[13]) +
			((int64_t)resampleYINT[15] * (int64_t)coeff[15]) +
			((int64_t)resampleYINT[17] * (int64_t)coeff[17]) +
			((int64_t)resampleYINT[19] * (int64_t)coeff[19])) << 2
		);
		*dstBuffer++ = ((outR.h >= 32767) ? 32767 : ((outR.h <= -32768) ? -32768 : (int16_t)outR.h));
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

			for (int32_t i = 0; i < 9; i++)
				((uint64_t*)resampleYINT)[i] = ((uint64_t*)resampleYINT)[i + 1];
			resampleYINT[18] = (int32_t)srcBuffer[0];
			resampleYINT[19] = (int32_t)srcBuffer[1];

			usedSrc++;
			srcBuffer += 2;

			if (usedSrc >= srcSizeInFrames) {
				srcFramesUsed = usedSrc;
				return usedDst;
			}
		}
	}

	srcFramesUsed = usedSrc;
	return usedDst;
}
#endif

#ifdef FPLAY_X86
uint32_t resampleLagrangeMono(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		for (int32_t i = 0; i < 9; i++)
			resampleY[i] = resampleY[i + 1];
		resampleY[9] = (float)srcBuffer[0];

		usedSrc++;
		srcBuffer++;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	while (usedDst < dstSizeInFrames) {
		const float* const coeff = resampleCoeff + resampleCoeffIdx;
		const int32_t outL = (int32_t)(
			(resampleY[0] * coeff[0]) +
			(resampleY[1] * coeff[2]) +
			(resampleY[2] * coeff[4]) +
			(resampleY[3] * coeff[6]) +
			(resampleY[4] * coeff[8]) +
			(resampleY[5] * coeff[10]) +
			(resampleY[6] * coeff[12]) +
			(resampleY[7] * coeff[14]) +
			(resampleY[8] * coeff[16]) +
			(resampleY[9] * coeff[18]));
		const int16_t o = (int16_t)((outL >= 32767) ? 32767 : ((outL <= -32768) ? -32768 : (int16_t)outL));
		*dstBuffer++ = o;
		*dstBuffer++ = o;
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

			for (int32_t i = 0; i < 9; i++)
				resampleY[i] = resampleY[i + 1];
			resampleY[9] = (float)srcBuffer[0];

			usedSrc++;
			srcBuffer++;

			if (usedSrc >= srcSizeInFrames) {
				srcFramesUsed = usedSrc;
				return usedDst;
			}
		}
	}

	srcFramesUsed = usedSrc;
	return usedDst;
}
#else
uint32_t resampleLagrangeMonoINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		for (int32_t i = 0; i < 9; i++)
			resampleYINT[i] = resampleYINT[i + 1];
		resampleYINT[9] = (int32_t)srcBuffer[0];

		usedSrc++;
		srcBuffer++;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	while (usedDst < dstSizeInFrames) {
		const int32_t* const coeff = resampleCoeffINT + resampleCoeffIdx;
		int64_3232 outL;
		outL.v = (
			(((int64_t)resampleYINT[0] * (int64_t)coeff[0]) +
			((int64_t)resampleYINT[1] * (int64_t)coeff[2]) +
			((int64_t)resampleYINT[2] * (int64_t)coeff[4]) +
			((int64_t)resampleYINT[3] * (int64_t)coeff[6]) +
			((int64_t)resampleYINT[4] * (int64_t)coeff[8]) +
			((int64_t)resampleYINT[5] * (int64_t)coeff[10]) +
			((int64_t)resampleYINT[6] * (int64_t)coeff[12]) +
			((int64_t)resampleYINT[7] * (int64_t)coeff[14]) +
			((int64_t)resampleYINT[8] * (int64_t)coeff[16]) +
			((int64_t)resampleYINT[9] * (int64_t)coeff[18])) << 2
		);
		const int16_t o = (int16_t)((outL.h >= 32767) ? 32767 : ((outL.h <= -32768) ? -32768 : (int16_t)outL.h));
		*dstBuffer++ = o;
		*dstBuffer++ = o;
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

			for (int32_t i = 0; i < 9; i++)
				resampleYINT[i] = resampleYINT[i + 1];
			resampleYINT[9] = (int32_t)srcBuffer[0];

			usedSrc++;
			srcBuffer++;

			if (usedSrc >= srcSizeInFrames) {
				srcFramesUsed = usedSrc;
				return usedDst;
			}
		}
	}

	srcFramesUsed = usedSrc;
	return usedDst;
}
#endif

#ifdef FPLAY_X86
void resampleComputeCoeffs() {
	static const uint32_t resampleFirstPrimes[8] = { 2, 3, 5, 7, 11, 13, 17, 19 };
	uint32_t factSrc = srcSampleRate, factDst = dstSampleRate;

	for (uint32_t i = 0; i < 8; i++) {
		const uint32_t prime = resampleFirstPrimes[i];
		while (!(factSrc % prime) && !(factDst % prime)) {
			factSrc /= prime;
			factDst /= prime;
		}
	}

	resampleCoeffLen = factDst * 20;
	if (resampleCoeffOriginal)
		delete resampleCoeffOriginal;
	resampleCoeffOriginal = new float[resampleCoeffLen + 4];
	//align memory on a 16-byte boundary (luckly, 20 * sizeof(float) is a multiple of 16)
	if (((size_t)resampleCoeffOriginal & 15))
		resampleCoeff = (float*)((size_t)resampleCoeffOriginal + 16 - ((size_t)resampleCoeffOriginal & 15));
	else
		resampleCoeff = resampleCoeffOriginal;
	if (resampleAdvance)
		delete resampleAdvance;
	resampleAdvance = new uint32_t[factDst];

	const double src = (double)factSrc;
	const double dst = (double)factDst;
	uint32_t lastPhaseI = 0;
	float* coeff = resampleCoeff + 20;

	for (uint32_t i = 1; i <= factDst; i++) {
		const double phase = ((double)i * src) / dst;
		const uint32_t phaseI = (uint32_t)phase;
		if (i == factDst) {
			coeff = resampleCoeff;
			resampleAdvance[0] = phaseI - lastPhaseI;
		} else {
			resampleAdvance[i] = phaseI - lastPhaseI;
		}
		lastPhaseI = phaseI;

		//compute the 10 coefficients of a 10th order Lagrange polynomial,
		//considering x to be something >= 4 and < 5
		const double phaseFrac = (phase - (double)phaseI) + 4.0;
		const double x_x0 = phaseFrac;
		const double x_x1 = phaseFrac - 1.0;
		const double x_x2 = phaseFrac - 2.0;
		const double x_x3 = phaseFrac - 3.0;
		const double x_x4 = phaseFrac - 4.0;
		const double x_x5 = phaseFrac - 5.0;
		const double x_x6 = phaseFrac - 6.0;
		const double x_x7 = phaseFrac - 7.0;
		const double x_x8 = phaseFrac - 8.0;
		const double x_x9 = phaseFrac - 9.0;

		//y0
		coeff[0] = (float)((x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / -362880.0);
		coeff[1] = coeff[0];
		//y1
		coeff[2] = (float)((x_x0 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / 40320.0);
		coeff[3] = coeff[2];
		//y2
		coeff[4] = (float)((x_x0 * x_x1 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / -10080.0);
		coeff[5] = coeff[4];
		//y3
		coeff[6] = (float)((x_x0 * x_x1 * x_x2 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / 4320.0);
		coeff[7] = coeff[6];
		//y4
		coeff[8] = (float)((x_x0 * x_x1 * x_x2 * x_x3 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / -2880.0);
		coeff[9] = coeff[8];
		//y5
		coeff[10] = (float)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x6 * x_x7 * x_x8 * x_x9) / 2880.0);
		coeff[11] = coeff[10];
		//y6
		coeff[12] = (float)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x7 * x_x8 * x_x9) / -4320.0);
		coeff[13] = coeff[12];
		//y7
		coeff[14] = (float)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x8 * x_x9) / 10080.0);
		coeff[15] = coeff[14];
		//y8
		coeff[16] = (float)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x9) / -40320.0);
		coeff[17] = coeff[16];
		//y9
		coeff[18] = (float)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8) / 362880.0);
		coeff[19] = coeff[18];

		coeff += 20;
	}
}
#else
void resampleComputeCoeffsINT() {
	static const uint32_t resampleFirstPrimes[8] = { 2, 3, 5, 7, 11, 13, 17, 19 };
	uint32_t factSrc = srcSampleRate, factDst = dstSampleRate;

	for (uint32_t i = 0; i < 8; i++) {
		const uint32_t prime = resampleFirstPrimes[i];
		while (!(factSrc % prime) && !(factDst % prime)) {
			factSrc /= prime;
			factDst /= prime;
		}
	}

	resampleCoeffLen = factDst * 20;
	if (resampleCoeffOriginalINT)
		delete resampleCoeffOriginalINT;
	resampleCoeffOriginalINT = new int32_t[resampleCoeffLen + 4];
	//align memory on a 16-byte boundary (luckly, 20 * sizeof(int32_t) is a multiple of 16)
	resampleCoeffINT = (int32_t*)((size_t)resampleCoeffOriginalINT + 16 - ((size_t)resampleCoeffOriginalINT & 15));
	if (resampleAdvance)
		delete resampleAdvance;
	resampleAdvance = new uint32_t[factDst];

	const double src = (double)factSrc;
	const double dst = (double)factDst;
	uint32_t lastPhaseI = 0;
	int32_t *coeff = resampleCoeffINT + 20;

	for (uint32_t i = 1; i <= factDst; i++) {
		const double phase = ((double)i * src) / dst;
		const uint32_t phaseI = (uint32_t)phase;
		if (i == factDst) {
			coeff = resampleCoeffINT;
			resampleAdvance[0] = phaseI - lastPhaseI;
		} else {
			resampleAdvance[i] = phaseI - lastPhaseI;
		}
		lastPhaseI = phaseI;

		//compute the 10 coefficients of a 10th order Lagrange polynomial,
		//considering x to be something >= 4 and < 5
		const double phaseFrac = (phase - (double)phaseI) + 4.0;
		const double x_x0 = phaseFrac;
		const double x_x1 = phaseFrac - 1.0;
		const double x_x2 = phaseFrac - 2.0;
		const double x_x3 = phaseFrac - 3.0;
		const double x_x4 = phaseFrac - 4.0;
		const double x_x5 = phaseFrac - 5.0;
		const double x_x6 = phaseFrac - 6.0;
		const double x_x7 = phaseFrac - 7.0;
		const double x_x8 = phaseFrac - 8.0;
		const double x_x9 = phaseFrac - 9.0;

		//y0
		coeff[0] = (int32_t)((x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) * (1073741824.0 / -362880.0));
		coeff[1] = coeff[0];
		//y1
		coeff[2] = (int32_t)((x_x0 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) * (1073741824.0 / 40320.0));
		coeff[3] = coeff[2];
		//y2
		coeff[4] = (int32_t)((x_x0 * x_x1 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) * (1073741824.0 / -10080.0));
		coeff[5] = coeff[4];
		//y3
		coeff[6] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) * (1073741824.0 / 4320.0));
		coeff[7] = coeff[6];
		//y4
		coeff[8] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) * (1073741824.0 / -2880.0));
		coeff[9] = coeff[8];
		//y5
		coeff[10] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x6 * x_x7 * x_x8 * x_x9) * (1073741824.0 / 2880.0));
		coeff[11] = coeff[10];
		//y6
		coeff[12] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x7 * x_x8 * x_x9) * (1073741824.0 / -4320.0));
		coeff[13] = coeff[12];
		//y7
		coeff[14] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x8 * x_x9) * (1073741824.0 / 10080.0));
		coeff[15] = coeff[14];
		//y8
		coeff[16] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x9) * (1073741824.0 / -40320.0));
		coeff[17] = coeff[16];
		//y9
		coeff[18] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8) * (1073741824.0 / 362880.0));
		coeff[19] = coeff[18];

		coeff += 20;
	}
}
#endif

void resetResamplerState() {
	resamplePendingAdvances = 0;
	resampleCoeffIdx = 0;
	resampleAdvanceIdx = 0;
#ifdef FPLAY_X86
	memset(resampleY, 0, sizeof(float) * 20);
#else
	memset(resampleYINT, 0, sizeof(int32_t) * 20);
#endif
}

void resetResampler() {
	resetResamplerState();

	if (srcSampleRate != dstSampleRate) {
#ifdef FPLAY_X86
		resampleComputeCoeffs();
#else
		resampleComputeCoeffsINT();
#endif

		//downsampling is only performed from 48000 Hz to 44100 Hz because we are not
		//applying any filters
		if ((srcSampleRate == 48000 && dstSampleRate == 44100) ||
			(srcSampleRate >= 8000 && dstSampleRate > srcSampleRate)) {

#ifdef FPLAY_X86
			resampleProc = ((srcChannelCount == 2) ? resampleLagrange : resampleLagrangeMono);
#else
			resampleProc = ((srcChannelCount == 2) ? (neonMode ? resampleLagrangeNeonINT : resampleLagrangeINT) : resampleLagrangeMonoINT);
#endif
			return;
		}
	}

	resampleProc = ((srcChannelCount == 2) ? resampleNull : resampleNullMono);
}

void initializeResampler() {
#ifdef FPLAY_X86
	resampleCoeff = 0;
	resampleCoeffOriginal = 0;
#else
	resampleCoeffINT = 0;
	resampleCoeffOriginalINT = 0;
#endif
	resampleAdvance = 0;
	resetResampler();
}

void terminateResampler() {
#ifdef FPLAY_X86
	resampleCoeff = 0;
	if (resampleCoeffOriginal) {
		delete resampleCoeffOriginal;
		resampleCoeffOriginal = 0;
	}
#else
	resampleCoeffINT = 0;
	if (resampleCoeffOriginalINT) {
		delete resampleCoeffOriginalINT;
		resampleCoeffOriginalINT = 0;
	}
#endif
	if (resampleAdvance) {
		delete resampleAdvance;
		resampleAdvance = 0;
	}
}
