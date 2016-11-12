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

import java.util.Arrays;

import br.com.carlosrafaelgn.fplay.util.SerializableMap;

public final class Equalizer {
	private static int minBandLevel, maxBandLevel;
	private static boolean enabled, enabled_wire, enabled_wire_mic, enabled_bt;
	private static int[] bandLevels, bandLevels_wire, bandLevels_wire_mic, bandLevels_bt, bandFrequencies;
	private static br.com.carlosrafaelgn.fplay.playback.context.Equalizer theEqualizer;

	public static void deserialize(SerializableMap opts, int audioSink) {
		//use OPTBIT_EQUALIZER_ENABLED, OPT_EQUALIZER_LEVELCOUNT and OPT_EQUALIZER_LEVEL0 for all audio sinks!!!
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			enabled_wire = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED);
			break;
		case Player.AUDIO_SINK_WIRE_MIC:
			enabled_wire_mic = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED);
			break;
		case Player.AUDIO_SINK_BT:
			enabled_bt = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED);
			break;
		default:
			enabled = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED);
			break;
		}
		int count = opts.getInt(Player.OPT_EQUALIZER_LEVELCOUNT);
		if (count > 0) {
			if (count > 512)
				count = 512;
			int[] levels;
			switch (audioSink) {
			case Player.AUDIO_SINK_WIRE:
				if (bandLevels_wire == null || bandLevels_wire.length != count)
					bandLevels_wire = new int[count];
				levels = bandLevels_wire;
				break;
			case Player.AUDIO_SINK_WIRE_MIC:
				if (bandLevels_wire_mic == null || bandLevels_wire_mic.length != count)
					bandLevels_wire_mic = new int[count];
				levels = bandLevels_wire_mic;
				break;
			case Player.AUDIO_SINK_BT:
				if (bandLevels_bt == null || bandLevels_bt.length != count)
					bandLevels_bt = new int[count];
				levels = bandLevels_bt;
				break;
			default:
				if (bandLevels == null || bandLevels.length != count)
					bandLevels = new int[count];
				levels = bandLevels;
				break;
			}
			for (int i = levels.length - 1; i >= 0; i--)
				levels[i] = opts.getInt(Player.OPT_EQUALIZER_LEVEL0 + i);
		}
	}

	public static void serialize(SerializableMap opts, int audioSink) {
		//use OPTBIT_EQUALIZER_ENABLED, OPT_EQUALIZER_LEVELCOUNT and OPT_EQUALIZER_LEVEL0 for all audio sinks!!!
		int[] levels;
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED, enabled_wire);
			levels = bandLevels_wire;
			break;
		case Player.AUDIO_SINK_WIRE_MIC:
			opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED, enabled_wire_mic);
			levels = bandLevels_wire_mic;
			break;
		case Player.AUDIO_SINK_BT:
			opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED, enabled_bt);
			levels = bandLevels_bt;
			break;
		default:
			opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED, enabled);
			levels = bandLevels;
			break;
		}
		opts.put(Player.OPT_EQUALIZER_LEVELCOUNT, (levels != null) ? levels.length : 0);
		if (levels != null) {
			for (int i = levels.length - 1; i >= 0; i--)
				opts.put(Player.OPT_EQUALIZER_LEVEL0 + i, levels[i]);
		}
	}

	static void loadConfig(SerializableMap opts) {
		enabled = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED);
		//use the regular enabled flag as the default for the new presets
		enabled_wire = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED_WIRE, enabled);
		enabled_wire_mic = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED_WIRE_MIC, enabled_wire);
		enabled_bt = opts.getBit(Player.OPTBIT_EQUALIZER_ENABLED_BT, enabled);
		int count = opts.getInt(Player.OPT_EQUALIZER_LEVELCOUNT);
		if (count > 0) {
			if (count > 512)
				count = 512;
			if (bandLevels == null || bandLevels.length != count)
				bandLevels = new int[count];
			if (bandLevels_wire == null || bandLevels_wire.length != count)
				bandLevels_wire = new int[count];
			if (bandLevels_wire_mic == null || bandLevels_wire_mic.length != count)
				bandLevels_wire_mic = new int[count];
			if (bandLevels_bt == null || bandLevels_bt.length != count)
				bandLevels_bt = new int[count];
			for (int i = bandLevels.length - 1; i >= 0; i--) {
				bandLevels[i] = opts.getInt(Player.OPT_EQUALIZER_LEVEL0 + i, bandLevels[i]);
				//use the regular levels as the default for the new presets
				bandLevels_wire[i] = opts.getInt(Player.OPT_EQUALIZER_LEVEL0_WIRE + i, bandLevels[i]);
				bandLevels_wire_mic[i] = opts.getInt(Player.OPT_EQUALIZER_LEVEL0_WIRE_MIC + i, bandLevels_wire[i]);
				bandLevels_bt[i] = opts.getInt(Player.OPT_EQUALIZER_LEVEL0_BT + i, bandLevels[i]);
			}
		}
	}

	static void saveConfig(SerializableMap opts) {
		opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED, enabled);
		opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED_WIRE, enabled_wire);
		opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED_WIRE_MIC, enabled_wire_mic);
		opts.putBit(Player.OPTBIT_EQUALIZER_ENABLED_BT, enabled_bt);
		opts.put(Player.OPT_EQUALIZER_LEVELCOUNT, (bandLevels != null) ? bandLevels.length : 0);
		if (bandLevels != null) {
			for (int i = bandLevels.length - 1; i >= 0; i--)
				opts.put(Player.OPT_EQUALIZER_LEVEL0 + i, bandLevels[i]);
		}
		if (bandLevels_wire != null) {
			for (int i = bandLevels_wire.length - 1; i >= 0; i--)
				opts.put(Player.OPT_EQUALIZER_LEVEL0_WIRE + i, bandLevels_wire[i]);
		}
		if (bandLevels_wire_mic != null) {
			for (int i = bandLevels_wire_mic.length - 1; i >= 0; i--)
				opts.put(Player.OPT_EQUALIZER_LEVEL0_WIRE_MIC + i, bandLevels_wire_mic[i]);
		}
		if (bandLevels_bt != null) {
			for (int i = bandLevels_bt.length - 1; i >= 0; i--)
				opts.put(Player.OPT_EQUALIZER_LEVEL0_BT + i, bandLevels_bt[i]);
		}
	}

	static void _checkSupport() {
		_initialize();
		_release();
	}

	static boolean _isCreated() {
		return (theEqualizer != null);
	}

	@SuppressWarnings({ "PointlessBooleanExpression", "ConstantConditions" })
	static void _initialize() {
		if (theEqualizer != null)
			return;
		try {
			theEqualizer = new br.com.carlosrafaelgn.fplay.playback.context.Equalizer();
			final int bandCount = theEqualizer.getNumberOfBands();
			if (bandLevels == null)
				bandLevels = new int[bandCount];
			else if (bandLevels.length != bandCount)
				bandLevels = Arrays.copyOf(bandLevels, bandCount);
			if (bandLevels_wire == null)
				bandLevels_wire = new int[bandCount];
			else if (bandLevels_wire.length != bandCount)
				bandLevels_wire = Arrays.copyOf(bandLevels_wire, bandCount);
			if (bandLevels_wire_mic == null)
				bandLevels_wire_mic = new int[bandCount];
			else if (bandLevels_wire_mic.length != bandCount)
				bandLevels_wire_mic = Arrays.copyOf(bandLevels_wire_mic, bandCount);
			if (bandLevels_bt == null)
				bandLevels_bt = new int[bandCount];
			else if (bandLevels_bt.length != bandCount)
				bandLevels_bt = Arrays.copyOf(bandLevels_bt, bandCount);
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
					bandFrequencies[i] = theEqualizer.getCenterFreq((short)i) / 1000;
			}
			short[] l = theEqualizer.getBandLevelRange();
			if (l == null || l.length != 2) {
				minBandLevel = -1500;
				maxBandLevel = 1500;
			} else {
				minBandLevel = (int)l[0];
				maxBandLevel = (int)l[1];
			}
		} catch (Throwable ex) {
			_release();
			bandLevels = null;
			bandLevels_wire = null;
			bandLevels_wire_mic = null;
			bandLevels_bt = null;
			bandFrequencies = null;
		}
	}

	static void _release() {
		if (theEqualizer != null) {
			try {
				theEqualizer.release();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			theEqualizer = null;
		}
	}

	public static boolean isSupported() {
		return ((bandLevels != null) && (bandLevels.length > 0));
	}

	public static boolean isEnabled(int audioSink) {
		return ((audioSink == Player.AUDIO_SINK_WIRE) ? enabled_wire : ((audioSink == Player.AUDIO_SINK_WIRE_MIC) ? enabled_wire_mic : ((audioSink == Player.AUDIO_SINK_BT) ? enabled_bt : enabled)));
	}

	public static int getBandCount() {
		return ((bandLevels == null) ? 0 : bandLevels.length);
	}

	public static int getBandFrequency(int band) {
		return ((bandFrequencies == null || band >= bandFrequencies.length) ? 0 : bandFrequencies[band]);
	}

	public static int getMinBandLevel() {
		return minBandLevel;
	}

	public static int getMaxBandLevel() {
		return maxBandLevel;
	}

	public static int getBandLevel(int band, int audioSink) {
		final int[] levels = ((audioSink == Player.AUDIO_SINK_WIRE) ? bandLevels_wire : ((audioSink == Player.AUDIO_SINK_WIRE_MIC) ? bandLevels_wire_mic : ((audioSink == Player.AUDIO_SINK_BT) ? bandLevels_bt : bandLevels)));
		return ((levels == null || band >= levels.length) ? 0 : levels[band]);
	}

	public static void setBandLevel(int band, int level, int audioSink) {
		final int[] levels = ((audioSink == Player.AUDIO_SINK_WIRE) ? bandLevels_wire : ((audioSink == Player.AUDIO_SINK_WIRE_MIC) ? bandLevels_wire_mic : ((audioSink == Player.AUDIO_SINK_BT) ? bandLevels_bt : bandLevels)));
		if (levels == null || band >= levels.length)
			return;
		if (level > maxBandLevel)
			level = maxBandLevel;
		else if (level < minBandLevel)
			level = minBandLevel;
		levels[band] = level;
	}

	static void _setEnabled(boolean enabled, int audioSink) {
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			Equalizer.enabled_wire = enabled;
			break;
		case Player.AUDIO_SINK_WIRE_MIC:
			Equalizer.enabled_wire_mic = enabled;
			break;
		case Player.AUDIO_SINK_BT:
			Equalizer.enabled_bt = enabled;
			break;
		default:
			Equalizer.enabled = enabled;
			break;
		}
		if (theEqualizer == null || audioSink != Player.audioSinkUsedInEffects)
			return;
		try {
			if (!enabled) {
				theEqualizer.setEnabled(false);
			} else {
				_applyAllBandSettings();
				theEqualizer.setEnabled(true);
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		enabled = theEqualizer.getEnabled();
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			Equalizer.enabled_wire = enabled;
			break;
		case Player.AUDIO_SINK_WIRE_MIC:
			Equalizer.enabled_wire_mic = enabled;
			break;
		case Player.AUDIO_SINK_BT:
			Equalizer.enabled_bt = enabled;
			break;
		default:
			Equalizer.enabled = enabled;
			break;
		}
	}

	static void _commit(int band, int audioSink) {
		if (theEqualizer == null || audioSink != Player.audioSinkUsedInEffects)
			return;
		if (band < 0) {
			_applyAllBandSettings();
		} else {
			final int[] levels = ((audioSink == Player.AUDIO_SINK_WIRE) ? bandLevels_wire : ((audioSink == Player.AUDIO_SINK_WIRE_MIC) ? bandLevels_wire_mic : ((audioSink == Player.AUDIO_SINK_BT) ? bandLevels_bt : bandLevels)));
			try {
				theEqualizer.setBandLevel((short)band, (short)levels[band]);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	private static void _applyAllBandSettings() {
		final int[] levels = ((Player.audioSinkUsedInEffects == Player.AUDIO_SINK_WIRE) ? bandLevels_wire : ((Player.audioSinkUsedInEffects == Player.AUDIO_SINK_WIRE_MIC) ? bandLevels_wire_mic : ((Player.audioSinkUsedInEffects == Player.AUDIO_SINK_BT) ? bandLevels_bt : bandLevels)));
		if (levels != null && levels.length > 0) {
			try {
				int i = ((levels.length > (int)theEqualizer.getNumberOfBands()) ? (int)theEqualizer.getNumberOfBands() : levels.length);
				final short[] bandLevels = new short[i];
				final short numBands = (short)(i);
				for (i = i - 1; i >= 0; i--) {
					int level = levels[i];
					if (level > maxBandLevel) {
						level = maxBandLevel;
						levels[i] = level;
					} else if (level < minBandLevel) {
						level = minBandLevel;
						levels[i] = level;
					}
					bandLevels[i] = (short)level;
				}
				theEqualizer.setProperties(numBands, bandLevels);
			} catch (Throwable ex) {
				ex.printStackTrace();
				for (int i = levels.length - 1; i >= 0; i--) {
					try {
						theEqualizer.setBandLevel((short)i, (short)levels[i]);
					} catch (Throwable ex2) {
						ex2.printStackTrace();
					}
				}
			}
		}
	}
}
