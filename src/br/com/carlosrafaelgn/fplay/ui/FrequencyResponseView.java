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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;

public final class FrequencyResponseView extends View {
	private double[] gainsDB;
	private int[] divisions, divisionLabelOffsets;
	private String[] divisionLabels;
	private float gainStep;
	private int viewWidth, usableHeight, y0, textSize, textBox, textYInBox, divisionWidth;
	private final Paint paint;

	public FrequencyResponseView(Context context) {
		super(context);

		paint = new Paint();
		paint.setColor(UI.color_text_listitem);
		paint.setDither(false);
		paint.setAntiAlias(true);
		paint.setStrokeCap(Paint.Cap.BUTT);
		paint.setStrokeWidth(UI.dpToPxI(2));
		paint.setStyle(Paint.Style.STROKE);

		super.setBackgroundResource(0);
		super.setDrawingCacheEnabled(false);

		setMinimumWidth(UI.defaultControlSize);
		setMinimumHeight(UI.defaultControlSize << 1);
	}

	public void setValues(double[] frequencies, double[] gainsDB) {
		this.gainsDB = gainsDB;

		if (frequencies != null) {
			if (((frequencies.length - 1) & 3) != 0)
				throw new IllegalArgumentException("(frequencies.length - 1) must be divisible by 4");
			if (frequencies.length != gainsDB.length)
				throw new IllegalArgumentException("frequencies.length must be equal to gainsDB.length");

			gainStep = (float)viewWidth / (float)gainsDB.length;

			final int divisionCount = ((frequencies.length - 1) >> 2) + 1;
			if (divisionCount > 1)
				divisionWidth = viewWidth / (divisionCount - 1);

			if (divisions == null || divisions.length != divisionCount) {
				divisions = new int[divisionCount];
				divisionLabelOffsets = new int[divisionCount];
				divisionLabels = new String[divisionCount];

				for (int i = 0; i < divisionCount; i++) {
					final int f = (int)frequencies[i << 2];
					divisionLabels[i] = ((f < 1000) ? Integer.toString(f) : ((f / 1000) + "k"));
				}

				if (textSize > 0) {
					final int lastDivision = divisionLabels.length - 1;
					divisionLabelOffsets[0] = -UI.strokeSize;
					for (int i = 1; i < lastDivision; i++)
						divisionLabelOffsets[i] = UI.measureText(divisionLabels[i], textSize) >> 1;
					divisionLabelOffsets[lastDivision] = UI.measureText(divisionLabels[lastDivision], textSize) + UI.strokeSize;
				}
			}
		}
		invalidate();
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
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(UI.defaultControlSize << 1, MeasureSpec.EXACTLY));
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (h < 2 || w < 2)
			return;

		viewWidth = w;

		textSize = h / 10;
		if (textSize < UI._4dp)
			textSize = UI._4dp;

		UI.textPaint.setTextSize(textSize);
		final Paint.FontMetrics fm = UI.textPaint.getFontMetrics();
		textBox = (int)(fm.descent - fm.ascent + 0.5f);
		textYInBox = textBox - (int)(fm.descent);

		usableHeight = h - textBox;

		y0 = (usableHeight - UI.strokeSize) >> 1;

		if (divisions != null && divisions.length > 1)
			divisionWidth = viewWidth / (divisions.length - 1);

		if (divisionLabels != null) {
			gainStep = (float)w / (float)gainsDB.length;

			final int lastDivision = divisionLabels.length - 1;
			divisionLabelOffsets[0] = -UI.strokeSize;
			for (int i = 1; i < lastDivision; i++)
				divisionLabelOffsets[i] = UI.measureText(divisionLabels[i], textSize) >> 1;
			divisionLabelOffsets[lastDivision] = UI.measureText(divisionLabels[lastDivision], textSize) + UI.strokeSize;
		}
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);

		final Rect rect = UI.rect;
		rect.left = UI.strokeSize;
		rect.top = 0;
		rect.right = viewWidth - UI.strokeSize;
		rect.bottom = UI.strokeSize;
		UI.fillRect(rect, canvas, UI.color_focused);
		rect.top = usableHeight - UI.strokeSize;
		rect.bottom = usableHeight;
		UI.fillRect(rect, canvas, UI.color_focused);
		rect.top = y0;
		rect.bottom = y0 + UI.strokeSize;
		UI.fillRect(rect, canvas, UI.color_focused);
		rect.left = viewWidth - UI.strokeSize;
		rect.top = 0;
		rect.right = viewWidth;
		rect.bottom = usableHeight;
		UI.fillRect(rect, canvas, UI.color_text_listitem);
		rect.left = 0;
		rect.right = UI.strokeSize;
		UI.fillRect(rect, canvas, UI.color_text_listitem);

		UI.drawText(canvas, "+15", UI.color_text_listitem, textSize, UI.controlSmallMargin, UI.strokeSize + textYInBox);
		UI.drawText(canvas, "+0", UI.color_text_listitem, textSize, UI.controlSmallMargin, y0 + UI.strokeSize + textYInBox);
		UI.drawText(canvas, "-15", UI.color_text_listitem, textSize, UI.controlSmallMargin, usableHeight - UI.strokeSize - textBox + textYInBox);

		if (divisions == null)
			return;

		final int lastDivision = divisions.length - 1;

		rect.left = 0;
		rect.top = 0;
		rect.right = UI.strokeSize;
		rect.bottom = usableHeight;
		UI.drawText(canvas, divisionLabels[0], UI.color_text_listitem, textSize, rect.left - divisionLabelOffsets[0], rect.bottom + textYInBox);
		for (int i = 1; i < lastDivision; i++) {
			rect.left += divisionWidth;
			rect.right += divisionWidth;
			UI.fillRect(rect, canvas, UI.color_text_listitem);
			UI.drawText(canvas, divisionLabels[i], UI.color_text_listitem, textSize, rect.left - divisionLabelOffsets[i], rect.bottom + textYInBox);
		}
		UI.drawText(canvas, divisionLabels[lastDivision], UI.color_text_listitem, textSize, viewWidth - divisionLabelOffsets[lastDivision], rect.bottom + textYInBox);

		if (gainsDB != null) {
			final int lastGain = gainsDB.length - 1;
			final double multiplier = (double)y0 / -15;
			float usableHeightf = (float)usableHeight,
				lastX = 0.0f,
				lastY = Math.max(0.0f, Math.min(usableHeightf, (float)(y0 + (gainsDB[0] * multiplier))));
			for (int i = 1; i < lastGain; i++) {
				final float x = i * gainStep,
					y = Math.max(0.0f, Math.min(usableHeightf, (float)(y0 + (gainsDB[i] * multiplier))));
				canvas.drawLine(lastX, lastY, x, y, paint);
				lastX = x;
				lastY = y;
			}
			canvas.drawLine(lastX, lastY, (float)viewWidth, Math.max(0.0f, Math.min(usableHeightf, (float)(y0 + (gainsDB[lastGain] * multiplier)))), paint);
		}
	}
}
