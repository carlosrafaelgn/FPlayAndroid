//
//FPlayAndroid is distributed under the FreeBSD License
//
//Copyright (c) 2013-2014, Carlos Rafael Gimenes das Neves
//All rights reserved.
//
//Redistribution and use in source and binary forms, with or without
//modification, are permitted provided that the following conditions are met:
//
//1. Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
//ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
//WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
//ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
//(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
//LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
//ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
//SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//The views and conclusions contained in the software and documentation are those
//of the authors and should not be interpreted as representing official policies,
//either expressed or implied, of the FreeBSD Project.
//
//https://github.com/carlosrafaelgn/FPlayAndroid
//
package br.com.carlosrafaelgn.fplay.playback;

/*import android.media.MediaPlayer;
import br.com.carlosrafaelgn.fplay.util.SerializableMap;*/

public final class PresetReverb {
	/*private static final int OPT_PRESET = 0x0114;
	//private static final int OPT_STRENGTH = 0x0115;
	private static int preset, strength;
	private static android.media.audiofx.PresetReverb thePresetReverb;
	
	public static void loadConfig(SerializableMap opts) {
		preset = opts.getInt(OPT_PRESET);
		//strength = opts.getInt(OPT_STRENGTH);
	}
	
	public static void saveConfig(SerializableMap opts) {
		opts.put(OPT_PRESET, preset);
		//opts.put(OPT_STRENGTH, strength);
	}
	
	private static void initialize() {
		if (preset != 0) {
			try {
				thePresetReverb = new android.media.audiofx.PresetReverb(1, 0);
				thePresetReverb.setEnabled(true);
			} catch (Throwable ex) {
				release();
			}
		}
	}
	
	public static void release() {
		if (thePresetReverb != null) {
			Player.releaseReverbFromPlayers();
			try {
				thePresetReverb.release();
			} catch (Throwable ex) {
			}
			thePresetReverb = null;
		}
	}
	
	public static int getPreset() {
		return preset;
	}
	
	public static void setPreset(int preset, boolean applyToPlayers) {
		if (preset < 0) //PRESET_NONE
			preset = 0;
		else if (preset > 6) //PRESET_PLATE
			preset = 6;
		PresetReverb.preset = preset;
		if (thePresetReverb == null && preset != 0)
			initialize();
		if (thePresetReverb != null) {
			if (preset == 0) {
				Player.releaseReverbFromPlayers();
				try {
					thePresetReverb.release();
				} catch (Throwable ex) {
				}
				thePresetReverb = null;
			} else {
				try {
					thePresetReverb.setPreset((short)preset);
				} catch (Throwable ex) {
					try {
						preset = (int)thePresetReverb.getPreset();
					} catch (Throwable ex2) {
						preset = 0;
					}
				}
				if (preset == 0) {
					Player.releaseReverbFromPlayers();
					try {
						thePresetReverb.release();
					} catch (Throwable ex) {
					}
					thePresetReverb = null;
				} else if (applyToPlayers) {
					Player.applyReverbToPlayers();
				}
			}
		}
	}
	
	public static int getStrength() {
		return strength;
	}
	
	public static void setStrength(int strength, boolean actuallyApply) {
	}
	
	public static void applyToPlayer(MediaPlayer player) {
		if (preset != 0 && player != null) {
			if (thePresetReverb == null) {
				initialize();
				setPreset(preset, false);
			}
			if (thePresetReverb != null) {
				try {
					player.attachAuxEffect(thePresetReverb.getId());
					player.setAuxEffectSendLevel(1);
				} catch (Throwable ex) {
				}
			}
		}
	}*/
}
