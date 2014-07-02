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

import br.com.carlosrafaelgn.fplay.util.SerializableMap;

public final class BassBoost {
	private static int sessionId = Integer.MIN_VALUE, strength;
	private static boolean enabled, strengthSupported, supported;
	private static android.media.audiofx.BassBoost theBooster;
	
	public static void loadConfig(SerializableMap opts) {
		if (opts.hasBits())
			enabled = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED);
		else
			enabled = opts.getBoolean(Player.OPT_BASSBOOST_ENABLED);
		strength = opts.getInt(Player.OPT_BASSBOOST_STRENGTH);
	}
	
	public static void saveConfig(SerializableMap opts) {
		opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED, enabled);
		opts.put(Player.OPT_BASSBOOST_STRENGTH, strength);
	}
	
	public static void initialize(int newSessionId) {
		if (newSessionId != Integer.MIN_VALUE)
			sessionId = newSessionId;
		try {
			theBooster = new android.media.audiofx.BassBoost(0, sessionId);
			strengthSupported = theBooster.getStrengthSupported();
			supported = true;
		} catch (Throwable ex) {
			supported = false;
		}
	}
	
	public static void release() {
		if (theBooster != null) {
			try {
				theBooster.release();
			} catch (Throwable ex) {
			}
			theBooster = null;
		}
	}
	
	public static boolean isSupported() {
		return supported;
	}
	
	public static boolean isStrengthSupported() {
		return strengthSupported;
	}
	
	public static boolean isEnabled() {
		return enabled;
	}
	
	public static void setEnabled(boolean enabled, boolean actuallyApply) {
		BassBoost.enabled = enabled;
		if (theBooster != null && actuallyApply) {
			try {
				if (!enabled) {
					theBooster.setEnabled(false);
				} else if (sessionId != Integer.MIN_VALUE) {
					if (!strengthSupported)
						strength = 1000;
					setStrength(strength, true);
					theBooster.setEnabled(true);
				}
			} catch (Throwable ex) {
			}
			BassBoost.enabled = theBooster.getEnabled();
		}
	}
	
	public static int getMaxStrength() {
		return 1000;
	}
	
	public static int getStrength() {
		return strength;
	}
	
	public static void setStrength(int strength, boolean actuallyApply) {
		if (strength > 1000)
			strength = 1000;
		else if (strength < 0)
			strength = 0;
		BassBoost.strength = strength;
		if (actuallyApply) {
			if (theBooster != null) {
				try {
					theBooster.setStrength(strengthSupported ? (short)strength : (short)((strength == 0) ? 0 : 1000));
					BassBoost.strength = theBooster.getRoundedStrength();
				} catch (Throwable ex) {
				}
			}
		}
	}
}
