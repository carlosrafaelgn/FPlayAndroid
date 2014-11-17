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
import android.os.Message;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgColorStateList;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.BluetoothConnectionManager;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public class BluetoothVisualizerJni extends RelativeLayout implements Visualizer, VisualizerView, MenuItem.OnMenuItemClickListener, BluetoothConnectionManager.BluetoothObserver, MainHandler.Callback, View.OnClickListener, Runnable {
	private static final int MSG_UPDATE_PACKAGES = 0x0600;
	private static final int MSG_PLAYER_COMMAND = 0x0601;

	private static final int MNU_SPEED0 = MNU_VISUALIZER + 1, MNU_SPEED1 = MNU_VISUALIZER + 2, MNU_SPEED2 = MNU_VISUALIZER + 3, MNU_SIZE_4 = MNU_VISUALIZER + 4, MNU_SIZE_8 = MNU_VISUALIZER + 5, MNU_SIZE_16 = MNU_VISUALIZER + 6, MNU_SIZE_32 = MNU_VISUALIZER + 7, MNU_SIZE_64 = MNU_VISUALIZER + 8, MNU_SIZE_128 = MNU_VISUALIZER + 9, MNU_SIZE_256 = MNU_VISUALIZER + 10;

	private static final int SOH = 0x01;
	private static final int ESC = 0x1B;
	private static final int EOT = 0x04;

	private static final int SIZE_4 = 0x30;
	private static final int SIZE_8 = 0x31;
	private static final int SIZE_16 = 0x32;
	private static final int SIZE_32 = 0x33;
	private static final int SIZE_64 = 0x34;
	private static final int SIZE_128 = 0x35;
	private static final int SIZE_256 = 0x36;
	private static final int PLAYER_COMMAND = 0x40;
	private static final int PLAYER_COMMAND_SEND_STATE = 0x00;
	private static final int PLAYER_COMMAND_PREVIOUS = 0x58; //KeyEvent.KEYCODE_MEDIA_PREVIOUS
	private static final int PLAYER_COMMAND_PLAY_PAUSE = 0x55; //KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
	private static final int PLAYER_COMMAND_NEXT = 0x57; //KeyEvent.KEYCODE_MEDIA_NEXT
	private static final int PLAYER_COMMAND_PLAY = 0x7E; //KeyEvent.KEYCODE_MEDIA_PLAY
	private static final int PLAYER_COMMAND_PAUSE = 0x56; //KeyEvent.KEYCODE_MEDIA_STOP
	private static final int PLAYER_COMMAND_INCREASE_VOLUME = 0x18; //KeyEvent.KEYCODE_VOLUME_UP
	private static final int PLAYER_COMMAND_DECREASE_VOLUME = 0x19; //KeyEvent.KEYCODE_VOLUME_DOWN
	private static final int PLAYER_STATE = 0x41;

	private byte[] bfft;
	private final SlimLock lock;
	private BluetoothConnectionManager bt;
	private BgButton btnStart, btnConnect;
	private TextView lblMsg;
	private int speed;
	private final AtomicLong state;
	private volatile int size, packagesSent, version, framesToSkip, framesToSkipOriginal;
	private volatile boolean connected, transmitting;
	private Activity activity;

	public BluetoothVisualizerJni(Context context, Activity activity, boolean landscape) {
		super(context);
		this.activity = activity;
		bfft = new byte[1024];
		lock = new SlimLock();
		state = new AtomicLong();
		size = SIZE_8;
		speed = 2;
		framesToSkip = 2;
		framesToSkipOriginal = 2;
		SimpleVisualizerJni.glChangeSpeed(speed);
		SimpleVisualizerJni.updateMultiplier(false);

		final BgColorStateList lblColor = (UI.useVisualizerButtonsInsideList ? UI.colorState_text_listitem_static : UI.colorState_text_static);
		final BgColorStateList btnColor = (UI.useVisualizerButtonsInsideList ? UI.colorState_text_listitem_reactive : UI.colorState_text_reactive);

		LayoutParams lp;
		lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		lp.addRule(CENTER_VERTICAL, TRUE);
		lblMsg = new TextView(context);
		lblMsg.setId(1);
		UI.mediumText(lblMsg);
		lblMsg.setTextColor(lblColor);
		lblMsg.setGravity(Gravity.CENTER_HORIZONTAL);
		lblMsg.setLayoutParams(lp);

		lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.topMargin = UI._4dp;
		lp.addRule(CENTER_HORIZONTAL, TRUE);
		lp.addRule(BELOW, 1);
		btnConnect = new BgButton(context);
		btnConnect.setId(2);
		btnConnect.setText(R.string.bt_connect);
		btnConnect.setTextColor(btnColor);
		btnConnect.setLayoutParams(lp);
		btnConnect.setOnClickListener(this);

		lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.topMargin = UI._4dp;
		lp.addRule(CENTER_HORIZONTAL, TRUE);
		lp.addRule(BELOW, 2);
		btnStart = new BgButton(context);
		btnStart.setId(3);
		btnStart.setText(R.string.bt_start);
		btnStart.setTextColor(btnColor);
		btnStart.setLayoutParams(lp);
		btnStart.setOnClickListener(this);
		btnStart.setVisibility(View.INVISIBLE);

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
			SimpleVisualizerJni.glChangeSpeed(speed);
			break;
		case MNU_SIZE_4:
		case MNU_SIZE_8:
		case MNU_SIZE_16:
		case MNU_SIZE_32:
		case MNU_SIZE_64:
		case MNU_SIZE_128:
		case MNU_SIZE_256:
			size = SIZE_4 + (id - MNU_SIZE_4);
			break;
		}
		return true;
	}

	//Runs on the MAIN thread
	@Override
	public VisualizerView getView() {
		return this;
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
	public void onCreateContextMenu(ContextMenu menu) {
		final Context ctx = getContext();
		UI.separator(menu, 1, 0);
		final Menu s = menu.addSubMenu(1, 0, 0, R.string.bt_sample_count)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		UI.prepare(s);
		s.add(1, MNU_SIZE_4, 0, "4")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_4) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_8, 1, "8")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_8) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_16, 2, "16")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_16) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_32, 3, "32")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_32) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_64, 4, "64")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_64) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_128, 5, "128")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_128) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s.add(1, MNU_SIZE_256, 6, "256")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((size == SIZE_256) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		UI.separator(menu, 1, 2);
		menu.add(2, MNU_SPEED0, 0, ctx.getText(R.string.speed) + ": 0")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed != 1 && speed != 2) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED1, 1, ctx.getText(R.string.speed) + ": 1")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((speed == 1) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		menu.add(2, MNU_SPEED2, 2, ctx.getText(R.string.speed) + ": 2")
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

	//Runs on ANY thread
	@Override
	public int getDesiredPointCount() {
		return 1024;
	}

	//Runs on a SECONDARY thread
	@Override
	public void load(Context context) {
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
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing, int deltaMillis) {
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
					bt.getOutputStream().write(bfft, 0, SimpleVisualizerJni.glOrBTProcess(bfft, deltaMillis, size));
					packagesSent++;
				} else {
					framesToSkip--;
				}
			}
			final long state64 = state.getAndSet(0);
			if (state64 != 0) {
				//Build and send a Player state message
				bfft[0] = SOH;
				bfft[1] = (byte)PLAYER_STATE;
				bfft[3] = 0;
				int state32 = (int)state64;
				int len = 0;
				len = writeByte(bfft, len, state32 & 3);
				state32 = ((state32 < 0) ? -1 : (state32 & ~7));
				len = writeByte(bfft, len, state32);
				len = writeByte(bfft, len, state32 >> 8);
				len = writeByte(bfft, len, state32 >> 16);
				len = writeByte(bfft, len, state32 >> 24);
				state32 = (int)(state64 >> 32);
				if (state32 < 0)
					state32 = -1;
				len = writeByte(bfft, len, state32);
				len = writeByte(bfft, len, state32 >> 8);
				len = writeByte(bfft, len, state32 >> 16);
				len = writeByte(bfft, len, state32 >> 24);
				bfft[2] = (byte)(len << 1);
				bfft[4 + len] = EOT;
				bt.getOutputStream().write(bfft, 0, len + 5);
			}
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

	//Runs on the MAIN thread (return value MUST always be the same)
	@Override
	public boolean isFullscreen() {
		return true;
	}

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight) {
		return new Point(availableWidth, availableHeight);
	}

	//Runs on the MAIN thread (AFTER Visualizer.release())
	@Override
	public void releaseView() {
		bfft = null;
		if (bt != null) {
			bt.destroy();
			bt = null;
		}
		lblMsg = null;
		btnStart = null;
		activity = null;
	}

	@Override
	public void run() {
		final int myVersion = version;
		try {
			final InputStream inputStream = bt.getInputStream();
			long lastCmdTime = SystemClock.uptimeMillis();
			int state = 0, payloadLength = 0, command = 0;
			boolean escaped = false;
			while (connected && myVersion == version) {
				final int data = inputStream.read();
				if (data == SOH) {
					state = 1;
				} else if (state != 0) {
					switch (state) {
					case 1:
						//Payload type
						state = ((data == PLAYER_COMMAND) ? 2 : 0);
						break;
					case 2:
						//Payload length (bits 0 - 6 left shifted by 1)
						payloadLength = data >> 1;
						state = 3;
						break;
					case 3:
						//Payload length (bits 7 - 13 left shifted by 1)
						payloadLength |= (data << 6);
						state = ((payloadLength == 1) ? 4 : 0);
						break;
					case 4:
						command = data;
						state = 5;
						break;
					case 5:
						state = 0;
						if (data == EOT) {
							//Command correctly received
							final long now = SystemClock.uptimeMillis();
							//Minimum interval accepted between commands
							if ((now - lastCmdTime) < 50)
								break;
							lastCmdTime = now;
							MainHandler.sendMessage(this, MSG_PLAYER_COMMAND, command, 0);
						}
						break;
					}
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void onClick(View view) {
		if (view == btnConnect) {
			lock.lockHighPriority();
			try {
				version++;
				transmitting = false;
				if (lblMsg != null)
					lblMsg.setText("");
				if (btnStart != null)
					btnStart.setVisibility(View.INVISIBLE);
				btnConnect.setText(R.string.bt_connect);
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
				btnStart.setText(transmitting ? R.string.bt_stop : R.string.bt_start);
			}
		}
	}

	@Override
	public void onBluetoothPairingStarted(BluetoothConnectionManager manager, String description, String address) {
		if (lblMsg != null)
			lblMsg.setText(R.string.bt_connecting);
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
			btnConnect.setText(R.string.bt_connect);
			btnConnect.setVisibility(View.VISIBLE);
		}
		if (btnStart != null)
			btnStart.setVisibility(View.INVISIBLE);
	}

	@Override
	public void onBluetoothConnected(BluetoothConnectionManager manager) {
		if (activity != null && Player.getState() == Player.STATE_INITIALIZED) {
			if (lblMsg != null) {
				lblMsg.setText(activity.getText(R.string.bt_packages_sent) + " 0");
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
				btnConnect.setText(R.string.bt_disconnect);
				btnConnect.setVisibility(View.VISIBLE);
			}
			if (btnStart != null) {
				btnStart.setText(R.string.bt_start);
				btnStart.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	public void onBluetoothError(BluetoothConnectionManager manager, int error) {
		version++;
		connected = false;
		transmitting = false;
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
				lblMsg.setText(activity.getText(R.string.bt_packages_sent).toString() + " " + packagesSent);
				MainHandler.sendMessageDelayed(this, MSG_UPDATE_PACKAGES, 1000);
			}
			break;
		case MSG_PLAYER_COMMAND:
			if (connected && Player.getState() == Player.STATE_INITIALIZED) {
				switch (message.arg1) {
				case PLAYER_COMMAND_SEND_STATE:
					generateAndSendState();
					break;
				case PLAYER_COMMAND_PREVIOUS:
				case PLAYER_COMMAND_PLAY_PAUSE:
				case PLAYER_COMMAND_NEXT:
				case PLAYER_COMMAND_PAUSE:
				case PLAYER_COMMAND_INCREASE_VOLUME:
				case PLAYER_COMMAND_DECREASE_VOLUME:
					Player.handleMediaButton(message.arg1);
					break;
				case PLAYER_COMMAND_PLAY:
					if (!Player.isPlaying())
						Player.handleMediaButton(message.arg1);
					break;
				}
			}
			break;
		}
		return true;
	}

	private void generateAndSendState() {
		final Song s = Player.getCurrentSong();
		state.set(4 |
			(Player.isPlaying() ? 1 : 0) |
			(Player.isCurrentSongPreparing() ? 2 : 0) |
			(((s == null) ? -1 : s.lengthMS) & ~7) |
			((long)Player.getCurrentPosition()) << 32);
	}

	private static int writeByte(byte[] message, int payloadIndex, int x) {
		if (x == SOH || x == ESC) {
			message[4 + payloadIndex] = ESC;
			message[5 + payloadIndex] = (byte)(x ^ 1);
			return payloadIndex + 2;
		}
		message[4 + payloadIndex] = (byte)x;
		return payloadIndex + 1;
	}
}
