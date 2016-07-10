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

//https://www.khronos.org/opengles/sdk/docs/man31/html/glDeleteBuffers.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glVertexAttribPointer.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glTexImage2D.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glGenBuffers.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glPixelStorei.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glUniform.xhtml

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
#define TYPE_PARTICLE 3
#define TYPE_IMMERSIVE_PARTICLE 4
#define TYPE_IMMERSIVE_PARTICLE_VR 5
#define TYPE_SPECTRUM2 6

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

typedef void (*DRAWPROC)();

static const char* const rectangleVShader = "attribute vec4 inPosition; attribute vec2 inTexCoord; varying vec2 vTexCoord; void main() { gl_Position = inPosition; vTexCoord = inTexCoord; }";
static const char* const textureFShader = "precision mediump float; varying vec2 vTexCoord; uniform sampler2D texColor; void main() { gl_FragColor = texture2D(texColor, vTexCoord); }";
static const char* const textureFShaderOES = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vTexCoord; uniform samplerExternalOES texColorOES; void main() { gl_FragColor = texture2D(texColorOES, vTexCoord); }";

static const char* const immersiveParticleVShader = "attribute vec2 inPosition; attribute vec2 inTexCoord; attribute float inIndex; varying vec2 vTexCoord; varying vec3 vColor; uniform float amplitude; uniform float diffusion; uniform float baseX; uniform vec2 posArr[16]; uniform vec2 aspect; uniform vec3 colorArr[16]; uniform float thetaArr[16]; uniform mat4 mvpMat; void main() {" \
"int32_t idx = int32_t(inIndex);" \
"vec2 pos = posArr[idx];" \
/*start with the original computation*/ \
"float a = mix(0.0625, 0.484375, amplitude)," \
	"bottom = 1.0 - clamp(pos.y, -1.0, 1.0);" \
"bottom = bottom * bottom * bottom * 0.125;" \
/*now that the particles are spread in a full circle, I decided*/ \
/*to increase their size by 50% (I also moved the "* 5" here) */ \
/*"a = (0.75 * a) + (0.25 * bottom);"*/ \
"a = (4.125 * a) + (1.375 * bottom);" \

"vec3 smoothedColor = colorArr[idx] + bottom + (0.25 * amplitude);" \
/*make the particles smoothly appear at the bottom and diminish at the top*/ \
/*(from here on, bottom will store the particle's distance from the center - the radius)*/ \
"if (pos.y > 0.0) {" \
	/*let's make the radius decrease from 3 to 1 at the top*/ \
	"bottom = 3.0 - (2.0 * pos.y);" \
	/*make the particles smaller as they approach the top (y > 0.8)*/ \
	"if (pos.y > 0.9)" \
		"a *= 1.0 - ((pos.y - 0.9) / 0.3);" \
"} else if (pos.y < -0.8) {" \
	"bottom = smoothstep(-1.2, -0.8, pos.y);" \
	/*make the particles larger than usual at the bottom*/ \
	/*(they will sprout 50% bigger)*/ \
	"a *= (1.5 - (0.5 * bottom));" \
	"smoothedColor *= bottom;" \
	"bottom *= 3.0;" \
"} else {" \
	"bottom = 3.0;" \
"}" \
"vTexCoord = inTexCoord;" \
"vColor = smoothedColor;" \

/*baseX goes from -0.9 / 0.9, which we will map to a full circle */ \
/*(using 1.7 instead of 3.14 maps to pi / 0)*/ \
"smoothedColor.x = -3.14 * (baseX + pos.x + (diffusion * (pos.y + 1.0) * pos.x * sin((2.0 * pos.y) + thetaArr[idx])));" \
/*spread the particles in a semicylinder with a radius of 3 and height of 12*/ \
"vec4 p = mvpMat * vec4(bottom * cos(smoothedColor.x), bottom * sin(smoothedColor.x), 6.0 * pos.y, 1.0);" \
/*"vec4 p = mvpMat * vec4(bottom * cos(smoothedColor.x) + (inPosition.x * a * 5.0), bottom * sin(smoothedColor.x) + (inPosition.y * a * 5.0), 6.0 * pos.y, 1.0);"*/ \

/*gl_Position is different from p, because we want the particles to be always facing the camera*/ \
"gl_Position = vec4(p.x + (inPosition.x * aspect.x * a), p.y + (inPosition.y * aspect.y * a), p.z, p.w);" \
"}";

static const char* const particleFShader = "precision mediump float; varying vec2 vTexCoord; varying vec3 vColor; uniform sampler2D texColor; void main() {" \
"float a = texture2D(texColor, vTexCoord).a;"
"gl_FragColor = vec4(vColor.r * a, vColor.g * a, vColor.b * a, 1.0);" \
"}";

static DRAWPROC glDrawProc;
static uint32_t glProgram, glProgram2, glType, glBuf[5];
static int32_t glTime, glAmplitude, glVerticesPerRow, glRows, glMatrix, glPos, glColor, glBaseX, glTheta, glOESTexture, glUpDown;

#define glResetState() glDrawProc = glDrawNothing; \
glProgram = 0; \
glProgram2 = 0; \
glBuf[0] = 0; \
glBuf[1] = 0; \
glBuf[2] = 0; \
glBuf[3] = 0; \
glBuf[4] = 0; \
glTime = 0; \
glAmplitude = 0; \
glVerticesPerRow = 0; \
glRows = 0; \
glMatrix = 0; \
glPos = 0; \
glColor = 0; \
glBaseX = 0; \
glTheta = 0; \
glUpDown = 0; \
glOESTexture = 0

float glSmoothStep(float edge0, float edge1, float x) {
	float t = (x - edge0) / (edge1 - edge0);
	return ((t <= 0.0f) ? 0.0f :
		((t >= 1.0f) ? 1.0f :
			(t * t * (3.0f - (2.0f * t)))
		)
	);
}

#include "GLSoundParticle.h"

int32_t glCreateProgramAndShaders(const char* vertexShaderSource, const char* fragmentShaderSource, uint32_t* program) {
	int32_t l;
	uint32_t p, vertexShader, fragmentShader;

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
	//int32_t s = 0;
	//glGetShaderInfoLog(vertexShader, 1024, &s, (char*)floatBuffer);
	//((char*)floatBuffer)[s] = 0;
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "Compilation result: %s", (char*)floatBuffer);
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

int32_t glComputeSpinSize(int32_t width, int32_t height, int32_t dp1OrLess) {
	dp1OrLess = (dp1OrLess ? 10 : 20);
	int32_t size = dp1OrLess;
	while (size < 33 && ((width % size) || (height % size)))
		size++;
	if (size > 32) {
		size = dp1OrLess;
		while (size < 33 && (height % size))
			size++;
		if (size > 32) {
			size = dp1OrLess;
			while (size < 33 && (width % size))
				size++;
			if (size > 32)
				size = dp1OrLess;
		}
	}
	return size;
}

void glSumData() {
	int32_t i, idx, last;
	uint8_t avg, *processedData = (uint8_t*)(floatBuffer + 512);

	//instead of dividing by 255, we are dividing by 256 (* 0.00390625f)
	//since the difference is visually unnoticeable
	idx = glAmplitude;
	for (i = 0; i < 6; i++)
		glUniform1f(idx++, (float)processedData[i] * 0.00390625f);
	for (; i < 20; i += 2)
		glUniform1f(idx++, (float)MAX(processedData[i], processedData[i + 1]) * 0.00390625f);
	for (; i < 36; i += 4) {
		avg = MAX(processedData[i], processedData[i + 1]);
		avg = MAX(avg, processedData[i + 2]);
		avg = MAX(avg, processedData[i + 3]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
	for (last = 44; last <= 100; last += 8) {
		avg = processedData[i++];
		for (; i < last; i++)
			avg = MAX(avg, processedData[i]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
	for (last = 116; last <= 228; last += 16) {
		avg = processedData[i++];
		for (; i < last; i++)
			avg = MAX(avg, processedData[i]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
}

void glUpdateSpectrumColorTexture() {
	commonColorIndexApplied = commonColorIndex;
	glActiveTexture(GL_TEXTURE1);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 256, 1, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (uint8_t*)(COLORS + commonColorIndex));
	glActiveTexture(GL_TEXTURE0);
}

int32_t glParticleSetup(int32_t hasGyro) {
	glBindAttribLocation(glProgram, 2, "inIndex");
	if (glGetError()) return -201;

	if (glType == TYPE_IMMERSIVE_PARTICLE_VR) {
		int32_t l;

		//create a second program to render the camera preview
		if ((l = glCreateProgramAndShaders(
			rectangleVShader,
			textureFShaderOES,
			&glProgram2)))
			return l;

		glBindAttribLocation(glProgram2, 3, "inPosition");
		if (glGetError()) return -202;
		glBindAttribLocation(glProgram2, 4, "inTexCoord");
		if (glGetError()) return -203;
		glLinkProgram(glProgram2);
		if (glGetError()) return -204;

		glGenBuffers(5, glBuf);
		if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2] || !glBuf[3] || !glBuf[4]) return -205;

		//create a rectangle that occupies the entire screen
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 4) * sizeof(float), glVerticesRect, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[4]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);

		if (glGetError()) return -206;
	} else {
		glGenBuffers(3, glBuf);
		if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2]) return -207;
	}

	if (glSoundParticle)
		delete glSoundParticle;
	glSoundParticle = new GLSoundParticle(hasGyro);

	//create a rectangle for the particles, cropping a 10-pixel border, to improve speed
#define left (-(32.0f - 10.0f) / 32.0f)
#define top ((32.0f - 10.0f) / 32.0f)
#define right ((32.0f - 10.0f) / 32.0f)
#define bottom (-(32.0f - 10.0f) / 32.0f)
	//let's create BG_PARTICLES_BY_COLUMN copies of the same rectangle, divided into 2 triangles
	for (int32_t i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
		const int32_t idx = i * (3 * 2 * 2);
		//triangle 1 (CCW)
		floatBuffer[idx + 0 ] = left;
		floatBuffer[idx + 1 ] = bottom;
		floatBuffer[idx + 2 ] = right;
		floatBuffer[idx + 3 ] = top;
		floatBuffer[idx + 4 ] = left;
		floatBuffer[idx + 5 ] = top;

		//triangle 2 (CCW)
		floatBuffer[idx + 6 ] = left;
		floatBuffer[idx + 7 ] = bottom;
		floatBuffer[idx + 8 ] = right;
		floatBuffer[idx + 9 ] = bottom;
		floatBuffer[idx + 10] = right;
		floatBuffer[idx + 11] = top;
	}
#undef left
#undef top
#undef right
#undef bottom
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 3 * 2 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
#define left (10.0f / 64.0f)
#define top (10.0f / 64.0f)
#define right (54.0f / 64.0f)
#define bottom (54.0f / 64.0f)
	//let's create BG_PARTICLES_BY_COLUMN copies of the same rectangle, divided into 2 triangles
	for (int32_t i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
		const int32_t idx = i * (3 * 2 * 2);
		//triangle 1 (CCW)
		floatBuffer[idx + 0 ] = left;
		floatBuffer[idx + 1 ] = bottom;
		floatBuffer[idx + 2 ] = right;
		floatBuffer[idx + 3 ] = top;
		floatBuffer[idx + 4 ] = left;
		floatBuffer[idx + 5 ] = top;

		//triangle 2 (CCW)
		floatBuffer[idx + 6 ] = left;
		floatBuffer[idx + 7 ] = bottom;
		floatBuffer[idx + 8 ] = right;
		floatBuffer[idx + 9 ] = bottom;
		floatBuffer[idx + 10] = right;
		floatBuffer[idx + 11] = top;
	}
#undef left
#undef top
#undef right
#undef bottom
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 3 * 2 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
	//now let's fill in the buffer used to store the indices
	for (int32_t i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
		const float f = (float)i;
		const int32_t idx = i * (3 * 2);
		//triangle 1 (CCW)
		floatBuffer[idx + 0] = f;
		floatBuffer[idx + 1] = f;
		floatBuffer[idx + 2] = f;

		//triangle 2 (CCW)
		floatBuffer[idx + 3] = f;
		floatBuffer[idx + 4] = f;
		floatBuffer[idx + 5] = f;
	}
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
	glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 3 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);

	if (glGetError()) return -208;

	glEnable(GL_BLEND);
	glBlendFunc(GL_ONE, GL_ONE);
	glBlendEquation(GL_FUNC_ADD);
	glGetError(); //clear any eventual error flags

	return 0;
}

void glDrawNothing() {
	glClear(GL_COLOR_BUFFER_BIT);
}

void glDrawLiquid() {
	glUseProgram(glProgram2);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
	glFlush(); //faster than glFinish :)

	glUseProgram(glProgram);
	glUniform1f(glTime, (float)commonTime * 0.001f);
	glSumData();
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 512 * 2);
}

void glDrawSpin() {
	if (glRows) {
		glUniform1f(glTime, (float)commonTime * 0.001f);

		glSumData();

		int32_t i, first = 0;
		for (i = 0; i < glRows; i++) {
			glDrawArrays(GL_TRIANGLE_STRIP, first, glVerticesPerRow);
			first += glVerticesPerRow;
		}
	}
}

void glDrawParticle() {
	glClear(GL_COLOR_BUFFER_BIT);

	if (glSoundParticle) {
		glSoundParticle->draw();
	}
}

void glDrawImmersiveParticleVR() {
	glUseProgram(glProgram2);
	glDisable(GL_BLEND);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
	glFlush(); //faster than glFinish :)

	glUseProgram(glProgram);
	glEnable(GL_BLEND);
	if (glSoundParticle)
		glSoundParticle->draw();
}

void glDrawSpectrum2() {
	if (commonColorIndexApplied != commonColorIndex)
		glUpdateSpectrumColorTexture();

	glClear(GL_COLOR_BUFFER_BIT);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (uint8_t*)(floatBuffer + 512));
	glUniform1f(glUpDown, 1.0f);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 512 * 2); //twice as many vertices as the regular spectrum
	glUniform1f(glUpDown, -1.0f);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 512 * 2); //twice as many vertices as the regular spectrum
}

void glDrawSpectrum2WithoutAmplitudeTexture() {
	if (commonColorIndexApplied != commonColorIndex)
		glUpdateSpectrumColorTexture();

	glClear(GL_COLOR_BUFFER_BIT);
	//instead of dividing by 255, we are dividing by 256 (* 0.00390625f)
	//since the difference is visually unnoticeable
	int32_t i, idx = glAmplitude, last;
	uint8_t avg, *processedData = (uint8_t*)(floatBuffer + 512);
	for (i = 0; i < 36; i++)
		glUniform1f(idx++, (float)processedData[i] * 0.00390625f);
	for (; i < 184; i += 2)
		glUniform1f(idx++, (float)MAX(processedData[i], processedData[i + 1]) * 0.00390625f);
	for (; i < 252; i += 4) {
		avg = MAX(processedData[i], processedData[i + 1]);
		avg = MAX(avg, processedData[i + 2]);
		avg = MAX(avg, processedData[i + 3]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
	glUniform1f(glUpDown, 1.0f);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 256 * 2); //twice as many vertices as the regular spectrum
	glUniform1f(glUpDown, -1.0f);
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 256 * 2); //twice as many vertices as the regular spectrum
}

void glDrawSpectrum() {
	if (commonColorIndexApplied != commonColorIndex)
		glUpdateSpectrumColorTexture();

	glClear(GL_COLOR_BUFFER_BIT);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (uint8_t*)(floatBuffer + 512));
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 256 * 2);
}

void glDrawSpectrumWithoutAmplitudeTexture() {
	if (commonColorIndexApplied != commonColorIndex)
		glUpdateSpectrumColorTexture();

	glClear(GL_COLOR_BUFFER_BIT);
	//instead of dividing by 255, we are dividing by 256 (* 0.00390625f)
	//since the difference is visually unnoticeable
	int32_t i, idx = glAmplitude, last;
	uint8_t avg, *processedData = (uint8_t*)(floatBuffer + 512);
	for (i = 0; i < 36; i++)
		glUniform1f(idx++, (float)processedData[i] * 0.00390625f);
	for (; i < 184; i += 2)
		glUniform1f(idx++, (float)MAX(processedData[i], processedData[i + 1]) * 0.00390625f);
	for (; i < 252; i += 4) {
		avg = MAX(processedData[i], processedData[i + 1]);
		avg = MAX(avg, processedData[i + 2]);
		avg = MAX(avg, processedData[i + 3]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
	glDrawArrays(GL_TRIANGLE_STRIP, 0, 128 * 2);
}

int32_t glCreateLiquid() {
	commonTimeLimit = 12566; //2 * 2 * pi * 1000

	int32_t l;

	if ((l = glCreateProgramAndShaders(
		//vertex shader
		"attribute float inPosition; attribute float inTexCoord; varying vec2 vTexCoord; varying float vAmpl; uniform float amplitude[33]; void main() {" \
		"vec2 coord = vec2((inPosition + 1.0) * 0.5, 0.0);" \
		"float absy;" \
		"if (inTexCoord < 0.0) {" \
			"vAmpl = 0.0;" \
			"gl_Position = vec4(inPosition, 1.0, 0.0, 1.0);" \
		"} else {" \
			"int32_t i = int32_t(inTexCoord);" \
			"absy = amplitude[i];" \
			"absy += (amplitude[i + 1] - absy) * smoothstep(0.0, 1.0, fract(inTexCoord));" \
			"vAmpl = 1.0;" \
			"gl_Position = vec4(inPosition, (absy * 2.0) - 1.0, 0.0, 1.0);" \
			"coord.y = 1.0 - absy;" \
		"}" \
		"vTexCoord = coord; }",

		//fragment shader
		"precision highp float; varying vec2 vTexCoord; varying float vAmpl; uniform sampler2D texColor; uniform float time; void main() {" \

		/* This water equation (length, sin, cos) was based on: http://glslsandbox.com/e#21421.0 */ \
		"vec2 p = (vec2(vTexCoord.x, mix(vTexCoord.y, vAmpl, 0.25)) * 6.0) - vec2(125.0);" \

		/* Let's perform only one iteration, in favor of speed ;) */ \
		"float t = time * -0.5;" \
		"vec2 i = p + vec2(cos(t - p.x) + sin(t + p.y), sin(t - p.y) + cos(t + p.x));" \
		"float c = 1.0 + (1.0 / length(vec2(p.x / (sin(i.x + t) * 100.0), p.y / (cos(i.y + t) * 100.0))));" \

		"c = 1.5 - sqrt(c);" \

		"c = 1.25 * c * c * c;" \
		"t = (vAmpl * vAmpl * vAmpl);" \

		"gl_FragColor = (0.5 * t) + (0.7 * vec4(c, c + 0.1, c + 0.2, 0.0)) + texture2D(texColor, vec2(vTexCoord.x, vTexCoord.y * (1.0 - (min(1.0, (1.2 * c) + t) * 0.55))));" \

		"}",

		&glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -100;
	glBindAttribLocation(glProgram, 1, "inTexCoord");
	if (glGetError()) return -101;
	glLinkProgram(glProgram);
	if (glGetError()) return -102;

	if ((l = glCreateProgramAndShaders(
		rectangleVShader,
		textureFShader,
		&glProgram2)))
		return l;

	glBindAttribLocation(glProgram2, 2, "inPosition");
	if (glGetError()) return -100;
	glBindAttribLocation(glProgram2, 3, "inTexCoord");
	if (glGetError()) return -101;
	glLinkProgram(glProgram2);
	if (glGetError()) return -102;

	glGenBuffers(4, glBuf);
	if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2] || !glBuf[3]) return -103;

	//in order to save memory and bandwidth, we are sending only the x coordinate
	//of each point, but with a few modifications...
	//
	//this array represents a horizontal triangle strip with x ranging from -1 to 1,
	//and which vertices are ordered like this:
	// 1  3  5  7
	//             ...
	// 0  2  4  6
	float *vertices = new float[1024];

	for (int32_t i = 0; i < 512; i++) {
		const float p = -1.0f + (2.0f * (float)i / 511.0f);
		vertices[(i << 1)    ] = p;
		vertices[(i << 1) + 1] = p;
	}
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

	//vertices at odd indices receive a -1, to indicate they are static, and must be placed
	//at the top of the screen
	for (int32_t i = 1; i < 1024; i += 2)
		vertices[i] = -1.0f;
	//vertices at even indices receive a value in range [0 .. 32[
	for (int32_t i = 0; i < 1024; i += 2)
		vertices[i] = (float)(i << 5) / 1024.0f;
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

	delete vertices;

	//create a rectangle that occupies the entire screen
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
	glBufferData(GL_ARRAY_BUFFER, (4 * 4) * sizeof(float), glVerticesRect, GL_STATIC_DRAW);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
	glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);

	if (glGetError()) return -104;

	uint32_t glTex = 0;

	glGenTextures(1, &glTex);
	if (glGetError() || !glTex) return -105;

	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex);
	if (glGetError()) return -106;

	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

	//create a default blue background
	((uint16_t*)floatBuffer)[0] = 0x31fb;
	((uint16_t*)floatBuffer)[1] = 0x5b3a;
	((uint16_t*)floatBuffer)[2] = 0x041f;
	((uint16_t*)floatBuffer)[3] = 0x34df;
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 2, 2, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (uint8_t*)floatBuffer);

	if (glGetError()) return -107;

	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);

	glUseProgram(glProgram);
	if (glGetError()) return -108;

	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
	glTime = glGetUniformLocation(glProgram, "time");
	glAmplitude = glGetUniformLocation(glProgram, "amplitude");
	if (glGetError()) return -109;

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 1, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(1);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glVertexAttribPointer(1, 1, GL_FLOAT, false, 0, 0);

	glUseProgram(glProgram2);
	if (glGetError()) return -110;

	glUniform1i(glGetUniformLocation(glProgram2, "texColor"), 0);
	if (glGetError()) return -111;

	glEnableVertexAttribArray(2);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
	glVertexAttribPointer(2, 4, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(3);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
	glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);
	if (glGetError()) return -112;

	glDrawProc = glDrawLiquid;

	return 0;
}

int32_t glCreateSpin(int32_t estimatedWidth, int32_t estimatedHeight, int32_t dp1OrLess) {
	commonTimeLimit = 6283; //2 * pi * 1000

	int32_t l;

	if ((l = glCreateProgramAndShaders(
		//vertex shader
		//inPosition stores the distance of the vertex to the origin in the z component
		//inTexCoord stores the angle of the vertex in the z component
		"attribute vec3 inPosition; attribute vec3 inTexCoord; varying vec2 vTexCoord; varying vec3 vColor; varying float dist; uniform float amplitude[33]; uniform float time; void main() {" \
		"gl_Position = vec4(inPosition.x, inPosition.y, 0.0, 1.0);" \
		"float d = inPosition.z;" \

		"vTexCoord = inTexCoord.xy;" \
		"float angle = inTexCoord.z - (0.25 * amplitude[int32_t(d * 31.9375)]);" \

		"dist = d * d * (0.5 + (1.5 * amplitude[2]));" \

		"vColor = vec3(abs(cos(angle*5.0+time))," \
		"abs(cos(angle*7.0+time*2.0))," \
		"abs(cos(angle*11.0+time*4.0))" \
		");" \

		"}",
		
		//fragment shader
		"precision mediump float; varying vec2 vTexCoord; varying vec3 vColor; varying float dist; uniform sampler2D texColor; void main() {" \
		"float c = dist - texture2D(texColor, vTexCoord).a;" \
		"gl_FragColor = vec4(vColor.r + c, vColor.g + c, vColor.b + c, 1.0);" \
		"}",
		
		&glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -100;
	glBindAttribLocation(glProgram, 1, "inTexCoord");
	if (glGetError()) return -101;
	glLinkProgram(glProgram);
	if (glGetError()) return -102;

	glGenBuffers(2, glBuf);
	if (glGetError() || !glBuf[0] || !glBuf[1]) return -103;

	uint32_t glTex = 0;

	glGenTextures(1, &glTex);
	if (glGetError() || !glTex) return -104;

	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex);
	if (glGetError()) return -105;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

	//according to these:
	//http://stackoverflow.com/questions/5705753/android-opengl-es-loading-a-non-power-of-2-texture
	//http://stackoverflow.com/questions/3740077/can-opengl-es-render-textures-of-non-base-2-dimensions
	//https://www.khronos.org/opengles/sdk/docs/man/xhtml/glTexParameter.xhtml
	//non-power-of-2 textures cannot be used with GL_TEXTURE_WRAP_x other than GL_CLAMP_TO_EDGE
	//(even though it works on many devices, the spec says it shouldn't...)
	int32_t TEXTURE_SIZE = glComputeSpinSize(estimatedWidth, estimatedHeight, dp1OrLess);
	TEXTURE_SIZE = ((TEXTURE_SIZE <= 16) ? 16 : 32);
	const float TEXTURE_COEF = ((TEXTURE_SIZE == 16) ? (255.0f * 0.078125f) : (255.0f * 0.0390625f));
	//generate the texture
	for (int32_t y = 0; y < TEXTURE_SIZE; y++) {
		float yf = (float)(y - (TEXTURE_SIZE >> 1));
		yf *= yf;
		for (int32_t x = 0; x < TEXTURE_SIZE; x++) {
			//0.0390625 = 1/25.6 (used for 32x32)
			//0.0625 = 1/16 (used for 20x20)
			//0.078125 = 1/12.8 (used for 16x16)
			float xf = (float)(x - (TEXTURE_SIZE >> 1));
			int32_t v = (int32_t)(TEXTURE_COEF * sqrtf((xf * xf) + yf));
			((uint8_t*)floatBuffer)[(y * TEXTURE_SIZE) + x] = ((v >= 255) ? 255 : (uint8_t)v);
		}
	}
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (uint8_t*)floatBuffer);
	if (glGetError()) return -106;

	glUseProgram(glProgram);
	if (glGetError()) return -107;

	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
	glTime = glGetUniformLocation(glProgram, "time");
	glAmplitude = glGetUniformLocation(glProgram, "amplitude");
	if (glGetError()) return -108;

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(1);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
	if (glGetError()) return -109;

	glDrawProc = glDrawSpin;

	return 0;
}

int32_t glCreateParticle(int32_t hasGyro) {
	commonTimeLimit = 0xffffffff;

	int32_t l;

	if ((l = glCreateProgramAndShaders(
		//vertex shader
		"attribute vec4 inPosition; attribute vec2 inTexCoord; attribute float inIndex; varying vec2 vTexCoord; varying vec3 vColor; uniform float amplitude; uniform float baseX; uniform vec2 posArr[16]; uniform vec2 aspect; uniform vec3 colorArr[16]; uniform float thetaArr[16]; void main() {" \
		"int32_t idx = int32_t(inIndex);" \
		"vec2 pos = posArr[idx];" \
		"float a = mix(0.0625, 0.34375, amplitude);" \
		"float bottom = 1.0 - clamp(pos.y, -1.0, 1.0);" \
		"bottom = bottom * bottom * bottom * 0.125;" \
		"a = (0.75 * a) + (0.25 * bottom);" \
		"gl_Position = vec4(baseX + pos.x + (5.0 * (pos.y + 1.0) * pos.x * sin((2.0 * pos.y) + thetaArr[idx])) + (inPosition.x * aspect.x * a), pos.y + (inPosition.y * aspect.y * a), 0.0, 1.0);" \
		"vTexCoord = inTexCoord;" \
		"vColor = colorArr[idx] + bottom + (0.25 * amplitude);" \
		"}",
		
		//fragment shader
		particleFShader,
		
		&glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -100;
	glBindAttribLocation(glProgram, 1, "inTexCoord");
	if (glGetError()) return -101;
	glLinkProgram(glProgram);
	if (glGetError()) return -102;

	if ((l = glParticleSetup(hasGyro))) return l;

	uint32_t glTex = 0;

	glGenTextures(1, &glTex);
	if (glGetError() || !glTex) return -103;

	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex);
	if (glGetError()) return -104;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	GLSoundParticle::fillTexture();
	if (glGetError()) return -105;

	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);

	glUseProgram(glProgram);
	if (glGetError()) return -106;

	glAmplitude = glGetUniformLocation(glProgram, "amplitude");
	glPos = glGetUniformLocation(glProgram, "posArr");
	glColor = glGetUniformLocation(glProgram, "colorArr");
	glBaseX = glGetUniformLocation(glProgram, "baseX");
	glTheta = glGetUniformLocation(glProgram, "thetaArr");
	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
	if (glGetError()) return -107;

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(1);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(2);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
	glVertexAttribPointer(2, 1, GL_FLOAT, false, 0, 0);
	if (glGetError()) return -108;

	glDrawProc = glDrawParticle;

	return 0;
}

int32_t glCreateImmersiveParticle(int32_t hasGyro) {
	commonTimeLimit = 0xffffffff;

	int32_t l;

	if ((l = glCreateProgramAndShaders(
		//vertex shader
		immersiveParticleVShader,

		//fragment shader
		particleFShader,

		&glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -100;
	glBindAttribLocation(glProgram, 1, "inTexCoord");
	if (glGetError()) return -101;
	glLinkProgram(glProgram);
	if (glGetError()) return -102;

	if ((l = glParticleSetup(hasGyro))) return l;

	uint32_t glTex[2] = { 0, 0 };

	if (glType == TYPE_IMMERSIVE_PARTICLE_VR) {
		glGenTextures(2, glTex);
		if (glGetError() || !glTex[0] || !glTex[1]) return -103;
	} else {
		glGenTextures(1, glTex);
		if (glGetError() || !glTex[0]) return -103;
	}

	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex[0]);
	if (glGetError()) return -104;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	GLSoundParticle::fillTexture();
	if (glGetError()) return -105;

	if (glType == TYPE_IMMERSIVE_PARTICLE_VR) {
		glBindTexture(GL_TEXTURE_EXTERNAL_OES, glTex[1]);
		if (glGetError()) return -106;
		glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glOESTexture = glTex[1];
	}

	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);

	glUseProgram(glProgram);
	if (glGetError()) return -107;

	glMatrix = glGetUniformLocation(glProgram, "mvpMat");
	glAmplitude = glGetUniformLocation(glProgram, "amplitude");
	glPos = glGetUniformLocation(glProgram, "posArr");
	glColor = glGetUniformLocation(glProgram, "colorArr");
	glBaseX = glGetUniformLocation(glProgram, "baseX");
	glTheta = glGetUniformLocation(glProgram, "thetaArr");
	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
	if (glGetError()) return -108;

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(1);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
	glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

	glEnableVertexAttribArray(2);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
	glVertexAttribPointer(2, 1, GL_FLOAT, false, 0, 0);

	if (glType == TYPE_IMMERSIVE_PARTICLE_VR) {
		glEnableVertexAttribArray(3);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glVertexAttribPointer(3, 4, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(4);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[4]);
		glVertexAttribPointer(4, 2, GL_FLOAT, false, 0, 0);
	}
	if (glGetError()) return -109;

	glDrawProc = ((glType == TYPE_IMMERSIVE_PARTICLE_VR) ? glDrawImmersiveParticleVR : glDrawParticle);

	return 0;
}

int32_t glCreateSpectrum2() {
	int32_t spectrumUsesTexture = 0;

	commonTimeLimit = 0xffffffff;
	glGetIntegerv(GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, &spectrumUsesTexture);
	if (spectrumUsesTexture < 2)
		spectrumUsesTexture = 0;

	int32_t l;

	if ((l = glCreateProgramAndShaders(
		//vertex shader
		spectrumUsesTexture ? "attribute float inPosition; varying vec4 vColor; uniform sampler2D texAmplitude; uniform sampler2D texColor; uniform float upDown; void main() {" \
			"float absx = abs(inPosition);" \
			"if (inPosition > 0.0) {" \
				/*top/bottom points*/ \
				"gl_Position = vec4(absx - 2.0, upDown, 0.0, 1.0);" \
				"vColor = vec4(1.0, 1.0, 1.0, 1.0);" \
			"} else {" \
				/*middle points*/ \
				/* since absx goes from 1 to 3, and mirroring takes place when the integer part is odd, we just let the x coordinate */ \
				/* to follow absx, and as a result, the first half will be mirrored, placing lower frequencies at the center of the screen */ \
				"vec4 ampl = texture2D(texAmplitude, vec2(absx, 0.0));" \
				"gl_Position = vec4(absx - 2.0, upDown * (1.0 - ampl.a), 0.0, 1.0);" \
				"vColor = texture2D(texColor, ampl.ar);" \
			"}" \
		"}"
		:
		//Tegra GPUs CANNOT use textures in vertex shaders AND does not support more than 256 registers! :(
		//http://stackoverflow.com/questions/11398114/vertex-shader-doesnt-run-on-galaxy-tab10-tegra-2
		//http://developer.download.nvidia.com/assets/mobile/files/tegra_gles2_development.pdf
		//https://www.khronos.org/registry/gles/specs/2.0/GLSL_ES_Specification_1.0.17.pdf page 111-113
		"attribute float inPosition; varying float vAmpl; varying float vColorAdd; uniform float amplitude[128]; uniform float upDown; void main() {" \
			"float absx = abs(inPosition);" \
			"float ampl;" \
			/*mirror effect has to be simulated by hand*/ \
			"if (absx < 2.0) {" \
				"ampl = amplitude[int32_t(floor(127.0 * (2.0 - absx)))];" \
				"absx -= 2.0;"
			"} else {" \
				"absx -= 2.0;"
				"ampl = amplitude[int32_t(floor(127.0 * absx))];" \
			"}" \
			"if (inPosition > 0.0) {" \
				/*top/bottom points*/ \
				"gl_Position = vec4(absx, upDown, 0.0, 1.0);" \
				"vColorAdd = 1.0;" \
			"} else {" \
				/*middle points*/ \
				"gl_Position = vec4(absx, upDown * (1.0 - ampl), 0.0, 1.0);" \
				"vColorAdd = 0.0;" \
			"}" \
			"vAmpl = ampl;" \
		"}",

		//fragment shader
		spectrumUsesTexture ? "precision mediump float; varying vec4 vColor; void main() {" \
			"gl_FragColor = vColor;" \
		"}"
		:
		//Tegra GPUs CANNOT use textures in vertex shaders! :(
		//http://stackoverflow.com/questions/11398114/vertex-shader-doesnt-run-on-galaxy-tab10-tegra-2
		//http://developer.download.nvidia.com/assets/mobile/files/tegra_gles2_development.pdf
		"precision mediump float; varying float vAmpl; varying float vColorAdd; uniform sampler2D texColor; void main() {" \
			"vec4 c = texture2D(texColor, vec2(vAmpl, 0.0));"
			"gl_FragColor = vec4(vColorAdd + c.r, vColorAdd + c.g, vColorAdd + c.b, 1.0);" \
		"}",

		&glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -100;
	glLinkProgram(glProgram);
	if (glGetError()) return -101;

	glGenBuffers(1, glBuf);
	if (glGetError() || !glBuf[0]) return -102;

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
	if (spectrumUsesTexture) {
		//floatBuffer contains only (256 * 2) + (256 / 4) floats :(
		float *vertices = new float[512 * 2];

		for (int32_t i = 0; i < 512; i++) {
			//even is negative, odd is positive
			const float p = 1.0f + (2.0f * (float)i / 511.0f);
			vertices[(i << 1)    ] = -p;
			vertices[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

		delete vertices;
	} else {
		for (int32_t i = 0; i < 256; i++) {
			//even is negative, odd is positive
			const float p = 1.0f + (2.0f * (float)i / 255.0f);
			floatBuffer[(i << 1)    ] = -p;
			floatBuffer[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (256 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
	}
	if (glGetError()) return -103;
	
	uint32_t glTex[2] = { 0, 0 };

	glGenTextures(2, glTex);
	if (glGetError() || !glTex[0] || !glTex[1]) return -104;

	//amplitude texture
	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex[0]);
	if (glGetError()) return -105;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT); //mirror the x coordinate (mirror takes place when the integer part is odd)
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	memset(floatBuffer, 0, 256);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (uint8_t*)floatBuffer);
	if (glGetError()) return -106;

	//color texture (blue or green)
	glActiveTexture(GL_TEXTURE1);
	glBindTexture(GL_TEXTURE_2D, glTex[1]);
	if (glGetError()) return -107;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	if (glGetError()) return -108;

	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);

	glUseProgram(glProgram);
	if (glGetError()) return -109;

	if (spectrumUsesTexture)
		glUniform1i(glGetUniformLocation(glProgram, "texAmplitude"), 0);
	else
		glAmplitude = glGetUniformLocation(glProgram, "amplitude");
	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 1);
	glUpDown = glGetUniformLocation(glProgram, "upDown");
	if (glGetError()) return -110;

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 1, GL_FLOAT, false, 0, 0);
	if (glGetError()) return -111;

	glDrawProc = (spectrumUsesTexture ? glDrawSpectrum2 : glDrawSpectrum2WithoutAmplitudeTexture);

	return 0;
}

int32_t glCreateSpectrum() {
	int32_t spectrumUsesTexture = 0;

	commonTimeLimit = 0xffffffff;
	glGetIntegerv(GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, &spectrumUsesTexture);
	if (spectrumUsesTexture < 2)
		spectrumUsesTexture = 0;

	int32_t l;

	if ((l = glCreateProgramAndShaders(
		//vertex shader
		spectrumUsesTexture ? "attribute float inPosition; varying vec4 vColor; uniform sampler2D texAmplitude; uniform sampler2D texColor; void main() {" \
			"float absx = abs(inPosition);" \
			"vec4 ampl = texture2D(texAmplitude, vec2(0.5 * (absx - 1.0), 0.0));" \
			"gl_Position = vec4(absx - 2.0, sign(inPosition) * ampl.a, 0.0, 1.0);" \
			"vColor = texture2D(texColor, ampl.ar);" \
		"}"
		:
		//Tegra GPUs CANNOT use textures in vertex shaders AND does not support more than 256 registers! :(
		//http://stackoverflow.com/questions/11398114/vertex-shader-doesnt-run-on-galaxy-tab10-tegra-2
		//http://developer.download.nvidia.com/assets/mobile/files/tegra_gles2_development.pdf
		//https://www.khronos.org/registry/gles/specs/2.0/GLSL_ES_Specification_1.0.17.pdf page 111-113
		"attribute float inPosition; varying float vAmpl; uniform float amplitude[128]; void main() {" \
			"float absx = abs(inPosition);" \
			"float ampl = amplitude[int32_t(floor(63.5 * (absx - 1.0)))];" \
			"gl_Position = vec4(absx - 2.0, sign(inPosition) * ampl, 0.0, 1.0);" \
			"vAmpl = ampl;" \
		"}",

		//fragment shader
		spectrumUsesTexture ? "precision mediump float; varying vec4 vColor; void main() {" \
			"gl_FragColor = vColor;" \
		"}"
		:
		//Tegra GPUs CANNOT use textures in vertex shaders! :(
		//http://stackoverflow.com/questions/11398114/vertex-shader-doesnt-run-on-galaxy-tab10-tegra-2
		//http://developer.download.nvidia.com/assets/mobile/files/tegra_gles2_development.pdf
		"precision mediump float; varying float vAmpl; uniform sampler2D texColor; void main() {" \
			"gl_FragColor = texture2D(texColor, vec2(vAmpl, 0.0));" \
		"}",

		&glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -100;
	glLinkProgram(glProgram);
	if (glGetError()) return -101;

	glGenBuffers(1, glBuf);
	if (glGetError() || !glBuf[0]) return -102;

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
	if (spectrumUsesTexture) {
		for (int32_t i = 0; i < 256; i++) {
			//even is negative, odd is positive
			const float p = 1.0f + (2.0f * (float)i / 255.0f);
			floatBuffer[(i << 1)    ] = -p;
			floatBuffer[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (256 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
	} else {
		for (int32_t i = 0; i < 128; i++) {
			//even is negative, odd is positive
			const float p = 1.0f + (2.0f * (float)i / 127.0f);
			floatBuffer[(i << 1)    ] = -p;
			floatBuffer[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (128 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
	}
	if (glGetError()) return -103;

	uint32_t glTex[2] = { 0, 0 };

	glGenTextures(2, glTex);
	if (glGetError() || !glTex[0] || !glTex[1]) return -104;

	//amplitude texture
	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, glTex[0]);
	if (glGetError()) return -105;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	memset(floatBuffer, 0, 256);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (uint8_t*)floatBuffer);
	if (glGetError()) return -106;

	//color texture (blue or green)
	glActiveTexture(GL_TEXTURE1);
	glBindTexture(GL_TEXTURE_2D, glTex[1]);
	if (glGetError()) return -107;
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	if (glGetError()) return -108;

	//leave everything prepared for fast drawing :)
	glActiveTexture(GL_TEXTURE0);

	glUseProgram(glProgram);
	if (glGetError()) return -109;

	if (spectrumUsesTexture)
		glUniform1i(glGetUniformLocation(glProgram, "texAmplitude"), 0);
	else
		glAmplitude = glGetUniformLocation(glProgram, "amplitude");
	glUniform1i(glGetUniformLocation(glProgram, "texColor"), 1);
	if (glGetError()) return -110;

	glEnableVertexAttribArray(0);
	glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
	glVertexAttribPointer(0, 1, GL_FLOAT, false, 0, 0);
	if (glGetError()) return -111;

	glDrawProc = (spectrumUsesTexture ? glDrawSpectrum : glDrawSpectrumWithoutAmplitudeTexture);

	return 0;
}

int32_t JNICALL glGetOESTexture(JNIEnv* env, jclass clazz) {
	return glOESTexture;
}

int32_t JNICALL glOnSurfaceCreated(JNIEnv* env, jclass clazz, int32_t bgColor, int32_t type, int32_t estimatedWidth, int32_t estimatedHeight, int32_t dp1OrLess, int32_t hasGyro) {
	commonSRand();
	glType = type;
	glResetState();

	//settings common to all OpenGL visualizations

	commonTime = 0;
	commonColorIndex = 0;
	commonColorIndexApplied = 0;

	glClearColor((float)((bgColor >> 16) & 0xff) / 255.0f, (float)((bgColor >> 8) & 0xff) / 255.0f, (float)(bgColor & 0xff) / 255.0f, 1.0f);

	//according to the docs, glTexImage2D initially expects images to be aligned on 4-byte
	//boundaries, but for ANDROID_BITMAP_FORMAT_RGB_565, AndroidBitmap_lockPixels aligns images
	//on 2-byte boundaries, making a few images look terrible!
	glPixelStorei(GL_UNPACK_ALIGNMENT, 2);

	glDisable(GL_DEPTH_TEST);
	glDisable(GL_CULL_FACE);
	glDisable(GL_DITHER);
	glDisable(GL_SCISSOR_TEST);
	glDisable(GL_STENCIL_TEST);
	glDisable(GL_BLEND);
	glGetError(); //clear any eventual error flags

	int32_t ret, hq = 0;

	switch (type) {
	case TYPE_LIQUID:
		ret = glCreateLiquid();
		break;
	case TYPE_SPIN:
		ret = glCreateSpin(estimatedWidth, estimatedHeight, dp1OrLess);
		break;
	case TYPE_PARTICLE:
		ret = glCreateParticle(hasGyro);
		break;
	case TYPE_IMMERSIVE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE_VR:
		ret = glCreateImmersiveParticle(hasGyro);
		break;
	case TYPE_SPECTRUM2:
		hq = 1;
		commonColorIndexApplied = 4; //to control the result of (commonColorIndexApplied != commonColorIndex) inside drawSpectrumXXX()
		ret = glCreateSpectrum2();
		break;
	default:
		hq = 1;
		commonColorIndexApplied = 4; //to control the result of (commonColorIndexApplied != commonColorIndex) inside drawSpectrumXXX()
		ret = glCreateSpectrum();
		break;
	}

	commonUpdateMultiplier(env, clazz, 0, hq);

	glReleaseShaderCompiler();

	return ret;
}

void JNICALL glOnSurfaceChanged(JNIEnv* env, jclass clazz, int32_t width, int32_t height, int32_t rotation, int32_t cameraPreviewW, int32_t cameraPreviewH, int32_t dp1OrLess) {
	glViewport(0, 0, width, height);
	if (glProgram && glBuf[0] && glBuf[1] && width > 0 && height > 0) {
		if (glType == TYPE_SPIN) {
			int32_t size = glComputeSpinSize(width, height, dp1OrLess);
			glVerticesPerRow = ((width + (size - 1)) / size) + 1;
			glRows = ((height + (size - 1)) / size);
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
			for (int32_t j = 0; j < glRows; j++) {
				//compute x and y every time to improve precision
				float y1 = 1.0f - ((float)((size << 1) * (j + 1)) / (float)height);
				for (int32_t i = 0; i < glVerticesPerRow; i++) {
					float x = -1.0f + ((float)((size << 1) * i) / (float)width);
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
			for (int32_t j = 0; j < glRows; j++) {
				for (int32_t i = 0; i < glVerticesPerRow; i++) {
					int32_t idx = (i << 1) + 1;
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
		} else if (glType == TYPE_PARTICLE || glType == TYPE_IMMERSIVE_PARTICLE || glType == TYPE_IMMERSIVE_PARTICLE_VR) {
			if (glSoundParticle)
				glSoundParticle->setAspect(width, height, rotation);
			if (width > height)
				glUniform2f(glGetUniformLocation(glProgram, "aspect"), (float)height / (float)width, 1.0f);
			else
				glUniform2f(glGetUniformLocation(glProgram, "aspect"), 1.0f, (float)width / (float)height);
			if (glType == TYPE_IMMERSIVE_PARTICLE_VR && glBuf[4] && cameraPreviewW > 0 && cameraPreviewH > 0) {
				glBindBuffer(GL_ARRAY_BUFFER, glBuf[4]);
				const float viewRatio = (float)width / (float)height;
				const float cameraRatio = (float)cameraPreviewW / (float)cameraPreviewH;
				float ratioError = viewRatio - cameraRatio;
				*((int32_t*)&ratioError) &= 0x7fffffff; //abs ;)
				if (ratioError <= 0.01f) {
					glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);
				} else {
					//if the camera ratio is too different from the view ratio, compensate for that
					//difference using the texture coords
					//(THIS ALGORITHM WAS A BIT SIMPLIFIED BECAUSE WE KNOW THAT
					//cameraPreviewW < width AND cameraPreviewH < height)
					float leftTex, topTex, rightTex = (float)width, bottomTex = (float)height;
					if (cameraRatio < viewRatio) {
						//crop top and bottom
						const float newH = bottomTex * ((float)cameraPreviewW / rightTex);
						const float diff = (((float)cameraPreviewH - newH) * 0.5f) / (float)cameraPreviewH;
						leftTex = 0.0f;
						topTex = diff;
						rightTex = 1.0f;
						bottomTex = 1.0f - diff;
					} else {
						//crop left and right
						const float newW = rightTex * ((float)cameraPreviewH / bottomTex);
						const float diff = (((float)cameraPreviewW - newW) * 0.5f) / (float)cameraPreviewW;
						leftTex = diff;
						topTex = 0.0f;
						rightTex = 1.0f - diff;
						bottomTex = 1.0f;
					}
					float texCoordsRect[] = {
						leftTex, bottomTex,
						rightTex, bottomTex,
						leftTex, topTex,
						rightTex, topTex
					};
					glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), texCoordsRect, GL_STATIC_DRAW);
				}
			}
		}
	} else {
		glVerticesPerRow = 0;
		glRows = 0;
	}
}

int32_t JNICALL glLoadBitmapFromJava(JNIEnv* env, jclass clazz, jobject bitmap) {
	AndroidBitmapInfo inf;
	if (AndroidBitmap_getInfo(env, bitmap, &inf))
		return ERR_INFO;

	if (inf.format != ANDROID_BITMAP_FORMAT_RGB_565)
		return ERR_FORMAT;

	uint8_t *dst = 0;
	if (AndroidBitmap_lockPixels(env, bitmap, (void**)&dst))
		return ERR_LOCK;

	if (!dst) {
		AndroidBitmap_unlockPixels(env, bitmap);
		return ERR_NOMEM;
	}

	glGetError(); //clear any eventual error flags
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, inf.width, inf.height, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, dst);
	int32_t error = glGetError();

	AndroidBitmap_unlockPixels(env, bitmap);

	return (error ? ERR_GL : 0);
}

void JNICALL glDrawFrame(JNIEnv* env, jclass clazz) {
	glDrawProc();
}

void JNICALL glOnSensorReset(JNIEnv* env, jclass clazz) {
	if (glSoundParticle)
		glSoundParticle->onSensorReset();
}

void JNICALL glOnSensorData(JNIEnv* env, jclass clazz, uint64_t sensorTimestamp, int32_t sensorType, jfloatArray jvalues) {
	if (!glSoundParticle || !jvalues)
		return;
	float* values = (float*)env->GetPrimitiveArrayCritical(jvalues, 0);
	float v[] = { values[0], values[1], values[2] };
	env->ReleasePrimitiveArrayCritical(jvalues, values, JNI_ABORT);
	glSoundParticle->onSensorData(sensorTimestamp, sensorType, v);
}

void JNICALL glSetImmersiveCfg(JNIEnv* env, jclass clazz, int32_t diffusion, int32_t riseSpeed) {
	if (!glSoundParticle || (glType != TYPE_IMMERSIVE_PARTICLE && glType != TYPE_IMMERSIVE_PARTICLE_VR))
		return;
	glSoundParticle->setImmersiveCfg(diffusion, riseSpeed);
}

void JNICALL glReleaseView(JNIEnv* env, jclass clazz) {
	if (glSoundParticle) {
		delete glSoundParticle;
		glSoundParticle = 0;
	}
}
