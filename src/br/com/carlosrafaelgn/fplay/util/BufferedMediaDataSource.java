package br.com.carlosrafaelgn.fplay.util;

import android.annotation.TargetApi;
import android.media.MediaDataSource;
import android.os.Build;

import java.io.IOException;
import java.io.RandomAccessFile;

@TargetApi(Build.VERSION_CODES.M)
public final class BufferedMediaDataSource extends MediaDataSource {
	private final RandomAccessFile file;
	private final byte[] buffer;
	private final long fileLength;
	private long fileStartOffset, fileEndOffset;

	public BufferedMediaDataSource(String path, long length, int bufferSize) throws IOException {
		file = new RandomAccessFile(path, "r");
		buffer = new byte[bufferSize];
		fileLength = length;
	}

	@Override
	public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
		synchronized (this.buffer) {
			if (size > this.buffer.length) {
				file.seek(position);
				return file.read(buffer, offset, size);
			}
			int readSoFar = 0;
			if (position >= fileStartOffset && position < fileEndOffset) {
				if ((position + size) <= fileEndOffset) {
					System.arraycopy(this.buffer, (int)(position - fileStartOffset), buffer, offset, size);
					return size;
				} else {
					readSoFar = (int)(fileEndOffset - position);
					System.arraycopy(this.buffer, (int)(position - fileStartOffset), buffer, offset, readSoFar);
					position += readSoFar;
				}
			}
			fileStartOffset = position;
			if (position != fileEndOffset)
				file.seek(position);
			int total = file.read(this.buffer, 0, this.buffer.length);
			if (total < 0) {
				fileEndOffset = position;
				return ((readSoFar > 0) ? readSoFar : -1);
			}
			fileEndOffset = fileStartOffset + total;
			total = Math.min(size - readSoFar, (int)(fileEndOffset - position));
			System.arraycopy(this.buffer, 0, buffer, offset + readSoFar, total);
			return (readSoFar + total);
		}
	}

	@Override
	public long getSize() {
		return fileLength;
	}

	@Override
	public void close() throws IOException {
		file.close();
	}
}
