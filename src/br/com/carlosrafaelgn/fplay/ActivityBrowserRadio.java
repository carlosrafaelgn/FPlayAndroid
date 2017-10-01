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
import android.net.Uri;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.BaseList;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.IcecastRadioStationList;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.list.RadioStationGenre;
import br.com.carlosrafaelgn.fplay.list.RadioStationList;
import br.com.carlosrafaelgn.fplay.list.ShoutcastRadioStationList;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.playback.RadioStationResolver;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgDialog;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.BgSpinner;
import br.com.carlosrafaelgn.fplay.ui.FastAnimator;
import br.com.carlosrafaelgn.fplay.ui.RadioStationView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.SafeURLSpan;

public final class ActivityBrowserRadio extends ClientActivity implements View.OnClickListener, DialogInterface.OnClickListener, DialogInterface.OnCancelListener, DialogInterface.OnDismissListener, RadioStationList.ItemClickListener, RadioStationList.ActionListener, RadioStationList.SelectionChangedListener<RadioStation>, BgListView.OnBgListViewKeyDownObserver, RadioStationList.RadioStationAddedObserver, FastAnimator.Observer, BgListView.OnScrollListener, BgSpinner.OnItemSelectedListener<RadioStationGenre> {
	private final boolean useShoutcast;
	private Uri externalUri;
	private SpannableStringBuilder message;
	private TextView sep2, lblLoading;
	private BgListView list;
	private RadioStationGenre[] genres;
	private RadioStationList radioStationList;
	private RelativeLayout panelSecondary, panelLoading;
	private BgSpinner<RadioStationGenre> btnType, btnGenre, btnGenreSecondary;
	private EditText txtTerm;
	private BgButton btnGoBack, btnFavorite, btnSearch, btnGoBackToPlayer, btnAdd, btnPlay;
	private boolean loading, isAtFavorites, isHidingLoadingPanel, animateListBox;
	private FastAnimator animatorFadeIn, animatorFadeOut, loadingPanelAnimatorHide, loadingPanelAnimatorShow;
	private CharSequence msgNoFavorites, msgNoStations, msgLoading;

	public ActivityBrowserRadio(boolean useShoutcast) {
		this.useShoutcast = useShoutcast;
	}

	@Override
	public CharSequence getTitle() {
		return getText(R.string.radio);
	}

	private void updateButtons() {
		UI.animationReset();
		if (isAtFavorites == (btnFavorite.getVisibility() == View.VISIBLE)) {
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
		UI.animationCommit(false, null);
	}
	
	private void addPlaySelectedItem(final boolean play) {
		if (radioStationList.getSelection() < 0)
			return;
		final RadioStation radioStation = radioStationList.getItem(radioStationList.getSelection());
		if (radioStation.m3uUrl == null || radioStation.m3uUrl.length() < 0) {
			UI.toast(R.string.error_file_not_found);
			return;
		}
		Player.songs.addingStarted();
		getHostActivity().bgMonitorStart();
		try {
			(new Thread("Checked Radio Station Adder Thread") {
				@Override
				public void run() {
					try {
						if (Player.state >= Player.STATE_TERMINATING)
							return;
						final int[] resultCode = { 0 };
						final String streamUrl = RadioStationResolver.resolveStreamUrlFromM3uUrl(radioStation.m3uUrl, resultCode);
						if (Player.state >= Player.STATE_TERMINATING)
							return;
						if (streamUrl == null)
							MainHandler.toast(((resultCode[0] >= 300 && resultCode[0] < 500) || resultCode[0] == 0) ? R.string.error_file_not_found : (resultCode[0] < 0 ? R.string.error_io : R.string.error_gen));
						else
							Player.songs.addFiles(new FileSt[]{new FileSt(radioStation.buildFullPath(streamUrl), radioStation.title, null, 0)}, null, 1, play, false, true, false);
					} finally {
						Player.songs.addingEnded();
					}
				}
			}).start();
		} catch (Throwable ex) {
			Player.songs.addingEnded();
			UI.toast(ex.getMessage());
		}
	}

	@Override
	public void onItemClicked(int position) {
		if (!isLayoutCreated())
			return;
		//UI.doubleClickMode is ignored for radio stations!
		if (radioStationList.getSelection() == position)
			addPlaySelectedItem(true);
		else
			radioStationList.setSelection(position, true);
	}
	
	@Override
	public void onItemLongClicked(int position) {
		if (!isLayoutCreated())
			return;
		if (radioStationList.getSelection() != position)
			radioStationList.setSelection(position, true);
		if (UI.playWithLongPress)
			addPlaySelectedItem(true);
	}

	@Override
	public void onItemCheckboxClicked(int position) {
		if (!isLayoutCreated())
			return;
		final RadioStation station = radioStationList.getItem(position);
		if (station.isFavorite)
			radioStationList.addFavoriteStation(station);
		else
			radioStationList.removeFavoriteStation(station);
	}

	@Override
	public void onLoadingProcessChanged(boolean started) {
		if (!isLayoutCreated() || radioStationList == null)
			return;
		loading = started;
		if (panelLoading != null) {
			if (loadingPanelAnimatorHide != null) {
				loadingPanelAnimatorHide.end();
				loadingPanelAnimatorShow.end();
				panelLoading.setVisibility(View.VISIBLE);
				(started ? loadingPanelAnimatorShow : loadingPanelAnimatorHide).start();
				isHidingLoadingPanel = !started;
			} else {
				panelLoading.setVisibility(started ? View.VISIBLE : View.GONE);
			}
		}
		if (list != null) {
			list.setCustomEmptyText(started ? msgLoading : (isAtFavorites ? msgNoFavorites : msgNoStations));
			if (animatorFadeIn != null && animateListBox) {
				lblLoading.setVisibility(View.VISIBLE);
				if (started) {
					//when the animation ends, list is made visible...
					//that's why we set the visibility after calling end()
					animatorFadeOut.end();
					list.setVisibility(View.INVISIBLE);
				} else if (list.getVisibility() != View.VISIBLE) {
					list.setVisibility(View.VISIBLE);
					animatorFadeIn.start();
				}
			}
		}
		//if (!started)
		//	updateButtons();
	}

	@Override
	public void onSelectionChanged(BaseList<RadioStation> list) {
		if (!isLayoutCreated())
			return;
		updateButtons();
	}

	private int validateGenreIndex(int index) {
		if (genres == null)
			return 0;
		int parent = index & 0xffff;
		if (parent >= genres.length)
			parent = genres.length - 1;
		if (index <= 0xffff)
			return parent;
		final RadioStationGenre genre = genres[parent];
		if (genre.children == null || genre.children.length == 0)
			return parent;
		int child = (index >>> 16);
		if (child >= genre.children.length)
			child = genre.children.length - 1;
		return parent | (child << 16);
	}

	private int getPrimaryGenreIndex() {
		if (genres == null)
			return -1;
		final int index = (useShoutcast ? Player.radioLastGenreShoutcast : Player.radioLastGenre);
		final int parent = index & 0xffff;
		return ((parent >= genres.length) ? (genres.length - 1) : parent);
	}

	private int getSecondaryGenreIndex() {
		if (genres == null)
			return -1;
		int index = (useShoutcast ? Player.radioLastGenreShoutcast : Player.radioLastGenre);
		final int parent = index & 0xffff;
		final RadioStationGenre genre = genres[(parent >= genres.length) ? (genres.length - 1) : parent];
		if (index <= 0xffff || genre.children == null || genre.children.length == 0)
			return 0;
		index = (index >>> 16);
		return ((index >= genre.children.length) ? (genre.children.length - 1) : index);
	}

	private RadioStationGenre getGenre() {
		if (genres == null)
			return null;
		int index = (useShoutcast ? Player.radioLastGenreShoutcast : Player.radioLastGenre);
		final int parent = index & 0xffff;
		final RadioStationGenre genre = genres[(parent >= genres.length) ? (genres.length - 1) : parent];
		if (index <= 0xffff || genre.children == null || genre.children.length == 0)
			return genre;
		index = (index >>> 16);
		return genre.children[(index >= genre.children.length) ? (genre.children.length - 1) : index];
	}

	private void restoreCacheOrDoSearch() {
		if (radioStationList.restoreCacheIfValid()) {
			animateListBox = true;
			onLoadingProcessChanged(true);
			updateButtons();
			onLoadingProcessChanged(false);
		} else {
			doSearch(true);
		}
	}

	private void doSearch(boolean firstSearch) {
		if (animatorFadeIn != null && (animatorFadeIn.isRunning() || animatorFadeOut.isRunning()))
			return;
		animateListBox = firstSearch;
		final int selection = radioStationList.getSelection();
		if (Player.radioSearchTerm != null) {
			Player.radioSearchTerm = Player.radioSearchTerm.trim();
			if (Player.radioSearchTerm.length() < 1)
				Player.radioSearchTerm = null;
		}
		if (Player.lastRadioSearchWasByGenre || Player.radioSearchTerm == null)
			radioStationList.fetchStations(getGenre(), null, firstSearch);
		else
			radioStationList.fetchStations(null, Player.radioSearchTerm, firstSearch);
		//do not call updateButtons() if onSelectionChanged() got called before!
		if (firstSearch && selection < 0)
			updateButtons();
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView list, int keyCode) {
		if (!isLayoutCreated())
			return true;
		int p;
		switch (keyCode) {
		case UI.KEY_LEFT:
			if (btnSearch != null && btnGoBack != null)
				((btnSearch.getVisibility() == View.VISIBLE) ? btnSearch : btnGoBack).requestFocus();
			return true;
		case UI.KEY_RIGHT:
			if (btnGoBackToPlayer != null)
				btnGoBackToPlayer.requestFocus();
			return true;
		case UI.KEY_ENTER:
			if (radioStationList != null) {
				p = radioStationList.getSelection();
				if (p >= 0)
					onItemClicked(p);
			}
			return true;
		case UI.KEY_EXTRA:
			if (radioStationList != null) {
				p = radioStationList.getSelection();
				if (p >= 0) {
					final RadioStation station = radioStationList.getItem(p);
					station.isFavorite = !station.isFavorite;
					onItemCheckboxClicked(p);
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
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (!isAtFavorites && totalItemCount > 0 && !loading && radioStationList != null && radioStationList.hasMoreResults() && (visibleItemCount >= totalItemCount || (firstVisibleItem + visibleItemCount) >= (totalItemCount - 5)))
			doSearch(false);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
	}

	@Override
	public void onClick(View view) {
		if (!isLayoutCreated())
			return;
		if (view == btnGoBack) {
			if (isAtFavorites) {
				isAtFavorites = false;
				if (animatorFadeOut != null) {
					lblLoading.setVisibility(View.VISIBLE);
					animatorFadeOut.start();
				} else {
					restoreCacheOrDoSearch();
				}
			} else {
				finish(0, view, true);
			}
		} else if (view == btnFavorite) {
			final int selection = radioStationList.getSelection();
			isAtFavorites = true;
			radioStationList.cancel();
			if (animatorFadeOut != null) {
				lblLoading.setVisibility(View.VISIBLE);
				animatorFadeOut.start();
			} else {
				radioStationList.fetchFavorites();
			}
			//do not call updateButtons() if onSelectionChanged() got called before!
			if (selection < 0)
				updateButtons();
		} else if (view == btnSearch) {
			final Context ctx = getHostActivity();
			final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);
			
			LinearLayout.LayoutParams p;

			btnType = new BgSpinner<>(ctx);
			l.addView(btnType, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

			btnGenre = new BgSpinner<>(ctx);
			btnGenre.setContentDescription(ctx.getText(R.string.genre));
			btnGenre.setVisibility(Player.lastRadioSearchWasByGenre ? View.VISIBLE : View.GONE);
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			l.addView(btnGenre, p);

			if (useShoutcast) {
				btnGenreSecondary = new BgSpinner<>(ctx);
				btnGenreSecondary.setContentDescription(ctx.getText(R.string.genre));
				btnGenreSecondary.setVisibility(Player.lastRadioSearchWasByGenre ? View.VISIBLE : View.GONE);
				p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				p.topMargin = UI.dialogMargin;
				l.addView(btnGenreSecondary, p);
			} else {
				btnGenreSecondary = null;
			}

			txtTerm = UI.createDialogEditText(ctx, 0, Player.radioSearchTerm == null ? "" : Player.radioSearchTerm, ctx.getText(R.string.search_term), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
			txtTerm.setOnClickListener(this);
			txtTerm.setVisibility(!Player.lastRadioSearchWasByGenre ? View.VISIBLE : View.GONE);
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			l.addView(txtTerm, p);

			final TextView lbl = UI.createDialogTextView(ctx, 0, null);
			lbl.setSingleLine(false);
			lbl.setMaxLines(4);
			lbl.setAutoLinkMask(0);
			lbl.setLinksClickable(true);
			lbl.setLinkTextColor(UI.colorState_text_listitem_secondary_static);
			lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._14sp);
			lbl.setGravity(Gravity.CENTER_HORIZONTAL);
			if (externalUri == null) {
				final String providedBy = getText(R.string.provided_by).toString(), msg, iconA, iconB;
				final int w;
				if (useShoutcast) {
					externalUri = Uri.parse("http://shoutcast.com");
					msg = "<br/>A B <small>(<a href=\"http://shoutcast.com\">shoutcast.com</a>)</small>";
					w = (int)((UI._18sp << 4) / 2.279f);
					iconA = UI.ICON_SHOUTCAST;
					iconB = UI.ICON_SHOUTCASTTEXT;
				} else {
					externalUri = Uri.parse("http://dir.xiph.org");
					msg = "<br/>A B <small>(<a href=\"http://dir.xiph.org\">dir.xiph.org</a>)</small>";
					w = (int)((UI._18sp << 4) / 3.587f);
					iconA = UI.ICON_ICECAST;
					iconB = UI.ICON_ICECASTTEXT;
				}
				message = new SpannableStringBuilder(SafeURLSpan.parseSafeHtml(providedBy + msg));
				message.setSpan(new ImageSpan(new TextIconDrawable(iconA, lbl.getTextColors().getDefaultColor(), UI.spToPxI(22), 0), ImageSpan.ALIGN_BASELINE), providedBy.length() + 1, providedBy.length() + 2, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
				message.setSpan(new ImageSpan(new TextIconDrawable(iconB, lbl.getTextColors().getDefaultColor(), w, UI._18sp, w), ImageSpan.ALIGN_BASELINE), providedBy.length() + 3, providedBy.length() + 4, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
			}
			lbl.setText(message);
			lbl.setMovementMethod(LinkMovementMethod.getInstance());
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI.dialogMargin;
			l.addView(lbl, p);

			btnType.setItems(new RadioStationGenre[]{
				new RadioStationGenre(getText(R.string.genre).toString()),
				new RadioStationGenre(getText(R.string.search_term).toString())
			});
			btnType.setSelectedItemPosition(Player.lastRadioSearchWasByGenre ? 0 : 1);
			btnType.setOnItemSelectedListener(this);

			final int primaryGenreIndex = getPrimaryGenreIndex();
			btnGenre.setItems(genres);
			btnGenre.setSelectedItemPosition(primaryGenreIndex);
			if (btnGenreSecondary != null) {
				btnGenre.setOnItemSelectedListener(this);
				btnGenreSecondary.setItems(genres[primaryGenreIndex].children);
				btnGenreSecondary.setSelectedItemPosition(getSecondaryGenreIndex());
			}

			UI.disableEdgeEffect(getHostActivity());
			final BgDialog dialog = new BgDialog(ctx, l, this);
			dialog.setTitle(R.string.search);
			dialog.setPositiveButton(R.string.search);
			dialog.setNegativeButton(R.string.cancel);
			dialog.setOnCancelListener(this);
			dialog.setOnDismissListener(this);
			dialog.show();
		} else if (view == btnGoBackToPlayer) {
			finish(-1, view, false);
		} else if (view == btnAdd) {
			addPlaySelectedItem(false);
		} else if (view == btnPlay) {
			addPlaySelectedItem(true);
		} else if (view == list) {
			if (!isAtFavorites && !loading && (radioStationList == null || radioStationList.getCount() == 0))
				onClick(btnFavorite);
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (!isLayoutCreated())
			return;
		if (which == AlertDialog.BUTTON_POSITIVE) {
			if (btnType != null)
				Player.lastRadioSearchWasByGenre = (btnType.getSelectedItemPosition() == 0);
			if (btnGenre != null) {
				if (useShoutcast) {
					Player.radioLastGenreShoutcast = btnGenre.getSelectedItemPosition();
					if (btnGenreSecondary != null)
						Player.radioLastGenreShoutcast |= (btnGenreSecondary.getSelectedItemPosition() << 16);
				} else {
					Player.radioLastGenre = btnGenre.getSelectedItemPosition();
				}
			}
			if (txtTerm != null)
				Player.radioSearchTerm = txtTerm.getText().toString();
			doSearch(true);
		}
		btnType = null;
		btnGenre = null;
		btnGenreSecondary = null;
		txtTerm = null;
		dialog.dismiss();
	}
	
	@Override
	public void onCancel(DialogInterface dialog) {
		onClick(dialog, AlertDialog.BUTTON_NEGATIVE);
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		UI.reenableEdgeEffect(getHostActivity());
	}

	@Override
	protected boolean onBackPressed() {
		if (!isLayoutCreated())
			return true;
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
		radioStationList = (useShoutcast ? new ShoutcastRadioStationList(getText(R.string.tags).toString(), getText(R.string.listeners).toString(), "-", getText(R.string.no_description).toString()) : new IcecastRadioStationList(getText(R.string.tags).toString(), "-", getText(R.string.no_description).toString(), getText(R.string.no_tags).toString()));
		radioStationList.setItemClickListener(this);
		radioStationList.setActionListener(this);
		radioStationList.setSelectionChangedListener(this);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_browser_radio);
		UI.smallTextAndColor((TextView)findViewById(R.id.lblLoadingSmall));
		msgLoading = getText(R.string.loading);
		msgNoFavorites = getText(R.string.no_favorites);
		msgNoStations = getText(R.string.no_stations);
		list = findViewById(R.id.list);
		list.setOnKeyDownObserver(this);
		list.setScrollBarType((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
		list.setCustomEmptyText(msgLoading);
		list.setEmptyListOnClickListener(this);
		list.setOnScrollListener(this);
		panelLoading = findViewById(R.id.panelLoading);
		if (UI.animationEnabled) {
			list.setVisibility(View.INVISIBLE);
			loadingPanelAnimatorHide = new FastAnimator(panelLoading, true, this, 0);
			loadingPanelAnimatorShow = new FastAnimator(panelLoading, false, null, 0);
			radioStationList.radioStationAddedObserver = this;
			animatorFadeIn = new FastAnimator(list, false, this, 0);
			animatorFadeOut = new FastAnimator(list, true, this, 0);
			lblLoading = findViewById(R.id.lblLoading);
			lblLoading.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
			lblLoading.setTextColor(UI.color_text_listitem_disabled);
			UI.headingText(lblLoading);
			lblLoading.setVisibility(View.VISIBLE);
		} else if (firstCreation) {
			list.setCustomEmptyText(msgLoading);
		}
		radioStationList.setObserver(list);
		btnGoBack = findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnFavorite = findViewById(R.id.btnFavorite);
		btnFavorite.setOnClickListener(this);
		btnFavorite.setIcon(UI.ICON_FAVORITE_ON);
		btnSearch = findViewById(R.id.btnSearch);
		btnSearch.setOnClickListener(this);
		btnSearch.setIcon(UI.ICON_SEARCH);
		panelSecondary = findViewById(R.id.panelSecondary);
		btnGoBackToPlayer = findViewById(R.id.btnGoBackToPlayer);
		btnGoBackToPlayer.setTextColor(UI.colorState_text_reactive);
		btnGoBackToPlayer.setOnClickListener(this);
		btnGoBackToPlayer.setCompoundDrawables(new TextIconDrawable(UI.ICON_LIST24, UI.color_text), null, null, null);
		btnGoBackToPlayer.setDefaultHeight();
		btnAdd = findViewById(R.id.btnAdd);
		btnAdd.setTextColor(UI.colorState_text_reactive);
		btnAdd.setOnClickListener(this);
		btnAdd.setIcon(UI.ICON_ADD);
		sep2 = findViewById(R.id.sep2);
		RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
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
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		UI.prepareControlContainer(panelSecondary, true, false);
		UI.prepareViewPaddingBasedOnScreenWidth(list, 0, 0, 0);
	}

	@Override
	protected void onPostCreateLayout(boolean firstCreation) {
		genres = RadioStationGenre.loadGenres(getHostActivity(), useShoutcast);
		if (genres == null)
			genres = new RadioStationGenre[] { new RadioStationGenre() };
		if (useShoutcast)
			Player.radioLastGenreShoutcast = validateGenreIndex(Player.radioLastGenreShoutcast);
		else
			Player.radioLastGenre = validateGenreIndex(Player.radioLastGenre);

		restoreCacheOrDoSearch();
	}

	@Override
	protected void onPause() {
		radioStationList.saveFavorites();
		radioStationList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		radioStationList.setObserver(list);
		if (loading != radioStationList.isLoading())
			onLoadingProcessChanged(radioStationList.isLoading());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (list != null)
			UI.prepareViewPaddingBasedOnScreenWidth(list, 0, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		if (animatorFadeIn != null) {
			animatorFadeIn.release();
			animatorFadeIn = null;
		}
		if (animatorFadeOut != null) {
			animatorFadeOut.release();
			animatorFadeOut = null;
		}
		if (loadingPanelAnimatorHide != null) {
			loadingPanelAnimatorHide.release();
			loadingPanelAnimatorHide = null;
		}
		if (loadingPanelAnimatorShow != null) {
			loadingPanelAnimatorShow.release();
			loadingPanelAnimatorShow = null;
		}
		list = null;
		genres = null;
		panelLoading = null;
		panelSecondary = null;
		btnGoBack = null;
		btnFavorite = null;
		btnSearch = null;
		btnGoBackToPlayer = null;
		btnAdd = null;
		sep2 = null;
		lblLoading = null;
		btnPlay = null;
		msgNoFavorites = null;
		msgNoStations = null;
		msgLoading = null;
	}
	
	@Override
	protected void onDestroy() {
		if (radioStationList != null) {
			radioStationList.setItemClickListener(null);
			radioStationList.setActionListener(null);
			radioStationList.setSelectionChangedListener(null);
			radioStationList.cancel();
			radioStationList.radioStationAddedObserver = null;
			radioStationList = null;
		}
	}

	@Override
	public void onRadioStationAdded() {
		if (list != null && animatorFadeIn != null && list.getVisibility() != View.VISIBLE) {
			animatorFadeIn.end();
			list.setVisibility(View.VISIBLE);
			animatorFadeIn.start();
		}
	}

	@Override
	public void onEnd(FastAnimator animator) {
		if (animator == this.animatorFadeIn) {
			if (lblLoading != null)
				lblLoading.setVisibility(View.GONE);
		} else if (animator == animatorFadeOut) {
			if (list != null && radioStationList != null) {
				list.setVisibility(View.INVISIBLE);
				animateListBox = true;
				if (isAtFavorites)
					radioStationList.fetchFavorites();
				else
					restoreCacheOrDoSearch();
			}
		} else {
			if (isHidingLoadingPanel && panelLoading != null) {
				isHidingLoadingPanel = false;
				panelLoading.setVisibility(View.GONE);
			}
		}
	}

	@Override
	public void onItemSelected(BgSpinner<RadioStationGenre> spinner, int position) {
		if (!isLayoutCreated())
			return;
		if (spinner == btnGenre) {
			if (btnGenre != null && btnGenreSecondary != null && genres != null && position >= 0 && position < genres.length) {
				btnGenreSecondary.setSelectedItemPosition(0);
				btnGenreSecondary.setItems(genres[position].children);
			}
		} else if (spinner == btnType) {
			if (btnGenre != null && txtTerm != null) {
				if (position == 0) {
					txtTerm.setVisibility(View.GONE);
					btnGenre.setVisibility(View.VISIBLE);
					if (btnGenreSecondary != null)
						btnGenreSecondary.setVisibility(View.VISIBLE);
				} else {
					btnGenre.setVisibility(View.GONE);
					if (btnGenreSecondary != null)
						btnGenreSecondary.setVisibility(View.GONE);
					txtTerm.setVisibility(View.VISIBLE);
					txtTerm.requestFocus();
				}
			}
		}
	}
}
