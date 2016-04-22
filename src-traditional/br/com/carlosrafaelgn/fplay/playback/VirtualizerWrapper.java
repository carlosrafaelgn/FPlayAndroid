package br.com.carlosrafaelgn.fplay.playback;

import android.media.audiofx.Virtualizer;

import com.h6ah4i.android.media.audiofx.IVirtualizer;

final class VirtualizerWrapper implements IVirtualizer {
	private final Virtualizer virtualizer;
	
	public VirtualizerWrapper(int audioSession) {
		virtualizer = new Virtualizer(0, audioSession);
	}

	@Override
	public boolean getStrengthSupported() {
		return virtualizer.getStrengthSupported();
	}

	@Override
	public void setStrength(short strength) {
		virtualizer.setStrength(strength);
	}

	@Override
	public short getRoundedStrength() {
		return virtualizer.getRoundedStrength();
	}

	@Override
	public int setEnabled(boolean enabled) {
		return virtualizer.setEnabled(enabled);
	}

	@Override
	public boolean getEnabled() {
		return virtualizer.getEnabled();
	}

	@Override
	public void release() {
		virtualizer.release();
	}
}
