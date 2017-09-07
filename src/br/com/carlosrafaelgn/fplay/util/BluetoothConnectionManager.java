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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.BaseItem;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgDialog;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.BgProgressBar;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.UI;

public final class BluetoothConnectionManager extends BroadcastReceiver implements Runnable, DialogInterface.OnClickListener, DialogInterface.OnCancelListener, DialogInterface.OnDismissListener, BaseList.ItemClickListener {
	public static final int REQUEST_ENABLE = 0x1000;

	public static final int OK = 0;
	public static final int NOT_ENABLED = 1;
	public static final int ERROR = -1;
	public static final int ERROR_NOT_ENABLED = -2;
	public static final int ERROR_DISCOVERY = -3;
	public static final int ERROR_NOTHING_PAIRED = -4;
	public static final int ERROR_STILL_PAIRING = -5;
	public static final int ERROR_CONNECTION = -6;
	public static final int ERROR_COMMUNICATION = -7;

	public interface BluetoothObserver {
		void onBluetoothPairingStarted(BluetoothConnectionManager manager, String description, String address);
		void onBluetoothPairingFinished(BluetoothConnectionManager manager, String description, String address, boolean success);
		void onBluetoothCancelled(BluetoothConnectionManager manager);
		void onBluetoothConnected(BluetoothConnectionManager manager);
		void onBluetoothError(BluetoothConnectionManager manager, int error);
	}

	private static final class DeviceItem extends BaseItem {
		public final String name, address;
		public final boolean paired, recentlyUsed;
		public final FileSt fileSt;

		public DeviceItem(String name, String address, boolean paired, boolean recentlyUsed) {
			this.name = name;
			this.address = address;
			this.paired = paired;
			this.recentlyUsed = recentlyUsed;
			this.fileSt = new FileSt(address, name + " - " + address, 0);
		}

		@Override
		public String toString() {
			return fileSt.name;
		}
	}

	private static final class DeviceList extends BaseList<DeviceItem> implements ArraySorter.Comparer<DeviceItem> {
		private final int scrollBarType;

		public DeviceList(int scrollBarType) {
			super(DeviceItem.class, 256);

			this.scrollBarType = scrollBarType;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			FileView view = (FileView)convertView;
			if (view == null)
				view = new FileView(Player.theApplication, false, false, scrollBarType);
			view.setItemState(items[position].fileSt, position, getItemState(position) | (items[position].recentlyUsed ? UI.STATE_CURRENT : 0), this, null, scrollBarType);
			return view;
		}

		@Override
		public int getViewHeight() {
			return FileView.getViewHeight(false);
		}

		@Override
		public int compare(DeviceItem a, DeviceItem b) {
			//if (a.recentlyUsed != b.recentlyUsed)
			//	return (a.recentlyUsed ? -1 : 1);
			return a.fileSt.name.compareToIgnoreCase(b.fileSt.name);
		}

		public void sort() {
			//synchronized (sync) {
				modificationVersion++;
				ArraySorter.sort(items, 0, this.count, this);
				current = -1;
				firstSel = -1;
				lastSel = -1;
				originalSel = -1;
				indexOfPreviouslyDeletedCurrentItem = -1;
			//}
			notifyDataSetChanged(-1, CONTENT_MOVED);
		}
	}

	private BluetoothObserver observer;
	private Activity activity;
	private BluetoothAdapter btAdapter;
	private BluetoothSocket btSocket;
	private InputStream inputStream;
	private OutputStream outputStream;
	private DeviceList deviceList;
	private TextView lblTitle;
	private BgProgressBar barWait;
	private BgListView listView;
	private BgDialog dialog;
	private boolean closed, connecting, finished, okToStartDiscovery;
	private int lastError;
	private volatile DeviceItem deviceItem;
	private volatile boolean cancelled;

	public BluetoothConnectionManager(BluetoothObserver observer) {
		this.observer = observer;
		try {
			btAdapter = BluetoothAdapter.getDefaultAdapter();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public void destroyUI() {
		if (btAdapter != null) {
			try {
				if (btAdapter.isDiscovering())
					btAdapter.cancelDiscovery();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			btAdapter = null;
		}
		if (listView != null) {
			listView.setAdapter(null);
			listView = null;
		}
		if (activity != null) {
			activity.unregisterReceiver(this);
			activity = null;
		}
		if (deviceList != null) {
			deviceList.setItemClickListener(null);
			deviceList = null;
		}
		lblTitle = null;
		barWait = null;
		dialog = null;
		deviceItem = null;
	}

	public void destroy() {
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			inputStream = null;
		}
		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			outputStream = null;
		}
		if (btSocket != null) {
			try {
				btSocket.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			btSocket = null;
		}
		destroyUI();
		observer = null;
	}

	@Override
	public void run() {
		final DeviceItem deviceItem = this.deviceItem;
		if (deviceItem == null)
			return;
		if (MainHandler.isOnMainThread()) {
			final boolean success = (inputStream != null && outputStream != null && lastError == OK);
			final boolean callObserver = !finished;
			finished = true;
			if (dialog != null)
				dialog.dismiss();
			destroyUI();
			connecting = false;
			if (observer != null && callObserver) {
				observer.onBluetoothPairingFinished(this, deviceItem.fileSt.name, deviceItem.address, success);
				if (cancelled)
					observer.onBluetoothCancelled(this);
				else if (lastError == OK)
					observer.onBluetoothConnected(this);
				else
					observer.onBluetoothError(this, lastError);
			}
			return;
		}
		try {
			BluetoothDevice device = btAdapter.getRemoteDevice(deviceItem.address);
			final UUID SERIAL_PORT_SERVICE_CLASS_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
			//Reflection obtained from http://pymasde.es/blueterm/
			btSocket = null;
			try {
				btSocket = (BluetoothSocket)device.getClass().getMethod("createInsecureRfcommSocket", int.class).invoke(device, 1);
			} catch (Throwable ex) {
				//try to create the socket in another way...
			}
			if (btSocket == null) {
				try {
					btSocket = (BluetoothSocket)device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
				} catch (Throwable ex) {
					//try to create the socket in another way...
				}
			}
			if (btSocket == null) {
				if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.HONEYCOMB)
					btSocket = device.createInsecureRfcommSocketToServiceRecord(SERIAL_PORT_SERVICE_CLASS_UUID);
				else
					btSocket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_SERVICE_CLASS_UUID);
			}
			try {
				btSocket.connect();
			} catch (Throwable ex) {
				btSocket = null;
				if (!deviceItem.paired) {
					lastError = ERROR_STILL_PAIRING;
					MainHandler.postToMainThread(this);
					return;
				}
				// try another method for connection, this should work on the HTC desire, credits to Michael Biermann
				try {
					Method mMethod = device.getClass().getMethod("createRfcommSocket", int.class);
					btSocket = (BluetoothSocket)mMethod.invoke(device, 1);
					btSocket.connect();
				} catch (Throwable e1){
					btSocket = null;
					lastError = ERROR_CONNECTION;
					MainHandler.postToMainThread(this);
					return;
				}
			}
			inputStream = btSocket.getInputStream();
			outputStream = btSocket.getOutputStream();
		} catch (Throwable ex) {
			lastError = (!deviceItem.paired ? ERROR_STILL_PAIRING : ERROR_CONNECTION);
			MainHandler.postToMainThread(this);
			return;
		}
		lastError = OK;
		MainHandler.postToMainThread(this);
	}

	public int showDialogAndScan(Activity activity) {
		if (btAdapter == null)
			return ERROR;
		try {
			if (!btAdapter.isEnabled()) {
				activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE);
				return NOT_ENABLED;
			}
		} catch (Throwable ex) {
			return ERROR_NOT_ENABLED;
		}

		this.activity = activity;
		activity.registerReceiver(this, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		activity.registerReceiver(this, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

		final LinearLayout l = new LinearLayout(activity);
		final LinearLayout panelControls = new LinearLayout(activity);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			UI.removeSplitTouch(l);
			UI.removeSplitTouch(panelControls);
		}
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBaselineAligned(false);
		panelControls.setOrientation(LinearLayout.VERTICAL);
		panelControls.setBaselineAligned(false);
		UI.prepareControlContainer(panelControls, false, true, 0, 0, 0, 0);

		lblTitle = UI.createDialogTextView(activity, 0, activity.getText(R.string.bt_scanning));
		lblTitle.setPadding(UI.dialogMargin, 0, UI.dialogMargin, UI.dialogMargin);
		lblTitle.setTextColor(UI.colorState_text_static);
		panelControls.addView(lblTitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		barWait = new BgProgressBar(activity);
		final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		p.leftMargin = UI.dialogMargin;
		p.rightMargin = UI.dialogMargin;
		p.bottomMargin = UI.dialogMargin;
		panelControls.addView(barWait, p);

		l.addView(panelControls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		final int scrollBarType;
		listView = new BgListView(activity);
		listView.setScrollBarType(scrollBarType = ((UI.browserScrollBarType == BgListView.SCROLLBAR_NONE) ? BgListView.SCROLLBAR_NONE : BgListView.SCROLLBAR_SYSTEM));
		l.addView(listView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		deviceList = new DeviceList(scrollBarType);
		deviceList.setItemClickListener(this);

		final Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices)
				deviceList.add(new DeviceItem(device.getName(), device.getAddress(), true, false), -1);
			deviceList.sort();
		}
		deviceList.setObserver(listView);
		try {
			if (btAdapter.isDiscovering())
				btAdapter.cancelDiscovery();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			btAdapter.startDiscovery();
		} catch (Throwable ex) {
			return ERROR_DISCOVERY;
		}

		dialog = new BgDialog(activity, l, this);
		dialog.setTitle(R.string.bt_devices);
		dialog.setPositiveButton(R.string.refresh_list);
		dialog.setNegativeButton(R.string.cancel);
		dialog.setOnCancelListener(this);
		dialog.setOnDismissListener(this);
		dialog.setEmptyBackground();
		dialog.removeTitleBorder();
		dialog.show();
		okToStartDiscovery = false;

		return OK;
	}

	private void stopDialogDiscovery() {
		try {
			if (btAdapter != null && btAdapter.isDiscovering())
				btAdapter.cancelDiscovery();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		if (lblTitle != null)
			lblTitle.setVisibility(View.GONE);
		if (barWait != null)
			barWait.setVisibility(View.GONE);
		okToStartDiscovery = true;
	}

	@Override
	public void onItemClicked(int position) {
		if (dialog != null && deviceList != null && position >= 0 && position < deviceList.getCount()) {
			final DeviceItem item = deviceList.getItem(position);
			if (item != null && item.address != null) {
				stopDialogDiscovery();
				deviceItem = item;
				if (lblTitle != null) {
					lblTitle.setText(R.string.bt_connecting);
					lblTitle.setVisibility(View.VISIBLE);
				}
				if (barWait != null)
					barWait.setVisibility(View.VISIBLE);
				if (listView != null)
					listView.setVisibility(View.GONE);
				okToStartDiscovery = false;
				if (observer != null)
					observer.onBluetoothPairingStarted(BluetoothConnectionManager.this, item.fileSt.name, item.address);
				lastError = OK;
				cancelled = false;
				finished = false;
				connecting = true;
				(new Thread(BluetoothConnectionManager.this, "Bluetooth Manager Thread")).start();
			}
		}
	}

	@Override
	public void onItemLongClicked(int position) {
	}

	@Override
	public void onItemCheckboxClicked(int position) {
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (activity == null || deviceList == null)
			return;
		final String action = intent.getAction();
		if (BluetoothDevice.ACTION_FOUND.equals(action)) {
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			final String address = device.getAddress();
			final String name = device.getName();
			boolean paired = false;
			for (int i = deviceList.getCount() - 1; i >= 0; i--) {
				final DeviceItem di = deviceList.getItem(i);
				if (di.address.equalsIgnoreCase(address)) {
					//an item with this same address/name is already on the list
					if (di.name.equalsIgnoreCase(name))
						return;
					paired = di.paired;
					deviceList.setSelection(i, true);
					deviceList.removeSelection();
					break;
				}
			}
			deviceList.add(new DeviceItem(((name == null || name.length() == 0) ? activity.getText(R.string.bt_null_device_name).toString() : name), address, paired, false), -1);
			deviceList.setSelection(-1, true);
			//stop sorting as the devices are discovered, to prevent the
			//order of the list from changing
			//deviceList.sort();
		} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
			if (!connecting) {
				stopDialogDiscovery();
				if (deviceList.getCount() == 0)
					deviceList.add(new DeviceItem(activity.getText(R.string.bt_not_found).toString(), null, false, false), -1);
			}
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case DialogInterface.BUTTON_POSITIVE:
			if (okToStartDiscovery && btAdapter != null && !connecting) {
				if (lblTitle != null)
					lblTitle.setVisibility(View.VISIBLE);
				if (barWait != null)
					barWait.setVisibility(View.VISIBLE);
				try {
					btAdapter.startDiscovery();
				} catch (Throwable ex) {
					return;
				}
				okToStartDiscovery = false;
			}
			break;
		case DialogInterface.BUTTON_NEGATIVE:
			cancelled = true;
			dialog.dismiss();
			break;
		}
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		if (activity != null)
			UI.reenableEdgeEffect(activity);
		if (!closed) {
			closed = true;
			destroyUI();
			if (!finished) {
				cancelled = true;
				finished = true;
				if (observer != null) {
					if (deviceItem != null)
						observer.onBluetoothPairingFinished(this, deviceItem.fileSt.name, deviceItem.address, false);
					observer.onBluetoothCancelled(this);
				}
			}
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		onDismiss(dialog);
	}
}
