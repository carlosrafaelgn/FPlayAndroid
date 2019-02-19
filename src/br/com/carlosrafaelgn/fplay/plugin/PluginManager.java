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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;

import com.google.gson.Gson;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.visualizer.SimpleVisualizerJni;
import dalvik.system.DexClassLoader;

public final class PluginManager implements MainHandler.Callback, DialogInterface.OnClickListener, DialogInterface.OnDismissListener, FPlay {
	private static final int MSG_ERROR_GEN = 0x0A00;
	private static final int MSG_INSTALL_PLUGIN = 0x0A01;

	private static final HashMap<String, Class<?>> pluginClasses = new HashMap<>();
	private static final HashMap<String, Object> pluginContexts = new HashMap<>();
	private static volatile boolean isPluginLoading;
	private static final PluginManager pluginManager = new PluginManager();

	public interface Observer {
		void onPluginCreated(int id, FPlayPlugin plugin);
	}

	public static FPlay getFPlay() {
		return pluginManager;
	}

	private volatile ActivityHost activity;
	private volatile String pluginName, packageName;

	private PluginManager() {
	}

	private static boolean tryToCreatePluginFromCache(ActivityHost activity, String className, int pluginId, Observer observer) {
		try {
			synchronized (pluginClasses) {
				final Class<?> clazz;
				if ((clazz = pluginClasses.get(className)) != null) {
					final FPlayPlugin plugin = (FPlayPlugin)clazz.newInstance();
					if (plugin != null) {
						plugin.init(pluginContexts.get(className), pluginManager);
						observer.onPluginCreated(pluginId, plugin);
						return true;
					}
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
			pluginManager.showMessage(activity, MSG_ERROR_GEN, null, null);
			return true;
		}
		return false;
	}

	private static boolean tryToCreatePluginFromPackage(Runnable runnable, ActivityHost activity, String className, String packageName) {
		try {
			//the plugin has already been downloaded, now we just need to load it

			//unfortunatelly, although getCodeCacheDir() is read/writable,
			//we cannot list its contents on a few devices... :(
			final File codeCacheDir = activity.getFilesDir();
			//final ApplicationInfo appInfo = activity.getApplicationInfo();
			//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			//	codeCacheDir = activity.getCodeCacheDir();
			//} else {
			//	codeCacheDir = new File(appInfo.dataDir, "code_cache");
			//	if (!codeCacheDir.exists()) {
			//		if (!codeCacheDir.mkdirs()) {
			//			if (!codeCacheDir.exists())
			//				codeCacheDir = activity.getFilesDir();
			//		}
			//	}
			//}

			final Context context = activity.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);

			if (context == null)
				return false;

			final String apkFilePath = context.getPackageCodePath();

			//delete old cached dex files in order to make sure they are
			//always up-to-date with their apk files (which might have changed)
			for (File file : codeCacheDir.listFiles()) {
				if (file.getName().toLowerCase(Locale.US).startsWith(packageName)) {
					try {
						if (file.delete())
							break;
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}
			/*final int apk = apkFilePath.lastIndexOf(packageName);
			if (apk > 0) {
				//could be a.b.c[-XXX]/YYY.apk or a.b.c[-XXX].apk
				int end = apkFilePath.indexOf(File.separatorChar, apk);
				if (end < apk)
					end = apkFilePath.lastIndexOf(".apk");
				if (end > apk) {
					final File oldDex = new File(codeCacheDir, apkFilePath.substring(apk, end) + ".dex");
					try {
						if (oldDex.exists()) {
							oldDex.delete();
						}
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}*/

			final Class<?> clazz;
			final ClassLoader classLoader = activity.getClassLoader();
			classLoader.loadClass("br.com.carlosrafaelgn.fplay.plugin.FPlay");
			classLoader.loadClass("br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin");
			final DexClassLoader dexClassLoader = new DexClassLoader(apkFilePath, codeCacheDir.getAbsolutePath(), null, classLoader);
			clazz = dexClassLoader.loadClass(className);

			if (clazz != null) {
				synchronized (pluginClasses) {
					if (!pluginClasses.containsKey(className))
						pluginClasses.put(className, clazz);
					if (!pluginContexts.containsKey(className))
						pluginContexts.put(className, context);
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

	public static void createPlugin(final ActivityHost activity, final String className, final String packageName, final String pluginName, final int pluginId, final Observer observer) {
		if (tryToCreatePluginFromCache(activity, className, pluginId, observer))
			return;

		synchronized (pluginClasses) {
			if (isPluginLoading)
				return;
			isPluginLoading = true;
		}

		final Thread thread = (new Thread("Plugin Loader Thread") {
			{
				setDaemon(true);
			}

			@Override
			public void run() {
				try {
					if (MainHandler.isOnMainThread()) {
						//the plugin class has been successfully loaded
						tryToCreatePluginFromCache(activity, className, pluginId, observer);
						return;
					}

					boolean mustDownload;

					try {
						mustDownload = (activity.getPackageManager().getPackageInfo(packageName, 0) == null);
					} catch (Throwable ex) {
						mustDownload = true;
					}

					if (mustDownload) {
						pluginManager.showMessage(activity, MSG_INSTALL_PLUGIN, pluginName, packageName);
						return;
					}

					if (!tryToCreatePluginFromPackage(this, activity, className, packageName))
						pluginManager.showMessage(activity, MSG_ERROR_GEN, null, null);
				} finally {
					isPluginLoading = false;
				}
			}
		});

		try {
			thread.start();
		} catch (Throwable ex) {
			isPluginLoading = false;
			ex.printStackTrace();
		}
	}

	private void showMessage(ActivityHost activity, int message, String pluginName, String packageName) {
		synchronized (pluginClasses) {
			this.activity = activity;
			this.pluginName = pluginName;
			this.packageName = packageName;
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
		case MSG_INSTALL_PLUGIN:
			synchronized (pluginClasses) {
				//replace %s... faster than format() ;)
				if (activity != null && pluginName != null && packageName != null)
					UI.showDialogMessage(activity, activity.getText(R.string.download), activity.getText(R.string.download_confirmation).toString().replace("%s", pluginName), R.string.download, R.string.no, this, this);
			}
			break;
		}
		return false;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		synchronized (pluginClasses) {
			if (activity != null && pluginName != null && packageName != null && which == DialogInterface.BUTTON_POSITIVE) {
				try {
					final Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse("market://details?id=" + packageName));
					activity.startActivity(intent);
				} catch (Throwable ex) {
					showMessage(activity, MSG_ERROR_GEN, null, null);
					return;
				}
			}
			activity = null;
			pluginName = null;
			packageName = null;
		}
		dialog.dismiss();
	}

	@Override
	public void onDismiss(DialogInterface dialogInterface) {
		synchronized (pluginClasses) {
			activity = null;
			pluginName = null;
			packageName = null;
		}
	}

	@Override
	public int getApiVersion() {
		return FPlayPlugin.API_VERSION;
	}

	@Override
	public int getFPlayVersionCode() {
		return UI.VERSION_CODE;
	}

	@Override
	public String getFPlayVersionName() {
		return UI.VERSION_NAME;
	}

	@Override
	public boolean isAlive() {
		return (Player.state == Player.STATE_ALIVE);
	}

	@Override
	public Object getApplicationContext() {
		return Player.theApplication;
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
	public void sendMessage(Object callback, int what) {
		MainHandler.sendMessage((MainHandler.Callback)callback, what);
	}

	@Override
	public void sendMessage(Object callback, int what, int arg1, int arg2) {
		MainHandler.sendMessage((MainHandler.Callback)callback, what, arg1, arg2);
	}

	@Override
	public void sendMessageAtTime(Object callback, int what, int arg1, int arg2, long uptimeMillis) {
		MainHandler.sendMessageAtTime((MainHandler.Callback)callback, what, arg1, arg2, uptimeMillis);
	}

	@Override
	public void removeMessages(Object callback, int what) {
		MainHandler.removeMessages((MainHandler.Callback)callback, what);
	}

	@Override
	public boolean deviceHasTelephonyRadio() {
		return Player.deviceHasTelephonyRadio();
	}

	@Override
	public boolean isConnectedToTheInternet() {
		return Player.isConnectedToTheInternet();
	}

	@Override
	public boolean isInternetConnectedViaWiFi() {
		return Player.isInternetConnectedViaWiFi();
	}

	@Override
	public int getWiFiIpAddress() {
		return Player.getWiFiIpAddress();
	}

	@Override
	public String getWiFiIpAddressStr() {
		return Player.getWiFiIpAddressStr();
	}

	@Override
	public String emoji(CharSequence text) {
		return UI.emoji(text);
	}

	@Override
	public void toast(String message) {
		MainHandler.toast(message);
	}

	@Override
	public <E> ItemSelectorDialog<E> showItemSelectorDialog(final Object activity, final CharSequence title, final CharSequence loadingMessage, final CharSequence connectingMessage, final boolean progressBarVisible, final Class<E> clazz, final E[] initialElements, final ItemSelectorDialog.Observer<E> observer) {
		//I know this is *SUPER UGLY*! But it saves a few classes :)
		final class Local extends br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog.Item implements ItemSelectorDialog<E>, br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog.Observer<Local> {
			private final br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog<Local> dialog;
			private final E item;

			Local(E item) {
				super(new FileSt("", (item == null) ? "" : item.toString()));
				this.dialog = null;
				this.item = item;
			}

			Local() {
				super(null);

				final Local[] localInitialElements = ((initialElements == null) ? null : new Local[initialElements.length]);

				if (initialElements != null) {
					for (int i = initialElements.length - 1; i >= 0; i--)
						localInitialElements[i] = new Local(initialElements[i]);
				}

				dialog = br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog.showDialog((Activity)activity, title, loadingMessage, connectingMessage, progressBarVisible, Local.class, localInitialElements, this);
				item = null;
			}

			@Override
			public void onItemSelectorDialogClosed(br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog<Local> itemSelectorDialog) {
				if (observer != null)
					observer.onItemSelectorDialogClosed(this);
			}

			@Override
			public void onItemSelectorDialogRefreshList(br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog<Local> itemSelectorDialog) {
				if (observer != null)
					observer.onItemSelectorDialogRefreshList(this);
			}

			@Override
			public void onItemSelectorDialogItemClicked(br.com.carlosrafaelgn.fplay.ui.ItemSelectorDialog<Local> itemSelectorDialog, int position, Local item) {
				if (observer != null)
					observer.onItemSelectorDialogItemClicked(this, position, item.item);
			}

			@Override
			public void add(E item) {
				if (dialog != null)
					dialog.add(new Local(item));
			}

			@Override
			public void clear() {
				if (dialog != null)
					dialog.clear();
			}

			@Override
			public void remove(int position) {
				if (dialog != null)
					dialog.remove(position);
			}

			@Override
			public void dismiss() {
				if (dialog != null)
					dialog.dismiss();
			}

			@Override
			public void cancel() {
				if (dialog != null)
					dialog.cancel();
			}

			@Override
			public boolean isCancelled() {
				return (dialog == null || dialog.isCancelled());
			}

			@Override
			public int getCount() {
				return (dialog == null ? 0 : dialog.getCount());
			}

			@Override
			public E getItem(int position) {
				if (dialog == null)
					return null;
				final Local item = dialog.getItem(position);
				return (item == null ? null : item.item);
			}

			@Override
			public void showProgressBar(boolean show) {
				if (dialog != null)
					dialog.showProgressBar(show);
			}

			@Override
			public void showConnecting(boolean connecting) {
				if (dialog != null)
					dialog.showConnecting(connecting);
			}
		}

		return new Local();
	}

	@Override
	public String getString(int str) {
		switch (str) {
		case STR_VISUALIZER_NOT_SUPPORTED:
			return UI.emoji(Player.theApplication.getText(R.string.visualizer_not_supported));
		}
		return "";
	}

	@Override
	public void fixLocale(Object context) {
		if (context != null)
			UI.reapplyForcedLocaleOnPlugins((Context)context);
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
	public VisualizerService createVisualizerService(Visualizer visualizer, VisualizerService.Observer observer) {
		return new br.com.carlosrafaelgn.fplay.playback.context.VisualizerService(visualizer, observer);
	}

	@Override
	public void visualizerSetSpeed(int speed) {
		SimpleVisualizerJni.commonSetSpeed(speed);
	}

	@Override
	public void visualizerSetColorIndex(int colorIndex) {
		SimpleVisualizerJni.commonSetColorIndex(colorIndex);
	}

	@Override
	public void visualizerUpdateMultiplier(boolean isVoice, boolean hq) {
		SimpleVisualizerJni.commonUpdateMultiplier(isVoice, hq);
	}

	@Override
	public int visualizerProcess(byte[] waveform, int opt) {
		return SimpleVisualizerJni.commonProcess(waveform, opt);
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
	public void resume() {
		Player.resume();
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
	public void setVolumeInPercentage(int percentage) {
		Player.setVolumeInPercentage(percentage);
	}

	@Override
	public int getVolumeInPercentage() {
		return Player.getVolumeInPercentage();
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
	public boolean isPlaying() {
		return Player.localPlaying;
	}

	@Override
	public boolean isPreparing() {
		return Player.isPreparing();
	}

	@Override
	public int getPlaybackPosition() {
		return Player.getPosition();
	}

	@Override
	public boolean isMediaButton(int keyCode) {
		return Player.isMediaButton(keyCode);
	}

	@Override
	public boolean handleMediaButton(int keyCode) {
		return Player.handleMediaButton(keyCode);
	}

	@Override
	public boolean currentSongInfo(SongInfo info) {
		final Song song = Player.localSong;
		if (song == null)
			return false;
		song.info(info);
		return true;
	}

	@Override
	public String formatTime(int timeMS) {
		return Song.formatTime(timeMS);
	}

	@Override
	public int getPlaylistVersion() {
		return Player.songs.getModificationVersion();
	}

	@Override
	public int getPlaylistCount() {
		return Player.songs.getCount();
	}

	@Override
	public void getPlaylistSongInfo(int index, SongInfo info) {
		Player.songs.getItem(index).info(info);
	}

	@Override
	public String encodeAddressPort(int address, int port) {
		return Player.encodeAddressPort(address, port);
	}

	@Override
	public byte[] decodeAddressPort(String encodedAddressPort) {
		return Player.decodeAddressPort(encodedAddressPort);
	}

	@Override
	public void adjustJsonString(StringBuilder builder, String str) {
		if (str != null) {
			final int len = str.length();
			for (int i = 0; i < len; i++) {
				final char c = str.charAt(i);
				switch (c) {
				case '\\':
					builder.append("\\\\");
					break;
				case '\"':
					builder.append("\\\"");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\t':
					builder.append("\\t");
					break;
				case '\0':
					builder.append(' ');
					break;
				default:
					builder.append(c);
					break;
				}
			}
		}
	}

	@Override
	public String toJson(Object src) {
		return (new Gson()).toJson(src);
	}

	@Override
	public <T> T fromJson(String json, Class<T> clazz) throws Exception {
		return (new Gson()).fromJson(json, clazz);
	}
}
