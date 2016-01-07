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

import android.content.Context;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

import br.com.carlosrafaelgn.fplay.playback.Player;

public final class IcecastRadioStationList extends RadioStationList {
	private final String tags, noOnAir, noDescription, noTags;
	private int pageNumber, currentStationIndex;

	public IcecastRadioStationList(String tags, String noOnAir, String noDescription, String noTags) {
		this.tags = tags;
		this.noOnAir = noOnAir;
		this.noDescription = noDescription;
		this.noTags = noTags;
	}

	private static boolean parseIcecastColumn2(XmlPullParser parser, String[] fields) throws Throwable {
		boolean hasFields = false, linkContainsType = false;
		int ev;
		String v;
		while ((ev = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
			if (ev == XmlPullParser.END_TAG && parser.getName().equals("td"))
				break;
			if (ev == XmlPullParser.START_TAG) {
				if (parser.getName().equals("p") && hasFields) {
					linkContainsType = true;
				} else if (parser.getName().equals("a")) {
					if (linkContainsType) {
						if (parser.nextToken() != XmlPullParser.TEXT) {
							//impossible to determine the type of the stream...
							//just drop it!
							hasFields = false;
						} else {
							v = parser.getText().trim();
							hasFields = (v.equals("MP3") || v.equals("Ogg Vorbis"));
							fields[2] = v;
						}
					} else {
						for (int a = parser.getAttributeCount() - 1; a >= 0; a--) {
							if (parser.getAttributeName(a).equals("href") &&
								(v = parser.getAttributeValue(a)).endsWith("m3u")) {
								fields[7] = ((v.charAt(0) == '/') ? ("http://dir.xiph.org" + v) : (v)).trim();
								hasFields = true;
								break;
							}
						}
					}
				}
			}
		}
		return hasFields;
	}

	private boolean parseIcecastColumn1(XmlPullParser parser, String[] fields, StringBuilder sb) throws Throwable {
		int ev = 0, pCount = 0;
		boolean hasFields = false, hasNextToken = false, parsingTags = false;
		String str;
		while (hasNextToken || ((ev = parser.nextToken()) != XmlPullParser.END_DOCUMENT)) {
			hasNextToken = false;
			if (ev == XmlPullParser.END_TAG && parser.getName().equals("td"))
				break;
			if (ev == XmlPullParser.START_TAG && parser.getName().equals("p")) {
				pCount++;
			} else if (ev == XmlPullParser.START_TAG && parser.getName().equals("ul")) {
				parsingTags = true;
				sb.delete(0, sb.length());
			} else if (parsingTags) {
				if (ev == XmlPullParser.START_TAG && parser.getName().equals("a")) {
					if (parser.nextToken() == XmlPullParser.TEXT) {
						if (sb.length() > 0) {
							sb.append(' ');
						} else {
							sb.append(tags);
							sb.append(": ");
						}
						sb.append(parser.getText());
					} else {
						hasNextToken = true;
						ev = parser.getEventType();
					}
				} else if (ev == XmlPullParser.END_TAG && parser.getName().equals("ul")) {
					hasFields = true;
					fields[6] = sb.toString().trim();
				}
			} else {
				switch (pCount) {
				case 1:
					if (ev == XmlPullParser.START_TAG) {
						if (parser.getName().equals("a")) {
							for (int a = parser.getAttributeCount() - 1; a >= 0; a--) {
								if (parser.getAttributeName(a).equals("href")) {
									fields[1] = parser.getAttributeValue(a).trim();
									//set hasFields to true, only if the title has been found!
									//hasFields = true;
									break;
								}
							}
							parser.nextToken();
							if ((str = readStringIfPossible(parser, sb)) != null) {
								hasFields = true;
								fields[0] = str.trim();
							}
							hasNextToken = true;
							ev = parser.getEventType();
						} else if (fields[0].length() != 0 && parser.getName().equals("span")) {
							if (parser.nextToken() == XmlPullParser.TEXT) {
								fields[3] = parser.getText().trim();
								if (fields[3].length() > 0)
									fields[3] = fields[3].substring(1).trim();
							} else {
								hasNextToken = true;
								ev = parser.getEventType();
							}
						}
					}
					break;
				case 2:
					if (fields[4].length() == 0 && (str = readStringIfPossible(parser, sb)) != null) {
						hasFields = true;
						fields[4] = str.trim();
						hasNextToken = true;
						ev = parser.getEventType();
					} else {
						hasNextToken = false;
					}
					break;
				case 3:
					if (ev == XmlPullParser.END_TAG && parser.getName().equals("strong")) {
						if (fields[5].length() == 0) {
							parser.nextToken();
							if ((str = readStringIfPossible(parser, sb)) != null) {
								hasFields = true;
								fields[5] = str.trim();
							}
							hasNextToken = true;
							ev = parser.getEventType();
						}
					}
					break;
				}
			}
		}
		return hasFields;
	}

	private boolean parseIcecastRow(XmlPullParser parser, String[] fields, StringBuilder sb) throws Throwable {
		fields[0] = ""; //title
		fields[1] = ""; //url
		fields[2] = ""; //type
		fields[3] = ""; //listeners
		fields[4] = ""; //description
		fields[5] = ""; //onAir
		fields[6] = ""; //tags
		fields[7] = ""; //m3uUrl
		int ev, colCount = 0;
		while ((ev = parser.nextToken()) != XmlPullParser.END_DOCUMENT && colCount < 2) {
			if (ev == XmlPullParser.END_TAG && parser.getName().equals("tr"))
				break;
			if (ev == XmlPullParser.START_TAG && parser.getName().equals("td")) {
				colCount++;
				if (colCount == 1) {
					if (!parseIcecastColumn1(parser, fields, sb))
						return false;
				} else {
					if (!parseIcecastColumn2(parser, fields))
						return false;
				}
			}
		}
		return true;
	}

	private boolean parseIcecastResults(InputStream is, String[] fields, int myVersion, StringBuilder sb) throws Throwable {
		int b = 0;
		while (b >= 0) {
			if ((b = is.read()) == (int)'<' &&
				(b = is.read()) == (int)'h' &&
				(b = is.read()) == (int)'2' &&
				(b = is.read()) == (int)'>')
				break;
		}
		if (b < 0)
			return false;
		while (b >= 0) {
			if ((b = is.read()) == (int)'<' &&
				(b = is.read()) == (int)'/' &&
				(b = is.read()) == (int)'h' &&
				(b = is.read()) == (int)'2' &&
				(b = is.read()) == (int)'>')
				break;
		}
		if (b < 0)
			return false;
		boolean hasResults = false;
		//According to these docs, kXML parser will accept some XML documents
		//that should actually be rejected (A robust "relaxed" mode for parsing
		//HTML or SGML files):
		//http://developer.android.com/training/basics/network-ops/xml.html
		//http://kxml.org/index.html
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser parser = factory.newPullParser();
			parser.setInput(is, "UTF-8");
			//special feature! (check out kXML2 source and you will find it!)
			parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true);
			int ev;
			while ((ev = parser.nextToken()) != XmlPullParser.END_DOCUMENT && currentStationIndex < MAX_COUNT) {
				if (ev == XmlPullParser.END_TAG && parser.getName().equals("table"))
					break;
				if (ev == XmlPullParser.START_TAG && parser.getName().equals("tr")) {
					if (myVersion != version)
						break;
					if (parseIcecastRow(parser, fields, sb) && myVersion == version) {
						final RadioStation station = new RadioStation(fields[0], fields[1], fields[2], fields[4].length() == 0 ? noDescription : fields[4], fields[5].length() == 0 ? noOnAir : fields[5], fields[6].length() == 0 ? noTags : fields[6], fields[7], false, false);
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
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return hasResults;
	}

	@Override
	protected void fetchStationsInternal(Context context, int myVersion, RadioStationGenre genre, String searchTerm, boolean reset) {
		int err = 0;
		InputStream inputStream = null;
		HttpURLConnection urlConnection = null;
		try {
			final int oldStationIndex = (reset ? 0 : currentStationIndex);
			do {
				if (myVersion == version) {
					//icecast returns up to 20 results per page and up to 5 pages
					if (reset) {
						reset = false;
						pageNumber = 0;
						currentStationIndex = 0;
					} else {
						pageNumber++;
						if (pageNumber > 4 || currentStationIndex > 90) {
							fetchStationsInternalResultsFound(myVersion, currentStationIndex, false);
							return;
						}
					}
				}
				String[] fields = new String[8];
				final StringBuilder sb = new StringBuilder(256);

				//genre MUST be one of the predefined genres (due to the encoding)
				final String url = ((genre != null) ?
					("http://dir.xiph.org/by_genre/" + genre.name.replace(" ", "%20") + "?page=") :
					("http://dir.xiph.org/search?search=" + URLEncoder.encode(searchTerm, "UTF-8") + "&page="));
				urlConnection = Player.createConnection(url + pageNumber);
				if (myVersion != version)
					return;
				err = urlConnection.getResponseCode();
				if (myVersion != version)
					return;
				if (err == 200) {
					inputStream = urlConnection.getInputStream();
					parseIcecastResults(inputStream, fields, myVersion, sb);
				} else {
					throw new IOException();
				}
				//if we were not able to add at least 10 new stations, repeat the entire procedure
			} while (myVersion == version && currentStationIndex < (oldStationIndex + 10));
			fetchStationsInternalResultsFound(myVersion, currentStationIndex, true);
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
			if (err < 0)
				fetchStationsInternalError(myVersion, err);
			System.gc();
		}
	}
}
