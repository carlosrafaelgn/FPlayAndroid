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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
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
import java.nio.ShortBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import br.com.carlosrafaelgn.fplay.BuildConfig;
import br.com.carlosrafaelgn.fplay.list.RadioStation;
import br.com.carlosrafaelgn.fplay.playback.context.IMediaPlayer;
import br.com.carlosrafaelgn.fplay.ui.UI;

public final class HttpStreamReceiver implements Runnable {
	private static final int TIMEOUT = 5000;
	private static final int MAX_TIMEOUT_COUNT = 5;
	private static final int MAX_METADATA_LENGTH = (0xFF * 16);
	private static final int MAX_HEADER_PACKET_LENGTH = 512; //an average response header has ~360 bytes (for a request header, even less than that)
	private static final int MAX_PACKET_LENGTH = 2048;
	private static final int MIN_BUFFER_LENGTH = 4 * MAX_PACKET_LENGTH;
	private static final int EXTERNAL_BUFFER_LENGTH = (128 * 1024); //5.3s worth of data @ 192kbps

	public static final class Metadata {
		public final String streamTitle, icyName, icyUrl;

		public Metadata(String streamTitle, String icyName, String icyUrl) {
			this.streamTitle = streamTitle;
			this.icyName = icyName;
			this.icyUrl = icyUrl;
		}
	}

	//God save the Internet :) (this was all the documentation I found!!!)
	//http://www.smackfu.com/stuff/programming/shoutcast.html
	//http://stackoverflow.com/questions/6061057/developing-the-client-for-the-icecast-server
	private URL url;
	private final String path;
	private final Object sync;
	private final int errorMsg, preparedMsg, metadataMsg, urlMsg, infoMsg, arg1, audioSessionId, initialNetworkBufferLengthInBytes, initialAudioBufferInMS;
	public final CircularIOBuffer buffer;
	private volatile boolean alive, finished, headerOk;
	private volatile int serverPortReady;
	private RadioStationResolver resolver;
	private boolean released;
	private String contentType, icyName, icyUrl, icyGenre;
	private int icyBitRate, icyMetaInterval;
	private Handler handler;
	private Thread clientThread, serverThread;
	private SocketChannel clientSocket, playerSocket;
	private ServerSocketChannel serverSocket;
	private AudioTrack audioTrack;
	public int bytesReceivedSoFar;
	public final boolean isPerformingFullPlayback;

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private final class FullPlaybackRunnable implements Runnable {
		private MediaCodec mediaCodec;
		private ByteBuffer[] inputBuffers, outputBuffers;
		private ShortBuffer[] outputBuffersAsShort;

		@SuppressWarnings("deprecation")
		private void createMediaCodec(int channelCount, int sampleRate, int bitRate) throws IOException {
			mediaCodec = MediaCodec.createDecoderByType(contentType);
			final MediaFormat format = new MediaFormat();
			format.setString(MediaFormat.KEY_MIME, contentType);
			format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
			format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
			format.setInteger(MediaFormat.KEY_CHANNEL_MASK, (channelCount == 1) ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
			format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
			mediaCodec.configure(format, null, null, 0);
			mediaCodec.start();
			inputBuffers = mediaCodec.getInputBuffers();
			outputBuffers = mediaCodec.getOutputBuffers();
			outputBuffersAsShort = new ShortBuffer[outputBuffers.length];
			for (int i = 0; i < outputBuffers.length; i++) {
				outputBuffersAsShort[i] = outputBuffers[i].asShortBuffer();
				outputBuffersAsShort[i].limit(outputBuffersAsShort[i].capacity());
			}
		}

		@SuppressWarnings("deprecation")
		private void createAudioTrack(int channelCount, int sampleRate, int bufferSizeInMS) {
			final int minBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRate, (channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
			int minBufferSizeInFrames = minBufferSizeInBytes >> 2;
			if (minBufferSizeInFrames <= 0)
				minBufferSizeInFrames = 1024;

			int bufferSizeInFrames = (bufferSizeInMS * sampleRate) / 1000;

			if (bufferSizeInFrames <= (minBufferSizeInFrames << 1)) {
				//we need at least 2 buffers
				bufferSizeInFrames = minBufferSizeInFrames << 1;
			} else {
				//otherwise, make sure it is a multiple of our minimal buffer size
				bufferSizeInFrames = ((bufferSizeInFrames + minBufferSizeInFrames - 1) / minBufferSizeInFrames) * minBufferSizeInFrames;
			}

			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, (channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInFrames << channelCount, AudioTrack.MODE_STREAM, audioSessionId);
			audioTrack.setStereoVolume(0.0f, 0.0f);
		}

		private void releaseMediaCodec() {
			if (mediaCodec != null) {
				try {
					mediaCodec.stop();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				try {
					mediaCodec.release();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				mediaCodec = null;
			}
		}

		private void releaseAudioTrack() {
			if (audioTrack != null) {
				try {
					audioTrack.flush();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				try {
					audioTrack.release();
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				audioTrack = null;
			}
		}

		@SuppressWarnings({ "deprecation", "PointlessBooleanExpression", "ConstantConditions" })
		@Override
		public void run() {
			PowerManager.WakeLock wakeLock = null;

			try {
				if (!BuildConfig.X) {
					wakeLock = ((PowerManager)Player.theApplication.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MediaContext WakeLock");
					wakeLock.setReferenceCounted(false);
					wakeLock.acquire();
				}

				if (!waitForHeaders())
					return;

				final int[] properties = new int[4]; //channel count, sample rate, bit rate, samples per frame
				int inputFrameSize = waitToReadMpegHeader(properties);
				if (inputFrameSize < 0)
					return;

				if (BuildConfig.X) {
					if (handler != null) {
						if (buffer.waitUntilCanRead(inputFrameSize) < 0)
							return;
						handler.sendMessageAtTime(Message.obtain(handler, infoMsg, arg1, 0, properties), SystemClock.uptimeMillis());
					}
					return;
				}

				final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
				final long usPerFrame = (long)properties[3] * 1000000L / (long)properties[1];
				long currentUs = 0;

				createMediaCodec(properties[0], properties[1], properties[2]);
				createAudioTrack(properties[0], properties[1], initialAudioBufferInMS);

				short[] tmpShortOutput = new short[properties[0] * properties[3]]; //1 frame @ mono/stereo, 16 bits per sample
				boolean okToProcessMoreInput = true, playbackStarted = false;
				int timedoutFrameCount = 0;

				while (alive) {
					if (okToProcessMoreInput) {
						if (buffer.waitUntilCanRead(inputFrameSize) < 0)
							break;

						//get the next available input buffer
						int inputBufferIndex;
						while ((inputBufferIndex = mediaCodec.dequeueInputBuffer(10000)) < 0) {
							if (!alive)
								return;
						}
						buffer.readArray(inputBuffers[inputBufferIndex], 0, inputFrameSize);
						buffer.commitRead(inputFrameSize);

						//queue the input buffer for decoding
						mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputFrameSize, currentUs, 0);
						currentUs += usPerFrame;
					}

					//wait for the decoding process to complete
					int outputBufferIndex;
					try {
						info.flags = 0;
						info.offset = 0;
						info.presentationTimeUs = 0;
						info.size = 0;
						OutputLoop:
						while ((outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 50000)) < 0) {
							if (!alive)
								return;
							info.flags = 0;
							info.offset = 0;
							info.presentationTimeUs = 0;
							info.size = 0;
							switch (outputBufferIndex) {
							case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
								outputBuffers = mediaCodec.getOutputBuffers();
								if (outputBuffers.length != outputBuffersAsShort.length)
									outputBuffersAsShort = new ShortBuffer[outputBuffers.length];
								for (int i = 0; i < outputBuffers.length; i++) {
									outputBuffersAsShort[i] = outputBuffers[i].asShortBuffer();
									outputBuffersAsShort[i].limit(outputBuffersAsShort[i].capacity());
								}
								break;
							case MediaCodec.INFO_TRY_AGAIN_LATER:
								timedoutFrameCount++;
								if (timedoutFrameCount > 10) {
									//force an empty buffer with BUFFER_FLAG_END_OF_STREAM, because a few devices
									//return MediaCodec.INFO_TRY_AGAIN_LATER often when using only regular buffers
									okToProcessMoreInput = false;
									int inputBufferIndex;
									while ((inputBufferIndex = mediaCodec.dequeueInputBuffer(10000)) < 0) {
										if (!alive)
											return;
									}
									inputBuffers[inputBufferIndex].limit(0);
									inputBuffers[inputBufferIndex].position(0);
									mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, currentUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
								}
								info.size = Integer.MAX_VALUE;
								break OutputLoop;
							}
						}
						if (outputBufferIndex >= 0)
							timedoutFrameCount = 0;
					} catch (Throwable ex) {
						//exceptions usually happen here when the data inside inputBuffer was invalid
						//(this happens if the other thread overwrites our data before we actually
						//had a chance to use it... this is very rare and happens only when we are
						//not being able to process data as fast as the remote server sends)
						releaseMediaCodec();
						createMediaCodec(properties[0], properties[1], properties[2]);

						inputFrameSize = waitToReadMpegHeader(null);
						if (inputFrameSize < 0)
							return;

						continue;
					}

					//output data to audioTrack
					if (info.size > 0 && info.size < Integer.MAX_VALUE) {
						info.size >>= 1; //from bytes to shorts

						if (info.size > tmpShortOutput.length)
							tmpShortOutput = new short[info.size];

						outputBuffersAsShort[outputBufferIndex].position(info.offset >> 1); //from bytes to shorts
						outputBuffersAsShort[outputBufferIndex].get(tmpShortOutput, 0, info.size);

						//release the output buffer
						mediaCodec.releaseOutputBuffer(outputBufferIndex, false);

						//send the decoded audio to audioTrack
						synchronized (sync) {
							if (!alive)
								break;
							while (alive) {
								final int actuallyWrittenShorts = audioTrack.write(tmpShortOutput, 0, info.size);
								if (actuallyWrittenShorts == 0) {
									if (!playbackStarted) {
										playbackStarted = true;
										//start the playback as soon as the buffer is full
										audioTrack.setStereoVolume(0.0f, 0.0f);
										audioTrack.play();
										audioTrack.setStereoVolume(0.0f, 0.0f);
										if (handler != null)
											handler.sendMessageAtTime(Message.obtain(handler, preparedMsg, arg1, 0), SystemClock.uptimeMillis());
									}
									try {
										Thread.sleep(10);
									} catch (Throwable ex) {
										//just ignore
									}
								} else if (actuallyWrittenShorts < 0) {
									throw new IOException();
								} else {
									break;
								}
							}
						}
					} else if (info.size <= 0) {
						//just release the output buffer
						mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
					}

					if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						okToProcessMoreInput = true;
						currentUs = 0;
						mediaCodec.flush();
					}

					inputFrameSize = waitToReadMpegHeader(null);
					if (inputFrameSize < 0)
						return;
				}
			} catch (Throwable ex) {
				//abort everything!
				synchronized (sync) {
					if (!alive)
						return;
					if (handler != null)
						handler.sendMessageAtTime(Message.obtain(handler, errorMsg, arg1, IMediaPlayer.ERROR_IO), SystemClock.uptimeMillis());
				}
			} finally {
				if (wakeLock != null)
					wakeLock.release();
				releaseMediaCodec();
				synchronized (sync) {
					releaseAudioTrack();
				}
			}
		}
	}

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
			final ByteBuffer tmp = ByteBuffer.wrap(new byte[MAX_HEADER_PACKET_LENGTH << 1]);
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
							if (!waitForHeaders())
								return;
							final byte[] hdr = ("ICY 200 OK\r\nicy-notice1:This stream is generated by FPlay\r\nicy-notice2:SHOUTcast Distributed Network Audio Server/Linux v1.0\r\nicy-pub:-1\r\nicy-name:FPlay\r\nAccept-Ranges: none\r\nServer: Icecast 2.3.3-kh11\r\nCache-Control: no-cache, no-store\r\nPragma: no-cache\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Origin, Accept, X-Requested-With, Content-Type\r\nAccess-Control-Allow-Methods: GET, OPTIONS, HEAD\r\nConnection: close\r\nExpires: Mon, 26 Jul 1997 05:00:00 GMT" +
								((icyGenre != null && icyGenre.length() > 0 && icyGenre.length() <= 200) ? ("\r\nicy-genre:" + icyGenre) : "") +
								((icyUrl != null && icyUrl.length() > 0 && icyUrl.length() <= 200) ? ("\r\nicy-url:" + icyUrl) : "") +
								((icyBitRate > 0) ? ("\r\nicy-br:" + icyBitRate) : "") +
								"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes();
							tmp.limit(hdr.length);
							tmp.position(0);
							tmp.put(hdr, 0, hdr.length);
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
				int timeoutCount = 0;

				if (serverPortReady <= 0)
					return;
				serverSocket.socket().setSoTimeout(TIMEOUT);

				//first let's receive the player's command and send the header as soon as possible!
				readPlayerRequestAndSendResponseHeader();

				//now playerSocket will only be used for output and we will just ignore any bytes received hereafter

				while (alive && !finished) {
					//if we limit the amount to be written to MAX_PACKET_LENGTH bytes we won't block
					//playerSocket.write for long periods
					buffer.waitUntilCanRead(MAX_PACKET_LENGTH);
					try {
						buffer.commitRead(playerSocket.write(buffer.readBuffer));
						timeoutCount = 0;
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
				synchronized (sync) {
					if (!alive)
						return;
					if (handler != null)
						handler.sendMessageAtTime(Message.obtain(handler, errorMsg, arg1, (ex instanceof SocketTimeoutException) ? IMediaPlayer.ERROR_TIMED_OUT : ((ex instanceof FileNotFoundException) ? IMediaPlayer.ERROR_NOT_FOUND : IMediaPlayer.ERROR_IO)), SystemClock.uptimeMillis());
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

	public HttpStreamReceiver(Handler handler, int errorMsg, int preparedMsg, int metadataMsg, int urlMsg, int infoMsg, int arg1, int bytesBeforeDecoding, int msBeforePlaying, int audioSessionId, String path) throws MalformedURLException {
		this.url = (RadioStation.isRadioUrl(path) ? normalizeIcyUrl(RadioStation.extractUrl(path)) : new URL(path));
		this.path = path;
		sync = new Object();
		isPerformingFullPlayback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);
		//when isPerformingFullPlayback is true, we will need to use the data inside this buffer from Java very often!
		//when isPerformingFullPlayback is false, and we will be just acting as a man-in-the-middle, then create a very small buffer
		buffer = new CircularIOBuffer((!isPerformingFullPlayback || bytesBeforeDecoding <= 0) ? MIN_BUFFER_LENGTH : (bytesBeforeDecoding <= EXTERNAL_BUFFER_LENGTH ? EXTERNAL_BUFFER_LENGTH : (bytesBeforeDecoding + MIN_BUFFER_LENGTH)), !isPerformingFullPlayback);
		this.handler = handler;
		this.errorMsg = errorMsg;
		this.preparedMsg = preparedMsg;
		this.metadataMsg = metadataMsg;
		this.urlMsg = urlMsg;
		this.infoMsg = infoMsg;
		this.arg1 = arg1;
		this.audioSessionId = audioSessionId;
		bytesReceivedSoFar = -1;
		initialNetworkBufferLengthInBytes = ((!isPerformingFullPlayback || bytesBeforeDecoding <= 0) ? 0 : bytesBeforeDecoding);
		initialAudioBufferInMS = msBeforePlaying;
	}

	private boolean waitForHeaders() {
		//wait for the content-type and the rest of the header to arrive
		while (!headerOk && alive) {
			synchronized (sync) {
				if (!alive)
					return false;
				if (headerOk)
					break;
				try {
					sync.wait(10);
				} catch (Throwable ex) {
					//ignore the interruptions
				}
			}
		}
		return alive;
	}

	private int sendRequestAndParseResponse(int redirectCount) throws IOException {
		if (!alive)
			return -1;
		if (redirectCount >= 3)
			throw new FileNotFoundException();
		headerOk = false;
		contentType = null;
		icyName = null;
		icyUrl = null;
		icyGenre = null;
		icyBitRate = 0;
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
				return -1;
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
		//we will use writeBuffer as a temporary storage in this method!
		buffer.writeBuffer.limit(httpCommand.length);
		buffer.writeBuffer.position(0);
		buffer.writeBuffer.put(httpCommand, 0, httpCommand.length);
		buffer.writeBuffer.position(0);
		localSocket.write(buffer.writeBuffer);

		//first: wait to receive the HTTPx 2xx or ICYx 2xx response
		//second: check the content-type and the other icy-xxx headers
		//last: wait for an empty line (a line break followed by another line break)

		final byte[] tmpLine = new byte[128];
		int totalBytesRead = 0, lineLen = 0, timeoutCount = 0;
		buffer.writeBuffer.limit(0);
		buffer.writeBuffer.position(0);
		boolean okToGo = false, shouldRedirectOnCompletion = false;

		//if the header exceeds MIN_BUFFER_LENGTH bytes, something is likely to be wrong...
		while (alive && totalBytesRead < MIN_BUFFER_LENGTH) {
			try {
				if (buffer.writeBuffer.remaining() == 0) {
					//read at most MAX_HEADER_PACKET_LENGTH bytes at a time
					buffer.writeBuffer.limit(MAX_HEADER_PACKET_LENGTH);
					buffer.writeBuffer.position(0);
					final int readLen = localSocket.read(buffer.writeBuffer);
					timeoutCount = 0;
					if (readLen < 0)
						throw new IOException();
					if (readLen == 0)
						continue;
					buffer.writeBuffer.limit(readLen);
					buffer.writeBuffer.position(0);
				}
				final byte b = buffer.writeBuffer.get();
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
										return -1;
									closeSocket(clientSocket);
									clientSocket = null;
								}
								url = normalizeIcyUrl(contentType);
								return sendRequestAndParseResponse(redirectCount + 1);
							}
							//leave both buffers prepared before leaving
							final int leftovers = buffer.writeBuffer.remaining();
							buffer.readBuffer.limit(buffer.writeBuffer.limit());
							buffer.readBuffer.position(buffer.writeBuffer.position());
							buffer.writeBuffer.position(buffer.writeBuffer.limit());
							return leftovers;
						}
						throw new IOException();
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
									icyBitRate = br;
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

		throw new IOException();
	}

	private int resolveUrlAndSendRequest() throws Throwable {
		Throwable lastEx = null;

		int res;
		try {
			res = sendRequestAndParseResponse(0);
			if (res >= 0)
				return res;
		} catch (Throwable ex) {
			lastEx = ex;
		}

		if (!alive)
			return -1;

		synchronized (sync) {
			closeSocket(clientSocket);
			clientSocket = null;
		}

		final String[] parts = RadioStation.splitPath(path);
		//if there is nothing else we can do, just throw an exception
		if (parts == null)
			throw (lastEx == null ? new FileNotFoundException() : lastEx);

		try {
			synchronized (sync) {
				if (!alive)
					return -1;
				resolver = new RadioStationResolver(parts[1], parts[2], parts[3].equals("1"));
			}

			final String[] newPath = new String[1];
			final String newUrl = resolver.resolve(newPath);

			if (!alive)
				return -1;
			if (newUrl == null)
				throw new FileNotFoundException();

			url = normalizeIcyUrl(newUrl);

			res = sendRequestAndParseResponse(0);
			if (res >= 0) {
				//notify that the url has changed
				synchronized (sync) {
					if (!alive)
						return -1;
					if (handler != null)
						handler.sendMessageAtTime(Message.obtain(handler, urlMsg, arg1, 0, newPath[0]), SystemClock.uptimeMillis());
				}
				return res;
			}

			if (!alive)
				return -1;

			throw new FileNotFoundException();
		} finally {
			synchronized (sync) {
				if (resolver != null) {
					resolver.release();
					resolver = null;
				}
			}
		}
	}

	private int processMetadata(ByteBuffer metaByteBuffer, int metaCountdown) throws IOException {
		int len;
		if (metaCountdown < 0) {
			//first time: read the first byte which indicates the metadata's length
			metaByteBuffer.limit(1);
			metaByteBuffer.position(0);
		}
		if ((len = clientSocket.read(metaByteBuffer)) <= 0)
			return metaCountdown;
		bytesReceivedSoFar += len;
		final byte[] array = metaByteBuffer.array();
		if (metaCountdown < 0) {
			metaCountdown = (((int)array[0] & 0xFF) << 4);
			metaByteBuffer.limit(metaCountdown);
			metaByteBuffer.position(0);
		} else {
			metaCountdown -= len;
			if (metaCountdown <= 0) {
				//we finished reading all the metadata!
				metaCountdown = 0;
				int i = metaByteBuffer.position() - 1;
				while (i > 0 && array[i] == 0)
					i--;
				if (i >= 0) {
					final String metadata = detectCharsetAndDecode(array, 0, i + 1);
					synchronized (sync) {
						if (handler != null)
							handler.sendMessageAtTime(Message.obtain(handler, metadataMsg, arg1, 0, new Metadata(metadata, icyName, icyUrl)), SystemClock.uptimeMillis());
					}
				}
			}
		}
		return metaCountdown;
	}

	@Override
	public void run() {
		try {
			int timeoutCount = 0, metaCountdown = 0, bufferingCounter, audioCountdown;

			if ((bufferingCounter = resolveUrlAndSendRequest()) < 0)
				return;

			//we only support mpeg streams
			if (!contentType.equalsIgnoreCase("audio/mpeg")) {
				//abort everything!
				synchronized (sync) {
					if (!alive)
						return;
					if (handler != null)
						handler.sendMessageAtTime(Message.obtain(handler, errorMsg, arg1, IMediaPlayer.ERROR_UNSUPPORTED_FORMAT), SystemClock.uptimeMillis());
				}
				return;
			} else {
				headerOk = true;
				synchronized (sync) {
					sync.notify();
				}
			}

			audioCountdown = icyMetaInterval - bufferingCounter;

			//do not allocate a direct buffer for the metadata, as we will have to constantly access that data from Java
			final ByteBuffer metaByteBuffer = ((icyMetaInterval > 0) ? ByteBuffer.wrap(new byte[MAX_METADATA_LENGTH]) : null);

			//from now on, just fill our local buffer
			while (alive) {
				int len;

				if (metaByteBuffer != null && metaCountdown != 0) {
					try {
						metaCountdown = processMetadata(metaByteBuffer, metaCountdown);
					} catch (SocketTimeoutException ex) {
						timeoutCount++;
						if (timeoutCount >= MAX_TIMEOUT_COUNT)
							throw ex;
					}
					continue;
				}

				//if we limit the amount to be read to MAX_PACKET_LENGTH bytes we won't block
				//clientSocket.read for long periods (also to consider: we cannot read metadata as audio)
				buffer.waitUntilCanWrite((MAX_PACKET_LENGTH <= audioCountdown || metaByteBuffer == null) ? MAX_PACKET_LENGTH : audioCountdown);

				try {
					if ((len = clientSocket.read(buffer.writeBuffer)) < 0) {
						//that's it! end of stream (probably this was just a file rather than a stream...)
						finished = true;
						buffer.commitWritten(0);
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
						if (bufferingCounter >= initialNetworkBufferLengthInBytes) {
							buffer.commitWritten(bufferingCounter);
							bufferingCounter = -1;
						}
					} else {
						buffer.commitWritten(len);
					}
				} catch (SocketTimeoutException ex) {
					timeoutCount++;
					if (timeoutCount >= MAX_TIMEOUT_COUNT)
						throw ex;
				}
			}
		} catch (Throwable ex) {
			//abort everything!
			synchronized (sync) {
				if (!alive)
					return;
				if (handler != null)
					handler.sendMessageAtTime(Message.obtain(handler, errorMsg, arg1, (ex instanceof SocketTimeoutException) ? 0 : ((ex instanceof FileNotFoundException) ? -1 : -2)), SystemClock.uptimeMillis());
			}
		} finally {
			synchronized (sync) {
				closeSocket(clientSocket);
				clientSocket = null;
			}
		}
	}

	public boolean start() {
		if (alive || released)
			return false;
		if (clientThread != null) {
			try {
				clientThread.join(50000);
			} catch (Throwable ex) {
				//we tried to wait...
			}
		}
		if (serverThread != null) {
			try {
				serverThread.join(50000);
			} catch (Throwable ex) {
				//we tried to wait...
			}
		}

		alive = true;

		//reset the internal state
		headerOk = false;
		contentType = null;
		icyName = null;
		icyUrl = null;
		icyGenre = null;
		icyBitRate = 0;
		icyMetaInterval = 0;
		bytesReceivedSoFar = -1;
		buffer.reset();

		clientThread = new Thread(this, "HttpStreamReceiver Client");
		clientThread.setDaemon(true);
		serverThread = new Thread(isPerformingFullPlayback ? new FullPlaybackRunnable() : new ServerRunnable(), "HttpStreamReceiver Server");
		serverThread.setDaemon(true);
		clientThread.start();
		serverThread.start();
		if (isPerformingFullPlayback)
			return true;
		while (alive && serverPortReady == 0) {
			synchronized (sync) {
				try {
					sync.wait(10);
				} catch (Throwable ex) {
					serverPortReady = -1;
				}
			}
		}
		return (serverPortReady > 0);
	}

	@SuppressWarnings("deprecation")
	public void setVolume(float left, float right) {
		if (!isPerformingFullPlayback || !alive || released)
			return;
		//let's remove the synchronization for the sake of speed during the fadeins
		//synchronized (sync) {
			try {
				if (audioTrack != null)
					audioTrack.setStereoVolume(left, right);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		//}
	}

	public void pause() {
		if (!isPerformingFullPlayback || !alive || released)
			return;
		alive = false;
		synchronized (sync) {
			try {
				if (audioTrack != null)
					audioTrack.pause();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			releaseThreadsAndSockets();
		}
		bytesReceivedSoFar = -1;
	}

	private void releaseThreadsAndSockets() {
		buffer.abortPendingReadsAndWrites();
		serverPortReady = 0;
		if (serverThread != null)
			serverThread.interrupt();
		if (clientThread != null)
			clientThread.interrupt();
		closeSocket(clientSocket);
		clientSocket = null;
		closeSocket(playerSocket);
		playerSocket = null;
		closeSocket(serverSocket);
		serverSocket = null;
		sync.notifyAll();
	}

	public void release() {
		alive = false;
		released = true;
		synchronized (sync) {
			if (resolver != null) {
				resolver.release();
				resolver = null;
			}
			handler = null;
			releaseThreadsAndSockets();
			clientThread = null;
			serverThread = null;
		}
	}

	public String getLocalURL() {
		return ((serverPortReady <= 0) ? null : ("http://127.0.0.1:" + serverPortReady + "/"));
	}

	private static final int[][][] MPEG_BIT_RATE = {
		//MPEG 2 & 2.5
		{
			{0,  8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000,112000,128000,144000,160000,0}, //Layer III
			{0,  8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000,112000,128000,144000,160000,0}, //Layer II
			{0, 32000, 48000, 56000, 64000, 80000, 96000,112000,128000,144000,160000,176000,192000,224000,256000,0}  //Layer I
		},
		//MPEG 1
		{
			{0, 32000, 40000, 48000, 56000, 64000, 80000, 96000,112000,128000,160000,192000,224000,256000,320000,0}, //Layer III
			{0, 32000, 48000, 56000, 64000, 80000, 96000,112000,128000,160000,192000,224000,256000,320000,384000,0}, //Layer II
			{0, 32000, 64000, 96000,128000,160000,192000,224000,256000,288000,320000,352000,384000,416000,448000,0}  //Layer I
		}
	};
	private static final int[][] MPEG_SAMPLE_RATE = {
		{11025, 12000,  8000, 0}, //MPEG 2.5
		{    0,     0,     0, 0}, //reserved
		{22050, 24000, 16000, 0}, //MPEG 2
		{44100, 48000, 32000, 0}  //MPEG 1
	};
	private static final int[][] MPEG_SAMPLES_PER_FRAME = {
		//MPEG 2 & 2.5
		{
			576,  //Layer III
			1152, //Layer II
			384   //Layer I
		},
		//MPEG 1
		{   1152, //Layer III
			1152, //Layer II
			384   //Layer I
		}
	};
	private static final int[][] MPEG_COEFF = {
		//MPEG 2 & 2.5
		{
			72,  // Layer III
			144, // Layer II
			12   // Layer I (must be multiplied with 4, because of slot size)
		},
		//MPEG 1
		{
			144, // Layer III
			144, // Layer II
			12   // Layer I (must be multiplied with 4, because of slot size)
		}
	};

	private int isByteAtOffsetAValidMpegHeader(int offset, int[] properties) {
		//header byte 0
		int b = buffer.peekReadArray(offset);

		if (b == 0x49 && buffer.peekReadArray(offset + 1) == 0x44 && buffer.peekReadArray(offset + 2) == 0x33) {
			//process the ID3 tag by skipping it comletely
			buffer.waitUntilCanRead(10);

			if (!alive)
				return -1;

			//refer to MetadataExtractor.java -> extractID3v2Andv1()
			final int flags = buffer.peekReadArray(offset + 5);
			final int sizeBytes0 = buffer.peekReadArray(offset + 6);
			final int sizeBytes1 = buffer.peekReadArray(offset + 7);
			final int sizeBytes2 = buffer.peekReadArray(offset + 8);
			final int sizeBytes3 = buffer.peekReadArray(offset + 9);
			int size = ((flags & 0x10) != 0 ? 10 : 0) + //footer presence flag
				(
					(sizeBytes3 & 0x7f) |
						((sizeBytes2 & 0x7f) << 7) |
						((sizeBytes1 & 0x7f) << 14) |
						((sizeBytes0 & 0x7f) << 21)
				) + 10; //the first 10 bytes
			while (size > 0 && alive) {
				final int len = buffer.waitUntilCanRead((size >= MAX_PACKET_LENGTH) ? MAX_PACKET_LENGTH : size);
				size -= len;
				buffer.readBuffer.position(buffer.readBuffer.limit());
				buffer.commitRead(len);
			}

			if (!alive)
				return -1;

			//proceed as if none of this had happened
			buffer.waitUntilCanRead(4);
			b = buffer.peekReadArray(offset);
		}

		if (b != 0xFF)
			return -1;

		//could be the first byte of our header, let's just check the next 3 bytes

		//header byte 1
		b = buffer.peekReadArray(offset + 1);

		//11 bits: sync word
		if ((b & 0xE0) != 0xE0)
			return -1;

		//2 bits: version must be != 1
		final int version = ((b >>> 3) & 0x03);
		if (version == 1)
			return -1;

		//2 bits: version must be != 1
		final int layer = ((b >>> 1) & 0x03);
		if (layer == 0)
			return -1;

		//1 bit: error protection bit (ignored)

		//header byte 2
		b = buffer.peekReadArray(offset + 2);

		//4 bits: bit rate
		final int bitRate = MPEG_BIT_RATE[version & 1][layer - 1][b >>> 4];
		if (bitRate == 0)
			return -1;

		//2 bits: sample rate
		final int sampleRate = MPEG_SAMPLE_RATE[version][(b >>> 2) & 0x03];
		if (sampleRate == 0)
			return -1;

		//1 bit: padding (if == 1, add 1 slot to the frame size)
		final int padding = ((b >>> 1) & 0x01);

		//1 bit: private bit (ignored)

		//header byte 3
		b = buffer.peekReadArray(offset + 3);

		//2 bits: channel mode
		if (properties != null) {
			properties[0] = (((b >>> 6) == 3) ? 1 : 2); //channel count
			properties[1] = sampleRate;
			properties[2] = bitRate;
			properties[3] = MPEG_SAMPLES_PER_FRAME[version & 1][layer - 1];
		}

		//2 bits: mode extension (ignored)

		//1 bit: copyright (ignored)

		//1 bit: original (ignored)

		//2 bits: emphasis (must be != 2)
		if ((b & 0x03) == 2)
			return -1;

		int frameSize = (((MPEG_COEFF[version & 1][layer - 1] * bitRate) / sampleRate) + padding);
		if (layer == 3) //Layer I
			frameSize <<= 2; //slot size * 4

		return frameSize;
	}

	private int waitToReadMpegHeader(int[] properties) {
		//we will look for the MPEG header
		//http://www.mp3-tech.org/programmer/frame_header.html
		//https://en.wikipedia.org/wiki/MP3
		while (alive) {
			buffer.waitUntilCanRead(4);

			final int frameSize = isByteAtOffsetAValidMpegHeader(0, properties);
			if (frameSize <= 0) {
				buffer.advanceBufferAndCommitReadOneByteWithoutNotification();
				continue;
			}

			buffer.waitUntilCanRead(frameSize + 4);

			//if the byte at offset 0 was actually a header, then THERE MUST be another header
			//right after it (extra validation)
			if (isByteAtOffsetAValidMpegHeader(frameSize, properties) <= 0) {
				buffer.advanceBufferAndCommitReadOneByteWithoutNotification();
				continue;
			}

			return frameSize;
		}

		return -1;
	}

	public int canReadMpegHeader(int[] properties) {
		//we will look for the MPEG header
		//http://www.mp3-tech.org/programmer/frame_header.html
		//https://en.wikipedia.org/wiki/MP3
		while (alive) {
			if (buffer.canRead(4) < 0)
				break;

			final int frameSize = isByteAtOffsetAValidMpegHeader(0, properties);
			if (frameSize <= 0) {
				buffer.advanceBufferAndCommitReadOneByteWithoutNotification();
				continue;
			}

			if (buffer.canRead(frameSize + 4) < 0)
				break;

			//if the byte at offset 0 was actually a header, then THERE MUST be another header
			//right after it (extra validation)
			if (isByteAtOffsetAValidMpegHeader(frameSize, properties) <= 0) {
				buffer.advanceBufferAndCommitReadOneByteWithoutNotification();
				continue;
			}

			return frameSize;
		}

		return -1;
	}
}
