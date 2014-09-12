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

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

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
import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
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

public final class ActivityBrowser2 extends ActivityBrowserView implements View.OnClickListener, DialogInterface.OnClickListener, BgListView.OnBgListViewKeyDownObserver {
	private static final int MNU_REMOVEFAVORITE = 100;
	private FileSt lastClickedFavorite;
	private TextView lblPath, sep, sep2;
	private BgListView list;
	private FileList fileList;
	private RelativeLayout panelSecondary, panelLoading;
	private EditText txtURL, txtTitle;
	private BgButton btnGoBack, btnRadio, btnURL, chkFavorite, chkAlbumArt, btnHome, chkAll, btnGoBackToPlayer, btnAdd, btnPlay;
	private AlbumArtFetcher albumArtFetcher;
	private int checkedCount;
	private boolean loading, isAtHome, verifyAlbumWhenChecking, albumArtArea;
	
	private void updateOverallLayout() {
		RelativeLayout.LayoutParams rp;
		if (isAtHome) {
			lblPath.setPadding(0, 0, 0, 0);
			rp = (RelativeLayout.LayoutParams)lblPath.getLayoutParams();
			rp.height = 0;
			lblPath.setLayoutParams(rp);
			panelSecondary.setPadding(0, 0, 0, 0);
			btnGoBackToPlayer.setVisibility(View.GONE);
			sep.setVisibility(View.GONE);
			chkAll.setVisibility(View.GONE);
			rp = new RelativeLayout.LayoutParams(UI.defaultControlSize, UI.defaultControlSize);
			rp.leftMargin = UI._8dp;
			rp.rightMargin = UI._8dp;
			rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			chkFavorite.setNextFocusUpId(R.id.list);
			btnHome.setLayoutParams(rp);
			btnHome.setNextFocusUpId(R.id.list);
			btnHome.setNextFocusRightId(R.id.list);
			UI.setNextFocusForwardId(btnHome, R.id.list);
			chkAll.setNextFocusUpId(R.id.list);
			btnGoBack.setNextFocusUpId(R.id.list);
			btnGoBack.setNextFocusLeftId(R.id.list);
			list.setNextFocusDownId(R.id.btnGoBack);
			list.setNextFocusRightId(R.id.btnGoBack);
			UI.setNextFocusForwardId(list, R.id.btnGoBack);
		} else {
			rp = (RelativeLayout.LayoutParams)lblPath.getLayoutParams();
			rp.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
			lblPath.setLayoutParams(rp);
			if (UI.isLargeScreen)
				lblPath.setPadding(UI._4dp, UI._4dp - UI.thickDividerSize, UI._4dp, UI._4dp);
			else
				lblPath.setPadding(UI._2dp, UI._2dp - UI.thickDividerSize, UI._2dp, UI._2dp);
			UI.prepareControlContainerPaddingOnly(panelSecondary, true, false);
			btnGoBackToPlayer.setVisibility(View.VISIBLE);
			sep.setVisibility(View.VISIBLE);
			chkAll.setVisibility(View.VISIBLE);
			rp = new RelativeLayout.LayoutParams(UI.defaultControlSize, UI.defaultControlSize);
			rp.leftMargin = UI._8dp;
			rp.rightMargin = UI._8dp;
			rp.addRule(RelativeLayout.LEFT_OF, R.id.sep);
			chkFavorite.setNextFocusUpId(R.id.btnGoBackToPlayer);
			btnHome.setLayoutParams(rp);
			btnHome.setNextFocusUpId((checkedCount != 0) ? R.id.btnAdd : R.id.btnGoBackToPlayer);
			btnHome.setNextFocusRightId(R.id.chkAll);
			UI.setNextFocusForwardId(btnHome, R.id.chkAll);
			chkAll.setNextFocusUpId((checkedCount != 0) ? R.id.btnPlay : R.id.btnGoBackToPlayer);
			btnGoBack.setNextFocusUpId(R.id.btnGoBackToPlayer);
			btnGoBack.setNextFocusLeftId((checkedCount != 0) ? R.id.btnPlay : R.id.btnGoBackToPlayer);
			list.setNextFocusDownId(R.id.btnGoBackToPlayer);
			list.setNextFocusRightId(R.id.btnGoBackToPlayer);
			UI.setNextFocusForwardId(list, R.id.btnGoBackToPlayer);
			btnGoBackToPlayer.setNextFocusRightId((checkedCount != 0) ? R.id.btnAdd : R.id.btnGoBack);
			UI.setNextFocusForwardId(btnGoBackToPlayer, (checkedCount != 0) ? R.id.btnAdd : R.id.btnGoBack);
		}
	}
	
	private void updateButtons() {
		if (!isAtHome != (chkAll.getVisibility() == View.VISIBLE))
			updateOverallLayout();
		if ((checkedCount != 0) != (btnAdd.getVisibility() == View.VISIBLE)) {
			if (checkedCount != 0) {
				btnAdd.setVisibility(View.VISIBLE);
				sep2.setVisibility(View.VISIBLE);
				btnPlay.setVisibility(View.VISIBLE);
				btnGoBack.setNextFocusLeftId(R.id.btnPlay);
				btnHome.setNextFocusUpId(R.id.btnAdd);
				chkAll.setNextFocusUpId(R.id.btnPlay);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnAdd);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnAdd);
			} else {
				btnAdd.setVisibility(View.GONE);
				sep2.setVisibility(View.GONE);
				btnPlay.setVisibility(View.GONE);
				btnGoBack.setNextFocusLeftId(R.id.btnGoBackToPlayer);
				btnHome.setNextFocusUpId(R.id.btnGoBackToPlayer);
				chkAll.setNextFocusUpId(R.id.btnGoBackToPlayer);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnGoBack);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnGoBack);
			}
		}
	}
	
	private void selectAlbumSongs(int position) {
		final boolean check = fileList.getItemT(position).isChecked;
		FileSt file;
		position++;
		while (position < fileList.getCount() && (file = fileList.getItemT(position)).specialType == 0) {
			file.isChecked = check;
			position++;
		}
		checkedCount = 0;
		for (int i = fileList.getCount() - 1; i >= 0; i--) {
			if (fileList.getItemT(i).isChecked)
				checkedCount++;
		}
		chkAll.setChecked(checkedCount == fileList.getCount());
		fileList.notifyCheckedChanged();
		updateButtons();
	}
	
	private void addPlayCheckedItems(final boolean play) {
		if (checkedCount <= 0)
			return;
		try {
			boolean addingFolder = false;
			int c = 0;
			final FileSt[] fs = new FileSt[checkedCount];
			for (int i = fileList.getCount() - 1, j = checkedCount - 1; i >= 0 && j >= 0; i--) {
				final FileSt file = fileList.getItemT(i);
				if (file.isChecked && file.specialType != FileSt.TYPE_ALBUM_ITEM) {
					if (!addingFolder) {
						c++;
						if (c > 1 || file.specialType != 0 || file.isDirectory)
							addingFolder = true;
					}
					fs[j] = file;
					j--;
				}
			}
			Player.songs.addingStarted();
			SongAddingMonitor.start(getHostActivity());
			(new Thread("Checked File Adder Thread") {
				@Override
				public void run() {
					boolean addingFolder = false;
					try {
						Throwable firstException = null;
						final ArrayList<FileSt> filesToAdd = new ArrayList<FileSt>(256);
						for (int i = 0; i < fs.length; i++) {
							if (Player.getState() == Player.STATE_TERMINATED || Player.getState() == Player.STATE_TERMINATING) {
								Player.songs.addingEnded();
								return;
							}
							final FileSt file = fs[i];
							if (file == null)
								continue;
							if (!file.isDirectory) {
								filesToAdd.add(file);
								if (!addingFolder && filesToAdd.size() > 1)
									addingFolder = true;
							} else {
								addingFolder = true;
								final FileFetcher ff = FileFetcher.fetchFilesInThisThread(file.path, null, false, true, true, false, false);
								if (ff.getThrowedException() == null) {
									if (ff.count <= 0)
										continue;
									filesToAdd.ensureCapacity(filesToAdd.size() + ff.count);
									for (int j = 0; j < ff.count; j++)
										filesToAdd.add(ff.files[j]);
								} else {
									if (firstException == null)
										firstException = ff.getThrowedException();
								}
							}
						}
						if (filesToAdd.size() <= 0) {
							if (firstException != null)
								Player.songs.onFilesFetched(null, firstException);
							else
								Player.songs.addingEnded();
						} else {
							Player.songs.addFiles(null, filesToAdd.iterator(), filesToAdd.size(), play, addingFolder);
						}
					} catch (Throwable ex) {
						Player.songs.addingEnded();
					}
				}
			}).start();
			if (play && addingFolder && Player.goBackWhenPlayingFolders)
				finish();
		} catch (Throwable ex) {
			Player.songs.addingEnded();
			UI.toast(getApplication(), ex.getMessage());
		}
	}
	
	@Override
	public void loadingProcessChanged(boolean started) {
		if (UI.browserActivity != this)
			return;
		loading = started;
		if (panelLoading != null)
			panelLoading.setVisibility(started ? View.VISIBLE : View.GONE);
		if (fileList != null) {
			verifyAlbumWhenChecking = ((fileList.getCount() > 0) && (fileList.getItemT(0).specialType == FileSt.TYPE_ALBUM_ITEM));
			if (list != null && !list.isInTouchMode())
				list.centerItem(fileList.getSelection(), false);
		}
		if (list != null)
			list.setCustomEmptyText(started ? "" : null);
		if (!started)
			updateButtons();
	}
	
	@Override
	public View createView() {
		return new FileView(Player.getService(), albumArtFetcher, true, true);
	}
	
	@Override
	public void processItemButtonClick(int position, boolean add) {
		if (!add && !list.isInTouchMode())
			fileList.setSelection(position, true);
		final FileSt file = fileList.getItemT(position);
		if (file.specialType == FileSt.TYPE_ALBUM_ITEM) {
			selectAlbumSongs(position);
		} else {
			if (file.isChecked) {
				if (verifyAlbumWhenChecking) {
					//check the album if all of its songs are checked
					while (--position >= 0) {
						final FileSt album = fileList.getItemT(position);
						if (album.specialType == FileSt.TYPE_ALBUM_ITEM) {
							boolean checkAlbum = true;
							while (++position < fileList.getCount()) {
								final FileSt song = fileList.getItemT(position);
								if (song.specialType != 0)
									break;
								if (!song.isChecked) {
									checkAlbum = false;
									break;
								}
							}
							if (checkAlbum && !album.isChecked) {
								album.isChecked = true;
								checkedCount++;
								fileList.notifyCheckedChanged();
							}
							break;
						}
					}
				}
				checkedCount++;
				if (checkedCount >= fileList.getCount()) {
					checkedCount = fileList.getCount();
					chkAll.setChecked(true);
				}
			} else {
				if (verifyAlbumWhenChecking) {
					//uncheck the album
					while (--position >= 0) {
						final FileSt album = fileList.getItemT(position);
						if (album.specialType == FileSt.TYPE_ALBUM_ITEM) {
							if (album.isChecked) {
								album.isChecked = false;
								checkedCount--;
								fileList.notifyCheckedChanged();
							}
							break;
						}
					}
				}
				checkedCount--;
				chkAll.setChecked(false);
				if (checkedCount < 0)
					checkedCount = 0;
			}
			updateButtons();
		}
	}
	
	@Override
	public void processItemClick(int position) {
		if (!UI.doubleClickMode || fileList.getSelection() == position) {
			final FileSt file = fileList.getItemT(position);
			if (file.isDirectory && file.specialType != FileSt.TYPE_ALBUM_ITEM) {
				navigateTo(file.path, null);
			} else {
				file.isChecked = !file.isChecked;
				if (file.specialType != FileSt.TYPE_ALBUM_ITEM)
					fileList.notifyCheckedChanged();
				processItemButtonClick(position, true);
			}
		} else {
			fileList.setSelection(position, true);
		}
	}
	
	@Override
	public void processItemLongClick(int position) {
		lastClickedFavorite = fileList.getItemT(position);
		if (lastClickedFavorite.specialType == FileSt.TYPE_FAVORITE)
			CustomContextMenu.openContextMenu(chkAll, this);
		else
			lastClickedFavorite = null;
	}
	
	private void processMenuItemClick(int id) {
		switch (id) {
		case MNU_REMOVEFAVORITE:
			if (lastClickedFavorite != null) {
				Player.removeFavoriteFolder(lastClickedFavorite.path);
				fileList.setSelection(fileList.indexOf(lastClickedFavorite), false);
				fileList.removeSelection();
				fileList.setSelection(-1, false);
			} else {
				UI.toast(getApplication(), R.string.msg_select_favorite_remove);
			}
			lastClickedFavorite = null;
			break;
		}
	}
	
	@Override
	public View getNullContextMenuView() {
		return null;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		UI.prepare(menu);
		menu.add(0, MNU_REMOVEFAVORITE, 0, R.string.remove_favorite)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_FAVORITE_OFF));
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		processMenuItemClick(item.getItemId());
		return true;
	}
	
	private void navigateTo(String to, String from) {
		if (isAtHome)
			Player.originalPath = to;
		isAtHome = (to.length() == 0);
		final boolean fav = ((to.length() > 1) && (to.charAt(0) == File.separatorChar));
		final boolean others = !isAtHome;
		albumArtArea = false;
		if (fav) {
			btnRadio.setVisibility(View.GONE);
			btnURL.setVisibility(View.GONE);
			btnGoBack.setNextFocusRightId(R.id.chkFavorite);
			UI.setNextFocusForwardId(btnGoBack, R.id.chkFavorite);
			btnHome.setNextFocusLeftId(R.id.chkFavorite);
			chkFavorite.setChecked(Player.isFavoriteFolder(to));
			chkFavorite.setVisibility(View.VISIBLE);
			chkAlbumArt.setVisibility(View.GONE);
			btnHome.setVisibility(View.VISIBLE);
		} else if (others) {
			albumArtArea = ((to.length() > 0) && ((to.charAt(0) == FileSt.ALBUM_ROOT_CHAR) || (to.charAt(0) == FileSt.ARTIST_ROOT_CHAR)));// (to.startsWith(FileSt.ALBUM_PREFIX) || to.startsWith(FileSt.ARTIST_ALBUM_PREFIX));
			btnRadio.setVisibility(View.GONE);
			btnURL.setVisibility(View.GONE);
			if (albumArtArea) {
				btnGoBack.setNextFocusRightId(R.id.chkAlbumArt);
				UI.setNextFocusForwardId(btnGoBack, R.id.chkAlbumArt);
				btnHome.setNextFocusLeftId(R.id.chkAlbumArt);
			} else {
				btnGoBack.setNextFocusRightId(R.id.btnHome);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnHome);
				btnHome.setNextFocusLeftId(R.id.btnGoBack);
			}
			chkFavorite.setVisibility(View.GONE);
			chkAlbumArt.setVisibility(albumArtArea ? View.VISIBLE : View.GONE);
			btnHome.setVisibility(View.VISIBLE);
		} else {
			btnRadio.setVisibility(View.VISIBLE);
			btnURL.setVisibility(View.VISIBLE);
			btnGoBack.setNextFocusRightId(R.id.btnRadio);
			UI.setNextFocusForwardId(btnGoBack, R.id.btnRadio);
			chkFavorite.setVisibility(View.GONE);
			chkAlbumArt.setVisibility(View.GONE);
			btnHome.setVisibility(View.GONE);
		}
		checkedCount = 0;
		chkAll.setChecked(false);
		updateButtons();
		Player.path = to;
		lblPath.setText(((to.length() > 0) && (to.charAt(0) != File.separatorChar)) ? to.substring(to.indexOf(FileSt.FAKE_PATH_ROOT_CHAR) + 1).replace(FileSt.FAKE_PATH_SEPARATOR_CHAR, File.separatorChar) : to);
		final boolean sectionsEnabled = ((to.length() > 0) && (to.startsWith(FileSt.ARTIST_PREFIX) || to.startsWith(FileSt.ALBUM_PREFIX)));
		list.setScrollBarType(((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) && !sectionsEnabled) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
		fileList.setPath(to, from, list.isInTouchMode(), (UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) && sectionsEnabled);
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView bgListView, int keyCode, KeyEvent event) {
		int p;
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
			if (btnURL.getVisibility() == View.VISIBLE)
				btnURL.requestFocus();
			else if (chkAll.getVisibility() == View.VISIBLE)
				chkAll.requestFocus();
			else
				btnGoBack.requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			if (btnGoBackToPlayer.getVisibility() == View.VISIBLE)
				btnGoBackToPlayer.requestFocus();
			else
				btnGoBack.requestFocus();
			return true;
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_DPAD_CENTER:
			p = fileList.getSelection();
			if (p >= 0)
				processItemClick(p);
			return true;
		case KeyEvent.KEYCODE_0:
		case KeyEvent.KEYCODE_SPACE:
			p = fileList.getSelection();
			if (p >= 0) {
				final FileSt file = fileList.getItemT(p);
				file.isChecked = !file.isChecked;
				processItemButtonClick(p, false);
			}
			return true;
		}
		return false;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
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
			} else {
				finish();
			}
		} else if (view == btnRadio) {
			UI.toast(getHostActivity(), R.string.coming_soon);
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
			UI.prepareDialogAndShow((new AlertDialog.Builder(ctx))
			.setTitle(getText(R.string.add_url_title))
			.setView(l)
			.setPositiveButton(R.string.add, this)
			.setNegativeButton(R.string.cancel, this)
			.create());
		} else if (view == chkFavorite) {
			if (Player.path.length() <= 1)
				return;
			if (chkFavorite.isChecked())
				Player.addFavoriteFolder(Player.path);
			else
				Player.removeFavoriteFolder(Player.path);
		} else if (view == chkAlbumArt) {
			UI.albumArt = chkAlbumArt.isChecked();
			for (int i = list.getChildCount() - 1; i >= 0; i--) {
				final View v = list.getChildAt(i);
				if (v instanceof FileView)
					((FileView)v).refreshItem();
			}
		} if (view == btnHome) {
			if (Player.path.length() > 0)
				navigateTo("", Player.path);
		} else if (view == chkAll) {
			if (loading)
				return;
			final boolean ck = chkAll.isChecked();
			int i = fileList.getCount() - 1;
			checkedCount = (ck ? (i + 1) : 0);
			for (; i >= 0; i--)
				fileList.getItemT(i).isChecked = ck;
			fileList.notifyCheckedChanged();
			updateButtons();
		} else if (view == btnGoBackToPlayer) {
			finish();
		} else if (view == btnAdd) {
			addPlayCheckedItems(false);
		} else if (view == btnPlay) {
			addPlayCheckedItems(true);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE) {
			String url = txtURL.getText().toString().trim();
			if (url.length() >= 4) {
				int s = 7;
				final String urlLC = url.toLowerCase(Locale.US);
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
	protected boolean onBackPressed() {
		if (UI.backKeyAlwaysReturnsToPlayerWhenBrowsing || isAtHome)
			return false;
		if (chkAll != null && checkedCount != 0) {
			chkAll.setChecked(false);
			checkedCount = 0;
			for (int i = fileList.getCount() - 1; i >= 0; i--)
				fileList.getItemT(i).isChecked = false;
			fileList.notifyCheckedChanged();
			updateButtons();
			return true;
		}
		onClick(btnGoBack);
		return true;
	}
	
	@Override
	protected void onCreate() {
		if (Player.path == null)
			Player.path = "";
		if (Player.originalPath == null)
			Player.originalPath = "";
		isAtHome = (Player.path.length() == 0);
		UI.browserActivity = this;
		fileList = new FileList();
		//We cannot use getDrawable() here, as sometimes the bitmap used by the drawable
		//is internally cached, therefore, causing an exception when we try to use it
		//after being recycled...
		//final Resources res = getResources();
		//try {
		//	ic_closed_folder = new ReleasableBitmapWrapper(BitmapFactory.decodeResource(res, R.drawable.ic_closed_folder));
		//} catch (Throwable ex) {
		//}
		albumArtFetcher = new AlbumArtFetcher();
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_browser2);
		final TextView lblLoading = (TextView)findViewById(R.id.lblLoading);
		UI.largeText(lblLoading);
		lblLoading.setTextColor(UI.colorState_text_listitem_static);
		lblPath = (TextView)findViewById(R.id.lblPath);
		lblPath.setTextColor(UI.colorState_text_highlight_static);
		UI.mediumText(lblPath);
		lblPath.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
		list = (BgListView)findViewById(R.id.list);
		list.setOnKeyDownObserver(this);
		fileList.setObserver(list);
		panelLoading = (RelativeLayout)findViewById(R.id.panelLoading);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnRadio = (BgButton)findViewById(R.id.btnRadio);
		btnRadio.setOnClickListener(this);
		btnRadio.setDefaultHeight();
		btnRadio.setCompoundDrawables(new TextIconDrawable(UI.ICON_RADIO, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		btnURL = (BgButton)findViewById(R.id.btnURL);
		btnURL.setOnClickListener(this);
		btnURL.setDefaultHeight();
		btnURL.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		chkFavorite = (BgButton)findViewById(R.id.chkFavorite);
		chkFavorite.setOnClickListener(this);
		chkFavorite.setIcon(UI.ICON_FAVORITE_ON, UI.ICON_FAVORITE_OFF, false, false, true, true);
		chkAlbumArt = (BgButton)findViewById(R.id.chkAlbumArt);
		chkAlbumArt.setOnClickListener(this);
		chkAlbumArt.setIcon(UI.ICON_ALBUMART, UI.ICON_ALBUMART_OFF, UI.albumArt, false, true, true);
		btnHome = (BgButton)findViewById(R.id.btnHome);
		btnHome.setOnClickListener(this);
		btnHome.setIcon(UI.ICON_HOME);
		panelSecondary = (RelativeLayout)findViewById(R.id.panelSecondary);
		sep = (TextView)findViewById(R.id.sep);
		RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
		rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
		rp.addRule(RelativeLayout.LEFT_OF, R.id.chkAll);
		rp.leftMargin = UI._8dp;
		rp.rightMargin = UI._8dp;
		sep.setLayoutParams(rp);
		sep.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
		chkAll = (BgButton)findViewById(R.id.chkAll);
		chkAll.setOnClickListener(this);
		chkAll.setIcon(UI.ICON_OPTCHK, UI.ICON_OPTUNCHK, false, true, true, true);
		btnGoBackToPlayer = (BgButton)findViewById(R.id.btnGoBackToPlayer);
		btnGoBackToPlayer.setTextColor(UI.colorState_text_reactive);
		btnGoBackToPlayer.setOnClickListener(this);
		btnGoBackToPlayer.setCompoundDrawables(new TextIconDrawable(UI.ICON_RIGHT, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		btnGoBackToPlayer.setDefaultHeight();
		btnAdd = (BgButton)findViewById(R.id.btnAdd);
		btnAdd.setTextColor(UI.colorState_text_reactive);
		btnAdd.setOnClickListener(this);
		btnAdd.setIcon(UI.ICON_ADD, true, false);
		sep2 = (TextView)findViewById(R.id.sep2);
		rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
		rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
		rp.addRule(RelativeLayout.LEFT_OF, R.id.btnPlay);
		rp.leftMargin = UI._8dp;
		rp.rightMargin = UI._8dp;
		sep2.setLayoutParams(rp);
		sep2.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
		btnPlay = (BgButton)findViewById(R.id.btnPlay);
		btnPlay.setTextColor(UI.colorState_text_reactive);
		btnPlay.setOnClickListener(this);
		btnPlay.setIcon(UI.ICON_PLAY, true, false);
		if (UI.isLargeScreen) {
			lblPath.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		} else if (UI.isLowDpiScreen) {
			btnRadio.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
			btnURL.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		}
		if (UI.extraSpacing) {
			final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, UI.defaultControlSize);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			lp.rightMargin = UI._8dp;
			btnURL.setLayoutParams(lp);
		}
		UI.prepareControlContainerWithoutRightPadding(findViewById(R.id.panelControls), false, true);
		UI.prepareControlContainer(panelSecondary, true, false);
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
		UI.prepareEdgeEffectColor(getApplication());
		//this is the opposite as in updateButtons(), to force updateOverallLayout()
		//to be called at least once
		if (!isAtHome == (chkAll.getVisibility() == View.VISIBLE))
			updateOverallLayout();
		navigateTo(Player.path, null);
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
		fileList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		UI.browserActivity = this;
		fileList.setObserver(list);
		SongAddingMonitor.start(getHostActivity());
		if (loading != fileList.isLoading())
			loadingProcessChanged(fileList.isLoading());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (UI.isLargeScreen && list != null)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		lastClickedFavorite = null;
		lblPath = null;
		list = null;
		panelLoading = null;
		panelSecondary = null;
		btnGoBack = null;
		btnRadio = null;
		btnURL = null;
		chkFavorite = null;
		chkAlbumArt = null;
		btnHome = null;
		sep = null;
		chkAll = null;
		btnGoBackToPlayer = null;
		btnAdd = null;
		sep2 = null;
		btnPlay = null;
	}
	
	@Override
	protected void onDestroy() {
		UI.browserActivity = null;
		fileList.cancel();
		fileList = null;
		if (albumArtFetcher != null) {
			albumArtFetcher.stopAndCleanup();
			albumArtFetcher = null;
		}
	}
}
