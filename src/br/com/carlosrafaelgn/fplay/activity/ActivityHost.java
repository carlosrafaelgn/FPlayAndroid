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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import br.com.carlosrafaelgn.fplay.ActivityMain;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.BgFrameLayout;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.FastAnimator;
import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.ui.drawable.NullDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;
import br.com.carlosrafaelgn.fplay.util.Timer;

//
//Handling Runtime Changes
//http://developer.android.com/guide/topics/resources/runtime-changes.html
//
//<activity> (all attributes, including android:configChanges)
//http://developer.android.com/guide/topics/manifest/activity-element.html
//
public final class ActivityHost extends Activity implements Player.PlayerDestroyedObserver, Player.PlayerBackgroundMonitor, Animation.AnimationListener, FastAnimator.Observer, Interpolator, Timer.TimerHandler, Runnable {
	private ClientActivity top;
	private boolean isFading, useFadeOutNextTime, ignoreFadeNextTime, createLayoutCausedAnimation, exitOnDestroy, accelerate, isCreatingLayout, pendingOrientationChanges;
	private BgFrameLayout baseParent;
	private FrameLayout parent;
	private View oldView, newView, pendingTransitionView;
	private Animation anim;
	private FastAnimator animator; //used only with UI.TRANSITION_FADE
	private int systemBgColor, backgroundMonitorLastMsg;
	private Timer backgroundMonitorTimer;

	//http://developer.samsung.com/html/techdoc/ProgrammingGuide_MultiWindow.pdf
	//http://developer.samsung.com/galaxy/multiwindow
	//https://blogs.oracle.com/poonam/entry/how_to_implement_an_interface
	private static Method samsungWindowGetMultiPhoneWindowEvent;
	private static Method samsungMultiPhoneWindowEventSetStateChangeListener;
	private Object samsungStateChangeListener;
	private int samsungSMultiWindowWidth, samsungSMultiWindowHeight;
	private class SamsungStateChangeListener implements java.lang.reflect.InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			try {
				/*
				public interface StateChangeListener
				{
					void onModeChanged(boolean isMultiWindow);
					void onZoneChanged(int zoneInfo);
					void onSizeChanged(Rect rectInfo);
				}
				*/
				boolean changed = false;
				if (method.getName().equals("onModeChanged")) {
					if (!(boolean)args[0]) {
						samsungSMultiWindowWidth = 0;
						samsungSMultiWindowHeight = 0;
						changed = true;
					}
				} else if (method.getName().equals("onSizeChanged")) {
					final Rect rectInfo = (Rect)args[0];
					samsungSMultiWindowWidth = rectInfo.width();
					samsungSMultiWindowHeight = rectInfo.height();
					changed = true;
				}
				if (changed && top != null) {
					//samsung multi-window bug! on API's < N, with multi-window turned on, sometimes
					//onSizeChanged() is called WHILE executing setContentView() inside onCreateLayout()
					if (isCreatingLayout) {
						pendingOrientationChanges = true;
					} else {
						pendingOrientationChanges = false;

						ActivityMain.localeHasBeenChanged = false;

						UI.initialize(ActivityHost.this, samsungSMultiWindowWidth, samsungSMultiWindowHeight);

						ignoreFadeNextTime = true;
						top.onOrientationChanged();
						ignoreFadeNextTime = false;
						System.gc();
					}
				}
			} catch (Throwable ex) {
				//just ignore
			}
			return null;
		}
	}

	private void preprareSamsungMultiWindowListener() {
		samsungSMultiWindowWidth = 0;
		samsungSMultiWindowHeight = 0;
		samsungStateChangeListener = null;
		try {
			final Object multiPhoneWindowEvent = samsungWindowGetMultiPhoneWindowEvent.invoke(getWindow());
			if (multiPhoneWindowEvent == null) {
				samsungWindowGetMultiPhoneWindowEvent = null;
				samsungMultiPhoneWindowEventSetStateChangeListener = null;
				return;
			}
			if (samsungMultiPhoneWindowEventSetStateChangeListener == null) {
				for (Method method : multiPhoneWindowEvent.getClass().getMethods()) {
					if (method.getName().equals("setStateChangeListener")) {
						samsungMultiPhoneWindowEventSetStateChangeListener = method;
						break;
					}
				}
			}
			if (samsungMultiPhoneWindowEventSetStateChangeListener == null) {
				samsungWindowGetMultiPhoneWindowEvent = null;
				samsungMultiPhoneWindowEventSetStateChangeListener = null;
				return;
			}
			final Class<?> stateChangeListenerClass = samsungMultiPhoneWindowEventSetStateChangeListener.getParameterTypes()[0];
			samsungStateChangeListener = Proxy.newProxyInstance(stateChangeListenerClass.getClassLoader(), new Class<?>[]{stateChangeListenerClass}, new SamsungStateChangeListener());
			samsungMultiPhoneWindowEventSetStateChangeListener.invoke(multiPhoneWindowEvent, samsungStateChangeListener);
		} catch (Throwable ex) {
			//just ignore
		}
	}

	private void cleanupSamsungMultiWindowListener() {
		try {
			final Object multiPhoneWindowEvent = samsungWindowGetMultiPhoneWindowEvent.invoke(getWindow());
			if (multiPhoneWindowEvent != null && samsungMultiPhoneWindowEventSetStateChangeListener != null)
				samsungMultiPhoneWindowEventSetStateChangeListener.invoke(multiPhoneWindowEvent, new Object[] { null });
		} catch (Throwable ex) {
			//just ignore
		}
		samsungStateChangeListener = null;
	}

	static {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			try {
				samsungWindowGetMultiPhoneWindowEvent = Window.class.getMethod("getMultiPhoneWindowEvent");
			} catch (Throwable ex) {
				samsungWindowGetMultiPhoneWindowEvent = null;
			}
		}
	}

	private void disableTopView() {
		final FrameLayout parent = baseParent;
		final View view;
		if (parent != null && (view = parent.getChildAt(0)) != null)
			setViewEnabled(view, false);
	}

	private void setViewEnabled(View view, boolean enabled) {
		view.setEnabled(enabled);
		if (view instanceof ViewGroup)
			setGroupEnabled((ViewGroup)view, enabled);
	}

	private void setGroupEnabled(ViewGroup viewGroup, boolean enabled) {
		for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
			final View view;
			if ((view = viewGroup.getChildAt(i)) != null) {
				view.setEnabled(enabled);
				if (view instanceof ViewGroup)
					setGroupEnabled((ViewGroup)view, enabled);
			}
		}
	}

	private void createLayout(ClientActivity activity, boolean firstCreation) {
		if (activity != null && !activity.finished) {
			createLayoutCausedAnimation = false;
			activity.postCreateCalled = (firstCreation ? 2 : 0);
			isCreatingLayout = true;
			activity.onCreateLayout(firstCreation);
			if (!activity.finished) {
				activity.layoutCreated = true;
				if (!createLayoutCausedAnimation && (activity.postCreateCalled & 1) == 0) {
					activity.postCreateCalled = 1;
					activity.onPostCreateLayout(firstCreation);
				}
			}
			isCreatingLayout = false;
			//samsung multi-window bug! on API's < N, with multi-window turned on, sometimes
			//onSizeChanged() is called WHILE executing setContentView() inside onCreateLayout()
			if (pendingOrientationChanges) {
				pendingOrientationChanges = false;
				if (!activity.finished) {
					ignoreFadeNextTime = true;
					activity.onOrientationChanged();
					ignoreFadeNextTime = false;
					System.gc();
				}
			}
		}
	}

	void updateTitle(CharSequence title, boolean announce) {
		setTitle(title);
		//announce should be false when starting/finishing an activity from a menu
		//because in those cases AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED will
		//already happen due to a change in the active window, making the user hear
		//the title twice!
		if (announce)
			UI.announceAccessibilityText(title);
	}

	public void setExitOnDestroy() {
		this.exitOnDestroy = true;
	}
	
	@Override
	public void onAnimationEnd(Animation animation) {
		boolean setNull = false;
		if (animator != null) {
			animator.release();
			animator = null;
		} else if (anim != null) {
			setNull = true;
			anim.setAnimationListener(null);
			anim = null;
		}
		if (isFading) {
			isFading = false;
			if (parent != null) {
				if (oldView != null) {
					parent.removeView(oldView);
					oldView = null;
				}
				if (newView != null) {
					if (setNull)
						newView.setAnimation(null);
					setViewEnabled(newView, true);
					newView = null;
				}
				parent = null;
			}
		}
		if (top != null && !top.finished && (top.postCreateCalled & 1) == 0) {
			top.postCreateCalled |= 1;
			top.onPostCreateLayout((top.postCreateCalled & 2) != 0);
		}
	}
	
	@Override
	public void onAnimationRepeat(Animation animation) {
	}
	
	@Override
	public void onAnimationStart(Animation animation) {
	}

	@Override
	public void onEnd(FastAnimator animator) {
		onAnimationEnd(null);
	}

	@Override
	public float getInterpolation(float input) {
		//making ActivityHost implement Interpolator saves us one class and one instance ;)
		if (accelerate)
			return input * input;
		input = 1.0f - input;
		return (1.0f - (input * input));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends View> T findViewById(int id) {
		return ((newView != null) ? (T)newView.findViewById(id) : (T)super.findViewById(id));
	}

	@Override
	public void setContentView(View view) {
		baseParent.removeAllViews();
		baseParent.addView(view);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			updateSystemColors(false);
	}

	@Override
	public void setContentView(View view, ViewGroup.LayoutParams params) {
		baseParent.removeAllViews();
		baseParent.addView(view, params);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			updateSystemColors(false);
	}

	@Override
	public void setContentView(int layoutResID) {
		baseParent.removeAllViews();
		((LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(layoutResID, baseParent, true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			updateSystemColors(false);
	}

	void setContentViewWithTransition(View view, boolean fadeAllowed, boolean forceFadeOut) {
		final int transition = UI.transitions & 0xFF;
		parent = ((fadeAllowed && !ignoreFadeNextTime && transition != UI.TRANSITION_NONE) ? baseParent : null);
		if (parent != null) {
			anim = null;
			animator = null;
			try {
				oldView = parent.getChildAt(0);
				if (oldView != null) {
					newView = view;
					if (transition == UI.TRANSITION_ZOOM_FADE) {
						final AnimationSet animationSet = new AnimationSet(false);
						anim = animationSet;
						Animation tmp;
						accelerate = false;
						if (useFadeOutNextTime || forceFadeOut) {
							tmp = new ScaleAnimation(1.0f, 1.1f, 1.0f, 1.1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
							tmp.setInterpolator(this);
							animationSet.addAnimation(tmp);
							tmp = new AlphaAnimation(1.0f, 0.0f);
							tmp.setInterpolator(Player.theUI);
							animationSet.addAnimation(tmp);
						} else {
							tmp = new ScaleAnimation(1.1f, 1.0f, 1.1f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
							tmp.setInterpolator(this);
							animationSet.addAnimation(tmp);
							tmp = new AlphaAnimation(0.0f, 1.0f);
							tmp.setInterpolator(Player.theUI);
							animationSet.addAnimation(tmp);
						}
						anim.setDuration(UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW);
						anim.setRepeatCount(0);
						anim.setFillAfter(false);
					} else if (transition == UI.TRANSITION_SLIDE_SMOOTH) {
						final AnimationSet animationSet = new AnimationSet(false);
						anim = animationSet;
						Animation tmp;
						accelerate = (useFadeOutNextTime || forceFadeOut);
						if (accelerate) {
							tmp = new TranslateAnimation(Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, UI.defaultControlSize << 1);
							tmp.setInterpolator(this);
							animationSet.addAnimation(tmp);
							tmp = new AlphaAnimation(1.0f, 0.0f);
							tmp.setInterpolator(Player.theUI);
							animationSet.addAnimation(tmp);
						} else {
							tmp = new TranslateAnimation(Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, UI.defaultControlSize << 1, Animation.ABSOLUTE, 0.0f);
							tmp.setInterpolator(this);
							animationSet.addAnimation(tmp);
							tmp = new AlphaAnimation(0.0f, 1.0f);
							tmp.setInterpolator(Player.theUI);
							animationSet.addAnimation(tmp);
						}
						anim.setDuration(UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW);
						anim.setRepeatCount(0);
						anim.setFillAfter(false);
					} else if (transition == UI.TRANSITION_SLIDE) {
						anim = ((useFadeOutNextTime || forceFadeOut) ?
							new TranslateAnimation(0.0f, 0.0f, 0.0f, (float)oldView.getHeight()) :
							new TranslateAnimation(0.0f, 0.0f, (float)oldView.getHeight(), 0.0f));
						accelerate = (useFadeOutNextTime || forceFadeOut);
						anim.setDuration(UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW);
						anim.setInterpolator(this);
						anim.setRepeatCount(0);
						anim.setFillAfter(false);
					} else if (transition == UI.TRANSITION_FADE) {
						animator = ((useFadeOutNextTime || forceFadeOut) ?
							new FastAnimator(oldView, true, this, UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW) :
							new FastAnimator(view, false, this, UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW));
					} else {
						final AnimationSet animationSet = new AnimationSet(true);
						anim = animationSet;
						int x, y;
						if (transition == UI.TRANSITION_DISSOLVE) {
							x = oldView.getWidth() >> 1;
							y = 0;
						} else {
							x = UI.lastViewCenterLocation[0];
							y = UI.lastViewCenterLocation[1];
						}
						try {
							parent.getLocationOnScreen(UI.lastViewCenterLocation);
							x -= UI.lastViewCenterLocation[0];
							y -= UI.lastViewCenterLocation[1];
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
						//leave prepared for next time
						UI.storeViewCenterLocationForFade(null);
						if (useFadeOutNextTime || forceFadeOut) {
							if (transition == UI.TRANSITION_DISSOLVE) {
								animationSet.addAnimation(new ScaleAnimation(1.0f, 0.75f, 1.0f, 1.0f, x, y));
								animationSet.addAnimation(new TranslateAnimation(0.0f, 0.0f, 0.0f, (float)(oldView.getHeight() >> 3)));
							} else {
								animationSet.addAnimation(new ScaleAnimation(1.0f, 0.3f, 1.0f, 0.3f, x, y));
							}
							animationSet.addAnimation(new AlphaAnimation(1.0f, 0.0f));
						} else {
							if (transition == UI.TRANSITION_DISSOLVE) {
								animationSet.addAnimation(new ScaleAnimation(0.75f, 1.0f, 1.0f, 1.0f, x, y));
								animationSet.addAnimation(new TranslateAnimation(0.0f, 0.0f, (float)-(oldView.getHeight() >> 3), 0.0f));
							} else {
								animationSet.addAnimation(new ScaleAnimation(0.3f, 1.0f, 0.3f, 1.0f, x, y));
							}
							animationSet.addAnimation(new AlphaAnimation(0.0f, 1.0f));
						}
						anim.setDuration(UI.TRANSITION_DURATION_FOR_ACTIVITIES_SLOW);
						anim.setInterpolator(Player.theUI);
						anim.setRepeatCount(0);
						anim.setFillAfter(false);
					}
				} else {
					parent = null;
				}
			} catch (Throwable ex) {
				anim = null;
				animator = null;
				parent = null;
			}
			//we need to delay the start of the animation in order to allow it to run smooth
			//from the very start, because we might have alread done a lot of work in this frame,
			//creating and adding the new views, and cleaning up the previous activity
			if (animator != null || anim != null) {
				if (anim != null)
					anim.setAnimationListener(this);
				parent.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
				setViewEnabled(view, false);
				if (useFadeOutNextTime || forceFadeOut) {
					oldView.bringToFront();
					pendingTransitionView = oldView;
				} else {
					view.bringToFront();
					view.setVisibility(View.INVISIBLE);
					pendingTransitionView = view;
				}
				isFading = true;
				createLayoutCausedAnimation = true;
				MainHandler.postToMainThreadAtTime(this, SystemClock.uptimeMillis() + 10);
			}
		}
		if (parent == null) {
			oldView = null;
			newView = null;
			setContentView(view);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			updateSystemColors(false);
		}
		useFadeOutNextTime = false;
	}

	@Override
	public void run() {
		if (pendingTransitionView == null)
			return;
		pendingTransitionView.setVisibility(View.VISIBLE);
		if (animator != null)
			animator.start();
		else if (anim != null)
			pendingTransitionView.startAnimation(anim);
		pendingTransitionView = null;
	}

	private void startActivityInternal(ClientActivity activity, boolean announce) {
		activity.finished = false;
		activity.activity = this;
		activity.previousActivity = top;
		top = activity;
		updateTitle(activity.getTitle(), announce);
		activity.paused = true;
		activity.onCreate();
		if (!activity.finished) {
			createLayout(activity, true);
			if (!activity.finished && activity.paused) {
				activity.paused = false;
				activity.onResume();
			}
		}
	}
	
	public void startActivity(ClientActivity activity, boolean announce) {
		if (top != null) {
			if (!top.paused) {
				top.paused = true;
				top.onPause();
			}
			if (top != null) {
				disableTopView();
				top.layoutCreated = false;
				top.onCleanupLayout();
			}
		}
		startActivityInternal(activity, announce);
	}
	
	public void finishActivity(ClientActivity activity, ClientActivity newActivity, int code, boolean announce) {
		if (activity.finished || activity != top)
			return;
		useFadeOutNextTime = true;
		activity.finished = true;
		if (!activity.paused) {
			activity.paused = true;
			activity.onPause();
		}
		disableTopView();
		activity.layoutCreated = false;
		activity.onCleanupLayout();
		activity.onDestroy();
		top = ((top != null) ? top.previousActivity : null);
		activity.activity = null;
		activity.previousActivity = null;
		final ClientActivity oldTop = top;
		if (oldTop != null) {
			oldTop.activity = this;
			updateTitle(oldTop.getTitle(), announce);
			if (!oldTop.finished)
				oldTop.activityFinished(activity, activity.requestCode, code);
		} else {
			//prevent the title from being said by TalkBack when the
			//application finishes in response to a menu item
			setTitle("\u00A0");
		}
		if (newActivity != null) {
			useFadeOutNextTime = false;
			startActivityInternal(newActivity, announce);
		} else {
			if (top == null) {
				finish();
			} else if (top == oldTop) {
				//we must check because the top activity could have
				//changed as a consequence of oldTop.activityFinished()
				//being called
				createLayout(top, false);
				if (top != null && !top.finished && top.paused) {
					top.paused = false;
					top.onResume();
				}
			}
		}
		System.gc();
	}

	@Override
	public void onBackPressed() {
		if (isFading)
			return;
		if (top != null) {
			if (top.onBackPressed())
				return;
			if (top != null && top.previousActivity != null) {
				finishActivity(top, null, 0, true);
				return;
			}
		}
		super.onBackPressed();
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (isFading)
			return false;
		if (top != null) {
			final View v = top.getNullContextMenuView();
			if (v != null)
				CustomContextMenu.openContextMenu(v, top);
		}
		return false;
	}
	
	//replace onKeyDown with dispatchKeyEvent + event.getAction() + event.getKeyCode()?!?!?!
	@Override
	public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
		//
		//Allowing applications to play nice(r) with each other: Handling remote control buttons
		//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
		//
		//...In a media playback application, this is used to react to headset button
		//presses when your activity doesn’t have the focus. For when it does, we override
		//the Activity.onKeyDown() or onKeyUp() methods for the user interface to trap the
		//headset button-related events...
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) {
				Player.handleMediaButton(keyCode);
				return true;
			}
			break;
		default:
			if ((event.getRepeatCount() == 0) && Player.handleMediaButton(keyCode))
				return true;
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		return (Player.isMediaButton(keyCode) || super.onKeyLongPress(keyCode, event));
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		return (Player.isMediaButton(keyCode) || super.onKeyUp(keyCode, event));
	}

	private void checkIntent(Intent intent) {
		final Uri data;
		if (Player.state >= Player.STATE_TERMINATING || intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || (data = intent.getData()) == null)
			return;
		if (Player.state == Player.STATE_ALIVE)
			Player.songs.addPathAndForceScrollIntoView(data.getPath(), true);
		else
			Player.pathToPlayWhenStarting = data.getPath();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		checkIntent(intent);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void updateSystemColors(boolean force) {
		int bgColor = ((top == null) ? UI.color_window : top.getSystemBgColor());
		if (!force && systemBgColor == bgColor)
			return;
		systemBgColor = bgColor;
		int turns = 0;
		do {
			bgColor = ColorUtils.blend(bgColor, 0xff000000, 0.8f);
			turns++;
		} while (ColorUtils.contrastRatio(bgColor, 0xffffffff) < 5 && turns < 5);
		getWindow().setNavigationBarColor(bgColor);
		getWindow().setStatusBarColor(bgColor);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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
		if (Player.state >= Player.STATE_TERMINATING) {
			finish();
			return;
		}
		Player.theApplication = getApplicationContext();
		if (samsungWindowGetMultiPhoneWindowEvent != null)
			preprareSamsungMultiWindowListener();
		UI.initialize(this, samsungSMultiWindowWidth, samsungSMultiWindowHeight);
		Player.startService();
		UI.setAndroidThemeAccordingly(this);
		UI.storeViewCenterLocationForFade(null);
		super.setContentView(baseParent = new BgFrameLayout(this));
		pendingOrientationChanges = false;
		top = new ActivityMain();
		top.finished = false;
		top.activity = this;
		top.previousActivity = null;
		//prevent the title from being said by TalkBack while the
		//application is still loading... (ActivityMain will change its
		//title when the player finishes loading)
		setTitle(Player.state == Player.STATE_ALIVE ? top.getTitle() : "\u00A0");
		getWindow().setBackgroundDrawable(new NullDrawable());
		top.paused = true;
		top.onCreate();
		if (top != null && !top.finished) {
			createLayout(top, true);
			if (top != null && !top.finished) {
				Player.addDestroyedObserver(this);
				checkIntent(getIntent());
			} else {
				finish();
			}
		} else {
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.clear(); //see the comments in the method above
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		final int usableScreenWidth = UI.usableScreenWidth, usableScreenHeight = UI.usableScreenHeight;
		ActivityMain.localeHasBeenChanged = false;

		int w = 0, h = 0;
		if (samsungSMultiWindowWidth > 0 && samsungSMultiWindowHeight > 0) {
			w = samsungSMultiWindowWidth;
			h = samsungSMultiWindowHeight;
		} else if (newConfig != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			w = UI.dpToPxI(newConfig.screenWidthDp);
			h = UI.dpToPxI(newConfig.screenHeightDp);
		}

		UI.initialize(this, w, h);

		if ((usableScreenWidth != UI.usableScreenWidth || usableScreenHeight != UI.usableScreenHeight) && top != null && !top.finished) {
			ignoreFadeNextTime = true;
			top.onOrientationChanged();
			ignoreFadeNextTime = false;
			System.gc();
		}
	}
	
	@Override
	protected void onStop() {
		//changed from onPaused() to onStop()
		//https://developer.android.com/guide/topics/ui/multi-window.html#lifecycle
		//In multi-window mode, an app can be in the paused state and still be visible to the user.
		if (pendingTransitionView != null)
			run();
		if (animator != null)
			animator.end();
		else if (anim != null)
			anim.cancel();
		backgroundMonitorStop();
		if (top != null && !top.paused) {
			top.paused = true;
			top.onPause();
		}
		Player.backgroundMonitor = null;
		Player.setAppNotInForeground(true);
		super.onStop();
	}

	@Override
	protected void onResume() {
		if (Player.state >= Player.STATE_TERMINATING) {
			finish();
			return;
		}
		Player.backgroundMonitor = this;
		Player.setAppNotInForeground(false);
		if (UI.forcedLocale != UI.LOCALE_NONE)
			UI.reapplyForcedLocale(this);
		if (top != null && top.paused) {
			top.paused = false;
			top.onResume();
		}
		backgroundMonitorStart();
		super.onResume();
	}
	
	private void finalCleanup() {
		ClientActivity c = top, p;
		if (samsungStateChangeListener != null)
			cleanupSamsungMultiWindowListener();
		top = null;
		parent = null;
		oldView = null;
		newView = null;
		if (anim != null) {
			anim.setAnimationListener(null);
			anim = null;
		}
		if (animator != null) {
			animator.release();
			animator = null;
		}
		while (c != null) {
			if (!c.finished) {
				c.finished = true;
				if (!c.paused) {
					c.paused = true;
					c.onPause();
				}
				c.layoutCreated = false;
				c.onCleanupLayout();
				c.onDestroy();
			}
			p = c.previousActivity;
			c.activity = null;
			c.previousActivity = null;
			c = p;
		}
		baseParent = null;
		backgroundMonitorStop();
		backgroundMonitorTimer = null;
	}
	
	@Override
	protected void onDestroy() {
		Player.removeDestroyedObserver(this);
		finalCleanup();
		super.onDestroy();
		if (exitOnDestroy || UI.isChromebook)
			Player.stopService();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (top != null)
			top.onActivityResult(requestCode, resultCode, data);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public boolean isReadStoragePermissionGranted() {
		return (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public void requestReadStoragePermission() {
		requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public boolean isWriteStoragePermissionGranted() {
		return (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public void requestWriteStoragePermission() {
		requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		/*if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && Player.state == Player.STATE_ALIVE) {
			exitOnDestroy = 2;
			finish();
			return;
		}*/
		if (top != null)
			top.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	public void onPlayerDestroyed() {
		finalCleanup();
		finish();
	}

	private static int backgroundMonitorCurrentMessage() {
		return ((Player.state != Player.STATE_ALIVE) ? R.string.loading :
			(Player.songs.isAdding() ? R.string.adding_songs :
				((Player.httpTransmitterLastErrorMessage != null) ? R.string.transmission_error :
					((Player.httpTransmitter != null) ? R.string.transmission_active :
						((Player.bluetoothVisualizerLastErrorMessage != null) ? R.string.bt_error :
							((Player.bluetoothVisualizer != null) ? R.string.bt_active :
								0))))));
	}

	@SuppressWarnings("deprecation")
	@Override
	public void backgroundMonitorStart() {
		final int msg = backgroundMonitorCurrentMessage();
		if (msg != 0) {
			backgroundMonitorStop();
			if (baseParent != null) {
				backgroundMonitorLastMsg = msg;
				baseParent.setMessage(backgroundMonitorLastMsg);
				if (backgroundMonitorTimer == null)
					backgroundMonitorTimer = new Timer(this, "Background Activity Monitor Timer", false, true, false);
				backgroundMonitorTimer.start(250);
			}
		}
	}

	private void backgroundMonitorBackgroundPluginEnded(boolean withError, int errorMsg) {
		if (baseParent == null)
			return;
		if (Player.state == Player.STATE_ALIVE && !Player.songs.isAdding()) {
			if (withError) {
				if (backgroundMonitorLastMsg != errorMsg) {
					backgroundMonitorLastMsg = errorMsg;
					baseParent.setMessage(backgroundMonitorLastMsg);
				}
			} else {
				backgroundMonitorStop();
			}
		}
	}

	@Override
	public void backgroundMonitorBluetoothEnded() {
		backgroundMonitorBackgroundPluginEnded(Player.bluetoothVisualizerLastErrorMessage != null, R.string.bt_error);
	}

	@Override
	public void backgroundMonitorHttpEnded() {
		backgroundMonitorBackgroundPluginEnded(Player.httpTransmitterLastErrorMessage != null, R.string.transmission_error);
	}

	private void backgroundMonitorStop() {
		backgroundMonitorLastMsg = 0;
		if (baseParent != null)
			baseParent.setMessage(0);
		if (backgroundMonitorTimer != null)
			backgroundMonitorTimer.stop();
	}

	@Override
	public void handleTimer(Timer timer, Object param) {
		final int msg = backgroundMonitorCurrentMessage();
		if (msg == 0) {
			backgroundMonitorStop();
		} else {
			if (backgroundMonitorLastMsg != msg) {
				backgroundMonitorLastMsg = msg;
				if (baseParent != null)
					baseParent.setMessage(msg);
			}
			if (Player.state == Player.STATE_ALIVE && !Player.songs.isAdding() && backgroundMonitorTimer != null)
				backgroundMonitorTimer.stop();
		}
	}
}
