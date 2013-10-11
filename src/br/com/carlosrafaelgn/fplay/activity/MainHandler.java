package br.com.carlosrafaelgn.fplay.activity;

import android.content.Context;
import android.os.Handler;

public final class MainHandler {
	private static Handler handler;
	
	public static void initialize(Context context) {
		if (handler == null)
			handler = new Handler(context.getMainLooper());
	}
	
	public static boolean isOnMainThread() {
		return (handler.getLooper().getThread() == Thread.currentThread());
	}
	
	public static boolean post(Runnable runnable) {
		return handler.post(runnable);
	}
}
