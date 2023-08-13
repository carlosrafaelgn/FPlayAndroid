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

static uint8_t* fullBuffer;
static uint32_t currentlyCommittedFrames, headPositionInFrames, bufferSizeInFrames, singleBufferSizeInFrames, singleBufferSizeInBytes, processingBufferCount, processingBufferSizeInFrames, finalProcessingBufferSizeInFrames, bufferCount, bufferWriteIndex, bufferReadIndex, writtenBufferCount, playedBufferCount;
static size_t contextVersion;

void resetOpenSL() {
	resetVisualizer();
	currentlyCommittedFrames = 0;
	headPositionInFrames = 0;
	bufferWriteIndex = 0;
	bufferReadIndex = 0;
	writtenBufferCount = 0;
	playedBufferCount = 0;
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
	contextVersion = 0;
	srcSampleRate = 44100;
	dstSampleRate = 44100;
	srcChannelCount = 2;
	bufferSizeInFrames = 0;
	singleBufferSizeInFrames = 0;
	singleBufferSizeInBytes = 0;
	processingBufferCount = 0;
	processingBufferSizeInFrames = 0;
	finalProcessingBufferSizeInFrames = 0;
	bufferCount = 0;

	resetOpenSL();
}

//this callback handler is called every time a buffer finishes playing
void openSLBufferCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
	if (bq == bqPlayerBufferQueue && (void*)contextVersion == context) {
		//this is an always incrementing counter
		headPositionInFrames += singleBufferSizeInFrames;

		if ((++bufferReadIndex) >= bufferCount)
			bufferReadIndex = 0;
		playedBufferCount++;
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

	bufferSizeInFrames = 0;
	singleBufferSizeInFrames = 0;
	singleBufferSizeInBytes = 0;
	processingBufferCount = 0;
	processingBufferSizeInFrames = 0;
	finalProcessingBufferSizeInFrames = 0;
	bufferCount = 0;
}

int32_t JNICALL openSLInitialize(JNIEnv* env, jclass clazz) {
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

	resetOpenSL();

	return 0;
}

int32_t JNICALL openSLCreate(JNIEnv* env, jclass clazz, uint32_t dstSampleRate, uint32_t bufferCount, uint32_t singleBufferSizeInFrames) {
	openSLRelease(env, clazz);

	if (::dstSampleRate != dstSampleRate) {
		::dstSampleRate = dstSampleRate;
		resetResampler();
		equalizerConfigChanged();
		virtualizerConfigChanged();
	}

	if (::bufferCount != bufferCount || ::singleBufferSizeInFrames != singleBufferSizeInFrames) {
		::bufferCount = bufferCount;
		::singleBufferSizeInFrames = singleBufferSizeInFrames;
		singleBufferSizeInBytes = singleBufferSizeInFrames << 2;
		bufferSizeInFrames = bufferCount * singleBufferSizeInFrames;

		processingBufferSizeInFrames = singleBufferSizeInFrames;
		while (processingBufferSizeInFrames > MAXIMUM_BUFFER_SIZE_IN_FRAMES_FOR_PROCESSING)
			processingBufferSizeInFrames >>= 1;
		processingBufferCount = (singleBufferSizeInFrames / processingBufferSizeInFrames);
		finalProcessingBufferSizeInFrames = singleBufferSizeInFrames - (processingBufferCount * processingBufferSizeInFrames);

		//we always output stereo audio, regardless of the input config
		if (fullBuffer) {
			delete fullBuffer;
			fullBuffer = 0;
		}

		fullBuffer = new uint8_t[bufferSizeInFrames << 2];
		if (!fullBuffer)
			return SL_RESULT_MEMORY_FAILURE;
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
	fmt.samplesPerSec = dstSampleRate * 1000;
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
	result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, openSLBufferCallback, (void*)contextVersion);
	if (result != SL_RESULT_SUCCESS)
		return result;

	//get the volume interface
	result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
	if (result != SL_RESULT_SUCCESS)
		return result;

	return 0;
}

int32_t JNICALL openSLPlay(JNIEnv* env, jclass clazz) {
	//set the player's state to playing
	return (bqPlayerPlay ? (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING) : SL_RESULT_PRECONDITIONS_VIOLATED);
}

int32_t JNICALL openSLPause(JNIEnv* env, jclass clazz) {
	//set the player's state to paused
	return (bqPlayerPlay ? (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED) : 0);
}

int32_t JNICALL openSLStopAndFlush(JNIEnv* env, jclass clazz) {
	contextVersion++;

	//set the player's state to stopped
	if (bqPlayerPlay) {
		const SLresult result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
		if (result)
			return result;
	}

	int32_t ret = 0;
	if (bqPlayerBufferQueue) {
		(*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);

		//register callback on the buffer queue
		ret = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, openSLBufferCallback, (void*)contextVersion);
	}

	resetOpenSL();

	return ret;
}

void JNICALL openSLSetVolumeInMillibels(JNIEnv* env, jclass clazz, int32_t volumeInMillibels) {
	if (bqPlayerVolume)
		(*bqPlayerVolume)->SetVolumeLevel(bqPlayerVolume, (SLmillibel)volumeInMillibels);
}

uint32_t JNICALL openSLGetHeadPositionInFrames(JNIEnv* env, jclass clazz) {
	return headPositionInFrames;
}

int64_t JNICALL openSLWriteNative(JNIEnv* env, jclass clazz, uint64_t nativeObj, uint32_t offsetInBytes, uint32_t sizeInFrames) {
	if (!fullBuffer || !bqPlayerBufferQueue || (nativeObj && !((MediaCodec*)nativeObj)->buffer))
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	//leave at least one spare buffer because sometimes openSLBufferCallback() is called a little bit ahead of time
	const uint32_t emptyBuffers = (bufferCount - writtenBufferCount + playedBufferCount);
	if (emptyBuffers < 2)
		return 0;
	if (emptyBuffers > bufferCount)
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	//we always output stereo audio, regardless of the input config
	int16_t* const dstBuffer = (int16_t*)(fullBuffer + (bufferWriteIndex * singleBufferSizeInBytes));

	WriteRet ret;
	ret.srcFramesUsed = 0;

	//fill current buffer with singleBufferSizeInFrames samples, only then commit the buffer
	//we will also commit the buffer if nativeObj is 0, and we already had a few samples in the buffer
	if (nativeObj) {
		//dstBuffer must always be filled with stereo frames
		ret.dstFramesUsed = resampleProc((int16_t*)(((MediaCodec*)nativeObj)->buffer + offsetInBytes), sizeInFrames, (int16_t*)((uint8_t*)dstBuffer + (currentlyCommittedFrames << 2)), singleBufferSizeInFrames - currentlyCommittedFrames, ret.srcFramesUsed);

		currentlyCommittedFrames += ret.dstFramesUsed;

		//if the buffer is not full enough, do not commit the buffer
		if (currentlyCommittedFrames < singleBufferSizeInFrames)
			return ret.val;

		//assertion
		if (currentlyCommittedFrames > singleBufferSizeInFrames)
			return -SL_RESULT_PRECONDITIONS_VIOLATED;
	} else {
		if (!currentlyCommittedFrames)
			return 0;
		ret.dstFramesUsed = currentlyCommittedFrames;

		//fill the rest of the buffer with 0's because we will commit this entire buffer
		memset((uint8_t*)dstBuffer + (currentlyCommittedFrames << 2), 0, (singleBufferSizeInFrames - currentlyCommittedFrames) << 2);
	}

	currentlyCommittedFrames = 0;

	advanceVisualizer(dstBuffer, singleBufferSizeInFrames);

	//we must not process too many samples at once, because the AGC algorithm expects at most ~1k samples
	int16_t* procBuffer = dstBuffer;
	for (uint32_t i = 0; i < processingBufferCount; i++, procBuffer += (processingBufferSizeInFrames << 1))
		effectProc(procBuffer, processingBufferSizeInFrames);
	if (finalProcessingBufferSizeInFrames)
		effectProc(procBuffer, finalProcessingBufferSizeInFrames);

	if ((++bufferWriteIndex) >= bufferCount)
		bufferWriteIndex = 0;
	writtenBufferCount++;

	const SLresult result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, dstBuffer, singleBufferSizeInBytes);
	if (result != SL_RESULT_SUCCESS)
		return -(abs((int32_t)result));

	return ret.val;
}

int64_t JNICALL openSLWrite(JNIEnv* env, jclass clazz, jbyteArray jarray, jobject jbuffer, uint32_t offsetInBytes, uint32_t sizeInFrames, uint32_t needsSwap) {
	if (!fullBuffer || !bqPlayerBufferQueue || (!jarray && !jbuffer))
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	//leave at least one spare buffer because sometimes openSLBufferCallback() is called a little bit ahead of time
	//(we do not need to worry about enqueuedBufferCount here since it is always < playedBufferCount)
	const uint32_t emptyBuffers = (bufferCount - writtenBufferCount + playedBufferCount);
	if (emptyBuffers < 2)
		return 0;
	if (emptyBuffers > bufferCount)
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	//we always output stereo audio, regardless of the input config
	int16_t* const dstBuffer = (int16_t*)(fullBuffer + (bufferWriteIndex * singleBufferSizeInBytes));

	WriteRet ret;
	ret.srcFramesUsed = 0;

	//fill current buffer with singleBufferSizeInFrames samples, only then commit the buffer

	//*we do not need to worry about the case when jbuffer and jarray are both null (meaning we had to flush current buffer)
	//because flushing is only performed in openSLWriteNative()
	if (jbuffer) {
		int16_t* const srcBuffer = (int16_t*)env->GetDirectBufferAddress(jbuffer);
		if (!srcBuffer)
			return -SL_RESULT_MEMORY_FAILURE;

		//this is not ok... (should be using a temporary buffer)
		if (needsSwap)
			swapShortsInplace((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), (((singleBufferSizeInFrames - currentlyCommittedFrames) << (srcChannelCount - 1)) * srcSampleRate) / dstSampleRate);

		//dstBuffer must always be filled with stereo frames
		ret.dstFramesUsed = resampleProc((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), sizeInFrames, (int16_t*)((uint8_t*)dstBuffer + (currentlyCommittedFrames << 2)), singleBufferSizeInFrames - currentlyCommittedFrames, ret.srcFramesUsed);
	} else {
		int16_t* const srcBuffer = (int16_t*)env->GetPrimitiveArrayCritical(jarray, 0);
		if (!srcBuffer)
			return -SL_RESULT_MEMORY_FAILURE;

		//this is not ok... (should be using a temporary buffer)
		if (needsSwap)
			swapShortsInplace((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), (((singleBufferSizeInFrames - currentlyCommittedFrames) << (srcChannelCount - 1)) * srcSampleRate) / dstSampleRate);

		//dstBuffer must always be filled with stereo frames
		ret.dstFramesUsed = resampleProc((int16_t*)((uint8_t*)srcBuffer + offsetInBytes), sizeInFrames, (int16_t*)((uint8_t*)dstBuffer + (currentlyCommittedFrames << 2)), singleBufferSizeInFrames - currentlyCommittedFrames, ret.srcFramesUsed);

		env->ReleasePrimitiveArrayCritical(jarray, srcBuffer, JNI_ABORT);
	}

	currentlyCommittedFrames += ret.dstFramesUsed;

	//if the buffer is not full enough, do not commit the buffer
	if (currentlyCommittedFrames < singleBufferSizeInFrames)
		return ret.val;

	//assertion
	if (currentlyCommittedFrames > singleBufferSizeInFrames)
		return -SL_RESULT_PRECONDITIONS_VIOLATED;

	currentlyCommittedFrames = 0;

	advanceVisualizer(dstBuffer, singleBufferSizeInFrames);

	//we must not process too many samples at once, because the AGC algorithm expects at most ~1k samples
	int16_t* procBuffer = dstBuffer;
	for (uint32_t i = 0; i < processingBufferCount; i++, procBuffer += (processingBufferSizeInFrames << 1))
		effectProc(procBuffer, processingBufferSizeInFrames);
	if (finalProcessingBufferSizeInFrames)
		effectProc(procBuffer, finalProcessingBufferSizeInFrames);

	if ((++bufferWriteIndex) >= bufferCount)
		bufferWriteIndex = 0;
	writtenBufferCount++;

	const SLresult result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, dstBuffer, singleBufferSizeInBytes);
	if (result != SL_RESULT_SUCCESS)
		return -(abs((int32_t)result));

	return ret.val;
}
