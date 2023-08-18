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

abstract class VorbisCommentExtractor {
	@SuppressWarnings("ResultOfMethodCallIgnored")
	protected static boolean extractVorbisComment(int blockLength, MetadataExtractor metadata, PrimitiveBufferedInputStream f, byte[][] tmpPtr) throws IOException {
		// https://xiph.org/flac/format.html#metadata_block_header
		// https://xiph.org/flac/format.html#metadata_block_vorbis_comment
		// https://www.xiph.org/vorbis/doc/v-comment.html

		final int vendorLength = f.readUInt32LE();
		blockLength -= 4;
		if (vendorLength <= 0 || vendorLength > blockLength)
			return false;

		f.skip(vendorLength);
		blockLength -= vendorLength;
		if (blockLength == 0)
			return true;

		int userCommentListLength = f.readUInt32LE();
		blockLength -= 4;
		if (userCommentListLength == -1 || blockLength < 0 || userCommentListLength > blockLength)
			return false;

		String performer = null;
		String albumArtist = null;
		String composer = null;

		while (userCommentListLength > 0) {
			userCommentListLength--;

			final int length = f.readUInt32LE();
			blockLength -= 4;
			if (length == -1 || blockLength < 0 || length > blockLength)
				return false;

			final int bytesToRead = Math.min(2048, length);

			byte[] tmp = tmpPtr[0];
			if (tmp.length < bytesToRead) {
				tmp = new byte[bytesToRead];
				tmpPtr[0] = tmp;
			}

			f.read(tmp, 0, bytesToRead);

			if (length > bytesToRead)
				f.skip(length - bytesToRead);
			blockLength -= length;

			try {
				int i;
				for (i = 0; i < bytesToRead; i++) {
					if (tmp[i] == 0x3d) { // =
						if (i > 0 && i < (bytesToRead - 1)) {
							final String name = new String(tmp, 0, i, "UTF-8").trim();

							String value;

							switch (name.toUpperCase()) {
							case "TITLE":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									metadata.title = value;
								break;

							case "ARTIST":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0) {
									if (metadata.artist != null)
										metadata.artist += ", " + value;
									else
										metadata.artist = value;
								}
								break;

							case "PERFORMER":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									performer = value;
								break;

							case "ALBUMARTIST":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									albumArtist = value;
								break;

							case "COMPOSER":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									composer = value;
								break;

							case "ALBUM":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									metadata.album = value;
								break;

							case "TRACKNUMBER":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									metadata.track = value;
								break;

							case "DATE":
								if ((value = new String(tmp, i + 1, bytesToRead - (i + 1), "UTF-8").trim()).length() > 0)
									metadata.year = value;
								break;
							}
						}
						break;
					}
				}
			} catch (Throwable ex) {
				// Just ignore...
			}
		}

		if (metadata.artist == null || metadata.artist.length() == 0) {
			if (performer != null && performer.length() > 0)
				metadata.artist = performer;
			else if (albumArtist != null && albumArtist.length() > 0)
				metadata.artist = albumArtist;
			else if (composer != null && composer.length() > 0)
				metadata.artist = composer;
		}

		return true;
	}
}
