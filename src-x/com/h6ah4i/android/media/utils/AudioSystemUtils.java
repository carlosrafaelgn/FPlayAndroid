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


package com.h6ah4i.android.media.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

public final class AudioSystemUtils {

	public static class AudioSystemProperties {
		public int outputSampleRate;
		public int outputFramesPerBuffer;
		public boolean supportLowLatency;
		public boolean supportFloatingPoint;
	}

	public static AudioSystemProperties getProperties(Context context) {
		AudioSystemProperties props = new AudioSystemProperties();

		//default values for JELLY_BEAN
		props.outputSampleRate = 44100;
		props.outputFramesPerBuffer = 512;

		//we will set low latency to false, because
		//AudioSystem::Impl::determine_output_frame_size() @ AudioSystem.cpp uses only one single buffer
		//for the output when in low latency mode
		//using the bass boost or the virtualizer also disables low latency mode, as it can be seen
		//in AudioSystem::Impl::check_is_low_latency()
		props.supportLowLatency = false; //context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);

		//force using kAudioSampleFormatType_S16 in AudioSystem::Impl::initSubmodules()
		props.supportFloatingPoint = false; //(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			getPropertiesJbMr1(context, props);

		//http://googlesamples.github.io/android-audio-high-performance/guides/audio-output-latency.html#use-the-optimal-buffer-size-when-enqueuing-audio-data
		//For example, on a Nexus 5 device the buffer size is 240 frames on build LMY48M so for this
		//device/build combination you should ideally supply a single buffer containing 240 frames
		//during every queue callback. If 240 frames is too small to maintain a reliable audio stream
		//you should use 480 frames (at the cost of increased latency) and so on, increasing buffer
		//size by 240 until you reach a good trade-off between latency and reliability.
		//
		//By multiplying by 2 we will increase the latency (which is not good) but we will also
		//increase the reliability (which is good).
		props.outputFramesPerBuffer <<= 1;

		return props;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private static void getPropertiesJbMr1(Context context, AudioSystemProperties props) {
		try {
			final AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
			final String strSampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
			final String strFramesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);

			final int sampleRate = Integer.parseInt(strSampleRate);
			final int framesPerBuffer = Integer.parseInt(strFramesPerBuffer);

			if (sampleRate > 0)
				props.outputSampleRate = sampleRate;
			if (framesPerBuffer > 0)
				props.outputFramesPerBuffer = framesPerBuffer;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}
}
