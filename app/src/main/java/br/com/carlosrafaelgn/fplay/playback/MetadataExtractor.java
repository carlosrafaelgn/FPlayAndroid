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

	private String readInfoStr(PrimitiveBufferedInputStream f, int actualStrLen) throws IOException {
		actualStrLen = f.read(tmpPtr[0], 0, actualStrLen);
		return finishReadingV2Frame(0, actualStrLen);
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

		_mainLoop:
		while (bytesLeftInFile > 0) {
			boolean isWave = false;
			int bytesLeftInChunk = 0, tmpChunkLength;

			switch (f.readUInt32BE()) {
			case -1:
				break _mainLoop;

			case 0x57415645: // WAVE
				isWave = true;
				bytesLeftInFile -= 4;
				break;

			case 0x4c495354: // LIST
				tmpChunkLength = f.readUInt32LE();
				if (tmpChunkLength == -1)
					break _mainLoop;

				bytesLeftInFile -= 8;
				bytesLeftInChunk = tmpChunkLength;

				if (bytesLeftInChunk < 0 || bytesLeftInChunk > bytesLeftInFile)
					break _mainLoop;
				break;

			case 0x69643320: // id3
				tmpChunkLength = f.readUInt32LE();
				if (tmpChunkLength == -1)
					break _mainLoop;

				bytesLeftInFile -= 8;

				if (tmpChunkLength < 0 || tmpChunkLength > bytesLeftInFile)
					break _mainLoop;

				if (tmpChunkLength > 4) {
					final int b0 = f.read(),
						b1 = f.read(),
						b2 = f.read();

					if (b0 == -1 || b1 == -1 || b2 == -1)
						break _mainLoop;

					bytesLeftInFile -= 3;
					tmpChunkLength -= 3;

					if (b0 == 0x49 || b1 == 0x44 || b2 == 0x33) {
						id3Position = f.totalLength - bytesLeftInFile;
						if (fmtOk && dataOk) {
							id3Position = -1;
							break _mainLoop;
						}
					}
				}

				f.skip(tmpChunkLength);

				continue;

			default:
				tmpChunkLength = f.readUInt32LE();
				if (tmpChunkLength == -1)
					break _mainLoop;

				bytesLeftInFile -= 8;

				if (tmpChunkLength < 0 || tmpChunkLength > bytesLeftInFile)
					break _mainLoop;

				f.skip(tmpChunkLength);

				continue;
			}

			while ((isWave && (!fmtOk || !dataOk)) || bytesLeftInChunk > 0) {
				final int subChunk = f.readUInt32BE();
				int subChunkLength = 0;

				if (subChunk == -1)
					break _mainLoop;

				bytesLeftInFile -= 4;
				bytesLeftInChunk -= 4;

				if (subChunk != 0x494e464f) { // INFO
					subChunkLength = f.readUInt32LE();

					if (subChunkLength < 0 ||
						subChunkLength > bytesLeftInFile ||
						(!isWave && subChunkLength > bytesLeftInChunk))
						break _mainLoop;

					bytesLeftInFile -= 4;
					bytesLeftInChunk -= 4;
				}

				switch (subChunk) {
				case 0x666d7420: // fmt
					if (!isWave)
						break;

					// https://docs.microsoft.com/en-us/windows/win32/api/mmeapi/ns-mmeapi-waveformatex
					// https://docs.microsoft.com/en-us/windows/win32/directshow/audio-subtypes
					final int wFormatTag = f.readUInt16LE(),
						nChannels = f.readUInt16LE(),
						nSamplesPerSec = f.readUInt32LE(),
						nAvgBytesPerSec = f.readUInt32LE(),
						nBlockAlign = f.readUInt16LE(),
						wBitsPerSample = f.readUInt16LE();

					subChunkLength -= 16;
					bytesLeftInFile -= 16;
					bytesLeftInChunk -= 16;

					if (wFormatTag == -1 ||
						nChannels == -1 ||
						nSamplesPerSec == -1 ||
						nAvgBytesPerSec == -1 ||
						nBlockAlign == -1 ||
						wBitsPerSample == -1)
						break _mainLoop;

					// We are ignoring compressed files...
					if (wFormatTag != 1 && // WAVE_FORMAT_PCM
						wFormatTag != 3) // WAVE_FORMAT_IEEE_FLOAT
						break;

					fmtOk = true;

					if (avgBytesPerSec == 0)
						avgBytesPerSec = nAvgBytesPerSec;

					sampleRate = nSamplesPerSec;
					channels = nChannels;
					break;

				case 0x64617461: // data
					if (isWave) {
						dataOk = true;

						if (dataLength == 0)
							dataLength = subChunkLength;
					}
					break;

				case 0x494e464f: // INFO
					// https://www.robotplanet.dk/audio/wav_meta_data/
					// https://www.robotplanet.dk/audio/wav_meta_data/riff_mci.pdf
					while (bytesLeftInChunk > 0) {
						if (bytesLeftInChunk < 4) {
							f.skip(bytesLeftInChunk);
							break;
						}

						final int metadataId = f.readUInt32BE();
						bytesLeftInFile -= 4;
						bytesLeftInChunk -= 4;

						if (metadataId == -1)
							break _mainLoop;

						int nullTerminatedStrLen = f.readUInt32LE();
						bytesLeftInFile -= 4;
						bytesLeftInChunk -= 4;

						if (nullTerminatedStrLen < 0 || nullTerminatedStrLen > bytesLeftInChunk) {
							f.skip(bytesLeftInChunk);
							bytesLeftInFile -= bytesLeftInChunk;
							bytesLeftInChunk = 0;
							continue;
						}

						if (nullTerminatedStrLen <= 1) {
							f.skip(nullTerminatedStrLen);
							bytesLeftInFile -= nullTerminatedStrLen;
							bytesLeftInChunk -= nullTerminatedStrLen;
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
							hasData = true;
							track = readInfoStr(f, actualStrLen);
							break;
						default:
							actualStrLen = 0;
							break;
						}

						f.skip(nullTerminatedStrLen - actualStrLen);
						bytesLeftInFile -= nullTerminatedStrLen;
						bytesLeftInChunk -= nullTerminatedStrLen;
					}
					break;
				}

				if (subChunkLength > 0) {
					f.skip(subChunkLength);
					bytesLeftInFile -= subChunkLength;
					bytesLeftInChunk -= subChunkLength;
				}
			}
		}

		if (fmtOk && dataOk) {
			if (id3Position > 0)
				f.seekTo(id3Position);
			length = Integer.toString((int)Math.floor(dataLength * 1000.0 / avgBytesPerSec));
			return (id3Position != 0);
		}

		return false;
	}

	private String finishReadingV2Frame(int encoding, int frameSize) throws IOException {
		byte[] tmp = tmpPtr[0];
		int offsetStart = 0, offsetEnd = frameSize - 1;
		TrimStart:
		while (frameSize > 0) {
			switch (tmp[offsetStart]) {
			case 0x00:
			case 0x09:
			case 0x0A:
			case 0x0B:
			case 0x0C:
			case 0x0D:
			case 0x20:
			case (byte)0x85:
			case (byte)0xA0:
				frameSize--;
				offsetStart++;
				break;
			default:
				break TrimStart;
			}
		}
		TrimEnd:
		while (frameSize > 0) {
			switch (tmp[offsetEnd]) {
			case 0x00:
			case 0x09:
			case 0x0A:
			case 0x0B:
			case 0x0C:
			case 0x0D:
			case 0x20:
			case (byte)0x85:
			case (byte)0xA0:
				frameSize--;
				offsetEnd--;
				break;
			default:
				break TrimEnd;
			}
		}
		//according to http://developer.android.com/reference/java/nio/charset/Charset.html
		//the following charsets are ALWAYS available:
		//ISO-8859-1
		//US-ASCII
		//UTF-16
		//UTF-16BE
		//UTF-16LE
		//UTF-8
		String ret = null;
		switch (encoding) {
		case 0: //ISO-8859-1
			ret = new String(tmp, offsetStart, frameSize, "ISO-8859-1");
			break;
		case 1: //UCS-2 (UTF-16 encoded Unicode with BOM), in ID3v2.2 and ID3v2.3
		case 2: //UTF-16BE encoded Unicode without BOM, in ID3v2.4
			//restore the extra 0 removed from the end
			if ((frameSize & 1) != 0 && (offsetStart + frameSize) < tmp.length)
				frameSize++;
			ret = new String(tmp, offsetStart, frameSize, (encoding == 1 ? "UTF-16" : "UTF-16BE"));
			break;
		case 3: //UTF-8 encoded Unicode, in ID3v2.4
			//BOM
			ret = ((tmp[0] == (byte)0xef && tmp[1] == (byte)0xbb && tmp[2] == (byte)0xbf) ?
				new String(tmp, offsetStart + 3, frameSize - 3, "UTF-8") :
				new String(tmp, offsetStart, frameSize, "UTF-8"));
			break;
		}
		return ((ret != null && ret.length() == 0) ? null : ret);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private String readV2Frame(PrimitiveBufferedInputStream f, int frameSize) throws IOException {
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
		return finishReadingV2Frame(encoding, frameSize);
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
			int i, c;
			if ((found & TITLE_B) == 0) {
				i = 3;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0) {
					title = new String(tmp, 3, c, "ISO-8859-1");
					hasData = true;
				}
			}
			if ((found & ARTIST_B) == 0) {
				i = 3 + 30;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0) {
					artist = new String(tmp, 3 + 30, c, "ISO-8859-1");
					hasData = true;
				}
			}
			if ((found & ALBUM_B) == 0) {
				i = 3 + 30 + 30;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0) {
					album = new String(tmp, 3 + 30 + 30, c, "ISO-8859-1");
					hasData = true;
				}
			}
			if ((found & YEAR_B) == 0) {
				i = 3 + 30 + 30 + 30;
				c = 0;
				while (c < 4 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0) {
					year = new String(tmp, 3 + 30 + 30 + 30, c, "ISO-8859-1");
					hasData = true;
				}
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
		final int hdrRevLo = f.read();
		final int hdrRevHi = f.read();
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

		if ((hdrRevLo & 0xff) > 2 || hdrRevHi != 0) { //only rev 3 or greater supported
			//http://id3.org/id3v2.3.0
			//http://id3.org/id3v2.4.0-structure
			//http://id3.org/id3v2.4.0-frames
			while (size > 0 && found != ALL_B) {
				//struct _ID3v2FrameHdr {
				//public:
				//	unsigned int id;
				//	unsigned int size;
				//	unsigned short flags;
				//} frame;
				final int frameId = f.readUInt32BE();
				final int frameSize = f.readUInt32BE();
				//skip the flags
				f.skip(2);
				if (frameId == 0 || frameSize <= 0 || frameSize > size)
					break;
				switch (frameId) {
				case 0x54495432: //title - TIT2
					if ((found & TITLE_B) == 0) {
						if ((title = readV2Frame(f, frameSize)) != null)
							found |= TITLE_B;
					} else {
						f.skip(frameSize);
					}
					break;
				case 0x54504531: //artist - TPE1
					if ((found & ARTIST_B) == 0) {
						if ((artist = readV2Frame(f, frameSize)) != null)
							found |= ARTIST_B;
					} else {
						f.skip(frameSize);
					}
					break;
				case 0x54414c42: //album - TALB
					if ((found & ALBUM_B) == 0) {
						if ((album = readV2Frame(f, frameSize)) != null)
							found |= ALBUM_B;
					} else {
						f.skip(frameSize);
					}
					break;
				case 0x5452434b: //track - TRCK
					if ((found & TRACK_B) == 0) {
						if ((track = readV2Frame(f, frameSize)) != null)
							found |= TRACK_B;
					} else {
						f.skip(frameSize);
					}
					break;
				case 0x54594552: //year - TYER
					if ((found & YEAR_B) == 0) {
						if ((year = readV2Frame(f, frameSize)) != null)
							found |= YEAR_B;
					} else {
						f.skip(frameSize);
					}
					break;
				case 0x54445243: //Recording time - TDRC
					if ((found & YEAR_B) == 0) {
						if ((year = readV2Frame(f, frameSize)) != null) {
							if (year.length() > 4)
								year = year.substring(0, 4);
							found |= YEAR_B;
						}
					} else {
						f.skip(frameSize);
					}
					break;
				case 0x544c454e: //length - TLEN
					if ((found & LENGTH_B) == 0) {
						if ((length = readV2Frame(f, frameSize)) != null)
							found |= LENGTH_B;
					} else {
						f.skip(frameSize);
					}
					break;
				default:
					f.skip(frameSize);
					break;
				}
				size -= (10 + frameSize);
			}
			//try to extract ID3v1 only if there are any blank fields
			hasData |= (found != 0);

			if (fetchSampleRateAndChannels)
				extractSampleRateAndChannels(f, id3TotalLength, aac);

			if ((found & ALL_BUT_LENGTH_B) != ALL_BUT_LENGTH_B && fileInputStream != null)
				extractID3v1(fileInputStream, found);
		}
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
