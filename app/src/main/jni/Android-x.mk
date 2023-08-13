LOCAL_PATH      := $(call my-dir)

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
# Make sure we compile a library with position independent code (no text relocations)
LOCALC_FLAGS    += -fPIC
include $(BUILD_SHARED_LIBRARY)
