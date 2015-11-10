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

//***************************************************************************
//
// My C++ port of the decompiled version of Google Cardboard
// Java version available at: https://github.com/rsanchezsaez/cardboard-java
//
//***************************************************************************

//******************************************************************
// I applied many optimizations here:
// - Since the only outcome from OrientationEKF is a pure rotation
// matrix, I decided to keep both mEkfToHeadTracker and
// mSensorToDisplay in a 3x3 matrix
//
// - I completely removed the matrices mEkfToHeadTracker and
// mSensorToDisplay. Instead of using them, I already pass the
// values to onSensorData adjusted according to the device
// rotation
//
// - I removed mLatestGyro and mLatestAcc, as they are only
// used locally
//******************************************************************
class HeadTracker {
private:
	uint64_t mLatestGyroEventClockTimeNs;
	OrientationEKF mTracker;

public:
	HeadTracker() {
		mLatestGyroEventClockTimeNs = 0;
	}

	void onSensorReset() {
		mTracker.reset();
	}

    void onSensorData(uint64_t sensorTimestamp, int sensorType, const Vector3& values) {
		if (sensorType == 1) {
			mTracker.processAcc(values);
		} else {
			mLatestGyroEventClockTimeNs = commonUptimeNs();
			mTracker.processGyro(values, sensorTimestamp);
		}
    }

	void getLastHeadView(float* headView4x4) {
		const ftype secondsSinceLastGyroEvent = (ftype)(commonUptimeNs() - mLatestGyroEventClockTimeNs) / (ftype)1000000000.0;
		const ftype secondsToPredictForward = secondsSinceLastGyroEvent + (ftype)(1.0 / 30.0);

		mTracker.computePredictedGLMatrix(secondsToPredictForward);

		for (int r = 0; r < 3; ++r) {
			for (int c = 0; c < 3; ++c)
				headView4x4[(c << 2) + r] = (float)mTracker.rotationMatrix.c[r][c];
		}
	}
};
