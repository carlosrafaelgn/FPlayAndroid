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
package br.com.carlosrafaelgn.fplay.plugin.wirelessvisualizer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.plugin.FPlay;
import br.com.carlosrafaelgn.fplay.plugin.FPlayPlugin;
import br.com.carlosrafaelgn.fplay.plugin.SlimLock;
import br.com.carlosrafaelgn.fplay.plugin.SongInfo;
import br.com.carlosrafaelgn.fplay.plugin.Visualizer;
import br.com.carlosrafaelgn.fplay.plugin.VisualizerService;
import br.com.carlosrafaelgn.iotdcp.IoTClient;
import br.com.carlosrafaelgn.iotdcp.IoTDevice;

@SuppressWarnings("unused")
public class PluginIoT implements FPlayPlugin, Visualizer, IoTClient.Observer, Handler.Callback, Runnable, VisualizerService.Observer {

	private Context pluginContext;
	private FPlay fplay;
	private Observer observer;
	private VisualizerService visualizerService;
	private byte[] waveform;
	private SlimLock lock;
	private AtomicInteger state;
	private volatile int size, packetsSent, version, framesToSkip, framesToSkipOriginal, stateVolume, dataType;
	private volatile boolean connected, transmitting;
	private boolean jniCalled, startTransmissionOnConnection;
	private int lastPlayerCommandTime, ignoreInput;

	@Override
	public int getApiVersion() {
		return API_VERSION;
	}

	@Override
	public int getPluginVersion() {
		return 1;
	}

	@Override
	public void init(Object pluginContext, FPlay fplay) {
		this.pluginContext = (Context)pluginContext;
		this.fplay = fplay;
	}

	@Override
	public void setObserver(Observer observer) {
		this.observer = observer;
	}

	@Override
	public void unload() {
	}

	@Override
	public int message(int message, int arg1, int arg2, Object obj) {
		if (pluginContext == null || fplay == null)
			return 0;

		switch (message) {
		}

		return 0;
	}

	public void destroy() {
		if (waveform != null) {
			version++;
			connected = false;
			transmitting = false;
			lock.lockHighPriority();
			try {
				waveform = null;
				//if (bt != null) {
				//	bt.destroy();
				//	bt = null;
				//}
			} finally {
				lock.releaseHighPriority();
			}
			if (visualizerService != null) {
				visualizerService.destroy();
				visualizerService = null;
			}
		}
		pluginContext = null;
		fplay = null;
		observer = null;
	}

	//Runs on the MAIN thread
	@Override
	public void onActivityResult(int requestCode, int resultCode, Object intent) {
	}

	//Runs on the MAIN thread
	@Override
	public void onActivityPause() {
	}

	//Runs on the MAIN thread
	@Override
	public void onActivityResume() {
	}

	//Runs on the MAIN thread
	@Override
	public void onCreateContextMenu(Object contextMenu) {
	}

	//Runs on the MAIN thread
	@Override
	public void onClick() {
	}

	//Runs on the MAIN thread
	@Override
	public void onPlayerChanged(SongInfo currentSongInfo, boolean songHasChanged, Throwable ex) {
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return false;
	}

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	@Override
	public Object getDesiredSize(int availableWidth, int availableHeight) {
		return null;
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean requiresHiddenControls() {
		return false;
	}

	//Runs on ANY thread
	@Override
	public int requiredDataType() {
		return dataType;
	}

	//Runs on ANY thread
	@Override
	public int requiredOrientation() {
		return ORIENTATION_NONE;
	}

	//Runs on a SECONDARY thread
	@Override
	public void load() {
	}

	//Runs on ANY thread
	@Override
	public boolean isLoading() {
		return false;
	}

	//Runs on ANY thread
	@Override
	public void cancelLoading() {
	}

	//Runs on the MAIN thread
	@Override
	public void configurationChanged(boolean landscape) {
	}

	//Runs on a SECONDARY thread
	@Override
	public void processFrame(boolean playing, byte[] waveform) {
		final FPlay fplay = this.fplay;
		if (fplay == null || !lock.lockLowPriority())
			return;
		try {
			if (transmitting) {
				//We use ignoreInput because taking 1024 samples, 60 times a seconds,
				//is useless, as there are only 44100 or 48000 samples in one second
				if (ignoreInput == 0 && !playing)
					Arrays.fill(waveform, (byte)0x80);
				if (framesToSkip <= 0) {
					framesToSkip = framesToSkipOriginal;
					//bt.getOutputStream().write(waveform, 0, fplay.visualizerProcess(waveform, size | ignoreInput | dataType));
					packetsSent++;
				} else {
					fplay.visualizerProcess(waveform, ignoreInput | dataType);
					framesToSkip--;
				}
				ignoreInput ^= IGNORE_INPUT;
			}
			int stateI = state.getAndSet(0);
			if (stateI != 0) {
				//Build and send a Player state message
				//waveform[0] = StartOfHeading;
				//waveform[1] = (byte)MessagePlayerState;
				//waveform[3] = 0;
				//int len = 0;
				//len = writeByte(waveform, len, stateI & 3);
				//len = writeByte(waveform, len, stateVolume);
				//stateI = stateSongPosition;
				//len = writeByte(waveform, len, stateI);
				//len = writeByte(waveform, len, stateI >> 8);
				//len = writeByte(waveform, len, stateI >> 16);
				//len = writeByte(waveform, len, stateI >> 24);
				//stateI = stateSongLength;
				//len = writeByte(waveform, len, stateI);
				//len = writeByte(waveform, len, stateI >> 8);
				//len = writeByte(waveform, len, stateI >> 16);
				//len = writeByte(waveform, len, stateI >> 24);
				//waveform[2] = (byte)(len << 1);
				//waveform[4 + len] = EndOfTransmission;
				//bt.getOutputStream().write(waveform, 0, len + 5);
				packetsSent++;
			}
		//} catch (IOException ex) {
		//	//Bluetooth error
		//	if (connected)
		//		fplay.sendMessage(this, MSG_BLUETOOTH_RXTX_ERROR);
		} catch (Throwable ex) {
			ex.printStackTrace();
		} finally {
			lock.releaseLowPriority();
		}
	}

	//Runs on a SECONDARY thread
	@Override
	public void release() {
	}

	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
	}

	@Override
	public void onException(IoTClient client, Throwable ex) {
	}

	@Override
	public void onMessageSent(IoTClient client, IoTDevice device, int message) {
	}

	@Override
	public void onQueryDevice(IoTClient client, IoTDevice device) {
	}

	@Override
	public void onChangePassword(IoTClient client, IoTDevice device, int responseCode, String password) {
	}

	@Override
	public void onHandshake(IoTClient client, IoTDevice device, int responseCode) {
	}

	@Override
	public void onPing(IoTClient client, IoTDevice device, int responseCode) {
	}

	@Override
	public void onReset(IoTClient client, IoTDevice device, int responseCode) {
	}

	@Override
	public void onGoodBye(IoTClient client, IoTDevice device, int responseCode) {
	}

	@Override
	public void onExecute(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int command) {
	}

	@Override
	public void onGetProperty(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int propertyIndex) {
	}

	@Override
	public void onSetProperty(IoTClient client, IoTDevice device, int responseCode, int interfaceIndex, int propertyIndex) {
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public void run() {
		Looper.prepare();
		//new IoTClient((Context)fplay.getApplicationContext())
	}

	@Override
	public boolean handleMessage(Message message) {
		if (fplay == null)
			return true;
		switch (message.what) {
		//case MSG_PLAYER_COMMAND:
		//	if (connected && fplay.isAlive()) {
		//		switch (message.arg1) {
		//		case MessageStartBinTransmission:
		//			switch (message.arg2) {
		//			case PayloadBins4:
		//			case PayloadBins8:
		//			case PayloadBins16:
		//			case PayloadBins32:
		//			case PayloadBins64:
		//			case PayloadBins128:
		//			case PayloadBins256:
		//				size = message.arg2;
		//				message(PLUGIN_MSG_START_TRANSMISSION, 0, 0, null);
		//				break;
		//			}
		//			break;
		//		case MessageStopBinTransmission:
		//			message(PLUGIN_MSG_STOP_TRANSMISSION, 0, 0, null);
		//			break;
		//		case MessagePlayerCommand:
		//			//The minimum interval must not take
		//			//PayloadPlayerCommandUpdateState into account
		//			if (message.arg2 == PayloadPlayerCommandUpdateState) {
		//				generateAndSendState();
		//				break;
		//			}
		//			final int now = (int)SystemClock.uptimeMillis();
		//			//Minimum interval accepted between commands
		//			if ((now - lastPlayerCommandTime) < 50)
		//				break;
		//			lastPlayerCommandTime = now;
		//			switch (message.arg2) {
		//			case PayloadPlayerCommandPrevious:
		//			case PayloadPlayerCommandPlayPause:
		//			case PayloadPlayerCommandNext:
		//			case PayloadPlayerCommandPause:
		//			case PayloadPlayerCommandPlay:
		//				if (message.arg2 != PayloadPlayerCommandPlay || !fplay.isPlaying())
		//					fplay.handleMediaButton(message.arg2);
		//				break;
		//			case PayloadPlayerCommandIncreaseVolume:
		//				fplay.increaseVolume();
		//				break;
		//			case PayloadPlayerCommandDecreaseVolume:
		//				fplay.decreaseVolume();
		//				break;
		//			default:
		//				if ((message.arg2 >> 8) == PayloadPlayerCommandSetVolume)
		//					fplay.setVolumeInPercentage((message.arg2 & 0xff) >> 1);
		//				break;
		//			}
		//			break;
		//		}
		//	}
		//	break;
		//case MSG_BLUETOOTH_RXTX_ERROR:
		//	if (connected && bt != null)
		//		onBluetoothError(bt, BluetoothConnectionManager.ERROR_COMMUNICATION);
		//	break;
		}
		return true;
	}

	@Override
	public void onFailure() {
		if (fplay == null || observer == null)
			return;
		//observer.message(PLUGIN_MSG_OBSERVER_ERROR_MESSAGE, 0, 0, fplay.getString(FPlay.STR_VISUALIZER_NOT_SUPPORTED));
		//observer.message(PLUGIN_MSG_OBSERVER_STOP, 0, 0, "");
	}

	@Override
	public void onFinalCleanup() {
	}
}
