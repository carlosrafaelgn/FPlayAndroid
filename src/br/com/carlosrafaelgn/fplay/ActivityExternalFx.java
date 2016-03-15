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

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.playback.ExternalFx;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;

public final class ActivityExternalFx extends ClientActivity implements Runnable, View.OnClickListener {

	private BgButton btnGoBack, btnDisplayExternalFx, btnDisableExternalFx;
	private boolean isDisabling;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.audio_effects);
	}

	@Override
	public void run() {
		if (isDisabling)
			finishAndStartActivity(new ActivityEffects(), 0, null, true);
	}

	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			if (!isDisabling)
				finish(0, view, true);
		} else if (view == btnDisplayExternalFx) {
			if (!isDisabling)
				ExternalFx.displayUI();
		} else if (view == btnDisableExternalFx) {
			if (!isDisabling) {
				isDisabling = true;
				Player.enableExternalFx(false, this);
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_external_fx);

		final LinearLayout panelControls = (LinearLayout)findViewById(R.id.panelControls);
		panelControls.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
		if (UI.isLargeScreen)
			UI.prepareViewPaddingForLargeScreen(panelControls, 0, 0);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnDisplayExternalFx = (BgButton)findViewById(R.id.btnDisplayExternalFx);
		btnDisplayExternalFx.setOnClickListener(this);
		btnDisplayExternalFx.setCompoundDrawables(new TextIconDrawable(UI.ICON_EQUALIZER, UI.color_text_listitem, UI.defaultControlContentsSize), null, null, null);
		btnDisplayExternalFx.setMinimumHeight(UI.defaultControlSize);
		btnDisplayExternalFx.setTextColor(UI.colorState_text_listitem_reactive);
		btnDisableExternalFx = (BgButton)findViewById(R.id.btnDisableExternalFx);
		btnDisableExternalFx.setOnClickListener(this);
		btnDisableExternalFx.setCompoundDrawables(new TextIconDrawable(UI.ICON_REMOVE, UI.color_text_listitem, UI.defaultControlContentsSize), null, null, null);
		btnDisableExternalFx.setMinimumHeight(UI.defaultControlSize);
		btnDisableExternalFx.setTextColor(UI.colorState_text_listitem_reactive);
		final TextView lblInfo = (TextView)findViewById(R.id.lblInfo);
		lblInfo.setTextColor(UI.color_text_disabled);
		lblInfo.setCompoundDrawables(new TextIconDrawable(UI.ICON_INFORMATION, UI.color_text_disabled, UI.defaultControlContentsSize), null, null, null);
		UI.mediumText(lblInfo);

		UI.prepareControlContainer(findViewById(R.id.panelTop), false, true);
	}

	@Override
	protected void onCleanupLayout() {
		btnGoBack = null;
		btnDisplayExternalFx = null;
		btnDisableExternalFx = null;
	}

	@Override
	protected void onDestroy() {
		//even if run() is executed, finishAndStartActivity won't be
		isDisabling = false;
	}
}
