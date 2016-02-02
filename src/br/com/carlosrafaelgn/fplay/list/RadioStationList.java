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
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.activity.MainHandler;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.RadioStationView;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.ArraySorter;
import br.com.carlosrafaelgn.fplay.util.Serializer;

public abstract class RadioStationList extends BaseList<RadioStation> implements Runnable, ArraySorter.Comparer<RadioStation>, MainHandler.Callback {
	public interface RadioStationAddedObserver {
		void onRadioStationAdded();
	}

	//after analyzing the results obtained from http://dir.xiph.org/xxx
	//I noticed that there are never more than 5 pages of results,
	//with 20 results each ;)
	//as for SHOUTcast... their limit is 500, but we will truncate to 100.........
	public static final int MAX_COUNT = 100;
	private static final int MSG_ERROR = 0x0300;
	private static final int MSG_MORE_RESULTS = 0x0301;

	private boolean loading, favoritesLoaded, favoritesChanged, containsFavorites;
	protected final Object favoritesSync;
	protected final HashSet<RadioStation> favorites;
	private volatile boolean readyToFetch, isSavingFavorites, reset, moreResults;
	protected volatile int version;
	private volatile RadioStationGenre genreToFetch;
	private volatile String searchTermToFetch;
	private volatile Context context;
	private RadioStationCache cache;
	public RadioStationAddedObserver radioStationAddedObserver;
	
	public RadioStationList(RadioStationCache cache) {
		super(RadioStation.class, MAX_COUNT);
		this.items = new RadioStation[MAX_COUNT];
		this.readyToFetch = true;
		this.favoritesSync = new Object();
		this.favorites = new HashSet<>(32);
		this.moreResults = true;
		if (cache != null && cache.stations != null && cache.stations.length >= cache.count && cache.count > 0)
			this.cache = cache;
	}

	public final boolean hasMoreResults() {
		return moreResults;
	}

	public final boolean isLoading() {
		return loading;
	}
	
	private void loadingProcessChanged(boolean started) {
		loading = started;
		if (UI.browserActivity != null)
			UI.browserActivity.loadingProcessChanged(started);
	}
	
	public final void cancel() {
		version++; //this is enough to cancel the other thread
		if (version <= 0) //we wrapped around (protection to ensure -version will always work properly)
			version = 1;
		if (loading)
			loadingProcessChanged(false);
	}

	protected static String readStringIfPossible(XmlPullParser parser, StringBuilder sb) throws Throwable {
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
				ex.printStackTrace();
			}
			try {
				if (fs != null)
					fs.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
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
				ex.printStackTrace();
			}
			try {
				if (fs != null)
					fs.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	@Override
	public final int compare(RadioStation a, RadioStation b) {
		int r = a.title.compareToIgnoreCase(b.title);
		if (r != 0)
			return r;
		r = a.onAir.compareToIgnoreCase(b.onAir);
		if (r != 0)
			return r;
		return a.m3uUrl.compareTo(b.m3uUrl);
	}

	@Override
	public final void run() {
		final int myVersion = version;
		final Context context = this.context;
		final RadioStationGenre genre = genreToFetch;
		final String searchTerm = searchTermToFetch;
		final boolean isSavingFavorites = this.isSavingFavorites;
		final boolean reset = this.reset;
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
						ex.printStackTrace();
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
								MainHandler.sendMessage(this, MSG_MORE_RESULTS, myVersion, count);
							}
						}
					} catch (Throwable ex) {
						err = -2;
					} finally {
						if (myVersion == version && err < 0)
							MainHandler.sendMessage(this, MSG_ERROR, myVersion, err);
					}
				}
			}
			return;
		}

		fetchStationsInternal(context, myVersion, genre, searchTerm, reset, true);
	}

	public final void addFavoriteStation(RadioStation station) {
		synchronized (favoritesSync) {
			if (favoritesLoaded) {
				station.isFavorite = true;
				favoritesChanged |= favorites.add(station);
			}
		}
	}

	public final void removeFavoriteStation(RadioStation station) {
		synchronized (favoritesSync) {
			if (favoritesLoaded) {
				station.isFavorite = false;
				favoritesChanged |= favorites.remove(station);
			}
		}
	}

	protected final void fetchStationsInternalResultsFound(int myVersion, int currentStationIndex, boolean moreResults) {
		if (myVersion == version)
			MainHandler.sendMessage(this, MSG_MORE_RESULTS, moreResults ? myVersion : -myVersion, currentStationIndex);
	}

	protected final void fetchStationsInternalError(int myVersion, int err) {
		if (myVersion == version)
			MainHandler.sendMessage(this, MSG_ERROR, myVersion, err);
	}

	protected abstract void fetchStationsInternal(Context context, int myVersion, RadioStationGenre genre, String searchTerm, boolean reset, boolean sendMessages);

	public final boolean fetchStations(Context context, RadioStationGenre genre, String searchTerm, boolean reset) {
		while (!readyToFetch)
			Thread.yield();
		cancel();
		containsFavorites = false;
		if (reset) {
			moreResults = true;
			clear();
		}
		if (!moreResults)
			return false;
		this.reset = reset;
		loadingProcessChanged(true);
		final Thread t = new Thread(this, "Radio Station Fetcher Thread");
		isSavingFavorites = false;
		genreToFetch = genre;
		searchTermToFetch = searchTerm;
		this.context = context;
		readyToFetch = false;
		try {
			t.start();
			return true;
		} catch (Throwable ex) {
			readyToFetch = true;
			loadingProcessChanged(false);
			return false;
		}
	}

	public final void fetchFavorites(Context context) {
		while (!readyToFetch)
			Thread.yield();
		cancel();
		clear();
		loadingProcessChanged(true);
		final Thread t = new Thread(this, "Icecast Favorite Stations Fetcher Thread");
		containsFavorites = true;
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

	public final void saveFavorites(Context context) {
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

	public final boolean restoreCacheIfValid() {
		cache = RadioStationCache.getIfNotExpired(cache);
		if (cache == null)
			return false;
		while (!readyToFetch)
			Thread.yield();
		cancel();
		this.reset = false;
		containsFavorites = false;
		isSavingFavorites = false;
		readCache(cache);
		return true;
	}

	protected void readCache(RadioStationCache cache) {
		clear();
		this.cache = cache;
		System.arraycopy(cache.stations, 0, items, 0, cache.count);
		count = cache.count;
		moreResults = cache.moreResults;
		notifyDataSetChanged(-1, CONTENT_ADDED);
	}

	protected abstract void writeCache(RadioStationCache cache);

	protected abstract void storeCache(RadioStationCache cache);

	public final RadioStation tryToFetchRadioStationAgain(Context context, String title) {
		try {
			version++;
			loading = false;
			fetchStationsInternal(context, version, null, title, true, false);
			if (items == null)
				return null;
			int i = 0;
			RadioStation radioStation;
			while (i < items.length && (radioStation = items[i]) != null) {
				if (title.equalsIgnoreCase(radioStation.title))
					return radioStation;
				i++;
			}
			return null;
		} catch (Throwable ex) {
			return null;
		}
	}

	@Override
	public final boolean handleMessage(Message msg) {
		if (Math.abs(msg.arg1) != version)
			return true;
		switch (msg.what) {
		case MSG_ERROR:
			moreResults = false;
			loadingProcessChanged(false);
			UI.toast(Player.getService(), ((msg.arg2 != -2) && !Player.isConnectedToTheInternet()) ? R.string.error_connection : R.string.error_gen);
			break;
		case MSG_MORE_RESULTS:
			if (!containsFavorites)
				moreResults = (msg.arg1 > 0);
			loadingProcessChanged(false);
			//protection against out of order messages... does this really happen? ;)
			if (msg.arg2 > count) {
				//items are always appended :)
				modificationVersion++;
				final int c = count;
				count = msg.arg2;
				addingItems(c, c - count);
				notifyDataSetChanged(-1, CONTENT_ADDED);
				if (radioStationAddedObserver != null)
					radioStationAddedObserver.onRadioStationAdded();
				if (!containsFavorites) {
					if (cache == null) {
						cache = new RadioStationCache();
						storeCache(cache);
					}
					cache.copyData(items, count, moreResults);
					writeCache(cache);
				}
			} else if (!containsFavorites && cache != null) {
				cache.moreResults = moreResults;
			}
			break;
		}
		return true;
	}

	@Override
	public final View getView(int position, View convertView, ViewGroup parent) {
		final RadioStationView view = ((convertView != null) ? (RadioStationView)convertView : new RadioStationView(Player.getService()));
		view.setItemState(items[position], position, getItemState(position));
		return view;
	}

	@Override
	public final int getViewHeight() {
		return RadioStationView.getViewHeight();
	}
}
