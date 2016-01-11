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
package br.com.carlosrafaelgn.fplay.playback;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.ui.UI;

//This class acts as a "man in the middle", receiving data from the actual server,
//buffering it, and finally serving it to the MediaPlayer class
public final class HttpStreamReceiver implements Runnable {
	private static final int TIMEOUT = 5000;
	private static final int MAX_TIMEOUT_COUNT = 5;
	private static final int MAX_METADATA_LENGTH = (0xFF * 16);
	private static final int MAX_HEADER_PACKET_LENGTH = 512; //an average response header has ~360 bytes (for a request header, even less than that)
	private static final int MAX_PACKET_LENGTH = 2048;
	private static final int MIN_BUFFER_LENGTH = 4 * MAX_PACKET_LENGTH;
	private static final int EXTERNAL_BUFFER_LENGTH = (128 * 1024); //5.3s worth of data @ 192kbps

	//God save the Internet :) (this was all the documentation I found!!!)
	//http://www.smackfu.com/stuff/programming/shoutcast.html
	//http://stackoverflow.com/questions/6061057/developing-the-client-for-the-icecast-server
	private URL url;
	private final Object sync, clientOkToWorkSignal, serverOkToWorkSignal;
	private final AtomicInteger storedLength;
	private final int errorMsg, metadataMsg, arg1;
	private volatile boolean alive, finished, headerOk;
	private volatile int serverPortReady;
	private volatile ByteBuffer buffer;
	private String contentType, icyName, icyUrl, icyGenre;
	private int icyBitrate, icyMetaInterval;
	private Handler errorHandler, metadataHandler;
	private Thread clientThread, serverThread;
	private SocketChannel clientSocket, playerSocket;
	private ServerSocketChannel serverSocket;
	public int bytesReceivedSoFar;

	private final class ServerRunnable implements Runnable {
		private void readPlayerRequestAndSendResponseHeader() throws IOException {
			final SocketChannel localSocket = serverSocket.accept();
			synchronized (sync) {
				if (!alive) {
					closeSocket(localSocket);
					return;
				}
				playerSocket = localSocket;
			}
			final Socket s = localSocket.socket();
			s.setReceiveBufferSize(MAX_HEADER_PACKET_LENGTH);
			s.setSendBufferSize(MIN_BUFFER_LENGTH);
			s.setSoTimeout(TIMEOUT);
			s.setTcpNoDelay(true);

			//since we are only accessing it from Java, using a DirectByteBuffer is
			//slower than using a ByteArrayBuffer
			final ByteBuffer tmp = ByteBuffer.wrap(new byte[MAX_HEADER_PACKET_LENGTH]);
			int totalBytesRead = 0, lineLen = 0, timeoutCount = 0;
			tmp.limit(0);
			tmp.position(0);
			while (alive && totalBytesRead < MIN_BUFFER_LENGTH) {
				try {
					if (tmp.remaining() == 0) {
						//read at most MAX_HEADER_PACKET_LENGTH bytes at a time
						tmp.limit(MAX_HEADER_PACKET_LENGTH);
						tmp.position(0);
						final int readLen = localSocket.read(tmp);
						timeoutCount = 0;
						if (readLen < 0)
							throw new IOException();
						if (readLen == 0)
							continue;
						tmp.limit(readLen);
						tmp.position(0);
					}
					//unlike sendRequestAndParseResponse() we do not really care about the actual lines here
					switch (tmp.get()) {
					case '\r':
						//ignore carriage returns
						totalBytesRead++;
						break;
					case '\n':
						if (lineLen == 0) {
							//send the response (but wait for the original header to arrive, first)
							while (alive && !headerOk) {
								synchronized (serverOkToWorkSignal) {
									if (!alive)
										return;
									if (headerOk)
										break;
									try {
										serverOkToWorkSignal.wait(100);
									} catch (Throwable ex) {
										//ignore the interruptions
									}
								}
							}
							final byte[] hdr = ("ICY 200 OK\r\nicy-notice1:This stream is generated by FPlay\r\nicy-notice2:SHOUTcast Distributed Network Audio Server/Linux v1.0\r\nicy-pub:1\r\nicy-name:FPlay" +
								((icyGenre != null && icyGenre.length() > 0) ? ("\r\nicy-genre:" + icyGenre) : "") +
								((icyUrl != null && icyUrl.length() > 0) ? ("\r\nicy-url:" + icyUrl) : "") +
								((icyBitrate > 0) ? ("\r\nicy-br:" + icyBitrate) : "") +
								"\r\ncontent-type:" + contentType + "\r\n\r\n").getBytes();
							tmp.limit(hdr.length);
							tmp.position(0);
							localSocket.write(tmp);
							return;
						}
						totalBytesRead++;
						lineLen = 0;
						break;
					default:
						totalBytesRead++;
						lineLen++;
						break;
					}
				} catch (SocketTimeoutException ex) {
					timeoutCount++;
					if (timeoutCount >= MAX_TIMEOUT_COUNT)
						throw ex;
				}
			}

			throw new IOException();
		}

		@Override
		public void run() {
			final int bufferLen;
			final ByteBuffer readBuffer;
			synchronized (sync) {
				if (buffer == null) {
					sync.notifyAll();
					return;
				}
				bufferLen = buffer.capacity();
				readBuffer = buffer.asReadOnlyBuffer();
				readBuffer.position(0);
			}
			int timeoutCount = 0;

			try {
				final InetAddress address = Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 });
				int port;
				ServerSocketChannel tmpServerSocket = null;
				for (port = 5000; port < 6500 && alive; port++) {
					try {
						tmpServerSocket = ServerSocketChannel.open();
						tmpServerSocket.socket().bind(new InetSocketAddress(address, port), 2);
						break;
					} catch (Throwable ex) {
						closeSocket(tmpServerSocket);
					}
				}
				synchronized (sync) {
					if (!alive) {
						closeSocket(tmpServerSocket);
						return;
					}
					serverSocket = tmpServerSocket;
				}
				serverPortReady = ((serverSocket == null || !alive) ? -1 : port);
			} catch (Throwable ex) {
				serverPortReady = -1;
			} finally {
				synchronized (sync) {
					sync.notifyAll();
				}
			}

			try {
				if (serverPortReady <= 0)
					return;
				serverSocket.socket().setSoTimeout(TIMEOUT);

				//first let's receive the player's command and send the header as soon as possible!
				readPlayerRequestAndSendResponseHeader();

				//now playerSocket will only be used for output and we will just ignore any bytes received hereafter

				while (alive && !finished) {
					if (readBuffer.position() >= bufferLen)
						readBuffer.position(0);
					final int maxLen1 = bufferLen - readBuffer.position();
					final int maxLen2 = storedLength.get();
					if (maxLen2 <= 0) {
						synchronized (serverOkToWorkSignal) {
							if (!alive || finished)
								break;
							if (storedLength.get() > 0)
								continue;
							try {
								serverOkToWorkSignal.wait(100);
							} catch (Throwable ex) {
								//ignore the interruptions
							}
						}
						continue;
					}
					int len = ((maxLen1 <= maxLen2) ? maxLen1 : maxLen2);
					//if we limit the amount to be written to MAX_PACKET_LENGTH bytes we won't block
					//playerSocket.write for long periods
					if (len > MAX_PACKET_LENGTH)
						len = MAX_PACKET_LENGTH;
					readBuffer.limit(readBuffer.position() + len);
					try {
						len = playerSocket.write(readBuffer);
						timeoutCount = 0;
						storedLength.addAndGet(-len);
						synchronized (clientOkToWorkSignal) {
							clientOkToWorkSignal.notify();
						}
					} catch (SocketTimeoutException ex) {
						timeoutCount++;
						if (timeoutCount >= MAX_TIMEOUT_COUNT)
							throw ex;
					} catch (IOException ex) {
						if (!alive)
							return;
						//maybe the player has closed the connection, but it could create a new connection
						synchronized (sync) {
							closeSocket(playerSocket);
							playerSocket = null;
						}
						if (!alive)
							return;
						readPlayerRequestAndSendResponseHeader();
					}
				}
			} catch (Throwable ex) {
				//abort everything!
				if (!alive)
					return;
				try {
					if (errorHandler != null)
						errorHandler.sendMessageAtTime(Message.obtain(errorHandler, errorMsg, arg1, (ex instanceof SocketTimeoutException) ? 0 : ((ex instanceof FileNotFoundException) ? -1 : -2)), SystemClock.uptimeMillis());
				} catch (Throwable ex2) {
					errorHandler = null;
				}
			} finally {
				synchronized (sync) {
					closeSocket(playerSocket);
					playerSocket = null;
					closeSocket(serverSocket);
					serverSocket = null;
				}
			}
		}
	}

	private static String detectCharsetAndDecode(byte[] buffer, int offset, int length) {
		//maybe one day we could place this method in its own class...
		boolean appearsUtf8 = true;
		int pendingUtf8Bytes = 0;
		length += offset;
		for (int i = offset; i < length; i++) {
			int b = ((int)buffer[i] & 0xFF);
			if (pendingUtf8Bytes == 0) {
				if (b <= 0x7F)
					continue;
				if (b >= 0xC0) { //11000000
					if (b < 0xE0) { //11100000
						pendingUtf8Bytes = 1;
					} else if (b < 0xF0) { //11110000
						pendingUtf8Bytes = 2;
					} else if (b < 0xF8) { //11111000
						pendingUtf8Bytes = 3;
					} else if (b < 0xFC) { //11111100
						pendingUtf8Bytes = 4;
					} else if (b < 0xFE) { //11111110
						pendingUtf8Bytes = 5;
					} else {
						appearsUtf8 = false;
						break;
					}
				} else {
					appearsUtf8 = false;
					break;
				}
			} else {
				if (b >= 0x80 && b <= 0xBF) {
					pendingUtf8Bytes--;
				} else {
					appearsUtf8 = false;
					break;
				}
			}
		}
		try {
			if (appearsUtf8)
				return new String(buffer, offset, length - offset, "UTF-8");
		} catch (Throwable ex) {
			//???
		}
		try {
			return new String(buffer, offset, length - offset, "ISO-8859-1");
		} catch (Throwable ex) {
			return null;
		}
	}

	private static void closeSocket(SocketChannel socket) {
		//the emulator was constantly throwing a *WEIRD* exception here during
		//runtime: Socket cannot be cast to Closeable!!! :/
		if (socket == null)
			return;
		try {
			final Socket s = socket.socket();
			if (!s.isClosed()) {
				s.shutdownInput();
				s.shutdownOutput();
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			socket.close();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	private static void closeSocket(ServerSocketChannel socket) {
		if (socket == null)
			return;
		try {
			socket.close();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static URL normalizeIcyUrl(String url) throws MalformedURLException {
		final URL temp = new URL(url);
		String path = temp.getPath();
		String query = temp.getQuery();
		int ok = 0;
		//the ; was sniffed from shoutcast.com
		if (path == null || path.length() == 0 || (path.length() == 1 && path.charAt(0) == '/'))
			path = "/;";
		else if (path.charAt(0) != '/')
			path = "/" + path;
		else
			ok |= 1;
		//the pair "icy=http" was sniffed from shoutcast.com
		if (query == null || query.length() == 0)
			query = "icy=http";
		else if (!temp.getQuery().contains("icy=http"))
			query += "&icy=http";
		else
			ok |= 2;
		return ((ok == 3) ? temp : new URL(temp.getProtocol(), temp.getHost(), (temp.getPort() < 0) ? temp.getDefaultPort() : temp.getPort(), path + "?" + query));
	}

	public HttpStreamReceiver(Handler errorHandler, int errorMsg, Handler metadataHandler, int metadataMsg, int arg1, String url) throws MalformedURLException {
		this(errorHandler, errorMsg, metadataHandler, metadataMsg, arg1, url, EXTERNAL_BUFFER_LENGTH, true);
	}

	public HttpStreamReceiver(Handler errorHandler, int errorMsg, Handler metadataHandler, int metadataMsg, int arg1, String url, int bufferLength, boolean createThreads) throws MalformedURLException {
		alive = true;
		this.url = normalizeIcyUrl(url);
		sync = new Object();
		clientOkToWorkSignal = new Object();
		serverOkToWorkSignal = new Object();
		storedLength = new AtomicInteger();
		buffer = ByteBuffer.allocateDirect(bufferLength <= MIN_BUFFER_LENGTH ? MIN_BUFFER_LENGTH : bufferLength);
		this.errorHandler = errorHandler;
		this.errorMsg = errorMsg;
		this.metadataHandler = metadataHandler;
		this.metadataMsg = metadataMsg;
		this.arg1 = arg1;
		bytesReceivedSoFar = -1;
		if (createThreads) {
			clientThread = new Thread(this, "HttpStreamReceiver Client");
			clientThread.setDaemon(true);
			serverThread = new Thread(new ServerRunnable(), "HttpStreamReceiver Server");
			serverThread.setDaemon(true);
		}
	}

	public boolean pingServer() throws IOException {
		return sendRequestAndParseResponse(0);
	}

	private boolean sendRequestAndParseResponse(int redirectCount) throws IOException {
		if (!alive)
			return false;
		if (redirectCount >= 3)
			throw new FileNotFoundException();
		headerOk = false;
		contentType = null;
		icyName = null;
		icyUrl = null;
		icyGenre = null;
		icyBitrate = 0;
		icyMetaInterval = 0;
		if (clientSocket != null) {
			//this allows pingServer() to be called multiple times
			synchronized (sync) {
				if (clientSocket != null) {
					closeSocket(clientSocket);
					clientSocket = null;
				}
			}
		}
		final SocketChannel localSocket = SocketChannel.open(new InetSocketAddress(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort()));
		synchronized (sync) {
			if (!alive) {
				closeSocket(localSocket);
				return false;
			}
			clientSocket = localSocket;
		}
		final Socket s = localSocket.socket();
		s.setReceiveBufferSize(EXTERNAL_BUFFER_LENGTH);
		s.setSendBufferSize(MAX_HEADER_PACKET_LENGTH);
		s.setSoTimeout(TIMEOUT);
		s.setTcpNoDelay(true);
		final byte[] httpCommand = ("GET " + url.getFile() + " HTTP/1.1\r\nHost:" +
			url.getHost() +
			"\r\nConnection: keep-alive\r\nPragma: no-cache\r\nCache-Control: no-cache\r\nAccept-Encoding: identity;q=1, *;q=0\r\nUser-Agent: FPlay/" +
			UI.VERSION_NAME.substring(1) +
			"\r\nIcy-MetaData:1\r\nAccept: */*\r\nReferer: " + url.toExternalForm() +
			"\r\nRange: bytes=0-\r\n\r\n").getBytes();
		buffer.limit(httpCommand.length);
		buffer.position(0);
		buffer.put(httpCommand, 0, httpCommand.length);
		buffer.position(0);
		localSocket.write(buffer);

		//first: wait to receive the HTTPx 2xx or ICYx 2xx response
		//second: check the content-type and the other icy-xxx headers
		//last: wait for an empty line (a line break followed by another line break)

		final byte[] tmpLine = new byte[128];
		int totalBytesRead = 0, lineLen = 0, timeoutCount = 0;
		buffer.limit(0);
		buffer.position(0);
		boolean okToGo = false, shouldRedirectOnCompletion = false;

		//if the header exceeds MIN_BUFFER_LENGTH bytes, something is likely to be wrong...
		while (alive && totalBytesRead < MIN_BUFFER_LENGTH) {
			try {
				if (buffer.remaining() == 0) {
					//read at most MAX_HEADER_PACKET_LENGTH bytes at a time
					buffer.limit(MAX_HEADER_PACKET_LENGTH);
					buffer.position(0);
					final int readLen = localSocket.read(buffer);
					timeoutCount = 0;
					if (readLen < 0)
						throw new IOException();
					if (readLen == 0)
						continue;
					buffer.limit(readLen);
					buffer.position(0);
				}
				final byte b = buffer.get();
				switch (b) {
				case '\r':
					//ignore carriage returns
					totalBytesRead++;
					break;
				case '\n':
					if (lineLen == 0) {
						if (okToGo && contentType != null) {
							if (shouldRedirectOnCompletion) {
								synchronized (sync) {
									if (!alive)
										return false;
									closeSocket(clientSocket);
									clientSocket = null;
								}
								url = normalizeIcyUrl(contentType);
								return sendRequestAndParseResponse(redirectCount + 1);
							}
							headerOk = true;
							synchronized (serverOkToWorkSignal) {
								serverOkToWorkSignal.notify();
							}
							return true;
						}
						return false;
					}
					totalBytesRead++;
					final String line = new String(tmpLine, 0, lineLen);
					if (!okToGo) {
						if (line.startsWith("HTTP") || line.startsWith("ICY")) {
							lineLen = line.indexOf(' ');
							if (lineLen <= 0) {
								lineLen = 0;
								continue;
							}
							if (line.length() <= (lineLen + 3)) {
								lineLen = 0;
								continue;
							}
							//we need a line like "HTTPx 2xx" or "ICYx 2xx"
							okToGo = true;
							switch (line.charAt(lineLen + 1)) {
							case '2':
								break;
							case '3':
								shouldRedirectOnCompletion = true;
								break;
							case '4': //assume all 4xx codes as 404
								throw new FileNotFoundException();
							default: //we do not accept 1xx and 5xx replies
								throw new IOException();
							}
						}
					} else {
						lineLen = line.indexOf(':');
						if (lineLen <= 0) {
							lineLen = 0;
							continue;
						}
						if (line.length() <= (lineLen + 2)) {
							lineLen = 0;
							continue;
						}
						if (line.regionMatches(true, 0, "location", 0, 8)) {
							if (shouldRedirectOnCompletion)
								contentType = line.substring(lineLen + 1).trim();
						} else if (line.regionMatches(true, 0, "content-type", 0, 12)) {
							if (!shouldRedirectOnCompletion)
								contentType = line.substring(lineLen + 1).trim();
						} else if (line.regionMatches(true, 0, "icy-url", 0, 7)) {
							icyUrl = line.substring(lineLen + 1).trim();
						} else if (line.regionMatches(true, 0, "icy-genre", 0, 9)) {
							icyGenre = line.substring(lineLen + 1).trim();
						} else if (line.regionMatches(true, 0, "icy-name", 0, 8)) {
							final String name = line.substring(lineLen + 1).trim();
							if (name.length() != 0)
								icyName = name;
						} else if (line.regionMatches(true, 0, "icy-description", 0, 15)) {
							//use the description as the name only if not overwriting an existing name
							final String description = line.substring(lineLen + 1).trim();
							if (description.length() != 0 && icyName == null)
								icyName = description;
						} else if (line.regionMatches(true, 0, "icy-metaint", 0, 11)) {
							try {
								final int metaint = Integer.parseInt(line.substring(lineLen + 1).trim());
								if (metaint > 0)
									icyMetaInterval = metaint;
							} catch (Throwable ex) {
								//not a valid interval
							}
						} else if (line.regionMatches(true, 0, "icy-br", 0, 6)) {
							try {
								final int br = Integer.parseInt(line.substring(lineLen + 1).trim());
								if (br > 0)
									icyBitrate = br;
							} catch (Throwable ex) {
								//not a valid bitrate
							}
						}
					}
					lineLen = 0;
					break;
				default:
					totalBytesRead++;
					if (lineLen < 128)
						tmpLine[lineLen++] = b;
					break;
				}
			} catch (SocketTimeoutException ex) {
				timeoutCount++;
				if (timeoutCount >= MAX_TIMEOUT_COUNT)
					throw ex;
			}
		}
		//oops... the header exceed our limit
		if (totalBytesRead >= MIN_BUFFER_LENGTH)
			throw new IOException();
		return false;
	}

	@Override
	public void run() {
		final int bufferLen;
		final ByteBuffer writeBuffer;
		synchronized (sync) {
			if (buffer == null)
				return;
			bufferLen = buffer.capacity();
			writeBuffer = buffer;
		}
		int timeoutCount = 0, bufferingCounter, audioCountdown, metaCountdown = 0;

		try {
			if (!sendRequestAndParseResponse(0))
				throw new IOException();

			//sendRequestAndParseResponse() left the buffer/writeBuffer with a few remaining
			//bytes (which are audio that must be accounted for)
			audioCountdown = writeBuffer.remaining();
			writeBuffer.position(writeBuffer.limit());
			bufferingCounter = audioCountdown;
			audioCountdown = icyMetaInterval - audioCountdown;

			//do not allocate a direct buffer for the metadata, as we will have to access
			//that data constantly from Java
			final byte[] metaBuffer = ((icyMetaInterval > 0) ? new byte[MAX_METADATA_LENGTH] : null);
			final ByteBuffer metaByteBuffer = ((metaBuffer != null) ? ByteBuffer.wrap(metaBuffer) : null);

			//from now on, just fill our local buffer
			while (alive) {
				int len;

				if (metaByteBuffer != null && metaCountdown != 0) {
					try {
						if (metaCountdown < 0) {
							//first time: read the first byte which indicates the metadata's length
							metaByteBuffer.limit(1);
							metaByteBuffer.position(0);
						}
						if ((len = clientSocket.read(metaByteBuffer)) <= 0)
							continue;
						bytesReceivedSoFar += len;
						if (metaCountdown < 0) {
							metaCountdown = (((int)metaBuffer[0] & 0xFF) << 4);
							metaByteBuffer.limit(metaCountdown);
							metaByteBuffer.position(0);
						} else {
							metaCountdown -= len;
							if (metaCountdown <= 0) {
								//we finished reading all the metadata!
								metaCountdown = 0;
								if (metadataHandler != null) {
									int i = metaByteBuffer.position() - 1;
									while (i > 0 && metaBuffer[i] == 0)
										i--;
									if (i >= 0)
										metadataHandler.sendMessageAtTime(Message.obtain(metadataHandler, metadataMsg, arg1, 0, detectCharsetAndDecode(metaBuffer, 0, i + 1)), SystemClock.uptimeMillis());
								}
							}
						}
					} catch (SocketTimeoutException ex) {
						timeoutCount++;
						if (timeoutCount >= MAX_TIMEOUT_COUNT)
							throw ex;
					}
					continue;
				}

				if (writeBuffer.position() >= bufferLen)
					writeBuffer.position(0);
				final int maxLen1 = bufferLen - writeBuffer.position();
				final int maxLen2 = bufferLen - storedLength.get();
				if (maxLen2 <= 0) {
					//the buffer is still full... because of metadata, we will
					//have to start discarding old data :(
					storedLength.addAndGet(-MAX_PACKET_LENGTH);
					final int newPosition = writeBuffer.position() - MAX_PACKET_LENGTH;
					writeBuffer.limit((newPosition < 0) ? (bufferLen + newPosition) : newPosition);
					writeBuffer.position(writeBuffer.limit());
					continue;
				}
				len = ((maxLen1 <= maxLen2) ? maxLen1 : maxLen2);

				//we cannot read metadata as audio
				if (metaByteBuffer != null && len > audioCountdown)
					len = audioCountdown;

				//if we limit the amount to be read to MAX_PACKET_LENGTH bytes we won't block
				//clientSocket.read for long periods
				if (bufferingCounter >= 0) {
					if (len > (MAX_PACKET_LENGTH >> 1))
						len = (MAX_PACKET_LENGTH >> 1);
				} else {
					if (len > MAX_PACKET_LENGTH)
						len = MAX_PACKET_LENGTH;
				}

				writeBuffer.limit(writeBuffer.position() + len);
				try {
					if ((len = clientSocket.read(writeBuffer)) < 0) {
						//that's it! end of stream (probably this was just a file rather than a stream...)
						finished = true;
						synchronized (serverOkToWorkSignal) {
							serverOkToWorkSignal.notify();
						}
						break;
					}

					audioCountdown -= len;
					if (audioCountdown <= 0) {
						metaCountdown = -1;
						audioCountdown = icyMetaInterval;
					}
					bytesReceivedSoFar += len;
					timeoutCount = 0;

					//before notifying the server for the first time, let's wait for the buffer to fill up
					if (bufferingCounter >= 0) {
						bufferingCounter += len;
						//if (bufferingCounter >= MIN_BUFFER_LENGTH) {
							storedLength.addAndGet(bufferingCounter);
							synchronized (serverOkToWorkSignal) {
								serverOkToWorkSignal.notify();
							}
							bufferingCounter = -1;
						//}
					} else {
						storedLength.addAndGet(len);
						synchronized (serverOkToWorkSignal) {
							serverOkToWorkSignal.notify();
						}
					}
				} catch (SocketTimeoutException ex) {
					timeoutCount++;
					if (timeoutCount >= MAX_TIMEOUT_COUNT)
						throw ex;
				}
			}
		} catch (Throwable ex) {
			//abort everything!
			if (!alive)
				return;
			try {
				if (errorHandler != null)
					errorHandler.sendMessageAtTime(Message.obtain(errorHandler, errorMsg, arg1, (ex instanceof SocketTimeoutException) ? 0 : ((ex instanceof FileNotFoundException) ? -1 : -2)), SystemClock.uptimeMillis());
			} catch (Throwable ex2) {
				errorHandler = null;
			}
		} finally {
			synchronized (sync) {
				closeSocket(clientSocket);
				clientSocket = null;
			}
		}
	}

	public boolean start() {
		if (!alive || buffer == null || serverThread == null || clientThread == null)
			return false;
		clientThread.start();
		serverThread.start();
		synchronized (sync) {
			if (serverPortReady == 0) {
				try {
					sync.wait();
				} catch (Throwable ex) {
					serverPortReady = -1;
				}
			}
		}
		return (serverPortReady > 0);
	}

	public void stop() {
		alive = false;
		synchronized (clientOkToWorkSignal) {
			clientOkToWorkSignal.notify();
		}
		synchronized (serverOkToWorkSignal) {
			serverOkToWorkSignal.notify();
		}
		synchronized (sync) {
			errorHandler = null;
			metadataHandler = null;
			serverPortReady = 0;
			if (serverThread != null) {
				serverThread.interrupt();
				serverThread = null;
			}
			if (clientThread != null) {
				clientThread.interrupt();
				clientThread = null;
			}
			closeSocket(clientSocket);
			clientSocket = null;
			closeSocket(playerSocket);
			playerSocket = null;
			closeSocket(serverSocket);
			serverSocket = null;
			sync.notifyAll();
			buffer = null;
		}
	}

	public String getResolvedURL() {
		return ((url == null) ? null : url.toExternalForm());
	}

	public String getLocalURL() {
		return ((serverPortReady <= 0) ? null : ("http://127.0.0.1:" + serverPortReady + "/"));
	}

	public String getIcyName() {
		return icyName;
	}

	public String getIcyUrl() {
		return icyUrl;
	}
}
