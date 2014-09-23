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
	public final String title, uri, type, /*listeners,*/ description, onAir, tags, m3uUri;
	private final int hash;
	public boolean isFavorite;
	
	public RadioStation(String title, String uri, String type, String description, String onAir, String tags, String m3uUri, boolean isFavorite) {
		this.title = title;
		this.uri = uri;
		this.type = type;
		//this.listeners = listeners;
		this.description = description;
		this.onAir = onAir;
		this.tags = tags;
		this.m3uUri = m3uUri;
		this.isFavorite = isFavorite;
		this.hash = m3uUri.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		return ((o instanceof RadioStation) && ((RadioStation)o).m3uUri.equalsIgnoreCase(m3uUri));
	}
	
	@Override
	public int hashCode() {
		return hash;
	}
	
	@Override
	public String toString() {
		return title;
	}
	
	public void serialize(OutputStream os) throws IOException {
		//NEVER change this order! (changing will destroy existing data)
		Serializer.serializeString(os, title);
		Serializer.serializeString(os, uri);
		Serializer.serializeString(os, type);
		Serializer.serializeString(os, description);
		Serializer.serializeString(os, onAir);
		Serializer.serializeString(os, tags);
		Serializer.serializeString(os, m3uUri);
		Serializer.serializeInt(os, 0); //flags
		Serializer.serializeInt(os, 0); //flags
	}
	
	public static RadioStation deserialize(InputStream is, boolean isFavorite) throws IOException {
		String title, uri, type, description, onAir, tags, m3uUri;
		//NEVER change this order! (changing will destroy existing data)
		title = Serializer.deserializeString(is);
		uri = Serializer.deserializeString(is);
		type = Serializer.deserializeString(is);
		description = Serializer.deserializeString(is);
		onAir = Serializer.deserializeString(is);
		tags = Serializer.deserializeString(is);
		m3uUri = Serializer.deserializeString(is);
		Serializer.deserializeInt(is); //flags
		Serializer.deserializeInt(is); //flags
		return new RadioStation(title, uri, type, description, onAir, tags, m3uUri, isFavorite);
	}
}
