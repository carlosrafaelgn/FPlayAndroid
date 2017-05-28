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
package br.com.carlosrafaelgn.fplay.ui;

import android.os.SystemClock;
import android.view.View;
import android.view.animation.Animation;

import br.com.carlosrafaelgn.fplay.activity.MainHandler;

public final class FastAnimator implements Animation.AnimationListener, Runnable {
	public interface Observer {
		//void onUpdate(FastAnimator animator, float value);
		void onEnd(FastAnimator animator);
	}

	private View viewToFade;//, referenceView;
	private Animation animation;
	private Observer observer;
	//private float invDuration;
	private boolean /*fadeOut,*/ running;
	private final boolean fadeOut;
	//private int version, ellapsedTime, duration, lastTime;
	//private Runnable runnable;

	public FastAnimator(View viewToFade, boolean fadeOut, Observer endObserver, int duration) {
		this.viewToFade = viewToFade;
		this.fadeOut = fadeOut;
		//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			this.animation = UI.animationCreateAlpha(fadeOut ? 1.0f : 0.0f, fadeOut ? 0.0f : 1.0f);
			if (endObserver != null)
				this.animation.setAnimationListener(this);
		//}
		this.observer = endObserver;
		//this.duration = (duration <= 0 ? UI.TRANSITION_DURATION_FOR_VIEWS : duration);
		//this.invDuration = 1.0f / (float)this.duration;
		//this.referenceView = viewToFade;
	}

	/*public FastAnimator(Observer observer, int duration, View referenceView) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			throw new RuntimeException("FastAnimator must be used with API 11+ for the observer to work properly");
		this.observer = observer;
		this.duration = (duration <= 0 ? UI.TRANSITION_DURATION_FOR_VIEWS : duration);
		this.invDuration = 1.0f / (float)this.duration;
		this.referenceView = referenceView;
	}*/

	public boolean isRunning() {
		return running;
	}

	public void release() {
		end();
		viewToFade = null;
		//referenceView = null;
		animation = null;
		observer = null;
	}

	public void start() {
		if (running)
			end();
		running = true;
		/*version++;
		ellapsedTime = 0;
		runnable = new Runnable() {
			private final int myVersion = version;
			@Override
			public void run() {
				if (myVersion != version)
					return;
				final int now = (int)SystemClock.uptimeMillis();
				lastTime = now - lastTime;
				//limit the delta so the user can actually see something changing on the screen
				ellapsedTime += ((lastTime >= 32) ? 32 : lastTime);
				lastTime = now;
				if (ellapsedTime >= duration) {
					end();
				} else {
					float value = (float)ellapsedTime * invDuration;
					//interpolate using the same algorithm as the one used in UI.animationInterpolator
					//(see UI class for more details)
					value = (value * value * (3.0f - (2.0f * value)));
					if (viewToFade != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
						viewToFade.setAlpha(fadeOut ? (1.0f - value) : value);
					else if (observer != null)
						observer.onUpdate(FastAnimator.this, value);
					if (running) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && referenceView != null)
							referenceView.postOnAnimation(this);
						else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
							MainHandler.postToMainThreadAtTime(this, lastTime + 16);
					}
				}
			}
		};*/
		if (viewToFade != null) {
			if (viewToFade.getVisibility() != View.VISIBLE) {
				if (fadeOut) {
					//don't even start the animation if there is nothing to animate...
					running = true;
					MainHandler.postToMainThreadAtTime(this, SystemClock.uptimeMillis() + 16);
					return;
				} else {
					viewToFade.setVisibility(View.VISIBLE);
				}
			}
			//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			//	viewToFade.setAlpha(fadeOut ? 1.0f : 0.0f);
			//} else {
				viewToFade.startAnimation(animation);
				//return;
			//}
		} /*else if (observer != null) {
			observer.onUpdate(this, 0.0f);
		}
		lastTime = (int)SystemClock.uptimeMillis();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && referenceView != null)
			referenceView.postOnAnimation(runnable);
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			MainHandler.postToMainThreadAtTime(runnable, lastTime + 16);*/
	}

	public void end() {
		if (!running)
			return;
		running = false;
		//version++;
		//runnable = null;
		if (viewToFade != null) {
			//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			//	viewToFade.setAlpha(fadeOut ? 0.0f : 1.0f);
			//	if (observer != null)
			//		observer.onEnd(this);
			//} else {
				viewToFade.setAnimation(null);
				animation.cancel();
			//}
		} //else if (observer != null) {
			//observer.onUpdate(this, 1.0f);
		//}
		if (observer != null)
			observer.onEnd(this);
	}

	/*public void prepareToRestart(View referenceView) {
		end();
		this.referenceView = referenceView;
	}*/

	@Override
	public void run() {
		end();
	}

	@Override
	public void onAnimationStart(Animation animation) {

	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if (!running)
			return;
		running = false;
		//version++;
		//runnable = null;
		if (viewToFade != null)
			viewToFade.setAnimation(null);
		//else if (observer != null)
			//observer.onUpdate(this, 1.0f);
		if (observer != null)
			observer.onEnd(this);
	}

	@Override
	public void onAnimationRepeat(Animation animation) {

	}
}
