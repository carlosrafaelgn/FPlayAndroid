//
// FPlayAndroid is distributed under the FreeBSD License
//
// Copyright (c) 2013, Carlos Rafael Gimenes das Neves
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
package br.com.carlosrafaelgn.fplay.util;

import android.os.SystemClock;

import br.com.carlosrafaelgn.fplay.activity.MainHandler;

public class Timer {
	public static interface TimerHandler {
		public void handleTimer(Timer timer, Object param);
	}
	
	private final Object sync;
	private final Runnable runnable;
	private final TimerHandler timerHandler;
	private final String name;
	private volatile int interval, version;
	private volatile boolean handledOnMain, compensatingForDelays;
	private volatile TimerThread thread;
	
	private class TimerThread extends Thread {
		private final int myVersion;
		private final boolean oneShot;
		private final Object param;
		
		public TimerThread(int myVersion, boolean oneShot, String name, Object param) {
			super((name == null) ? "Timer Thread" : name);
			this.myVersion = myVersion;
			this.oneShot = oneShot;
			this.param = param;
		}
		
		@Override
		public void run() {
			if (MainHandler.isOnMainThread()) {
				if (version == myVersion) {
					if (runnable != null)
						runnable.run();
					else
						timerHandler.handleTimer(Timer.this, param);
				}
				return;
			}
			long lastTime = SystemClock.elapsedRealtime();
			while (version == myVersion) {
				if (compensatingForDelays) {
					final long now = SystemClock.elapsedRealtime();
					final int actualInterval = interval - ((int)now - (int)lastTime);
					lastTime = now;
					if (actualInterval > 0) {
						synchronized (sync) {
							try {
								sync.wait(actualInterval);
							} catch (InterruptedException e) { }
						}
					} else {
						synchronized (sync) {
							try {
								sync.wait(1); //just not to hog the CPU!
							} catch (InterruptedException e) { }
						}
					}
				} else {
					synchronized (sync) {
						try {
							sync.wait(interval);
						} catch (InterruptedException e) { }
					}
				}
				try {
					if (version == myVersion) {
						if (handledOnMain) {
							MainHandler.post(this);
						} else {
							if (runnable != null)
								runnable.run();
							else
								timerHandler.handleTimer(Timer.this, param);
						}
					}
				} catch (Throwable ex) {
					System.err.println(ex);
					break;
				}
				if (oneShot)
					break;
			}
			if (version == myVersion) {
				synchronized (sync) {
					if (version == myVersion)
						thread = null;
				}
			}
		}
	}
	
	public Timer(Runnable runnable) {
		this.sync = new Object();
		this.runnable = runnable;
		this.timerHandler = null;
		this.name = null;
	}
	
	public Timer(Runnable runnable, String name) {
		this.sync = new Object();
		this.runnable = runnable;
		this.timerHandler = null;
		this.name = name;
	}
	
	public Timer(TimerHandler timerHandler) {
		this.sync = new Object();
		this.runnable = null;
		this.timerHandler = timerHandler;
		this.name = null;
	}
	
	public Timer(TimerHandler timerHandler, String name) {
		this.sync = new Object();
		this.runnable = null;
		this.timerHandler = timerHandler;
		this.name = name;
	}
	
	public void start(int interval, boolean oneShot) {
		synchronized (sync) {
			this.version++;
			this.interval = interval;
			this.thread = new TimerThread(version, oneShot, name, null);
			this.thread.start();
		}
	}
	
	public void start(int interval, boolean oneShot, Object param) {
		synchronized (sync) {
			this.version++;
			this.interval = interval;
			this.thread = new TimerThread(version, oneShot, name, param);
			this.thread.start();
		}
	}
	
	public void stop() {
		if (thread != null) {
			synchronized (sync) {
				if (thread != null) {
					version++;
					sync.notifyAll();
					thread = null;
				}
			}
		}
	}
	
	public void stopAndWait() {
		if (thread != null) {
			Thread t;
			synchronized (sync) {
				t = thread;
				if (thread != null) {
					version++;
					sync.notifyAll();
					thread = null;
				}
			}
			if (t != null) {
				try {
					t.join();
				} catch (InterruptedException e) { }
			}
		}
	}
	
	public int getInterval() {
		return interval;
	}
	
	public boolean isAlive() {
		return (thread != null);
	}
	
	public boolean isCompensatingForDelays() {
		return compensatingForDelays;
	}
	
	public void setCompensatingForDelays(boolean compensatingForDelays) {
		this.compensatingForDelays = compensatingForDelays;
	}
	
	public boolean isHandledOnMain() {
		return handledOnMain;
	}
	
	public void setHandledOnMain(boolean handledOnMain) {
		this.handledOnMain = handledOnMain;
	}
}
