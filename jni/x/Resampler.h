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

//the integer interpolation algorithm is based on AudioResampler from The Android Open Source Project, available at:
//https://android.googlesource.com/platform/frameworks/av/+/master/services/audioflinger/AudioResampler.cpp
/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

typedef uint32_t (*RESAMPLEPROC)(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed);

static int32_t resampleLast[2] __attribute__((aligned(16)));
static uint32_t resamplePhaseFraction;
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

#define kNumPhaseBits 30
#define kPhaseMultiplier (double)(1 << kNumPhaseBits)
#define kPhaseMask ((1 << kNumPhaseBits) - 1)
#define kNumInterpBits 15
#define kPreInterpShift (kNumPhaseBits - kNumInterpBits)

#define kPhaseIncrement (uint32_t)((kPhaseMultiplier * 44100.0) / 48000.0)

uint32_t resample44100to48000(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	register int32_t lastL = resampleLast[0];
	register int32_t lastR = resampleLast[1];

	//when resampling from 44100 to 48000, usedSrc changes 11 times and remains the same 1 time
	//when resampling from 48000 to 44100, usedSrc changes everytime
	//therefore, there is no need to try to optimize the buffer fetches and the computation ((current - last) * shiftedPhaseFraction)
	while (usedSrc == 0 && usedDst < dstSizeInFrames) {
		const int32_t shiftedPhaseFraction = (int32_t)(resamplePhaseFraction >> kPreInterpShift);
		*dstBuffer++ = (int16_t)(lastL + ((((int32_t)srcBuffer[0] - lastL) * shiftedPhaseFraction) >> kNumInterpBits));
		*dstBuffer++ = (int16_t)(lastR + ((((int32_t)srcBuffer[1] - lastR) * shiftedPhaseFraction) >> kNumInterpBits));

		resamplePhaseFraction += kPhaseIncrement;
		const uint32_t increment = (resamplePhaseFraction >> kNumPhaseBits);
		srcBuffer += (increment << 1);
		usedSrc += increment;
		resamplePhaseFraction &= kPhaseMask;

		usedDst++;
	}

	if (usedSrc) {
		lastL = (int32_t)srcBuffer[-2];
		lastR = (int32_t)srcBuffer[-1];
	}

	while (usedSrc < srcSizeInFrames && usedDst < dstSizeInFrames) {
		const int32_t shiftedPhaseFraction = (int32_t)(resamplePhaseFraction >> kPreInterpShift);
		*dstBuffer++ = (int16_t)(lastL + ((((int32_t)srcBuffer[0] - lastL) * shiftedPhaseFraction) >> kNumInterpBits));
		*dstBuffer++ = (int16_t)(lastR + ((((int32_t)srcBuffer[1] - lastR) * shiftedPhaseFraction) >> kNumInterpBits));

		resamplePhaseFraction += kPhaseIncrement;
		const uint32_t increment = (resamplePhaseFraction >> kNumPhaseBits);
		srcBuffer += (increment << 1);
		usedSrc += increment;
		resamplePhaseFraction &= kPhaseMask;

		lastL = (int32_t)srcBuffer[-2];
		lastR = (int32_t)srcBuffer[-1];

		usedDst++;
	}

	resampleLast[0] = lastL;
	resampleLast[1] = lastR;

	srcFramesUsed = usedSrc;
	return usedDst;
}

uint32_t resample44100to48000Mono(int16_t* srcBuffer, uint32_t srcSizeInFrames, int16_t* dstBuffer, uint32_t dstSizeInFrames, uint32_t& srcFramesUsed) {
	//both ARM (32/64) and x86 (64) have lots of registers!
	register uint32_t usedSrc = 0, usedDst = 0;

	register int32_t lastL = resampleLast[0];

	//when resampling from 44100 to 48000, usedSrc changes 11 times and remains the same 1 time
	//when resampling from 48000 to 44100, usedSrc changes everytime
	//therefore, there is no need to try to optimize the buffer fetches and the computation ((current - last) * shiftedPhaseFraction)
	while (usedSrc == 0 && usedDst < dstSizeInFrames) {
		const int16_t outL = (int16_t)(lastL + ((((int32_t)srcBuffer[0] - lastL) * (int32_t)(resamplePhaseFraction >> kPreInterpShift)) >> kNumInterpBits));
		//mono to stereo
		*dstBuffer++ = outL;
		*dstBuffer++ = outL;

		resamplePhaseFraction += kPhaseIncrement;
		const uint32_t increment = (resamplePhaseFraction >> kNumPhaseBits);
		srcBuffer += increment;
		usedSrc += increment;
		resamplePhaseFraction &= kPhaseMask;

		usedDst++;
	}

	if (usedSrc)
		lastL = (int32_t)srcBuffer[-1];

	while (usedSrc < srcSizeInFrames && usedDst < dstSizeInFrames) {
		const int16_t outL = (int16_t)(lastL + ((((int32_t)srcBuffer[0] - lastL) * (int32_t)(resamplePhaseFraction >> kPreInterpShift)) >> kNumInterpBits));
		//mono to stereo
		*dstBuffer++ = outL;
		*dstBuffer++ = outL;

		resamplePhaseFraction += kPhaseIncrement;
		const uint32_t increment = (resamplePhaseFraction >> kNumPhaseBits);
		srcBuffer += increment;
		usedSrc += increment;
		resamplePhaseFraction &= kPhaseMask;

		lastL = (int32_t)srcBuffer[-1];

		usedDst++;
	}

	resampleLast[0] = lastL;
	resampleLast[1] = lastL;

	srcFramesUsed = usedSrc;
	return usedDst;
}

#undef kPhaseIncrement
#define kPhaseIncrement (uint32_t)((kPhaseMultiplier * 48000.0) / 44100.0)

//48000 to 44100 code (cannot be linear interpolated because that produces a few small, yet audible, artifacts)

#undef kPhaseIncrement

#undef kNumPhaseBits
#undef kPhaseMultiplier
#undef kPhaseMask
#undef kNumInterpBits
#undef kPreInterpShift

void resetResampler() {
	resampleLast[0] = 0;
	resampleLast[1] = 0;
	resamplePhaseFraction = 0;

	if (srcSampleRate != dstSampleRate) {
		if (srcSampleRate == 44100 && dstSampleRate == 48000) {
			resampleProc = ((srcChannelCount == 2) ? resample44100to48000 : resample44100to48000Mono);
			return;
		}
	}

	resampleProc = ((srcChannelCount == 2) ? resampleNull : resampleNullMono);
}

#define initializeResampler resetResampler
