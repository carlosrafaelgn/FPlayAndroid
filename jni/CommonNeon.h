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

//used during Bluetooth processing
#define MessageBins4 ((int)0x20)
#define MessageBins8 ((int)0x21)
#define MessageBins16 ((int)0x22)
#define MessageBins32 ((int)0x23)
#define MessageBins64 ((int)0x24)
#define MessageBins128 ((int)0x25)
#define MessageBins256 ((int)0x26)
#define IgnoreInput ((int)0x80)
#define ComputeVUMeter ((int)0x100)

#define DEFSPEED (0.140625f / 16.0f)

//for the alignment:
//https://gcc.gnu.org/onlinedocs/gcc-3.2/gcc/Variable-Attributes.html

//to make the math easier COLORS has 257 int's (from 0 to 256) for each different color
extern const unsigned short COLORS[] __attribute__((aligned(16)));

extern float floatBuffer[] __attribute__((aligned(16)));
extern float previousM[] __attribute__((aligned(16)));
#ifdef _MAY_HAVE_NEON_
extern unsigned int neonMode, neonDone;
extern int intBuffer[] __attribute__((aligned(16)));
#endif

extern float commonCoefNew;
extern unsigned int commonColorIndex, commonColorIndexApplied;

extern void commonProcessNeon(signed char* bfft, int deltaMillis, int opt);
//extern void processNeon(signed char* bfft, int deltaMillis);
