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

#define FEATURE_PROCESSOR_ARM 0x0001
#define FEATURE_PROCESSOR_NEON 0x0002
#define FEATURE_PROCESSOR_X86 0x0004
#define FEATURE_PROCESSOR_SSE 0x0008
#define FEATURE_PROCESSOR_64_BITS 0x0010
#define FEATURE_DECODING_NATIVE 0x0020
#define FEATURE_DECODING_DIRECT 0x0040

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

//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html

#ifdef FPLAY_ARM
	#include <errno.h>
	#include <fcntl.h>
	static uint32_t neonMode;
#endif

#define MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING 1152

//when channelCount is 1, the frame size is 2 (16 bits per sample, mono)
//when channelCount is 2, the frame size is 4 (16 bits per sample, stereo)
//therefore:
//frames = bytes >> channelCount
//bytes = frames << channelCount;
static uint32_t srcSampleRate, srcChannelCount;
uint32_t dstSampleRate;

union WriteRet {
    uint64_t val;
    struct {
        uint32_t dstFramesUsed; //low
        uint32_t srcFramesUsed; //high
    };
};

void swapShorts(int16_t* buffer, uint32_t sizeInShorts) {
	while (sizeInShorts) {
		*buffer = bswap_16(*buffer);
		buffer++;
		sizeInShorts++;
	}
}

#include "Resampler.h"
#include "Effects.h"
#include "MediaCodec.h"
#include "OpenSL.h"

uint32_t JNICALL getProcessorFeatures(JNIEnv* env, jclass clazz) {
#ifdef FPLAY_ARM
	#ifdef FPLAY_64_BITS
		return FEATURE_PROCESSOR_ARM | neonMode | FEATURE_PROCESSOR_64_BITS;
	#else
		return FEATURE_PROCESSOR_ARM | neonMode;
	#endif
#else
	//http://developer.android.com/intl/pt-br/ndk/guides/abis.html#x86
	//All x86 Android devices use at least Atom processors (and all Atom processor have SSE, SSE2, SSE3 support)
	#ifdef FPLAY_64_BITS
		return FEATURE_PROCESSOR_X86 | FEATURE_PROCESSOR_SSE | FEATURE_PROCESSOR_64_BITS;
	#else
		return FEATURE_PROCESSOR_X86 | FEATURE_PROCESSOR_SSE;
	#endif
#endif
}

void JNICALL updateSrcParams(JNIEnv* env, jclass clazz, uint32_t srcSampleRate, uint32_t srcChannelCount, uint32_t resetFiltersAndWritePosition) {
	if (::srcSampleRate != srcSampleRate || ::srcChannelCount != srcChannelCount) {
		::srcSampleRate = srcSampleRate;
		::srcChannelCount = srcChannelCount;
		resetResampler();
	}

	if (resetFiltersAndWritePosition) {
		resetOpenSL();
		resetEqualizer();
		resetVirtualizer();
	}
}

void JNICALL audioTrackInitialize(JNIEnv* env, jclass clazz) {
	equalizerConfigChanged();
	virtualizerConfigChanged();
}

void JNICALL audioTrackCreate(JNIEnv* env, jclass clazz, uint32_t dstSampleRate) {
	if (::dstSampleRate != dstSampleRate) {
		::dstSampleRate = dstSampleRate;
		resetResampler();
		equalizerConfigChanged();
		virtualizerConfigChanged();
	}
}

int64_t JNICALL audioTrackProcessEffects(JNIEnv* env, jclass clazz, jbyteArray jsrcArray, jobject jsrcBuffer, uint32_t offsetInBytes, uint32_t sizeInFrames, uint32_t needsSwap, jbyteArray jdstArray, jobject jdstBuffer) {
	int16_t* const srcBuffer = (int16_t*)(jsrcBuffer ? env->GetDirectBufferAddress(jsrcBuffer) : env->GetPrimitiveArrayCritical(jsrcArray, 0));
	if (!srcBuffer)
		return -SL_RESULT_MEMORY_FAILURE;

	int16_t* const dstBuffer = (int16_t*)(jdstBuffer ? env->GetDirectBufferAddress(jdstBuffer) : env->GetPrimitiveArrayCritical(jdstArray, 0));
	if (!dstBuffer) {
		if (!jsrcBuffer)
			env->ReleasePrimitiveArrayCritical(jsrcArray, srcBuffer, JNI_ABORT);
		return -SL_RESULT_MEMORY_FAILURE;
	}

	//this is not ok... (should be using a temporary buffer)
	if (needsSwap)
		swapShorts((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), sizeInFrames << (srcChannelCount - 1));

	WriteRet ret;
	ret.srcFramesUsed = 0;
	ret.dstFramesUsed = resampleProc((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), sizeInFrames, dstBuffer, MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING, ret.srcFramesUsed);

	effectProc(dstBuffer, ret.dstFramesUsed, dstBuffer);

	if (!jsrcBuffer)
		env->ReleasePrimitiveArrayCritical(jsrcArray, srcBuffer, JNI_ABORT);

	if (!jdstBuffer)
		env->ReleasePrimitiveArrayCritical(jdstArray, dstBuffer, 0);

	return ret.val;
}

#ifdef FPLAY_ARM
void checkNeonMode() {
	//based on
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.c?r=2195
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.h?r=2195
	neonMode = 0;
	char cpuinfo[4096];
	int32_t cpuinfo_len = -1;
	int32_t fd = open("/proc/cpuinfo", O_RDONLY);
	if (fd >= 0) {
		do {
			cpuinfo_len = read(fd, cpuinfo, 4096);
		} while (cpuinfo_len < 0 && errno == EINTR);
		close(fd);
		if (cpuinfo_len > 0) {
			cpuinfo[cpuinfo_len] = 0;
			//look for the "\nFeatures: " line
			for (int32_t i = cpuinfo_len - 9; i >= 0; i--) {
				if (memcmp(cpuinfo + i, "\nFeatures", 9) == 0) {
					i += 9;
					while (i < cpuinfo_len && (cpuinfo[i] == ' ' || cpuinfo[i] == '\t' || cpuinfo[i] == ':'))
						i++;
					cpuinfo_len -= 5;
					//now look for the " neon" feature
					while (i <= cpuinfo_len && cpuinfo[i] != '\n') {
						if (memcmp(cpuinfo + i, " neon", 5) == 0 ||
							memcmp(cpuinfo + i, "\tneon", 5) == 0) {
							neonMode = FEATURE_PROCESSOR_NEON;
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
#endif
	initializeOpenSL();
	initializeEffects();
	initializeMediaCodec();
	initializeResampler();

	JNINativeMethod methodTable[] = {
		{"getProcessorFeatures", "()I", (void*)getProcessorFeatures},
		{"updateSrcParams", "(III)V", (void*)updateSrcParams},
		{"getCurrentAutomaticEffectsGainInMB", "()I", (void*)getCurrentAutomaticEffectsGainInMB},
		{"enableAutomaticEffectsGain", "(I)V", (void*)enableAutomaticEffectsGain},
		{"isAutomaticEffectsGainEnabled", "()I", (void*)isAutomaticEffectsGainEnabled},
		{"enableEqualizer", "(I)V", (void*)enableEqualizer},
		{"isEqualizerEnabled", "()I", (void*)isEqualizerEnabled},
		{"setEqualizerBandLevel", "(II)V", (void*)setEqualizerBandLevel},
		{"setEqualizerBandLevels", "([S)V", (void*)setEqualizerBandLevels},
		{"enableBassBoost", "(I)V", (void*)enableBassBoost},
		{"isBassBoostEnabled", "()I", (void*)isBassBoostEnabled},
		{"setBassBoostStrength", "(I)V", (void*)setBassBoostStrength},
		{"getBassBoostRoundedStrength", "()I", (void*)getBassBoostRoundedStrength},
		{"enableVirtualizer", "(I)V", (void*)enableVirtualizer},
		{"isVirtualizerEnabled", "()I", (void*)isVirtualizerEnabled},
		{"setVirtualizerStrength", "(I)V", (void*)setVirtualizerStrength},
		{"getVirtualizerRoundedStrength", "()I", (void*)getVirtualizerRoundedStrength},
		{"mediaCodecPrepare", "(IJ[J)I", (void*)mediaCodecPrepare},
		{"mediaCodecNextOutputBuffer", "(J)I", (void*)mediaCodecNextOutputBuffer},
		{"mediaCodecSeek", "(JI)J", (void*)mediaCodecSeek},
		{"mediaCodecReleaseOutputBuffer", "(J)V", (void*)mediaCodecReleaseOutputBuffer},
		{"mediaCodecRelease", "(J)V", (void*)mediaCodecRelease},
		{"mediaCodecLoadExternalLibrary", "()I", (void*)mediaCodecLoadExternalLibrary},
		{"audioTrackInitialize", "()V", (void*)audioTrackInitialize},
		{"audioTrackCreate", "(I)V", (void*)audioTrackCreate},
		{"audioTrackProcessEffects", "([BLjava/nio/ByteBuffer;III[BLjava/nio/ByteBuffer;)J", (void*)audioTrackProcessEffects},
		{"openSLInitialize", "()I", (void*)openSLInitialize},
		{"openSLCreate", "(III)I", (void*)openSLCreate},
		{"openSLPlay", "()I", (void*)openSLPlay},
		{"openSLPause", "()I", (void*)openSLPause},
		{"openSLStopAndFlush", "()I", (void*)openSLStopAndFlush},
		{"openSLRelease", "()V", (void*)openSLRelease},
		{"openSLTerminate", "()V", (void*)openSLTerminate},
		{"openSLSetVolumeInMillibels", "(I)V", (void*)openSLSetVolumeInMillibels},
		{"openSLGetHeadPositionInFrames", "()I", (void*)openSLGetHeadPositionInFrames},
		{"openSLCopyVisualizerData", "(J)V", (void*)openSLCopyVisualizerData},
		{"openSLWriteNative", "(JII)J", (void*)openSLWriteNative},
		{"openSLWrite", "([BLjava/nio/ByteBuffer;III)J", (void*)openSLWrite}
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
	openSLTerminate(0, 0);
	terminateMediaCodec();
	terminateResampler();
}

}
