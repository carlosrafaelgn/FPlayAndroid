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
package br.com.carlosrafaelgn.fplay.activity;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import br.com.carlosrafaelgn.fplay.ActivityMain;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;

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
	private ColorDrawable windowColorDrawable;
	
	public ClientActivity getTopActivity() {
		return top;
	}
	
	public void setExitOnDestroy(boolean exitOnDestroy) {
		this.exitOnDestroy = exitOnDestroy;
	}
	
	public void startActivity(ClientActivity activity) {
		if (top != null) {
			top.onPause();
			top.onCleanupLayout();
		}
		activity.finished = false;
		activity.activity = this;
		activity.previousActivity = top;
		top = activity;
		activity.onCreate();
		activity.onCreateLayout(true);
		activity.onResume();
	}
	
	public void finishActivity(ClientActivity activity, int code) {
		if (activity.finished || activity != top)
			return;
		activity.finished = true;
		activity.onPause();
		activity.onCleanupLayout();
		activity.onDestroy();
		top = top.previousActivity;
		activity.activity = null;
		activity.previousActivity = null;
		if (top == null) {
			finish();
		} else {
			top.onCreateLayout(false);
			top.onResume();
			top.activityFinished(activity, activity.requestCode, code);
		}
		System.gc();
	}
	
	@Override
	public void onBackPressed() {
		if (top != null) {
			if (top.onBackPressed())
				return;
			if (top.previousActivity != null) {
				finishActivity(top, 0);
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
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			final ActionBar b = getActionBar();
			if (b != null)
				b.setDisplayHomeAsUpEnabled(true);
		}
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
		if (Player.handleMediaButton(keyCode))
			return true;
		return super.onKeyDown(keyCode, event);
	}
	
	public void setWindowColor(int color) {
		if (windowColorDrawable == null) {
			windowColor = color;
			windowColorDrawable = new ColorDrawable(color);
			getWindow().setBackgroundDrawable(windowColorDrawable);
		} else if (windowColor != color) {
			windowColor = color;
			windowColorDrawable.change(color);
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
		setupActionBar();
		UI.initialize(this);
		MainHandler.initialize(getApplication());
		top = new ActivityMain();
		top.finished = false;
		top.activity = this;
		top.previousActivity = null;
		top.onCreate();
		top.onCreateLayout(true);
		setWindowColor(UI.color_window);
		Player.addDestroyedObserver(this);
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
		super.onPause();
		if (top != null)
			top.onPause();
	}
	
	@Override
	protected void onResume() {
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
