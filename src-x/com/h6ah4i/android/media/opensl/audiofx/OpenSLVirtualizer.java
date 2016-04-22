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


package com.h6ah4i.android.media.opensl.audiofx;

import android.util.Log;

import com.h6ah4i.android.media.audiofx.IVirtualizer;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayer;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayerContext;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayerNativeLibraryLoader;

public final class OpenSLVirtualizer extends OpenSLAudioEffect implements IVirtualizer {
	private static final String TAG = "Virtualizer";

	// fields
	private long mNativeHandle;
	private static final boolean HAS_NATIVE;
	private final boolean mStrengthSupported;
	private final short[] mParamShortBuff;
	private final boolean[] mParamBoolBuff;

	static {
		// load native library
		HAS_NATIVE = OpenSLMediaPlayerNativeLibraryLoader.loadLibraries();
	}

	public OpenSLVirtualizer(OpenSLMediaPlayerContext context) {
		if (context == null)
			throw new IllegalArgumentException("The argument 'context' cannot be null");

		if (HAS_NATIVE)
			mNativeHandle = createNativeImplHandle(OpenSLMediaPlayer.Internal.getNativeHandle(context));

		if (mNativeHandle == 0)
			throw new UnsupportedOperationException("Failed to initialize native layer");

		mParamShortBuff = new short[1];
		mParamBoolBuff = new boolean[1];

		mStrengthSupported = getStrengthSupportedInternal();
	}

	private boolean getStrengthSupportedInternal() {
		final boolean[] strengthSupported = mParamBoolBuff;
		strengthSupported[0] = false;
		if (mNativeHandle != 0) {
			if (getStrengthSupportedImplNative(mNativeHandle, strengthSupported) != SUCCESS) {
				strengthSupported[0] = false;
			}
		}
		return strengthSupported[0];
	}

	@Override
	protected void finalize() throws Throwable {
		release();
		super.finalize();
	}

	@Override
	public void release() {
		try {
			if (HAS_NATIVE && mNativeHandle != 0) {
				deleteNativeImplHandle(mNativeHandle);
				mNativeHandle = 0;
			}
		} catch (Exception e) {
			Log.e(TAG, "release()", e);
		}
	}

	@Override
	public int setEnabled(boolean enabled) {
		checkNativeImplIsAvailable();

		try {
			final int result = setEnabledImplNative(mNativeHandle, enabled);

			parseResultAndThrowExceptForIOExceptions(result);

			return SUCCESS;
		} catch (UnsupportedOperationException e) {
			return ERROR_INVALID_OPERATION;
		}
	}

	@Override
	public boolean getEnabled() {
		checkNativeImplIsAvailable();

		final boolean[] enabled = mParamBoolBuff;
		final int result = getEnabledImplNative(mNativeHandle, enabled);

		if (result == OpenSLMediaPlayer.Internal.RESULT_CONTROL_LOST)
			return false;

		parseResultAndThrowExceptForIOExceptions(result);

		return enabled[0];
	}

	@Override
	public boolean getStrengthSupported() {
		return mStrengthSupported;
	}

	@Override
	public void setStrength(short strength) {
		checkNativeImplIsAvailable();
		final int result = setStrengthImplNative(mNativeHandle, strength);

		parseResultAndThrowExceptForIOExceptions(result);
	}

	@Override
	public short getRoundedStrength() {
		checkNativeImplIsAvailable();
		final short[] roundedStrength = mParamShortBuff;
		final int result = getRoundedStrengthImplNative(mNativeHandle, roundedStrength);

		parseResultAndThrowExceptForIOExceptions(result);

		return roundedStrength[0];
	}

	//
	// Utilities
	//

	private void checkNativeImplIsAvailable() {
		if (mNativeHandle == 0)
			throw new IllegalStateException("Native implemenation handle is not present");
	}

	//
	// Native methods
	//
	private static native long createNativeImplHandle(long context_handle);

	private static native void deleteNativeImplHandle(long handle);

	private static native int setEnabledImplNative(long handle, boolean enabled);

	private static native int getEnabledImplNative(long handle, boolean[] enabled);

	private static native int getIdImplNative(long handle, int[] id);

	private static native int hasControlImplNative(long handle, boolean[] hasControl);

	private static native int getStrengthSupportedImplNative(long handle, boolean[] strengthSupported);

	private static native int getRoundedStrengthImplNative(long handle, short[] roundedStrength);

	private static native int getPropertiesImplNative(long handle, int[] settings);

	private static native int setStrengthImplNative(long handle, short strength);

	private static native int setPropertiesImplNative(long handle, int[] settings);
}
