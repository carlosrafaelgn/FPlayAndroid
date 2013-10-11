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
package br.com.carlosrafaelgn.fplay.playback;

import java.util.Arrays;

import br.com.carlosrafaelgn.fplay.util.SerializableMap;

public final class Equalizer {
	//values 0x01xx are shared among all effects
	private static final int OPT_ENABLED = 0x0100;
	private static final int OPT_PRESET = 0x0101;
	private static final int OPT_LEVELCOUNT = 0x0102;
	private static final int OPT_LEVEL0 = 0x20000;
	private static int sessionId = -1, minBandLevel, maxBandLevel, preset;
	private static boolean enabled;
	private static int[] bandLevels, bandFrequencies;
	private static android.media.audiofx.Equalizer theEqualizer;
	
	public static void deserializePreset(SerializableMap opts) {
		preset = -1;
		int count = opts.getInt(OPT_LEVELCOUNT);
		if (count > 0) {
			if (bandLevels == null) {
				if (count > 512)
					count = 512;
				bandLevels = new int[count];
			}
			for (int i = bandLevels.length - 1; i >= 0; i--)
				bandLevels[i] = opts.getInt(OPT_LEVEL0 + i, bandLevels[i]);
		}		
	}
	
	public static void loadConfig(SerializableMap opts) {
		enabled = opts.getBoolean(OPT_ENABLED);
		deserializePreset(opts);
		preset = opts.getInt(OPT_PRESET, -1);
	}
	
	public static void serializePreset(SerializableMap opts) {
		opts.put(OPT_LEVELCOUNT, (bandLevels != null) ? bandLevels.length : 0);
		if (bandLevels != null) {
			for (int i = bandLevels.length - 1; i >= 0; i--)
				opts.put(OPT_LEVEL0 + i, bandLevels[i]);
		}		
	}
	
	public static void saveConfig(SerializableMap opts) {
		opts.put(OPT_ENABLED, enabled);
		opts.put(OPT_PRESET, preset);
		serializePreset(opts);
	}
	
	public static void initialize(int newSessionId) {
		if (newSessionId != Integer.MIN_VALUE)
			sessionId = newSessionId;
		try {
			theEqualizer = new android.media.audiofx.Equalizer(0, sessionId);
		} catch (Throwable ex) {
			return;
		}
		final int bandCount = theEqualizer.getNumberOfBands();
		if (bandLevels == null)
			bandLevels = new int[bandCount];
		else if (bandLevels.length != bandCount)
			bandLevels = Arrays.copyOf(bandLevels, bandCount);
		boolean copyFrequencies = false;
		if (bandFrequencies == null) {
			bandFrequencies = new int[bandCount];
			copyFrequencies = true;
		} else if (bandFrequencies.length != bandCount) {
			bandFrequencies = Arrays.copyOf(bandFrequencies, bandCount);
			copyFrequencies = true;
		}
		if (copyFrequencies) {
			for (int i = bandCount - 1; i >= 0; i--)
				bandFrequencies[i] = theEqualizer.getCenterFreq((short)i);
		}
		short[] l = theEqualizer.getBandLevelRange();
		if (l == null || l.length != 2) {
			minBandLevel = -1500;
			maxBandLevel = 1500;
		} else {
			minBandLevel = (int)l[0];
			maxBandLevel = (int)l[1];
		}
		setEnabled(enabled);
	}
	
	public static void release() {
		if (theEqualizer != null) {
			//theEqualizer.setEnabled(false);
			theEqualizer.release();
			theEqualizer = null;
		}
	}
	
	public static boolean isUsingFactoryPreset() {
		return (preset >= 0);
	}
	
	public static int getFactoryPresetCount() {
		return ((theEqualizer == null) ? 0 : (int)theEqualizer.getNumberOfPresets());
	}
	
	public static String getFactoryPresetName(int preset) {
		return ((theEqualizer == null || preset < 0 || preset >= (int)theEqualizer.getNumberOfPresets()) ? null : theEqualizer.getPresetName((short)preset));
	}
	
	public static int getCurrentFactoryPreset() {
		return preset;
	}
	
	public static void setCurrentFactoryPreset(int preset) {
		if (theEqualizer == null)
			return;
		if (preset >= 0 && preset < theEqualizer.getNumberOfPresets()) {
			Equalizer.preset = preset;
			try {
				theEqualizer.usePreset((short)preset);
			} catch (Throwable ex) {
			}
		} else if (preset < 0) {
			Equalizer.preset = -1;
			applyAllBandSettings();
		}
	}
	
	public static boolean isSupported() {
		return ((bandLevels != null) && (bandLevels.length > 0));
	}
	
	public static boolean isEnabled() {
		return enabled;
	}
	
	public static void setEnabled(boolean enabled) {
		Equalizer.enabled = enabled;
		if (!enabled) {
			if (theEqualizer != null) {
				System.out.println(theEqualizer.setEnabled(false));
				Equalizer.enabled = theEqualizer.getEnabled();
				//theEqualizer.release();
				//theEqualizer = null;
			}
		} else if (sessionId >= 0) {
			//if (theEqualizer == null) {
			//	try {
			//		theEqualizer = new android.media.audiofx.Equalizer(0, sessionId);
			//	} catch (Throwable ex) {
			//		return;
			//	}
			//}
			if (theEqualizer != null) {
				try {
					applyAllBandSettings();
					System.out.println(theEqualizer.setEnabled(true));
				} catch (Throwable ex) {
				}
				Equalizer.enabled = theEqualizer.getEnabled();
			}
		}
	}
	
	public static int getBandCount() {
		return ((bandLevels == null) ? 0 : bandLevels.length);
	}
	
	public static int getBandFrequency(int band) {
		return ((bandFrequencies == null) ? 0 : bandFrequencies[band]);
	}
	
	public static int getMinBandLevel() {
		return minBandLevel;
	}
	
	public static int getMaxBandLevel() {
		return maxBandLevel;
	}
	
	public static int getBandLevel(int band) {
		return ((bandLevels == null || band >= bandLevels.length) ? 0 : bandLevels[band]);
	}
	
	public static void setBandLevel(int band, int level, boolean actuallyApply) {
		if (bandLevels == null || band >= bandLevels.length)
			return;
		if (level > maxBandLevel)
			level = maxBandLevel;
		else if (level < minBandLevel)
			level = minBandLevel;
		bandLevels[band] = level;
		if (actuallyApply) {
			if (preset >= 0) {
				preset = -1;
				applyAllBandSettings();
			} else if (theEqualizer != null) {
				try {
					theEqualizer.setBandLevel((short)band, (short)level);
				} catch (Throwable ex) {
				}
			}
		}
	}
	
	public static void applyAllBandSettings() {
		if (theEqualizer == null)
			return;
		if (preset >= 0 && preset < theEqualizer.getNumberOfPresets()) {
			try {
				theEqualizer.usePreset((short)preset);
			} catch (Throwable ex) {
			}
			return;
		}
		if (bandLevels != null && bandLevels.length > 0) {
			preset = -1;
			try {
				final android.media.audiofx.Equalizer.Settings s = new android.media.audiofx.Equalizer.Settings();
				int i = ((bandLevels.length > (int)theEqualizer.getNumberOfBands()) ? (int)theEqualizer.getNumberOfBands() : bandLevels.length);
				s.bandLevels = new short[i];
				s.curPreset = -1;
				s.numBands = (short)(i);
				for (i = i - 1; i >= 0; i--) {
					int level = bandLevels[i];
					if (level > maxBandLevel) {
						level = maxBandLevel;
						bandLevels[i] = level;
					} else if (level < minBandLevel) {
						level = minBandLevel;
						bandLevels[i] = level;
					}
					s.bandLevels[i] = (short)level;
				}
				theEqualizer.setProperties(s);
			} catch (Throwable ex) {
				for (int i = bandLevels.length - 1; i >= 0; i--) {
					try {
						theEqualizer.setBandLevel((short)i, (short)bandLevels[i]);
					} catch (Throwable ex2) {
					}
				}
			}
		}
	}
}
