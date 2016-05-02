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

final class Equalizer implements IEqualizer {
	private final android.media.audiofx.Equalizer equalizer;

	public Equalizer(int audioSession) {
		equalizer = new android.media.audiofx.Equalizer(0, audioSession);
	}

	@Override
	public short[] getBandLevelRange() {
		return equalizer.getBandLevelRange();
	}

	@Override
	public int getCenterFreq(short band) {
		return equalizer.getCenterFreq(band);
	}

	@Override
	public short getNumberOfBands() {
		return equalizer.getNumberOfBands();
	}

	@Override
	public void setBandLevel(short band, short level) {
		equalizer.setBandLevel(band, level);
	}

	@Override
	public void setProperties(short numBands, short[] bandLevels) {
		android.media.audiofx.Equalizer.Settings settings = new android.media.audiofx.Equalizer.Settings();

		settings.curPreset = -1;
		settings.numBands = numBands;
		settings.bandLevels = bandLevels;

		equalizer.setProperties(settings);
	}

	@Override
	public int setEnabled(boolean enabled) {
		return equalizer.setEnabled(enabled);
	}

	@Override
	public boolean getEnabled() {
		return equalizer.getEnabled();
	}

	@Override
	public void release() {
		equalizer.release();
	}
}
