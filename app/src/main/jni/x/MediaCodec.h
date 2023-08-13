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

//http://developer.android.com/ndk/guides/audio/opensl-for-android.html#da
//... deprecated Android-specific extension to OpenSL ES 1.0.1 for decoding an encoded stream to PCM without immediate playback
//DECODING AUDIO WITH OPENSL ES IS DEPRECATED IN ANDROID!!!
//To decode an encoded stream to PCM but not play back immediately, for apps running on Android 4.x (API levels 16–20), we
//recommend using the MediaCodec class. For new applications running on Android 5.0 (API level 21) or higher, we recommend
//using the NDK equivalent, <NdkMedia*.h>. These header files reside in the media/ directory under your installation root.

#include <dlfcn.h>

static void* libmediandk;

#define AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM 4
#define AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED -3
#define AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED -2
#define AMEDIACODEC_INFO_TRY_AGAIN_LATER -1
#define media_status_t int32_t
#define AMediaCodec void
#define AMediaExtractor void
#define AMediaFormat void

typedef enum {
	AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC,
	AMEDIAEXTRACTOR_SEEK_NEXT_SYNC,
	AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC
} SeekMode;

struct AMediaCodecBufferInfo {
	int32_t offset;
	int32_t size;
	int64_t presentationTimeUs;
	uint32_t flags;
};

static media_status_t (*AMediaCodec_configure)(AMediaCodec*, const AMediaFormat* format, void* surface, void* crypto, uint32_t flags);
static AMediaCodec* (*AMediaCodec_createDecoderByType)(const char *mime_type);
static media_status_t (*AMediaCodec_delete)(AMediaCodec*);
static ssize_t (*AMediaCodec_dequeueInputBuffer)(AMediaCodec*, int64_t timeoutUs);
static ssize_t (*AMediaCodec_dequeueOutputBuffer)(AMediaCodec*, AMediaCodecBufferInfo *info, int64_t timeoutUs);
static media_status_t (*AMediaCodec_flush)(AMediaCodec*);
static uint8_t* (*AMediaCodec_getInputBuffer)(AMediaCodec*, size_t idx, size_t *out_size);
static uint8_t* (*AMediaCodec_getOutputBuffer)(AMediaCodec*, size_t idx, size_t *out_size);
static AMediaFormat* (*AMediaCodec_getOutputFormat)(AMediaCodec*);
static media_status_t (*AMediaCodec_queueInputBuffer)(AMediaCodec*, size_t idx, off_t offset, size_t size, uint64_t time, uint32_t flags);
static media_status_t (*AMediaCodec_releaseOutputBuffer)(AMediaCodec*, size_t idx, bool render);
static media_status_t (*AMediaCodec_start)(AMediaCodec*);
static media_status_t (*AMediaCodec_stop)(AMediaCodec*);

static bool (*AMediaExtractor_advance)(AMediaExtractor*);
static media_status_t (*AMediaExtractor_delete)(AMediaExtractor*);
static int64_t (*AMediaExtractor_getSampleTime)(AMediaExtractor*);
static size_t (*AMediaExtractor_getTrackCount)(AMediaExtractor*);
static AMediaFormat* (*AMediaExtractor_getTrackFormat)(AMediaExtractor*, size_t idx);
static AMediaExtractor* (*AMediaExtractor_new)();
static ssize_t (*AMediaExtractor_readSampleData)(AMediaExtractor*, uint8_t *buffer, size_t capacity);
static media_status_t (*AMediaExtractor_seekTo)(AMediaExtractor*, int64_t seekPosUs, SeekMode mode);
static media_status_t (*AMediaExtractor_selectTrack)(AMediaExtractor*, size_t idx);
static media_status_t (*AMediaExtractor_setDataSourceFd)(AMediaExtractor*, int32_t fd, off64_t offset, off64_t length);

static bool (*AMediaFormat_getInt32)(AMediaFormat*, const char *name, int32_t *out);
static bool (*AMediaFormat_getInt64)(AMediaFormat*, const char *name, int64_t *out);
static bool (*AMediaFormat_getString)(AMediaFormat*, const char *name, const char **out);

#define INPUT_BUFFER_TIMEOUT_IN_US 0
#define OUTPUT_BUFFER_TIMEOUT_IN_US 0

class MediaCodec {
public:
	unsigned char* buffer;

private:
	int32_t inputOver;
	ssize_t bufferIndex;
	AMediaExtractor* mediaExtractor;
	AMediaCodec* mediaCodec;

	static bool isAudio(const char* mime) {
		return (mime &&
			(mime[0] == 'a') &&
			(mime[1] == 'u') &&
			(mime[2] == 'd') &&
			(mime[3] == 'i') &&
			(mime[4] == 'o') &&
			(mime[5] == '/'));
	}

public:
	MediaCodec() {
		inputOver = false;
		bufferIndex = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
		buffer = 0;
		mediaExtractor = 0;
		mediaCodec = 0;
	}

	~MediaCodec() {
		inputOver = false;
		bufferIndex = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
		buffer = 0;
		if (mediaExtractor) {
			AMediaExtractor_delete(mediaExtractor);
			mediaExtractor = 0;
		}
		if (mediaCodec) {
			AMediaCodec_stop(mediaCodec);
			AMediaCodec_delete(mediaCodec);
			mediaCodec = 0;
		}
	}

	int32_t prepare(int32_t fd, uint64_t length, uint64_t* outParams) {
		int32_t ret;

		mediaExtractor = AMediaExtractor_new();
		if (!mediaExtractor)
			return -1;

		if ((ret = AMediaExtractor_setDataSourceFd(mediaExtractor, fd, 0, length)))
			return ret;

		const size_t numTracks = AMediaExtractor_getTrackCount(mediaExtractor);
		size_t i;
		AMediaFormat* format = 0;
		const char* mime = 0;
		for (i = 0; i < numTracks; i++) {
			format = AMediaExtractor_getTrackFormat(mediaExtractor, i);
			if (!format)
				continue;
			if (!AMediaFormat_getString(format, "mime", &mime))
				continue;
			if (isAudio(mime)) {
				if ((ret = AMediaExtractor_selectTrack(mediaExtractor, i)))
					return ret;
				int32_t channelCount, sampleRate;
				int64_t duration;
				if (!AMediaFormat_getInt32(format, "channel-count", &channelCount) ||
					!AMediaFormat_getInt32(format, "sample-rate", &sampleRate) ||
					!AMediaFormat_getInt64(format, "durationUs", &duration))
					continue;
				//only mono and stereo files for now...
				if (channelCount != 1 && channelCount != 2)
					return -1;
				outParams[1] = (uint64_t)channelCount;
				outParams[2] = (uint64_t)sampleRate;
				outParams[3] = (uint64_t)duration;
				break;
			}
		}
		if (i >= numTracks)
			return -1;

		mediaCodec = AMediaCodec_createDecoderByType(mime);
		if (!mediaCodec)
			return -1;

		if ((ret = AMediaCodec_configure(mediaCodec, format, 0, 0, 0)))
			return ret;

		if ((ret = AMediaCodec_start(mediaCodec)))
			return ret;

		inputOver = false;
		bufferIndex = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
		buffer = 0;

		if ((ret = fillInputBuffers()))
			return ret;

		return 0;
	}

	int64_t doSeek(int32_t msec, int32_t totalMsec) {
		int32_t ret;

		if ((ret = AMediaCodec_flush(mediaCodec)))
			return (int64_t)ret;

		if ((ret = AMediaExtractor_seekTo(mediaExtractor, (int64_t)msec * 1000LL, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC))) {
			if (msec > (totalMsec - 500)) {
				inputOver = true;
				return 0x7FFFFFFFFFFFFFFFLL;
			}
			return (int64_t)ret;
		}

		inputOver = false;
		bufferIndex = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
		buffer = 0;

		const int64_t sampleTime = AMediaExtractor_getSampleTime(mediaExtractor);
		if (sampleTime < 0) {
			inputOver = true;
			return 0x7FFFFFFFFFFFFFFFLL;
		}

		if ((ret = fillInputBuffers()))
			return (int64_t)ret;

		return sampleTime;
	}

	int32_t fillInputBuffers() {
		while (!inputOver) {
			const ssize_t index = AMediaCodec_dequeueInputBuffer(mediaCodec, INPUT_BUFFER_TIMEOUT_IN_US);
			if (index < 0)
				break;
			size_t inputBufferCapacity;
			uint8_t* inputBuffer = AMediaCodec_getInputBuffer(mediaCodec, index, &inputBufferCapacity);
			if (!inputBuffer)
				break;
			const ssize_t size = AMediaExtractor_readSampleData(mediaExtractor, inputBuffer, inputBufferCapacity);
			if (size < 0) {
				inputOver = true;
				int32_t ret;
				if ((ret = AMediaCodec_queueInputBuffer(mediaCodec, index, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM)))
					return ret;
				break;
			} else {
				int32_t ret;
				if ((ret = AMediaCodec_queueInputBuffer(mediaCodec, index, 0, size, 0, 0)))
					return ret;
				//although the doc says "Returns false if no more sample data is available
				//(end of stream)", sometimes, AMediaExtractor_advance() returns false in other cases....
				AMediaExtractor_advance(mediaExtractor);
			}
		}
		return 0;
	}

	int32_t nextOutputBuffer() {
		//positive: ok (odd means input over)
		//negative: error
		int32_t ret;

		AMediaCodecBufferInfo bufferInfo;
		bufferInfo.flags = 0;
		bufferIndex = AMediaCodec_dequeueOutputBuffer(mediaCodec, &bufferInfo, OUTPUT_BUFFER_TIMEOUT_IN_US);

		if (bufferIndex < 0) {
			if (bufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
				bufferIndex = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
				AMediaFormat* format = AMediaCodec_getOutputFormat(mediaCodec);
				if (format) {
					int32_t newChannelCount, newSampleRate;
					if (AMediaFormat_getInt32(format, "channel-count", &newChannelCount) &&
						AMediaFormat_getInt32(format, "sample-rate", &newSampleRate))
						return ((newChannelCount << 28) | newSampleRate);
				}
			}

			return ((bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) ? 0x7FFFFFFF : 0x7FFFFFFE);
		}

		size_t outputBufferCapacity;
		buffer = AMediaCodec_getOutputBuffer(mediaCodec, bufferIndex, &outputBufferCapacity);
		if (!buffer) {
			if ((ret = AMediaCodec_releaseOutputBuffer(mediaCodec, bufferIndex, 0)))
				return ret;
			return ((bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) ? 0x7FFFFFFF : 0x7FFFFFFE);
		}

		buffer += bufferInfo.offset;

		return ((bufferInfo.size << 1) | ((bufferInfo.flags >> 2) & 1));
	}

	void releaseOutputBuffer() {
		if (mediaCodec && bufferIndex >= 0) {
			AMediaCodec_releaseOutputBuffer(mediaCodec, bufferIndex, 0);
			bufferIndex = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
			buffer = 0;
		}
	}
};

int32_t JNICALL mediaCodecPrepare(JNIEnv* env, jclass clazz, int32_t fd, uint64_t length, jlongArray joutParams) {
	if (!fd || !joutParams || env->GetArrayLength(joutParams) < 4)
		return -1;

	uint64_t outParams[4];

	MediaCodec* nativeObj = new MediaCodec();
	const int32_t ret = nativeObj->prepare(fd, length, outParams);

	if (ret < 0) {
		delete nativeObj;
		return ret;
	}

	outParams[0] = (uint64_t)nativeObj;
	env->SetLongArrayRegion(joutParams, 0, 4, (jlong*)outParams);

	return 0;
}

int32_t JNICALL mediaCodecFillInputBuffers(JNIEnv* env, jclass clazz, uint64_t nativeObj) {
	if (!nativeObj)
		return -1;

	return ((MediaCodec*)nativeObj)->fillInputBuffers();
}

int32_t JNICALL mediaCodecNextOutputBuffer(JNIEnv* env, jclass clazz, uint64_t nativeObj) {
	if (!nativeObj)
		return -1;

	return ((MediaCodec*)nativeObj)->nextOutputBuffer();
}

int64_t JNICALL mediaCodecSeek(JNIEnv* env, jclass clazz, uint64_t nativeObj, int32_t msec, int32_t totalMsec) {
	if (!nativeObj || msec < 0 || totalMsec < 0)
		return -1;

	return ((MediaCodec*)nativeObj)->doSeek(msec, totalMsec);
}

void JNICALL mediaCodecReleaseOutputBuffer(JNIEnv* env, jclass clazz, uint64_t nativeObj) {
	if (nativeObj)
		((MediaCodec*)nativeObj)->releaseOutputBuffer();
}

void JNICALL mediaCodecRelease(JNIEnv* env, jclass clazz, uint64_t nativeObj) {
	if (nativeObj)
		delete ((MediaCodec*)nativeObj);
}

int32_t JNICALL mediaCodecLoadExternalLibrary(JNIEnv* env, jclass clazz) {
	libmediandk = dlopen("libmediandk.so", RTLD_NOW | RTLD_LOCAL);
	if (!libmediandk)
		return -1;
	if (!(*((void**)&AMediaCodec_configure) = dlsym(libmediandk, "AMediaCodec_configure")))
		return -2;
	if (!(*((void**)&AMediaCodec_createDecoderByType) = dlsym(libmediandk, "AMediaCodec_createDecoderByType")))
		return -3;
	if (!(*((void**)&AMediaCodec_delete) = dlsym(libmediandk, "AMediaCodec_delete")))
		return -4;
	if (!(*((void**)&AMediaCodec_dequeueInputBuffer) = dlsym(libmediandk, "AMediaCodec_dequeueInputBuffer")))
		return -5;
	if (!(*((void**)&AMediaCodec_dequeueOutputBuffer) = dlsym(libmediandk, "AMediaCodec_dequeueOutputBuffer")))
		return -6;
	if (!(*((void**)&AMediaCodec_flush) = dlsym(libmediandk, "AMediaCodec_flush")))
		return -7;
	if (!(*((void**)&AMediaCodec_getInputBuffer) = dlsym(libmediandk, "AMediaCodec_getInputBuffer")))
		return -8;
	if (!(*((void**)&AMediaCodec_getOutputBuffer) = dlsym(libmediandk, "AMediaCodec_getOutputBuffer")))
		return -9;
	if (!(*((void**)&AMediaCodec_getOutputFormat) = dlsym(libmediandk, "AMediaCodec_getOutputFormat")))
		return -91;
	if (!(*((void**)&AMediaCodec_queueInputBuffer) = dlsym(libmediandk, "AMediaCodec_queueInputBuffer")))
		return -10;
	if (!(*((void**)&AMediaCodec_releaseOutputBuffer) = dlsym(libmediandk, "AMediaCodec_releaseOutputBuffer")))
		return -11;
	if (!(*((void**)&AMediaCodec_start) = dlsym(libmediandk, "AMediaCodec_start")))
		return -12;
	if (!(*((void**)&AMediaCodec_stop) = dlsym(libmediandk, "AMediaCodec_stop")))
		return -13;
	if (!(*((void**)&AMediaExtractor_advance) = dlsym(libmediandk, "AMediaExtractor_advance")))
		return -14;
	if (!(*((void**)&AMediaExtractor_delete) = dlsym(libmediandk, "AMediaExtractor_delete")))
		return -15;
	if (!(*((void**)&AMediaExtractor_getSampleTime) = dlsym(libmediandk, "AMediaExtractor_getSampleTime")))
		return -16;
	if (!(*((void**)&AMediaExtractor_getTrackCount) = dlsym(libmediandk, "AMediaExtractor_getTrackCount")))
		return -17;
	if (!(*((void**)&AMediaExtractor_getTrackFormat) = dlsym(libmediandk, "AMediaExtractor_getTrackFormat")))
		return -18;
	if (!(*((void**)&AMediaExtractor_new) = dlsym(libmediandk, "AMediaExtractor_new")))
		return -19;
	if (!(*((void**)&AMediaExtractor_readSampleData) = dlsym(libmediandk, "AMediaExtractor_readSampleData")))
		return -20;
	if (!(*((void**)&AMediaExtractor_seekTo) = dlsym(libmediandk, "AMediaExtractor_seekTo")))
		return -21;
	if (!(*((void**)&AMediaExtractor_selectTrack) = dlsym(libmediandk, "AMediaExtractor_selectTrack")))
		return -22;
	if (!(*((void**)&AMediaExtractor_setDataSourceFd) = dlsym(libmediandk, "AMediaExtractor_setDataSourceFd")))
		return -23;
	if (!(*((void**)&AMediaFormat_getInt32) = dlsym(libmediandk, "AMediaFormat_getInt32")))
		return -24;
	if (!(*((void**)&AMediaFormat_getInt64) = dlsym(libmediandk, "AMediaFormat_getInt64")))
		return -25;
	if (!(*((void**)&AMediaFormat_getString) = dlsym(libmediandk, "AMediaFormat_getString")))
		return -26;

	return 0;
}

void initializeMediaCodec() {
	libmediandk = 0;
}

void terminateMediaCodec() {
	if (libmediandk) {
		dlclose(libmediandk);
		libmediandk = 0;
	}
}
