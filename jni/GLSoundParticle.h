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
			//increase the amplitudes in order to improve the effect
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
};

static GLSoundParticle* glSoundParticle;

#undef BG_COLUMNS
#undef BG_PARTICLES_BY_COLUMN
#undef BG_COUNT
