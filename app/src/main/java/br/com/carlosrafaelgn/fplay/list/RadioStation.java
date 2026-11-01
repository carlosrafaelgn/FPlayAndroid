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

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import br.com.carlosrafaelgn.fplay.util.Serializer;

public final class RadioStation extends BaseItem {
	private static final String UNIT_SEPARATOR = "\u001F";
	private static final char UNIT_SEPARATOR_CHAR = '\u001F';
	//when isShoutcast is false, the station came from the Community Radio Station Index,
	//m3uUrl stores the station's url_resolved (which is the actual stream url, and not
	//an M3U playlist url) and stationUuid stores the station's stationuuid
	public final String title, stationSiteUrl, type, /*listeners,*/ description, onAir, tags, m3uUrl, stationUuid;
	private final int hash;
	public boolean isFavorite, isShoutcast;

	public static String[] splitPath(String path) {
		final int i = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR);
		if (i <= 0)
			return null;
		final int i2 = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR, i + 1);
		final int i3 = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR, i2 + 1);
		if (i2 <= (i + 1) || i3 <= (i2 + 1))
			return null;
		//the fifth part (stationUuid) is optional, and it is only present in paths
		//created from Community Radio Station Index stations
		final int i4 = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR, i3 + 1);
		return new String[] {
			path.substring(0, i),
			path.substring(i + 1, i2),
			path.substring(i2 + 1, i3),
			((i4 < 0) ? path.substring(i3 + 1) : path.substring(i3 + 1, i4)),
			((i4 < 0 || i4 >= (path.length() - 1)) ? null : path.substring(i4 + 1))
		};
	}

	public static String extractUrl(String path) {
		final int i = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR);
		return ((i <= 0) ? path : path.substring(0, i));
	}

	public static boolean isRadioUrl(String path) {
		return (path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR) > 0);
	}

	public RadioStation(String title, String stationSiteUrl, String type, String description, String onAir, String tags, String m3uUrl, String stationUuid, boolean isFavorite, boolean isShoutcast) {
		this.title = title;
		this.stationSiteUrl = stationSiteUrl;
		this.type = type;
		//this.listeners = listeners;
		this.description = description;
		this.onAir = onAir;
		this.tags = tags;
		this.m3uUrl = m3uUrl;
		this.stationUuid = ((stationUuid == null || stationUuid.length() == 0) ? null : stationUuid);
		this.isFavorite = isFavorite;
		this.isShoutcast = isShoutcast;
		//hash must be computed using the same rules as equals()
		this.hash = ((this.stationUuid != null) ? this.stationUuid : title).toLowerCase(Locale.US).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof RadioStation))
			return false;
		final RadioStation s = (RadioStation)o;
		//the Community Radio Station Index has a lot of stations with the same name,
		//so we use their uuids to tell them apart (SHOUTcast stations have no uuids)
		if (stationUuid != null || s.stationUuid != null)
			return (stationUuid != null && s.stationUuid != null && stationUuid.equalsIgnoreCase(s.stationUuid));
		return s.title.equalsIgnoreCase(title);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@NonNull
	@Override
	public String toString() {
		return title;
	}

	public String buildFullPath(String streamUrl) {
		return streamUrl + UNIT_SEPARATOR + m3uUrl + UNIT_SEPARATOR + title + UNIT_SEPARATOR + (isShoutcast ? "1" : "0") + ((stationUuid == null) ? "" : (UNIT_SEPARATOR + stationUuid));
	}

	public void serialize(OutputStream os) throws IOException {
		//NEVER change this order! (changing will destroy existing data)
		Serializer.serializeString(os, title);
		Serializer.serializeString(os, stationSiteUrl);
		Serializer.serializeString(os, type);
		Serializer.serializeString(os, description);
		Serializer.serializeString(os, onAir);
		Serializer.serializeString(os, tags);
		Serializer.serializeString(os, m3uUrl);
		Serializer.serializeInt(os, (isShoutcast ? 1 : 0) | ((stationUuid != null) ? 2 : 0)); //flags
		Serializer.serializeInt(os, 0); //flags
		if (stationUuid != null)
			Serializer.serializeString(os, stationUuid);
	}

	public static RadioStation deserialize(InputStream is, boolean isFavorite) throws IOException {
		String title, stationSiteUrl, type, description, onAir, tags, m3uUri, stationUuid = null;
		//NEVER change this order! (changing will destroy existing data)
		title = Serializer.deserializeString(is);
		stationSiteUrl = Serializer.deserializeString(is);
		type = Serializer.deserializeString(is);
		description = Serializer.deserializeString(is);
		onAir = Serializer.deserializeString(is);
		tags = Serializer.deserializeString(is);
		m3uUri = Serializer.deserializeString(is);
		final int flags = Serializer.deserializeInt(is); //flags
		Serializer.deserializeInt(is); //flags
		if ((flags & 2) != 0)
			stationUuid = Serializer.deserializeString(is);
		return new RadioStation(title, stationSiteUrl, type, description, onAir, tags, m3uUri, stationUuid, isFavorite, (flags & 1) != 0);
	}
}
