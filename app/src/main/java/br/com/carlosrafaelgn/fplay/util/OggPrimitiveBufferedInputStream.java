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

import java.io.IOException;
import java.io.InputStream;

public class OggPrimitiveBufferedInputStream extends PrimitiveBufferedInputStream {
	// https://en.wikipedia.org/wiki/Ogg#Page_structure
	// https://wiki.xiph.org/OggVorbis
	// (Ogg) Basic page structure = 27 bytes
	// (Ogg) Maximum segment table = 255 bytes
	// (Vorbis) Pack type = 1 bytes
	// (Vorbis) Identifier = 6 bytes "vorbis"
	private static final int VORBIS_IDENTIFIER_LENGTH = 1 + 6;

	private int currentPageLength;

	public OggPrimitiveBufferedInputStream(InputStream in, int size, int totalLength) {
		super(in, size, totalLength);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private int readPageLength() throws IOException {
		// Capture pattern (32 bits)
		if (super.readUInt32BE() != 0x4f676753) // OggS
			return -1;

		// Version (8 bits)
		// Header type (8 bits)
		// Granule position (64 bits)
		// Bitstream serial number (32 bits)
		// Page sequence number (32 bits)
		// Checksum (32 bits)
		super.skip(22);

		// Page segments (8 bits)
		int pageSegments = super.read();
		if (pageSegments == -1)
			return -1;

		int pageLength = 0;
		while (pageSegments-- > 0) {
			final int r = super.read();
			if (r == -1)
				return 0;
			pageLength += r;
		}

		return pageLength;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public boolean findInitialVorbisCommentPage() throws IOException {
		for (; ; ) {
			// Read one Ogg page + enough data to try to figure out its vorbis type

			final int pageLength = readPageLength();
			if (pageLength < 0)
				return false;

			if (pageLength == 0)
				continue;

			if (pageLength > VORBIS_IDENTIFIER_LENGTH) {
				if (super.read() == 0x03) {
					final int signature1 = super.readUInt32BE();
					final int signature2 = super.readUInt16BE();
					if (signature1 == 0x766f7262 && // vorb
						signature2 == 0x6973) { // is

						currentPageLength = pageLength - 7;

						return true;
					}

					// Not a comment page, just skip it
					super.skip(pageLength - 7);
				} else {
					// Not a comment page, just skip it
					super.skip(pageLength - 1);
				}
			} else {
				// Not a comment page, just skip it
				super.skip(pageLength);
			}
		}
	}

	@Override
	public synchronized long skip(long n) throws IOException {
		if (n <= 0 || n > Integer.MAX_VALUE)
			return 0;

		long totalSkipped = 0;

		if ((int)n <= currentPageLength) {
			currentPageLength -= (int)n;
			return super.skip(n);
		}

		int additionalSkipLength = (int)n - currentPageLength;
		totalSkipped += super.skip(currentPageLength);
		currentPageLength = 0;

		while (super.available() > 0 || in.available() > 0) {
			currentPageLength = readPageLength();
			if (currentPageLength < 0) {
				// Something wrong happened, so just skip to the end to force an eof
				totalSkipped += super.skip(totalLength);
				return totalSkipped;
			}

			if (currentPageLength == 0)
				continue;

			if (additionalSkipLength <= currentPageLength) {
				currentPageLength -= additionalSkipLength;
				totalSkipped += super.skip(additionalSkipLength);
				break;
			}

			additionalSkipLength -= currentPageLength;
			totalSkipped += super.skip(currentPageLength);
			currentPageLength = 0;
		}

		return totalSkipped;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private boolean readNextPageLength() throws IOException {
		while (super.available() > 0 || in.available() > 0) {
			currentPageLength = readPageLength();
			if (currentPageLength < 0) {
				// Something wrong happened, so just skip to the end to force an eof
				super.skip(totalLength);
				return false;
			}

			if (currentPageLength == 0)
				continue;

			return true;
		}

		return false;
	}

	@Override
	public int read() throws IOException {
		if (currentPageLength == 0 && !readNextPageLength())
			return -1;

		currentPageLength--;
		return super.read();
	}

	@Override
	public int readUInt32BE() throws IOException {
		if (currentPageLength >= 4) {
			currentPageLength -= 4;
			return super.readUInt32BE();
		}

		final int a = read(),
			b = read(),
			c = read(),
			d = read();

		return ((a < 0 || b < 0 || c < 0 || d < 0) ? -1 : (
			((a & 0xff) << 24) |
			((b & 0xff) << 16) |
			((c & 0xff) << 8) |
			(d & 0xff)
		));
	}

	@Override
	public int readUInt32LE() throws IOException {
		if (currentPageLength >= 4) {
			currentPageLength -= 4;
			return super.readUInt32LE();
		}

		final int a = read(),
			b = read(),
			c = read(),
			d = read();

		return ((a < 0 || b < 0 || c < 0 || d < 0) ? -1 : (
			(a & 0xff) |
			((b & 0xff) << 8) |
			((c & 0xff) << 16) |
			((d & 0xff) << 24)
		));
	}

	@Override
	public int readUInt16BE() throws IOException {
		if (currentPageLength >= 2) {
			currentPageLength -= 2;
			return super.readUInt16BE();
		}

		final int a = read(),
			b = read();

		return ((a < 0 || b < 0) ? -1 : (
			((a & 0xff) << 8) |
			(b & 0xff)
		));
	}

	@Override
	public int readUInt16LE() throws IOException {
		if (currentPageLength >= 2) {
			currentPageLength -= 2;
			return super.readUInt16LE();
		}

		final int a = this.read(),
			b = this.read();

		return ((a < 0 || b < 0) ? -1 : (
			(a & 0xff) |
			((b & 0xff) << 8)
		));
	}

	@Override
	public void seekTo(long position) {
		throw new UnsupportedOperationException("Trying to seek to a new position in a OggPrimitiveBufferedInputStream");
	}

	@Override
	public synchronized int read(byte[] b, int off, int len) throws IOException {
		int totalRead = 0;

		while (len > 0 && (super.available() > 0 || in.available() > 0)) {
			if (currentPageLength == 0 && !readNextPageLength())
				return totalRead;

			final int p = super.read(b, off, Math.min(len, currentPageLength));
			if (p <= 0)
				break;

			len -= p;
			currentPageLength -= p;
			off += p;
			totalRead += p;
		}

		if (currentPageLength == 0)
			readNextPageLength();

		return totalRead;
	}
}
