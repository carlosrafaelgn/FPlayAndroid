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
package br.com.carlosrafaelgn.fplay.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PrimitiveBufferedInputStream extends BufferedInputStream {
	public final int totalLength;
	private final byte[] tmp = new byte[4];

	public PrimitiveBufferedInputStream(InputStream in, int size, int totalLength) {
		super(in, size);
		this.totalLength = totalLength;
	}

	@Override
	public synchronized long skip(long n) throws IOException {
		long actuallySkipped = super.skip(n);
		if (actuallySkipped < n) {
			actuallySkipped += in.skip(n - actuallySkipped);
			pos = 0;
			count = 0;
			markpos = -1;
		}
		return actuallySkipped;
	}

	public int readUInt32BE() throws IOException {
		if (super.read(tmp, 0, 4) < 4)
			return -1;
		return ((tmp[0] & 0xff) << 24) | ((tmp[1] & 0xff) << 16) | ((tmp[2] & 0xff) << 8) | (tmp[3] & 0xff);
	}

	public int readUInt32LE() throws IOException {
		if (super.read(tmp, 0, 4) < 4)
			return -1;
		return (tmp[0] & 0xff) | ((tmp[1] & 0xff) << 8) | ((tmp[2] & 0xff) << 16) | ((tmp[3] & 0xff) << 24);
	}

	public int readUInt16BE() throws IOException {
		if (super.read(tmp, 0, 2) < 2)
			return -1;
		return ((tmp[0] & 0xff) << 8) | (tmp[1] & 0xff);
	}

	public int readUInt16LE() throws IOException {
		if (super.read(tmp, 0, 2) < 2)
			return -1;
		return (tmp[0] & 0xff) | ((tmp[1] & 0xff) << 8);
	}

	public long readPosition() throws IOException {
		if (in instanceof FileInputStream) {
			final FileInputStream fileInputStream = (FileInputStream)in;
			final long available = count - pos;
			return fileInputStream.getChannel().position() - available;
		}
		throw new UnsupportedOperationException("Trying to seek to a new position in a stream that is not a FileInputStream");
	}

	public void seekTo(long position) throws IOException {
		if (in instanceof FileInputStream) {
			final FileInputStream fileInputStream = (FileInputStream)in;
			fileInputStream.getChannel().position(position);
			pos = 0;
			count = 0;
			markpos = -1;
			return;
		}
		throw new UnsupportedOperationException("Trying to seek to a new position in a stream that is not a FileInputStream");
	}
}
