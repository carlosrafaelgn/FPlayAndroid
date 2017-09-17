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
package br.com.carlosrafaelgn.fplay.plugin;

@SuppressWarnings("unused")
public interface Visualizer {
	String EXTRA_VISUALIZER_CLASS_NAME = "br.com.carlosrafaelgn.fplay.ActivityVisualizer.VISUALIZER_CLASS_NAME";

	int MNU_VISUALIZER = 200;

	int CAPTURE_SIZE = 1024;

	int DATA_NONE = 0x0000;
	int DATA_FFT = 0x0100;
	int DATA_VUMETER = 0x0200;
	int IGNORE_INPUT = 0x0400;

	int BEAT_DETECTION_1 = 0x1000;
	int BEAT_DETECTION_2 = 0x2000;
	int BEAT_DETECTION_3 = 0x3000;
	int BEAT_DETECTION_4 = 0x4000;
	int BEAT_DETECTION_5 = 0x5000;
	int BEAT_DETECTION_6 = 0x6000;
	int BEAT_DETECTION_7 = 0x7000;
	int BEAT_DETECTION = 0xF000;

	int ORIENTATION_NONE = 0;
	int ORIENTATION_LANDSCAPE = 1;
	int ORIENTATION_PORTRAIT = 2;

	//Runs on the MAIN thread
	void onActivityResult(int requestCode, int resultCode, Object intent);

	//Runs on the MAIN thread
	void onActivityPause();

	//Runs on the MAIN thread
	void onActivityResume();

	//Runs on the MAIN thread
	void onCreateContextMenu(Object contextMenu);

	//Runs on the MAIN thread
	void onClick();

	//Runs on the MAIN thread
	void onPlayerChanged(SongInfo currentSongInfo, boolean songHasChanged, Throwable ex);

	//Runs on the MAIN thread (returned value MUST always be the same)
	boolean isFullscreen();

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	Object getDesiredSize(int availableWidth, int availableHeight);

	//Runs on the MAIN thread (returned value MUST always be the same)
	boolean requiresHiddenControls();

	//Runs on ANY thread
	int requiredDataType();

	//Runs on ANY thread
	int requiredOrientation();

	//Runs on a SECONDARY thread
	void load();
	
	//Runs on ANY thread
	boolean isLoading();
	
	//Runs on the MAIN thread
	void cancelLoading();
	
	//Runs on the MAIN thread
	void configurationChanged(boolean landscape);
	
	//Runs on a SECONDARY thread
	void processFrame(boolean playing, byte[] waveform);
	
	//Runs on a SECONDARY thread
	void release();

	//Runs on the MAIN thread (AFTER release())
	void releaseView();
}
