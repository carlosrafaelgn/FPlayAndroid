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
#include <android/log.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
#include <byteswap.h>

#define FFT_SIZE 1024 //FFT_SIZE must be a power of 2 <= 65536
#define QUARTER_FFT_SIZE (FFT_SIZE / 4)

#define PROCESSOR_FEATURE_NEON 1
#define PROCESSOR_FEATURE_SSE 2

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
	//x86 or x86_64
	#define FPLAY_X86
#elif defined(__arm__) || defined(__aarch64__)
	//arm or arm64
	#define FPLAY_ARM
#else
	#error ("Unknown platform!")
#endif

//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html

//when channelCount is 1, the frame size is 2 (16 bits per sample, mono)
//when channelCount is 2, the frame size is 4 (16 bits per sample, stereo)
//therefore:
//frames = bytes >> channelCount
//bytes = frames << channelCount;
static unsigned int channelCount, sampleRate, bufferSizeInFrames, writePositionInFrames;
static short* visualizerBuffer;

#ifdef FPLAY_ARM
	#include <errno.h>
	#include <fcntl.h>
	static unsigned int neonMode;
#endif

#include "Equalizer_BassBoost.h"

unsigned int JNICALL getProcessorFeatures(JNIEnv* env, jclass clazz) {
#ifdef FPLAY_X86
	//http://developer.android.com/intl/pt-br/ndk/guides/abis.html#x86
	//All x86 Android devices use at least Atom processors (and all Atom processor have SSE, SSE2, SSE3 support)
	return PROCESSOR_FEATURE_SSE;
#else
	return neonMode;
#endif
}

void JNICALL setBufferSizeInFrames(JNIEnv* env, jclass clazz, unsigned int bufferSizeInFrames) {
	::bufferSizeInFrames = bufferSizeInFrames;
}

void JNICALL configChanged(JNIEnv* env, jclass clazz, int channelCount, int sampleRate) {
	::channelCount = channelCount;
	::sampleRate = sampleRate;

	equalizerConfigChanged();
}

void JNICALL resetFiltersAndWritePosition(JNIEnv* env, jclass clazz, unsigned int writePositionInFrames) {
	::writePositionInFrames = writePositionInFrames % bufferSizeInFrames;

	resetEqualizer();
}

uint64_t JNICALL startVisualization(JNIEnv* env, jclass clazz) {
	if (visualizerBuffer)
		delete visualizerBuffer;
	visualizerBuffer = new short[bufferSizeInFrames];
	writePositionInFrames = 0;
	return (uint64_t)visualizerBuffer;
}

uint64_t JNICALL getVisualizationPtr(JNIEnv* env, jclass clazz) {
	return (uint64_t)visualizerBuffer;
}

void JNICALL stopVisualization(JNIEnv* env, jclass clazz) {
	if (visualizerBuffer) {
		delete visualizerBuffer;
		visualizerBuffer = 0;
	}
}

void swapShorts(short* buffer, unsigned int sizeInShorts) {
	while (sizeInShorts) {
		*buffer = bswap_16(*buffer);
		buffer++;
		sizeInShorts++;
	}
}

int JNICALL processDirectData(JNIEnv* env, jclass clazz, jobject jbuffer, int offsetInBytes, int sizeInBytes, int needsSwap) {
	const unsigned int sizeInFrames = (sizeInBytes >> channelCount);

	if (equalizerEnabled || visualizerBuffer) {
		short* const buffer = (short*)env->GetDirectBufferAddress(jbuffer);
		if (!buffer) {
			//we must keep track of the write position, no matter what!
			writePositionInFrames = (writePositionInFrames + sizeInFrames) % bufferSizeInFrames;
			return -1;
		}

		//swap all shorts!!!
		if (needsSwap)
			swapShorts((short*)((unsigned char*)buffer + offsetInBytes), sizeInBytes >> 1);

		if (equalizerEnabled)
			processEqualizer((short*)((unsigned char*)buffer + offsetInBytes), sizeInFrames);
	}

	//we must keep track of the write position, no matter what!
	writePositionInFrames = (writePositionInFrames + sizeInFrames) % bufferSizeInFrames;

	return 0;
}

int JNICALL processData(JNIEnv* env, jclass clazz, jbyteArray jbuffer, unsigned int offsetInBytes, unsigned int sizeInBytes, int needsSwap) {
	const unsigned int sizeInFrames = (sizeInBytes >> channelCount);

	if (equalizerEnabled || visualizerBuffer) {
		short* const buffer = (short*)env->GetPrimitiveArrayCritical(jbuffer, 0);
		if (!buffer) {
			//we must keep track of the write position, no matter what!
			writePositionInFrames = (writePositionInFrames + sizeInFrames) % bufferSizeInFrames;
			return -1;
		}

		//swap all shorts!!!
		if (needsSwap)
			swapShorts((short*)((unsigned char*)buffer + offsetInBytes), sizeInBytes >> 1);

		if (equalizerEnabled)
			processEqualizer((short*)((unsigned char*)buffer + offsetInBytes), sizeInFrames);

		//if neither the equalizer nor the bass boost are enabled, we do not copy data back (JNI_ABORT)
		env->ReleasePrimitiveArrayCritical(jbuffer, buffer, equalizerEnabled ? 0 : JNI_ABORT);
	}

	//we must keep track of the write position, no matter what!
	writePositionInFrames = (writePositionInFrames + sizeInFrames) % bufferSizeInFrames;

	return 0;
}

#ifdef FPLAY_ARM
void checkNeonMode() {
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
		}
	}
}
#endif

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
#ifdef FPLAY_ARM
	checkNeonMode();
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "Neon mode: %d", neonMode);
#endif
	channelCount = 2;
	sampleRate = 44100;
	bufferSizeInFrames = 0;
	writePositionInFrames = 0;
	visualizerBuffer = 0;
	initializeEqualizer();

	JNINativeMethod methodTable[] = {
		{"setBufferSizeInFrames", "(I)V", (void*)setBufferSizeInFrames},
		{"configChanged", "(II)V", (void*)configChanged},
		{"resetFiltersAndWritePosition", "(I)V", (void*)resetFiltersAndWritePosition},
		{"enableEqualizer", "(I)V", (void*)enableEqualizer},
		{"isEqualizerEnabled", "()I", (void*)isEqualizerEnabled},
		{"setEqualizerBandLevel", "(II)V", (void*)setEqualizerBandLevel},
		{"setEqualizerBandLevels", "([S)V", (void*)setEqualizerBandLevels},
		{"enableBassBoost", "(I)V", (void*)enableBassBoost},
		{"isBassBoostEnabled", "()I", (void*)isBassBoostEnabled},
		{"setBassBoostStrength", "(I)V", (void*)setBassBoostStrength},
		{"getBassBoostRoundedStrength", "()I", (void*)getBassBoostRoundedStrength},
		{"startVisualization", "()J", (void*)startVisualization},
		{"getVisualizationPtr", "()J", (void*)getVisualizationPtr},
		{"stopVisualization", "()V", (void*)stopVisualization},
		{"processDirectData", "(Ljava/nio/ByteBuffer;III)I", (void*)processDirectData},
		{"processData", "([BIII)I", (void*)processData}
	};
	JNIEnv* env;
	if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
		return -1;
	jclass clazz = env->FindClass("br/com/carlosrafaelgn/fplay/playback/context/MediaContext");
	if (!clazz)
		return -1;
	env->RegisterNatives(clazz, methodTable, sizeof(methodTable) / sizeof(methodTable[0]));
	return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
	stopVisualization(0, 0);
}

}
