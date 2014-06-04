#include <jni.h>
#ifdef __ARM_NEON__
#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <machine/cpu-features.h>
#include <arm_neon.h>
#endif
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <string.h>
#include <math.h>

extern "C" {

//for the alignment:
//https://gcc.gnu.org/onlinedocs/gcc-3.2/gcc/Variable-Attributes.html

//to make the math easier COLORS has 257 int's (from 0 to 256)
//static const unsigned int COLORS[] = { 0xff000000, 0xff0b00b2, 0xff0c00b1, 0xff0e00af, 0xff0e00af, 0xff0f00ae, 0xff1000ad, 0xff1200ac, 0xff1300ab, 0xff1500ab, 0xff1600aa, 0xff1700a9, 0xff1900a8, 0xff1a00a6, 0xff1b00a6, 0xff1d00a4, 0xff1f00a3, 0xff2000a1, 0xff2200a1, 0xff2300a0, 0xff25009e, 0xff27009d, 0xff29009c, 0xff2b009a, 0xff2d0099, 0xff2e0098, 0xff300096, 0xff320095, 0xff340094, 0xff360092, 0xff380090, 0xff39008f, 0xff3c008e, 0xff3e008c, 0xff40008b, 0xff420089, 0xff440088, 0xff470086, 0xff480085, 0xff4b0083, 0xff4c0082, 0xff4f0080, 0xff51007f, 0xff54007c, 0xff56007c, 0xff57007a, 0xff5a0078, 0xff5c0076, 0xff5f0075, 0xff610073, 0xff640071, 0xff65006f, 0xff68006e, 0xff6b006c, 0xff6d006a, 0xff6f0069, 0xff710066, 0xff740065, 0xff760063, 0xff790062, 0xff7b0060, 0xff7d005e, 0xff80005c, 0xff82005b, 0xff850059, 0xff860057, 0xff890056, 0xff8c0054, 0xff8e0052, 0xff910050, 0xff93004f, 0xff96004d, 0xff97004b, 0xff9a0049, 0xff9c0048, 0xff9f0046, 0xffa10045, 0xffa40043, 0xffa60040, 0xffa8003f, 0xffaa003e, 0xffad003c, 0xffaf003a, 0xffb10039, 0xffb30037, 0xffb60035, 0xffb80034, 0xffba0032, 0xffbc0031, 0xffbe002e, 0xffc1002d, 0xffc3002c, 0xffc5002a, 0xffc70028, 0xffca0027, 0xffcb0025, 0xffce0024, 0xffcf0023, 0xffd10022, 0xffd30020, 0xffd6001e, 0xffd7001d, 0xffd9001b, 0xffdb001a, 0xffdd0019, 0xffdf0017, 0xffe10017, 0xffe20015, 0xffe40014, 0xffe60012, 0xffe70011, 0xffe90010, 0xffea000f, 0xffec000d, 0xffed000c, 0xffef000b, 0xfff1000b, 0xfff2000a, 0xfff40008, 0xfff50007, 0xfff60006, 0xfff70005, 0xfff90005, 0xfff90003, 0xfffb0003, 0xfffc0002, 0xfffd0001, 0xfffe0001, 0xffff0000, 0xffff0100, 0xffff0200, 0xffff0300, 0xffff0500, 0xffff0600, 0xffff0600, 0xffff0800, 0xffff0900, 0xffff0b00, 0xffff0c00, 0xffff0d00, 0xffff0f00, 0xffff1000, 0xffff1200, 0xffff1400, 0xffff1500, 0xffff1700, 0xffff1900, 0xffff1a00, 0xffff1c00, 0xffff1d00, 0xffff2000, 0xffff2200, 0xffff2300, 0xffff2500, 0xffff2700, 0xffff2900, 0xffff2b00, 0xffff2d00, 0xffff2f00, 0xffff3100, 0xffff3400, 0xffff3500, 0xffff3700, 0xffff3900, 0xffff3c00, 0xffff3e00, 0xffff4000, 0xffff4200, 0xffff4400, 0xffff4700, 0xffff4900, 0xffff4b00, 0xffff4e00, 0xffff5000, 0xffff5200, 0xffff5500, 0xffff5700, 0xffff5900, 0xffff5c00, 0xffff5e00, 0xffff6100, 0xffff6300, 0xffff6600, 0xffff6800, 0xffff6a00, 0xffff6c00, 0xffff6f00, 0xffff7200, 0xffff7400, 0xffff7700, 0xffff7900, 0xffff7c00, 0xffff7e00, 0xffff8000, 0xffff8300, 0xffff8500, 0xffff8700, 0xffff8a00, 0xffff8d00, 0xffff8f00, 0xffff9200, 0xffff9500, 0xffff9700, 0xffff9900, 0xffff9b00, 0xffff9e00, 0xffffa000, 0xffffa300, 0xffffa500, 0xffffa700, 0xffffa900, 0xffffac00, 0xffffae00, 0xffffb100, 0xffffb200, 0xffffb600, 0xffffb700, 0xffffba00, 0xffffbc00, 0xffffbe00, 0xffffc100, 0xffffc300, 0xffffc400, 0xffffc700, 0xffffc900, 0xffffcb00, 0xffffcd00, 0xffffcf00, 0xffffd100, 0xffffd300, 0xffffd500, 0xffffd700, 0xffffd900, 0xffffdb00, 0xffffdd00, 0xffffde00, 0xffffe000, 0xffffe100, 0xffffe400, 0xffffe500, 0xffffe700, 0xffffe900, 0xffffea00, 0xffffeb00, 0xffffed00, 0xffffef00, 0xfffff000, 0xfffff100, 0xfffff300, 0xfffff400, 0xfffff500, 0xfffff600, 0xfffff800, 0xfffff900, 0xfffffa00, 0xfffffb00, 0xfffffb00 };
//static const unsigned int COLORS[] = { 0xff000000, 0xffb2000b, 0xffb1000c, 0xffaf000e, 0xffaf000e, 0xffae000f, 0xffad0010, 0xffac0012, 0xffab0013, 0xffab0015, 0xffaa0016, 0xffa90017, 0xffa80019, 0xffa6001a, 0xffa6001b, 0xffa4001d, 0xffa3001f, 0xffa10020, 0xffa10022, 0xffa00023, 0xff9e0025, 0xff9d0027, 0xff9c0029, 0xff9a002b, 0xff99002d, 0xff98002e, 0xff960030, 0xff950032, 0xff940034, 0xff920036, 0xff900038, 0xff8f0039, 0xff8e003c, 0xff8c003e, 0xff8b0040, 0xff890042, 0xff880044, 0xff860047, 0xff850048, 0xff83004b, 0xff82004c, 0xff80004f, 0xff7f0051, 0xff7c0054, 0xff7c0056, 0xff7a0057, 0xff78005a, 0xff76005c, 0xff75005f, 0xff730061, 0xff710064, 0xff6f0065, 0xff6e0068, 0xff6c006b, 0xff6a006d, 0xff69006f, 0xff660071, 0xff650074, 0xff630076, 0xff620079, 0xff60007b, 0xff5e007d, 0xff5c0080, 0xff5b0082, 0xff590085, 0xff570086, 0xff560089, 0xff54008c, 0xff52008e, 0xff500091, 0xff4f0093, 0xff4d0096, 0xff4b0097, 0xff49009a, 0xff48009c, 0xff46009f, 0xff4500a1, 0xff4300a4, 0xff4000a6, 0xff3f00a8, 0xff3e00aa, 0xff3c00ad, 0xff3a00af, 0xff3900b1, 0xff3700b3, 0xff3500b6, 0xff3400b8, 0xff3200ba, 0xff3100bc, 0xff2e00be, 0xff2d00c1, 0xff2c00c3, 0xff2a00c5, 0xff2800c7, 0xff2700ca, 0xff2500cb, 0xff2400ce, 0xff2300cf, 0xff2200d1, 0xff2000d3, 0xff1e00d6, 0xff1d00d7, 0xff1b00d9, 0xff1a00db, 0xff1900dd, 0xff1700df, 0xff1700e1, 0xff1500e2, 0xff1400e4, 0xff1200e6, 0xff1100e7, 0xff1000e9, 0xff0f00ea, 0xff0d00ec, 0xff0c00ed, 0xff0b00ef, 0xff0b00f1, 0xff0a00f2, 0xff0800f4, 0xff0700f5, 0xff0600f6, 0xff0500f7, 0xff0500f9, 0xff0300f9, 0xff0300fb, 0xff0200fc, 0xff0100fd, 0xff0100fe, 0xff0000ff, 0xff0001ff, 0xff0002ff, 0xff0003ff, 0xff0005ff, 0xff0006ff, 0xff0006ff, 0xff0008ff, 0xff0009ff, 0xff000bff, 0xff000cff, 0xff000dff, 0xff000fff, 0xff0010ff, 0xff0012ff, 0xff0014ff, 0xff0015ff, 0xff0017ff, 0xff0019ff, 0xff001aff, 0xff001cff, 0xff001dff, 0xff0020ff, 0xff0022ff, 0xff0023ff, 0xff0025ff, 0xff0027ff, 0xff0029ff, 0xff002bff, 0xff002dff, 0xff002fff, 0xff0031ff, 0xff0034ff, 0xff0035ff, 0xff0037ff, 0xff0039ff, 0xff003cff, 0xff003eff, 0xff0040ff, 0xff0042ff, 0xff0044ff, 0xff0047ff, 0xff0049ff, 0xff004bff, 0xff004eff, 0xff0050ff, 0xff0052ff, 0xff0055ff, 0xff0057ff, 0xff0059ff, 0xff005cff, 0xff005eff, 0xff0061ff, 0xff0063ff, 0xff0066ff, 0xff0068ff, 0xff006aff, 0xff006cff, 0xff006fff, 0xff0072ff, 0xff0074ff, 0xff0077ff, 0xff0079ff, 0xff007cff, 0xff007eff, 0xff0080ff, 0xff0083ff, 0xff0085ff, 0xff0087ff, 0xff008aff, 0xff008dff, 0xff008fff, 0xff0092ff, 0xff0095ff, 0xff0097ff, 0xff0099ff, 0xff009bff, 0xff009eff, 0xff00a0ff, 0xff00a3ff, 0xff00a5ff, 0xff00a7ff, 0xff00a9ff, 0xff00acff, 0xff00aeff, 0xff00b1ff, 0xff00b2ff, 0xff00b6ff, 0xff00b7ff, 0xff00baff, 0xff00bcff, 0xff00beff, 0xff00c1ff, 0xff00c3ff, 0xff00c4ff, 0xff00c7ff, 0xff00c9ff, 0xff00cbff, 0xff00cdff, 0xff00cfff, 0xff00d1ff, 0xff00d3ff, 0xff00d5ff, 0xff00d7ff, 0xff00d9ff, 0xff00dbff, 0xff00ddff, 0xff00deff, 0xff00e0ff, 0xff00e1ff, 0xff00e4ff, 0xff00e5ff, 0xff00e7ff, 0xff00e9ff, 0xff00eaff, 0xff00ebff, 0xff00edff, 0xff00efff, 0xff00f0ff, 0xff00f1ff, 0xff00f3ff, 0xff00f4ff, 0xff00f5ff, 0xff00f6ff, 0xff00f8ff, 0xff00f9ff, 0xff00faff, 0xff00fbff, 0xff00fbff };
static const unsigned short COLORS[] __attribute__((aligned(16))) = { 0x0000, 0x0816, 0x0816, 0x0815, 0x0815, 0x0815, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1015, 0x1815, 0x1814, 0x1814, 0x1814, 0x1814, 0x2014, 0x2014, 0x2014, 0x2013, 0x2013, 0x2813, 0x2813, 0x2813, 0x2813, 0x3012, 0x3012, 0x3012, 0x3012, 0x3812, 0x3811, 0x3811, 0x3811, 0x4011, 0x4011, 0x4011, 0x4010, 0x4810, 0x4810, 0x4810, 0x4810, 0x500f, 0x500f, 0x500f, 0x500f, 0x580f, 0x580e, 0x580e, 0x600e, 0x600e, 0x600d, 0x680d, 0x680d, 0x680d, 0x680d, 0x700c, 0x700c, 0x700c, 0x780c, 0x780c, 0x780b, 0x800b, 0x800b, 0x800b, 0x800a, 0x880a, 0x880a, 0x880a, 0x900a, 0x9009, 0x9009, 0x9009, 0x9809, 0x9809, 0x9808, 0xa008, 0xa008, 0xa008, 0xa807, 0xa807, 0xa807, 0xa807, 0xb007, 0xb006, 0xb006, 0xb806, 0xb806, 0xb806, 0xb805, 0xc005, 0xc005, 0xc005, 0xc005, 0xc804, 0xc804, 0xc804, 0xc804, 0xd004, 0xd004, 0xd003, 0xd003, 0xd803, 0xd803, 0xd803, 0xd802, 0xe002, 0xe002, 0xe002, 0xe002, 0xe002, 0xe802, 0xe801, 0xe801, 0xe801, 0xe801, 0xf001, 0xf001, 0xf001, 0xf000, 0xf000, 0xf000, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf800, 0xf820, 0xf820, 0xf820, 0xf840, 0xf840, 0xf840, 0xf860, 0xf860, 0xf860, 0xf880, 0xf880, 0xf8a0, 0xf8a0, 0xf8a0, 0xf8c0, 0xf8c0, 0xf8e0, 0xf8e0, 0xf900, 0xf900, 0xf900, 0xf920, 0xf920, 0xf940, 0xf940, 0xf960, 0xf960, 0xf980, 0xf9a0, 0xf9a0, 0xf9a0, 0xf9c0, 0xf9e0, 0xf9e0, 0xfa00, 0xfa00, 0xfa20, 0xfa20, 0xfa40, 0xfa40, 0xfa60, 0xfa80, 0xfa80, 0xfaa0, 0xfaa0, 0xfac0, 0xfae0, 0xfae0, 0xfb00, 0xfb00, 0xfb20, 0xfb40, 0xfb40, 0xfb60, 0xfb60, 0xfb80, 0xfba0, 0xfba0, 0xfbc0, 0xfbe0, 0xfbe0, 0xfc00, 0xfc00, 0xfc20, 0xfc20, 0xfc40, 0xfc60, 0xfc60, 0xfc80, 0xfca0, 0xfca0, 0xfcc0, 0xfcc0, 0xfce0, 0xfd00, 0xfd00, 0xfd20, 0xfd20, 0xfd40, 0xfd60, 0xfd60, 0xfd80, 0xfd80, 0xfda0, 0xfda0, 0xfdc0, 0xfde0, 0xfde0, 0xfe00, 0xfe00, 0xfe20, 0xfe20, 0xfe40, 0xfe40, 0xfe60, 0xfe60, 0xfe80, 0xfe80, 0xfea0, 0xfea0, 0xfec0, 0xfec0, 0xfee0, 0xfee0, 0xff00, 0xff00, 0xff20, 0xff20, 0xff20, 0xff40, 0xff40, 0xff40, 0xff60, 0xff60, 0xff80, 0xff80, 0xff80, 0xffa0, 0xffa0, 0xffa0, 0xffc0, 0xffc0, 0xffc0, 0xffc0, 0xffc0 };
static const unsigned short COLORS_GREEN[] __attribute__((aligned(16))) = { 0x0000, 0x0000, 0x0000, 0x0020, 0x0020, 0x0040, 0x0040, 0x0060, 0x0060, 0x0080, 0x00a0, 0x00a0, 0x00c0, 0x00e0, 0x00e0, 0x0100, 0x0120, 0x0140, 0x0160, 0x0160, 0x0180, 0x01a0, 0x01c0, 0x01e0, 0x01e0, 0x0200, 0x0220, 0x0240, 0x0260, 0x0280, 0x0280, 0x02a0, 0x02c0, 0x02e0, 0x0300, 0x0320, 0x0340, 0x0360, 0x0360, 0x0380, 0x03a0, 0x03c0, 0x03e0, 0x0400, 0x0400, 0x0420, 0x0440, 0x0440, 0x0460, 0x0480, 0x0480, 0x04a0, 0x04c0, 0x04c0, 0x04c0, 0x04e0, 0x04e0, 0x0ce0, 0x0d00, 0x0d00, 0x0d00, 0x1520, 0x1520, 0x1540, 0x1540, 0x1d40, 0x1d60, 0x1d60, 0x2580, 0x2580, 0x25a0, 0x2da0, 0x2da0, 0x2dc0, 0x35c0, 0x35e0, 0x35e0, 0x3e00, 0x3e00, 0x3e00, 0x4620, 0x4620, 0x4e40, 0x4e40, 0x4e60, 0x5660, 0x5660, 0x5e80, 0x5e80, 0x5ea0, 0x66a0, 0x66c0, 0x66c0, 0x6ec0, 0x6ee0, 0x76e0, 0x7700, 0x7f00, 0x7f00, 0x7f20, 0x8720, 0x8720, 0x8f40, 0x8f40, 0x8f40, 0x9760, 0x9760, 0x9f80, 0x9f80, 0x9f80, 0xa780, 0xa7a0, 0xafa0, 0xafa0, 0xafc0, 0xb7c0, 0xb7c0, 0xbfc0, 0xbfc0, 0xbfe0, 0xc7e0, 0xc7e0, 0xc7e0, 0xcfe0, 0xcfe0, 0xcfe0, 0xd7e0, 0xd7e0, 0xdfe0, 0xdfe0, 0xdfe0, 0xdfe0, 0xe7e0, 0xe7e0, 0xe7e0, 0xefe0, 0xefe0, 0xefe0, 0xefe0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xf7e0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffe0, 0xffc0, 0xffc0, 0xffc0, 0xffa0, 0xffa0, 0xffa0, 0xff80, 0xff80, 0xff80, 0xff60, 0xff60, 0xff40, 0xff40, 0xff40, 0xff20, 0xff00, 0xff00, 0xfee0, 0xfee0, 0xfec0, 0xfec0, 0xfea0, 0xfea0, 0xfe80, 0xfe60, 0xfe60, 0xfe40, 0xfe20, 0xfe20, 0xfe00, 0xfe00, 0xfde0, 0xfdc0, 0xfda0, 0xfda0, 0xfd80, 0xfd60, 0xfd60, 0xfd40, 0xfd20, 0xfd00, 0xfd00, 0xfce0, 0xfcc0, 0xfca0, 0xfca0, 0xfc80, 0xfc60, 0xfc40, 0xfc40, 0xfc20, 0xfc00, 0xfbe0, 0xfbe0, 0xfbc0, 0xfba0, 0xfb80, 0xfb60, 0xfb60, 0xfb40, 0xfb20, 0xfb00, 0xfb00, 0xfae0, 0xfac0, 0xfaa0, 0xfaa0, 0xfa80, 0xfa60, 0xfa60, 0xfa40, 0xfa20, 0xfa00, 0xfa00, 0xf9e0, 0xf9c0, 0xf9c0, 0xf9a0, 0xf980, 0xf980, 0xf960, 0xf960, 0xf940, 0xf920, 0xf920, 0xf900, 0xf8e0, 0xf8e0, 0xf8c0, 0xf8c0, 0xf8a0, 0xf8a0, 0xf880, 0xf880, 0xf860, 0xf860, 0xf860, 0xf840, 0xf840, 0xf820, 0xf820, 0xf820, 0xf800, 0xf800, 0xf800, 0xf800 };
static float floatBuffer[256 * 2] __attribute__((aligned(16)));
static float invBarW;
static int barW, barH, barBins, barWidthInPixels, recreateVoice;
static unsigned short bgColor;
static unsigned short* voice, *alignedVoice;
#ifdef __ARM_NEON__
static unsigned int neonMode;
static int* computedBars, *alignedComputedBars;
static int computedBarsWidth;
#endif

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_refreshMultiplier(JNIEnv* env, jclass clazz, jboolean isVoice) {
	float* const fft = floatBuffer;
	float* const multiplier = fft + 256;
	if (isVoice) {
		for (int i = 0; i < 256; i++) {
			fft[i] = 0;
			multiplier[i] = 2.0f * expf((float)i / 128.0f);
		}
	} else {
		for (int i = 0; i < 256; i++) {
			fft[i] = 0;
			multiplier[i] = 256.0f * expf((float)i / 128.0f);
			//multiplier[i] = 1;//(((float)i + 100.0f) / 101.0f) * expf((float)i / 300.0f);
		}
	}
}

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_init(JNIEnv* env, jclass clazz, int jbgColor) {
	voice = 0;
	recreateVoice = 0;
#ifdef __ARM_NEON__
	neonMode = 0;
	computedBars = 0;
	computedBarsWidth = -1;
#endif
	const unsigned int r = ((jbgColor >> 16) & 0xff) >> 3;
	const unsigned int g = ((jbgColor >> 8) & 0xff) >> 2;
	const unsigned int b = (jbgColor & 0xff) >> 3;
	bgColor = (unsigned short)((r << 11) | (g << 5) | b);
	Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_refreshMultiplier(env, clazz, 0);
}

JNIEXPORT int JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_checkNeonMode(JNIEnv* env, jclass clazz) {
#ifdef __ARM_NEON__
	//based on
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.c?r=2195
	//http://code.google.com/p/webrtc/source/browse/trunk/src/system_wrappers/source/android/cpu-features.h?r=2195
	neonMode = 0;
	char cpuinfo[4096];
	int cpuinfo_len = -1;
	int fd = open("/proc/cpuinfo", O_RDONLY);
	if (fd >= 0) {
		do {
			cpuinfo_len = read(fd, cpuinfo, 4096);
		} while (cpuinfo_len < 0 && errno == EINTR);
		close(fd);
		if (cpuinfo_len > 0) {
			cpuinfo[cpuinfo_len] = 0;
			//look for the "\nFeatures: " line
			for (int i = cpuinfo_len - 9; i >= 0; i--) {
				if (memcmp(cpuinfo + i, "\nFeatures", 9) == 0) {
					i += 9;
					while (i < cpuinfo_len && (cpuinfo[i] == ' ' || cpuinfo[i] == '\t' || cpuinfo[i] == ':'))
						i++;
					cpuinfo_len -= 5;
					//now look for the " neon" feature
					while (i <= cpuinfo_len && cpuinfo[i] != '\n') {
						if (memcmp(cpuinfo + i, " neon", 5) == 0 ||
							memcmp(cpuinfo + i, "\tneon", 5) == 0) {
							neonMode = 1;
							break;
						}
						i++;
					}
					break;
				}
			}
			// __android_log_print(ANDROID_LOG_INFO, "JNI", "Neon mode: %d", neonMode);
		}
	}
	return neonMode;
#else
	return 0;
#endif
}

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_terminate(JNIEnv* env, jclass clazz) {
	if (voice) {
		free(voice);
		voice = 0;
	}
#ifdef __ARM_NEON__
	neonMode = 0;
	if (computedBars) {
		free(computedBars);
		computedBars = 0;
	}
	computedBarsWidth = -1;
#endif
}

JNIEXPORT int JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_prepareSurface(JNIEnv* env, jclass clazz, jobject surface) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return -1;
	int ret = -2;
	int w = ANativeWindow_getWidth(wnd), h = ANativeWindow_getHeight(wnd);
	if (w > 0 && h > 0) {
		barW = w >> 8;
		barH = h & (~1); //make the height always an even number
		if (barW < 1)
			barW = 1;
		const int size = barW << 8;
		invBarW = 1.0f / (float)barW;
		barBins = ((size > w) ? ((w < 256) ? (w & ~7) : 256) : 256);
		barWidthInPixels = barBins * barW;
		recreateVoice = 1;
		ret = ANativeWindow_setBuffersGeometry(wnd, barWidthInPixels, barH, WINDOW_FORMAT_RGB_565);
	}
	if (ret < 0) {
		invBarW = 1;
		barBins = 0;
		barWidthInPixels = 0;
		recreateVoice = 0;
	}
	ANativeWindow_release(wnd);
	return ret;
}

#ifdef __ARM_NEON__
JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_processSimple(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface, jboolean lerp) {
#else
JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_process(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface, jboolean lerp) {
#endif
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return;
	ANativeWindow_Buffer inf;
	if (ANativeWindow_lock(wnd, &inf, 0) < 0) {
		ANativeWindow_release(wnd);
		return;
	}
	if (inf.width != barWidthInPixels ||
		inf.height != barH) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to unsigned short
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetByteArrayElements(jbfft, 0);
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = bfft[0];
	float previous = 0;
	
	for (int i = 0; i < barBins; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im))));
		const float old = fft[i];
		if (m < old)
			m = (0.25f * m) + (0.75f * old);
			//m = (0.28125f * m) + (0.71875f * old);
		fft[i] = m;
		
		if (barW == 1 || !lerp) {
			//v goes from 0 to 32768 (inclusive)
			int v = (int)m;
			if (v < 0)
				v = 0;
			else if (v > 32768)
				v = 32768;
			
			const unsigned short color = COLORS[v >> 7];
			v = ((v * barH) >> 15);
			int v2 = v;
			v = (barH >> 1) - (v >> 1);
			v2 += v;
			unsigned short* currentBar = (unsigned short*)inf.bits;
			inf.bits = (void*)((unsigned short*)inf.bits + barW);
			
			int y = 0;
			switch (barW) {
			case 1:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			case 2:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			case 3:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar[2] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			case 4:
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar[3] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar[1] = color;
					currentBar[2] = color;
					currentBar[3] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar[1] = bgColor;
					currentBar[2] = bgColor;
					currentBar[3] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			default:
				for (; y < v; y++) {
					for (int b = barW - 1; b >= 0; b--)
						currentBar[b] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					for (int b = barW - 1; b >= 0; b--)
						currentBar[b] = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					for (int b = barW - 1; b >= 0; b--)
						currentBar[b] = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				break;
			}
		} else {
			const float delta = (int)(m - previous) * invBarW;
			for (int i = 0; i < barW; i++) {
				previous += delta;
				/*if (previous < 0.0f)
					previous = 0.0f;
				else if (previous > 32768.0f)
					previous = 32768.0f;*/
				
				//v goes from 0 to 32768 (inclusive)
				int v = (int)previous;
				if (v < 0)
					v = 0;
				else if (v > 32768)
					v = 32768;
				
				const unsigned short color = COLORS[v >> 7];
				v = ((v * barH) >> 15);
				int v2 = v;
				v = (barH >> 1) - (v >> 1);
				v2 += v;
				unsigned short* currentBar = (unsigned short*)inf.bits;
				inf.bits = (void*)((unsigned short*)inf.bits + 1);
				
				int y = 0;
				for (; y < v; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < v2; y++) {
					*currentBar = color;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
				for (; y < barH; y++) {
					*currentBar = bgColor;
					currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
				}
			}
		}
	}
	ANativeWindow_unlockAndPost(wnd);
	env->ReleaseByteArrayElements(jbfft, (jbyte*)bfft, JNI_ABORT);
	ANativeWindow_release(wnd);
}

#ifdef __ARM_NEON__
static const int __0[] __attribute__((aligned(16))) = { 0, 0, 0, 0 };
static const int __32768[] __attribute__((aligned(16))) = { 32768, 32768, 32768, 32768 };
static int __tmp[4] __attribute__((aligned(16)));
static int __v[4] __attribute__((aligned(16)));
static int __v2[4] __attribute__((aligned(16)));
JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_processNeon(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface, jboolean lerp) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return;
	ANativeWindow_Buffer inf;
	if (ANativeWindow_lock(wnd, &inf, 0) < 0) {
		ANativeWindow_release(wnd);
		return;
	}
	if (inf.width != barWidthInPixels ||
		inf.height != barH) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to unsigned short
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetByteArrayElements(jbfft, 0);
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = bfft[0];
	
	//step 1: compute all magnitudes
	for (int i = barBins - 1; i >= 0; i--) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im))));
		const float old = fft[i];
		if (m < old)
			m = (0.25f * m) + (0.75f * old);
			//m = (0.28125f * m) + (0.71875f * old);
		fft[i] = m;
	}
	
	if (barW == 1 || !lerp) {
		int32x4_t _0 = vld1q_s32(__0), _32768 = vld1q_s32(__32768), _barH = { barH, barH, barH, barH }, _barH2 = vshrq_n_s32(_barH, 1);
		for (int i = 0; i < barBins; i += 4) {
			//_v goes from 0 to 32768 (inclusive)
			int32x4_t _v = vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(vld1q_f32(fft + i))));
			vst1q_s32(__tmp, vshrq_n_s32(_v, 7));
			_v = vshrq_n_s32(vmulq_s32(_v, _barH), 15);
			int32x4_t _v2 = _v;
			_v = vsubq_s32(_barH2, vshrq_n_s32(_v, 1));
			vst1q_s32(__v, _v);
			vst1q_s32(__v2, vaddq_s32(_v2, _v));
			for (int j = 0; j < 4; j++) {
				const unsigned short color = COLORS_GREEN[__tmp[j]];
				const int v = __v[j];
				const int v2 = __v2[j];
				unsigned short* currentBar = (unsigned short*)inf.bits;
				inf.bits = (void*)((unsigned short*)inf.bits + barW);
				
				int y = 0;
				switch (barW) {
				case 1:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 2:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 3:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar[2] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				case 4:
					for (; y < v; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar[3] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						*currentBar = color;
						currentBar[1] = color;
						currentBar[2] = color;
						currentBar[3] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						*currentBar = bgColor;
						currentBar[1] = bgColor;
						currentBar[2] = bgColor;
						currentBar[3] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				default:
					for (; y < v; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < v2; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = color;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					for (; y < barH; y++) {
						for (int b = barW - 1; b >= 0; b--)
							currentBar[b] = bgColor;
						currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
					}
					break;
				}
			}
		}
	} else {
		if (!computedBars || computedBarsWidth < barWidthInPixels) {
			computedBarsWidth = barWidthInPixels;
			if (computedBars)
				free(computedBars);
			computedBars = (int*)malloc((barWidthInPixels << 2) + 16);
			unsigned char* al = (unsigned char*)computedBars;
			while (((unsigned int)al) & 15)
				al++;
			alignedComputedBars = (int*)al;
		}
		float32x4_t _invBarW = { invBarW, invBarW, invBarW, invBarW }, _prev = { 0.0f, fft[0], fft[1], fft[2] };
		int32x4_t _0 = vld1q_s32(__0), _32768 = vld1q_s32(__32768);
		const int barW2 = barW << 1, barW3 = barW2 + barW;
		int c;
		for (int i = 0; i < barBins; i += 4) {
			if (i) {
				//alignment issues.... :( (must be tested...)
				((float*)__tmp)[0] = fft[i - 1];
				((float*)__tmp)[1] = fft[i];
				((float*)__tmp)[2] = fft[i + 1];
				((float*)__tmp)[3] = fft[i + 2];
				_prev = vld1q_f32((float*)__tmp);
			}
			c = barW * i;
			vst1q_s32(__tmp, vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(_prev))));
			alignedComputedBars[c] = __tmp[0];
			alignedComputedBars[c + barW] = __tmp[1];
			alignedComputedBars[c + barW2] = __tmp[2];
			alignedComputedBars[c + barW3] = __tmp[3];
			c++;
			
			float32x4_t _next = vld1q_f32(fft + i);
			_next = vmulq_f32(vsubq_f32(_next, _prev), _invBarW); //_next is now delta
			for (int b = 1; b < barW; b++) {
				_prev = vaddq_f32(_prev, _next);
				vst1q_s32(__tmp, vminq_s32(_32768, vmaxq_s32(_0, vcvtq_s32_f32(_prev))));
				alignedComputedBars[c] = __tmp[0];
				alignedComputedBars[c + barW] = __tmp[1];
				alignedComputedBars[c + barW2] = __tmp[2];
				alignedComputedBars[c + barW3] = __tmp[3];
				c++;
			}
		}
		for (int i = 0; i < barWidthInPixels; i++) {
			//v goes from 0 to 32768 (inclusive)
			int v = alignedComputedBars[i];
			const unsigned short color = COLORS_GREEN[v >> 7];
			v = ((v * barH) >> 15);
			int v2 = v;
			v = (barH >> 1) - (v >> 1);
			v2 += v;
			unsigned short* currentBar = (unsigned short*)inf.bits;
			inf.bits = (void*)((unsigned short*)inf.bits + 1);
			
			int y = 0;
			for (; y < v; y++) {
				*currentBar = bgColor;
				currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
			}
			for (; y < v2; y++) {
				*currentBar = color;
				currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
			}
			for (; y < barH; y++) {
				*currentBar = bgColor;
				currentBar = (unsigned short*)((unsigned char*)currentBar + inf.stride);
			}
		}
	}
	ANativeWindow_unlockAndPost(wnd);
	env->ReleaseByteArrayElements(jbfft, (jbyte*)bfft, JNI_ABORT);
	ANativeWindow_release(wnd);
}

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_process(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface, jboolean lerp) {
	if (neonMode)
		Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_processNeon(env, clazz, jbfft, surface, lerp);
	else
		Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_processSimple(env, clazz, jbfft, surface, lerp);
}
#endif

JNIEXPORT void JNICALL Java_br_com_carlosrafaelgn_fplay_visualizer_SimpleVisualizerJni_processVoice(JNIEnv* env, jclass clazz, jbyteArray jbfft, jobject surface) {
	ANativeWindow* wnd = ANativeWindow_fromSurface(env, surface);
	if (!wnd)
		return;
	ANativeWindow_Buffer inf;
	if (ANativeWindow_lock(wnd, &inf, 0) < 0) {
		ANativeWindow_release(wnd);
		return;
	}
	if (inf.width != barWidthInPixels ||
		inf.height != barH) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	if (recreateVoice) {
		if (voice)
			free(voice);
		voice = (unsigned short*)malloc(((inf.stride * inf.height) << 1) + 16);
		unsigned char* al = (unsigned char*)voice;
		while (((unsigned int)al) & 15)
			al++;
		alignedVoice = (unsigned short*)al;
		recreateVoice = 0;
	}
	if (!voice) {
		ANativeWindow_unlockAndPost(wnd);
		ANativeWindow_release(wnd);
		return;
	}
	inf.stride <<= 1; //convert from pixels to unsigned short
	
	int v = inf.stride * (inf.height - 1);
	memcpy(alignedVoice, (unsigned char*)alignedVoice + inf.stride, v);
	unsigned short* currentBar = (unsigned short*)((unsigned char*)alignedVoice + v);
	
	float* const fft = floatBuffer;
	const float* const multiplier = fft + 256;
	//fft format:
	//index  0   1    2  3  4  5  ..... n-2        n-1
	//       Rdc Rnyq R1 I1 R2 I2       R(n-1)/2  I(n-1)/2
	signed char* const bfft = (signed char*)env->GetByteArrayElements(jbfft, 0);
	//*** we are not drawing/analyzing the last bin (Nyquist) ;) ***
	bfft[1] = bfft[0];
	float previous = 0;
	
	for (int i = 0; i < barBins; i++) {
		//bfft[i] stores values from 0 to -128/127 (inclusive)
		const int re = (int)bfft[i << 1];
		const int im = (int)bfft[(i << 1) + 1];
		const float m = (multiplier[i] * sqrtf((float)((re * re) + (im * im))));
		if (barW == 1) {
			//v goes from 0 to 32768 (inclusive)
			const int v = (int)m;
			*currentBar = COLORS_GREEN[(v <= 0) ? 0 : ((v >= 256) ? 256 : v)];
			currentBar++;
		} else {
			const float delta = (m - previous) * invBarW;
			for (int i = 0; i < barW; i++) {
				previous += delta;
				const int v = (int)m;
				/*if (v < 0) {
					v = 0;
					//previous = 0.0f;
				} else if (v > 256) {
					v = 256;
					//previous = 256.0f;
				}*/
				//*currentBar = COLORS_GREEN[v];
				*currentBar = COLORS_GREEN[(v <= 0) ? 0 : ((v >= 256) ? 256 : v)];
				currentBar++;
			}
		}
	}
	memcpy(inf.bits, alignedVoice, inf.stride * inf.height);
	ANativeWindow_unlockAndPost(wnd);
	env->ReleaseByteArrayElements(jbfft, (jbyte*)bfft, JNI_ABORT);
	ANativeWindow_release(wnd);
}

}
