LOCAL_PATH      := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := SimpleVisualizerJni
LOCAL_LDLIBS    := -landroid -llog -lGLESv2

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a-hard)
	LOCAL_SRC_FILES := SimpleVisualizerJni.cpp.neon
	LOCAL_ARM_NEON  := true
else
	LOCAL_SRC_FILES := SimpleVisualizerJni.cpp
endif

include $(BUILD_SHARED_LIBRARY)
