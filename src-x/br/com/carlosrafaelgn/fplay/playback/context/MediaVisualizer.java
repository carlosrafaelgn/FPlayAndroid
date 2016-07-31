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

import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.util.Timer;
import br.com.carlosrafaelgn.fplay.visualizer.Visualizer;

public final class MediaVisualizer implements Runnable, Timer.TimerHandler {
	public interface Handler {
		void onFailure();
		void onFinalCleanup();
	}
	private Visualizer visualizer;
	private Handler handler;
	private volatile boolean alive, reset, created, playing, failed, visualizerReady;
	private byte[] waveform;
	private Timer timer;

	public MediaVisualizer(Visualizer visualizer, Handler handler) {
		this.visualizer = visualizer;
		this.handler = handler;
		alive = true;
		reset = true;
		playing = Player.localPlaying;
		waveform = new byte[Visualizer.CAPTURE_SIZE];
		timer = new Timer(this, "Visualizer Thread", false, false, true);
		timer.start(16);
	}

	public void playingChanged() {
		playing = Player.localPlaying;
	}

	public void pause() {
		if (timer != null)
			timer.pause();
	}

	public void resume() {
		if (timer != null)
			timer.resume();
	}

	public void resetAndResume() {
		//unlike the traditional visualizer, there is no need to reset this visualizer
		//(we only need to zero it out)
		reset = true;
		if (timer != null)
			timer.resume();
	}

	public void destroy() {
		if (timer != null) {
			alive = false;
			if (visualizer != null)
				visualizer.cancelLoading();
			timer.resume();
			timer = null;
		}
	}

	@Override
	public void run() {
		if (failed) {
			failed = false;
			if (handler != null)
				handler.onFailure();
		}
		if (handler != null) {
			handler.onFinalCleanup();
			handler = null;
		}
		waveform = null;
		timer = null;
		visualizer = null;
	}

	@Override
	public void handleTimer(Timer timer, Object param) {
		if (alive) {
			if (reset) {
				reset = false;
				if (created) {
					MediaContext.zeroOutVisualizer();
				} else {
					if (!MediaContext.startVisualizer()) {
						created = false;
						failed = true;
						alive = false;
					} else if (!visualizerReady && alive && visualizer != null) {
						visualizer.load();
						visualizerReady = true;
						created = true;
					}
				}
			}
			if (visualizer != null) {
				if (playing)
					MediaContext.getVisualizerWaveform(waveform);
				visualizer.processFrame(playing, waveform);
			}
		}
		if (!alive) {
			timer.release();
			if (visualizer != null)
				visualizer.release();
			MediaContext.stopVisualizer();
			MainHandler.postToMainThread(this);
			System.gc();
		}
	}
}
