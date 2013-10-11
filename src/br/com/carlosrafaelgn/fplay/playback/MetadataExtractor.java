package br.com.carlosrafaelgn.fplay.playback;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.list.FileSt;

public final class MetadataExtractor {
	public static final int TITLE = 0;
	public static final int ARTIST = 1;
	public static final int ALBUM = 2;
	public static final int TRACK = 3;
	
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
			ret = ((tmp[0] == 0xEF && tmp[1] == 0xBB && tmp[2] == 0xBF) ?
					new String(tmp, 3, frameSize - 3, "UTF-8") :
					new String(tmp, 0, frameSize, "UTF-8"));
			break;
		}
		return ((ret != null && ret.length() == 0) ? null : ret);
	}
	
	private static String[] extractID3v1(RandomAccessFile f, String[] fields, byte[] tmp) {
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
			if (fields[MetadataExtractor.TITLE] == null) {
				i = 3;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0)
					fields[MetadataExtractor.TITLE] = new String(tmp, 3, c, "ISO-8859-1");
			}
			if (fields[MetadataExtractor.ARTIST] == null) {
				i = 3 + 30;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0)
					fields[MetadataExtractor.ARTIST] = new String(tmp, 3 + 30, c, "ISO-8859-1");
			}
			if (fields[MetadataExtractor.ALBUM] == null) {
				i = 3 + 30 + 30;
				c = 0;
				while (c < 30 && tmp[i] != 0) {
					c++;
					i++;
				}
				if (c != 0)
					fields[MetadataExtractor.ALBUM] = new String(tmp, 3 + 30 + 30, c, "ISO-8859-1");
			}
			if (fields[MetadataExtractor.TRACK] == null && tmp[128 - 3] == 0 && tmp[128 - 2] != 0)
				fields[MetadataExtractor.TRACK] = Integer.toString((int)tmp[128 - 2] & 0xFF);
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
		if ((hdr & 0xFFFFFF00) != 0x49443300) //ID3
			return null;
		final int hdrRev = f.read();
		final int flags = f.read();
		final int sizeBytes0 = f.read();
		final int sizeBytes1 = f.read();
		final int sizeBytes2 = f.read();
		final int sizeBytes3 = f.read();
		int size = ((flags & 0x10) != 0 ? 10 : 0) + //footer presence flag
		(
			(sizeBytes3 & 0x7F) |
			((sizeBytes2 & 0x7F) << 7) |
			((sizeBytes1 & 0x7F) << 14) |
			((sizeBytes0 & 0x7F) << 21)
		);
		if ((hdr & 0xFF) > 2 || hdrRev != 0) { //only rev 3 or greater supported
			//http://id3.org/id3v2.3.0
			//http://id3.org/id3v2.4.0-structure
			final String[] fields = new String[4];
			int found = 0;
			while (size > 0 && found != 0x0F) {
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
					if (fields[MetadataExtractor.TITLE] == null) {
						fields[MetadataExtractor.TITLE] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[MetadataExtractor.TITLE] != null)
							found |= 0x01;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x54504531: //artist - TPE1
					if (fields[MetadataExtractor.ARTIST] == null) {
						fields[MetadataExtractor.ARTIST] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[MetadataExtractor.ARTIST] != null)
							found |= 0x02;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x54414C42: //album - TALB
					if (fields[MetadataExtractor.ALBUM] == null) {
						fields[MetadataExtractor.ALBUM] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[MetadataExtractor.ALBUM] != null)
							found |= 0x04;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				case 0x5452434B: //track - TRCK
					if (fields[MetadataExtractor.TRACK] == null) {
						fields[MetadataExtractor.TRACK] = readV2Frame(f, frameSize, tmpPtr);
						if (fields[MetadataExtractor.TRACK] != null)
							found |= 0x08;
					} else {
						f.skipBytes(frameSize);
					}
					break;
				//case 0x54594552: //year - TYER
				//	break;
				default:
					f.skipBytes(frameSize);
					break;
				}
				size -= (10 + frameSize);
			}
			//try to extract ID3v1 only if there are any blank fields
			return ((found != 0x0F) ? extractID3v1(f, fields, tmpPtr[0]) : fields);
		}
		return null;
	}
	
	public static String[] extract(FileSt file, byte[][] tmpPtr) {
		int i = file.path.lastIndexOf('.');
		if (i < 0)
			return null;
		final String ext = file.path.substring(i + 1).toLowerCase(Locale.ENGLISH);
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
