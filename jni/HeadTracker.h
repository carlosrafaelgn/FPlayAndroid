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

//***************************************************************
// I applied one optimization here:
// Since the only outcome from OrientationEKF is a pure rotation
// matrix, I decided to keep both mEkfToHeadTracker and
// mSensorToDisplay in a 3x3 matrix
//***************************************************************
class HeadTracker {
private:
	Matrix3x3 mEkfToHeadTracker, mSensorToDisplay;
	uint64_t mLatestGyroEventClockTimeNs;
	Vector3 mLatestGyro, mLatestAcc;
	OrientationEKF mTracker;

public:
	HeadTracker(int displayRotation) {
		mLatestGyroEventClockTimeNs = 0;
		mLatestGyro.x = 0;
		mLatestGyro.y = 0;
		mLatestGyro.z = 0;
		mLatestAcc.x = 0;
		mLatestAcc.y = 0;
		mLatestAcc.z = 0;
		displayRotationChanged(displayRotation);
	}

	void displayRotationChanged(int displayRotation) {
		//Matrix.setRotateEulerM(this.mSensorToDisplay, 0, 0.0f, 0.0f, -rotation);
        float cx = 1.0f, sx = 0.0f;
        const float cy = 1.0f, sy = 0.0f;
        float cz, sz;
		switch (displayRotation) {
		case 1: //ROTATION_90 (-90)
			cz = 0.0f;
			sz = -1.0f;
			break;
		case 2: //ROTATION_180 (-180)
			cz = -1.0f;
			sz = 0.0f;
			break;
		case 3: //ROTATION_270 (-270)
			cz = 0.0f;
			sz = 1.0f;
			break;
		default: //ROTATION_0
			cz = 1.0f;
			sz = 0.0f;
			break;
		}
		float cxsy = cx * sy;
		float sxsy = sx * sy;

		mSensorToDisplay.m[0] = cy * cz;
		mSensorToDisplay.m[1] = -cy * sz;
		mSensorToDisplay.m[2] = sy;

		mSensorToDisplay.m[3] = cxsy * cz + cx * sz;
		mSensorToDisplay.m[4] = -cxsy * sz + cx * cz;
		mSensorToDisplay.m[5] = -sx * cy;

		mSensorToDisplay.m[6] = -sxsy * cz + sx * sz;
		mSensorToDisplay.m[7] = sxsy * sz + sx * cz;
		mSensorToDisplay.m[8] = cx * cy;

		//Matrix.setRotateEulerM(this.mEkfToHeadTracker, 0, -90.0f, 0.0f,  rotation);
        cx = 0.0f;
        sx = -1.0f;
		switch (displayRotation) {
		case 1: //ROTATION_90
			cz = 0.0f;
			sz = 1.0f;
			break;
		case 2: //ROTATION_180
			cz = -1.0f;
			sz = 0.0f;
			break;
		case 3: //ROTATION_270
			cz = 0.0f;
			sz = -1.0f;
			break;
		default: //ROTATION_0
			cz = 1.0f;
			sz = 0.0f;
			break;
		}
		cxsy = cx * sy;
		sxsy = sx * sy;

		mEkfToHeadTracker.m[0] = cy * cz;
		mEkfToHeadTracker.m[1] = -cy * sz;
		mEkfToHeadTracker.m[2] = sy;

		mEkfToHeadTracker.m[3] = cxsy * cz + cx * sz;
		mEkfToHeadTracker.m[4] = -cxsy * sz + cx * cz;
		mEkfToHeadTracker.m[5] = -sx * cy;

		mEkfToHeadTracker.m[6] = -sxsy * cz + sx * sz;
		mEkfToHeadTracker.m[7] = sxsy * sz + sx * cz;
		mEkfToHeadTracker.m[8] = cx * cy;
	}

	void onSensorReset() {
		mTracker.reset();
	}

    void onSensorData(uint64_t sensorTimestamp, int sensorType, float* values) {
		if (sensorType == 1) {
			mLatestAcc.x = values[0];
			mLatestAcc.y = values[1];
			mLatestAcc.z = values[2];
			mTracker.processAcc(mLatestAcc);
		} else {
			mLatestGyroEventClockTimeNs = commonUptimeNs();
			mLatestGyro.x = values[0];
			mLatestGyro.y = values[1];
			mLatestGyro.z = values[2];
			mTracker.processGyro(mLatestGyro, sensorTimestamp);
		}
    }

	void getLastHeadView(float* headView4x4) {
		const float secondsSinceLastGyroEvent = (float)(commonUptimeNs() - mLatestGyroEventClockTimeNs) / 1000000000.0f;
		const float secondsToPredictForward = secondsSinceLastGyroEvent + (1.0f / 30.0f);

		mTracker.computePredictedGLMatrix(secondsToPredictForward);
		Matrix3x3 mTmpHeadView;
		Matrix3x3::mult(mSensorToDisplay, mTracker.rotationMatrix, mTmpHeadView);
		Matrix3x3::mult(mTmpHeadView, mEkfToHeadTracker, mTmpHeadView);

		for (int r = 0; r < 3; ++r) {
			for (int c = 0; c < 3; ++c)
				headView4x4[(c << 2) + r] = mTmpHeadView.c[r][c];
		}
	}
};
