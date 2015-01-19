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

import android.app.Service;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;

//
//Supported Media Formats
//http://developer.android.com/guide/appendix/media-formats.html
//
public final class FileFetcher implements Runnable, ArraySorter.Comparer<FileSt> {
	public static interface Listener {
		public void onFilesFetched(FileFetcher fetcher, Throwable e);
	}
	
	private static final class RootItem {
		public final String fs_specLC, pathLC, path;
		public final boolean isFileSystemTypeValid;
		
		public RootItem(String fs_spec, String path, boolean isFileSystemTypeValid) {
			this.fs_specLC = fs_spec.toLowerCase(Locale.US);
			this.pathLC = path.toLowerCase(Locale.US);
			this.path = path;
			this.isFileSystemTypeValid = isFileSystemTypeValid;
		}
		
		@Override
		public int hashCode() {
			return fs_specLC.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof String)
				return fs_specLC.equals(o);
			return fs_specLC.equals(((RootItem)o).fs_specLC);
		}
	}
	
	private static final int LIST_DELTA = 32;
	private static final HashSet<String> supportedTypes;
	public String unknownArtist;
	public final String path;
	public FileSt[] files;
	public String[] sections;
	public int[] sectionPositions;
	public int count;
	public final boolean playAfterFetching, /*oldBrowserBehavior,*/ isInTouchMode, createSections;
	private Throwable notifyE;
	private Listener listener;
	private boolean recursive;
	private final boolean notifyFromMain, recursiveIfFirstEmpty;
	private volatile boolean cancelled;
	
	static {
		supportedTypes = new HashSet<String>(15);
		supportedTypes.add(".3gp");
		supportedTypes.add(".3ga");
		supportedTypes.add(".mp4");
		supportedTypes.add(".m4a");
		supportedTypes.add(".aac");
		supportedTypes.add(".mp3");
		supportedTypes.add(".flac");
		supportedTypes.add(".mid");
		supportedTypes.add(".xmf");
		supportedTypes.add(".mxmf");
		supportedTypes.add(".rtttl");
		supportedTypes.add(".rtx");
		supportedTypes.add(".ogg");
		supportedTypes.add(".imy");
		supportedTypes.add(".wav");
		supportedTypes.add(".mkv");
	}
	
	public static boolean isFileSupported(String name) {
		final int i = name.lastIndexOf('.');
		if (i < 0) return false;
		return supportedTypes.contains(name.substring(i).toLowerCase(Locale.US));
	}
	
	public static FileFetcher fetchFiles(String path, Listener listener, boolean notifyFromMain, boolean recursive, boolean isInTouchMode, boolean createSections) {
		FileFetcher f = new FileFetcher(path, listener, notifyFromMain, recursive, false, false, isInTouchMode, createSections);
		f.fetch();
		return f;
	}
	
	public static FileFetcher fetchFiles(String path, Listener listener, boolean notifyFromMain, boolean recursive, boolean recursiveIfFirstEmpty, boolean playAfterFetching, boolean createSections) {
		FileFetcher f = new FileFetcher(path, listener, notifyFromMain, recursive, recursiveIfFirstEmpty, playAfterFetching, false, createSections);
		f.fetch();
		return f;
	}
	
	public static FileFetcher fetchFilesInThisThread(String path, Listener listener, boolean notifyFromMain, boolean recursive, boolean recursiveIfFirstEmpty, boolean playAfterFetching, boolean createSections) {
		FileFetcher f = new FileFetcher(path, listener, notifyFromMain, recursive, recursiveIfFirstEmpty, playAfterFetching, false, createSections);
		f.run();
		return f;
	}
	
	private FileFetcher(String path, Listener listener, boolean notifyFromMain, boolean recursive, boolean recursiveIfFirstEmpty, boolean playAfterFetching, boolean isInTouchMode, boolean createSections) {
		this.files = new FileSt[LIST_DELTA];
		this.path = path;
		this.listener = listener;
		this.notifyFromMain = notifyFromMain;
		this.recursive = recursive;
		this.recursiveIfFirstEmpty = recursiveIfFirstEmpty;
		this.playAfterFetching = playAfterFetching;
		//this.oldBrowserBehavior = UI.oldBrowserBehavior;
		this.isInTouchMode = isInTouchMode;
		this.createSections = createSections;
		this.count = 0;
	}
	
	private void fetch() {
		(new Thread(this, "File Fetcher Thread")).start();
	}
	
	public Throwable getThrowedException() {
		return notifyE;
	}
	
	private void ensureCapacity(int capacity) {
		if (capacity < count)
			return;
		
		if (capacity > files.length ||
			capacity <= (files.length - (2 * LIST_DELTA))) {
			capacity += LIST_DELTA;
		} else {
			return;
		}
		
		files = Arrays.copyOf(files, capacity);
	}
	
	private void addStorage(Service s, File path, boolean isExternal, int[] internalCount, int[] externalCount, int[] usbCount, int[] addedCount, String[] addedPaths) throws Throwable {
		if (!path.exists() || !path.isDirectory())
			return;
		
		int c = addedCount[0];
		if (c >= addedPaths.length)
			return;
		
		//readlink command could be used instead? which is better???
		//http://www.computerhope.com/unix/readlink.htm
		String canonicalPath = path.getCanonicalFile().getAbsolutePath();
		String canonicalPathLC = canonicalPath.toLowerCase(Locale.US);
		try {
			//limit the amount of iterations while resolving symlinks
			for (int i = 4; i > 0; i--) {
				final File file = new File(canonicalPath);
				final String p = file.getCanonicalFile().getAbsolutePath();
				final String pLC = p.toLowerCase(Locale.US);
				if (pLC.equals(canonicalPathLC))
					break;
				path = file;
				canonicalPath = p;
				canonicalPathLC = pLC;
			}
		} catch (Throwable ex) {
		}
		
		for (int i = c - 1; i >= 0; i--) {
			if (canonicalPathLC.equals(addedPaths[i]))
				return;
		}
		
		/*
		//check if the directory is empty before actually adding it
		try {
			final String[] files = path.list();
			if (files == null || files.length == 0)
				return;
		} catch (Throwable ex) {
			return;
		}*/
		
		addedPaths[c] = canonicalPathLC;
		addedCount[0] = c + 1;
		if (isExternal) {
			if (canonicalPathLC.contains("usb")) {
				c = usbCount[0] + 1;
				files[count] = new FileSt(canonicalPath, s.getText(R.string.usb_storage).toString() + ((c <= 1) ? "" : (" " + Integer.toString(c))), null, FileSt.TYPE_EXTERNAL_STORAGE_USB);
				usbCount[0] = c;
			} else {
				//try to avoid duplication of internal sdcard on a few phones... 
				if (internalCount[0] > 0 && canonicalPathLC.contains("/legacy"))
					return;
				c = externalCount[0] + 1;
				files[count] = new FileSt(canonicalPath, s.getText(R.string.external_storage).toString() + ((c <= 1) ? "" : (" " + Integer.toString(c))), null, FileSt.TYPE_EXTERNAL_STORAGE);
				externalCount[0] = c;
			}
		} else {
			files[count] = new FileSt(canonicalPath, s.getText(R.string.internal_storage).toString(), null, FileSt.TYPE_INTERNAL_STORAGE);
			internalCount[0]++;
		}
		count++;
	}
	
	private void fetchRoot() {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		files = Player.getFavoriteFolders(16);
		count = files.length - 16;
		
		String desc = s.getText(R.string.artists).toString();
		files[count] = new FileSt(FileSt.ARTIST_ROOT + FileSt.FAKE_PATH_ROOT + desc, desc, null, FileSt.TYPE_ARTIST_ROOT);
		count++;
		
		desc = s.getText(R.string.albums).toString();
		files[count] = new FileSt(FileSt.ALBUM_ROOT + FileSt.FAKE_PATH_ROOT + desc, desc, null, FileSt.TYPE_ALBUM_ROOT);
		count++;
		
		File f;
		try {
			f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
			if (f != null && f.exists() && f.isDirectory()) {
				files[count] = new FileSt(f.getAbsolutePath(), s.getText(R.string.music).toString(), null, FileSt.TYPE_MUSIC);
				count++;
			}
		} catch (Throwable ex) {
		}
		try {
			f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			if (f != null && f.exists() && f.isDirectory()) {
				files[count] = new FileSt(f.getAbsolutePath(), s.getText(R.string.downloads).toString(), null, FileSt.TYPE_DOWNLOADS);
				count++;
			}
		} catch (Throwable ex) {
		}
		f = null;
		
		if (cancelled)
			return;
		
		int i;
		int[] internalCount = new int[1], externalCount = new int[1], usbCount = new int[1], addedCount = new int[1];
		String[] addedPaths = new String[16];
		String path;
		
		try {
			addStorage(s, Environment.getExternalStorageDirectory(), Environment.isExternalStorageRemovable(), internalCount, externalCount, usbCount, addedCount, addedPaths);
		} catch (Throwable ex) {
		}
		
		//the following is an improved version based on these ideas:
		//http://sapienmobile.com/?p=204
		//http://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0
		
		/*try {
			path = System.getenv("SECONDARY_STORAGE");
			if (path != null && path.length() > 0) {
				//this file helps clarifying this ':' a little bit...
				//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.4.2_r1/android/os/Environment.java
				int start = path.indexOf(':');
				if (start < 0) {
					addStorage(s, new File(path), true, internalCount, externalCount, usbCount, addedCount, addedPaths);
				} else {
					//avoid using split ;)
					int end;
					do {
						end = path.indexOf(':', start + 1);
						if (end <= start) end = path.length();
						addStorage(s, new File(path.substring(start, end)), true, internalCount, externalCount, usbCount, addedCount, addedPaths);
						start = end + 1;
					} while (end < path.length());
				}
			}
		} catch (Throwable ex) {
		}
		
		if (cancelled)
			return;*/
		
		InputStream is = null;
		InputStreamReader isr = null;
		BufferedReader br = null;
		try {
			final HashMap<String, RootItem> map = new HashMap<String, RootItem>(32);
			String line;
			is = Runtime.getRuntime().exec("mount").getInputStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			while ((line = br.readLine()) != null) {
				if (cancelled)
					break;
				
				//skip secure storages (accessing them usually result in
				//a permission denied error)
				if (line.length() == 0 ||
					line.contains("secure") ||
					line.contains("asec")) continue;
				
				//http://unix.stackexchange.com/questions/91960/can-anyone-explain-the-output-of-mount
				
				//every line returned by mount must be seen as
				//fs_spec fs_file fs_vfstype [fs_mntopts - optional]
				
				final int first = line.indexOf(' ');
				if (first <= 0) continue;
				final int second = line.indexOf(' ', first + 1);
				if (second <= first) continue;
				
				final String fs_spec = line.substring(0, first);
				path = line.substring(first + 1, second);
				
				//fuse is used for internal storage
				final RootItem item = new RootItem(fs_spec, path, line.contains("fat") || line.contains("fuse"));
				map.put(item.fs_specLC, item);
			}
			for (RootItem item : map.values()) {
				if (cancelled)
					break;
				if (item.isFileSystemTypeValid) {
					RootItem tmp = item, it = item;
					//try to get the actual path pointed by this item, using a
					//poor man's cycle prevention ;)
					i = 0;
					while (i < 4 && (tmp = map.get(it.pathLC)) != null) {
						it = tmp;
						i++;
					}
					try {
						//a few old phones erroneously return these 3 as mounted devices
						if (!it.path.equals("/system") && !it.path.equals("/data") && !it.path.equals("/cache"))
							addStorage(s, new File(it.path), true, internalCount, externalCount, usbCount, addedCount, addedPaths);
					} catch (Throwable ex) {
					}
				}
			}
		} catch (Throwable ex) {
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Throwable ex) {
				}
			}
			if (isr != null) {
				try {
					isr.close();
				} catch (Throwable ex) {
				}
			}
			if (is != null) {
				try {
					is.close();
				} catch (Throwable ex) {
				}
			}
		}
		
		if (count < files.length) {
			files[count] = new FileSt(File.separator, s.getText(R.string.all_files).toString(), null, FileSt.TYPE_ALL_FILES);
			count++;
		}
	}
	
	private void fetchArtists() {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		final String fakeRoot = FileSt.FAKE_PATH_ROOT + s.getText(R.string.artists).toString() + FileSt.FAKE_PATH_SEPARATOR;
		final String root = FileSt.ARTIST_ROOT + File.separator;
		final ArrayList<FileSt> tmp = new ArrayList<FileSt>(64);
		final String[] proj = { MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST };
		final Cursor c = s.getContentResolver().query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, proj, null, null, null);
		while (c.moveToNext()) {
			if (cancelled || Player.state >= Player.STATE_TERMINATING) {
				count = 0;
				c.close();
				return;
			}
			String name = c.getString(1);
			if (name.equals("<unknown>")) {
				if (unknownArtist == null)
					unknownArtist = s.getText(R.string.unknownArtist).toString();
				name = unknownArtist;
			}
			final long id = c.getLong(0);
			final FileSt f = new FileSt(root + id + fakeRoot + name, name, null, FileSt.TYPE_ARTIST);
			f.artistIdForAlbumArt = id;
			tmp.add(f);
		}
		c.close();

		count = tmp.size();
		files = new FileSt[count];
		tmp.toArray(files);
		ArraySorter.sort(files, 0, files.length, new ArraySorter.Comparer<FileSt>() {
			@Override
			public int compare(FileSt a, FileSt b) {
				if (a.name == unknownArtist)
					return -1;
				else if (b.name == unknownArtist)
					return 1;
				return a.name.compareToIgnoreCase(b.name);
			}
		});
		
		tmp.clear();
	}
	
	private void fetchAlbums(String path) {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		final String artist;
		final String fakeRoot;
		final String root;
		if (path == null) {
			artist = null;
			fakeRoot = FileSt.FAKE_PATH_ROOT + s.getText(R.string.albums).toString() + FileSt.FAKE_PATH_SEPARATOR;
			root = FileSt.ALBUM_ROOT + File.separator;
		} else {
			final int fakePathIdx = path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR);
			final String realPath = path.substring(0, fakePathIdx);
			final String fakePath = path.substring(fakePathIdx);
			artist = realPath.substring(realPath.lastIndexOf(File.separatorChar) + 1);
			fakeRoot = fakePath + FileSt.FAKE_PATH_SEPARATOR;
			root = realPath + File.separator;
		}
		final ArrayList<FileSt> tmp = new ArrayList<FileSt>(64);
		final String[] proj = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ALBUM_ART };
		final Cursor c = s.getContentResolver().query((artist == null) ?
				MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI :
				MediaStore.Audio.Artists.Albums.getContentUri("external", Long.parseLong(artist)), proj, null, null, null);
		while (c.moveToNext()) {
			if (cancelled || Player.state >= Player.STATE_TERMINATING) {
				count = 0;
				c.close();
				return;
			}
			final String name = c.getString(1);
			tmp.add(new FileSt(root + c.getLong(0) + fakeRoot + name, name, c.getString(2), FileSt.TYPE_ALBUM));
		}
		c.close();

		count = tmp.size();
		files = new FileSt[count];
		tmp.toArray(files);
		ArraySorter.sort(files, 0, files.length, this);
		
		tmp.clear();
	}
	
	private void fetchTracks(String path) {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		final int fakePathIdx = path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR);
		final String realPath = path.substring(0, fakePathIdx);
		final int albumIdIdx = realPath.lastIndexOf(File.separatorChar) + 1;
		final String artist = ((realPath.charAt(0) == FileSt.ARTIST_ROOT_CHAR) ? realPath.substring(2, albumIdIdx - 1) : null);
		final String album = realPath.substring(albumIdIdx);
		final ArrayList<FileSt> tmp = new ArrayList<FileSt>(64);
		final String[] proj = { MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.TRACK };
		final Cursor c = s.getContentResolver().query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
				(artist == null) ?
				(MediaStore.Audio.Media.ALBUM_ID + "=?") :
				(MediaStore.Audio.Media.ALBUM_ID + "=? AND " + MediaStore.Audio.Media.ARTIST_ID + "=?"),
				(artist == null) ?
				new String[] { album } :
				new String[] { album, artist },
				null);
		while (c.moveToNext()) {
			if (cancelled || Player.state >= Player.STATE_TERMINATING) {
				count = 0;
				c.close();
				return;
			}
			//temporarily use specialType as the song's track number ;)
			tmp.add(new FileSt(c.getString(0), c.getString(1), null, c.getInt(2), false));
		}
		c.close();

		count = tmp.size();
		files = new FileSt[count];
		tmp.toArray(files);
		ArraySorter.sort(files, 0, files.length, new ArraySorter.Comparer<FileSt>() {
			@Override
			public int compare(FileSt a, FileSt b) {
				if (a.specialType != b.specialType)
					return a.specialType - b.specialType;
				return a.name.compareToIgnoreCase(b.name);
			}
		});
		for (int i = files.length - 1; i >= 0; i--)
			files[i].specialType = 0;
		
		tmp.clear();
	}
	
	//
	//Despite its name, EXTERNAL_CONTENT_URI also comprises the internal sdcard (at least
	//it does so in all devices I have tested!)
	//I'm keeping this old version here just in case.....
	//
	/*private void fetchArtists() {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		final String fakeRoot = FileSt.FAKE_PATH_ROOT + s.getText(R.string.artists).toString() + FileSt.FAKE_PATH_SEPARATOR;
		final String root = FileSt.ARTIST_ROOT + File.separator;
		final ArrayList<FileSt> tmp = new ArrayList<FileSt>(64);
		final String[] proj = { MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST };
		final long[] mask = new long[] { 0, 0xffffffffffffffffL };
		final Uri[] uris = new Uri[] {
				MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI ,
				MediaStore.Audio.Artists.INTERNAL_CONTENT_URI };
		
		for (int i = uris.length - 1; i >= 0 && !cancelled; i--) {
			final Cursor c = s.getContentResolver().query(uris[i], proj, null, null, null);
			while (!cancelled && c.moveToNext()) {
				final String name = c.getString(1);
				tmp.add(new FileSt(root + (mask[i] ^ c.getLong(0)) + fakeRoot + name, name, FileSt.TYPE_ARTIST));
			}
			c.close();
		}
		
		if (cancelled)
			return;
		
		count = tmp.size();
		files = new FileSt[count];
		tmp.toArray(files);
		ArraySorter.sort(files, 0, files.length, this);
		
		tmp.clear();
	}
	
	private void fetchAlbums(String path) {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		String artist;
		String fakeRoot;
		String root;
		if (path == null) {
			artist = null;
			fakeRoot = FileSt.FAKE_PATH_ROOT + s.getText(R.string.albums).toString() + FileSt.FAKE_PATH_SEPARATOR;
			root = FileSt.ALBUM_ROOT + File.separator;
		} else {
			final int fakePathIdx = path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR);
			final String realPath = path.substring(0, fakePathIdx);
			final String fakePath = path.substring(fakePathIdx);
			artist = realPath.substring(realPath.lastIndexOf(File.separatorChar) + 1);
			fakeRoot = fakePath + FileSt.FAKE_PATH_SEPARATOR;
			root = realPath + File.separator;
		}
		
		final ArrayList<FileSt> tmp = new ArrayList<FileSt>(64);
		final String[] proj = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM };
		long[] mask;
		Uri[] uris;
		if (artist == null) {
			mask = new long[] { 0, 0xffffffffffffffffL };
			uris = new Uri[] {
					MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
					MediaStore.Audio.Albums.INTERNAL_CONTENT_URI };
		} else {
			final long id = Long.parseLong(artist);
			if (id < 0) {
				mask = new long[] { 0xffffffffffffffffL };
				uris = new Uri[] {
						MediaStore.Audio.Artists.Albums.getContentUri("internal", id ^ 0xffffffffffffffffL) };
			} else {
				mask = new long[] { 0 };
				uris = new Uri[] {
						MediaStore.Audio.Artists.Albums.getContentUri("external", id) };
			}
		}
		
		for (int i = uris.length - 1; i >= 0 && !cancelled; i--) {
			final Cursor c = s.getContentResolver().query(uris[i], proj, null, null, null);
			while (!cancelled && c.moveToNext()) {
				final String name = c.getString(1);
				tmp.add(new FileSt(root + (mask[i] ^ c.getLong(0)) + fakeRoot + name, name, FileSt.TYPE_ALBUM));
			}
			c.close();
		}
		
		if (cancelled)
			return;
		
		count = tmp.size();
		files = new FileSt[count];
		tmp.toArray(files);
		ArraySorter.sort(files, 0, files.length, this);
		
		tmp.clear();
	}
	
	private void fetchTracks(String path) {
		final Service s = Player.getService();
		if (s == null)
			return;
		
		final int fakePathIdx = path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR);
		final String realPath = path.substring(0, fakePathIdx);
		final long album = Long.parseLong(realPath.substring(realPath.lastIndexOf(File.separatorChar) + 1));
		final ArrayList<FileSt> tmp = new ArrayList<FileSt>(64);
		final String[] proj = { MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.TRACK };
		final Cursor c = s.getContentResolver().query(
				(album < 0) ? MediaStore.Audio.Media.INTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
				MediaStore.Audio.Media.ALBUM_ID + "=?",
				new String[] { Long.toString((album < 0) ? (album ^ 0xffffffffffffffffL) : album) },
				null);
		while (!cancelled && c.moveToNext()) {
			tmp.add(new FileSt(c.getString(0), c.getString(1), c.getInt(2), false));
		}
		c.close();
		
		if (cancelled)
			return;
		
		count = tmp.size();
		files = new FileSt[count];
		tmp.toArray(files);
		ArraySorter.sort(files, 0, files.length, new ArraySorter.Comparer<FileSt>() {
			@Override
			public int compare(FileSt a, FileSt b) {
				if (a.specialType != b.specialType)
					return a.specialType - b.specialType;
				return a.name.compareToIgnoreCase(b.name);
			}
		});
		for (int i = files.length - 1; i >= 0; i--)
			files[i].specialType = 0;
		
		tmp.clear();
	}*/
	
	private void fetchFiles(String path, boolean first) {
		if (cancelled || Player.state >= Player.STATE_TERMINATING) {
			count = 0;
			return;
		}
		int i;
		File root = new File((path.charAt(path.length() - 1) == File.separatorChar) ? path : (path + File.separator));
		File[] files = root.listFiles();
		boolean filesAdded = false;
		if (files == null || files.length == 0) {
			if (this.files == null)
				this.files = new FileSt[0];
			return;
		}
		final int l = count;
		ensureCapacity(count + files.length);
		for (i = 0; i < files.length; i++) {
			if (cancelled || Player.state >= Player.STATE_TERMINATING) {
				count = 0;
				return;
			}
			final String t = files[i].getName();
			if (!files[i].isDirectory() && !isFileSupported(t)) {
				files[i] = null;
				continue;
			}
			this.files[count] = new FileSt(files[i], t);
			if (!this.files[count].isDirectory)
				filesAdded = true;
			count++;
			files[i] = null;
		}
		files = null;
		final int e = count;
		ArraySorter.sort(this.files, l, e - l, this);
		if (first && !filesAdded && recursiveIfFirstEmpty)
			recursive = true;
		if (!recursive)
			return;
		for (i = l; i < e; i++) {
			if (cancelled || Player.state >= Player.STATE_TERMINATING) {
				count = 0;
				return;
			}
			if (this.files[i].isDirectory)
				fetchFiles(this.files[i].path, false);
		}
	}
	
	private void fetchPrivateFiles(String fileType) {
		if (cancelled || Player.state >= Player.STATE_TERMINATING) {
			count = 0;
			return;
		}
		final String[] files = Player.getService().fileList();
		if (files == null || files.length == 0) {
			if (this.files == null)
				this.files = new FileSt[0];
			return;
		}
		ensureCapacity(files.length);
		int i, c = 0;
		final int l = fileType.length();
		for (i = files.length - 1; i >= 0; i--) {
			if (cancelled || Player.state >= Player.STATE_TERMINATING) {
				count = 0;
				return;
			}
			final String f = files[i];
			if (files[i].endsWith(fileType)) {
				this.files[c] = new FileSt(f, f.substring(0, f.length() - l), null, 0);
				c++;
			}
		}
		count = c;
		ArraySorter.sort(this.files, 0, c, this);
	}
	
	public void computeSections() {
		if (!createSections || count < 1) {
			sections = null;
			sectionPositions = null;
			return;
		}
		int sectionIdx = 0, i = 1, currentCount = 1;
		int[] charCount = new int[100]; //no more than 100 groups allowed
		int[] pos = new int[100];
		char[] chars = new char[100];
		char current, last = (char)Character.toUpperCase((int)files[0].name.charAt(0));
		if (last < '@' || files[0].name == unknownArtist)
			last = '#';
		chars[0] = last;
		pos[0] = 0;
		while (i < count && sectionIdx < 100) {
			current = (char)Character.toUpperCase((int)files[i].name.charAt(0));
			if (current < '@')
				current = '#';
			if (current != last) {
				charCount[sectionIdx] = currentCount;
				sectionIdx++;
				currentCount = 1;
				last = current;
				if (sectionIdx < 100) {
					chars[sectionIdx] = last;
					pos[sectionIdx] = i;
				}
			} else {
				currentCount++;
			}
			i++;
		}
		if (currentCount != 0 && sectionIdx < 100) {
			charCount[sectionIdx] = currentCount;
			sectionIdx++;
		}
		//we must not create more than 28 sections
		if (sectionIdx > 28) {
			//sort by charCount (ignoring the first section, which is always included)
			//a insertion-sort-like sort should do it :)
			for (i = 2; i < sectionIdx; i++) {
				final int c = charCount[i];
				final int p = pos[i];
				final char ch = chars[i];
				int j = i - 1;
				//ignore section 0
				while (j > 0 && c > charCount[j]) {
					charCount[j + 1] = charCount[j];
					pos[j + 1] = pos[j];
					chars[j + 1] = chars[j];
					charCount[j] = c;
					pos[j] = p;
					chars[j] = ch;
					j--;
				}
			}
			sectionIdx = 28;
			//now we take the first 28 sections, and sort them by pos
			for (i = 2; i < 28; i++) {
				final int p = pos[i];
				final char ch = chars[i];
				int j = i - 1;
				//ignore section 0
				while (j > 0 && p < pos[j]) {
					pos[j + 1] = pos[j];
					chars[j + 1] = chars[j];
					pos[j] = p;
					chars[j] = ch;
					j--;
				}
			}
		}
		sections = new String[sectionIdx];
		sectionPositions = new int[sectionIdx];
		for (i = sectionIdx - 1; i >= 0; i--) {
			sections[i] = Character.toString(chars[i]);
			sectionPositions[i] = pos[i];
		}
	}
	
	@Override
	public int compare(FileSt a, FileSt b) {
		if (a.isDirectory == b.isDirectory)
			return a.name.compareToIgnoreCase(b.name);
		return (a.isDirectory ? -1 : 1);
	}
	
	public void cancel() {
		cancelled = true;
	}
	
	@Override
	public void run() {
		if (MainHandler.isOnMainThread()) {
			if (listener != null && !cancelled && Player.state < Player.STATE_TERMINATING)
				listener.onFilesFetched(this, notifyE);
			listener = null;
			notifyE = null;
			return;
		}
		Throwable e = null;
		try {
			if (path == null || path.length() == 0) {
				fetchRoot();
			} else if (path.charAt(0) == FileSt.PRIVATE_FILETYPE_ID) {
				fetchPrivateFiles(path);
			} else if (path.charAt(0) == FileSt.ARTIST_ROOT_CHAR) {
				if (path.startsWith(FileSt.ARTIST_PREFIX)) {
					fetchArtists();
					computeSections();
				} else {
					final int p1 = path.indexOf(File.separatorChar);
					final int p2 = path.lastIndexOf(File.separatorChar, path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR));
					if (p2 != p1) {
						fetchTracks(path);
					} else {
						fetchAlbums(path);
						final boolean keepAlbumsAsItems = (!recursive);// && !oldBrowserBehavior);
						if (recursive || keepAlbumsAsItems) {
							//we actually need to fetch all tracks from all this artist's albums...
							final FileSt[] albums = files;
							final ArrayList<FileSt> tracks = new ArrayList<FileSt>(albums.length * 11);
							for (int i = 0; i < albums.length; i++) {
								if (cancelled || Player.state >= Player.STATE_TERMINATING) {
									count = 0;
									tracks.clear();
									break;
								}
								try {
									fetchTracks(albums[i].path);
									if (files != null && count > 0) {
										tracks.ensureCapacity(tracks.size() + count);
										if (keepAlbumsAsItems) {
											albums[i].specialType = FileSt.TYPE_ALBUM_ITEM;
											tracks.add(albums[i]);
										}
										for (int j = 0; j < count; j++) {
											tracks.add(files[j]);
											files[j] = null;
										}
										files = null;
									}
								} catch (Throwable ex) {
									e = ex;
								}
								albums[i] = null;
							}
							if (tracks.size() > 0) {
								//ignore any errors if at least one track was fetched
								e = null;
								count = tracks.size();
								files = new FileSt[count];
								tracks.toArray(files);
								tracks.clear();
							} else {
								count = 0;
								if (files == null)
									files = new FileSt[0];
							}
						}
					}
				}
			} else if (path.charAt(0) == FileSt.ALBUM_ROOT_CHAR) {
				if (path.startsWith(FileSt.ALBUM_PREFIX)) {
					fetchAlbums(null);
					computeSections();
				} else {
					fetchTracks(path);
				}
			} else {
				fetchFiles(path, true);
				computeSections();
			}
		} catch (Throwable ex) {
			e = ex;
		}
		if (!cancelled && Player.state < Player.STATE_TERMINATING) {
			if (listener != null) {
				if (notifyFromMain) {
					notifyE = e;
					MainHandler.postToMainThread(this);
				} else {
					listener.onFilesFetched(this, e);
					listener = null;
				}
			} else {
				notifyE = e;
			}
		}
	}
}
