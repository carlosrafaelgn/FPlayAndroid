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

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.activity.ActivityVisualizer;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.BgSeekBar;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;

//
//How to create a ListView using ArrayAdapter in Android
//http://anujarosha.wordpress.com/2011/11/17/how-to-create-a-listview-using-arrayadapter-in-android/
//
//Customizing Android ListView Items with Custom ArrayAdapter
//http://www.ezzylearning.com/tutorial.aspx?tid=1763429
//
//Communicating with the UI Thread
//https://developer.android.com/training/multiple-threads/communicate-ui.html
//
//Difference of px, dp, dip and sp in Android?
//http://stackoverflow.com/questions/2025282/difference-of-px-dp-dip-and-sp-in-android
//
//Supporting Keyboard Navigation
//http://developer.android.com/training/keyboard-input/navigation.html
//
//Why are nested weights bad for performance? Alternatives?
//http://stackoverflow.com/questions/9430764/why-are-nested-weights-bad-for-performance-alternatives
//
//Maintain/Save/Restore scroll position when returning to a ListView
//http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview
//
public final class ActivityMain extends ClientActivity implements Runnable, Player.PlayerObserver, View.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, BgSeekBar.OnBgSeekBarChangeListener, BgListView.OnAttachedObserver, BgListView.OnBgListViewKeyDownObserver, ActivityFileSelection.OnFileSelectionListener {
	private static final int MAX_SEEK = 10000, MNU_ADDSONGS = 100, MNU_CLEARLIST = 101, MNU_LOADLIST = 102, MNU_SAVELIST = 103, MNU_TOGGLECONTROLMODE = 104, MNU_EFFECTS = 105, MNU_VISUALIZER = 106, MNU_SETTINGS = 107, MNU_EXIT = 108;
	private TextView lblTitle, lblMsgSelMove, lblTime;
	private BgSeekBar barSeek, barVolume;
	private ViewGroup panelControls, panelSecondary, panelSelection;
	private BgButton btnPrev, btnPlay, btnNext, btnMenu, btnMoveSel, btnRemoveSel, btnCancelSel, btnDecreaseVolume, btnIncreaseVolume, btnVolume;
	private BgListView list;
	private Timer tmrSong;
	private ColorDrawable windowDrawable;
	private CharSequence lastTitleText;
	private int firstSel, lastSel;
	private int lastTime;
	private boolean showSecondary, alwaysShowSecondary, playingBeforeSeek, selectCurrentWhenAttached, blackBackground;
	private StringBuilder timeBuilder, volumeBuilder;
	
	private void saveListViewPosition() {
		if (list != null && list.getAdapter() != null) {
			final int i = list.getFirstVisiblePosition();
			if (i < 0)
				return;
			final View v = list.getChildAt(0);
			Player.listFirst = i;
			Player.listTop = ((v == null) ? 0 : v.getTop());
		}
	}
	
	private void restoreListViewPosition(boolean selectCurrent) {
		if (list != null) {
			if (Player.positionToSelect >= 0) {
				list.centerItem(Player.positionToSelect, false);
				Player.positionToSelect = -1;
				return;
			}
			final int c = Player.songs.getCurrentPosition();
			if (Player.listFirst >= 0 && (!selectCurrent || Player.songs.selecting || Player.songs.moving)) {
				list.setSelectionFromTop(Player.listFirst, Player.listTop);
			} else {
				if (selectCurrent && Player.lastCurrent != c && c >= 0) {
					if (Player.songs.getSelection() != c)
						Player.songs.setSelection(c, true);
					list.centerItem(c, false);
				} else {
					if (Player.listFirst >= 0)
						list.setSelectionFromTop(Player.listFirst, Player.listTop);
					else
						list.centerItem(Player.songs.getSelection(), false);
				}
			}
			Player.lastCurrent = -1;
		}
	}
	
	private String volumeDBToString() {
		int volumeDB = Player.getVolumeDB();
		if (Player.displayVolumeInDB) {
			if (volumeDB <= Player.MIN_VOLUME_DB)
				return "-\u221E dB";
			if (volumeDB >= 0)
				return "-0.00 dB";
			volumeDB = -volumeDB;
			volumeBuilder.delete(0, volumeBuilder.length());
			volumeBuilder.append('-');
			volumeBuilder.append(volumeDB / 100);
			volumeBuilder.append('.');
			volumeDB %= 100;
			if (volumeDB < 10)
				volumeBuilder.append('0');
			volumeBuilder.append(volumeDB);
			volumeBuilder.append(" dB");
			return volumeBuilder.toString();
		}
		if (volumeDB <= Player.MIN_VOLUME_DB)
			return "0%";
		if (volumeDB >= 0)
			return "100%";
		volumeBuilder.delete(0, volumeBuilder.length());
		volumeBuilder.append(((Player.MIN_VOLUME_DB - volumeDB) * 100) / Player.MIN_VOLUME_DB);
		volumeBuilder.append('%');
		return volumeBuilder.toString();
	}
	
	private void setVolumeIcon() {
		if (btnVolume != null) {
			final int max = -Player.MIN_VOLUME_DB;
			final int v = max + Player.getVolumeDB();
			if (v == max)
				btnVolume.setText(UI.ICON_VOLUME4);
			else if (v == 0)
				btnVolume.setText(UI.ICON_VOLUME0);
			else if (v > ((max << 1) / 3))
				btnVolume.setText(UI.ICON_VOLUME3);
			else if (v > (max / 3))
				btnVolume.setText(UI.ICON_VOLUME2);
			else
				btnVolume.setText(UI.ICON_VOLUME1);
		}
	}
	
	private void startSelecting() {
		if (firstSel >= 0) {
			if (!UI.isLandscape && alwaysShowSecondary) {
				final int h = panelControls.getHeight() + panelSecondary.getHeight();
				final int ph = UI.defaultControlSize + UI._8dp;
				final ViewGroup.LayoutParams p = panelSelection.getLayoutParams();
				if (h > ph && p != null && p.height != h) {
					p.height = h;
					panelSelection.setLayoutParams(p);
				}
			}
			btnMoveSel.setVisibility(View.VISIBLE);
			btnRemoveSel.setVisibility(View.VISIBLE);
			btnCancelSel.setText(R.string.cancel);
			panelControls.setVisibility(View.GONE);
			panelSecondary.setVisibility(View.GONE);
			panelSelection.setVisibility(View.VISIBLE);
			lblMsgSelMove.setText(R.string.msg_sel);
			lblTitle.setVisibility(View.GONE);
			lblMsgSelMove.setVisibility(View.VISIBLE);
			lblMsgSelMove.setSelected(true);
			if (UI.isLandscape) {
				btnCancelSel.setNextFocusUpId(R.id.btnRemoveSel);
				UI.setNextFocusForwardId(list, R.id.btnMoveSel);
			} else {
				btnCancelSel.setNextFocusRightId(R.id.btnMoveSel);
				UI.setNextFocusForwardId(btnCancelSel, R.id.btnMoveSel);
				UI.setNextFocusForwardId(list, R.id.btnCancelSel);
			}
			list.requestFocus();
			Player.songs.selecting = true;
			Player.songs.moving = false;
		}
	}
	
	private void startMovingSelection() {
		if (Player.songs.getFirstSelectedPosition() >= 0) {
			btnMoveSel.setVisibility(View.INVISIBLE);
			btnRemoveSel.setVisibility(View.INVISIBLE);
			btnCancelSel.setText(R.string.done);
			lblMsgSelMove.setText(R.string.msg_move);
			lblMsgSelMove.setSelected(true);
			if (UI.isLandscape) {
				btnCancelSel.setNextFocusUpId(R.id.list);
			} else {
				btnCancelSel.setNextFocusRightId(R.id.list);
				UI.setNextFocusForwardId(btnCancelSel, R.id.list);
			}
			UI.setNextFocusForwardId(list, R.id.btnCancelSel);
			Player.songs.selecting = false;
			Player.songs.moving = true;
		}
	}
	
	private void removeSelection() {
		if (Player.songs.getFirstSelectedPosition() >= 0) {
			Player.songs.removeSelection();
			cancelSelection(true);
		}
	}
	
	private void cancelSelection(boolean removed) {
		if (!removed && firstSel >= 0 && lastSel >= 0) {
			//final int p = ((firstSel < lastSel) ? Player.songs.getFirstSelectedPosition() : Player.songs.getLastSelectedPosition());
			//Player.songs.setSelection(p, p, true, p >= list.getFirstVisiblePosition() && p <= list.getLastVisiblePosition());
			int p = Player.songs.getSelection();
			if (p < 0)
				p = ((firstSel < lastSel) ? Player.songs.getFirstSelectedPosition() : Player.songs.getLastSelectedPosition());
			Player.songs.setSelection(p, true);
		}
		Player.songs.selecting = false;
		Player.songs.moving = false;
		firstSel = -1;
		lastSel = -1;
		lblMsgSelMove.setVisibility(View.GONE);
		lblTitle.setVisibility(View.VISIBLE);
		lblTitle.setSelected(true);
		panelSelection.setVisibility(View.GONE);
		if (UI.isLandscape || alwaysShowSecondary) {
			UI.setNextFocusForwardId(list, R.id.btnPrev);
			panelControls.setVisibility(View.VISIBLE);
			panelSecondary.setVisibility(View.VISIBLE);
		} else {
			UI.setNextFocusForwardId(list, R.id.lblTitle);
			(showSecondary ? panelSecondary : panelControls).setVisibility(View.VISIBLE);
		}
		list.requestFocus();
	}
	
	private void bringCurrentIntoView() {
		if (!Player.songs.moving && !Player.songs.selecting) {
			final int p = Player.songs.getCurrentPosition();
			if (p <= list.getFirstVisiblePosition() || p >= list.getLastVisiblePosition())
				list.centerItem(p, true);
		}
	}
	
	@Override
	public void onPlayerChanged(boolean onlyPauseChanged, Throwable ex) {
		final Song s = Player.getCurrentSong();
		if (s == null || ex != null) {
			if (btnPlay != null)
				btnPlay.setText(UI.ICON_PLAY);
			if (lblTitle != null) {
				//there is no need to use equals(), as it is just a way to prevent
				//the same song's title from being set to the control
				final CharSequence txt = ((s == null) ? getText(R.string.nothing_playing) : s.title);
				if (lastTitleText != txt) {
					lastTitleText = txt;
					lblTitle.setText(txt);
					lblTitle.setSelected(true);
				}
			}
			if (barSeek != null)
				barSeek.setEnabled(false);
			tmrSong.stop();
		} else {
			if (btnPlay != null)
				btnPlay.setText(Player.isPlaying() ? UI.ICON_PAUSE : UI.ICON_PLAY);
			//same as above...
			if (lblTitle != null && lastTitleText != s.title) {
				lastTitleText = s.title;
				lblTitle.setText(s.title);
				lblTitle.setSelected(true);
			}
			if (barSeek != null)
				barSeek.setEnabled(true);
			if (Player.isPlaying() && !Player.isControlMode()) {
				if (!tmrSong.isAlive())
					tmrSong.start(250, false);
			} else {
				tmrSong.stop();
			}
		}
		lastTime = -2;
		run();
	}
	
	@Override
	public void onPlayerControlModeChanged(boolean controlMode) {
		if (Player.songs.selecting || Player.songs.moving)
			cancelSelection(false);
		if (controlMode)
			Player.lastCurrent = Player.songs.getCurrentPosition();
		onCleanupLayout();
		onCreateLayout(false);
		resume(true);
		System.gc();
	}
	
	@Override
	public View getNullContextMenuView() {
		return ((!Player.songs.selecting && !Player.songs.moving) ? btnMenu : null);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		UI.prepare(menu);
		menu.add(0, MNU_ADDSONGS, 0, R.string.add_songs)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_ADD));
		UI.separator(menu, 0, 1);
		Menu s = menu.addSubMenu(1, 0, 0, R.string.list)
				.setIcon(new TextIconDrawable(UI.ICON_LIST));
		UI.prepare(s);
		s.add(0, MNU_CLEARLIST, 0, R.string.clear_list)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_REMOVE));
		s.add(0, MNU_LOADLIST, 1, R.string.load_list)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_LOAD));
		s.add(0, MNU_SAVELIST, 2, R.string.save_list)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_SAVE));
		UI.separator(menu, 1, 1);
		menu.add(2, MNU_TOGGLECONTROLMODE, 0, Player.isControlMode() ? R.string.leave_control_mode : R.string.control_mode)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_NEXT));
		if (UI.isLandscape && !UI.isLargeScreen) {
			s = menu.addSubMenu(2, 0, 1, R.string.more)
					.setIcon(new TextIconDrawable(UI.ICON_MENU));
			UI.prepare(s);
		} else {
			UI.separator(menu, 2, 1);
			s = menu;
		}
		s.add(2, MNU_EFFECTS, 2, R.string.audio_effects)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_EQUALIZER));
		s.add(2, MNU_VISUALIZER, 3, R.string.visualizer)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		s.add(2, MNU_SETTINGS, 4, R.string.settings)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_SETTINGS));
		UI.separator(menu, 2, 4);
		menu.add(3, MNU_EXIT, 0, R.string.exit)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_EXIT));
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		case MNU_ADDSONGS:
			Player.alreadySelected = false;
			startActivity(new ActivityBrowser());
			break;
		case MNU_CLEARLIST:
			Player.songs.clear();
			break;
		case MNU_LOADLIST:
			Player.alreadySelected = false;
			startActivity(new ActivityFileSelection(MNU_LOADLIST, false, true, getText(R.string.item_list).toString(), "#lst", this));
			break;
		case MNU_SAVELIST:
			startActivity(new ActivityFileSelection(MNU_SAVELIST, true, false, getText(R.string.item_list).toString(), "#lst", this));
			break;
		case MNU_TOGGLECONTROLMODE:
			Player.setControlMode(!Player.isControlMode());
			break;
		case MNU_EFFECTS:
			startActivity(new ActivityEffects());
			break;
		case MNU_VISUALIZER:
			getHostActivity().startActivity(new Intent(getApplication(), ActivityVisualizer.class));
			break;
		case MNU_SETTINGS:
			startActivity(new ActivitySettings());
			break;
		case MNU_EXIT:
			Player.stopService();
			setExitOnDestroy(true);
			finish();
			break;
		}
		return true;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnPrev) {
			Player.previous();
			if (!Player.isControlMode())
				bringCurrentIntoView();
		} else if (view == btnPlay) {
			Player.playPause();
		} else if (view == btnNext) {
			Player.next();
			if (!Player.isControlMode())
				bringCurrentIntoView();
		} else if (view == btnMenu) {
			CustomContextMenu.openContextMenu(btnMenu, this);
		} else if (view == btnMoveSel) {
			startMovingSelection();
		} else if (view == btnRemoveSel) {
			removeSelection();
		} else if (view == btnCancelSel) {
			cancelSelection(false);
		} else if (view == btnDecreaseVolume) {
			Player.setVolumeDB(Player.getVolumeDB() - 200, true);
			setVolumeIcon();
		} else if (view == btnIncreaseVolume) {
			Player.setVolumeDB(Player.getVolumeDB() + 200, true);
			setVolumeIcon();
		} else if (view == lblTitle) {
			if (Player.isControlMode()) {
				Player.playPause();
			} else {
				if (showSecondary) {
					showSecondary = false;
					lblTitle.setNextFocusRightId(R.id.btnPrev);
					lblTitle.setNextFocusDownId(R.id.btnPrev);
					UI.setNextFocusForwardId(lblTitle, R.id.btnPrev);
					panelSecondary.setVisibility(View.GONE);
					panelControls.setVisibility(View.VISIBLE);
				} else {
					showSecondary = true;
					lblTitle.setNextFocusRightId(R.id.barVolume);
					lblTitle.setNextFocusDownId(R.id.barVolume);
					UI.setNextFocusForwardId(lblTitle, R.id.barVolume);
					panelControls.setVisibility(View.GONE);
					panelSecondary.setVisibility(View.VISIBLE);
				}
			}
		}
	}
	
	@Override
	public void onItemClick(AdapterView<?> parentView, View childView, int position, long id) {
		if (Player.songs.selecting) {
			lastSel = position;
			Player.songs.setSelection(firstSel, position, position, true, true);
		} else if (Player.songs.moving) {
			Player.songs.moveSelection(position);
		} else {
			if (Player.doubleClickMode) {
				if (Player.songs.getFirstSelectedPosition() == position) {
					if (Player.songs.getItemT(position) == Player.getCurrentSong() && !Player.isPlaying())
						Player.playPause();
					else
						Player.play(position);
				} else {
					Player.songs.setSelection(position, position, true, true);
				}
			} else {
				Player.songs.setSelection(position, position, true, true);
				if (Player.songs.getItemT(position) == Player.getCurrentSong() && !Player.isPlaying())
					Player.playPause();
				else
					Player.play(position);
			}
		}
	}
	
	@Override
	public boolean onItemLongClick(AdapterView<?> parentView, View childView, int position, long id) {
		if (!Player.songs.selecting && !Player.songs.moving) {
			//select the clicked item before opening the menu
			if (!Player.songs.isSelected(position))
				Player.songs.setSelection(position, position, position, true, true);
			firstSel = Player.songs.getFirstSelectedPosition();
			lastSel = firstSel;
			startSelecting();
		}
		return true;
	}
	
	@Override
	public boolean onBackPressed() {
		if (Player.isControlMode()) {
			Player.setControlMode(false);
			return true;
		} else if (Player.songs.selecting || Player.songs.moving) {
			cancelSelection(false);
			return true;
		}
		return false;
	}
	
	@Override
	protected void onCreate() {
		Player.startService(getApplication());
		addWindowFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		if (Player.keepScreenOn)
			addWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			clearWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (Player.forcedOrientation == 0)
			getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		else if (Player.forcedOrientation < 0)
			getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		else
			getHostActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		Player.songs.selecting = false;
		Player.songs.moving = false;
		firstSel = -1;
		lastSel = -1;
		lastTime = -2;
		timeBuilder = new StringBuilder(16);
		volumeBuilder = new StringBuilder(16);
		tmrSong = new Timer(this);
		tmrSong.setCompensatingForDelays(true);
		tmrSong.setHandledOnMain(true);
	}
	
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		showSecondary = false;
		setContentView(Player.isControlMode() ? (UI.isLandscape ? R.layout.activity_main_control_l : R.layout.activity_main_control) : (UI.isLandscape ? R.layout.activity_main_l : R.layout.activity_main));
		lblTitle = (TextView)findViewById(R.id.lblTitle);
		btnPrev = (BgButton)findViewById(R.id.btnPrev);
		btnPrev.setOnClickListener(this);
		btnNext = (BgButton)findViewById(R.id.btnNext);
		btnNext.setOnClickListener(this);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		CustomContextMenu.registerForContextMenu(btnMenu, this);
		if (Player.isControlMode()) {
			btnPrev.setIconNoChanges(UI.ICON_PREV);
			btnNext.setIconNoChanges(UI.ICON_NEXT);
			btnMenu.setIconNoChanges(UI.ICON_MENU);
			btnPrev.setIconStretchable(true);
			btnNext.setIconStretchable(true);
			btnMenu.setIconStretchable(true);
			
			btnDecreaseVolume = (BgButton)findViewById(R.id.btnDecreaseVolume);
			btnDecreaseVolume.setOnClickListener(this);
			btnDecreaseVolume.setIconNoChanges(UI.ICON_DECREASE_VOLUME);
			btnDecreaseVolume.setIconStretchable(true);
			btnIncreaseVolume = (BgButton)findViewById(R.id.btnIncreaseVolume);
			btnIncreaseVolume.setOnClickListener(this);
			btnIncreaseVolume.setIconNoChanges(UI.ICON_INCREASE_VOLUME);
			btnIncreaseVolume.setIconStretchable(true);
			btnVolume = (BgButton)findViewById(R.id.btnVolume);
			btnVolume.setIconNoChanges(UI.ICON_VOLUME0);
			btnVolume.setIconStretchable(true);
			btnVolume.setEnabled(false);
			
			Player.songs.selecting = false;
			Player.songs.moving = false;
			
			lblTitle.setOnClickListener(this);
			final int w = getDecorViewWidth(), h = getDecorViewHeight();
			final int min, max;
			if (w < h) {
				min = w;
				max = h;
			} else {
				min = h;
				max = w;
			}
			int panelH = (UI.isLandscape ? ((min * 25) / 100) : ((max * 14) / 100));
			if (!UI.isLandscape && panelH > (min >> 2))
				panelH = (min >> 2);
			((ViewGroup)findViewById(R.id.panelTop)).setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelH));
			
			RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(panelH, panelH);
			rp.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			btnDecreaseVolume.setLayoutParams(rp);
			
			rp = new RelativeLayout.LayoutParams(panelH, panelH);
			rp.addRule(RelativeLayout.RIGHT_OF, R.id.btnDecreaseVolume);
			btnVolume.setLayoutParams(rp);
			
			rp = new RelativeLayout.LayoutParams(panelH, panelH);
			rp.addRule(RelativeLayout.RIGHT_OF, R.id.btnVolume);
			btnIncreaseVolume.setLayoutParams(rp);
			
			rp = new RelativeLayout.LayoutParams(panelH, panelH);
			rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			btnMenu.setLayoutParams(rp);
			
			int lds = 0;
			if (UI.isLowDpiScreen) {
				lds = (UI.isLandscape ? UI.dpToPxI(12) : UI._8dp);
				btnDecreaseVolume.setPadding(lds, lds, lds, lds);
				btnIncreaseVolume.setPadding(lds, lds, lds, lds);
				btnVolume.setPadding(lds, lds, lds, lds);
				btnMenu.setPadding(lds, lds, lds, lds);
			} else {
				btnDecreaseVolume.setPadding(UI._16dp, UI._16dp, UI._16dp, UI._16dp);
				btnIncreaseVolume.setPadding(UI._16dp, UI._16dp, UI._16dp, UI._16dp);
				btnVolume.setPadding(UI._16dp, UI._16dp, UI._16dp, UI._16dp);
				btnMenu.setPadding(UI._16dp, UI._16dp, UI._16dp, UI._16dp);
			}
			
			if (UI.isLandscape) {
				lblTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, (min * 10) / 100);
				final int pa = (min * 7) / 100;
				btnPrev.setPadding(pa, pa, pa, pa);
				btnNext.setPadding(pa, pa, pa, pa);
			} else {
				LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (max * 40) / 100);
				p2.topMargin = UI._16dp;
				p2.bottomMargin = UI._16dp;
				lblTitle.setLayoutParams(p2);
				lblTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, (max * 6) / 100);
				final int ph = (min * 12) / 100, pv = (max * 12) / 100;
				btnPrev.setPadding(ph, pv, ph, pv);
				btnNext.setPadding(ph, pv, ph, pv);
			}
		} else {
			btnPrev.setIcon(UI.ICON_PREV);
			btnNext.setIcon(UI.ICON_NEXT);
			btnMenu.setIcon(UI.ICON_MENU);
			
			barVolume = (BgSeekBar)findViewById(R.id.barVolume);
			barVolume.setOnBgSeekBarChangeListener(this);
			barVolume.setMax(-Player.MIN_VOLUME_DB / 5);
			barVolume.setVertical(UI.isLandscape && !Player.isControlMode());
			barVolume.setKeyIncrement(20);
			barVolume.setEmptySpaceColor(UI.color_window);
			
	        if (!Player.marqueeTitle) {
	        	lblTitle.setEllipsize(TruncateAt.END);
	        	lblTitle.setHorizontallyScrolling(false);
	        }
	        
			lblMsgSelMove = (TextView)findViewById(R.id.lblMsgSelMove);
			barSeek = (BgSeekBar)findViewById(R.id.barSeek);
			barSeek.setOnBgSeekBarChangeListener(this);
			barSeek.setMax(MAX_SEEK);
			barSeek.setVertical(UI.isLandscape);
			barSeek.setFocusable(false);
			barSeek.setEmptySpaceColor(UI.color_window);
			btnPlay = (BgButton)findViewById(R.id.btnPlay);
			btnPlay.setOnClickListener(this);
			btnPlay.setIcon(UI.ICON_PLAY);
			list = (BgListView)findViewById(R.id.list);
			list.setOnItemClickListener(this);
			list.setOnItemLongClickListener(this);
			if (UI.isLandscape)
				list.setSideBorders();
			panelControls = (ViewGroup)findViewById(R.id.panelControls);
			panelSecondary = (ViewGroup)findViewById(R.id.panelSecondary);
			panelSelection = (ViewGroup)findViewById(R.id.panelSelection);
			btnMoveSel = (BgButton)findViewById(R.id.btnMoveSel);
			btnMoveSel.setOnClickListener(this);
			btnMoveSel.setIcon(UI.ICON_MOVE, !UI.isLandscape);
			btnRemoveSel = (BgButton)findViewById(R.id.btnRemoveSel);
			btnRemoveSel.setOnClickListener(this);
			btnRemoveSel.setIcon(UI.ICON_DELETE, !UI.isLandscape);
			btnCancelSel = (BgButton)findViewById(R.id.btnCancelSel);
			btnCancelSel.setOnClickListener(this);
			alwaysShowSecondary = true;
			lblTime = null;
			if (UI.isLowDpiScreen) {
				barVolume.setTextSizeIndex(1);
				barSeek.setTextSizeIndex(1);
			}
			if (UI.isLargeScreen || !UI.isLowDpiScreen) {
				if (UI.isLargeScreen) {
					if (lblTitle != null)
						lblTitle.setPadding(UI._8dp, UI._4dp, UI._4dp, UI._8dp);
					if (lblMsgSelMove != null)
						lblMsgSelMove.setPadding(UI._8dp, UI._4dp, UI._4dp, UI._8dp);
				}
				btnCancelSel.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
			}
			if (UI.isLowDpiScreen && !UI.isLandscape) {
				alwaysShowSecondary = false;
				lblTime = (TextView)findViewById(R.id.lblTime);
				lblTime.setVisibility(View.VISIBLE);
				lblTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
				panelControls.setPadding(0, 0, 0, 0);
				panelSecondary.setVisibility(View.GONE);
				panelSecondary.setPadding(0, 0, 0, 0);
				panelSelection.setPadding(0, 0, 0, 0);
				lblTitle.setClickable(true);
				lblTitle.setFocusable(true);
				lblTitle.setOnClickListener(this);
				lblTitle.setTextColor(new ColorStateList(new int[][] { new int[] { android.R.attr.state_pressed }, new int[] { android.R.attr.state_focused }, new int[] {} }, new int[] { UI.color_text_selected, UI.color_text_selected, UI.color_current }));
				lblTitle.setCompoundDrawables(new TextIconDrawable(UI.ICON_EQUALIZER, true, UI._18spBox), null, null, null);
				barVolume.setNextFocusUpId(R.id.lblTitle);
				barVolume.setNextFocusDownId(R.id.list);
				UI.setNextFocusForwardId(barVolume, R.id.list);
				btnPrev.setNextFocusLeftId(R.id.lblTitle);
				btnPrev.setNextFocusUpId(R.id.lblTitle);
				btnPrev.setNextFocusDownId(R.id.list);
				btnPlay.setNextFocusUpId(R.id.lblTitle);
				btnPlay.setNextFocusDownId(R.id.list);
				btnNext.setNextFocusUpId(R.id.lblTitle);
				btnNext.setNextFocusDownId(R.id.list);
				btnMenu.setNextFocusUpId(R.id.lblTitle);
				btnMenu.setNextFocusRightId(R.id.list);
				btnMenu.setNextFocusDownId(R.id.list);
				UI.setNextFocusForwardId(btnMenu, R.id.list);
				UI.setNextFocusForwardId(list, R.id.lblTitle);
				final RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
				p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
				btnCancelSel.setLayoutParams(p);
			} else {
				lblTitle.setTextColor(UI.color_current);
			}
			btnCancelSel.setDefaultHeight();
			final boolean m = Player.songs.moving;
			if (m || Player.songs.selecting)
				startSelecting();
			if (m)
				startMovingSelection();
		}
	}
	
	@Override
	public void onBgListViewAttached(BgListView list) {
		restoreListViewPosition(selectCurrentWhenAttached);
		selectCurrentWhenAttached = false;
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView bgListView, int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
			if (Player.songs.selecting) {
				(UI.isLandscape ? btnMoveSel : btnRemoveSel).requestFocus();
			} else if (Player.songs.moving) {
				btnCancelSel.requestFocus();
			} else if (UI.isLandscape || alwaysShowSecondary || showSecondary) {
				barVolume.requestFocus();
			} else {
				btnMenu.requestFocus();
			}
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			if (Player.songs.selecting) {
				(UI.isLandscape ? btnMoveSel : btnCancelSel).requestFocus();
			} else if (Player.songs.moving) {
				btnCancelSel.requestFocus();
			} else if (!UI.isLandscape && !alwaysShowSecondary) {
				lblTitle.requestFocus();
			} else {
				btnPrev.requestFocus();
			}
			return true;
		}
		final int s = Player.songs.getSelection();
		if (Player.songs.moving || Player.songs.selecting) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_FORWARD_DEL:
				if (s >= 0)
					removeSelection();
				return true;
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_PAGE_UP:
			case KeyEvent.KEYCODE_PAGE_DOWN:
			case KeyEvent.KEYCODE_MOVE_HOME:
			case KeyEvent.KEYCODE_MOVE_END:
				int n = list.getNewPosition(s, keyCode, false);
				if (n < 0)
					return true;
				final boolean center = (n <= (list.getFirstVisiblePosition() + 1) || n >= (list.getLastVisiblePosition() - 1));
				if (Player.songs.moving) {
					Player.songs.moveSelection(n);
				} else {
					lastSel = n;
					Player.songs.setSelection(firstSel, n, n, true, true);
				}
				if (center)
					list.centerItem(n, false);
				return true;
			}
		} else {
			switch (keyCode) {
			case KeyEvent.KEYCODE_FORWARD_DEL:
				if (s >= 0)
					Player.songs.removeSelection();
				return true;
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_SPACE:
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (s >= 0) {
					if (Player.songs.getItemT(s) == Player.getCurrentSong() && !Player.isPlaying())
						Player.playPause();
					else
						Player.play(s);
				}
				return true;
			}
		}
		return false;
	}
	
	private void prepareWindowBg(boolean pausing) {
		if (windowDrawable == null)
			windowDrawable = new ColorDrawable(UI.color_window);
		if (pausing || !Player.isControlMode()) {
			if (blackBackground) {
				blackBackground = false;
				windowDrawable.change(UI.color_window);
				getHostActivity().getWindow().setBackgroundDrawable(windowDrawable);
			}
		} else if (!blackBackground) {
			blackBackground = true;
			windowDrawable.change(UI.color_bg);
			getHostActivity().getWindow().setBackgroundDrawable(windowDrawable);
		}
	}
	
	private void resume(boolean selectCurrent) {
		Player.songs.setObserver(list);
		SongAddingMonitor.start(getHostActivity());
		if (barVolume != null) {
			barVolume.setValue((Player.getVolumeDB() - Player.MIN_VOLUME_DB) / 5);
			barVolume.setText(volumeDBToString());
		} else {
			setVolumeIcon();
		}
		if (list != null) {
			selectCurrentWhenAttached = selectCurrent;
			list.notifyMeWhenFirstAttached(this);
			list.setOnKeyDownObserver(this);
			list.requestFocus();
			MainHandler.post(new Runnable() {
				@Override
				public void run() {
					//run this again on next frame...
					if (list != null)
						list.requestFocus();
				}
			});
		} else if (Player.isControlMode()) {
			if (!btnMenu.isInTouchMode()) {
				btnMenu.requestFocus();
				MainHandler.post(new Runnable() {
					@Override
					public void run() {
						//run this again on next frame...
						if (btnMenu != null)
							btnMenu.requestFocus();
					}
				});
			}
		}
		prepareWindowBg(false);
		onPlayerChanged(false, null);
	}
	
	@Override
	protected void onResume() {
		Player.isMainActiveOnTop = true;
		Player.observer = this;
		Player.registerMediaButtonEventReceiver();
		resume(true);
	}
	
	@Override
	protected void onOrientationChanged() {
		onCleanupLayout();
		onCreateLayout(false);
		resume(false);
	}
	
	@Override
	protected void onPause() {
		Player.isMainActiveOnTop = false;
		saveListViewPosition();
		if (list != null)
			list.setOnKeyDownObserver(null);
		tmrSong.stop();
		SongAddingMonitor.stop();
		if (Player.songs.selecting || Player.songs.moving)
			cancelSelection(false);
		Player.songs.setObserver(null);
		Player.observer = null;
		lastTime = -2;
		if (!Player.isControlMode())
			Player.lastCurrent = Player.songs.getCurrentPosition();
		prepareWindowBg(true);
	}
	
	@Override
	protected void onCleanupLayout() {
		saveListViewPosition();
		lblTitle = null;
		lastTitleText = null;
		lblMsgSelMove = null;
		lblTime = null;
		barSeek = null;
		barVolume = null;
		btnPrev = null;
		btnPlay = null;
		btnNext = null;
		btnMenu = null;
		panelControls = null;
		panelSecondary = null;
		panelSelection = null;
		btnMoveSel = null;
		btnRemoveSel = null;
		btnCancelSel = null;
		btnDecreaseVolume = null;
		btnIncreaseVolume = null;
		btnVolume = null;
		list = null;
		if (tmrSong != null)
			tmrSong.stop();
		SongAddingMonitor.stop();
	}
	
	@Override
	protected void onDestroy() {
		tmrSong = null;
		timeBuilder = null;
		volumeBuilder = null;
		windowDrawable = null;
	}
	
	@Override
	public void run() {
		if (Player.isCurrentSongPreparing()) {
			if (!alwaysShowSecondary && lblTime != null)
				lblTime.setText(R.string.loading);
			if (barSeek != null && !barSeek.isTracking()) {
				barSeek.setText(R.string.loading);
				barSeek.setValue(0);
			}
			return;
		}
		final int m = Player.getCurrentPosition(),
				t = ((m < 0) ? -1 : (m / 1000));
		if (t == lastTime) return;
		lastTime = t;
		if (t < 0) {
			if (!alwaysShowSecondary && lblTime != null)
				lblTime.setText(R.string.no_time);
			if (barSeek != null && !barSeek.isTracking()) {
				barSeek.setText(R.string.no_time);
				barSeek.setValue(0);
			}
		} else {
			Song.formatTimeSec(t, timeBuilder);
			if (!alwaysShowSecondary && lblTime != null)
				lblTime.setText(timeBuilder);
			if (barSeek != null && !barSeek.isTracking()) {
				final Song s = Player.getCurrentSong();
				int v = 0;
				if (s != null && s.lengthMS > 0) {
					if (m >= 214740) //avoid overflow! ;)
						v = (int)(((long)m * (long)MAX_SEEK) / (long)s.lengthMS);
					else
						v = (m * MAX_SEEK) / s.lengthMS;
				}
				barSeek.setText(timeBuilder.toString());
				barSeek.setValue(v);
			}
		}
	}
	
	private int getMSFromBarValue(int value) {
		final Song s = Player.getCurrentSong();
		if (s == null || s.lengthMS <= 0 || value < 0)
			return -1;
		return (int)(((long)value * (long)s.lengthMS) / (long)MAX_SEEK);
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (fromUser) {
			if (seekBar == barVolume) {
				Player.setVolumeDB((value * 5) + Player.MIN_VOLUME_DB, true);
				seekBar.setText(volumeDBToString());
			} else if (seekBar == barSeek) {
				value = getMSFromBarValue(value);
				if (value < 0) {
					seekBar.setText(R.string.no_time);
					seekBar.setValue(0);
				} else {
					Song.formatTime(value, timeBuilder);
					seekBar.setText(timeBuilder.toString());
				}
			}
		}
	}
	
	@Override
	public boolean onStartTrackingTouch(BgSeekBar seekBar) {
		if (seekBar == barSeek) {
			if (Player.getCurrentSong() != null && Player.getCurrentSong().lengthMS > 0) {
				playingBeforeSeek = Player.isPlaying();
				if (playingBeforeSeek)
					Player.playPause();
				barVolume.setVisibility(View.GONE);
				return true;
			}
			return false;
		}
		return true;
	}
	
	@Override
	public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled) {
		if (seekBar == barSeek) {
			if (Player.getCurrentSong() != null) {
				final int ms = getMSFromBarValue(seekBar.getValue());
				if (cancelled || ms < 0) {
					if (playingBeforeSeek) {
						Player.playPause();
					} else {
						run();
					}
				} else {
					Player.seekTo(ms, playingBeforeSeek);
				}
			}
			barVolume.setVisibility(View.VISIBLE);
		}
	}
	
	@Override
	public void OnFileSelected(int id, String path, String name) {
		if (id == MNU_LOADLIST) {
			Player.songs.clear();
			Player.songs.startDeserializing(getApplication(), path, true, false, false);
			SongAddingMonitor.start(getHostActivity());
		} else {
			Player.songs.serialize(getApplication(), path);
		}
	}
	
	@Override
	public void OnAddClicked(int id, String path, String name) {
		if (id == MNU_LOADLIST) {
			Player.songs.startDeserializing(getApplication(), path, false, true, false);
			SongAddingMonitor.start(getHostActivity());
		}
	}
	
	@Override
	public void OnPlayClicked(int id, String path, String name) {
		if (id == MNU_LOADLIST) {
			Player.songs.startDeserializing(getApplication(), path, false, !Player.clearListWhenPlayingFolders, true);
			SongAddingMonitor.start(getHostActivity());
		}
	}
}
