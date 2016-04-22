package br.com.carlosrafaelgn.fplay.playback;

import android.media.audiofx.Equalizer;

import com.h6ah4i.android.media.audiofx.IEqualizer;

final class EqualizerWrapper implements IEqualizer {
	private final Equalizer equalizer;

	public EqualizerWrapper(int audioSession) {
		equalizer = new Equalizer(0, audioSession);
	}

	@Override
	public short getBand(int frequency) {
		return equalizer.getBand(frequency);
	}

	@Override
	public int[] getBandFreqRange(short band) {
		return equalizer.getBandFreqRange(band);
	}

	@Override
	public short getBandLevel(short band) {
		return equalizer.getBandLevel(band);
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
	public short getCurrentPreset() {
		return equalizer.getCurrentPreset();
	}

	@Override
	public short getNumberOfBands() {
		return equalizer.getNumberOfBands();
	}

	@Override
	public short getNumberOfPresets() {
		return equalizer.getNumberOfPresets();
	}

	@Override
	public String getPresetName(short preset) {
		return equalizer.getPresetName(preset);
	}

	@Override
	public void setBandLevel(short band, short level) {
		equalizer.setBandLevel(band, level);
	}

	@Override
	public void usePreset(short preset) {
		equalizer.usePreset(preset);
	}

	@Override
	public Settings getProperties() {
		Equalizer.Settings settings = equalizer.getProperties();
		Settings settings2 = new Settings();

		settings2.curPreset = settings.curPreset;
		settings2.numBands = settings.numBands;
		settings2.bandLevels = settings.bandLevels;

		return settings2;
	}

	@Override
	public void setProperties(Settings settings) {
		Equalizer.Settings settings2 = new Equalizer.Settings();

		settings2.curPreset = settings.curPreset;
		settings2.numBands = settings.numBands;
		settings2.bandLevels = settings.bandLevels;

		equalizer.setProperties(settings2);
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
