//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013, Carlos Rafael Gimenes das Neves
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.media.MediaMetadataRetriever;
import br.com.carlosrafaelgn.fplay.playback.MetadataExtractor;
import br.com.carlosrafaelgn.fplay.util.Serializer;

public final class Song extends BaseItem {
	public final String path;
	public final boolean isHttp;
	public String title, artist, album;
	public int track, lengthMS;
	public String length;
	public Song possibleNextSong;
	public boolean alreadyPlayed, selected;
	
	private Song(String path, String title, String artist, String album, int track, int lengthMS) {
		this.path = path;
		this.isHttp = (path.startsWith("http://") || path.startsWith("https://"));
		this.title = title;
		this.artist = artist;
		this.album = album;
		this.track = track;
		this.lengthMS = lengthMS;
		validateFields(null);
	}
	
	public Song(String url, String title) {
		this.path = url;
		this.isHttp = true;
		this.title = title;
		validateFields(null);
	}
	
	public Song(FileSt fileSt, byte[][] tmpPtr) {
		this.path = fileSt.path;
		this.isHttp = false;
		
		//MediaMetadataRetriever simply returns null for all keys, except METADATA_KEY_DURATION,
		//on several devices, even though the file has the metadata... :(
		//So, trust our MetadataExtractor, and only call MediaMetadataRetriever afterwards
		
		final String[] fields = MetadataExtractor.extract(fileSt, tmpPtr);
		
		final MediaMetadataRetriever retr = new MediaMetadataRetriever();
		try {
			retr.setDataSource(fileSt.path);
			String s;
			try {
				this.title = ((fields != null && fields[MetadataExtractor.TITLE] != null) ? fields[MetadataExtractor.TITLE] : retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
			} catch (Throwable ex) { }
			try {
				this.artist = ((fields != null && fields[MetadataExtractor.ARTIST] != null) ? fields[MetadataExtractor.ARTIST] : retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
			} catch (Throwable ex) { }
			try {
				this.album = ((fields != null && fields[MetadataExtractor.ALBUM] != null) ? fields[MetadataExtractor.ALBUM] : retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
			} catch (Throwable ex) { }
			try {
				s = ((fields != null && fields[MetadataExtractor.TRACK] != null) ? fields[MetadataExtractor.TRACK] : retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
				if (s != null && s.length() > 0)
					this.track = Integer.parseInt(s);
			} catch (Throwable ex) { }
			try {
				s = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
				if (s != null && s.length() > 0)
					this.lengthMS = Integer.parseInt(s);
			} catch (Throwable ex) { }
		} catch (Throwable ex) { }
		retr.release();
		validateFields(fileSt.name);
	}
	
	private void validateFields(String fileName) {
		if (this.title == null || this.title.length() == 0) {
			if (fileName != null) {
				final int i = fileName.lastIndexOf('.');
				this.title = ((i > 0) ? fileName.substring(0, i) : fileName);
			}
		}
		if (this.title == null || this.title.length() == 0)
			this.title = "-";
		if (this.artist == null || this.artist.length() == 0)
			this.artist = "-";
		if (this.album == null || this.album.length() == 0)
			this.album = "-";
		if (this.track <= 0)
			this.track = -1;
		if (this.lengthMS <= 0)
			this.lengthMS = -1;
		this.length = formatTime(this.lengthMS);
	}
	
	@Override
	public String toString() {
		return title;
	}
	
	public void serialize(OutputStream os) throws IOException {
		Serializer.serializeString(os, path);
		Serializer.serializeString(os, title);
		Serializer.serializeString(os, artist);
		Serializer.serializeString(os, album);
		Serializer.serializeInt(os, track);
		Serializer.serializeInt(os, lengthMS);
		Serializer.serializeInt(os, 0); //flags
		Serializer.serializeInt(os, 0); //flags
	}
	
	public static Song deserialize(InputStream is) throws IOException {
		String path, title, artist, album;
		int track, lengthMS;
		path = Serializer.deserializeString(is);
		title = Serializer.deserializeString(is);
		artist = Serializer.deserializeString(is);
		album = Serializer.deserializeString(is);
		track = Serializer.deserializeInt(is);
		lengthMS = Serializer.deserializeInt(is);
		Serializer.deserializeInt(is); //flags
		Serializer.deserializeInt(is); //flags
		return new Song(path, title, artist, album, track, lengthMS);
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
		buf.append('\'');
		timeS %= 60;
		if (timeS < 10) buf.append('0');
		buf.append(timeS);
		buf.append('\"');
	}
	
	public static void formatTimeSec(int timeS, StringBuilder buf) {
		buf.delete(0, buf.length());
		if (timeS < 0) {
			buf.append('-');
			return;
		}
		buf.append(timeS / 60);
		buf.append('\'');
		timeS %= 60;
		if (timeS < 10) buf.append('0');
		buf.append(timeS);
		buf.append('\"');
	}
}
