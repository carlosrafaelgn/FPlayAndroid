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
package br.com.carlosrafaelgn.fplay.playback;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;

import java.io.IOException;

public abstract class HttpStreamExtractor {
	private final String srcType;
	private String dstType;
	private int channelCount, sampleRate, samplesPerFrame, bitRate;

	public HttpStreamExtractor(String srcType) {
		this.srcType = srcType;
	}

	public final String getSrcType() {
		return srcType;
	}

	public final String getDstType() {
		return dstType;
	}

	protected final void setDstType(String dstType) {
		this.dstType = dstType;
	}

	public final int getChannelCount() {
		return channelCount;
	}

	protected final void setChannelCount(int channelCount) {
		this.channelCount = channelCount;
	}

	public final int getSampleRate() {
		return sampleRate;
	}

	protected final void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
	}

	public final int getSamplesPerFrame() {
		return samplesPerFrame;
	}

	protected final void setSamplesPerFrame(int samplesPerFrame) {
		this.samplesPerFrame = samplesPerFrame;
	}

	public final int getBitRate() {
		return bitRate;
	}

	protected final void setBitRate(int bitRate) {
		this.bitRate = bitRate;
	}

	public abstract int waitToReadHeader(boolean fillProperties);

	public abstract int canReadHeader();

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	protected void formatMediaCodec(MediaFormat format) {
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@SuppressWarnings("deprecation")
	public final MediaCodec createMediaCodec() throws IOException {
		MediaCodec mediaCodec = MediaCodec.createDecoderByType(getDstType());
		final MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, getDstType());
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, getSampleRate());
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, getChannelCount());
		format.setInteger(MediaFormat.KEY_CHANNEL_MASK, (getChannelCount() == 1) ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
		format.setInteger(MediaFormat.KEY_BIT_RATE, getBitRate());
		formatMediaCodec(format);
		mediaCodec.configure(format, null, null, 0);
		mediaCodec.start();
		return mediaCodec;
	}
}
