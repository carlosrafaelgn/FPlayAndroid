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
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;

public abstract class HttpStreamExtractor {
	private static final int MAX_READ_LENGTH = 2048;

	private static final class MetadataInputStream extends InputStream {
		private final CircularIOBuffer buffer;
		private final int size;
		private int readSoFar;

		MetadataInputStream(CircularIOBuffer buffer, int size) {
			this.buffer = buffer;
			this.size = size;
		}

		@Override
		public int read() {
			if (readSoFar >= size || buffer.waitUntilCanRead(1) <= 0)
				return -1;
			buffer.peek(0);
			buffer.skip(1);
			return 0;
		}

		@SuppressWarnings("ConstantConditions")
		@Override
		public int read(byte b[], int off, int len) {
			if (b == null)
				throw new NullPointerException();
			else if (off < 0 || len < 0 || len > b.length - off)
				throw new IndexOutOfBoundsException();
			else if (len == 0)
				return 0;

			final int available = size - readSoFar;
			int s = ((len >= available) ? available : len);
			final int actualLen = s;
			while (s > 0) {
				if ((len = buffer.waitUntilCanRead((s >= MAX_READ_LENGTH) ? MAX_READ_LENGTH : s)) <= 0)
					return actualLen - s;
				s -= len;
				readSoFar += len;
				buffer.read(b, off, len);
				off += len;
			}
			return actualLen;
		}

		@Override
		public long skip(long n) {
			final int available = size - readSoFar;
			int s = ((n >= available) ? available : (int)n);
			n = (long)s;
			while (s > 0) {
				int len;
				if ((len = buffer.waitUntilCanRead((s >= MAX_READ_LENGTH) ? MAX_READ_LENGTH : s)) <= 0)
					return n - s;
				s -= len;
				readSoFar += len;
				buffer.skip(len);
			}
			return n;
		}

		@Override
		public int available() {
			return size - readSoFar;
		}

		@Override
		public void close() {
			//we must not set buffer to null, because MetadataExtractor.extract()
			//closes this stream at the end, but we need to access the stream
			//after MetadataExtractor.extract() returns
			//buffer = null;
		}
	}

	private final String srcType;
	private String dstType;
	private int channelCount, sampleRate, samplesPerFrame, bitRate;

	public HttpStreamExtractor(String srcType) {
		this.srcType = srcType;
	}

	@SuppressWarnings({"unused"})
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

	@SuppressWarnings({"unused"})
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

	protected final int processID3v2(CircularIOBuffer buffer, int peekOffset, Handler handler, int metadataMsg, int arg1) {
		int len;
		if (buffer.peek(peekOffset + 1) == 0x44 && buffer.peek(peekOffset + 2) == 0x33) {
			if ((len = buffer.waitUntilCanRead(peekOffset + 10)) < (peekOffset + 10))
				return (len < 0 ? -1 : 0);

			//refer to MetadataExtractor.java -> extractID3v2Andv1()
			final int flags = buffer.peek(peekOffset + 5);
			final int sizeBytes0 = buffer.peek(peekOffset + 6);
			final int sizeBytes1 = buffer.peek(peekOffset + 7);
			final int sizeBytes2 = buffer.peek(peekOffset + 8);
			final int sizeBytes3 = buffer.peek(peekOffset + 9);
			int size = ((flags & 0x10) != 0 ? 10 : 0) + //footer presence flag
				(
					(sizeBytes3 & 0x7f) |
					((sizeBytes2 & 0x7f) << 7) |
					((sizeBytes1 & 0x7f) << 14) |
					((sizeBytes0 & 0x7f) << 21)
				) + 10; //the first 10 bytes

			//consume only if we are not peeking ahead...
			if (peekOffset > 0)
				return size;

			final MetadataInputStream inputStream = new MetadataInputStream(buffer, size);
			final MetadataExtractor metadataExtractor = new MetadataExtractor();
			metadataExtractor.extract(inputStream, size);
			//make sure we consume the entire header
			if ((len = inputStream.available()) > 0) {
				if ((int)inputStream.skip(len) < len)
					return -1;
			}
			inputStream.close();
			//it is safe to destroy metadataExtractor here, because
			//all its useful data will remain untouched after destroy()
			metadataExtractor.destroy();
			if (handler != null)
				handler.sendMessageAtTime(Message.obtain(handler, metadataMsg, arg1, 0, metadataExtractor), SystemClock.uptimeMillis());

			//proceed as if none of this had happened
			if ((len = buffer.waitUntilCanRead(4)) < 4)
				return (len < 0 ? -1 : 0);
			return Integer.MAX_VALUE;
		}

		return -1;
	}

	protected final int processID3v1(CircularIOBuffer buffer, int offset) {
		int len;
		if (buffer.peek(offset + 1) == 0x41 && buffer.peek(offset + 2) == 0x47) {
			//final TAG header? (this should be the last chunk of data in a file...)

			//skip only if we are not peeking ahead...
			if (offset > 0)
				return 128;

			if ((len = buffer.waitUntilCanRead(128)) < 128)
				return (len < 0 ? -1 : 0);

			buffer.skip(len);

			//proceed as if none of this had happened
			if ((len = buffer.waitUntilCanRead(4)) < 4)
				return (len < 0 ? -1 : 0);
			return Integer.MAX_VALUE;
		}

		return -1;
	}
}
