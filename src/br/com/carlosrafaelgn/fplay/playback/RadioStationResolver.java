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
package br.com.carlosrafaelgn.fplay.playback;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import br.com.carlosrafaelgn.fplay.list.Song;

final class RadioStationResolver extends Thread {
	private final Object sync;
	private volatile boolean alive;
	private final int msg, arg1;
	private Handler handler;
	private Song.RadioStationExtraInfo extraInfo;

	public RadioStationResolver(int msg, int arg1, Handler handler, Song.RadioStationExtraInfo extraInfo) {
		super("Radio Station Resolver Thread");
		sync = new Object();
		alive = true;
		this.msg = msg;
		this.arg1 = arg1;
		this.handler = handler;
		this.extraInfo = extraInfo;
	}

	@Override
	public void run() {
		final Song.RadioStationExtraInfo extraInfo = this.extraInfo;
		int err = -1;
		Object result = null;
		try {
			err = 0;
			result = "";
		} catch (Throwable ex) {
			err = -1;
			result = ex;
		} finally {
			synchronized (sync) {
				if (alive) {
					alive = false;
					if (handler != null) {
						handler.sendMessageAtTime(Message.obtain(handler, msg, arg1, err, result), SystemClock.uptimeMillis());
						handler = null;
					}
					this.extraInfo = null;
				}
			}
		}
	}

	public void cancel() {
		synchronized (sync) {
			alive = false;
			handler = null;
			extraInfo = null;
		}
	}
}
