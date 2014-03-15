package br.com.carlosrafaelgn.fplay.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Rect;
import android.graphics.Shader;
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

public final class ColorPickerView extends RelativeLayout implements View.OnClickListener, BgSeekBar.OnBgSeekBarChangeListener, BgSeekBar.OnBgSeekBarDrawListener, TextWatcher {
	private BgSeekBar barH, barS, barV;
	private EditText txt, txtH, txtS, txtV;
	private TextView lblCurrent;
	private HSV hsv, hsvTmp;
	private int initialColor, currentColor;
	private boolean ignoreChanges;
	private LinearGradient gH, gS, gV;
	private ColorDrawable bgCurrent;
	
	public static void showDialog(Context context, int initialColor) {
		UI.prepareDialogAndShow((new AlertDialog.Builder(context))
		//.setTitle(R.string.oops)
		.setCancelable(true)
		.setView(new ColorPickerView(context, initialColor))
		.setPositiveButton(R.string.ok, null)
		.setNegativeButton(R.string.cancel, null)
		.create());
	}
	
	public ColorPickerView(Context context) {
		super(context);
		init(context, 0);
	}
	
	public ColorPickerView(Context context, int initialColor) {
		super(context);
		init(context, initialColor);
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
		barH.setMax(1024);
		barH.setValue((int)(hsv.h * 1024.0));
		barH.setPointMode(true);
		barH.setVertical(true);
		p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.leftMargin = ((eachW - UI.defaultControlSize) >> 1);
		p.addRule(RelativeLayout.ALIGN_LEFT, 2);
		p.addRule(RelativeLayout.BELOW, 2);
		p.addRule(RelativeLayout.ABOVE, 6);
		addView(barH, p);
		barS = new BgSeekBar(context);
		barS.setMax(1024);
		barS.setValue((int)(hsv.s * 1024.0));
		barS.setPointMode(true);
		barS.setVertical(true);
		p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.topMargin = UI._8dp;
		p.leftMargin = ((eachW - UI.defaultControlSize) >> 1);
		p.addRule(RelativeLayout.ALIGN_LEFT, 3);
		p.addRule(RelativeLayout.BELOW, 3);
		p.addRule(RelativeLayout.ABOVE, 6);
		addView(barS, p);
		barV = new BgSeekBar(context);
		barV.setMax(1024);
		barV.setValue((int)(hsv.v * 1024.0));
		barV.setPointMode(true);
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
	
	private void updateGH() {
		gH = new LinearGradient(0, 0, barH.getHeight(), 0, new int[] { 0xffff0000, 0xffffff00, 0xff00ff00, 0xff00ffff, 0xff0000ff, 0xffff00ff, 0xffff0000 }, new float[] { 0, 1.0f/6.0f, 2.0f/6.0f, 0.5f, 4.0f/6.0f, 5.0f/6.0f, 1.0f }, Shader.TileMode.CLAMP);
	}
	
	private void updateGS() {
		hsvTmp.h = hsv.h;
		hsvTmp.s = 0;
		hsvTmp.v = hsv.v;
		final int c = hsvTmp.toRGB();
		hsvTmp.s = 1.0f;
		gS = new LinearGradient(0, 0, barS.getHeight(), 0, c, hsvTmp.toRGB(), Shader.TileMode.CLAMP);
	}
	
	private void updateGV() {
		hsvTmp.h = hsv.h;
		hsvTmp.s = hsv.s;
		hsvTmp.v = 0;
		final int c = hsvTmp.toRGB();
		hsvTmp.v = 1.0f;
		gV = new LinearGradient(0, 0, barV.getHeight(), 0, c, hsvTmp.toRGB(), Shader.TileMode.CLAMP);
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
	public void onClick(View view) {
		if (txt.requestFocus())
			txt.setText(ColorUtils.toHexColor(initialColor));
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (ignoreChanges)
			return;
		hsv.h = barH.getValue() * 0.0009765625;
		hsv.s = barS.getValue() * 0.0009765625;
		hsv.v = barV.getValue() * 0.0009765625;
		currentColor = hsv.toRGB();
		if (seekBar == barH) {
			updateGS();
			updateGV();
		} else if (seekBar == barS) {
			updateGV();
		} else if (seekBar == barV) {
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
		bgCurrent.change(currentColor);
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
		if (seekBar == barH)
			updateGH();
		else if (seekBar == barS)
			updateGS();
		else if (seekBar == barV)
			updateGV();
	}
	
	@Override
	public void onDraw(Canvas canvas, BgSeekBar seekBar, Rect rect, int filledSize) {
		final int bottom = rect.bottom;
		rect.bottom -= UI._16dp;
		if (seekBar == barH) {
			if (gH != null)
				UI.fillRect(canvas, gH, rect);
		} else if (seekBar == barS) {
			if (gS != null)
				UI.fillRect(canvas, gS, rect);
		} else if (seekBar == barV) {
			if (gV != null)
				UI.fillRect(canvas, gV, rect);
		}
		TextIconDrawable.drawIcon(canvas, UI.ICON_SLIDER, filledSize + UI.strokeSize - UI._16dp, bottom - UI._16dp, UI._16dp << 1, currentColor);
	}
	
	@Override
	public void afterTextChanged(Editable s) {
		if (ignoreChanges)
			return;
		final View f = getFocusedChild();
		if (f == txtH || f == txtS || f == txtV) {
			try {
				final int nh = Integer.parseInt(txtH.getText().toString());
				if (nh > 360 || nh < 0)
					return;
				final int ns = Integer.parseInt(txtS.getText().toString());
				if (ns > 100 || ns < 0)
					return;
				final int nv = Integer.parseInt(txtV.getText().toString());
				if (nv > 100 || nv < 0)
					return;
				hsv.h = (double)nh / 360.0;
				hsv.s = (double)ns / 100.0;
				hsv.v = (double)nv / 100.0;
			} catch (Throwable ex) {
			}
			ignoreChanges = true;
			currentColor = hsv.toRGB();
			barH.setValue((int)(hsv.h * 1024.0));
			barS.setValue((int)(hsv.s * 1024.0));
			barV.setValue((int)(hsv.v * 1024.0));
			txt.setText(ColorUtils.toHexColor(currentColor));
			ignoreChanges = false;
		} else {
			final String cs = s.toString().trim();
			if (cs.length() < 6)
				return;
			if (cs.charAt(0) == '#') {
				if (cs.length() != 7)
					return;
			} else if (cs.length() != 6) {
				return;
			}
			final int c = ColorUtils.parseHexColor(cs);
			if (c == 0)
				return;
			currentColor = c;
			hsv.fromRGB(c);
			ignoreChanges = true;
			barH.setValue((int)(hsv.h * 1024.0));
			barS.setValue((int)(hsv.s * 1024.0));
			barV.setValue((int)(hsv.v * 1024.0));
			txtH.setText(Integer.toString((int)(hsv.h * 360.0)));
			txtS.setText(Integer.toString((int)(hsv.s * 100.0)));
			txtV.setText(Integer.toString((int)(hsv.v * 100.0)));
			ignoreChanges = false;
		}
		updateGS();
		updateGV();
		barH.invalidate();
		barS.invalidate();
		barV.invalidate();
		bgCurrent.change(currentColor);
		lblCurrent.invalidate();
	}
	
	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}
	
	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}
}
