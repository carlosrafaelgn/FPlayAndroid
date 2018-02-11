package org.nanohttpd.webserver;

/*
 * #%L
 * NanoHttpd-Webserver
 * %%
 * Copyright (C) 2012 - 2015 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

// This is a modified version suited for my needs :)

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.IStatus;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

public final class FileSystemWebServer extends NanoHTTPD {
	public interface FileHandler {
		boolean exists();
		String etag();
		long length();
		InputStream createInputStream() throws IOException;
	}

	public interface FileHandlerFactory {
		FileHandler createIfCanHandle(String uri);
	}

	private static final class FileSystemFileHandler implements FileHandler {
		private final String uri;
		private File file;

		FileSystemFileHandler(String uri) {
			this.uri = uri;
		}

		@Override
		public String toString() {
			return uri;
		}

		@Override
		public boolean exists() {
			if (file == null) {
				try {
					file = new File(uri);
					if (!file.exists() || file.isDirectory()) {
						file = null;
						return false;
					}
				} catch (Throwable ex) {
					file = null;
					return false;
				}
			}
			return true;
		}

		@Override
		public String etag() {
			//return Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());
			return (file == null ? "" : Long.toHexString(file.lastModified() ^ file.length()));
		}

		@Override
		public long length() {
			return (file == null ? 0 : file.length());
		}

		@Override
		public InputStream createInputStream() throws IOException {
			return (file == null ? null : new FileInputStream(file));
		}
	}

	private static final String CORS = "*";

	private final static String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS, HEAD";

	private final static int MAX_AGE = 42 * 60 * 60;

	// explicitly relax visibility to package for tests purposes
	private final static String DEFAULT_ALLOWED_HEADERS = "origin,accept,content-type";

	private final FileHandlerFactory fileHandlerFactory;

	static {
		MIME_TYPES = new HashMap<>();
		MIME_TYPES.put("jpg", "image/jpeg");
		MIME_TYPES.put("jpeg", "image/jpeg");
		MIME_TYPES.put("png", "image/png");
		MIME_TYPES.put("css", "text/css");
		MIME_TYPES.put("js", "text/javascript");
		MIME_TYPES.put("htm", "text/html");
		MIME_TYPES.put("html", "text/html");

		MIME_TYPES.put("m3u", "audio/mpeg-url");
		MIME_TYPES.put("m3u8", "application/vnd.apple.mpegurl");
		MIME_TYPES.put("json", "application/json");

		MIME_TYPES.put("3gp", "audio/3gpp");
		MIME_TYPES.put("3gpp", "audio/3gpp");
		MIME_TYPES.put("3ga", "audio/3ga");
		MIME_TYPES.put("3gpa", "audio/3ga");
		MIME_TYPES.put("mp4", "audio/mp4");
		MIME_TYPES.put("m4a", "audio/mp4");
		MIME_TYPES.put("aac", "audio/aac");
		MIME_TYPES.put("mp3", "audio/mpeg");
		MIME_TYPES.put("mid", "audio/mid");
		MIME_TYPES.put("rmi", "audio/mid");
		MIME_TYPES.put("xmf", "audio/mobile-xmf");
		MIME_TYPES.put("mxmf", "audio/mobile-xmf");
		MIME_TYPES.put("rtttl", "audio/x-rtttl");
		MIME_TYPES.put("rtx", "audio/rtx");
		MIME_TYPES.put("ota", "audio/ota"); //???
		MIME_TYPES.put("imy", "audio/imy"); //???
		MIME_TYPES.put("ogg", "audio/ogg");
		MIME_TYPES.put("oga", "audio/ogg");
		MIME_TYPES.put("wav", "audio/wav"); //audio/vnd.wave ?
		MIME_TYPES.put("mka", "audio/x-matroska");
		MIME_TYPES.put("flac", "audio/flac");
	}

	public FileSystemWebServer(String host, int port) {
		super(host, port);
		fileHandlerFactory = null;
	}

	public FileSystemWebServer(String host, int port, FileHandlerFactory fileHandlerFactory) {
		super(host, port);
		this.fileHandlerFactory = fileHandlerFactory;
	}

	private static Response getForbiddenResponse(String s) {
		return Response.newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: " + s);
	}

	private static Response getNotFoundResponse() {
		return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
	}

	private static Response newFixedLengthResponse(IStatus status, String mimeType, String message) {
		Response response = Response.newFixedLengthResponse(status, mimeType, message);
		response.addHeader("Accept-Ranges", "bytes");
		return response;
	}

	@SuppressWarnings("deprecation")
	@Override
	public Response serve(IHTTPSession session) {
		final Map<String, String> headers = session.getHeaders();
		//Map<String, String> parms = session.getParms();
		String uri = session.getUri();

		// First let's handle CORS OPTION query
		Response r;
		if (Method.OPTIONS.equals(session.getMethod())) {
			r = Response.newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, null, 0);
		} else {
			// Remove URL arguments
			if (uri.indexOf('?') >= 0)
				uri = uri.substring(0, uri.indexOf('?'));
			if ((uri = uri.trim()).length() == 0)
				return getNotFoundResponse();
			if (uri.charAt(0) != '/')
				uri = "/" + uri;

			FileHandler fileHandler;
			if (fileHandlerFactory != null) {
				if ((fileHandler = fileHandlerFactory.createIfCanHandle(uri)) == null)
					fileHandler = new FileSystemFileHandler(uri);
			} else {
				fileHandler = new FileSystemFileHandler(uri);
			}

			if (!fileHandler.exists())
				return getNotFoundResponse();

			String mimeTypeForFile = getMimeTypeForFile(uri);
			r = serveFile(headers, fileHandler, mimeTypeForFile);
			if (r == null)
				r = getNotFoundResponse();
		}

		r.addHeader("Access-Control-Allow-Origin", CORS);
		r.addHeader("Access-Control-Allow-Headers", DEFAULT_ALLOWED_HEADERS);
		r.addHeader("Access-Control-Allow-Credentials", "true");
		r.addHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
		r.addHeader("Access-Control-Max-Age", "" + MAX_AGE);

		return r;
	}

	private static Response serveFile(Map<String, String> header, FileHandler fileHandler, String mime) {
		Response res;
		InputStream inputStreamToCloseOnError = null;
		try {
			// Calculate etag
			final String etag = fileHandler.etag();

			// Support (simple) skipping:
			long startFrom = 0;
			long endAt = -1;
			String range = header.get("range");
			if (range != null) {
				if (range.startsWith("bytes=")) {
					range = range.substring("bytes=".length());
					int minus = range.indexOf('-');
					try {
						if (minus > 0) {
							startFrom = Long.parseLong(range.substring(0, minus));
							endAt = Long.parseLong(range.substring(minus + 1));
						}
					} catch (NumberFormatException ignored) {
					}
				}
			}

			// get if-range header. If present, it must match etag or else we
			// should ignore the range request
			final String ifRange = header.get("if-range");
			final boolean headerIfRangeMissingOrMatching = (ifRange == null || etag.equals(ifRange));

			final String ifNoneMatch = header.get("if-none-match");
			final boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null && ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag));

			// Change return code and add Content-Range header when skipping is
			// requested
			final long fileLen = fileHandler.length();

			if (headerIfRangeMissingOrMatching && range != null && startFrom >= 0 && startFrom < fileLen) {
				// range request that matches current etag
				// and the startFrom of the range is satisfiable
				if (headerIfNoneMatchPresentAndMatching) {
					// range request that matches current etag
					// and the startFrom of the range is satisfiable
					// would return range from file
					// respond with not-modified
					res = newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
					res.addHeader("ETag", etag);
				} else {
					if (endAt < 0) {
						endAt = fileLen - 1;
					}

					inputStreamToCloseOnError = fileHandler.createInputStream();
					startFrom = inputStreamToCloseOnError.skip(startFrom);

					long newLen = endAt - startFrom + 1;
					if (newLen < 0) {
						newLen = 0;
					}

					res = Response.newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, inputStreamToCloseOnError, newLen);
					res.addHeader("Accept-Ranges", "bytes");
					res.addHeader("Content-Length", "" + newLen);
					res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
					res.addHeader("ETag", etag);
					inputStreamToCloseOnError = null;
				}
			} else {

				if (headerIfRangeMissingOrMatching && range != null && startFrom >= fileLen) {
					// return the size of the file
					// 4xx responses are not trumped by if-none-match
					res = newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
					res.addHeader("Content-Range", "bytes */" + fileLen);
					res.addHeader("ETag", etag);
				} else if (range == null && headerIfNoneMatchPresentAndMatching) {
					// full-file-fetch request
					// would return entire file
					// respond with not-modified
					res = newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
					res.addHeader("ETag", etag);
				} else if (!headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching) {
					// range request that doesn't match current etag
					// would return entire (different) file
					// respond with not-modified

					res = newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
					res.addHeader("ETag", etag);
				} else {
					// supply the file
					inputStreamToCloseOnError = fileHandler.createInputStream();
					res = newFixedFileResponse(inputStreamToCloseOnError, fileHandler.length(), mime);
					res.addHeader("Content-Length", "" + fileLen);
					res.addHeader("ETag", etag);
				}
			}
		} catch (IOException ioe) {
			try {
				if (inputStreamToCloseOnError != null)
					inputStreamToCloseOnError.close();
			} catch (Throwable ex) {
				// Just ignore...
			}

			res = getForbiddenResponse("Reading file failed.");
		}

		return res;
	}

	private static Response newFixedFileResponse(InputStream inputStream, long length, String mime) throws FileNotFoundException {
		Response res;
		res = Response.newFixedLengthResponse(Status.OK, mime, inputStream, (int)length);
		res.addHeader("Accept-Ranges", "bytes");
		return res;
	}
}
