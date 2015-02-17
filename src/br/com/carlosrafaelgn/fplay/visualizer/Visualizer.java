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

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.view.ContextMenu;

import br.com.carlosrafaelgn.fplay.list.Song;

public interface Visualizer {
	public static final String EXTRA_VISUALIZER_CLASS_NAME = "br.com.carlosrafaelgn.fplay.ActivityVisualizer.VISUALIZER_CLASS_NAME";
	public static final int MAX_POINTS = 1024;
	public static final int MNU_VISUALIZER = 200;

	//Runs on the MAIN thread
	public void onActivityResult(int requestCode, int resultCode, Intent data);

	//Runs on the MAIN thread
	public void onActivityPause();

	//Runs on the MAIN thread
	public void onActivityResume();

	//Runs on the MAIN thread
	public void onCreateContextMenu(ContextMenu menu);

	//Runs on the MAIN thread
	public void onClick();

	//Runs on the MAIN thread
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, Throwable ex);

	//Runs on the MAIN thread (returned value MUST always be the same)
	public boolean isFullscreen();

	//Runs on the MAIN thread (called only if isFullscreen() returns false)
	public Point getDesiredSize(int availableWidth, int availableHeight);

	//Runs on the MAIN thread (returned value MUST always be the same)
	public boolean requiresScreen();

	//Runs on ANY thread (returned value MUST always be the same)
	public int getDesiredPointCount();
	
	//Runs on a SECONDARY thread
	public void load(Context context);
	
	//Runs on ANY thread
	public boolean isLoading();
	
	//Runs on the MAIN thread
	public void cancelLoading();
	
	//Runs on the MAIN thread
	public void configurationChanged(boolean landscape);
	
	//Runs on a SECONDARY thread
	public void processFrame(android.media.audiofx.Visualizer visualizer, boolean playing, int deltaMillis);
	
	//Runs on a SECONDARY thread
	public void release();

	//Runs on the MAIN thread (AFTER release())
	public void releaseView();
}
