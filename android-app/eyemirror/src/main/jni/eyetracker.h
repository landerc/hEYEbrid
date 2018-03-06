
#include <jni.h>
#include <string>

#include <opencv2/opencv.hpp>
#include <opencv2/objdetect/objdetect.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d.hpp>

#ifndef UVCCAMERA_MASTER_EYETRACKER_H_H
#define UVCCAMERA_MASTER_EYETRACKER_H_H

#ifdef __cplusplus
extern "C" {
#endif

//std::pair<cv::RotatedRect, jfloat> pupilTracking(cv::Mat frame);
std::pair<cv::Rect, cv::Point2f> mapEllipseToCorneal(cv::RotatedRect pupilEllipse, cv::Mat frame);

JNIEXPORT jfloatArray JNICALL Java_org_lander_eyemirror_Processing_NativeInterface_doHybridEyeTracking(JNIEnv *env, jobject thiz, jlong inputIR, jlong inputCorneal, jdouble scaleFactor);
JNIEXPORT jfloatArray JNICALL Java_org_lander_eyemirror_Processing_NativeInterface_doPupilTracking(JNIEnv *env, jobject thiz, jlong inputIR);

#ifdef __cplusplus
}
#endif

#endif //UVCCAMERA_MASTER_EYETRACKER_H_H
