/*
 *    Copyright (C) 2014 Haruki Hasegawa
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/// ===============================================================
// Most of declarations and Javadoc comments are copied from
// /frameworks/base/media/java/android/media/audiofx/Equalizer.java
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/// ===============================================================

package com.h6ah4i.android.media.audiofx;

import java.util.StringTokenizer;

public interface IEqualizer extends IAudioEffect {

	/**
	 * The Settings class regroups all equalizer parameters. It is used in
	 * conjuntion with getProperties() and setProperties() methods
	 * to backup and restore all parameters in a single call.
	 */
	class Settings implements Cloneable {
		public short curPreset;
		public short numBands;
		public short[] bandLevels;

		@Override
		public Settings clone() {
			try {
				// deep copy
				Settings clone = (Settings) super.clone();

				clone.curPreset = curPreset;
				clone.numBands = numBands;
				clone.bandLevels = (bandLevels != null) ? (bandLevels.clone()) : null;

				return clone;
			} catch (CloneNotSupportedException e) {
				return null;
			}
		}
	}

	/**
	 * Gets the band that has the most effect on the given frequency.
	 *
	 * @param frequency frequency in milliHertz which is to be equalized via the
	 *            returned band.
	 * @return the frequency band that has most effect on the given frequency.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short getBand(int frequency);

	int[] getBandFreqRange(short band);

	/**
	 * Gets the gain set for the given equalizer band.
	 *
	 * @param band frequency band whose gain is requested. The numbering of the
	 *            bands starts from 0 and ends at (number of bands - 1).
	 * @return the gain in millibels of the given band.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short getBandLevel(short band);

	/**
	 * Gets the level range for use by {@link #setBandLevel(short,short)}. The
	 * level is expressed in milliBel.
	 *
	 * @return the band level range in an array of short integers. The first
	 *         element is the lower limit of the range, the second element the
	 *         upper limit.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short[] getBandLevelRange();

	/**
	 * Gets the center frequency of the given band.
	 *
	 * @param band frequency band whose center frequency is requested. The
	 *            numbering of the bands starts from 0 and ends at (number of
	 *            bands - 1).
	 * @return the center frequency in milliHertz
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	int getCenterFreq(short band);

	/**
	 * Gets current preset.
	 *
	 * @return the preset that is set at the moment.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short getCurrentPreset();

	/**
	 * Gets the number of frequency bands supported by the Equalizer engine.
	 *
	 * @return the number of bands
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short getNumberOfBands();

	/**
	 * Gets the total number of presets the equalizer supports. The presets will
	 * have indices [0, number of presets-1].
	 *
	 * @return the number of presets the equalizer supports.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short getNumberOfPresets();

	/**
	 * Gets the preset name based on the index.
	 *
	 * @param preset index of the preset. The valid range is [0, number of
	 *            presets-1].
	 * @return a string containing the name of the given preset.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	String getPresetName(short preset);

	/**
	 * Sets the given equalizer band to the given gain value.
	 *
	 * @param band frequency band that will have the new gain. The numbering of
	 *            the bands starts from 0 and ends at (number of bands - 1).
	 * @param level new gain in millibels that will be set to the given band.
	 *            getBandLevelRange() will define the maximum and minimum
	 *            values.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 * @see #getNumberOfBands()
	 */
	void setBandLevel(short band, short level);

	/**
	 * Sets the equalizer according to the given preset.
	 *
	 * @param preset new preset that will be taken into use. The valid range is
	 *            [0, number of presets-1].
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 * @see #getNumberOfPresets()
	 */
	void usePreset(short preset);

	/**
	 * Gets the equalizer properties. This method is useful when a snapshot of
	 * current equalizer settings must be saved by the application.
	 *
	 * @return an IEqualizer.Settings object containing all current parameters
	 *         values
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	IEqualizer.Settings getProperties();

	/**
	 * Sets the equalizer properties. This method is useful when equalizer
	 * settings have to be applied from a previous backup.
	 *
	 * @param settings an IEqualizer.Settings object containing the properties
	 *            to apply
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	void setProperties(IEqualizer.Settings settings);
}
