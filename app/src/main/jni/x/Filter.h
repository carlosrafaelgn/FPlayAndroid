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

void computeFilter(uint32_t band, const int32_t* equalizerActuallyUsedGainInMillibels, float* equalizerLastBandGain, EqualizerCoefs* equalizerCoefs) {
	if (!band || band >= equalizerMaxBandCount) {
		//nothing to be done in this band...
		//(band 0 is the pre amp, which is accounted for in the last band)
		return;
	}

	if (band == (equalizerMaxBandCount - 1)) {
		//the last band is not a filter, it's just a simple gain
		if (!equalizerActuallyUsedGainInMillibels[band]) {
			equalizerLastBandGain[0] = 1.0f;
			equalizerLastBandGain[1] = 1.0f;
		} else {
			equalizerLastBandGain[0] = (float)pow(10.0, (double)equalizerActuallyUsedGainInMillibels[band] / 2000.0);
			equalizerLastBandGain[1] = equalizerLastBandGain[0];
		}
		return;
	}

	//the idea for this equalizer is simple/trick ;)
	//
	//band Max-1 is an ordinary gain, corresponding to its gain + pre amp
	//band Max-2 is a lowshelf filter, applying a gain corresponding to this delta: Band Max-2's gain - Band Max-1's gain
	//...
	//band 1 is a lowshelf filter, applying a gain corresponding to this delta: Band 1's gain - Band 2's gain
	//band 0 is the pre amp (accounted for in band Max-1)

	EqualizerCoefs* const equalizerCoef = &(equalizerCoefs[band - 1]);

	if (!equalizerActuallyUsedGainInMillibels[band]) {
		//this band is an easy one! ;)
		equalizerCoef->b0L = 1.0f;
		equalizerCoef->b0R = 1.0f;
		equalizerCoef->b1L = 0.0f;
		equalizerCoef->b1R = 0.0f;
		equalizerCoef->_a1L = 0.0f;
		equalizerCoef->_a1R = 0.0f;
		equalizerCoef->b2L = 0.0f;
		equalizerCoef->b2R = 0.0f;
		equalizerCoef->_a2L = 0.0f;
		equalizerCoef->_a2R = 0.0f;
		return;
	}

	//the method used to compute b0, b1, b2, a1 and a2 was created
	//by Robert Bristow-Johnson (extracted from his Audio-EQ-Cookbook.txt)
	//
	//Cookbook formulae for audio EQ biquad filter coefficients
	//by Robert Bristow-Johnson  <rbj@audioimagination.com>
	//
	//Links:
	//https://webaudio.github.io/Audio-EQ-Cookbook/audio-eq-cookbook.html
	//https://webaudio.github.io/Audio-EQ-Cookbook/Audio-EQ-Cookbook.txt
	//http://www.earlevel.com/main/2010/12/20/biquad-calculator/
	//
	//These are the original/old links, but they all appear to be gone now!
	//http://www.musicdsp.org/archive.php?classid=3#197
	//http://www.musicdsp.org/archive.php?classid=3#198
	//http://www.musicdsp.org/files/Audio-EQ-Cookbook.txt
	//http://www.musicdsp.org/files/EQ-Coefficients.pdf
	//
	//
	//Begin with these user defined parameters:
	//
	//Fs (the sampling frequency)
	//
	//f0 ("wherever it's happenin', man."  Center Frequency or
	//	Corner Frequency, or shelf midpoint frequency, depending
	//	on which filter type.  The "significant frequency".)
	//
	//dBgain (used only for peaking and shelving filters)
	//
	//Q or BW or S (only one must be chosen)
	//Q (the EE kind of definition, except for peakingEQ in which A*Q is
	//	the classic EE Q.  That adjustment in definition was made so that
	//	a boost of N dB followed by a cut of N dB for identical Q and
	//	f0/Fs results in a precisely flat unity gain filter or "wire".)
	//
	//BW, the bandwidth in octaves (between -3 dB frequencies for BPF
	//	and notch or between midpoint (dBgain/2) gain frequencies for
	//	peaking EQ)
	//
	//S, a "shelf slope" parameter (for shelving EQ only).  When S = 1,
	//	the shelf slope is as steep as it can be and remain monotonically
	//	increasing or decreasing gain with frequency.  The shelf slope, in
	//	dB/octave, remains proportional to S for all other values for a
	//	fixed f0/Fs and dBgain.
	//
	//Then compute a few intermediate variables:
	//
	//A  = sqrt( 10^(dBgain/20) )
	//   =	   10^(dBgain/40)	 (for peaking and shelving EQ filters only)
	//
	//w0 = 2*pi*f0/Fs
	//
	//cos(w0)
	//sin(w0)
	//
	//alpha = sin(w0)/(2*Q)                             (case: Q)
	//	  = sin(w0)*sinh( ln(2)/2 * BW * w0/sin(w0) )   (case: BW)
	//	  = sin(w0)/2 * sqrt( (A + 1/A)*(1/S - 1) + 2 ) (case: S)

	const double Fs = (double)dstSampleRate;
	double f0;
	switch (band) {
	case 1: //31.25 Hz / 62.5 Hz
		f0 = 92.75;
		break;
	case 2: //125 Hz
		f0 = 187.5;
		break;
	case 3: //250 Hz
		f0 = 375.0;
		break;
	case 4: //500 Hz / 1000 Hz
		f0 = 1500.0;
		break;
	case 5: //2000 Hz / 4000 Hz
		f0 = 6000.0;
		break;
	default: //8000 Hz
		f0 = 12000.0;
		break;
	}
	const double PI = 3.1415926535897932384626433832795;
	const double S = 2.0;
	const double A = pow(10.0, (double)equalizerActuallyUsedGainInMillibels[band] / 4000.0);
	const double w0 = 2.0 * PI * f0 / Fs;
	const double cosw0 = cos(w0);
	const double sinw0 = sin(w0);
	//alpha = sin(w0)/2 * sqrt( (A + 1/A)*(1/S - 1) + 2 )
	//S used to be assumed as 1, resulting in
	//alpha = sin(w0)/2 * sqrt( (A + 1/A)*(1/1 - 1) + 2 )
	//alpha = sin(w0)/2 * sqrt(2)
	//alpha = sin(w0) * 0.70710678118654752440084436210485
	//but that yielded a very subtle slope... therefore, we are now
	//using S = 2, making the slope more aggressive
	const double alpha = sinw0 * 0.5 * sqrt((A + (1.0 / A)) * ((1.0 / S) - 1.0) + 2.0);

	const double two_sqrtA_alpha = 2.0 * sqrt(A) * alpha;

	const double b0 =     A*( (A+1.0) - ((A-1.0)*cosw0) + two_sqrtA_alpha );
	const double b1 = 2.0*A*( (A-1.0) - ((A+1.0)*cosw0)                   );
	const double b2 =     A*( (A+1.0) - ((A-1.0)*cosw0) - two_sqrtA_alpha );
	const double a0 =         (A+1.0) + ((A-1.0)*cosw0) + two_sqrtA_alpha;
	const double a1 =  -2.0*( (A-1.0) + ((A+1.0)*cosw0)                   );
	const double a2 =         (A+1.0) + ((A-1.0)*cosw0) - two_sqrtA_alpha;

	equalizerCoef->b0L = b0 / a0;
	equalizerCoef->b0R = equalizerCoef->b0L;
	equalizerCoef->b1L = b1 / a0;
	equalizerCoef->b1R = equalizerCoef->b1L;
	equalizerCoef->_a1L = -a1 / a0; //we must invert a1 and a2's signal in order to use only additions!
	equalizerCoef->_a1R = equalizerCoef->_a1L;
	equalizerCoef->b2L = b2 / a0;
	equalizerCoef->b2R = equalizerCoef->b2L;
	equalizerCoef->_a2L = -a2 / a0;
	equalizerCoef->_a2R = equalizerCoef->_a2L;
}
