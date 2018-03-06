#include <stdio.h>
#include <ctime>
#include <eyetracker.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <assert.h>

#include <vector>


#include <cstring>
#include <unistd.h>
#include <math.h>

#include <Eigen/Geometry>
#include <Eigen/Core>

//#include "detect_2d.hpp"
//#include "singleeyefitter/common/types.h"
//#include "singleeyefitter/Geometry/Ellipse.h"
#include "algo.h"

const double PI = std::acos(-1);

using namespace std;
using namespace cv;
//using namespace singleeyefitter;

#define  LOG_TAG    "hEYEbridNativeLog"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

// COLORS
const cv::Scalar BLUE(255, 0, 0), RED(0, 0, 255), GREEN(0, 255, 0), CYAN(255, 255, 0), MAGENTA(255, 0, 255), YELLOW(0, 255, 255), WHITE(255, 255, 255), BLACK(0, 0, 0);


//local fields for pupil tracking
//Mat ir_bgr;
RNG rng(12345);

// local fields for corneal imaging
//Mat corneal_bgr, corneal_cropped;
float scaleX, scaleY;

//pupil detector
//Detector2D* detector = new Detector2D();
//Detector2DProperties props = {23, 5, 160, 2, 5, 240, 40, 0.6, 1.1, 0.8, 1.1, 5, 0.09, 4.3, 0.5, 1.0, 3.0};
//Detector2DProperties props = {17, 3, 200, 3, 5, 150, 20, 0.8, 1.1, 0.6, 1.1, 5, 0.1, 1.8, 0.6, 1.2, 3.0}; //{17, 3, 200, 3, 5, 150, 40, 0.8, 1.1, 0.6, 1.1, 5, 0.1, 1.8, 0.6, 1.2, 3.0};
//latest {23, 5, 160, 2, 5, 240, 40, 0.6, 1.1, 0.8, 1.1, 5, 0.09, 4.3, 0.5, 1.0, 2.0}

// homography from IR image to Corneal image for the NEW DEVICE
float homography_data[10] = {0.9411758128003351, -0.1607495562741495, 18.38700995149708, 0.01117534941842861, 0.852291404417159, 1.947125564885389, -2.741177320155755e-06, -0.0007763603185282179, 1};//{4.258276997465864, -0.5302232716614563, -33.65806679866328, 0.3351775791843725, 3.603183903552681, -60.42467503656557, 0.0001722707804211404, -0.0006659274629936168, 0.9999999999999999};
Mat homography = Mat(3, 3, CV_32F, homography_data);
//TODO: adapt homography matrix values

template<typename Scalar>
Eigen::Matrix<Scalar, 2, 1> toEigen(const cv::Point2f& point)
{
    return Eigen::Matrix<Scalar, 2, 1>(static_cast<Scalar>(point.x),
                                       static_cast<Scalar>(point.y));
}

Mat ir_global;
/**
 * meethod to do pupil tracking using PUPIL framework's 2D tracking method, returns array compatible with pupil format
 * @param frame the ir camera frame
 * @return the rotated ellipse describing the pupil
 */
pair<RotatedRect, float> pupilTracking(Mat frame) {
    Mat frame_gray, debugImg;
    cvtColor(frame, frame_gray, CV_BGR2GRAY);
    //Rect roi = Rect(0,0,frame_gray.cols,frame_gray.rows);

    //auto result = detector->detect(props, frame_gray, frame, debugImg, roi, false, false, false);

    //RotatedRect iris_ellipse = singleeyefitter::toRotatedRect(result->ellipse);
    RotatedRect iris_ellipse = ELSE::run(frame_gray);

    //return make_pair(iris_ellipse, Point2f((float)result->confidence, (float)result->timestamp));
    return make_pair(iris_ellipse, 0.99);
}

//template<typename Scalar>
int getPupilEllipseInformation(RotatedRect rect, int w, int h, float *res) {
/*
    Ellipse2D<Scalar> ellipse = Ellipse2D<Scalar>(toEigen<Scalar>(rect.center),
                                                  static_cast<Scalar>(rect.size.height / 2.0),
                                                  static_cast<Scalar>(rect.size.width / 2.0),
                                                  static_cast<Scalar>((rect.angle + 90.0) * constants::PI / 180.0));*/

    int x = rect.center.x;
    int y = rect.center.y;
    float major_radius = rect.size.height / 2.0;
    float minor_radius = rect.size.width / 2.0;
    float angle = (rect.angle + 90.0) * PI / 180.0;

    // 0 = 'confidence', 1,2 = 'ellipse_center', 3,4 = 'ellipse_axes', 5 = 'ellipse_angle', 6 = 'diameter', 7,8 = 'norm_pos'
    res[0] = 1.0f;

    res[1] = x;
    res[2] = y;
    res[3] = minor_radius * 2.0;
    res[4] = major_radius * 2.0;
    res[5] = angle * 180.0 / std::acos(-1) - 90.0;
    res[6] = fmax(minor_radius * 2.0, major_radius * 2.0);

    res[7] = x / w;
    res[8] = y / h;

/*
    res[1] = ellipse.center[0][0];
    res[2] = ellipse.center[1][0];
    res[3] = ellipse.minor_radius[0] * 2.0;
    res[4] = ellipse.major_radius[0] * 2.0;
    res[5] = ellipse.angle[0] * 180.0 / std::acos(-1) - 90.0;
    res[6] = fmax(ellipse.minor_radius[0] * 2.0, ellipse.major_radius[0] * 2.0);

    res[7] = ellipse.center[0][0] / w;
    res[8] = ellipse.center[1][0] / h;
*/
    return 0;
}

pair<Rect, Point2f> mapEllipseToCorneal(RotatedRect pupilEllipse, Mat frame, double scale) {
    //Rect bRect = pupilEllipse.boundingRect();

    //new method to compute the smallest bounding rect --> https://stackoverflow.com/questions/32920419/opencv-minimum-upright-bounding-rect-of-a-rotatedrect
    float degree = pupilEllipse.angle*3.1415/180;
    float majorAxe = pupilEllipse.size.width/2;
    float minorAxe = pupilEllipse.size.height/2;
    float x = pupilEllipse.center.x;
    float y = pupilEllipse.center.y;
    float c_degree = cos(degree);
    float s_degree = sin(degree);
    float t1 = atan(-(majorAxe*s_degree)/(minorAxe*c_degree));
    float c_t1 = cos(t1);
    float s_t1 = sin(t1);
    float w1 = majorAxe*c_t1*c_degree;
    float w2 = minorAxe*s_t1*s_degree;
    float maxX = x + w1-w2;
    float minX = x - w1+w2;

    t1 = atan((minorAxe*c_degree)/(majorAxe*s_degree));
    c_t1 = cos(t1);
    s_t1 = sin(t1);
    w1 = minorAxe*s_t1*c_degree;
    w2 = majorAxe*c_t1*s_degree;
    float maxY = y + w1+w2;
    float minY = y - w1-w2;
    if (minY > maxY)
    {
        float temp = minY;
        minY = maxY;
        maxY = temp;
    }
    if (minX > maxX)
    {
        float temp = minX;
        minX = maxX;
        maxX = temp;
    }
    Rect bRect(minX,minY,maxX-minX+1,maxY-minY+1);


    vector <Point2f> orig_ellipse;
    orig_ellipse.push_back(bRect.tl());
    orig_ellipse.push_back(bRect.br());

    vector <Point2f> new_ellipse;
    perspectiveTransform(orig_ellipse, new_ellipse, homography);

    new_ellipse.at(0).x *= scale;
    new_ellipse.at(0).y *= scale;
    new_ellipse.at(1).x *= scale;
    new_ellipse.at(1).y *= scale;

    //bug fix --> should not happen
    if (new_ellipse.at(0).x < 0.0) new_ellipse.at(0).x = 0.0;
    if (new_ellipse.at(0).y < 0.0) new_ellipse.at(0).y = 0.0;
    if (new_ellipse.at(1).x < 0.0) new_ellipse.at(1).x = 0.0;
    if (new_ellipse.at(1).y < 0.0) new_ellipse.at(1).y = 0.0;

    /*
    LOGD("transformation completed:%i, %i, %i, %i", (int) new_ellipse.at(0).x,
         (int) new_ellipse.at(0).y, (int) new_ellipse.at(1).x,
         (int) new_ellipse.at(1).y);*/

    Rect pupilCorneal = Rect(new_ellipse.at(0), new_ellipse.at(1));

    //transform the center
    vector <Point2f> orig_center;
    orig_center.push_back(pupilEllipse.center);
    vector <Point2f> new_center;
    perspectiveTransform(orig_center, new_center, homography);
    //LOGD("center transform: %f, %f", new_center[0].x, new_center[0].y);
    //LOGD("center transform: %f, %f", new_center[0].x - pupilCorneal.tl().x, new_center[0].y - pupilCorneal.tl().y);

    //Drawing
    //rectangle(ir_bgr, bRect, BLUE, 4);
    //rectangle(ir_bgr, yellowrect, YELLOW, 4);
    //rectangle(corneal_bgr, pupilCorneal, BLUE, 4);
    //circle(corneal_bgr, Point((int) new_center[0].x, (int) new_center[0].y), 5, BLUE, -1);

    //Point2f center_translated(new_center[0].x - (float)pupilCorneal.tl().x, new_center[0].y - (float)pupilCorneal.tl().y);

    //vector <Point2f> centers;
    //centers.push_back(new_center[0]);
    //centers.push_back(center_translated);

    return make_pair(pupilCorneal, Point2f(new_center[0].x * scale, new_center[0].y * scale));
}

/**
 * method to track the pupil using PUpils 2D tracking method and maping the found ellipse onto the corneal image
 */
JNIEXPORT jfloatArray JNICALL Java_org_lander_eyemirror_Processing_NativeInterface_doPupilTracking(JNIEnv *env, jobject thiz, jlong inputIR) {
    //create return array containing the pupil center and the size of the cropped image
    jfloatArray res = env->NewFloatArray(9);
    jfloat tmp[9];
    tmp[0] = 0;
    tmp[1] = 0;
    tmp[2] = 0;
    tmp[3] = 0;
    tmp[4] = 0;
    tmp[5] = 0;
    tmp[6] = 0;
    tmp[7] = 0;
    tmp[8] = 0;

    //ir and corneal images as BGR
    Mat ir_bgr;

    //get infrared cam image
    if (inputIR) {
        Mat& inIR  = *(Mat*)inputIR;
        cvtColor(inIR, ir_bgr, CV_RGBA2BGR);

        //track the pupil by finding an ellipse
        pair<RotatedRect, float> pupilData = pupilTracking(ir_bgr);
        RotatedRect pupilEllipse = pupilData.first;
        float conf = pupilData.second;

        //check if we found the pupil
        if (pupilEllipse.center.x > 0 && pupilEllipse.center.y > 0) {
            //tmp[0] = pupilEllipse.center.x;
            //tmp[1] = pupilEllipse.center.y;

            float tmp2[9] = {0};
            getPupilEllipseInformation(pupilEllipse, ir_bgr.cols, ir_bgr.rows, tmp2);
            tmp[0] = conf;
            tmp[1] = tmp2[1];
            tmp[2] = tmp2[2];
            tmp[3] = tmp2[3];
            tmp[4] = tmp2[4];
            tmp[5] = tmp2[5];
            tmp[6] = tmp2[6];
            tmp[7] = tmp2[7];
            tmp[8] = tmp2[8];


            // debug drawing
            ellipse(ir_bgr, pupilEllipse, RED, 1);
            circle(ir_bgr, Point((int) pupilEllipse.center.x, (int) pupilEllipse.center.y), 3, RED, -1);

            cvtColor(ir_bgr, inIR, CV_BGR2RGBA);
        }
    }

    env->SetFloatArrayRegion(res, 0, 9, tmp);
    return res;
}



/**
 * method to track the pupil using PUpils 2D tracking method and maping the found ellipse onto the corneal image
 */
JNIEXPORT jfloatArray JNICALL Java_org_lander_eyemirror_Processing_NativeInterface_doHybridEyeTracking(JNIEnv *env, jobject thiz, jlong inputIR, jlong inputCorneal, jdouble scaleFactor) {
    //create return array containing the pupil center and the size of the cropped image
    jfloatArray res = env->NewFloatArray(15);
    jfloat tmp[15];
    tmp[0] = 0;
    tmp[1] = 0;
    tmp[2] = 0;
    tmp[3] = 0;
    tmp[4] = 0;
    tmp[5] = 0;
    tmp[6] = 0;
    tmp[7] = 0;
    tmp[8] = 0;
    tmp[9] = 0;
    tmp[10] = 0;
    tmp[11] = 0;
    tmp[12] = 0;
    tmp[13] = 0;
    tmp[14] = 0;

    //ir and corneal images as BGR
    Mat ir_bgr, corneal_bgr;

    //get infrared cam image
    if (inputIR) {
        Mat& inIR  = *(Mat*)inputIR;
        cvtColor(inIR, ir_bgr, CV_RGBA2BGR);

        //track the pupil by finding an ellipse
        pair<RotatedRect, float> pupilData = pupilTracking(ir_bgr);
        RotatedRect pupilEllipse = pupilData.first;
        float conf = pupilData.second;

        //check if we found the pupil
        if (pupilEllipse.center.x > 0 && pupilEllipse.center.y > 0 && conf >= 0.9) {

            float tmp2[9] = {0};
            getPupilEllipseInformation(pupilEllipse, ir_bgr.cols, ir_bgr.rows, tmp2);

            tmp[0] = conf;
            tmp[1] = tmp2[1];
            tmp[2] = tmp2[2];
            tmp[3] = tmp2[3];
            tmp[4] = tmp2[4];
            tmp[5] = tmp2[5];
            tmp[6] = tmp2[6];
            tmp[7] = tmp2[7];
            tmp[8] = tmp2[8];


            // debug drawing
            ellipse(ir_bgr, pupilEllipse, RED, 1);
            circle(ir_bgr, Point((int) pupilEllipse.center.x, (int) pupilEllipse.center.y), 3, RED, -1);


            //use the computed ellipse for the mapping and cropping
            if (inputCorneal) {

                //transform the ellipse
                pair<Rect, Point2f> mappingResult = mapEllipseToCorneal(pupilEllipse, corneal_bgr, scaleFactor);
                Point2f pupilCorneal = mappingResult.second;
                Rect pupilCornealRect = mappingResult.first;

                //set size of transformed ellipse as array
                tmp[9] = pupilCorneal.x;
                tmp[10] = pupilCorneal.y;
                tmp[11] = 1.0 * pupilCornealRect.width;
                tmp[12] = 1.0 * pupilCornealRect.height;
                tmp[13] = 1.0 * pupilCornealRect.tl().x;
                tmp[14] = 1.0 * pupilCornealRect.tl().y;


                //get corneal camera image
                if (inputCorneal) {
                    Mat &inCorneal = *(Mat *) inputCorneal;
                    cvtColor(inCorneal, corneal_bgr, CV_RGBA2BGR);
                    //LOGD("cropping");

                    if (pupilCornealRect.x <0) {
                        pupilCornealRect.x = 0;
                    }

                    if (pupilCornealRect.y <0) {
                        pupilCornealRect.y = 0;
                    }

                    if (pupilCornealRect.width + pupilCornealRect.x > corneal_bgr.cols) {
                        pupilCornealRect.width = corneal_bgr.cols - pupilCornealRect.x - 1;
                    }

                    if (pupilCornealRect.height + pupilCornealRect.y > corneal_bgr.rows) {
                        pupilCornealRect.height = corneal_bgr.rows - pupilCornealRect.y - 1;
                    }

                    //Draw cross hair
                    //line(corneal_bgr, Point(pupilCorneal.x - 10, pupilCorneal.y), Point(pupilCorneal.x + 10, pupilCorneal.y), Scalar( 0, 0, 255 ),  2, 4 );
                    //line(corneal_bgr, Point(pupilCorneal.x, pupilCorneal.y - 10), Point(pupilCorneal.x, pupilCorneal.y + 10), Scalar( 0, 0, 255 ),  2, 4 );
                    //LOGD("drawing: %f, %f", pupilCorneal.x, pupilCorneal.y);

                    //crop the image
                    Mat corneal_cropped;
                    flip(corneal_bgr(pupilCornealRect).clone(), corneal_cropped, 1);
                    cvtColor(corneal_cropped, inCorneal, CV_BGR2RGBA);
                    //LOGD("cropping done");
                }
            }

            cvtColor(ir_bgr, inIR, CV_BGR2RGBA);
        }
    }

    env->SetFloatArrayRegion(res, 0, 15, tmp);
    return res;
}

/*
JNIEXPORT void JNICALL Java_org_lander_eyemirror_NativeInterface_getCroppedCornealImg(
        JNIEnv *env,
        jobject thiz, jlong output) {
    Mat& outCorneal = *(Mat*)output;
    cvtColor(corneal_cropped, outCorneal, CV_BGR2RGBA);
}

JNIEXPORT void JNICALL Java_org_lander_eyemirror_NativeInterface_getDebugImages(
        JNIEnv *env,
        jobject thiz, jlong outputIR, jlong outputCorneal) {
    Mat& outIR = *(Mat*)outputIR;
    cvtColor(ir_bgr, outIR, CV_BGR2RGBA);

    Mat& outCorneal = *(Mat*)outputCorneal;
    cvtColor(corneal_bgr, outCorneal, CV_BGR2RGBA);
}

 //method for passing bitmaps to NDK
JNIEXPORT void JNICALL Java_org_lander_eyemirror_NativeInterface_passEyeImages(JNIEnv *env, jobject thiz, jlong inputIR, jlong inputCorneal) {
    //get infrared cam image
    Mat& inIR  = *(Mat*)inputIR;
    Size irSize = inIR.size();

    //scale it for faster processing
    Mat smallIR;
    //TODO: not needed!
    //resize(inIR, smallIR, Size(irSize.width / 3, irSize.height / 3));
    cvtColor(inIR, ir_bgr, CV_RGBA2BGR);

    //get corneal image
    Mat& inCorneal = *(Mat*)inputCorneal;
    //Size cornealSize = inCorneal.size();
    cvtColor(inCorneal, corneal_bgr, CV_RGBA2BGR);
}


//method for passing bitmaps to NDK
JNIEXPORT void JNICALL Java_org_lander_eyemirror_NativeInterface_passIRImage(JNIEnv *env, jobject thiz, jlong inputIR) {
    //get infrared cam image
    Mat& inIR  = *(Mat*)inputIR;
    Size irSize = inIR.size();
    cvtColor(inIR, ir_bgr, CV_RGBA2BGR);
}
*/