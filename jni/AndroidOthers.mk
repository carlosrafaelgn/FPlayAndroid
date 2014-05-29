LOCAL_PATH      := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := SimpleVisualizerJni
LOCAL_SRC_FILES := SimpleVisualizerJni.cpp
LOCAL_LDLIBS    := -landroid

include $(BUILD_SHARED_LIBRARY)
