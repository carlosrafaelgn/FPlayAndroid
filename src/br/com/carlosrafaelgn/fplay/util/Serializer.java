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
package br.com.carlosrafaelgn.fplay.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings("unused")
public final class Serializer {
	public static void serializeInt(OutputStream os, int value) throws IOException {
		os.write(value);
		os.write(value >>> 8);
		os.write(value >>> 16);
		os.write(value >>> 24);
	}
	
	public static void serializeInt(byte[] output, int offset, int value) {
		output[offset] = (byte)value;
		output[offset + 1] = (byte)(value >>> 8);
		output[offset + 2] = (byte)(value >>> 16);
		output[offset + 3] = (byte)(value >>> 24);
	}
	
	public static void serializeLong(OutputStream os, long value) throws IOException {
		os.write((int)value);
		os.write((int)(value >>> 8));
		os.write((int)(value >>> 16));
		os.write((int)(value >>> 24));
		os.write((int)(value >>> 32));
		os.write((int)(value >>> 40));
		os.write((int)(value >>> 48));
		os.write((int)(value >>> 56));
	}
	
	public static void serializeLong(byte[] output, int offset, long value) {
		output[offset] = (byte)value;
		output[offset + 1] = (byte)(value >>> 8);
		output[offset + 2] = (byte)(value >>> 16);
		output[offset + 3] = (byte)(value >>> 24);
		output[offset + 4] = (byte)(value >>> 32);
		output[offset + 5] = (byte)(value >>> 40);
		output[offset + 6] = (byte)(value >>> 48);
		output[offset + 7] = (byte)(value >>> 56);
	}
	
	public static void serializeFloat(OutputStream os, float value) throws IOException {
		serializeInt(os, Float.floatToRawIntBits(value));
	}
	
	public static void serializeFloat(byte[] output, int offset, float value) {
		serializeInt(output, offset, Float.floatToRawIntBits(value));
	}
	
	public static void serializeDouble(OutputStream os, double value) throws IOException {
		serializeLong(os, Double.doubleToRawLongBits(value));
	}
	
	public static void serializeDouble(byte[] output, int offset, double value) {
		serializeLong(output, offset, Double.doubleToRawLongBits(value));
	}
	
	public static void serializeString(OutputStream os, String value) throws IOException {
		if (value == null) {
			serializeInt(os, -1);
		} else if (value.length() == 0) {
			serializeInt(os, 0);
		} else {
			final byte[] tmp = value.getBytes();
			serializeInt(os, tmp.length);
			os.write(tmp);
		}
	}
	
	public static int deserializeInt(InputStream is) throws IOException {
		return ((is.read() & 0xff) | ((is.read() & 0xff) << 8) | ((is.read() & 0xff) << 16) | (is.read() << 24));
	}
	
	public static int deserializeInt(byte[] input, int offset) {
		return (input[offset] & 0xff) | ((input[offset + 1] & 0xff) << 8) | ((input[offset + 2] & 0xff) << 16) | (input[offset + 3] << 24);
	}
	
	public static long deserializeLong(InputStream is) throws IOException {
		return ((long)
			(
				(is.read() & 0xff) | ((is.read() & 0xff) << 8) | ((is.read() & 0xff) << 16) | (is.read() << 24)
			) & 0xFFFFFFFFL) |
			((long)(
				(is.read() & 0xff) | ((is.read() & 0xff) << 8) | ((is.read() & 0xff) << 16) | (is.read() << 24)
			) << 32);
	}
	
	public static long deserializeLong(byte[] input, int offset) {
		return ((long)
			(
				(input[offset] & 0xff) | ((input[offset + 1] & 0xff) << 8) | ((input[offset + 2] & 0xff) << 16) | (input[offset + 3] << 24)
			) & 0xFFFFFFFFL) |
			((long)(
				(input[offset + 4] & 0xff) | ((input[offset + 5] & 0xff) << 8) | ((input[offset + 6] & 0xff) << 16) | (input[offset + 7] << 24)
			) << 32);
	}
	
	public static float deserializeFloat(InputStream is) throws IOException {
		return Float.intBitsToFloat(deserializeInt(is));
	}
	
	public static float deserializeFloat(byte[] input, int offset) {
		return Float.intBitsToFloat(deserializeInt(input, offset));
	}
	
	public static double deserializeDouble(InputStream is) throws IOException {
		return Double.longBitsToDouble(deserializeLong(is));
	}
	
	public static double deserializeDouble(byte[] input, int offset) {
		return Double.longBitsToDouble(deserializeLong(input, offset));
	}
	
	public static String deserializeString(InputStream is) throws IOException {
		int len = deserializeInt(is);
		if (len < 0)
			return null;
		if (len == 0)
			return "";
		final byte[] tmp = new byte[len];
		len = is.read(tmp, 0, len);
		return new String(tmp, 0, len);
	}
}
