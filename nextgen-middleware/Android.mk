LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libmiddleware

LOCAL_SRC_FILES += \
    $(LOCAL_PATH)/src/main/cpp/middleware.cpp 

include $(BUILD_SHARED_LIBRARY)

