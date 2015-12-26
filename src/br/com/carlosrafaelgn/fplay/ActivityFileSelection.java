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
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Formatter;

import br.com.carlosrafaelgn.fplay.list.FileList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.FastAnimator;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivityFileSelection extends ActivityBrowserView implements View.OnClickListener, DialogInterface.OnClickListener, BgListView.OnBgListViewKeyDownObserver, InputFilter {
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
	private StringBuilder formatterSB;
	private Formatter formatter;
	private EditText txtSaveAsName;
	private BgListView list;
	private FileList fileList;
	private FileSt checkedFile;
	private BgButton btnGoBack, btnMenu, btnAdd, btnPlay;
	private RelativeLayout panelSecondary;
	private boolean loading, isCreatingLayout;
	private TextIconDrawable btnMenuIcon;
	private FastAnimator animator;
	private CharSequence msgEmptyList, msgLoading;

	public static ActivityFileSelection createPlaylistSelector(Context context, CharSequence title, int id, boolean save, boolean hasButtons, OnFileSelectionListener listener) {
		return new ActivityFileSelection(title, id, save, hasButtons, context.getText(R.string.item_list).toString(), FileSt.FILETYPE_PLAYLIST, listener);
	}

	public static ActivityFileSelection createPresetSelector(Context context, CharSequence title, int id, boolean save, boolean hasButtons, OnFileSelectionListener listener) {
		return new ActivityFileSelection(title, id, save, hasButtons, context.getText(R.string.item_preset).toString(), FileSt.FILETYPE_PRESET, listener);
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
		this.formatterSB = new StringBuilder();
		this.formatter = new Formatter(formatterSB);
	}

	@Override
	public CharSequence getTitle() {
		return title;
	}

	@SuppressWarnings("StringEquality")
	private void updateOverallLayout() {
		UI.animationReset();
		if (!save) {
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
		} else {
			if (btnMenuIcon != null && btnMenu != null && btnMenuIcon.getIcon() != ((checkedFile != null) ? UI.ICON_DELETE : UI.ICON_SAVE)) {
				final CharSequence txt;
				if (checkedFile == null) {
					txt = getText(R.string.msg_create_new);
					btnMenuIcon.setIcon(UI.ICON_SAVE);
				} else {
					txt = getText(R.string.msg_delete_button);
					btnMenuIcon.setIcon(UI.ICON_DELETE);
				}
				btnMenu.setText(txt);
				btnMenu.setContentDescription(txt);
			}
		}
		UI.animationCommit(isCreatingLayout, null);
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

	@Override
	public void loadingProcessChanged(boolean started) {
		if (UI.browserActivity != this)
			return;
		loading = started;
		if (list != null) {
			if (animator != null) {
				if (started) {
					list.setVisibility(View.INVISIBLE);
				} else {
					animator.end();
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
					if (UI.accessibilityManager != null && UI.accessibilityManager.isEnabled())
						UI.announceAccessibilityText(count == 0 ? msgEmptyList : FileView.makeContextDescription(true, getHostActivity(), fileList.getItemT(0)));
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
	public View createView() {
		return new FileView(Player.getService(), null, true);
	}
	
	@Override
	public void processItemCheckboxClick(int position) {
		//see the comments at processItemButtonClick(), in ActivityBrowser2
		if (list == null || fileList == null)
			return;
		if (!list.isInTouchMode() && fileList.getSelection() != position)
			fileList.setSelection(position, true);
		final FileSt file = fileList.getItemT(position);
		if (file == null) //same as above
			return;
		if (checkedFile != file && checkedFile != null)
			checkedFile.isChecked = false;
		checkedFile = (file.isChecked ? file : null);
		updateOverallLayout();
		fileList.notifyCheckedChanged();
	}
	
	private void confirm(final FileSt file, final int deleteIndex) {
		UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
			.setTitle(getText(R.string.oops))
			.setView(UI.createDialogView(getHostActivity(), format(deleteIndex >= 0 ? R.string.msg_confirm_delete : R.string.msg_confirm_overwrite, itemType, file.name)))
			.setPositiveButton(deleteIndex >= 0 ? R.string.delete : R.string.overwrite, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					final OnFileSelectionListener listener = ActivityFileSelection.this.listener;
					if (deleteIndex >= 0) {
						try {
							if (listener == null || !listener.onDeleteClicked(ActivityFileSelection.this.id, file))
								getApplication().deleteFile(file.path);
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
							listener.onFileSelected(ActivityFileSelection.this.id, file);
					}
				}
			})
			.setNegativeButton(R.string.cancel, this)
			.create());
	}
	
	@Override
	public void processItemClick(int position) {
		//see the comments at processItemClick(), in ActivityBrowser2
		if (list == null || fileList == null)
			return;
		if (!UI.doubleClickMode || fileList.getSelection() == position) {
			final FileSt file = fileList.getItemT(position);
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
	public void processItemLongClick(int position) {
	}

	@Override
	public boolean onBgListViewKeyDown(BgListView list, int keyCode) {
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
				processItemClick(p);
			return true;
		case UI.KEY_EXTRA:
			if (fileList != null && (p = fileList.getSelection()) >= 0) {
				final FileSt file = fileList.getItemT(p);
				file.isChecked = !file.isChecked;
				processItemCheckboxClick(p);
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
		if (view == btnGoBack) {
			finish(0, view, true);
		} if (view == btnMenu) {
			if (loading)
				return;
			if (save && checkedFile == null) {
				final Context ctx = getHostActivity();
				final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);

				TextView lbl = new TextView(ctx);
				lbl.setText(format(R.string.msg_enter_name, itemType));
				lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
				l.addView(lbl);

				txtSaveAsName = new EditText(ctx);
				txtSaveAsName.setContentDescription(lbl.getText());
				txtSaveAsName.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
				txtSaveAsName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
				txtSaveAsName.setFilters(new InputFilter[] { this, new InputFilter.LengthFilter(64) });
				txtSaveAsName.setSingleLine();
				final LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				p.topMargin = UI.dialogMargin;
				txtSaveAsName.setLayoutParams(p);
				if (fileList != null && fileList.getSelection() >= 0)
					txtSaveAsName.setText(fileList.getItemT(fileList.getSelection()).name);
				l.addView(txtSaveAsName);

				UI.prepareDialogAndShow((new AlertDialog.Builder(ctx))
					.setTitle(format(R.string.msg_create_new_title, itemType))
					.setView(l)
					.setPositiveButton(R.string.create, this)
					.setNegativeButton(R.string.cancel, this)
					.create());
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
		if (which == AlertDialog.BUTTON_POSITIVE) {
			String n = txtSaveAsName.getText().toString().trim();
			if (!FileSt.isValidPrivateFileName(n))
				return;
			if (n.length() > 64)
				n = n.substring(0, 64);
			for (int i = fileList.getCount() - 1; i >= 0; i--) {
				final FileSt f = fileList.getItemT(i);
				if (f.name.equals(n)) {
					confirm(f, -1);
					txtSaveAsName = null;
					return;
				}
			}
			final OnFileSelectionListener listener = this.listener;
			finish(0, null, false);
			if (listener != null)
				listener.onFileSelected(ActivityFileSelection.this.id, new FileSt(n + fileType, n, 0));
		}
		txtSaveAsName = null;
	}
	
	@Override
	protected void onCreate() {
		UI.browserActivity = this;
		fileList = new FileList();
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_file_selection);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		msgEmptyList = getText(R.string.empty_list);
		msgLoading = getText(R.string.loading);
		list = (BgListView)findViewById(R.id.list);
		list.setScrollBarType((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
		list.setOnKeyDownObserver(this);
		if (UI.animationEnabled) {
			list.setCustomEmptyText(msgEmptyList);
			((View)list.getParent()).setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
			animator = new FastAnimator(list, false, null, 0);
			final TextView lblLoading = (TextView)findViewById(R.id.lblLoading);
			lblLoading.setTextColor(UI.color_text_disabled);
			UI.largeText(lblLoading);
			lblLoading.setVisibility(View.VISIBLE);
		}
		fileList.setObserver(list);
		panelSecondary = (RelativeLayout)findViewById(R.id.panelSecondary);
		if (save) {
			final CharSequence txt = getText(R.string.msg_create_new);
			btnMenu.setText(txt);
			btnMenu.setContentDescription(txt);
			btnMenu.setDefaultHeight();
			btnMenu.setCompoundDrawables((btnMenuIcon = new TextIconDrawable(UI.ICON_SAVE, UI.color_text, UI.defaultControlContentsSize)), null, null, null);
		} else {
			final CharSequence txt = getText(R.string.msg_delete_button);
			btnMenu.setText(txt);
			btnMenu.setContentDescription(txt);
			btnMenu.setDefaultHeight();
			btnMenu.setCompoundDrawables((btnMenuIcon = new TextIconDrawable(UI.ICON_DELETE, UI.color_text, UI.defaultControlContentsSize)), null, null, null);
			btnAdd = (BgButton)findViewById(R.id.btnAdd);
			btnAdd.setTextColor(UI.colorState_text_reactive);
			btnAdd.setOnClickListener(this);
			btnAdd.setIcon(UI.ICON_ADD);
			RelativeLayout.LayoutParams rp;
			final TextView sep2 = (TextView)findViewById(R.id.sep2);
			rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
			rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			rp.addRule(RelativeLayout.LEFT_OF, R.id.btnPlay);
			rp.leftMargin = UI.controlMargin;
			rp.rightMargin = UI.controlMargin;
			sep2.setLayoutParams(rp);
			sep2.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
			btnPlay = (BgButton)findViewById(R.id.btnPlay);
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
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		fileList.setPrivateFileType(fileType, list.isInTouchMode());
		isCreatingLayout = true;
		updateOverallLayout();
		isCreatingLayout = false;
	}
	
	@Override
	protected void onPause() {
		fileList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		UI.browserActivity = this;
		fileList.setObserver(loading ? null : list);
	}
	
	@Override
	protected void onOrientationChanged() {
		if (list != null && UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
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
		panelSecondary = null;
		btnMenuIcon = null;
		msgEmptyList = null;
		msgLoading = null;
	}
	
	@Override
	protected void onDestroy() {
		UI.browserActivity = null;
		fileList.cancel();
		fileList = null;
		title = null;
		listener = null;
		formatterSB = null;
		formatter = null;
	}
}
