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

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;

import br.com.carlosrafaelgn.fplay.ui.UI;

/**
 * This class performs the graphical effect used at the edges of scrollable widgets
 * when the user scrolls beyond the content bounds in 2D space.
 *
 * <p>EdgeEffect is stateful. Custom widgets using EdgeEffect should create an
 * instance for each edge that should show the effect, feed it input data using
 * the methods {@link #onAbsorb(int)}, {@link #onPull(float)}, and {@link #onRelease()},
 * and draw the effect using {@link #draw(Canvas)} in the widget's overridden
 * {@link android.view.View#draw(Canvas)} method. If {@link #isFinished()} returns
 * false after drawing, the edge effect's animation is not yet complete and the widget
 * should schedule another drawing pass to continue the animation.</p>
 *
 * <p>When drawing, widgets should draw their main content and child views first,
 * usually by invoking <code>super.draw(canvas)</code> from an overridden <code>draw</code>
 * method. (This will invoke onDraw and dispatch drawing to child views as needed.)
 * The edge effect may then be drawn on top of the view's content using the
 * {@link #draw(Canvas)} method.</p>
 */
@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class BgEdgeEffect extends EdgeEffect {
	private static final int STATE_IDLE = 0;
	private static final int STATE_PULL = 1;
	private static final int STATE_ABSORB = 2;
	private static final int STATE_RECEDE = 3;
	private static final int STATE_PULL_DECAY = 4;

	private static final float VELOCITY_GLOW_FACTOR = 6.0f;

	// Time it will take the effect to fully recede in ms
	private static final float RECEDE_TIME = 600.0f;

	// Time it will take before a pulled glow begins receding in ms
	private static final float PULL_TIME = 167.0f;

	// Time it will take in ms for a pulled glow to decay to partial strength before release
	private static final float PULL_DECAY_TIME = 2000.0f;

	private static final float MIN_ALPHA = 0.25f;
	private static final float MAX_ALPHA = 0.65f;

	// Minimum velocity that will be absorbed
	private static final int MIN_VELOCITY = 100;
	// Maximum velocity, clamps at this value
	private static final int MAX_VELOCITY = 10000;

	private float mAlpha, mAlphaStart, mAlphaFinish;
	private float mScaleY, mScaleYStart, mScaleYFinish;

	private int mState, mStartTime, mPullLastTime;
	private float mDuration, mPullDistance, mTargetPullScaleY;

	public int mOffsetY;
	private int mX, mY, mWidth;
	private final int mColor, mMaxHeight;

	/**
	 * Construct a new EdgeEffect with a theme appropriate for the provided context.
	 * @param context Context used to provide theming and resource information for the EdgeEffect
	 */
	public BgEdgeEffect(Context context, int primaryColor) {
		super(context);
		mColor = primaryColor & 0x00ffffff;
		//a little bit below SongView's height
		mMaxHeight = (UI._1dp << 1) + UI.verticalMargin + UI._22spBox + UI._14spBox; // ((3 * UI.screenHeight) >> 5);
	}

	/**
	 * Set the size of this edge effect in pixels.
	 *
	 * @param width Effect width in pixels
	 * @param height Effect height in pixels
	 */
	public void setSize(int width, int height) {
		mWidth = width;
	}

	/**
	 * Set the position of this edge effect in pixels. This position is
	 * only used by {@link #getBounds(boolean)}.
	 *
	 * @param x The position of the edge effect on the X axis
	 * @param y The position of the edge effect on the Y axis
	 */
	public void setPosition(int x, int y) {
		mX = x;
		mY = y;
	}

	/**
	 * Returns the bounds of the edge effect.
	 */
	public Rect getBounds(boolean reverse) {
		final Rect rect = UI.rect;
		final int dy = mY - (reverse ? (mMaxHeight + mOffsetY) : 0);
		rect.left = mX;
		rect.top = dy;
		rect.right = mX + mWidth;
		rect.bottom = dy + mMaxHeight + mOffsetY;
		return rect;
	}

	/**
	 * Reports if this EdgeEffect's animation is finished. If this method returns false
	 * after a call to {@link #draw(Canvas)} the host widget should schedule another
	 * drawing pass to continue the animation.
	 *
	 * @return true if animation is finished, false if drawing should continue on the next frame.
	 */
	public boolean isFinished() {
		return mState == STATE_IDLE;
	}

	/**
	 * Immediately finish the current animation.
	 * After this call {@link #isFinished()} will return true.
	 */
	public void finish() {
		mState = STATE_IDLE;
	}

	/**
	 * A view should call this when content is pulled away from an edge by the user.
	 * This will update the state of the current visual effect and its associated animation.
	 * The host view should always {@link android.view.View#invalidate()} after this
	 * and draw the results accordingly.
	 *
	 * <p>Views using EdgeEffect should favor {@link #onPull(float, float)} when the displacement
	 * of the pull point is known.</p>
	 *
	 * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
	 *                      1.f (full length of the view) or negative values to express change
	 *                      back toward the edge reached to initiate the effect.
	 */
	public void onPull(float deltaDistance) {
		onPull(deltaDistance, 0.5f);
	}

	/**
	 * A view should call this when content is pulled away from an edge by the user.
	 * This will update the state of the current visual effect and its associated animation.
	 * The host view should always {@link android.view.View#invalidate()} after this
	 * and draw the results accordingly.
	 *
	 * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
	 *                      1.f (full length of the view) or negative values to express change
	 *                      back toward the edge reached to initiate the effect.
	 * @param displacement The displacement from the starting side of the effect of the point
	 *                     initiating the pull. In the case of touch this is the finger position.
	 *                     Values may be from 0-1.
	 */
	public void onPull(float deltaDistance, float displacement) {
		final int now = (int)SystemClock.uptimeMillis();

		if (mState == STATE_PULL_DECAY && (now - mStartTime) < (int)mDuration)
			return;

		if (mState != STATE_PULL) {
			mState = STATE_PULL;
			mPullLastTime = now;
		}
		mStartTime = now;
		mDuration = PULL_TIME;

		if (deltaDistance < 0.0f)
			mPullDistance -= deltaDistance;
		else
			mPullDistance += deltaDistance;

		mTargetPullScaleY = Math.max(0.0f, Math.min(mPullDistance * 5.0f, 1.0f)); //mScaleY = (d * d * (3.0f - (2.0f * d)));
		//mAlpha = mScaleY * MAX_ALPHA;
		//mAlphaStart = mAlpha;
		//mAlphaFinish = mAlpha;
		//mScaleYStart = mScaleY;
		//mScaleYFinish = mScaleY;
	}

	/**
	 * Call when the object is released after being pulled.
	 * This will begin the "decay" phase of the effect. After calling this method
	 * the host view should {@link android.view.View#invalidate()} and thereby
	 * draw the results accordingly.
	 */
	public void onRelease() {
		mPullDistance = 0.0f;

		if (mState != STATE_PULL && mState != STATE_PULL_DECAY)
			return;

		mAlphaStart = mAlpha;
		mAlphaFinish = 0.0f;
		mScaleYStart = mScaleY;
		mScaleYFinish = 0.0f;

		mState = STATE_RECEDE;
		mStartTime = (int)SystemClock.uptimeMillis();
		mDuration = RECEDE_TIME;
	}

	/**
	 * Call when the effect absorbs an impact at the given velocity.
	 * Used when a fling reaches the scroll boundary.
	 *
	 * <p>When using a {@link android.widget.Scroller} or {@link android.widget.OverScroller},
	 * the method <code>getCurrVelocity</code> will provide a reasonable approximation
	 * to use here.</p>
	 *
	 * @param velocity Velocity at impact in pixels per second.
	 */
	public void onAbsorb(int velocity) {
		if (velocity < 0)
			velocity = -velocity;
		final float fvel = (float)Math.max(MIN_VELOCITY, Math.min(velocity, MAX_VELOCITY));

		mPullDistance = 0.0f;

		mState = STATE_ABSORB;
		mStartTime = (int)SystemClock.uptimeMillis();
		mDuration = 0.15f + (fvel * 0.02f);

		// The glow depends more on the velocity, and therefore starts out
		// nearly invisible.
		mAlphaStart = MIN_ALPHA;
		// Alpha should change for the glow as well as size.
		mAlphaFinish = fvel * VELOCITY_GLOW_FACTOR * 0.00001f;
		if (mAlphaFinish > MAX_ALPHA)
			mAlphaFinish = MAX_ALPHA;
		else if (mAlphaFinish < mAlphaStart)
			mAlphaFinish = mAlphaStart;

		mScaleYStart = 0.0f;
		// Growth for the size of the glow should be quadratic to properly
		// respond
		// to a user's scrolling speed. The faster the scrolling speed, the more
		// intense the effect should be for both the size and the saturation.
		mScaleYFinish = 0.025f + (fvel * fvel * 0.00000075f);
		if (mScaleYFinish > 1.0f)
			mScaleYFinish = 1.0f;
	}

	/**
	 * Set the color of this edge effect in argb.
	 *
	 * @param color Color in argb
	 */
	public void setColor(int color) {
	}

	/**
	 * Return the color of this edge effect in argb.
	 * @return The color of this edge effect in argb
	 */
	public int getColor() {
		return mColor;
	}

	/**
	 * Draw into the provided canvas. Assumes that the canvas has been rotated
	 * accordingly and the size has been set. The effect will be drawn the full
	 * width of X=0 to X=width, beginning from Y=0 and extending to some factor <
	 * 1.f of height.
	 *
	 * @param canvas Canvas to draw into
	 * @return true if drawing should continue beyond this frame to continue the
	 *         animation
	 */
	public boolean draw(Canvas canvas) {
		if (mState == STATE_IDLE)
			return false;

		final int now = (int)SystemClock.uptimeMillis();
		float t = (float)(now - mStartTime) / mDuration;
		if (t > 1.0f)
			t = 1.0f;

		if (mState == STATE_PULL) {
			final float coefNew = (float)(now - mPullLastTime) * (0.140625f / 16.0f); //0.140625f @ 60fps (~16ms)
			mScaleY = (mTargetPullScaleY * coefNew) + (mScaleY * (1.0f - coefNew));
			mPullLastTime = now;
			if (mScaleY > mTargetPullScaleY)
				mScaleY = mTargetPullScaleY;
			mAlpha = (mScaleY * (MAX_ALPHA - MIN_ALPHA)) + MIN_ALPHA;
		} else {
			float interp = 1.0f - t;
			interp = (1.0f - (interp * interp));
			mAlpha = mAlphaStart + ((mAlphaFinish - mAlphaStart) * interp);
			mScaleY = mScaleYStart + ((mScaleYFinish - mScaleYStart) * interp);
			if (mAlpha >= MAX_ALPHA)
				mAlpha = MAX_ALPHA;
			if (mScaleY >= 1.0f)
				mScaleY = 1.0f;
		}

		if (t >= 1.0f) {
			switch (mState) {
			case STATE_ABSORB:
				mState = STATE_RECEDE;
				mStartTime = now;
				mDuration = RECEDE_TIME;

				mAlphaStart = mAlpha;
				mScaleYStart = mScaleY;

				// After absorb, the glow should fade to nothing.
				mAlphaFinish = 0.0f;
				mScaleYFinish = 0.0f;
				break;
			case STATE_PULL:
				mState = STATE_PULL_DECAY;
				mStartTime = now;
				mDuration = PULL_DECAY_TIME;

				mAlphaStart = mAlpha;
				mScaleYStart = mScaleY;

				// After pull, the glow should fade to nothing.
				mAlphaFinish = 0.0f;
				mScaleYFinish = 0.0f;
				break;
			case STATE_PULL_DECAY:
				mState = STATE_RECEDE;
				break;
			case STATE_RECEDE:
				mState = STATE_IDLE;
				break;
			}
		}

		final Rect rect = UI.rect;
		final RectF rectF = UI.rectF;
		canvas.save();
		UI.fillPaint.setAntiAlias(true);
		UI.fillPaint.setColor(((int)(255.0f * Math.min(mAlpha, 1.0f)) << 24) | mColor);
		rect.left = mX;
		rect.top = mOffsetY;
		rect.right = mX + mWidth;
		rect.bottom = mMaxHeight + mOffsetY;
		canvas.clipRect(rect);
		rectF.left = (float)(mX - UI._18sp);
		rectF.right = (float)(mX + mWidth + UI._18sp);
		rectF.bottom = (mScaleY * (float)(mMaxHeight + mOffsetY));
		rectF.top = -rectF.bottom + (float)mOffsetY;
		canvas.drawOval(rectF, UI.fillPaint);
		UI.fillPaint.setAntiAlias(false);
		canvas.restore();

		return true;
	}

	/**
	 * Return the maximum height that the edge effect will be drawn at given the original
	 * {@link #setSize(int, int) input size}.
	 * @return The maximum height of the edge effect
	 */
	public int getMaxHeight() {
		return mMaxHeight + mOffsetY;
	}
}
