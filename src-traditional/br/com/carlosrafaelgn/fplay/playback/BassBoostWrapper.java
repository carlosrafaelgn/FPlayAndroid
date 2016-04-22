package br.com.carlosrafaelgn.fplay.playback;

import android.media.audiofx.BassBoost;

import com.h6ah4i.android.media.audiofx.IBassBoost;

final class BassBoostWrapper implements IBassBoost {
	private final BassBoost bassBoost;

	public BassBoostWrapper(int audioSession) {
		bassBoost = new BassBoost(0, audioSession);
	}

	@Override
	public boolean getStrengthSupported() {
		return bassBoost.getStrengthSupported();
	}

	@Override
	public void setStrength(short strength) {
		bassBoost.setStrength(strength);
	}

	@Override
	public short getRoundedStrength() {
		return bassBoost.getRoundedStrength();
	}

	@Override
	public int setEnabled(boolean enabled) {
		return bassBoost.setEnabled(enabled);
	}

	@Override
	public boolean getEnabled() {
		return bassBoost.getEnabled();
	}

	@Override
	public void release() {
		bassBoost.release();
	}
}
