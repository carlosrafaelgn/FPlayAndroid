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

#if defined(__arm__) || defined(__aarch64__)
//arm or arm64
#define FPLAY_ARM
#elif defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
//x86 or x86_64
#define FPLAY_X86
#else
#error ("Unknown platform!")
#endif

#if defined(__aarch64__) || defined(__x86_64__) || defined(_M_X64)
//arm64 or x86_64
#define FPLAY_64_BITS
#else
#define FPLAY_32_BITS
#endif

#include <inttypes.h>

#include "Constants.h"

//for the alignment:
//https://gcc.gnu.org/onlinedocs/gcc-3.2/gcc/Variable-Attributes.html

//QUARTER_FFT_SIZE float elements
#define _fftFloatElements QUARTER_FFT_SIZE
#define _fft floatBuffer
//QUARTER_FFT_SIZE float elements
#define _multiplierFloatElements QUARTER_FFT_SIZE
#define _multiplier (floatBuffer + _fftFloatElements)
//QUARTER_FFT_SIZE float elements
#define _previousMFloatElements QUARTER_FFT_SIZE
#define _previousM (floatBuffer + (_fftFloatElements + _multiplierFloatElements))
//QUARTER_FFT_SIZE 8-bit elements = QUARTER_FFT_SIZE / 4 float elements
#define _processedDataFloatElements (QUARTER_FFT_SIZE / 4)
#define _processedData ((uint8_t*)(floatBuffer + (_fftFloatElements + _multiplierFloatElements + _previousMFloatElements)))
//2 * (QUARTER_FFT_SIZE 8-bit elements) = 2 * (QUARTER_FFT_SIZE / 4 float elements)
#define _fftIFloatElements (2 * (QUARTER_FFT_SIZE / 4))
#define _fftI ((uint8_t*)(floatBuffer + (_fftFloatElements + _multiplierFloatElements + _previousMFloatElements + _processedDataFloatElements)))

extern float floatBuffer[] __attribute__((aligned(16)));
extern float commonCoefNew;

extern void commonProcessNeon(int32_t deltaMillis, int32_t opt);
extern void doFftNeon(int32_t *workspace, uint8_t *outFft);
