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
package br.com.carlosrafaelgn.fplay.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.drawable.BorderDrawable;
import br.com.carlosrafaelgn.fplay.util.Timer;

public class SongAddingMonitor implements Timer.TimerHandler {
	private static TextView notification;
	private static final Timer timer = new Timer(new SongAddingMonitor(), "Song Adding Monitor Timer", false, true, false);
	
	private SongAddingMonitor() {
	}
	
	@SuppressWarnings("deprecation")
	public static void start(Activity activity) {
		if (Player.songs.isAdding()) {
			stop();
			//...the parent of an activity's content view is always a FrameLayout.
			//http://android-developers.blogspot.com.br/2009/03/android-layout-tricks-3-optimize-by.html
			View parent = activity.findViewById(android.R.id.content);
			if (parent != null && parent instanceof FrameLayout) {
				notification = new TextView(activity);
				FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
				p.leftMargin = UI._8dp;
				p.topMargin = UI._8dp;
				p.rightMargin = UI._8dp;
				p.bottomMargin = UI._8dp;
				p.gravity = Gravity.LEFT | Gravity.BOTTOM;
				notification.setLayoutParams(p);
				UI.smallText(notification);
				notification.setText(R.string.msg_adding);
				notification.setBackgroundDrawable(new BorderDrawable(UI.color_current_border, UI.color_current, true, true, true, true));
				notification.setTextColor(UI.colorState_text_sel);
				notification.setPadding(UI._2dp, UI._2dp, UI._2dp, UI._2dp);
				((FrameLayout)parent).addView(notification);
				timer.start(500, false);
			}
		}
	}
	
	public static void stop() {
		if (notification != null) {
			notification.setVisibility(View.GONE);
			final ViewParent p = notification.getParent();
			if (p != null)
				((FrameLayout)p).removeView(notification);
			notification = null;
		}
		timer.stop();
	}
	
	@Override
	public void handleTimer(Timer timer, Object param) {
		if (!Player.songs.isAdding())
			stop();
	}
}
