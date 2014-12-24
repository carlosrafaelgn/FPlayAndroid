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

#define TYPE_SPECTRUM 0
#define TYPE_LIQUID 1
#define TYPE_SPIN 2

#define left -1.0f
#define top 1.0f
#define right 1.0f
#define bottom -1.0f
#define z 0.0f
static const float glVerticesRect[] = {
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
static const float glTexCoordsRect[] = {
	leftTex, bottomTex,
	rightTex, bottomTex,
	leftTex, topTex,
	rightTex, topTex
};
#undef leftTex
#undef topTex
#undef rightTex
#undef bottomTex

static const char* const rectangleVShader = "attribute vec4 inPosition; attribute vec2 inTexCoord; varying vec2 vTexCoord; void main() { gl_Position = inPosition; vTexCoord = inTexCoord; }";

static unsigned int glProgram, glProgram2, glType, glBuf[4];
static int glTime, glAmplitude, glVerticesPerRow, glRows;

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
	glProgram = 0;
	glProgram2 = 0;
	glBuf[0] = 0;
	glBuf[1] = 0;
	glBuf[2] = 0;
	glBuf[3] = 0;
	glTime = 0;
	glAmplitude = 0;
	glVerticesPerRow = 0;
	glRows = 0;
	commonTime = 0;
	commonTimeLimit = ((type == TYPE_SPIN) ? 6283 : 12566); //2 * pi * 1000 : 2 * 2 * pi * 1000

	int l;
	unsigned int glTex[2];
	const char* vertexShader;
	const char* fragmentShader;

	switch (type) {
	case TYPE_LIQUID:
		vertexShader = "attribute float inPosition; attribute float inTexCoord; varying vec2 vTexCoord; varying float vAmpl; uniform float amplitude[33]; void main() {" \
		"vec2 coord = vec2((inPosition + 1.0) * 0.5, 0.0);" \
		"float absy;" \
		"if (inTexCoord < 0.0) {" \
			"vAmpl = 0.0;" \
			"gl_Position = vec4(inPosition, 1.0, 0.0, 1.0);" \
		"} else {" \
			"int i = int(inTexCoord);" \
			"absy = amplitude[i];" \
			"absy += (amplitude[i + 1] - absy) * smoothstep(0.0, 1.0, fract(inTexCoord));" \
			"vAmpl = 1.0;" \
			"gl_Position = vec4(inPosition, (absy * 2.0) - 1.0, 0.0, 1.0);" \
			"coord.y = 1.0 - absy;" \
		"}" \
		"vTexCoord = coord; }";
		break;
	case TYPE_SPIN:
		//inPosition stores the distance of the vertex to the origin in the z component
		//inTexCoord stores the angle of the vertex in the z component
		vertexShader = "attribute vec3 inPosition; attribute vec3 inTexCoord; varying vec2 vTexCoord; varying vec3 vColor; varying float dist; uniform float amplitude[33]; uniform float time; void main() {" \
		"gl_Position = vec4(inPosition.x, inPosition.y, 0.0, 1.0);" \
		"float d = inPosition.z;" \

		"vTexCoord = inTexCoord.xy;" \
		"float angle = inTexCoord.z - (0.25 * amplitude[int(d * 31.9375)]);" \

		"dist = d * d * (0.5 + (1.5 * amplitude[2]));" \

		"vColor = vec3(abs(cos(angle*5.0+time))," \
		"abs(cos(angle*7.0+time*2.0))," \
		"abs(cos(angle*11.0+time*4.0))" \
		");" \

		"}";
		break;
	default:
		vertexShader = "attribute float inPosition; varying vec4 vColor; uniform sampler2D texAmplitude; uniform sampler2D texColor; void main() {" \
		"float absx = abs(inPosition);" \
		"vec4 c = texture2D(texAmplitude, vec2(0.5 * (absx - 1.0), 0.0));" \
		"gl_Position = vec4(absx - 2.0, sign(inPosition) * c.a, 0.0, 1.0);" \
		"vColor = texture2D(texColor, c.ar); }";
		break;
	}

	switch (type) {
	case TYPE_LIQUID:
		fragmentShader = "precision highp float; varying vec2 vTexCoord; varying float vAmpl; uniform sampler2D texColor; uniform float time; void main() {" \

		/* This water equation (length, sin, cos) was based on: http://glslsandbox.com/e#21421.0 */ \
		"vec2 p = (vec2(vTexCoord.x, mix(vTexCoord.y, vAmpl, 0.25)) * 6.0) - vec2(125.0);" \

		"float t = time * -0.5;" \
		"vec2 i = p + vec2(cos(t - p.x) + sin(t + p.y), sin(t - p.y) + cos(t + p.x));" \
		"float c = 1.0 + (1.0 / length(vec2(p.x / (sin(i.x + t) * 100.0), p.y / (cos(i.y + t) * 100.0))));" \

		/* Let's perform only one iteration, in favor of speed ;) */
		/*"t = time * -0.05;" \
		"i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));" \
		"c += 1.0 / length(vec2(p.x / (sin(i.x + t) * 100.0), p.y / (cos(i.y + t) * 100.0)));" \

		"c = 1.5 - sqrt(c * 0.5);"*/ \
		"c = 1.5 - sqrt(c);" \

		"c = 1.25 * c * c * c;" \
		"t = (vAmpl * vAmpl * vAmpl);" \

		"gl_FragColor = (0.5 * t) + (0.7 * vec4(c, c + 0.1, c + 0.2, 0.0)) + texture2D(texColor, vec2(vTexCoord.x, vTexCoord.y * (1.0 - (min(1.0, (1.2 * c) + t) * 0.55))));" \

		"}";
		break;
	case TYPE_SPIN:
		fragmentShader = "precision mediump float; varying vec2 vTexCoord; varying vec3 vColor; varying float dist; uniform sampler2D texColor; void main() {" \
		"float c = dist - texture2D(texColor, vTexCoord).a;" \
		"gl_FragColor = vec4(vColor.r + c, vColor.g + c, vColor.b + c, 1.0);" \
		"}";
		break;
	default:
		fragmentShader = "precision mediump float; varying vec4 vColor; void main() { gl_FragColor = vColor; }";
		break;
	}

	if ((l = createProgram(vertexShader, fragmentShader, &glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -10;
	if (type != TYPE_SPECTRUM) {
		glBindAttribLocation(glProgram, 1, "inTexCoord");
		if (glGetError()) return -11;
	}
	glLinkProgram(glProgram);
	if (glGetError()) return -12;

	if (type == TYPE_LIQUID) {
		if ((l = createProgram(
			rectangleVShader,
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

	if (type == TYPE_LIQUID) {
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
		for (int i = 1; i < 1024; i += 2)
			vertices[i] = -1.0f;
		//vertices at even indices receive a value in range [0 .. 32[
		for (int i = 0; i < 1024; i += 2)
			vertices[i] = (float)(i << 5) / 1024.0f;
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

		delete vertices;

		//create a rectangle that occupies the entire screen
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 4) * sizeof(float), glVerticesRect, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);

		if (glGetError()) return -14;
	} else if (type == TYPE_SPIN) {
        glGenBuffers(2, glBuf);
        if (glGetError() || !glBuf[0] || !glBuf[1]) return -13;
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

	switch (type) {
	case TYPE_LIQUID:
	case TYPE_SPIN:
		glGenTextures(1, glTex);
		if (glGetError() || !glTex[0]) return -15;
		break;
	default:
		glGenTextures(2, glTex);
		if (glGetError() || !glTex[0] || !glTex[1]) return -15;
		break;
	}

	if (type != TYPE_SPIN) {
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, glTex[0]);
		if (glGetError()) return -16;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		if (type == TYPE_LIQUID) {
			//create a default blue background
			((unsigned short*)floatBuffer)[0] = 0x31fb;
			((unsigned short*)floatBuffer)[1] = 0x5b3a;
			((unsigned short*)floatBuffer)[2] = 0x041f;
			((unsigned short*)floatBuffer)[3] = 0x34df;
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 2, 2, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (unsigned char*)floatBuffer);
		} else {
			memset(floatBuffer, 0, 256);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)floatBuffer);
		}
		if (glGetError()) return -17;

		if (type != TYPE_LIQUID) {
			glActiveTexture(GL_TEXTURE1);
			glBindTexture(GL_TEXTURE_2D, glTex[1]);
			if (glGetError()) return -18;
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			if (glGetError()) return -19;
		}

		//leave everything prepared for fast drawing :)
		glActiveTexture(GL_TEXTURE0);
	} else {
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, glTex[0]);
		if (glGetError()) return -16;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		//generate the 20x20 texture
		for (int y = 0; y < 20; y++) {
			float yf = (float)(y - 10);
			yf *= yf;
			for (int x = 0; x < 20; x++) {
				//length(vec2(x-10.0, y-10.0)) * 0.0625
				float xf = (float)(x - 10);
				int v = (int)(255.0f * 0.0625f * sqrtf((xf * xf) + yf));
				((unsigned char*)floatBuffer)[(y * 20) + x] = (unsigned char)((v >= 255) ? 255 : v);
			}
		}
		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 20, 20, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)floatBuffer);
		if (glGetError()) return -17;
	}

	glUseProgram(glProgram);
	if (glGetError()) return -20;

	switch (type) {
	case TYPE_LIQUID:
	case TYPE_SPIN:
		glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
		glTime = glGetUniformLocation(glProgram, "time");
		glAmplitude = glGetUniformLocation(glProgram, "amplitude");
		break;
	default:
		glUniform1i(glGetUniformLocation(glProgram, "texAmplitude"), 0);
		glUniform1i(glGetUniformLocation(glProgram, "texColor"), 1);
		break;
	}
	if (glGetError()) return -21;

	if (type == TYPE_LIQUID) {
		glUseProgram(glProgram2);
		if (glGetError()) return -22;

		glUniform1i(glGetUniformLocation(glProgram2, "texColor"), 0);
		if (glGetError()) return -23;
	}

	if (type != TYPE_SPIN) {
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glVertexAttribPointer(0, 1, GL_FLOAT, false, 0, 0);
	} else {
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
	}
	if (type == TYPE_LIQUID) {
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

	commonColorIndex = ((type != TYPE_SPECTRUM) ? 4 : 0);
	commonColorIndexApplied = 4; //to control the result of (commonColorIndexApplied != commonColorIndex) inside glDrawFrame
	commonUpdateMultiplier(env, clazz, 0);

	glReleaseShaderCompiler();

	return 0;
}

void JNICALL glOnSurfaceChanged(JNIEnv* env, jclass clazz, int width, int height) {
	glViewport(0, 0, width, height);
	if (glType == TYPE_SPIN && glProgram && glBuf[0] && glBuf[1] && width > 0 && height > 0) {
		glVerticesPerRow = ((width + 19) / 20) + 1;
		glRows = ((height + 19) / 20);
		struct _coord {
			float x, y, z;
		};
		_coord* vertices = new _coord[(glVerticesPerRow << 1) * glRows];

		//compute the position of each vertex on the screen, respecting their order:
		// 1  3  5  7
		//             ...
		// 0  2  4  6
		//inPosition stores the distance of the vertex to the origin in the z component
		_coord* v = vertices;
		float y0 = 1.0f;
		for (int j = 0; j < glRows; j++) {
			//compute x and y every time to improve precision
			float y1 = 1.0f - ((float)(20 * 2 * (j + 1)) / (float)height);
			for (int i = 0; i < glVerticesPerRow; i++) {
				float x = -1.0f + ((float)(20 * 2 * i) / (float)width);
				v[(i << 1)    ].x = x;
				v[(i << 1)    ].y = y1;
				v[(i << 1) + 1].x = x;
				v[(i << 1) + 1].y = y0;

				x = (-x + 1.0f) * 0.5f;
				x = x * x;

				float y = (y0 + 1.0f) * 0.5f;
				float z = 1.0f - (sqrtf(x + (y * y)) / 1.25f);
				v[(i << 1) + 1].z = ((z > 0.0f) ? z : 0.0f);

				y = (y1 + 1.0f) * 0.5f;
				z = 1.0f - (sqrtf(x + (y * y)) / 1.25f);
				v[(i << 1)    ].z = ((z > 0.0f) ? z : 0.0f);
			}
			y0 = y1;
			v += (glVerticesPerRow << 1);
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (glVerticesPerRow << 1) * glRows * 3 * sizeof(float), vertices, GL_STATIC_DRAW);

		//inTexCoord stores the angle of the vertex in the z component
		v = vertices;
		for (int j = 0; j < glRows; j++) {
			for (int i = 0; i < glVerticesPerRow; i++) {
				int idx = (i << 1) + 1;
				v[idx].z = atan2f((v[idx].y + 1.0f) * 0.5f, (-v[idx].x + 1.0f) * 0.5f);
				v[idx].x = (float)i;
				v[idx].y = (float)j;
				//even vertices are located below odd ones
				idx--;
				v[idx].z = atan2f((v[idx].y + 1.0f) * 0.5f, (-v[idx].x + 1.0f) * 0.5f);
				v[idx].x = (float)i;
				v[idx].y = (float)(j + 1);
			}
			v += (glVerticesPerRow << 1);
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glBufferData(GL_ARRAY_BUFFER, (glVerticesPerRow << 1) * glRows * 3 * sizeof(float), vertices, GL_STATIC_DRAW);

		delete vertices;

		glVerticesPerRow <<= 1;
	} else {
		glVerticesPerRow = 0;
		glRows = 0;
	}
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

	glGetError(); //clear any eventual error flags
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, inf.width, inf.height, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, dst);
	int error = glGetError();

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

	int i, idx, last;
	unsigned char avg, *processedData = (unsigned char*)(floatBuffer + 512);

	switch (glType) {
	case TYPE_LIQUID:
		glUseProgram(glProgram2);

		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

		glFlush(); //faster than glFinish :)

		glUseProgram(glProgram);

	case TYPE_SPIN:
		glUniform1f(glTime, (float)commonTime * 0.001f);

#define MAX(A,B) (((A) > (B)) ? (A) : (B))
		idx = glAmplitude;
		for (i = 0; i < 6; i++)
			glUniform1f(idx++, (float)processedData[i] / 255.0f);
		for (; i < 20; i += 2)
			glUniform1f(idx++, (float)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1) / 255.0f);
		for (; i < 36; i += 4)
			glUniform1f(idx++, (float)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2) / 255.0f);
		for (last = 44; last <= 100; last += 8) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			glUniform1f(idx++, (float)avg / 255.0f);
		}
		for (last = 116; last <= 228; last += 16) {
			avg = 0;
			for (; i < last; i++)
				avg = MAX(avg, processedData[i]);
			glUniform1f(idx++, (float)avg / 255.0f);
		}
#undef MAX

		if (glType == TYPE_LIQUID) {
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 512 * 2);
		} else if (glRows) {
			int first = 0;
			for (int i = 0; i < glRows; i++) {
				glDrawArrays(GL_TRIANGLE_STRIP, first, glVerticesPerRow);
				first += glVerticesPerRow;
			}
		}
		break;
	default:
		glClear(GL_COLOR_BUFFER_BIT);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, processedData);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 256 * 2);
		break;
	}
}
