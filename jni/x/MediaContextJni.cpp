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
#include <time.h>

#define FFT_SIZE 1024 //FFT_SIZE must be a power of 2 <= 65536
#define QUARTER_FFT_SIZE (FFT_SIZE / 4)

#define FEATURE_PROCESSOR_ARM 0x0001
#define FEATURE_PROCESSOR_NEON 0x0002
#define FEATURE_PROCESSOR_X86 0x0004
#define FEATURE_PROCESSOR_SSE 0x0008
#define FEATURE_PROCESSOR_SSE41 0x0010
#define FEATURE_PROCESSOR_64_BITS 0x0020
#define FEATURE_DECODING_NATIVE 0x0040
#define FEATURE_DECODING_DIRECT 0x0080

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
#else
//https://developer.android.com/ndk/guides/abis.html#x86
//https://developer.android.com/ndk/guides/abis.html#86-64
//All x86 Android devices use at least Atom processors (all Atom processor have SSE, SSE2, SSE3 and SSSE3 support, and all 64-bits Atom have SSE4.1/4.2 support)
#include <xmmintrin.h> //SSE
#include <emmintrin.h> //SSE2
#include <pmmintrin.h> //SSE3
#include <tmmintrin.h> //SSSE3
//https://software.intel.com/sites/landingpage/IntrinsicsGuide/
#endif

//when channelCount is 1, the frame size is 2 (16 bits per sample, mono)
//when channelCount is 2, the frame size is 4 (16 bits per sample, stereo)
//therefore:
//frames = bytes >> channelCount
//bytes = frames << channelCount;
static uint32_t srcSampleRate, srcChannelCount;
uint32_t dstSampleRate;
static int16_t *tmpSwapBufferForAudioTrack;

union int64_3232 {
	int64_t v;
	struct {
		int32_t l;
		int32_t h;
	};
};

union WriteRet {
    uint64_t val;
    struct {
        uint32_t dstFramesUsed; //low
        uint32_t srcFramesUsed; //high
    };
};

uint32_t uptimeMillis() {
	struct timespec t;
	t.tv_sec = 0;
	t.tv_nsec = 0;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return (uint32_t)((t.tv_sec * 1000) + (t.tv_nsec / 1000000));
}

void swapShortsInplace(int16_t* buffer, uint32_t sizeInShorts) {
	while (sizeInShorts) {
		*buffer = bswap_16(*buffer);
		buffer++;
		sizeInShorts--;
	}
}

void swapShorts(int16_t* srcBuffer, uint32_t sizeInShorts, int16_t* dstBuffer) {
	while (sizeInShorts) {
		*dstBuffer = bswap_16(*srcBuffer);
		srcBuffer++;
		dstBuffer++;
		sizeInShorts--;
	}
}

#include "Visualizer.h"
#include "Effects.h"
#include "Resampler.h"
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
	//https://developer.android.com/ndk/guides/abis.html#x86
	//https://developer.android.com/ndk/guides/abis.html#86-64
	//All x86 Android devices use at least Atom processors (all Atom processor have SSE, SSE2, SSE3 and SSSE3 support, and all 64-bits Atom have SSE4.1/4.2 support)
	#ifdef FPLAY_64_BITS
		return FEATURE_PROCESSOR_X86 | FEATURE_PROCESSOR_SSE | FEATURE_PROCESSOR_SSE41 | FEATURE_PROCESSOR_64_BITS;
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
		resetResamplerState();
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

	//we must use a temporary buffer whenever swapping is necessary
	int16_t* actualSrcBuffer;
	if (needsSwap) {
		if (!tmpSwapBufferForAudioTrack)
			tmpSwapBufferForAudioTrack = new int16_t[MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 1];
		swapShorts((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), sizeInFrames << (srcChannelCount - 1), tmpSwapBufferForAudioTrack);
		actualSrcBuffer = tmpSwapBufferForAudioTrack;
	} else {
		actualSrcBuffer = (int16_t*)((uint8_t*)srcBuffer + offsetInBytes);
	}

	WriteRet ret;
	ret.srcFramesUsed = 0;
	ret.dstFramesUsed = resampleProc(actualSrcBuffer, sizeInFrames, dstBuffer, MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 1, ret.srcFramesUsed);

	if (!jsrcBuffer)
		env->ReleasePrimitiveArrayCritical(jsrcArray, srcBuffer, JNI_ABORT);

	advanceVisualizer(dstBuffer, ret.dstFramesUsed);

	effectProc(dstBuffer, ret.dstFramesUsed);

	if (!jdstBuffer)
		env->ReleasePrimitiveArrayCritical(jdstArray, dstBuffer, 0);

	return ret.val;
}

int64_t JNICALL audioTrackProcessNativeEffects(JNIEnv* env, jclass clazz, uint64_t nativeObj, uint32_t offsetInBytes, uint32_t sizeInFrames, jobject jdstBuffer) {
	if (!nativeObj || !((MediaCodec*)nativeObj)->buffer || !jdstBuffer)
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	int16_t* const dstBuffer = (int16_t*)env->GetDirectBufferAddress(jdstBuffer);
	if (!dstBuffer)
		return -SL_RESULT_MEMORY_FAILURE;

	WriteRet ret;
	ret.srcFramesUsed = 0;
	ret.dstFramesUsed = resampleProc((int16_t*)(((MediaCodec*)nativeObj)->buffer + offsetInBytes), sizeInFrames, dstBuffer, MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING << 1, ret.srcFramesUsed);

	advanceVisualizer(dstBuffer, ret.dstFramesUsed);

	effectProc(dstBuffer, ret.dstFramesUsed);

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
	initializeVisualizer();
	tmpSwapBufferForAudioTrack = 0;

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
		{"mediaCodecFillInputBuffers", "(J)I", (void*)mediaCodecFillInputBuffers},
		{"mediaCodecNextOutputBuffer", "(J)I", (void*)mediaCodecNextOutputBuffer},
		{"mediaCodecSeek", "(JI)J", (void*)mediaCodecSeek},
		{"mediaCodecReleaseOutputBuffer", "(J)V", (void*)mediaCodecReleaseOutputBuffer},
		{"mediaCodecRelease", "(J)V", (void*)mediaCodecRelease},
		{"mediaCodecLoadExternalLibrary", "()I", (void*)mediaCodecLoadExternalLibrary},
		{"audioTrackInitialize", "()V", (void*)audioTrackInitialize},
		{"audioTrackCreate", "(I)V", (void*)audioTrackCreate},
		{"audioTrackProcessNativeEffects", "(JIILjava/nio/ByteBuffer;)J", (void*)audioTrackProcessNativeEffects},
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
		{"openSLWriteNative", "(JII)J", (void*)openSLWriteNative},
		{"openSLWrite", "([BLjava/nio/ByteBuffer;III)J", (void*)openSLWrite},
		{"visualizerStart", "(II)I", (void*)visualizerStart},
		{"visualizerStop", "()V", (void*)visualizerStop},
		{"visualizerZeroOut", "()V", (void*)visualizerZeroOut},
		{"visualizerGetWaveform", "([BI)V", (void*)visualizerGetWaveform}
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
	terminateVisualizer();
	if (tmpSwapBufferForAudioTrack) {
		delete tmpSwapBufferForAudioTrack;
		tmpSwapBufferForAudioTrack = 0;
	}
}

}
