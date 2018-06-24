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
import java.nio.channels.SocketChannel;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class SocketWrapper {
	private SocketChannel socketChannel;
	private SSLSocket sslSocket;
	private InputStream sslSocketInputStream;
	private OutputStream sslSocketOutputStream;

	public SocketWrapper(URL url) throws IOException {
		if (!"https".equalsIgnoreCase(url.getProtocol())) {
			socketChannel = SocketChannel.open(new InetSocketAddress(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort()));
		} else {
			final SocketFactory sf = SSLSocketFactory.getDefault();
			sslSocket = (SSLSocket)sf.createSocket(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort());
			sslSocket.setUseClientMode(true);
			sslSocket.setEnableSessionCreation(true);
			sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());
			sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
			sslSocketInputStream = sslSocket.getInputStream();
			sslSocketOutputStream = sslSocket.getOutputStream();
		}
	}

	public void write(ByteBuffer buffer) throws IOException {
		if (socketChannel != null) {
			socketChannel.write(buffer);
		} else {
			final int pos = buffer.position();
			final int len = buffer.remaining();
			sslSocketOutputStream.write(buffer.array(), pos, len);
			buffer.position(pos + len);
		}
	}

	public int read(ByteBuffer buffer) throws IOException {
		if (socketChannel != null) {
			return socketChannel.read(buffer);
		} else {
			final int pos = buffer.position();
			final int r = sslSocketInputStream.read(buffer.array(), pos, buffer.remaining());
			buffer.position(pos + r);
			return r;
		}
	}

	public Socket socket() {
		return (socketChannel != null ? socketChannel.socket() : sslSocket);
	}

	public void destroy() {
		if (socketChannel != null) {
			try {
				final Socket s = socketChannel.socket();
				if (!s.isClosed()) {
					s.shutdownInput();
					s.shutdownOutput();
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				socketChannel.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			socketChannel = null;
		} else if (sslSocket != null) {
			try {
				if (!sslSocket.isClosed()) {
					sslSocket.shutdownInput();
					sslSocket.shutdownOutput();
				}
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				sslSocket.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			sslSocket = null;
			sslSocketInputStream = null;
			sslSocketOutputStream = null;
		}
	}
}
