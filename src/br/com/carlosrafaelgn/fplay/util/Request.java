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

	public static class CloseToken {
		private final Object token = new Object();
		private boolean closed;
		private HttpURLConnection urlConnection;

		private boolean setUrlConnection(HttpURLConnection urlConnection) {
			synchronized (token) {
				if (!closed) {
					this.urlConnection = urlConnection;
					return true;
				}
				return false;
			}
		}

		private void clearUrlConnection() {
			synchronized (token) {
				closed = true;
				urlConnection = null;
			}
		}

		public void close() {
			synchronized (token) {
				closed = true;
				if (urlConnection != null) {
					//Apparently, Android already uses OkHttp internally...
					//https://stackoverflow.com/questions/26000027/does-android-use-okhttp-internally
					try {
						urlConnection.disconnect();
					} catch (Throwable ex) {
						//just ignore
					}

					//On old API's, disconnect() does not abort ongoing connection attempts,
					//it just aborts ongoing reads or writes...

					//Since connection timeout has been reduced to 10 seconds, let's just leave
					//the calling thread wait for the connection to timeout naturally, instead of
					//force closing it (although the following code works, and has been tested,
					//I'd rather not use it, unless it proves really necessary someday)

					////https://android.googlesource.com/platform/external/okhttp/+/2946265382960fbd8e2bd765e40e3bc7016e960e/src/main/java/com/squareup/okhttp/internal/http/HttpURLConnectionImpl.java
					////https://android.googlesource.com/platform/external/okhttp/+/2946265382960fbd8e2bd765e40e3bc7016e960e/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java
					////https://android.googlesource.com/platform/external/okhttp/+/2946265382960fbd8e2bd765e40e3bc7016e960e/src/main/java/com/squareup/okhttp/Connection.java
					//
					////https://android.googlesource.com/platform/external/okhttp/+/master/okhttp/src/main/java/com/squareup/okhttp/OkHttpClient.java
					////https://android.googlesource.com/platform/external/okhttp/+/master/okhttp/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java
					////https://android.googlesource.com/platform/external/okhttp/+/master/okhttp/src/main/java/com/squareup/okhttp/Connection.java
					//
					////This is not pretty, I know... But this is an attempt to (try to) quickly abort
					////ongoing connection attempts
					//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					//	try {
					//		//protected HttpEngine httpEngine
					//		Field field = urlConnection.getClass().getDeclaredField("httpEngine");
					//		field.setAccessible(true);
					//		Object httpEngine = field.get(urlConnection);
					//		if (httpEngine != null) {
					//			//public Connection getConnection()
					//			Method method = httpEngine.getClass().getDeclaredMethod("getConnection");
					//			method.setAccessible(true);
					//			Object connection = method.invoke(httpEngine);
					//			if (connection != null) {
					//				//public void close()
					//				method.setAccessible(true);
					//				method = connection.getClass().getDeclaredMethod("close");
					//				method.invoke(connection);
					//			}
					//		}
					//	} catch (Throwable ex) {
					//		//just ignore
					//	}
					//}

					urlConnection = null;
				}
			}
		}
	}

	public static HttpURLConnection createConnection(String url, int startByteRange) throws Throwable {
		HttpURLConnection urlConnection = (HttpURLConnection)(new URL(url)).openConnection();
		urlConnection.setInstanceFollowRedirects(true);
		urlConnection.setConnectTimeout(10000);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(false);
		urlConnection.setReadTimeout(20000);
		urlConnection.setRequestMethod("GET");
		urlConnection.setUseCaches(false);
		//Apparently, adding this line causes a few spurious "unexpected end of stream" exceptions...
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
		urlConnection.setConnectTimeout(10000);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(hasBody);
		urlConnection.setReadTimeout(20000);
		urlConnection.setRequestMethod(method);
		urlConnection.setUseCaches(false);
		//Apparently, adding this line causes a few spurious "unexpected end of stream" exceptions...
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
		} catch (Throwable ex) {
			//just ignore
		}
		return sb.toString();
	}

	public static Object get(String url, CloseToken closeToken) {
		return send(null, url, "GET", null, 0, false, false, closeToken);
	}

	public static void get(Callback callback, String url, CloseToken closeToken) {
		send(callback, url, "GET", null, 0, false, false, closeToken);
	}

	public static Object getRawByteArrayInputStream(String url, CloseToken closeToken) {
		return send(null, url, "GET", null, 0, false, true, closeToken);
	}

	public static Object post(String url, String body, CloseToken closeToken) {
		return send(null, url, "POST", body, 0, false, false, closeToken);
	}

	public static Object post(String url, byte[] body, int length, CloseToken closeToken) {
		return send(null, url, "POST", body, length, false, false, closeToken);
	}

	public static void post(Callback callback, String url, String body, CloseToken closeToken) {
		send(callback, url, "POST", body, 0, false, false, closeToken);
	}

	public static Object postJson(String url, Object jsonObject, CloseToken closeToken) {
		return send(null, url, "POST", jsonObject, 0, true, false, closeToken);
	}

	public static void postJson(Callback callback, String url, Object jsonObject, CloseToken closeToken) {
		send(callback, url, "POST", jsonObject, 0, true, false, closeToken);
	}

	public static Object getJson(String url, @NonNull Class<?> clazz, CloseToken closeToken) {
		final Object result = get(url, closeToken);
		if (result instanceof String) {
			Object json;
			try {
				json = (new Gson()).fromJson(result.toString(), clazz);
			} catch (Throwable ex) {
				json = null;
			}
			return json;
		}
		return result;
	}

	private static Object send(String url, String method, byte[] body, int bodyLength, boolean bodyIsJson, boolean returnRawInputStream, CloseToken closeToken) {
		InputStream inputStream = null;
		RawByteArrayOutputStream outputStream = null;
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = createJsonConnection(method, url, body != null && bodyLength > 0);
			if (closeToken != null && !closeToken.setUrlConnection(urlConnection))
				return -1;
			if (body != null && bodyLength > 0) {
				if (bodyIsJson)
					urlConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
				urlConnection.setFixedLengthStreamingMode(bodyLength);
				urlConnection.getOutputStream().write(body, 0, bodyLength);
			}
			int status = urlConnection.getResponseCode();
			if ((status >= 100 && status < 200) || status == 204) {
				try {
					inputStream = urlConnection.getInputStream();
				} catch (Throwable ex) {
					//just ignore
				}
			} else if (status >= 200 && status <= 299) {
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
			} else {
				//We must use urlConnection.getErrorStream() in order to process the data
				//sent by the server, in the presence of errors
				try {
					inputStream = urlConnection.getErrorStream();
				} catch (Throwable ex) {
					//just ignore
				}
			}
			return status;
		} catch (Throwable ex) {
			return ex;
		} finally {
			//We cannot use CloseToken.close() here because disconnect() does not
			//abort ongoing connection attempts. Therefore, even if close() had been
			//called, a connection could have been estabilished afterwards...
			if (closeToken != null)
				closeToken.clearUrlConnection();
			try {
				if (urlConnection != null)
					urlConnection.disconnect();
			} catch (Throwable ex) {
				//just ignore
			}
			try {
				if (outputStream != null)
					outputStream.close();
			} catch (Throwable ex) {
				//just ignore
			}
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (Throwable ex) {
				//just ignore
			}
			System.gc();
		}
	}

	private static Object send(Callback callback, String url, String method, Object body, int bodyLengthWhenByteArray, boolean bodyIsJson, boolean returnRawInputStream, CloseToken closeToken) {
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
				return send(url, method, bbody, bodyLengthWhenByteArray, bodyIsJson, returnRawInputStream, closeToken);

			(new Request(callback)).execute(url, method, bbody, bodyLengthWhenByteArray, bodyIsJson, returnRawInputStream, closeToken);

			return null;
		} catch (Throwable ex) {
			return ex;
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
		return send(objects[0].toString(), objects[1].toString(), (byte[])objects[2], (int)objects[3], (boolean)objects[4], (boolean)objects[5], (CloseToken)objects[6]);
	}
}
