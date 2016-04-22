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
// /frameworks/base/media/java/android/media/audiofx/BassBoost.java
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

public interface IBassBoost extends IAudioEffect {

	/**
	 * Indicates whether setting strength is supported. If this method returns
	 * false, only one strength is supported and the setStrength() method always
	 * rounds to that value.
	 *
	 * @return true is strength parameter is supported, false otherwise
	 */
	boolean getStrengthSupported();

	/**
	 * Sets the strength of the bass boost effect. If the implementation does
	 * not support per mille accuracy for setting the strength, it is allowed to
	 * round the given strength to the nearest supported value. You can use the
	 * {@link #getRoundedStrength()} method to query the (possibly rounded)
	 * value that was actually set.
	 *
	 * @param strength strength of the effect. The valid range for strength
	 *            strength is [0, 1000], where 0 per mille designates the
	 *            mildest effect and 1000 per mille designates the strongest.
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	void setStrength(short strength);

	/**
	 * Gets the current strength of the effect.
	 *
	 * @return the strength of the effect. The valid range for strength is [0,
	 *         1000], where 0 per mille designates the mildest effect and 1000
	 *         per mille the strongest
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 * @throws UnsupportedOperationException
	 */
	short getRoundedStrength();
}
