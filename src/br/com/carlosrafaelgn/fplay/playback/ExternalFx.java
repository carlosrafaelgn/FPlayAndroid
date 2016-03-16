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
package br.com.carlosrafaelgn.fplay.playback;

import android.app.Activity;
import android.content.Intent;
import android.media.audiofx.AudioEffect;

import br.com.carlosrafaelgn.fplay.util.SerializableMap;

public final class ExternalFx {
	private static boolean enabled, applied, supported;

	static void loadConfig(SerializableMap opts) {
		enabled = opts.getBit(Player.OPTBIT_EXTERNALFX_ENABLED);
	}

	static void saveConfig(SerializableMap opts) {
		opts.putBit(Player.OPTBIT_EXTERNALFX_ENABLED, enabled);
	}

	private static void broadcastOpenIntent() {
		final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
		intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "br.com.carlosrafaelgn.fplay");
		intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
		intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, Player.audioSessionId);
		Player.theApplication.sendBroadcast(intent);
	}

	private static Intent createDisplayIntent() {
		final Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
		intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "br.com.carlosrafaelgn.fplay");
		intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
		intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, Player.audioSessionId);
		return intent;
	}

	private static void broadcastCloseIntent() {
		final Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
		intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "br.com.carlosrafaelgn.fplay");
		intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
		intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, Player.audioSessionId);
		Player.theApplication.sendBroadcast(intent);
	}

	static void _checkSupport() {
		try {
			supported = (createDisplayIntent().resolveActivity(Player.theApplication.getPackageManager()) != null);
		} catch (Throwable ex) {
			supported = false;
		}
	}

	static void _initialize() {
		//just a placeholder to keep this effect similar to the others
	}

	static void _release() {
		if (applied) {
			try {
				broadcastCloseIntent();
			} catch (Throwable ex) {
				supported = false;
			}
			applied = false;
		}
	}

	public static boolean isSupported() {
		return supported;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean displayUI(Activity activity) {
		if (supported && enabled) {
			try {
				activity.startActivity(createDisplayIntent());
				return true;
			} catch (Throwable ex) {
				supported = false;
			}
		}
		return false;
	}

	static void _setEnabled(boolean enabled) {
		ExternalFx.enabled = enabled;
		try {
			if (!enabled) {
				broadcastCloseIntent();
				applied = false;
			} else if (supported) {
				applied = true;
				broadcastOpenIntent();
			}
		} catch (Throwable ex) {
			supported = false;
		}
	}
}
