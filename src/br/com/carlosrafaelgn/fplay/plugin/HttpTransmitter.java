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

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.playback.Player;

public final class HttpTransmitter implements FPlayPlugin.Observer {
	private static final int PLUGIN_MSG_START = 0x0001;
	private static final int PLUGIN_MSG_ERROR_MESSAGE = 0x0002;
	private static final int PLUGIN_MSG_GET_ADDRESS = 0x0003;
	private static final int PLUGIN_MSG_GET_ENCODED_ADDRESS = 0x0004;
	private static final int PLUGIN_MSG_REFRESH_LIST = 0x0005;

	private FPlayPlugin plugin;
	private String[] address;

	public static boolean create(FPlayPlugin plugin, ActivityHost activityHost) {
		if (plugin == null)
			return false;

		Player.stopAllBackgroundPlugins();
		Player.httpTransmitter = new HttpTransmitter(plugin);

		try {
			if (plugin.message(PLUGIN_MSG_START, 0, 0, null) == 1) {
				Player.httpTransmitterLastErrorMessage = null;
				if (Player.backgroundMonitor != null)
					Player.backgroundMonitor.backgroundMonitorStart();
				return true;
			} else {
				Player.stopHttpTransmitter();
				Player.httpTransmitterLastErrorMessage = activityHost.getText(R.string.transmission_error).toString();
			}
		} catch (Throwable ex) {
			Player.stopHttpTransmitter();
			Player.httpTransmitterLastErrorMessage = activityHost.getText(!Player.isInternetConnectedViaWiFi() ?
				R.string.error_wifi :
				(!Player.isConnectedToTheInternet() ?
					R.string.error_connection :
					R.string.error_gen)).toString();
		}

		return false;
	}

	private HttpTransmitter(FPlayPlugin plugin) {
		this.plugin = plugin;
		this.address = new String[1];
		plugin.setObserver(this);
	}

	@Override
	public int message(int message, int arg1, int arg2, Object obj) {
		if (plugin == null)
			return 0;

		if (message == PLUGIN_MSG_ERROR_MESSAGE)
			Player.httpTransmitterLastErrorMessage = (obj == null ? null : obj.toString());

		return 0;
	}

	public void destroy() {
		final FPlayPlugin plugin = this.plugin;
		this.plugin = null;
		address = null;

		Player.httpTransmitter = null;

		if (plugin != null)
			plugin.destroy();

		if (Player.backgroundMonitor != null)
			Player.backgroundMonitor.backgroundMonitorHttpEnded();
	}

	public String getAddress() {
		if (plugin == null || address == null)
			return "";
		plugin.message(PLUGIN_MSG_GET_ADDRESS, 0, 0, address);
		return (address[0] == null ? "" : address[0]);
	}

	public String getEncodedAddress() {
		if (plugin == null || address == null)
			return "";
		plugin.message(PLUGIN_MSG_GET_ENCODED_ADDRESS, 0, 0, address);
		return (address[0] == null ? "" : address[0]);
	}

	public void refreshList() {
		if (plugin == null)
			return;
		plugin.message(PLUGIN_MSG_REFRESH_LIST, 0, 0, null);
	}
}
