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
package br.com.carlosrafaelgn.fplay;

import java.util.Formatter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.list.FileList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivityFileSelection extends ActivityBrowserView implements View.OnClickListener, DialogInterface.OnClickListener, BgListView.OnBgListViewKeyDownObserver {
	public static interface OnFileSelectionListener {
		public void onFileSelected(int id, String path, String name);
		public void onAddClicked(int id, String path, String name);
		public void onPlayClicked(int id, String path, String name);
	}
	
	private static final int MNU_LOAD = 100, MNU_SAVE = 101, MNU_SAVEAS = 102, MNU_DELETE = 103;
	private final boolean save, hasButtons;
	private final String fileType, itemType;
	private final OnFileSelectionListener listener;
	private final int id;
	private final StringBuilder formatterSB;
	private final Formatter formatter;
	private EditText txtSaveAsName;
	private BgListView list;
	private FileList fileList;
	private RelativeLayout panelLoading;
	private BgButton btnGoBack, btnMenu;
	private boolean loading;
	private int lastLongClickedId;
	
	public ActivityFileSelection(int id, boolean save, boolean hasButtons, String itemType, String fileType, OnFileSelectionListener listener) {
		if (fileType.charAt(0) != FileSt.PRIVATE_FILETYPE_ID)
			throw new IllegalArgumentException("fileType must start with " + FileSt.PRIVATE_FILETYPE_ID);
		this.id = id;
		this.save = save;
		this.hasButtons = hasButtons;
		this.itemType = itemType;
		this.fileType = fileType;
		this.listener = listener;
		this.lastLongClickedId = -1;
		this.formatterSB = new StringBuilder();
		this.formatter = new Formatter(formatterSB);
	}
	
	private String format(int resId, String p1) {
		formatterSB.delete(0, formatterSB.length());
		formatter.format(getText(resId).toString(), p1);
		return formatterSB.toString();
	}
	
	private String format(int resId, String p1, String p2) {
		formatterSB.delete(0, formatterSB.length());
		formatter.format(getText(resId).toString(), p1, p2);
		return formatterSB.toString();
	}
	
	private void updateMenu(int count) {
		if (count != 0 || save) {
			if (btnGoBack != null) {
				btnGoBack.setNextFocusRightId(R.id.btnMenu);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnMenu);
			}
			if (btnMenu != null)
				btnMenu.setVisibility(View.VISIBLE);
		} else {
			if (btnGoBack != null) {
				btnGoBack.setNextFocusRightId(R.id.list);
				UI.setNextFocusForwardId(btnGoBack, R.id.list);
			}
			if (btnMenu != null)
				btnMenu.setVisibility(View.GONE);
		}
	}
	
	@Override
	public void loadingProcessChanged(boolean started) {
		if (UI.browserActivity != this)
			return;
		loading = started;
		if (panelLoading != null)
			panelLoading.setVisibility(started ? View.VISIBLE : View.GONE);
		int count = 0;
		if (fileList != null) {
			fileList.setObserver(started ? null : list);
			count = fileList.getCount();
			if (!started && count > 0 && hasButtons)
				fileList.setSelection(0, true);
		}
		if (list != null)
			list.centerItem(fileList.getSelection(), false);
		if (!started)
			updateMenu(count);
	}
	
	@Override
	public View createView() {
		return new FileView(Player.getService(), null, hasButtons, false);
	}
	
	@Override
	public void processItemButtonClick(int position, boolean add) {
		if (!hasButtons)
			return;
		final FileSt file = fileList.getItemT(position);
		if (add) {
			listener.onAddClicked(id, file.path, file.name);
		} else {
			listener.onPlayClicked(id, file.path, file.name);
			if (Player.goBackWhenPlayingFolders)
				finish(0, (list == null) ? null : list.getViewForPosition(position));
		}
	}
	
	private void confirm(final String path, final String name, final boolean delete) {
		UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
		.setTitle(getText(R.string.oops))
		.setView(UI.createDialogView(getHostActivity(), format(delete ? R.string.msg_confirm_delete : R.string.msg_confirm_overwrite, itemType, name)))
		.setPositiveButton(delete ? R.string.delete : R.string.overwrite, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (delete) {
					try {
						getApplication().deleteFile(path);
						fileList.removeSelection();
						updateMenu(fileList.getCount());
					} catch (Throwable ex) {
					}
				} else {
					finish(0, null);
					listener.onFileSelected(ActivityFileSelection.this.id, path, name);
				}
			}
		})
		.setNegativeButton(R.string.cancel, this)
		.create());
	}
	
	@Override
	public void processItemClick(int position) {
		if (fileList.getSelection() == position || (!UI.doubleClickMode && !hasButtons)) {
			final FileSt file = fileList.getItemT(position);
			if (save) {
				confirm(file.path, file.name, false);
				return;
			}
			finish(0, (list == null) ? null : list.getViewForPosition(position));
			listener.onFileSelected(id, file.path, file.name);
		} else {
			fileList.setSelection(position, true);
		}
	}
	
	@Override
	public void processItemLongClick(int position) {
		if (fileList.getSelection() != position && (UI.doubleClickMode || hasButtons))
			fileList.setSelection(position, true);
		lastLongClickedId = position;
		CustomContextMenu.openContextMenu(btnMenu, this);
	}
	
	private void processMenuItemClick(int id) {
		final int s = ((lastLongClickedId < 0) ? fileList.getSelection() : lastLongClickedId);
		switch (id) {
		case MNU_LOAD:
			if (s >= 0)
				processItemClick(s);
			break;
		case MNU_SAVE:
			if (s >= 0)
				processItemClick(s);
			break;
		case MNU_SAVEAS:
			final Context ctx = getHostActivity();
			final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);
			
			TextView lbl = new TextView(ctx);
			lbl.setText(format(R.string.msg_enter_name, itemType));
			lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
			l.addView(lbl);
			
			txtSaveAsName = new EditText(ctx);
			txtSaveAsName.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
			txtSaveAsName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtSaveAsName.setSingleLine();
			final LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			p.topMargin = UI._DLGsppad;
			txtSaveAsName.setLayoutParams(p);
			if (s >= 0)
				txtSaveAsName.setText(fileList.getItemT(s).name);
			l.addView(txtSaveAsName);
			
			UI.prepareDialogAndShow((new AlertDialog.Builder(ctx))
			.setTitle(format(R.string.msg_create_new_title, itemType))
			.setView(l)
			.setPositiveButton(R.string.create, this)
			.setNegativeButton(R.string.cancel, this)
			.create());
			break;
		case MNU_DELETE:
			if (s >= 0) {
				final FileSt f = fileList.getItemT(s);
				confirm(f.path, f.name, true);
			}
			break;
		}
	}
	
	@Override
	public View getNullContextMenuView() {
		return btnMenu;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		final int i = ((lastLongClickedId < 0) ? fileList.getSelection() : lastLongClickedId);
		UI.prepare(menu);
		if (save)
			menu.add(0, MNU_SAVEAS, 0, format(R.string.msg_create_new, itemType))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_SAVE));
		if (i >= 0) {
			if (save)
				menu.add(0, MNU_SAVE, 1, format(R.string.msg_overwrite, itemType, fileList.getItemT(i).name))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable(UI.ICON_SAVE));
			else
				menu.add(0, MNU_LOAD, 0, R.string.load)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable(UI.ICON_LOAD));
			UI.separator(menu, 1, 0);
			menu.add(1, MNU_DELETE, 1, format(R.string.msg_delete, itemType, fileList.getItemT(i).name))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_DELETE));
		} else if (!save && !UI.doubleClickMode && fileList.getCount() > 0) {
			//just to show something when the user clicks the menu button
			menu.add(1, MNU_DELETE, 1, format(R.string.msg_delete, itemType, fileList.getItemT(0).name))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_DELETE));
		}
	}	
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		processMenuItemClick(item.getItemId());
		return true;
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView bgListView, int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
			((btnMenu.getVisibility() == View.VISIBLE) ? btnMenu : btnGoBack).requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			btnGoBack.requestFocus();
			return true;
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_SPACE:
		case KeyEvent.KEYCODE_DPAD_CENTER:
			final int p = fileList.getSelection();
			if (p >= 0)
				processItemClick(p);
			return true;
		}
		return false;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			finish(0, view);
		} if (view == btnMenu) {
			lastLongClickedId = -1;
			CustomContextMenu.openContextMenu(btnMenu, this);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE) {
			String n = txtSaveAsName.getText().toString().trim();
			if (!FileSt.isValidPrivateFileName(n))
				return;
			if (n.length() > 64)
				n = n.substring(0, 64);
			for (int i = fileList.getCount() - 1; i >= 0; i--) {
				if (fileList.getItemT(i).name.equals(n)) {
					confirm(n + fileType, n, false);
					txtSaveAsName = null;
					return;
				}
			}
			finish(0, null);
			listener.onFileSelected(ActivityFileSelection.this.id, n + fileType, n);
		}
		txtSaveAsName = null;
	}
	
	@Override
	protected void onCreate() {
		UI.browserActivity = this;
		fileList = new FileList();
	}
	
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_file_selection);
		UI.largeTextAndColor((TextView)findViewById(R.id.lblLoading));
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		btnMenu.setIcon(UI.ICON_MENU);
		list = (BgListView)findViewById(R.id.list);
		list.setScrollBarType((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
		list.setOnKeyDownObserver(this);
		fileList.setObserver(list);
		panelLoading = (RelativeLayout)findViewById(R.id.panelLoading);
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		fileList.setPrivateFileType(fileType, list.isInTouchMode());
		UI.prepareEdgeEffectColor(getApplication());
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
		fileList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		UI.browserActivity = this;
		fileList.setObserver(loading ? null : list);
		SongAddingMonitor.start(getHostActivity());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (UI.isLargeScreen && list != null)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		btnGoBack = null;
		btnMenu = null;
		list = null;
		panelLoading = null;
	}
	
	@Override
	protected void onDestroy() {
		UI.browserActivity = null;
		fileList.cancel();
		fileList = null;
	}
}
