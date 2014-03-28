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

import java.nio.IntBuffer;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.ui.drawable.ColorDrawable;
import br.com.carlosrafaelgn.fplay.ui.drawable.TextIconDrawable;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;
import br.com.carlosrafaelgn.fplay.util.ColorUtils.HSV;

public final class ColorPickerView extends RelativeLayout implements View.OnClickListener, BgSeekBar.OnBgSeekBarChangeListener, BgSeekBar.OnBgSeekBarDrawListener, TextWatcher, DialogInterface.OnDismissListener, DialogInterface.OnClickListener {
	public static interface OnColorPickerViewListener {
		public void onColorPicked(ColorPickerView picker, View parentView, int color);
	}
	
	private BgSeekBar barH, barS, barV;
	private EditText txt, txtH, txtS, txtV;
	private TextView lblCurrent;
	private HSV hsv, hsvTmp;
	private int initialColor, currentColor;
	private boolean ignoreChanges;
	private Bitmap bmpH, bmpS, bmpV;
	private Rect bmpRect;
	private IntBuffer bmpBuf;
	private ColorDrawable bgCurrent;
	private View parentView;
	private OnColorPickerViewListener listener;
	
	public static void showDialog(Context context, int initialColor, View parentView, OnColorPickerViewListener listener) {
		final ColorPickerView picker = new ColorPickerView(context, initialColor);
		picker.parentView = parentView;
		picker.listener = listener;
		final AlertDialog dlg = (new AlertDialog.Builder(context))
				.setView(picker)
				.setPositiveButton(R.string.ok, picker)
				.setNegativeButton(R.string.cancel, null)
				.create();
		dlg.setOnDismissListener(picker);
		UI.prepareDialogAndShow(dlg);
	}
	
	private ColorPickerView(Context context, int initialColor) {
		super(context);
		init(context, initialColor);
	}
	
	public ColorPickerView(Context context) {
		super(context);
		init(context, 0);
	}
	
	public ColorPickerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, 0);
	}
	
	public ColorPickerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, 0);
	}
	
	@SuppressWarnings("deprecation")
	private void init(Context context, int initialColor) {
		setPadding(UI._8dp, UI._8dp, UI._8dp, UI._8dp);
		final int eachW = (UI._18sp * 7) >> 1;
		initialColor = 0xff000000 | (initialColor & 0x00ffffff);
		hsv = new HSV();
		hsv.fromRGB(initialColor);
		hsvTmp = new HSV();
		bmpRect = new Rect(0, 0, 1, 1);
		this.initialColor = initialColor;
		currentColor = initialColor;
		final LinearLayout l = new LinearLayout(context);
		l.setId(1);
		l.setWeightSum(2);
		LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
		addView(l, p);
		final TextView lbl = new TextView(context);
		lbl.setBackgroundDrawable(new ColorDrawable(initialColor));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, UI.defaultControlSize);
		lp.weight = 1;
		lp.rightMargin = UI._4dp;
		l.addView(lbl, lp);
		bgCurrent = new ColorDrawable(initialColor);
		lblCurrent = new TextView(context);
		lblCurrent = new TextView(context);
		lblCurrent.setBackgroundDrawable(bgCurrent);
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, UI.defaultControlSize);
		lp.weight = 1;
		lp.leftMargin = UI._4dp;
		l.addView(lblCurrent, lp);
		
		TextView lblTit = new TextView(context);
		lblTit.setId(2);
		lblTit.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		lblTit.setSingleLine();
		lblTit.setGravity(Gravity.CENTER);
		lblTit.setText("H");
		p = new LayoutParams(eachW, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.BELOW, 1);
		addView(lblTit, p);
		lblTit = new TextView(context);
		lblTit.setId(3);
		lblTit.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		lblTit.setSingleLine();
		lblTit.setGravity(Gravity.CENTER);
		lblTit.setText("S");
		p = new LayoutParams(eachW, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.BELOW, 1);
		addView(lblTit, p);
		lblTit = new TextView(context);
		lblTit.setId(4);
		lblTit.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		lblTit.setSingleLine();
		lblTit.setGravity(Gravity.CENTER);
		lblTit.setText("V");
		p = new LayoutParams(eachW, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.BELOW, 1);
		addView(lblTit, p);
		
		barH = new BgSeekBar(context);
		barH.setId(5);
		barH.setMax(360);
		barH.setValue((int)(hsv.h * 360.0));
		barH.setSliderMode(true);
		barH.setVertical(true);
		p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.leftMargin = ((eachW - UI.defaultControlSize) >> 1);
		p.addRule(RelativeLayout.ALIGN_LEFT, 2);
		p.addRule(RelativeLayout.BELOW, 2);
		p.addRule(RelativeLayout.ABOVE, 6);
		addView(barH, p);
		barS = new BgSeekBar(context);
		barS.setMax(100);
		barS.setValue((int)(hsv.s * 100.0));
		barS.setSliderMode(true);
		barS.setVertical(true);
		p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.leftMargin = ((eachW - UI.defaultControlSize) >> 1);
		p.addRule(RelativeLayout.ALIGN_LEFT, 3);
		p.addRule(RelativeLayout.BELOW, 3);
		p.addRule(RelativeLayout.ABOVE, 6);
		addView(barS, p);
		barV = new BgSeekBar(context);
		barV.setMax(100);
		barV.setValue((int)(hsv.v * 100.0));
		barV.setSliderMode(true);
		barV.setVertical(true);
		p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.leftMargin = ((eachW - UI.defaultControlSize) >> 1);
		p.addRule(RelativeLayout.ALIGN_LEFT, 4);
		p.addRule(RelativeLayout.BELOW, 4);
		p.addRule(RelativeLayout.ABOVE, 6);
		addView(barV, p);
		
		txtH = new EditText(context);
		txtH.setId(6);
		txtH.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		txtH.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		txtH.setSingleLine();
		txtH.setGravity(Gravity.CENTER);
		txtH.setText(Integer.toString((int)(hsv.h * 360.0)));
		p = new LayoutParams(eachW, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ABOVE, 7);
		addView(txtH, p);
		txtS = new EditText(context);
		txtS.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		txtS.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		txtS.setSingleLine();
		txtS.setGravity(Gravity.CENTER);
		txtS.setText(Integer.toString((int)(hsv.s * 100.0)));
		p = new LayoutParams(eachW, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ABOVE, 7);
		addView(txtS, p);
		txtV = new EditText(context);
		txtV.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		txtV.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		txtV.setSingleLine();
		txtV.setGravity(Gravity.CENTER);
		txtV.setText(Integer.toString((int)(hsv.v * 100.0)));
		p = new LayoutParams(eachW, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ABOVE, 7);
		addView(txtV, p);
		
		txt = new EditText(context);
		txt.setId(7);
		txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._18sp);
		txt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		txt.setSingleLine();
		txt.setGravity(Gravity.CENTER);
		txt.setText(ColorUtils.toHexColor(initialColor));
		p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
		addView(txt, p);
		
		lbl.setOnClickListener(this);
		barH.setOnBgSeekBarChangeListener(this);
		barH.setOnBgSeekBarDrawListener(this);
		barS.setOnBgSeekBarChangeListener(this);
		barS.setOnBgSeekBarDrawListener(this);
		barV.setOnBgSeekBarChangeListener(this);
		barV.setOnBgSeekBarDrawListener(this);
		txtH.addTextChangedListener(this);
		txtS.addTextChangedListener(this);
		txtV.addTextChangedListener(this);
		txt.addTextChangedListener(this);
	}
	
	private void cleanup() {
		barH = null;
		barS = null;
		barV = null;
		txt = null;
		txtH = null;
		txtS = null;
		txtV = null;
		lblCurrent = null;
		hsv = null;
		hsvTmp = null;
		if (bmpH != null) {
			bmpH.recycle();
			bmpH = null;
		}
		if (bmpS != null) {
			bmpS.recycle();
			bmpS = null;
		}
		if (bmpV != null) {
			bmpV.recycle();
			bmpV = null;
		}
		bmpBuf = null;
		bgCurrent = null;
		parentView = null;
		listener = null;
		System.gc();
	}
	
	private static Bitmap updateBitmap(Bitmap bmp, int width) {
		if (bmp == null) {
			return Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888);
		} else if (bmp.getWidth() != width) {
			bmp.recycle();
			return Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888);
		}
		return bmp;
	}
	
	private static IntBuffer updateBuffer(IntBuffer buffer, int width) {
		buffer = ((buffer == null || buffer.capacity() < width) ? IntBuffer.allocate(width) : buffer);
		buffer.clear();
		buffer.limit(width);
		return buffer;
	}
	
	private void updateGH() {
		final int h = barH.getHeight();
		bmpH = updateBitmap(bmpH, h);
		bmpBuf = updateBuffer(bmpBuf, h);
		hsvTmp.s = hsv.s;
		hsvTmp.v = hsv.v;
		final double d = (double)(h - 1);
		for (int i = 0; i < h; i++) {
			hsvTmp.h = (double)i / d;
			bmpBuf.put(i, hsvTmp.toRGB(true));
		}
		bmpH.copyPixelsFromBuffer(bmpBuf);
	}
	
	private void updateGS() {
		final int h = barS.getHeight();
		bmpS = updateBitmap(bmpS, h);
		bmpBuf = updateBuffer(bmpBuf, h);
		hsvTmp.h = hsv.h;
		hsvTmp.v = hsv.v;
		final double d = (double)(h - 1);
		for (int i = 0; i < h; i++) {
			hsvTmp.s = (double)i / d;
			bmpBuf.put(i, hsvTmp.toRGB(true));
		}
		bmpS.copyPixelsFromBuffer(bmpBuf);
	}
	
	private void updateGV() {
		final int h = barV.getHeight();
		bmpV = updateBitmap(bmpV, h);
		bmpBuf = updateBuffer(bmpBuf, h);
		hsvTmp.h = hsv.h;
		hsvTmp.s = hsv.s;
		final double d = (double)(h - 1);
		for (int i = 0; i < h; i++) {
			hsvTmp.v = (double)i / d;
			bmpBuf.put(i, hsvTmp.toRGB(true));
		}
		bmpV.copyPixelsFromBuffer(bmpBuf);
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
		super.setBackground(null);
	}
	
	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background) {
		super.setBackgroundDrawable(null);
	}
	
	@Override
	public void setBackgroundResource(int resid) {
		super.setBackgroundResource(0);
	}
	
	@Override
	public void setBackgroundColor(int color) {
		super.setBackgroundResource(0);
	}
	
	@Override
	public Drawable getBackground() {
		return null;
	}
	
	@Override
	public void invalidateDrawable(Drawable drawable) {
	}
	
	@Override
	protected boolean verifyDrawable(Drawable drawable) {
		return false;
	}
	
	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return false;
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		cleanup();
	}
	
	@Override
	public void onClick(View view) {
		if (txt.requestFocus())
			txt.setText(ColorUtils.toHexColor(initialColor));
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (ignoreChanges)
			return;
		hsv.h = barH.getValue() / 360.0;
		hsv.s = barS.getValue() / 100.0;
		hsv.v = barV.getValue() / 100.0;
		currentColor = hsv.toRGB(false);
		if (seekBar == barH) {
			updateGS();
			updateGV();
		} else if (seekBar == barS) {
			updateGH();
			updateGV();
		} else if (seekBar == barV) {
			updateGH();
			updateGS();
		}
		ignoreChanges = true;
		txtH.setText(Integer.toString((int)(hsv.h * 360.0)));
		txtS.setText(Integer.toString((int)(hsv.s * 100.0)));
		txtV.setText(Integer.toString((int)(hsv.v * 100.0)));
		txt.setText(ColorUtils.toHexColor(currentColor));
		ignoreChanges = false;
		barH.invalidate();
		barS.invalidate();
		barV.invalidate();
		bgCurrent.setColor(currentColor);
		lblCurrent.invalidate();
	}
	
	@Override
	public boolean onStartTrackingTouch(BgSeekBar seekBar) {
		return true;
	}
	
	@Override
	public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled) {
		System.gc();
	}
	
	@Override
	public void onSizeChanged(BgSeekBar seekBar, int width, int height) {
		bmpRect.right = height;
		if (seekBar == barH)
			updateGH();
		else if (seekBar == barS)
			updateGS();
		else if (seekBar == barV)
			updateGV();
	}
	
	@Override
	public void onDraw(Canvas canvas, BgSeekBar seekBar, Rect rect, int filledSize) {
		final Bitmap bmp = ((seekBar == barH) ? bmpH : ((seekBar == barS) ? bmpS : bmpV));
		rect.bottom -= UI._8dp;
		if (bmp != null) {
			for (int i = rect.bottom - 1; i >= UI._8dp; i--)
				canvas.drawBitmap(bmp, 0, i, null);
		}
		TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDERTOP, filledSize + UI.strokeSize - UI._8dp, 0, UI._16dp, 0xffffffff);
		TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDERBOTTOM, filledSize + UI.strokeSize - UI._8dp, rect.bottom, UI._16dp, 0xff000000);
	}
	
	@Override
	public void afterTextChanged(Editable s) {
		if (ignoreChanges)
			return;
		final View f = getFocusedChild();
		if (f == txtH || f == txtS || f == txtV) {
			int nh, ns, nv;
			try {
				nh = Integer.parseInt(txtH.getText().toString());
				if (nh > 360 || nh < 0)
					return;
				ns = Integer.parseInt(txtS.getText().toString());
				if (ns > 100 || ns < 0)
					return;
				nv = Integer.parseInt(txtV.getText().toString());
				if (nv > 100 || nv < 0)
					return;
				hsv.h = (double)nh / 360.0;
				hsv.s = (double)ns / 100.0;
				hsv.v = (double)nv / 100.0;
			} catch (Throwable ex) {
				return;
			}
			ignoreChanges = true;
			currentColor = hsv.toRGB(false);
			barH.setValue(nh);
			barS.setValue(ns);
			barV.setValue(nv);
			txt.setText(ColorUtils.toHexColor(currentColor));
			ignoreChanges = false;
		} else {
			String cs = s.toString().trim();
			if (cs.length() < 6)
				return;
			if (cs.charAt(0) == '#')
				cs = cs.substring(1).trim();
			if (cs.length() != 6)
				return;
			int c = ColorUtils.parseHexColor(cs);
			if (c == 0)
				return;
			currentColor = c;
			hsv.fromRGB(c);
			ignoreChanges = true;
			c = (int)(hsv.h * 360.0);
			barH.setValue(c);
			txtH.setText(Integer.toString(c));
			c = (int)(hsv.s * 100.0);
			barS.setValue(c);
			txtS.setText(Integer.toString(c));
			c = (int)(hsv.v * 100.0);
			barV.setValue(c);
			txtV.setText(Integer.toString(c));
			ignoreChanges = false;
		}
		updateGH();
		updateGS();
		updateGV();
		barH.invalidate();
		barS.invalidate();
		barV.invalidate();
		bgCurrent.setColor(currentColor);
		lblCurrent.invalidate();
	}
	
	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}
	
	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		cleanup();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE && listener != null)
			listener.onColorPicked(this, parentView, currentColor);
		cleanup();
	}
}
