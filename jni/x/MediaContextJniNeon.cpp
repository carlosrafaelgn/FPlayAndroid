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
#include <android/log.h>
#include <arm_neon.h>

extern unsigned int equalizerMaxBandCount;
extern int equalizerTemp[] __attribute__((aligned(16)));
extern float equalizerCoefs[] __attribute__((aligned(16))),
equalizerSamples[] __attribute__((aligned(16)));

//http://infocenter.arm.com/help/index.jsp?topic=/com.arm.doc.dui0491h/CIHJBEFE.html

void processEqualizerNeon(short* buffer, unsigned int sizeInFrames) {
	while ((sizeInFrames--)) {
		float *samples = equalizerSamples;

		equalizerTemp[0] = (int)buffer[0];
		equalizerTemp[1] = (int)buffer[1];
		//inLR = { L, R }
		float32x2_t inLR = vcvt_f32_s32(*((int32x2_t*)equalizerTemp));

		//since this is a cascade filter, band0's output is band1's input and so on....
		for (int i = 0; i < equalizerMaxBandCount; i++, samples += 8) {
			//y(n) = b0.x(n) + b1.x(n-1) + b2.x(n-2) - a1.y(n-1) - a2.y(n-2)
			//but, since our a2 was negated and b1 = a1, the formula becomes:
			//y(n) = b0.x(n) + b1.(x(n-1) - y(n-1)) + b2.x(n-2) + a2.y(n-2)

			//b0_b1 = { b0, b0, b1, b1 }
			float32x4_t b0_b1 = vld1q_f32(equalizerCoefs + (i << 3));
			//b2_a2 = { b2, b2, -a2, -a2 }
			float32x4_t b2_a2 = vld1q_f32(equalizerCoefs + (i << 3) + 4);

			//local = { x_n1_L, x_n1_R, y_n1_L, y_n1_R }
			float32x4_t local = vld1q_f32(samples);

			//inLR4 = { L, R, x_n1_L - y_n1_L, x_n1_R - y_n1_R }
			float32x4_t inLR4 = vcombine_f32(inLR, vsub_f32(vget_low_f32(local), vget_high_f32(local)));

			//tmp2 = { x_n2_L, x_n2_R, y_n2_L, y_n2_R }
			float32x4_t tmp2 = vld1q_f32(samples + 4);

			//b0_b1 = { b0 * L, b0 * R, b1 * (x_n1_L - y_n1_L), b1 * (x_n1_R - y_n1_R) }
			b0_b1 = vmulq_f32(b0_b1, inLR4);

			//b2_a2 = { b2 * x_n2_L, b2 * x_n2_R, -a2 * y_n2_L, -a2 * y_n2_R }
			b2_a2 = vmulq_f32(b2_a2, tmp2);

			//b0_b1 = { (b0 * L) + (b2 * x_n2_L), (b0 * R) + (b2 * x_n2_R), (b1 * (x_n1_L - y_n1_L)) + (-a2 * y_n2_L), (b1 * (x_n1_R - y_n1_R)) + (-a2 * y_n2_R) }
			b0_b1 = vaddq_f32(b0_b1, b2_a2);

			//x_n2_L = local_x_n1_L;
			//x_n2_R = local_x_n1_R;
			//y_n2_L = local_y_n1_L;
			//y_n2_R = local_y_n1_R;
			vst1q_f32(samples + 4, local);

			//outLR = { outL, outR }
			float32x2_t outLR = vadd_f32(vget_low_f32(b0_b1), vget_high_f32(b0_b1));

			//x_n1_L = inL;
			//x_n1_R = inR;
			//y_n1_L = outL;
			//y_n1_R = outR;
			vst1q_f32(samples, vcombine_f32(inLR, outLR));

			//inL = outL;
			//inR = outR;
			inLR = outLR;
		}

		//the final output is the last band's output (or its next band's input)
		//const int iL = (int)inL;
		//const int iR = (int)inR;
		int32x2_t iLR = vcvt_s32_f32(inLR);

		//buffer[0] = (iL >= 32767 ? 32767 : (iL <= -32768 ? -32768 : (short)iL));
		//buffer[1] = (iR >= 32767 ? 32767 : (iR <= -32768 ? -32768 : (short)iR));
		int16x4_t iLRshort = vqmovn_s32(vcombine_s32(iLR, iLR));
		buffer[0] = vget_lane_s16(iLRshort, 0);
		buffer[1] = vget_lane_s16(iLRshort, 1);

		buffer += 2;
	}
}
