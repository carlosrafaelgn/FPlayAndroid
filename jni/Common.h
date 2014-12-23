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

#include "CommonNeon.h"

//for the alignment:
//https://gcc.gnu.org/onlinedocs/gcc-3.2/gcc/Variable-Attributes.html

//to make the math easier COLORS has 257 int's (from 0 to 256) for each different color
const unsigned short COLORS[] __attribute__((aligned(16))) = { 	/*Blue */ 0x0000, 0x0816, 0x0816, 0x0815, 0x0815, 0x0815, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1815, 0x1814, 0x1814, 0x1814, 0x1814, 0x2014, 0x2014, 0x2014, 0x2013, 0x2013, 0x2813, 0x2813, 0x2813, 0x2813, 0x3012, 0x3012, 0x3012, 0x3012, 0x3812, 0x3811, 0x3811, 0x3811, 0x4011, 0x4011, 0x4011, 0x4010, 0x4810, 0x4810, 0x4810, 0x4810, 0x500f, 0x500f, 0x500f, 0x500f, 0x580f, 0x580e, 0x580e, 0x600e, 0x600e, 0x600d, 0x680d, 0x680d, 0x680d, 0x680d, 0x700c, 0x700c, 0x700c, 0x780c, 0x780c, 0x780b, 0x800b, 0x800b, 0x800b, 0x800a, 0x880a, 0x880a, 0x880a, 0x900a, 0x9009, 0x9009, 0x9009, 0x9809, 0x9809, 0x9808, 0xa008, 0xa008, 0xa008, 0xa807, 0xa807, 0xa807, 0xa807, 0xb007, 0xb006, 0xb006, 0xb806, 0xb806, 0xb806, 0xb805, 0xc005, 0xc005, 0xc005, 0xc005, 0xc804, 0xc804, 0xc804, 0xc804, 0xd004, 0xd004, 0xd003, 0xd003, 0xd803, 0xd803, 0xd803, 0xd802, 0xe002, 0xe002, 0xe002, 0xe002, 0xe002, 0xe802, 0xe801, 0xe801, 0xe801, 0xe801, 0xf001, 0xf001, 0xf001, 0xf000, 0xf000, 0xf000, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf820, 0xf820, 0xf820, 0xf840, 0xf840, 0xf840, 0xf860, 0xf860, 0xf860, 0xf880, 0xf880, 0xf8a0, 0xf8a0, 0xf8a0, 0xf8c0, 0xf8c0, 0xf8e0, 0xf8e0, 0xf900, 0xf900, 0xf900, 0xf920, 0xf920, 0xf940, 0xf940, 0xf960, 0xf960, 0xf980, 0xf9a0, 0xf9a0, 0xf9a0, 0xf9c0, 0xf9e0, 0xf9e0, 0xfa00, 0xfa00, 0xfa20, 0xfa20, 0xfa40, 0xfa40, 0xfa60, 0xfa80, 0xfa80, 0xfaa0, 0xfaa0, 0xfac0, 0xfae0, 0xfae0, 0xfb00, 0xfb00, 0xfb20, 0xfb40, 0xfb40, 0xfb60, 0xfb60, 0xfb80, 0xfba0, 0xfba0, 0xfbc0, 0xfbe0, 0xfbe0, 0xfc00, 0xfc00, 0xfc20, 0xfc20, 0xfc40, 0xfc60, 0xfc60, 0xfc80, 0xfca0, 0xfca0, 0xfcc0, 0xfcc0, 0xfce0, 0xfd00, 0xfd00, 0xfd20, 0xfd20, 0xfd40, 0xfd60, 0xfd60, 0xfd80, 0xfd80, 0xfda0, 0xfda0, 0xfdc0, 0xfde0, 0xfde0, 0xfe00, 0xfe00, 0xfe20, 0xfe20, 0xfe40, 0xfe40, 0xfe60, 0xfe60, 0xfe80, 0xfe80, 0xfea0, 0xfea0, 0xfec0, 0xfec0, 0xfee0, 0xfee0, 0xff00, 0xff00, 0xff20, 0xff20, 0xff20, 0xff40, 0xff40, 0xff40, 0xff60, 0xff60, 0xff80, 0xff80, 0xff80, 0xffa0, 0xffa0, 0xffa0, 0xffc0, 0xffc0, 0xffc0, 0xffc0, 0xffc0,
																		/*Green*/ 0x0000, 0x0000, 0x0000, 0x0020, 0x0020, 0x0040, 0x0040, 0x0060, 0x0060, 0x0080, 0x00a0, 0x00a0, 0x00c0, 0x00e0, 0x00e0, 0x0100, 0x0120, 0x0140, 0x0160, 0x0160, 0x0180, 0x01a0, 0x01c0, 0x01e0, 0x01e0, 0x0200, 0x0220, 0x0240, 0x0260, 0x0280, 0x0280, 0x02a0, 0x02c0, 0x02e0, 0x0300, 0x0320, 0x0340, 0x0360, 0x0360, 0x0380, 0x03a0, 0x03c0, 0x03e0, 0x0400, 0x0400, 0x0420, 0x0440, 0x0440, 0x0460, 0x0480, 0x0480, 0x04a0, 0x04c0, 0x04c0, 0x04c0, 0x04e0, 0x04e0, 0x0ce0, 0x0d00, 0x0d00, 0x0d00, 0x1520, 0x1520, 0x1540, 0x1540, 0x1d40, 0x1d60, 0x1d60, 0x2580, 0x2580, 0x25a0, 0x2da0, 0x2da0, 0x2dc0, 0x35c0, 0x35e0, 0x35e0, 0x3e00, 0x3e00, 0x3e00, 0x4620, 0x4620, 0x4e40, 0x4e40, 0x4e60, 0x5660, 0x5660, 0x5e80, 0x5e80, 0x5ea0, 0x66a0, 0x66c0, 0x66c0, 0x6ec0, 0x6ee0, 0x76e0, 0x7700, 0x7f00, 0x7f00, 0x7f20, 0x8720, 0x8720, 0x8f40, 0x8f40, 0x8f40, 0x9760, 0x9760, 0x9f80, 0x9f80, 0x9f80, 0xa780, 0xa7a0, 0xafa0, 0xafa0, 0xafc0, 0xb7c0, 0xb7c0, 0xbfc0, 0xbfc0, 0xbfe0, 0xc7e0, 0xc7e0, 0xc7e0, 0xcfe0, 0xcfe0, 0xcfe0, 0xd7e0, 0xd7e0, 0xdfe0, 0xdfe0, 0xdfe0, 0xdfe0, 0xe7e0, 0xe7e0, 0xe7e0, 0xefe0, 0xefe0, 0xefe0, 0xefe0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffc0, 0xffc0, 0xffc0, 0xffa0, 0xffa0, 0xffa0, 0xff80, 0xff80, 0xff80, 0xff60, 0xff60, 0xff40, 0xff40, 0xff40, 0xff20, 0xff00, 0xff00, 0xfee0, 0xfee0, 0xfec0, 0xfec0, 0xfea0, 0xfea0, 0xfe80, 0xfe60, 0xfe60, 0xfe40, 0xfe20, 0xfe20, 0xfe00, 0xfe00, 0xfde0, 0xfdc0, 0xfda0, 0xfda0, 0xfd80, 0xfd60, 0xfd60, 0xfd40, 0xfd20, 0xfd00, 0xfd00, 0xfce0, 0xfcc0, 0xfca0, 0xfca0, 0xfc80, 0xfc60, 0xfc40, 0xfc40, 0xfc20, 0xfc00, 0xfbe0, 0xfbe0, 0xfbc0, 0xfba0, 0xfb80, 0xfb60, 0xfb60, 0xfb40, 0xfb20, 0xfb00, 0xfb00, 0xfae0, 0xfac0, 0xfaa0, 0xfaa0, 0xfa80, 0xfa60, 0xfa60, 0xfa40, 0xfa20, 0xfa00, 0xfa00, 0xf9e0, 0xf9c0, 0xf9c0, 0xf9a0, 0xf980, 0xf980, 0xf960, 0xf960, 0xf940, 0xf920, 0xf920, 0xf900, 0xf8e0, 0xf8e0, 0xf8c0, 0xf8c0, 0xf8a0, 0xf8a0, 0xf880, 0xf880, 0xf860, 0xf860, 0xf860, 0xf840, 0xf840, 0xf820, 0xf820, 0xf820, 0xf800, 0xf800, 0xf800, 0xf800 };
float floatBuffer[(256 * 2) + (256 / 4)] __attribute__((aligned(16)));
#ifdef _MAY_HAVE_NEON_
unsigned int neonMode, neonDone;
int intBuffer[8] __attribute__((aligned(16)));
#endif

float commonCoefNew;
unsigned int commonColorIndex, commonColorIndexApplied, commonTime;

void JNICALL commonSetSpeed(JNIEnv* env, jclass clazz, int speed) {
	switch (speed) {
	case 1:
		commonCoefNew = 0.09375f / 16.0f; //0.09375 @ 60fps (~16ms)
		break;
	case 2:
		commonCoefNew = 0.140625f / 16.0f;
		break;
	default:
		commonCoefNew = 0.0625f / 16.0f;
		break;
	}
}

void JNICALL commonSetColorIndex(JNIEnv* env, jclass clazz, int jcolorIndex) {
	commonColorIndex = jcolorIndex;
}

int JNICALL commonCheckNeonMode(JNIEnv* env, jclass clazz) {
#ifdef _MAY_HAVE_NEON_
	if (neonDone == 1)
		return neonMode;
	//based on
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.c?r=2195
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.h?r=2195
	neonMode = 0;
	char cpuinfo[4096];
	int cpuinfo_len = -1;
	int fd = open("/proc/cpuinfo", O_RDONLY);
	if (fd >= 0) {
		do {
			cpuinfo_len = read(fd, cpuinfo, 4096);
		} while (cpuinfo_len < 0 && errno == EINTR);
		close(fd);
		if (cpuinfo_len > 0) {
			cpuinfo[cpuinfo_len] = 0;
			//look for the "\nFeatures: " line
			for (int i = cpuinfo_len - 9; i >= 0; i--) {
				if (memcmp(cpuinfo + i, "\nFeatures", 9) == 0) {
					i += 9;
					while (i < cpuinfo_len && (cpuinfo[i] == ' ' || cpuinfo[i] == '\t' || cpuinfo[i] == ':'))
						i++;
					cpuinfo_len -= 5;
					//now look for the " neon" feature
					while (i <= cpuinfo_len && cpuinfo[i] != '\n') {
						if (memcmp(cpuinfo + i, " neon", 5) == 0 ||
							memcmp(cpuinfo + i, "\tneon", 5) == 0) {
							neonMode = 1;
							break;
						}
						i++;
					}
					break;
				}
			}
			// __android_log_print(ANDROID_LOG_INFO, "JNI", "Neon mode: %d", neonMode);
		}
	}
	neonDone = 1;
	return neonMode;
#else
	return 0;
#endif
}

void JNICALL commonUpdateMultiplier(JNIEnv* env, jclass clazz, jboolean isVoice) {
	float* const fft = floatBuffer;
	float* const multiplier = fft + 256;
	unsigned char* const processedData = (unsigned char*)(fft + 512);
	if (isVoice) {
		for (int i = 0; i < 256; i++) {
			fft[i] = 0;
			processedData[i] = 0;
			//const double d = 180.0 - exp(1.0 / (((double)i / 10000.0) + 0.187));
			//multiplier[i] = ((d <= 1.5) ? 1.5f : (float)(d / 111.0));
			const double d = 5.0 * (400.0 - exp(1.0 / (((double)i / 3700.0) + 0.165)));
			multiplier[i] = ((d <= 256.0) ? 256.0f : (float)d) / 114.0f;
			//multiplier[i] = 2.0f * expf((float)i / 128.0f);
		}
	} else {
		for (int i = 0; i < 256; i++) {
			fft[i] = 0;
			processedData[i] = 0;
			//const double d = 180.0 - exp(1.0 / (((double)i / 10000.0) + 0.187));
			//multiplier[i] = ((d <= 1.5) ? 1.5f : (float)d);
			const double d = 5.0 * (400.0 - exp(1.0 / (((double)i / 3700.0) + 0.165)));
			multiplier[i] = ((d <= 256.0) ? 256.0f : (float)d);
			//multiplier[i] = 256.0f * expf((float)i / 128.0f);
		}
	}
}

int JNICALL commonProcess(JNIEnv* env, jclass clazz, jbyteArray jbfft, int deltaMillis, int bt) {
	commonTime += deltaMillis;

	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* bfft = (signed char*)env->GetPrimitiveArrayCritical(jbfft, 0);
	if (!bfft)
		return 0;
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = 0;

	unsigned char* processedData = (unsigned char*)(floatBuffer + 512);

	int i;
#ifdef _MAY_HAVE_NEON_
if (!neonMode) {
#endif
	float* fft = floatBuffer;
	const float* multiplier = floatBuffer + 256;

	const float coefNew = commonCoefNew * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;

	for (i = 0; i < 256; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		const int amplSq = (re * re) + (im * im);
		float m = ((amplSq < 8) ? 0.0f : (multiplier[i] * sqrtf((float)(amplSq))));
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
		//v goes from 0 to 32768+ (inclusive)
		const unsigned int v = ((unsigned int)m) >> 7;
		processedData[i] = ((v >= 255) ? 255 : (unsigned char)v);
	}
#ifdef _MAY_HAVE_NEON_
} else {
	commonProcessNeon(bfft, deltaMillis);
}
#endif
	if (!bt) {
		env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
		return 0;
	}


	//Bluetooth processing from here on


#define PACK_BIN(BIN) if ((BIN) == 0x01 || (BIN) == 0x1B) { *packet = 0x1B; packet[1] = ((unsigned char)(BIN) ^ 1); packet += 2; len += 2; } else { *packet = (unsigned char)(BIN); packet++; len++; }
#define MAX(A,B) (((A) > (B)) ? (A) : (B))
	unsigned char* packet = (unsigned char*)bfft;
	int len = 0, last;
	unsigned char avg;
	unsigned char b;
	packet[0] = 1; //SOH - Start of Heading
	packet[1] = (unsigned char)bt; //payload type
	//packet[2] and packet[3] are the payload length
	packet += 4;
	//processedData stores the first 256 bins, out of the 512 captured by visualizer.getFft
	//which represents frequencies from DC to SampleRate / 4 (roughly from 0Hz to 11000Hz for a SR of 44100Hz)
	//
	//the mapping algorithms used in SIZE_4, SIZE_8, SIZE_16, SIZE_32, SIZE_64 and in SIZE_128
	//were "empirically created", without too much of theory envolved ;)
	switch (bt) {
	case SIZE_4:
		avg = (unsigned char)(((unsigned int)processedData[0] + (unsigned int)processedData[1] + (unsigned int)processedData[2] + (unsigned int)processedData[3]) >> 2);
		PACK_BIN(avg);
		avg = 0;
		for (i = 4; i < 36; i++) //for (i = 5; i < 37; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 5;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 100; i++) //for (; i < 101; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 6;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 228; i++) //for (; i < 229; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 7;
		PACK_BIN(avg);
		break;
	case SIZE_8:
		avg = (unsigned char)(((unsigned int)processedData[0] + (unsigned int)processedData[1]) >> 1);
		PACK_BIN(avg);
		avg = (unsigned char)(((unsigned int)processedData[2] + (unsigned int)processedData[3]) >> 1);
		PACK_BIN(avg);
		avg = 0;
		for (i = 4; i < 20; i++) //for (i = 5; i < 21; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 4;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 36; i++) //for (; i < 37; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 4;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 68; i++) //for (; i < 69; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 5;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 100; i++) //for (; i < 101; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 5;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 164; i++) //for (; i < 165; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 6;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 228; i++) //for (; i < 229; i++)
			avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
		//avg >>= 6;
		PACK_BIN(avg);
		break;
	case SIZE_16:
		avg = (unsigned char)(((unsigned int)processedData[0] + (unsigned int)processedData[1]) >> 1);
		PACK_BIN(avg);
		avg = (unsigned char)(((unsigned int)processedData[2] + (unsigned int)processedData[3]) >> 1);
		PACK_BIN(avg);
		for (i = 4; i < 20; i += 4) { //for (i = 5; i < 21; i += 4) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2);
			PACK_BIN(avg);
		}
		for (last = 28; last <= 36; last += 8) { //for (last = 29; last <= 37; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 3;
			PACK_BIN(avg);
		}
		for (last = 52; last <= 100; last += 16) { //for (last = 53; last <= 101; last += 16) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 4;
			PACK_BIN(avg);
		}
		for (last = 132; last <= 228; last += 32) { //for (last = 133; last <= 229; last += 32) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 5;
			PACK_BIN(avg);
		}
		break;
	case SIZE_32:
		b = processedData[0];
		PACK_BIN(b);
		b = processedData[1];
		PACK_BIN(b);
		b = processedData[2];
		PACK_BIN(b);
		b = processedData[3];
		PACK_BIN(b);
		for (i = 4; i < 20; i += 2) { //for (i = 5; i < 21; i += 2) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1);
			PACK_BIN(avg);
		}
		for (; i < 36; i += 4) { //for (; i < 37; i += 4) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2);
			PACK_BIN(avg);
		}
		for (last = 44; last <= 100; last += 8) { //for (last = 45; last <= 101; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 3;
			PACK_BIN(avg);
		}
		for (last = 116; last <= 228; last += 16) { //for (last = 117; last <= 229; last += 16) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 4;
			PACK_BIN(avg);
		}
		break;
	case SIZE_64:
		for (i = 0; i < 20; i++) { //for (i = 1; i < 21; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		for (; i < 36; i += 2) { //for (; i < 37; i += 2) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1);
			PACK_BIN(avg);
		}
		for (; i < 132; i += 4) { //for (; i < 133; i += 4) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2);
			PACK_BIN(avg);
		}
		for (last = 140; last <= 228; last += 8) { //for (last = 141; last <= 229; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 3;
			PACK_BIN(avg);
		}
		break;
	case SIZE_128:
		for (i = 0; i < 36; i++) { //for (i = 1; i < 37; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		for (; i < 184; i += 2) { //for (; i < 185; i += 2) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1);
			PACK_BIN(avg);
		}
		for (; i < 252; i += 4) { //for (; i < 253; i += 4) {
			avg = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2);
			PACK_BIN(avg);
		}
		break;
	case SIZE_256:
		for (i = 0; i < 256; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		break;
	default:
		env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
		return 0;
	}
#undef PACK_BIN
	//fill in the payload length
	((unsigned char*)bfft)[2] = (len & 0x7F) << 1; //lower 7 bits, left shifted by 1
	((unsigned char*)bfft)[3] = (len >> 6) & 0xFE; //upper 7 bits, left shifted by 1
	*packet = 4; //EOT - End of Transmission
	env->ReleasePrimitiveArrayCritical(jbfft, bfft, 0);
	return len + 5;
}
