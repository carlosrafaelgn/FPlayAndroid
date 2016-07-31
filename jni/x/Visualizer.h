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

uint32_t visualizerWriteOffsetInFrames, visualizerBufferSizeInFrames;
uint8_t* visualizerBuffer;
static uint32_t visualizerCreatedBufferSizeInFrames;
#ifdef FPLAY_X86
static const int8_t visualizerShuffleIndices[16] __attribute__((aligned(16))) = { 0, 1, 4, 5, 8, 9, 12, 13, 2, 3, 6, 7, 10, 11, 14, 15 };
static const int8_t visualizerx80[16] __attribute__((aligned(16))) = { 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0, 0, 0, 0, 0, 0, 0, 0 };
#define visualizerWriteProc visualizerWrite
#else
typedef void (*VISUALIZERPROC)(const int16_t* srcBuffer, uint32_t bufferSizeInFrames);
extern void visualizerWriteNeon(const int16_t* srcBuffer, uint32_t bufferSizeInFrames);
static VISUALIZERPROC visualizerWriteProc;
#endif

#define resetVisualizer() visualizerWriteOffsetInFrames = 0
#define advanceVisualizer(A, B) if (visualizerBuffer) visualizerWriteProc(A, B); visualizerWriteOffsetInFrames += B; while (visualizerWriteOffsetInFrames >= visualizerBufferSizeInFrames) visualizerWriteOffsetInFrames -= visualizerBufferSizeInFrames

void visualizerWrite(const int16_t* srcBuffer, uint32_t bufferSizeInFrames) {
	const uint32_t frameCountAtTheEnd = visualizerBufferSizeInFrames - visualizerWriteOffsetInFrames;
	uint8_t* dstBuffer = visualizerBuffer + visualizerWriteOffsetInFrames;
	uint32_t count = ((bufferSizeInFrames <= frameCountAtTheEnd) ? bufferSizeInFrames : frameCountAtTheEnd);
#ifdef FPLAY_X86
	const __m128i x80 = _mm_load_si128((const __m128i*)visualizerx80);
	const __m128i indices = _mm_load_si128((const __m128i*)visualizerShuffleIndices);
#endif
	do {
		uint32_t i = count;
#ifdef FPLAY_X86
		while (i >= 8) {
			//L0 R0 L1 R1 L2 R2 L3 R3
			__m128i src = _mm_lddqu_si128((__m128i const*)srcBuffer);
			//L4 R4 L5 R5 L6 R6 L7 R7
			__m128i src2 = _mm_lddqu_si128((__m128i const*)(srcBuffer + 8));
			//L0 R0 L1 R1 L2 R2 L3 R3 -> L0 L1 L2 L3 R0 R1 R2 R3
			src = _mm_shuffle_epi8(src, indices);
			//L4 R4 L5 R5 L6 R6 L7 R7 -> L4 L5 L6 L7 R4 R5 R6 R7
			src2 = _mm_shuffle_epi8(src2, indices);
			//L0 L1 L2 L3 L4 L5 L6 L7
			const __m128i left = _mm_castps_si128(_mm_movelh_ps(_mm_castsi128_ps(src), _mm_castsi128_ps(src2)));
			//R0 R1 R2 R3 R4 R5 R6 R7
			const __m128i right = _mm_castps_si128(_mm_movehl_ps(_mm_castsi128_ps(src2), _mm_castsi128_ps(src)));
			const __m128i tmpZero = _mm_setzero_si128();
			const __m128i tmpSignExtensionL = _mm_cmpgt_epi16(tmpZero, left); //tmpSignExtensionL = (0 > left ? 0xFFFF : 0)
			__m128i left32_0 = _mm_unpacklo_epi16(left, tmpSignExtensionL); //convert the lower 4 int16_t's into 4 int32_t's
			__m128i left32_1 = _mm_unpackhi_epi16(left, tmpSignExtensionL); //convert the upper 4 int16_t's into 4 int32_t's
			const __m128i tmpSignExtensionR = _mm_cmpgt_epi16(tmpZero, right); //tmpSignExtensionR = (0 > right ? 0xFFFF : 0)
			const __m128i right32_0 = _mm_unpacklo_epi16(right, tmpSignExtensionR); //convert the lower 4 int16_t's into 4 int32_t's
			const __m128i right32_1 = _mm_unpackhi_epi16(right, tmpSignExtensionR); //convert the upper 4 int16_t's into 4 int32_t's

			left32_0 = _mm_add_epi32(left32_0, right32_0);
			left32_1 = _mm_add_epi32(left32_1, right32_1);

			left32_0 = _mm_srai_epi32(left32_0, 9);
			left32_1 = _mm_srai_epi32(left32_1, 9);

			left32_0 = _mm_packs_epi32(left32_0, left32_1);
			left32_0 = _mm_packs_epi16(left32_0, left32_0);

			left32_0 = _mm_xor_si128(left32_0, x80);

			_mm_store_sd((double*)dstBuffer, _mm_castsi128_pd(left32_0));
			dstBuffer += 8;
			srcBuffer += 16;
			i -= 8;
		}
#endif
		while (i--) {
			*dstBuffer++ = (uint8_t)((((int32_t)srcBuffer[0] + (int32_t)srcBuffer[1]) >> 9) ^ 0x80); // >> 9 = 1 (average) + 8 (remove lower byte)
			srcBuffer += 2;
		}
		bufferSizeInFrames -= count;
		count = bufferSizeInFrames;
		dstBuffer = visualizerBuffer;
	} while (bufferSizeInFrames);
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
		uint32_t* buffer = (uint32_t*)visualizerBuffer;
		bufferSizeInFrames = visualizerBufferSizeInFrames;
		while (bufferSizeInFrames >= 4) {
			*buffer++ = 0x80808080;
			bufferSizeInFrames -= 4;
		}
		while (bufferSizeInFrames) {
			*((uint8_t*)buffer) = 0x80;
			buffer = (uint32_t*)((uint8_t*)buffer + 1);
			bufferSizeInFrames--;
		}
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

void JNICALL visualizerZeroOut(JNIEnv* env, jclass clazz) {
	if (visualizerBuffer) {
		uint32_t* buffer = (uint32_t*)visualizerBuffer;
		uint32_t bufferSizeInFrames = visualizerBufferSizeInFrames;
		while (bufferSizeInFrames >= 4) {
			*buffer++ = 0x80808080;
			bufferSizeInFrames -= 4;
		}
		while (bufferSizeInFrames) {
			*((uint8_t*)buffer) = 0x80;
			buffer = (uint32_t*)((uint8_t*)buffer + 1);
			bufferSizeInFrames--;
		}
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
#ifdef FPLAY_ARM
	visualizerWriteProc = (neonMode ? visualizerWriteNeon : visualizerWrite);
#endif
}

#define terminateVisualizer() visualizerStop(0, 0)
