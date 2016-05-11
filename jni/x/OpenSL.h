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

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

//https://www.khronos.org/registry/sles/specs/OpenSL_ES_Specification_1.0.1.pdf
//https://gcc.gnu.org/onlinedocs/gcc-4.4.3/gcc/Atomic-Builtins.html
//https://android.googlesource.com/platform/system/media/+/gingerbread/opensles/libopensles/IBufferQueue.c

//engine interfaces
static SLObjectItf engineObject;
static SLEngineItf engineEngine;

//output mix interfaces
static SLObjectItf outputMixObject;

//buffer queue player interfaces
static SLObjectItf bqPlayerObject;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;
static SLVolumeItf bqPlayerVolume;

struct BufferDescription {
	unsigned int sizeInFrames;
	unsigned int startOffsetInFrames;
	unsigned int commitedFrames;
};

static unsigned char* fullBuffer;
static BufferDescription* bufferDescriptors;
static unsigned int headPositionInFrames, emptyFrames, bufferCount, bufferWriteIndex, bufferReadIndex, emptyBuffers;
static size_t contextVersion;

void resetOpenSL() {
	writePositionInFrames = 0;
	headPositionInFrames = 0;
	emptyFrames = bufferSizeInFrames;
	bufferWriteIndex = 0;
	bufferReadIndex = 0;
	emptyBuffers = bufferCount;
}

void initializeOpenSL() {
	engineObject = 0;
	engineEngine = 0;
	outputMixObject = 0;
	bqPlayerObject = 0;
	bqPlayerPlay = 0;
	bqPlayerBufferQueue = 0;
	bqPlayerVolume = 0;

	fullBuffer = 0;
	bufferDescriptors = 0;
	contextVersion = 0;
	srcChannelCount = 2;
	sampleRate = 44100;
	bufferSizeInFrames = 0;
	bufferCount = 0;

	resetOpenSL();
}

//this callback handler is called every time a buffer finishes playing
void openSLBufferCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
	if (bq == bqPlayerBufferQueue && bufferDescriptors && (void*)contextVersion == context) {
		const BufferDescription thisBuffer = bufferDescriptors[bufferReadIndex];

		//this is an always incrementing counter
		headPositionInFrames += thisBuffer.sizeInFrames;
		__sync_add_and_fetch(&emptyFrames, thisBuffer.commitedFrames);

		bufferReadIndex = bufferReadIndex + 1;
		if (bufferReadIndex >= bufferCount)
			bufferReadIndex = 0;
		__sync_add_and_fetch(&emptyBuffers, 1);
	}
}

void JNICALL openSLRelease(JNIEnv* env, jclass clazz) {
	contextVersion++;

	//destroy buffer queue audio player object, and invalidate all associated interfaces
	if (bqPlayerPlay)
		(*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);

	if (bqPlayerBufferQueue)
		(*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);

	if (bqPlayerObject) {
		(*bqPlayerObject)->Destroy(bqPlayerObject);
		bqPlayerObject = 0;
		bqPlayerPlay = 0;
		bqPlayerBufferQueue = 0;
		bqPlayerVolume = 0;
	}
}

void JNICALL openSLTerminate(JNIEnv* env, jclass clazz) {
	openSLRelease(env, clazz);

	//destroy output mix object, and invalidate all associated interfaces
	if (outputMixObject) {
		(*outputMixObject)->Destroy(outputMixObject);
		outputMixObject = 0;
	}

	//destroy engine object, and invalidate all associated interfaces
	if (engineObject) {
		(*engineObject)->Destroy(engineObject);
		engineObject = 0;
		engineEngine = 0;
	}

	if (fullBuffer) {
		delete fullBuffer;
		fullBuffer = 0;
	}

	if (bufferDescriptors) {
		delete bufferDescriptors;
		bufferDescriptors = 0;
	}

	bufferCount = 0;
}

int JNICALL openSLInitialize(JNIEnv* env, jclass clazz, unsigned int bufferSizeInFrames) {
	openSLTerminate(env, clazz);

	equalizerConfigChanged();
	virtualizerConfigChanged();

	SLresult result;

	//create engine
	result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//realize the engine
	result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//get the engine interface, which is needed in order to create other objects
	result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//create the output mix
	result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, 0, 0);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//realize the output mix
	result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
	if (result != SL_RESULT_SUCCESS)
		return result;

	::bufferSizeInFrames = bufferSizeInFrames;

	//we always output stereo audio, regardless of the input config
	fullBuffer = new unsigned char[bufferSizeInFrames << 2];
	if (!fullBuffer)
		return SL_RESULT_MEMORY_FAILURE;

	bufferCount = (bufferSizeInFrames + 511) >> 9;
	if (bufferCount > 256)
		bufferCount = 256;

	bufferDescriptors = new BufferDescription[bufferCount];
	if (!bufferDescriptors)
		return SL_RESULT_MEMORY_FAILURE;

	resetOpenSL();

	return 0;
}

int JNICALL openSLCreate(JNIEnv* env, jclass clazz, unsigned int sampleRate) {
	openSLRelease(env, clazz);

	if (::sampleRate != sampleRate) {
		::sampleRate = sampleRate;
		equalizerConfigChanged();
		virtualizerConfigChanged();
	}

	resetOpenSL();

	SLresult result;

	//configure audio source
	SLDataLocator_AndroidSimpleBufferQueue bufferQueueLocator;
	bufferQueueLocator.locatorType = SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE;
	bufferQueueLocator.numBuffers = bufferCount;

	SLDataFormat_PCM fmt;
	fmt.formatType = SL_DATAFORMAT_PCM;
	fmt.numChannels = 2;
	fmt.samplesPerSec = sampleRate * 1000;
	fmt.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
	fmt.containerSize = SL_PCMSAMPLEFORMAT_FIXED_16;
	fmt.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
	fmt.endianness = SL_BYTEORDER_LITTLEENDIAN;

	SLDataSource audioSrc;
	audioSrc.pLocator = &bufferQueueLocator;
	audioSrc.pFormat = &fmt;

	//configure audio sink
	SLDataLocator_OutputMix outputMixLocator;
	outputMixLocator.locatorType = SL_DATALOCATOR_OUTPUTMIX;
	outputMixLocator.outputMix = outputMixObject;

	SLDataSink audioSink;
	audioSink.pLocator = &outputMixLocator;
	audioSink.pFormat = 0;

	//create audio player
	const SLInterfaceID ids[2] = { SL_IID_BUFFERQUEUE,  SL_IID_VOLUME };
	const SLboolean req[2] = { SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE };
	result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSink, 2, ids, req);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//realize the player
	result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//get the play interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//get the buffer queue interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE, &bqPlayerBufferQueue);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//register callback on the buffer queue
	contextVersion++;
	result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, openSLBufferCallback, (void*)contextVersion);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//get the volume interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
	if (result != SL_RESULT_SUCCESS)
		return result;

	return 0;
}

int JNICALL openSLPlay(JNIEnv* env, jclass clazz) {
	//set the player's state to playing
	return (bqPlayerPlay ? (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING) : SL_RESULT_PRECONDITIONS_VIOLATED);
}

int JNICALL openSLPause(JNIEnv* env, jclass clazz) {
	//set the player's state to paused
	return (bqPlayerPlay ? (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED) : 0);
}

int JNICALL openSLStopAndFlush(JNIEnv* env, jclass clazz) {
	//set the player's state to stopped
	if (bqPlayerPlay) {
		const SLresult result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
		if (result)
			return result;
	}

	if (bqPlayerBufferQueue) {
		(*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);

		//register callback on the buffer queue
		contextVersion++;
		return (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, openSLBufferCallback, (void*)contextVersion);
	}

	return 0;
}

void JNICALL openSLSetVolumeInMillibels(JNIEnv* env, jclass clazz, int volumeInMillibels) {
	if (bqPlayerVolume)
		(*bqPlayerVolume)->SetVolumeLevel(bqPlayerVolume, (SLmillibel)volumeInMillibels);
}

unsigned int JNICALL openSLGetHeadPositionInFrames(JNIEnv* env, jclass clazz) {
	return headPositionInFrames;
}

void swapShorts(short* buffer, unsigned int sizeInShorts) {
	while (sizeInShorts) {
		*buffer = bswap_16(*buffer);
		buffer++;
		sizeInShorts++;
	}
}

int JNICALL openSLWriteDirect(JNIEnv* env, jclass clazz, jobject jbuffer, unsigned int offsetInBytes, unsigned int sizeInBytes, unsigned int needsSwap) {
	if (!fullBuffer || !bqPlayerBufferQueue || !jbuffer)
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	unsigned int sizeInFrames = (sizeInBytes >> srcChannelCount);

	//keep each buffer within a reasonable size limit (we divide by 2 instead of performing a
	//simple subtraction, in order to try to keep the next buffers' sizes reasonably large as well)
	while (sizeInFrames > 1536) {
		sizeInFrames >>= 1;
		sizeInBytes = sizeInFrames << srcChannelCount;
	}

	const unsigned int localEmptyFrames = emptyFrames;

	if (sizeInFrames > localEmptyFrames || !sizeInFrames || !emptyBuffers)
		return 0;

	BufferDescription* const bufferDescriptor = bufferDescriptors + bufferWriteIndex;

	//let's try to prevent very small buffers close to the end of fullBuffer
	//for example: localEmptyFrames = 500, framesAvailableAtTheEndWithoutWrappingAround = 10, sizeInFrames = 200
	//if we didn't care, this buffer would be 10 frames long, and the next, 190 frames long...
	const unsigned int framesAvailableAtTheEndWithoutWrappingAround = bufferSizeInFrames - writePositionInFrames;
	if (sizeInFrames > framesAvailableAtTheEndWithoutWrappingAround) {
		//for sure there is not enough room at the beginning
		if (framesAvailableAtTheEndWithoutWrappingAround >= localEmptyFrames)
			return 0;

		//let's check if there is enough room at the beginning
		const unsigned int framesAvailableAtTheBeginning = localEmptyFrames - framesAvailableAtTheEndWithoutWrappingAround;
		if (sizeInFrames > framesAvailableAtTheBeginning)
			return 0;

		bufferDescriptor->startOffsetInFrames = 0;
		bufferDescriptor->commitedFrames = sizeInFrames + framesAvailableAtTheEndWithoutWrappingAround;
	} else {
		bufferDescriptor->startOffsetInFrames = writePositionInFrames;
		bufferDescriptor->commitedFrames = sizeInFrames;
	}
	bufferDescriptor->sizeInFrames = sizeInFrames;

	short* srcBuffer = (short*)env->GetDirectBufferAddress(jbuffer);
	if (!srcBuffer)
		return -SL_RESULT_MEMORY_FAILURE;

	srcBuffer = (short*)((unsigned char*)srcBuffer + offsetInBytes);

	//we always output stereo audio, regardless of the input config
	short* const dstBuffer = (short*)(fullBuffer + (bufferDescriptor->startOffsetInFrames << 2));

	//one day we will convert from mono to stereo here, in such a way, dstBuffer will always contain stereo frames
	memcpy(dstBuffer, srcBuffer, sizeInBytes);

	if (needsSwap)
		swapShorts(dstBuffer, sizeInBytes >> 1);

	effectProc(dstBuffer, sizeInFrames);

	writePositionInFrames = bufferDescriptor->startOffsetInFrames + sizeInFrames;
	if (writePositionInFrames >= bufferSizeInFrames)
		writePositionInFrames -= bufferSizeInFrames;
	__sync_add_and_fetch(&emptyFrames, (unsigned int)(-bufferDescriptor->commitedFrames));

	bufferWriteIndex = bufferWriteIndex + 1;
	if (bufferWriteIndex >= bufferCount)
		bufferWriteIndex = 0;
	__sync_add_and_fetch(&emptyBuffers, (unsigned int)(-1));

	SLresult result;

	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, dstBuffer, sizeInBytes);
	if (result != SL_RESULT_SUCCESS)
		return -(abs((int)result));

	return sizeInBytes;
}

int JNICALL openSLWrite(JNIEnv* env, jclass clazz, jbyteArray jbuffer, unsigned int offsetInBytes, unsigned int sizeInBytes, int needsSwap) {
	if (!fullBuffer || !bqPlayerBufferQueue || !jbuffer)
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	unsigned int sizeInFrames = (sizeInBytes >> srcChannelCount);

	//keep each buffer within a reasonable size limit (we divide by 2 instead of performing a
	//simple subtraction, in order to try to keep the next buffers' sizes reasonably large as well)
	while (sizeInFrames > 1536) {
		sizeInFrames >>= 1;
		sizeInBytes = sizeInFrames << srcChannelCount;
	}

	const unsigned int localEmptyFrames = emptyFrames;

	if (sizeInFrames > localEmptyFrames || !sizeInFrames || !emptyBuffers)
		return 0;

	BufferDescription* const bufferDescriptor = bufferDescriptors + bufferWriteIndex;

	//let's try to prevent very small buffers close to the end of fullBuffer
	//for example: localEmptyFrames = 500, framesAvailableAtTheEndWithoutWrappingAround = 10, sizeInFrames = 200
	//if we didn't care, this buffer would be 10 frames long, and the next, 190 frames long...
	const unsigned int framesAvailableAtTheEndWithoutWrappingAround = bufferSizeInFrames - writePositionInFrames;
	if (sizeInFrames > framesAvailableAtTheEndWithoutWrappingAround) {
		//for sure there is not enough room at the beginning
		if (framesAvailableAtTheEndWithoutWrappingAround >= localEmptyFrames)
			return 0;

		//let's check if there is enough room at the beginning
		const unsigned int framesAvailableAtTheBeginning = localEmptyFrames - framesAvailableAtTheEndWithoutWrappingAround;
		if (sizeInFrames > framesAvailableAtTheBeginning)
			return 0;

		bufferDescriptor->startOffsetInFrames = 0;
		bufferDescriptor->commitedFrames = sizeInFrames + framesAvailableAtTheEndWithoutWrappingAround;
	} else {
		bufferDescriptor->startOffsetInFrames = writePositionInFrames;
		bufferDescriptor->commitedFrames = sizeInFrames;
	}
	bufferDescriptor->sizeInFrames = sizeInFrames;

	short* const buffer = (short*)env->GetPrimitiveArrayCritical(jbuffer, 0);
	if (!buffer)
		return -SL_RESULT_MEMORY_FAILURE;

	short* const srcBuffer = (short*)((unsigned char*)buffer + offsetInBytes);

	//we always output stereo audio, regardless of the input config
	short* const dstBuffer = (short*)(fullBuffer + (bufferDescriptor->startOffsetInFrames << 2));

	//one day we will convert from mono to stereo here, in such a way, dstBuffer will always contain stereo frames
	memcpy(dstBuffer, srcBuffer, sizeInBytes);

	env->ReleasePrimitiveArrayCritical(jbuffer, buffer, JNI_ABORT);

	if (needsSwap)
		swapShorts(dstBuffer, sizeInBytes >> 1);

	effectProc(dstBuffer, sizeInFrames);

	writePositionInFrames = bufferDescriptor->startOffsetInFrames + sizeInFrames;
	if (writePositionInFrames >= bufferSizeInFrames)
		writePositionInFrames -= bufferSizeInFrames;
	__sync_add_and_fetch(&emptyFrames, (unsigned int)(-bufferDescriptor->commitedFrames));

	bufferWriteIndex = bufferWriteIndex + 1;
	if (bufferWriteIndex >= bufferCount)
		bufferWriteIndex = 0;
	__sync_add_and_fetch(&emptyBuffers, (unsigned int)(-1));

	SLresult result;

	result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, dstBuffer, sizeInBytes);
	if (result != SL_RESULT_SUCCESS)
		return -(abs((int)result));

	return sizeInBytes;
}
