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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class ShoutcastRadioStationList extends RadioStationList {
	private final String noOnAir, noDescription;
	private int pageNumber, currentStationIndex;
	private String baseUrl;

	public ShoutcastRadioStationList(String noOnAir, String noDescription) {
		this.noOnAir = noOnAir;
		this.noDescription = noDescription;
	}

	@Override
	protected void fetchStationsInternal(Context context, int myVersion, RadioStationGenre genre, String searchTerm, boolean reset) {
		int err = 0;
		InputStream datInputStream = null;
		try {
			if (reset && myVersion == version) {
				pageNumber = 0;
				currentStationIndex = 0;
			}
			boolean hasResults;

			if (baseUrl == null) {
				final byte[] tmp = new byte[67];
				final byte[] tmp2 = {0x00, 0x08, 0x04, 0x0c, 0x02, 0x0a, 0x06, 0x0e, 0x01, 0x09, 0x05, 0x0d, 0x03, 0x0b, 0x07, 0x0f};
				datInputStream = context.getAssets().open("binary/url.dat");
				if (datInputStream.read(tmp, 0, 67) == 67) {
					for (int i = 0; i < 67; i++) {
						final byte b = tmp[i];
						tmp[i] = (byte)((tmp2[b & 0x0f] << 4) | tmp2[(b >> 4) & 0x0f]);
					}
				}
				datInputStream.close();
				datInputStream = null;
				//Sorry, everyone!!!
				//As a part of the process of getting a DevID, they ask you not to make it publicly available :(
				//But.... you can get your own DevID for FREE here: http://www.shoutcast.com/Partners :)
				baseUrl = new String(tmp, 0, 67, "UTF-8");
			}

			fetchStationsInternalResultsFound(myVersion, currentStationIndex, false);
		} catch (Throwable ex) {
			err = -1;
		} finally {
			if (datInputStream != null) {
				try {
					datInputStream.close();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
			if (err < 0)
				fetchStationsInternalError(myVersion, err);
		}
	}
}
