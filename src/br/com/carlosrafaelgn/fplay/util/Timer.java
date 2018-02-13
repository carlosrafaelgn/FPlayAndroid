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
package br.com.carlosrafaelgn.fplay.util;

import android.os.Message;
import android.os.SystemClock;

import br.com.carlosrafaelgn.fplay.activity.MainHandler;

public final class Timer implements MainHandler.Callback {
	public interface TimerHandler {
		void handleTimer(Timer timer, Object param);
	}
	
	private static final int MSG_ONESHOT = 0x0200;
	private static final int MSG_INTERVAL = 0x0201;
	private final Object sync;
	private final String name;
	private final boolean oneShot, handledOnMain, compensatingForDelays;
	private volatile TimerHandler timerHandler;
	private volatile int interval, version;
	private volatile TimerThread thread;
	private volatile boolean alive, paused;
	private volatile Object param;
	private long nextTime;
	
	private class TimerThread extends Thread {
		private final int myVersion;
		
		public TimerThread(int myVersion, String name) {
			super((name == null) ? "Timer Thread" : name);
			this.myVersion = myVersion;
		}
		
		@Override
		public void run() {
			long lastTime = SystemClock.uptimeMillis();
			while (version == myVersion) {
				if (paused) {
					synchronized (sync) {
						try {
							sync.wait();
						} catch (InterruptedException ex) {
							ex.printStackTrace();
						}
					}
					if (version != myVersion)
						break;
				}
				if (compensatingForDelays) {
					final long now = SystemClock.uptimeMillis();
					final int actualInterval = interval - ((int)now - (int)lastTime);
					lastTime = now;
					if (actualInterval > 0) {
						synchronized (sync) {
							try {
								sync.wait(actualInterval);
							} catch (InterruptedException ex) {
								ex.printStackTrace();
							}
						}
					} else {
						synchronized (sync) {
							try {
								sync.wait(1); //just not to hog the CPU!
							} catch (InterruptedException ex) {
								ex.printStackTrace();
							}
						}
					}
				} else {
					synchronized (sync) {
						try {
							sync.wait(interval);
						} catch (InterruptedException ex) {
							ex.printStackTrace();
						}
					}
				}
				if (paused)
					continue;
				try {
					if (version == myVersion) {
						if (timerHandler != null)
							timerHandler.handleTimer(Timer.this, param);
					}
				} catch (Throwable ex) {
					ex.printStackTrace();
					break;
				}
				if (oneShot)
					break;
			}
			if (version == myVersion) {
				synchronized (sync) {
					if (version == myVersion) {
						thread = null;
						alive = false;
					}
				}
			}
		}
	}

	public Timer(TimerHandler timerHandler, String name, boolean oneShot, boolean handledOnMain, boolean compensatingForDelays) {
		this.sync = new Object();
		this.timerHandler = timerHandler;
		this.name = name;
		this.oneShot = oneShot;
		this.handledOnMain = handledOnMain;
		this.compensatingForDelays = compensatingForDelays;
	}
	
	public void start(int interval) {
		if (handledOnMain) {
			version++;
			this.interval = interval;
			param = null;
			alive = true;
			thread = null;
			if (oneShot) {
				MainHandler.sendMessageAtTime(this, MSG_ONESHOT, version, 0, SystemClock.uptimeMillis() + interval);
			} else {
				if (compensatingForDelays)
					nextTime = SystemClock.uptimeMillis() + interval;
				MainHandler.sendMessageAtTime(this, MSG_INTERVAL, version, 0, SystemClock.uptimeMillis() + interval);
			}
		} else {
			synchronized (sync) {
				version++;
				this.interval = interval;
				param = null;
				alive = true;
				thread = new TimerThread(version, name);
				thread.start();
			}
		}
	}
	
	public void start(int interval, Object param) {
		if (handledOnMain) {
			version++;
			this.interval = interval;
			this.param = param;
			alive = true;
			paused = false;
			thread = null;
			if (oneShot) {
				MainHandler.sendMessageAtTime(this, MSG_ONESHOT, version, 0, SystemClock.uptimeMillis() + interval);
			} else {
				if (compensatingForDelays)
					nextTime = SystemClock.uptimeMillis() + interval;
				MainHandler.sendMessageAtTime(this, MSG_INTERVAL, version, 0, SystemClock.uptimeMillis() + interval);
			}
		} else {
			synchronized (sync) {
				version++;
				if (paused && thread != null)
					sync.notifyAll();
				this.interval = interval;
				this.param = param;
				alive = true;
				paused = false;
				thread = new TimerThread(version, name);
				thread.start();
			}
		}
	}
	
	public void pause() {
		if (alive && !handledOnMain)
			paused = true;
	}
	
	public void resume() {
		if (alive && !handledOnMain && paused) {
			paused = false;
			synchronized (sync) {
				sync.notifyAll();
			}
		}
	}
	
	public void stop() {
		paused = false;
		if (alive) {
			if (handledOnMain) {
				version++;
				MainHandler.removeMessages(this, oneShot ? MSG_ONESHOT : MSG_INTERVAL);
			} else {
				synchronized (sync) {
					version++;
					if (thread != null) {
						sync.notifyAll();
						thread = null;
					}
				}
			}
			alive = false;
		}
	}

	public void release() {
		stop();
		timerHandler = null;
		param = null;
	}
	
	@Override
	public boolean handleMessage(Message msg) {
		if (msg.arg1 == version) {
			switch (msg.what) {
			case MSG_INTERVAL:
				if (timerHandler != null)
					timerHandler.handleTimer(this, param);
				if (alive) {
					if (compensatingForDelays) {
						final long now = SystemClock.uptimeMillis(), next = nextTime + interval;
						nextTime = ((next < now) ? now : next);
						MainHandler.sendMessageAtTime(this, MSG_INTERVAL, version, 0, nextTime);
					} else {
						MainHandler.sendMessageAtTime(this, MSG_INTERVAL, version, 0, SystemClock.uptimeMillis() + interval);
					}
				}
				break;
			case MSG_ONESHOT:
				alive = false;
				if (timerHandler != null)
					timerHandler.handleTimer(this, param);
				break;
			}
		}
		return true;
	}

	public boolean isAlive() {
		return alive;
	}
}
