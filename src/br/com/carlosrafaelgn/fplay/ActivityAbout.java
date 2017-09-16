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

import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.RandomAccessFile;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.activity.ClientActivity;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgButton;
import br.com.carlosrafaelgn.fplay.ui.ObservableScrollView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.util.SafeURLSpan;

public final class ActivityAbout extends ClientActivity implements View.OnClickListener, Runnable {
	private ObservableScrollView list;
	private LinearLayout panelSecondary;
	private BgButton btnGoBack;
	private TextView lblUsage;
	private int pid;
	private long _SC_CLK_TCK, lastTime, lastTickCount;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.about);
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions", "deprecation" })
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_about);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		final TextView lblTitle = (TextView)findViewById(R.id.lblTitle);
		lblTitle.setText(R.string.app_name);
		UI.headingTextAndColor(lblTitle);
		final TextView lblVersion = (TextView)findViewById(R.id.lblVersion);
		UI.smallTextAndColor(lblVersion);
		//hardcode this in order to try to speed up things a little bit
		lblVersion.setText(UI.VERSION_NAME);
		//try {
		//	final PackageInfo inf = getApplication().getPackageManager().getPackageInfo(getApplication().getPackageName(), 0);
		//	lblVersion.setText("v" + inf.versionName);
		//} catch (Throwable ex) {
		//	ex.printStackTrace();
		//}
		UI.smallTextAndColor((TextView)findViewById(R.id.lblAppBy));
		final TextView lblMsg = (TextView)findViewById(R.id.lblMsg);
		final StringBuilder sb = new StringBuilder(2048);
		sb.append(getText(R.string.app_more_info));
		sb.append(getText(R.string.app_more_info_thanks));
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
		final int features = Player.getFeatures();
		sb.delete(0, sb.length());
		sb.append(getText(R.string.system_info));
		sb.append("\nABI");
		sb.append(UI.collon());
		sb.append(Build.CPU_ABI);
		if ((features & Player.FEATURE_PROCESSOR_ARM) != 0)
			sb.append(
				((features & Player.FEATURE_PROCESSOR_NEON) != 0) ?
					(((features & Player.FEATURE_PROCESSOR_64_BITS) != 0) ? " (64 bits + NEON)" : " (32 bits + NEON)") :
						(((features & Player.FEATURE_PROCESSOR_64_BITS) != 0) ? " (64 bits)" : " (32 bits)"));
		else if ((features & Player.FEATURE_PROCESSOR_X86) != 0)
			sb.append(
				((features & Player.FEATURE_PROCESSOR_SSE41) != 0) ?
					(((features & Player.FEATURE_PROCESSOR_64_BITS) != 0) ? " (64 bits + SSE4.1)" : " (32 bits + SSE4.1)") :
						(((features & Player.FEATURE_PROCESSOR_SSE) != 0) ?
							(((features & Player.FEATURE_PROCESSOR_64_BITS) != 0) ? " (64 bits + SSE)" : " (32 bits + SSE)") :
								(((features & Player.FEATURE_PROCESSOR_64_BITS) != 0) ? " (64 bits)" : " (32 bits)")));
		sb.append("\nAPI");
		sb.append(UI.collon());
		sb.append(Build.VERSION.SDK_INT);
		sb.append("\nDPI");
		sb.append(UI.collon());
		sb.append(UI.densityDpi);
		sb.append("\ndp");
		sb.append(UI.collon());
		sb.append(UI.formatIntAsFloat((int)(UI.density * 100.0f), true, true));
		sb.append("\nsp");
		sb.append(UI.collon());
		sb.append(UI.formatIntAsFloat((int)(UI.scaledDensity * 100.0f), true, true));
		sb.append('\n');
		sb.append(getText(R.string.resolution));
		sb.append(" (px)");
		sb.append(UI.collon());
		sb.append(UI.screenWidth);
		sb.append(" x ");
		sb.append(UI.screenHeight);
		sb.append('\n');
		sb.append(getText(R.string.resolution));
		sb.append(" (dp)");
		sb.append(UI.collon());
		sb.append(UI.formatIntAsFloat((int)(UI.pxToDp(UI.screenWidth) * 100.0f), true, true));
		sb.append(" x ");
		sb.append(UI.formatIntAsFloat((int)(UI.pxToDp(UI.screenHeight) * 100.0f), true, true));
		if (UI.isLowDpiScreen)
			sb.append("\nLDPI");
		if (UI.isLargeScreen)
			sb.append("\nLarge Screen");
		if (UI.isChromebook)
			sb.append("\nChromebook");
		if ((features & Player.FEATURE_DECODING_NATIVE) != 0)
			sb.append("\nNative Decoding");
		else if ((features & Player.FEATURE_DECODING_DIRECT) != 0)
			sb.append("\nDirect Decoding");
		final int[] playbackInfo = Player.getCurrentPlaybackInfo();
		if (playbackInfo != null && playbackInfo.length >= 6) {
			if (playbackInfo[0] > 0) {
				sb.append("\nNative Sample Rate (Device)");
				sb.append(UI.collon());
				sb.append(playbackInfo[0]);
				sb.append(" Hz");
			}
			if (playbackInfo[1] > 0) {
				sb.append("\nInput Sample Rate (Track)");
				sb.append(UI.collon());
				sb.append(playbackInfo[1]);
				sb.append(" Hz");

				sb.append("\nOutput Sample Rate (Playback)");
				sb.append(UI.collon());
				sb.append(playbackInfo[2]);
				sb.append(" Hz");

				sb.append("\nFrames per Buffer (Device)");
				sb.append(UI.collon());
				sb.append(playbackInfo[3]);

				sb.append("\nFrames per Buffer (Actually Used)");
				sb.append(UI.collon());
				sb.append(playbackInfo[4]);
			}
			if (playbackInfo[5] > 0)
				sb.append((playbackInfo[5] == 1) ? "\nAudioTrack engine" : "\nOpenSL ES engine");
		}
		lblDbg.setTypeface(UI.defaultTypeface);
		lblDbg.setTextColor(UI.colorState_text_listitem_secondary_static);
		lblDbg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._14sp);
		lblDbg.setText(sb.toString());
		list = (ObservableScrollView)findViewById(R.id.list);
		list.setBackgroundDrawable(new ColorDrawable(UI.color_list_original));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			UI.prepareViewPaddingBasedOnScreenWidth(list, 0, 0, 0);
			panelSecondary = null;
		} else {
			panelSecondary = (LinearLayout)findViewById(R.id.panelSecondary);
			UI.prepareViewPaddingBasedOnScreenWidth(panelSecondary, UI.controlLargeMargin, UI.controlMargin, UI.controlMargin);
			list = null;
		}
		lblUsage = (TextView)findViewById(R.id.lblUsage);
		lblUsage.setTypeface(UI.defaultTypeface);
		lblUsage.setTextColor(UI.colorState_text_listitem_secondary_static);
		lblUsage.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._14sp);
		lblUsage.setText("");
		if (UI.isLargeScreen)
			lblMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		UI.prepareControlContainer(findViewById(R.id.panelControls), false, true);
		MainHandler.postToMainThreadAtTime(this, SystemClock.uptimeMillis() + 1000);
	}

	@Override
	protected void onOrientationChanged() {
		if (list != null)
			UI.prepareViewPaddingBasedOnScreenWidth(list, 0, 0, 0);
		else if (panelSecondary != null)
			UI.prepareViewPaddingBasedOnScreenWidth(panelSecondary, UI.controlLargeMargin, UI.controlMargin, UI.controlMargin);
	}
	
	@Override
	protected void onCleanupLayout() {
		list = null;
		panelSecondary = null;
		btnGoBack = null;
		lblUsage = null;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack)
			finish(0, view, true);
	}

	@Override
	public void run() {
		if (lblUsage != null) {
			if (pid == 0)
				pid = Process.myPid();
			if (_SC_CLK_TCK == 0) {
				try {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
						_SC_CLK_TCK = Os.sysconf(OsConstants._SC_CLK_TCK);
					else
						_SC_CLK_TCK = 100;
				} catch (Throwable ex) {
					_SC_CLK_TCK = 100; //silly assumption :/
				}
			}

			RandomAccessFile reader = null;
			try {
				//http://man7.org/linux/man-pages/man5/proc.5.html
				//http://man7.org/linux/man-pages/man3/sysconf.3.html
				reader = new RandomAccessFile("/proc/" + pid + "/stat", "r");
				final String[] parts = reader.readLine().split(" +");
				final long tickCount = Long.parseLong(parts[13], 10) + Long.parseLong(parts[14], 10);
				final long time = SystemClock.elapsedRealtime();
				if (lastTime != 0) {
					//CPU time in seconds, not ticks (* 100000L = * 1000 (ms to s) * 100 (%))
					lblUsage.setText(String.format(Locale.US, "CPU usage: %.2f%%",
						(double)((tickCount - lastTickCount) * 100000L) / (double)(_SC_CLK_TCK * (time - lastTime))));
				}
				lastTickCount = tickCount;
				lastTime = time;
			} catch (Throwable ex) {
				//just ignore...
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable ex) {
						//just ignore...
					}
				}
			}

			MainHandler.postToMainThreadAtTime(this, SystemClock.uptimeMillis() + 1000);
		}
	}
}
