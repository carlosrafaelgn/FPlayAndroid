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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.list.FileList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgDialog;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.FastAnimator;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivityFileSelection extends ClientActivity implements View.OnClickListener, DialogInterface.OnClickListener, FileList.ItemClickListener, FileList.ActionListener, BgListView.OnBgListViewKeyDownObserver, InputFilter, FastAnimator.Observer, Runnable {
	public interface OnFileSelectionListener {
		void onFileSelected(int id, FileSt file);
		void onAddClicked(int id, FileSt file);
		void onPlayClicked(int id, FileSt file);
		boolean onDeleteClicked(int id, FileSt file);
	}
	
	private final boolean save, hasButtons;
	private final String fileType, itemType;
	private final int id;
	private CharSequence title;
	private OnFileSelectionListener listener;
	private EditText txtSaveAsName;
	private BgListView list;
	private TextView lblLoading;
	private FileList fileList;
	private FileSt checkedFile;
	private BgButton btnGoBack, btnMenu, btnAdd, btnPlay;
	private RelativeLayout panelSecondary;
	private boolean loading, isCreatingLayout, btnMenuIconAnimation;
	private FileSt confirmFile;
	private int confirmDeleteIndex;
	private TextIconDrawable btnMenuIcon;
	private FastAnimator animator;
	private CharSequence msgEmptyList, msgLoading;

	public static ActivityFileSelection createPlaylistSelector(CharSequence title, int id, boolean save, boolean hasButtons, OnFileSelectionListener listener) {
		return new ActivityFileSelection(title, id, save, hasButtons, Player.theApplication.getText(R.string.item_list).toString(), FileSt.FILETYPE_PLAYLIST, listener);
	}

	public static ActivityFileSelection createPresetSelector(CharSequence title, int id, boolean save, boolean hasButtons, OnFileSelectionListener listener) {
		return new ActivityFileSelection(title, id, save, hasButtons, Player.theApplication.getText(R.string.item_preset).toString(), FileSt.FILETYPE_PRESET, listener);
	}

	private ActivityFileSelection(CharSequence title, int id, boolean save, boolean hasButtons, String itemType, String fileType, OnFileSelectionListener listener) {
		if (fileType.charAt(0) != FileSt.PRIVATE_FILETYPE_ID)
			throw new IllegalArgumentException("fileType must start with " + FileSt.PRIVATE_FILETYPE_ID);
		this.title = title;
		this.id = id;
		this.save = save;
		this.hasButtons = (hasButtons && !save);
		this.itemType = itemType;
		this.fileType = fileType;
		this.listener = listener;
		this.confirmDeleteIndex = Integer.MIN_VALUE;
	}

	@Override
	public CharSequence getTitle() {
		return title;
	}

	private void updateBtnMenuIcon() {
		if (btnMenuIcon == null || btnMenu == null)
			return;
		final CharSequence txt;
		if (checkedFile == null) {
			txt = getText(R.string.msg_create_new);
			btnMenuIcon.setIcon(UI.ICON_CREATE);
		} else {
			txt = getText(R.string.msg_delete_button);
			btnMenuIcon.setIcon(UI.ICON_DELETE);
		}
		btnMenu.setText(txt);
		btnMenu.setContentDescription(txt);
	}

	@SuppressWarnings("StringEquality")
	private void updateOverallLayout() {
		UI.animationReset();
		if (!save) {
			boolean setObserver = false;
			RelativeLayout.LayoutParams rp;
			final int count = ((fileList != null) ? fileList.getCount() : 0);
			if (count != 0 && checkedFile != null) {
				if (btnGoBack != null) {
					btnGoBack.setNextFocusRightId(R.id.btnMenu);
					UI.setNextFocusForwardId(btnGoBack, R.id.btnMenu);
				}
				if (btnMenu != null)
					UI.animationAddViewToShow(btnMenu);
			} else {
				if (checkedFile != null) {
					checkedFile.isChecked = false;
					checkedFile = null;
				}
				if (btnGoBack != null) {
					btnGoBack.setNextFocusRightId(R.id.list);
					UI.setNextFocusForwardId(btnGoBack, R.id.list);
				}
				if (btnMenu != null)
					UI.animationAddViewToHide(btnMenu);
			}
			if (hasButtons) {
				if (checkedFile == null) {
					if (panelSecondary != null && panelSecondary.getVisibility() != View.GONE) {
						if (list != null) {
							rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
							rp.addRule(RelativeLayout.BELOW, R.id.panelControls);
							rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
							list.setLayoutParams(rp);
							UI.setNextFocusForwardId(list, R.id.btnGoBack);
						}
						UI.animationAddViewToHide(panelSecondary);
						if (btnMenu != null)
							btnMenu.setNextFocusUpId(R.id.list);
						if (btnGoBack != null) {
							btnGoBack.setNextFocusUpId(R.id.list);
							btnGoBack.setNextFocusLeftId(R.id.list);
						}
					}
				} else if (panelSecondary != null && panelSecondary.getVisibility() != View.VISIBLE) {
					if (UI.animationEnabled) {
						if (lblLoading != null) {
							setObserver = true;
							//to prevent a black area behind the panelSecondary's animation
							lblLoading.setVisibility(View.VISIBLE);
						}
					}
					if (list != null) {
						rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
						rp.addRule(RelativeLayout.BELOW, R.id.panelControls);
						rp.addRule(RelativeLayout.ABOVE, R.id.panelSecondary);
						list.setLayoutParams(rp);
						UI.setNextFocusForwardId(list, R.id.btnAdd);
					}
					UI.animationAddViewToShow(panelSecondary);
					if (btnMenu != null)
						btnMenu.setNextFocusUpId(R.id.btnPlay);
					if (btnGoBack != null) {
						btnGoBack.setNextFocusUpId(R.id.btnPlay);
						btnGoBack.setNextFocusLeftId(R.id.btnPlay);
					}
				}
			}
			btnMenuIconAnimation = false;
			if (setObserver)
				UI.animationFinishedObserver = this;
			UI.animationCommit(isCreatingLayout, null);
		} else {
			if (btnMenuIcon != null && btnMenu != null && btnMenuIcon.getIcon() != ((checkedFile != null) ? UI.ICON_DELETE : UI.ICON_CREATE)) {
				if (UI.animationEnabled && !isCreatingLayout) {
					btnMenuIconAnimation = true;
					UI.animationFinishedObserver = this;
					UI.animationAddViewToHide(btnMenu);
					UI.animationCommit(false, null);
				} else {
					updateBtnMenuIcon();
				}
			}
		}
	}

	@Override
	public void run() {
		if (!isLayoutCreated())
			return;
		if (btnMenuIconAnimation) {
			if (btnMenu == null)
				return;
			//btnMenu has just been hidden, time to update it
			updateBtnMenuIcon();
			UI.animationReset();
			UI.animationAddViewToShow(btnMenu);
			UI.animationCommit(false, null);
		} else if (lblLoading != null) {
			//the animation has just finished, time to hide lblLoading
			lblLoading.setVisibility(View.GONE);
		}
	}

	private void confirm(FileSt file, int deleteIndex) {
		final Context ctx = getHostActivity();
		confirmFile = file;
		confirmDeleteIndex = deleteIndex;
		final BgDialog dialog = new BgDialog(ctx, UI.createDialogView(getHostActivity(), UI.format(deleteIndex >= 0 ? R.string.msg_confirm_delete : R.string.msg_confirm_overwrite, itemType, file.name)), this);
		dialog.setTitle(R.string.oops);
		dialog.setPositiveButton(deleteIndex >= 0 ? R.string.delete : R.string.overwrite);
		dialog.setNegativeButton(R.string.cancel);
		dialog.show();
	}
	
	@Override
	public void onItemClicked(int position) {
		if (!isLayoutCreated())
			return;
		//see the comments at processItemClick(), in ActivityBrowser2
		if (list == null || fileList == null)
			return;
		if (!UI.doubleClickMode || fileList.getSelection() == position) {
			final FileSt file = fileList.getItem(position);
			if (save) {
				confirm(file, -1);
				return;
			}
			final OnFileSelectionListener listener = this.listener;
			finish(0, list.getViewForPosition(position), true);
			if (listener != null)
				listener.onFileSelected(id, file);
		} else {
			fileList.setSelection(position, true);
		}
	}

	@Override
	public void onItemLongClicked(int position) {
	}

	@Override
	public void onItemCheckboxClicked(int position) {
		if (!isLayoutCreated())
			return;
		//see the comments at processItemButtonClick(), in ActivityBrowser2
		if (list == null || fileList == null)
			return;
		if (!list.isInTouchMode() && fileList.getSelection() != position)
			fileList.setSelection(position, true);
		final FileSt file = fileList.getItem(position);
		if (file == null) //same as above
			return;
		if (checkedFile != file && checkedFile != null)
			checkedFile.isChecked = false;
		checkedFile = (file.isChecked ? file : null);
		updateOverallLayout();
		fileList.notifyCheckedChanged();
	}

	@Override
	public void onLoadingProcessChanged(boolean started) {
		if (!isLayoutCreated() || fileList == null)
			return;
		loading = started;
		if (list != null) {
			if (animator != null) {
				animator.end();
				//when the animation ends, lblLoading is made hidden...
				//that's why we set the visibility after calling end()
				lblLoading.setVisibility(View.VISIBLE);
				if (started) {
					list.setVisibility(View.INVISIBLE);
				} else {
					list.setVisibility(View.VISIBLE);
					animator.start();
				}
			} else {
				list.setCustomEmptyText(started ? msgLoading : msgEmptyList);
			}
			if (fileList != null) {
				fileList.setObserver(started ? null : list);
				final int count = fileList.getCount();
				if (!started) {
					if (UI.isAccessibilityManagerEnabled)
						UI.announceAccessibilityText(count == 0 ? msgEmptyList : FileView.makeContextDescription(true, getHostActivity(), fileList.getItem(0)));
					if (count > 0 && !list.isInTouchMode()) {
						fileList.setSelection(0, true);
						list.centerItem(0);
					}
				}
			}
		}
		//if (!started)
		//	updateOverallLayout();
	}

	@Override
	public boolean onBgListViewKeyDown(BgListView list, int keyCode) {
		if (!isLayoutCreated())
			return true;
		final int p;
		switch (keyCode) {
		case UI.KEY_LEFT:
			if (btnMenu != null && btnGoBack != null)
				((btnMenu.getVisibility() == View.VISIBLE) ? btnMenu : btnGoBack).requestFocus();
			return true;
		case UI.KEY_RIGHT:
			if (btnAdd != null && btnGoBack != null && panelSecondary != null)
				((panelSecondary.getVisibility() == View.VISIBLE) ? btnAdd : btnGoBack).requestFocus();
			return true;
		case UI.KEY_ENTER:
			if (fileList != null && (p = fileList.getSelection()) >= 0)
				onItemClicked(p);
			return true;
		case UI.KEY_EXTRA:
			if (fileList != null && (p = fileList.getSelection()) >= 0) {
				final FileSt file = fileList.getItem(p);
				file.isChecked = !file.isChecked;
				onItemCheckboxClicked(p);
			}
			return true;
		}
		return false;
	}

	@Override
	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
		if (end <= start)
			return null;

		final StringBuilder sb = new StringBuilder(end - start);

		for (int i = start; i < end; i++) {
			final char c = source.charAt(i);
			switch (c) {
			case '/':
			case '*':
			case '\"':
			case ':':
			case '?':
			case '\\':
			case '|':
			case '<':
			case '>':
				continue;
			}
			sb.append(c);
		}

		//returning null means "no changes"
		return ((sb.length() == (end - start)) ? null : sb);
	}

	@Override
	public void onClick(View view) {
		if (!isLayoutCreated())
			return;
		if (view == btnGoBack) {
			finish(0, view, true);
		} if (view == btnMenu) {
			if (loading)
				return;
			if (save && checkedFile == null) {
				final Context ctx = getHostActivity();
				final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);

				txtSaveAsName = UI.createDialogEditText(ctx, 0, (fileList != null && fileList.getSelection() >= 0) ? fileList.getItem(fileList.getSelection()).name : null, UI.format(R.string.msg_enter_name, itemType), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
				txtSaveAsName.setFilters(new InputFilter[]{this, new InputFilter.LengthFilter(64)});
				l.addView(txtSaveAsName, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

				final BgDialog dialog = new BgDialog(ctx, l, this);
				dialog.setTitle(UI.format(R.string.msg_create_new_title, itemType), true);
				dialog.setPositiveButton(R.string.create);
				dialog.setNegativeButton(R.string.cancel);
				dialog.show();
			} else {
				if (fileList != null && checkedFile != null) {
					final int s = fileList.indexOf(checkedFile);
					if (s >= 0)
						confirm(checkedFile, s);
				}
			}
		} else if (view == btnAdd) {
			if (hasButtons && checkedFile != null) {
				if (listener != null)
					listener.onAddClicked(id, checkedFile);
				checkedFile.isChecked = false;
				checkedFile = null;
				if (fileList != null)
					fileList.notifyCheckedChanged();
				updateOverallLayout();
			}
		} else if (view == btnPlay) {
			if (hasButtons && checkedFile != null) {
				if (listener != null)
					listener.onPlayClicked(id, checkedFile);
				if (Player.goBackWhenPlayingFolders) {
					finish(0, (list == null || fileList == null) ? null : list.getViewForPosition(fileList.indexOf(checkedFile)), true);
				} else {
					checkedFile.isChecked = false;
					checkedFile = null;
					if (fileList != null)
						fileList.notifyCheckedChanged();
					updateOverallLayout();
				}
			}
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (!isLayoutCreated())
			return;
		if (which == AlertDialog.BUTTON_POSITIVE) {
			final OnFileSelectionListener listener = this.listener;
			if (confirmDeleteIndex == Integer.MIN_VALUE) {
				String n = txtSaveAsName.getText().toString().trim();
				if (!FileSt.isValidPrivateFileName(n))
					return;
				if (n.length() > 64)
					n = n.substring(0, 64);
				for (int i = fileList.getCount() - 1; i >= 0; i--) {
					final FileSt f = fileList.getItem(i);
					if (f.name.equals(n)) {
						dialog.dismiss();
						confirm(f, -1);
						txtSaveAsName = null;
						return;
					}
				}
				finish(0, null, false);
				if (listener != null)
					listener.onFileSelected(ActivityFileSelection.this.id, new FileSt(n + fileType, n, 0));
			} else {
				if (confirmDeleteIndex >= 0) {
					try {
						if (listener == null || !listener.onDeleteClicked(ActivityFileSelection.this.id, confirmFile))
							Player.theApplication.deleteFile(confirmFile.path);
						final int p;
						if (checkedFile != null && fileList != null && (p = fileList.indexOf(checkedFile)) >= 0) {
							checkedFile.isChecked = false;
							checkedFile = null;
							if (fileList.getSelection() != p)
								fileList.setSelection(p, true);
							fileList.removeSelection();
							if (list != null && list.isInTouchMode() && fileList.getSelection() >= 0)
								fileList.setSelection(-1, true);
							updateOverallLayout();
						}
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				} else {
					finish(0, null, false);
					if (listener != null)
						listener.onFileSelected(ActivityFileSelection.this.id, confirmFile);
				}
				confirmDeleteIndex = Integer.MIN_VALUE;
				confirmFile = null;
			}
		}
		txtSaveAsName = null;
		dialog.dismiss();
	}
	
	@Override
	protected void onCreate() {
		fileList = new FileList();
		fileList.setItemClickListener(this);
		fileList.setActionListener(this);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_file_selection);
		btnGoBack = findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnMenu = findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		msgEmptyList = getText(R.string.empty_list);
		msgLoading = getText(R.string.loading);
		list = findViewById(R.id.list);
		list.setScrollBarType(fileList.scrollBarType = ((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType));
		list.setOnKeyDownObserver(this);
		if (UI.animationEnabled) {
			if (firstCreation)
				list.setVisibility(View.GONE);
			list.setCustomEmptyText(msgEmptyList);
			animator = new FastAnimator(list, false, this, 0);
			lblLoading = findViewById(R.id.lblLoading);
			lblLoading.setTextColor(UI.color_text_listitem_disabled);
			lblLoading.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
			UI.headingText(lblLoading);
			lblLoading.setVisibility(View.VISIBLE);
		}
		fileList.setObserver(list);
		panelSecondary = findViewById(R.id.panelSecondary);
		if (save) {
			final CharSequence txt = getText(R.string.msg_create_new);
			btnMenu.setText(txt);
			btnMenu.setContentDescription(txt);
			btnMenu.setDefaultHeight();
			btnMenu.setCompoundDrawables((btnMenuIcon = new TextIconDrawable(UI.ICON_CREATE, UI.color_text)), null, null, null);
		} else {
			final CharSequence txt = getText(R.string.msg_delete_button);
			btnMenu.setText(txt);
			btnMenu.setContentDescription(txt);
			btnMenu.setDefaultHeight();
			btnMenu.setCompoundDrawables((btnMenuIcon = new TextIconDrawable(UI.ICON_DELETE, UI.color_text)), null, null, null);
			btnAdd = findViewById(R.id.btnAdd);
			btnAdd.setTextColor(UI.colorState_text_reactive);
			btnAdd.setOnClickListener(this);
			btnAdd.setIcon(UI.ICON_ADD);
			RelativeLayout.LayoutParams rp;
			final TextView sep2 = findViewById(R.id.sep2);
			rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
			rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			rp.addRule(RelativeLayout.LEFT_OF, R.id.btnPlay);
			rp.leftMargin = UI.controlMargin;
			rp.rightMargin = UI.controlMargin;
			sep2.setLayoutParams(rp);
			sep2.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
			btnPlay = findViewById(R.id.btnPlay);
			btnPlay.setTextColor(UI.colorState_text_reactive);
			btnPlay.setOnClickListener(this);
			btnPlay.setIcon(UI.ICON_PLAY);
			if (hasButtons) {
				UI.prepareControlContainer(panelSecondary, true, false);
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, UI.thickDividerSize + UI.defaultControlSize + (UI.extraSpacing ? (UI.controlMargin << 1) : 0));
				rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
				panelSecondary.setLayoutParams(rp);
			}
		}
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		UI.prepareViewPaddingBasedOnScreenWidth(list, 0, 0, 0);
		isCreatingLayout = true;
		updateOverallLayout();
		isCreatingLayout = false;
	}

	@Override
	protected void onPostCreateLayout(boolean firstCreation) {
		fileList.setPrivateFileType(fileType, list.isInTouchMode());
	}

	@Override
	protected void onPause() {
		fileList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		fileList.setObserver(loading ? null : list);
		if (loading != fileList.isLoading())
			onLoadingProcessChanged(fileList.isLoading());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (list != null)
			UI.prepareViewPaddingBasedOnScreenWidth(list, 0, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		if (animator != null) {
			animator.release();
			animator = null;
		}
		checkedFile = null;
		btnGoBack = null;
		btnMenu = null;
		btnAdd = null;
		btnPlay = null;
		list = null;
		lblLoading = null;
		panelSecondary = null;
		btnMenuIcon = null;
		msgEmptyList = null;
		msgLoading = null;
	}
	
	@Override
	protected void onDestroy() {
		if (fileList != null) {
			fileList.setItemClickListener(null);
			fileList.setActionListener(null);
			fileList.cancel();
			fileList = null;
		}
		title = null;
		listener = null;
	}

	@Override
	public void onEnd(FastAnimator animator) {
		if (lblLoading != null)
			lblLoading.setVisibility(View.GONE);
	}
}
