/*
Fast Fourier/Cosine/Sine Transform
	dimension   :one
	data length :power of 2
	decimation  :frequency
	radix       :4, 2
	data        :inplace
	table       :use
functions
	rdft: Real Discrete Fourier Transform
function prototypes
	void rdft(int, int, float *, int *, float *);

-------- Real DFT / Inverse of Real DFT --------
	[definition]
		<case1> RDFT
			R[k] = sum_j=0^n-1 a[j]*cos(2*pi*j*k/n), 0<=k<=n/2
			I[k] = sum_j=0^n-1 a[j]*sin(2*pi*j*k/n), 0<k<n/2
		<case2> IRDFT (excluding scale)
			a[k] = (R[0] + R[n/2]*cos(pi*k))/2 +
				   sum_j=1^n/2-1 R[j]*cos(2*pi*j*k/n) +
				   sum_j=1^n/2-1 I[j]*sin(2*pi*j*k/n), 0<=k<n
	[usage]
		<case1>
			ip[0] = 0; // first time only
			rdft(n, 1, a, ip, w);
		<case2>
			ip[0] = 0; // first time only
			rdft(n, -1, a, ip, w);
	[parameters]
		n              :data length (int)
						n >= 2, n = power of 2
		a[0...n-1]     :input/output data (float *)
						<case1>
							output data
								a[2*k] = R[k], 0<=k<n/2
								a[2*k+1] = I[k], 0<k<n/2
								a[1] = R[n/2]
						<case2>
							input data
								a[2*j] = R[j], 0<=j<n/2
								a[2*j+1] = I[j], 0<j<n/2
								a[1] = R[n/2]
		ip[0...*]      :work area for bit reversal (int *)
						length of ip >= 2+sqrt(n/2)
						strictly,
						length of ip >=
							2+(1<<(int)(log(n/2+0.5)/log(2))/2).
						ip[0],ip[1] are pointers of the cos/sin table.
		w[0...n/2-1]   :cos/sin table (float *)
						w[],ip[] are initialized if ip[0] == 0.
	[remark]
		Inverse of
			rdft(n, 1, a, ip, w);
		is
			rdft(n, -1, a, ip, w);
			for (j = 0; j <= n - 1; j++) {
				a[j] *= 2.0 / n;
			}
		.

Appendix :
	The cos/sin table is recalculated when the larger table required.
	w[] and ip[] are compatible with all routines.

Reference:
	* Masatake MORI, Makoto NATORI, Tatuo TORII: Suchikeisan,
	  Iwanamikouzajyouhoukagaku18, Iwanami, 1982 (Japanese)
	* Henri J. Nussbaumer: Fast Fourier Transform and Convolution
	  Algorithms, Springer Verlag, 1982
	* C. S. Burrus, Notes on the FFT (with large FFT paper list)
	  http://www-dsp.rice.edu/research/fft/fftnote.asc

Copyright:
	Copyright(C) 1996-2001 Takuya OOURA
	email: ooura@mmm.t.u-tokyo.ac.jp
	download: http://momonga.t.u-tokyo.ac.jp/~ooura/fft.html
	You may use, copy, modify this code for any purpose and
	without fee. You may distribute this ORIGINAL package.

History:
	...
	Dec. 1995  : Edit the General Purpose FFT
	Mar. 1996  : Change the specification
	Jun. 1996  : Change the method of trigonometric function table
	Sep. 1996  : Modify the documents
	Feb. 1997  : Change the butterfly loops
	Dec. 1997  : Modify the documents
	Dec. 1997  : Add "fft4g.*"
	Jul. 1998  : Fix some bugs in the documents
	Jul. 1998  : Add "fft8g.*" and delete "fft4f.*"
	Jul. 1998  : Add a benchmark program "pi_fft.c"
	Jul. 1999  : Add a simple version "fft*g_h.c"
	Jul. 1999  : Add a Split-Radix FFT package "fftsg*.c"
	Sep. 1999  : Reduce the memory operation (minor optimization)
	Oct. 1999  : Change the butterfly structure of "fftsg*.c"
	Oct. 1999  : Save the code size
	Sep. 2001  : Add "fftsg.f"
	Sep. 2001  : Add Pthread & Win32thread routines to "fftsg*.c"
	Dec. 2006  : Fix a minor bug in "fftsg.f"

*** I only changed the data type, removed unused code and reordered the functions.
*** All the rest is just like the original file:
*** http://www.kurims.kyoto-u.ac.jp/~ooura/fft.html
*** https://www.jjj.de/fft/fftpage.html

*/

void bitrv2(int32_t n, int32_t *ip, float *a) {
	int32_t j, j1, k, k1, l, m, m2;
	float xr, xi, yr, yi;

	ip[0] = 0;
	l = n;
	m = 1;
	while ((m << 3) < l) {
		l >>= 1;
		for (j = 0; j < m; j++) {
			ip[m + j] = ip[j] + l;
		}
		m <<= 1;
	}
	m2 = 2 * m;
	if ((m << 3) == l) {
		for (k = 0; k < m; k++) {
			for (j = 0; j < k; j++) {
				j1 = 2 * j + ip[k];
				k1 = 2 * k + ip[j];
				xr = a[j1];
				xi = a[j1 + 1];
				yr = a[k1];
				yi = a[k1 + 1];
				a[j1] = yr;
				a[j1 + 1] = yi;
				a[k1] = xr;
				a[k1 + 1] = xi;
				j1 += m2;
				k1 += 2 * m2;
				xr = a[j1];
				xi = a[j1 + 1];
				yr = a[k1];
				yi = a[k1 + 1];
				a[j1] = yr;
				a[j1 + 1] = yi;
				a[k1] = xr;
				a[k1 + 1] = xi;
				j1 += m2;
				k1 -= m2;
				xr = a[j1];
				xi = a[j1 + 1];
				yr = a[k1];
				yi = a[k1 + 1];
				a[j1] = yr;
				a[j1 + 1] = yi;
				a[k1] = xr;
				a[k1 + 1] = xi;
				j1 += m2;
				k1 += 2 * m2;
				xr = a[j1];
				xi = a[j1 + 1];
				yr = a[k1];
				yi = a[k1 + 1];
				a[j1] = yr;
				a[j1 + 1] = yi;
				a[k1] = xr;
				a[k1 + 1] = xi;
			}
			j1 = 2 * k + m2 + ip[k];
			k1 = j1 + m2;
			xr = a[j1];
			xi = a[j1 + 1];
			yr = a[k1];
			yi = a[k1 + 1];
			a[j1] = yr;
			a[j1 + 1] = yi;
			a[k1] = xr;
			a[k1 + 1] = xi;
		}
	} else {
		for (k = 1; k < m; k++) {
			for (j = 0; j < k; j++) {
				j1 = 2 * j + ip[k];
				k1 = 2 * k + ip[j];
				xr = a[j1];
				xi = a[j1 + 1];
				yr = a[k1];
				yi = a[k1 + 1];
				a[j1] = yr;
				a[j1 + 1] = yi;
				a[k1] = xr;
				a[k1 + 1] = xi;
				j1 += m2;
				k1 += m2;
				xr = a[j1];
				xi = a[j1 + 1];
				yr = a[k1];
				yi = a[k1 + 1];
				a[j1] = yr;
				a[j1 + 1] = yi;
				a[k1] = xr;
				a[k1 + 1] = xi;
			}
		}
	}
}

void makewt(int32_t nw, int32_t *ip, float *w) {
	int32_t j, nwh;
	float delta, x, y;

	ip[0] = nw;
	ip[1] = 1;
	if (nw > 2) {
		nwh = nw >> 1;
		delta = atanf(1.0f) / nwh;
		w[0] = 1;
		w[1] = 0;
		w[nwh] = cosf(delta * nwh);
		w[nwh + 1] = w[nwh];
		if (nwh > 2) {
			for (j = 2; j < nwh; j += 2) {
				x = cosf(delta * j);
				y = sinf(delta * j);
				w[j] = x;
				w[j + 1] = y;
				w[nw - j] = y;
				w[nw - j + 1] = x;
			}
			bitrv2(nw, ip + 2, w);
		}
	}
}

void makect(int32_t nc, int32_t *ip, float *c) {
	int32_t j, nch;
	float delta;

	ip[1] = nc;
	if (nc > 1) {
		nch = nc >> 1;
		delta = atanf(1.0f) / nch;
		c[0] = cosf(delta * nch);
		c[nch] = 0.5f * c[0];
		for (j = 1; j < nch; j++) {
			c[j] = 0.5f * cosf(delta * j);
			c[nc - j] = 0.5f * sinf(delta * j);
		}
	}
}

void cftmdl(int32_t n, int32_t l, float *a, float *w) {
	int32_t j, j1, j2, j3, k, k1, k2, m, m2;
	float wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
	float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

	m = l << 2;
	for (j = 0; j < l; j += 2) {
		j1 = j + l;
		j2 = j1 + l;
		j3 = j2 + l;
		x0r = a[j] + a[j1];
		x0i = a[j + 1] + a[j1 + 1];
		x1r = a[j] - a[j1];
		x1i = a[j + 1] - a[j1 + 1];
		x2r = a[j2] + a[j3];
		x2i = a[j2 + 1] + a[j3 + 1];
		x3r = a[j2] - a[j3];
		x3i = a[j2 + 1] - a[j3 + 1];
		a[j] = x0r + x2r;
		a[j + 1] = x0i + x2i;
		a[j2] = x0r - x2r;
		a[j2 + 1] = x0i - x2i;
		a[j1] = x1r - x3i;
		a[j1 + 1] = x1i + x3r;
		a[j3] = x1r + x3i;
		a[j3 + 1] = x1i - x3r;
	}
	wk1r = w[2];
	for (j = m; j < l + m; j += 2) {
		j1 = j + l;
		j2 = j1 + l;
		j3 = j2 + l;
		x0r = a[j] + a[j1];
		x0i = a[j + 1] + a[j1 + 1];
		x1r = a[j] - a[j1];
		x1i = a[j + 1] - a[j1 + 1];
		x2r = a[j2] + a[j3];
		x2i = a[j2 + 1] + a[j3 + 1];
		x3r = a[j2] - a[j3];
		x3i = a[j2 + 1] - a[j3 + 1];
		a[j] = x0r + x2r;
		a[j + 1] = x0i + x2i;
		a[j2] = x2i - x0i;
		a[j2 + 1] = x0r - x2r;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[j1] = wk1r * (x0r - x0i);
		a[j1 + 1] = wk1r * (x0r + x0i);
		x0r = x3i + x1r;
		x0i = x3r - x1i;
		a[j3] = wk1r * (x0i - x0r);
		a[j3 + 1] = wk1r * (x0i + x0r);
	}
	k1 = 0;
	m2 = 2 * m;
	for (k = m2; k < n; k += m2) {
		k1 += 2;
		k2 = 2 * k1;
		wk2r = w[k1];
		wk2i = w[k1 + 1];
		wk1r = w[k2];
		wk1i = w[k2 + 1];
		wk3r = wk1r - 2 * wk2i * wk1i;
		wk3i = 2 * wk2i * wk1r - wk1i;
		for (j = k; j < l + k; j += 2) {
			j1 = j + l;
			j2 = j1 + l;
			j3 = j2 + l;
			x0r = a[j] + a[j1];
			x0i = a[j + 1] + a[j1 + 1];
			x1r = a[j] - a[j1];
			x1i = a[j + 1] - a[j1 + 1];
			x2r = a[j2] + a[j3];
			x2i = a[j2 + 1] + a[j3 + 1];
			x3r = a[j2] - a[j3];
			x3i = a[j2 + 1] - a[j3 + 1];
			a[j] = x0r + x2r;
			a[j + 1] = x0i + x2i;
			x0r -= x2r;
			x0i -= x2i;
			a[j2] = wk2r * x0r - wk2i * x0i;
			a[j2 + 1] = wk2r * x0i + wk2i * x0r;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[j1] = wk1r * x0r - wk1i * x0i;
			a[j1 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[j3] = wk3r * x0r - wk3i * x0i;
			a[j3 + 1] = wk3r * x0i + wk3i * x0r;
		}
		wk1r = w[k2 + 2];
		wk1i = w[k2 + 3];
		wk3r = wk1r - 2 * wk2r * wk1i;
		wk3i = 2 * wk2r * wk1r - wk1i;
		for (j = k + m; j < l + (k + m); j += 2) {
			j1 = j + l;
			j2 = j1 + l;
			j3 = j2 + l;
			x0r = a[j] + a[j1];
			x0i = a[j + 1] + a[j1 + 1];
			x1r = a[j] - a[j1];
			x1i = a[j + 1] - a[j1 + 1];
			x2r = a[j2] + a[j3];
			x2i = a[j2 + 1] + a[j3 + 1];
			x3r = a[j2] - a[j3];
			x3i = a[j2 + 1] - a[j3 + 1];
			a[j] = x0r + x2r;
			a[j + 1] = x0i + x2i;
			x0r -= x2r;
			x0i -= x2i;
			a[j2] = -wk2i * x0r - wk2r * x0i;
			a[j2 + 1] = -wk2i * x0i + wk2r * x0r;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[j1] = wk1r * x0r - wk1i * x0i;
			a[j1 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[j3] = wk3r * x0r - wk3i * x0i;
			a[j3 + 1] = wk3r * x0i + wk3i * x0r;
		}
	}
}

void cft1st(int32_t n, float *a, float *w) {
	int32_t j, k1, k2;
	float wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
	float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

	x0r = a[0] + a[2];
	x0i = a[1] + a[3];
	x1r = a[0] - a[2];
	x1i = a[1] - a[3];
	x2r = a[4] + a[6];
	x2i = a[5] + a[7];
	x3r = a[4] - a[6];
	x3i = a[5] - a[7];
	a[0] = x0r + x2r;
	a[1] = x0i + x2i;
	a[4] = x0r - x2r;
	a[5] = x0i - x2i;
	a[2] = x1r - x3i;
	a[3] = x1i + x3r;
	a[6] = x1r + x3i;
	a[7] = x1i - x3r;
	wk1r = w[2];
	x0r = a[8] + a[10];
	x0i = a[9] + a[11];
	x1r = a[8] - a[10];
	x1i = a[9] - a[11];
	x2r = a[12] + a[14];
	x2i = a[13] + a[15];
	x3r = a[12] - a[14];
	x3i = a[13] - a[15];
	a[8] = x0r + x2r;
	a[9] = x0i + x2i;
	a[12] = x2i - x0i;
	a[13] = x0r - x2r;
	x0r = x1r - x3i;
	x0i = x1i + x3r;
	a[10] = wk1r * (x0r - x0i);
	a[11] = wk1r * (x0r + x0i);
	x0r = x3i + x1r;
	x0i = x3r - x1i;
	a[14] = wk1r * (x0i - x0r);
	a[15] = wk1r * (x0i + x0r);
	k1 = 0;
	for (j = 16; j < n; j += 16) {
		k1 += 2;
		k2 = 2 * k1;
		wk2r = w[k1];
		wk2i = w[k1 + 1];
		wk1r = w[k2];
		wk1i = w[k2 + 1];
		wk3r = wk1r - 2 * wk2i * wk1i;
		wk3i = 2 * wk2i * wk1r - wk1i;
		x0r = a[j] + a[j + 2];
		x0i = a[j + 1] + a[j + 3];
		x1r = a[j] - a[j + 2];
		x1i = a[j + 1] - a[j + 3];
		x2r = a[j + 4] + a[j + 6];
		x2i = a[j + 5] + a[j + 7];
		x3r = a[j + 4] - a[j + 6];
		x3i = a[j + 5] - a[j + 7];
		a[j] = x0r + x2r;
		a[j + 1] = x0i + x2i;
		x0r -= x2r;
		x0i -= x2i;
		a[j + 4] = wk2r * x0r - wk2i * x0i;
		a[j + 5] = wk2r * x0i + wk2i * x0r;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[j + 2] = wk1r * x0r - wk1i * x0i;
		a[j + 3] = wk1r * x0i + wk1i * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[j + 6] = wk3r * x0r - wk3i * x0i;
		a[j + 7] = wk3r * x0i + wk3i * x0r;
		wk1r = w[k2 + 2];
		wk1i = w[k2 + 3];
		wk3r = wk1r - 2 * wk2r * wk1i;
		wk3i = 2 * wk2r * wk1r - wk1i;
		x0r = a[j + 8] + a[j + 10];
		x0i = a[j + 9] + a[j + 11];
		x1r = a[j + 8] - a[j + 10];
		x1i = a[j + 9] - a[j + 11];
		x2r = a[j + 12] + a[j + 14];
		x2i = a[j + 13] + a[j + 15];
		x3r = a[j + 12] - a[j + 14];
		x3i = a[j + 13] - a[j + 15];
		a[j + 8] = x0r + x2r;
		a[j + 9] = x0i + x2i;
		x0r -= x2r;
		x0i -= x2i;
		a[j + 12] = -wk2i * x0r - wk2r * x0i;
		a[j + 13] = -wk2i * x0i + wk2r * x0r;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[j + 10] = wk1r * x0r - wk1i * x0i;
		a[j + 11] = wk1r * x0i + wk1i * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[j + 14] = wk3r * x0r - wk3i * x0i;
		a[j + 15] = wk3r * x0i + wk3i * x0r;
	}
}

void cftfsub(int32_t n, float *a, float *w) {
	int32_t j, j1, j2, j3, l;
	float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

	l = 2;
	if (n > 8) {
		cft1st(n, a, w);
		l = 8;
		while ((l << 2) < n) {
			cftmdl(n, l, a, w);
			l <<= 2;
		}
	}
	if ((l << 2) == n) {
		for (j = 0; j < l; j += 2) {
			j1 = j + l;
			j2 = j1 + l;
			j3 = j2 + l;
			x0r = a[j] + a[j1];
			x0i = a[j + 1] + a[j1 + 1];
			x1r = a[j] - a[j1];
			x1i = a[j + 1] - a[j1 + 1];
			x2r = a[j2] + a[j3];
			x2i = a[j2 + 1] + a[j3 + 1];
			x3r = a[j2] - a[j3];
			x3i = a[j2 + 1] - a[j3 + 1];
			a[j] = x0r + x2r;
			a[j + 1] = x0i + x2i;
			a[j2] = x0r - x2r;
			a[j2 + 1] = x0i - x2i;
			a[j1] = x1r - x3i;
			a[j1 + 1] = x1i + x3r;
			a[j3] = x1r + x3i;
			a[j3 + 1] = x1i - x3r;
		}
	} else {
		for (j = 0; j < l; j += 2) {
			j1 = j + l;
			x0r = a[j] - a[j1];
			x0i = a[j + 1] - a[j1 + 1];
			a[j] += a[j1];
			a[j + 1] += a[j1 + 1];
			a[j1] = x0r;
			a[j1 + 1] = x0i;
		}
	}
}

void cftbsub(int32_t n, float *a, float *w) {
	int32_t j, j1, j2, j3, l;
	float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

	l = 2;
	if (n > 8) {
		cft1st(n, a, w);
		l = 8;
		while ((l << 2) < n) {
			cftmdl(n, l, a, w);
			l <<= 2;
		}
	}
	if ((l << 2) == n) {
		for (j = 0; j < l; j += 2) {
			j1 = j + l;
			j2 = j1 + l;
			j3 = j2 + l;
			x0r = a[j] + a[j1];
			x0i = -a[j + 1] - a[j1 + 1];
			x1r = a[j] - a[j1];
			x1i = -a[j + 1] + a[j1 + 1];
			x2r = a[j2] + a[j3];
			x2i = a[j2 + 1] + a[j3 + 1];
			x3r = a[j2] - a[j3];
			x3i = a[j2 + 1] - a[j3 + 1];
			a[j] = x0r + x2r;
			a[j + 1] = x0i - x2i;
			a[j2] = x0r - x2r;
			a[j2 + 1] = x0i + x2i;
			a[j1] = x1r - x3i;
			a[j1 + 1] = x1i - x3r;
			a[j3] = x1r + x3i;
			a[j3 + 1] = x1i + x3r;
		}
	} else {
		for (j = 0; j < l; j += 2) {
			j1 = j + l;
			x0r = a[j] - a[j1];
			x0i = -a[j + 1] + a[j1 + 1];
			a[j] += a[j1];
			a[j + 1] = -a[j + 1] - a[j1 + 1];
			a[j1] = x0r;
			a[j1 + 1] = x0i;
		}
	}
}

void rftfsub(int32_t n, float *a, int32_t nc, float *c) {
	int32_t j, k, kk, ks, m;
	float wkr, wki, xr, xi, yr, yi;

	m = n >> 1;
	ks = 2 * nc / m;
	kk = 0;
	for (j = 2; j < m; j += 2) {
		k = n - j;
		kk += ks;
		wkr = 0.5f - c[nc - kk];
		wki = c[kk];
		xr = a[j] - a[k];
		xi = a[j + 1] + a[k + 1];
		yr = wkr * xr - wki * xi;
		yi = wkr * xi + wki * xr;
		a[j] -= yr;
		a[j + 1] -= yi;
		a[k] += yr;
		a[k + 1] -= yi;
	}
}

void rftbsub(int32_t n, float *a, int32_t nc, float *c) {
	int32_t j, k, kk, ks, m;
	float wkr, wki, xr, xi, yr, yi;

	a[1] = -a[1];
	m = n >> 1;
	ks = 2 * nc / m;
	kk = 0;
	for (j = 2; j < m; j += 2) {
		k = n - j;
		kk += ks;
		wkr = 0.5f - c[nc - kk];
		wki = c[kk];
		xr = a[j] - a[k];
		xi = a[j + 1] + a[k + 1];
		yr = wkr * xr + wki * xi;
		yi = wkr * xi - wki * xr;
		a[j] -= yr;
		a[j + 1] = yi - a[j + 1];
		a[k] += yr;
		a[k + 1] = yi - a[k + 1];
	}
	a[m + 1] = -a[m + 1];
}

void rdft(int32_t n, int32_t isgn, float *a, int32_t *ip, float *w) {
	int32_t nw, nc;
	float xi;

	nw = ip[0];
	if (n > (nw << 2)) {
		nw = n >> 2;
		makewt(nw, ip, w);
	}
	nc = ip[1];
	if (n > (nc << 2)) {
		nc = n >> 2;
		makect(nc, ip, w + nw);
	}
	if (isgn >= 0) {
		if (n > 4) {
			bitrv2(n, ip + 2, a);
			cftfsub(n, a, w);
			rftfsub(n, a, nc, w + nw);
		} else if (n == 4) {
			cftfsub(n, a, w);
		}
		xi = a[0] - a[1];
		a[0] += a[1];
		a[1] = xi;
	} else {
		a[1] = 0.5f * (a[0] - a[1]);
		a[0] -= a[1];
		if (n > 4) {
			rftbsub(n, a, nc, w + nw);
			bitrv2(n, ip + 2, a);
			cftbsub(n, a, w);
		} else if (n == 4) {
			cftfsub(n, a, w);
		}
	}
}
