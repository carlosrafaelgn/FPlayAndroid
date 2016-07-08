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
extern uint32_t resampleLagrangeNeon(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);
extern uint32_t resampleLagrangeNeonINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);
#endif

uint32_t resamplePendingAdvances, resampleCoeffLen, resampleCoeffIdx, resampleAdvanceIdx;
float *resampleCoeff;
uint32_t *resampleAdvance;
float resampleY[20] __attribute__((aligned(16)));
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
uint32_t resampleLagrange(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		memmove(resampleY, resampleY + 2, 18 * sizeof(float));
		resampleY[18] = (float)srcBuffer[0];
		resampleY[19] = (float)srcBuffer[1];

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	while (usedDst < dstSizeInFrames) {
		const float* const coeff = resampleCoeff + resampleCoeffIdx;
		const int32_t outL = (int32_t)(
			(resampleY[0] * coeff[0]) +
			(resampleY[2] * coeff[2]) +
			(resampleY[4] * coeff[4]) +
			(resampleY[6] * coeff[6]) +
			(resampleY[8] * coeff[8]) +
			(resampleY[10] * coeff[10]) +
			(resampleY[12] * coeff[12]) +
			(resampleY[14] * coeff[14]) +
			(resampleY[16] * coeff[16]) +
			(resampleY[18] * coeff[18]));
		*dstBuffer++ = ((outL >= 32767) ? 32767 : ((outL <= -32768) ? -32768 : (int16_t)outL));
		const int32_t outR = (int32_t)(
			(resampleY[1] * coeff[1]) +
			(resampleY[3] * coeff[3]) +
			(resampleY[5] * coeff[5]) +
			(resampleY[7] * coeff[7]) +
			(resampleY[9] * coeff[9]) +
			(resampleY[11] * coeff[11]) +
			(resampleY[13] * coeff[13]) +
			(resampleY[15] * coeff[15]) +
			(resampleY[17] * coeff[17]) +
			(resampleY[19] * coeff[19]));
		*dstBuffer++ = ((outR >= 32767) ? 32767 : ((outR <= -32768) ? -32768 : (int16_t)outR));
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

			memmove(resampleY, resampleY + 2, 18 * sizeof(float));
			resampleY[18] = (float)srcBuffer[0];
			resampleY[19] = (float)srcBuffer[1];

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

uint32_t resampleLagrangeINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		memmove(resampleY, resampleY + 2, 18 * sizeof(int32_t));
		((int32_t*)resampleY)[18] = (int32_t)srcBuffer[0];
		((int32_t*)resampleY)[19] = (int32_t)srcBuffer[1];

		usedSrc++;
		srcBuffer += 2;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	while (usedDst < dstSizeInFrames) {
		const int32_t* const coeff = (int32_t*)resampleCoeff + resampleCoeffIdx;
		const int32_t outL = (int32_t)(
			((int64_t)(((int32_t*)resampleY)[0] * coeff[0]) +
			(int64_t)(((int32_t*)resampleY)[2] * coeff[2]) +
			(int64_t)(((int32_t*)resampleY)[4] * coeff[4]) +
			(int64_t)(((int32_t*)resampleY)[6] * coeff[6]) +
			(int64_t)(((int32_t*)resampleY)[8] * coeff[8]) +
			(int64_t)(((int32_t*)resampleY)[10] * coeff[10]) +
			(int64_t)(((int32_t*)resampleY)[12] * coeff[12]) +
			(int64_t)(((int32_t*)resampleY)[14] * coeff[14]) +
			(int64_t)(((int32_t*)resampleY)[16] * coeff[16]) +
			(int64_t)(((int32_t*)resampleY)[18] * coeff[18])) >> 15
		);
		*dstBuffer++ = ((outL >= 32767) ? 32767 : ((outL <= -32768) ? -32768 : (int16_t)outL));
		const int32_t outR = (int32_t)(
			((int64_t)(((int32_t*)resampleY)[1] * coeff[1]) +
			(int64_t)(((int32_t*)resampleY)[3] * coeff[3]) +
			(int64_t)(((int32_t*)resampleY)[5] * coeff[5]) +
			(int64_t)(((int32_t*)resampleY)[7] * coeff[7]) +
			(int64_t)(((int32_t*)resampleY)[9] * coeff[9]) +
			(int64_t)(((int32_t*)resampleY)[11] * coeff[11]) +
			(int64_t)(((int32_t*)resampleY)[13] * coeff[13]) +
			(int64_t)(((int32_t*)resampleY)[15] * coeff[15]) +
			(int64_t)(((int32_t*)resampleY)[17] * coeff[17]) +
			(int64_t)(((int32_t*)resampleY)[19] * coeff[19])) >> 15
		);
		*dstBuffer++ = ((outR >= 32767) ? 32767 : ((outR <= -32768) ? -32768 : (int16_t)outR));
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

			memmove(resampleY, resampleY + 2, 18 * sizeof(int32_t));
			((int32_t*)resampleY)[18] = (int32_t)srcBuffer[0];
			((int32_t*)resampleY)[19] = (int32_t)srcBuffer[1];

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

uint32_t resampleLagrangeMono(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		memmove(resampleY, resampleY + 1, 9 * sizeof(float));
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

			memmove(resampleY, resampleY + 1, 9 * sizeof(float));
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

uint32_t resampleLagrangeMonoINT(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (resamplePendingAdvances) {
		resamplePendingAdvances--;

		memmove(resampleY, resampleY + 1, 9 * sizeof(int32_t));
		((int32_t*)resampleY)[9] = (int32_t)srcBuffer[0];

		usedSrc++;
		srcBuffer++;

		if (usedSrc >= srcSizeInFrames) {
			srcFramesUsed = usedSrc;
			return usedDst;
		}
	}

	while (usedDst < dstSizeInFrames) {
		const int32_t* const coeff = (int32_t*)resampleCoeff + resampleCoeffIdx;
		const int32_t outL = (int32_t)(
			((int64_t)(((int32_t*)resampleY)[0] * coeff[0]) +
			(int64_t)(((int32_t*)resampleY)[1] * coeff[2]) +
			(int64_t)(((int32_t*)resampleY)[2] * coeff[4]) +
			(int64_t)(((int32_t*)resampleY)[3] * coeff[6]) +
			(int64_t)(((int32_t*)resampleY)[4] * coeff[8]) +
			(int64_t)(((int32_t*)resampleY)[5] * coeff[10]) +
			(int64_t)(((int32_t*)resampleY)[6] * coeff[12]) +
			(int64_t)(((int32_t*)resampleY)[7] * coeff[14]) +
			(int64_t)(((int32_t*)resampleY)[8] * coeff[16]) +
			(int64_t)(((int32_t*)resampleY)[9] * coeff[18])) >> 15
		);
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

			memmove(resampleY, resampleY + 1, 9 * sizeof(int32_t));
			((int32_t*)resampleY)[9] = (int32_t)srcBuffer[0];

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
	if (resampleCoeff)
		delete resampleCoeff;
	resampleCoeff = new float[resampleCoeffLen];
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
	if (resampleCoeff)
		delete resampleCoeff;
	resampleCoeff = new float[resampleCoeffLen];
	if (resampleAdvance)
		delete resampleAdvance;
	resampleAdvance = new uint32_t[factDst];

	const double src = (double)factSrc;
	const double dst = (double)factDst;
	uint32_t lastPhaseI = 0;
	int32_t* coeff = (int32_t*)resampleCoeff + 20;

	for (uint32_t i = 1; i <= factDst; i++) {
		const double phase = ((double)i * src) / dst;
		const uint32_t phaseI = (uint32_t)phase;
		if (i == factDst) {
			coeff = (int32_t*)resampleCoeff;
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
		coeff[0] = (int32_t)((x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / (-362880.0 / 32768.0));
		coeff[1] = coeff[0];
		//y1
		coeff[2] = (int32_t)((x_x0 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / (40320.0 / 32768.0));
		coeff[3] = coeff[2];
		//y2
		coeff[4] = (int32_t)((x_x0 * x_x1 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / (-10080.0 / 32768.0));
		coeff[5] = coeff[4];
		//y3
		coeff[6] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / (4320.0 / 32768.0));
		coeff[7] = coeff[6];
		//y4
		coeff[8] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x5 * x_x6 * x_x7 * x_x8 * x_x9) / (-2880.0 / 32768.0));
		coeff[9] = coeff[8];
		//y5
		coeff[10] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x6 * x_x7 * x_x8 * x_x9) / (2880.0 / 32768.0));
		coeff[11] = coeff[10];
		//y6
		coeff[12] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x7 * x_x8 * x_x9) / (-4320.0 / 32768.0));
		coeff[13] = coeff[12];
		//y7
		coeff[14] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x8 * x_x9) / (10080.0 / 32768.0));
		coeff[15] = coeff[14];
		//y8
		coeff[16] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x9) / (-40320.0 / 32768.0));
		coeff[17] = coeff[16];
		//y9
		coeff[18] = (int32_t)((x_x0 * x_x1 * x_x2 * x_x3 * x_x4 * x_x5 * x_x6 * x_x7 * x_x8) / (362880.0 / 32768.0));
		coeff[19] = coeff[18];

		coeff += 20;
	}
}

void resetResamplerState() {
	resamplePendingAdvances = 0;
	resampleCoeffIdx = 0;
	resampleAdvanceIdx = 0;
	memset(resampleY, 0, sizeof(float) * 20);
}

void resetResampler() {
	resetResamplerState();

	if (srcSampleRate != dstSampleRate) {
		//resampleComputeCoeffs();
		resampleComputeCoeffsINT();

		//downsampling is only performed from 48000 Hz to 44100 Hz because we are not
		//applying any filters
		if ((srcSampleRate == 48000 && dstSampleRate == 44100) ||
			(srcSampleRate >= 8000 && dstSampleRate > srcSampleRate)) {

#ifdef FPLAY_ARM
			//resampleProc = ((srcChannelCount == 2) ? (neonMode ? resampleLagrangeNeon : resampleLagrange) : resampleLagrangeMono);
			resampleProc = ((srcChannelCount == 2) ? (neonMode ? resampleLagrangeNeonINT : resampleLagrangeINT) : resampleLagrangeMonoINT);
#else
			//resampleProc = ((srcChannelCount == 2) ? resampleLagrange : resampleLagrangeMono);
			resampleProc = ((srcChannelCount == 2) ? resampleLagrangeINT : resampleLagrangeMonoINT);
#endif
			return;
		}
	}

	resampleProc = ((srcChannelCount == 2) ? resampleNull : resampleNullMono);
}

void initializeResampler() {
	resampleCoeff = 0;
	resampleAdvance = 0;
	resetResampler();
}

void terminateResampler() {
	if (resampleCoeff) {
		delete resampleCoeff;
		resampleCoeff = 0;
	}
	if (resampleAdvance) {
		delete resampleAdvance;
		resampleAdvance = 0;
	}
}
