#include $(call all-subdir-makefiles)
LOCAL_PATH := $(call my-dir)

#opencv
include $(CLEAR_VARS)
OPENCVROOT:= C:/Users/chla01/development/tools/opencv-3.2.0-android-sdk/OpenCV-android-sdk #TODO adapt to your needs
OPENCV_CAMERA_MODULES:=off
OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED
include C:/Users/chla01/development/tools/opencv-3.2.0-android-sdk/OpenCV-android-sdk/sdk/native/jni/OpenCV.mk #TODO adapt to your needs



LOCAL_SRC_FILES := eyetracker.cpp

LOCAL_LDLIBS += -llog -ljnigraphics #-ldl
LOCAL_MODULE := heyebrid

include $(BUILD_SHARED_LIBRARY)
