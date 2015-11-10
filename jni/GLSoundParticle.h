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

#include "LinearAlg.h"
#include "HeadTracker.h"

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

	unsigned int rotation, nextDiffusion;
	float matrix[16], xScale, yScale;

	HeadTracker* headTracker;

	SimpleMutex mutex;

	void fillBgParticle(int index, float y) {
		bgPos[(index << 1)] = 0.0078125f * (float)(((int)rand() & 7) - 4);
		bgPos[(index << 1) + 1] = y;
		bgTheta[index] = 0.03125f * (float)(rand() & 63);
		bgSpeedY[index] = 0.125f + (0.00390625f * (float)(rand() & 15));
		bgColor[index] = rand() & 15;
	}

public:
	GLSoundParticle() {
		lastTime = commonTime;
		timeCoef = ((glType == TYPE_IMMERSIVE_PARTICLE_VR) ? 0.0017f : ((glType == TYPE_IMMERSIVE_PARTICLE) ? 0.0003f : 0.001f));

		rotation = 0;
		nextDiffusion = ((glType == TYPE_IMMERSIVE_PARTICLE_VR) ? 4 : 1);
		yScale = 0.0f;
		xScale = 0.0f;
		headTracker = ((glType == TYPE_PARTICLE) ? 0 : new HeadTracker());

#define zNear 1.0f
#define zFar 50.0f
#define fovA (zFar / (zNear - zFar))
#define fovB ((zNear * zFar) / (zNear - zFar))
		memset(matrix, 0, sizeof(float) * 16);
		matrix[14] = fovB;

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
				fillBgParticle(i, -1.2f + (0.01953125f * (float)(rand() & 127)));
		}
	}

	~GLSoundParticle() {
		if (headTracker) {
			delete headTracker;
			headTracker = 0;
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

				if (d >= 1.0f) d = 0.0f;
				else d = 1.0f - d;
				float d2 = d;

				//we increased the particle size, while decreasing the outer glow,
				//in order to improve the pixel usage in the center and leave a
				//10-pixel border around the image that is safe to be cropped
				/*d = d * d;
				d = d + (0.5f * d);
				if (d < 0.55f)
					d = 0.0f;
				else if (d < 1.0f)
					d = glSmoothStep(0.55f, 1.0f, d);*/

				d2 = glSmoothStep(0.2f, 1.1f, d2); //0.0f, 1.0f, d2);
				d2 = d2 * d2;
				d2 = d2 + d2;
				if (d2 > 1.0f) d2 = 1.0f;

				const unsigned int v = (unsigned int)(255.0f * d2); //(255.0f * (d + 0.5f * d2));
				tex[(y * TEXTURE_SIZE) + x] = ((v >= 255) ? 255 : (unsigned char)v);
			}
		}
		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_ALPHA, GL_UNSIGNED_BYTE, tex);

		delete tex;
#undef TEXTURE_SIZE
	}

	void setAspect(int width, int height, int rotation) {
		if (headTracker) { //if (glType != TYPE_PARTICLE) {
			this->rotation = rotation;
			if (width >= height) {
				//landscape
				//yScale = cot(fovY / 2) = cot(fovYInDegrees * PI / 360) //cot(x) = tan(PI/2 - x)
				//considering fovYInDegrees = 50 deg:
				yScale = 2.1445069205095586163562607910459f;
			} else {
				//portrait
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

		float a;
		int p = 0, c, ic, i = 2, last = 44, last2 = 116;
		unsigned char avg, *processedData = (unsigned char*)(floatBuffer + 512);

		if (headTracker) { //if (glType != TYPE_PARTICLE) {
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
			headTracker->getLastHeadView(matrix);
			mutex.leave0();

			//HeadTracker::getRotationMatrix() returns the matrix in row-major order and
			//OpenGL needs the matrices in column-major order... nevertheless we must not
			//transpose this matrix, as it will be used as the camera/view matrix, and the
			//view matrix is the inverse of the world matrix (luckly, the inverse of a pure
			//rotation matrix is also its transpose!)

			//row-major means array indices are distributed by rows, not by columns!
			//for example, the matrix returned by SensorManager.getRotationMatrix()
			//is distributed like this:
			//   array index          value
			// | 0  1  2  3  |   | Hx Hy Hz 0 |
			// | 4  5  6  7  |   | Mx My Mz 0 |
			// | 8  9  10 11 |   | Ax Ay Az 0 |
			// | 12 13 14 15 |   | 0  0  0  1 |
			//if it were column-major, the indices would be like this:
			//   array index          value
			// | 0  4  8  12 |   | Hx Hy Hz 0 |
			// | 1  5  9  13 |   | Mx My Mz 0 |
			// | 2  6  10 14 |   | Ax Ay Az 0 |
			// | 3  7  11 15 |   | 0  0  0  1 |

			//now, we apply a projection matrix with a fov of 50 deg
			//based on D3DXMatrixPerspectiveFovRH
			//http://msdn.microsoft.com/en-us/library/windows/desktop/bb205351(v=vs.85).aspx
			//...as a matter of fact, this is a port from my original JavaScript implementation:
			//http://carlosrafaelgn.com.br/WebGL/Matrix4.js -> perspectiveFovFromLeft

			//matrix = fov * view

			//optimization: assume m3, m7, m11, m12, m13, m14 were 0
			//and m15 was 1 before applying the fov matrix
			const float m2 = matrix[2], m6 = matrix[6], m10 = matrix[10]; //, m14 = matrix[14];
			matrix[0] *= xScale;
			matrix[4] *= xScale;
			matrix[8] *= xScale;
			//matrix[12] *= xScale;
			matrix[1] *= yScale;
			matrix[5] *= yScale;
			matrix[9] *= yScale;
			//matrix[13] *= yScale;
			matrix[2] = (fovA * m2); // + (fovB * matrix[3]);
			matrix[6] = (fovA * m6); // + (fovB * matrix[7]);
			matrix[10] = (fovA * m10); // + (fovB * matrix[11]);
			//matrix[14] = fovB; //(fovA * m14) + (fovB * matrix[15]);
			matrix[3] = -m2;
			matrix[7] = -m6;
			matrix[11] = -m10;
			//matrix[15] = -m14;
#undef zNear
#undef zFar
#undef fovA
#undef fovB
			glUniformMatrix4fv(glMatrix, 1, 0, matrix);
		}

		for (c = 0; c < BG_COLUMNS; c++) {
#define MAX(A,B) (((A) > (B)) ? (A) : (B))
			//instead of dividing by 255, we are dividing by 256 (* 0.00390625f)
			//since the difference is visually unnoticeable

			//increase the amplitudes as the frequency increases, in order to improve the effect
			if (i < 6) {
				a = (float)processedData[i] * 0.00390625f;
				i++;
			} else if (i < 20) {
				a = (float)MAX(processedData[i], processedData[i + 1]) * (1.5f * 0.00390625f);
				i += 2;
			} else if (i < 36) {
				avg = MAX(processedData[i], processedData[i + 1]);
				avg = MAX(avg, processedData[i + 2]);
				avg = MAX(avg, processedData[i + 3]);
				a = (float)avg * (1.5f * 0.00390625f);
				i += 4;
			} else if (i < 100) {
				avg = processedData[i++];
				for (; i < last; i++)
					avg = MAX(avg, processedData[i]);
				a = (float)avg * (2.0f * 0.00390625f);
				last += 8;
			} else {
				avg = processedData[i++];
				for (; i < last2; i++)
					avg = MAX(avg, processedData[i]);
				a = (float)avg * (2.5f * 0.00390625f);
				last2 += 16;
			}
#undef MAX
			glUniform1f(glAmplitude, (a >= 1.0f) ? 1.0f : a);
			//the 31 columns spread from -0.9 to 0.9, and they are evenly spaced
			glUniform1f(glBaseX, -0.9f + (0.06206897f * (float)c));

			for (ic = 0; ic < BG_PARTICLES_BY_COLUMN; ic++, p++) {
				if (bgPos[(p << 1) + 1] > 1.2f)
					fillBgParticle(p, -1.2f);
				else
					bgPos[(p << 1) + 1] += bgSpeedY[p] * delta;
				glUniform3fv(glColor, 1, COLORS + (bgColor[p] * 3));
				glUniform2fv(glPos, 1, bgPos + (p << 1));
				glUniform1f(glTheta, bgTheta[p]);
				glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
			}
		}
	}

	void onSensorReset() {
		if (headTracker)
			headTracker->onSensorReset();
	}

	void onSensorData(uint64_t sensorTimestamp, int sensorType, const float* values) {
		if (headTracker) {
			Vector3 v;
			switch (rotation) {
			case 1: //ROTATION_90
				v.x = (ftype)-values[1];
				v.y = (ftype)values[0];
				break;
			case 2: //ROTATION_180
				v.x = (ftype)-values[0];
				v.y = (ftype)-values[1];
				break;
			case 3: //ROTATION_270
				v.x = (ftype)values[1];
				v.y = (ftype)-values[0];
				break;
			default: //ROTATION_0
				v.x = (ftype)values[0];
				v.y = (ftype)values[1];
				break;
			}
			v.z = (ftype)values[2];
			mutex.enter1();
			headTracker->onSensorData(sensorTimestamp, sensorType, v);
			mutex.leave1();
		}
	}
};

static GLSoundParticle* glSoundParticle;

#undef BG_COLUMNS
#undef BG_PARTICLES_BY_COLUMN
#undef BG_COUNT
