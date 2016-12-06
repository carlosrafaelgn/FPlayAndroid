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
package br.com.carlosrafaelgn.fplay.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.drawable.BgShadowDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;

public final class BackgroundActivityMonitor implements Timer.TimerHandler {
	private static LinearLayout notification;
	private static TextView notificationTextView;
	private static int lastMsg;
	private static final Timer timer = new Timer(new BackgroundActivityMonitor(), "Background Activity Monitor Timer", false, true, false);

	@SuppressWarnings("deprecation")
	public static void start(ActivityHost activity) {
		if (Player.state != Player.STATE_ALIVE || Player.songs.isAdding() || Player.bluetoothVisualizerState != Player.BLUETOOTH_VISUALIZER_STATE_INITIAL || Player.bluetoothVisualizerLastErrorMessage != 0) {
			stop();
			//...the parent of an activity's content view is always a FrameLayout.
			//http://android-developers.blogspot.com.br/2009/03/android-layout-tricks-3-optimize-by.html
			View parent = activity.findViewByIdDirect(android.R.id.content);
			if (parent != null && parent instanceof FrameLayout) {
				notificationTextView = new TextView(activity);
				UI.smallText(notificationTextView);
				UI.prepareNotificationViewColors(notificationTextView);
				lastMsg = ((Player.state != Player.STATE_ALIVE) ? R.string.loading : (Player.songs.isAdding() ? R.string.adding_songs : ((Player.bluetoothVisualizerLastErrorMessage != 0) ? R.string.bt_error : R.string.bt_active)));
				notificationTextView.setText(lastMsg);
				notificationTextView.setGravity(Gravity.CENTER);
				notificationTextView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
				notificationTextView.setPadding(UI.controlSmallMargin, UI.controlSmallMargin, UI.controlSmallMargin, UI.controlSmallMargin);

				notification = new LinearLayout(activity);
				final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
				p.leftMargin = UI.controlSmallMargin;
				p.topMargin = UI.controlSmallMargin;
				p.rightMargin = UI.controlSmallMargin;
				p.bottomMargin = UI.controlSmallMargin;
				p.gravity = Gravity.START | Gravity.BOTTOM;
				notification.setLayoutParams(p);
				notification.setOrientation(LinearLayout.VERTICAL);
				notification.setBackgroundDrawable(new BgShadowDrawable(false));
				notification.addView(notificationTextView);

				((FrameLayout)parent).addView(notification);

				if (Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_INITIAL)
					timer.start(250);
			}
		}
	}

	public static void bluetoothEnded() {
		if (Player.state == Player.STATE_ALIVE && !Player.songs.isAdding()) {
			if (Player.bluetoothVisualizerLastErrorMessage != 0) {
				if (lastMsg != R.string.bt_error && notificationTextView != null) {
					lastMsg = R.string.bt_error;
					notificationTextView.setText(lastMsg);
				}
			} else {
				stop();
			}
		}
	}
	
	public static void stop() {
		lastMsg = 0;
		if (notification != null) {
			notification.setVisibility(View.GONE);
			final ViewParent p = notification.getParent();
			if (p != null && p instanceof FrameLayout)
				((FrameLayout)p).removeView(notification);
			notification = null;
			notificationTextView = null;
		}
		timer.stop();
	}
	
	@Override
	public void handleTimer(Timer timer, Object param) {
		final int msg = ((Player.state != Player.STATE_ALIVE) ? R.string.loading : (Player.songs.isAdding() ? R.string.adding_songs : ((Player.bluetoothVisualizerLastErrorMessage != 0) ? R.string.bt_error : ((Player.bluetoothVisualizerState != Player.BLUETOOTH_VISUALIZER_STATE_INITIAL) ? R.string.bt_active : 0))));
		if (msg == 0) {
			stop();
		} else {
			if (lastMsg != msg) {
				lastMsg = msg;
				if (notificationTextView != null)
					notificationTextView.setText(msg);
			}
			if (Player.state == Player.STATE_ALIVE && !Player.songs.isAdding())
				BackgroundActivityMonitor.timer.stop();
		}
	}
}
