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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.util.OggPrimitiveBufferedInputStream;
import br.com.carlosrafaelgn.fplay.util.PrimitiveBufferedInputStream;

public final class MetadataExtractor {
	private static final int ALL_B = 0x3f;
	private static final int ALL_BUT_LENGTH_B = 0x1f;
	private static final int TITLE_B = 0x01;
	private static final int ARTIST_B = 0x02;
	private static final int ALBUM_B = 0x04;
	private static final int TRACK_B = 0x08;
	private static final int YEAR_B = 0x10;
	private static final int LENGTH_B = 0x20;

	public boolean hasData;
	public String title, artist, album, track, year, length;
	public int sampleRate, channels;
	private byte[][] tmpPtr = new byte[][] { new byte[256] };

	// Reads at most rawLength bytes from in, removing the 0x00 bytes inserted after every 0xFF
	// byte by the unsynchronisation scheme (used for ID3v2.2 and ID3v2.3 tags, where the scheme
	// is applied to the entire tag)
	private static final class UnsynchronisedInputStream extends InputStream {
		private final InputStream in;
		private int rawBytesLeft;
		private boolean lastWasFF;

		UnsynchronisedInputStream(InputStream in, int rawLength) {
			this.in = in;
			this.rawBytesLeft = rawLength;
		}

		@Override
		public int read() throws IOException {
			for (; ; ) {
				if (rawBytesLeft <= 0)
					return -1;
				final int b = in.read();
				if (b < 0) {
					rawBytesLeft = 0;
					return -1;
				}
				rawBytesLeft--;
				final boolean inserted = (lastWasFF && b == 0);
				lastWasFF = (b == 0xff);
				if (!inserted)
					return b;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int total = 0;
			while (total < len) {
				final int c = read();
				if (c < 0)
					break;
				b[off + total] = (byte)c;
				total++;
			}
			return ((total == 0 && len > 0) ? -1 : total);
		}

		@Override
		public int available() throws IOException {
			// BufferedInputStream relies on this to decide whether to keep reading or not
			return Math.min(rawBytesLeft, in.available());
		}
	}

	private String readInfoStr(PrimitiveBufferedInputStream f, int actualStrLen) throws IOException {
		actualStrLen = f.read(tmpPtr[0], 0, actualStrLen);
		return finishReadingV2Frame(0, actualStrLen, false);
	}

	// RIFF chunks are word aligned, so a chunk with an odd length is followed by a padding byte,
	// which is not included in the chunk length (returns the updated value of bytesLeft)
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static int skipRIFFPadding(PrimitiveBufferedInputStream f, int chunkLength, int bytesLeft) throws IOException {
		if ((chunkLength & 1) == 0 || bytesLeft <= 0)
			return bytesLeft;
		f.skip(1);
		return bytesLeft - 1;
	}

	// When entering extractRIFFInfo() the list type (INFO) has already been consumed, and when
	// leaving it, all bytesLeftInChunk bytes have been consumed (unless false is returned)
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private boolean extractRIFFInfo(PrimitiveBufferedInputStream f, int bytesLeftInChunk) throws IOException {
		// https://www.robotplanet.dk/audio/wav_meta_data/
		// https://www.robotplanet.dk/audio/wav_meta_data/riff_mci.pdf
		while (bytesLeftInChunk > 0) {
			if (bytesLeftInChunk < 8) {
				f.skip(bytesLeftInChunk);
				break;
			}

			final int metadataId = f.readUInt32BE();
			final int nullTerminatedStrLen = f.readUInt32LE();
			bytesLeftInChunk -= 8;

			if (metadataId == -1)
				return false;

			if (nullTerminatedStrLen < 0 || nullTerminatedStrLen > bytesLeftInChunk) {
				f.skip(bytesLeftInChunk);
				break;
			}

			if (nullTerminatedStrLen <= 1) {
				f.skip(nullTerminatedStrLen);
				bytesLeftInChunk -= nullTerminatedStrLen;
				bytesLeftInChunk = skipRIFFPadding(f, nullTerminatedStrLen, bytesLeftInChunk);
				continue;
			}

			int actualStrLen = Math.min(257, nullTerminatedStrLen) - 1;

			switch (metadataId) {
			case 0x494e414d: // INAM
				hasData = true;
				title = readInfoStr(f, actualStrLen);
				break;
			case 0x49505244: // IPRD
				hasData = true;
				album = readInfoStr(f, actualStrLen);
				break;
			case 0x49415254: // IART
				hasData = true;
				artist = readInfoStr(f, actualStrLen);
				break;
			case 0x49435244: // ICRD
				hasData = true;
				year = readInfoStr(f, actualStrLen);
				break;
			case 0x4954524b: // ITRK
			case 0x49505254: // IPRT (used by a few writers, such as ffmpeg, to store the track number)
				hasData = true;
				track = readInfoStr(f, actualStrLen);
				break;
			default:
				actualStrLen = 0;
				break;
			}

			f.skip(nullTerminatedStrLen - actualStrLen);
			bytesLeftInChunk -= nullTerminatedStrLen;
			bytesLeftInChunk = skipRIFFPadding(f, nullTerminatedStrLen, bytesLeftInChunk);
		}

		return true;
	}

	// When entering extractRIFFList() the chunk id and length have already been consumed, and
	// when leaving it, all listLength bytes have been consumed (unless false is returned)
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private boolean extractRIFFList(PrimitiveBufferedInputStream f, int listLength) throws IOException {
		if (listLength < 4) {
			f.skip(listLength);
			return true;
		}

		final int listType = f.readUInt32BE();
		if (listType == -1)
			return false;

		if (listType == 0x494e464f) // INFO
			return extractRIFFInfo(f, listLength - 4);

		f.skip(listLength - 4);
		return true;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private boolean extractRIFF(PrimitiveBufferedInputStream f) throws IOException {
		// When entering extractRIFF() the first four bytes have already been consumed
		if (f.totalLength < 44)
			return false;

		final int riffLength = f.readUInt32LE();
		if (riffLength == -1 || f.totalLength < (8 + riffLength))
			return false;

		int bytesLeftInFile = riffLength, id3Position = 0, dataLength = 0, avgBytesPerSec = 0;
		boolean fmtOk = false, dataOk = false;

		// bytesLeftInFile must always be kept up to date, as it is used to compute id3Position
		// (the current position in the file is 8 + riffLength - bytesLeftInFile)
		_mainLoop:
		while (bytesLeftInFile > 0) {
			final int chunkId = f.readUInt32BE();
			if (chunkId == -1)
				break;

			if (chunkId == 0x57415645) { // WAVE (the form type, which is not followed by a length)
				bytesLeftInFile -= 4;
				continue;
			}

			final int chunkLength = f.readUInt32LE();

			bytesLeftInFile -= 8;

			if (chunkLength < 0 || chunkLength > bytesLeftInFile)
				break;

			int bytesLeftInChunk = chunkLength;

			switch (chunkId) {
			case 0x666d7420: // fmt
				// https://docs.microsoft.com/en-us/windows/win32/api/mmeapi/ns-mmeapi-waveformatex
				// https://docs.microsoft.com/en-us/windows/win32/directshow/audio-subtypes
				final int wFormatTag = f.readUInt16LE(),
					nChannels = f.readUInt16LE(),
					nSamplesPerSec = f.readUInt32LE(),
					nAvgBytesPerSec = f.readUInt32LE(),
					nBlockAlign = f.readUInt16LE(),
					wBitsPerSample = f.readUInt16LE();

				bytesLeftInChunk -= 16;
				bytesLeftInFile -= 16;

				if (wFormatTag == -1 ||
					nChannels == -1 ||
					nSamplesPerSec == -1 ||
					nAvgBytesPerSec == -1 ||
					nBlockAlign == -1 ||
					wBitsPerSample == -1)
					break _mainLoop;

				int formatTag = wFormatTag;

				// https://docs.microsoft.com/en-us/windows/win32/api/mmreg/ns-mmreg-waveformatextensible
				if (wFormatTag == 0xfffe && bytesLeftInChunk >= 24) { // WAVE_FORMAT_EXTENSIBLE
					f.skip(8); // cbSize, wValidBitsPerSample, dwChannelMask

					// The first two bytes of SubFormat (a GUID) contain the actual format tag
					formatTag = f.readUInt16LE();

					bytesLeftInChunk -= 10;
					bytesLeftInFile -= 10;

					if (formatTag == -1)
						break _mainLoop;
				}

				// We are ignoring compressed files...
				if (formatTag != 1 && // WAVE_FORMAT_PCM
					formatTag != 3) // WAVE_FORMAT_IEEE_FLOAT
					break;

				fmtOk = true;

				if (avgBytesPerSec == 0)
					avgBytesPerSec = nAvgBytesPerSec;

				sampleRate = nSamplesPerSec;
				channels = nChannels;
				break;

			case 0x64617461: // data
				dataOk = true;

				if (dataLength == 0)
					dataLength = chunkLength;
				break;

			case 0x4c495354: // LIST
				if (!extractRIFFList(f, chunkLength))
					break _mainLoop;

				bytesLeftInFile -= chunkLength;
				bytesLeftInChunk = 0;
				break;

			case 0x69643320: // id3
			case 0x49443320: // ID3
				if (bytesLeftInChunk > 4) {
					final int b0 = f.read(),
						b1 = f.read(),
						b2 = f.read();

					if (b0 == -1 || b1 == -1 || b2 == -1)
						break _mainLoop;

					bytesLeftInFile -= 3;
					bytesLeftInChunk -= 3;

					if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) { // ID3
						if (fmtOk && dataOk) {
							// Nothing else is needed, so just leave f right after "ID3"
							id3Position = -1;
							break _mainLoop;
						}
						// Right after "ID3" (8 = "RIFF" + riffLength, which are not included in riffLength)
						id3Position = 8 + riffLength - bytesLeftInFile;
					}
				}
				break;
			}

			if (bytesLeftInChunk > 0) {
				f.skip(bytesLeftInChunk);
				bytesLeftInFile -= bytesLeftInChunk;
			}

			bytesLeftInFile = skipRIFFPadding(f, chunkLength, bytesLeftInFile);
		}

		if (fmtOk && dataOk) {
			if (id3Position > 0)
				f.seekTo(id3Position);
			length = Integer.toString((int)Math.floor(dataLength * 1000.0 / avgBytesPerSec));
			return (id3Position != 0);
		}

		return false;
	}

	private static boolean isTrimmable(char c) {
		switch (c) {
		case 0x0000:
		case 0x0009:
		case 0x000A:
		case 0x000B:
		case 0x000C:
		case 0x000D:
		case 0x0020:
		case 0x0085:
		case 0x00A0:
		case 0xFEFF: //BOM
			return true;
		default:
			return false;
		}
	}

	private String finishReadingV2Frame(int encoding, int frameSize, boolean multipleValues) throws IOException {
		if (frameSize <= 0)
			return null;
		final byte[] tmp = tmpPtr[0];
		//according to http://developer.android.com/reference/java/nio/charset/Charset.html
		//the following charsets are ALWAYS available:
		//ISO-8859-1
		//US-ASCII
		//UTF-16
		//UTF-16BE
		//UTF-16LE
		//UTF-8
		final String str;
		switch (encoding) {
		case 0: //ISO-8859-1
			str = new String(tmp, 0, frameSize, "ISO-8859-1");
			break;
		case 1: //UCS-2 (UTF-16 encoded Unicode with BOM), in ID3v2.2 and ID3v2.3
			str = new String(tmp, 0, frameSize & ~1, "UTF-16");
			break;
		case 2: //UTF-16BE encoded Unicode without BOM, in ID3v2.4
			str = new String(tmp, 0, frameSize & ~1, "UTF-16BE");
			break;
		case 3: //UTF-8 encoded Unicode, in ID3v2.4 (a possible BOM is trimmed below)
			str = new String(tmp, 0, frameSize, "UTF-8");
			break;
		default:
			return null;
		}
		//only trim after decoding, because trimming the raw bytes breaks multi-byte characters
		//(e.g., 0x85 and 0xA0 are also UTF-8 continuation bytes, and 0x00 is half of a UTF-16 unit)
		final String trimmed = trim(str);
		if (trimmed == null)
			return null;
		int nullIndex = trimmed.indexOf('\0');
		if (nullIndex < 0)
			return trimmed;
		//in ID3v2.4, text frames may contain multiple values separated by 0x00, whereas in the
		//other versions (and in RIFF INFO chunks), everything after the first 0x00 must be ignored
		if (!multipleValues)
			return trim(trimmed.substring(0, nullIndex));
		final StringBuilder builder = new StringBuilder(trimmed.length() + 8);
		int start = 0;
		while (start <= trimmed.length()) {
			if ((nullIndex = trimmed.indexOf('\0', start)) < 0)
				nullIndex = trimmed.length();
			//each value is trimmed individually (in UTF-16, each value may even have its own BOM)
			final String value = trim(trimmed.substring(start, nullIndex));
			if (value != null) {
				if (builder.length() > 0)
					builder.append(", ");
				builder.append(value);
			}
			start = nullIndex + 1;
		}
		return ((builder.length() == 0) ? null : builder.toString());
	}

	//returns null if nothing is left after trimming
	private static String trim(String str) {
		int start = 0, end = str.length();
		while (start < end && isTrimmable(str.charAt(start)))
			start++;
		while (end > start && isTrimmable(str.charAt(end - 1)))
			end--;
		return ((start == end) ? null : str.substring(start, end));
	}

	//ID3v1 fields are terminated by a 0x00 byte (unless they occupy the entire field), but they
	//are also frequently padded with spaces
	private static String readID3v1Field(byte[] tmp, int offset, int maxLength) throws IOException {
		int length = 0;
		while (length < maxLength && tmp[offset + length] != 0)
			length++;
		return trim(new String(tmp, offset, length, "ISO-8859-1"));
	}

	//removes the 0x00 bytes inserted after every 0xFF byte by the unsynchronisation scheme
	//(returns the new length of the data)
	private static int removeUnsynchronisation(byte[] buffer, int length) {
		int dst = 0;
		for (int src = 0; src < length; src++) {
			final byte b = buffer[src];
			buffer[dst++] = b;
			if (b == (byte)0xff && (src + 1) < length && buffer[src + 1] == 0)
				src++;
		}
		return dst;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private String readV2Frame(PrimitiveBufferedInputStream f, int frameSize, boolean unsynchronised, boolean multipleValues) throws IOException {
		if (frameSize < 2) {
			f.skip(frameSize);
			return null;
		}
		final int encoding = f.read();
		frameSize--; //discount the encoding
		if (encoding < 0 || encoding > 3) {
			f.skip(frameSize);
			return null;
		}
		byte[] tmp = tmpPtr[0];
		if (frameSize > tmp.length) {
			tmp = new byte[frameSize + 16];
			tmpPtr[0] = tmp;
		}
		frameSize = f.read(tmp, 0, frameSize);
		//the encoding byte is never 0xFF, so there is no 0x00 to be removed right after it
		if (unsynchronised && frameSize > 0)
			frameSize = removeUnsynchronisation(tmp, frameSize);
		return finishReadingV2Frame(encoding, frameSize, multipleValues);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void extractID3v1(FileInputStream fileInputStream, int found) {
		byte[] tmp = tmpPtr[0];
		FileChannel fileChannel = null;
		try {
			fileChannel = fileInputStream.getChannel();
			fileChannel.position(fileChannel.size() - 128);
			fileInputStream.read(tmp, 0, 128);
			if (tmp[0] != 0x54 ||
				tmp[1] != 0x41 ||
				tmp[2] != 0x47) //TAG
				return;
			//struct _ID3v1 {
			//public:
			//	char title[30];
			//	char artist[30];
			//	char album[30];
			//	char year[4];
			//	char comment[28];
			//	unsigned char zeroByte; //If a track number is stored, this byte contains a binary 0.
			//	unsigned char track; //The number of the track on the album, or 0. Invalid, if previous byte is not a binary 0.
			//	unsigned char genre; //Index in a list of genres, or 255
			//} tag;
			String field;
			if ((found & TITLE_B) == 0 && (field = readID3v1Field(tmp, 3, 30)) != null) {
				title = field;
				hasData = true;
			}
			if ((found & ARTIST_B) == 0 && (field = readID3v1Field(tmp, 3 + 30, 30)) != null) {
				artist = field;
				hasData = true;
			}
			if ((found & ALBUM_B) == 0 && (field = readID3v1Field(tmp, 3 + 30 + 30, 30)) != null) {
				album = field;
				hasData = true;
			}
			if ((found & YEAR_B) == 0 && (field = readID3v1Field(tmp, 3 + 30 + 30 + 30, 4)) != null) {
				year = field;
				hasData = true;
			}
			if ((found & TRACK_B) == 0 && tmp[128 - 3] == 0 && tmp[128 - 2] != 0) {
				track = Integer.toString((int)tmp[128 - 2] & 0xff);
				hasData = true;
			}
		} catch (Throwable ex) {
			//ignore all exceptions while reading ID3v1, in favor of
			//everything that has already been read in ID3v2
		} finally {
			if (fileChannel != null) {
				try {
					fileChannel.close();
				} catch (Throwable ex) {
					//ignore all exceptions while reading ID3v1, in favor of
					//everything that has already been read in ID3v2
				}
			}
		}
	}

	private void extractSampleRateAndChannels(PrimitiveBufferedInputStream f, int firstFramePosition, boolean aac) {
		try {
			f.seekTo(firstFramePosition);

			// Sometimes, there are a few zeroed bytes after the ID3
			int paddingBytes = 4092;
			int hdr = 0;

			while (paddingBytes > 0) {
				final int firstHeaderByte = f.read();
				if (firstHeaderByte < 0)
					break;

				if (firstHeaderByte > 0) {
					if (firstHeaderByte == 0xFF) {
						hdr = ((firstHeaderByte << 24) |
							(f.read() << 16) |
							(f.read() << 8) |
							f.read());
					}
					break;
				}

				paddingBytes--;
			}

			if (aac) {
				// https://wiki.multimedia.cx/index.php?title=ADTS
				//
				// AAAAAAAA AAAABCCD EEFFFFGH HHIJKLMM MMMMMMMM MMMOOOOO OOOOOOPP (QQQQQQQQ QQQQQQQQ)
				//
				// Header consists of 7 or 9 bytes (without or with CRC).
				//
				// A = Syncword, all bits must be set to 1.
				// B = MPEG Version, set to 0 for MPEG-4 and 1 for MPEG-2.
				// C = Layer, always set to 0.
				// D = Protection absence, set to 1 if there is no CRC and 0 if there is CRC.
				// E = Profile, the MPEG-4 Audio Object Type minus 1.
				// F = MPEG-4 Sampling Frequency Index (15 is forbidden).
				// G = Private bit, guaranteed never to be used by MPEG, set to 0 when encoding, ignore when decoding.
				// H = MPEG-4 Channel Configuration (in the case of 0, the channel configuration is sent via an inband PCE (Program Config Element)).

				if (hdr == -1 || ((hdr >> 20) & 0xfff) != 0xfff)
					return;

				// https://wiki.multimedia.cx/index.php?title=MPEG-4_Audio
				final int frequencyIndex = (hdr >> 10) & 15;
				if (frequencyIndex >= 13)
					return;

				switch (frequencyIndex) {
				case 0:
					sampleRate = 96000;
					break;
				case 1:
					sampleRate = 88200;
					break;
				case 2:
					sampleRate = 64000;
					break;
				case 3:
					sampleRate = 48000;
					break;
				case 4:
					sampleRate = 44100;
					break;
				case 5:
					sampleRate = 32000;
					break;
				case 6:
					sampleRate = 24000;
					break;
				case 7:
					sampleRate = 22050;
					break;
				case 8:
					sampleRate = 16000;
					break;
				case 9:
					sampleRate = 12000;
					break;
				case 10:
					sampleRate = 11025;
					break;
				case 11:
					sampleRate = 8000;
					break;
				default:
					sampleRate = 7350;
					break;
				}

				// With HE-AAC (SBR), the ADTS header contains the sample rate of the core AAC
				// stream, which is half of the actual output sample rate. Unfortunately, SBR can
				// only be reliably detected by decoding the audio data, so we just assume that low
				// sample rates mean HE-AAC (this is the case for nearly all music encoded at such
				// rates, but genuine low sample rate AAC-LC files will be reported incorrectly)
				final boolean assumedHEAAC = (sampleRate <= 24000);
				if (assumedHEAAC)
					sampleRate <<= 1;

				final int channelIndex = (hdr >> 6) & 7;

				switch (channelIndex) {
				case 0:
					channels = 2; // Assumed :)
					break;
				case 7:
					channels = 8;
					break;
				default:
					channels = channelIndex;
					break;
				}

				// With HE-AAC v2 (SBR + PS), the ADTS header contains the channel configuration of
				// the core AAC stream, which is mono, whereas the actual output is stereo, as
				// Parametric Stereo is used to rebuild the stereo image. Just as SBR, PS can only be
				// reliably detected by decoding the audio data, so we just assume that mono streams
				// that we already assumed to be HE-AAC are, actually, HE-AAC v2 (genuine mono HE-AAC
				// files will be reported incorrectly)
				if (assumedHEAAC && channels == 1)
					channels = 2;
			} else {
				// http://www.mp3-tech.org/programmer/frame_header.html
				//
				// AAAAAAAA AAABBCCD EEEEFFGH IIJJKLMM
				//
				// A = Frame sync (all bits must be set)
				// B = MPEG Audio version ID
				//     00 - MPEG Version 2.5 (later extension of MPEG 2)
				//     01 - reserved
				//     10 - MPEG Version 2 (ISO/IEC 13818-3)
				//     11 - MPEG Version 1 (ISO/IEC 11172-3)
				// C = Layer description
				//     00 - reserved
				//     01 - Layer III
				//     10 - Layer II
				//     11 - Layer I
				// D = Protection bit
				// E = Bitrate index
				// F = Sampling rate frequency index
				//     bits	MPEG1    MPEG2    MPEG2.5
				//     00   44100 Hz 22050 Hz 11025 Hz
				//     01   48000 Hz 24000 Hz 12000 Hz
				//     10   32000 Hz 16000 Hz 8000 Hz
				//     11   reserv.  reserv.  reserv.
				// G = Padding bit
				// H = Private bit
				// I = Channel Mode
				//     00 - Stereo
				//     01 - Joint stereo (Stereo)
				//     10 - Dual channel (2 mono channels)
				//     11 - Single channel (Mono)

				if (hdr == -1 || ((hdr >> 21) & 0x7ff) != 0x7ff)
					return;

				final int version = (hdr >> 19) & 3;
				if (version == 1)
					return;

				final int frequencyIndex = (hdr >> 10) & 3;
				if (frequencyIndex == 3)
					return;

				switch (version) {
				case 0: // MPEG Version 2.5 (later extension of MPEG 2)
					switch (frequencyIndex) {
					case 0:
						sampleRate = 11025;
						break;
					case 1:
						sampleRate = 12000;
						break;
					case 2:
						sampleRate = 8000;
						break;
					}
					break;
				case 2: // MPEG Version 2 (ISO/IEC 13818-3)
					switch (frequencyIndex) {
					case 0:
						sampleRate = 22050;
						break;
					case 1:
						sampleRate = 24000;
						break;
					case 2:
						sampleRate = 16000;
						break;
					}
					break;
				case 3: // MPEG Version 1 (ISO/IEC 11172-3)
					switch (frequencyIndex) {
					case 0:
						sampleRate = 44100;
						break;
					case 1:
						sampleRate = 48000;
						break;
					case 2:
						sampleRate = 32000;
						break;
					}
					break;
				}

				channels = ((((hdr >> 6) & 3) == 3) ? 1 : 2);
			}
		} catch (Throwable ex) {
			// Just ignore, in favor of everything that has been extracted so far
		}
	}

	//synchsafe integers only use the 7 least significant bits of each byte
	static int decodeSynchsafe(int value) {
		return (value & 0x7f) |
			((value >> 1) & 0x3f80) |
			((value >> 2) & 0x1fc000) |
			((value >> 3) & 0xfe00000);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void extractID3v2Andv1(PrimitiveBufferedInputStream f, FileInputStream fileInputStream, boolean aac, boolean fetchSampleRateAndChannels) throws IOException  {
		//struct _ID3v2TagHdr {
		//public:
		//	unsigned int hdr;
		//	unsigned char hdrRev;
		//	unsigned char flags;
		//	unsigned char sizeBytes[4];
		//} tagV2Hdr;

		int found = 0;
		int hdr = (f.read() << 24) | (f.read() << 16) | (f.read() << 8);
		if (hdr != 0x49443300) { //ID3x
			hdr |= f.read();
			if (hdr == 0x52494646) { // RIFF
				fetchSampleRateAndChannels = false;
				// When extractRIFF == true, f points to a possible ID3 tag, with the first 3 bytes already consumed
				if (!extractRIFF(f))
					return;
				if (length != null && length.length() > 0)
					found |= LENGTH_B;
			} else {
				if (fetchSampleRateAndChannels)
					extractSampleRateAndChannels(f, 0, aac);
				extractID3v1(fileInputStream, 0);
				return;
			}
		}
		final int majorVersion = f.read();
		f.read(); //ignore the revision as it does not affect the layout of the tag
		final int flags = f.read();
		final int sizeBytes0 = f.read();
		final int sizeBytes1 = f.read();
		final int sizeBytes2 = f.read();
		final int sizeBytes3 = f.read();
		int size = ((flags & 0x10) != 0 ? 10 : 0) + //footer presence flag
		(
			(sizeBytes3 & 0x7f) |
			((sizeBytes2 & 0x7f) << 7) |
			((sizeBytes1 & 0x7f) << 14) |
			((sizeBytes0 & 0x7f) << 21)
		);

		final int id3TotalLength = 10 + size;

		//in ID3v2.2, flag 0x40 means the whole tag is compressed, but in ID3v2.3 and ID3v2.4,
		//it indicates the presence of an extended header
		if (majorVersion >= 2 && majorVersion <= 4 && (majorVersion != 2 || (flags & 0x40) == 0)) {
			//in ID3v2.2 and ID3v2.3, flag 0x80 means the unsynchronisation scheme has been applied to
			//the entire tag (frame headers included), whereas in ID3v2.4, it is applied to the data
			//of each frame individually (in which case, it is handled by extractID3v2Frames())
			if ((flags & 0x80) != 0 && majorVersion != 4)
				found = extractID3v2Frames(new PrimitiveBufferedInputStream(new UnsynchronisedInputStream(f, size), 4096, size), majorVersion, flags, size, found);
			else
				found = extractID3v2Frames(f, majorVersion, flags, size, found);
		}

		hasData |= (found != 0);

		//even when the ID3v2 tag is not supported, the audio data and a possible ID3v1 tag still are
		if (fetchSampleRateAndChannels)
			extractSampleRateAndChannels(f, id3TotalLength, aac);

		//try to extract ID3v1 only if there are any blank fields
		if ((found & ALL_BUT_LENGTH_B) != ALL_BUT_LENGTH_B && fileInputStream != null)
			extractID3v1(fileInputStream, found);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private int extractID3v2Frames(PrimitiveBufferedInputStream f, int majorVersion, int flags, int size, int found) throws IOException {
		//http://id3.org/id3v2-00
		//http://id3.org/id3v2.3.0
		//http://id3.org/id3v2.4.0-structure
		//http://id3.org/id3v2.4.0-frames
		final boolean v22 = (majorVersion == 2);
		final boolean v24 = (majorVersion == 4);
		final boolean allFramesUnsynchronised = (v24 && (flags & 0x80) != 0);
		final int frameHeaderSize = (v22 ? 6 : 10);
		if (!v22 && (flags & 0x40) != 0) {
			//skip the extended header: in ID3v2.3, its size is a regular integer that does not
			//include the 4 bytes of the size itself, whereas in ID3v2.4, its size is a synchsafe
			//integer that includes the entire extended header
			int extendedHeaderSize = f.readUInt32BE();
			if (v24)
				extendedHeaderSize = (((extendedHeaderSize & 0x80808080) != 0) ? -1 : (decodeSynchsafe(extendedHeaderSize) - 4));
			if (extendedHeaderSize < 0 || (4 + extendedHeaderSize) > size)
				return found; //invalid extended header, so do not even try to read the frames
			f.skip(extendedHeaderSize);
			size -= (4 + extendedHeaderSize);
		}
		while (size > 0 && found != ALL_B) {
			int frameId, frameSize, frameFlags = 0;
			if (v22) {
				//struct _ID3v22FrameHdr {
				//public:
				//	unsigned char id[3];
				//	unsigned char size[3];
				//} frame;
				frameId = (f.read() << 16) | (f.read() << 8) | f.read();
				frameSize = (f.read() << 16) | (f.read() << 8) | f.read();
				//map the frames we are interested in to their ID3v2.3 equivalents
				switch (frameId) {
				case 0x545432: //TT2
					frameId = 0x54495432; //TIT2
					break;
				case 0x545031: //TP1
					frameId = 0x54504531; //TPE1
					break;
				case 0x54414c: //TAL
					frameId = 0x54414c42; //TALB
					break;
				case 0x54524b: //TRK
					frameId = 0x5452434b; //TRCK
					break;
				case 0x545945: //TYE
					frameId = 0x54594552; //TYER
					break;
				case 0x544c45: //TLE
					frameId = 0x544c454e; //TLEN
					break;
				}
			} else {
				//struct _ID3v2FrameHdr {
				//public:
				//	unsigned int id;
				//	unsigned int size;
				//	unsigned short flags;
				//} frame;
				frameId = f.readUInt32BE();
				frameSize = f.readUInt32BE();
				//in ID3v2.4, frame sizes are synchsafe integers (7 bits per byte), but a few taggers
				//(e.g., old versions of iTunes) wrote regular integers instead, which can only be
				//detected when the most significant bit of any of the bytes is set
				if (v24 && (frameSize & 0x80808080) == 0)
					frameSize = decodeSynchsafe(frameSize);
				frameFlags = f.readUInt16BE();
			}
			if (frameId == 0 || frameSize <= 0 || frameSize > size)
				break;

			size -= (frameHeaderSize + frameSize);

			boolean frameUnsynchronised = false;
			if (v24) {
				//https://id3.org/id3v2.4.0-structure (4.1.2. Frame header flags)
				if ((frameFlags & 0x0c) != 0) {
					//compressed (0x08) or encrypted (0x04) frames are not supported
					f.skip(frameSize);
					continue;
				}
				//extra bytes added before the actual frame data
				int extraBytes = 0;
				if ((frameFlags & 0x40) != 0) //grouping identity
					extraBytes++;
				if ((frameFlags & 0x01) != 0) //data length indicator
					extraBytes += 4;
				if (extraBytes > 0) {
					if (extraBytes >= frameSize) {
						f.skip(frameSize);
						continue;
					}
					f.skip(extraBytes);
					frameSize -= extraBytes;
				}
				frameUnsynchronised = (allFramesUnsynchronised || (frameFlags & 0x02) != 0);
			} else if (!v22) {
				//https://id3.org/id3v2.3.0 (3.3.1. Frame header flags)
				if ((frameFlags & 0xc0) != 0) {
					//compressed (0x80) or encrypted (0x40) frames are not supported
					f.skip(frameSize);
					continue;
				}
				if ((frameFlags & 0x20) != 0) {
					//grouping identity (an extra byte added before the actual frame data)
					f.skip(1);
					frameSize--;
				}
			}

			switch (frameId) {
			case 0x54495432: //title - TIT2
				if ((found & TITLE_B) == 0) {
					if ((title = readV2Frame(f, frameSize, frameUnsynchronised, v24)) != null)
						found |= TITLE_B;
				} else {
					f.skip(frameSize);
				}
				break;
			case 0x54504531: //artist - TPE1
				if ((found & ARTIST_B) == 0) {
					if ((artist = readV2Frame(f, frameSize, frameUnsynchronised, v24)) != null)
						found |= ARTIST_B;
				} else {
					f.skip(frameSize);
				}
				break;
			case 0x54414c42: //album - TALB
				if ((found & ALBUM_B) == 0) {
					if ((album = readV2Frame(f, frameSize, frameUnsynchronised, v24)) != null)
						found |= ALBUM_B;
				} else {
					f.skip(frameSize);
				}
				break;
			case 0x5452434b: //track - TRCK
				if ((found & TRACK_B) == 0) {
					if ((track = readV2Frame(f, frameSize, frameUnsynchronised, v24)) != null)
						found |= TRACK_B;
				} else {
					f.skip(frameSize);
				}
				break;
			case 0x54594552: //year - TYER
			case 0x54445243: //Recording time - TDRC (normalized later by normalizeTrackAndYear())
				if ((found & YEAR_B) == 0) {
					if ((year = readV2Frame(f, frameSize, frameUnsynchronised, v24)) != null)
						found |= YEAR_B;
				} else {
					f.skip(frameSize);
				}
				break;
			case 0x544c454e: //length - TLEN
				if ((found & LENGTH_B) == 0) {
					if ((length = readV2Frame(f, frameSize, frameUnsynchronised, v24)) != null)
						found |= LENGTH_B;
				} else {
					f.skip(frameSize);
				}
				break;
			default:
				f.skip(frameSize);
				break;
			}
		}
		return found;
	}

	private void normalizeTrackAndYear() {
		//track numbers are frequently stored as "track/total" (e.g., "3/12"), and dates are
		//frequently stored as "yyyy-MM-dd", but only the leading integers are used
		if (track != null) {
			final int slash = track.indexOf('/');
			if (slash >= 0)
				track = track.substring(0, slash).trim();
			if (track.length() == 0)
				track = null;
		}
		if (year != null && year.length() > 4)
			year = year.substring(0, 4);
	}

	public void extract(FileSt file) {
		hasData = false;
		title = null;
		artist = null;
		album = null;
		track = null;
		year = null;
		length = null;
		sampleRate = 0;
		channels = 0;
		//the only formats supported for now...
		boolean aac = false, flac = false, ogg = false;
		if (!file.path.regionMatches(true, file.path.length() - 4, ".mp3", 0, 4) &&
			!(aac = file.path.regionMatches(true, file.path.length() - 4, ".aac", 0, 4)) &&
			!file.path.regionMatches(true, file.path.length() - 4, ".wav", 0, 4) &&
			!(flac = file.path.regionMatches(true, file.path.length() - 5, ".flac", 0, 5)) &&
			!(ogg = file.path.regionMatches(true, file.path.length() - 4, ".ogg", 0, 4)))
			return;
		FileInputStream fileInputStream = null;
		PrimitiveBufferedInputStream bufferedInputStream = null;
		try {
			fileInputStream = ((file.file != null) ? new FileInputStream(file.file) : new FileInputStream(file.path));
			bufferedInputStream = (ogg ? new OggPrimitiveBufferedInputStream(fileInputStream, 32768, fileInputStream.available()) : new PrimitiveBufferedInputStream(fileInputStream, 32768, fileInputStream.available()));
			if (flac)
				FLACMetadataExtractor.extract(this, bufferedInputStream, tmpPtr);
			else if (ogg)
				OggMetadataExtractor.extract(this, (OggPrimitiveBufferedInputStream)bufferedInputStream, tmpPtr);
			else
				extractID3v2Andv1(bufferedInputStream, fileInputStream, aac, true);
			normalizeTrackAndYear();
		} catch (Throwable ex) {
			hasData = false;
			ex.printStackTrace();
		} finally {
			if (bufferedInputStream != null) {
				try {
					bufferedInputStream.close();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
			if (fileInputStream != null) {
				try {
					fileInputStream.close();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public void extract(InputStream inputStream, int totalLength) {
		hasData = false;
		title = null;
		artist = null;
		album = null;
		track = null;
		year = null;
		length = null;
		PrimitiveBufferedInputStream bufferedInputStream = null;
		try {
			bufferedInputStream = new PrimitiveBufferedInputStream(inputStream, 32768, totalLength);
			extractID3v2Andv1(bufferedInputStream, null, false, false);
			normalizeTrackAndYear();
		} catch (Throwable ex) {
			hasData = false;
			ex.printStackTrace();
		} finally {
			if (bufferedInputStream != null) {
				try {
					bufferedInputStream.close();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public void destroy() {
		tmpPtr = null;
	}
}
