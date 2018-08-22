package br.com.carlosrafaelgn.fplay.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.widget.FrameLayout;

import br.com.carlosrafaelgn.fplay.util.ColorUtils;

public final class BgFrameLayout extends FrameLayout {
	private int lastMessage, messageWidth, borderColor;
	private String message;

	public BgFrameLayout(@NonNull Context context) {
		super(context);
	}

	@Override
	public int getPaddingLeft() {
		return 0;
	}

	@Override
	public int getPaddingTop() {
		return 0;
	}

	@Override
	public int getPaddingRight() {
		return 0;
	}

	@Override
	public int getPaddingBottom() {
		return 0;
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
		super.setBackground(null);
	}

	@SuppressWarnings("deprecation")
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
	public boolean isOpaque() {
		return true;
	}

	public void setMessage(int message) {
		if (lastMessage == message)
			return;
		lastMessage = message;
		if (message == 0) {
			this.message = null;
		} else {
			this.message = getContext().getText(message).toString();
			messageWidth = UI.measureText(this.message, UI._14sp);
			borderColor = ColorUtils.blend(UI.color_highlight, 0, 0.5f);
		}
		invalidate();
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		//Apparently, a few devices actually call dispatchDraw() with a null canvas...?!?!
		if (canvas == null)
			return;
		super.dispatchDraw(canvas);

		if (message == null)
			return;

		final Rect rect = UI.rect;
		getDrawingRect(rect);
		rect.top = rect.bottom - UI._14spBox - (UI.controlSmallMargin << 1);
		rect.right = rect.left + messageWidth + (UI.controlSmallMargin << 1);
		if (UI.hasBorders) {
			UI.strokeRect(rect, canvas, borderColor, UI.strokeSize);
			rect.left += UI.strokeSize;
			rect.top += UI.strokeSize;
			rect.right -= UI.strokeSize;
			rect.bottom -= UI.strokeSize;
			UI.fillRect(rect, canvas, UI.color_highlight);
			UI.drawText(canvas, message, UI.color_text_highlight, UI._14sp, rect.left + UI.controlSmallMargin - UI.strokeSize, rect.top + UI.controlSmallMargin + UI._14spYinBox - UI.strokeSize);
		} else {
			UI.fillRect(rect, canvas, UI.color_highlight);
			UI.drawText(canvas, message, UI.color_text_highlight, UI._14sp, rect.left + UI.controlSmallMargin, rect.top + UI.controlSmallMargin + UI._14spYinBox);
		}
	}
}
