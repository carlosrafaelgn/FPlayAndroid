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
package br.com.carlosrafaelgn.fplay;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import br.com.carlosrafaelgn.fplay.activity.ActivityHost;
import br.com.carlosrafaelgn.fplay.list.Song;
import br.com.carlosrafaelgn.fplay.playback.Player;

public final class WidgetMain extends AppWidgetProvider {
	private static AppWidgetManager appWidgetManager;
	private static ComponentName widgetComponent;
	
	private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, boolean onlyPauseChanged) {
		final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.main_widget);
		final Song s = Player.getCurrentSong();
		
		if (Player.isCurrentSongPreparing())
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.loading));
		else if (s == null)
			views.setTextViewText(R.id.lblTitle, context.getText(R.string.nothing_playing));
		else
			views.setTextViewText(R.id.lblTitle, s.title);
		
		views.setImageViewResource(R.id.btnPlay, Player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);		
		
		Intent intent = new Intent(context, ActivityHost.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		views.setOnClickPendingIntent(R.id.lblTitle, PendingIntent.getActivity(context, 0, intent, 0));
		
		intent = new Intent(context, Player.class);
		intent.setAction(Player.ACTION_PREVIOUS);
		views.setOnClickPendingIntent(R.id.btnPrev, PendingIntent.getService(context, 0, intent, 0));
		
		intent = new Intent(context, Player.class);
		intent.setAction(Player.ACTION_PLAY_PAUSE);
		views.setOnClickPendingIntent(R.id.btnPlay, PendingIntent.getService(context, 0, intent, 0));
		
		intent = new Intent(context, Player.class);
		intent.setAction(Player.ACTION_NEXT);
		views.setOnClickPendingIntent(R.id.btnNext, PendingIntent.getService(context, 0, intent, 0));
		
		appWidgetManager.updateAppWidget(appWidgetId, views);
	}
	
	public static void updateWidgets(Context context, boolean onlyPauseChanged) {
		if (appWidgetManager == null)
			appWidgetManager = AppWidgetManager.getInstance(context);
		if (widgetComponent == null)
			widgetComponent = new ComponentName(context, WidgetMain.class);
		final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
		if (appWidgetIds == null)
			return;
		for (int i = appWidgetIds.length - 1; i >= 0; i--)
			updateAppWidget(context, appWidgetManager, appWidgetIds[i], onlyPauseChanged);
	}
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		for (int i = appWidgetIds.length - 1; i >= 0; i--)
			updateAppWidget(context, appWidgetManager, appWidgetIds[i], false);
	}
}
