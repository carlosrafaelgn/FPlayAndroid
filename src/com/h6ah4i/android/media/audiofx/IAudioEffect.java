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
// /frameworks/base/media/java/android/media/audiofx/AudioEffect.java
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

import com.h6ah4i.android.media.IReleasable;

public interface IAudioEffect extends IReleasable {
	/**
	 * Successful operation.
	 */
	int SUCCESS = android.media.audiofx.AudioEffect.SUCCESS;
	/**
	 * Unspecified error.
	 */
	int ERROR = android.media.audiofx.AudioEffect.ERROR;
	/**
	 * Internal operation status. Not returned by any method.
	 */
	int ALREADY_EXISTS = android.media.audiofx.AudioEffect.ALREADY_EXISTS;
	/**
	 * Operation failed due to bad object initialization.
	 */
	int ERROR_NO_INIT = android.media.audiofx.AudioEffect.ERROR_NO_INIT;
	/**
	 * Operation failed due to bad parameter value.
	 */
	int ERROR_BAD_VALUE = android.media.audiofx.AudioEffect.ERROR_BAD_VALUE;
	/**
	 * Operation failed because it was requested in wrong state.
	 */
	int ERROR_INVALID_OPERATION = android.media.audiofx.AudioEffect.ERROR_INVALID_OPERATION;
	/**
	 * Operation failed due to lack of memory.
	 */
	int ERROR_NO_MEMORY = android.media.audiofx.AudioEffect.ERROR_NO_MEMORY;
	/**
	 * Operation failed due to dead remote object.
	 */
	int ERROR_DEAD_OBJECT = android.media.audiofx.AudioEffect.ERROR_DEAD_OBJECT;

	/**
	 * Enable or disable the effect. Creating an audio effect does not
	 * automatically apply this effect on the audio source. It creates the
	 * resources necessary to process this effect but the audio signal is still
	 * bypassed through the effect engine. Calling this method will make that
	 * the effect is actually applied or not to the audio content being played
	 * in the corresponding audio session.
	 *
	 * @param enabled the requested enable state
	 * @return {@link #SUCCESS} in case of success,
	 *         {@link #ERROR_INVALID_OPERATION} or {@link #ERROR_DEAD_OBJECT} in
	 *         case of failure.
	 * @throws IllegalStateException
	 */
	int setEnabled(boolean enabled);

	/**
	 * Returns effect enabled state
	 *
	 * @return true if the effect is enabled, false otherwise.
	 * @throws IllegalStateException
	 */
	boolean getEnabled();

	/**
	 * Releases the native AudioEffect resources. It is a good practice to
	 * release the effect engine when not in use as control can be returned to
	 * other applications or the native resources released.
	 */
	@Override
	void release();
}
