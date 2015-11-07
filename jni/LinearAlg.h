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

class Vector3 {
public:
	union {
		float c[3];
		struct {
			float x, y, z;
		};
	};

	void setZero() {
		x = 0.0f;
		y = 0.0f;
		z = 0.0f;
	}

	void scale(const float f) {
		x *= f;
		y *= f;
		z *= f;
	}

	float length() const {
		return sqrtf((x * x) + (y * y) + (z * z));
	}

	void normalize() {
		float f = sqrtf((x * x) + (y * y) + (z * z));
		if ((*(int*)&f)) {
			f = 1.0f / f;
			x *= f;
			y *= f;
			z *= f;
		}
	}

	static float dot(const Vector3 &a, const Vector3 &b) {
		return (a.x * b.x) + (a.y * b.y) + (a.z * b.z);
	}

	static void sub(const Vector3 &a, const Vector3 &b, Vector3 &result) {
		result.x = a.x - b.x;
		result.y = a.y - b.y;
		result.z = a.z - b.z;
	}

	static void cross(const Vector3 &a, const Vector3 &b, Vector3 &result) {
		const float x = (a.y * b.z) - (a.z * b.y);
		const float y = (a.z * b.x) - (a.x * b.z);
		const float z = (a.x * b.y) - (a.y * b.x);
		result.x = x;
		result.y = y;
		result.z = z;
	}

	int largestAbsComponent() const {
		float xAbs = x, yAbs = y, zAbs = z;
		*((int*)&xAbs) &= 0x7fffffff;
		*((int*)&yAbs) &= 0x7fffffff;
		*((int*)&zAbs) &= 0x7fffffff;
		if (xAbs > yAbs) {
			if (xAbs > zAbs)
				return 0;
			return 2;
		}
		if (yAbs > zAbs)
			return 1;
		return 2;
	}

	static void ortho(const Vector3 &v, Vector3 &result) {
		int k = v.largestAbsComponent() - 1;
		if (k < 0)
			k = 2;
		result.x = 0.0f;
		result.y = 0.0f;
		result.z = 0.0f;
		result.c[k] = 1.0f;
		cross(v, result, result);
		result.normalize();
	}
};

class Matrix3x3 {
public:
	union {
		float m[9];
		float c[3][3];
	};

	void setZero() {
		m[0] = 0.0f;
		m[1] = 0.0f;
		m[2] = 0.0f;
		m[3] = 0.0f;
		m[4] = 0.0f;
		m[5] = 0.0f;
		m[6] = 0.0f;
		m[7] = 0.0f;
		m[8] = 0.0f;
	}

	void setIdentity() {
		m[0] = 1.0f;
		m[1] = 0.0f;
		m[2] = 0.0f;
		m[3] = 0.0f;
		m[4] = 1.0f;
		m[5] = 0.0f;
		m[6] = 0.0f;
		m[7] = 0.0f;
		m[8] = 1.0f;
	}

	void setSameDiagonal(float f) {
		m[0] = f;
		m[4] = f;
		m[8] = f;
	}

	void setColumn(int col, const Vector3 &v) {
		m[col] = v.x;
		m[col + 3] = v.y;
		m[col + 6] = v.z;
	}

	void scale(float f) {
		for (int i = 0; i < 9; i++)
			m[i] *= f;
	}

	void plusEquals(const Matrix3x3 &b) {
		for (int i = 0; i < 9; i++)
			m[i] += b.m[i];
	}

	void minusEquals(const Matrix3x3 &b) {
		for (int i = 0; i < 9; i++)
			m[i] -= b.m[i];
	}

	void transpose() {
		float tmp = m[1];
		m[1] = m[3];
		m[3] = tmp;
		tmp = m[2];
		m[2] = m[6];
		m[6] = tmp;
		tmp = m[5];
		m[5] = m[7];
		m[7] = tmp;
	}

	void transpose(Matrix3x3 &result) const {
		const float m1 = m[1];
		const float m2 = m[2];
		const float m5 = m[5];
		result.m[0] = m[0];
		result.m[1] = m[3];
		result.m[2] = m[6];
		result.m[3] = m1;
		result.m[4] = m[4];
		result.m[5] = m[7];
		result.m[6] = m2;
		result.m[7] = m5;
		result.m[8] = m[8];
	}

	static void add(const Matrix3x3 &a, const Matrix3x3 &b, Matrix3x3 &result) {
		result.m[0] = a.m[0] + b.m[0];
		result.m[1] = a.m[1] + b.m[1];
		result.m[2] = a.m[2] + b.m[2];
		result.m[3] = a.m[3] + b.m[3];
		result.m[4] = a.m[4] + b.m[4];
		result.m[5] = a.m[5] + b.m[5];
		result.m[6] = a.m[6] + b.m[6];
		result.m[7] = a.m[7] + b.m[7];
		result.m[8] = a.m[8] + b.m[8];
	}

	static void mult(const Matrix3x3 &a, const Matrix3x3 &b, Matrix3x3 &result) {
		float dst[9];
		dst[0] = a.m[0] * b.m[0] + a.m[1] * b.m[3] + a.m[2] * b.m[6];
		dst[1] = a.m[0] * b.m[1] + a.m[1] * b.m[4] + a.m[2] * b.m[7];
		dst[2] = a.m[0] * b.m[2] + a.m[1] * b.m[5] + a.m[2] * b.m[8];
		dst[3] = a.m[3] * b.m[0] + a.m[4] * b.m[3] + a.m[5] * b.m[6];
		dst[4] = a.m[3] * b.m[1] + a.m[4] * b.m[4] + a.m[5] * b.m[7];
		dst[5] = a.m[3] * b.m[2] + a.m[4] * b.m[5] + a.m[5] * b.m[8];
		dst[6] = a.m[6] * b.m[0] + a.m[7] * b.m[3] + a.m[8] * b.m[6];
		dst[7] = a.m[6] * b.m[1] + a.m[7] * b.m[4] + a.m[8] * b.m[7];
		dst[8] = a.m[6] * b.m[2] + a.m[7] * b.m[5] + a.m[8] * b.m[8];
		memcpy(result.m, dst, 9 * sizeof(float));
	}

	static void mult(const Matrix3x3 &a, const Vector3 &v, Vector3 &result) {
		const float x = a.m[0] * v.x + a.m[1] * v.y + a.m[2] * v.z;
		const float y = a.m[3] * v.x + a.m[4] * v.y + a.m[5] * v.z;
		const float z = a.m[6] * v.x + a.m[7] * v.y + a.m[8] * v.z;
		result.x = x;
		result.y = y;
		result.z = z;
	}

	float determinant() const {
		return (c[0][0] * (c[1][1] * c[2][2] - c[2][1] * c[1][2]))
		- (c[0][1] * (c[1][0] * c[2][2] - c[1][2] * c[2][0]))
		+ (c[0][2] * (c[1][0] * c[2][1] - c[1][1] * c[2][0]));
	}

	bool invert(Matrix3x3 &result) const {
		const float d = determinant();
		if (!(*(int*)&d))
			return false;
		const float invdet = 1.0f / d;

		float dst[9];
		dst[0] = ((m[4] * m[8]) - (m[7] * m[5])) * invdet;
		dst[1] = -((m[1] * m[8]) - (m[2] * m[7])) * invdet;
		dst[2] = ((m[1] * m[5]) - (m[2] * m[4])) * invdet;
		dst[3] = -((m[3] * m[8]) - (m[5] * m[6])) * invdet;
		dst[4] = ((m[0] * m[8]) - (m[2] * m[6])) * invdet;
		dst[5] = -((m[0] * m[5]) - (m[3] * m[2])) * invdet;
		dst[6] = ((m[3] * m[7]) - (m[6] * m[4])) * invdet;
		dst[7] = -((m[0] * m[7]) - (m[6] * m[1])) * invdet;
		dst[8] = ((m[0] * m[4]) - (m[3] * m[1])) * invdet;
		memcpy(result.m, dst, 9 * sizeof(float));

		return true;
	}
};

class So3Util {
public:
	static void sO3FromTwoVec(const Vector3 &a, const Vector3 &b, Matrix3x3 &result) {
		Vector3 sO3FromTwoVecA, sO3FromTwoVecB, sO3FromTwoVecN, temp31;
		Vector3::cross(a, b, sO3FromTwoVecN);
		if (sO3FromTwoVecN.length() == 0.0f) {
			const float dot = Vector3::dot(a, b);
			if (dot >= 0.0f) {
				result.setIdentity();
			} else {
				Vector3::ortho(a, sO3FromTwoVecN);
				rotationPiAboutAxis(sO3FromTwoVecN, result);
			}
			return;
		}
		sO3FromTwoVecA = a;
		sO3FromTwoVecB = b;
		sO3FromTwoVecN.normalize();
		sO3FromTwoVecA.normalize();
		sO3FromTwoVecB.normalize();
		Matrix3x3 r1;
		r1.setColumn(0, sO3FromTwoVecA);
		r1.setColumn(1, sO3FromTwoVecN);
		Vector3::cross(sO3FromTwoVecN, sO3FromTwoVecA, temp31);
		r1.setColumn(2, temp31);
		Matrix3x3 r2;
		r2.setColumn(0, sO3FromTwoVecB);
		r2.setColumn(1, sO3FromTwoVecN);
		Vector3::cross(sO3FromTwoVecN, sO3FromTwoVecB, temp31);
		r2.setColumn(2, temp31);
		r1.transpose();
		Matrix3x3::mult(r2, r1, result);
	}

	static void rotationPiAboutAxis(const Vector3 &v, Matrix3x3 &result) {
		Vector3 rotationPiAboutAxisTemp = v;
		rotationPiAboutAxisTemp.scale(3.141592653589793f / rotationPiAboutAxisTemp.length());
		rodriguesSo3Exp(rotationPiAboutAxisTemp, 0.0f, 0.20264236728467558f, result);
	}

	static void sO3FromMu(const Vector3 &w, Matrix3x3 &result) {
		const float thetaSq = Vector3::dot(w, w);
		const float theta = sqrtf(thetaSq);
		double kA;
		double kB;
		if (thetaSq < 1.0E-8f) {
			kA = 1.0f - (0.1666666716337204f * thetaSq);
			kB = 0.5f;
		} else if (thetaSq < 1.0E-6f) {
			kB = 0.5f - (0.0416666679084301f * thetaSq);
			kA = 1.0f - (thetaSq * 0.1666666716337204f * (1.0f - 0.1666666716337204f * thetaSq));
		} else {
			const float invTheta = 1.0f / theta;
			kA = sinf(theta) * invTheta;
			kB = (1.0f - cosf(theta)) * (invTheta * invTheta);
		}
		rodriguesSo3Exp(w, kA, kB, result);
	}

	static void muFromSO3(const Matrix3x3 &so3, Vector3 &result) {
		const float cosAngle = (so3.c[0][0] + so3.c[1][1] + so3.c[2][2] - 1.0f) * 0.5f;

		result.x = (so3.c[2][1] - so3.c[1][2]) * 0.5f;
		result.y = (so3.c[0][2] - so3.c[2][0]) * 0.5f;
		result.z = (so3.c[1][0] - so3.c[0][1]) * 0.5f;

		const float sinAngleAbs = result.length();

		if (cosAngle > 0.7071067811865476f) {
			if (sinAngleAbs > 0.0f)
				result.scale(asinf(sinAngleAbs) / sinAngleAbs);
		} else if (cosAngle > -0.7071067811865476f) {
			result.scale(acosf(cosAngle) / sinAngleAbs);
		} else {
			const float angle = 3.141592653589793f - asinf(sinAngleAbs);
			const float d0 = so3.c[0][0] - cosAngle;
			const float d = so3.c[1][1] - cosAngle;
			const float d2 = so3.c[2][2] - cosAngle;
			Vector3 r2;

			if ((d0 * d0) > (d * d) && (d0 * d0) > (d2 * d2)) {
				r2.x = d0;
				r2.y = (so3.c[1][0] + so3.c[0][1]) * 0.5f;
				r2.z = (so3.c[0][2] + so3.c[2][0]) * 0.5f;
			} else if ((d * d) > (d2 * d2)) {
				r2.x = (so3.c[1][0] + so3.c[0][1]) * 0.5f;
				r2.y = d;
				r2.z = (so3.c[2][1] + so3.c[1][2]) * 0.5f;
			} else {
				r2.x = (so3.c[0][2] + so3.c[2][0]) * 0.5f;
				r2.y = (so3.c[2][1] + so3.c[1][2]) * 0.5f;
				r2.z = d2;
			}

			if (Vector3::dot(r2, result) < 0.0f)
				r2.scale(-1.0f);
			r2.normalize();
			r2.scale(angle);

			result = r2;
		}
	}

private:
	static void rodriguesSo3Exp(const Vector3 &w, float kA, float kB, Matrix3x3 &result) {
		const float wx2 = w.x * w.x;
		const float wy2 = w.y * w.y;
		const float wz2 = w.z * w.z;
		result.c[0][0] = 1.0f - (kB * (wy2 + wz2));
		result.c[1][1] = 1.0f - (kB * (wx2 + wz2));
		result.c[2][2] = 1.0f - (kB * (wx2 + wy2));
		float a = kA * w.z;
		float b = kB * (w.x * w.y);
		result.c[0][1] = b - a;
		result.c[1][0] = b + a;
		a = kA * w.y;
		b = kB * (w.x * w.z);
		result.c[0][2] = b + a;
		result.c[2][0] = b - a;
		a = kA * w.x;
		b = kB * (w.y * w.z);
		result.c[1][2] = b - a;
		result.c[2][1] = b + a;
	}
};

class OrientationEKF {
public:
	float rotationMatrix[16];
private:
	Matrix3x3 so3SensorFromWorld, so3LastMotion, mP, mQ, mRaccel, mS, mH, mK;
	Vector3 mNu, mz, mh, mu, mx, down;
	uint64_t sensorTimeStampGyro;
	Vector3 lastGyro;
	float previousAccelNorm, movingAverageAccelNormChange, filteredGyroTimestep;
	int numGyroTimestepSamples;
	bool timestepFilterInit, gyroFilterValid, alignedToGravity;

public:
	OrientationEKF() {
		memset(this, 0, sizeof(OrientationEKF));
		gyroFilterValid = true;
		reset();
	}

	void reset() {
		sensorTimeStampGyro = 0;
		so3SensorFromWorld.setIdentity();
		so3LastMotion.setIdentity();
		mP.setZero();
		mP.setSameDiagonal(25.0f);
		mQ.setZero();
		mQ.setSameDiagonal(1.0f);
		mRaccel.setZero();
		mRaccel.setSameDiagonal(0.5625f);
		mS.setZero();
		mH.setZero();
		mK.setZero();
		mNu.setZero();
		mz.setZero();
		mh.setZero();
		mu.setZero();
		mx.setZero();
		down.x = 0.0f;
		down.y = 0.0f;
		down.z = 9.81f;
		alignedToGravity = false;
	}

	void getPredictedGLMatrix(float secondsAfterLastGyroEvent) {
		Vector3 pmu = lastGyro;
		pmu.scale(-secondsAfterLastGyroEvent);

		Matrix3x3 so3PredictedMotion;
		So3Util::sO3FromMu(pmu, so3PredictedMotion);
		Matrix3x3::mult(so3PredictedMotion, so3SensorFromWorld, so3PredictedMotion);

		//glMatrixFromSo3(so3PredictedMotion);
		for (int r = 0; r < 3; ++r) {
			for (int c = 0; c < 3; ++c)
				rotationMatrix[(c << 2) + r] = so3PredictedMotion.c[r][c];
		}
		/*rotationMatrix[3] = 0.0;
		rotationMatrix[7] = 0.0;
		rotationMatrix[11] = 0.0;

		rotationMatrix[12] = 0.0;
		rotationMatrix[13] = 0.0;
		rotationMatrix[14] = 0.0;
		rotationMatrix[15] = 1.0;*/
	}

	void processGyro(const Vector3 &gyro, uint64_t sensorTimeStamp) {
		const float kTimeThreshold = 0.04f;
		const float kdTDefault = 0.01f;
		if (sensorTimeStampGyro) {
			float dT = (sensorTimeStamp - sensorTimeStampGyro) * 1.0E-9f;
			if (dT > kTimeThreshold) {
				dT = (gyroFilterValid ? filteredGyroTimestep : kdTDefault );
			} else {
				const float kFilterCoeff = 0.95f;
				const int kMinSamples = 10;
				if (!timestepFilterInit) {
					filteredGyroTimestep = dT;
					numGyroTimestepSamples = 1;
					timestepFilterInit = true;
				} else {
					filteredGyroTimestep = kFilterCoeff * filteredGyroTimestep + (1.0f - kFilterCoeff) * dT;
					if (++numGyroTimestepSamples > kMinSamples)
						gyroFilterValid = true;
				}
			}
			mu = gyro;
			mu.scale(-dT);
			So3Util::sO3FromMu(mu, so3LastMotion);
			Matrix3x3::mult(so3LastMotion, so3SensorFromWorld, so3SensorFromWorld);
			updateCovariancesAfterMotion();
			Matrix3x3 processGyroTempM2 = mQ;
			processGyroTempM2.scale(dT * dT);
			mP.plusEquals(processGyroTempM2);
		}
		sensorTimeStampGyro = sensorTimeStamp;
		lastGyro = gyro;
	}

	void processAcc(const Vector3 &acc) {
		mz = acc;

		const float currentAccelNorm = mz.length();
		float currentAccelNormChange = abs(currentAccelNorm - previousAccelNorm);
		*((int*)&currentAccelNormChange) &= 0x7fffffff; //abs ;)
		previousAccelNorm = currentAccelNorm;
		const float kSmoothingFactor = 0.5f;
		movingAverageAccelNormChange = kSmoothingFactor * (currentAccelNormChange + movingAverageAccelNormChange);
		const float kMaxAccelNormChange = 0.15f;
		const float kMinAccelNoiseSigma = 0.75f;
		const float kMaxAccelNoiseSigma = 7.0f;
		const float normChangeRatio = movingAverageAccelNormChange / kMaxAccelNormChange;
		float accelNoiseSigma = kMinAccelNoiseSigma + normChangeRatio * (kMaxAccelNoiseSigma - kMinAccelNoiseSigma);
		if (accelNoiseSigma > kMaxAccelNoiseSigma)
			accelNoiseSigma = kMaxAccelNoiseSigma;
		mRaccel.setSameDiagonal(accelNoiseSigma * accelNoiseSigma);

		if (alignedToGravity) {
			accObservationFunctionForNumericalJacobian(so3SensorFromWorld, mNu);
			const float eps = 1.0E-7;
			for (int dof = 0; dof < 3; ++dof) {
				Vector3 delta;
				delta.setZero();
				delta.c[dof] = eps;
				Matrix3x3 processAccTempM1, processAccTempM2;
				So3Util::sO3FromMu(delta, processAccTempM1);
				Matrix3x3::mult(processAccTempM1, so3SensorFromWorld, processAccTempM2);
				Vector3 processAccTempV1, processAccTempV2;
				accObservationFunctionForNumericalJacobian(processAccTempM2, processAccTempV1);
				Vector3::sub(mNu, processAccTempV1, processAccTempV2);
				processAccTempV2.scale(1.0 / eps);
				mH.setColumn(dof, processAccTempV2);
			}
			Matrix3x3 processAccTempM3, processAccTempM4, processAccTempM5;
			mH.transpose(processAccTempM3);
			Matrix3x3::mult(mP, processAccTempM3, processAccTempM4);
			Matrix3x3::mult(mH, processAccTempM4, processAccTempM5);
			Matrix3x3::add(processAccTempM5, mRaccel, mS);
			mS.invert(processAccTempM3);
			mH.transpose(processAccTempM4);
			Matrix3x3::mult(processAccTempM4, processAccTempM3, processAccTempM5);
			Matrix3x3::mult(mP, processAccTempM5, mK);
			Matrix3x3::mult(mK, mNu, mx);
			Matrix3x3::mult(mK, mH, processAccTempM3);
			processAccTempM4.setIdentity();
			processAccTempM4.minusEquals(processAccTempM3);
			Matrix3x3::mult(processAccTempM4, mP, processAccTempM3);
			mP = processAccTempM3;
			So3Util::sO3FromMu(mx, so3LastMotion);
			Matrix3x3::mult(so3LastMotion, so3SensorFromWorld, so3SensorFromWorld);
			updateCovariancesAfterMotion();
		} else {
			So3Util::sO3FromTwoVec(down, mz, so3SensorFromWorld);
			alignedToGravity = true;
		}
	}

private:
	void updateCovariancesAfterMotion() {
		Matrix3x3 updateCovariancesAfterMotionTempM1;
		so3LastMotion.transpose(updateCovariancesAfterMotionTempM1);
		Matrix3x3::mult(mP, updateCovariancesAfterMotionTempM1, updateCovariancesAfterMotionTempM1);
		Matrix3x3::mult(so3LastMotion, updateCovariancesAfterMotionTempM1, mP);
		so3LastMotion.setIdentity();
	}

	void accObservationFunctionForNumericalJacobian(const Matrix3x3 &so3SensorFromWorldPred, Vector3 &result) {
		Matrix3x3::mult(so3SensorFromWorldPred, down, mh);
		Matrix3x3 accObservationFunctionForNumericalJacobianTempM;
		So3Util::sO3FromTwoVec(mh, mz, accObservationFunctionForNumericalJacobianTempM);
		So3Util::muFromSO3(accObservationFunctionForNumericalJacobianTempM, result);
	}
};
