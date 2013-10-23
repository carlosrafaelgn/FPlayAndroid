//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013, Carlos Rafael Gimenes das Neves
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

import java.io.File;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
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
import br.com.carlosrafaelgn.fplay.list.FileFetcher;
import br.com.carlosrafaelgn.fplay.list.FileList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivityBrowser extends ActivityFileView implements View.OnClickListener, DialogInterface.OnClickListener, BgListView.OnBgListViewKeyDownObserver {
	private static final int MNU_ADDSONG = 100, MNU_PLAYSONG = 101, MNU_ADDFOLDER = 102, MNU_ADDFOLDERSUB = 103, MNU_PLAYFOLDER = 104, MNU_PLAYFOLDERSUB = 105, MNU_GOBACK = 106, MNU_REMOVEFAVORITE = 107;
	private TextView lblPath;
	private BgListView list;
	private FileList fileList;
	private LinearLayout panelLoading;
	private EditText txtURL, txtTitle;
	private BgButton btnGoBack, btnURL, chkFavorite, btnHome, btnUp, btnMenu;
	private boolean loading;
	private Drawable ic_closed_folder, ic_internal, ic_external, ic_favorite, ic_artist, ic_album;
	
	private void refreshMenu(int count) {
		boolean mnu = false;
		if (count != 0 && btnURL.getVisibility() != View.VISIBLE)
			mnu = (fileList.getItemT(0).specialType != FileSt.TYPE_ARTIST);
		if (mnu) {
			btnMenu.setVisibility(View.VISIBLE);
			if (btnUp.getVisibility() == View.VISIBLE) {
				final RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.defaultControlSize, UI.defaultControlSize);
				rp.leftMargin = UI._8dp;
				rp.addRule(RelativeLayout.LEFT_OF, R.id.btnMenu);
				btnUp.setLayoutParams(rp);
				btnUp.setNextFocusRightId(R.id.btnMenu);
				UI.setNextFocusForwardId(btnUp, R.id.btnMenu);
				btnMenu.setNextFocusLeftId(R.id.btnUp);
			} else {
				btnGoBack.setNextFocusRightId(R.id.btnMenu);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnMenu);
				btnMenu.setNextFocusLeftId(R.id.btnGoBack);
			}
		} else {
			btnMenu.setVisibility(View.GONE);
			if (btnUp.getVisibility() == View.VISIBLE) {
				final RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.defaultControlSize, UI.defaultControlSize);
				rp.leftMargin = UI._8dp;
				rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
				btnUp.setLayoutParams(rp);
				btnUp.setNextFocusRightId(R.id.list);
				UI.setNextFocusForwardId(btnUp, R.id.list);
			}
		}
	}
	
	@Override
	public void showNotification(boolean show) {
		loading = show;
		if (panelLoading != null)
			panelLoading.setVisibility(show ? View.VISIBLE : View.GONE);
		int count = 0;
		if (fileList != null) {
			fileList.setObserver(show ? null : list);
			count = fileList.getCount();
		}
		if (list != null)
			list.centerItem(fileList.getSelection(), false);
		if (!show)
			refreshMenu(count);
	}
	
	@Override
	public FileView createFileView() {
		return new FileView(Player.getService(), this, ic_closed_folder, ic_internal, ic_external, ic_favorite, ic_artist, ic_album, true);
	}
	
	@Override
	public void processItemButtonClick(int position, boolean add) {
		if (fileList.getItemT(position).isDirectory) {
			if (add) {
				if (!Player.msgAddShown) {
					(new AlertDialog.Builder(getHostActivity()))
					.setTitle(getText(R.string.add))
					.setMessage(getText(R.string.msg_add))
					.setPositiveButton(R.string.got_it, null)
					.show();
					Player.msgAddShown = true;
					return;
				}
			} else {
				if (!Player.msgPlayShown) {
					(new AlertDialog.Builder(getHostActivity()))
					.setTitle(getText(R.string.play))
					.setMessage(getText(R.string.msg_play))
					.setPositiveButton(R.string.got_it, null)
					.show();
					Player.msgPlayShown = true;
					return;
				}
			}
			processMenuItemClick(add ? MNU_ADDFOLDERSUB : MNU_PLAYFOLDERSUB);
		} else {
			processMenuItemClick(add ? MNU_ADDSONG : MNU_PLAYSONG);
		}
	}
	
	@Override
	public void processItemClick(int position) {
		final FileSt f = fileList.getItemT(position);
		if (fileList.getSelection() == position) {
			if (f.isDirectory)
				navigateTo(f.path, null);
			else
				processMenuItemClick(MNU_PLAYSONG);
		} else {
			fileList.setSelection(position, true);
		}
	}
	
	@Override
	public void processItemLongClick(int position) {
		if (fileList.getSelection() != position)
			fileList.setSelection(position, true);
		CustomContextMenu.openContextMenu(btnMenu, this);
	}
	
	private void processMenuItemClick(final int id) {
		final int s = fileList.getSelection();
		FileSt f;
		switch (id) {
		case MNU_GOBACK:
			finish();
			break;
		case MNU_ADDSONG:
		case MNU_PLAYSONG:
			if (s >= 0) {
				f = fileList.getItemT(s);
				if (f.isDirectory) {
					UI.toast(getApplication(), R.string.msg_select_song);
				} else {
					try {
						final FileSt[] fs = new FileSt[] { f };
						Player.songs.addingStarted();
						SongAddingMonitor.start(getHostActivity());
						(new Thread("Single File Adder Thread") {
							@Override
							public void run() {
								Player.songs.addFiles(fs, -1, 1, id == MNU_PLAYSONG, false);
							}
						}).start();
					} catch (Throwable ex) {
						Player.songs.addingEnded();
						UI.toast(getApplication(), ex.getMessage());
					}
				}
			} else {
				UI.toast(getApplication(), R.string.msg_select_song);
			}
			break;
		case MNU_ADDFOLDER:
		case MNU_ADDFOLDERSUB:
		case MNU_PLAYFOLDER:
		case MNU_PLAYFOLDERSUB:
			final boolean play = ((id == MNU_PLAYFOLDER) || (id == MNU_PLAYFOLDERSUB));
			if (s >= 0) {
				f = fileList.getItemT(s);
				if (!f.isDirectory) {
					UI.toast(getApplication(), play ? R.string.msg_select_folder_play : R.string.msg_select_folder_add);
				} else {
					try {
						Player.songs.addingStarted();
						SongAddingMonitor.start(getHostActivity());
						FileFetcher.fetchFiles(f.path, Player.songs, false, (id == MNU_ADDFOLDERSUB) || (id == MNU_PLAYFOLDERSUB), true, play);
					} catch (Throwable ex) {
						Player.songs.addingEnded();
						UI.toast(getApplication(), ex.getMessage());
					}
				}
			} else {
				UI.toast(getApplication(), play ? R.string.msg_select_folder_play : R.string.msg_select_folder_add);
			}
			break;
		case MNU_REMOVEFAVORITE:
			if (s >= 0) {
				f = fileList.getItemT(s);
				if (f.specialType != FileSt.TYPE_FAVORITE) {
					UI.toast(getApplication(), R.string.msg_select_favorite_remove);
				} else {
					Player.removeFavoriteFolder(f.path);
					fileList.removeSelection();
				}
			} else {
				UI.toast(getApplication(), R.string.msg_select_favorite_remove);
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
		final int i = fileList.getSelection();
		UI.prepare(menu);
		if (i >= 0) {
			final FileSt f = fileList.getItemT(i);
			if (Player.path.length() != 0 && !Player.path.startsWith(FileSt.ARTIST_ROOT + FileSt.FAKE_PATH_ROOT)) {
				if (f.specialType == FileSt.TYPE_ALBUM) {
					menu.add(0, MNU_ADDFOLDER, 0, R.string.add_album)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable(UI.ICON_ADD));
					UI.separator(menu, 0, 1);
					menu.add(1, MNU_PLAYFOLDER, 0, R.string.play_album)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_PLAY));
				} else if (f.isDirectory) {
					menu.add(0, MNU_ADDFOLDER, 0, R.string.add_folder)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_ADD));
					menu.add(0, MNU_ADDFOLDERSUB, 1, R.string.add_folder_sub)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_ADD));
					UI.separator(menu, 0, 2);
					menu.add(1, MNU_PLAYFOLDER, 0, R.string.play_folder)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_PLAY));
					menu.add(1, MNU_PLAYFOLDERSUB, 1, R.string.play_folder_sub)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_PLAY));
				} else {
					menu.add(0, MNU_ADDSONG, 0, R.string.add_song)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_ADD));
					menu.add(0, MNU_PLAYSONG, 1, R.string.play_song)
						.setOnMenuItemClickListener(this)
						.setIcon(new TextIconDrawable(UI.ICON_PLAY));
				}
			} else if (f.specialType == FileSt.TYPE_FAVORITE) {
				menu.add(0, MNU_REMOVEFAVORITE, 0, R.string.remove_favorite)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable(UI.ICON_FAVORITE_OFF));
			}
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		processMenuItemClick(item.getItemId());
		return true;
	}
	
	private void navigateTo(String to, String from) {
		final boolean fav = ((to.length() > 1) && (to.charAt(0) == File.separatorChar));
		final boolean others = (to.length() > 0);
		if (fav) {
			btnURL.setVisibility(View.GONE);
			btnGoBack.setNextFocusRightId(R.id.chkFavorite);
			UI.setNextFocusForwardId(btnGoBack, R.id.chkFavorite);
			btnHome.setNextFocusLeftId(R.id.chkFavorite);
			chkFavorite.setChecked(Player.isFavoriteFolder(to));
			chkFavorite.setVisibility(View.VISIBLE);
			btnHome.setVisibility(View.VISIBLE);
			btnUp.setVisibility(View.VISIBLE);
			lblPath.setVisibility(View.VISIBLE);
		} else if (others) {
			btnURL.setVisibility(View.GONE);
			btnGoBack.setNextFocusRightId(R.id.btnHome);
			UI.setNextFocusForwardId(btnGoBack, R.id.btnHome);
			btnHome.setNextFocusLeftId(R.id.btnGoBack);
			chkFavorite.setVisibility(View.GONE);
			btnHome.setVisibility(View.VISIBLE);
			btnUp.setVisibility(View.VISIBLE);
			lblPath.setVisibility(View.VISIBLE);
		} else {
			refreshMenu(0);
			btnURL.setVisibility(View.VISIBLE);
			btnGoBack.setNextFocusRightId(R.id.btnURL);
			UI.setNextFocusForwardId(btnGoBack, R.id.btnURL);
			chkFavorite.setVisibility(View.GONE);
			btnHome.setVisibility(View.GONE);
			btnUp.setVisibility(View.GONE);
			lblPath.setVisibility(View.GONE);
		}
		if (Player.path.length() == 0)
			Player.originalPath = to;
		Player.path = to;
		lblPath.setText(((to.length() > 0) && (to.charAt(0) != File.separatorChar)) ? to.substring(to.indexOf(FileSt.FAKE_PATH_ROOT_CHAR) + 1).replace(FileSt.FAKE_PATH_SEPARATOR_CHAR, File.separatorChar) : to);
		fileList.setPath(to, from);
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView bgListView, int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
			if (btnURL.getVisibility() == View.VISIBLE)
				btnURL.requestFocus();
			else if (btnMenu.getVisibility() == View.VISIBLE)
				btnMenu.requestFocus();
			else if (btnUp.getVisibility() == View.VISIBLE)
				btnUp.requestFocus();
			else
				btnGoBack.requestFocus();
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
			finish();
		} else if (view == btnURL) {
			final Context ctx = getHostActivity();
			final LinearLayout l = new LinearLayout(ctx);
			l.setOrientation(LinearLayout.VERTICAL);
			l.setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);
			TextView lbl = new TextView(ctx);
			lbl.setText(R.string.url);
			lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			l.addView(lbl);
			txtURL = new EditText(ctx);
			txtURL.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			txtURL.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
			LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			p.topMargin = UI._8dp;
			p.bottomMargin = UI._16dp;
			txtURL.setLayoutParams(p);
			l.addView(txtURL);
			lbl = new TextView(ctx);
			lbl.setText(R.string.description);
			lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			l.addView(lbl);
			txtTitle = new EditText(ctx);
			txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			txtTitle.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
			txtTitle.setSingleLine();
			p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			p.topMargin = UI._8dp;
			txtTitle.setLayoutParams(p);
			l.addView(txtTitle);
			(new AlertDialog.Builder(ctx))
			.setTitle(getText(R.string.add_url_title))
			.setView(l)
			.setPositiveButton(R.string.add, this)
			.setNegativeButton(R.string.cancel, this)
			.show();
		} else if (view == chkFavorite) {
			if (Player.path.length() <= 1)
				return;
			if (chkFavorite.isChecked())
				Player.addFavoriteFolder(Player.path);
			else
				Player.removeFavoriteFolder(Player.path);
		} if (view == btnHome) {
			if (Player.path.length() > 0)
				navigateTo("", Player.path);
		} else if (view == btnUp) {
			if (Player.path.length() > 1) {
				if (Player.path.equals(Player.originalPath)) {
					navigateTo("", Player.path);
					return;
				}
				if (Player.path.charAt(0) != File.separatorChar) {
					final int fakePathIdx = Player.path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR);
					final String realPath = Player.path.substring(0, fakePathIdx);
					final String fakePath = Player.path.substring(fakePathIdx + 1);
					int i = realPath.lastIndexOf(File.separatorChar, realPath.length() - 1);
					if (i < 0)
						navigateTo("", Player.path);
					else
						navigateTo(realPath.substring(0, i) + FileSt.FAKE_PATH_ROOT + fakePath.substring(0, fakePath.lastIndexOf(FileSt.FAKE_PATH_SEPARATOR_CHAR)), realPath + FileSt.FAKE_PATH_ROOT);
				} else {
					final int i = Player.path.lastIndexOf(File.separatorChar, Player.path.length() - 1);
					final String originalPath = Player.path;
					navigateTo((i <= 0) ? File.separator : Player.path.substring(0, i), ((i >= 0) && (i < originalPath.length())) ? originalPath.substring(i + 1) : null);
				}
			} else if (Player.path.length() == 1) {
				navigateTo("", Player.path);
			}
		} else if (view == btnMenu) {
			CustomContextMenu.openContextMenu(btnMenu, this);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int whichButton) {
		if (whichButton == AlertDialog.BUTTON_POSITIVE) {
			String url = txtURL.getText().toString().trim();
			if (url.length() >= 4) {
				int s = 7;
				final String urlLC = url.toLowerCase(Locale.ENGLISH);
				if (urlLC.startsWith("http://")) {
					url = "http://" + url.substring(7);
				} else if (urlLC.startsWith("https://")) {
					url = "https://" + url.substring(8);
					s = 8;
				} else {
					url = "http://" + url;
				}
				String title = txtTitle.getText().toString().trim();
				if (title.length() == 0)
					title = url.substring(s);
				final int p = Player.songs.getCount();
				Player.songs.add(new Song(url, title), -1);
				Player.setSelectionAfterAdding(p);
			}
		}
		txtURL = null;
		txtTitle = null;
	}
	
	@Override
	protected void onCreate() {
		if (Player.path == null)
			Player.path = "";
		if (Player.originalPath == null)
			Player.originalPath = "";
		fileList = new FileList();
		fileList.observerActivity = this;
		ic_closed_folder = getDrawable(R.drawable.ic_closed_folder);
		ic_internal = getDrawable(R.drawable.ic_internal);
		ic_external = getDrawable(R.drawable.ic_external);
		ic_favorite = getDrawable(R.drawable.ic_favorite);
		ic_artist = getDrawable(R.drawable.ic_artist);
		ic_album = getDrawable(R.drawable.ic_album);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_browser);
		lblPath = (TextView)findViewById(R.id.lblPath);
		lblPath.setText(Player.path);
		lblPath.setTextColor(UI.colorState_text_sel);
		lblPath.setBackgroundDrawable(new ColorDrawable(UI.color_current));
		list = (BgListView)findViewById(R.id.list);
		fileList.setObserver(list);
		panelLoading = (LinearLayout)findViewById(R.id.panelLoading);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnURL = (BgButton)findViewById(R.id.btnURL);
		btnURL.setOnClickListener(this);
		btnURL.setDefaultHeight();
		btnURL.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, true), null, null, null);
		chkFavorite = (BgButton)findViewById(R.id.chkFavorite);
		chkFavorite.setOnClickListener(this);
		chkFavorite.setIcon(UI.ICON_FAVORITE_ON, UI.ICON_FAVORITE_OFF, false);
		btnHome = (BgButton)findViewById(R.id.btnHome);
		btnHome.setOnClickListener(this);
		btnHome.setIcon(UI.ICON_HOME);
		btnUp = (BgButton)findViewById(R.id.btnUp);
		btnUp.setOnClickListener(this);
		btnUp.setIcon(UI.ICON_UP);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		btnMenu.setIcon(UI.ICON_MENU);
		if (UI.isLargeScreen) {
			lblPath.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
			lblPath.setPadding(UI._4dp, UI._4dp, UI._4dp, UI._4dp);
		} else if (UI.isLowDpiScreen) {
			findViewById(R.id.panelControls).setPadding(0, 0, 0, 0);
			btnURL.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		}
		CustomContextMenu.registerForContextMenu(btnMenu, this);
		navigateTo(Player.path, null);
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
		fileList.setObserver(null);
		fileList.observerActivity = null;
		list.setOnKeyDownObserver(null);
	}
	
	@Override
	protected void onResume() {
		list.setOnKeyDownObserver(this);
		fileList.observerActivity = this;
		fileList.setObserver(loading ? null : list);
		SongAddingMonitor.start(getHostActivity());
	}
	
	@Override
	protected void onCleanupLayout() {
		lblPath = null;
		list = null;
		panelLoading = null;
		btnGoBack = null;
		btnURL = null;
		chkFavorite = null;
		btnHome = null;
		btnUp = null;
		btnMenu = null;
	}
	
	@Override
	protected void onDestroy() {
		fileList.observerActivity = null;
		fileList.cancel();
		fileList = null;
		ic_closed_folder = null;
		ic_internal = null;
		ic_external = null;
		ic_favorite = null;
		ic_artist = null;
		ic_album = null;
	}
}
