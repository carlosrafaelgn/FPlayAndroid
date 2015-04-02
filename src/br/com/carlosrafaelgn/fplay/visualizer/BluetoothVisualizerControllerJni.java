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
package br.com.carlosrafaelgn.fplay.visualizer;

import android.app.Activity;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.util.BluetoothConnectionManager;
import br.com.carlosrafaelgn.fplay.util.SlimLock;

public class BluetoothVisualizerControllerJni {
	private static final int MSG_UPDATE_PACKAGES = 0x0600;
	private static final int MSG_PLAYER_COMMAND = 0x0601;
	private static final int MSG_BLUETOOTH_RXTX_ERROR = 0x0602;

	private static final int[] FRAMES_TO_SKIP = { 0, 1, 2, 3, 4, 5, 9, 11, 14, 19, 29, 59 };

	private static final int FlagState = 0x07;
	private static final int FlagEscape = 0x08;

	private static final int StartOfHeading = 0x01;
	private static final int Escape = 0x1b;
	private static final int EndOfTransmission = 0x04;

	private static final int MessageBins4 = 0x20;
	private static final int MessageBins8 = 0x21;
	private static final int MessageBins16 = 0x22;
	private static final int MessageBins32 = 0x23;
	private static final int MessageBins64 = 0x24;
	private static final int MessageBins128 = 0x25;
	private static final int MessageBins256 = 0x26;
	private static final int MessageStartBinTransmission = 0x30;
	private static final int PayloadBins4 = MessageBins4;
	private static final int PayloadBins8 = MessageBins8;
	private static final int PayloadBins16 = MessageBins16;
	private static final int PayloadBins32 = MessageBins32;
	private static final int PayloadBins64 = MessageBins64;
	private static final int PayloadBins128 = MessageBins128;
	private static final int PayloadBins256 = MessageBins256;
	private static final int MessageStopBinTransmission = 0x31;
	private static final int MessagePlayerCommand = 0x32;
	private static final int PayloadPlayerCommandUpdateState = 0x00;
	private static final int PayloadPlayerCommandPrevious = 0x58; //KeyEvent.KEYCODE_MEDIA_PREVIOUS
	private static final int PayloadPlayerCommandPlayPause = 0x55; //KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
	private static final int PayloadPlayerCommandNext = 0x57; //KeyEvent.KEYCODE_MEDIA_NEXT
	private static final int PayloadPlayerCommandPlay = 0x7e; //KeyEvent.KEYCODE_MEDIA_PLAY
	private static final int PayloadPlayerCommandPause = 0x56; //KeyEvent.KEYCODE_MEDIA_STOP
	private static final int PayloadPlayerCommandIncreaseVolume = 0x18; //KeyEvent.KEYCODE_VOLUME_UP
	private static final int PayloadPlayerCommandDecreaseVolume = 0x19; //KeyEvent.KEYCODE_VOLUME_DOWN
	private static final int PayloadPlayerCommandSetVolume = 0xd1; //KeyEvent.KEYCODE_MUSIC
	private static final int MessagePlayerState = 0x33;
	private static final int PayloadPlayerStateFlagPlaying = 0x01;
	private static final int PayloadPlayerStateFlagLoading = 0x02;

	public int lastMessage;
	private FxVisualizer fxVisualizer;
	private byte[] bfft;
	private final SlimLock lock;
	private BluetoothConnectionManager bt;
	private int speed;
	private final AtomicInteger state;
	private volatile int size, packagesSent, version, framesToSkip, framesToSkipOriginal, stateVolume, stateSongPosition, stateSongLength;
	private volatile boolean connected, transmitting;
	private Activity activity;
	private long lastPlayerCommandTime;

	public BluetoothVisualizerControllerJni(int size, int speed, int framesToSkip) {
		bfft = new byte[1024];
		lock = new SlimLock();
		state = new AtomicInteger();
		setSize(size);
		setSpeed(speed);
		setFramesToSkip(framesToSkip);
		lastPlayerCommandTime = SystemClock.uptimeMillis();
		SimpleVisualizerJni.commonUpdateMultiplier(false);
	}

	public void setSize(int size) {
		this.size = ((size <= 0) ? PayloadBins4 : ((size >= 6) ? PayloadBins256 : (PayloadBins4 + size)));
	}

	public void setSpeed(int speed) {
		this.speed = ((speed <= 0) ? 0 : ((size >= 2) ? 2 : 1));
		SimpleVisualizerJni.commonSetSpeed(speed);
	}

	public void setFramesToSkip(int framesToSkip) {
		framesToSkipOriginal = ((framesToSkip <= 0) ? FRAMES_TO_SKIP[0] : ((framesToSkip >= FRAMES_TO_SKIP.length) ? FRAMES_TO_SKIP[FRAMES_TO_SKIP.length - 1] : FRAMES_TO_SKIP[framesToSkip]));
		this.framesToSkip = framesToSkipOriginal;
	}

	public void playingChanged() {
		fxVisualizer.playingChanged();
	}

	public void pause() {
		fxVisualizer.pause();
	}

	public void resume() {
		if (fxVisualizer != null)
			fxVisualizer.resume();
	}

	public void resetAndResume() {
		if (fxVisualizer != null)
			fxVisualizer.resetAndResume();
	}

	public void destroy() {
		if (fxVisualizer != null) {
			fxVisualizer.destroy();
			fxVisualizer = null;
		}
	}
}
