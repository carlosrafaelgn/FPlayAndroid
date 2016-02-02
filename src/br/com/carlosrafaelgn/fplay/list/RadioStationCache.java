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

import android.os.SystemClock;

public final class RadioStationCache {
	public static final int EXPIRATION_TIME_MS = 30 * 60 * 1000; //30 minutes

	public final long timestamp;
	public int count, pageNumber, currentStationIndex;
	public RadioStation[] stations;
	public boolean moreResults;

	public RadioStationCache() {
		this.timestamp = SystemClock.elapsedRealtime();
		this.stations = new RadioStation[RadioStationList.MAX_COUNT];
	}

	public void copyData(RadioStation[] stations, int count, boolean moreResults) {
		if (this.stations == null || stations == null)
			return;
		System.arraycopy(stations, 0, this.stations, 0, count);
		this.count = count;
		this.moreResults = moreResults;
	}

	public static RadioStationCache getIfNotExpired(Object cache) {
		if (cache != null && cache instanceof RadioStationCache) {
			final RadioStationCache radioStationCache = (RadioStationCache)cache;
			if (radioStationCache.timestamp <= (SystemClock.elapsedRealtime() + EXPIRATION_TIME_MS))
				return radioStationCache;
			for (int i = radioStationCache.stations.length - 1; i >= 0; i--)
				radioStationCache.stations[i] = null;
			radioStationCache.stations = null;
			return null;
		}
		return null;
	}
}
