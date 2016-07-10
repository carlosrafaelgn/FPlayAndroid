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

#define USE_DOUBLE

#ifdef USE_DOUBLE
	#define ftype double
	#define fint int64_t
	#define fintabs(A) *((int64_t*)&A) &= 0x7fffffffffffffffLL
	#define fsqrt sqrt
	#define fsin sin
	#define fcos cos
	#define fasin asin
	#define facos acos
#else
	#define ftype float
	#define fint int32_t
	#define fintabs(A) *((int32_t*)&A) &= 0x7fffffff
	#define fsqrt sqrtf
	#define fsin sinf
	#define fcos cosf
	#define fasin asinf
	#define facos acosf
#endif

class Vector3 {
public:
	union {
		ftype c[3];
		struct {
			ftype x, y, z;
		};
	};

	void setZero() {
		x = (ftype)0.0;
		y = (ftype)0.0;
		z = (ftype)0.0;
	}

	void scale(const ftype f) {
		x *= f;
		y *= f;
		z *= f;
	}

	ftype length() const {
		return fsqrt((x * x) + (y * y) + (z * z));
	}

	void normalize() {
		ftype f = fsqrt((x * x) + (y * y) + (z * z));
		if ((*(fint*)&f)) {
			f = (ftype)1.0 / f;
			x *= f;
			y *= f;
			z *= f;
		}
	}

	static ftype dot(const Vector3 &a, const Vector3 &b) {
		return (a.x * b.x) + (a.y * b.y) + (a.z * b.z);
	}

	static void sub(const Vector3 &a, const Vector3 &b, Vector3 &result) {
		result.x = a.x - b.x;
		result.y = a.y - b.y;
		result.z = a.z - b.z;
	}

	static void cross(const Vector3 &a, const Vector3 &b, Vector3 &result) {
		const ftype x = (a.y * b.z) - (a.z * b.y);
		const ftype y = (a.z * b.x) - (a.x * b.z);
		const ftype z = (a.x * b.y) - (a.y * b.x);
		result.x = x;
		result.y = y;
		result.z = z;
	}

	int32_t largestAbsComponent() const {
		ftype xAbs = x, yAbs = y, zAbs = z;
		fintabs(xAbs);
		fintabs(yAbs);
		fintabs(zAbs);
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
		int32_t k = v.largestAbsComponent() - 1;
		if (k < 0)
			k = 2;
		result.x = (ftype)0.0;
		result.y = (ftype)0.0;
		result.z = (ftype)0.0;
		result.c[k] = (ftype)1.0;
		cross(v, result, result);
		result.normalize();
	}
};

class Matrix3x3 {
public:
	union {
		ftype m[9];
		ftype c[3][3];
	};

	void setZero() {
		m[0] = (ftype)0.0;
		m[1] = (ftype)0.0;
		m[2] = (ftype)0.0;
		m[3] = (ftype)0.0;
		m[4] = (ftype)0.0;
		m[5] = (ftype)0.0;
		m[6] = (ftype)0.0;
		m[7] = (ftype)0.0;
		m[8] = (ftype)0.0;
	}

	void setIdentity() {
		m[0] = (ftype)1.0;
		m[1] = (ftype)0.0;
		m[2] = (ftype)0.0;
		m[3] = (ftype)0.0;
		m[4] = (ftype)1.0;
		m[5] = (ftype)0.0;
		m[6] = (ftype)0.0;
		m[7] = (ftype)0.0;
		m[8] = (ftype)1.0;
	}

	void setSameDiagonal(ftype f) {
		m[0] = f;
		m[4] = f;
		m[8] = f;
	}

	void setColumn(int32_t col, const Vector3 &v) {
		m[col] = v.x;
		m[col + 3] = v.y;
		m[col + 6] = v.z;
	}

	void scale(ftype f) {
		for (int32_t i = 0; i < 9; i++)
			m[i] *= f;
	}

	void plusEquals(const Matrix3x3 &b) {
		for (int32_t i = 0; i < 9; i++)
			m[i] += b.m[i];
	}

	void minusEquals(const Matrix3x3 &b) {
		for (int32_t i = 0; i < 9; i++)
			m[i] -= b.m[i];
	}

	void transpose() {
		ftype tmp = m[1];
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
		const ftype m1 = m[1];
		const ftype m2 = m[2];
		const ftype m5 = m[5];
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
		ftype dst[9];
		dst[0] = a.m[0] * b.m[0] + a.m[1] * b.m[3] + a.m[2] * b.m[6];
		dst[1] = a.m[0] * b.m[1] + a.m[1] * b.m[4] + a.m[2] * b.m[7];
		dst[2] = a.m[0] * b.m[2] + a.m[1] * b.m[5] + a.m[2] * b.m[8];
		dst[3] = a.m[3] * b.m[0] + a.m[4] * b.m[3] + a.m[5] * b.m[6];
		dst[4] = a.m[3] * b.m[1] + a.m[4] * b.m[4] + a.m[5] * b.m[7];
		dst[5] = a.m[3] * b.m[2] + a.m[4] * b.m[5] + a.m[5] * b.m[8];
		dst[6] = a.m[6] * b.m[0] + a.m[7] * b.m[3] + a.m[8] * b.m[6];
		dst[7] = a.m[6] * b.m[1] + a.m[7] * b.m[4] + a.m[8] * b.m[7];
		dst[8] = a.m[6] * b.m[2] + a.m[7] * b.m[5] + a.m[8] * b.m[8];
		memcpy(result.m, dst, 9 * sizeof(ftype));
	}

	static void mult(const Matrix3x3 &a, const Vector3 &v, Vector3 &result) {
		const ftype x = a.m[0] * v.x + a.m[1] * v.y + a.m[2] * v.z;
		const ftype y = a.m[3] * v.x + a.m[4] * v.y + a.m[5] * v.z;
		const ftype z = a.m[6] * v.x + a.m[7] * v.y + a.m[8] * v.z;
		result.x = x;
		result.y = y;
		result.z = z;
	}

	ftype determinant() const {
		return (c[0][0] * (c[1][1] * c[2][2] - c[2][1] * c[1][2]))
		- (c[0][1] * (c[1][0] * c[2][2] - c[1][2] * c[2][0]))
		+ (c[0][2] * (c[1][0] * c[2][1] - c[1][1] * c[2][0]));
	}

	bool invert(Matrix3x3 &result) const {
		const ftype d = determinant();
		if (!(*(fint*)&d))
			return false;
		const ftype invdet = (ftype)1.0 / d;

		ftype dst[9];
		dst[0] = ((m[4] * m[8]) - (m[7] * m[5])) * invdet;
		dst[1] = -((m[1] * m[8]) - (m[2] * m[7])) * invdet;
		dst[2] = ((m[1] * m[5]) - (m[2] * m[4])) * invdet;
		dst[3] = -((m[3] * m[8]) - (m[5] * m[6])) * invdet;
		dst[4] = ((m[0] * m[8]) - (m[2] * m[6])) * invdet;
		dst[5] = -((m[0] * m[5]) - (m[3] * m[2])) * invdet;
		dst[6] = ((m[3] * m[7]) - (m[6] * m[4])) * invdet;
		dst[7] = -((m[0] * m[7]) - (m[6] * m[1])) * invdet;
		dst[8] = ((m[0] * m[4]) - (m[3] * m[1])) * invdet;
		memcpy(result.m, dst, 9 * sizeof(ftype));

		return true;
	}
};

class So3Util {
public:
	static void sO3FromTwoVec(const Vector3 &a, const Vector3 &b, Matrix3x3 &result) {
		Vector3 sO3FromTwoVecA, sO3FromTwoVecB, sO3FromTwoVecN, temp31;
		Vector3::cross(a, b, sO3FromTwoVecN);
		if (sO3FromTwoVecN.length() == (ftype)0.0) {
			const ftype dot = Vector3::dot(a, b);
			if (dot >= (ftype)0.0) {
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
		rotationPiAboutAxisTemp.scale((ftype)3.141592653589793 / rotationPiAboutAxisTemp.length());
		rodriguesSo3Exp(rotationPiAboutAxisTemp, (ftype)0.0, (ftype)0.20264236728467558, result);
	}

	static void sO3FromMu(const Vector3 &w, Matrix3x3 &result) {
		const ftype thetaSq = Vector3::dot(w, w);
		const ftype theta = fsqrt(thetaSq);
		ftype kA;
		ftype kB;
		if (thetaSq < (ftype)1.0E-8) {
			kA = (ftype)1.0 - ((ftype)0.1666666716337204 * thetaSq);
			kB = (ftype)0.5;
		} else if (thetaSq < (ftype)1.0E-6) {
			kB = (ftype)0.5 - ((ftype)0.0416666679084301 * thetaSq);
			kA = (ftype)1.0 - (thetaSq * (ftype)0.1666666716337204 * ((ftype)1.0 - (ftype)0.1666666716337204 * thetaSq));
		} else {
			const ftype invTheta = (ftype)1.0 / theta;
			kA = fsin(theta) * invTheta;
			kB = ((ftype)1.0 - fcos(theta)) * (invTheta * invTheta);
		}
		rodriguesSo3Exp(w, kA, kB, result);
	}

	static void muFromSO3(const Matrix3x3 &so3, Vector3 &result) {
		const ftype cosAngle = (so3.c[0][0] + so3.c[1][1] + so3.c[2][2] - (ftype)1.0) * (ftype)0.5;

		result.x = (so3.c[2][1] - so3.c[1][2]) * (ftype)0.5;
		result.y = (so3.c[0][2] - so3.c[2][0]) * (ftype)0.5;
		result.z = (so3.c[1][0] - so3.c[0][1]) * (ftype)0.5;

		const ftype sinAngleAbs = result.length();

		if (cosAngle > (ftype)0.7071067811865476) {
			if (sinAngleAbs > (ftype)0.0)
				result.scale(fasin(sinAngleAbs) / sinAngleAbs);
		} else if (cosAngle > (ftype)-0.7071067811865476) {
			result.scale(facos(cosAngle) / sinAngleAbs);
		} else {
			const ftype angle = (ftype)3.141592653589793 - fasin(sinAngleAbs);
			const ftype d0 = so3.c[0][0] - cosAngle;
			const ftype d = so3.c[1][1] - cosAngle;
			const ftype d2 = so3.c[2][2] - cosAngle;
			Vector3 r2;

			if ((d0 * d0) > (d * d) && (d0 * d0) > (d2 * d2)) {
				r2.x = d0;
				r2.y = (so3.c[1][0] + so3.c[0][1]) * (ftype)0.5;
				r2.z = (so3.c[0][2] + so3.c[2][0]) * (ftype)0.5;
			} else if ((d * d) > (d2 * d2)) {
				r2.x = (so3.c[1][0] + so3.c[0][1]) * (ftype)0.5;
				r2.y = d;
				r2.z = (so3.c[2][1] + so3.c[1][2]) * (ftype)0.5;
			} else {
				r2.x = (so3.c[0][2] + so3.c[2][0]) * (ftype)0.5;
				r2.y = (so3.c[2][1] + so3.c[1][2]) * (ftype)0.5;
				r2.z = d2;
			}

			if (Vector3::dot(r2, result) < (ftype)0.0)
				r2.scale((ftype)-1.0);
			r2.normalize();
			r2.scale(angle);

			result = r2;
		}
	}

private:
	static void rodriguesSo3Exp(const Vector3 &w, ftype kA, ftype kB, Matrix3x3 &result) {
		const ftype wx2 = w.x * w.x;
		const ftype wy2 = w.y * w.y;
		const ftype wz2 = w.z * w.z;
		result.c[0][0] = (ftype)1.0 - (kB * (wy2 + wz2));
		result.c[1][1] = (ftype)1.0 - (kB * (wx2 + wz2));
		result.c[2][2] = (ftype)1.0 - (kB * (wx2 + wy2));
		ftype a = kA * w.z;
		ftype b = kB * (w.x * w.y);
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

//******************************************************************
// I applied two optimizations here:
// - Since the only outcome from OrientationEKF is a pure rotation
// matrix, I decided to keep it in a 3x3 matrix
//
// - I removed many vectors and matrices, as they are only
// used locally
//******************************************************************
class OrientationEKF {
public:
	Matrix3x3 rotationMatrix;
private:
	Matrix3x3 so3SensorFromWorld, so3LastMotion, mP, mQ;
	Vector3 mz, down, lastGyro;
	uint64_t sensorTimestampGyro;
	ftype previousAccelNorm, movingAverageAccelNormChange, filteredGyroTimestep;
	int32_t numGyroTimestepSamples;
	bool timestepFilterInit, gyroFilterValid, alignedToGravity;

public:
	OrientationEKF() {
		memset(this, 0, sizeof(OrientationEKF));
		gyroFilterValid = true;
		reset();
	}

	void reset() {
		sensorTimestampGyro = 0;
		so3SensorFromWorld.setIdentity();
		so3LastMotion.setIdentity();
		mP.setZero();
		mP.setSameDiagonal((ftype)25.0);
		mQ.setZero();
		mQ.setSameDiagonal((ftype)1.0);
		mz.setZero();
		down.x = (ftype)0.0;
		down.y = (ftype)0.0;
		down.z = (ftype)9.81;
		alignedToGravity = false;
	}

	void computePredictedGLMatrix(ftype secondsAfterLastGyroEvent) {
		Vector3 pmu = lastGyro;
		pmu.scale(-secondsAfterLastGyroEvent);
		So3Util::sO3FromMu(pmu, rotationMatrix);
		Matrix3x3::mult(rotationMatrix, so3SensorFromWorld, rotationMatrix);
	}

	void processGyro(const Vector3 &gyro, uint64_t sensorTimestamp) {
		const ftype kTimeThreshold = (ftype)0.04;
		const ftype kdTDefault = (ftype)0.01;
		if (sensorTimestampGyro) {
			ftype dT = (sensorTimestamp - sensorTimestampGyro) * (ftype)1.0E-9;
			if (dT > kTimeThreshold) {
				dT = (gyroFilterValid ? filteredGyroTimestep : kdTDefault);
			} else {
				const ftype kFilterCoeff = (ftype)0.95;
				const int32_t kMinSamples = 10;
				if (!timestepFilterInit) {
					filteredGyroTimestep = dT;
					numGyroTimestepSamples = 1;
					timestepFilterInit = true;
				} else {
					filteredGyroTimestep = kFilterCoeff * filteredGyroTimestep + ((ftype)1.0 - kFilterCoeff) * dT;
					if (++numGyroTimestepSamples > kMinSamples)
						gyroFilterValid = true;
				}
			}
			Vector3 mu = gyro;
			mu.scale(-dT);
			So3Util::sO3FromMu(mu, so3LastMotion);
			Matrix3x3::mult(so3LastMotion, so3SensorFromWorld, so3SensorFromWorld);
			updateCovariancesAfterMotion();
			Matrix3x3 processGyroTempM2 = mQ;
			processGyroTempM2.scale(dT * dT);
			mP.plusEquals(processGyroTempM2);
		}
		sensorTimestampGyro = sensorTimestamp;
		lastGyro = gyro;
	}

	void processAcc(const Vector3 &acc) {
		mz = acc;

		const ftype currentAccelNorm = mz.length();
		ftype currentAccelNormChange = currentAccelNorm - previousAccelNorm;
		fintabs(currentAccelNormChange);
		previousAccelNorm = currentAccelNorm;
		const ftype kSmoothingFactor = (ftype)0.5;
		movingAverageAccelNormChange = kSmoothingFactor * (currentAccelNormChange + movingAverageAccelNormChange);
		const ftype kMaxAccelNormChange = (ftype)0.15;
		const ftype kMinAccelNoiseSigma = (ftype)0.75;
		const ftype kMaxAccelNoiseSigma = (ftype)7.0;
		const ftype normChangeRatio = movingAverageAccelNormChange / kMaxAccelNormChange;
		ftype accelNoiseSigma = kMinAccelNoiseSigma + normChangeRatio * (kMaxAccelNoiseSigma - kMinAccelNoiseSigma);
		if (accelNoiseSigma > kMaxAccelNoiseSigma)
			accelNoiseSigma = kMaxAccelNoiseSigma;

		Matrix3x3 mRaccel;
		mRaccel.setZero();
		mRaccel.setSameDiagonal(accelNoiseSigma * accelNoiseSigma);

		if (alignedToGravity) {
			Matrix3x3 mS, mH, mK;
			Vector3 mNu, mx;
			mS.setZero();
			mH.setZero();
			mK.setZero();
			accObservationFunctionForNumericalJacobian(so3SensorFromWorld, mNu);
			const ftype eps = (ftype)1.0E-7;
			for (int32_t dof = 0; dof < 3; dof++) {
				Vector3 delta;
				delta.setZero();
				delta.c[dof] = eps;
				Matrix3x3 processAccTempM1, processAccTempM2;
				So3Util::sO3FromMu(delta, processAccTempM1);
				Matrix3x3::mult(processAccTempM1, so3SensorFromWorld, processAccTempM2);
				Vector3 processAccTempV1, processAccTempV2;
				accObservationFunctionForNumericalJacobian(processAccTempM2, processAccTempV1);
				Vector3::sub(mNu, processAccTempV1, processAccTempV2);
				processAccTempV2.scale((ftype)1.0 / eps);
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
		Vector3 mh;
		Matrix3x3::mult(so3SensorFromWorldPred, down, mh);
		Matrix3x3 accObservationFunctionForNumericalJacobianTempM;
		So3Util::sO3FromTwoVec(mh, mz, accObservationFunctionForNumericalJacobianTempM);
		So3Util::muFromSO3(accObservationFunctionForNumericalJacobianTempM, result);
	}
};
