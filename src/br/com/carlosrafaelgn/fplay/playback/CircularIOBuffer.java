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

import android.os.SystemClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public final class CircularIOBuffer {
	private volatile boolean alive, finished;
	private volatile int filledSize;
	private final Object sync;
	private long lastWriteTime;
	public final int capacity;
	public final byte[] array;
	public final ByteBuffer writeBuffer, readBuffer;

	public CircularIOBuffer(int capacity) {
		alive = true;
		sync = new Object();
		this.capacity = capacity;
		array = new byte[capacity];
		writeBuffer = ByteBuffer.wrap(array);
		readBuffer = writeBuffer.asReadOnlyBuffer();
		readBuffer.limit(readBuffer.capacity());
		lastWriteTime = SystemClock.elapsedRealtime();
	}

	public void reset() {
		synchronized (sync) {
			alive = true;
			finished = false;
			filledSize = 0;
			writeBuffer.limit(0);
			readBuffer.limit(readBuffer.capacity());
		}
	}

	public void abortPendingReadsAndWrites() {
		alive = false;
		synchronized (sync) {
			sync.notifyAll();
		}
	}

	private int canReadInternal(int length) {
		if (!alive)
			return -1;
		if (filledSize < length) {
			if (!finished)
				return -1;
			length = filledSize;
		}
		if (readBuffer.position() >= capacity)
			readBuffer.position(0);
		return length;
	}

	public int waitUntilCanRead(int length) {
		synchronized (sync) {
			while (alive && !finished && filledSize < length) {
				try {
					sync.wait(10);
				} catch (Throwable ex) {
					//ignore the interruptions
				}
			}
			return canReadInternal(length);
		}
	}

	public boolean canFinish() {
		synchronized (sync) {
			return (!alive || (finished && filledSize <= 0));
		}
	}

	public int canRead(int length) {
		synchronized (sync) {
			return canReadInternal(length);
		}
	}

	private void commitRead(int length) {
		synchronized (sync) {
			if (filledSize < length)
				throw new IllegalArgumentException("filledSize < length");
			filledSize -= length;
			sync.notifyAll();
		}
	}

	public int peek(int peekOffset) {
		peekOffset += readBuffer.position();
		return ((int)array[(peekOffset >= capacity) ? (peekOffset - capacity) : peekOffset] & 0xFF);
	}

	public void skip(int length) {
		final int readPosition = readBuffer.position();
		final int bytesAvailableBeforeEndOfBuffer = capacity - readPosition;
		if (bytesAvailableBeforeEndOfBuffer >= length) {
			//one copy would do it
			readBuffer.position(readPosition + length);
		} else {
			//two copies would be required
			readBuffer.position(length - bytesAvailableBeforeEndOfBuffer);
		}
		commitRead(length);
	}

	public void read(ByteBuffer dst, int dstOffset, int length) {
		final int originalLength = length;
		final int readPosition = readBuffer.position();
		final int bytesAvailableBeforeEndOfBuffer = capacity - readPosition;
		dst.limit(dstOffset + length);
		dst.position(dstOffset);
		if (bytesAvailableBeforeEndOfBuffer >= length) {
			//one copy will do it
			dst.put(array, readPosition, length);
			readBuffer.position(readPosition + length);
		} else {
			//two copies are required
			if (bytesAvailableBeforeEndOfBuffer > 0) {
				dst.put(array, readPosition, bytesAvailableBeforeEndOfBuffer);
				length -= bytesAvailableBeforeEndOfBuffer;
			}
			dst.put(array, 0, length);
			readBuffer.position(length);
		}
		dst.position(dstOffset);
		commitRead(originalLength);
	}

	public void read(byte[] dst, int dstOffset, int length) {
		final int originalLength = length;
		final int readPosition = readBuffer.position();
		final int bytesAvailableBeforeEndOfBuffer = capacity - readPosition;
		if (bytesAvailableBeforeEndOfBuffer >= length) {
			//one copy will do it
			System.arraycopy(array, readPosition, dst, dstOffset, length);
			readBuffer.position(readPosition + length);
		} else {
			//two copies are required
			if (bytesAvailableBeforeEndOfBuffer > 0) {
				System.arraycopy(array, readPosition, dst, dstOffset, bytesAvailableBeforeEndOfBuffer);
				length -= bytesAvailableBeforeEndOfBuffer;
			}
			System.arraycopy(array, 0, dst, dstOffset + bytesAvailableBeforeEndOfBuffer, length);
			readBuffer.position(length);
		}
		commitRead(originalLength);
	}

	private static void throttleChannel(int writtenBytes) throws InterruptedException {
		if (writtenBytes <= 1280) {
			Thread.sleep(10);
		} else {
			writtenBytes >>= 6;
			Thread.sleep((writtenBytes >= 200) ? 200 : writtenBytes);
		}
	}

	public void readIntoChannel(WritableByteChannel channel) throws IOException, InterruptedException {
		//all the Thread.sleep() in here are necessary, because old Androids consume data
		//much, much faster than it is produced, even on a fast network!!!
		if (channel == null)
			return;
		int length = filledSize;
		final int readPosition = readBuffer.position();
		final int bytesAvailableBeforeEndOfBuffer = capacity - readPosition;
		int written;
		if (bytesAvailableBeforeEndOfBuffer >= length) {
			//one copy will do it
			readBuffer.limit(readPosition + length);
			if ((written = channel.write(readBuffer)) > 0) {
				commitRead(written);
				throttleChannel(written);
			} else {
				throttleChannel(0);
			}
		} else {
			//two copies are required
			if (bytesAvailableBeforeEndOfBuffer > 0) {
				readBuffer.limit(capacity);
				if ((written = channel.write(readBuffer)) > 0) {
					if (written != bytesAvailableBeforeEndOfBuffer) {
						//the channel did not consume all of our bytes, so it is needless to continue
						commitRead(written);
						throttleChannel(written);
						return;
					}
					length -= written;
				} else {
					throttleChannel(0);
					return;
				}
			}
			readBuffer.position(0);
			readBuffer.limit(length);
			if ((written = channel.write(readBuffer)) > 0) {
				written += bytesAvailableBeforeEndOfBuffer;
				commitRead(written);
				throttleChannel(written);
			} else {
				commitRead(bytesAvailableBeforeEndOfBuffer);
				throttleChannel(bytesAvailableBeforeEndOfBuffer);
			}
		}
	}

	public int waitUntilCanWrite(int length) {
		synchronized (sync) {
			while (alive && (capacity - filledSize) < length) {
				try {
					sync.wait(10);
				} catch (Throwable ex) {
					//ignore the interruptions
				}
			}
		}
		if (writeBuffer.position() >= capacity)
			writeBuffer.position(0);
		if (!alive)
			return -1;
		final int bytesAvailableBeforeEndOfBuffer = capacity - writeBuffer.position();
		if (length > bytesAvailableBeforeEndOfBuffer)
			length = bytesAvailableBeforeEndOfBuffer;
		writeBuffer.limit(writeBuffer.position() + length);
		return length;
	}

	public void commitWritten(int length) {
		if (length < 0)
			throw new IllegalArgumentException("length < 0");
		synchronized (sync) {
			if (capacity < (filledSize + length))
				throw new IllegalArgumentException("capacity < (filledSize + length)");
			filledSize += length;
			sync.notifyAll();
		}
	}

	public void commitWrittenFinished(int length) {
		if (length < 0)
			throw new IllegalArgumentException("length < 0");
		synchronized (sync) {
			if (capacity < (filledSize + length))
				throw new IllegalArgumentException("capacity < (filledSize + length)");
			finished = true;
			filledSize += length;
			sync.notifyAll();
		}
	}

	public int getFilledSize() {
		return filledSize;
	}
}
