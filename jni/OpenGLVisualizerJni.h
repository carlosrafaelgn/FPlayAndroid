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

//https://www.khronos.org/opengles/sdk/docs/man3/docbook4/xhtml/glDeleteBuffers.xml
//https://www.khronos.org/opengles/sdk/docs/man3/docbook4/xhtml/glVertexAttribPointer.xml
//https://www.khronos.org/opengles/sdk/docs/man3/docbook4/xhtml/glTexImage2D.xml
//https://www.khronos.org/opengles/sdk/docs/man3/docbook4/xhtml/glGenBuffers.xml

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

//used during Bluetooth processing
#define SIZE_4 ((int)'0')
#define SIZE_8 ((int)'1')
#define SIZE_16 ((int)'2')
#define SIZE_32 ((int)'3')
#define SIZE_64 ((int)'4')
#define SIZE_128 ((int)'5')
#define SIZE_256 ((int)'6')

static unsigned int glProgram, glVShader, glFShader, glTex[2], glBuf[2], glColorIndex;
static float glNew;

void JNICALL glChangeColorIndex(JNIEnv* env, jclass clazz, int jcolorIndex) {
	glColorIndex = jcolorIndex;
}

void JNICALL glChangeSpeed(JNIEnv* env, jclass clazz, int speed) {
	switch (speed) {
	case 1:
		glNew = 0.09375f / 16.0f; //0.09375 @ 60fps (~16ms)
		break;
	case 2:
		glNew = 0.140625f / 16.0f;
		break;
	default:
		glNew = 0.0625f / 16.0f;
		break;
	}
}

int formatRGB(int x, char* str) {
	str[1] = '.';
	if (x >= 255) {
		str[0] = '1';
		str[2] = '0';
		return 3;
	}
	str[0] = '0';
	if (x <= 0) {
		str[2] = '0';
		return 3;
	}
	str += 2 + 5;
	x = (x * 1000000) / 255;
	//x ranges from 003921 to 996078
	int digits = 0;
	while (digits < 6) {
		*str = '0' + (x % 10);
		x /= 10;
		str--;
		digits++;
	}
	return 8;
}

int JNICALL glOnSurfaceCreated(JNIEnv* env, jclass clazz, int bgColor) {
	const int bgR = ((bgColor >> 16) & 0xff);
	const int bgG = ((bgColor >> 8) & 0xff);
	const int bgB = (bgColor & 0xff);
	int l, l2;
	glProgram = glCreateProgram();
	if (glGetError() || !glProgram) return -1;
	glVShader = glCreateShader(GL_VERTEX_SHADER);
	if (glGetError() || !glVShader) return -2;
	glFShader = glCreateShader(GL_FRAGMENT_SHADER);
	if (glGetError() || !glFShader) return -3;
	const char* vertexShader = "attribute vec4 inPosition; attribute vec2 inTexCoord; varying vec2 vTexCoord; void main() { gl_Position = inPosition; vTexCoord = inTexCoord; }";
	l = strlen(vertexShader);
	glShaderSource(glVShader, 1, &vertexShader, &l);
	if (glGetError()) return -4;
	
	const char* fragmentShader1 = "precision mediump float; uniform sampler2D texAmplitude; uniform sampler2D texColor; varying vec2 vTexCoord; void main() {" \
	"vec4 c = texture2D(texAmplitude, vec2(" \
	"vTexCoord.x" \
	/*"(vTexCoord.x < 0.25) ? (vTexCoord.x * 0.5) : (((vTexCoord.x - 0.25) * 1.167) + 0.125)"*/ \
	", 0.0)); if (vTexCoord.y <= c.a && vTexCoord.y >= -c.a) {" \
	"gl_FragColor = texture2D(texColor, c.ar);" \
	"} else {" \
	"gl_FragColor = vec4(";
	
	//sprintf causes SERIOUS issues on some devices when using %.8f, %.8f, %.8f
	
	const char* fragmentShader2 = ",1.0);" \
	"} }";
	
	//memory reuse FTW! :)
	char* tmp = (char*)floatBuffer;
	l = strlen(fragmentShader1);
	memcpy(tmp, fragmentShader1, l);
	tmp += l;
	
	l2 = formatRGB(bgR, tmp);
	l += l2;
	tmp += l2;
	*tmp = ',';
	l++;
	tmp++;
	
	l2 = formatRGB(bgG, tmp);
	l += l2;
	tmp += l2;
	*tmp = ',';
	l++;
	tmp++;
	
	l2 = formatRGB(bgB, tmp);
	l += l2;
	tmp += l2;
	
	l2 = strlen(fragmentShader2);
	l += l2;
	memcpy(tmp, fragmentShader2, l2 + 1); //copy the null char
	
	const char* fragmentShader = (const char*)floatBuffer;
	glShaderSource(glFShader, 1, &fragmentShader, &l);
	if (glGetError()) return -5;
	glCompileShader(glVShader);
	if (glGetError()) return -6;
	glCompileShader(glFShader);
	if (glGetError()) return -7;
	glAttachShader(glProgram, glVShader);
	if (glGetError()) return -8;
	glAttachShader(glProgram, glFShader);
	if (glGetError()) return -9;
	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -10;
	glBindAttribLocation(glProgram, 1, "inTexCoord");
	if (glGetError()) return -11;
	glLinkProgram(glProgram);
	if (glGetError()) return -12;

	//create a rectangle that occupies the entire screen
	#define left -1.0f
	#define top 1.0f
	#define right 1.0f
	#define bottom -1.0f
	#define z 0.0f
	const float vertices[] = {
		left, bottom, z, 1.0f,
		right, bottom, z, 1.0f,
		left, top, z, 1.0f,
		right, top, z, 1.0f
	};
	#undef left
	#undef top
	#undef right
	#undef bottom
	#undef z
	#define leftTex 0.0f
	#define topTex 1.0f
	#define rightTex 1.0f
	#define bottomTex -1.0f
	const float texCoords[] = {
		leftTex, bottomTex,
		rightTex, bottomTex,
		leftTex, topTex,
		rightTex, topTex
	};
	#undef leftTex
	#undef topTex
	#undef rightTex
	#undef bottomTex
	glBuf[0] = 0;
	glBuf[1] = 0;
	glGenBuffers(2, glBuf);
	if (glGetError() || !glBuf[0] || !glBuf[1]) return -13;
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glBufferData(GL_ARRAY_BUFFER, (4 << 2) * sizeof(float), vertices, GL_STATIC_DRAW);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glBufferData(GL_ARRAY_BUFFER, (4 << 1) * sizeof(float), texCoords, GL_STATIC_DRAW);
	if (glGetError()) return -14;
	
	glClearColor(bgR, bgG, bgB, 1.0f);
	glDisable(GL_DEPTH_TEST);
	glDisable(GL_CULL_FACE);
	glDisable(GL_DITHER);
	glDisable(GL_SCISSOR_TEST);
	glDisable(GL_STENCIL_TEST);
	glDisable(GL_BLEND);
	//glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
	glEnable(GL_TEXTURE_2D);
	glGetError(); //clear any eventual error flags
	
	glTex[0] = 0;
	glTex[1] = 0;
	glGenTextures(2, glTex);
	if (glGetError() || !glTex[0] || !glTex[1]) return -15;
	
	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex[0]);
	if (glGetError()) return -16;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	memset(floatBuffer + 512, 0, 256);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)(floatBuffer + 512));
	if (glGetError()) return -17;

	glActiveTexture(GL_TEXTURE1);
	glBindTexture(GL_TEXTURE_2D, glTex[1]);
	if (glGetError()) return -18;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	if (glGetError()) return -19;
	
	glUseProgram(glProgram);
	glUniform1i(glGetUniformLocation(glProgram, "texAmplitude"), 0);
	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 1);
	if (glGetError()) return -20;
	
	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);
	glEnableVertexAttribArray(0);
	glEnableVertexAttribArray(1);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, 0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
	
	colorIndex = 1; //to make (colorIndex != glColorIndex) become true inside glDrawFrame
	updateMultiplier(env, clazz, 0);
	
	return 0;
}

void JNICALL glOnSurfaceChanged(JNIEnv* env, jclass clazz, int width, int height) {
	glViewport(0, 0, width, height);
}

int JNICALL glOrBTProcess(JNIEnv* env, jclass clazz, jbyteArray jbfft, int deltaMillis, int bt) {
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetPrimitiveArrayCritical(jbfft, 0);
	if (!bfft)
		return 0;
	unsigned char* processedData = (unsigned char*)(floatBuffer + 512);
	const float coefNew = glNew * (float)deltaMillis;
	const float coefOld = 1.0f - coefNew;
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = 0;
	int i;
	for (i = 0; i < 256; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		int amplSq = (re * re) + (im * im);
		if (amplSq < 3) amplSq = 0;
		float m = multiplier[i] * (float)(amplSq);
		const float old = fft[i];
		if (m < old)
			m = (coefNew * m) + (coefOld * old);
		fft[i] = m;
		//v goes from 0 to 32768+ (inclusive)
		const unsigned int v = ((unsigned int)m) >> 7;
		processedData[i] = ((v >= 255) ? 255 : (unsigned char)v);
	}
	if (!bt) {
		env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
		return 0;
	}
#define PACK_BIN(BIN) if ((BIN) == 0x01 || (BIN) == 0x1B) { *packet = 0x1B; packet[1] = ((unsigned char)(BIN) ^ 1); packet += 2; len += 2; } else { *packet = (unsigned char)(BIN); packet++; len++; }
	unsigned char* packet = (unsigned char*)bfft;
	int len = 0, last;
	unsigned int avg;
	unsigned char b;
	packet[0] = 1; //SOH - Start of Heading
	packet[1] = (unsigned char)bt; //payload type
	//packet[2] and packet[3] are the payload length
	packet += 4;
	//processedData stores the first 256 bins, out of the 512 captured by visualizer.getFft
	//which represents frequencies from DC to SampleRate / 4 (roughly from 0Hz to 11000Hz for a SR of 44100Hz)
	//
	//the mapping algorithms used in SIZE_4, SIZE_8, SIZE_16, SIZE_32, SIZE_64 and in SIZE_128
	//were "empirically created", without too much of theory envolved ;)
	switch (bt) {
	case SIZE_4:
		avg = ((unsigned int)processedData[1] + (unsigned int)processedData[2] + (unsigned int)processedData[3] + (unsigned int)processedData[4]) >> 2;
		PACK_BIN(avg);
		avg = 0;
		for (i = 5; i < 37; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 5;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 101; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 6;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 229; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 7;
		PACK_BIN(avg);
		break;
	case SIZE_8:
		avg = ((unsigned int)processedData[1] + (unsigned int)processedData[2]) >> 1;
		PACK_BIN(avg);
		avg = ((unsigned int)processedData[3] + (unsigned int)processedData[4]) >> 1;
		PACK_BIN(avg);
		avg = 0;
		for (i = 5; i < 21; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 4;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 37; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 4;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 69; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 5;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 101; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 5;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 165; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 6;
		PACK_BIN(avg);
		avg = 0;
		for (; i < 229; i++)
			avg += (unsigned int)processedData[i];
		avg >>= 6;
		PACK_BIN(avg);
		break;
	case SIZE_16:
		avg = ((unsigned int)processedData[1] + (unsigned int)processedData[2]) >> 1;
		PACK_BIN(avg);
		avg = ((unsigned int)processedData[3] + (unsigned int)processedData[4]) >> 1;
		PACK_BIN(avg);
		for (i = 5; i < 21; i += 4) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2;
			PACK_BIN(avg);
		}
		for (last = 29; last <= 37; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg += (unsigned int)processedData[i];
			avg >>= 3;
			PACK_BIN(avg);
		}
		for (last = 53; last <= 101; last += 16) {
			avg = 0;
			for (; i < last; i++)
				avg += (unsigned int)processedData[i];
			avg >>= 4;
			PACK_BIN(avg);
		}
		for (last = 133; last <= 229; last += 32) {
			avg = 0;
			for (; i < last; i++)
				avg += (unsigned int)processedData[i];
			avg >>= 5;
			PACK_BIN(avg);
		}
		break;
	case SIZE_32:
		b = processedData[1];
		PACK_BIN(b);
		b = processedData[2];
		PACK_BIN(b);
		b = processedData[3];
		PACK_BIN(b);
		b = processedData[4];
		PACK_BIN(b);
		for (i = 5; i < 21; i += 2) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1;
			PACK_BIN(avg);
		}
		for (; i < 37; i += 4) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2;
			PACK_BIN(avg);
		}
		for (last = 45; last <= 101; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg += (unsigned int)processedData[i];
			avg >>= 3;
			PACK_BIN(avg);
		}
		for (last = 117; last <= 229; last += 16) {
			avg = 0;
			for (; i < last; i++)
				avg += (unsigned int)processedData[i];
			avg >>= 4;
			PACK_BIN(avg);
		}
		break;
	case SIZE_64:
		for (i = 1; i < 21; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		for (; i < 37; i += 2) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1;
			PACK_BIN(avg);
		}
		for (; i < 133; i += 4) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2;
			PACK_BIN(avg);
		}
		for (last = 141; last <= 229; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg += (unsigned int)processedData[i];
			avg >>= 3;
			PACK_BIN(avg);
		}
		break;
	case SIZE_128:
		for (i = 1; i < 37; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		for (; i < 185; i += 2) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1;
			PACK_BIN(avg);
		}
		for (; i < 253; i += 4) {
			avg = ((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2;
			PACK_BIN(avg);
		}
		break;
	case SIZE_256:
		for (i = 0; i < 256; i++) {
			b = processedData[i];
			PACK_BIN(b);
		}
		break;
	default:
		env->ReleasePrimitiveArrayCritical(jbfft, bfft, JNI_ABORT);
		return 0;
	}
#undef PACK_BIN
	//fill in the payload length
	((unsigned char*)bfft)[2] = (len & 0x7F) << 1; //lower 7 bits, left shifted by 1
	((unsigned char*)bfft)[3] = (len >> 6) & 0xFE; //upper 7 bits, left shifted by 1
	*packet = 4; //EOT - End of Transmission
	env->ReleasePrimitiveArrayCritical(jbfft, bfft, 0);
	return len + 5;
}

void JNICALL glDrawFrame(JNIEnv* env, jclass clazz) {
	if (colorIndex != glColorIndex) {
		colorIndex = glColorIndex;
		glActiveTexture(GL_TEXTURE1);
		for (int i = 0, idx = colorIndex; i < 256; i++, idx++) {
			const unsigned int c = (unsigned int)COLORS[idx];
			unsigned int r = (c >> 11) & 0x1f;
			unsigned int g = (c >> 5) & 0x3f;
			unsigned int b = c & 0x1f;
			r = ((r << 3) | (r >> 2));
			g = ((g << 2) | (g >> 4)) << 8;
			b = ((b << 3) | (b >> 2)) << 16;
			((unsigned int*)floatBuffer)[i] = 0xff000000 | r | g | b;
		}
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 256, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, (unsigned char*)floatBuffer);
		updateMultiplier(env, clazz, 0);
		glActiveTexture(GL_TEXTURE0);
	}
	//glUseProgram(glProgram);
	//glClear(GL_COLOR_BUFFER_BIT);
	//glActiveTexture(GL_TEXTURE0);
	//glBindTexture(GL_TEXTURE_2D, glTex[0]);
	//glActiveTexture(GL_TEXTURE1);
	//glBindTexture(GL_TEXTURE_2D, glTex[1]);
	//glEnableVertexAttribArray(0);
	//glEnableVertexAttribArray(1);
	//refresh the texture
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)(floatBuffer + 512));
	//draw the rectangle (4 vertices)
	//glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	//glVertexAttribPointer(0, 4, GL_FLOAT, false, 0, 0);
	//glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	//glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}
