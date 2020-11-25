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

import android.content.res.ColorStateList;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

public final class BgColorStateList extends ColorStateList {
	private static final int[][] states = new int[][] { new int[] {} };
	private static final int[] colors = new int[] { 0 };
	private int normalColor;
	private final int alteredColor;
	
	public BgColorStateList(int color) {
		super(states, colors);
		this.normalColor = color;
		this.alteredColor = color;
	}
	
	public BgColorStateList(int normalColor, int alteredColor) {
		super(states, colors);
		this.normalColor = normalColor;
		this.alteredColor = alteredColor;
	}

	public void setNormalColorAlpha(int alpha) {
		normalColor = (alpha << 24) | (normalColor & 0x00ffffff);
	}

	@Override
	public int getColorForState(int[] stateSet, int defaultColor) {
		if (stateSet != null) {
			for (int i = stateSet.length - 1; i >= 0; i--) {
				if (stateSet[i] == android.R.attr.state_pressed ||
					stateSet[i] == android.R.attr.state_focused ||
					stateSet[i] == android.R.attr.state_hovered)
					return alteredColor;
			}
		}
		return normalColor;
	}
	
	@Override
	public int getDefaultColor() {
		return normalColor;
	}
	
	@Override
	public boolean isStateful() {
		return (normalColor != alteredColor);
	}

	@NonNull
	@Override
	public ColorStateList withAlpha(int alpha) {
		return this;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(normalColor);
		dest.writeInt(alteredColor);
	}

	public static final Parcelable.Creator<BgColorStateList> CREATOR = new Parcelable.Creator<BgColorStateList>() {
		@Override
		public BgColorStateList[] newArray(int size) {
			return new BgColorStateList[size];
		}

		@Override
		public BgColorStateList createFromParcel(Parcel source) {
			return new BgColorStateList(source.readInt(), source.readInt());
		}
	};
}
