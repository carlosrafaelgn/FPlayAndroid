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

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.AlbumArtFetcher;
import br.com.carlosrafaelgn.fplay.list.FileFetcher;
import br.com.carlosrafaelgn.fplay.list.FileList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BackgroundActivityMonitor;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.FastAnimator;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.TypedRawArrayList;

public final class ActivityBrowser2 extends ActivityBrowserView implements View.OnClickListener, DialogInterface.OnClickListener, DialogInterface.OnCancelListener, BgListView.OnBgListViewKeyDownObserver, FastAnimator.Observer {
	private static final int MNU_REMOVEFAVORITE = 100;
	private FileSt lastClickedFavorite;
	private TextView lblPath, sep, sep2, lblLoading;
	private BgListView list;
	private FileList fileList;
	private RelativeLayout panelSecondary;
	private EditText txtURL, txtTitle;
	private BgButton btnGoBack, btnURL, chkFavorite, chkAlbumArt, btnHome, chkAll, btnGoBackToPlayer, btnAdd, btnPlay;
	private AlbumArtFetcher albumArtFetcher;
	private int checkedCount;
	private boolean loading, isAtHome, verifyAlbumWhenChecking, isCreatingLayout;
	private FastAnimator animator;
	private CharSequence msgEmptyList, msgLoading;
	private String pendingTo;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.add_songs);
	}

	private void updateOverallLayout() {
		RelativeLayout.LayoutParams rp;
		if (isAtHome) {
			lblPath.setPadding(0, 0, 0, 0);
			rp = (RelativeLayout.LayoutParams)lblPath.getLayoutParams();
			rp.height = 0;
			lblPath.setLayoutParams(rp);
			if (panelSecondary.getVisibility() != View.GONE) {
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
				rp.addRule(RelativeLayout.BELOW, R.id.lblPath);
				rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
				list.setLayoutParams(rp);
				//do not change lblLoading's layout, as it covers the background behind panelSecondary
				/*if (lblLoading != null) {
					rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
					rp.addRule(RelativeLayout.BELOW, R.id.lblPath);
					rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
					lblLoading.setLayoutParams(rp);
				}*/
				UI.animationAddViewToHide(panelSecondary);
			}
			//UI.animationAddViewToHide(btnGoBackToPlayer);
			UI.animationAddViewToHide(sep);
			UI.animationAddViewToHide(chkAll);
			chkFavorite.setNextFocusUpId(R.id.list);
			btnHome.setNextFocusUpId(R.id.list);
			btnHome.setNextFocusRightId(R.id.list);
			UI.setNextFocusForwardId(btnHome, R.id.list);
			chkAll.setNextFocusUpId(R.id.list);
			btnGoBack.setNextFocusUpId(R.id.list);
			btnGoBack.setNextFocusLeftId(R.id.list);
			UI.setNextFocusForwardId(list, R.id.btnGoBack);
		} else {
			rp = (RelativeLayout.LayoutParams)lblPath.getLayoutParams();
			rp.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
			lblPath.setLayoutParams(rp);
			final int m = (UI.isLargeScreen ? UI.controlSmallMargin : (UI.controlSmallMargin >> 1));
			lblPath.setPadding(m, m - UI.thickDividerSize, m, m);
			if (panelSecondary.getVisibility() != View.VISIBLE) {
				panelSecondary.setVisibility(View.VISIBLE);
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
				rp.addRule(RelativeLayout.BELOW, R.id.lblPath);
				rp.addRule(RelativeLayout.ABOVE, R.id.panelSecondary);
				list.setLayoutParams(rp);
				//do not change lblLoading's layout, as it covers the background behind panelSecondary
				/*if (lblLoading != null) {
					rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
					rp.addRule(RelativeLayout.BELOW, R.id.lblPath);
					rp.addRule(RelativeLayout.ABOVE, R.id.panelSecondary);
					lblLoading.setLayoutParams(rp);
				}*/
				UI.animationSetViewToShowFirst(panelSecondary);
			}
			//UI.animationAddViewToShow(btnGoBackToPlayer);
			UI.animationAddViewToShow(sep);
			UI.animationAddViewToShow(chkAll);
			chkFavorite.setNextFocusUpId(R.id.btnGoBackToPlayer);
			btnHome.setNextFocusUpId((checkedCount != 0) ? R.id.btnAdd : R.id.btnGoBackToPlayer);
			btnHome.setNextFocusRightId(R.id.chkAll);
			UI.setNextFocusForwardId(btnHome, R.id.chkAll);
			chkAll.setNextFocusUpId((checkedCount != 0) ? R.id.btnPlay : R.id.btnGoBackToPlayer);
			btnGoBack.setNextFocusUpId(R.id.btnGoBackToPlayer);
			btnGoBack.setNextFocusLeftId((checkedCount != 0) ? R.id.btnPlay : R.id.btnGoBackToPlayer);
			UI.setNextFocusForwardId(list, R.id.btnGoBackToPlayer);
			btnGoBackToPlayer.setNextFocusRightId((checkedCount != 0) ? R.id.btnAdd : R.id.btnGoBack);
			UI.setNextFocusForwardId(btnGoBackToPlayer, (checkedCount != 0) ? R.id.btnAdd : R.id.btnGoBack);
		}
	}
	
	private void updateButtons(boolean standaloneAnimation) {
		if (standaloneAnimation)
			UI.animationReset();
		if (isAtHome == (chkAll.getVisibility() == View.VISIBLE))
			updateOverallLayout();
		if ((checkedCount == 0) == (btnAdd.getVisibility() == View.VISIBLE)) {
			if (checkedCount != 0) {
				UI.animationAddViewToShow(btnAdd);
				UI.animationAddViewToShow(sep2);
				UI.animationAddViewToShow(btnPlay);
				btnGoBack.setNextFocusLeftId(R.id.btnPlay);
				btnHome.setNextFocusUpId(R.id.btnAdd);
				chkAll.setNextFocusUpId(R.id.btnPlay);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnAdd);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnAdd);
			} else {
				UI.animationAddViewToHide(btnAdd);
				UI.animationAddViewToHide(sep2);
				UI.animationAddViewToHide(btnPlay);
				btnGoBack.setNextFocusLeftId(R.id.btnGoBackToPlayer);
				btnHome.setNextFocusUpId(R.id.btnGoBackToPlayer);
				chkAll.setNextFocusUpId(R.id.btnGoBackToPlayer);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnGoBack);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnGoBack);
			}
		}
		if (standaloneAnimation)
			UI.animationCommit(isCreatingLayout, null);
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
		updateButtons(true);
	}
	
	private void addPlayCheckedItems(final boolean play) {
		if (checkedCount <= 0)
			return;
		Player.songs.addingStarted();
		BackgroundActivityMonitor.start(getHostActivity());
		boolean addingFolder = false;
		try {
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
			(new Thread("Checked File Adder Thread") {
				@Override
				public void run() {
					boolean addingFolder = false;
					final TypedRawArrayList<FileSt> filesToAdd = new TypedRawArrayList<>(FileSt.class, 256);
					try {
						Throwable firstException = null;
						for (int i = 0; i < fs.length; i++) {
							if (Player.state >= Player.STATE_TERMINATING)
								return;
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
								final Throwable thrownException = ff.getThrownException();
								if (thrownException == null) {
									if (ff.count <= 0)
										continue;
									filesToAdd.ensureCapacity(filesToAdd.size() + ff.count);
									for (int j = 0; j < ff.count; j++)
										filesToAdd.add(ff.files[j]);
								} else {
									if (firstException == null)
										firstException = thrownException;
								}
							}
						}
						if (filesToAdd.size() <= 0) {
							if (firstException != null && Player.state == Player.STATE_ALIVE)
								MainHandler.toast(firstException);
						} else {
							Player.songs.addFiles(null, filesToAdd.iterator(), filesToAdd.size(), play, addingFolder, false, false);
						}
					} catch (Throwable ex) {
						ex.printStackTrace();
					} finally {
						Player.songs.addingEnded();
					}
					filesToAdd.clear();
				}
			}).start();
		} catch (Throwable ex) {
			Player.songs.addingEnded();
			UI.toast(getApplication(), ex.getMessage());
		}
		if (play && addingFolder && Player.goBackWhenPlayingFolders) {
			finish(0, null, true);
		} else {
			//Unselect everything after adding/playing
			chkAll.setChecked(false);
			checkedCount = 0;
			for (int c = fileList.getCount() - 1; c >= 0; c--)
				fileList.getItemT(c).isChecked = false;
			fileList.notifyCheckedChanged();
			updateButtons(true);
		}
	}

	@Override
	public void loadingProcessChanged(boolean started) {
		if (UI.browserActivity != this)
			return;
		loading = started;
		if (fileList != null) {
			verifyAlbumWhenChecking = ((fileList.getCount() > 0) && (fileList.getItemT(0).specialType == FileSt.TYPE_ALBUM_ITEM));
			if (list != null && !list.isInTouchMode())
				list.centerItem(fileList.getSelection());
		}
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
			if (!started && UI.accessibilityManager != null && UI.accessibilityManager.isEnabled() && fileList != null) {
				if (fileList.getCount() == 0) {
					UI.announceAccessibilityText(msgEmptyList);
				} else {
					final int i = fileList.getFirstSelectedPosition();
					UI.announceAccessibilityText(FileView.makeContextDescription(!isAtHome, getHostActivity(), fileList.getItemT(i < 0 ? 0 : i)));
				}
			}
		}
		//if (!started)
		//	updateButtons(true);
	}
	
	@Override
	public View createView() {
		return new FileView(Player.getService(), albumArtFetcher, true);
	}

	private void processItemCheckboxClickInternal(int position, boolean forceNotifyCheckedChanged) {
		//somehow, Google Play indicates a NullPointerException, either here or
		//in processItemClick, in a LG Optimus L3 (2.3.3) :/
		if (list == null || fileList == null)
			return;
		if (!list.isInTouchMode())
			fileList.setSelection(position, true);
		final FileSt file = fileList.getItemT(position);
		if (file == null) //same as above
			return;
		final int originalPosition = position;
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
								forceNotifyCheckedChanged = false;
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
								forceNotifyCheckedChanged = false;
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
			updateButtons(true);
		}
		if (forceNotifyCheckedChanged) {
			if (list != null) {
				final FileView view = (FileView)list.getViewForPosition(originalPosition);
				if (view != null) {
					view.refreshItem();
					return;
				}
			}
			fileList.notifyCheckedChanged();
		}
	}

	@Override
	public void processItemCheckboxClick(int position) {
		processItemCheckboxClickInternal(position, false);
	}
	
	@Override
	public void processItemClick(int position) {
		//somehow, Google Play indicates a NullPointerException, either here or
		//in processItemButtonClick, in a LG Optimus L3 (2.3.3) :/
		if (list == null || fileList == null)
			return;
		if (!UI.doubleClickMode || fileList.getSelection() == position) {
			final FileSt file = fileList.getItemT(position);
			if (file == null) //same as above
				return;
			switch (file.specialType) {
			case FileSt.TYPE_ICECAST:
				fileList.setSelection(position, position, false, true);
				startActivity(new ActivityBrowserRadio(false), 1, list.getViewForPosition(position), true);
				return;
			case FileSt.TYPE_SHOUTCAST:
				fileList.setSelection(position, position, false, true);
				startActivity(new ActivityBrowserRadio(true), 1, list.getViewForPosition(position), true);
				return;
			}
			if (file.isDirectory && file.specialType != FileSt.TYPE_ALBUM_ITEM) {
				navigateTo(file.path, null, false);
			} else {
				file.isChecked = !file.isChecked;
				processItemCheckboxClickInternal(position, true);
			}
		} else {
			fileList.setSelection(position, true);
		}
	}
	
	@Override
	public void processItemLongClick(int position) {
		if (loading || list == null || fileList == null || position < 0 || position >= fileList.getCount())
			return;
		if (!isAtHome) {
			final boolean forceHideButtons = (btnAdd != null && btnAdd.getVisibility() != View.VISIBLE);
			//unselect everything, then select the item, and finally play
			int i = fileList.getCount() - 1;
			checkedCount = 0;
			for (; i >= 0; i--)
				fileList.getItemT(i).isChecked = false;
			final FileSt file = fileList.getItemT(position);
			file.isChecked = true;
			processItemCheckboxClickInternal(position, false);
			addPlayCheckedItems(true);
			if (forceHideButtons) {
				//hide all the buttons to prevent flickering
				btnAdd.setVisibility(View.GONE);
				sep2.setVisibility(View.GONE);
				btnPlay.setVisibility(View.GONE);
			}
			return;
		}
		lastClickedFavorite = fileList.getItemT(position);
		if (lastClickedFavorite.specialType == FileSt.TYPE_FAVORITE) {
			if (UI.doubleClickMode && fileList.getSelection() != position)
				fileList.setSelection(position, true);
			CustomContextMenu.openContextMenu(list, this);
		} else {
			lastClickedFavorite = null;
		}
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
		if (isAtHome && !loading && list != null && fileList != null) {
			final int p = fileList.getSelection();
			if (p < 0 || p >= fileList.getCount())
				return null;
			lastClickedFavorite = fileList.getItemT(p);
			if (lastClickedFavorite.specialType == FileSt.TYPE_FAVORITE)
				return list;
			lastClickedFavorite = null;
		}
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
	
	private void navigateTo(String to, String from, boolean onlyUpdateButtons) {
		UI.animationReset();
		if (isAtHome)
			Player.originalPath = to;
		isAtHome = (to.length() == 0);
		final boolean fav = ((to.length() > 1) && (to.charAt(0) == File.separatorChar));
		final boolean others = !isAtHome;
		if (fav) {
			UI.animationAddViewToHide(btnURL);
			btnGoBack.setNextFocusRightId(R.id.chkFavorite);
			UI.setNextFocusForwardId(btnGoBack, R.id.chkFavorite);
			btnHome.setNextFocusLeftId(R.id.chkFavorite);
			chkFavorite.setChecked(Player.isFavoriteFolder(to));
			UI.animationAddViewToShow(chkFavorite);
			UI.animationAddViewToHide(chkAlbumArt);
			UI.animationAddViewToShow(btnHome);
		} else if (others) {
			final boolean albumArtArea = ((to.length() > 0) && ((to.charAt(0) == FileSt.ALBUM_ROOT_CHAR) || (to.charAt(0) == FileSt.ARTIST_ROOT_CHAR)));// (to.startsWith(FileSt.ALBUM_PREFIX) || to.startsWith(FileSt.ARTIST_ALBUM_PREFIX));
			UI.animationAddViewToHide(btnURL);
			if (albumArtArea) {
				btnGoBack.setNextFocusRightId(R.id.chkAlbumArt);
				UI.setNextFocusForwardId(btnGoBack, R.id.chkAlbumArt);
				btnHome.setNextFocusLeftId(R.id.chkAlbumArt);
			} else {
				btnGoBack.setNextFocusRightId(R.id.btnHome);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnHome);
				btnHome.setNextFocusLeftId(R.id.btnGoBack);
			}
			UI.animationAddViewToHide(chkFavorite);
			if (albumArtArea)
				UI.animationAddViewToShow(chkAlbumArt);
			else
				UI.animationAddViewToHide(chkAlbumArt);
			UI.animationAddViewToShow(btnHome);
		} else {
			UI.animationAddViewToShow(btnURL);
			btnGoBack.setNextFocusRightId(R.id.btnURL);
			UI.setNextFocusForwardId(btnGoBack, R.id.btnURL);
			UI.animationAddViewToHide(chkFavorite);
			UI.animationAddViewToHide(chkAlbumArt);
			UI.animationAddViewToHide(btnHome);
		}
		checkedCount = 0;
		chkAll.setChecked(false);
		updateButtons(false);
		UI.animationCommit(isCreatingLayout, null);
		Player.path = to;
		lblPath.setText(((to.length() > 0) && (to.charAt(0) != File.separatorChar)) ? to.substring(to.indexOf(FileSt.FAKE_PATH_ROOT_CHAR) + 1).replace(FileSt.FAKE_PATH_SEPARATOR_CHAR, File.separatorChar) : to);
		final boolean sectionsEnabled = ((to.length() > 0) && (to.startsWith(FileSt.ARTIST_PREFIX) || to.startsWith(FileSt.ALBUM_PREFIX)));
		list.setScrollBarType(((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) && !sectionsEnabled) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
		FileView.updateExtraMargins(list.getScrollBarType() == BgListView.SCROLLBAR_INDEXED);
		if (!onlyUpdateButtons)
			fileList.setPath(to, from, list.isInTouchMode(), (UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) && sectionsEnabled);
	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 1 && pendingTo != null && grantResults != null) {
			navigateTo(pendingTo, null, false);
			pendingTo = null;
		}
	}

	@Override
	public boolean onBgListViewKeyDown(BgListView list, int keyCode) {
		final int p;
		switch (keyCode) {
		case UI.KEY_LEFT:
			if (btnURL != null && chkAll != null)
				((btnURL.getVisibility() == View.VISIBLE) ? btnURL : ((chkAll.getVisibility() == View.VISIBLE) ? chkAll : btnGoBack)).requestFocus();
			return true;
		case UI.KEY_RIGHT:
			if (btnGoBackToPlayer != null && btnGoBack != null && panelSecondary != null)
				((panelSecondary.getVisibility() == View.VISIBLE) ? btnGoBackToPlayer : btnGoBack).requestFocus();
			return true;
		case UI.KEY_ENTER:
			if (fileList != null && (p = fileList.getSelection()) >= 0)
				processItemClick(p);
			return true;
		case UI.KEY_EXTRA:
			if (!isAtHome && fileList != null && (p = fileList.getSelection()) >= 0) {
				final FileSt file = fileList.getItemT(p);
				file.isChecked = !file.isChecked;
				processItemCheckboxClickInternal(p, true);
			}
			return true;
		}
		return false;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			if (chkAll != null && checkedCount != 0) {
				chkAll.setChecked(false);
				checkedCount = 0;
				for (int i = fileList.getCount() - 1; i >= 0; i--)
					fileList.getItemT(i).isChecked = false;
				fileList.notifyCheckedChanged();
				updateButtons(true);
				return;
			}
			if (Player.path.length() != 0) {
				//does not work well... the focused item is always the first (but the selected item may vary)
				//if (UI.accessibilityManager != null && UI.accessibilityManager.isEnabled() && list != null)
				//	list.requestFocusFromTouch();
				if (Player.path.length() == 1 || Player.path.equals(Player.originalPath)) {
					navigateTo("", Player.path, false);
					return;
				}
				if (Player.path.charAt(0) != File.separatorChar) {
					final int fakePathIdx = Player.path.indexOf(FileSt.FAKE_PATH_ROOT_CHAR);
					final String realPath = Player.path.substring(0, fakePathIdx);
					final String fakePath = Player.path.substring(fakePathIdx + 1);
					int i = realPath.lastIndexOf(File.separatorChar, realPath.length() - 1);
					if (i < 0)
						navigateTo("", Player.path, false);
					else
						navigateTo(realPath.substring(0, i) + FileSt.FAKE_PATH_ROOT + fakePath.substring(0, fakePath.lastIndexOf(FileSt.FAKE_PATH_SEPARATOR_CHAR)), realPath + FileSt.FAKE_PATH_ROOT, false);
				} else {
					final int i = Player.path.lastIndexOf(File.separatorChar, Player.path.length() - 1);
					final String originalPath = Player.path;
					navigateTo((i <= 0) ? File.separator : Player.path.substring(0, i), ((i >= 0) && (i < originalPath.length())) ? originalPath.substring(i + 1) : null, false);
				}
			} else {
				finish(0, view, true);
			}
		} else if (view == btnURL) {
			final Context ctx = getHostActivity();
			final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);
			
			l.addView(UI.createDialogTextView(ctx, 0, null, ctx.getText(R.string.url)));

			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			p.bottomMargin = UI.dialogMargin << 1;
			txtURL = UI.createDialogEditText(ctx, 1, p, null, ctx.getText(R.string.url), InputType.TYPE_TEXT_VARIATION_URI);
			l.addView(txtURL);

			l.addView(UI.createDialogTextView(ctx, 0, null, ctx.getText(R.string.description)));

			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			txtTitle = UI.createDialogEditText(ctx, 0, p, null, ctx.getText(R.string.description), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
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
				navigateTo("", Player.path, false);
		} else if (view == chkAll) {
			if (loading || isAtHome)
				return;
			final boolean ck = chkAll.isChecked();
			int i = fileList.getCount() - 1;
			checkedCount = (ck ? (i + 1) : 0);
			for (; i >= 0; i--)
				fileList.getItemT(i).isChecked = ck;
			fileList.notifyCheckedChanged();
			updateButtons(true);
		} else if (view == btnGoBackToPlayer) {
			finish(0, view, true);
		} else if (view == btnAdd) {
			addPlayCheckedItems(false);
		} else if (view == btnPlay) {
			addPlayCheckedItems(true);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE && txtURL != null && txtTitle != null) {
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
	public void onCancel(DialogInterface dialog) {
		onClick(dialog, AlertDialog.BUTTON_NEGATIVE);
	}
	
	public void activityFinished(ClientActivity activity, int requestCode, int code) {
		if (requestCode == 1 && code == -1)
			finish(0, null, true);
	}
	
	@Override
	protected boolean onBackPressed() {
		if (UI.backKeyAlwaysReturnsToPlayerWhenBrowsing || isAtHome)
			return false;
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
		lblPath = (TextView)findViewById(R.id.lblPath);
		lblPath.setTextColor(UI.colorState_text_highlight_static);
		UI.mediumText(lblPath);
		lblPath.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
		msgEmptyList = getText(R.string.empty_list);
		msgLoading = getText(R.string.loading);
		list = (BgListView)findViewById(R.id.list);
		list.setOnKeyDownObserver(this);
		if (UI.animationEnabled) {
			if (firstCreation)
				list.setVisibility(View.GONE);
			list.setCustomEmptyText(msgEmptyList);
			animator = new FastAnimator(list, false, this, 0);
			lblLoading = (TextView)findViewById(R.id.lblLoading);
			//try to center the text by making up for panelSecondary
			lblLoading.setPadding(0, 0, 0, UI.defaultControlSize + UI._1dp + (UI.extraSpacing ? (UI.controlMargin << 1) : 0));
			lblLoading.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
			lblLoading.setTextColor(UI.color_text_disabled);
			UI.largeText(lblLoading);
			//when returning from the radio
			lblLoading.setVisibility((fileList.getCount() > 0) ? View.GONE : View.VISIBLE);
		} else if (firstCreation) {
			list.setCustomEmptyText(msgLoading);
		}
		fileList.setObserver(list);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnURL = (BgButton)findViewById(R.id.btnURL);
		btnURL.setOnClickListener(this);
		btnURL.setDefaultHeight();
		btnURL.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		chkFavorite = (BgButton)findViewById(R.id.chkFavorite);
		chkFavorite.setOnClickListener(this);
		chkFavorite.formatAsCheckBox(UI.ICON_FAVORITE_ON, UI.ICON_FAVORITE_OFF, false, true, true);
		chkFavorite.setContentDescription(getText(R.string.remove_from_favorites), getText(R.string.add_to_favorites));
		chkAlbumArt = (BgButton)findViewById(R.id.chkAlbumArt);
		chkAlbumArt.setOnClickListener(this);
		chkAlbumArt.formatAsCheckBox(UI.ICON_ALBUMART, UI.ICON_ALBUMART_OFF, UI.albumArt, true, true);
		btnHome = (BgButton)findViewById(R.id.btnHome);
		btnHome.setOnClickListener(this);
		btnHome.setIcon(UI.ICON_HOME);
		panelSecondary = (RelativeLayout)findViewById(R.id.panelSecondary);
		RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, UI.thickDividerSize + UI.defaultControlSize + (UI.extraSpacing ? (UI.controlMargin << 1) : 0));
		rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		panelSecondary.setLayoutParams(rp);
		sep = (TextView)findViewById(R.id.sep);
		rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
		rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
		rp.addRule(RelativeLayout.LEFT_OF, R.id.chkAll);
		rp.leftMargin = UI.controlMargin;
		rp.rightMargin = UI.controlMargin;
		sep.setLayoutParams(rp);
		sep.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
		chkAll = (BgButton)findViewById(R.id.chkAll);
		chkAll.setOnClickListener(this);
		chkAll.formatAsPlainCheckBox(false, true, true);
		chkAll.setContentDescription(getText(R.string.unselect_everything), getText(R.string.select_everything));
		btnGoBackToPlayer = (BgButton)findViewById(R.id.btnGoBackToPlayer);
		btnGoBackToPlayer.setTextColor(UI.colorState_text_reactive);
		btnGoBackToPlayer.setOnClickListener(this);
		btnGoBackToPlayer.setCompoundDrawables(new TextIconDrawable(UI.ICON_LIST, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		btnGoBackToPlayer.setDefaultHeight();
		btnAdd = (BgButton)findViewById(R.id.btnAdd);
		btnAdd.setTextColor(UI.colorState_text_reactive);
		btnAdd.setOnClickListener(this);
		btnAdd.setIcon(UI.ICON_ADD);
		sep2 = (TextView)findViewById(R.id.sep2);
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
		if (UI.isLargeScreen)
			lblPath.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		if (UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED ||
			UI.browserScrollBarType == BgListView.SCROLLBAR_LARGE) {
			UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		} else {
			if (UI.extraSpacing) {
				final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, UI.defaultControlSize);
				lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
				lp.rightMargin = UI.controlMargin;
				btnURL.setLayoutParams(lp);
			}
			UI.prepareControlContainerWithoutRightPadding(findViewById(R.id.panelControls), false, true);
		}
		UI.prepareControlContainer(panelSecondary, true, false);
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
		//this is the opposite as in updateButtons(), to force updateOverallLayout()
		//to be called at least once
		if (!isAtHome == (chkAll.getVisibility() == View.VISIBLE)) {
			UI.animationReset();
			updateOverallLayout();
			UI.animationCommit(true, null);
		}
		isCreatingLayout = true;
		navigateTo(Player.path, null, true);
		isCreatingLayout = false;
	}

	@Override
	protected void onPostCreateLayout(boolean firstCreation) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (!getHostActivity().isReadStoragePermissionGranted()) {
				pendingTo = ((Player.path == null) ? "" : Player.path);
				getHostActivity().requestReadStoragePermission();
				return;
			}
		}
		isCreatingLayout = true;
		navigateTo(Player.path, null, !firstCreation);
		isCreatingLayout = false;
	}

	@Override
	protected void onPause() {
		fileList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		UI.browserActivity = this;
		fileList.setObserver(list);
		if (loading != fileList.isLoading())
			loadingProcessChanged(fileList.isLoading());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (list != null && UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		pendingTo = null;
		if (animator != null) {
			animator.release();
			animator = null;
		}
		lastClickedFavorite = null;
		lblPath = null;
		lblLoading = null;
		list = null;
		panelSecondary = null;
		btnGoBack = null;
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
		msgEmptyList = null;
		msgLoading = null;
	}
	
	@Override
	protected void onDestroy() {
		FileView.updateExtraMargins(false);
		UI.browserActivity = null;
		fileList.cancel();
		fileList = null;
		if (albumArtFetcher != null) {
			albumArtFetcher.stopAndCleanup();
			albumArtFetcher = null;
		}
	}

	@Override
	public void onUpdate(FastAnimator animator, float value) {
	}

	@Override
	public void onEnd(FastAnimator animator) {
		if (lblLoading != null)
			lblLoading.setVisibility(View.GONE);
	}
}
