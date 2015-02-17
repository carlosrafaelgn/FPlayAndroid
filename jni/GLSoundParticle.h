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

	float COLORS[16 * 3];

	float bgPos[BG_COUNT * 2], bgSpeedY[BG_COUNT], bgTheta[BG_COUNT];
	unsigned char bgColor[BG_COUNT];

	unsigned int sensorData, lastSensorTime, landscape;
	float matrix[9], accelData[3], magneticData[3], oldAccelData[3], oldMagneticData[3];

	void FillBgParticle(int index, float y) {
		bgPos[(index << 1)] = 0.0078125f * (float)(((int)rand() & 7) - 4);
		bgPos[(index << 1) + 1] = y;
		bgTheta[index] = 0.03125f * (float)(rand() & 63);
		bgSpeedY[index] = 0.125f + (0.00390625f * (float)(rand() & 15));
		bgColor[index] = rand() & 15;
	}

public:
	GLSoundParticle() {
		lastTime = commonTime;
		timeCoef = 0.001f;

		sensorData = 0;
		lastSensorTime = 0;
		landscape = 0;
		memset(matrix, 0, sizeof(float) * 9);
		memset(accelData, 0, sizeof(float) * 3);
		memset(magneticData, 0, sizeof(float) * 3);
		memset(oldAccelData, 0, sizeof(float) * 3);
		memset(oldMagneticData, 0, sizeof(float) * 3);
		matrix[0] = 1.0f;
		matrix[4] = 1.0f;
		matrix[8] = 1.0f;

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

		int i = 0, c, ic;
		for (c = 0; c < BG_COLUMNS; c++) {
			for (ic = 0; ic < BG_PARTICLES_BY_COLUMN; ic++, i++)
				FillBgParticle(i, -1.2f + (0.01953125f * (float)(rand() & 127)));
		}
	}

	static void FillTexture() {
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
				{
					d = 0.0f;
				}
				else if (d < 1.0f)
				{
					d = glSmoothStep(0.55f, 1.0f, d);
				}

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

	void SetAspect(int width, int height) {
		//change the time coefficient to slow down the particles when in portrait mode
		timeCoef = ((width >= height) ? 0.001f : (0.001f * (float)width / (float)height));
		landscape = (width >= height);
	}

	void Draw() {
		float delta = (float)(commonTime - lastTime) * timeCoef;
		lastTime = commonTime;

		glClear(GL_COLOR_BUFFER_BIT);

		float a;
		int p = 0, c, ic, i = 2, last = 44, last2 = 116;
		unsigned char avg, *processedData = (unsigned char*)(floatBuffer + 512);

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
			//the 31 columns spread from -0.9 to 0.9, and they are evenly spaced
			glUniform1f(glBaseX, -0.9f + (0.06206897f * (float)c));

			for (ic = 0; ic < BG_PARTICLES_BY_COLUMN; ic++, p++) {
				if (bgPos[(p << 1) + 1] > 1.2f)
					FillBgParticle(p, -1.2f);
				else
					bgPos[(p << 1) + 1] += bgSpeedY[p] * delta;
				glUniform3fv(glColor, 1, COLORS + (bgColor[p] * 3));
				glUniform2fv(glPos, 1, bgPos + (p << 1));
				glUniform1f(glTheta, bgTheta[p]);
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
			}
		}

/*float amplitude[33];
vec2 pos;
int index;
int bassIndex;
vec2 size;
vec3 color;*/
	}

	void OnSensorData(int sensorType, float* values) {
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
		const float coefNew = (0.09375f / 16.0f) * (float)commonUptimeDeltaMillis(&lastSensorTime); //0.09375 @ 60fps (~16ms)
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
		const float Mx = (Ay * Hz) - (Az * Hy);
		const float My = (Az * Hx) - (Ax * Hz);
		const float Mz = (Ax * Hy) - (Ay * Hx);
		matrix[0] = Hx; matrix[1] = Hy; matrix[2] = Hz;
		matrix[3] = Mx; matrix[4] = My; matrix[5] = Mz;
		matrix[6] = Ax; matrix[7] = Ay; matrix[8] = Az;
		//SensorManager.getRotationMatrix() returns the matrix in row-major order and
		//OpenGL needs the matrices in column-major order... nevertheless we must not
		//transpose this matrix, as it will be used as the camera matrix, and the camera
		//matrix is the inverse of the world matrix (luckly, the inverse of a pure
		//rotation matrix is also its transpose!)
	}
};

static GLSoundParticle* glSoundParticle;

#undef BG_COLUMNS
#undef BG_PARTICLES_BY_COLUMN
#undef BG_COUNT
