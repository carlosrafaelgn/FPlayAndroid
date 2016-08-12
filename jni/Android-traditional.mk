LOCAL_PATH      := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := SimpleVisualizerJni
LOCAL_LDLIBS    := -landroid -ljnigraphics -llog -lGLESv2
LOCAL_SRC_FILES := SimpleVisualizerJni.cpp
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_SRC_FILES += NeonFunctions.cpp.neon
else
	# x86 Support for ARM NEON Intrinsics
	# x86 also uses this file -> https://developer.android.com/ndk/guides/x86.html
	LOCAL_SRC_FILES += NeonFunctions.cpp
endif
# Make sure we compile a library with position independent code (no text relocations)
LOCALC_FLAGS    += -fPIC
include $(BUILD_SHARED_LIBRARY)
