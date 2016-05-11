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

float getBandGainInDB(unsigned int band) {
	if ((effectsEnabled & BASSBOOST_ENABLED)) {
		if (band < BASSBOOST_BAND_COUNT)
			return ((effectsEnabled & EQUALIZER_ENABLED) ?
					//bassBoostStrength 0    -> +0dB
					//bassBoostStrength 1000 -> +10dB
					(equalizerGainInDB[band] + ((float)bassBoostStrength / 100.0f)) :
						((float)bassBoostStrength / 100.0f));
		else
			return ((effectsEnabled & EQUALIZER_ENABLED) ?
					equalizerGainInDB[band] :
						0.0f);
			//return ((effectsEnabled & EQUALIZER_ENABLED) ?
			//		//bassBoostStrength 0    -> -0dB
			//		//bassBoostStrength 1000 -> -6dB
			//		(equalizerGainInDB[band] - ((float)bassBoostStrength / 167.0f)) :
			//			-((float)bassBoostStrength / 167.0f));
	}
	return equalizerGainInDB[band];
}

void computeFilter(unsigned int band) {
	//the method used to compute b0, b1, b2, a1 and a2 was created by Haruki Hasegawa,
	//for his OpenSLMediaPlayer: https://github.com/h6ah4i/android-openslmediaplayer
	//the original formula was in his spreadsheet: https://docs.google.com/spreadsheets/d/1hj2aoW83rGraANzHxKaCpECFQ0WawVbap4tgxZ9FSmo/pubhtml?gid=1587344290&single=true
	//
	//Copyright (C) 2014-2015 Haruki Hasegawa
	//
	//Licensed under the Apache License, Version 2.0 (the "License");
	//you may not use this file except in compliance with the License.
	//You may obtain a copy of the License at
	//
    //http://www.apache.org/licenses/LICENSE-2.0
	//
	//Unless required by applicable law or agreed to in writing, software
	//distributed under the License is distributed on an "AS IS" BASIS,
	//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	//See the License for the specific language governing permissions and
	//limitations under the License.
	//
	if (band >= equalizerMaxBandCount) {
		//nothing to be done in this band...
		equalizerCoefs[(band << 3)    ] = 1.0f; //b0 L
		equalizerCoefs[(band << 3) + 1] = 1.0f; //b0 R
		equalizerCoefs[(band << 3) + 2] = 0.0f; //b1 (a1) L
		equalizerCoefs[(band << 3) + 3] = 0.0f; //b1 (a1) R
		equalizerCoefs[(band << 3) + 4] = 0.0f; //b2 L
		equalizerCoefs[(band << 3) + 5] = 0.0f; //b2 R
		equalizerCoefs[(band << 3) + 6] = 0.0f; //-a2 L
		equalizerCoefs[(band << 3) + 7] = 0.0f; //-a2 R
		return;
	}

#define BW_S 1.0
#define neighborBandCorrelationCoef -0.15
#define PI 3.1415926535897932384626433832795
#define LN2_2 0.34657359027997265470861606072909
	static const double bands[BAND_COUNT] = { 31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0 };
	const double fs = (double)sampleRate;
	const double f0 = bands[band];
	const double w0 = ((2.0 * PI) * f0) / fs;
	const double sinw0 = sin(w0);
	const double cosw0 = cos(w0);
	const double Q = 1.0 / (2.0 * sinh(LN2_2 * BW_S * (w0 / sinw0)));
	const double gainCorrelationInDB = (neighborBandCorrelationCoef *
										((band == 0) ? getBandGainInDB(band + 1) :
											((band == (equalizerMaxBandCount - 1)) ? getBandGainInDB(band - 1) :
												(getBandGainInDB(band - 1) + getBandGainInDB(band + 1)))));
	const double modGainInDB = getBandGainInDB(band) + gainCorrelationInDB;
	const double alpha = sinw0 / (2.0 * Q);
	const double A = pow(10.0, modGainInDB / 40.0);
	const double alpha_mul_A = alpha * A;
	const double alpha_div_A = alpha / A;
	const double a0 = 1.0 + alpha_div_A;

	//a1 and b1 are equal!
	const float b0 = (float)((1.0 + alpha_mul_A) / a0);
	equalizerCoefs[(band << 3)    ] = b0; //L
	equalizerCoefs[(band << 3) + 1] = b0; //R
	const float b1 = (float)((-2.0 * cosw0) / a0);
	equalizerCoefs[(band << 3) + 2] = b1; //L
	equalizerCoefs[(band << 3) + 3] = b1; //R
	const float b2 = (float)((1.0 - alpha_mul_A) / a0);
	equalizerCoefs[(band << 3) + 4] = b2; //L
	equalizerCoefs[(band << 3) + 5] = b2; //R
	const float _a2 = -(float)((1.0 - alpha_div_A) / a0); //invert a2's signal to make processEqualizer()'s life easier
	equalizerCoefs[(band << 3) + 6] = _a2; //L
	equalizerCoefs[(band << 3) + 7] = _a2; //R

#undef BW_S
#undef neighborBandCorrelationCoef
#undef PI
#undef LN2_2
}
