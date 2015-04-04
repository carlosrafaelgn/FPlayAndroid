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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Message;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.BluetoothConnectionManager;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public class BluetoothVisualizerJni extends RelativeLayout implements Visualizer, MenuItem.OnMenuItemClickListener, BluetoothConnectionManager.BluetoothObserver, MainHandler.Callback, View.OnClickListener, Runnable {
	private static final int MSG_UPDATE_PACKAGES = 0x0600;
	private static final int MSG_PLAYER_COMMAND = 0x0601;
	private static final int MSG_BLUETOOTH_RXTX_ERROR = 0x0602;

	private static final int[] FRAMES_TO_SKIP = { 0, 1, 2, 3, 4, 5, 9, 11, 14, 19, 29, 59 };

	private static final int MNU_SPEED0 = MNU_VISUALIZER + 1, MNU_SPEED1 = MNU_VISUALIZER + 2, MNU_SPEED2 = MNU_VISUALIZER + 3, MNU_SIZE_4 = MNU_VISUALIZER + 4, MNU_SIZE_8 = MNU_VISUALIZER + 5, MNU_SIZE_16 = MNU_VISUALIZER + 6, MNU_SIZE_32 = MNU_VISUALIZER + 7, MNU_SIZE_64 = MNU_VISUALIZER + 8, MNU_SIZE_128 = MNU_VISUALIZER + 9, MNU_SIZE_256 = MNU_VISUALIZER + 10;
	private static final int MNU_FRAMES_TO_SKIP = MNU_VISUALIZER + 100;

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

	private byte[] bfft;
	private final SlimLock lock;
	private BluetoothConnectionManager bt;
	private BgButton btnTutorial, btnStart, btnConnect;
	private TextView lblMsg;
	private int speed;
	private final AtomicInteger state;
	private final String bt_packages_sent, bt_start, bt_stop, bt_connect, bt_connecting, bt_disconnect;
	private volatile int size, packagesSent, version, framesToSkip, framesToSkipOriginal, stateVolume, stateSongPosition, stateSongLength;
	private volatile boolean connected, transmitting;
	private Activity activity;
	private long lastPlayerCommandTime;

	public BluetoothVisualizerJni(Context context, Activity activity, boolean landscape, Intent extras) {
		super(context);
		this.activity = activity;
		bfft = new byte[1024];
		lock = new SlimLock();
		state = new AtomicInteger();
		size = PayloadBins16;
		speed = 2;
		framesToSkip = 3;
		framesToSkipOriginal = 3;
		lastPlayerCommandTime = SystemClock.uptimeMillis();
		SimpleVisualizerJni.commonSetSpeed(speed);
		SimpleVisualizerJni.commonUpdateMultiplier(false);

		bt_packages_sent = activity.getText(R.string.bt_packages_sent).toString();
		bt_start = activity.getText(R.string.bt_start).toString();
		bt_stop = activity.getText(R.string.bt_stop).toString();
		bt_connect = activity.getText(R.string.bt_connect).toString();
		bt_connecting = activity.getText(R.string.bt_connecting).toString();
		bt_disconnect = activity.getText(R.string.bt_disconnect).toString();

		LayoutParams lp;
		lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		lp.addRule(CENTER_VERTICAL, TRUE);
		lblMsg = new TextView(context);
		lblMsg.setId(1);
		UI.mediumText(lblMsg);
		lblMsg.setTextColor(UI.colorState_text_visualizer_static);
		lblMsg.setGravity(Gravity.CENTER_HORIZONTAL);
		lblMsg.setLayoutParams(lp);

		lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.topMargin = UI._4dp;
		lp.addRule(CENTER_HORIZONTAL, TRUE);
		lp.addRule(BELOW, 1);
		btnConnect = new BgButton(context);
		btnConnect.setId(2);
		btnConnect.setText(bt_connect);
		btnConnect.setTextColor(UI.colorState_text_visualizer_reactive);
		btnConnect.setCompoundDrawables(new TextIconDrawable(UI.ICON_BLUETOOTH, UI.colorState_text_visualizer_reactive.getDefaultColor(), UI.defaultControlContentsSize), null, null, null);
		btnConnect.setLayoutParams(lp);
		btnConnect.setOnClickListener(this);

		lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.topMargin = UI._4dp;
		lp.addRule(CENTER_HORIZONTAL, TRUE);
		lp.addRule(BELOW, 2);
		btnStart = new BgButton(context);
		btnStart.setId(3);
		btnStart.setText(bt_start);
		btnStart.setTextColor(UI.colorState_text_visualizer_reactive);
		btnStart.setCompoundDrawables(new TextIconDrawable(UI.ICON_VISUALIZER, UI.colorState_text_visualizer_reactive.getDefaultColor(), UI.defaultControlContentsSize), null, null, null);
		btnStart.setLayoutParams(lp);
		btnStart.setOnClickListener(this);
		btnStart.setVisibility(View.INVISIBLE);

		lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.topMargin = UI._8dp;
		lp.rightMargin = UI._8dp;
		lp.addRule(ALIGN_PARENT_TOP, TRUE);
		lp.addRule(ALIGN_PARENT_RIGHT, TRUE);
		btnTutorial = new BgButton(context);
		btnTutorial.setId(4);
		btnTutorial.setText(R.string.tutorial);
		btnTutorial.setTextColor(UI.colorState_text_visualizer_reactive);
		btnTutorial.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, UI.colorState_text_visualizer_reactive.getDefaultColor(), UI.defaultControlContentsSize), null, null, null);
		btnTutorial.setLayoutParams(lp);
		btnTutorial.setOnClickListener(this);

		addView(btnTutorial);
		addView(lblMsg);
		addView(btnConnect);
		addView(btnStart);
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		final int id = item.getItemId();
		switch (id) {
		case MNU_SPEED0:
		case MNU_SPEED1:
		case MNU_SPEED2:
			speed = id - MNU_SPEED0;
			SimpleVisualizerJni.commonSetSpeed(speed);
			break;
		case MNU_SIZE_4:
		case MNU_SIZE_8:
		case MNU_SIZE_16:
		case MNU_SIZE_32:
		case MNU_SIZE_64:
		case MNU_SIZE_128:
		case MNU_SIZE_256:
			size = PayloadBins4 + (id - MNU_SIZE_4);
			break;
		}
		if (id >= MNU_FRAMES_TO_SKIP && id < (MNU_FRAMES_TO_SKIP + FRAMES_TO_SKIP.length)) {
			final int f = FRAMES_TO_SKIP[id - MNU_FRAMES_TO_SKIP];
			framesToSkipOriginal = f;
			framesToSkip = f;
		}
		return true;
	}

	//Runs on the MAIN thread
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case BluetoothConnectionManager.REQUEST_ENABLE:
			switch (resultCode) {
			case Activity.RESULT_OK:
				if (lblMsg != null)
					lblMsg.setText("");
				if (bt != null) {
					final int err = bt.showDialogAndScan();
					if (err != BluetoothConnectionManager.OK)
						onBluetoothError(bt, err);
				}
				break;
			case Activity.RESULT_CANCELED:
				if (bt != null)
					onBluetoothError(bt, BluetoothConnectionManager.NOT_ENABLED);
				break;
			default:
				if (bt != null)
					onBluetoothError(bt, BluetoothConnectionManager.ERROR_CONNECTION);
				break;
			}
			break;
		}
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
		final Context ctx = getContext();
		UI.separator(menu, 1, 0);
		Menu s = menu.addSubMenu(1, 0, 1, R.string.bt_fps)
			.setIcon(new TextIconDrawable(UI.ICON_CLOCK));
		UI.prepare(s);
		for (int i = 0; i < FRAMES_TO_SKIP.length; i++) {
			final int f = FRAMES_TO_SKIP[i];
			s.add(1, MNU_FRAMES_TO_SKIP + i, i, Integer.toString(60 / (f + 1)))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((framesToSkipOriginal == f) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		}
		s = menu.addSubMenu(1, 0, 2, R.string.bt_sample_count)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		UI.prepare(s);
		s.add(1, MNU_SIZE_4, 0, "4")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins4) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_8, 1, "8")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins8) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_16, 2, "16")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins16) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_32, 3, "32")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins32) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_64, 4, "64")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins64) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_128, 5, "128")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins128) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_256, 6, "256")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == PayloadBins256) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		UI.separator(menu, 1, 3);
		menu.add(2, MNU_SPEED0, 0, ctx.getText(R.string.sustain) + ": 3")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed != 1 && speed != 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED1, 1, ctx.getText(R.string.sustain) + ": 2")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 1) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED2, 2, ctx.getText(R.string.sustain) + ": 1")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
	}

	//Runs on the MAIN thread
	@Override
	public void onClick() {
	}

	//Runs on the MAIN thread
	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex) {
		if (connected && bt != null)
			generateAndSendState();
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return false;
	}

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight) {
		return new Point(availableWidth, availableHeight);
	}

	//Runs on the MAIN thread (returned value MUST always be the same)
	@Override
	public boolean requiresHiddenControls() {
		return false;
	}

	//Runs on ANY thread (returned value MUST always be the same)
	@Override
	public int getDesiredPointCount() {
		return 1024;
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
				if (framesToSkip <= 0) {
					framesToSkip = framesToSkipOriginal;
					//WE MUST NEVER call any method from visualizer
					//while the player is not actually playing
					if (!playing)
						Arrays.fill(bfft, 0, 1024, (byte)0);
					else
						visualizer.getFft(bfft);
					bt.getOutputStream().write(bfft, 0, SimpleVisualizerJni.commonProcess(bfft, size));
					packagesSent++;
				} else {
					framesToSkip--;
				}
			}
			int stateI = state.getAndSet(0);
			if (stateI != 0) {
				//Build and send a Player state message
				bfft[0] = StartOfHeading;
				bfft[1] = (byte)MessagePlayerState;
				bfft[3] = 0;
				int len = 0;
				len = writeByte(bfft, len, stateI & 3);
				len = writeByte(bfft, len, stateVolume);
				stateI = stateSongPosition;
				len = writeByte(bfft, len, stateI);
				len = writeByte(bfft, len, stateI >> 8);
				len = writeByte(bfft, len, stateI >> 16);
				len = writeByte(bfft, len, stateI >> 24);
				stateI = stateSongLength;
				len = writeByte(bfft, len, stateI);
				len = writeByte(bfft, len, stateI >> 8);
				len = writeByte(bfft, len, stateI >> 16);
				len = writeByte(bfft, len, stateI >> 24);
				bfft[2] = (byte)(len << 1);
				bfft[4 + len] = EndOfTransmission;
				bt.getOutputStream().write(bfft, 0, len + 5);
				packagesSent++;
			}
		} catch (IOException ex) {
			//Bluetooth error
			if (connected)
				MainHandler.sendMessage(this, MSG_BLUETOOTH_RXTX_ERROR);
		} catch (Throwable ex) {
		} finally {
			lock.releaseLowPriority();
		}
	}

	//Runs on a SECONDARY thread
	@Override
	public void release() {
		version++;
		connected = false;
		transmitting = false;
	}

	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
		bfft = null;
		if (bt != null) {
			bt.destroy();
			bt = null;
		}
		btnTutorial = null;
		lblMsg = null;
		btnStart = null;
		btnConnect = null;
		activity = null;
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
		}
	}

	@Override
	public void onClick(View view) {
		if (view == btnTutorial) {
			try {
				if (activity != null)
					activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/carlosrafaelgn/FPlayArduino")));
			} catch (Throwable ex) {
			}
		} else if (view == btnConnect) {
			lock.lockHighPriority();
			try {
				version++;
				transmitting = false;
				if (lblMsg != null)
					lblMsg.setText("");
				if (btnStart != null)
					btnStart.setVisibility(View.INVISIBLE);
				btnConnect.setText(bt_connect);
				if (bt != null) {
					bt.destroy();
					bt = null;
				}
				if (connected) {
					connected = false;
				} else {
					btnConnect.setVisibility(View.INVISIBLE);
					bt = new BluetoothConnectionManager(activity, this);
					final int err = bt.showDialogAndScan();
					if (err != BluetoothConnectionManager.OK && err != BluetoothConnectionManager.NOT_ENABLED)
						onBluetoothError(bt, err);
				}
			} finally {
				lock.releaseHighPriority();
			}
		} else if (view == btnStart) {
			if (connected) {
				framesToSkip = framesToSkipOriginal;
				transmitting = !transmitting;
				btnStart.setText(transmitting ? bt_stop : bt_start);
			}
		}
	}

	@Override
	public void onBluetoothPairingStarted(BluetoothConnectionManager manager, String description, String address) {
		if (lblMsg != null)
			lblMsg.setText(bt_connecting);
	}

	@Override
	public void onBluetoothPairingFinished(BluetoothConnectionManager manager, String description, String address, boolean success) {
		if (lblMsg != null)
			lblMsg.setText("");
	}

	@Override
	public void onBluetoothCancelled(BluetoothConnectionManager manager) {
		if (lblMsg != null)
			lblMsg.setText("");
		if (btnConnect != null) {
			btnConnect.setText(bt_connect);
			btnConnect.setVisibility(View.VISIBLE);
		}
		if (btnStart != null)
			btnStart.setVisibility(View.INVISIBLE);
	}

	@Override
	public void onBluetoothConnected(BluetoothConnectionManager manager) {
		if (activity != null && Player.state == Player.STATE_INITIALIZED) {
			if (lblMsg != null) {
				lblMsg.setText(bt_packages_sent + " 0");
				MainHandler.sendMessageDelayed(this, MSG_UPDATE_PACKAGES, 1000);
			}
			packagesSent = 0;
			state.set(0);
			version++;
			connected = true;
			transmitting = false;
			generateAndSendState();
			(new Thread(this, "Bluetooth RX Thread")).start();
			if (btnConnect != null) {
				btnConnect.setText(bt_disconnect);
				btnConnect.setVisibility(View.VISIBLE);
			}
			if (btnStart != null) {
				btnStart.setText(bt_start);
				btnStart.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	public void onBluetoothError(BluetoothConnectionManager manager, int error) {
		version++;
		connected = false;
		transmitting = false;
		if (bt != null) {
			bt.destroy();
			bt = null;
		}
		if (lblMsg != null) {
			switch (error) {
			case BluetoothConnectionManager.NOT_ENABLED:
			case BluetoothConnectionManager.ERROR_NOT_ENABLED:
				lblMsg.setText(R.string.bt_needs_to_be_enabled);
				break;
			case BluetoothConnectionManager.ERROR_DISCOVERY:
				lblMsg.setText(R.string.bt_discovery_error);
				break;
			case BluetoothConnectionManager.ERROR_NOTHING_PAIRED:
				lblMsg.setText(R.string.bt_not_paired);
				break;
			case BluetoothConnectionManager.ERROR_STILL_PAIRING:
				lblMsg.setText(R.string.bt_pairing);
				break;
			case BluetoothConnectionManager.ERROR_CONNECTION:
				lblMsg.setText(R.string.bt_connection_error);
				break;
			case BluetoothConnectionManager.ERROR_COMMUNICATION:
				lblMsg.setText(R.string.bt_communication_error);
				break;
			default:
				lblMsg.setText(R.string.bt_not_supported);
				break;
			}
		}
		if (btnConnect != null) {
			btnConnect.setText(R.string.bt_connect);
			btnConnect.setVisibility(View.VISIBLE);
		}
		if (btnStart != null) {
			btnStart.setText(R.string.bt_start);
			btnStart.setVisibility(View.INVISIBLE);
		}
	}

	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
		case MSG_UPDATE_PACKAGES:
			if (connected && lblMsg != null && activity != null) {
				lblMsg.setText(bt_packages_sent + " " + packagesSent);
				MainHandler.sendMessageDelayed(this, MSG_UPDATE_PACKAGES, 1000);
			}
			break;
		case MSG_PLAYER_COMMAND:
			if (connected && Player.state == Player.STATE_INITIALIZED) {
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
						//simulate the actual button click
						if (!transmitting)
							onClick(btnStart);
						break;
					}
					break;
				case MessageStopBinTransmission:
					//simulate the actual button click
					if (transmitting)
						onClick(btnStart);
					break;
				case MessagePlayerCommand:
					//The minimum interval must not take
					//PayloadPlayerCommandUpdateState into account
					if (message.arg2 == PayloadPlayerCommandUpdateState) {
						generateAndSendState();
						break;
					}
					final long now = SystemClock.uptimeMillis();
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
						if (message.arg2 != PayloadPlayerCommandPlay || !Player.playing)
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
							Player.setGenericVolumePercent((message.arg2 & 0xff) >> 1);
						break;
					}
					break;
				}
			}
			break;
		case MSG_BLUETOOTH_RXTX_ERROR:
			if (connected)
				onBluetoothError(bt, BluetoothConnectionManager.ERROR_COMMUNICATION);
			break;
		}
		return true;
	}

	private void generateAndSendState() {
		final Song s = Player.currentSong;
		stateVolume = Player.getGenericVolumePercent();
		stateSongPosition = Player.getCurrentPosition();
		stateSongLength = ((s == null) ? -1 : s.lengthMS);
		state.set(4 |
			(Player.playing ? PayloadPlayerStateFlagPlaying : 0) |
			(Player.isCurrentSongPreparing() ? PayloadPlayerStateFlagLoading : 0));
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
}
