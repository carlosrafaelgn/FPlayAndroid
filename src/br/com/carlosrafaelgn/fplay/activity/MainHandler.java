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
//    list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
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
package br.com.carlosrafaelgn.fplay.activity;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

public final class MainHandler extends Handler {
	private static MainHandler theHandler;
	private static Thread mainThread;
	
	private MainHandler(Context context) {
		super(context.getMainLooper());
	}
	
	public static MainHandler initialize(Context context) {
		if (theHandler == null) {
			theHandler = new MainHandler(context);
			mainThread = theHandler.getLooper().getThread();
		}
		return theHandler;
	}
	
	public static boolean isOnMainThread() {
		return (mainThread == Thread.currentThread());
	}
	
	public static void postToMainThread(Runnable runnable) {
		theHandler.post(runnable);
	}
	
	public static void sendMessage(MainHandler.Callback callback, int what) {
		theHandler.sendMessageAtTime(Message.obtain(theHandler, what, callback), SystemClock.uptimeMillis());
	}
	
	public static void sendMessage(MainHandler.Callback callback, int what, int arg1, int arg2) {
		theHandler.sendMessageAtTime(Message.obtain(theHandler, what, arg1, arg2, callback), SystemClock.uptimeMillis());
	}
	
	public static void sendMessageDelayed(MainHandler.Callback callback, int what, long delayMillis) {
		theHandler.sendMessageAtTime(Message.obtain(theHandler, what, callback), SystemClock.uptimeMillis() + delayMillis);
	}
	
	public static void sendMessageDelayed(MainHandler.Callback callback, int what, int arg1, int arg2, long delayMillis) {
		theHandler.sendMessageAtTime(Message.obtain(theHandler, what, arg1, arg2, callback), SystemClock.uptimeMillis() + delayMillis);
	}
	
	public static void sendMessageAtTime(MainHandler.Callback callback, int what, int arg1, int arg2, long when) {
		theHandler.sendMessageAtTime(Message.obtain(theHandler, what, arg1, arg2, callback), when);
	}
	
	@Override
	public void dispatchMessage(Message msg) {
		final Runnable r = msg.getCallback();
		if (r != null) {
			r.run();
			msg.obj = null;
		} else {
			final MainHandler.Callback c = (MainHandler.Callback)msg.obj;
			msg.obj = null;
			c.handleMessage(msg);
		}
	}
}
