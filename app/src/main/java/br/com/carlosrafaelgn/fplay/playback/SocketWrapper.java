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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class SocketWrapper {
	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;

	public SocketWrapper(URL url, int receiveBufferSize, int sendBufferSize, int timeout, int connectionTimeout) throws IOException {
		if (!"https".equalsIgnoreCase(url.getProtocol())) {
			//I decided to stop using SocketChannel because I could not find a way
			//to configure its connection timeout, which is really important!
			socket = new Socket();
			socket.setReceiveBufferSize(receiveBufferSize);
			socket.setSendBufferSize(sendBufferSize);
			socket.setSoTimeout(timeout);
			socket.setTcpNoDelay(true);
			socket.connect(new InetSocketAddress(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort()), connectionTimeout);
		} else {
			final SocketFactory sf = SSLSocketFactory.getDefault();
			boolean connected = false;
			SSLSocket sslSocket;
			try {
				sslSocket = (SSLSocket)sf.createSocket();
			} catch (Throwable ex) {
				//Should not happen, but...
				sslSocket = (SSLSocket)sf.createSocket(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort());
				connected = true;
			}
			sslSocket.setReceiveBufferSize(receiveBufferSize);
			sslSocket.setSendBufferSize(sendBufferSize);
			sslSocket.setSoTimeout(timeout);
			sslSocket.setTcpNoDelay(true);
			sslSocket.setUseClientMode(true);
			sslSocket.setEnableSessionCreation(true);
			sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
			sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
			if (!connected)
				sslSocket.connect(new InetSocketAddress(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort()), connectionTimeout);
			socket = sslSocket;
		}
		inputStream = socket.getInputStream();
		outputStream = socket.getOutputStream();
	}

	public void write(ByteBuffer buffer) throws IOException {
		final int pos = buffer.position();
		final int len = buffer.remaining();
		outputStream.write(buffer.array(), pos, len);
		buffer.position(pos + len);
	}

	public int read(ByteBuffer buffer) throws IOException {
		final int pos = buffer.position();
		final int r = inputStream.read(buffer.array(), pos, buffer.remaining());
		buffer.position(pos + r);
		return r;
	}

	public void destroy() {
		if (socket != null) {
			try {
				if (!socket.isClosed()) {
					socket.shutdownInput();
					socket.shutdownOutput();
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				socket.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			socket = null;
			outputStream = null;
			inputStream = null;
		}
	}
}
