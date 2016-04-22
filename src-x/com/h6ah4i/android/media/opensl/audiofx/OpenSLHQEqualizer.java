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

import java.nio.charset.Charset;

import android.util.Log;

import com.h6ah4i.android.media.audiofx.IEqualizer;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayer;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayerContext;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayerNativeLibraryLoader;

public final class OpenSLHQEqualizer extends OpenSLAudioEffect implements IEqualizer {
	private static final String TAG = "HQEqualizer";

	private long mNativeHandle;
	private static final boolean HAS_NATIVE;
	private final int[] mParamIntBuff;
	private final short[] mParamShortBuff;
	private final boolean[] mParamBoolBuff;

	static {
		// load native library
		HAS_NATIVE = OpenSLMediaPlayerNativeLibraryLoader.loadLibraries();
	}

	public OpenSLHQEqualizer(OpenSLMediaPlayerContext context) {
		if (context == null)
			throw new IllegalArgumentException("The argument 'context' cannot be null");

		if (HAS_NATIVE)
			mNativeHandle = createNativeImplHandle(OpenSLMediaPlayer.Internal.getNativeHandle(context));

		if (mNativeHandle == 0)
			throw new UnsupportedOperationException("Failed to initialize native layer");

		mParamIntBuff = new int[32];
		mParamShortBuff = new short[1];
		mParamBoolBuff = new boolean[1];
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
		try {
			checkNativeImplIsAvailable();

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
	public short getBand(int frequency) {
		checkNativeImplIsAvailable();

		final short[] band = mParamShortBuff;
		final int result = getBandImplNative(mNativeHandle, frequency, band);

		parseResultAndThrowExceptForIOExceptions(result);

		return band[0];
	}

	@Override
	public int[] getBandFreqRange(short band) {
		checkNativeImplIsAvailable();

		final int[] range = new int[2];
		final int result = getBandFreqRangeImplNative(mNativeHandle, band, range);

		parseResultAndThrowExceptForIOExceptions(result);

		return range;
	}

	@Override
	public short getBandLevel(short band) {
		checkNativeImplIsAvailable();

		final short[] level = mParamShortBuff;
		final int result = getBandLevelImplNative(mNativeHandle, band, level);

		parseResultAndThrowExceptForIOExceptions(result);

		return level[0];
	}

	@Override
	public short[] getBandLevelRange() {
		checkNativeImplIsAvailable();

		final short[] range = new short[2];
		final int result = getBandLevelRangeImplNative(mNativeHandle, range);

		parseResultAndThrowExceptForIOExceptions(result);

		return range;
	}

	@Override
	public int getCenterFreq(short band) {
		checkNativeImplIsAvailable();

		final int[] freq = mParamIntBuff;
		final int result = getCenterFreqImplNative(mNativeHandle, band, freq);

		parseResultAndThrowExceptForIOExceptions(result);

		return freq[0];
	}

	@Override
	public short getCurrentPreset() {
		checkNativeImplIsAvailable();

		final short[] preset = mParamShortBuff;
		final int result = getCurrentPresetImplNative(mNativeHandle, preset);

		parseResultAndThrowExceptForIOExceptions(result);

		return preset[0];
	}

	@Override
	public short getNumberOfBands() {
		checkNativeImplIsAvailable();

		final short[] num_bands = mParamShortBuff;
		final int result = getNumberOfBandsImplNative(mNativeHandle, num_bands);

		parseResultAndThrowExceptForIOExceptions(result);

		return num_bands[0];
	}

	@Override
	public short getNumberOfPresets() {
		checkNativeImplIsAvailable();

		final short[] num_presets = mParamShortBuff;
		final int result = getNumberOfPresetsImplNative(mNativeHandle, num_presets);

		parseResultAndThrowExceptForIOExceptions(result);

		return num_presets[0];
	}

	@Override
	public String getPresetName(short preset) {
		checkNativeImplIsAvailable();

		try {
			final byte[] buff = new byte[128];
			final int result = getPresetNameImplNative(mNativeHandle, preset, buff);

			parseResultAndThrowExceptForIOExceptions(result);

			int len = 0;

			while (len < buff.length && buff[len] != 0)
				len++;

			return new String(buff, 0, len, Charset.forName("UTF-8"));
		} catch (IllegalArgumentException e) {
			return "";
		} catch (IllegalStateException e) {
			return "";
		}
	}

	@Override
	public void setBandLevel(short band, short level) {
		checkNativeImplIsAvailable();

		final int result = setBandLevelImplNative(mNativeHandle, band, level);

		parseResultAndThrowExceptForIOExceptions(result);
	}

	@Override
	public void usePreset(short preset) {
		checkNativeImplIsAvailable();

		final int result = usePresetImplNative(mNativeHandle, preset);

		parseResultAndThrowExceptForIOExceptions(result);
	}

	@Override
	public IEqualizer.Settings getProperties() {
		checkNativeImplIsAvailable();

		final int[] values = mParamIntBuff;

		final int result = getPropertiesImplNative(mNativeHandle, values);

		parseResultAndThrowExceptForIOExceptions(result);

		final IEqualizer.Settings settings = new Settings();

		settings.curPreset = (short)(values[0] & 0xffff);
		settings.numBands = (short)(values[1] & 0xffff);
		settings.bandLevels = new short[settings.numBands];
		for (int i = 0; i < settings.numBands; i++)
			settings.bandLevels[i] = (short)(values[2 + i] & 0xffff);

		return settings;
	}

	@Override
	public void setProperties(IEqualizer.Settings settings) {
		checkNativeImplIsAvailable();

		if (settings == null)
			throw new IllegalArgumentException("The argument 'settings' cannot be null");

		if (settings.bandLevels == null)
			throw new IllegalArgumentException("settings invalid property: bandLevels is null");

		if (settings.numBands != settings.bandLevels.length)
			throw new IllegalArgumentException("settings invalid band count: " + settings.numBands);

		final int[] values = mParamIntBuff;

		values[0] = settings.curPreset & 0xffff;
		values[1] = settings.numBands & 0xffff;
		for (int i = 0; i < settings.numBands; i++)
			values[i + 2] = settings.bandLevels[i] & 0xffff;

		final int result = setPropertiesImplNative(mNativeHandle, values);

		parseResultAndThrowExceptForIOExceptions(result);
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

	private static native int getBandImplNative(long handle, int frequency, short[] band);

	private static native int getBandFreqRangeImplNative(long handle, short band, int[] range);

	private static native int getBandLevelImplNative(long handle, short band, short[] level);

	private static native int getBandLevelRangeImplNative(long handle, short[] range);

	private static native int getCenterFreqImplNative(long handle, short band, int[] freq);

	private static native int getCurrentPresetImplNative(long handle, short[] preset);

	private static native int getNumberOfBandsImplNative(long handle, short[] num_bands);

	private static native int getNumberOfPresetsImplNative(long handle, short[] num_presets);

	private static native int getPresetNameImplNative(long handle, short preset, byte[] buffer);

	private static native int getPropertiesImplNative(long handle, int[] settings);

	private static native int setBandLevelImplNative(long handle, short band, short level);

	private static native int usePresetImplNative(long handle, short preset);

	private static native int setPropertiesImplNative(long handle, int[] settings);
}
