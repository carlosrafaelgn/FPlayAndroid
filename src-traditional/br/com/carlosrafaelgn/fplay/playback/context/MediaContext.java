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
package br.com.carlosrafaelgn.fplay.playback.context;

import android.content.Context;

import br.com.carlosrafaelgn.fplay.playback.Player;

public final class MediaContext {
	public static final int BUFFER_SIZE_500MS = 0x01;
	public static final int BUFFER_SIZE_1000MS = 0x00;
	public static final int BUFFER_SIZE_1500MS = 0x02;
	public static final int BUFFER_SIZE_2000MS = 0x03;
	public static final int BUFFER_SIZE_2500MS = 0x04;

	public static final int FILL_THRESHOLD_25 = 0x10;
	public static final int FILL_THRESHOLD_50 = 0x20;
	public static final int FILL_THRESHOLD_75 = 0x30;
	public static final int FILL_THRESHOLD_100 = 0x00;

	public static final int FEATURE_PROCESSOR_ARM = 0x0001;
	public static final int FEATURE_PROCESSOR_NEON = 0x0002;
	public static final int FEATURE_PROCESSOR_X86 = 0x0004;
	public static final int FEATURE_PROCESSOR_SSE = 0x0008;
	public static final int FEATURE_PROCESSOR_64_BITS = 0x0010;
	public static final int FEATURE_DECODING_NATIVE = 0x0020;
	public static final int FEATURE_DECODING_DIRECT = 0x0040;

	public static void _initialize() {
	}

	public static void _release() {
	}

	public static IMediaPlayer createMediaPlayer() {
		return new MediaPlayerWrapper();
	}

	public static int getFeatures() {
		return 0;
	}

	public static int getBufferConfig() {
		return 0;
	}

	public static void setBufferConfig(int bufferConfig) {
	}
}
