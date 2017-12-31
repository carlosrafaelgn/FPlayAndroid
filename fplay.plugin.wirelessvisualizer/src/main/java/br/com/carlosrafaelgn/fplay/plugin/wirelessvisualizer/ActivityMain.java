package br.com.carlosrafaelgn.fplay.plugin.wirelessvisualizer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class ActivityMain extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", "br.com.carlosrafaelgn.fplay.plugin.wirelessvisualizer", null)));
		} catch (Throwable ex) {
			ex.printStackTrace();
		}

		finish();
	}
}
