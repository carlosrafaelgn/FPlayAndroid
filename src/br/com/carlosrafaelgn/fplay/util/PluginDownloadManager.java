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
package br.com.carlosrafaelgn.fplay.util;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.ui.BgDialog;
import br.com.carlosrafaelgn.fplay.ui.UI;
import dalvik.system.DexClassLoader;

public final class PluginDownloadManager {
	private static PluginDownloadManager pluginDownloadManager;

	private PluginDownloadManager() {
	}

	public static void copyFile(File from, File to) throws IOException {
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

	public static boolean checkForPermissions(ActivityHost activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (!activity.isReadStoragePermissionGranted()) {
				activity.requestReadStoragePermission();
				return false;
			}
			if (!activity.isWriteStoragePermissionGranted()) {
				activity.requestWriteStoragePermission();
				return false;
			}
		}
		return true;
	}

	private static Object tryToLoadApkFile(ActivityHost activity, String className, File pluginApkFile) {
		try {
			//the plugin has already been "installed", we just need to load it
			final String optimizedPath;
			final ApplicationInfo appInfo = activity.getApplicationInfo();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				optimizedPath = activity.getCodeCacheDir().getPath();
			} else {
				File codeCache = new File(appInfo.dataDir, "code_cache");
				if (!codeCache.exists()) {
					if (!codeCache.mkdirs()) {
						if (!codeCache.exists())
							codeCache = activity.getFilesDir();
					}
				}
				optimizedPath = codeCache.getPath();
			}
			final DexClassLoader dexClassLoader = new DexClassLoader(pluginApkFile.getPath(), optimizedPath, appInfo.nativeLibraryDir, PluginDownloadManager.class.getClassLoader());
			final Class<?> clazz = dexClassLoader.loadClass(className);
			final Object plugin = clazz.newInstance();
			if (plugin != null)
				return plugin;
		} catch (Throwable ex) {
			//there isn't much we can do...
			ex.printStackTrace();
		}
		UI.showDialogMessage(activity, activity.getText(R.string.oops), activity.getText(R.string.error_gen), R.string.cancel);
		return null;
	}

	public static Object createPluginClass(ActivityHost activity, String className, String pluginName, String pluginDownloadUri, boolean skipToDowload) {
		//based on:
		//https://stackoverflow.com/q/6857807/3569421
		//https://developer.android.com/reference/dalvik/system/DexClassLoader.html
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.1_r1/android/support/v4/content/ContextCompat.java#ContextCompat.getCodeCacheDir%28android.content.Context%29

		boolean mustDownload = true;
		File downloadFile = null;
		final String pluginApkFileName = pluginDownloadUri.substring(pluginDownloadUri.lastIndexOf('/') + 1);
		final File pluginApkFile = activity.getFileStreamPath(pluginApkFileName);

		if (!skipToDowload) {
			Class<?> clazz = null;
			try {
				clazz = Class.forName(className);
				final Object plugin = clazz.newInstance();
				if (plugin != null)
					return plugin;
			} catch (Throwable ex) {
				//something went wrong with the plugin
				if (clazz != null) {
					UI.showDialogMessage(activity, activity.getText(R.string.oops), activity.getText(R.string.error_gen), R.string.cancel);
					return null;
				}
			}

			if (pluginApkFile.exists())
				return tryToLoadApkFile(activity, className, pluginApkFile);

			//we must download the plugin only if it has not been downloaded yet
			try {
				downloadFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), pluginApkFileName);
				mustDownload = !downloadFile.exists();
			} catch (Throwable ex) {
				mustDownload = true;
			}
		}

		if (mustDownload) {
			if (!checkForPermissions(activity))
				return -1;
		} else {
			try {
				copyFile(downloadFile, pluginApkFile);
			} catch (Throwable ex) {
				UI.showDialogMessage(activity, activity.getText(R.string.oops), activity.getText(R.string.error_gen), R.string.cancel);
				return null;
			}
			return tryToLoadApkFile(activity, className, pluginApkFile);
		}

		return null;
	}
}
