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

package br.com.carlosrafaelgn.fplay.playback;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;

import com.h6ah4i.android.media.opensl.OpenSLMediaPlayer;
import com.h6ah4i.android.media.opensl.OpenSLMediaPlayerContext;
import com.h6ah4i.android.media.opensl.audiofx.OpenSLBassBoost;
import com.h6ah4i.android.media.opensl.audiofx.OpenSLHQEqualizer;
import com.h6ah4i.android.media.opensl.audiofx.OpenSLHQVisualizer;
import com.h6ah4i.android.media.opensl.audiofx.OpenSLVirtualizer;

public final class MediaFactory {
	private static OpenSLMediaPlayerContext mediaPlayerContext;

	public static void _initialize(Context context) {
		final OpenSLMediaPlayerContext.Parameters params = new OpenSLMediaPlayerContext.Parameters();

		// override parameters
		//final boolean hasCyanogenModDSPManager = (getEqualizerNumberOfBands() == 6);

		// NOTE:
		// CyanogenMod causes app crash if those effects are enabled
		//if (!hasCyanogenModDSPManager) {
		//	params.options |= OpenSLMediaPlayerContext.OPTION_USE_BASSBOOST;
		//	params.options |= OpenSLMediaPlayerContext.OPTION_USE_VIRTUALIZER;
		//}

		params.options |= OpenSLMediaPlayerContext.OPTION_USE_HQ_EQUALIZER;
		params.options |= OpenSLMediaPlayerContext.OPTION_USE_HQ_VISUALIZER;

		// https://github.com/h6ah4i/android-openslmediaplayer/wiki/OpenSL-prefixed-API-classes#resampler_quality_-constants
		// https://github.com/h6ah4i/android-openslmediaplayer/wiki/OpenSL-prefixed-API-classes#hq_eaualizer_impl_-constants
		params.resamplerQuality = OpenSLMediaPlayerContext.RESAMPLER_QUALITY_LOW;
		params.hqEqualizerImplType = OpenSLMediaPlayerContext.HQ_EAUALIZER_IMPL_FLAT_GAIN_RESPOINSE;

		mediaPlayerContext = new OpenSLMediaPlayerContext(context, params);
	}

	public static void _release() {
		if (mediaPlayerContext != null) {
			mediaPlayerContext.release();
			mediaPlayerContext = null;
		}
	}

	public static OpenSLMediaPlayer createMediaPlayer() {
		return new OpenSLMediaPlayer(mediaPlayerContext);
	}

	public static OpenSLHQEqualizer createEqualizer() {
		return new OpenSLHQEqualizer(mediaPlayerContext);
	}

	public static OpenSLBassBoost createBassBoost() {
		return new OpenSLBassBoost(mediaPlayerContext);
	}

	public static OpenSLVirtualizer createVirtualizer() {
		return new OpenSLVirtualizer(mediaPlayerContext);
	}

	public static OpenSLHQVisualizer createVisualizer() {
		return new OpenSLHQVisualizer(mediaPlayerContext);
	}

	private static int getEqualizerNumberOfBands() {
		MediaPlayer player = null;
		Equalizer eq = null;
		try {
			player = new MediaPlayer();
			eq = new Equalizer(0, player.getAudioSessionId());
			return eq.getNumberOfBands();
		} catch (Throwable ex) {
			ex.printStackTrace();
		} finally {
			if (eq != null)
				eq.release();
			if (player != null)
				player.release();
		}
		return 0;
	}
}
