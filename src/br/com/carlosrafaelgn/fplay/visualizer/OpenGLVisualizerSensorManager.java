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
package br.com.carlosrafaelgn.fplay.visualizer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

public final class OpenGLVisualizerSensorManager extends Thread implements Handler.Callback, SensorEventListener {
	private static final int MSG_REGISTER = 0x0600;
	private static final int MSG_UNREGISTER = 0x0601;
	private static final int MSG_RESET = 0x0602;
	private Looper looper;
	private Handler handler;
	private SensorManager sensorManager;
	private Sensor accel, gyro, mag;
	public final boolean isCapable, hasGyro;

	public OpenGLVisualizerSensorManager(Context context, boolean forceMagnetic) {
		super("OpenGL Visualizer Sensor Manager Thread");
		try {
			sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		} catch (Throwable ex) {
			sensorManager = null;
			accel = null;
			gyro = null;
			mag = null;
		}
		if (sensorManager != null) {
			try {
				accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			} catch (Throwable ex) {
				accel = null;
			}
			if (!forceMagnetic) {
				try {
					gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
				} catch (Throwable ex) {
					gyro = null;
				}
			}
			//let's create a fallback mechanism: the magnetic sensor will be used in
			//devices where the gyroscope is not present
			if (gyro == null) {
				try {
					mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
				} catch (Throwable ex) {
					mag = null;
				}
			}
		}
		isCapable = (accel != null && (gyro != null || mag != null));
		if (!isCapable) {
			sensorManager = null;
			accel = null;
			gyro = null;
			mag = null;
			hasGyro = false;
		} else {
			hasGyro = (gyro != null);
		}
	}

	public void release() {
		synchronized (this) {
			if (looper != null) {
				looper.quit();
				try {
					wait();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
			sensorManager = null;
			accel = null;
			gyro = null;
			mag = null;
		}
	}

	public void reset() {
		synchronized (this) {
			if (handler != null)
				handler.sendEmptyMessageAtTime(MSG_RESET, SystemClock.uptimeMillis());
		}
	}

	private void _reset() {
		SimpleVisualizerJni.glOnSensorReset();
	}

	public void register() {
		synchronized (this) {
			if (handler != null)
				handler.sendEmptyMessageAtTime(MSG_REGISTER, SystemClock.uptimeMillis());
		}
	}

	private void _register() {
		if (sensorManager != null && accel != null && (gyro != null || mag != null)) {
			//SENSOR_DELAY_UI provides a nice refresh rate, SENSOR_DELAY_GAME
			//provides an awesome refresh rate, but SENSOR_DELAY_FASTEST is the fastest!
			//...different algorithms, different refresh rates!
			if (gyro != null) {
				sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST);
				sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_FASTEST);
			} else {
				sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
				sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
			}
		}
	}

	public void unregister() {
		synchronized (this) {
			if (handler != null)
				handler.sendEmptyMessageAtTime(MSG_UNREGISTER, SystemClock.uptimeMillis());
		}
	}

	private void _unregister() {
		if (sensorManager != null)
			sensorManager.unregisterListener(this);
	}

	@Override
	public void run() {
		Looper.prepare();
		synchronized (this) {
			looper = Looper.myLooper();
			handler = new Handler(looper, this);
			notify();
		}
		Looper.loop();
		synchronized (this) {
			looper = null;
			handler = null;
			_unregister();
			notify();
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_REGISTER:
			_register();
			break;
		case MSG_UNREGISTER:
			_unregister();
			break;
		case MSG_RESET:
			_reset();
			break;
		}
		return true;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		SimpleVisualizerJni.glOnSensorData(event.timestamp, (event.sensor == accel) ? Sensor.TYPE_ACCELEROMETER : Sensor.TYPE_GYROSCOPE, event.values);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
}
