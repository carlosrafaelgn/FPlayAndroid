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
package br.com.carlosrafaelgn.fplay.plugin.httptransmitter;

import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;

import org.nanohttpd.webserver.FileSystemWebServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import br.com.carlosrafaelgn.fplay.plugin.FPlay;
import br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin;
import br.com.carlosrafaelgn.fplay.plugin.SongInfo;

@SuppressWarnings("unused")
public final class Plugin implements FPlayPlugin, FileSystemWebServer.FileHandlerFactory {
	private static final int PLUGIN_MSG_START = 0x0001;
	private static final int PLUGIN_MSG_ERROR_MESSAGE = 0x0002;
	private static final int PLUGIN_MSG_GET_ADDRESS = 0x0003;
	private static final int PLUGIN_MSG_GET_ENCODED_ADDRESS = 0x0004;
	private static final int PLUGIN_MSG_REFRESH_LIST = 0x0005;

	private static final class Playlist {
		final int listVersion;
		final byte[] listJson, listVersionBytes;
		final SongInfo[] list;
		final String tag;

		Playlist(int listVersion, byte[] listJson, SongInfo[] list, String tag) {
			this.listVersion = listVersion;
			this.listJson = listJson;
			this.listVersionBytes = Integer.toString(listVersion).getBytes();
			this.list = list;
			this.tag = tag;
		}
	}

	private static final class FileHandler implements FileSystemWebServer.FileHandler {
		private final File file;
		private final byte[] contents;
		private final String tag;

		FileHandler(String uri, Playlist playlist) {
			tag = playlist.tag;
			switch (uri) {
			case "/list.json":
				file = null;
				contents = playlist.listJson;
				break;
			case "/version.json":
				file = null;
				contents = playlist.listVersionBytes;
				break;
			default:
				contents = null;
				if (uri.length() < 4) {
					file = null;
				} else {
					final int slash = uri.indexOf('/'), slash2 = uri.lastIndexOf('/'), dot = uri.lastIndexOf('.');
					if (slash < 0 || slash2 <= slash || dot <= slash2) {
						file = null;
					} else {
						boolean ok;
						int version, index;
						try {
							version = Integer.parseInt(uri.substring(slash + 1, slash2));
							index = Integer.parseInt(uri.substring(slash2 + 1, dot));
							ok = true;
						} catch (Throwable ex) {
							ok = false;
							version = -1;
							index = -1;
						}
						final SongInfo info;
						file = ((!ok || version != playlist.listVersion || index < 0 || index >= playlist.list.length || (info = playlist.list[index]) == null || info.isHttp) ?
							null :
							new File(info.path));
					}
				}
				break;
			}
		}

		@Override
		public boolean exists() {
			return (file != null || contents != null);
		}

		@Override
		public String etag() {
			return tag;
		}

		@Override
		public long length() {
			return (file != null ? file.length() : (contents != null ? contents.length : 0));
		}

		@Override
		public InputStream createInputStream() throws IOException {
			return (file != null ? new FileInputStream(file) : (contents != null ? new ByteArrayInputStream(contents) : null));
		}
	}

	private Context pluginContext;
	private FPlay fplay;
	private Observer observer;
	private FileSystemWebServer webServer;
	private String localAddress, encodedLocalAddress, baseTag;
	private volatile Playlist playlist;

	@Override
	public int getApiVersion() {
		return API_VERSION;
	}

	@Override
	public int getPluginVersion() {
		return 1;
	}

	@Override
	public int getPluginType() {
		return TYPE_BACKGROUND_PROCESS;
	}

	@Override
	public void init(Object pluginContext, FPlay fplay) {
		this.pluginContext = (Context)pluginContext;
		this.fplay = fplay;
	}

	@Override
	public void destroy() {
		if (webServer != null) {
			webServer.stop();
			webServer = null;
		}

		pluginContext = null;
		fplay = null;
		observer = null;
		localAddress = null;
		encodedLocalAddress = null;
		baseTag = null;
		playlist = null;
	}

	@Override
	public void setObserver(Observer observer) {
		this.observer = observer;
	}

	@Override
	public void load() {
	}

	@Override
	public void unload() {
	}

	@Override
	public int message(int message, int arg1, int arg2, Object obj) {
		if (pluginContext == null || fplay == null)
			return 0;

		final String[] address;

		switch (message) {
		case PLUGIN_MSG_START:
			if (webServer != null)
				return 0;

			baseTag = Long.toString(System.currentTimeMillis()) + Long.toString(SystemClock.elapsedRealtime());

			refreshList(true);

			try {
				final int addr = fplay.getWiFiIpAddress();
				final String addrStr = fplay.getWiFiIpAddressStr();
				if (addr == 0 || addrStr == null)
					throw new IllegalStateException("Could not resolve local Wifi address");

				webServer = new FileSystemWebServer(addrStr, 0, this);
				webServer.start();
				final InetSocketAddress socketAddress = (InetSocketAddress)webServer.getMyServerSocket().getLocalSocketAddress();
				localAddress = socketAddress.getAddress().toString() + ":" + socketAddress.getPort();

				final int port = socketAddress.getPort();
				localAddress = addrStr + ":" + port;
				encodedLocalAddress = Base64.encodeToString(new byte[] {
					(byte)addr,
					(byte)(addr >> 8),
					(byte)(addr >> 16),
					(byte)(addr >> 24),
					(byte)port,
					(byte)(port >> 8)
				}, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE).replace('-', '@');
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			return 1;
		case PLUGIN_MSG_GET_ADDRESS:
			if (obj instanceof String[] && (address = (String[])obj).length > 0)
				address[0] = localAddress;
			return 1;
		case PLUGIN_MSG_GET_ENCODED_ADDRESS:
			if (obj instanceof String[] && (address = (String[])obj).length > 0)
				address[0] = encodedLocalAddress;
			return 1;
		case PLUGIN_MSG_REFRESH_LIST:
			refreshList(false);
			return 1;
		}

		return 0;
	}

	private static void serializeString(StringBuilder builder, String str) {
		builder.append(str == null ? "null" : str.replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"));
	}

	private void refreshList(boolean force) {
		if (fplay == null)
			return;

		if (!force && playlist != null && playlist.listVersion == fplay.getPlaylistVersion())
			return;

		final StringBuilder builder = new StringBuilder(128 * 1024);
		final int version = fplay.getPlaylistVersion();
		final int count = fplay.getPlaylistCount();
		final SongInfo[] list = new SongInfo[count];
		builder.append("{\"list\":[");
		for (int i = 0; i < count; i++) {
			if (i != 0)
				builder.append(',');

			final SongInfo info = new SongInfo();
			list[i] = info;
			fplay.getPlaylistSongInfo(i, info);

			builder.append("{\"path\":\"");
			builder.append(version);
			builder.append("/");
			builder.append(i);
			final int ext = info.path.lastIndexOf('.');
			if (ext >= 0)
				serializeString(builder, info.path.substring(ext));
			builder.append("\",\"title\":\"");
			serializeString(builder, info.title);
			builder.append("\",\"artist\":\"");
			serializeString(builder, info.artist);
			builder.append("\",\"album\":\"");
			serializeString(builder, info.album);
			builder.append("\",\"track\":");
			builder.append(info.track);
			builder.append(",\"lengthMS\":");
			builder.append(info.lengthMS);
			builder.append(",\"year\":");
			builder.append(info.year);
			builder.append(",\"length\":\"");
			serializeString(builder, info.length);
			builder.append("\"}");
		}
		builder.append("]}");

		playlist = new Playlist(version, builder.toString().getBytes(), list, baseTag + version);
	}

	@Override
	public FileSystemWebServer.FileHandler createIfCanHandle(String uri) {
		return new FileHandler(uri, playlist);
	}
}
