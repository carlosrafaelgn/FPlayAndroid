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
package br.com.carlosrafaelgn.fplay.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

public final class SafeURLSpan extends URLSpan {
	private final int color;
	private final boolean underlineText;

	public SafeURLSpan(String url, int color, boolean underlineText) {
		super(url);
		this.color = color;
		this.underlineText = underlineText;
	}

	@Override
	public void updateDrawState(@NonNull TextPaint ds) {
		super.updateDrawState(ds);
		if (color != 0)
			ds.setColor(color);
		ds.setUnderlineText(underlineText);
	}

	@Override
	public void onClick(@NonNull View widget) {
		try {
			final Uri uri = Uri.parse(getURL());
			final Context context = widget.getContext();
			final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			if (context != null) {
				intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
				context.startActivity(intent);
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static CharSequence parseSafeHtml(CharSequence html) {
		return replaceURLSpans(Html.fromHtml(html.toString()), 0, true);
	}

	public static CharSequence parseSafeHtml(CharSequence html, int color, boolean underlineText) {
		return replaceURLSpans(Html.fromHtml(html.toString()), color, underlineText);
	}

	public static CharSequence replaceURLSpans(CharSequence text, int color, boolean underlineText) {
		if (text instanceof Spannable) {
			final Spannable s = (Spannable)text;
			final URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
			if (spans != null && spans.length > 0) {
				for (int i = spans.length - 1; i >= 0; i--) {
					final URLSpan span = spans[i];
					final int start = s.getSpanStart(span);
					final int end = s.getSpanEnd(span);
					final int flags = s.getSpanFlags(span);
					s.removeSpan(span);
					s.setSpan(new SafeURLSpan(span.getURL(), color, underlineText), start, end, flags);
				}
			}
		}
		return text;
	}
}
