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
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.list.RadioStationList;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BackgroundActivityMonitor;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgColorStateList;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.RadioStationView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.SafeURLSpan;

public final class ActivityBrowserRadio extends ActivityBrowserView implements View.OnClickListener, DialogInterface.OnClickListener, DialogInterface.OnCancelListener, BgListView.OnBgListViewKeyDownObserver, RadioStationList.OnBaseListSelectionChangedListener<RadioStation>, SpinnerAdapter, RadioStationList.RadioStationAddedObserver, Animation.AnimationListener {
	private TextView sep2;
	private BgListView list;
	private RadioStationList radioStationList;
	private RelativeLayout panelSecondary, panelLoading;
	private RadioButton chkGenre, chkTerm;
	private ColorStateList defaultTextColors;
	private Spinner btnGenre;
	private EditText txtTerm;
	private BgButton btnGoBack, btnFavorite, btnSearch, btnGoBackToPlayer, btnAdd, btnPlay;
	private boolean loading, isAtFavorites, isCreatingLayout, isHidingLoadingPanel;
	private Animation animation, loadingPanelAnimationHide, loadingPanelAnimationShow;
	private CharSequence msgNoFavorites, msgNoStations, msgLoading;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.add_radio);
	}

	private void updateButtons() {
		UI.animationReset();
		if (!isAtFavorites != (btnFavorite.getVisibility() == View.VISIBLE)) {
			if (isAtFavorites) {
				UI.animationAddViewToHide(btnFavorite);
				UI.animationAddViewToHide(btnSearch);
				btnGoBack.setNextFocusRightId(R.id.list);
				UI.setNextFocusForwardId(btnGoBack, R.id.list);
			} else {
				UI.animationAddViewToShow(btnFavorite);
				UI.animationAddViewToShow(btnSearch);
				btnGoBack.setNextFocusRightId(R.id.btnFavorite);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnFavorite);
			}
		}
		final int s = radioStationList.getSelection();
		if ((s >= 0) != (btnAdd.getVisibility() == View.VISIBLE)) {
			if (s >= 0) {
				UI.animationAddViewToShow(btnAdd);
				UI.animationAddViewToShow(sep2);
				UI.animationAddViewToShow(btnPlay);
				btnGoBack.setNextFocusLeftId(R.id.btnPlay);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnAdd);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnAdd);
			} else {
				UI.animationAddViewToHide(btnAdd);
				UI.animationAddViewToHide(sep2);
				UI.animationAddViewToHide(btnPlay);
				btnGoBack.setNextFocusLeftId(R.id.btnGoBackToPlayer);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnGoBack);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnGoBack);
			}
		}
		UI.animationCommit(isCreatingLayout, null);
	}
	
	private void addPlaySelectedItem(final boolean play) {
		if (radioStationList.getSelection() < 0)
			return;
		try {
			final RadioStation radioStation = radioStationList.getItemT(radioStationList.getSelection());
			if (radioStation.m3uUri == null || radioStation.m3uUri.length() < 0) {
				UI.toast(getApplication(), R.string.error_file_not_found);
				return;
			}
			Player.songs.addingStarted();
			BackgroundActivityMonitor.start(getHostActivity());
			(new Thread("Checked Radio Station Adder Thread") {
				@Override
				public void run() {
					InputStream is = null;
					InputStreamReader isr = null;
					BufferedReader br = null;
					HttpURLConnection urlConnection = null;
					try {
						if (Player.state >= Player.STATE_TERMINATING) {
							Player.songs.addingEnded();
							return;
						}
						urlConnection = (HttpURLConnection)(new URL(radioStation.m3uUri)).openConnection();
						final int s = urlConnection.getResponseCode();
						if (s == 200) {
							is = urlConnection.getInputStream();
							isr = new InputStreamReader(is, "UTF-8");
							br = new BufferedReader(isr, 1024);
							ArrayList<String> lines = new ArrayList<>(8);
							String line;
							while ((line = br.readLine()) != null) {
								line = line.trim();
								if (line.length() > 0 && line.charAt(0) != '#' &&
									(line.regionMatches(true, 0, "http://", 0, 7) ||
									line.regionMatches(true, 0, "https://", 0, 8)))
									lines.add(line);
							}
							if (Player.state >= Player.STATE_TERMINATING) {
								Player.songs.addingEnded();
								return;
							}
							if (lines.size() == 0) {
								Player.songs.addingEnded();
								MainHandler.toast(R.string.error_gen);
							} else {
								//instead of just using the first available address, let's use
								//one from the middle ;)
								Player.songs.addFiles(new FileSt[] { new FileSt(lines.get(lines.size() >> 1), radioStation.title, null, 0) }, null, 1, play, false, true);
							}
						} else {
							Player.songs.addingEnded();
							MainHandler.toast((s >= 400 && s < 500) ? R.string.error_file_not_found : R.string.error_gen);
						}
					} catch (Throwable ex) {
						Player.songs.addingEnded();
						MainHandler.toast(ex);
					} finally {
						try {
							if (urlConnection != null)
								urlConnection.disconnect();
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
						try {
							if (is != null)
								is.close();
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
						try {
							if (isr != null)
								isr.close();
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
						try {
							if (br != null)
								br.close();
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
						System.gc();
					}
				}
			}).start();
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
		if (panelLoading != null) {
			if (loadingPanelAnimationHide != null && !isCreatingLayout) {
				panelLoading.setVisibility(View.VISIBLE);
				loadingPanelAnimationHide.cancel();
				loadingPanelAnimationShow.cancel();
				panelLoading.startAnimation(started ? loadingPanelAnimationShow : loadingPanelAnimationHide);
				isHidingLoadingPanel = !started;
			} else {
				panelLoading.setVisibility(started ? View.VISIBLE : View.GONE);
			}
		}
		if (list != null) {
			list.setCustomEmptyText(started ? msgLoading : (isAtFavorites ? msgNoFavorites : msgNoStations));
			if (animation != null) {
				if (started) {
					list.setVisibility(View.INVISIBLE);
				} else if (list.getVisibility() != View.VISIBLE) {
					animation.cancel();
					list.setVisibility(View.VISIBLE);
					list.startAnimation(animation);
				}
			}
		}
		//if (!started)
		//	updateButtons();
	}
	
	@Override
	public View createView() {
		return new RadioStationView(Player.getService());
	}
	
	@Override
	public void processItemCheckboxClick(int position) {
		final RadioStation station = radioStationList.getItemT(position);
		if (station.isFavorite)
			radioStationList.addFavoriteStation(station);
		else
			radioStationList.removeFavoriteStation(station);
	}
	
	@Override
	public void processItemClick(int position) {
		//UI.doubleClickMode is ignored for radio stations!
		if (radioStationList.getSelection() == position)
			addPlaySelectedItem(true);
		else
			radioStationList.setSelection(position, true);
	}
	
	@Override
	public void processItemLongClick(int position) {
		if (radioStationList.getSelection() != position)
			radioStationList.setSelection(position, true);
	}
	
	private static int getValidGenre(int genre) {
		return ((genre < 0) ? 0 : ((genre >= RadioStationList.GENRES.length) ? (RadioStationList.GENRES.length - 1) : genre));
	}
	
	private static String getGenreString(int genre) {
		return RadioStationList.GENRES[getValidGenre(genre)];
	}
	
	private void doSearch() {
		final int selection = radioStationList.getSelection();
		if (Player.radioSearchTerm != null && Player.radioSearchTerm.length() < 1)
			Player.radioSearchTerm = null;
		if (Player.lastRadioSearchWasByGenre || Player.radioSearchTerm == null)
			radioStationList.fetchIcecast(getApplication(), getGenreString(Player.radioLastGenre), null);
		else
			radioStationList.fetchIcecast(getApplication(), null, Player.radioSearchTerm);
		//do not call updateButtons() if onSelectionChanged() got called before!
		if (selection < 0)
			updateButtons();
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView list, int keyCode) {
		int p;
		switch (keyCode) {
		case UI.KEY_LEFT:
			if (btnSearch.getVisibility() == View.VISIBLE)
				btnSearch.requestFocus();
			else
				btnGoBack.requestFocus();
			return true;
		case UI.KEY_RIGHT:
			btnGoBackToPlayer.requestFocus();
			return true;
		case UI.KEY_ENTER:
			if (radioStationList != null) {
				p = radioStationList.getSelection();
				if (p >= 0)
					processItemClick(p);
			}
			return true;
		case UI.KEY_EXTRA:
			if (radioStationList != null) {
				p = radioStationList.getSelection();
				if (p >= 0) {
					final RadioStation station = radioStationList.getItemT(p);
					station.isFavorite = !station.isFavorite;
					processItemCheckboxClick(p);
					if (list != null) {
						final RadioStationView view = (RadioStationView)list.getViewForPosition(p);
						if (view != null) {
							view.refreshItemFavoriteButton();
							break;
						}
					}
					radioStationList.notifyCheckedChanged();
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void onSelectionChanged(BaseList<RadioStation> list) {
		updateButtons();
	}

	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			if (isAtFavorites) {
				isAtFavorites = false;
				doSearch();
			} else {
				finish(0, view, true);
			}
		} else if (view == btnFavorite) {
			final int selection = radioStationList.getSelection();
			isAtFavorites = true;
			radioStationList.cancel();
			radioStationList.fetchFavorites(getApplication());
			//do not call updateButtons() if onSelectionChanged() got called before!
			if (selection < 0)
				updateButtons();
		} else if (view == btnSearch) {
			final Context ctx = getHostActivity();
			final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);
			
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			chkGenre = new RadioButton(ctx);
			chkGenre.setText(R.string.genre);
			chkGenre.setChecked(Player.lastRadioSearchWasByGenre);
			chkGenre.setOnClickListener(this);
			chkGenre.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
			chkGenre.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			btnGenre = new Spinner(ctx);
			btnGenre.setContentDescription(ctx.getText(R.string.genre));
			btnGenre.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin << 1;
			chkTerm = new RadioButton(ctx);
			chkTerm.setText(R.string.search_term);
			chkTerm.setChecked(!Player.lastRadioSearchWasByGenre);
			chkTerm.setOnClickListener(this);
			chkTerm.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
			chkTerm.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			txtTerm = new EditText(ctx);
			txtTerm.setContentDescription(ctx.getText(R.string.search_term));
			txtTerm.setText(Player.radioSearchTerm == null ? "" : Player.radioSearchTerm);
			txtTerm.setOnClickListener(this);
			txtTerm.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
			txtTerm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
			txtTerm.setSingleLine();
			txtTerm.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			p.bottomMargin = UI.dialogMargin;
			final TextView lbl = new TextView(ctx);
			lbl.setAutoLinkMask(0);
			lbl.setLinksClickable(true);
			//http://developer.android.com/design/style/color.html
			lbl.setLinkTextColor(new BgColorStateList(UI.isAndroidThemeLight() ? 0xff0099cc : 0xff33b5e5));
			lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._14sp);
			lbl.setGravity(Gravity.CENTER_HORIZONTAL);
			lbl.setText(SafeURLSpan.parseSafeHtml(getText(R.string.by_dir_xiph_org)));
			lbl.setMovementMethod(LinkMovementMethod.getInstance());
			lbl.setLayoutParams(p);
			
			l.addView(chkGenre);
			l.addView(btnGenre);
			l.addView(chkTerm);
			l.addView(txtTerm);
			l.addView(lbl);
			
			btnGenre.setAdapter(this);
			btnGenre.setSelection(getValidGenre(Player.radioLastGenre));
			defaultTextColors = txtTerm.getTextColors();
			
			UI.prepareDialogAndShow((new AlertDialog.Builder(ctx))
			.setTitle(getText(R.string.search))
			.setView(l)
			.setPositiveButton(R.string.search, this)
			.setNegativeButton(R.string.cancel, this)
			.setOnCancelListener(this)
			.create());
		} else if (view == btnGoBackToPlayer) {
			finish(-1, view, false);
		} else if (view == btnAdd) {
			addPlaySelectedItem(false);
		} else if (view == btnPlay) {
			addPlaySelectedItem(true);
		} else if (view == chkGenre || view == btnGenre) {
			chkGenre.setChecked(true);
			chkTerm.setChecked(false);
		} else if (view == chkTerm || view == txtTerm) {
			chkGenre.setChecked(false);
			chkTerm.setChecked(true);
		} else if (view == list) {
			if (!isAtFavorites && !loading && (radioStationList == null || radioStationList.getCount() == 0))
				onClick(btnFavorite);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE) {
			Player.lastRadioSearchWasByGenre = chkGenre.isChecked();
			Player.radioLastGenre = btnGenre.getSelectedItemPosition();
			Player.radioSearchTerm = txtTerm.getText().toString().trim();
			doSearch();
		}
		chkGenre = null;
		btnGenre = null;
		chkTerm = null;
		txtTerm = null;
		defaultTextColors = null;
	}
	
	@Override
	public void onCancel(DialogInterface dialog) {
		onClick(dialog, AlertDialog.BUTTON_NEGATIVE);
	}
	
	@Override
	protected boolean onBackPressed() {
		if (UI.backKeyAlwaysReturnsToPlayerWhenBrowsing) {
			finish(-1, null, false);
			return true;
		}
		if (!isAtFavorites)
			return false;
		onClick(btnGoBack);
		return true;
	}
	
	@Override
	protected void onCreate() {
		UI.browserActivity = this;
		radioStationList = new RadioStationList(getText(R.string.tags).toString(), "-", getText(R.string.no_description).toString(), getText(R.string.no_tags).toString());
		radioStationList.setOnBaseListSelectionChangedListener(this);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_browser_radio);
		UI.smallTextAndColor((TextView)findViewById(R.id.lblLoadingSmall));
		msgLoading = getText(R.string.loading);
		msgNoFavorites = getText(R.string.no_favorites);
		msgNoStations = getText(R.string.no_stations);
		list = (BgListView)findViewById(R.id.list);
		list.setOnKeyDownObserver(this);
		list.setScrollBarType((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
		list.setCustomEmptyText(msgLoading);
		list.setEmptyListOnClickListener(this);
		if (UI.animationEnabled) {
			(loadingPanelAnimationHide = UI.animationCreateAlpha(1.0f, 0.0f)).setAnimationListener(this);
			loadingPanelAnimationShow = UI.animationCreateAlpha(0.0f, 1.0f);
			radioStationList.radioStationAddedObserver = this;
			((View)list.getParent()).setBackgroundDrawable(new ColorDrawable(UI.color_list));
			animation = UI.animationCreateAlpha(0.0f, 1.0f);
			final TextView lblLoading = (TextView)findViewById(R.id.lblLoading);
			lblLoading.setTextColor(UI.color_text_disabled);
			UI.largeText(lblLoading);
			lblLoading.setVisibility(View.VISIBLE);
		}
		radioStationList.setObserver(list);
		panelLoading = (RelativeLayout)findViewById(R.id.panelLoading);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnFavorite = (BgButton)findViewById(R.id.btnFavorite);
		btnFavorite.setOnClickListener(this);
		btnFavorite.setIcon(UI.ICON_FAVORITE_ON);
		btnSearch = (BgButton)findViewById(R.id.btnSearch);
		btnSearch.setOnClickListener(this);
		btnSearch.setIcon(UI.ICON_SEARCH);
		panelSecondary = (RelativeLayout)findViewById(R.id.panelSecondary);
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
		RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
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
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		UI.prepareControlContainer(panelSecondary, true, false);
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
		UI.prepareEdgeEffectColor(getApplication());
		isCreatingLayout = true;
		doSearch();
		isCreatingLayout = false;
	}
	
	@Override
	protected void onPause() {
		radioStationList.saveFavorites(getApplication());
		radioStationList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		UI.browserActivity = this;
		radioStationList.setObserver(list);
		if (loading != radioStationList.isLoading())
			loadingProcessChanged(radioStationList.isLoading());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (UI.isLargeScreen && list != null)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		if (animation != null) {
			animation.cancel();
			animation = null;
		}
		if (loadingPanelAnimationHide != null) {
			loadingPanelAnimationHide.cancel();
			loadingPanelAnimationHide = null;
		}
		if (loadingPanelAnimationShow != null) {
			loadingPanelAnimationShow.cancel();
			loadingPanelAnimationShow = null;
		}
		list = null;
		panelLoading = null;
		panelSecondary = null;
		btnGoBack = null;
		btnFavorite = null;
		btnSearch = null;
		btnGoBackToPlayer = null;
		btnAdd = null;
		sep2 = null;
		btnPlay = null;
		msgNoFavorites = null;
		msgNoStations = null;
		msgLoading = null;
	}
	
	@Override
	protected void onDestroy() {
		UI.browserActivity = null;
		if (radioStationList != null) {
			radioStationList.cancel();
			radioStationList.setOnBaseListSelectionChangedListener(null);
			radioStationList.radioStationAddedObserver = null;
			radioStationList = null;
		}
	}
	
	@Override
	public int getCount() {
		return RadioStationList.GENRES.length;
	}

	@Override
	public Object getItem(int position) {
		return getGenreString(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		TextView txt = (TextView)convertView;
		if (txt == null) {
			txt = new TextView(getApplication());
			txt.setPadding(UI.dialogMargin, UI.dialogMargin, UI.dialogMargin, UI.dialogMargin);
			txt.setTypeface(UI.defaultTypeface);
			txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
			txt.setTextColor(defaultTextColors);
		}
		txt.setText(getGenreString(position));
		return txt;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		TextView txt = (TextView)convertView;
		if (txt == null) {
			txt = new TextView(getApplication());
			txt.setPadding(UI.dialogMargin, UI.dialogDropDownVerticalMargin, UI.dialogMargin, UI.dialogDropDownVerticalMargin);
			txt.setTypeface(UI.defaultTypeface);
			txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
			txt.setTextColor(defaultTextColors);
		}
		txt.setText(getGenreString(position));
		return txt;
	}

	@Override
	public void onRadioStationAdded() {
		if (list != null && animation != null && list.getVisibility() != View.VISIBLE) {
			animation.cancel();
			list.setVisibility(View.VISIBLE);
			list.startAnimation(animation);
		}
	}

	@Override
	public void onAnimationStart(Animation animation) {

	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if (isHidingLoadingPanel && panelLoading != null) {
			isHidingLoadingPanel = false;
			panelLoading.setVisibility(View.GONE);
		}
	}

	@Override
	public void onAnimationRepeat(Animation animation) {

	}
}
