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

//==============================================================================
//
// This algorithm is an adaptation of the algorithm from the hardcover
// book "Numerical Recipes: The Art of Scientific Computing, 3rd Edition",
// with some additional optimizations and changes.
//
// I HIGHLY recommend this book!!! :D
//
//==============================================================================

class FFTNR {
private:
	static float tblW[56];
	static uint32_t tblBr[FFT_SIZE >> 1];
	static void Complex(float* data, int32_t isign);
	static void Real(float* data, int32_t isign);
public:
	static void Initialize();
	inline static void Forward(float* data) { Real(data, 1); }
	inline static void Inverse(float* data) { Real(data, -1); }
};

// Ordering of data
// time [0]              | Real [bin 0]
// time [1]              | Real [bin FFT_SIZE/2]
// time [2]              | Real [bin 1]
// time [3]              | Imag [bin 1]
// time [...]            | Real [bin ...]
// time [...]            | Imag [bin ...]
// time [FFT_SIZE - 2]   | Real [bin FFT_SIZE/2 - 1]
// time [FFT_SIZE - 1]   | Imag [bin FFT_SIZE/2 - 1]

// In order to obtain the original sines' amplitudes, after converting from rectangular notation (real+imaginary)
// to polar notation (amplitude+phase), we must divide each amplitude by FFT_SIZE/2
// In addition, the first and last bins (bin 0 and bin FFT_SIZE/2) must be divided again by 2

float FFTNR::tblW[56];
uint32_t FFTNR::tblBr[FFT_SIZE >> 1];

void FFTNR::Initialize() {
	int32_t i, j = 1, m, c = 0, mmax;
	double theta, wpr;
	for (i = 1; i < FFT_SIZE; i += 2) {
		if (j > i) {
			tblBr[c] = i;
			tblBr[c + 1] = j;
			c += 2;
		}
		m = FFT_SIZE >> 1;
		while (m >= 2 && j > m) {
			j -= m;
			m >>= 1;
		}
		j += m;
	}
	tblBr[c] = FFT_SIZE;

	i = 1;
	mmax = 8;
	theta = 6.283185307179586476925286766559 * 0.125;
	tblW[0] = -1;
	tblW[16] = 1;
	tblW[32] = -1;
	while (mmax < FFT_SIZE) {
		mmax <<= 1;
		tblW[i + 32] = -(tblW[i + 16] = (float)sin(theta));
		theta *= 0.5;
		wpr = sin(theta);
		tblW[i] = (float)(-2.0 * wpr * wpr);
		i++;
	}

	tblW[48] = (float)(2.0 / (double)FFT_SIZE);
	theta = (float)(3.1415926535897932384626433832795 / (double)(FFT_SIZE >> 1));
	wpr = sin(0.5 * theta);
	wpr = -2.0 * wpr * wpr;
	tblW[49] = (float)wpr; //wpr
	tblW[50] = (float)(1.0 + wpr); //wr
	tblW[51] = (float)sin(theta); //wi, wpi
	tblW[52] = -tblW[51]; //wi, wpi (-1)
}

void FFTNR::Complex(float* data, int32_t isign) {
	const float *tblr = tblW;
	const float *tbli = ((isign == 1) ? (tblW + 16) : (tblW + 32));
	int32_t mmax, m, istep, i, j;
	float wr, wi, tempr, tempi, dj1, dj;
	m = 0;
	//bit reversal swap
	while ((i = tblBr[m]) < FFT_SIZE) {
		j = tblBr[m + 1];
		m += 2;
		tempr = data[j - 1];
		data[j - 1] = data[i - 1];
		data[i - 1] = tempr;
		tempi = data[j];
		data[j] = data[i];
		data[i] = tempi;
	}
	//first pass (mmax = 2 / wr = 1 / wi = 0)
	for (i = 1; i <= FFT_SIZE; i += 4) {
		j = i + 2;
		data[j - 1] = data[i - 1] - (tempr = data[j - 1]);
		data[j] = data[i] - (tempi = data[j]);
		data[i - 1] += tempr;
		data[i] += tempi;
	}

	mmax = 4;

	while (mmax < FFT_SIZE) {
		istep = mmax << 1;
		const float wpr = *tblr++;
		const float wpi = *tbli++;
		//---------------------------------------------
		//special case for the inner loop when m = 1:
		//wr = 1 / wi = 0
		for (i = 1; i <= FFT_SIZE; i += istep) {
			j = i + mmax;
			data[j - 1] = data[i - 1] - (tempr = data[j - 1]);
			data[j] = data[i] - (tempi = data[j]);
			data[i - 1] += tempr;
			data[i] += tempi;
		}
		wr = 1.0f + wpr;
		wi = wpi;
		//---------------------------------------------
		const int32_t halfmmax = ((mmax >> 1) + 1);
		for (m = 3; m < halfmmax; m += 2) {
			for (i = m; i <= FFT_SIZE; i += istep) {
				j = i + mmax;
				data[j - 1] = (data[i - 1] - (tempr = (wr * (dj1 = data[j - 1])) - (wi * (dj = data[j]))));
				data[j] = (data[i] - (tempi = (wr * dj) + (wi * dj1)));
				data[i - 1] += tempr;
				data[i] += tempi;
			}
			wr += ((dj = wr) * wpr) - (wi * wpi);
			wi += (wi * wpr) + (dj * wpi);
		}
		//---------------------------------------------
		//special case for the inner loop when m = ((mmax >>> 1) + 1):
		//wr = 0 / wi = isign
		if (isign == 1) {
			for (i = m; i <= FFT_SIZE; i += istep) {
				j = i + mmax;
				tempi = data[j - 1];
				data[j - 1] = data[i - 1] + (tempr = data[j]);
				data[j] = data[i] - tempi;
				data[i - 1] -= tempr;
				data[i] += tempi;
			}
			wr = -wpi;
			wi = 1.0f + wpr;
		} else {
			for (i = m; i <= FFT_SIZE; i += istep) {
				j = i + mmax;
				tempi = data[j - 1];
				data[j - 1] = data[i - 1] - (tempr = data[j]);
				data[j] = data[i] + tempi;
				data[i - 1] += tempr;
				data[i] -= tempi;
			}
			wr = wpi;
			wi = -1.0f - wpr;
		}
		m += 2;
		//---------------------------------------------
		for (; m < mmax; m += 2) {
			for (i = m; i <= FFT_SIZE; i += istep) {
				j = i + mmax;
				data[j - 1] = (data[i - 1] - (tempr = (wr * (dj1 = data[j - 1])) - (wi * (dj = data[j]))));
				data[j] = (data[i] - (tempi = (wr * dj) + (wi * dj1)));
				data[i - 1] += tempr;
				data[i] += tempi;
			}
			wr += ((dj = wr) * wpr) - (wi * wpi);
			wi += (wi * wpr) + (dj * wpi);
		}
		mmax = istep;
	}
}

void FFTNR::Real(float* data, int32_t isign) {
	float c2, wpi;
	if (isign == 1) {
		c2 = -0.5f;
		Complex(data, 1);
		wpi = tblW[51];
	} else {
		c2 = 0.5f;
		wpi = tblW[52];
	}
	const float wpr = tblW[49];
	float wr = tblW[50];
	float wi = wpi;
	for (int32_t i = 1; i < (FFT_SIZE >> 2); i++) {
		const int32_t i1 = (i << 1);
		const int32_t i2 = 1 + i1;
		const int32_t i3 = (FFT_SIZE - i1);
		const int32_t i4 = 1 + i3;
		float d1, d2, d3, d4;
		const float h1r = 0.5f * ((d1 = data[i1]) + (d3 = data[i3]));
		const float h1i = 0.5f * ((d2 = data[i2]) - (d4 = data[i4]));
		const float h2r = -c2 * (d2 + d4);
		const float h2i = c2 * (d1 - d3);
		data[i1] = (h1r + (d1 = (wr * h2r)) - (d2 = (wi * h2i)));
		data[i2] = (h1i + (d3 = (wr * h2i)) + (d4 = (wi * h2r)));
		data[i3] = (h1r - d1 + d2);
		data[i4] = (d3 + d4 - h1i);
		const float tempw = wr;
		wr += (tempw * wpr) - (wi * wpi);
		wi += (wi * wpr) + (tempw * wpi);
	}
	if (isign == 1) {
		const float tempr = data[0];
		data[0] = (tempr + data[1]);
		data[1] = (tempr - data[1]);
	} else {
		const float tempr = data[0];
		data[0] = (0.5f * (tempr + data[1]));
		data[1] = (0.5f * (tempr - data[1]));
		Complex(data, -1);
		const float n2rev = (float)tblW[48];
		for (int32_t i = FFT_SIZE - 1; i >= 0; i--)
			data[i] *= n2rev;
	}
}
