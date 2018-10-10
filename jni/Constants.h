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

#define CAPTURE_SIZE 1024
#define LOG_FFT_SIZE 10
#define MAX_FFT_SIZE 1024

#define FFT_SIZE 1024 //FFT_SIZE must be a power of 2 <= 65536
#define QUARTER_FFT_SIZE (FFT_SIZE / 4)

#define COEF_SPEED_0 (0.0625f / 16.0f) //0.0625f @ 60fps (~16ms)
#define COEF_SPEED_1 (0.09375f / 16.0f) //0.09375 @ 60fps (~16ms)
#define COEF_SPEED_2 (0.140625f / 16.0f) //0.140625f @ 60fps (~16ms)
#define COEF_SPEED_FASTEST 1.0f //this setting ignores old values, effectively not applying any filters
#define COEF_SPEED_DEF COEF_SPEED_2

//used during Bluetooth processing
#define BLUETOOTH_BINS_4     0x0020
#define BLUETOOTH_BINS_8     0x0021
#define BLUETOOTH_BINS_16    0x0022
#define BLUETOOTH_BINS_32    0x0023
#define BLUETOOTH_BINS_64    0x0024
#define BLUETOOTH_BINS_128   0x0025
#define BLUETOOTH_BINS_256   0x0026
#define BLUETOOTH_PROCESSING 0x00FF

#define DATA_NONE            0x0000
#define DATA_FFT             0x0100
#define DATA_VUMETER         0x0200
#define IGNORE_INPUT         0x0400

#define BEAT_DETECTION_1     0x1000
#define BEAT_DETECTION_2     0x2000
#define BEAT_DETECTION_3     0x3000
#define BEAT_DETECTION_4     0x4000
#define BEAT_DETECTION_5     0x5000
#define BEAT_DETECTION_6     0x6000
#define BEAT_DETECTION_7     0x7000
#define BEAT_DETECTION       0xF000
