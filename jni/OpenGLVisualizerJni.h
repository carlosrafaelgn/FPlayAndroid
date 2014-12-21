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

#include <android/bitmap.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

//These constants must be kept synchronized with the ones in Java
#define ERR_INFO -1
#define ERR_FORMAT -2
#define ERR_LOCK -3
#define ERR_NOMEM -4
#define ERR_GL -5

#define TYPE_FULLSCREEN 0
#define TYPE_MAG 1
#define TYPE_MAG_REV 2

static unsigned int glProgram, glProgram2, glType;

int createProgram(const char* vertexShaderSource, const char* fragmentShaderSource, unsigned int* program) {
	int l;
	unsigned int p, vertexShader, fragmentShader;

	p = glCreateProgram();
	if (glGetError() || !p) return -1;
	vertexShader = glCreateShader(GL_VERTEX_SHADER);
	if (glGetError() || !vertexShader) return -2;
	fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
	if (glGetError() || !fragmentShader) return -3;

	l = strlen(vertexShaderSource);
	glShaderSource(vertexShader, 1, &vertexShaderSource, &l);
	if (glGetError()) return -4;

	l = strlen(fragmentShaderSource);
	glShaderSource(fragmentShader, 1, &fragmentShaderSource, &l);
	if (glGetError()) return -5;

	glCompileShader(vertexShader);
	l = 0;
	glGetShaderiv(vertexShader, GL_COMPILE_STATUS, &l);
	if (glGetError() || !l) return -6;

	glCompileShader(fragmentShader);
	l = 0;
	glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, &l);
	if (glGetError() || !l) return -7;

	glAttachShader(p, vertexShader);
	if (glGetError()) return -8;
	glAttachShader(p, fragmentShader);
	if (glGetError()) return -9;

	*program = p;

	return 0;
}

int JNICALL glOnSurfaceCreated(JNIEnv* env, jclass clazz, int bgColor, int type) {
	glType = type;

	int l;
	unsigned int glTex[2], glBuf[4];
	const char* vertexShader;
	const char* fragmentShader;

	if (type == TYPE_MAG || type == TYPE_MAG_REV)
		vertexShader = "attribute float inPosition; attribute float inTexCoord; varying vec2 vTexCoord; varying float vAmpl; uniform sampler2D texAmplitude; void main() {" \
		"vec2 coord = vec2((inPosition + 1.0) * 0.5, 0.0);" \
		"float absy;" \
		"if (inTexCoord < 0.0) {" \
			"vAmpl = 0.0;" \
			"gl_Position = vec4(inPosition, 1.0, 0.0, 1.0);" \
		"} else {" \
			"if (inTexCoord < 1.0) {" \
				"absy = texture2D(texAmplitude, vec2(inTexCoord * 2.0, 0.0)).a;" \
			"} else {" \
				"coord.y = floor(inTexCoord) - 1.0;" \
				"absy = texture2D(texAmplitude, vec2(coord.y / 992.0, 0.0)).a;" \
				"absy += (texture2D(texAmplitude, vec2((coord.y + 32.0) / 992.0, 0.0)).a - absy) * smoothstep(0.0, 1.0, fract(inTexCoord));" \
			"}" \
			"vAmpl = 1.0;" \
			"gl_Position = vec4(inPosition, (absy * 2.0) - 1.0, 0.0, 1.0);" \
			"coord.y = 1.0 - absy;" \
		"}" \
		"vTexCoord = coord; }";
	else
		vertexShader = "attribute float inPosition; varying vec4 vColor; uniform sampler2D texAmplitude; uniform sampler2D texColor; void main() {" \
		"float absx = abs(inPosition);" \
		"vec4 c = texture2D(texAmplitude, vec2(0.5 * (absx - 1.0), 0.0));" \
		"gl_Position = vec4(absx - 2.0, sign(inPosition) * c.a, 0.0, 1.0);" \
		"vColor = texture2D(texColor, c.ar); }";

	if (type == TYPE_MAG || type == TYPE_MAG_REV)
		fragmentShader = "precision mediump float; varying vec2 vTexCoord; varying float vAmpl; uniform sampler2D texColor; void main() {" \
		"vec4 c = texture2D(texColor, vTexCoord);"
		"gl_FragColor = c + (vAmpl * 0.5); }";
	else
		fragmentShader = "precision mediump float; varying vec4 vColor; void main() { gl_FragColor = vColor; }";

	if ((l = createProgram(vertexShader, fragmentShader, &glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -10;
	if (type == TYPE_MAG || type == TYPE_MAG_REV) {
		glBindAttribLocation(glProgram, 1, "inTexCoord");
		if (glGetError()) return -11;
	}
	glLinkProgram(glProgram);
	if (glGetError()) return -12;

	if (type == TYPE_MAG || type == TYPE_MAG_REV) {
		if ((l = createProgram(
			"attribute vec4 inPosition; attribute vec2 inTexCoord; varying vec2 vTexCoord; void main() { gl_Position = inPosition; vTexCoord = inTexCoord; }",
			"precision mediump float; varying vec2 vTexCoord; uniform sampler2D texColor; void main() { gl_FragColor = texture2D(texColor, vTexCoord); }",
			&glProgram2)))
			return l;

		glBindAttribLocation(glProgram2, 2, "inPosition");
		if (glGetError()) return -10;
		glBindAttribLocation(glProgram2, 3, "inTexCoord");
		if (glGetError()) return -11;
		glLinkProgram(glProgram2);
		if (glGetError()) return -12;
	}

	glBuf[0] = 0;
	glBuf[1] = 0;
	glBuf[2] = 0;
	glBuf[3] = 0;

	if (type == TYPE_MAG || type == TYPE_MAG_REV) {
		glGenBuffers(4, glBuf);
		if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2] || !glBuf[3]) return -13;

		//in order to save memory and bandwidth, we are sending only the x coordinate
		//of each point, but with a few modifications...
		//
		//this array represents a horizontal triangle strip with x ranging from -1 to 1,
		//and which vertices are ordered like this:
		// 1  3  5  7
		//             ...
		// 0  2  4  6
		float *vertices = new float[1024];

		for (int i = 0; i < 512; i++) {
			const float p = -1.0f + (2.0f * (float)i / 511.0f);
			vertices[(i << 1)    ] = p;
			vertices[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

		//vertices at odd indices receive a -1, to indicate they are static, and must be placed
		//at the top of the screen
		for (int i = 1; i < 1024; i += 2) {
			vertices[i] = -1.0f;
		}
		//vertices at indices 0, 32, 64, 96 .... receive the coordinate where to read the
		//magnitude from the magnitude texture (from 0 to 0.5)
		for (int i = 0; i < 1024; i += 32) {
			vertices[i] = (float)(i >> 5) / 62.0f;
		}
		//vertices at indices 2..30, 34..62, 66..94 .... receive a coordinate to make the GPU's
		//life easier when computing smoothStep
		for (int start = 0; start < 1024; start += 32) {
			for (int i = 2; i < 32; i += 2) {
				vertices[start + i] = (float)start + 1.0f + ((float)i / 32.0f); // 0 < i/32 < 1 !!!
			}
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

		delete vertices;

		//create a rectangle that occupies the entire screen
		#define left -1.0f
		#define top 1.0f
		#define right 1.0f
		#define bottom -1.0f
		#define z 0.0f
		const float verticesC[] = {
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
		#define topTex 0.0f
		#define rightTex 1.0f
		#define bottomTex 1.0f
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
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 4) * sizeof(float), verticesC, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), texCoords, GL_STATIC_DRAW);

		if (glGetError()) return -14;
	} else {
		glGenBuffers(1, glBuf);
		if (glGetError() || !glBuf[0]) return -13;

		//in order to save memory and bandwidth, we are sending only the x coordinate
		//of each point, but with a few modifications...
		//
		//this array represents a horizontal triangle strip with x ranging from 1 to 3,
		//and which vertices are ordered like this:
		// 1  3  5  7
		//             ...
		// 0  2  4  6
		//
		//values at even indexes receive a negative value, so that sign() returns -1
		//in the vertex shader
		//
		//we cannot send x = 0, as sign(0) = 0, and this would render that point useless
		for (int i = 0; i < 256; i++) {
			//even is negative, odd is positive
			const float p = 1.0f + (2.0f * (float)i / 255.0f);
			floatBuffer[(i << 1)    ] = -p;
			floatBuffer[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (256 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
		if (glGetError()) return -14;

		glClearColor((float)((bgColor >> 16) & 0xff) / 255.0f, (float)((bgColor >> 8) & 0xff) / 255.0f, (float)(bgColor & 0xff) / 255.0f, 1.0f);
	}

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
	memset(floatBuffer, 0, 512);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, (type == TYPE_MAG || type == TYPE_MAG_REV) ? 32 : 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)floatBuffer);
	if (glGetError()) return -17;

	glActiveTexture(GL_TEXTURE1);
	glBindTexture(GL_TEXTURE_2D, glTex[1]);
	if (glGetError()) return -18;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	if (type == TYPE_MAG || type == TYPE_MAG_REV)
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 256, 1, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (unsigned char*)floatBuffer);
	if (glGetError()) return -19;

	glUseProgram(glProgram);
	if (glGetError()) return -20;

	glUniform1i(glGetUniformLocation(glProgram, "texAmplitude"), 0);
	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 1);
	if (glGetError()) return -21;

	if (type == TYPE_MAG || type == TYPE_MAG_REV) {
		glUseProgram(glProgram2);
		if (glGetError()) return -22;

		glUniform1i(glGetUniformLocation(glProgram2, "texColor"), 1);
		if (glGetError()) return -23;
	}

	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 1, GL_FLOAT, false, 0, 0);

	if (type == TYPE_MAG || type == TYPE_MAG_REV) {
		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glVertexAttribPointer(1, 1, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(2);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glVertexAttribPointer(2, 4, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(3);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);
	}
	if (glGetError()) return -24;

	commonColorIndex = (((type == TYPE_MAG) || (type == TYPE_MAG_REV)) ? 4 : 0);
	commonColorIndexApplied = 4; //to control the result of (commonColorIndexApplied != commonColorIndex) inside glDrawFrame
	commonUpdateMultiplier(env, clazz, 0);

	glReleaseShaderCompiler();

	return 0;
}

void JNICALL glOnSurfaceChanged(JNIEnv* env, jclass clazz, int width, int height) {
	glViewport(0, 0, width, height);
}

int JNICALL glLoadBitmapFromJava(JNIEnv* env, jclass clazz, jobject bitmap) {
	AndroidBitmapInfo inf;
	if (AndroidBitmap_getInfo(env, bitmap, &inf))
		return ERR_INFO;

	if (inf.format != ANDROID_BITMAP_FORMAT_RGB_565)
		return ERR_FORMAT;

	unsigned char *dst = 0;
	if (AndroidBitmap_lockPixels(env, bitmap, (void**)&dst))
		return ERR_LOCK;

	if (!dst) {
		AndroidBitmap_unlockPixels(env, bitmap);
		return ERR_NOMEM;
	}

	glActiveTexture(GL_TEXTURE1);
	glGetError(); //clear any eventual error flags
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, inf.width, inf.height, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, dst);
	int error = glGetError();
	glActiveTexture(GL_TEXTURE0);

	AndroidBitmap_unlockPixels(env, bitmap);

	//to make (commonColorIndexApplied != commonColorIndex) become false inside glDrawFrame
	commonColorIndex = 4;
	commonColorIndexApplied = 4;

	return (error ? ERR_GL : 0);
}

void JNICALL glDrawFrame(JNIEnv* env, jclass clazz) {
	if (commonColorIndexApplied != commonColorIndex) {
		commonColorIndexApplied = commonColorIndex;
		glActiveTexture(GL_TEXTURE1);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 256, 1, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (unsigned char*)(COLORS + commonColorIndex));
		glActiveTexture(GL_TEXTURE0);
	}

	if (glType == TYPE_MAG || glType == TYPE_MAG_REV) {
		glUseProgram(glProgram2);

		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

		glFinish();

		glUseProgram(glProgram);

		unsigned char* processedData = (unsigned char*)(floatBuffer + 512);

#define MAX(A,B) (((A) > (B)) ? (A) : (B))
		int i, idx = 4, last;
		unsigned char avg;
		unsigned char data[32];

		((unsigned int*)data)[0] = ((unsigned int*)processedData)[0];

		for (i = 4; i < 20; i += 2) {
			data[idx++] = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1);
		}
		for (; i < 36; i += 4) {
			data[idx++] = (unsigned char)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2);
		}
		for (last = 44; last <= 100; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 3;
			data[idx++] = avg;
		}
		for (last = 116; last <= 228; last += 16) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]); //avg += (unsigned int)processedData[i];
			//avg >>= 4;
			data[idx++] = avg;
		}
#undef MAX

		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 32, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, data);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 512 * 2);
	} else {
		glClear(GL_COLOR_BUFFER_BIT);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)(floatBuffer + 512));
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 256 * 2);
	}
}
