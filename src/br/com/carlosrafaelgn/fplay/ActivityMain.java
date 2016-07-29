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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils.TruncateAt;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ActivityVisualizer;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.list.SongList;
import br.com.carlosrafaelgn.fplay.playback.ExternalFx;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BackgroundActivityMonitor;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgDialog;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.BgSeekBar;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.SongView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;
import br.com.carlosrafaelgn.fplay.visualizer.AlbumArtVisualizer;
import br.com.carlosrafaelgn.fplay.visualizer.OpenGLVisualizerJni;
import br.com.carlosrafaelgn.fplay.visualizer.SimpleVisualizerJni;
import br.com.carlosrafaelgn.fplay.visualizer.Visualizer;

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
public final class ActivityMain extends ClientActivity implements Timer.TimerHandler, Player.PlayerObserver, View.OnClickListener, BgSeekBar.OnBgSeekBarChangeListener, SongList.ItemClickListener, BgListView.OnAttachedObserver, BgListView.OnBgListViewKeyDownObserver, ActivityFileSelection.OnFileSelectionListener, BgButton.OnPressingChangeListener {
	private static final int MAX_SEEK = 10000, MNU_ADDSONGS = 100, MNU_CLEARLIST = 101, MNU_LOADLIST = 102, MNU_SAVELIST = 103, MNU_TOGGLECONTROLMODE = 104, MNU_RANDOMMODE = 105, MNU_EFFECTS = 106, MNU_VISUALIZER = 107, MNU_SETTINGS = 108, MNU_EXIT = 109, MNU_SORT_BY_TITLE = 110, MNU_SORT_BY_ARTIST = 111, MNU_SORT_BY_ALBUM = 112, MNU_VISUALIZER_SPECTRUM = 113, MNU_REPEAT = 114, MNU_REPEAT_ONE = 115, MNU_VISUALIZER_BLUETOOTH = 116, MNU_VISUALIZER_LIQUID = 117, MNU_VISUALIZER_SPIN = 118, MNU_VISUALIZER_PARTICLE = 119, MNU_VISUALIZER_IMMERSIVE_PARTICLE = 120, MNU_VISUALIZER_ALBUMART = 121, MNU_REPEAT_NONE = 122, MNU_VISUALIZER_IMMERSIVE_PARTICLE_VR = 123, MNU_VISUALIZER_SPECTRUM2 = 124;
	private View vwVolume;
	private TextView lblTitle, lblArtist, lblTrack, lblAlbum, lblLength, lblMsgSelMove;
	private TextIconDrawable lblTitleIcon;
	private BgSeekBar barSeek, barVolume;
	private ViewGroup panelControls, panelSecondary, panelSelection;
	private BgButton btnAdd, btnPrev, btnPlay, btnNext, btnMenu, btnMoreInfo, btnMoveSel, btnRemoveSel, btnCancelSel, btnDecreaseVolume, btnIncreaseVolume, btnVolume;
	private BgListView list;
	private Timer tmrSong, tmrUpdateVolumeDisplay, tmrVolume;
	private int firstSel, lastSel, lastTime, volumeButtonPressed, tmrVolumeInitialDelay, vwVolumeId, pendingListCommand;
	private boolean skipToDestruction, forceFadeOut, isCreatingLayout;//, ignoreAnnouncement;
	private StringBuilder timeBuilder, volumeBuilder;
	public static boolean localeHasBeenChanged;

	@Override
	public CharSequence getTitle() {
		return "FPlay" + UI.collon() + Player.getCurrentTitle(false);
	}

	@Override
	public int getSystemBgColor() {
		return (Player.controlMode ? UI.color_control_mode : UI.color_window);
	}

	private String volumeToString(int volume) {
		switch (Player.volumeControlType) {
		case Player.VOLUME_CONTROL_STREAM:
			//The parameter volume is only used to help properly synchronize when
			//Player.volumeControlType == Player.VOLUME_CONTROL_STREAM
			return Integer.toString(volume);
		case Player.VOLUME_CONTROL_DB:
			if (volume <= Player.VOLUME_MIN_DB)
				return "-\u221E dB";
			if (volume >= 0)
				return "-0 dB";
			volumeBuilder.delete(0, volumeBuilder.length());
			volumeBuilder.append('-');
			volumeBuilder.append(-volume / 100);
			volumeBuilder.append(" dB");
			return volumeBuilder.toString();
		default:
			volumeBuilder.delete(0, volumeBuilder.length());
			volumeBuilder.append(Player.getVolumeInPercentage());
			volumeBuilder.append('%');
			return volumeBuilder.toString();
		}
	}
	
	private void setVolumeIcon(int volume) {
		final String icon;
		final int max;
		switch (Player.volumeControlType) {
		case Player.VOLUME_CONTROL_STREAM:
			//The parameter volume is only used to help properly synchronize when
			//Player.volumeControlType == Player.VOLUME_CONTROL_STREAM
			max = Player.volumeStreamMax;
			break;
		case Player.VOLUME_CONTROL_DB:
		case Player.VOLUME_CONTROL_PERCENT:
			max = -Player.VOLUME_MIN_DB;
			volume += max;
			break;
		default:
			if (btnVolume != null)
				btnVolume.setText(UI.ICON_VOLUME4);
			return;
		}
		icon = ((volume == max) ? UI.ICON_VOLUME4 :
					((volume == 0) ? UI.ICON_VOLUME0 :
						((volume > ((max + 1) >> 1)) ? UI.ICON_VOLUME3 :
							((volume > (max >> 2)) ? UI.ICON_VOLUME2 :
								UI.ICON_VOLUME1))));
		if (btnVolume != null)
			btnVolume.setText(icon);
		else if (barVolume != null)
			barVolume.setIcon(icon);
	}
	
	private void updateVolumeDisplay(int volume) {
		final int value;
		if (Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) {
			if (volume == Integer.MIN_VALUE)
				volume = Player.getSystemStreamVolume();
			value = volume;
		} else {
			if (volume == Integer.MIN_VALUE)
				volume = Player.localVolumeDB;
			value = ((Player.volumeControlType == Player.VOLUME_CONTROL_DB) ?
				((volume - Player.VOLUME_MIN_DB) / 100) :
					((volume - Player.VOLUME_MIN_DB) / (-Player.VOLUME_MIN_DB / 100)));
		}
		setVolumeIcon(volume);
		if (barVolume != null) {
			barVolume.setValue(value);
			barVolume.setText(volumeToString(volume));
		}
	}

	private void showMoreInfo() {
		final int p;
		if ((p = Player.songs.getSelection()) < 0 || p >= Player.songs.getCount())
			return;
		final Song song = Player.songs.getItemT(p);
		if (song == null)
			return;
		final StringBuilder stringBuilder = new StringBuilder(1024);

		stringBuilder.append(getText(R.string.path));
		stringBuilder.append('\n');
		stringBuilder.append(song.getHumanReadablePath());

		stringBuilder.append("\n\n");
		stringBuilder.append(getText(R.string.title));
		stringBuilder.append('\n');
		stringBuilder.append(song.title);

		if (song.artist.length() > 0 && !song.artist.equals("-")) {
			stringBuilder.append("\n\n");
			stringBuilder.append(getText(R.string.artist));
			stringBuilder.append('\n');
			stringBuilder.append(song.artist);
		}

		if (song.album.length() > 0 && !song.album.equals("-")) {
			stringBuilder.append("\n\n");
			stringBuilder.append(song.isHttp ? getText(R.string.url) : getText(R.string.album));
			stringBuilder.append('\n');
			stringBuilder.append(song.album);
		}

		if (song.track > 0) {
			stringBuilder.append("\n\n");
			stringBuilder.append(getText(R.string.track));
			stringBuilder.append('\n');
			stringBuilder.append(song.track);
		}

		if (song.year > 0) {
			stringBuilder.append("\n\n");
			stringBuilder.append(getText(R.string.year));
			stringBuilder.append('\n');
			stringBuilder.append(song.year);
		}

		if (song.lengthMS > 0) {
			stringBuilder.append("\n\n");
			stringBuilder.append(getText(R.string.duration));
			stringBuilder.append('\n');
			stringBuilder.append(song.length);
		}

		if (song == Player.localSong) {
			//this information is only available for the song currently being played
			final int sampleRate = Player.getSrcSampleRate();
			final int channelCount = Player.getChannelCount();
			if (sampleRate > 0 && channelCount > 0) {
				stringBuilder.append("\n\n");
				stringBuilder.append(sampleRate);
				stringBuilder.append(" Hz / ");
				stringBuilder.append((channelCount == 1) ? "Mono" : "Stereo");
			}
		}

		final LinearLayout l = (LinearLayout)UI.createDialogView(getHostActivity(), null);
		l.addView(UI.createDialogEditText(getHostActivity(), 0, stringBuilder.toString(), null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
		final BgDialog dialog = new BgDialog(getHostActivity(), l, null);
		dialog.setTitle(R.string.information);
		dialog.setPositiveButton(R.string.ok);
		dialog.show();
	}

	@SuppressWarnings("deprecation")
	private void startSelecting() {
		if (firstSel >= 0) {
			if (UI.isLargeScreen) {
				final ViewGroup.LayoutParams p = lblMsgSelMove.getLayoutParams();
				if (p.height != UI.defaultControlSize) {
					p.height = UI.defaultControlSize;
					lblMsgSelMove.setLayoutParams(p);
				}
			} else if (!UI.isLandscape) {
				final int h = (UI.defaultControlSize << 1) + panelControls.getPaddingBottom() + panelSecondary.getPaddingBottom();
				final ViewGroup.LayoutParams p = panelSelection.getLayoutParams();
				if (p != null && p.height != h) {
					p.height = h;
					panelSelection.setLayoutParams(p);
				}
			}
			UI.animationReset();
			UI.animationAddViewToShow(btnMoreInfo);
			UI.animationAddViewToShow(btnMoveSel);
			UI.animationAddViewToShow(btnRemoveSel);
			btnCancelSel.setText(R.string.cancel);
			UI.animationAddViewToHide(panelControls);
			UI.animationAddViewToHide(panelSecondary);
			UI.animationAddViewToShow(panelSelection);
			lblMsgSelMove.setText(R.string.msg_sel);
			if (!UI.isLargeScreen)
				UI.animationAddViewToHide(lblTitle);
			UI.animationAddViewToShow(lblMsgSelMove);
			lblMsgSelMove.setSelected(true);
			if (UI.isLargeScreen) {
				btnCancelSel.setNextFocusLeftId(R.id.btnRemoveSel);
				UI.setNextFocusForwardId(list, R.id.btnMoreInfo);
			} else if (UI.isLandscape) {
				btnCancelSel.setNextFocusUpId(R.id.btnRemoveSel);
				UI.setNextFocusForwardId(list, R.id.btnMoreInfo);
			} else {
				btnCancelSel.setNextFocusRightId(R.id.btnMoreInfo);
				UI.setNextFocusForwardId(btnCancelSel, R.id.btnMoreInfo);
				UI.setNextFocusForwardId(list, R.id.btnCancelSel);
			}
			Player.songs.selecting = true;
			Player.songs.moving = false;
			list.skipUpDownTranslation = true;
			UI.animationCommit(isCreatingLayout, null);
		}
	}
	
	private void startMovingSelection() {
		if (Player.songs.getFirstSelectedPosition() >= 0) {
			UI.animationReset();
			UI.animationAddViewToHide(btnMoreInfo);
			UI.animationAddViewToHide(btnMoveSel);
			UI.animationAddViewToHide(btnRemoveSel);
			if (UI.animationEnabled) {
				UI.animationAddViewToHide(btnCancelSel);
				UI.animationAddViewToHide(lblMsgSelMove);
				btnCancelSel.setTag(getText(R.string.done));
				lblMsgSelMove.setTag(getText(R.string.msg_move));
				UI.animationAddViewToShow(btnCancelSel);
				UI.animationAddViewToShow(lblMsgSelMove);
			} else {
				btnCancelSel.setText(R.string.done);
				lblMsgSelMove.setText(R.string.msg_move);
			}
			UI.animationCommit(isCreatingLayout, null);
			lblMsgSelMove.setSelected(true);
			if (UI.isLargeScreen) {
				btnCancelSel.setNextFocusLeftId(R.id.list);
			} else if (UI.isLandscape) {
				btnCancelSel.setNextFocusUpId(R.id.list);
			} else {
				btnCancelSel.setNextFocusRightId(R.id.list);
				UI.setNextFocusForwardId(btnCancelSel, R.id.list);
			}
			UI.setNextFocusForwardId(list, R.id.btnCancelSel);
			Player.songs.selecting = false;
			Player.songs.moving = true;
			list.skipUpDownTranslation = true;
		}
	}
	
	private void removeSelection() {
		if (Player.songs.getFirstSelectedPosition() >= 0) {
			Player.songs.removeSelection();
			cancelSelection(true);
		} else {
			list.skipUpDownTranslation = false;
		}
	}
	
	private void cancelSelection(boolean removed) {
		if (list.isInTouchMode()) {
			final int p = Player.songs.getCurrentPosition();
			if (p >= 0 && p < Player.songs.getCount())
				Player.songs.setSelection(p, true);
			else
				Player.songs.setSelection(-1, true);
		} else if (!removed && firstSel >= 0 && lastSel >= 0) {
			int p = Player.songs.getSelection();
			if (p < 0)
				p = ((firstSel < lastSel) ? Player.songs.getFirstSelectedPosition() : Player.songs.getLastSelectedPosition());
			Player.songs.setSelection(p, false);
		}
		Player.songs.selecting = false;
		Player.songs.moving = false;
		list.skipUpDownTranslation = false;
		firstSel = -1;
		lastSel = -1;
		UI.animationReset();
		UI.animationAddViewToHide(lblMsgSelMove);
		if (!UI.isLargeScreen)
			UI.animationAddViewToShow(lblTitle);
		lblTitle.setSelected(true);
		UI.animationAddViewToHide(panelSelection);
		UI.setNextFocusForwardId(list, UI.isLargeScreen ? vwVolumeId : R.id.btnAdd);
		UI.animationAddViewToShow(panelControls);
		UI.animationAddViewToShow(panelSecondary);
		UI.animationCommit(isCreatingLayout, null);
	}
	
	private void bringCurrentIntoView() {
		if (!Player.songs.moving && !Player.songs.selecting) {
			final int p = Player.songs.getCurrentPosition();
			if (p <= list.getFirstVisiblePosition() || p >= list.getLastVisiblePosition()) {
				//this smooth scroll has some serious issues in many devices,
				//specially when scrolling large distances!
				//if (UI.animationEnabled)
				//	list.centerItemSmoothly(p);
				//else
					list.centerItem(p);
			}
		}
	}
	
	private void addSongs(View sourceView) {
		if (Player.state == Player.STATE_ALIVE) {
			Player.alreadySelected = false;
			startActivity(new ActivityBrowser2(), 0, sourceView, sourceView != null);
		}
	}
	
	private boolean decreaseVolume() {
		final int volume = Player.decreaseVolume();
		if (volume != Integer.MIN_VALUE) {
			setVolumeIcon(volume);
			return true;
		}
		return false;
	}

	private boolean increaseVolume() {
		final int volume = Player.increaseVolume();
		if (volume != Integer.MIN_VALUE) {
			setVolumeIcon(volume);
			return true;
		}
		return false;
	}

	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, boolean preparingHasChanged, Throwable ex) {
		final String icon = (Player.localPlaying ? UI.ICON_PAUSE : UI.ICON_PLAY);
		if (btnPlay != null) {
			btnPlay.setText(icon);
			btnPlay.setContentDescription(getText(Player.localPlaying ? R.string.pause : R.string.play));
		}
		if (lblTitleIcon != null)
			lblTitleIcon.setIcon(icon);
		if (songHasChanged) {
			if (Player.followCurrentSong && list != null && !list.changingCurrentWouldScareUser())
				bringCurrentIntoView();
			if (lblTitle != null) {
				lblTitle.setText(Player.getCurrentTitle(barSeek == null && Player.isPreparing()));
				lblTitle.setSelected(true);
				//if (ignoreAnnouncement)
				//	ignoreAnnouncement = false;
				//else if (UI.accessibilityManager != null && UI.accessibilityManager.isEnabled())
				//	UI.announceAccessibilityText(title);
			}
			if (lblArtist != null)
				lblArtist.setText((currentSong == null) ? "-" : currentSong.artist);
			if (lblTrack != null)
				lblTrack.setText((currentSong == null || currentSong.track <= 0) ? "-" : Integer.toString(currentSong.track));
			if (lblAlbum != null)
				lblAlbum.setText((currentSong == null) ? "-" : currentSong.album);
			if (lblLength != null)
				lblLength.setText((currentSong == null || currentSong.length == null || currentSong.length.length() == 0) ? "-" : currentSong.length);
			//if the user adds a song while in control mode, but quickly leaves control mode, they
			//will be able to see the song that has just been added
			//but if the current song changes, let's stick to it and ignore the recently added song
			if (Player.controlMode)
				Player.positionToCenter = -1;
		} else if (preparingHasChanged) {
			if (barSeek != null) {
				if (Player.isPreparing() && !barSeek.isTracking()) {
					barSeek.setText(R.string.loading);
					barSeek.setValue(0);
				}
			} else if (lblTitle != null) {
				lblTitle.setText(Player.getCurrentTitle(Player.isPreparing()));
				lblTitle.setSelected(true);
			}
		}
		if ((Player.localPlaying || Player.isPreparing()) && !Player.controlMode) {
			if (!tmrSong.isAlive())
				tmrSong.start(250);
		} else {
			tmrSong.stop();
		}
		lastTime = -2;
		handleTimer(tmrSong, null);
	}

	@Override
	public void onPlayerMetadataChanged(Song currentSong) {
		onPlayerChanged(currentSong, true, true, null);
		if (list != null) {
			for (int i = list.getChildCount() - 1; i >= 0; i--) {
				final View view = list.getChildAt(i);
				if (view instanceof SongView)
					((SongView)view).updateIfCurrent();
			}
		}
	}

	@Override
	public void onPlayerControlModeChanged(boolean controlMode) {
		if (Player.songs.selecting || Player.songs.moving)
			cancelSelection(false);
		onCleanupLayout();
		forceFadeOut = !controlMode;
		onCreateLayout(false);
		forceFadeOut = false;
		resume();
		System.gc();
	}
	
	@Override
	public void onPlayerGlobalVolumeChanged(int volume) {
		updateVolumeDisplay(volume);
	}
	
	@Override
	public void onPlayerAudioSinkChanged() {
		//when changing the output, the global volume usually changes
		if (Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) {
			updateVolumeDisplay(Integer.MIN_VALUE);
			if (barVolume != null)
				barVolume.setMax(Player.volumeStreamMax);
			tmrUpdateVolumeDisplay.start(750);
		}
	}
	
	@Override
	public void onPlayerMediaButtonPrevious() {
		if (!Player.controlMode)
			bringCurrentIntoView();
	}
	
	@Override
	public void onPlayerMediaButtonNext() {
		if (!Player.controlMode)
			bringCurrentIntoView();
	}
	
	@Override
 	public View getNullContextMenuView() {
		return ((!Player.songs.selecting && !Player.songs.moving && Player.state == Player.STATE_ALIVE) ? btnMenu : null);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (UI.forcedLocale != UI.LOCALE_NONE && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN && !localeHasBeenChanged) {
			localeHasBeenChanged = true;
			UI.reapplyForcedLocale(getHostActivity());
		}
		UI.prepare(menu);
		menu.add(0, MNU_ADDSONGS, 0, R.string.add_songs)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_FPLAY));
		UI.separator(menu, 0, 1);
		Menu s2, s = menu.addSubMenu(1, 0, 0, R.string.list)
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
		UI.separator(s, 0, 3);
		s.add(0, MNU_SORT_BY_TITLE, 4, R.string.sort_by_title)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_MOVE));
		s.add(0, MNU_SORT_BY_ARTIST, 5, R.string.sort_by_artist)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_MOVE));
		s.add(0, MNU_SORT_BY_ALBUM, 6, R.string.sort_by_album)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_MOVE));
		UI.separator(menu, 1, 1);
		menu.add(2, MNU_TOGGLECONTROLMODE, 0, R.string.control_mode)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(Player.controlMode ? UI.ICON_OPTCHK : UI.ICON_OPTUNCHK));
		if (UI.isLandscape && !UI.isLargeScreen) {
			s = menu.addSubMenu(2, 0, 1, R.string.more)
					.setIcon(new TextIconDrawable(UI.ICON_MENU));
			UI.prepare(s);
		} else {
			s = menu;
		}
		final int mnuId;
		final String mnuIcon;
		if (Player.songs.isInRandomMode()) {
			mnuId = R.string.random_mode;
			mnuIcon = UI.ICON_SHUFFLE;
		} else {
			switch (Player.songs.getRepeatMode()) {
			case SongList.REPEAT_NONE:
				mnuId = R.string.repeat_none;
				mnuIcon = UI.ICON_REPEATNONE;
				break;
			case SongList.REPEAT_ONE:
				mnuId = R.string.repeat_one;
				mnuIcon = UI.ICON_REPEATONE;
				break;
			default:
				mnuId = R.string.repeat_all;
				mnuIcon = UI.ICON_REPEAT;
				break;
			}
		}
		s2 = s.addSubMenu(2, 0, 0, getText(mnuId) + "\u2026")
			.setIcon(new TextIconDrawable(mnuIcon));
		UI.prepare(s2);
		s2.add(2, MNU_REPEAT, 0, getText(R.string.repeat_all))
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((Player.songs.getRepeatMode() == SongList.REPEAT_ALL && !Player.songs.isInRandomMode()) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		s2.add(2, MNU_REPEAT_ONE, 1, getText(R.string.repeat_one))
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((Player.songs.getRepeatMode() == SongList.REPEAT_ONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		UI.separator(s2, 2, 2);
		s2.add(2, MNU_RANDOMMODE, 3, getText(R.string.random_mode))
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(Player.songs.isInRandomMode() ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		UI.separator(s2, 2, 4);
		s2.add(2, MNU_REPEAT_NONE, 5, getText(R.string.repeat_none))
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((Player.songs.getRepeatMode() == SongList.REPEAT_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		UI.separator(s, 2, 1);
		s.add(2, MNU_EFFECTS, 2, R.string.audio_effects)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_EQUALIZER));
		s2 = s.addSubMenu(2, 0, 3, getText(R.string.visualizer) + "\u2026")
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		UI.prepare(s2);
		s2.add(0, MNU_VISUALIZER_SPECTRUM, 0, "Spectrum")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		if (BuildConfig.X)
			s2.add(0, MNU_VISUALIZER_SPECTRUM2, 1, "Spectrum 2")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		s2.add(0, MNU_VISUALIZER_SPIN, 2, "Spinning Rainbow")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		s2.add(0, MNU_VISUALIZER_PARTICLE, 3, "Sound Particles")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		s2.add(0, MNU_VISUALIZER_IMMERSIVE_PARTICLE, 4, "Into the Particles")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_3DPAN));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			s2.add(0, MNU_VISUALIZER_IMMERSIVE_PARTICLE_VR, 5, "Into the Particles (VR)")
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_3DPAN));
		UI.separator(s2, 1, 0);
		s2.add(1, MNU_VISUALIZER_ALBUMART, 1, R.string.album_art)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_ALBUMART));
		s2.add(1, MNU_VISUALIZER_BLUETOOTH, 2, "Bluetooth + Arduino")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_BLUETOOTH));
		UI.separator(s2, 2, 0);
		s2.add(2, MNU_VISUALIZER_LIQUID, 1, "Liquid Spectrum")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		s2.add(2, MNU_VISUALIZER, 2, "Classic")
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_VISUALIZER));
		s.add(2, MNU_SETTINGS, 4, R.string.settings)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_SETTINGS));
		UI.separator(menu, 2, 5);
		menu.add(3, MNU_EXIT, 0, R.string.exit)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable(UI.ICON_EXIT));
	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode > 100 && requestCode < 200 && Player.state == Player.STATE_ALIVE && grantResults != null && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			openVisualizer(requestCode);
		} else if (requestCode == 2 && pendingListCommand != 0) {
			if (Player.state == Player.STATE_ALIVE && grantResults != null && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				selectPlaylist(pendingListCommand);
			pendingListCommand = 0;
		}
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	private void openVisualizer(int id) {
		if (!BuildConfig.X && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (getHostActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				getHostActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, id);
				return;
			}
		}
		switch (id) {
		case MNU_VISUALIZER:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, SimpleVisualizerJni.class.getName()));
			break;
		case MNU_VISUALIZER_SPECTRUM:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()));
			break;
		case MNU_VISUALIZER_SPECTRUM2:
			if (BuildConfig.X)
				getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()).putExtra(OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE, OpenGLVisualizerJni.TYPE_SPECTRUM2));
			break;
		case MNU_VISUALIZER_LIQUID:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()).putExtra(OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE, OpenGLVisualizerJni.TYPE_LIQUID));
			break;
		case MNU_VISUALIZER_SPIN:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()).putExtra(OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE, OpenGLVisualizerJni.TYPE_SPIN));
			break;
		case MNU_VISUALIZER_PARTICLE:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()).putExtra(OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE, OpenGLVisualizerJni.TYPE_PARTICLE));
			break;
		case MNU_VISUALIZER_IMMERSIVE_PARTICLE:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()).putExtra(OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE, OpenGLVisualizerJni.TYPE_IMMERSIVE_PARTICLE));
			break;
		case MNU_VISUALIZER_IMMERSIVE_PARTICLE_VR:
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					if (getHostActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
						getHostActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, id);
						return;
					}
				}
				getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, OpenGLVisualizerJni.class.getName()).putExtra(OpenGLVisualizerJni.EXTRA_VISUALIZER_TYPE, OpenGLVisualizerJni.TYPE_IMMERSIVE_PARTICLE_VR));
			}
			break;
		case MNU_VISUALIZER_BLUETOOTH:
			startActivity(new ActivitySettings(false, true), 0, null, false);
			break;
		case MNU_VISUALIZER_ALBUMART:
			getHostActivity().startActivity((new Intent(Player.theApplication, ActivityVisualizer.class)).putExtra(Visualizer.EXTRA_VISUALIZER_CLASS_NAME, AlbumArtVisualizer.class.getName()));
			break;
		}
	}

	private void selectPlaylist(int how) {
		if (how == 1) {
			Player.alreadySelected = false;
			startActivity(ActivityFileSelection.createPlaylistSelector(getText(R.string.load_list), MNU_LOADLIST, false, true, this), 0, null, false);
		} else {
			startActivity(ActivityFileSelection.createPlaylistSelector(getText(R.string.save_list), MNU_SAVELIST, true, false, this), 0, null, false);
		}
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		final int id = item.getItemId();
		if (id == MNU_EXIT) {
			getHostActivity().setExitOnDestroy();
			Player.pause();
			finish(0, null, false);
			return true;
		}
		if (Player.state != Player.STATE_ALIVE)
			return true;
		switch (id) {
		case MNU_ADDSONGS:
			addSongs(null);
			break;
		case MNU_CLEARLIST:
			Player.songs.clear();
			break;
		case MNU_LOADLIST:
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (!getHostActivity().isWriteStoragePermissionGranted()) {
					pendingListCommand = 1;
					getHostActivity().requestWriteStoragePermission();
					break;
				}
			}
			selectPlaylist(1);
			break;
		case MNU_SAVELIST:
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (!getHostActivity().isWriteStoragePermissionGranted()) {
					pendingListCommand = 2;
					getHostActivity().requestWriteStoragePermission();
					break;
				}
			}
			selectPlaylist(2);
			break;
		case MNU_SORT_BY_TITLE:
			Player.songs.sort(SongList.SORT_BY_TITLE);
			break;
		case MNU_SORT_BY_ARTIST:
			Player.songs.sort(SongList.SORT_BY_ARTIST);
			break;
		case MNU_SORT_BY_ALBUM:
			Player.songs.sort(SongList.SORT_BY_ALBUM);
			break;
		case MNU_TOGGLECONTROLMODE:
			Player.setControlMode(!Player.controlMode);
			break;
		case MNU_REPEAT:
			Player.songs.setRepeatMode(SongList.REPEAT_ALL);
			break;
		case MNU_REPEAT_ONE:
			Player.songs.setRepeatMode(SongList.REPEAT_ONE);
			break;
		case MNU_RANDOMMODE:
			Player.songs.setRandomMode(true);
			break;
		case MNU_REPEAT_NONE:
			Player.songs.setRepeatMode(SongList.REPEAT_NONE);
			break;
		case MNU_EFFECTS:
			if (ExternalFx.isEnabled() && ExternalFx.isSupported())
				ExternalFx.displayUI(getHostActivity());
			else
				startActivity(new ActivityEffects(), 0, null, false);
			break;
		case MNU_VISUALIZER:
		case MNU_VISUALIZER_SPECTRUM:
		case MNU_VISUALIZER_SPECTRUM2:
		case MNU_VISUALIZER_LIQUID:
		case MNU_VISUALIZER_SPIN:
		case MNU_VISUALIZER_PARTICLE:
		case MNU_VISUALIZER_IMMERSIVE_PARTICLE:
		case MNU_VISUALIZER_IMMERSIVE_PARTICLE_VR:
		case MNU_VISUALIZER_BLUETOOTH:
		case MNU_VISUALIZER_ALBUMART:
			openVisualizer(id);
			break;
		case MNU_SETTINGS:
			startActivity(new ActivitySettings(false, false), 0, null, false);
			break;
		}
		return true;
	}

	@Override
	public void onClick(View view) {
		if (view == btnAdd) {
			addSongs(view);
		} else if (view == btnPrev) {
			Player.previous();
			if (!Player.controlMode)
				bringCurrentIntoView();
		} else if (view == btnPlay) {
			Player.playPause();
		} else if (view == btnNext) {
			Player.next();
			if (!Player.controlMode)
				bringCurrentIntoView();
		} else if (view == btnMenu) {
			CustomContextMenu.openContextMenu(btnMenu, this);
		} else if (view == btnMoreInfo) {
			showMoreInfo();
		} else if (view == btnMoveSel) {
			startMovingSelection();
		} else if (view == btnRemoveSel) {
			removeSelection();
		} else if (view == btnCancelSel) {
			cancelSelection(false);
		} else if (view == btnDecreaseVolume) {
			//this click will only actually perform an action when triggered by keys
			if (volumeButtonPressed == 0)
				decreaseVolume();
		} else if (view == btnIncreaseVolume) {
			//this click will only actually perform an action when triggered by keys
			if (volumeButtonPressed == 0)
				increaseVolume();
		} else if (view == btnVolume) {
			Player.showStreamVolumeUI();
		} else if (view == lblTitle) {
			if (Player.controlMode)
				Player.playPause();
		} else if (view == list) {
			if (Player.songs.getCount() == 0)
				addSongs(view);
		}
	}

	@Override
	public void onItemClicked(int position) {
		if (Player.songs.selecting) {
			lastSel = position;
			Player.songs.setSelection(firstSel, position, position, true, true);
		} else if (Player.songs.moving) {
			Player.songs.moveSelection(position);
		} else {
			if (UI.doubleClickMode) {
				if (Player.songs.getFirstSelectedPosition() == position) {
					if (Player.songs.getItemT(position) == Player.localSong && !Player.localPlaying)
						Player.playPause();
					else
						Player.play(position);
				} else {
					Player.songs.setSelection(position, position, true, true);
				}
			} else {
				boolean ok = true;
				if (Player.songs.getItemT(position) == Player.localSong && !Player.localPlaying)
					Player.playPause();
				else
					ok = Player.play(position);
				if (ok)
					Player.songs.setSelection(position, position, true, true);
			}
		}
	}

	@Override
	public void onItemLongClicked(int position) {
		if (!Player.songs.selecting && !Player.songs.moving) {
			//select the clicked item before opening the menu
			if (!Player.songs.isSelected(position))
				Player.songs.setSelection(position, position, position, true, true);
			firstSel = Player.songs.getFirstSelectedPosition();
			lastSel = firstSel;
			startSelecting();
		}
	}

	@Override
	public void onItemCheckboxClicked(int position) {
	}

	@Override
	public boolean onBackPressed() {
		/*if (Player.controlMode) {
			Player.setControlMode(false);
			return true;
		} else*/ if (Player.songs.selecting || Player.songs.moving) {
			cancelSelection(false);
			return true;
		}
		return UI.blockBackKey;
	}

	@Override
	protected void onCreate() {
		if (Player.state >= Player.STATE_TERMINATING) {
			skipToDestruction = true;
			return;
		} else {
			skipToDestruction = false;
		}
		localeHasBeenChanged = false;
		addWindowFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		if (UI.keepScreenOn)
			addWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			clearWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getHostActivity().setRequestedOrientation((UI.forcedOrientation == 0) ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ((UI.forcedOrientation < 0) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
		//whenever the activity is being displayed, the volume keys must control
		//the music volume and nothing else!
		getHostActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
		Player.songs.selecting = false;
		Player.songs.moving = false;
		firstSel = -1;
		lastSel = -1;
		lastTime = -2;
		timeBuilder = new StringBuilder(16);
		volumeBuilder = new StringBuilder(16);
		tmrSong = new Timer(this, "Song Timer", false, true, true);
		tmrUpdateVolumeDisplay = new Timer(this, "Update Volume Display Timer", true, true, false);
		tmrVolume = new Timer(this, "Volume Timer", false, true, true);
		pendingListCommand = 0;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		if (Player.state >= Player.STATE_TERMINATING || skipToDestruction) {
			skipToDestruction = true;
			finish(0, null, false);
			return;
		}
		setContentView(Player.controlMode ? (UI.isLandscape ? R.layout.activity_main_control_l : R.layout.activity_main_control) : (UI.isLandscape ? ((UI.isLargeScreen && UI.controlsToTheLeft) ? R.layout.activity_main_l_left : R.layout.activity_main_l) : R.layout.activity_main), true, forceFadeOut);
		lblTitle = (TextView)findViewById(R.id.lblTitle);
		btnPrev = (BgButton)findViewById(R.id.btnPrev);
		btnPrev.setOnClickListener(this);
		btnNext = (BgButton)findViewById(R.id.btnNext);
		btnNext.setOnClickListener(this);
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		if (Player.controlMode) {
			final ViewGroup panelControls = (ViewGroup)findViewById(R.id.panelControls);
			panelControls.setBackgroundDrawable(new ColorDrawable(UI.color_control_mode));
			final int panelControlsPadding = (UI.isLowDpiScreen ? UI.controlMargin : UI.controlLargeMargin);
			panelControls.setPadding(panelControlsPadding, panelControlsPadding, panelControlsPadding, panelControlsPadding);
			UI.largeText(lblTitle);
			btnPrev.setIconNoChanges(UI.ICON_PREV);
			btnNext.setIconNoChanges(UI.ICON_NEXT);
			btnMenu.setIconNoChanges(UI.ICON_MENU);
			btnPrev.setIconStretchable(true);
			btnNext.setIconStretchable(true);
			btnMenu.setIconStretchable(true);
			
			volumeButtonPressed = 0;
			btnDecreaseVolume = (BgButton)findViewById(R.id.btnDecreaseVolume);
			btnDecreaseVolume.setOnClickListener(this);
			btnDecreaseVolume.setOnPressingChangeListener(this);
			btnDecreaseVolume.setIconNoChanges(UI.ICON_DECREASE_VOLUME);
			btnDecreaseVolume.setIconStretchable(true);
			btnIncreaseVolume = (BgButton)findViewById(R.id.btnIncreaseVolume);
			btnIncreaseVolume.setOnClickListener(this);
			btnIncreaseVolume.setOnPressingChangeListener(this);
			btnIncreaseVolume.setIconNoChanges(UI.ICON_INCREASE_VOLUME);
			btnIncreaseVolume.setIconStretchable(true);
			btnVolume = (BgButton)findViewById(R.id.btnVolume);
			btnVolume.setIconNoChanges(UI.ICON_VOLUME0);
			btnVolume.setIconStretchable(true);
			btnVolume.setEnabled(false);
			
			Player.songs.selecting = false;
			Player.songs.moving = false;
			
			lblTitle.setOnClickListener(this);
			final int w = getDecorViewWidth() - (panelControlsPadding << 1), h = getDecorViewHeight() - (panelControlsPadding << 1);
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
			findViewById(R.id.panelTop).setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelH));
			
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

			lblTitleIcon = new TextIconDrawable(UI.ICON_PLAY, UI.colorState_text_control_mode_reactive.getDefaultColor(), panelH >> 1);
			lblTitle.setCompoundDrawables(null, null, lblTitleIcon, null);

			final int lds = ((UI.isLowDpiScreen && !UI.isLargeScreen) ? (UI.isLandscape ? ((UI.controlMargin * 3) >> 1) : UI.controlMargin) : UI.controlLargeMargin);
			btnDecreaseVolume.setPadding(lds, lds, lds, lds);
			btnIncreaseVolume.setPadding(lds, lds, lds, lds);
			btnVolume.setPadding(lds, lds, lds, lds);
			btnMenu.setPadding(lds, lds, lds, lds);

			if (UI.isLandscape) {
				lblTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, (min * 9) / 100);
				final int pa = (min * 7) / 100;
				btnPrev.setPadding(pa, pa, pa, pa);
				btnNext.setPadding(pa, pa, pa, pa);
			} else {
				LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (max * 40) / 100);
				p2.topMargin = UI.controlLargeMargin;
				p2.bottomMargin = UI.controlLargeMargin;
				lblTitle.setLayoutParams(p2);
				lblTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, (min * 9) / 100);
				final int ph = (min * 12) / 100, pv = (max * 12) / 100;
				btnPrev.setPadding(ph, pv, ph, pv);
				btnNext.setPadding(ph, pv, ph, pv);
			}
			btnDecreaseVolume.setTextColor(UI.colorState_text_control_mode_reactive);
			btnVolume.setTextColor(UI.colorState_text_control_mode_reactive);
			btnIncreaseVolume.setTextColor(UI.colorState_text_control_mode_reactive);
			btnMenu.setTextColor(UI.colorState_text_control_mode_reactive);
			lblTitle.setTextColor(UI.colorState_text_control_mode_reactive);
			btnPrev.setTextColor(UI.colorState_text_control_mode_reactive);
			btnNext.setTextColor(UI.colorState_text_control_mode_reactive);
		} else {
			UI.largeText(lblTitle);
			btnPrev.setIcon(UI.ICON_PREV);
			btnNext.setIcon(UI.ICON_NEXT);
			btnMenu.setIcon(UI.ICON_MENU);
			
			btnAdd = (BgButton)findViewById(R.id.btnAdd);
			btnAdd.setOnClickListener(this);
			btnAdd.setIcon(UI.ICON_FPLAY);
			
			if (!UI.marqueeTitle) {
				lblTitle.setEllipsize(TruncateAt.END);
				lblTitle.setHorizontallyScrolling(false);
			} else {
				lblTitle.setHorizontalFadingEdgeEnabled(false);
				lblTitle.setVerticalFadingEdgeEnabled(false);
				//lblTitle.setFadingEdgeLength(0);
			}
			lblTitle.setTextColor(UI.colorState_text_title_static);
			
			lblArtist = (TextView)findViewById(R.id.lblArtist);
			if (UI.isLargeScreen == (lblArtist == null))
				UI.isLargeScreen = (lblArtist != null);
			
			lblMsgSelMove = (TextView)findViewById(R.id.lblMsgSelMove);
			UI.largeText(lblMsgSelMove);
			lblMsgSelMove.setTextColor(UI.colorState_text_title_static);
			lblMsgSelMove.setHorizontalFadingEdgeEnabled(false);
			lblMsgSelMove.setVerticalFadingEdgeEnabled(false);
			//lblMsgSelMove.setFadingEdgeLength(0);
			barSeek = (BgSeekBar)findViewById(R.id.barSeek);
			barSeek.setAdditionalContentDescription(getText(R.string.go_to).toString());
			barSeek.setOnBgSeekBarChangeListener(this);
			barSeek.setMax(MAX_SEEK);
			barSeek.setVertical(UI.isLandscape && !UI.isLargeScreen);
			barSeek.setFocusable(false);
			btnPlay = (BgButton)findViewById(R.id.btnPlay);
			btnPlay.setOnClickListener(this);
			btnPlay.setIcon(UI.ICON_PLAY);
			list = (BgListView)findViewById(R.id.list);
			list.setScrollBarType(UI.songListScrollBarType);
			list.setOnKeyDownObserver(this);
			list.setEmptyListOnClickListener(this);
			//Apparently, not all devices can properly draw the character &#9835; :(
			final String originalText = getText(R.string.touch_to_add_songs).toString();
			final int iconIdx = originalText.indexOf('\u266B');
			if (iconIdx > 0) {
				final SpannableStringBuilder txt = new SpannableStringBuilder(originalText);
				txt.setSpan(new ImageSpan(new TextIconDrawable(UI.ICON_FPLAY, UI.color_text_disabled, UI._22sp, 0), DynamicDrawableSpan.ALIGN_BASELINE), iconIdx, iconIdx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				list.setCustomEmptyText(txt);
			} else {
				list.setCustomEmptyText(originalText);
			}
			panelControls = (ViewGroup)findViewById(R.id.panelControls);
			panelSecondary = (ViewGroup)findViewById(R.id.panelSecondary);
			panelSelection = (ViewGroup)findViewById(R.id.panelSelection);
			btnMoreInfo = (BgButton)findViewById(R.id.btnMoreInfo);
			btnMoreInfo.setOnClickListener(this);
			btnMoreInfo.setIcon(UI.ICON_INFORMATION, UI.isLargeScreen || !UI.isLandscape, true);
			btnMoveSel = (BgButton)findViewById(R.id.btnMoveSel);
			btnMoveSel.setOnClickListener(this);
			btnMoveSel.setIcon(UI.ICON_MOVE, UI.isLargeScreen || !UI.isLandscape, true);
			btnRemoveSel = (BgButton)findViewById(R.id.btnRemoveSel);
			btnRemoveSel.setOnClickListener(this);
			btnRemoveSel.setIcon(UI.ICON_DELETE, UI.isLargeScreen || !UI.isLandscape, true);
			btnCancelSel = (BgButton)findViewById(R.id.btnCancelSel);
			btnCancelSel.setOnClickListener(this);
			
			barVolume = (BgSeekBar)findViewById(R.id.barVolume);
			btnVolume = (BgButton)findViewById(R.id.btnVolume);

			if (UI.isLargeScreen) {
				UI.mediumTextAndColor((TextView)findViewById(R.id.lblTitleStatic));
				UI.mediumTextAndColor((TextView)findViewById(R.id.lblArtistStatic));
				UI.mediumTextAndColor((TextView)findViewById(R.id.lblTrackStatic));
				UI.mediumTextAndColor((TextView)findViewById(R.id.lblAlbumStatic));
				UI.mediumTextAndColor((TextView)findViewById(R.id.lblLengthStatic));
				lblArtist.setTextColor(UI.colorState_text_title_static);
				UI.largeText(lblArtist);
				lblTrack = (TextView)findViewById(R.id.lblTrack);
				lblTrack.setTextColor(UI.colorState_text_title_static);
				UI.largeText(lblTrack);
				lblAlbum = (TextView)findViewById(R.id.lblAlbum);
				lblAlbum.setTextColor(UI.colorState_text_title_static);
				UI.largeText(lblAlbum);
				lblLength = (TextView)findViewById(R.id.lblLength);
				lblLength.setTextColor(UI.colorState_text_title_static);
				UI.largeText(lblLength);
			} else {
				lblTrack = null;
				lblAlbum = null;
				lblLength = null;
			}
			
			if (Player.volumeControlType == Player.VOLUME_CONTROL_NONE) {
				panelSecondary.removeView(barVolume);
				barVolume = null;
				btnVolume.setVisibility(View.VISIBLE);
				btnVolume.setOnClickListener(this);
				btnVolume.setIcon(UI.ICON_VOLUME4);
				vwVolume = btnVolume;
				vwVolumeId = R.id.btnVolume;
				if (UI.isLargeScreen) {
					UI.setNextFocusForwardId(list, R.id.btnVolume);
					UI.setNextFocusForwardId(barSeek, R.id.btnVolume);
					barSeek.setNextFocusRightId(R.id.btnVolume);
					if (!UI.isLandscape)
						btnAdd.setNextFocusLeftId(R.id.btnVolume);
					btnAdd.setNextFocusUpId(R.id.btnVolume);
					btnPrev.setNextFocusUpId(R.id.btnVolume);
					btnPlay.setNextFocusUpId(R.id.btnVolume);
					btnNext.setNextFocusUpId(R.id.btnVolume);
					btnMenu.setNextFocusUpId(R.id.btnVolume);
				} else {
					if (UI.isLandscape) {
						btnPrev.setNextFocusRightId(R.id.btnVolume);
						btnPlay.setNextFocusRightId(R.id.btnVolume);
						btnNext.setNextFocusRightId(R.id.btnVolume);
						btnAdd.setNextFocusRightId(R.id.btnVolume);
					} else {
						btnPrev.setNextFocusDownId(R.id.btnVolume);
						btnPlay.setNextFocusDownId(R.id.btnVolume);
						btnNext.setNextFocusDownId(R.id.btnVolume);
						btnAdd.setNextFocusDownId(R.id.btnVolume);
					}
					UI.setNextFocusForwardId(btnMenu, R.id.btnVolume);
					btnMenu.setNextFocusRightId(R.id.btnVolume);
					btnMenu.setNextFocusDownId(R.id.btnVolume);
				}
			} else {
				panelSecondary.removeView(btnVolume);
				btnVolume = null;
				barVolume.setAdditionalContentDescription(getText(R.string.volume).toString());
				barVolume.setOnBgSeekBarChangeListener(this);
				barVolume.setMax(
					((Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) ?
						Player.volumeStreamMax :
							((Player.volumeControlType == Player.VOLUME_CONTROL_DB) ?
								(-Player.VOLUME_MIN_DB / 100) :
									100)));
				barVolume.setVertical(UI.isLandscape && !UI.isLargeScreen);
				barVolume.setKeyIncrement(1);
				barVolume.setIcon(UI.ICON_VOLUME4);
				vwVolume = barVolume;
				vwVolumeId = R.id.barVolume;
			}

			if (UI.isLargeScreen) {
				findViewById(R.id.panelInfo).setBackgroundDrawable(new BorderDrawable(UI.color_highlight, UI.color_window, ((UI.isLandscape && !UI.controlsToTheLeft) ? UI.thickDividerSize : 0), (!UI.isLandscape ? UI.thickDividerSize : 0), ((UI.isLandscape && UI.controlsToTheLeft) ? UI.thickDividerSize : 0), 0));
			} else {
				if (UI.isLandscape) {
					//we need these two panels (panelAnimationBg and panelAnimationBg2)
					//because ugly artifacts appear when an animation is going on
					findViewById(R.id.panelAnimationBg).setBackgroundDrawable(new ColorDrawable(UI.color_window));
					findViewById(R.id.panelAnimationBg2).setBackgroundDrawable(new BorderDrawable(UI.color_highlight, UI.color_window, 0, 0, UI.thickDividerSize, 0));
					list.setTopBorder();
					panelSecondary.setPadding(0, 0, UI.controlMargin + UI.thickDividerSize, UI.controlLargeMargin);
				} else {
					final LinearLayout panelTop = (LinearLayout)findViewById(R.id.panelTop);
					if (UI.placeTitleAtTheBottom) {
						panelTop.removeView(lblTitle);
						panelTop.removeView(lblMsgSelMove);
						panelTop.addView(lblTitle);
						panelTop.addView(lblMsgSelMove);
						lblTitle.setPadding(UI.controlSmallMargin, 0, UI.controlSmallMargin, UI.controlMargin);
						lblMsgSelMove.setPadding(UI.controlSmallMargin, 0, UI.controlSmallMargin, UI.controlMargin);
						if (UI.extraSpacing)
							panelTop.setPadding(0, UI.controlMargin, 0, 0);
					}
					panelTop.setBackgroundDrawable(new BorderDrawable(UI.color_highlight, UI.color_window, 0, 0, 0, UI.thickDividerSize));
					panelSecondary.setPadding(0, 0, 0, UI.controlMargin + UI.thickDividerSize);
				}
				if (UI.extraSpacing) {
					panelControls.setPadding(UI.controlMargin, 0, UI.controlMargin, UI.controlMargin);
					panelSelection.setPadding(UI.controlMargin, 0, UI.controlMargin + (UI.isLandscape ? UI.thickDividerSize : 0), UI.controlMargin + (UI.isLandscape ? 0 : UI.thickDividerSize));
				} else {
					if (!UI.isLandscape)
						panelControls.setPadding(0, 0, 0, UI.isLowDpiScreen ? 0 : UI.controlMargin);
					panelSelection.setPadding(0, 0, UI.isLandscape ? UI.thickDividerSize : 0, 0);
				}
			}
			btnCancelSel.setDefaultHeight();
			final boolean m = Player.songs.moving;
			isCreatingLayout = true;
			if (m || Player.songs.selecting)
				startSelecting();
			if (m)
				startMovingSelection();
			isCreatingLayout = false;
		}
	}

	@Override
	public void onBgListViewAttached(BgListView list) {
		//after a long time, I decided the best solution is to simply center the desired song
		//instead of trying to memorize the list position
		if (Player.positionToCenter >= 0) {
			list.centerItem(Player.positionToCenter);
			Player.positionToCenter = -1;
		} else {
			list.centerItem(Player.songs.getSelection());
		}
	}

	@Override
	public boolean onBgListViewKeyDown(BgListView list, int keyCode) {
		switch (keyCode) {
		case UI.KEY_LEFT:
			if (btnCancelSel != null && btnMoreInfo != null && btnMoveSel != null && btnRemoveSel != null && btnMenu != null && vwVolume != null) {
				if (Player.songs.selecting)
					(UI.isLargeScreen ? btnCancelSel : (UI.isLandscape ? btnMoreInfo : btnRemoveSel)).requestFocus();
				else if (Player.songs.moving)
					btnCancelSel.requestFocus();
				else if (UI.isLargeScreen)
					btnMenu.requestFocus();
				else
					vwVolume.requestFocus();
			}
			return true;
		case UI.KEY_RIGHT:
			if (btnCancelSel != null && btnMoreInfo != null && btnMoveSel != null && btnAdd != null && vwVolume != null) {
				if (Player.songs.selecting)
					((UI.isLargeScreen || UI.isLandscape) ? btnMoreInfo : btnCancelSel).requestFocus();
				else if (Player.songs.moving)
					btnCancelSel.requestFocus();
				else if (UI.isLargeScreen)
					(UI.isLandscape ? btnAdd : vwVolume).requestFocus();
				else
					btnAdd.requestFocus();
			}
			return true;
		}
		final int s = Player.songs.getSelection();
		if (Player.songs.moving || Player.songs.selecting) {
			switch (keyCode) {
			case UI.KEY_DEL:
				if (s >= 0)
					removeSelection();
				return true;
			case UI.KEY_EXTRA:
			case UI.KEY_ENTER:
				if (Player.songs.selecting)
					startMovingSelection();
				else
					cancelSelection(false);
				return true;
			case UI.KEY_UP:
			case UI.KEY_DOWN:
			case UI.KEY_PAGE_UP:
			case UI.KEY_PAGE_DOWN:
			case UI.KEY_HOME:
			case UI.KEY_END:
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
					list.centerItem(n);
				return true;
			}
		} else {
			switch (keyCode) {
			case UI.KEY_DEL:
				if (s >= 0)
					Player.songs.removeSelection();
				return true;
			case UI.KEY_EXTRA:
				if (s >= 0)
					onItemLongClicked(s);
				return true;
			case UI.KEY_ENTER:
				if (s >= 0) {
					if (Player.songs.getItemT(s) == Player.localSong && !Player.localPlaying)
						Player.playPause();
					else
						Player.play(s);
				}
				return true;
			}
		}
		return false;
	}
	
	private void resume() {
		Player.songs.setItemClickListener(this);
		Player.songs.setObserver(list);
		updateVolumeDisplay(Integer.MIN_VALUE);
		if (list != null)
			list.notifyMeWhenFirstAttached(this);
		//ignoreAnnouncement = true;
		onPlayerChanged(Player.localSong, true, true, null);
	}
	
	@Override
	protected void onResume() {
		Player.isMainActiveOnTop = true;
		Player.observer = this;
		Player.registerMediaButtonEventReceiver();
		resume();
		UI.showNextStartupMsg(getHostActivity());
	}
	
	@Override
	protected void onOrientationChanged() {
		onCleanupLayout();
		onCreateLayout(false);
		resume();
	}
	
	@Override
	protected void onPause() {
		if (skipToDestruction)
			return;
		Player.isMainActiveOnTop = false;
		if (tmrSong != null)
			tmrSong.stop();
		if (tmrUpdateVolumeDisplay != null)
			tmrUpdateVolumeDisplay.stop();
		if (tmrVolume != null)
			tmrVolume.stop();
		volumeButtonPressed = 0;
		if (Player.songs.selecting || Player.songs.moving)
			cancelSelection(false);
		Player.songs.setItemClickListener(null);
		Player.songs.setObserver(null);
		Player.observer = null;
		lastTime = -2;
		pendingListCommand = 0;
		if (UI.forcedLocale != UI.LOCALE_NONE && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN && !localeHasBeenChanged) {
			localeHasBeenChanged = true;
			UI.reapplyForcedLocale(getHostActivity());
		}
	}
	
	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		lblTitle = null;
		lblArtist = null;
		lblTrack = null;
		lblAlbum = null;
		lblLength = null;
		lblTitleIcon = null;
		lblMsgSelMove = null;
		barSeek = null;
		barVolume = null;
		vwVolume = null;
		btnAdd = null;
		btnPrev = null;
		btnPlay = null;
		btnNext = null;
		btnMenu = null;
		panelControls = null;
		panelSecondary = null;
		panelSelection = null;
		btnMoreInfo = null;
		btnMoveSel = null;
		btnRemoveSel = null;
		btnCancelSel = null;
		btnDecreaseVolume = null;
		btnIncreaseVolume = null;
		btnVolume = null;
		list = null;
		if (tmrSong != null)
			tmrSong.stop();
		if (tmrUpdateVolumeDisplay != null)
			tmrUpdateVolumeDisplay.stop();
		if (tmrVolume != null)
			tmrVolume.stop();
	}
	
	@Override
	protected void onDestroy() {
		skipToDestruction = false;
		if (tmrSong != null) {
			tmrSong.release();
			tmrSong = null;
		}
		if (tmrUpdateVolumeDisplay != null) {
			tmrUpdateVolumeDisplay.release();
			tmrUpdateVolumeDisplay = null;
		}
		if (tmrVolume != null) {
			tmrVolume.release();
			tmrVolume = null;
		}
		timeBuilder = null;
		volumeBuilder = null;
	}
	
	@Override
	public void handleTimer(Timer timer, Object param) {
		if (timer == tmrVolume) {
			if (tmrVolumeInitialDelay > 0) {
				tmrVolumeInitialDelay--;
			} else {
				switch (volumeButtonPressed) {
				case 1:
					if (!decreaseVolume())
						tmrVolume.stop();
					break;
				case 2:
					if (!increaseVolume())
						tmrVolume.stop();
					break;
				default:
					tmrVolume.stop();
					break;
				}
			}
			return;
		} else if (timer == tmrUpdateVolumeDisplay) {
			updateVolumeDisplay(Integer.MIN_VALUE);
			return;
		}
		final Song s = Player.localSong;
		if (Player.isPreparing()) {
			if (barSeek != null && !barSeek.isTracking()) {
				if (s.isHttp) {
					final int m = Player.getHttpPosition();
					barSeek.setText((m == -1) ? getText(R.string.connecting).toString() : ((Player.getHttpPosition() >>> 10) + " KiB " + getText(R.string.loading)));
				} else {
					barSeek.setText(R.string.loading);
				}
				barSeek.setValue(0);
			}
			return;
		} else if (s != null && s.isHttp) {
			if (barSeek != null) {
				final int m = Player.getHttpPosition();
				barSeek.setText((m == -1) ? "-" : ((m >>> 10) + " KiB"));
				barSeek.setValue(0);
			}
			return;
		}
		final int m = Player.getPosition(),
				t = ((m < 0) ? -1 : (m / 1000));
		if (t == lastTime) return;
		lastTime = t;
		if (t < 0) {
			if (barSeek != null && !barSeek.isTracking()) {
				barSeek.setText(R.string.no_info);
				barSeek.setValue(0);
			}
		} else {
			Song.formatTimeSec(t, timeBuilder);
			if (barSeek != null && !barSeek.isTracking()) {
				int v = 0;
				if (s != null && s.lengthMS > 0) {
					//avoid overflow! ;)
					v = ((m >= 214740) ?
						(int)(((long)m * (long)MAX_SEEK) / (long)s.lengthMS) :
						((m * MAX_SEEK) / s.lengthMS));
				}
				barSeek.setText(timeBuilder.toString());
				barSeek.setValue(v);
			}
		}
	}
	
	private int getMSFromBarValue(int value) {
		final Song s = Player.localSong;
		if (s == null || s.lengthMS <= 0 || value < 0)
			return -1;
		return (int)(((long)value * (long)s.lengthMS) / (long)MAX_SEEK);
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (fromUser) {
			if (seekBar == barVolume) {
				final int volume = Player.setVolume(
					((Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) ?
						value :
							((Player.volumeControlType == Player.VOLUME_CONTROL_DB) ?
								Player.VOLUME_MIN_DB + (value * 100) :
									Player.VOLUME_MIN_DB - ((value * Player.VOLUME_MIN_DB) / 100))));
				setVolumeIcon(volume);
				seekBar.setText(volumeToString(volume));
			} else if (seekBar == barSeek) {
				value = getMSFromBarValue(value);
				if (value < 0) {
					seekBar.setText(R.string.no_info);
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
			if (Player.localSong != null && Player.localSong.lengthMS > 0) {
				if (UI.expandSeekBar && !UI.isLargeScreen) {
					UI.animationReset();
					UI.animationAddViewToHide(vwVolume);
					UI.animationCommit(isCreatingLayout, null);
				}
				return true;
			}
			return false;
		}
		return true;
	}
	
	@Override
	public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled) {
		if (seekBar == barSeek) {
			if (Player.localSong != null) {
				final int ms = getMSFromBarValue(seekBar.getValue());
				if (cancelled || ms < 0)
					handleTimer(tmrSong, null);
				else
					Player.seekTo(ms);
			}
			if (UI.expandSeekBar && !UI.isLargeScreen) {
				UI.animationReset();
				UI.animationAddViewToShow(vwVolume);
				UI.animationCommit(isCreatingLayout, null);
			}
		}
	}

	@Override
	public void onFileSelected(int id, FileSt file) {
		if (id == MNU_LOADLIST) {
			Player.songs.clear();
			Player.songs.startDeserializingOrImportingFrom(file, true, false, false);
			BackgroundActivityMonitor.start(getHostActivity());
		} else {
			Player.songs.startExportingTo(file);
			BackgroundActivityMonitor.start(getHostActivity());
		}
	}

	@Override
	public void onAddClicked(int id, FileSt file) {
		if (id == MNU_LOADLIST) {
			Player.songs.startDeserializingOrImportingFrom(file, false, true, false);
			BackgroundActivityMonitor.start(getHostActivity());
		}
	}

	@Override
	public void onPlayClicked(int id, FileSt file) {
		if (id == MNU_LOADLIST) {
			Player.songs.startDeserializingOrImportingFrom(file, false, !Player.clearListWhenPlayingFolders, true);
			BackgroundActivityMonitor.start(getHostActivity());
		}
	}

	@Override
	public boolean onDeleteClicked(int id, FileSt file) {
		SongList.delete(file);
		return true;
	}

	@Override
	public void onPressingChanged(BgButton button, boolean pressed) {
		if (button == btnDecreaseVolume) {
			if (pressed) {
				if (Player.volumeControlType == Player.VOLUME_CONTROL_NONE) {
					Player.showStreamVolumeUI();
					tmrVolume.stop();
				} else {
					volumeButtonPressed = 1;
					tmrVolumeInitialDelay = 3;
					if (decreaseVolume())
						tmrVolume.start(175);
					else
						tmrVolume.stop();
				}
			} else if (volumeButtonPressed == 1) {
				volumeButtonPressed = 0;
				tmrVolume.stop();
			}
		} else if (button == btnIncreaseVolume) {
			if (pressed) {
				if (Player.volumeControlType == Player.VOLUME_CONTROL_NONE) {
					Player.showStreamVolumeUI();
					tmrVolume.stop();
				} else {
					volumeButtonPressed = 2;
					tmrVolumeInitialDelay = 3;
					if (increaseVolume())
						tmrVolume.start(175);
					else
						tmrVolume.stop();
				}
			} else if (volumeButtonPressed == 2) {
				volumeButtonPressed = 0;
				tmrVolume.stop();
			}
		}
	}
}
