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

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import br.com.carlosrafaelgn.fplay.list.IcecastRadioStationList;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.list.ShoutcastRadioStationList;
import br.com.carlosrafaelgn.fplay.util.TypedRawArrayList;

public final class RadioStationResolver extends Thread {
	private final Object sync;
	private volatile boolean alive;
	private final int msg, arg1;
	private String streamUrl, m3uUrl, title;
	private final boolean isShoutcast;
	private Handler handler;
	private HttpStreamReceiver streamReceiver;

	private RadioStationResolver(int msg, int arg1, Handler handler, String streamUrl, String m3uUrl, String title, boolean isShoutcast) {
		super("Radio Station Resolver Thread");
		sync = new Object();
		alive = true;
		this.msg = msg;
		this.arg1 = arg1;
		this.handler = handler;
		this.streamUrl = streamUrl;
		this.m3uUrl = m3uUrl;
		this.title = title;
		this.isShoutcast = isShoutcast;
		try {
			streamReceiver = new HttpStreamReceiver(null, 0, null, 0, null, 0, 0, 0, streamUrl, -1, false);
		} catch (Throwable ex) {
			streamReceiver = null;
		}
		setDaemon(true);
	}

	public static RadioStationResolver resolveIfNeeded(int msg, int arg1, Handler handler, String path) {
		final int i = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR);
		if (i <= 0)
			return null;
		final int i2 = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR, i + 1);
		final int i3 = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR, i2 + 1);
		if (i2 <= (i + 1) || i3 <= (i2 + 1))
			return null;
		final RadioStationResolver resolver = new RadioStationResolver(msg, arg1, handler, path.substring(0, i), path.substring(i + 1, i2), path.substring(i2 + 1, i3), path.substring(i3 + 1).equals("1"));
		resolver.start();
		return resolver;
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

	@Override
	public void run() {
		final String streamUrl = this.streamUrl, m3uUrl = this.m3uUrl, title = this.title;
		if (streamUrl == null || m3uUrl == null || title == null)
			return;
		int httpCode = -1;
		Object result = null;
		try {
			//first step: try to connect to the stream url, if it is possible, no further actions are required
			try {
				if (streamReceiver != null && streamReceiver.pingServer()) {
					synchronized (sync) {
						if (streamReceiver != null) {
							httpCode = 200;
							result = streamReceiver.getResolvedURL();
							streamReceiver.release();
							streamReceiver = null;
						}
					}
					return;
				}
			} catch (Throwable ex) {
				httpCode = -1;
			}

			if (!alive)
				return;

			//second: try to resolve the stream url again (maybe something has changed)
			result = resolveStreamUrlFromM3uUrl(m3uUrl, null);
			if (!alive)
				return;
			if (result != null) {
				httpCode = 200;
				return;
			}

			//third: perform a new search for this radio station (this is the most time consuming operation)
			final RadioStation radioStation = (isShoutcast ? new ShoutcastRadioStationList("", "", "", "") : new IcecastRadioStationList("", "", "", "")).tryToFetchRadioStationAgain(Player.getService(), title);
			if (!alive)
				return;
			if (radioStation != null) {
				result = resolveStreamUrlFromM3uUrl(radioStation.m3uUrl, null);
				if (!alive)
					return;
				if (result != null) {
					httpCode = 200;
					return;
				}
			}

			httpCode = 0;
			result = null;
		} catch (Throwable ex) {
			httpCode = -1;
			result = ex;
		} finally {
			synchronized (sync) {
				if (alive) {
					alive = false;
					if (handler != null) {
						handler.sendMessageAtTime(Message.obtain(handler, msg, arg1, httpCode, result), SystemClock.uptimeMillis());
						handler = null;
					}
					this.streamUrl = null;
					this.m3uUrl = null;
					this.title = null;
				}
			}
		}
	}

	public void cancel() {
		synchronized (sync) {
			alive = false;
			handler = null;
			streamUrl = null;
			m3uUrl = null;
			title = null;
			if (streamReceiver != null) {
				streamReceiver.release();
				streamReceiver = null;
			}
		}
	}
}
