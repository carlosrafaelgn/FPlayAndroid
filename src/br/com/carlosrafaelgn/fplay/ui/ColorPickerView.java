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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import br.com.carlosrafaelgn.fplay.R;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class ColorPickerView extends LinearLayout implements DialogInterface.OnClickListener, TextWatcher {
	public interface OnColorPickerViewListener {
		void onColorPicked(ColorPickerView picker, View parentView, int color);
	}

	private final class ColorSwatchView extends View implements OnClickListener {
		private final int[] colors;
		private int squareSize, margin, contentsWidth, contentsHeight;
		private int contentsLeft, currentColor, lastX, lastY;
		private boolean attached;

		public ColorSwatchView(Context context) {
			super(context);

			super.setBackgroundResource(0);
			super.setDrawingCacheEnabled(false);

			currentColor = -1;
			lastX = Integer.MIN_VALUE;
			lastY = Integer.MIN_VALUE;
			colors = ColorUtils.generateWebPalette31();

			squareSize = UI.defaultControlSize;
			margin = UI.controlMargin;
			contentsWidth = ((squareSize * 5) + (margin * 6));
			contentsHeight = ((squareSize * 31) + (margin * 32));

			setOnClickListener(this);
		}

		public boolean currentColorExists() {
			return (currentColor >= 0);
		}

		public void bringCurrentIntoView() {
			if (currentColor < 0)
				return;

			int x = (currentColor % 5);
			int y = (currentColor / 5);

			if (x != 0) x = (contentsLeft + (squareSize * x) + (margin * (x - 1)));
			if (y != 0) y = (margin + (squareSize * y) + (margin * (y - 1)));

			if (attached && colorSwatchMode && scrollView != null)
				scrollView.scrollTo(x, y);
		}

		public void setColor(int color) {
			color |= 0xff000000;
			int currentColor = -1;
			for (int i = colors.length - 1; i >= 0; i--) {
				if (color == colors[i]) {
					currentColor = i;
					break;
				}
			}
			if (this.currentColor != currentColor) {
				this.currentColor = currentColor;
				invalidate();
			}
		}

		@Override
		@ExportedProperty(category = "drawing")
		public boolean isOpaque() {
			return false;
		}

		@Override
		public boolean hasOverlappingRendering() {
			return true;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			squareSize = UI.defaultControlSize;
			margin = UI.controlMargin;
			contentsWidth = ((squareSize * 5) + (margin * 6));
			contentsHeight = ((squareSize * 31) + (margin * 32));

			int w;
			while ((w = resolveSize(contentsWidth, widthMeasureSpec)) < contentsWidth && squareSize > UI.controlMargin) {
				if (margin > UI.controlXtraSmallMargin)
					margin--;
				else
					squareSize--;
				contentsWidth = ((squareSize * 5) + (margin * 6));
				contentsHeight = ((squareSize * 31) + (margin * 32));
			}

			contentsLeft = ((w - contentsWidth) >> 1);

			setMeasuredDimension(w, contentsHeight);
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			contentsLeft = ((w - contentsWidth) >> 1);
			bringCurrentIntoView();
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(@NonNull MotionEvent event) {
			if (isEnabled() && event.getAction() == MotionEvent.ACTION_DOWN) {
				lastX = (int)event.getX();
				lastY = (int)event.getY();
			}
			return super.onTouchEvent(event);
		}

		@Override
		public void onClick(View v) {
			//we need to handle clicks in here, rather than inside performClick()
			//because if the listener is null, no sound effects are played (if they are configured)
			if (lastX != Integer.MIN_VALUE) {
				int x = lastX;
				int y = lastY;
				lastX = Integer.MIN_VALUE;
				lastY = Integer.MIN_VALUE;
				if (x < (contentsLeft + margin)) return;
				if (x >= (contentsLeft + contentsWidth - margin)) return;
				if (y < margin) y = margin;
				else if (y >= (margin + contentsHeight)) y = margin + contentsHeight - 1;
				x -= (contentsLeft + margin);
				y -= margin;
				x /= (squareSize + margin);
				y /= (squareSize + margin);
				final int currentColor = ((y * 5) + x);
				if (this.currentColor != currentColor) {
					this.currentColor = currentColor;
					invalidate();
					ColorPickerView.this.setColor(colors[currentColor], true);
				}
			}
		}

		@Override
		public void draw(Canvas canvas) {
			super.draw(canvas);
			final Rect rect = UI.rect;
			final int size = margin + squareSize;
			rect.top = margin;
			rect.bottom = size;
			int i = 0;
			for (int y = 0; y < 31; y++) {
				rect.left = contentsLeft + margin;
				rect.right = rect.left + squareSize;
				for (int x = 0; x < 5; x++, i++) {
					UI.fillRect(rect, canvas, colors[i]);
					if (i == currentColor) {
						UI.strokeRect(rect, canvas, 0xff000000, UI.strokeSize);
						rect.inset(UI.strokeSize, UI.strokeSize);
						UI.strokeRect(rect, canvas, 0xffffffff, UI.strokeSize);
						rect.inset(-UI.strokeSize, -UI.strokeSize);
					}
					rect.left += size;
					rect.right += size;
				}
				rect.top += size;
				rect.bottom += size;
			}
		}

		@Override
		protected void onAttachedToWindow() {
			attached = true;
			super.onAttachedToWindow();
		}

		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();
			attached = false;
		}
	}

	private final class ColorView extends View {
		private Bitmap bitmap;
		private final ColorUtils.HSV hsv;
		private int saturation, saturationPosition, value, valuePosition, viewWidth, viewHeight, backgroundColor;
		private boolean tracking;
		private final boolean limitHeight;

		public ColorView(Context context, boolean limitHeight) {
			super(context);

			super.setBackgroundResource(0);
			super.setDrawingCacheEnabled(false);

			hsv = new ColorUtils.HSV(0.0, 1.0, 1.0);
			backgroundColor = 0xffff0000;
			this.limitHeight = limitHeight;
			final int _100dp = UI.dpToPxI(100.0f);
			setMinimumWidth(_100dp);
			setMinimumHeight(_100dp);
		}

		public void setHue(int hue) {
			hsv.h = (double)hue / 360.0;
			backgroundColor = hsv.toRGB(false);
			invalidate();
		}

		public void setSaturation(int saturation, boolean notifyChanges) {
			if (this.saturation != saturation) {
				this.saturation = (saturation <= 0 ? 0 : (Math.min(saturation, 100)));
				saturationPosition = (((100 - this.saturation) * (viewHeight - UI.strokeSize)) / 100);
				invalidate();
				if (notifyChanges)
					onSaturationChanged(this.saturation);
			}
		}

		public void setValue(int value, boolean notifyChanges) {
			if (this.value != value) {
				this.value = (value <= 0 ? 0 : (Math.min(value, 100)));
				valuePosition = ((this.value * (viewWidth - UI.strokeSize)) / 100);
				invalidate();
				if (notifyChanges)
					onValueChanged(this.value);
			}
		}

		@Override
		@ExportedProperty(category = "drawing")
		public boolean isOpaque() {
			return true;
		}

		@Override
		public boolean hasOverlappingRendering() {
			return true;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			final int requestedWidth = MeasureSpec.getSize(widthMeasureSpec);
			final int requestedHeight = MeasureSpec.getSize(heightMeasureSpec);
			super.onMeasure(MeasureSpec.makeMeasureSpec(requestedWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((limitHeight && requestedWidth <= requestedHeight) ? requestedWidth : requestedHeight, MeasureSpec.EXACTLY));
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			if (h < 2 || w < 2)
				return;

			viewWidth = w;
			viewHeight = h;
			if (bitmap == null || bitmap.getHeight() != h) {
				if (bitmap != null)
					bitmap.recycle();
				final int[] colors = new int[w * h];
				final ColorUtils.HSV hsv = new ColorUtils.HSV(0.0, 1.0, 0.0);
				for (int y = 0; y < h; y++) {
					hsv.s = (double)((h - 1) - y) / (double)(h - 1);
					for (int x = 0; x < w; x++) {
						hsv.v = (double)x / (double)(w - 1);
						final int color = hsv.toRGB(false);
						final int r = (color >>> 16) & 0xff;
						final int other = color & 0xff;
						final int alpha = 255 - r + other;
						colors[(y * w) + x] = (alpha << 24) | (r << 16) | (r << 8) | r;
					}
				}
				bitmap = Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888);
			}
			saturationPosition = (((100 - saturation) * (viewHeight - UI.strokeSize)) / 100);
			valuePosition = ((value * (viewWidth - UI.strokeSize)) / 100);
		}

		private void trackTouchEvent(int x, int y) {
			setSaturation((100 * (viewHeight - y)) / (viewHeight - UI.strokeSize), true);
			setValue((100 * x) / (viewWidth - UI.strokeSize), true);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(@NonNull MotionEvent event) {
			if (!isEnabled())
				return false;

			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				tracking = true;
				setPressed(true);
				trackTouchEvent((int)event.getX(), (int)event.getY());
				if (getParent() != null)
					getParent().requestDisallowInterceptTouchEvent(true);
				playSoundEffect(SoundEffectConstants.CLICK);
				break;
			case MotionEvent.ACTION_MOVE:
				if (tracking)
					trackTouchEvent((int)event.getX(), (int)event.getY());
				break;
			case MotionEvent.ACTION_UP:
				setPressed(false);
				if (tracking) {
					trackTouchEvent((int)event.getX(), (int)event.getY());
					tracking = false;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				setPressed(false);
				invalidate();
				tracking = false;
				break;
			}
			return true;
		}

		@Override
		public void draw(Canvas canvas) {
			super.draw(canvas);
			if (bitmap == null)
				return;
			final Rect rect = UI.rect;
			getDrawingRect(rect);
			canvas.drawColor(backgroundColor);
			canvas.drawBitmap(bitmap, 0.0f, 0.0f, null);
			rect.left = valuePosition - UI.strokeSize;
			rect.right = valuePosition;
			UI.fillRect(rect, canvas, 0xffffffff);
			rect.left += UI.strokeSize;
			rect.right += UI.strokeSize;
			UI.fillRect(rect, canvas, 0xff000000);
			rect.left += UI.strokeSize;
			rect.right += UI.strokeSize;
			UI.fillRect(rect, canvas, 0xffffffff);

			rect.left = 0;
			rect.right = viewWidth;

			rect.top = saturationPosition - UI.strokeSize;
			rect.bottom = saturationPosition;
			UI.fillRect(rect, canvas, 0xffffffff);
			rect.top += UI.strokeSize;
			rect.bottom += UI.strokeSize;
			UI.fillRect(rect, canvas, 0xff000000);
			rect.top += UI.strokeSize;
			rect.bottom += UI.strokeSize;
			UI.fillRect(rect, canvas, 0xffffffff);

			// ;)
			rect.left = valuePosition;
			rect.top = saturationPosition - UI.strokeSize;
			rect.right = valuePosition + UI.strokeSize;
			rect.bottom = saturationPosition + (UI.strokeSize << 1);
			UI.fillRect(rect, canvas, 0xff000000);
		}

		@Override
		protected void onDetachedFromWindow() {
			if (bitmap != null) {
				bitmap.recycle();
				bitmap = null;
			}
			super.onDetachedFromWindow();
		}
	}

	private final class HueView extends View {
		private Bitmap bitmap;
		private int hue, huePosition, viewSize;
		private boolean tracking;
		private final boolean vertical;

		public HueView(Context context, boolean vertical) {
			super(context);

			super.setBackgroundResource(0);
			super.setDrawingCacheEnabled(false);

			this.vertical = vertical;
			setMinimumWidth((UI.defaultControlSize * 3) >> 2);
			setMinimumHeight((UI.defaultControlSize * 3) >> 2);
		}

		public void setHue(int hue, boolean notifyChanges) {
			if (this.hue != hue) {
				this.hue = (hue <= 0 ? 0 : (Math.min(hue, 360)));
				huePosition = ((this.hue * (viewSize - UI.strokeSize)) / 360);
				invalidate();
				if (colorView != null)
					colorView.setHue(this.hue);
				if (notifyChanges)
					onHueChanged(this.hue);
			}
		}

		@Override
		@ExportedProperty(category = "drawing")
		public boolean isOpaque() {
			return true;
		}

		@Override
		public boolean hasOverlappingRendering() {
			return true;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			if (vertical)
				widthMeasureSpec = MeasureSpec.makeMeasureSpec((UI.defaultControlSize * 3) >> 2, MeasureSpec.EXACTLY);
			else
				heightMeasureSpec = MeasureSpec.makeMeasureSpec((UI.defaultControlSize * 3) >> 2, MeasureSpec.EXACTLY);
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}

		private int[] computeColors(int size) {
			final int[] colors = new int[size];
			final ColorUtils.HSV hsv = new ColorUtils.HSV(0.0, 1.0, 1.0);
			for (int i = 0; i < size; i++) {
				hsv.h = (double)i / (double)(size - 1);
				colors[i] = hsv.toRGB(false);
			}
			return colors;
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			if (h < 2 || w < 2)
				return;

			if (vertical) {
				viewSize = h;
				if (bitmap == null || bitmap.getHeight() != h) {
					if (bitmap != null)
						bitmap.recycle();
					bitmap = Bitmap.createBitmap(computeColors(h), 1, h, Bitmap.Config.ARGB_8888);
				}
			} else {
				viewSize = w;
				if (bitmap == null || bitmap.getWidth() != w) {
					if (bitmap != null)
						bitmap.recycle();
					bitmap = Bitmap.createBitmap(computeColors(w), w, 1, Bitmap.Config.ARGB_8888);
				}
			}
			huePosition = ((hue * (viewSize - UI.strokeSize)) / 360);
		}

		private void trackTouchEvent(int position) {
			setHue((360 * position) / (viewSize - UI.strokeSize), true);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(@NonNull MotionEvent event) {
			if (!isEnabled())
				return false;

			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				tracking = true;
				setPressed(true);
				trackTouchEvent((int)(vertical ? event.getY() : event.getX()));
				if (getParent() != null)
					getParent().requestDisallowInterceptTouchEvent(true);
				playSoundEffect(SoundEffectConstants.CLICK);
				break;
			case MotionEvent.ACTION_MOVE:
				if (tracking)
					trackTouchEvent((int)(vertical ? event.getY() : event.getX()));
				break;
			case MotionEvent.ACTION_UP:
				setPressed(false);
				if (tracking) {
					trackTouchEvent((int)(vertical ? event.getY() : event.getX()));
					tracking = false;
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				setPressed(false);
				invalidate();
				tracking = false;
				break;
			}
			return true;
		}

		@Override
		public void draw(Canvas canvas) {
			super.draw(canvas);
			if (bitmap == null)
				return;
			final Rect rect = UI.rect;
			getDrawingRect(rect);
			if (vertical) {
				for (int i = rect.left; i < rect.right; i++)
					canvas.drawBitmap(bitmap, (float)i, 0.0f, null);
				rect.top += huePosition - UI.strokeSize;
				rect.bottom = huePosition;
				UI.strokeRect(rect, canvas, 0xffffffff, UI.strokeSize);
				rect.top += UI.strokeSize;
				rect.bottom += UI.strokeSize;
				UI.strokeRect(rect, canvas, 0xff000000, UI.strokeSize);
				rect.top += UI.strokeSize;
				rect.bottom += UI.strokeSize;
			} else {
				for (int i = rect.top; i < rect.bottom; i++)
					canvas.drawBitmap(bitmap, 0.0f, (float)i, null);
				rect.left = huePosition - UI.strokeSize;
				rect.right = huePosition;
				UI.strokeRect(rect, canvas, 0xffffffff, UI.strokeSize);
				rect.left += UI.strokeSize;
				rect.right += UI.strokeSize;
				UI.strokeRect(rect, canvas, 0xff000000, UI.strokeSize);
				rect.left += UI.strokeSize;
				rect.right += UI.strokeSize;
			}
			UI.strokeRect(rect, canvas, 0xffffffff, UI.strokeSize);
		}

		@Override
		protected void onDetachedFromWindow() {
			if (bitmap != null) {
				bitmap.recycle();
				bitmap = null;
			}
			super.onDetachedFromWindow();
		}
	}

	private ObservableScrollView scrollView;
	private final RelativeLayout landscapeContainer;
	private ColorSwatchView colorSwatchView;
	private ColorView colorView;
	private HueView hueView;
	private LinearLayout controlPanel;
	private BgEditText txtH, txtS, txtV, txtHTML;
	private final ColorUtils.HSV hsv = new ColorUtils.HSV();
	private int h, s, v, rgb;
	private boolean ignoreChanges, colorSwatchMode;
	private final boolean oneShot;
	private OnColorPickerViewListener listener;
	private final View parentView;

	public static void showDialog(Context context, int initialColor, View parentView, boolean oneShot, OnColorPickerViewListener listener) {
		ColorPickerView view = new ColorPickerView(context, parentView, oneShot);
		view.setColor(initialColor, true);
		//call setOnColorPickerViewListener() after setColor(), so the listener is not notified if oneShot is false
		view.setOnColorPickerViewListener(listener);
		view.setColorSwatchMode(view.colorSwatchView.currentColorExists());
		final BgDialog dialog = new BgDialog(context, view, view);
		dialog.setTitle(null);
		dialog.setNeutralButton(UI.ICON_PALETTE, context.getText(R.string.more));
		dialog.setPositiveButton(R.string.ok);
		if (oneShot)
			dialog.setNegativeButton(R.string.cancel);
		dialog.show();
	}

	public ColorPickerView(Context context) {
		this(context, null, false);
	}

	public ColorPickerView(Context context, View parentView, boolean oneShot) {
		super(context);

		setOrientation(VERTICAL);

		RelativeLayout.LayoutParams rp;
		LinearLayout.LayoutParams lp;

		this.parentView = parentView;
		this.oneShot = oneShot;

		scrollView = new ObservableScrollView(context);
		scrollView.setForegroundGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		colorSwatchView = new ColorSwatchView(context);
		scrollView.addView(colorSwatchView, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		//unfortunately, we cannot afford to waste a single pixel... :/
		final int margin = (UI.isLargeScreen ? (UI.dialogMargin << 1) : (UI.dialogMargin >> 1));
		final int internalMargin = (UI.isLargeScreen ? UI.dialogMargin : (UI.dialogMargin >> 1));
		final int textSize = (UI.isLargeScreen ? UI._18sp : UI._14sp);
		final int editViewWidthSmall = UI.measureText("_000_", textSize);
		final int editViewWidth = UI.measureText("_#000000_", textSize);

		setPadding(margin, margin, margin, margin);

		//weird... I know... but at least lint stops complaining!
		final int id = 0;

		if (UI.isLandscape || UI.isLargeScreen) {
			landscapeContainer = new RelativeLayout(context);
			landscapeContainer.setVisibility(GONE);
			addView(landscapeContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

			controlPanel = new LinearLayout(context);
			controlPanel.setOrientation(LinearLayout.VERTICAL);
			controlPanel.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
			controlPanel.setId(id + 1);

			txtH = UI.createDialogEditText(context, 0, Integer.toString(h), UI.isLargeScreen ? "H" : "HSV", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtH.setSmallContentDescription(!UI.isLargeScreen);
			txtH.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtH.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtH.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
			controlPanel.addView(txtH, lp);

			txtS = UI.createDialogEditText(context, 0, Integer.toString(s), UI.isLargeScreen ? "S" : null, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtS.setSmallContentDescription(!UI.isLargeScreen);
			txtS.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtS.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtS.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = margin;
			controlPanel.addView(txtS, lp);

			txtV = UI.createDialogEditText(context, 0, Integer.toString(v), UI.isLargeScreen ? "V" : null, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtV.setSmallContentDescription(!UI.isLargeScreen);
			txtV.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtV.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtV.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = margin;
			controlPanel.addView(txtV, lp);

			txtHTML = UI.createDialogEditText(context, 0, ColorUtils.toHexColor(rgb), "HTML", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtHTML.setSmallContentDescription(!UI.isLargeScreen);
			txtHTML.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtHTML.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtHTML.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = margin;
			controlPanel.addView(txtHTML, lp);

			rp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			landscapeContainer.addView(controlPanel, rp);

			hueView = new HueView(context, true);
			hueView.setId(id + 2);
			rp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			rp.rightMargin = internalMargin;
			rp.addRule(RelativeLayout.LEFT_OF, 1);
			rp.addRule(RelativeLayout.ALIGN_TOP, 1);
			rp.addRule(RelativeLayout.ALIGN_BOTTOM, 1);
			landscapeContainer.addView(hueView, rp);

			colorView = new ColorView(context, false);
			colorView.setId(id + 3);
			rp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			rp.rightMargin = internalMargin;
			rp.addRule(RelativeLayout.LEFT_OF, 2);
			rp.addRule(RelativeLayout.ALIGN_TOP, 1);
			rp.addRule(RelativeLayout.ALIGN_BOTTOM, 1);
			landscapeContainer.addView(colorView, rp);
		} else {
			landscapeContainer = null;

			controlPanel = new LinearLayout(context);
			controlPanel.setOrientation(LinearLayout.HORIZONTAL);
			controlPanel.setGravity(Gravity.CENTER_HORIZONTAL);
			controlPanel.setId(id + 1);

			txtH = UI.createDialogEditText(context, 0, Integer.toString(h), "H", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtH.setSmallContentDescription(!UI.isLargeScreen);
			txtH.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtH.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtH.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidthSmall, ViewGroup.LayoutParams.WRAP_CONTENT);
			controlPanel.addView(txtH, lp);

			txtS = UI.createDialogEditText(context, 0, Integer.toString(s), "S", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtS.setSmallContentDescription(!UI.isLargeScreen);
			txtS.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtS.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtS.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidthSmall, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.leftMargin = margin;
			controlPanel.addView(txtS, lp);

			txtV = UI.createDialogEditText(context, 0, Integer.toString(v), "V", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtV.setSmallContentDescription(!UI.isLargeScreen);
			txtV.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtV.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtV.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidthSmall, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.leftMargin = margin;
			controlPanel.addView(txtV, lp);

			txtHTML = UI.createDialogEditText(context, 0, ColorUtils.toHexColor(rgb), "HTML", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			txtHTML.setSmallContentDescription(!UI.isLargeScreen);
			txtHTML.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			txtHTML.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			txtHTML.addTextChangedListener(this);
			lp = new LinearLayout.LayoutParams(editViewWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.leftMargin = margin;
			controlPanel.addView(txtHTML, lp);

			setWeightSum(1.0f);

			colorView = new ColorView(context, true);
			colorView.setId(id + 3);
			lp = new LayoutParams(LayoutParams.MATCH_PARENT, 0);
			lp.weight = 1.0f;
			lp.bottomMargin = internalMargin;
			colorView.setVisibility(GONE);
			addView(colorView, lp);

			hueView = new HueView(context, false);
			hueView.setId(id + 2);
			lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			lp.bottomMargin = internalMargin;
			hueView.setVisibility(GONE);
			addView(hueView, lp);

			controlPanel.setVisibility(GONE);
			addView(controlPanel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		}
	}

	public void setColorSwatchMode(boolean colorSwatchMode) {
		this.colorSwatchMode = colorSwatchMode;
		if (scrollView != null) {
			scrollView.setVisibility(colorSwatchMode ? VISIBLE : GONE);
			if (colorSwatchMode)
				colorSwatchView.bringCurrentIntoView();
			if (landscapeContainer == null) {
				colorView.setVisibility(colorSwatchMode ? GONE : VISIBLE);
				hueView.setVisibility(colorSwatchMode ? GONE : VISIBLE);
				controlPanel.setVisibility(colorSwatchMode ? GONE : VISIBLE);
			} else {
				landscapeContainer.setVisibility(colorSwatchMode ? GONE : VISIBLE);
			}
		}
	}

	public void setColor(int color) {
		setColor(color, true);
	}

	@SuppressLint("SetTextI18n")
	private void setColor(int color, boolean updateHTML) {
		ignoreChanges = !updateHTML;
		setRGB(color);
		hsv.fromRGB(rgb);
		h = (int)((hsv.h * 360.0) + 0.5);
		s = (int)((hsv.s * 100.0) + 0.5);
		v = (int)((hsv.v * 100.0) + 0.5);
		hsv.h = (double)h / 360.0;
		hsv.s = (double)s / 100.0;
		hsv.v = (double)v / 100.0;
		if (hueView != null)
			hueView.setHue(h, false);
		if (colorView != null) {
			colorView.setSaturation(s, false);
			colorView.setValue(v, false);
		}
		ignoreChanges = true;
		if (txtH != null)
			txtH.setText(Integer.toString(h));
		if (txtS != null)
			txtS.setText(Integer.toString(s));
		if (txtV != null)
			txtV.setText(Integer.toString(v));
		ignoreChanges = false;
	}

	private void setRGB(int rgb) {
		rgb |= 0xff000000;
		if (!oneShot && listener != null && this.rgb != rgb)
			listener.onColorPicked(this, parentView, rgb);
		this.rgb = rgb;
		if (colorSwatchView != null)
			colorSwatchView.setColor(rgb);
		if (!ignoreChanges) {
			ignoreChanges = true;
			if (txtHTML != null)
				txtHTML.setText(ColorUtils.toHexColor(rgb));
			ignoreChanges = false;
		}
	}

	public void setOnColorPickerViewListener(OnColorPickerViewListener listener) {
		this.listener = listener;
	}

	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return false;
	}

	@Override
	public boolean hasOverlappingRendering() {
		return true;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case DialogInterface.BUTTON_NEUTRAL:
			setColorSwatchMode(!colorSwatchMode);
			return;
		case DialogInterface.BUTTON_POSITIVE:
			if (listener != null)
				listener.onColorPicked(this, parentView, rgb);
			break;
		}
		dialog.dismiss();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

	@Override
	public void afterTextChanged(Editable editable) {
		View focusedView = getFocusedChild();
		while (focusedView != null && !(focusedView instanceof BgEditText))
			focusedView = ((focusedView instanceof ViewGroup) ? ((ViewGroup)focusedView).getFocusedChild() : null);
		if (focusedView == null || ignoreChanges)
			return;
		if (focusedView == txtH) {
			try {
				h = Integer.parseInt(txtH.getText().toString());
				if (h < 0)
					h = 0;
				else if (h > 360)
					h = 360;
				hsv.h = (double)h / 360.0;
				setRGB(hsv.toRGB(false));
				hueView.setHue(h, false);
			} catch (Throwable ex) {
				//just ignore
			}
		} else if (focusedView == txtS) {
			try {
				s = Integer.parseInt(txtS.getText().toString());
				if (s < 0)
					s = 0;
				else if (s > 100)
					s = 100;
				hsv.s = (double)s / 100.0;
				setRGB(hsv.toRGB(false));
				colorView.setSaturation(s, false);
			} catch (Throwable ex) {
				//just ignore
			}
		} else if (focusedView == txtV) {
			try {
				v = Integer.parseInt(txtV.getText().toString());
				if (v < 0)
					v = 0;
				else if (v > 100)
					v = 100;
				hsv.v = (double)v / 100.0;
				setRGB(hsv.toRGB(false));
				colorView.setValue(v, false);
			} catch (Throwable ex) {
				//just ignore
			}
		} else if (focusedView == txtHTML) {
			String hex = txtHTML.getText().toString();
			if (hex.length() < 6)
				return;
			if (hex.charAt(0) == '#')
				hex = hex.substring(1);
			if (hex.length() != 6)
				return;
			try {
				setColor(0xff000000 | Integer.parseInt(hex, 16), false);
			} catch (Throwable ex) {
				//just ignore
			}
		}
	}

	@SuppressLint("SetTextI18n")
	private void onHueChanged(int h) {
		if (txtH != null) {
			this.h = h;
			hsv.h = (double)h / 360.0;
			setRGB(hsv.toRGB(false));
			ignoreChanges = true;
			txtH.setText(Integer.toString(h));
			ignoreChanges = false;
		}
	}

	@SuppressLint("SetTextI18n")
	private void onSaturationChanged(int s) {
		if (txtS != null) {
			this.s = s;
			hsv.s = (double)s / 100.0;
			setRGB(hsv.toRGB(false));
			ignoreChanges = true;
			txtS.setText(Integer.toString(s));
			ignoreChanges = false;
		}
	}

	@SuppressLint("SetTextI18n")
	private void onValueChanged(int v) {
		if (txtV != null) {
			this.v = v;
			hsv.v = (double)v / 100.0;
			setRGB(hsv.toRGB(false));
			ignoreChanges = true;
			txtV.setText(Integer.toString(v));
			ignoreChanges = false;
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		scrollView = null;
		colorSwatchView = null;
		colorView = null;
		hueView = null;
		controlPanel = null;
		txtH = null;
		txtS = null;
		txtV = null;
		txtHTML = null;
		listener = null;
		super.onDetachedFromWindow();
	}
}
