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
package br.com.carlosrafaelgn.fplay.plugin;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.UI;
import dalvik.system.DexClassLoader;

public final class PluginManager implements MainHandler.Callback, FPlay {
	private static final int MSG_ERROR_GEN = 0x0A00;

	private static final HashMap<String, Class<?>> pluginClasses = new HashMap<>();
	private static volatile boolean isPluginLoading;
	private static final PluginManager pluginManager = new PluginManager();

	public interface Observer {
		void onPluginCreated(int id, FPlayPlugin plugin);
	}

	public static FPlay getFPlay() {
		return pluginManager;
	}

	private volatile ActivityHost activity;

	private PluginManager() {
	}

	private static void copyFile(File from, File to) throws IOException {
		FileInputStream fileInputStream = null;
		FileOutputStream fileOutputStream = null;
		FileChannel fromFile = null, toFile = null;
		try {
			fromFile = (fileInputStream = new FileInputStream(from)).getChannel();
			toFile = (fileOutputStream = new FileOutputStream(to)).getChannel();
			toFile.transferFrom(fromFile, 0, fromFile.size());
		} finally {
			try {
				if (fileInputStream != null)
					fileInputStream.close();
			} catch (Throwable ex) {
				//just ignore...
			}
			try {
				if (fileOutputStream != null)
					fileOutputStream.close();
			} catch (Throwable ex) {
				//just ignore...
			}
			try {
				if (fromFile != null)
					fromFile.close();
			} catch (Throwable ex) {
				//just ignore...
			}
			try {
				if (toFile != null)
					toFile.close();
			} catch (Throwable ex) {
				//just ignore...
			}
		}
	}

	private static boolean tryToCreatePluginFromCache(ActivityHost activity, String className, int pluginId, Observer observer) {
		try {
			synchronized (pluginClasses) {
				final Class<?> clazz;
				if ((clazz = pluginClasses.get(className)) != null) {
					final Object plugin = clazz.newInstance();
					if (plugin != null) {
						observer.onPluginCreated(pluginId, (FPlayPlugin)plugin);
						return true;
					}
				}
			}
		} catch (Throwable ex) {
			pluginManager.showMessage(activity, MSG_ERROR_GEN);
			return true;
		}
		return false;
	}

	private static boolean tryToCreatePluginFromApkFile(Runnable runnable, ActivityHost activity, String className, File pluginApkFile) {
		try {
			//the plugin has already been downloaded, now we just need to load it

			final String optimizedPath;
			final ApplicationInfo appInfo = activity.getApplicationInfo();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				optimizedPath = activity.getCodeCacheDir().getAbsolutePath();
			} else {
				File codeCache = new File(appInfo.dataDir, "code_cache");
				if (!codeCache.exists()) {
					if (!codeCache.mkdirs()) {
						if (!codeCache.exists())
							codeCache = activity.getFilesDir();
					}
				}
				optimizedPath = codeCache.getAbsolutePath();
			}

			//this way the plugin is loaded in a totally different class loader, and it is impossible
			//to cast the plugin to FPlayPlugin
			////final DexClassLoader dexClassLoader = new DexClassLoader(pluginApkFile.getAbsolutePath(), optimizedPath, null, ClassLoader.getSystemClassLoader().getParent());

			//this way, on the other hand, the plugin is loaded in a different class loader, but it
			//references our class loader, making the casts to FPlayPlugin work properly... nevertheless,
			//Class.forName still will not be able to find the plugin class (which will have to be reloaded
			//everytime, unless we cache the resulting class for the first time...)
			//final DexClassLoader dexClassLoader = new DexClassLoader(pluginApkFile.getAbsolutePath(), optimizedPath, null, activity.getClassLoader());
			//final Class<?> clazz = dexClassLoader.loadClass(className);

			//https://developer.android.com/reference/dalvik/system/DexFile.html
			//this way works like a charm, but DexFile will be removed in API 27, or API 28 :(
			//final DexFile df = new DexFile(pluginApkFile);
			//final Class<?> clazz = df.loadClass(className, activity.getClassLoader());

			//so, we will stick to the second approach
			final Class<?> clazz;
			final ClassLoader classLoader = activity.getClassLoader();
			classLoader.loadClass("br.com.carlosrafaelgn.fplay.plugin.FPlay");
			classLoader.loadClass("br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin");
			final DexClassLoader dexClassLoader = new DexClassLoader(pluginApkFile.getAbsolutePath(), optimizedPath, null, classLoader);
			clazz = dexClassLoader.loadClass(className);

			if (clazz != null) {
				synchronized (pluginClasses) {
					if (!pluginClasses.containsKey(className))
						pluginClasses.put(className, clazz);
				}
				MainHandler.postToMainThread(runnable);
				return true;
			}
		} catch (Throwable ex) {
			//there isn't much we can do...
			ex.printStackTrace();
		}
		return false;
	}

	public static void createPlugin(final ActivityHost activity, final String className, final String pluginName, final String pluginDownloadUri, final boolean skipToDowload, final int pluginId, final Observer observer) {
		//based on:
		//https://stackoverflow.com/q/6857807/3569421
		//https://stackoverflow.com/a/22687446/3569421
		//https://developer.android.com/reference/dalvik/system/DexClassLoader.html
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.1_r1/android/support/v4/content/ContextCompat.java#ContextCompat.getCodeCacheDir%28android.content.Context%29

		if (!skipToDowload && tryToCreatePluginFromCache(activity, className, pluginId, observer))
			return;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (!activity.isReadStoragePermissionGranted() ||
				!activity.isWriteStoragePermissionGranted()) {
				activity.requestPluginStoragePermission(pluginId);
				return;
			}
		}

		synchronized (pluginClasses) {
			if (isPluginLoading)
				return;
			isPluginLoading = true;
		}

		Thread thread = (new Thread("Plugin Loader Thread") {
			@Override
			public void run() {
				try {
					if (MainHandler.isOnMainThread()) {
						//the plugin class has been successfully loaded
						tryToCreatePluginFromCache(activity, className, pluginId, observer);
						return;
					}

					boolean mustDownload = true;
					File downloadFile = null;
					final String pluginApkFileName = pluginDownloadUri.substring(pluginDownloadUri.lastIndexOf('/') + 1);
					final File pluginApkFile = activity.getFileStreamPath(pluginApkFileName);

					if (!skipToDowload) {
						if (pluginApkFile.exists() && tryToCreatePluginFromApkFile(this, activity, className, pluginApkFile))
							return;

						//we must download the plugin only if it has not been downloaded yet
						try {
							downloadFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), pluginApkFileName);
							mustDownload = !downloadFile.exists();
						} catch (Throwable ex) {
							mustDownload = true;
						}
					}

					if (mustDownload) {
						//download plugin file here...
					} else {
						try {
							copyFile(downloadFile, pluginApkFile);
						} catch (Throwable ex) {
							pluginManager.showMessage(activity, MSG_ERROR_GEN);
							return;
						}
						if (!tryToCreatePluginFromApkFile(this, activity, className, pluginApkFile))
							pluginManager.showMessage(activity, MSG_ERROR_GEN);
					}
				} finally {
					isPluginLoading = false;
				}
			}
		});

		try {
			thread.setDaemon(true);
			thread.start();
		} catch (Throwable ex) {
			isPluginLoading = false;
			ex.printStackTrace();
		}
	}

	private void showMessage(ActivityHost activity, int message) {
		synchronized (pluginClasses) {
			this.activity = activity;
			MainHandler.sendMessage(this, message);
		}
	}

	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
		case MSG_ERROR_GEN:
			//something went wrong with the plugin
			synchronized (pluginClasses) {
				if (activity != null) {
					UI.showDialogMessage(activity, activity.getText(R.string.oops), activity.getText(R.string.error_gen), R.string.cancel);
					activity = null;
				}
			}
			break;
		}
		return false;
	}

	@Override
	public int getApiVersion() {
		return FPlayPlugin.API_VERSION;
	}

	@Override
	public int getFPlayVersion() {
		return UI.VERSION_CODE;
	}

	@Override
	public Object getApplicationContext() {
		return Player.theApplication;
	}

	@Override
	public CharSequence getText(int id) {
		return "";
	}

	@Override
	public boolean isOnMainThread() {
		return MainHandler.isOnMainThread();
	}

	@Override
	public void postToMainThread(Runnable runnable) {
		MainHandler.postToMainThread(runnable);
	}

	@Override
	public void postToMainThreadAtTime(Runnable runnable, long uptimeMillis) {
		MainHandler.postToMainThreadAtTime(runnable, uptimeMillis);
	}

	@Override
	public void toast(String message) {
		MainHandler.toast(message);
	}

	@Override
	public String formatIntAsFloat(int number, boolean useTwoDecimalPlaces, boolean removeDecimalPlacesIfExact) {
		return UI.formatIntAsFloat(number, useTwoDecimalPlaces, removeDecimalPlacesIfExact);
	}

	@Override
	public void formatIntAsFloat(StringBuilder sb, int number, boolean useTwoDecimalPlaces, boolean removeDecimalPlacesIfExact) {
		UI.formatIntAsFloat(sb, number, useTwoDecimalPlaces, removeDecimalPlacesIfExact);
	}

	@Override
	public int dpToPxI(float dp) {
		return UI.dpToPxI(dp);
	}

	@Override
	public int spToPxI(float sp) {
		return UI.spToPxI(sp);
	}

	@Override
	public void previous() {
		Player.previous();
	}

	@Override
	public void pause() {
		Player.pause();
	}

	@Override
	public void playPause() {
		Player.playPause();
	}

	@Override
	public void next() {
		Player.next();
	}

	@Override
	public int increaseVolume() {
		return Player.increaseVolume();
	}

	@Override
	public int decreaseVolume() {
		return Player.decreaseVolume();
	}

	@Override
	public String[] currentSongInfo(String[] info) {
		final Song song = Player.localSong;
		if (song == null)
			return null;
		if (info == null || info.length < 6)
			info = new String[6];
		info[SONG_TITLE] = song.title;
		info[SONG_ARTIST] = song.artist;
		info[SONG_ALBUM] = song.album;
		info[SONG_TRACK] = Integer.toString(song.track);
		info[SONG_YEAR] = Integer.toString(song.year);
		info[SONG_LENGTH] = Integer.toString(song.lengthMS);
		return info;
	}

	@Override
	public String formatTime(int timeMS) {
		return Song.formatTime(timeMS);
	}
}
