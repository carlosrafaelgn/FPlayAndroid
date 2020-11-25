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
package br.com.carlosrafaelgn.fplay.list;

import android.os.Build;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.Request;
import br.com.carlosrafaelgn.fplay.util.TypedRawArrayList;

public final class ShoutcastRadioStationList extends RadioStationList {
	private final String tags, listeners, noOnAir, noDescription;
	private int pageNumber, currentStationIndex;
	private String baseUrl;

	public ShoutcastRadioStationList(String tags, String listeners, String noOnAir, String noDescription) {
		super(RadioStationCache.getIfNotExpired(Player.radioStationCacheShoutcast));
		this.tags = tags + UI.collon();
		this.listeners = listeners + UI.collon();
		this.noOnAir = noOnAir;
		this.noDescription = noDescription;
	}

	private boolean parseShoutcastStation(XmlPullParser parser, TypedRawArrayList<String> tempGenres, String[] fields) {
		fields[0] = ""; //title
		fields[1] = ""; //url
		fields[2] = ""; //type
		fields[3] = ""; //listeners
		fields[4] = ""; //description
		fields[5] = ""; //onAir
		fields[6] = ""; //tags
		fields[7] = ""; //m3uUrl
		String br = null, genre;
		tempGenres.clear();
		try {
			for (int i = parser.getAttributeCount() - 1; i >= 0; i--) {
				final String attribute = parser.getAttributeName(i);
				if ("name".equals(attribute))
					fields[0] = parser.getAttributeValue(i);
				else if ("id".equals(attribute))
					fields[1] = parser.getAttributeValue(i);
				else if ("mt".equals(attribute))
					fields[2] = parser.getAttributeValue(i);
				else if ("lc".equals(attribute))
					fields[3] = parser.getAttributeValue(i);
				else if ("ct".equals(attribute))
					fields[5] = parser.getAttributeValue(i);
				else if ("br".equals(attribute))
					br = parser.getAttributeValue(i) + "kbps";
				else if (("genre".equals(attribute) || "genre2".equals(attribute) || "genre3".equals(attribute) || "genre4".equals(attribute)) &&
					tempGenres.size() < 4 && !tempGenres.contains((genre = parser.getAttributeValue(i))))
					tempGenres.add(genre);
			}
			if (fields[0].length() == 0 || fields[1].length() == 0)
				return false;
			fields[7] = "http://yp.shoutcast.com/sbin/tunein-station.m3u?id=" + fields[1];
			if (fields[3].length() != 0)
				fields[4] = listeners + fields[3];
			if (br != null && br.length() != 0)
				fields[4] = ((fields[4].length() == 0) ? br : (fields[4] + "\n" + br));
			if (tempGenres.size() != 0) {
				fields[6] = tags + tempGenres.get(0);
				for (int i = 1; i < tempGenres.size(); i++)
					fields[6] += ", " + parser.getAttributeValue(i);
			}
		} catch (Throwable ex) {
			return false;
		}
		return true;
	}

	private boolean parseShoutcastResults(InputStream is, String[] fields, int myVersion) throws Throwable {
		boolean hasResults = false;
		//According to these docs, kXML parser will accept some XML documents
		//that should actually be rejected (A robust "relaxed" mode for parsing
		//HTML or SGML files):
		//http://developer.android.com/training/basics/network-ops/xml.html
		//http://kxml.org/index.html
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		XmlPullParser parser = factory.newPullParser();
		parser.setInput(is, "UTF-8");
		//special feature! (check out kXML2 source and you will find it!)
		parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true);
		int ev;
		final TypedRawArrayList<String> tempGenres = new TypedRawArrayList<>(String.class, 4);
		while ((ev = parser.nextToken()) != XmlPullParser.END_DOCUMENT && currentStationIndex < MAX_COUNT) {
			if (ev == XmlPullParser.START_TAG) {
				if (myVersion != version)
					break;
				if (parser.getName().equals("statusCode")) {
					if (parser.nextToken() == XmlPullParser.TEXT) {
						if (Integer.parseInt(parser.getText()) != 200)
							throw new IOException();
					}
				} else if (parser.getName().equals("station")) {
					if (parseShoutcastStation(parser, tempGenres, fields) && myVersion == version) {
						final RadioStation station = new RadioStation(fields[0], fields[1], fields[2], fields[4].length() == 0 ? noDescription : fields[4], fields[5].length() == 0 ? noOnAir : fields[5], fields[6], fields[7], false, true);
						synchronized (favoritesSync) {
							station.isFavorite = favorites.contains(station);
						}
						if (myVersion != version)
							break;
						items[currentStationIndex++] = station;
						hasResults = true;
					}
				}
			}
		}
		tempGenres.clear();
		return hasResults;
	}

	@Override
	protected void fetchStationsInternal(int myVersion, RadioStationGenre genre, String searchTerm, boolean reset, boolean sendMessages) {
		int err = 0;
		InputStream inputStream = null;
		HttpURLConnection urlConnection = null;
		try {
			if (myVersion == version) {
				//for shoutcast, pageNumber stores the "limit" parameter
				if (reset) {
					pageNumber = 0;
					currentStationIndex = 0;
				} else {
					pageNumber += 20;
					if (pageNumber >= 100) {
						if (sendMessages)
							fetchStationsInternalResultsFound(myVersion, currentStationIndex, false);
						return;
					}
				}
			}
			boolean hasResults;
			String[] fields = new String[8];

			if (baseUrl == null) {
				final byte[] tmp = new byte[67];
				final byte[] tmp2 = { 0x00, 0x08, 0x04, 0x0c, 0x02, 0x0a, 0x06, 0x0e, 0x01, 0x09, 0x05, 0x0d, 0x03, 0x0b, 0x07, 0x0f };
				inputStream = Player.theApplication.getAssets().open("binary/url.dat");
				if (inputStream.read(tmp, 0, 67) == 67) {
					for (int i = 0; i < 67; i++) {
						final byte b = tmp[i];
						tmp[i] = (byte)((tmp2[b & 0x0f] << 4) | tmp2[(b >> 4) & 0x0f]);
					}
				}
				inputStream.close();
				inputStream = null;
				//Sorry, everyone!!!
				//As a part of the process of getting a DevID, they ask you not to make it publicly available :(
				//But.... you can get your own DevID for FREE here: http://www.shoutcast.com/Partners :)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
					baseUrl = new String(tmp, 0, 67, StandardCharsets.UTF_8);
				else
					//noinspection CharsetObjectCanBeUsed
					baseUrl = new String(tmp, 0, 67, "UTF-8");
			}
			//https://developer.android.com/guide/appendix/media-formats.html
			//ADTS raw AAC (.aac, decode in Android 3.1+)
			//urlConnection = Player.createConnection(baseUrl + "&f=xml&mt=audio/mpeg&limit=" + pageNumber + ",20&" +
			urlConnection = Request.createConnection(baseUrl + ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) ? "&f=xml&limit=" : "&f=xml&mt=audio/mpeg&limit=") + pageNumber + ",20&" +
				((genre != null) ? ("genre_id=" + genre.id) : ("search=" + URLEncoder.encode(searchTerm, "UTF-8"))));
			if (myVersion != version)
				return;
			err = urlConnection.getResponseCode();
			if (myVersion != version)
				return;
			if (err == 200) {
				inputStream = urlConnection.getInputStream();
				hasResults = parseShoutcastResults(inputStream, fields, myVersion);
			} else {
				throw new IOException();
			}
			if (sendMessages)
				fetchStationsInternalResultsFound(myVersion, currentStationIndex, hasResults);
			err = 0;
		} catch (Throwable ex) {
			err = -1;
		} finally {
			try {
				if (urlConnection != null)
					urlConnection.disconnect();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			if (err < 0 && sendMessages)
				fetchStationsInternalError(myVersion, err);
			System.gc();
		}
	}

	@Override
	protected void readCache(RadioStationCache cache) {
		super.readCache(cache);
		pageNumber = cache.pageNumber;
		currentStationIndex = cache.currentStationIndex;
	}

	@Override
	protected void writeCache(RadioStationCache cache) {
		cache.pageNumber = pageNumber;
		cache.currentStationIndex = currentStationIndex;
	}

	@Override
	protected void storeCache(RadioStationCache cache) {
		Player.radioStationCacheShoutcast = cache;
	}
}
