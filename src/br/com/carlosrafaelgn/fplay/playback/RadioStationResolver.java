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
//	list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//	this list of conditions and the following disclaimer in the documentation
//	and/or other materials provided with the distribution.
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
package br.com.carlosrafaelgn.fplay.playback;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import br.com.carlosrafaelgn.fplay.list.IcecastRadioStationList;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.list.RadioStationList;
import br.com.carlosrafaelgn.fplay.list.ShoutcastRadioStationList;
import br.com.carlosrafaelgn.fplay.util.TypedRawArrayList;

public final class RadioStationResolver {
	private final Object sync;
	private volatile boolean alive;
	private final String m3uUrl, title;
	private final boolean isShoutcast;
	private RadioStationList radioStationList;

	public RadioStationResolver(String m3uUrl, String title, boolean isShoutcast) {
		sync = new Object();
		alive = true;
		this.m3uUrl = m3uUrl;
		this.title = title;
		this.isShoutcast = isShoutcast;
	}

	public static String resolveStreamUrlFromM3uUrl(String m3uUrl, int[] resultCode) {
		int err = 0;
		InputStream is = null;
		InputStreamReader isr = null;
		BufferedReader br = null;
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = Player.createConnection(m3uUrl);
			err = urlConnection.getResponseCode();
			if (err == 200) {
				is = urlConnection.getInputStream();
				isr = new InputStreamReader(is, "UTF-8");
				br = new BufferedReader(isr, 1024);
				TypedRawArrayList<String> foundUrls = new TypedRawArrayList<>(String.class, 8);
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.length() > 0 && line.charAt(0) != '#' &&
						(line.regionMatches(true, 0, "http://", 0, 7) ||
						line.regionMatches(true, 0, "https://", 0, 8)))
						foundUrls.add(line);
				}
				if (foundUrls.size() == 0) {
					err = 0;
					return null;
				} else {
					//instead of just using the first available address, let's use
					//one from the middle ;)
					return foundUrls.get(foundUrls.size() >> 1);
				}
			}
			return null;
		} catch (Throwable ex) {
			err = -1;
			return null;
		} finally {
			if (resultCode != null)
				resultCode[0] = err;
			try {
				if (urlConnection != null)
					urlConnection.disconnect();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (is != null)
					is.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (isr != null)
					isr.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (br != null)
					br.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			System.gc();
		}
	}

	public String resolve(String[] newPath) {
		if (!alive)
			return null;

		String result;

		//first: try to resolve the stream url again (maybe something has changed)
		result = resolveStreamUrlFromM3uUrl(m3uUrl, null);
		if (!alive)
			return null;
		if (result != null) {
			if (newPath != null)
				newPath[0] = (new RadioStation(title, "", "", "", "", "", m3uUrl, false, isShoutcast)).buildFullPath(result);
			return result;
		}

		//second: perform a new search for this radio station (this is the most time consuming operation)
		synchronized (sync) {
			if (!alive)
				return null;
			radioStationList = (isShoutcast ? new ShoutcastRadioStationList("", "", "", "") : new IcecastRadioStationList("", "", "", ""));
		}
		final RadioStation radioStation = radioStationList.tryToFetchRadioStationAgain(Player.getService(), title);
		if (!alive)
			return null;
		if (radioStation != null) {
			result = resolveStreamUrlFromM3uUrl(radioStation.m3uUrl, null);
			if (result != null) {
				if (newPath != null)
					newPath[0] = radioStation.buildFullPath(result);
				return result;
			}
		}
		return null;
	}

	public void release() {
		alive = false;
		synchronized (sync) {
			if (radioStationList != null) {
				radioStationList.cancel();
				radioStationList = null;
			}
		}
	}
}
