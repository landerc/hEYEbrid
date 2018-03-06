#include <opencv2/opencv.hpp>
#include <opencv2/features2d.hpp>
#include <vector>
#include <iostream>
#include <opencv2/xfeatures2d.hpp>

using namespace std;
using namespace cv;

#include "stats.h" // Stats structure definition
#include "utils.h" // Drawing and printing functions

const double akaze_thresh = 3e-4; // AKAZE detection threshold set to locate about 1000 keypoints
const double ransac_thresh = 2.5f; // RANSAC inlier threshold
const double nn_match_ratio = 0.8f; // Nearest-neighbour matching ratio
const int bb_min_inliers = 5; // Minimal number of inliers to draw bounding box
const int stats_update_period = 10; // On-screen statistics are updated every 10 frames

class Tracker
{
public:
    Tracker(Ptr<Feature2D> _detector, Ptr<DescriptorMatcher> _matcher) :
        detector(_detector),
        matcher(_matcher)
    {}
    void setFirstFrame(const Mat frame, vector<Point2f> bb, string title, Stats& stats);
    Mat process(const Mat frame, Stats& stats);
    Ptr<Feature2D> getDetector() {
        return detector;
    }
protected:
    Ptr<Feature2D> detector;
    Ptr<DescriptorMatcher> matcher;
    Mat first_frame, first_desc;
    vector<KeyPoint> first_kp;
    vector<Point2f> object_bb;
};
void Tracker::setFirstFrame(const Mat frame, vector<Point2f> bb, string title, Stats& stats)
{
    first_frame = frame.clone();
    detector->detectAndCompute(first_frame, noArray(), first_kp, first_desc);
    vector<Point2f> bbpoints;
    KeyPoint::convert(first_kp, bbpoints);
    stats.keypoints = (int)first_kp.size();
    //drawBoundingBox(first_frame, bb);
    circle(first_frame, bb.at(0), 5, Scalar(0,0,255), -1);
    //putText(first_frame, title, Point(0, 60), FONT_HERSHEY_PLAIN, 5, Scalar::all(0), 4);
    object_bb = bb;
}
Mat Tracker::process(const Mat frame, Stats& stats)
{
    vector<KeyPoint> kp;
    Mat desc;
    detector->detectAndCompute(frame, noArray(), kp, desc);
    cout << "detected" << endl;

    if(desc.type()!=CV_32F) {
      desc.convertTo(desc, CV_32F);
      cout << "converted" << endl;
    }

    if(first_desc.type()!=CV_32F) {
      first_desc.convertTo(first_desc, CV_32F);
      cout << "converted" << endl;
    }


    stats.keypoints = (int)kp.size();
    vector< vector<DMatch> > matches;
    vector<KeyPoint> matched1, matched2;
    cout << "before match" << endl;
    matcher->knnMatch(first_desc, desc, matches, 2);
    cout << "matched" << endl;
    for(unsigned i = 0; i < matches.size(); i++) {
        if(matches[i][0].distance < nn_match_ratio * matches[i][1].distance) {
            matched1.push_back(first_kp[matches[i][0].queryIdx]);
            matched2.push_back(      kp[matches[i][0].trainIdx]);
        }
    }
    stats.matches = (int)matched1.size();
    Mat inlier_mask, homography;
    vector<KeyPoint> inliers1, inliers2;
    vector<DMatch> inlier_matches;
    if(matched1.size() >= 4) {
        homography = findHomography(Points(matched1), Points(matched2),
                                    RANSAC, ransac_thresh, inlier_mask);
    }
    if(matched1.size() < 4 || homography.empty()) {
        cout << "homo empty" << endl;
        Mat res;
        hconcat(first_frame, frame, res);
        stats.inliers = 0;
        stats.ratio = 0;
        return res;
    }
    for(unsigned i = 0; i < matched1.size(); i++) {
        if(inlier_mask.at<uchar>(i)) {
            int new_i = static_cast<int>(inliers1.size());
            inliers1.push_back(matched1[i]);
            inliers2.push_back(matched2[i]);
            inlier_matches.push_back(DMatch(new_i, new_i, 0));
        }
    }
    stats.inliers = (int)inliers1.size();
    stats.ratio = stats.inliers * 1.0 / stats.matches;
    vector<Point2f> new_bb;
    cout << homography << endl;
    perspectiveTransform(object_bb, new_bb, homography);
    Mat frame_with_bb = frame.clone();
    if(stats.inliers >= bb_min_inliers) {
        //drawBoundingBox(frame_with_bb, new_bb);
        circle(frame_with_bb, new_bb.at(0), 8, Scalar(0,0,255), -1);
    }
    Mat res;
    drawMatches(first_frame, inliers1, frame_with_bb, inliers2,
                inlier_matches, res,
                Scalar(255, 0, 0), Scalar(255, 0, 0));
    return res;
}

int main(int argc, char *argv[])
{
  Mat img1 = imread(argv[1]);
  Mat img2 = imread(argv[2]);

  //eyeTracking(img1);

  cv::Ptr<Feature2D> f2d = xfeatures2d::SIFT::create();

  //Ptr<AKAZE> akaze = AKAZE::create();
  //akaze->setThreshold(akaze_thresh);

  //Ptr<ORB> orb = ORB::create();

  Ptr<DescriptorMatcher> matcher = DescriptorMatcher::create("BruteForce");

  Ptr<flann::IndexParams> indexParams = makePtr<flann::LshIndexParams>(6, 12, 1); // instantiate LSH index parameters
  Ptr<flann::SearchParams> searchParams = makePtr<flann::SearchParams>(50);       // instantiate flann search parameters

  // instantiate FlannBased matcher
  //Ptr<DescriptorMatcher> matcher2 = makePtr<FlannBasedMatcher>(indexParams, searchParams);

  Ptr<DescriptorMatcher> matcher2 = DescriptorMatcher::create(1);

  Stats stats1, stats2, akaze_stats, sift_stats;

  //Tracker akaze_tracker(akaze, matcher);
  Tracker sift_tracker(f2d, matcher);
  //Tracker orb_tracker(orb, matcher);

  vector<Point2f> bb;
  bb.push_back(Point2f(145.0,146.0));

  //akaze_tracker.setFirstFrame(img1, bb, "AKAZE", stats1);
  sift_tracker.setFirstFrame(img1, bb, "SIFT", stats2);

  Mat akaze_res, sift_res, res_frame;

  //akaze_res = akaze_tracker.process(img2, stats1);
  //akaze_stats += stats1;

  //cout << "here" << endl;
  sift_res = sift_tracker.process(img2, stats2);
  sift_stats += stats2;


  printStatistics("SIFT", sift_stats);

  //vconcat(akaze_res, res_frame);
  vconcat(sift_res, res_frame);

  //printStatistics("AKAZE", akaze_stats);

  namedWindow("Camera Calibration", WINDOW_AUTOSIZE);
  imshow("Camera Calibration", res_frame);

  waitKey();
}
