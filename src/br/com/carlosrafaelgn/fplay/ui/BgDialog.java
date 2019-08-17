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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.ui.drawable.BgShadowDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;

public final class BgDialog extends Dialog implements View.OnClickListener, View.OnTouchListener {
	private BgButton btnNeutral, btnNegative, btnPositive;
	private View contentView;
	private CharSequence title;
	private boolean titleVisible, emptyBackground, scanChildren, titleBorder, neutralIcon, hideSoftInput;
	private Drawable backgroundDrawable;
	private int backgroundId, shadowType;
	private DialogInterface.OnClickListener clickListener;

	public BgDialog(Context context, View contentView, DialogInterface.OnClickListener clickListener) {
		super(context, UI.dimBackground ? R.style.BgDialog : R.style.BgDialogNoDimming);
		setCancelable(true);
		setCanceledOnTouchOutside(true);
		title = "";
		scanChildren = true;
		titleBorder = true;
		this.contentView = contentView;
		this.clickListener = clickListener;
		UI.preparePopupTransition(this);
	}

	@Override
	public void setTitle(CharSequence title) {
		setTitle(title, true);
	}

	@Override
	public void setTitle(int titleId) {
		setTitle(titleId, true);
	}

	public void setTitle(int titleId, boolean visible) {
		title = (titleId == 0 ? "" : getContext().getText(titleId));
		titleVisible = (titleId != 0 && visible);
		super.setTitle(title);
	}

	public void setTitle(CharSequence title, boolean visible) {
		this.title = ((title == null) ? "" : title);
		titleVisible = (title != null && title.length() != 0 && visible);
		super.setTitle(this.title);
	}

	public void setNeutralButton(int resId) {
		neutralIcon = false;
		if (resId == 0) {
			btnNeutral = null;
		} else {
			if (btnNeutral == null)
				btnNeutral = new BgButton(getContext());
			btnNeutral.setText(resId);
			btnNeutral.setOnClickListener(this);
		}
	}

	public void setNeutralButton(String icon, CharSequence contentDescription) {
		neutralIcon = true;
		if (btnNeutral == null)
			btnNeutral = new BgButton(getContext());
		btnNeutral.setIcon(icon);
		btnNeutral.setContentDescription(contentDescription);
		btnNeutral.setOnClickListener(this);
	}

	public void setNegativeButton(int resId) {
		if (resId == 0) {
			btnNegative = null;
		} else {
			if (btnNegative == null)
				btnNegative = new BgButton(getContext());
			btnNegative.setText(resId);
			btnNegative.setOnClickListener(this);
		}
	}

	public void setPositiveButton(int resId) {
		if (resId == 0) {
			btnPositive = null;
		} else {
			if (btnPositive == null)
				btnPositive = new BgButton(getContext());
			btnPositive.setText(resId);
			btnPositive.setOnClickListener(this);
		}
	}

	public void setScanChildren() {
		scanChildren = true;
	}

	public void removeTitleBorder() {
		titleBorder = false;
	}

	public void setEmptyBackground() {
		emptyBackground = true;
		backgroundDrawable = null;
		backgroundId = 0;
	}

	public void setShadowType(int shadowType) {
		this.shadowType = shadowType;
	}

	public void setBackground(Drawable background) {
		emptyBackground = false;
		backgroundDrawable = background;
		backgroundId = 0;
	}

	public void setBackground(int resId) {
		emptyBackground = false;
		backgroundDrawable = null;
		backgroundId = resId;
	}

	public void setHideSoftInput(boolean hideSoftInput) {
		this.hideSoftInput = hideSoftInput;
	}

	public void showPositiveButton(boolean show) {
		if (btnPositive == null)
			return;
		if (btnNegative != null) {
			final RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			if (show)
				rp.addRule(RelativeLayout.LEFT_OF, 3);
			else
				rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			btnNegative.setLayoutParams(rp);
		}
		btnPositive.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	@Override
	public boolean dispatchPopulateAccessibilityEvent(@NonNull AccessibilityEvent event) {
		if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
			event.getText().add(title);
			return true;
		}
		return super.dispatchPopulateAccessibilityEvent(event);
	}

	@Override
	public void onClick(View v) {
		if (clickListener == null) {
			dismiss();
			return;
		}
		if (v == btnNeutral)
			clickListener.onClick(this, DialogInterface.BUTTON_NEUTRAL);
		else if (v == btnNegative)
			clickListener.onClick(this, DialogInterface.BUTTON_NEGATIVE);
		else if (v == btnPositive)
			clickListener.onClick(this, DialogInterface.BUTTON_POSITIVE);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		//we cannot consume the touch event if the view is a ScrollView,
		//otherwise the user will not be able to scroll the view...
		if (v == contentView)
			return !(v instanceof ScrollView);
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			dismiss(); //dismiss the dialog if the user touches the shadow
		case MotionEvent.ACTION_MOVE:
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			return true;
		}
		return false;
	}

	private void scanChildrenRecursive(ViewGroup parent) {
		for (int i = parent.getChildCount(); i >= 0; i--) {
			final View v = parent.getChildAt(i);
			if (v instanceof ViewGroup)
				scanChildrenRecursive((ViewGroup)v);
			else if (v instanceof TextView)
				((TextView)v).setTypeface(UI.defaultTypeface);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getWindow() != null)
			getWindow().setSoftInputMode(hideSoftInput ? WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

		final Context context = getContext();

		final BgFlexLayout panel = new BgFlexLayout(getContext());

		//weird... I know... but at least lint stops complaining!
		final int id = 0;

		if (titleVisible) {
			final TextView txtTitle = new TextView(context);
			txtTitle.setText(title);
			txtTitle.setId(id + 1);
			txtTitle.setTypeface(UI.defaultTypeface);
			txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
			txtTitle.setTextColor(UI.colorState_text_title_static);
			UI.prepareControlContainer(txtTitle, false, titleBorder, UI.controlLargeMargin, UI.controlLargeMargin, UI.controlLargeMargin, UI.controlLargeMargin);
			panel.addView(txtTitle, new BgFlexLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
		}

		final RelativeLayout panelBottom;
		if (btnNeutral != null || btnNegative != null || btnPositive != null) {
			RelativeLayout.LayoutParams rp;
			panelBottom = new RelativeLayout(context);
			panelBottom.setId(id + 2);
			final int padding = ((UI.isLargeScreen || !UI.isLowDpiScreen) ? UI.controlMargin : UI.controlSmallMargin);
			UI.prepareControlContainer(panelBottom, true, false, padding, padding, padding, padding);
			if (btnNeutral != null) {
				if (!neutralIcon)
					btnNeutral.setDefaultHeightAndLargeMinimumWidth();
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				rp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				panelBottom.addView(btnNeutral, rp);
			}
			if (btnNegative != null) {
				btnNegative.setDefaultHeightAndLargeMinimumWidth();
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				if (btnPositive != null)
					rp.addRule(RelativeLayout.LEFT_OF, 3);
				else
					rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				panelBottom.addView(btnNegative, rp);
			}
			if (btnPositive != null) {
				btnPositive.setId(id + 3);
				btnPositive.setDefaultHeightAndLargeMinimumWidth();
				rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				if (UI.isLargeScreen)
					rp.leftMargin = UI.controlMargin;
				rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				panelBottom.addView(btnPositive, rp);
			}
			panel.addView(panelBottom, new BgFlexLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
		} else {
			panelBottom = null;
		}

		final boolean flexPanelUsed = (titleVisible || panelBottom != null);

		if (!emptyBackground || flexPanelUsed) {
			if (backgroundId != 0)
				contentView.setBackgroundResource(backgroundId);
			else
				contentView.setBackgroundDrawable(backgroundDrawable != null ? backgroundDrawable : new ColorDrawable(UI.color_list_original));
		}

		if (scanChildren && UI.isUsingAlternateTypeface) {
			if (contentView instanceof TextView)
				((TextView)contentView).setTypeface(UI.defaultTypeface);
			else if (contentView instanceof ViewGroup)
				scanChildrenRecursive((ViewGroup)contentView);
		}

		if (flexPanelUsed) {
			panel.addView(contentView, titleVisible ? 1 : 0, new BgFlexLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
			panel.setFlexChild(contentView);
			panel.setOpaque(true);
			contentView = panel;
		}

		//we must set this listener and always return true
		//to prevent the touch from falling through to the
		//parent, dismissing the dialog (setting contentView
		//as clickable also prevents the click from falling
		//through, but also causes undesired effects when
		//using mouse and keyboard)
		contentView.setOnTouchListener(this);

		setContentView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		final ViewParent viewParent = contentView.getParent();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && !UI.isLowDpiScreen && (viewParent instanceof ViewGroup)) {
			final ViewGroup parent = (ViewGroup)viewParent;
			//we cannot use setOnClickListener() because the click produces audio/haptic feedback
			//if the user configured the device in such way (whereas touching the original background
			//does not produce any feedback)
			parent.setOnTouchListener(this);
			parent.setBackgroundDrawable(new BgShadowDrawable(context, shadowType));
		}
	}
}
