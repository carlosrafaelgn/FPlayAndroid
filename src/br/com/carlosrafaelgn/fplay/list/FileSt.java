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

import android.support.annotation.NonNull;

import java.io.File;

import br.com.carlosrafaelgn.fplay.plugin.SongInfo;

public final class FileSt extends BaseItem {
	public static final String ARTIST_ROOT = "@";
	public static final char ARTIST_ROOT_CHAR = '@';
	public static final String ALBUM_ROOT = "!";
	public static final char ALBUM_ROOT_CHAR = '!';
	public static final String FPLAY_REMOTE_LIST_ROOT = "f";
	public static final char FPLAY_REMOTE_LIST_ROOT_CHAR = 'f';
	public static final String FAKE_PATH_ROOT = "*";
	public static final char FAKE_PATH_ROOT_CHAR = '*';
	public static final String FAKE_PATH_SEPARATOR = "\u001A"; //Substitute!!! Old school techniques :D
	public static final String ARTIST_PREFIX = ARTIST_ROOT + FAKE_PATH_ROOT;
	public static final String ALBUM_PREFIX = ALBUM_ROOT + FAKE_PATH_ROOT;
	public static final String FPLAY_REMOTE_LIST_PREFIX = FPLAY_REMOTE_LIST_ROOT + FAKE_PATH_ROOT;
	public static final char FAKE_PATH_SEPARATOR_CHAR = '\u001A';
	public static final char PRIVATE_FILETYPE_ID = '#';
	public static final String FILETYPE_PLAYLIST = "#lst";
	public static final String FILETYPE_PRESET = "#pset";
	public static final int TYPE_ALL_FILES = 1;
	public static final int TYPE_INTERNAL_STORAGE = 2;
	public static final int TYPE_EXTERNAL_STORAGE = 3;
	public static final int TYPE_MUSIC = 4;
	public static final int TYPE_DOWNLOADS = 5;
	public static final int TYPE_FAVORITE = 6;
	public static final int TYPE_ARTIST = 7;
	public static final int TYPE_ARTIST_ROOT = 8;
	public static final int TYPE_ALBUM = 9;
	public static final int TYPE_ALBUM_ROOT = 10;
	public static final int TYPE_ALBUM_ITEM = 11;
	public static final int TYPE_EXTERNAL_STORAGE_USB = 12;
	public static final int TYPE_ICECAST = 13;
	public static final int TYPE_SHOUTCAST = 14;
	public static final int TYPE_FPLAY_REMOTE_LIST = 15;
	public final boolean isDirectory;
	public final String path, name;
	public Long albumId;
	public int specialType, tracks, albums;
	public long artistIdForAlbumArt; //when dealing with playlists, artistIdForAlbumArt stores the playlist id
	public File file;
	public boolean isChecked;
	public SongInfo songInfo;

	public FileSt(File file) {
		this.isDirectory = file.isDirectory();
		this.path = file.getAbsolutePath();
		//int i = path.lastIndexOf('/'), e;
		//String t = path;
		//if (i >= 0) {
		//	e = path.lastIndexOf('.');
		//	t = ((e <= i) ? path.substring(i + 1) : path.substring(i + 1, e));
		//}
		//this.title = t;
		this.name = file.getName();
		this.file = file; //keep this here for MetadataExtractor...
	}

	public FileSt(SongInfo songInfo) {
		this.isDirectory = false;
		this.path = songInfo.path;
		this.name = songInfo.title;
		this.songInfo = songInfo;
	}

	public FileSt(String absolutePath, String name, Long albumId) {
		this.isDirectory = true;
		this.path = absolutePath;
		this.name = ((name.length() == 0) ? " " : name);
		this.albumId = albumId;
		this.specialType = FileSt.TYPE_ALBUM;
	}

	public FileSt(String absolutePath, String name, int specialType) {
		this.isDirectory = (specialType != 0);
		this.path = absolutePath;
		this.name = ((name.length() == 0) ? " " : name);
		this.specialType = specialType;
	}

	public FileSt(String absolutePath, String name) {
		this.isDirectory = false;
		this.path = absolutePath;
		this.name = ((name.length() == 0) ? " " : name);
	}

	@NonNull
	@Override
	public String toString() {
		return name;
	}
	
	public static boolean isValidPrivateFileName(String name) {
		if ((name != null) && (name.length() != 0) && !name.endsWith(FILETYPE_PLAYLIST) && !name.endsWith(FILETYPE_PRESET)) {
			for (int i = name.length() - 1; i >= 0; i--) {
				switch (name.charAt(i)) {
				case '/':
				case '*':
				case '\"':
				case ':':
				case '?':
				case '\\':
				case '|':
				case '<':
				case '>':
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public boolean isPrivatePlaylist() {
		return ((path != null) && path.endsWith(FILETYPE_PLAYLIST));
	}
}
