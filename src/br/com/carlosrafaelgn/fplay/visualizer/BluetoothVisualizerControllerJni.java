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
package br.com.carlosrafaelgn.fplay.visualizer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Message;
import android.os.SystemClock;
import android.view.ContextMenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BackgroundActivityMonitor;
import br.com.carlosrafaelgn.fplay.util.BluetoothConnectionManager;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public final class BluetoothVisualizerControllerJni implements Visualizer, BluetoothConnectionManager.BluetoothObserver, MainHandler.Callback, Runnable, FxVisualizer.FxVisualizerHandler {
	private static final int MSG_PLAYER_COMMAND = 0x0600;
	private static final int MSG_BLUETOOTH_RXTX_ERROR = 0x0601;

	private static final int FlagState = 0x07;
	private static final int FlagEscape = 0x08;

	private static final int StartOfHeading = 0x01;
	private static final int Escape = 0x1b;
	private static final int EndOfTransmission = 0x04;

	private static final int MessageBins4 = 0x20;
	private static final int MessageBins8 = 0x21;
	private static final int MessageBins16 = 0x22;
	private static final int MessageBins32 = 0x23;
	private static final int MessageBins64 = 0x24;
	private static final int MessageBins128 = 0x25;
	private static final int MessageBins256 = 0x26;
	private static final int MessageStartBinTransmission = 0x30;
	private static final int PayloadBins4 = MessageBins4;
	private static final int PayloadBins8 = MessageBins8;
	private static final int PayloadBins16 = MessageBins16;
	private static final int PayloadBins32 = MessageBins32;
	private static final int PayloadBins64 = MessageBins64;
	private static final int PayloadBins128 = MessageBins128;
	private static final int PayloadBins256 = MessageBins256;
	private static final int MessageStopBinTransmission = 0x31;
	private static final int MessagePlayerCommand = 0x32;
	private static final int PayloadPlayerCommandUpdateState = 0x00;
	private static final int PayloadPlayerCommandPrevious = 0x58; //KeyEvent.KEYCODE_MEDIA_PREVIOUS
	private static final int PayloadPlayerCommandPlayPause = 0x55; //KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
	private static final int PayloadPlayerCommandNext = 0x57; //KeyEvent.KEYCODE_MEDIA_NEXT
	private static final int PayloadPlayerCommandPlay = 0x7e; //KeyEvent.KEYCODE_MEDIA_PLAY
	private static final int PayloadPlayerCommandPause = 0x56; //KeyEvent.KEYCODE_MEDIA_STOP
	private static final int PayloadPlayerCommandIncreaseVolume = 0x18; //KeyEvent.KEYCODE_VOLUME_UP
	private static final int PayloadPlayerCommandDecreaseVolume = 0x19; //KeyEvent.KEYCODE_VOLUME_DOWN
	private static final int PayloadPlayerCommandSetVolume = 0xd1; //KeyEvent.KEYCODE_MUSIC
	private static final int MessagePlayerState = 0x33;
	private static final int PayloadPlayerStateFlagPlaying = 0x01;
	private static final int PayloadPlayerStateFlagLoading = 0x02;

	private FxVisualizer fxVisualizer;
	private byte[] waveform;
	private final SlimLock lock;
	private final AtomicInteger state;
	private BluetoothConnectionManager bt;
	private volatile int size, packetsSent, version, framesToSkip, framesToSkipOriginal, stateVolume, stateSongPosition, stateSongLength, dataType;
	private volatile boolean connected, transmitting;
	private boolean jniCalled, startTransmissionOnConnection;
	private int lastPlayerCommandTime, ignoreInput;

	public BluetoothVisualizerControllerJni(ActivityHost activity, boolean startTransmissionOnConnection) {
		waveform = new byte[Visualizer.CAPTURE_SIZE];
		lock = new SlimLock();
		state = new AtomicInteger();
		this.startTransmissionOnConnection = startTransmissionOnConnection;
		lastPlayerCommandTime = (int)SystemClock.uptimeMillis();
		ignoreInput = 0;
		Player.bluetoothVisualizerLastErrorMessage = 0;
		Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING;
		bt = new BluetoothConnectionManager(this);
		final int err = bt.showDialogAndScan(activity);
		if (err != BluetoothConnectionManager.OK)
			onBluetoothError(bt, err);
		else
			BackgroundActivityMonitor.start(activity);
	}

	public int getPacketsSent() {
		return packetsSent;
	}

	public void syncSize() {
		this.size = PayloadBins4 + Player.getBluetoothVisualizerSize();
	}

	public void syncSpeed() {
		SimpleVisualizerJni.commonSetSpeed(Player.getBluetoothVisualizerSpeed());
	}

	public void syncFramesToSkip() {
		framesToSkipOriginal = Player.getBluetoothVisualizerFramesToSkip();
		framesToSkip = framesToSkipOriginal;
	}

	public void syncDataType() {
		dataType = (Player.isBluetoothUsingVUMeter() ? (DATA_FFT | DATA_VUMETER | BEAT_DETECTION_2) : DATA_FFT);
		if (fxVisualizer != null)
			fxVisualizer.updateVisualizerDataType();
	}

	public void startTransmission() {
		if (connected && !transmitting) {
			if (!jniCalled) {
				jniCalled = true;
				SimpleVisualizerJni.commonUpdateMultiplier(false);
				syncSize();
				syncSpeed();
				syncFramesToSkip();
				syncDataType();
			} else {
				framesToSkip = framesToSkipOriginal;
			}
			transmitting = true;
			Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING;
		}
	}

	public void stopTransmission() {
		if (connected && transmitting) {
			transmitting = false;
			Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED;
		}
	}

	public void playingChanged() {
		if (fxVisualizer != null)
			fxVisualizer.playingChanged();
		if (connected)
			generateAndSendState();
	}

	public void pause() {
		if (fxVisualizer != null)
			fxVisualizer.pause();
	}

	public void resume() {
		if (fxVisualizer != null)
			fxVisualizer.resume();
	}

	public void resetAndResume() {
		if (fxVisualizer != null)
			fxVisualizer.resetAndResume();
	}

	public void destroy() {
		if (waveform != null) {
			version++;
			connected = false;
			transmitting = false;
			Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_INITIAL;
			lock.lockHighPriority();
			try {
				waveform = null;
				if (bt != null) {
					bt.destroy();
					bt = null;
				}
			} finally {
				lock.releaseHighPriority();
			}
			if (fxVisualizer != null) {
				fxVisualizer.destroy();
				fxVisualizer = null;
			}
			Player.stopBluetoothVisualizer();
			BackgroundActivityMonitor.bluetoothEnded();
		}
	}

	//Runs on the MAIN thread
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
	public void onCreateContextMenu(ContextMenu menu) {
	}

	//Runs on the MAIN thread
	@Override
	public void onClick() {
	}

	//Runs on the MAIN thread
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return false;
	}

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight) {
		return null;
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean requiresHiddenControls() {
		return false;
	}

	//Runs on ANY thread
	@Override
	public int dataTypeRequired() {
		return dataType;
	}

	//Runs on a SECONDARY thread
	@Override
	public void load(Context context) {
		SimpleVisualizerJni.commonCheckNeonMode();
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
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing) {
		if (!lock.lockLowPriority())
			return;
		try {
			if (transmitting) {
				//We use ignoreInput, because sampling 1024, 60 times a seconds,
				//is useless, as there are only 44100 or 48000 samples in one second
				if (ignoreInput == 0) {
					//WE MUST NEVER call any method from visualizer
					//while the player is not actually playing
					if (!playing)
						Arrays.fill(waveform, 0, 1024, (byte)0x80);
					else
						visualizer.getWaveForm(waveform);
				}
				if (framesToSkip <= 0) {
					framesToSkip = framesToSkipOriginal;
					bt.getOutputStream().write(waveform, 0, SimpleVisualizerJni.commonProcess(waveform, size | ignoreInput | dataType));
					packetsSent++;
				} else {
					SimpleVisualizerJni.commonProcess(waveform, ignoreInput | dataType);
					framesToSkip--;
				}
				ignoreInput ^= IGNORE_INPUT;
			}
			int stateI = state.getAndSet(0);
			if (stateI != 0) {
				//Build and send a Player state message
				waveform[0] = StartOfHeading;
				waveform[1] = (byte)MessagePlayerState;
				waveform[3] = 0;
				int len = 0;
				len = writeByte(waveform, len, stateI & 3);
				len = writeByte(waveform, len, stateVolume);
				stateI = stateSongPosition;
				len = writeByte(waveform, len, stateI);
				len = writeByte(waveform, len, stateI >> 8);
				len = writeByte(waveform, len, stateI >> 16);
				len = writeByte(waveform, len, stateI >> 24);
				stateI = stateSongLength;
				len = writeByte(waveform, len, stateI);
				len = writeByte(waveform, len, stateI >> 8);
				len = writeByte(waveform, len, stateI >> 16);
				len = writeByte(waveform, len, stateI >> 24);
				waveform[2] = (byte)(len << 1);
				waveform[4 + len] = EndOfTransmission;
				bt.getOutputStream().write(waveform, 0, len + 5);
				packetsSent++;
			}
		} catch (IOException ex) {
			//Bluetooth error
			if (connected)
				MainHandler.sendMessage(this, MSG_BLUETOOTH_RXTX_ERROR);
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
	public void onBluetoothPairingStarted(BluetoothConnectionManager manager, String description, String address) {
	}

	@Override
	public void onBluetoothPairingFinished(BluetoothConnectionManager manager, String description, String address, boolean success) {
	}

	@Override
	public void onBluetoothCancelled(BluetoothConnectionManager manager) {
		Player.stopBluetoothVisualizer();
	}

	@Override
	public void onBluetoothConnected(BluetoothConnectionManager manager) {
		if (fxVisualizer == null && bt != null && Player.state == Player.STATE_ALIVE) {
			packetsSent = 0;
			version++;
			connected = true;
			transmitting = false;
			Player.bluetoothVisualizerState = Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED;
			generateAndSendState();
			(new Thread(this, "Bluetooth RX Thread")).start();
			fxVisualizer = new FxVisualizer(null, this, this);
			if (startTransmissionOnConnection)
				startTransmission();
		}
	}

	@Override
	public void onBluetoothError(BluetoothConnectionManager manager, int error) {
		switch (error) {
		case BluetoothConnectionManager.NOT_ENABLED:
		case BluetoothConnectionManager.ERROR_NOT_ENABLED:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_needs_to_be_enabled;
			break;
		case BluetoothConnectionManager.ERROR_DISCOVERY:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_discovery_error;
			break;
		case BluetoothConnectionManager.ERROR_NOTHING_PAIRED:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_not_paired;
			break;
		case BluetoothConnectionManager.ERROR_STILL_PAIRING:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_pairing;
			break;
		case BluetoothConnectionManager.ERROR_CONNECTION:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_connection_error;
			break;
		case BluetoothConnectionManager.ERROR_COMMUNICATION:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_communication_error;
			break;
		default:
			Player.bluetoothVisualizerLastErrorMessage = R.string.bt_not_supported;
			break;
		}
		Player.stopBluetoothVisualizer();
	}

	@Override
	public void run() {
		final int myVersion = version;
		try {
			final InputStream inputStream = bt.getInputStream();
			int state = 0, payloadLength = 0, payload = 0, currentMessage = 0;
			while (connected && myVersion == version) {
				final int data = inputStream.read();
				if (data == StartOfHeading) {
					//Restart the state machine
					state &= (~(FlagEscape | FlagState));
					continue;
				}
				switch ((state & FlagState)) {
				case 0:
					//This byte should be the message type
					switch (data) {
					case MessageStartBinTransmission:
					case MessageStopBinTransmission:
					case MessagePlayerCommand:
						//Take the state machine to its next state
						currentMessage = data;
						state++;
						continue;
					default:
						//Take the state machine to its error state
						state |= FlagState;
						continue;
					}
				case 1:
					//This should be payload length's first byte
					//(bits 0 - 6 left shifted by 1)
					if ((data & 0x01) != 0) {
						//Take the state machine to its error state
						state |= FlagState;
					} else {
						payloadLength = data >> 1;
						//Take the state machine to its next state
						state++;
					}
					continue;
				case 2:
					//This should be payload length's second byte
					//(bits 7 - 13 left shifted by 1)
					if ((data & 0x01) != 0) {
						//Take the state machine to its error state
						state |= FlagState;
					} else {
						payloadLength |= (data << 6);

						if (currentMessage == MessageStopBinTransmission) {
							if (payloadLength != 0) {
								//Take the state machine to its error state
								state |= FlagState;
								continue;
							}
							// Skip two states as this message has no payload
							state += 2;
						} else {
							if (payloadLength != 1) {
								if (currentMessage != MessagePlayerCommand || payloadLength != 2) {
									//Take the state machine to its error state
									state |= FlagState;
									continue;
								}
							}
							//Take the state machine to its next state
							state++;
							payload = 0;
						}
					}
					continue;
				case 3:
					//We are receiving the payload

					if (data == Escape) {
						//Until this date, the only payloads which are
						//valid for reception do not include escapable bytes...

						//Take the state machine to its error state
						state |= FlagState;
						continue;
					}

					if (currentMessage == MessagePlayerCommand) {
						payload = (payload << 8) | data;
						payloadLength--;

						//Keep the machine in state 3
						if (payloadLength > 0)
							continue;
					} else {
						payload = data;
					}

					//For now, the only payload received is 1 byte long
					state++;
					continue;
				case 4:
					//Take the state machine to its error state
					state |= FlagState;

					//Sanity check: data should be EoT
					if (data == EndOfTransmission)
						//Message correctly received
						MainHandler.sendMessage(this, MSG_PLAYER_COMMAND, currentMessage, payload);
				}
			}
		} catch (IOException ex) {
			//Bluetooth error
			if (connected)
				MainHandler.sendMessage(this, MSG_BLUETOOTH_RXTX_ERROR);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
		case MSG_PLAYER_COMMAND:
			if (connected && Player.state == Player.STATE_ALIVE) {
				switch (message.arg1) {
				case MessageStartBinTransmission:
					switch (message.arg2) {
					case PayloadBins4:
					case PayloadBins8:
					case PayloadBins16:
					case PayloadBins32:
					case PayloadBins64:
					case PayloadBins128:
					case PayloadBins256:
						size = message.arg2;
						startTransmission();
						break;
					}
					break;
				case MessageStopBinTransmission:
					stopTransmission();
					break;
				case MessagePlayerCommand:
					//The minimum interval must not take
					//PayloadPlayerCommandUpdateState into account
					if (message.arg2 == PayloadPlayerCommandUpdateState) {
						generateAndSendState();
						break;
					}
					final int now = (int)SystemClock.uptimeMillis();
					//Minimum interval accepted between commands
					if ((now - lastPlayerCommandTime) < 50)
						break;
					lastPlayerCommandTime = now;
					switch (message.arg2) {
					case PayloadPlayerCommandPrevious:
					case PayloadPlayerCommandPlayPause:
					case PayloadPlayerCommandNext:
					case PayloadPlayerCommandPause:
					case PayloadPlayerCommandPlay:
						if (message.arg2 != PayloadPlayerCommandPlay || !Player.localPlaying)
							Player.handleMediaButton(message.arg2);
						break;
					case PayloadPlayerCommandIncreaseVolume:
						Player.increaseVolume();
						break;
					case PayloadPlayerCommandDecreaseVolume:
						Player.decreaseVolume();
						break;
					default:
						if ((message.arg2 >> 8) == PayloadPlayerCommandSetVolume)
							Player.setVolumeInPercentage((message.arg2 & 0xff) >> 1);
						break;
					}
					break;
				}
			}
			break;
		case MSG_BLUETOOTH_RXTX_ERROR:
			if (connected && bt != null)
				onBluetoothError(bt, BluetoothConnectionManager.ERROR_COMMUNICATION);
			break;
		}
		return true;
	}

	private void generateAndSendState() {
		final Song s = Player.localSong;
		stateVolume = Player.getVolumeInPercentage();
		stateSongPosition = Player.getPosition();
		stateSongLength = ((s == null) ? -1 : s.lengthMS);
		state.set(4 |
			(Player.localPlaying ? PayloadPlayerStateFlagPlaying : 0) |
			(Player.isPreparing() ? PayloadPlayerStateFlagLoading : 0));
	}

	private static int writeByte(byte[] message, int payloadIndex, int x) {
		x &= 0xff;
		if (x == StartOfHeading || x == Escape) {
			message[4 + payloadIndex] = Escape;
			message[5 + payloadIndex] = (byte)(x ^ 1);
			return payloadIndex + 2;
		}
		message[4 + payloadIndex] = (byte)x;
		return payloadIndex + 1;
	}

	@Override
	public void onFailure() {
		Player.bluetoothVisualizerLastErrorMessage = R.string.visualizer_not_supported;
		Player.stopBluetoothVisualizer();
	}

	@Override
	public void onFinalCleanup() {
	}
}
