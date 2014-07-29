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
package br.com.carlosrafaelgn.fplay.activity;

import android.app.Application;
import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import br.com.carlosrafaelgn.fplay.ui.CustomContextMenu;
import br.com.carlosrafaelgn.fplay.ui.UI;

public abstract class ClientActivity implements MenuItem.OnMenuItemClickListener, OnCreateContextMenuListener {
	ActivityHost activity;
	ClientActivity previousActivity;
	int requestCode;
	boolean finished;
	
	public final int getDecorViewWidth() {
		final int w = activity.getWindow().getDecorView().getWidth();
		return ((w > 0) ? w : UI.usableScreenWidth);
	}
	
	public final int getDecorViewHeight() {
		final int h = activity.getWindow().getDecorView().getHeight();
		return ((h > 0) ? h : UI.usableScreenHeight);
	}
	
	public final void addWindowFlags(int flags) {
		activity.getWindow().addFlags(flags);
	}
	
	public final void setWindowFlags(int flags, int mask) {
		activity.getWindow().setFlags(flags, mask);
	}
	
	public final void clearWindowFlags(int flags) {
		activity.getWindow().clearFlags(flags);
	}
	
	public final Application getApplication() {
		return activity.getApplication();
	}
	
	public final ActivityHost getHostActivity() {
		return activity;
	}
	
	public final void setContentView(int layoutResID) {
		activity.setContentView(layoutResID);
	}
	
	public final void setContentView(View view) {
		activity.setContentView(view);
	}
	
	public final View findViewById(int id) {
		return activity.findViewById(id);
	}
	
	public final boolean isTopActivity() {
		return (activity.getTopActivity() == this);
	}
	
	public final ClientActivity getTopActivity() {
		return activity.getTopActivity();
	}
	
	public final ClientActivity getPreviousActivity() {
		return previousActivity;
	}
	
	public final void startActivity(ClientActivity activity) {
		this.activity.startActivity(activity);
	}
	
	public final void startActivity(ClientActivity activity, int requestCode) {
		activity.requestCode = requestCode;
		this.activity.startActivity(activity);
	}
	
	public final void finish(int code) {
		activity.finishActivity(this, null, code);
	}
	
	public final void finish() {
		activity.finishActivity(this, null, 0);
	}
	
	public final void finishAndStartNewActivity(int code, ClientActivity activity, int requestCode) {
		activity.requestCode = requestCode;
		this.activity.finishActivity(this, activity, code);
	}
	
	public final void openContextMenu(View view) {
		CustomContextMenu.openContextMenu(view, this);
	}
	
	public final void setExitOnDestroy(boolean exitOnDestroy) {
		activity.setExitOnDestroy(exitOnDestroy);
	}
	
	public final CharSequence getText(int resId) {
		return activity.getText(resId);
	}
	
	public final Resources getResources() {
		return activity.getResources();
	}
	
	public void activityFinished(ClientActivity activity, int requestCode, int code) {
	}
	
	public int getDesiredWindowColor() {
		return 0;
	}
	
	public View getNullContextMenuView() {
		return null;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		return false;
	}
	
	protected boolean onBackPressed() {
		return false;
	}
	
	protected void onCreate() {
	}
	
	protected void onCreateLayout(boolean firstCreation) {
	}
	
	protected void onResume() {
	}
	
	protected void onOrientationChanged() {
	}
	
	protected void onPause() {
	}
	
	protected void onCleanupLayout() {
	}
	
	protected void onDestroy() {
	}
}
