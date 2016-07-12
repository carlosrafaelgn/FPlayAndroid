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
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := MediaContextJni
LOCAL_LDLIBS    := -llog -lOpenSLES -ldl
LOCAL_SRC_FILES := $(LOCAL_PATH)/x/MediaContextJni.cpp
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_SRC_FILES += $(LOCAL_PATH)/x/MediaContextJniNeon.cpp.neon
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
	LOCAL_SRC_FILES += $(LOCAL_PATH)/x/MediaContextJniNeon.cpp
endif
ifeq ($(TARGET_ARCH_ABI),x86_64)
	LOCAL_SRC_FILES += $(LOCAL_PATH)/x/MediaContextJniSSE41.cpp
endif
include $(BUILD_SHARED_LIBRARY)
