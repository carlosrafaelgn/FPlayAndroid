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

import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class SocketWrapper {
	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;

	public SocketWrapper(URL url, int receiveBufferSize, int sendBufferSize, int timeout, int connectionTimeout) throws IOException {
		final String host = url.getHost();
		final int port = ((url.getPort() < 0) ? url.getDefaultPort() : url.getPort());
		//I decided to stop using SocketChannel because I could not find a way
		//to configure its connection timeout, which is really important!
		final Socket plainSocket = new Socket();
		try {
			plainSocket.setReceiveBufferSize(receiveBufferSize);
			plainSocket.setSendBufferSize(sendBufferSize);
			plainSocket.setSoTimeout(timeout);
			plainSocket.setTcpNoDelay(true);
			plainSocket.connect(new InetSocketAddress(host, port), connectionTimeout);
			if (!"https".equalsIgnoreCase(url.getProtocol())) {
				socket = plainSocket;
			} else {
				//TLS is layered on top of the plain socket, which is already connected (using our
				//own connection timeout), because only this version of createSocket() lets the
				//SSL socket know which host it is talking to, which is required in order to send
				//the SNI extension (without SNI, a lot of servers abort the handshake, or send
				//back a certificate for another host)
				final SSLSocket sslSocket = (SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(plainSocket, host, port, true);
				socket = sslSocket;
				sslSocket.setUseClientMode(true);
				enableSNI(sslSocket, host);
				//TLS 1.1 and 1.2 are supported, but disabled by default, on Android 4.1 - 4.4
				//(the default cipher suites are not changed, because enabling all supported
				//cipher suites also enables TLS_FALLBACK_SCSV, which makes servers abort the
				//handshake with an inappropriate_fallback alert, along with insecure suites)
				if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 20)
					enableModernProtocols(sslSocket);
				sslSocket.startHandshake();
				//SSLSocket only validates the certificate chain, but not the host name
				if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, sslSocket.getSession()))
					throw new SSLPeerUnverifiedException("Host name not verified: " + host);
			}
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
		} catch (IOException | RuntimeException ex) {
			//the caller never gets a reference to this object, so we must not leak the socket
			if (socket != null)
				destroy();
			else
				closeSilently(plainSocket);
			throw ex;
		}
	}

	private static void enableSNI(SSLSocket sslSocket, String host) {
		if (Build.VERSION.SDK_INT >= 24) {
			try {
				final SSLParameters parameters = sslSocket.getSSLParameters();
				parameters.setServerNames(Collections.<SNIServerName>singletonList(new SNIHostName(host)));
				sslSocket.setSSLParameters(parameters);
			} catch (Throwable ex) {
				//SNIHostName does not accept IP addresses (SNI is not used in that case)
			}
		} else {
			//older versions only send SNI when setHostname() is called (this is what OkHttp does)
			try {
				sslSocket.getClass().getMethod("setHostname", String.class).invoke(sslSocket, host);
			} catch (Throwable ex) {
				//just ignore if this socket implementation does not have setHostname()
			}
		}
	}

	private static void enableModernProtocols(SSLSocket sslSocket) {
		final String[] supportedProtocols = sslSocket.getSupportedProtocols();
		final ArrayList<String> protocols = new ArrayList<>(supportedProtocols.length);
		for (String protocol : supportedProtocols) {
			if (!protocol.startsWith("SSL"))
				protocols.add(protocol);
		}
		if (protocols.size() != 0)
			sslSocket.setEnabledProtocols(protocols.toArray(new String[0]));
	}

	private static void closeSilently(Socket socket) {
		try {
			socket.close();
		} catch (Throwable ex) {
			//just ignore
		}
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
		//r is -1 when the stream has ended
		if (r > 0)
			buffer.position(pos + r);
		return r;
	}

	public void destroy() {
		if (socket != null) {
			try {
				//SSL sockets do not support shutdownInput()/shutdownOutput()
				if (!socket.isClosed() && !(socket instanceof SSLSocket)) {
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
