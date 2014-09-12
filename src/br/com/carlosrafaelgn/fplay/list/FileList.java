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
package br.com.carlosrafaelgn.fplay.list;

import java.io.File;

import android.view.View;
import android.view.ViewGroup;
import br.com.carlosrafaelgn.fplay.playback.Player;
import br.com.carlosrafaelgn.fplay.ui.FileView;
import br.com.carlosrafaelgn.fplay.ui.UI;

//
//Supported Media Formats
//http://developer.android.com/guide/appendix/media-formats.html
//
public final class FileList extends BaseList<FileSt> implements FileFetcher.Listener, BaseList.BaseSectionIndexer {
	private final String[] nullSections;
	private final int[] nullSectionPositions;
	private boolean loading;
	private String path, comingFrom;
	private FileFetcher fetcher;
	private String[] sections;
	private int[] sectionPositions;
	
	public FileList() {
		super(FileSt.class);
		nullSections = new String[] { "" };
		nullSectionPositions = new int[] { 0 };
		sections = nullSections;
		sectionPositions = nullSectionPositions;
	}
	
	public boolean isLoading() {
		return loading;
	}
	
	private void loadingProcessChanged(boolean started) {
		loading = started;
		if (UI.browserActivity != null)
			UI.browserActivity.loadingProcessChanged(started);
	}
	
	public String getPath() {
		return path;
	}
	
	public void setPath(String path, String comingFrom, boolean isInTouchMode, boolean createSections) {
		if (fetcher != null)
			fetcher.cancel();
		sections = nullSections;
		sectionPositions = nullSectionPositions;
		clear();
		loadingProcessChanged(true);
		this.comingFrom = comingFrom;
		fetcher = FileFetcher.fetchFiles(path, this, true, false, isInTouchMode, createSections);
	}
	
	public void setPrivateFileType(String fileType, boolean isInTouchMode) {
		if (fetcher != null)
			fetcher.cancel();
		sections = nullSections;
		sectionPositions = nullSectionPositions;
		clear();
		loadingProcessChanged(true);
		fetcher = FileFetcher.fetchFiles(fileType, this, true, false, isInTouchMode, false);
	}
	
	public void cancel() {
		if (fetcher != null) {
			fetcher.cancel();
			fetcher = null;
			comingFrom = null;
			loadingProcessChanged(false);
		}
	}
	
	@Override
	public void onFilesFetched(FileFetcher fetcher, Throwable e) {
		if (fetcher != this.fetcher)
			return;
		try {
			if (e != null)
				UI.toast(Player.getService(), e);
			items = fetcher.files;
			count = fetcher.count;
			path = fetcher.path;
			if (count < 1 || fetcher.sections == null || fetcher.sections.length < 1) {
				sections = nullSections;
				sectionPositions = nullSectionPositions;
			} else {
				sections = fetcher.sections;
				sectionPositions = fetcher.sectionPositions;
			}
			//if (listObserver != null || fetcher.oldBrowserBehavior) {
				int p = ((/*fetcher.oldBrowserBehavior || */!fetcher.isInTouchMode) ? 0 : -1);
				if (comingFrom != null && comingFrom.length() > 0) {
					if (path == null || path.length() == 0) {
						for (int i = count - 1; i >= 0; i--) {
							if (items[i].path.equals(comingFrom)) {
								p = i;
								break;
							}
						}
					} else {
						if (!path.startsWith(File.separator)) {
							for (int i = count - 1; i >= 0; i--) {
								if (items[i].path.startsWith(comingFrom)) {
									p = i;
									break;
								}
							}
						} else {
							for (int i = count - 1; i >= 0; i--) {
								if (items[i].name.equals(comingFrom)) {
									p = i;
									break;
								}
							}
						}
					}
				}
				//there is no need for this call, as FileList does not override
				//notifyDataSetChanged to check whatHappened... also, setSelection
				//already calls notifyDataSetChanged
				//notifyDataSetChanged(p, CONTENT_ADDED);
				setSelection(p, false);
				//if (!fetcher.oldBrowserBehavior && listObserver != null && listObserver.isInTouchMode()) {
				//	setSelection(-1, false);
				//	listObserver.scrollItemToTop(p, false);
				//} else {
				//	setSelection(p, false);
				//}
			//} else {
			//	setSelection(-1, false);
			//}
		} finally {
			this.fetcher = null;
			comingFrom = null;
			loadingProcessChanged(false);
			System.gc();
		}
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final FileView view = (FileView)((convertView != null) ? convertView : UI.browserActivity.createView());
		view.setItemState(items[position], position, getItemState(position));
		return view;
	}
	
	@Override
	public boolean hasSections() {
		return (sections != nullSections);
	}
	
	@Override
	public String[] getSectionStrings() {
		return sections;
	}
	
	@Override
	public int[] getSectionPositions() {
		return sectionPositions;
	}
}
