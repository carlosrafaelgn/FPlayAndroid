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
import java.io.RandomAccessFile;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.list.FileSt;

public final class MetadataExtractor {
	private static final int FIELD_COUNT = 6;
	public static final int TITLE = 0;
	public static final int ARTIST = 1;
	public static final int ALBUM = 2;
	public static final int TRACK = 3;
	public static final int YEAR = 4;
	public static final int LENGTH = 5;
	
	private static final int ALL_B = 0x3f;
	private static final int ALL_BUT_LENGTH_B = 0x1f;
	private static final int TITLE_B = 0x01;
	private static final int ARTIST_B = 0x02;
	private static final int ALBUM_B = 0x04;
	private static final int TRACK_B = 0x08;
	private static final int YEAR_B = 0x10;
	private static final int LENGTH_B = 0x20;
	
	private static String readV2Frame(RandomAccessFile f, int frameSize, byte[][] tmpPtr) throws IOException {
		if (frameSize < 2) {
			f.skipBytes(frameSize);
			return null;
		}
		final int encoding = f.read();
		frameSize--; //discount the encoding
		if (encoding < 0 || encoding > 3) {
			f.skipBytes(frameSize);
			return null;
		}
		byte[] tmp = tmpPtr[0];
		if (frameSize > tmp.length) {
			tmp = new byte[frameSize + 16];
			tmpPtr[0] = tmp;
		}
		f.readFully(tmp, 0, frameSize);
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
			ret = new String(tmp, 0, frameSize, "ISO-8859-1");
			break;
		case 1: //UCS-2 (UTF-16 encoded Unicode with BOM), in ID3v2.2 and ID3v2.3
		case 2: //UTF-16BE encoded Unicode without BOM, in ID3v2.4
			ret = new String(tmp, 0, frameSize, "UTF-16");
			break;
		case 3: //UTF-8 encoded Unicode, in ID3v2.4
			//BOM
			ret = ((tmp[0] == 0xef && tmp[1] == 0xbb && tmp[2] == 0xbf) ?
					new String(tmp, 3, frameSize - 3, "UTF-8") :
					new String(tmp, 0, frameSize, "UTF-8"));
			break;
		}
		return ((ret != null && ret.length() == 0) ? null : ret);
	}
	
	private static String[] extractID3v1(RandomAccessFile f, int found, String[] fields, byte[] tmp) {
		try {
			f.seek(f.length() - 128);
			f.readFully(tmp, 0, 128);
			if (tmp[0] != 0x54 ||
				tmp[1] != 0x41 ||
				tmp[2] != 0x47) //TAG
				return fields;
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
				if (c != 0)
					fields[MetadataExtractor.TITLE] = new String(tmp, 3, c, "ISO-8859-1");
			}
			if ((found & ARTIST_B) == 0) {
				i = 3 + 30;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0)
					fields[MetadataExtractor.ARTIST] = new String(tmp, 3 + 30, c, "ISO-8859-1");
			}
			if ((found & ALBUM_B) == 0) {
				i = 3 + 30 + 30;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0)
					fields[MetadataExtractor.ALBUM] = new String(tmp, 3 + 30 + 30, c, "ISO-8859-1");
			}
			if ((found & YEAR_B) == 0) {
				i = 3 + 30 + 30 + 30;
				c = 0;
				while (c < 4 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0)
					fields[MetadataExtractor.YEAR] = new String(tmp, 3 + 30 + 30 + 30, c, "ISO-8859-1");
			}
			if ((found & TRACK_B) == 0 && tmp[128 - 3] == 0 && tmp[128 - 2] != 0)
				fields[MetadataExtractor.TRACK] = Integer.toString((int)tmp[128 - 2] & 0xff);
		} catch (Throwable ex) {
			//ignore all exceptions while reading ID3v1, in favor of
			//everything that has already been read in ID3v2
		}
		return fields;
	}
	
	private static String[] extractID3v2Andv1(RandomAccessFile f, byte[][] tmpPtr) throws IOException  {
		//struct _ID3v2TagHdr {
		//public:
		//	unsigned int hdr;
		//	unsigned char hdrRev;
		//	unsigned char flags;
		//	unsigned char sizeBytes[4];
		//} tagV2Hdr;
		
		//readInt() reads a big-endian 32-bit integer
		final int hdr = f.readInt();
		if ((hdr & 0xffffff00) != 0x49443300) //ID3
			return null;
		final int hdrRev = f.read();
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
		if ((hdr & 0xff) > 2 || hdrRev != 0) { //only rev 3 or greater supported
			//http://id3.org/id3v2.3.0
			//http://id3.org/id3v2.4.0-structure
			//http://id3.org/id3v2.4.0-frames
			final String[] fields = new String[FIELD_COUNT];
			int found = 0;
			while (size > 0 && found != ALL_B) {
				//struct _ID3v2FrameHdr {
				//public:
				//	unsigned int id;
				//	unsigned int size;
				//	unsigned short flags;
				//} frame;
				final int frameId = f.readInt();
				final int frameSize = f.readInt();
				//skip the flags
				f.readShort();
				if (frameId == 0 || frameSize <= 0 || frameSize > size)
					break;
				switch (frameId) {
				case 0x54495432: //title - TIT2
					if ((found & TITLE_B) == 0) {
						fields[TITLE] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[TITLE] != null)
							found |= TITLE_B;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x54504531: //artist - TPE1
					if ((found & ARTIST_B) == 0) {
						fields[ARTIST] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[ARTIST] != null)
							found |= ARTIST_B;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x54414c42: //album - TALB
					if ((found & ALBUM_B) == 0) {
						fields[ALBUM] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[ALBUM] != null)
							found |= ALBUM_B;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x5452434b: //track - TRCK
					if ((found & TRACK_B) == 0) {
						fields[TRACK] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[TRACK] != null)
							found |= TRACK_B;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x54594552: //year - TYER
					if ((found & YEAR_B) == 0) {
						fields[YEAR] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[YEAR] != null)
							found |= YEAR_B;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x54445243: //Recording time - TDRC
					if ((found & YEAR_B) == 0) {
						fields[YEAR] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[YEAR] != null) {
							if (fields[YEAR].length() > 4)
								fields[YEAR] = fields[YEAR].substring(0, 4);
							found |= YEAR_B;
						}
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x544c454e: //length - TLEN
					if ((found & LENGTH_B) == 0) {
						fields[LENGTH] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[LENGTH] != null)
							found |= LENGTH_B;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				default:
					f.skipBytes(frameSize);
					break;
				}
				size -= (10 + frameSize);
			}
			//try to extract ID3v1 only if there are any blank fields
			return (((found & ALL_BUT_LENGTH_B) != ALL_BUT_LENGTH_B) ? extractID3v1(f, found, fields, tmpPtr[0]) : fields);
		}
		return null;
	}
	
	public static String[] extract(FileSt file, byte[][] tmpPtr) {
		int i = file.path.lastIndexOf('.');
		if (i < 0)
			return null;
		final String ext = file.path.substring(i + 1).toLowerCase(Locale.US);
		//the only two formats supported for now... I hope to add ogg soon ;)
		if (!ext.equals("mp3") && !ext.equals("aac"))
			return null;
		RandomAccessFile f = null;
		try {
			f = ((file.file != null) ? new RandomAccessFile(file.file, "r") : new RandomAccessFile(file.path, "r"));
			return extractID3v2Andv1(f, tmpPtr);
		} catch (Throwable ex) {
		} finally {
			if (f != null) {
				try {
					f.close();
				} catch (Throwable ex2) {
				}
			}
		}
		return null;
	}
}
