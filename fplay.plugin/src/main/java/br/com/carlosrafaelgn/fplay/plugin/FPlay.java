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
public interface FPlay {
	int STR_VISUALIZER_NOT_SUPPORTED = 0x0001;

	int getApiVersion();
	int getFPlayVersionCode();
	String getFPlayVersionName();

	boolean isAlive();
	Object getApplicationContext();

	boolean isOnMainThread();
	void postToMainThread(Runnable runnable);
	void postToMainThreadAtTime(Runnable runnable, long uptimeMillis);
	void sendMessage(Object callback, int what);
	void sendMessage(Object callback, int what, int arg1, int arg2);
	void sendMessageAtTime(Object callback, int what, int arg1, int arg2, long uptimeMillis);
	void removeMessages(Object callback, int what);

	boolean deviceHasTelephonyRadio();
	boolean isConnectedToTheInternet();
	boolean isInternetConnectedViaWiFi();
	String getWiFiIpAddress();

	String emoji(CharSequence text);
	void toast(String message);

	<E> ItemSelectorDialog<E> showItemSelectorDialog(Object activity, CharSequence title, CharSequence loadingMessage, CharSequence connectingMessage, boolean progressBarVisible, Class<E> clazz, E[] initialElements, ItemSelectorDialog.Observer<E> observer);

	String getString(int str);
	void fixLocale(Object context);
	String formatIntAsFloat(int number, boolean useTwoDecimalPlaces, boolean removeDecimalPlacesIfExact);
	void formatIntAsFloat(StringBuilder sb, int number, boolean useTwoDecimalPlaces, boolean removeDecimalPlacesIfExact);
	int dpToPxI(float dp);
	int spToPxI(float sp);

	VisualizerService createVisualizerService(Visualizer visualizer, VisualizerService.Observer observer);
	void visualizerSetSpeed(int speed);
	void visualizerSetColorIndex(int colorIndex);
	void visualizerUpdateMultiplier(boolean isVoice, boolean hq);
	int visualizerProcess(byte[] waveform, int opt);

	void previous();
	void pause();
	void resume();
	void playPause();
	void next();
	void setVolumeInPercentage(int percentage);
	int getVolumeInPercentage();
	int increaseVolume();
	int decreaseVolume();
	boolean isPlaying();
	boolean isPreparing();
	int getPlaybackPosition();
	boolean isMediaButton(int keyCode);
	boolean handleMediaButton(int keyCode);
	boolean currentSongInfo(SongInfo info);
	String formatTime(int timeMS);

	int getPlaylistVersion();
	int getPlaylistCount();
	void getPlaylistSongInfo(int index, SongInfo info);
}
