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

#include <sched.h>

//Adaptation of Peterson's algorithm
//http://en.wikipedia.org/wiki/Peterson%27s_algorithm

//http://gcc.gnu.org/onlinedocs/gcc-4.4.3/gcc/Atomic-Builtins.html
class SimpleMutex {
private:
	unsigned int flag0, flag1, turn;
public:
	SimpleMutex() {
		flag0 = 0;
		flag1 = 0;
		turn = 0;
	}

	inline void enter0() {
		flag0 = 1;
		turn = 1;
		__sync_synchronize();
		while (flag1 && turn)
			sched_yield();
	}

	inline void leave0() {
		flag0 = 0;
		__sync_synchronize();
	}

	inline void enter1() {
		flag1 = 1;
		turn = 0;
		__sync_synchronize();
		while (flag0 && !turn)
			sched_yield();
	}

	inline void leave1() {
		flag1 = 0;
		__sync_synchronize();
	}
};
