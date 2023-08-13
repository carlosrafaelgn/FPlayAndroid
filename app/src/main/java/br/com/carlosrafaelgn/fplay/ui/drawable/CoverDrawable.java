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
package br.com.carlosrafaelgn.fplay.ui.drawable;

import android.graphics.Canvas;

import br.com.carlosrafaelgn.fplay.ui.UI;
import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class CoverDrawable {
	private static final int[] COLORS = ColorUtils.generateWebPalette31();
	private static final String[] ICONS = new String[] {
		UI.ICON_HOME,
		UI.ICON_MIC,
		UI.ICON_FAVORITE_OFF,
		UI.ICON_VOLUME3,
		UI.ICON_PLAY,
		UI.ICON_SHARE,
		UI.ICON_FLAT,
		UI.ICON_FPLAY,
		UI.ICON_CLOCK,
		UI.ICON_EARPHONE,
		UI.ICON_VISUALIZER24,
		UI.ICON_RADIO,
		UI.ICON_SHUFFLE24,
		UI.ICON_FADE,
		UI.ICON_REPEAT24,
		UI.ICON_TRANSPARENT
	};

	public static void drawCover(Canvas canvas, String name, int left, int top, int size) {
		final int right = left + size;
		final int bottom = top + size;
		final int size2 = size >> 1;
		final int size4 = size >> 2;
		final int size3_4 = size - size4;
		final int hash = name.hashCode();

		//color (1 bit + 5 bits + 2 bits = 8 bits)
		final boolean darkBackground = ((hash & 1) != 0);
		int baseColor = (hash >> 1) & 31;
		if (baseColor >= 30)
			baseColor = 29;
		final int tmp, tmp2;
		UI.fillPaint.setColor(COLORS[(tmp = ((baseColor + 1) * 5)) + (tmp2 = ((hash >> 6) & 1)) + (darkBackground ? 3 : 0)]);
		final int background2 = COLORS[tmp + 1 - tmp2 + (darkBackground ? 3 : 0)];

		//background (3 bits)
		switch (((hash >> 8) & 7)) {
		case 0:
			canvas.drawRect(left, top, right, bottom, UI.fillPaint);
			break;
		case 1:
			canvas.drawRect(left, top, left + size2, bottom, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left + size2, top, right, bottom, UI.fillPaint);
			break;
		case 2:
			canvas.drawRect(left, top, left + size4, bottom, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left + size4, top, right, bottom, UI.fillPaint);
			break;
		case 3:
			canvas.drawRect(left, top, left + size3_4, bottom, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left + size3_4, top, right, bottom, UI.fillPaint);
			break;
		case 4:
			canvas.drawRect(left, top, right, top + size2, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left, top + size2, right, bottom, UI.fillPaint);
			break;
		case 5:
			canvas.drawRect(left, top, right, top + size4, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left, top + size4, right, bottom, UI.fillPaint);
			break;
		case 6:
			canvas.drawRect(left, top, right, top + size3_4, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left, top + size3_4, right, bottom, UI.fillPaint);
			break;
		default:
			canvas.drawRect(left, top, left + size2, top + size2, UI.fillPaint);
			canvas.drawRect(left + size2, top + size2, right, bottom, UI.fillPaint);
			UI.fillPaint.setColor(background2);
			canvas.drawRect(left + size2, top, right, top + size2, UI.fillPaint);
			canvas.drawRect(left, top + size2, left + size2, bottom, UI.fillPaint);
			break;
		}

		//foreground (4 bits)
		TextIconDrawable.drawIcon(canvas, ICONS[(hash >> 11) & 15], left + size4, top + size4, size2, COLORS[tmp + (darkBackground ? 0 : 4)]);

		//border (3 bits)
		final int thickness = size >> 5;
		UI.fillPaint.setColor(((hash >> 15) & 1) != 1 ? 0xffffffff : 0xff000000);
		switch (((hash >> 15) & 7)) {
		case 0:
		case 3:
			//no border
			break;
		case 1:
		case 6:
			canvas.drawRect(left, top, left + thickness, bottom, UI.fillPaint);
			canvas.drawRect(right - thickness, top, right, bottom, UI.fillPaint);
			break;
		case 2:
		case 5:
			canvas.drawRect(left, top, right, top + thickness, UI.fillPaint);
			canvas.drawRect(left, bottom - thickness, right, bottom, UI.fillPaint);
			canvas.drawRect(left, top + thickness, left + thickness, bottom - thickness, UI.fillPaint);
			canvas.drawRect(right - thickness, top + thickness, right, bottom - thickness, UI.fillPaint);
			break;
		default:
			canvas.drawRect(left, top, right, top + thickness, UI.fillPaint);
			canvas.drawRect(left, bottom - thickness, right, bottom, UI.fillPaint);
			break;
		}
	}
}
