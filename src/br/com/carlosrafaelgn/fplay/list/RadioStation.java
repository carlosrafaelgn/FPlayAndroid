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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import br.com.carlosrafaelgn.fplay.util.Serializer;

public final class RadioStation extends BaseItem {
	private static final String UNIT_SEPARATOR = "\u001F";
	private static final char UNIT_SEPARATOR_CHAR = '\u001F';
	public final String title, stationSiteUrl, type, /*listeners,*/ description, onAir, tags, m3uUrl;
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
		return new String[] {
			path.substring(0, i),
			path.substring(i + 1, i2),
			path.substring(i2 + 1, i3),
			path.substring(i3 + 1)
		};
	}

	public static String extractUrl(String path) {
		final int i = path.indexOf(RadioStation.UNIT_SEPARATOR_CHAR);
		return ((i <= 0) ? path : path.substring(0, i));
	}

	public RadioStation(String title, String stationSiteUrl, String type, String description, String onAir, String tags, String m3uUrl, boolean isFavorite, boolean isShoutcast) {
		this.title = title;
		this.stationSiteUrl = stationSiteUrl;
		this.type = type;
		//this.listeners = listeners;
		this.description = description;
		this.onAir = onAir;
		this.tags = tags;
		this.m3uUrl = m3uUrl;
		this.isFavorite = isFavorite;
		this.isShoutcast = isShoutcast;
		this.hash = title.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return ((o == this) || ((o instanceof RadioStation) && ((RadioStation)o).title.equalsIgnoreCase(title)));
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return title;
	}

	public String buildFullPath(String streamUrl) {
		return streamUrl + UNIT_SEPARATOR + m3uUrl + UNIT_SEPARATOR + title + UNIT_SEPARATOR + (isShoutcast ? "1" : "0");
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
		Serializer.serializeInt(os, isShoutcast ? 1 : 0); //flags
		Serializer.serializeInt(os, 0); //flags
	}

	public static RadioStation deserialize(InputStream is, boolean isFavorite) throws IOException {
		String title, stationSiteUrl, type, description, onAir, tags, m3uUri;
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
		return new RadioStation(title, stationSiteUrl, type, description, onAir, tags, m3uUri, isFavorite, (flags & 1) != 0);
	}
}
