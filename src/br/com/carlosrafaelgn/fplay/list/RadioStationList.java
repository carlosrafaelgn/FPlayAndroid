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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashSet;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.RadioStationView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;
import br.com.carlosrafaelgn.fplay.util.Serializer;

public final class RadioStationList extends BaseList<RadioStation> implements Runnable, ArraySorter.Comparer<RadioStation>, MainHandler.Callback {
	//after analyzing the results obtained from http://dir.xiph.org/xxx
	//I noticed that there are never more than 5 pages of results,
	//with 20 results each ;)
	private static final int MAX_COUNT = 100;
	private static final int MSG_FINISHED = 0x0300;
	private static final int MSG_MORE_RESULTS = 0x0301;
	
	public static final int POPULAR_GENRE_COUNT = 32;
	//I took these genres from http://dir.xiph.org/yp.xml
	//... after grouping, counting, sorting and selecting properly ;)
	public static final String[] GENRES = new String[] {
		"8bit",
		"Alternative",
		"Anime",
		"Christian",
		"Classic",
		"Classical",
		"Dance",
		"Disco",
		"Electronic",
		"Hits",
		"House",
		"Jazz",
		"Lounge",
		"Metal",
		"Misc",
		"Music",
		"News",
		"Oldies",
		"Pop",
		"Radio",
		"Reggae",
		"Rock",
		"Salsa",
		"Ska",
		"Talk",
		"Techno",
		"Top",
		"Top40",
		"Top100",
		"Trance",
		"Various",
		"Video Game", //last popular genre
		
		"40s",
		"50s",
		"60s",
		"70s",
		"80s",
		"90s",
		"00s",
		"Adult",
		"Alternate",
		"Ambiance",
		"Ambient",
		"Argentina",
		"Baladas",
		"Bass",
		"Beatles",
		"Bible",
		"Blues",
		"Broadway",
		"Catholic",
		"Celtic",
		"Chill",
		"Chillout",
		"Chiptunes",
		"Club",
		"Comedy",
		"Contemporary",
		"Country",
		"Downtempo",
		"Dubstep",
		"Easy",
		"Eclectic",
		"Electro",
		"Electronica",
		"Elektro",
		"Eurodance",
		"Experimental",
		"Folk",
		"France",
		"Funk",
		"German",
		"Gospel",
		"Goth",
		"Hardcore",
		"Hardstyle",
		"Hindi",
		"Hiphop",
		"Hit",
		"Ibiza",
		"Indie",
		"Industrial",
		"Inspirational",
		"Instrumental",
		"International",
		"Italia",
		"Japan",
		"Jpop",
		"Jrock",
		"Jungle",
		"Korea",
		"Kpop",
		"Latin",
		"Latina",
		"Latinpop",
		"Layback",
		"Libre",
		"Live",
		"Lovesongs",
		"Mariachi",
		"Mashup",
		"Merengue",
		"Minecraft",
		"Mixed",
		"Modern",
		"Motown",
		"Mozart",
		"Musica",
		"Nederlands",
		"New",
		"Oldschool",
		"Paris",
		"Progressive",
		"Psytrance",
		"Punk",
		"Punkrock",
		"Rap",
		"Recuerdos",
		"Reggaeton",
		"Relax",
		"Remixes",
		"Rockabilly",
		"Romantica",
		"Roots",
		"Russian",
		"Schlager",
		"Sertanejo",
		"Slow",
		"Smooth",
		"Soul",
		"Soundtrack",
		"Southern",
		"Sports",
		"Student",
		"Tech",
		"Tropical",
		"Webradio",
		"Western",
		"World",
		"Zen",
		"Zouk"
	};
	
	private boolean loading, favoritesLoaded, favoritesChanged;
	private final Object favoritesSync;
	private final HashSet<RadioStation> favorites;
	private final String tags, noOnAir, noDescription, noTags;
	private volatile boolean readyToFetch, isSavingFavorites;
	private volatile int version;
	private volatile String genreToFetch, searchTermToFetch;
	private volatile Context context;
	
	public RadioStationList(String tags, String noOnAir, String noDescription, String noTags) {
		super(RadioStation.class);
		this.items = new RadioStation[MAX_COUNT];
		this.readyToFetch = true;
		this.favoritesSync = new Object();
		this.favorites = new HashSet<RadioStation>(32);
		this.tags = tags;
		this.noOnAir = noOnAir;
		this.noDescription = noDescription;
		this.noTags = noTags;
	}
	
	public boolean isLoading() {
		return loading;
	}
	
	private void loadingProcessChanged(boolean started) {
		loading = started;
		if (UI.browserActivity != null)
			UI.browserActivity.loadingProcessChanged(started);
	}
	
	public void cancel() {
		version++;
		if (loading)
			loadingProcessChanged(false);
	}
	
	private static String readStringIfPossible(XmlPullParser parser, StringBuilder sb) throws Throwable {
		sb.delete(0, sb.length());
		switch (parser.getEventType()) {
		case XmlPullParser.COMMENT:
			break;
		case XmlPullParser.ENTITY_REF:
			break;
		case XmlPullParser.IGNORABLE_WHITESPACE:
			sb.append(' ');
			break;
		case XmlPullParser.PROCESSING_INSTRUCTION:
		case XmlPullParser.TEXT:
			if (parser.isWhitespace())
				sb.append(' ');
			else
				sb.append(parser.getText());
			break;
		default:
			return null;
		}
		for (; ; ) {
			switch (parser.nextToken()) {
			case XmlPullParser.COMMENT:
				break;
			case XmlPullParser.ENTITY_REF:
				break;
			case XmlPullParser.IGNORABLE_WHITESPACE:
				sb.append(' ');
				break;
			case XmlPullParser.PROCESSING_INSTRUCTION:
			case XmlPullParser.TEXT:
				if (parser.isWhitespace())
					sb.append(' ');
				else
					sb.append(parser.getText());
				break;
			default:
				return sb.toString();
			}
		}
	}
	
	private static boolean parseIcecastColumn2(XmlPullParser parser, String[] fields, StringBuilder sb) throws Throwable {
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
		fields[1] = ""; //uri
		fields[2] = ""; //type
		fields[3] = ""; //listeners
		fields[4] = ""; //description
		fields[5] = ""; //onAir
		fields[6] = ""; //tags
		fields[7] = ""; //m3uUri
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
					if (!parseIcecastColumn2(parser, fields, sb))
						return false;
				}
			}
		}
		return true;
	}
	
	private boolean parseIcecastResults(InputStream is, String[] fields, int myVersion, StringBuilder sb, int[] currentStationIndex) throws Throwable {
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
			while ((ev = parser.nextToken()) != XmlPullParser.END_DOCUMENT && currentStationIndex[0] < MAX_COUNT) {
				if (ev == XmlPullParser.END_TAG && parser.getName().equals("table"))
					break;
				if (ev == XmlPullParser.START_TAG && parser.getName().equals("tr")) {
					if (myVersion != version)
						break;
					if (parseIcecastRow(parser, fields, sb) && myVersion == version) {
						final RadioStation station = new RadioStation(fields[0], fields[1], fields[2], fields[4].length() == 0 ? noDescription : fields[4], fields[5].length() == 0 ? noOnAir : fields[5], fields[6].length() == 0 ? noTags : fields[6], fields[7], false);
						synchronized (favoritesSync) {
							station.isFavorite = favorites.contains(station);
						}
						if (myVersion != version)
							break;
						items[currentStationIndex[0]++] = station;
						hasResults = true;
					}
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return hasResults;
	}
	
	private void loadFavoritesInternal(Context context) throws IOException {
		FileInputStream fs = null;
		BufferedInputStream bs = null;
		try {
			fs = context.openFileInput("_RadioFav");
			bs = new BufferedInputStream(fs, 4096);
			final int version = Serializer.deserializeInt(bs);
			final int count = Math.min(Serializer.deserializeInt(bs), MAX_COUNT);
			if (version == 0x0100 && count > 0) {
				favorites.clear();
				for (int i = 0; i < count; i++)
					favorites.add(RadioStation.deserialize(bs, true));
			}
		} catch (IOException ex) {
			if (ex instanceof FileNotFoundException && fs == null) {
				favorites.clear();
			} else {
				throw ex;
			}
		} finally {
			try {
				if (bs != null)
					bs.close();
			} catch (Throwable ex) {
			}
			try {
				if (fs != null)
					fs.close();
			} catch (Throwable ex) {
			}
		}
	}
	
	private void saveFavoritesInternal(Context context) throws IOException {
		FileOutputStream fs = null;
		BufferedOutputStream bs = null;
		try {
			final int count = Math.min(MAX_COUNT, favorites.size());
			int i = 0;
			fs = context.openFileOutput("_RadioFav", 0);
			bs = new BufferedOutputStream(fs, 4096);
			Serializer.serializeInt(bs, 0x0100);
			Serializer.serializeInt(bs, count);
			for (RadioStation s : favorites) {
				if (i >= count)
					break;
				s.serialize(bs);
				i++;
			}
			bs.flush();
		} finally {
			try {
				if (bs != null)
					bs.close();
			} catch (Throwable ex) {
			}
			try {
				if (fs != null)
					fs.close();
			} catch (Throwable ex) {
			}
		}
	}
	
	@Override
	public int compare(RadioStation a, RadioStation b) {
		int r = a.title.compareToIgnoreCase(b.title);
		if (r != 0)
			return r;
		r = a.onAir.compareToIgnoreCase(b.onAir);
		if (r != 0)
			return r;
		return a.m3uUri.compareTo(b.m3uUri);
	}
	
	@Override
	public void run() {
		final int myVersion = version;
		final Context context = this.context;
		final String genre = genreToFetch, searchTerm = searchTermToFetch;
		final boolean isSavingFavorites = this.isSavingFavorites;
		this.context = null;
		readyToFetch = true;
		
		int err = 0;
		
		if (!favoritesLoaded && !isSavingFavorites && context != null) {
			synchronized (favoritesSync) {
				if (!favoritesLoaded) {
					try {
						loadFavoritesInternal(context);
						favoritesLoaded = true;
						favoritesChanged = false;
					} catch (Throwable ex) {
					}
				}
			}
		}
		
		if (genre == null && searchTerm == null) {
			//favorites
			synchronized (favoritesSync) {
				if (isSavingFavorites) {
					try {
						if (favoritesLoaded && favoritesChanged && context != null) {
							saveFavoritesInternal(context);
							favoritesChanged = false;
						}
					} catch (Throwable ex) {
						MainHandler.toast(R.string.error_gen);
					}
				} else {
					try {
						if (favoritesLoaded && context != null) {
							if (myVersion != version)
								return;
							final RadioStation[] stations = new RadioStation[favorites.size()];
							favorites.toArray(stations);
							ArraySorter.sort(stations, 0, stations.length, this);
							if (myVersion == version) {
								final int count = Math.min(stations.length, MAX_COUNT);
								System.arraycopy(stations, 0, items, 0, count);
								MainHandler.sendMessage(RadioStationList.this, MSG_MORE_RESULTS, myVersion, count);
							}
						}
					} catch (Throwable ex) {
						err = -2;
					} finally {
						if (myVersion == version)
							MainHandler.sendMessage(RadioStationList.this, MSG_FINISHED, myVersion, err);
					}
				}
			}
			return;
		}
		
		try {
			int pageNumber = 0;
			boolean hasResults;
			String[] fields = new String[8];
			final StringBuilder sb = new StringBuilder(256);
			final int[] currentStationIndex = { 0 };
			
			//genre MUST be one of the predefined genres (due to the encoding)
			final String uri = ((genre != null) ?
					("http://dir.xiph.org/by_genre/" + genre.replace(" ", "%20") + "?page=") :
					("http://dir.xiph.org/search?search=" + URLEncoder.encode(searchTerm, "UTF-8") + "&page="));
			do {
				if (myVersion != version)
					break;
				AndroidHttpClient client = null;
				HttpResponse response = null;
				InputStream is = null;
				try {
					client = AndroidHttpClient.newInstance("Mozilla/5.0 (X11; U; Linux i686; en-US) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36 Debian");
					response = client.execute(new HttpGet(uri + pageNumber));
					final StatusLine statusLine = response.getStatusLine();
					if (myVersion != version)
						break;
					if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
						is = response.getEntity().getContent();
						hasResults = parseIcecastResults(is, fields, myVersion, sb, currentStationIndex);
						if (hasResults && myVersion == version)
							MainHandler.sendMessage(RadioStationList.this, MSG_MORE_RESULTS, myVersion, currentStationIndex[0]);
					} else {
						hasResults = false;
						err = statusLine.getStatusCode();
					}
				} catch (Throwable ex) {
					hasResults = false;
					err = -1;
				} finally {
					try {
						if (client != null)
							client.close();
					} catch (Throwable ex) {
					}
					try {
						if (is != null)
							is.close();
					} catch (Throwable ex) {
					}
					is = null;
					client = null;
					response = null;
					System.gc();
				}
				pageNumber++;
			} while (hasResults && pageNumber < 5);
		} catch (Throwable ex) {
			err = -1;
		} finally {
			if (myVersion == version)
				MainHandler.sendMessage(RadioStationList.this, MSG_FINISHED, myVersion, err);
		}
	}
	
	public void addFavoriteStation(RadioStation station) {
		synchronized (favoritesSync) {
			if (favoritesLoaded) {
				station.isFavorite = true;
				favoritesChanged |= favorites.add(station);
			}
		}
	}
	
	public void removeFavoriteStation(RadioStation station) {
		synchronized (favoritesSync) {
			if (favoritesLoaded) {
				station.isFavorite = false;
				favoritesChanged |= favorites.remove(station);
			}
		}
	}
	
	public void fetchIcecast(Context context, String genre, String searchTerm) {
		while (!readyToFetch)
			Thread.yield();
		cancel();
		clear();
		loadingProcessChanged(true);
		final Thread t = new Thread(this, "Icecast Station Fetcher Thread");
		isSavingFavorites = false;
		genreToFetch = genre;
		searchTermToFetch = searchTerm;
		this.context = context;
		readyToFetch = false;
		try {
			t.start();
		} catch (Throwable ex) {
			readyToFetch = true;
			loadingProcessChanged(false);
		}
	}
	
	public void fetchFavorites(Context context) {
		while (!readyToFetch)
			Thread.yield();
		cancel();
		clear();
		loadingProcessChanged(true);
		final Thread t = new Thread(this, "Icecast Favorite Stations Fetcher Thread");
		isSavingFavorites = false;
		genreToFetch = null;
		searchTermToFetch = null;
		this.context = context;
		readyToFetch = false;
		try {
			t.start();
		} catch (Throwable ex) {
			readyToFetch = true;
			loadingProcessChanged(false);
		}
	}
	
	public void saveFavorites(Context context) {
		while (!readyToFetch)
			Thread.yield();
		synchronized (favoritesSync) {
			if (!favoritesLoaded || !favoritesChanged)
				return;
		}
		final Thread t = new Thread(this, "Icecast Favorite Stations Storer Thread");
		isSavingFavorites = true;
		genreToFetch = null;
		searchTermToFetch = null;
		this.context = context;
		readyToFetch = false;
		try {
			t.start();
		} catch (Throwable ex) {
			readyToFetch = true;
		}
	}
	
	@Override
	public boolean handleMessage(Message msg) {
		if (msg.arg1 != version)
			return true;
		switch (msg.what) {
		case MSG_FINISHED:
			loadingProcessChanged(false);
			if (msg.arg2 != 0)
				UI.toast(Player.getService(), ((msg.arg2 != -2) && !Player.isConnectedToTheInternet()) ? R.string.error_connection : R.string.error_gen);
			break;
		case MSG_MORE_RESULTS:
			//protection against out of order messages... does this really happen? ;)
			if (msg.arg2 > count) {
				//items are always appended :)
				modificationVersion++;
				final int c = count;
				count = msg.arg2;
				addingItems(c, c - count);
				notifyDataSetChanged(-1, CONTENT_ADDED);
			}
			break;
		}
		return true;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final RadioStationView view = ((convertView != null) ? (RadioStationView)convertView : new RadioStationView(Player.getService()));
		view.setItemState(items[position], position, getItemState(position));
		return view;
	}
	
	@Override
	public int getViewHeight() {
		return RadioStationView.getViewHeight();
	}
}
