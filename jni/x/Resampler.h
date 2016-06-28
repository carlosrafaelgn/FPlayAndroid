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
extern uint32_t resampleHermiteNeon(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);
#endif

float resampleHermitePhase, resampleHermitePhaseIncrement;
float resampleHermiteY[8] __attribute__((aligned(16)));
static RESAMPLEPROC resampleProc;

uint32_t resampleNull(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//nothing to be done but copying from source to destination (if they are different buffers)
	if (srcSizeInFrames > dstSizeInFrames)
		srcSizeInFrames = dstSizeInFrames;
	if (srcBuffer != dstBuffer)
		memcpy(dstBuffer, srcBuffer, srcSizeInFrames << 2);
	srcFramesUsed = srcSizeInFrames;
	return srcSizeInFrames;
}

uint32_t resampleNullMono(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	if (srcSizeInFrames > dstSizeInFrames)
		srcSizeInFrames = dstSizeInFrames;
	//we need to copy the samples backwards because srcBuffer and dstBuffer could be pointing to the same address
	dstSizeInFrames = srcSizeInFrames;
	srcBuffer += (dstSizeInFrames - 1);
	dstBuffer += (dstSizeInFrames << 1) - 1;
	while (dstSizeInFrames--) {
		const int16_t i = *srcBuffer--;
		*dstBuffer-- = i;
		*dstBuffer-- = i;
	}
	srcFramesUsed = srcSizeInFrames;
	return srcSizeInFrames;
}

uint32_t resampleHermite(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (usedDst < dstSizeInFrames) {
		//4-point hermite interpolation, with its polynom slightly optimized (reduced)
		//(both tension and bias were considered to be 0)
		const float x2 = resampleHermitePhase * resampleHermitePhase;
		const float x_1 = resampleHermitePhase - 1.0f;
		const float _2x = 2.0f * resampleHermitePhase;
		const float a = (3.0f - _2x) * x2;
		const float b = (_2x + 1.0f) * x_1 * x_1;
		const float c = 0.5f * resampleHermitePhase * (resampleHermitePhase + 1.0f) * ((x2 * resampleHermitePhase) - 2.0f);
		const float d = 0.5f * x_1 * x2;
		const int32_t outL = (int32_t)((a * resampleHermiteY[4]) + (b * resampleHermiteY[2]) - (c * (resampleHermiteY[0] - resampleHermiteY[4])) - (d * (resampleHermiteY[2] - resampleHermiteY[6])));
		*dstBuffer++ = ((outL >= 32767) ? 32767 : ((outL <= -32768) ? -32768 : (int16_t)outL));
		const int32_t outR = (int32_t)((a * resampleHermiteY[5]) + (b * resampleHermiteY[3]) - (c * (resampleHermiteY[1] - resampleHermiteY[5])) - (d * (resampleHermiteY[3] - resampleHermiteY[7])));
		*dstBuffer++ = ((outR >= 32767) ? 32767 : ((outR <= -32768) ? -32768 : (int16_t)outR));
		usedDst++;

		resampleHermitePhase += resampleHermitePhaseIncrement;

		while (resampleHermitePhase >= 1.0f) {
			resampleHermitePhase -= 1.0f;

			usedSrc++;
			srcBuffer += 2;

			if (usedSrc >= srcSizeInFrames) {
				srcFramesUsed = usedSrc;
				return usedDst;
			}

			resampleHermiteY[6] = resampleHermiteY[4];
			resampleHermiteY[4] = resampleHermiteY[2];
			resampleHermiteY[2] = resampleHermiteY[0];
			resampleHermiteY[0] = (float)srcBuffer[0];

			resampleHermiteY[7] = resampleHermiteY[5];
			resampleHermiteY[5] = resampleHermiteY[3];
			resampleHermiteY[3] = resampleHermiteY[1];
			resampleHermiteY[1] = (float)srcBuffer[1];
		}
	}

	srcFramesUsed = usedSrc;
	return usedDst;
}

uint32_t resampleHermiteMono(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	while (usedDst < dstSizeInFrames) {
		const float x2 = resampleHermitePhase * resampleHermitePhase;
		const float x_1 = resampleHermitePhase - 1.0f;
		const float _2x = 2.0f * resampleHermitePhase;
		const float a = (3.0f - _2x) * x2;
		const float b = (_2x + 1.0f) * x_1 * x_1;
		const float c = 0.5f * resampleHermitePhase * (resampleHermitePhase + 1.0f) * ((x2 * resampleHermitePhase) - 2.0f);
		const float d = 0.5f * x_1 * x2;
		const int32_t outL = (int32_t)((a * resampleHermiteY[4]) + (b * resampleHermiteY[2]) - (c * (resampleHermiteY[0] - resampleHermiteY[4])) - (d * (resampleHermiteY[2] - resampleHermiteY[6])));
		const int16_t outL16 = ((outL >= 32767) ? 32767 : ((outL <= -32768) ? -32768 : (int16_t)outL));
		*dstBuffer++ = outL16;
		*dstBuffer++ = outL16;
		usedDst++;

		resampleHermitePhase += resampleHermitePhaseIncrement;

		while (resampleHermitePhase >= 1.0f) {
			resampleHermitePhase -= 1.0f;

			usedSrc++;
			srcBuffer++;

			if (usedSrc >= srcSizeInFrames) {
				srcFramesUsed = usedSrc;
				return usedDst;
			}

			resampleHermiteY[6] = resampleHermiteY[4];
			resampleHermiteY[4] = resampleHermiteY[2];
			resampleHermiteY[2] = resampleHermiteY[0];
			resampleHermiteY[0] = (float)srcBuffer[0];
		}
	}

	srcFramesUsed = usedSrc;
	return usedDst;
}

void resetResampler() {
	resampleHermitePhase = 0.0f;
	resampleHermitePhaseIncrement = 0.0f;
	memset(resampleHermiteY, 0, sizeof(float) * 8);

	if (srcSampleRate != dstSampleRate) {
		//downsampling is only performed from 48000 Hz to 44100 Hz because we are not
		//applying any filters
		if ((srcSampleRate == 48000 && dstSampleRate == 44100) ||
			(srcSampleRate >= 8000 && dstSampleRate > srcSampleRate)) {
			resampleHermitePhaseIncrement = (float)srcSampleRate / (float)dstSampleRate;
#ifdef FPLAY_ARM
			resampleProc = ((srcChannelCount == 2) ? (neonMode ? resampleHermiteNeon : resampleHermite) : resampleHermiteMono);
#else
			resampleProc = ((srcChannelCount == 2) ? resampleHermite : resampleHermiteMono);
#endif
			return;
		}
	}

	resampleProc = ((srcChannelCount == 2) ? resampleNull : resampleNullMono);
}

#define initializeResampler resetResampler
