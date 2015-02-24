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

#define BG_COLUMNS 31
#define BG_PARTICLES_BY_COLUMN 16
#define BG_COUNT (BG_COLUMNS * BG_PARTICLES_BY_COLUMN)

class GLSoundParticle {
private:
	unsigned int lastTime;
	float timeCoef;

	float COLORS[16 * 3], BASEX[BG_COLUMNS], bgPos[BG_COUNT * 2], bgSpeedY[BG_COUNT], bgTheta[BG_COUNT], bgColor[BG_COUNT * 3];

	unsigned int sensorData, lastSensorTime, landscape, nextDiffusion;
	float matrix[16], accelData[3], magneticData[3], oldAccelData[3], oldMagneticData[3], screenLargestSize, xScale, yScale;

	SimpleMutex mutex;

	void fillBgParticle(int index, float y) {
		bgPos[(index << 1)] = 0.0078125f * (float)(((int)rand() & 7) - 4);
		bgPos[(index << 1) + 1] = y;
		bgTheta[index] = 0.03125f * (float)(rand() & 63);
		bgSpeedY[index] = 0.125f + (0.00390625f * (float)(rand() & 15));
		index += (index << 1); //index *= 3;
		int colorIdx = rand() & 15;
		colorIdx += (colorIdx << 1); //colorIdx *= 3;
		bgColor[index    ] = COLORS[colorIdx    ];
		bgColor[index + 1] = COLORS[colorIdx + 1];
		bgColor[index + 2] = COLORS[colorIdx + 2];
	}

public:
	GLSoundParticle() {
		lastTime = commonTime;
		timeCoef = ((glType == TYPE_IMMERSIVE_PARTICLE) ? 0.0003f : 0.001f);

		sensorData = 0;
		lastSensorTime = 0;
		landscape = 0;
		nextDiffusion = 1;
		yScale = 0.0f;
		xScale = 0.0f;
		memset(matrix, 0, sizeof(float) * 16);
		memset(accelData, 0, sizeof(float) * 3);
		memset(magneticData, 0, sizeof(float) * 3);
		memset(oldAccelData, 0, sizeof(float) * 3);
		memset(oldMagneticData, 0, sizeof(float) * 3);
#define zNear 1.0f
#define zFar 50.0f
#define fovCoefA (zFar / (zNear - zFar))
#define fovCoefB ((zNear * zFar) / (zNear - zFar))
		matrix[14] = fovCoefB; //-1 //for the fov matrix

#define FULL 0.75f
#define HALF 0.325f
#define ZERO 0.0f
#define COLORS_R(A, B) COLORS[(3 * A)] = B
#define COLORS_G(A, B) COLORS[(3 * A) + 1] = B
#define COLORS_B(A, B) COLORS[(3 * A) + 2] = B

		COLORS_R( 0, FULL); COLORS_G( 0, ZERO); COLORS_B( 0, ZERO);
		COLORS_R( 1, ZERO); COLORS_G( 1, FULL); COLORS_B( 1, ZERO);
		COLORS_R( 2, ZERO); COLORS_G( 2, ZERO); COLORS_B( 2, FULL);
		COLORS_R( 3, FULL); COLORS_G( 3, ZERO); COLORS_B( 3, FULL);
		COLORS_R( 4, FULL); COLORS_G( 4, FULL); COLORS_B( 4, ZERO);
		COLORS_R( 5, ZERO); COLORS_G( 5, FULL); COLORS_B( 5, FULL);
		COLORS_R( 6, FULL); COLORS_G( 6, FULL); COLORS_B( 6, FULL);
		COLORS_R( 7, FULL); COLORS_G( 7, HALF); COLORS_B( 7, ZERO);
		COLORS_R( 8, FULL); COLORS_G( 8, ZERO); COLORS_B( 8, HALF);
		COLORS_R( 9, HALF); COLORS_G( 9, FULL); COLORS_B( 9, ZERO);
		COLORS_R(10, ZERO); COLORS_G(10, FULL); COLORS_B(10, HALF);
		COLORS_R(11, ZERO); COLORS_G(11, HALF); COLORS_B(11, FULL);
		COLORS_R(12, HALF); COLORS_G(12, ZERO); COLORS_B(12, FULL);
		//the colors I like most appear twice ;)
		COLORS_R(13, ZERO); COLORS_G(13, ZERO); COLORS_B(13, FULL);
		COLORS_R(14, FULL); COLORS_G(14, HALF); COLORS_B(14, ZERO);
		COLORS_R(15, ZERO); COLORS_G(15, HALF); COLORS_B(15, FULL);

#undef FULL
#undef HALF
#undef ZERO
#undef COLORS_R
#undef COLORS_G
#undef COLORS_B

		//we will render BG_PARTICLES_BY_COLUMN particles at a time (which is one column)
		int i = 0, c, ic;
		float *pos = bgPos, *idx = bgSpeedY;
		unsigned char *indices = (unsigned char*)bgTheta, first = 0;
		for (i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
			//left bottom
			pos[0] = -1.0f;
			pos[1] = -1.0f;
			//right bottom
			pos[2] =  1.0f;
			pos[3] = -1.0f;
			//left top
			pos[4] = -1.0f;
			pos[5] =  1.0f;
			//right top
			pos[6] =  1.0f;
			pos[7] =  1.0f;
			pos += 8;

			idx[0] = (float)i;
			idx[1] = (float)i;
			idx[2] = (float)i;
			idx[3] = (float)i;
			idx += 4;

			//triangle 0
			indices[0] = first;
			indices[1] = first + 1;
			indices[2] = first + 2;
			//triangle 1
			indices[3] = first + 2;
			indices[4] = first + 1;
			indices[5] = first + 3;
			indices += 6;
			first += 4;
		}

		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 4 * 2) * sizeof(float), bgPos, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 4) * sizeof(int), bgSpeedY, GL_STATIC_DRAW);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, glBuf[2]);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, BG_PARTICLES_BY_COLUMN * 6, bgTheta, GL_STATIC_DRAW);

		i = 0;
		for (c = 0; c < BG_COLUMNS; c++) {
			//the 31 columns spread from -0.9 to 0.9, and they are evenly spaced
			BASEX[c] = -0.9f + (0.06206897f * (float)c);
			for (ic = 0; ic < BG_PARTICLES_BY_COLUMN; ic++, i++)
				fillBgParticle(i, -1.2f + (0.01953125f * (float)(rand() & 127)));
		}

	}

	static void fillTexture() {
#define TEXTURE_SIZE 64
		unsigned char *tex = new unsigned char[TEXTURE_SIZE * TEXTURE_SIZE];

		glActiveTexture(GL_TEXTURE0);
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			float yf = (float)(y - (TEXTURE_SIZE >> 1));
			yf *= yf;
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				float xf = (float)(x - (TEXTURE_SIZE >> 1));

				float d = sqrtf((xf * xf) + yf) / (float)((TEXTURE_SIZE / 2) - 2.0f);
				if (d > 1.0f) d = 1.0f;

				float d2 = d;
				d = 1.0f - d;
				d = d * d;
				d = d + (0.5f * d);
				if (d < 0.55f)
					d = 0.0f;
				else if (d < 1.0f)
					d = glSmoothStep(0.55f, 1.0f, d);

				d2 = 1.0f - d2;
				d2 = glSmoothStep(0.0f, 1.0f, d2);
				d2 = d2 * d2;
				d2 = d2 + d2;
				if (d2 > 1.0f) d2 = 1.0f;

				d = (d + 0.5f * d2);

				unsigned int v = (unsigned int)(255.0f * d);
				tex[(y * TEXTURE_SIZE) + x] = ((v >= 255) ? 255 : (unsigned char)v);
			}
		}
		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_ALPHA, GL_UNSIGNED_BYTE, tex);

		delete tex;
#undef TEXTURE_SIZE
	}

	void setAspect(int width, int height) {
		if (glType == TYPE_IMMERSIVE_PARTICLE) {
			if (width >= height) {
				landscape = 1;
				//yScale = cot(fovY / 2) = cot(fovYInDegrees * PI / 360) //cot(x) = tan(PI/2 - x)
				//considering fovYInDegrees = 50 deg:
				yScale = 2.1445069205095586163562607910459f;
			} else {
				landscape = 0;
				//in this case, we must make up for the extended height, and increase
				//the fov proportionally (0.43633231299858239423092269212215 = 50 * PI / 360)
				yScale = tanf(1.5707963267948966192313216916398f - (0.43633231299858239423092269212215f * (float)height / (float)width));
			}
			//xScale = yScale / aspect ratio
			xScale = yScale * (float)height / (float)width;
		} else {
			//change the time coefficient to slow down the particles when in portrait mode
			timeCoef = ((width >= height) ? 0.001f : (0.001f * (float)width / (float)height));
		}
	}

	void setImmersiveCfg(int diffusion, int riseSpeed) {
		if (diffusion >= 0)
			nextDiffusion = diffusion + 1;
		if (riseSpeed >= 0) {
			switch (riseSpeed) {
			case 0:
				timeCoef = 0.0f;
				break;
			case 2:
				timeCoef = 0.001f;
				break;
			case 3:
				timeCoef = 0.0017f;
				break;
			default:
				timeCoef = 0.0003f;
				break;
			}
		}
	}

	void draw() {
		float delta = (float)(commonTime - lastTime) * timeCoef;
		lastTime = commonTime;

		glClear(GL_COLOR_BUFFER_BIT);

		float a;
		int p = 0, c, ic, i = 2, last = 44, last2 = 116;
		unsigned char avg, *processedData = (unsigned char*)(floatBuffer + 512);

		if (glType == TYPE_IMMERSIVE_PARTICLE) {
			if (nextDiffusion) {
				//not perfect... but good enough ;)
				c = nextDiffusion;
				nextDiffusion = 0;
				switch (c) {
				case 1:
					*((float*)&c) = 0.0f;
					break;
				case 3:
					*((float*)&c) = 10.0f;
					break;
				case 4:
					*((float*)&c) = 15.0f;
					break;
				default:
					*((float*)&c) = 5.0f;
					break;
				}
				glUniform1f(glGetUniformLocation(glProgram, "diffusion"), *((float*)&c));
			}
			mutex.enter0();
			glUniformMatrix4fv(glMatrix, 1, 0, matrix);
			mutex.leave0();
		}

		for (c = 0; c < BG_COLUMNS; c++) {
#define MAX(A,B) (((A) > (B)) ? (A) : (B))
			//increase the amplitudes as the frequency increases, in order to improve the effect
			if (i < 6) {
				a = (float)processedData[i] / 255.0f;
				i++;
			} else if (i < 20) {
				a = 1.5f * (float)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1]) >> 1) / 255.0f;
				i += 2;
			} else if (i < 36) {
				a = 1.5f * (float)(((unsigned int)processedData[i] + (unsigned int)processedData[i + 1] + (unsigned int)processedData[i + 2] + (unsigned int)processedData[i + 3]) >> 2) / 255.0f;
				i += 4;
			} else if (i < 100) {
				avg = 0;
				for (; i < last; i++)
					avg = MAX(avg, processedData[i]);
				a = 2.0f * (float)avg / 255.0f;
				last += 8;
			} else {
				avg = 0;
				for (; i < last2; i++)
					avg = MAX(avg, processedData[i]);
				a = 2.5f * (float)avg / 255.0f;
				last2 += 16;
			}
#undef MAX
			glUniform1f(glAmplitude, (a >= 1.0f) ? 1.0f : a);
			glUniform1f(glBaseX, BASEX[c]);

			for (ic = 0; ic < BG_PARTICLES_BY_COLUMN; ic++, p++) {
				if (bgPos[(p << 1) + 1] > 1.2f)
					fillBgParticle(p, -1.2f);
				else
					bgPos[(p << 1) + 1] += bgSpeedY[p] * delta;
			}

			p -= BG_PARTICLES_BY_COLUMN;
			glUniform3fv(glColor, BG_PARTICLES_BY_COLUMN, bgColor + (p * 3));
			glUniform2fv(glPos, BG_PARTICLES_BY_COLUMN, bgPos + (p << 1));
			glUniform1fv(glTheta, BG_PARTICLES_BY_COLUMN, bgTheta + p);
			p += BG_PARTICLES_BY_COLUMN;

			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, glBuf[2]);
			glDrawElements(GL_TRIANGLES, (BG_PARTICLES_BY_COLUMN * 6), GL_UNSIGNED_BYTE, 0);
		}
	}

	void onSensorData(int sensorType, float* values) {
		if (sensorType == 1) {
			accelData[0] = values[0];
			accelData[1] = values[1];
			accelData[2] = values[2];
			sensorData |= 1;
		} else {
			magneticData[0] = values[0];
			magneticData[1] = values[1];
			magneticData[2] = values[2];
			sensorData |= 2;
		}
		if (sensorData != 3)
			return;
		sensorData = 0;
		if (lastSensorTime == 0) {
			oldAccelData[0] = accelData[0];
			oldAccelData[1] = accelData[1];
			oldAccelData[2] = accelData[2];
			oldMagneticData[0] = magneticData[0];
			oldMagneticData[1] = magneticData[1];
			oldMagneticData[2] = magneticData[2];
			commonUptimeDeltaMillis(&lastSensorTime);
			return;
		}
		const float coefNew = (0.03125f / 16.0f) * (float)commonUptimeDeltaMillis(&lastSensorTime); //0.03125 @ 60fps (~16ms)
		const float coefOld = 1.0f - coefNew;
		accelData[0] = (oldAccelData[0] * coefOld) + (accelData[0] * coefNew);
		accelData[1] = (oldAccelData[1] * coefOld) + (accelData[1] * coefNew);
		accelData[2] = (oldAccelData[2] * coefOld) + (accelData[2] * coefNew);
		oldAccelData[0] = accelData[0];
		oldAccelData[1] = accelData[1];
		oldAccelData[2] = accelData[2];
		magneticData[0] = (oldMagneticData[0] * coefOld) + (magneticData[0] * coefNew);
		magneticData[1] = (oldMagneticData[1] * coefOld) + (magneticData[1] * coefNew);
		magneticData[2] = (oldMagneticData[2] * coefOld) + (magneticData[2] * coefNew);
		oldMagneticData[0] = magneticData[0];
		oldMagneticData[1] = magneticData[1];
		oldMagneticData[2] = magneticData[2];
		//SensorManager.getRotationMatrix(matrix, null, accelData, magneticData);
		//Original code -> AOSP: http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.2_r1/android/hardware/SensorManager.java
		//(just porting from Java to C++ to improve performance)
		float Ax, Ay, Az, Ex, Ey, Ez;
		if (landscape) {
			Ax = -accelData[1];
			Ay = accelData[0];
			Az = accelData[2];
			Ex = -magneticData[1];
			Ey = magneticData[0];
			Ez = magneticData[2];
		} else {
			Ax = accelData[0];
			Ay = accelData[1];
			Az = accelData[2];
			Ex = magneticData[0];
			Ey = magneticData[1];
			Ez = magneticData[2];
		}
		float Hx = (Ey * Az) - (Ez * Ay);
		float Hy = (Ez * Ax) - (Ex * Az);
		float Hz = (Ex * Ay) - (Ey * Ax);
		const float normH = (float)sqrtf((Hx * Hx) + (Hy * Hy) + (Hz * Hz));
		if (normH < 0.1f) {
			//device is close to free fall (or in space?), or close to
			//magnetic north pole. Typical values are > 100...
			//leave the matrix as-is!
			return;
		}
		const float invH = 1.0f / normH;
		Hx *= invH;
		Hy *= invH;
		Hz *= invH;
		const float invA = 1.0f / (float)sqrtf((Ax * Ax) + (Ay * Ay) + (Az * Az));
		Ax *= invA;
		Ay *= invA;
		Az *= invA;
		Ex = (Ay * Hz) - (Az * Hy);
		Ey = (Az * Hx) - (Ax * Hz);
		Ez = (Ax * Hy) - (Ay * Hx);

		//SensorManager.getRotationMatrix() returns the matrix in row-major order and
		//OpenGL needs the matrices in column-major order... nevertheless we must not
		//transpose this matrix, as it will be used as the camera/view matrix, and the
		//view matrix is the inverse of the world matrix (luckly, the inverse of a pure
		//rotation matrix is also its transpose!)

		//now, we apply a projection matrix with a fov of 50 deg
		//based on D3DXMatrixPerspectiveFovRH
		//http://msdn.microsoft.com/en-us/library/windows/desktop/bb205351(v=vs.85).aspx
		//...as a matter of fact, this is a port from my original JavaScript implementation:
		//http://carlosrafaelgn.com.br/WebGL/Matrix4.js

		//matrix = fov * view

		//optimizations:
		//- assume m3, m7, m11, m12, m13, m14 are 0 and m15 is 1 before applying the fov matrix
		//- use macros intead of storing everything in the actual matrix, just to read everything
		//back again to apply the fov
#define m0 Hx
#define m1 Hy
#define m2 Hz
#define m4 Ex
#define m5 Ey
#define m6 Ez
#define m8 Ax
#define m9 Ay
#define m10 Az
		mutex.enter1();
		matrix[0] = m0 * xScale;
		matrix[4] = m4 * xScale;
		matrix[8] = m8 * xScale;
		matrix[1] = m1 * yScale;
		matrix[5] = m5 * yScale;
		matrix[9] = m9 * yScale;
		matrix[2] = m2 * fovCoefA;
		matrix[6] = m6 * fovCoefA;
		matrix[10] = m10 * fovCoefA;
		matrix[3] = -m2; //m2 * fovCoefB;
		matrix[7] = -m6; //m6 * fovCoefB;
		matrix[11] = -m10; //m10 * fovCoefB;
		mutex.leave1();
#undef m0
#undef m1
#undef m2
#undef m4
#undef m5
#undef m6
#undef m8
#undef m9
#undef m10
#undef zNear //defined inside the constructor
#undef zFar
#undef fovCoefA
#undef fovCoefB
	}
};

static GLSoundParticle* glSoundParticle;

#undef BG_COLUMNS
#undef BG_PARTICLES_BY_COLUMN
#undef BG_COUNT
