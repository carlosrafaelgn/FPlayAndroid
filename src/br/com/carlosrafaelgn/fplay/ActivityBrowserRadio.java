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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.net.http.AndroidHttpClient;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.list.FileSt;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.list.RadioStationList;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.RadioStationView;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivityBrowserRadio extends ActivityBrowserView implements View.OnClickListener, DialogInterface.OnClickListener, DialogInterface.OnCancelListener, BgListView.OnBgListViewKeyDownObserver, SpinnerAdapter {
	private TextView sep2;
	private BgListView list;
	private RadioStationList radioStationList;
	private RelativeLayout panelSecondary, panelLoading;
	private RadioButton chkGenre, chkTerm;
	private ColorStateList defaultTextColors;
	private Spinner btnGenre;
	private EditText txtTerm;
	private BgButton btnGoBack, btnFavorite, btnSearch, btnGoBackToPlayer, btnAdd, btnPlay;
	private boolean loading, isAtFavorites;
	
	private void updateButtons() {
		if (!isAtFavorites != (btnFavorite.getVisibility() == View.VISIBLE)) {
			if (isAtFavorites) {
				btnFavorite.setVisibility(View.GONE);
				btnSearch.setVisibility(View.GONE);
				btnGoBack.setNextFocusRightId(R.id.list);
				UI.setNextFocusForwardId(btnGoBack, R.id.list);
			} else {
				btnFavorite.setVisibility(View.VISIBLE);
				btnSearch.setVisibility(View.VISIBLE);
				btnGoBack.setNextFocusRightId(R.id.btnFavorite);
				UI.setNextFocusForwardId(btnGoBack, R.id.btnFavorite);
			}
		}
		final int s = radioStationList.getSelection();
		if ((s >= 0) != (btnAdd.getVisibility() == View.VISIBLE)) {
			if (s >= 0) {
				btnAdd.setVisibility(View.VISIBLE);
				sep2.setVisibility(View.VISIBLE);
				btnPlay.setVisibility(View.VISIBLE);
				btnGoBack.setNextFocusLeftId(R.id.btnPlay);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnAdd);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnAdd);
			} else {
				btnAdd.setVisibility(View.GONE);
				sep2.setVisibility(View.GONE);
				btnPlay.setVisibility(View.GONE);
				btnGoBack.setNextFocusLeftId(R.id.btnGoBackToPlayer);
				btnGoBackToPlayer.setNextFocusRightId(R.id.btnGoBack);
				UI.setNextFocusForwardId(btnGoBackToPlayer, R.id.btnGoBack);
			}
		}
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
			SongAddingMonitor.start(getHostActivity());
			(new Thread("Checked Radio Station Adder Thread") {
				@Override
				public void run() {
					AndroidHttpClient client = null;
					HttpResponse response = null;
					InputStream is = null;
					InputStreamReader isr = null;
					BufferedReader br = null;
					try {
						if (Player.getState() == Player.STATE_TERMINATED || Player.getState() == Player.STATE_TERMINATING) {
							Player.songs.addingEnded();
							return;
						}
						client = AndroidHttpClient.newInstance("Mozilla/5.0 (X11; U; Linux i686; en-US) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36 Debian");
						response = client.execute(new HttpGet(radioStation.m3uUri));
						final StatusLine statusLine = response.getStatusLine();
						final int s = statusLine.getStatusCode();
						if (s == HttpStatus.SC_OK) {
							is = response.getEntity().getContent();
							isr = new InputStreamReader(is, "UTF-8");
							br = new BufferedReader(isr, 1024);
							ArrayList<String> lines = new ArrayList<String>(8);
							String line;
							while ((line = br.readLine()) != null) {
								line = line.trim();
								if (line.length() > 0 && line.charAt(0) != '#' &&
									(line.regionMatches(true, 0, "http://", 0, 7) ||
									line.regionMatches(true, 0, "https://", 0, 8)))
									lines.add(line);
							}
							if (Player.getState() == Player.STATE_TERMINATED || Player.getState() == Player.STATE_TERMINATING) {
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
							if (client != null)
								client.close();
						} catch (Throwable ex) {
						}
						try {
							if (is != null)
								is.close();
						} catch (Throwable ex) {
						}
						try {
							if (isr != null)
								isr.close();
						} catch (Throwable ex) {
						}
						try {
							if (br != null)
								br.close();
						} catch (Throwable ex) {
						}
						br = null;
						isr = null;
						is = null;
						client = null;
						response = null;
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
		if (panelLoading != null)
			panelLoading.setVisibility(started ? View.VISIBLE : View.GONE);
		if (!started)
			updateButtons();
	}
	
	@Override
	public View createView() {
		return new RadioStationView(Player.getService());
	}
	
	@Override
	public void processItemButtonClick(int position, boolean add) {
		//add/remove favorite!!!
	}
	
	@Override
	public void processItemClick(int position) {
		//UI.doubleClickMode is ignored for radio stations!
		if (radioStationList.getSelection() == position) {
			addPlaySelectedItem(true);
		} else {
			radioStationList.setSelection(position, true);
			updateButtons();
		}
	}
	
	@Override
	public void processItemLongClick(int position) {
		if (radioStationList.getSelection() != position) {
			radioStationList.setSelection(position, true);
			updateButtons();
		}
	}
	
	private static int getValidGenre(int genre) {
		return ((genre < 0) ? 0 : ((genre >= RadioStationList.GENRES.length) ? (RadioStationList.GENRES.length - 1) : genre));
	}
	
	private static String getGenreString(int genre) {
		return RadioStationList.GENRES[getValidGenre(genre)];
	}
	
	private void doSearch() {
		if (Player.radioSearchTerm != null && Player.radioSearchTerm.length() < 1)
			Player.radioSearchTerm = null;
		if (Player.lastRadioSearchWasByGenre || Player.radioSearchTerm == null)
			radioStationList.fetchIcecast(getGenreString(Player.radioLastGenre), null);
		else
			radioStationList.fetchIcecast(null, Player.radioSearchTerm);
	}
	
	@Override
	public boolean onBgListViewKeyDown(BgListView bgListView, int keyCode, KeyEvent event) {
		int p;
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
			if (btnSearch.getVisibility() == View.VISIBLE)
				btnSearch.requestFocus();
			else
				btnGoBack.requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			btnGoBackToPlayer.requestFocus();
			return true;
		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_0:
		case KeyEvent.KEYCODE_SPACE:
			p = radioStationList.getSelection();
			if (p >= 0)
				processItemClick(p);
			return true;
		}
		return false;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			if (isAtFavorites) {
			} else {
				finish();
			}
		} else if (view == btnFavorite) {
			//radioStationList.cancel();
			////fetch favorites
			//isAtFavorites = true;
		} else if (view == btnSearch) {
			final Context ctx = getHostActivity();
			final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);
			
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			chkGenre = new RadioButton(ctx);
			chkGenre.setText(R.string.genre);
			chkGenre.setChecked(Player.lastRadioSearchWasByGenre);
			chkGenre.setOnClickListener(this);
			chkGenre.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
			chkGenre.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI._DLGsppad;
			btnGenre = new Spinner(ctx);
			btnGenre.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI._DLGsppad << 1;
			chkTerm = new RadioButton(ctx);
			chkTerm.setText(R.string.search_term);
			chkTerm.setChecked(!Player.lastRadioSearchWasByGenre);
			chkTerm.setOnClickListener(this);
			chkTerm.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
			chkTerm.setLayoutParams(p);
			
			p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			p.topMargin = UI._DLGsppad;
			txtTerm = new EditText(ctx);
			txtTerm.setText(Player.radioSearchTerm == null ? "" : Player.radioSearchTerm);
			txtTerm.setOnClickListener(this);
			txtTerm.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
			txtTerm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
			txtTerm.setSingleLine();
			txtTerm.setLayoutParams(p);
			
			l.addView(chkGenre);
			l.addView(btnGenre);
			l.addView(chkTerm);
			l.addView(txtTerm);
			
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
			finish(-1);
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
			finish(-1);
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
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_browser_radio);
		UI.smallTextAndColor((TextView)findViewById(R.id.lblLoading));
		list = (BgListView)findViewById(R.id.list);
		list.setOnKeyDownObserver(this);
		list.setScrollBarType((UI.browserScrollBarType == BgListView.SCROLLBAR_INDEXED) ? BgListView.SCROLLBAR_LARGE : UI.browserScrollBarType);
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
		btnGoBackToPlayer.setCompoundDrawables(new TextIconDrawable(UI.ICON_RIGHT, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		btnGoBackToPlayer.setDefaultHeight();
		btnAdd = (BgButton)findViewById(R.id.btnAdd);
		btnAdd.setTextColor(UI.colorState_text_reactive);
		btnAdd.setOnClickListener(this);
		btnAdd.setIcon(UI.ICON_ADD, true, false);
		sep2 = (TextView)findViewById(R.id.sep2);
		RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
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
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		UI.prepareControlContainer(panelSecondary, true, false);
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
		UI.prepareEdgeEffectColor(getApplication());
		updateButtons();
		doSearch();
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
		radioStationList.setObserver(null);
	}
	
	@Override
	protected void onResume() {
		UI.browserActivity = this;
		radioStationList.setObserver(list);
		SongAddingMonitor.start(getHostActivity());
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
	}
	
	@Override
	protected void onDestroy() {
		UI.browserActivity = null;
		radioStationList.cancel();
		radioStationList = null;
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
			txt.setPadding(UI._8dp, UI._4dp, UI._8dp, UI._4dp);
			txt.setTypeface(UI.defaultTypeface);
			txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
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
			txt.setPadding(UI._DLGdppad, UI._DLGsppad, UI._DLGdppad, UI._DLGsppad);
			txt.setTypeface(UI.defaultTypeface);
			txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._DLGsp);
			txt.setTextColor(defaultTextColors);
		}
		txt.setText(getGenreString(position));
		return txt;
	}
}
