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
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Arrays;

import javax.xml.validation.TypeInfoProvider;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgColorStateList;
import br.com.carlosrafaelgn.fplay.ui.BgTextView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.BluetoothConnectionManager;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public class BluetoothVisualizerJni extends RelativeLayout implements Visualizer, VisualizerView, MenuItem.OnMenuItemClickListener, BluetoothConnectionManager.BluetoothObserver, MainHandler.Callback, View.OnClickListener {
	private static final int MSG_UPDATE_PACKAGES = 0x0600;

	private static final int MNU_SPEED0 = MNU_VISUALIZER + 1, MNU_SPEED1 = MNU_VISUALIZER + 2, MNU_SPEED2 = MNU_VISUALIZER + 3, MNU_SIZE_4 = MNU_VISUALIZER + 4, MNU_SIZE_8 = MNU_VISUALIZER + 5, MNU_SIZE_16 = MNU_VISUALIZER + 6, MNU_SIZE_32 = MNU_VISUALIZER + 7, MNU_SIZE_64 = MNU_VISUALIZER + 8, MNU_SIZE_128 = MNU_VISUALIZER + 9, MNU_SIZE_256 = MNU_VISUALIZER + 10;

	private static final int SIZE_4 = (int)'0';
	private static final int SIZE_8 = (int)'1';
	private static final int SIZE_16 = (int)'2';
	private static final int SIZE_32 = (int)'3';
	private static final int SIZE_64 = (int)'4';
	private static final int SIZE_128 = (int)'5';
	private static final int SIZE_256 = (int)'6';

	private byte[] bfft;
	private final SlimLock lock;
	private BluetoothConnectionManager bt;
	private BgButton btnStart;
	private TextView lblMsg;
	private int speed;
	private volatile int size, packagesSent, interval;
	private volatile boolean connected;
	private Activity activity;

	public BluetoothVisualizerJni(Context context, Activity activity, boolean landscape) {
		super(context);
		this.activity = activity;
		bfft = new byte[1024];
		lock = new SlimLock();
		size = SIZE_8;
		speed = 2;
		SimpleVisualizerJni.glChangeSpeed(speed);
		interval = 50;
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
		btnStart = new BgButton(context);
		btnStart.setId(2);
		btnStart.setText(R.string.bt_start);
		btnStart.setTextColor(btnColor);
		btnStart.setLayoutParams(lp);
		btnStart.setOnClickListener(this);

		addView(lblMsg);
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
		final Menu s = menu.addSubMenu(1, 0, 0, R.string.bt_sample_count);
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
			if (connected) {
				//WE MUST NEVER call any method from visualizer
				//while the player is not actually playing
				if (!playing)
					Arrays.fill(bfft, 0, 1024, (byte)0);
				else
					visualizer.getFft(bfft);
				final int len = SimpleVisualizerJni.glOrBTProcess(bfft, deltaMillis, size);
				bt.getOutputStream().write(bfft, 0, len);
				packagesSent++;
				if (interval > deltaMillis)
					Thread.sleep(interval - deltaMillis - 1);
			}
		} catch (Throwable ex) {
		} finally {
			lock.releaseLowPriority();
		}
	}

	//Runs on a SECONDARY thread
	@Override
	public void release() {
		connected = false;
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
	public void onClick(View view) {
		if (view == btnStart) {
			lock.lockHighPriority();
			try {
				if (bt != null) {
					bt.destroy();
					bt = null;
				}
				if (lblMsg != null)
					lblMsg.setText("");
				btnStart.setText(R.string.bt_start);
				if (connected) {
					connected = false;
				} else {
					btnStart.setVisibility(View.INVISIBLE);
					bt = new BluetoothConnectionManager(activity, this);
					final int err = bt.showDialogAndScan();
					if (err != BluetoothConnectionManager.OK)
						onBluetoothError(bt, err);
				}
			} finally {
				lock.releaseHighPriority();
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
		if (btnStart != null) {
			btnStart.setText(R.string.bt_start);
			btnStart.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void onBluetoothConnected(BluetoothConnectionManager manager) {
		if (lblMsg != null && activity != null) {
			lblMsg.setText(activity.getText(R.string.bt_packages_sent) + " 0");
			MainHandler.sendMessageDelayed(this, MSG_UPDATE_PACKAGES, 1000);
		}
		packagesSent = 0;
		connected = true;
		if (btnStart != null) {
			btnStart.setText(R.string.bt_stop);
			btnStart.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void onBluetoothError(BluetoothConnectionManager manager, int error) {
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
		if (btnStart != null) {
			btnStart.setText(R.string.bt_start);
			btnStart.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public boolean handleMessage(Message message) {
		if (message.what == MSG_UPDATE_PACKAGES && connected && lblMsg != null && activity != null) {
			lblMsg.setText(activity.getText(R.string.bt_packages_sent).toString() + " " + packagesSent);
			MainHandler.sendMessageDelayed(this, MSG_UPDATE_PACKAGES, 1000);
		}
		return true;
	}
}
