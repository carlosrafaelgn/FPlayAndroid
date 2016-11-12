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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import br.com.carlosrafaelgn.fplay.playback.Player;

//
//Allowing applications to play nice(r) with each other: Handling remote control buttons
//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
//
public final class ExternalReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent == null || Player.state != Player.STATE_ALIVE)
			return;
		final String a = intent.getAction();
		if (a == null)
			return;
		switch (a) {
		case "android.media.AUDIO_BECOMING_NOISY":
			Player.becomingNoisy();
			break;
		case "android.intent.action.HEADSET_PLUG":
			if (Player.hasFocus)
				Player.registerMediaButtonEventReceiver();
			Player.audioSinkChanged(intent.getExtras().getInt("state", -1) > 0, intent.getExtras().getInt("microphone", -1) > 0);
			break;
		case "android.intent.action.MEDIA_BUTTON":
			final Object o = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (o == null || !(o instanceof KeyEvent))
				return;
			final KeyEvent e = (KeyEvent)o;
			if (e.getAction() == KeyEvent.ACTION_DOWN)
				Player.handleMediaButton(e.getKeyCode());
			break;
		case "android.intent.action.CALL_BUTTON":
			if (Player.hasFocus)
				Player.handleMediaButton(KeyEvent.KEYCODE_CALL);
			break;
		case "android.media.ACTION_SCO_AUDIO_STATE_UPDATED":
		case "android.media.SCO_AUDIO_STATE_CHANGED":
		case "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED":
		case "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED":
		case "android.bluetooth.intent.action.HEADSET_STATE_CHANGED":
			if (Player.hasFocus)
				Player.registerMediaButtonEventReceiver();
			Player.audioSinkChanged(false, false);
			break;
		}
		if (isOrderedBroadcast())
			abortBroadcast();
	}
}
