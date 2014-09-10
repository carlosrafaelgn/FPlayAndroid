LOCAL_PATH      := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := SimpleVisualizerJni
LOCAL_SRC_FILES := SimpleVisualizerJni.cpp.neon
LOCAL_LDLIBS    := -landroid -llog -lGLESv2
LOCAL_ARM_NEON  := true

TARGET_CFLAGS   += -mhard-float -D_NDK_MATH_NO_SOFTFP=1
TARGET_LDFLAGS  += -Wl,-lm_hard

include $(BUILD_SHARED_LIBRARY)
