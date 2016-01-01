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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.ui.UI;

//This class acts as a "man in the middle", receiving data from the actual server,
//buffering it, and finally serving it to the MediaPlayer class
public final class HttpStreamReceiver implements Runnable {
	private static final int TIMEOUT = 5000;
	private static final int MAX_TIMEOUT_COUNT = 5;

	private final URL url;
	private final Object clientSync, serverSync;
	private final AtomicInteger storedLength;
	private volatile boolean alive, finished;
	private volatile int serverPortReady;
	private volatile byte[] buffer;
	private Thread clientThread, serverThread;
	private Socket clientSocket, playerSocket;
	private ServerSocket serverSocket;

	private final class ServerRunnable implements Runnable {
		@Override
		public void run() {
			final int bufferLen = buffer.length;
			int readIndex = 0, timeoutCount = 0;
			OutputStream outputStream;

			try {
				final InetAddress address = Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 });
				int port;
				for (port = 6000; port < 6500 && alive; port++) {
					try {
						serverSocket = new ServerSocket(port, 2, address);
					} catch (Throwable ex) {
						serverSocket = null;
						continue;
					}
					break;
				}
				serverPortReady = ((serverSocket == null || !alive) ? -1 : port);
			} catch (Throwable ex) {
				serverPortReady = -1;
			} finally {
				synchronized (serverSync) {
					serverSync.notify();
				}
			}

			try {
				if (serverPortReady <= 0)
					return;
				serverSocket.setSoTimeout(TIMEOUT);
				playerSocket = serverSocket.accept();
				playerSocket.setReceiveBufferSize(1024);
				playerSocket.setSendBufferSize(32 * 1024);
				playerSocket.setSoTimeout(TIMEOUT);
				playerSocket.setTcpNoDelay(true);
				//we will just ignore any bytes received
				outputStream = playerSocket.getOutputStream();

				//just send the data to the client
				while (alive && !finished) {
					if (readIndex >= bufferLen)
						readIndex = 0;
					final int maxLen1 = bufferLen - readIndex;
					final int maxLen2 = storedLength.get();
					if (maxLen2 <= 0) {
						synchronized (serverSync) {
							if (!alive || finished)
								break;
							try {
								serverSync.wait();
							} catch (Throwable ex) {
								//ignore the interruptions
							}
						}
						continue;
					}
					final int len = ((maxLen1 <= maxLen2) ? maxLen1 : maxLen2);
					try {
						outputStream.write(buffer, readIndex, len);
						timeoutCount = 0;
						storedLength.addAndGet(-len);
						readIndex += len;
						synchronized (clientSync) {
							clientSync.notify();
						}
					} catch (SocketTimeoutException ex) {
						timeoutCount++;
						if (timeoutCount >= MAX_TIMEOUT_COUNT)
							throw ex;
					}
				}
			} catch (Throwable ex) {
				//abort everything
			} finally {
				closeSocket(playerSocket);
				closeSocket(serverSocket);
			}
		}
	}

	private static void closeSocket(Closeable socket) {
		if (socket == null)
			return;
		try {
			if (socket instanceof Socket) {
				final Socket s = (Socket)socket;
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

	public HttpStreamReceiver(String url) throws MalformedURLException {
		URL temp = new URL(url);
		if (temp.getQuery() == null || !temp.getQuery().contains("icy=http")) {
			final boolean hasQuery = (temp.getQuery() != null && temp.getQuery().length() > 0);
			url = temp.toExternalForm();
			if (!hasQuery && (temp.getPath() == null || temp.getPath().length() == 0))
				url += "/";
			url += (hasQuery ? "&icy=http" : "?icy=http");
			temp = new URL(url);
		}
		this.url = temp;
		clientSync = new Object();
		serverSync = new Object();
		storedLength = new AtomicInteger();
		buffer = new byte[256 * 1024];
		clientThread = new Thread(this, "HttpStreamReceiver Client");
		serverThread = new Thread(new ServerRunnable(), "HttpStreamReceiver Server");
	}

	@Override
	public void run() {
		final int bufferLen = buffer.length;
		int writeIndex, len, timeoutCount = 0;
		InputStream inputStream;

		try {
			clientSocket = new Socket(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort());
			clientSocket.setReceiveBufferSize(32 * 1024);
			clientSocket.setSendBufferSize(1024);
			clientSocket.setSoTimeout(TIMEOUT);
			clientSocket.setTcpNoDelay(true);
			clientSocket.getOutputStream().write((
				"GET " + url.getPath() + "?" + url.getQuery() + " HTTP/1.1\r\nHost:" +
					url.getHost() +
					"\r\nConnection: keep-alive\r\nPragma: no-cache\r\nCache-Control: no-cache\r\nAccept-Encoding: identity;q=1, *;q=0\r\nUser-Agent: FPlay/" +
					UI.VERSION_NAME.substring(1) +
					"\r\nAccept: */*\r\nReferer: " + url.toExternalForm() +
					"\r\nRange: bytes=0-\r\n\r\n").getBytes());
			inputStream = clientSocket.getInputStream();

			//first: wait to receive the HTTP/1.1 2xx response
			//second: check the content-type
			//last: wait for an empty line (a line break followed by a line break)
			len = 0;
			boolean okToGo = false;
			String contentType = null;
			writeIndex = 0;
			//if the header exceeds 16k bytes, something is likely to be wrong...
			hdrLoop:
			while (alive && writeIndex < 16384) {
				try {
					int b;
					switch ((b = inputStream.read())) {
					case -1:
						throw new IOException();
					case '\r':
						//don't place CR's into the buffer
						writeIndex++;
						break;
					case '\n':
						if (okToGo && contentType != null) {
							if (len == 0) {
								//after receiving the final empty line, just queue a fake response to be served
								final byte[] hdr = ("HTTP/1.1 200 OK\r\nContent-Type: " + contentType +
									"\r\nicy-name: FPlayStream \r\nicy-pub: 1\r\n\r\n").getBytes();
								contentType = null;
								if (buffer != null) {
									System.arraycopy(hdr, 0, buffer, 0, hdr.length);
									writeIndex = hdr.length;
									storedLength.addAndGet(hdr.length);
									synchronized (serverSync) {
										serverSync.notify();
									}
								}
								break hdrLoop;
							}
							len = 0;
							continue;
						}
						writeIndex++;
						String line = new String(buffer, 0, len);
						if (!okToGo) {
							if (line.startsWith("HTTP")) {
								len = line.indexOf(' ');
								if (len <= 0) {
									len = 0;
									continue;
								}
								if (line.length() <= (len + 3)) {
									len = 0;
									continue;
								}
								//we need a line like "HTTP/1.1 2xx"
								if (line.charAt(len + 1) != '2')
									throw new IOException();
								okToGo = true;
							}
						} else {
							line = line.toLowerCase();
							if (line.startsWith("content-type")) {
								len = line.indexOf(':');
								if (len <= 0) {
									len = 0;
									continue;
								}
								if (line.length() <= (len + 6)) {
									len = 0;
									continue;
								}
								//we need a line like "content-type: audio/xxx"
								contentType = line.substring(len + 1).trim();
							}
						}
						len = 0;
						break;
					default:
						writeIndex++;
						if (len < 512)
							buffer[len++] = (byte)b;
						break;
					}
					timeoutCount = 0;
				} catch (SocketTimeoutException ex) {
					timeoutCount++;
					if (timeoutCount >= MAX_TIMEOUT_COUNT)
						throw ex;
				}
			}
			//oops... the header exceed our limit
			if (writeIndex >= 16384)
				throw new IOException();

			//from now on, just fill the buffer
			timeoutCount = 0;
			while (alive) {
				if (writeIndex >= bufferLen)
					writeIndex = 0;
				final int maxLen1 = bufferLen - writeIndex;
				final int maxLen2 = bufferLen - storedLength.get();
				if (maxLen2 <= 0) {
					//the buffer is still full, just wait sometime
					synchronized (clientSync) {
						if (!alive)
							break;
						try {
							clientSync.wait(TIMEOUT);
						} catch (Throwable ex) {
							//ignore the interruptions
						}
					}
					continue;
				}
				try {
					if ((len = inputStream.read(buffer, writeIndex, maxLen1 <= maxLen2 ? maxLen1 : maxLen2)) == -1) {
						//that's it! end of stream (probably this was just a file rather than a stream...)
						finished = true;
						synchronized (serverSync) {
							serverSync.notify();
						}
						break;
					}
				} catch (SocketTimeoutException ex) {
					timeoutCount++;
					if (timeoutCount >= MAX_TIMEOUT_COUNT)
						throw ex;
				}
				timeoutCount = 0;
				storedLength.addAndGet(len);
				writeIndex += len;
				synchronized (serverSync) {
					serverSync.notify();
				}
			}
		} catch (Throwable ex) {
			//abort everything!
		} finally {
			closeSocket(clientSocket);
		}
	}

	public boolean start() {
		if (alive || clientThread == null || serverThread == null || buffer == null)
			return false;
		alive = true;
		serverPortReady = 0;
		serverThread.start();
		clientThread.start();
		synchronized (serverSync) {
			if (serverPortReady == 0) {
				try {
					serverSync.wait();
				} catch (Throwable ex) {
					serverPortReady = -1;
				}
			}
		}
		return (serverPortReady > 0);
	}

	public void stop() {
		alive = false;
		serverPortReady = 0;
		synchronized (clientSync) {
			clientSync.notify();
		}
		synchronized (serverSync) {
			serverSync.notify();
		}
		closeSocket(clientSocket);
		closeSocket(playerSocket);
		closeSocket(serverSocket);
		buffer = null;
		clientThread = null;
		serverThread = null;
	}

	public String getLocalURL() {
		if (serverPortReady <= 0)
			return null;
		return "http://127.0.0.1:" + serverPortReady;
	}
}
