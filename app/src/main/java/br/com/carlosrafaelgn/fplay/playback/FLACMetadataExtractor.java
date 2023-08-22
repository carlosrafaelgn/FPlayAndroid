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

import java.io.IOException;

import br.com.carlosrafaelgn.fplay.util.PrimitiveBufferedInputStream;

final class FLACMetadataExtractor extends VorbisCommentExtractor {
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static int readHeaderAndStreamInfo(MetadataExtractor metadata, PrimitiveBufferedInputStream f, byte[] tmp) throws IOException {
		// https://xiph.org/flac/format.html#stream

		if (f.readUInt32BE() != 0x664c6143) // fLaC
			return 0;

		int typeAndLength = f.readUInt32BE();
		if (typeAndLength == 0 || typeAndLength == -1)
			return 0;

		final int lastBlock = (typeAndLength & 0x80000000);
		typeAndLength &= 0x7fffffff;
		if (typeAndLength != 0x22)
			return 0;

		// https://xiph.org/flac/format.html#metadata_block_streaminfo

		f.skip(10); // minimum block size, maximum block size, minimum frame size, maximum frame size

		int d0 = f.readUInt32BE();
		long totalSamples = (long)f.readUInt32BE();
		if (d0 == 0 || d0 == -1 || totalSamples == -1)
			return 0;

		totalSamples &= 0xFFFFFFFFL;
		f.skip(16); // MD5 signature of the unencoded audio data (128 bits)

		// d0
		// MSB
		// sample rate in Hz (20 bits)
		// number of channels - 1 (3 bits)
		// bits per sample - 1 (5 bits)
		// MSB total samples in stream (4 bits)
		// LSB

		totalSamples += (long)(d0 & 0x0f) << 32; // Not using << because this number is larger than 0xffffffff
		d0 >>>= 4;

		final int bitsPerSample = (d0 & 0x1f) + 1;
		d0 >>>= 5;

		final int numberOfChannels = (d0 & 0x07) + 1;
		d0 >>>= 3;

		// d0 is the sample rate now
		if (bitsPerSample < 4 || d0 == 0)
			return 0;

		if (totalSamples != 0)
			metadata.length = Integer.toString((int)Math.floor(totalSamples * 1000.0 / d0));

		metadata.sampleRate = d0;
		metadata.channels = numberOfChannels;

		return ((lastBlock != 0) ? -1 : 1);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static int readMetadataBlock(MetadataExtractor metadata, PrimitiveBufferedInputStream f, byte[][] tmpPtr) throws IOException {
		// https://xiph.org/flac/format.html#metadata_block_header
		// https://xiph.org/flac/format.html#metadata_block_vorbis_comment
		// https://www.xiph.org/vorbis/doc/v-comment.html

		int typeAndLength = f.readUInt32BE();
		if (typeAndLength == 0 || typeAndLength == -1)
			return 0;

		final int lastBlock = (typeAndLength & 0x80000000);
		typeAndLength &= 0x7fffffff;

		final int type = typeAndLength >>> 24;
		// typeAndLength is the length now
		typeAndLength &= 0x00ffffff;

		if (type == 127 || (typeAndLength + f.readPosition()) > f.totalLength)
			return 0;

		if (type == 4) // VORBIS_COMMENT
			return (extractVorbisComment(typeAndLength, metadata, f, tmpPtr) ? -1 : 0);

		f.skip(typeAndLength);

		return ((lastBlock != 0) ? -1 : 1);
	}

	public static void extract(MetadataExtractor metadata, PrimitiveBufferedInputStream f, byte[][] tmpPtr) throws IOException {
		// The header and stream info, together, should have less than 256 bytes. Therefore,
		// since f is filled with enough data, readHeaderAndStreamInfo() does not need to be async.
		int r = readHeaderAndStreamInfo(metadata, f, tmpPtr[0]);
		if (r == 0)
			return;
		if (r < 0) {
			metadata.hasData = true;
			return;
		}

		do {
			r = readMetadataBlock(metadata, f, tmpPtr);
			if (r == 0)
				return;
		} while (r > 0);

		metadata.hasData = true;
	}
}
