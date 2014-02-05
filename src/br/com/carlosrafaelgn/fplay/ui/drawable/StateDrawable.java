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
package br.com.carlosrafaelgn.fplay.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.StateSet;
import br.com.carlosrafaelgn.fplay.ui.UI;

public final class StateDrawable extends Drawable {
	private final Drawable normal, altered, checked, checkedAltered;
	private final boolean hasChecks;
	private int[] stateSet;
	private int opacity, state, alpha;
	private boolean dither;
	private ColorFilter cf;
	private Drawable current;
	
	public StateDrawable(Drawable normal, Drawable altered) {
		this.normal = normal;
		this.altered = altered;
		this.checked = null;
		this.checkedAltered = null;
		this.hasChecks = false;
		this.stateSet = StateSet.WILD_CARD;
		computeCurrent();
	}
	
	public StateDrawable(Drawable normal, Drawable altered, Drawable checked, Drawable checkedAltered) {
		this.normal = normal;
		this.altered = altered;
		this.checked = checked;
		this.checkedAltered = checkedAltered;
		this.hasChecks = ((checked != null) || (checkedAltered != null));
		this.stateSet = StateSet.WILD_CARD;
		computeCurrent();
	}
	
	private StateDrawable(Drawable normal, Drawable altered, Drawable checked, Drawable checkedAltered, int state, int[] stateSet) {
		this.normal = normal;
		this.altered = altered;
		this.checked = checked;
		this.checkedAltered = checkedAltered;
		this.hasChecks = ((checked != null) || (checkedAltered != null));
		this.state = state;
		this.stateSet = stateSet;
		computeCurrent();
	}
	
	private Drawable computeCurrent() {
		if (hasChecks && ((state & UI.STATE_CHECKED) != 0))
			current = (((state & (UI.STATE_SELECTED | UI.STATE_FOCUSED | UI.STATE_PRESSED)) != 0) ? checkedAltered : checked);
		else
			current = (((state & (UI.STATE_SELECTED | UI.STATE_FOCUSED | UI.STATE_PRESSED)) != 0) ? altered : normal);
		opacity = ((current == null) ? PixelFormat.TRANSPARENT : current.getOpacity());
		return current;
	}
	
	@Override
	public void draw(Canvas canvas) {
		if (current != null)
			current.draw(canvas);
	}
	
	@Override
	public int[] getState() {
		return stateSet;
	}
	
	@Override
	public boolean setState(int[] stateSet) {
		this.stateSet = stateSet;
		int newState = 0;
		if (stateSet != null) {
			for (int i = stateSet.length - 1; i >= 0; i--) {
				switch (stateSet[i]) {
				case android.R.attr.state_selected:
					newState |= UI.STATE_SELECTED;
					break;
				case android.R.attr.state_focused:
					newState |= UI.STATE_FOCUSED;
					break;
				case android.R.attr.state_pressed:
					newState |= UI.STATE_PRESSED;
					break;
				case android.R.attr.state_checked:
					newState |= UI.STATE_CHECKED;
					break;
				}
			}
		}
		if (state == newState)
			return false;
		state = newState;
		computeCurrent();
		invalidateSelf();
		return true;
	}
	
	@Override
	public void setBounds(Rect bounds) {
		super.setBounds(bounds);
		if (normal != null)
			normal.setBounds(bounds);
		if (altered != null)
			altered.setBounds(bounds);
		if (checked != null)
			checked.setBounds(bounds);
		if (checkedAltered != null)
			checkedAltered.setBounds(bounds);
	}
	
	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		super.setBounds(left, top, right, bottom);
		if (normal != null)
			normal.setBounds(left, top, right, bottom);
		if (altered != null)
			altered.setBounds(left, top, right, bottom);
		if (checked != null)
			checked.setBounds(left, top, right, bottom);
		if (checkedAltered != null)
			checkedAltered.setBounds(left, top, right, bottom);
	}
	
	@Override
	public void setAlpha(int alpha) {
		if (this.alpha != alpha) {
			this.alpha = alpha;
			if (normal != null)
				normal.setAlpha(alpha);
			if (altered != null)
				altered.setAlpha(alpha);
			if (checked != null)
				checked.setAlpha(alpha);
			if (checkedAltered != null)
				checkedAltered.setAlpha(alpha);
		}
	}
	
	@Override
	public void setDither(boolean dither) {
		if (this.dither != dither) {
			this.dither = dither;
			if (normal != null)
				normal.setDither(dither);
			if (altered != null)
				altered.setDither(dither);
			if (checked != null)
				checked.setDither(dither);
			if (checkedAltered != null)
				checkedAltered.setDither(dither);
		}
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
		if (this.cf != cf) {
			this.cf = cf;
			if (normal != null)
				normal.setColorFilter(cf);
			if (altered != null)
				altered.setColorFilter(cf);
			if (checked != null)
				checked.setColorFilter(cf);
			if (checkedAltered != null)
				checkedAltered.setColorFilter(cf);
		}
	}
	
	@Override
	public Drawable getCurrent() {
		return current;
	}
	
	@Override
	public int getIntrinsicHeight() {
		return ((current != null) ? current.getIntrinsicHeight() : 0);
	}
	
	@Override
	public int getIntrinsicWidth() {
		return ((current != null) ? current.getIntrinsicWidth() : 0);
	}
	
	@Override
	public int getMinimumHeight() {
		return ((current != null) ? current.getMinimumHeight() : 0);
	}
	
	@Override
	public int getMinimumWidth() {
		return ((current != null) ? current.getMinimumWidth() : 0);
	}
	
	@Override
	public Region getTransparentRegion() {
		return ((current != null) ? current.getTransparentRegion() : null);
	}
	
 	@Override
	public boolean getPadding(Rect padding) {
		return ((current != null) ? current.getPadding(padding) : false);
	}
	
	@Override
	public int getOpacity() {
		return opacity;
	}
	
	@Override
	public Drawable mutate() {
		final StateDrawable t = new StateDrawable((normal == null) ? null : normal.mutate(), (altered == null) ? null : altered.mutate(), (checked == null) ? null : checked.mutate(), (checkedAltered == null) ? null : checkedAltered.mutate(), state, stateSet);
		t.setAlpha(alpha);
		t.setDither(dither);
		t.setColorFilter(cf);
		t.setVisible(isVisible(), true);
		t.setBounds(getBounds());
		return t;
	}
	
	@Override
	public boolean isStateful() {
		return true;
	}
}
