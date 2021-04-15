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

import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.plugin.wirelessvisualizer.Plugin;

@SuppressWarnings("unused")
public final class WirelessVisualizer implements FPlayPlugin.Observer {
	public static final int REQUEST_ENABLE = 0x1000;

	private static final int PLUGIN_MSG_START = 0x0001;
	private static final int PLUGIN_MSG_START_TRANSMISSION = 0x0002;
	private static final int PLUGIN_MSG_STOP_TRANSMISSION = 0x0003;
	private static final int PLUGIN_MSG_PLAYING_CHANGED = 0x0004;
	private static final int PLUGIN_MSG_PAUSE = 0x0005;
	private static final int PLUGIN_MSG_RESUME = 0x0006;
	private static final int PLUGIN_MSG_RESET_AND_RESUME = 0x0007;
	private static final int PLUGIN_MSG_GET_SENT_PACKETS = 0x0008;
	private static final int PLUGIN_MSG_SYNC_SIZE = 0x0009;
	private static final int PLUGIN_MSG_SYNC_SPEED = 0x000A;
	private static final int PLUGIN_MSG_SYNC_FRAMES_TO_SKIP = 0x000B;
	private static final int PLUGIN_MSG_SYNC_DATA_TYPE = 0x000C;

	private static final int PLUGIN_MSG_OBSERVER_STATE_CHANGED = 0x0101;
	private static final int PLUGIN_MSG_OBSERVER_STOP = 0x0102;
	private static final int PLUGIN_MSG_OBSERVER_ERROR_MESSAGE = 0x0103;

	private static final int PLUGIN_MSG_OBSERVER_STATE_CHANGED_CONNECTED = 0x0001;
	private static final int PLUGIN_MSG_OBSERVER_STATE_CHANGED_TRANSMITTING = 0x0002;

	private FPlayPlugin plugin;

	public static void create(ActivityHost activityHost, boolean startTransmissionOnConnection) {
		final FPlayPlugin plugin = new Plugin();
		plugin.init(activityHost, PluginManager.getFPlay());
		final WirelessVisualizer wirelessVisualizer;

		Player.stopAllBackgroundPlugins();
		Player.bluetoothVisualizer = (wirelessVisualizer = new WirelessVisualizer(plugin));

		if (plugin.message(PLUGIN_MSG_START, startTransmissionOnConnection ? 1 : 0, 0, activityHost) == 1) {
			wirelessVisualizer.syncSize();
			wirelessVisualizer.syncSpeed();
			wirelessVisualizer.syncFramesToSkip();
			wirelessVisualizer.syncDataType();
			Player.bluetoothVisualizerLastErrorMessage = null;
			Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING;
			if (Player.backgroundMonitor != null)
				Player.backgroundMonitor.backgroundMonitorStart();
		}
	}

	private WirelessVisualizer(FPlayPlugin plugin) {
		this.plugin = plugin;
		plugin.setObserver(this);
	}

	@Override
	public int message(int message, int arg1, int arg2, Object obj) {
		if (plugin == null)
			return 0;

		switch (message) {
		case PLUGIN_MSG_OBSERVER_STATE_CHANGED:
			switch (arg1) {
			case PLUGIN_MSG_OBSERVER_STATE_CHANGED_CONNECTED:
				Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED;
				break;
			case PLUGIN_MSG_OBSERVER_STATE_CHANGED_TRANSMITTING:
				Player.setBluetoothVisualizerSize(arg2 & 0x07);
				Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING;
				break;
			}
			break;
		case PLUGIN_MSG_OBSERVER_STOP:
			Player.stopBluetoothVisualizer();
			break;
		case PLUGIN_MSG_OBSERVER_ERROR_MESSAGE:
			Player.bluetoothVisualizerLastErrorMessage = ((obj == null) ? null : obj.toString());
			break;
		}

		return 0;
	}

	public void destroy() {
		final FPlayPlugin plugin = this.plugin;
		this.plugin = null;

		Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_INITIAL;
		Player.bluetoothVisualizer = null;

		if (plugin != null)
			plugin.destroy();

		if (Player.backgroundMonitor != null)
			Player.backgroundMonitor.backgroundMonitorBluetoothEnded();
	}

	public void update(boolean songHasChanged) {
		if (plugin != null) {
			if (!songHasChanged && Player.localPlaying)
				plugin.message(PLUGIN_MSG_RESET_AND_RESUME, 0, 0, null);
			else
				plugin.message(PLUGIN_MSG_RESUME, 0, 0, null);
			plugin.message(PLUGIN_MSG_PLAYING_CHANGED, 0, 0, null);
		}
	}

	public void startTransmission() {
		if (plugin == null)
			return;
		syncSize();
		syncSpeed();
		syncFramesToSkip();
		syncDataType();
		plugin.message(PLUGIN_MSG_START_TRANSMISSION, 0, 0, null);
	}

	public void stopTransmission() {
		if (plugin == null)
			return;
		plugin.message(PLUGIN_MSG_STOP_TRANSMISSION, 0, 0, null);
	}

	public int getSentPackets() {
		return ((plugin == null) ? 0 : plugin.message(PLUGIN_MSG_GET_SENT_PACKETS, 0, 0, null));
	}

	public void syncSize() {
		if (plugin != null)
			plugin.message(PLUGIN_MSG_SYNC_SIZE, Player.getBluetoothVisualizerSize(), 0, null);
	}

	public void syncSpeed() {
		if (plugin != null)
			plugin.message(PLUGIN_MSG_SYNC_SPEED, Player.getBluetoothVisualizerSpeed(), 0, null);
	}

	public void syncFramesToSkip() {
		if (plugin != null)
			plugin.message(PLUGIN_MSG_SYNC_FRAMES_TO_SKIP, Player.getBluetoothVisualizerFramesToSkip(), 0, null);
	}

	public void syncDataType() {
		if (plugin != null)
			plugin.message(PLUGIN_MSG_SYNC_DATA_TYPE, Player.isBluetoothUsingVUMeter() ? 1 : 0, 0, null);
	}
}
