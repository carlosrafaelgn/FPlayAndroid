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

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.carlosrafaelgn.fplay.ui.UI;

//This class acts as a "man in the middle", receiving data from the actual server,
//buffering it, and finally serving it to the MediaPlayer class
public final class HttpStreamReceiver implements Runnable {
	private final URL url;
	private final Object clientSync, serverSync;
	private final AtomicInteger storedLength;
	private volatile boolean alive;
	private volatile int serverPortReady;
	private volatile byte[] buffer;
	private Thread clientThread, serverThread;
	private Socket clientSocket, playerSocket;
	private ServerSocket serverSocket;

	private final class ServerRunnable implements Runnable {
		@Override
		public void run() {
			int readIndex;
			Socket client = null;

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
				serverSocket.setSoTimeout(5000);
				playerSocket = serverSocket.accept();
				playerSocket.setReceiveBufferSize(1024);
				playerSocket.setSendBufferSize(32 * 1024);
				playerSocket.setSoTimeout(5000);
				playerSocket.setTcpNoDelay(true);
			} catch (Throwable ex) {
			} finally {
				try {
					if (playerSocket != null)
						playerSocket.close();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				try {
					if (serverSocket != null)
						serverSocket.close();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	private static void closeSocket() {

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
		int writeIndex, len;
		InputStream inputStream;
		byte[] tmp = new byte[512];

		try {
			clientSocket = new Socket(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort());
			clientSocket.setReceiveBufferSize(32 * 1024);
			clientSocket.setSendBufferSize(1024);
			clientSocket.setSoTimeout(5000);
			clientSocket.setTcpNoDelay(true);
			clientSocket.getOutputStream().write((
				"GET " + url.getPath() + "?" + url.getQuery() + " HTTP/1.1\r\nHost:" +
					url.getHost() +
					"\r\nConnection: keep-alive\r\nPragma: no-cache\r\nCache-Control: no-cache\r\nAccept-Encoding: identity;q=1, *;q=0\r\nUser-Agent: FPlay/" +
					UI.VERSION_NAME.substring(1) +
					"\r\nAccept: */*\r\nReferer: " + url.toExternalForm() +
					"\r\nRange: bytes=0-\r\n\r\n").getBytes());
			inputStream = clientSocket.getInputStream();
			while (alive) {
				try {
					len = inputStream.read(tmp, 0, 512);
				} catch (Throwable ex) {
					len = 0;
				}
				if (len < 0)
					break;

			}
			//while (alive) {

			//}
		} catch (Throwable ex) {
			//abort everything!
		} finally {
			try {
				if (clientSocket != null)
					clientSocket.shutdownInput();
				if (clientSocket != null)
					clientSocket.shutdownOutput();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (clientSocket != null)
					clientSocket.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	public void start() throws IOException {
		if (alive || clientThread == null || serverThread == null || buffer == null)
			return;
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
		if (serverPortReady <= 0)
			throw new IOException();
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
		try {
			if (clientSocket != null)
				clientSocket.close();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			if (playerSocket != null)
				playerSocket.close();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			if (serverSocket != null)
				serverSocket.close();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
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
