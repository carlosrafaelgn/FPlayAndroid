#include <jni.h>
#include <malloc.h>
#include <string.h>
#include <math.h>
#include <android/bitmap.h>
//#include <GLES2/gl2.h>

extern "C" {

//to make the math easier COLORS has 257 int's (from 0 to 256)
//static const unsigned int COLORS[] = { 0xff000000, 0xff0b00b2, 0xff0c00b1, 0xff0e00af, 0xff0e00af, 0xff0f00ae, 0xff1000ad, 0xff1200ac, 0xff1300ab, 0xff1500ab, 0xff1600aa, 0xff1700a9, 0xff1900a8, 0xff1a00a6, 0xff1b00a6, 0xff1d00a4, 0xff1f00a3, 0xff2000a1, 0xff2200a1, 0xff2300a0, 0xff25009e, 0xff27009d, 0xff29009c, 0xff2b009a, 0xff2d0099, 0xff2e0098, 0xff300096, 0xff320095, 0xff340094, 0xff360092, 0xff380090, 0xff39008f, 0xff3c008e, 0xff3e008c, 0xff40008b, 0xff420089, 0xff440088, 0xff470086, 0xff480085, 0xff4b0083, 0xff4c0082, 0xff4f0080, 0xff51007f, 0xff54007c, 0xff56007c, 0xff57007a, 0xff5a0078, 0xff5c0076, 0xff5f0075, 0xff610073, 0xff640071, 0xff65006f, 0xff68006e, 0xff6b006c, 0xff6d006a, 0xff6f0069, 0xff710066, 0xff740065, 0xff760063, 0xff790062, 0xff7b0060, 0xff7d005e, 0xff80005c, 0xff82005b, 0xff850059, 0xff860057, 0xff890056, 0xff8c0054, 0xff8e0052, 0xff910050, 0xff93004f, 0xff96004d, 0xff97004b, 0xff9a0049, 0xff9c0048, 0xff9f0046, 0xffa10045, 0xffa40043, 0xffa60040, 0xffa8003f, 0xffaa003e, 0xffad003c, 0xffaf003a, 0xffb10039, 0xffb30037, 0xffb60035, 0xffb80034, 0xffba0032, 0xffbc0031, 0xffbe002e, 0xffc1002d, 0xffc3002c, 0xffc5002a, 0xffc70028, 0xffca0027, 0xffcb0025, 0xffce0024, 0xffcf0023, 0xffd10022, 0xffd30020, 0xffd6001e, 0xffd7001d, 0xffd9001b, 0xffdb001a, 0xffdd0019, 0xffdf0017, 0xffe10017, 0xffe20015, 0xffe40014, 0xffe60012, 0xffe70011, 0xffe90010, 0xffea000f, 0xffec000d, 0xffed000c, 0xffef000b, 0xfff1000b, 0xfff2000a, 0xfff40008, 0xfff50007, 0xfff60006, 0xfff70005, 0xfff90005, 0xfff90003, 0xfffb0003, 0xfffc0002, 0xfffd0001, 0xfffe0001, 0xffff0000, 0xffff0100, 0xffff0200, 0xffff0300, 0xffff0500, 0xffff0600, 0xffff0600, 0xffff0800, 0xffff0900, 0xffff0b00, 0xffff0c00, 0xffff0d00, 0xffff0f00, 0xffff1000, 0xffff1200, 0xffff1400, 0xffff1500, 0xffff1700, 0xffff1900, 0xffff1a00, 0xffff1c00, 0xffff1d00, 0xffff2000, 0xffff2200, 0xffff2300, 0xffff2500, 0xffff2700, 0xffff2900, 0xffff2b00, 0xffff2d00, 0xffff2f00, 0xffff3100, 0xffff3400, 0xffff3500, 0xffff3700, 0xffff3900, 0xffff3c00, 0xffff3e00, 0xffff4000, 0xffff4200, 0xffff4400, 0xffff4700, 0xffff4900, 0xffff4b00, 0xffff4e00, 0xffff5000, 0xffff5200, 0xffff5500, 0xffff5700, 0xffff5900, 0xffff5c00, 0xffff5e00, 0xffff6100, 0xffff6300, 0xffff6600, 0xffff6800, 0xffff6a00, 0xffff6c00, 0xffff6f00, 0xffff7200, 0xffff7400, 0xffff7700, 0xffff7900, 0xffff7c00, 0xffff7e00, 0xffff8000, 0xffff8300, 0xffff8500, 0xffff8700, 0xffff8a00, 0xffff8d00, 0xffff8f00, 0xffff9200, 0xffff9500, 0xffff9700, 0xffff9900, 0xffff9b00, 0xffff9e00, 0xffffa000, 0xffffa300, 0xffffa500, 0xffffa700, 0xffffa900, 0xffffac00, 0xffffae00, 0xffffb100, 0xffffb200, 0xffffb600, 0xffffb700, 0xffffba00, 0xffffbc00, 0xffffbe00, 0xffffc100, 0xffffc300, 0xffffc400, 0xffffc700, 0xffffc900, 0xffffcb00, 0xffffcd00, 0xffffcf00, 0xffffd100, 0xffffd300, 0xffffd500, 0xffffd700, 0xffffd900, 0xffffdb00, 0xffffdd00, 0xffffde00, 0xffffe000, 0xffffe100, 0xffffe400, 0xffffe500, 0xffffe700, 0xffffe900, 0xffffea00, 0xffffeb00, 0xffffed00, 0xffffef00, 0xfffff000, 0xfffff100, 0xfffff300, 0xfffff400, 0xfffff500, 0xfffff600, 0xfffff800, 0xfffff900, 0xfffffa00, 0xfffffb00, 0xfffffb00 };
static const unsigned int COLORS[] = { 0xff000000, 0xffb2000b, 0xffb1000c, 0xffaf000e, 0xffaf000e, 0xffae000f, 0xffad0010, 0xffac0012, 0xffab0013, 0xffab0015, 0xffaa0016, 0xffa90017, 0xffa80019, 0xffa6001a, 0xffa6001b, 0xffa4001d, 0xffa3001f, 0xffa10020, 0xffa10022, 0xffa00023, 0xff9e0025, 0xff9d0027, 0xff9c0029, 0xff9a002b, 0xff99002d, 0xff98002e, 0xff960030, 0xff950032, 0xff940034, 0xff920036, 0xff900038, 0xff8f0039, 0xff8e003c, 0xff8c003e, 0xff8b0040, 0xff890042, 0xff880044, 0xff860047, 0xff850048, 0xff83004b, 0xff82004c, 0xff80004f, 0xff7f0051, 0xff7c0054, 0xff7c0056, 0xff7a0057, 0xff78005a, 0xff76005c, 0xff75005f, 0xff730061, 0xff710064, 0xff6f0065, 0xff6e0068, 0xff6c006b, 0xff6a006d, 0xff69006f, 0xff660071, 0xff650074, 0xff630076, 0xff620079, 0xff60007b, 0xff5e007d, 0xff5c0080, 0xff5b0082, 0xff590085, 0xff570086, 0xff560089, 0xff54008c, 0xff52008e, 0xff500091, 0xff4f0093, 0xff4d0096, 0xff4b0097, 0xff49009a, 0xff48009c, 0xff46009f, 0xff4500a1, 0xff4300a4, 0xff4000a6, 0xff3f00a8, 0xff3e00aa, 0xff3c00ad, 0xff3a00af, 0xff3900b1, 0xff3700b3, 0xff3500b6, 0xff3400b8, 0xff3200ba, 0xff3100bc, 0xff2e00be, 0xff2d00c1, 0xff2c00c3, 0xff2a00c5, 0xff2800c7, 0xff2700ca, 0xff2500cb, 0xff2400ce, 0xff2300cf, 0xff2200d1, 0xff2000d3, 0xff1e00d6, 0xff1d00d7, 0xff1b00d9, 0xff1a00db, 0xff1900dd, 0xff1700df, 0xff1700e1, 0xff1500e2, 0xff1400e4, 0xff1200e6, 0xff1100e7, 0xff1000e9, 0xff0f00ea, 0xff0d00ec, 0xff0c00ed, 0xff0b00ef, 0xff0b00f1, 0xff0a00f2, 0xff0800f4, 0xff0700f5, 0xff0600f6, 0xff0500f7, 0xff0500f9, 0xff0300f9, 0xff0300fb, 0xff0200fc, 0xff0100fd, 0xff0100fe, 0xff0000ff, 0xff0001ff, 0xff0002ff, 0xff0003ff, 0xff0005ff, 0xff0006ff, 0xff0006ff, 0xff0008ff, 0xff0009ff, 0xff000bff, 0xff000cff, 0xff000dff, 0xff000fff, 0xff0010ff, 0xff0012ff, 0xff0014ff, 0xff0015ff, 0xff0017ff, 0xff0019ff, 0xff001aff, 0xff001cff, 0xff001dff, 0xff0020ff, 0xff0022ff, 0xff0023ff, 0xff0025ff, 0xff0027ff, 0xff0029ff, 0xff002bff, 0xff002dff, 0xff002fff, 0xff0031ff, 0xff0034ff, 0xff0035ff, 0xff0037ff, 0xff0039ff, 0xff003cff, 0xff003eff, 0xff0040ff, 0xff0042ff, 0xff0044ff, 0xff0047ff, 0xff0049ff, 0xff004bff, 0xff004eff, 0xff0050ff, 0xff0052ff, 0xff0055ff, 0xff0057ff, 0xff0059ff, 0xff005cff, 0xff005eff, 0xff0061ff, 0xff0063ff, 0xff0066ff, 0xff0068ff, 0xff006aff, 0xff006cff, 0xff006fff, 0xff0072ff, 0xff0074ff, 0xff0077ff, 0xff0079ff, 0xff007cff, 0xff007eff, 0xff0080ff, 0xff0083ff, 0xff0085ff, 0xff0087ff, 0xff008aff, 0xff008dff, 0xff008fff, 0xff0092ff, 0xff0095ff, 0xff0097ff, 0xff0099ff, 0xff009bff, 0xff009eff, 0xff00a0ff, 0xff00a3ff, 0xff00a5ff, 0xff00a7ff, 0xff00a9ff, 0xff00acff, 0xff00aeff, 0xff00b1ff, 0xff00b2ff, 0xff00b6ff, 0xff00b7ff, 0xff00baff, 0xff00bcff, 0xff00beff, 0xff00c1ff, 0xff00c3ff, 0xff00c4ff, 0xff00c7ff, 0xff00c9ff, 0xff00cbff, 0xff00cdff, 0xff00cfff, 0xff00d1ff, 0xff00d3ff, 0xff00d5ff, 0xff00d7ff, 0xff00d9ff, 0xff00dbff, 0xff00ddff, 0xff00deff, 0xff00e0ff, 0xff00e1ff, 0xff00e4ff, 0xff00e5ff, 0xff00e7ff, 0xff00e9ff, 0xff00eaff, 0xff00ebff, 0xff00edff, 0xff00efff, 0xff00f0ff, 0xff00f1ff, 0xff00f3ff, 0xff00f4ff, 0xff00f5ff, 0xff00f6ff, 0xff00f8ff, 0xff00f9ff, 0xff00faff, 0xff00fbff, 0xff00fbff };
static float FLOAT_BUFFER[512], filterNew, filterOld;

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_setFilter(JNIEnv* env, jclass clazz, jfloat jfilterNew) {
	float* fft = FLOAT_BUFFER;
	float* multiplier = fft + 256;
	filterNew = jfilterNew;
	filterOld = 1.0f - jfilterNew;
	const float filter = filterNew;
	for (int i = 0; i < 256; i++) {
		fft[i] = 0;
		multiplier[i] = (((float)i + 100.0f) / 101.0f) * expf((float)i / 300.0f) * filter;
	}
}

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_init(JNIEnv* env, jclass clazz, jfloat jfilterNew) {
	Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_setFilter(env, clazz, jfilterNew);
}

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_process(JNIEnv* env, jclass clazz, jbyteArray jbfft, jintArray jpts) {
	float* const fft = FLOAT_BUFFER;
	const float* const multiplier = fft + 256;
	//if (!fft)
	//	return;
	unsigned int* const pts = (unsigned int*)env->GetIntArrayElements(jpts, 0);
	const signed char* const bfft = (signed char*)env->GetByteArrayElements(jbfft, 0);
	//we are not drawing/analyzing the last bin (Nyquist)
	fft[0] = (multiplier[0] * (float)((int)bfft[0])) + (0.5f * fft[0]);
	//fft[i] stores values from 0 to -128/127 (inclusive)
	//pts[i] goes from 0 to 32768 (inclusive)
	int v0 = (int)(fft[0] * 256.0f);
	if (v0 < 0)
		v0 = -v0;
	if (v0 > 32768)
		pts[0] = 32768;
	else
		pts[0] = v0;
	const float filter = filterOld;
	for (int i = 1; i < 256; i++) {
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		const float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im)))) + (filter * fft[i]);
		fft[i] = m;
		const int v = (int)(m * 256.0f);
		if (v > 32768)
			pts[i] = 32768;
		else if (v < 0)
			pts[i] = 0;
		else
			pts[i] = v;
	}
	env->ReleaseIntArrayElements(jpts, (jint*)pts, 0);
	env->ReleaseByteArrayElements(jbfft, (jbyte*)bfft, JNI_ABORT);
}

JNIEXPORT int JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_fill(JNIEnv* env, jclass clazz, jobject bitmap, int barW, jintArray jpts) {
	AndroidBitmapInfo inf;
	if (AndroidBitmap_getInfo(env, bitmap, &inf))
		return -1;
	if (inf.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
		return -2;
	unsigned int* dst = 0;
	if (AndroidBitmap_lockPixels(env, bitmap, (void**)&dst))
		return -3;
	const unsigned int* const pts = (unsigned int*)env->GetIntArrayElements(jpts, 0);
	if (barW == 1) {
		for (int i = 0; i < 256; i++) {
			//pts[i] goes from 0 to 32768 (inclusive)
			unsigned int value = pts[i];
			const unsigned int top = inf.height - ((value * inf.height) >> 15);
			value = COLORS[value >> 7];
			unsigned int* currentBar = dst;
			dst++;
			int y = 0;
			for (y = 0; y < top; y++) {
				*currentBar = 0xff000000;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
			for (; y < inf.height; y++) {
				*currentBar = value;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
		}
	} else if (barW == 2) {
		for (int i = 0; i < 256; i++) {
			//pts[i] goes from 0 to 32768 (inclusive)
			unsigned int value = pts[i];
			const unsigned int top = inf.height - ((value * inf.height) >> 15);
			value = COLORS[value >> 7];
			unsigned int* currentBar = dst;
			dst += 2;
			int y = 0;
			for (y = 0; y < top; y++) {
				*currentBar = 0xff000000;
				currentBar[1] = 0xff000000;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
			for (; y < inf.height; y++) {
				*currentBar = value;
				currentBar[1] = value;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
		}
	} else if (barW == 3) {
		for (int i = 0; i < 256; i++) {
			//pts[i] goes from 0 to 32768 (inclusive)
			unsigned int value = pts[i];
			const unsigned int top = inf.height - ((value * inf.height) >> 15);
			value = COLORS[value >> 7];
			unsigned int* currentBar = dst;
			dst += 3;
			int y = 0;
			for (y = 0; y < top; y++) {
				*currentBar = 0xff000000;
				currentBar[1] = 0xff000000;
				currentBar[2] = 0xff000000;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
			for (; y < inf.height; y++) {
				*currentBar = value;
				currentBar[1] = value;
				currentBar[2] = value;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
		}
	} else {
		barW--;
		for (int i = 0; i < 256; i++) {
			//pts[i] goes from 0 to 32768 (inclusive)
			unsigned int value = pts[i];
			const unsigned int top = inf.height - ((value * inf.height) >> 15);
			value = COLORS[value >> 7];
			unsigned int* currentBar = dst;
			dst += (barW + 1);
			int y = 0;
			for (y = 0; y < top; y++) {
				for (int b = barW; b >= 0; b--)
					currentBar[b] = 0xff000000;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
			for (; y < inf.height; y++) {
				for (int b = barW; b >= 0; b--)
					currentBar[b] = value;
				currentBar = (unsigned int*)((unsigned char*)currentBar + inf.stride);
			}
		}
	}
	env->ReleaseIntArrayElements(jpts, (jint*)pts, JNI_ABORT);
	AndroidBitmap_unlockPixels(env, bitmap);
	return 0;
}

}
