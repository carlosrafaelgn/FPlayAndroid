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

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import br.com.carlosrafaelgn.fplay.ActivityMain;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.NullDrawable;

//
//Handling Runtime Changes
//http://developer.android.com/guide/topics/resources/runtime-changes.html
//
//<activity> (all attributes, including android:configChanges)
//http://developer.android.com/guide/topics/manifest/activity-element.html
//
public final class ActivityHost extends Activity implements Player.PlayerDestroyedObserver {
	private ClientActivity top;
	private boolean exitOnDestroy;
	private int windowColor;
	private final NullDrawable windowNullDrawable = new NullDrawable();
	private ColorDrawable windowColorDrawable;
	
	public ClientActivity getTopActivity() {
		return top;
	}
	
	public void setExitOnDestroy(boolean exitOnDestroy) {
		this.exitOnDestroy = exitOnDestroy;
	}
	
	private void startActivityInternal(ClientActivity activity) {
		activity.finished = false;
		activity.activity = this;
		activity.previousActivity = top;
		top = activity;
		setWindowColor(top.getDesiredWindowColor());
		activity.onCreate();
		if (!activity.finished) {
			activity.onCreateLayout(true);
			if (!activity.finished)
				activity.onResume();
		}
	}
	
	public void startActivity(ClientActivity activity) {
		if (top != null) {
			top.onPause();
			if (top != null)
				top.onCleanupLayout();
		}
		startActivityInternal(activity);
	}
	
	public void finishActivity(ClientActivity activity, ClientActivity newActivity, int code) {
		if (activity.finished || activity != top)
			return;
		activity.finished = true;
		activity.onPause();
		activity.onCleanupLayout();
		activity.onDestroy();
		if (top != null)
			top = top.previousActivity;
		else
			top = null;
		activity.activity = null;
		activity.previousActivity = null;
		if (newActivity != null) {
			startActivityInternal(newActivity);
		} else {
			if (top == null) {
				finish();
			} else {
				setWindowColor(top.getDesiredWindowColor());
				top.onCreateLayout(false);
				if (top != null && !top.finished) {
					top.onResume();
					if (top != null && !top.finished)
						top.activityFinished(activity, activity.requestCode, code);
				}
			}
		}
		System.gc();
	}
	
	@Override
	public void onBackPressed() {
		if (top != null) {
			if (top.onBackPressed())
				return;
			if (top != null && top.previousActivity != null) {
				finishActivity(top, null, 0);
				return;
			}
		}
		super.onBackPressed();
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (top != null) {
			final View v = top.getNullContextMenuView();
			if (v != null)
				CustomContextMenu.openContextMenu(v, top);
		}
		return false;
	}
	
	//replace onKeyDown with dispatchKeyEvent + event.getAction() + event.getKeyCode()?!?!?!
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		//
		//Allowing applications to play nice(r) with each other: Handling remote control buttons
		//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
		//
		//...In a media playback application, this is used to react to headset button
		//presses when your activity doesn’t have the focus. For when it does, we override
		//the Activity.onKeyDown() or onKeyUp() methods for the user interface to trap the
		//headset button-related events...
		if ((event == null || event.getRepeatCount() == 0) && Player.handleMediaButton(keyCode))
			return true;
		return super.onKeyDown(keyCode, event);
	}
	
	public void setWindowColor(int color) {
		if (color == 0) {
			if (windowColor != color) {
				windowColor = 0;
				getWindow().setBackgroundDrawable(windowNullDrawable);
			}
		} else if (windowColorDrawable == null) {
			windowColor = color;
			windowColorDrawable = new ColorDrawable(color);
			getWindow().setBackgroundDrawable(windowColorDrawable);
		} else if (windowColor != color) {
			windowColor = color;
			windowColorDrawable.setColor(color);
			getWindow().setBackgroundDrawable(windowColorDrawable);
		}
	}
	
	@Override
	protected final void onCreate(Bundle savedInstanceState) {
		//StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
		//	.detectAll()
		//	.penaltyLog()
		//	.build());
		//StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
		//	.detectAll()
		//	.penaltyLog()
		//	.build());
		//top is null everytime onCreate is called, which means
		//everytime a new ActivityMain is created
		//if top was any ClientActivity other than ActivityMain when
		//onSaveInstanceState was called, then the android:viewHierarchyState
		//in the savedInstanceState Bundle will not match the actual view
		//structure that belongs to ActivityMain
		//that's why we pass null to super.onCreate!
		super.onCreate(null);
		UI.initialize(getApplication());
		MainHandler.initialize();
		if (Player.startService(getApplication()))
			UI.setAndroidThemeAccordingly13(this);
		top = new ActivityMain();
		top.finished = false;
		top.activity = this;
		top.previousActivity = null;
		windowColor = 1;
		setWindowColor(top.getDesiredWindowColor());
		top.onCreate();
		if (top != null && !top.finished) {
			top.onCreateLayout(true);
			if (top != null && !top.finished)
				Player.addDestroyedObserver(this);
			else
				finish();
		} else {
			finish();
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		//when the user is returning to the app, just reset the
		//previously stored position, allowing for the current song
		//to be displayed instead
		Player.listFirst = -1;
		Player.listTop = 0;
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.clear(); //see the comments in the method above
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		final boolean i = UI.isLandscape;
		UI.initialize(this);
		if (i != UI.isLandscape) {
			if (top != null) {
				top.onOrientationChanged();
				System.gc();
			}
		}
	}
	
	@Override
	protected void onPause() {
		if (top != null)
			top.onPause();
		Player.setAppIdle(true);
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		Player.setAppIdle(false);
		if (top != null)
			top.onResume();
		super.onResume();
	}
	
	private void finalCleanup() {
		ClientActivity c = top, p;
		top = null;
		while (c != null) {
			//the activity is already paused, so, just clean it up and destroy
			c.finished = true;
			c.onCleanupLayout();
			c.onDestroy();
			p = c.previousActivity;
			c.activity = null;
			c.previousActivity = null;
			c = p;
		}
	}
	
	@Override
	protected void onDestroy() {
		Player.removeDestroyedObserver(this);
		finalCleanup();
		windowColorDrawable = null;
		super.onDestroy();
		if (exitOnDestroy)
			System.exit(0);
	}
	
	@Override
	public void onPlayerDestroyed() {
		finalCleanup();
		finish();
	}
}
