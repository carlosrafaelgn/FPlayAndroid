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
package br.com.carlosrafaelgn.fplay.playback.context;

public final class Equalizer {
	private static final short DB_RANGE = 1500; //+-15dB (in millibels)

	public short[] getBandLevelRange() {
		return new short[] { -DB_RANGE, DB_RANGE };
	}

	public int getCenterFreq(short band) {
		switch (band) {
		case 0:
			return 0;
		case 1:
			return (31250 + 62500) >> 1;
		case 2:
			return 125000;
		case 3:
			return 250000;
		case 4:
			return (500000 + 1000000) >> 1;
		case 5:
			return (2000000 + 4000000) >> 1;
		case 6:
			return 8000000;
		default:
			return 16000000;
		}
	}

	public short getNumberOfBands() {
		return 8;
	}

	public void setBandLevel(short band, short level) {
		MediaContext._setEqualizerBandLevel(band, level);
	}

	public void setProperties(short numBands, short[] bandLevels) {
		if (numBands != 8 || bandLevels == null || bandLevels.length < 8)
			return;
		MediaContext._setEqualizerBandLevels(bandLevels);
	}

	public int setEnabled(boolean enabled) {
		MediaContext._enableEqualizer(enabled ? 1 : 0);
		return 0;
	}

	public static void getFrequencyResponse(int bassBoostStrength, short[] levels, double[] frequencies, double[] gains) {
		MediaContext._getEqualizerFrequencyResponse(bassBoostStrength, levels, frequencies, gains);
	}

	public boolean getEnabled() {
		return (MediaContext.isEqualizerEnabled() != 0);
	}

	public void release() {
		MediaContext._enableEqualizer(0);
	}
}
