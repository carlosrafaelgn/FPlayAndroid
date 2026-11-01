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
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.Request;

//Community Radio Station Index
//https://api.radio-browser.info
public final class CommunityRadioStationIndexList extends RadioStationList {
	private static final String DISCOVERY_HOST = "all.api.radio-browser.info";
	private static final long HOSTS_EXPIRATION_TIME_MS = 60L * 60L * 1000L;
	//https://api.radio-browser.info asks all clients to send a speaking user agent
	private static final String USER_AGENT = "FPlay/1.0 (https://github.com/carlosrafaelgn/FPlayAndroid)";
	private static final int PAGE_SIZE = 20;
	private static final int MAX_TAG_COUNT = 8;
	//protection against too many requests, in case most of the stations are filtered out
	private static final int MAX_OFFSET = 5 * MAX_COUNT;

	private static final Object hostsSync = new Object();
	private static String[] hosts;
	private static long hostsTimestamp;
	private static int currentHostIndex;

	private static final Object countriesSync = new Object();
	private static String[] countries;

	private final String tags, noOnAir, noDescription, noTags;
	private int offset, currentStationIndex;

	public CommunityRadioStationIndexList(String tags, String noOnAir, String noDescription, String noTags) {
		super(RadioStationCache.getIfNotExpired(Player.radioStationCache));
		this.tags = tags + UI.collon();
		this.noOnAir = noOnAir;
		this.noDescription = noDescription;
		this.noTags = noTags;
	}

	private static String getString(JSONObject obj, String name) {
		//optString() returns "null" for null values
		return (obj.isNull(name) ? "" : obj.optString(name, "").trim());
	}

	private static boolean isSupportedCodec(String codec) {
		//https://developer.android.com/guide/appendix/media-formats.html
		//ADTS raw AAC (.aac, decode in Android 3.1+)
		return (codec.equalsIgnoreCase("MP3") || ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) && codec.regionMatches(true, 0, "AAC", 0, 3)));
	}

	private static void appendParameter(StringBuilder url, String name, String value) throws Throwable {
		if (value == null || (value = value.trim()).length() == 0)
			return;
		url.append('&');
		url.append(name);
		url.append('=');
		url.append(URLEncoder.encode(value, "UTF-8"));
	}

	private static void appendTagsParameter(StringBuilder url, String tags) throws Throwable {
		if (tags == null || (tags = tags.trim()).length() == 0)
			return;
		if (tags.indexOf(',') < 0) {
			//a single tag is matched partially ("rock" also matches "classic rock")
			appendParameter(url, "tag", tags);
			return;
		}
		//all the tags in tagList must match exactly
		final StringBuilder tagList = new StringBuilder(tags.length());
		for (String tag : tags.split(",")) {
			if ((tag = tag.trim()).length() == 0)
				continue;
			if (tagList.length() != 0)
				tagList.append(',');
			tagList.append(tag);
		}
		appendParameter(url, "tagList", tagList.toString());
	}

	private RadioStation parseStation(JSONObject obj, boolean checkCodec) {
		final String title = getString(obj, "name");
		final String url = getString(obj, "url_resolved");
		final String codec = getString(obj, "codec");
		//HLS streams are not supported
		if (title.length() == 0 || url.length() == 0 || obj.optInt("hls", 0) != 0 || (checkCodec && !isSupportedCodec(codec)))
			return null;

		final StringBuilder sb = new StringBuilder(128);

		//the community radio station index does not provide the song currently being played,
		//so we use onAir to store the station's location ("state, country")
		final String state = getString(obj, "state");
		if (state.length() != 0)
			sb.append(state);
		final String country = getString(obj, "country");
		//a few stations already include the country in their states ("Santos, Sao Paulo (Brazil)")
		if (country.length() != 0 && !state.toLowerCase(Locale.US).contains(country.toLowerCase(Locale.US))) {
			if (sb.length() != 0)
				sb.append(", ");
			sb.append(country);
		}
		final String onAir = sb.toString();

		sb.delete(0, sb.length());
		for (String language : getString(obj, "language").split(",")) {
			if ((language = language.trim()).length() == 0)
				continue;
			if (sb.length() != 0)
				sb.append(", ");
			sb.append(language);
		}
		final int bitRate = obj.optInt("bitrate", 0);
		if (codec.length() != 0 || bitRate > 0) {
			if (sb.length() != 0)
				sb.append('\n');
			if (codec.length() != 0)
				sb.append(codec);
			if (bitRate > 0) {
				if (codec.length() != 0)
					sb.append(' ');
				sb.append(bitRate);
				sb.append("kbps");
			}
		}
		final String description = sb.toString();

		sb.delete(0, sb.length());
		final String[] allTags = getString(obj, "tags").split(",");
		int tagCount = 0;
		for (String tag : allTags) {
			if ((tag = tag.trim()).length() == 0)
				continue;
			sb.append((tagCount == 0) ? tags : ", ");
			sb.append(tag);
			if (++tagCount >= MAX_TAG_COUNT)
				break;
		}

		return new RadioStation(title, getString(obj, "homepage"), codec, (description.length() == 0) ? noDescription : description, (onAir.length() == 0) ? noOnAir : onAir, (tagCount == 0) ? noTags : sb.toString(), url, getString(obj, "stationuuid"), false, false);
	}

	private static String[] resolveHosts() {
		final ArrayList<String> serverHosts = new ArrayList<>();
		try {
			//https://api.radio-browser.info asks all clients to discover the available servers
			//by querying the A/AAAA records of all.api.radio-browser.info, and then performing
			//a reverse DNS look-up for each address, in order to obtain the server names
			final InetAddress[] addresses = InetAddress.getAllByName(DISCOVERY_HOST);
			for (InetAddress address : addresses) {
				//getCanonicalHostName() forces a reverse DNS (PTR) look-up
				final String hostName = address.getCanonicalHostName();
				if (hostName != null && !hostName.equals(address.getHostAddress()) && !serverHosts.contains(hostName))
					serverHosts.add(hostName);
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		//randomize the server selection
		Collections.shuffle(serverHosts);
		return serverHosts.toArray(new String[0]);
	}

	private static String[] getHosts() {
		synchronized (hostsSync) {
			final long now = SystemClock.elapsedRealtime();
			if (hosts == null || (now - hostsTimestamp) >= HOSTS_EXPIRATION_TIME_MS) {
				final String[] newHosts = resolveHosts();
				if (newHosts.length > 0) {
					hosts = newHosts;
					hostsTimestamp = now;
					currentHostIndex = 0;
				} else if (hosts == null) {
					//it was not possible to discover the servers... let's fall back to the
					//discovery host itself (without caching it, so we try again next time)
					return new String[] { DISCOVERY_HOST };
				}
				//if the servers could not be discovered again, just keep using the old
				//ones (without updating hostsTimestamp, so we try again next time)
			}
			return hosts;
		}
	}

	//list can be null, meaning the request cannot be cancelled
	private static boolean isCancelled(CommunityRadioStationIndexList list, int myVersion) {
		return (list != null && myVersion != list.version);
	}

	private static JSONArray fetchJsonArray(String pathAndQuery, CommunityRadioStationIndexList list, int myVersion) throws Throwable {
		return (JSONArray)fetchJson(pathAndQuery, list, myVersion, true);
	}

	//tries all hosts, starting from the current one, and only throws after all of them have failed
	//(pathAndQuery is relative to /json/, and the result is either a JSONArray or a JSONObject)
	private static Object fetchJson(String pathAndQuery, CommunityRadioStationIndexList list, int myVersion, boolean isArray) throws Throwable {
		final String[] serverHosts = getHosts();
		int hostIndex;
		synchronized (hostsSync) {
			hostIndex = ((serverHosts == hosts) ? currentHostIndex : 0);
		}
		Throwable lastEx = null;
		for (int i = 0; i < serverHosts.length; i++) {
			if (isCancelled(list, myVersion))
				return null;
			try {
				return fetchJson(serverHosts[hostIndex], pathAndQuery, list, myVersion, isArray);
			} catch (Throwable ex) {
				lastEx = ex;
				//move on to the next available host
				hostIndex = (hostIndex + 1) % serverHosts.length;
				synchronized (hostsSync) {
					if (serverHosts == hosts)
						currentHostIndex = hostIndex;
				}
			}
		}
		throw ((lastEx == null) ? new IOException() : lastEx);
	}

	private static Object fetchJson(String host, String pathAndQuery, CommunityRadioStationIndexList list, int myVersion, boolean isArray) throws Throwable {
		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = Request.createJsonConnection("GET", "http://" + host + "/json/" + pathAndQuery, false);
			urlConnection.setRequestProperty("User-Agent", USER_AGENT);
			if (isCancelled(list, myVersion))
				return null;
			final int err = urlConnection.getResponseCode();
			if (isCancelled(list, myVersion))
				return null;
			if (err != 200)
				throw new IOException();
			inputStream = urlConnection.getInputStream();
			inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
			final StringBuilder sb = new StringBuilder(16384);
			final char[] buffer = new char[1024];
			int len;
			while ((len = inputStreamReader.read(buffer, 0, 1024)) >= 0) {
				if (isCancelled(list, myVersion))
					return null;
				sb.append(buffer, 0, len);
			}
			return (isArray ? new JSONArray(sb.toString()) : new JSONObject(sb.toString()));
		} finally {
			try {
				if (urlConnection != null)
					urlConnection.disconnect();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (inputStreamReader != null)
					inputStreamReader.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	//this method blocks, and should be called from a background thread
	//(the countries are cached indefinitely, but only after they have been successfully fetched)
	public static String[] fetchCountries() {
		synchronized (countriesSync) {
			if (countries != null)
				return countries;
			try {
				final JSONArray allCountries = fetchJsonArray("countries?hidebroken=true", null, 0);
				if (allCountries == null)
					return null;
				final ArrayList<String> names = new ArrayList<>(allCountries.length());
				for (int i = 0; i < allCountries.length(); i++) {
					final JSONObject obj = allCountries.optJSONObject(i);
					if (obj == null || obj.optInt("stationcount", 1) <= 0)
						continue;
					final String name = getString(obj, "name");
					if (name.length() != 0 && !names.contains(name))
						names.add(name);
				}
				if (names.size() == 0)
					return null;
				//the server sorts the countries by their codes, not by their names
				Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
				countries = names.toArray(new String[0]);
				return countries;
			} catch (Throwable ex) {
				return null;
			} finally {
				System.gc();
			}
		}
	}

	public static void countClick(final String stationUuid) {
		if (stationUuid == null || stationUuid.length() == 0)
			return;
		try {
			(new Thread("Community Radio Station Click Counter Thread") {
				{
					setDaemon(true);
				}

				@Override
				public void run() {
					try {
						//the community radio station index asks players to count a click every time
						//users start playing a station: http://all.api.radio-browser.info/#Count_station_click
						fetchJson("url/" + URLEncoder.encode(stationUuid, "UTF-8"), null, 0, false);
					} catch (Throwable ex) {
						//just ignore
						ex.printStackTrace();
					}
				}
			}).start();
		} catch (Throwable ex) {
			//just ignore
			ex.printStackTrace();
		}
	}

	public RadioStation fetchStationByUuid(String stationUuid) {
		final int myVersion = version;
		try {
			final JSONArray stations = fetchJsonArray("stations/byuuid/" + URLEncoder.encode(stationUuid, "UTF-8"), this, myVersion);
			if (stations == null)
				return null;
			for (int i = 0; i < stations.length(); i++) {
				final JSONObject obj = stations.optJSONObject(i);
				if (obj == null)
					continue;
				final RadioStation station = parseStation(obj, false);
				if (station != null && stationUuid.equalsIgnoreCase(station.stationUuid))
					return station;
			}
			return null;
		} catch (Throwable ex) {
			return null;
		} finally {
			System.gc();
		}
	}

	@Override
	protected void fetchStationsInternal(int myVersion, RadioStationGenre genre, String searchTerm, String searchCountry, String searchTags, boolean reset, boolean sendMessages) {
		int err = 0;
		try {
			//for the community radio station index, offset stores the "offset" parameter
			if (reset && myVersion == version) {
				offset = 0;
				currentStationIndex = 0;
			}
			boolean couldHaveMoreResults;
			final int oldStationIndex = currentStationIndex;
			do {
				if (currentStationIndex >= MAX_COUNT || offset >= MAX_OFFSET) {
					if (sendMessages)
						fetchStationsInternalResultsFound(myVersion, currentStationIndex, false);
					return;
				}

				//Advanced station search
				//https://api.radio-browser.info/#Advanced_station_search
				final StringBuilder url = new StringBuilder(256);
				url.append("stations/search?hidebroken=true&order=clickcount&reverse=true&limit=");
				url.append(PAGE_SIZE);
				url.append("&offset=");
				url.append(offset);
				appendParameter(url, "name", searchTerm);
				appendParameter(url, "country", searchCountry);
				appendTagsParameter(url, (genre != null) ? genre.name : searchTags);

				final JSONArray stations = fetchJsonArray(url.toString(), this, myVersion);
				if (stations == null || myVersion != version)
					return;
				final int count = stations.length();
				offset += count;
				for (int i = 0; i < count && currentStationIndex < MAX_COUNT; i++) {
					final JSONObject obj = stations.optJSONObject(i);
					if (obj == null)
						continue;
					final RadioStation station = parseStation(obj, true);
					if (station == null)
						continue;
					synchronized (favoritesSync) {
						station.isFavorite = favorites.contains(station);
					}
					if (myVersion != version)
						return;
					items[currentStationIndex++] = station;
				}
				couldHaveMoreResults = (count >= PAGE_SIZE);
				//if we were not able to add at least 10 new stations, repeat the entire procedure
			} while (myVersion == version && currentStationIndex < (oldStationIndex + 10) && couldHaveMoreResults);
			if (sendMessages)
				fetchStationsInternalResultsFound(myVersion, currentStationIndex, couldHaveMoreResults && currentStationIndex < MAX_COUNT && offset < MAX_OFFSET);
			err = 0;
		} catch (Throwable ex) {
			err = -1;
		} finally {
			if (err < 0 && sendMessages)
				fetchStationsInternalError(myVersion, err);
			System.gc();
		}
	}

	@Override
	protected void readCache(RadioStationCache cache) {
		super.readCache(cache);
		offset = cache.pageNumber;
		currentStationIndex = cache.currentStationIndex;
	}

	@Override
	protected void writeCache(RadioStationCache cache) {
		cache.pageNumber = offset;
		cache.currentStationIndex = currentStationIndex;
	}

	@Override
	protected void storeCache(RadioStationCache cache) {
		Player.radioStationCache = cache;
	}
}
