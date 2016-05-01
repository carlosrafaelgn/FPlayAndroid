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

//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
//http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html

#define MAX(A,B) (((A) > (B)) ? (A) : (B))

//when channelCount is 1, the frame size is 2 (16 bits per sample, mono)
//when channelCount is 2, the frame size is 4 (16 bits per sample, stereo)
//therefore:
//frames = bytes >> channelCount
//bytes = frames << channelCount;
static unsigned int equalizerEnabled, bassBoostEnabled, channelCount, sampleRate, bufferSizeInFrames, writePositionInFrames;
static unsigned short* visualizerBuffer;

void JNICALL setBufferSizeInFrames(JNIEnv* env, jclass clazz, unsigned int bufferSizeInFrames) {
	::bufferSizeInFrames = bufferSizeInFrames;
}

void JNICALL configChanged(JNIEnv* env, jclass clazz, int channelCount, int sampleRate) {
	::channelCount = channelCount;
	::sampleRate = sampleRate;
}

void JNICALL resetFiltersAndWritePosition(JNIEnv* env, jclass clazz, unsigned int bufferSizeInFrames, unsigned int writePositionInFrames) {
	::bufferSizeInFrames = bufferSizeInFrames;
	::writePositionInFrames = writePositionInFrames % bufferSizeInFrames;
	//zero out all filter-related structures
}

void JNICALL enableEqualizer(JNIEnv* env, jclass clazz, int enabled) {
	equalizerEnabled = enabled;
}

void JNICALL enableBassBoost(JNIEnv* env, jclass clazz, int enabled) {
	bassBoostEnabled = enabled;
}

uint64_t JNICALL startVisualization(JNIEnv* env, jclass clazz) {
	if (visualizerBuffer)
		delete visualizerBuffer;
	visualizerBuffer = new unsigned short[bufferSizeInFrames];
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

void swapShorts(unsigned short* buffer, unsigned int sizeInShorts) {
	while (sizeInShorts) {
		*buffer = bswap_16(*buffer);
		buffer++;
		sizeInShorts++;
	}
}

void doProcess(unsigned short* buffer, unsigned int sizeInFrames) {
}

int JNICALL processDirectData(JNIEnv* env, jclass clazz, jobject jbuffer, int offsetInBytes, int sizeInBytes, int needsSwap) {
	const unsigned int sizeInFrames = (sizeInBytes >> channelCount);

	if (!equalizerEnabled && !bassBoostEnabled && !visualizerBuffer) {
		//we must keep track of the write position, no matter what!
		writePositionInFrames = (writePositionInFrames + sizeInFrames) % bufferSizeInFrames;
		return 0;
	}

	unsigned short* buffer = (unsigned short*)env->GetDirectBufferAddress(jbuffer);
	if (!buffer)
		return -1;

	//swap all shorts!!!
	if (needsSwap)
		swapShorts((unsigned short*)((unsigned char*)buffer + offsetInBytes), sizeInBytes >> 1);

	//doProcess updates writePositionInFrames internally
	doProcess((unsigned short*)((unsigned char*)buffer + offsetInBytes), sizeInFrames);

	return 0;
}

int JNICALL processData(JNIEnv* env, jclass clazz, jbyteArray jbuffer, unsigned int offsetInBytes, unsigned int sizeInBytes, int needsSwap) {
	const unsigned int sizeInFrames = (sizeInBytes >> channelCount);

	if (!equalizerEnabled && !bassBoostEnabled && !visualizerBuffer) {
		//we must keep track of the write position, no matter what!
		writePositionInFrames = (writePositionInFrames + sizeInFrames) % bufferSizeInFrames;
		return 0;
	}

	unsigned short* buffer = (unsigned short*)env->GetPrimitiveArrayCritical(jbuffer, 0);
	if (!buffer)
		return -1;

	//swap all shorts!!!
	if (needsSwap)
		swapShorts((unsigned short*)((unsigned char*)buffer + offsetInBytes), sizeInBytes >> 1);

	//doProcess updates writePositionInFrames internally
	doProcess((unsigned short*)((unsigned char*)buffer + offsetInBytes), sizeInFrames);

	//if neither the equalizer nor the bass boost are enabled, we do not copy data back
	env->ReleasePrimitiveArrayCritical(jbuffer, buffer, (equalizerEnabled || bassBoostEnabled) ? 0 : JNI_ABORT);

	return 0;
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
	equalizerEnabled = 0;
	bassBoostEnabled = 0;
	channelCount = 0;
	sampleRate = 0;
	bufferSizeInFrames = 0;
	writePositionInFrames = 0;
	visualizerBuffer = 0;

	JNINativeMethod methodTable[] = {
		{"setBufferSizeInFrames", "(I)V", (void*)setBufferSizeInFrames},
		{"configChanged", "(II)V", (void*)configChanged},
		{"resetFiltersAndWritePosition", "(I)V", (void*)resetFiltersAndWritePosition},
		{"enableEqualizer", "(I)V", (void*)enableEqualizer},
		{"enableBassBoost", "(I)V", (void*)enableBassBoost},
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
