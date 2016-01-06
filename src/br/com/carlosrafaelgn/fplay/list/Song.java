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
package br.com.carlosrafaelgn.fplay.list;

import android.media.MediaMetadataRetriever;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import br.com.carlosrafaelgn.fplay.playback.MetadataExtractor;
import br.com.carlosrafaelgn.fplay.util.Serializer;

public final class Song extends BaseItem {
	public static final int EXTRA_ARTIST = 0;
	public static final int EXTRA_ALBUM = 1;
	public static final int EXTRA_TRACK_ARTIST = 2;
	public static final int EXTRA_TRACK_ALBUM = 3;
	public static final int EXTRA_TRACK_ARTIST_ALBUM = 4;
	public static final int EXTRA_ARTIST_ALBUM = 5;
	public static int extraInfoMode;
	public final String path;
	public final boolean isHttp;
	public String title, artist, album, extraInfo;
	public int track, lengthMS, year;
	public String length;
	public boolean alreadyPlayed, selected;

	public Song(String path, String title, String artist, String album, int track, int lengthMS, int year) {
		this.path = path;
		this.isHttp = (path.startsWith("http://") || path.startsWith("https://"));
		this.title = title;
		this.artist = artist;
		this.album = album;
		this.track = track;
		this.lengthMS = lengthMS;
		this.year = year;
		validateFields(null);
	}
	
	public Song(String url, String title) {
		this.path = url.trim();
		this.isHttp = true;
		this.title = title.trim();
		validateFields(null);
	}
	
	public Song(FileSt fileSt, byte[][] tmpPtr) {
		this.path = fileSt.path;
		this.isHttp = false;
		
		//MediaMetadataRetriever simply returns null for all keys, except METADATA_KEY_DURATION,
		//on several devices, even though the file has the metadata... :(
		//So, trust our MetadataExtractor, and only call MediaMetadataRetriever for unsupported file types
		
		final String[] fields = MetadataExtractor.extract(fileSt, tmpPtr);
		
		if (fields != null) {
			this.title = fields[MetadataExtractor.TITLE];
			this.artist = fields[MetadataExtractor.ARTIST];
			this.album = fields[MetadataExtractor.ALBUM];
			if (fields[MetadataExtractor.TRACK] != null) {
				try {
					this.track = Integer.parseInt(fields[MetadataExtractor.TRACK]);
				} catch (Throwable ex) {
					this.track = 0;
				}
			}
			if (fields[MetadataExtractor.YEAR] != null) {
				try {
					this.year = Integer.parseInt(fields[MetadataExtractor.YEAR]);
				} catch (Throwable ex) {
					this.year = 0;
				}
			}
			if (fields[MetadataExtractor.LENGTH] != null) {
				try {
					this.lengthMS = Integer.parseInt(fields[MetadataExtractor.LENGTH]);
				} catch (Throwable ex) {
					this.lengthMS = 0;
				}
			}
		}
		if (fields == null || this.lengthMS <= 0) {
			final MediaMetadataRetriever retr = new MediaMetadataRetriever();
			try {
				retr.setDataSource(fileSt.path);
				String s;
				if (fields == null) {
					try {
						this.title = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
					} catch (Throwable ex) {
						this.title = null;
					}
					try {
						this.artist = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
					} catch (Throwable ex) {
						this.artist = null;
					}
					try {
						this.album = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
					} catch (Throwable ex) {
						this.album = null;
					}
					try {
						s = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
						if (s != null && s.length() > 0)
							this.track = Integer.parseInt(s);
					} catch (Throwable ex) {
						this.track = 0;
					}
					try {
						s = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
						if (s != null && s.length() > 0)
							this.year = Integer.parseInt(s);
					} catch (Throwable ex) {
						this.year = 0;
					}
				}
				try {
					s = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
					if (s != null && s.length() > 0)
						this.lengthMS = Integer.parseInt(s);
				} catch (Throwable ex) {
					this.lengthMS = 0;
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			retr.release();
		}
		validateFields(fileSt.name);
	}
	
	private void validateFields(String fileName) {
		if (title == null || title.length() == 0) {
			if (fileName != null) {
				final int i = fileName.lastIndexOf('.');
				title = ((i > 0) ? fileName.substring(0, i) : fileName);
			}
		}
		if (title == null) {
			title = "-";
		} else {
			if (fileName != null)
				title = title.trim();
			if (title.length() == 0)
				title = "-";
		}
		if (artist == null) {
			artist = "-";
		} else {
			if (fileName != null)
				artist = artist.trim();
			if (artist.length() == 0)
				artist = "-";
		}
		if (album == null) {
			album = "-";
		} else {
			if (fileName != null)
				album = album.trim();
			if (album.length() == 0)
				album = "-";
		}
		updateExtraInfo();
		if (track <= 0)
			track = -1;
		if (lengthMS <= 0)
			lengthMS = -1;
		if (year <= 0)
			year = -1;
		length = (isHttp ? "" : formatTime(lengthMS));
	}
	
	@Override
	public String toString() {
		return title;
	}

	public String getHumanReadablePath() {
		final int i = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR);
		return ((i <= 0) ? path : path.substring(0, i));
	}

	public void updateExtraInfo() {
		if (isHttp) {
			extraInfo = "";
		} else {
			switch (extraInfoMode) {
			case EXTRA_ARTIST:
				extraInfo = artist;
				break;
			case EXTRA_ALBUM:
				extraInfo = album;
				break;
			case EXTRA_TRACK_ARTIST:
				extraInfo = ((track > 0) ? (track + " / " + artist) : artist);
				break;
			case EXTRA_TRACK_ALBUM:
				extraInfo = ((track > 0) ? (track + " / " + album) : album);
				break;
			case EXTRA_TRACK_ARTIST_ALBUM:
				extraInfo = ((track > 0) ? (track + " / " + artist + " / " + album) : (artist + " / " + album));
				break;
			default:
				extraInfo = artist + " / " + album;
				break;
			}
		}
	}
	
	public void serialize(OutputStream os) throws IOException {
		//NEVER change this order! (changing will destroy existing lists)
		Serializer.serializeString(os, path);
		Serializer.serializeString(os, title);
		Serializer.serializeString(os, artist);
		Serializer.serializeString(os, album);
		Serializer.serializeInt(os, track);
		Serializer.serializeInt(os, lengthMS);
		Serializer.serializeInt(os, year);
		Serializer.serializeInt(os, 0); //flags
	}
	
	public static Song deserialize(InputStream is) throws IOException {
		String path, title, artist, album;
		int track, lengthMS, year;
		//NEVER change this order! (changing will destroy existing lists)
		path = Serializer.deserializeString(is);
		title = Serializer.deserializeString(is);
		artist = Serializer.deserializeString(is);
		album = Serializer.deserializeString(is);
		track = Serializer.deserializeInt(is);
		lengthMS = Serializer.deserializeInt(is);
		year = Serializer.deserializeInt(is);
		Serializer.deserializeInt(is); //flags
		return new Song(path, title, artist, album, track, lengthMS, year);
	}
	
	public static String formatTime(int timeMS) {
		final StringBuilder sb = new StringBuilder(8);
		formatTime(timeMS, sb);
		return sb.toString();
	}
	
	public static void formatTime(int timeMS, StringBuilder buf) {
		buf.delete(0, buf.length());
		if (timeMS < 0) {
			buf.append('-');
			return;
		}
		int timeS = timeMS / 1000;
		buf.append(timeS / 60);
		buf.append(':');
		timeS %= 60;
		if (timeS < 10) buf.append('0');
		buf.append(timeS);
	}
	
	public static void formatTimeSec(int timeS, StringBuilder buf) {
		buf.delete(0, buf.length());
		if (timeS < 0) {
			buf.append('-');
			return;
		}
		buf.append(timeS / 60);
		buf.append(':');
		timeS %= 60;
		if (timeS < 10) buf.append('0');
		buf.append(timeS);
	}
}
