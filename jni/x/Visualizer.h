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

static uint32_t visualizerWriteOffsetInFrames, visualizerBufferSizeInFrames, visualizerCreatedBufferSizeInFrames;
static uint8_t* visualizerBuffer;

#define resetVisualizer() visualizerWriteOffsetInFrames = 0
#define advanceVisualizer(A, B) if (visualizerBuffer) visualizerWrite(A, B); visualizerWriteOffsetInFrames += B; while (visualizerWriteOffsetInFrames >= visualizerBufferSizeInFrames) visualizerWriteOffsetInFrames -= visualizerBufferSizeInFrames

void visualizerWrite(int16_t* buffer, uint32_t bufferSizeInFrames) {
	
}

int32_t JNICALL visualizerStart(JNIEnv* env, jclass clazz, uint32_t bufferSizeInFrames, uint32_t createIfNotCreated) {
	if (!createIfNotCreated) {
		visualizerWriteOffsetInFrames = 0;
		visualizerBufferSizeInFrames = bufferSizeInFrames;
	}

	if ((!createIfNotCreated && !visualizerBuffer) || (visualizerBuffer && visualizerCreatedBufferSizeInFrames == visualizerBufferSizeInFrames))
		return 0;

	if (visualizerBuffer)
		delete visualizerBuffer;
	visualizerBuffer = new uint8_t[visualizerBufferSizeInFrames];

	if (visualizerBuffer) {
		visualizerCreatedBufferSizeInFrames = visualizerBufferSizeInFrames;
		return 0;
	}

	visualizerCreatedBufferSizeInFrames = 0;
	return -1;
}

void JNICALL visualizerStop(JNIEnv* env, jclass clazz) {
	visualizerCreatedBufferSizeInFrames = 0;
	if (visualizerBuffer) {
		delete visualizerBuffer;
		visualizerBuffer = 0;
	}
}

void JNICALL visualizerGetWaveform(JNIEnv* env, jclass clazz, jbyteArray jwaveform, uint32_t headPositionInFrames) {
	if (!visualizerBuffer || !visualizerBufferSizeInFrames || !jwaveform)
		return;

	uint8_t* const waveform = (uint8_t*)env->GetPrimitiveArrayCritical(jwaveform, 0);
	if (!waveform)
		return;

	headPositionInFrames %= visualizerBufferSizeInFrames;

	//visualizerBuffer must be treated as a circular buffer
	const uint32_t frameCountAtTheEnd = visualizerBufferSizeInFrames - headPositionInFrames;
	if (frameCountAtTheEnd >= 1024) {
		memcpy(waveform, visualizerBuffer + headPositionInFrames, 1024);
	} else {
		memcpy(waveform, visualizerBuffer + headPositionInFrames, frameCountAtTheEnd);
		memcpy(waveform + frameCountAtTheEnd, visualizerBuffer, 1024 - frameCountAtTheEnd);
	}

	env->ReleasePrimitiveArrayCritical(jwaveform, waveform, 0);
}

void initializeVisualizer() {
	visualizerWriteOffsetInFrames = 0;
	visualizerBufferSizeInFrames = 0;
	visualizerCreatedBufferSizeInFrames = 0;
	visualizerBuffer = 0;
}

#define terminateVisualizer() visualizerStop(0, 0)
