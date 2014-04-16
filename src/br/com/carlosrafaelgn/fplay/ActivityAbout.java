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

import android.content.pm.PackageInfo;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.SongAddingMonitor;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;

public final class ActivityAbout extends ClientActivity implements View.OnClickListener {
	private ScrollView list;
	private BgButton btnGoBack;
	
	private String formatFloat(String format, float number) {
		String r = null;
		try {
			if (UI.getForcedLocale() != UI.LOCALE_NONE)
				r = String.format(UI.getLocaleFromCode(UI.getCurrentLocale(getApplication())), format, number);
		} catch (Throwable ex) {
		}
		return ((r == null) ? String.format(format, number) : r);
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
		list.setBackgroundDrawable(new BorderDrawable(0, UI.thickDividerSize, 0, 0));
		final TextView lblTitle = (TextView)findViewById(R.id.lblTitle);
		lblTitle.setText("FPlay");
		UI.largeTextAndColor(lblTitle);
		final TextView lblVersion = (TextView)findViewById(R.id.lblVersion);
		UI.smallTextAndColor(lblVersion);
		try {
			final PackageInfo inf = getApplication().getPackageManager().getPackageInfo(getApplication().getPackageName(), 0);
			lblVersion.setText("v" + inf.versionName);
		} catch (Throwable e) {
		}
		UI.smallTextAndColor((TextView)findViewById(R.id.lblAppBy));
		final TextView lblMsg = (TextView)findViewById(R.id.lblMsg);
		final StringBuilder sb = new StringBuilder(1024);
		sb.append(getText(R.string.app_more_info));
		sb.append("\n\nFolder/Disc icons:\nhttp://www.24psd.com/ubuntu+icon+pack\n\nPhone icon:\nhttp://www.psdgraphics.com/graphics/photoshop-recreation-of-google-nexus-one-smartphone-download-psd\n\nSD card icon:\nhttp://artofapogee.blogspot.com.br/2010/02/sd-card-icon.html");
		sb.append(getText(R.string.app_more_info2));
		sb.append(getText(R.string.app_license));
		lblMsg.setAutoLinkMask(Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
		lblMsg.setLinksClickable(true);
		lblMsg.setText(sb.toString());
		lblMsg.setLinkTextColor(UI.color_text_listitem_secondary);
		UI.smallText(lblMsg);
		lblMsg.setTextColor(UI.colorState_text_listitem_static);
		final TextView lblDbg = (TextView)findViewById(R.id.lblDbg);
		sb.delete(0, sb.length());
		sb.append(getText(R.string.system_info));
		sb.append("\nDPI: ");
		sb.append(UI.densityDpi);
		sb.append("\ndp: ");
		sb.append(formatFloat("%.1f", UI.density));
		sb.append("\nsp: ");
		sb.append(formatFloat("%.1f", UI.scaledDensity));
		sb.append("\n" + getText(R.string.resolution) + " (px): ");
		sb.append(UI.screenWidth);
		sb.append(" x ");
		sb.append(UI.screenHeight);
		sb.append("\n" + getText(R.string.resolution) + " (dp): ");
		sb.append(formatFloat("%.1f", UI.pxToDp(UI.screenWidth)));
		sb.append(" x ");
		sb.append(formatFloat("%.1f", UI.pxToDp(UI.screenHeight)));
		if (UI.isLowDpiScreen)
			sb.append("\nLDPI");
		if (UI.isLargeScreen)
			sb.append("\nLarge Screen");
		lblDbg.setTypeface(UI.defaultTypeface);
		lblDbg.setTextColor(UI.colorState_text_listitem_secondary_static);
		lblDbg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.spToPxI(12));
		lblDbg.setText(sb.toString());
		if (UI.isLargeScreen) {
			UI.prepareViewPaddingForLargeScreen(list);
			lblMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		} else if (UI.isLowDpiScreen) {
			findViewById(R.id.panelControls).setPadding(0, 0, 0, 0);
		}
	}
	
	@Override
	protected void onPause() {
		SongAddingMonitor.stop();
	}
	
	@Override
	protected void onResume() {
		SongAddingMonitor.start(getHostActivity());
	}
	
	@Override
	protected void onOrientationChanged() {
		if (UI.isLargeScreen && list != null)
			UI.prepareViewPaddingForLargeScreen(list);
	}
	
	@Override
	protected void onCleanupLayout() {
		list = null;
		btnGoBack = null;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			finish();
		}
	}
}
