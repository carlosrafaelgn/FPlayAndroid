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
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.BgListView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.util.SafeURLSpan;

public final class ActivityAbout extends ClientActivity implements View.OnClickListener {
	private ScrollView list;
	private BgButton btnGoBack;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.about);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_about);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		list = (ScrollView)findViewById(R.id.list);
		list.setHorizontalFadingEdgeEnabled(false);
		list.setVerticalFadingEdgeEnabled(false);
		list.setFadingEdgeLength(0);
		list.setBackgroundDrawable(new ColorDrawable(UI.color_list));
		list.setVerticalScrollBarEnabled(UI.browserScrollBarType != BgListView.SCROLLBAR_NONE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			setVerticalScrollBarPosition();
		final TextView lblTitle = (TextView)findViewById(R.id.lblTitle);
		lblTitle.setText("FPlay");
		UI.largeTextAndColor(lblTitle);
		final TextView lblVersion = (TextView)findViewById(R.id.lblVersion);
		UI.smallTextAndColor(lblVersion);
		try {
			final PackageInfo inf = getApplication().getPackageManager().getPackageInfo(getApplication().getPackageName(), 0);
			lblVersion.setText("v" + inf.versionName);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		UI.smallTextAndColor((TextView)findViewById(R.id.lblAppBy));
		final TextView lblMsg = (TextView)findViewById(R.id.lblMsg);
		final StringBuilder sb = new StringBuilder(2048);
		sb.append(getText(R.string.app_more_info));
		sb.append(getText(R.string.app_more_info2));
		sb.append(getText(R.string.app_license));
		lblMsg.setAutoLinkMask(0);
		lblMsg.setLinksClickable(true);
		lblMsg.setLinkTextColor(UI.colorState_text_listitem_secondary_static);
		UI.smallText(lblMsg);
		lblMsg.setTextColor(UI.colorState_text_listitem_static);
		lblMsg.setText(SafeURLSpan.parseSafeHtml(sb));
		lblMsg.setMovementMethod(LinkMovementMethod.getInstance());
		final TextView lblDbg = (TextView)findViewById(R.id.lblDbg);
		sb.delete(0, sb.length());
		sb.append(getText(R.string.system_info));
		sb.append("\nABI: ");
		sb.append(Build.CPU_ABI);
		sb.append("\nAPI: ");
		sb.append(Build.VERSION.SDK_INT);
		sb.append("\nDPI: ");
		sb.append(UI.densityDpi);
		sb.append("\ndp: ");
		sb.append(UI.formatIntAsFloat((int)(UI.density * 100.0f), true, true));
		sb.append("\nsp: ");
		sb.append(UI.formatIntAsFloat((int)(UI.scaledDensity * 100.0f), true, true));
		sb.append('\n');
		sb.append(getText(R.string.resolution));
		sb.append(" (px): ");
		sb.append(UI.screenWidth);
		sb.append(" x ");
		sb.append(UI.screenHeight);
		sb.append('\n');
		sb.append(getText(R.string.resolution));
		sb.append(" (dp): ");
		sb.append(UI.formatIntAsFloat((int)(UI.pxToDp(UI.screenWidth) * 100.0f), true, true));
		sb.append(" x ");
		sb.append(UI.formatIntAsFloat((int)(UI.pxToDp(UI.screenHeight) * 100.0f), true, true));
		if (UI.isLowDpiScreen)
			sb.append("\nLDPI");
		if (UI.isLargeScreen)
			sb.append("\nLarge Screen");
		lblDbg.setTypeface(UI.defaultTypeface);
		lblDbg.setTextColor(UI.colorState_text_listitem_secondary_static);
		lblDbg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._14sp);
		lblDbg.setText(sb.toString());
		if (UI.isLargeScreen) {
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
			lblMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		}
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		UI.prepareEdgeEffectColor(getApplication());
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setVerticalScrollBarPosition() {
		if (list != null)
			list.setVerticalScrollbarPosition(UI.scrollBarToTheLeft ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
	}

	@Override
	protected void onOrientationChanged() {
		if (UI.isLargeScreen && list != null)
			UI.prepareViewPaddingForLargeScreen(list, 0, 0);
	}
	
	@Override
	protected void onCleanupLayout() {
		list = null;
		btnGoBack = null;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack)
			finish(0, view, true);
	}
}
