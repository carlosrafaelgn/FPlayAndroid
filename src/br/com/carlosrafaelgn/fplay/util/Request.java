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

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Request extends AsyncTask<Object, Object, Object> {
	public interface Callback {
		void onRequestFinished(int status, String reply, Throwable ex);
	}

	// Apparently, Android already uses OkHttp internally...
	// https://stackoverflow.com/questions/26000027/does-android-use-okhttp-internally

	public static HttpURLConnection createConnection(String url, int startByteRange) throws Throwable {
		HttpURLConnection urlConnection = (HttpURLConnection)(new URL(url)).openConnection();
		urlConnection.setInstanceFollowRedirects(true);
		urlConnection.setConnectTimeout(30000);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(false);
		urlConnection.setReadTimeout(30000);
		urlConnection.setRequestMethod("GET");
		urlConnection.setUseCaches(false);
		// Apparently, adding this line causes a few spurious "unexpected end of stream" exceptions...
		//urlConnection.setRequestProperty("Connection", "close");
		if (startByteRange > 0)
			urlConnection.setRequestProperty("Range", "bytes=" + startByteRange + "-");
		return urlConnection;
	}

	public static HttpURLConnection createConnection(String url) throws Throwable {
		return createConnection(url, 0);
	}

	public static HttpURLConnection createJsonConnection(String method, String url, boolean hasBody) throws Throwable {
		HttpURLConnection urlConnection = (HttpURLConnection)(new URL(url)).openConnection();
		urlConnection.setInstanceFollowRedirects(true);
		urlConnection.setConnectTimeout(30000);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(hasBody);
		urlConnection.setReadTimeout(30000);
		urlConnection.setRequestMethod(method);
		urlConnection.setUseCaches(false);
		// Apparently, adding this line causes a few spurious "unexpected end of stream" exceptions...
		//urlConnection.setRequestProperty("Connection", "close");
		urlConnection.setRequestProperty("Accept", "application/json");
		urlConnection.setRequestProperty("Accept-Charset", "utf-8");
		return urlConnection;
	}

	public static String buildURL(String baseURL, String resource, String... nameValuePairs) {
		final StringBuilder sb = new StringBuilder(baseURL.length() + 2 + resource.length() + (nameValuePairs.length << 3));
		sb.append(baseURL);
		if (baseURL.charAt(baseURL.length() - 1) != '/' && resource.charAt(0) != '/')
			sb.append('/');
		sb.append(resource);
		try {
			if (nameValuePairs.length > 0) {
				if (resource.charAt(resource.length() - 1) != '?')
					sb.append('?');
				sb.append(URLEncoder.encode(nameValuePairs[0], "UTF-8"));
				sb.append('=');
				sb.append(URLEncoder.encode(nameValuePairs[1] == null ? "null" : nameValuePairs[1], "UTF-8"));
				for (int i = 2; i < nameValuePairs.length; i += 2) {
					sb.append('&');
					sb.append(URLEncoder.encode(nameValuePairs[i], "UTF-8"));
					sb.append('=');
					sb.append(URLEncoder.encode(nameValuePairs[i + 1] == null ? "null" : nameValuePairs[i + 1], "UTF-8"));
				}
			}
		} catch (Throwable igth) {
			// Just ignore...
		}
		return sb.toString();
	}

	public static Object get(String url) {
		return send(null, url, "GET", null, 0, false, false);
	}

	public static void get(Callback callback, String url) {
		send(callback, url, "GET", null, 0, false, false);
	}

	public static Object getRawByteArrayInputStream(String url) {
		return send(null, url, "GET", null, 0, false, true);
	}

	public static Object post(String url, String body) {
		return send(null, url, "POST", body, 0, false, false);
	}

	public static Object post(String url, byte[] body, int length) {
		return send(null, url, "POST", body, length, false, false);
	}

	public static void post(Callback callback, String url, String body) {
		send(callback, url, "POST", body, 0, false, false);
	}

	public static Object postJson(String url, Object jsonObject) {
		return send(null, url, "POST", jsonObject, 0, true, false);
	}

	public static void postJson(Callback callback, String url, Object jsonObject) {
		send(callback, url, "POST", jsonObject, 0, true, false);
	}

	public static Object getJson(String url, @NonNull Class<?> clazz) {
		final Object result = get(url);
		if (result instanceof String) {
			Object json;
			try {
				json = (new Gson()).fromJson(result.toString(), clazz);
			} catch (Throwable igth) {
				json = null;
			}
			return json;
		}
		return result;
	}

	private static Object send(String url, String method, byte[] body, int bodyLength, boolean bodyIsJson, boolean returnRawInputStream) {
		InputStream inputStream = null;
		RawByteArrayOutputStream outputStream = null;
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = createJsonConnection(method, url, body != null && bodyLength > 0);
			if (body != null && bodyLength > 0) {
				if (bodyIsJson)
					urlConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
				urlConnection.getOutputStream().write(body, 0, bodyLength);
			}
			int status = urlConnection.getResponseCode();
			if (status == 200) {
				// We must use urlConnection.getErrorStream() in order to process the data
				// sent by the server, when status != 2xx
				inputStream = urlConnection.getInputStream();
				int maxLength = urlConnection.getContentLength();
				if (maxLength <= 0)
					maxLength = 32 * 1024;
				outputStream = new RawByteArrayOutputStream(maxLength);
				byte[] buffer = new byte[1024];
				int len;
				//Java >= 0 (-1 indicates EOF)
				//C# > 0 (0 indicates EOF)
				while ((len = inputStream.read(buffer, 0, 1024)) >= 0)
					outputStream.write(buffer, 0, len);
				if (returnRawInputStream)
					return new RawByteArrayInputStream(outputStream.rawBuffer(), 0, outputStream.size());
				buffer = outputStream.rawBuffer();
				len = ((buffer[0] == (byte)0xEF && buffer[1] == (byte)0xBB && buffer[2] == (byte)0xBF) ? 3 : 0);
				return new String(outputStream.rawBuffer(), len, outputStream.size() - len);
			}
			return status;
		} catch (Throwable igth) {
			return igth;
		} finally {
			try {
				if (urlConnection != null)
					urlConnection.disconnect();
			} catch (Throwable igth) {
				// Just ignore...
			}
			try {
				if (outputStream != null)
					outputStream.close();
			} catch (Throwable igth) {
				// Just ignore...
			}
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (Throwable igth) {
				// Just ignore...
			}
			System.gc();
		}
	}

	private static Object send(Callback callback, String url, String method, Object body, int bodyLengthWhenByteArray, boolean bodyIsJson, boolean returnRawInputStream) {
		try {
			if (body != null) {
				if (method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("DELETE"))
					throw new IllegalArgumentException("method");
			} else {
				if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("DELETE"))
					throw new IllegalArgumentException("body");
			}

			byte[] bbody = null;
			if (body != null) {
				if (body instanceof byte[]) {
					bbody = (byte[])body;
				} else {
					bbody = (bodyIsJson ? (new Gson()).toJson(body) : body.toString()).getBytes();
					bodyLengthWhenByteArray = bbody.length;
				}
			}

			if (callback == null)
				return send(url, method, bbody, bodyLengthWhenByteArray, bodyIsJson, returnRawInputStream);

			(new Request(callback)).execute(url, method, bbody, bodyLengthWhenByteArray, bodyIsJson, returnRawInputStream);

			return null;
		} catch (Throwable igth) {
			return igth;
		}
	}

	private Callback callback;

	private Request(Callback callback) {
		this.callback = callback;
	}

	@Override
	protected void onPostExecute(Object o) {
		if (o instanceof Throwable)
			callback.onRequestFinished(0, null, (Throwable)o);
		else if (o instanceof String)
			callback.onRequestFinished(200, o.toString(), null);
		else
			callback.onRequestFinished((Integer)o, null, null);
		callback = null;
	}

	@Override
	protected Object doInBackground(Object... objects) {
		return send(objects[0].toString(), objects[1].toString(), (byte[])objects[2], (int)objects[3], (boolean)objects[4], (boolean)objects[5]);
	}
}
