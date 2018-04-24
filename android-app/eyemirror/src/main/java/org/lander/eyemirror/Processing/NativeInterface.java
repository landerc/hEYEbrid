package org.lander.eyemirror.Processing;

import android.graphics.Bitmap;
import android.util.Log;

import org.lander.eyemirror.Utils.CornealPoint;
import org.lander.eyemirror.Utils.DataFrame;
import org.lander.eyemirror.Utils.GazePoint;
import org.lander.eyemirror.Utils.ImageData;
import org.lander.eyemirror.Utils.Mode;
import org.lander.eyemirror.Utils.PupilPoint;
import org.lander.eyemirror.activities.Heyebrid3CActivity;
import org.lander.eyemirror.activities.HeyebridActivity;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.AKAZE;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by christian on 25/01/2017.
 */
public class NativeInterface {


    public interface ProcessingCallback {
        public void onHybridEyeTracked(Bitmap ir, Bitmap corneal, DataFrame df);

        public void onSceneRegistered(Bitmap feature);

        public void onGazeMapped();
    }

    static {
        System.loadLibrary("heyebrid");
    }

    public native float[] doHybridEyeTracking(long inputIR, long inputCorneal, double scaleFactor);

    public native float[] doPupilTracking(long inputIR);

    /**
     * Feature Tracker
     */
    private Tracker mTracker;

    /**
     * callback for drawing and processing
     */
    private ProcessingCallback mCallback;

    /**
     * thread used for processing
     */
    private Thread mNativeThread;

    /**
     * current ir image
     */
    private Bitmap mIR;

    /**
     * current corneal image
     */
    private Bitmap mCorneal;

    /**
     * current scene image
     */
    private Bitmap mScene;

    /**
     * flag indicating if debug infromation should be drawn
     */
    private boolean drawDebug;

    /**
     * flag indicating if a homography matrix should be computed
     */
    private boolean doHomography;

    /**
     * object used for synchronization
     */
    private final Object mSyncNative = new Object();
    private final Object mSyncHomography = new Object();

    /**
     * current homography matrix used for calibration
     */
    private Mat mHomography;

    private ImageData irImageData, cornealImageData, worldImageData;


    public NativeInterface(ProcessingCallback callback, Mode which) {
        mCallback = callback;

        switch (which) {
            case HEYEBRID3C:
                //irImageData = new ConcurrentLinkedQueue<>();
                //cornealImageData = new ConcurrentLinkedQueue<>();
                //worldImageData = new ConcurrentLinkedQueue<>();

                //TODO:implement: do a homography computation onyl every 5 minutes or so, but do it until a homography is found
                //TODO: use the current homography to sample points and compute a calibration mapping function, ir pupil -> scene
                //TODO: update this function based on the homography
                mNativeThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!mNativeThread.isInterrupted()) {
                            Bitmap tmpIR;
                            Bitmap tmpCorneal = null;
                            Bitmap tmpScene = null;
                            boolean draw;
                            boolean homogr = false;
                            double irTS = 0;
                            double corTS = 0;
                            synchronized (mSyncNative) {
                                draw = drawDebug;
                                tmpIR = mIR.copy(mIR.getConfig(), mIR.isMutable());
                                tmpCorneal = mCorneal.copy(mCorneal.getConfig(), mCorneal.isMutable());
                                irTS = irImageData.timestamp;
                                corTS = cornealImageData.timestamp;

                                if (doHomography && mCorneal != null && mScene != null) {
                                    homogr = true;
                                    tmpScene = mScene.copy(mScene.getConfig(), mScene.isMutable());
                                }
                            }

                            hybridPlusEyeTracking(tmpIR, tmpCorneal, tmpScene, draw, homogr, irTS, corTS);
                        }

                    }
                });
                break;
            case HEYEBRID:
                //irImageData = new ConcurrentLinkedQueue<>();
                //cornealImageData = new ConcurrentLinkedQueue<>();

                mNativeThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!mNativeThread.isInterrupted()) {
                            Bitmap tmpIR;
                            Bitmap tmpCorneal;
                            boolean draw;
                            //double eps = 10000;
                            double irTS = 0;
                            double corTS = 0;
                            synchronized (mSyncNative) {
                                draw = drawDebug;
                                //ImageData ir = irImageData.poll();
                                //ImageData cor = cornealImageData.poll();
                                tmpIR = irImageData.bmp.copy(irImageData.bmp.getConfig(), irImageData.bmp.isMutable());
                                tmpCorneal = cornealImageData.bmp.copy(cornealImageData.bmp.getConfig(), cornealImageData.bmp.isMutable());
                                irTS = irImageData.timestamp;
                                corTS = cornealImageData.timestamp;
                            }

                            if (tmpIR != null && tmpCorneal != null) {

                                //eps = Math.abs(irTS - corTS);

                                //System.out.println("cor: " + corTS);
                                //System.out.println("ir: " + irTS);
                                //System.out.println("time diff: " + eps);
                                //if (tmpIR != null && tmpCorneal != null/* && eps < 20*/) {
                                hybridEyeTracking(tmpIR, tmpCorneal, draw, irTS, corTS);
                                //}
                            }


                            //System.out.println("time diff: " + Math.abs(ir.timestamp - cor.timestamp));
                            //}

                            //if (tmpIR != null && tmpCorneal != null && eps < 20) {
                            //   hybridEyeTracking(tmpIR, tmpCorneal, draw);
                            //}
                        }

                    }
                });
                break;
            case PUPIL:
                //irImageData = new ConcurrentLinkedQueue<>();

                mNativeThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!mNativeThread.isInterrupted()) {
                            Bitmap tmpIR;
                            boolean draw;
                            double irTS = 0;
                            double sceneTS = 0;
                            synchronized (mSyncNative) {
                                draw = drawDebug;
                                tmpIR = irImageData.bmp.copy(irImageData.bmp.getConfig(), irImageData.bmp.isMutable());
                                irTS = irImageData.timestamp;
                                sceneTS = worldImageData.timestamp;
                            }

                            if (tmpIR != null) {
                                pupilTracking(tmpIR, draw, irTS, sceneTS);
                            }
                        }
                    }
                });
                break;
            default:
                break;

        }


    }

    public void startNativeThread() {
        mNativeThread.start();
    }

    public void stopNativeThread() {
        mNativeThread.interrupt();
    }

    public void setIR(ImageData in) {
        /*
        synchronized (mSyncNative) {
            mIR = bmp;
        }
        */
        synchronized (mSyncNative) {
            irImageData = in;
        }
    }

    public void setCorneal(ImageData in) {
        /*
        synchronized (mSyncNative) {
            mCorneal = bmp;
        }
        */
        synchronized (mSyncNative) {
            cornealImageData = in; //.add(in);
        }
    }

    public void setScene(ImageData in) {
        synchronized (mSyncNative) {
            worldImageData = in;
        }
    }

    public void setDoHomography(boolean value) {
        synchronized (mSyncNative) {
            doHomography = value;
        }
    }

    public void setDrawDebug(boolean value) {
        synchronized (mSyncNative) {
            drawDebug = value;
        }
    }

    /**
     * Creates a AKAZE based feature mTracker
     */
    public void createAkazeTracker() {
        AKAZE akaze = AKAZE.create();
        akaze.setThreshold(3e-4);
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

        mTracker = new Tracker(akaze, matcher);
    }


    /**
     * method that maps current pupil points to appropriate gaze points
     */
    public GazePoint gazeMapping(CornealPoint pp) {
        if (pp != null) {
            MatOfPoint2f cp = pp.getCornealPupil();
            double[] aaa = cp.get(0, 0);
            Mat src = new Mat();
            cp.assignTo(src, CvType.CV_32FC2);
            //src.push_back(cp);

            double[] aaaa = src.get(0, 0);

            Mat dest = new Mat(1, 1, CvType.CV_32FC2);

            synchronized (mSyncHomography) {
                Core.perspectiveTransform(src, dest, mHomography);
            }

            int aa = dest.rows();
            int bb = dest.cols();

            double[] a = dest.get(0, 0);
            double[] b = dest.get(dest.rows() - 1, dest.cols() - 1);

            return new GazePoint(new Point(dest.get(0, 0)[0], dest.get(0, 0)[1]));

        } else {
            return null;
        }
    }

    public void calibrate() {
        setDoHomography(true);
    }


    /**
     * Computes a homography matrix between two given images
     *
     * @param corneal  - corenal image
     * @param observed - observed image
     * @return the homography matrix and the debug image
     */
    private Mat[] computeHomography(Mat corneal, Mat observed, boolean debug) {
        mTracker.setFirstFrame(corneal);
        return mTracker.process(observed, debug);
    }

    public void pupilTracking(Bitmap irFrame, boolean debug, double irTS, double corTS) {
        if (irFrame == null) {
            return;
        }

        Mat irRGBA = new Mat(irFrame.getHeight(), irFrame.getWidth(), CvType.CV_8UC4);
        org.opencv.android.Utils.bitmapToMat(irFrame, irRGBA);

        float[] result = doPupilTracking(irRGBA != null ? irRGBA.getNativeObjAddr() : 0);

        //TODO: here the callback is called with the result of the pupil tracking
        if (mCallback != null) {
            PupilPoint pp = new PupilPoint(new Point(result[7], result[8]), new Point(result[1], result[2]), result[5], new Point(result[3], result[4]), result[6], irTS, result[0]);

            Bitmap resultIR = Bitmap.createBitmap(irRGBA.width(), irRGBA.height(), Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(irRGBA, resultIR);
            mCallback.onHybridEyeTracked(resultIR, null, new DataFrame(pp));
        }
    }

    /**
     * Method for pupil tracking in IR image and mapping on Corneal image. It also crops the corneal image around the pupil.
     *
     * @param irFrame      - the IR camera frame
     * @param cornealFrame - the corneal camera frame
     */
    public void hybridEyeTracking(Bitmap irFrame, Bitmap cornealFrame, boolean debug, double irTS, double corTS) {
        if (irFrame == null || cornealFrame == null) {
            return;
        }

        Mat corRGBA = null;



        Mat irRGBA = new Mat(irFrame.getHeight(), irFrame.getWidth(), CvType.CV_8UC4);
        org.opencv.android.Utils.bitmapToMat(irFrame, irRGBA);

        if (cornealFrame != null) {
            corRGBA = new Mat(cornealFrame.getHeight(), cornealFrame.getWidth(), CvType.CV_8UC4);
            org.opencv.android.Utils.bitmapToMat(cornealFrame, corRGBA);
        }

        float[] result = doHybridEyeTracking(irRGBA != null ? irRGBA.getNativeObjAddr() : 0, corRGBA != null ? corRGBA.getNativeObjAddr() : 0, HeyebridActivity.CORNEAL_WIDTH / (HeyebridActivity.IR_WIDTH * 1.0));

        //check if the pupil was tracked
        if (result[1] > 0 && result[2] > 0) {
            PupilPoint pp = new PupilPoint(new Point(result[7], result[8]), new Point(result[1], result[2]), result[5], new Point(result[3], result[4]), result[6], irTS, result[0]);

            //check if we have a mapped pupil to corneal image
            if (result[9] > 0 && result[10] > 0 & result[11] > 0 && result[12] > 0) {

                CornealPoint cp = new CornealPoint(new Point(result[9], result[10]), (int) result[11], (int) result[12], corTS, new Point(result[13], result[14]));

                Mat cropped_flip = corRGBA;

                if (mCallback != null) {
                    Bitmap resultIR = Bitmap.createBitmap(irRGBA.width(), irRGBA.height(), Bitmap.Config.ARGB_8888);
                    org.opencv.android.Utils.matToBitmap(irRGBA, resultIR);

                    Bitmap cornealFlipped = Bitmap.createBitmap(cropped_flip.cols(), cropped_flip.rows(), Bitmap.Config.ARGB_8888);
                    org.opencv.android.Utils.matToBitmap(cropped_flip, cornealFlipped);

                    DataFrame df = new DataFrame(pp, cp);
                    mCallback.onHybridEyeTracked(resultIR, cornealFlipped, df);
                }
            } else {
                //otherwise just update the IR image
            /*
            if (mCallback != null) {
                Bitmap resultIR = Bitmap.createBitmap(irRGBA.width(), irRGBA.height(), Bitmap.Config.ARGB_8888);
                org.opencv.android.Utils.matToBitmap(irRGBA, resultIR);

                mCallback.onHybridEyeTracked(resultIR, null, new DataFrame(pp));
            }*/
            }
        } else {
            //do nothing
        }
    }

    /**
     * Method for pupil tracking in IR image and mapping on Corneal image. It also crops the corneal image around the pupil, and maps it if necessary to the world view.
     *  @param irFrame      - the IR camera frame
     * @param cornealFrame - the corneal camera frame
     * @param irTS
     * @param corTS
     */
    public void hybridPlusEyeTracking(Bitmap irFrame, Bitmap cornealFrame, Bitmap scene, boolean debug, boolean doMapping, double irTS, double corTS) {
        if (irFrame == null) {
            return;
        }
        Mat corRGBA = null;

        Mat irRGBA = new Mat(irFrame.getHeight(), irFrame.getWidth(), CvType.CV_8UC4);
        org.opencv.android.Utils.bitmapToMat(irFrame, irRGBA);

        if (cornealFrame != null) {
            corRGBA = new Mat(cornealFrame.getHeight(), cornealFrame.getWidth(), CvType.CV_8UC4);
            org.opencv.android.Utils.bitmapToMat(cornealFrame, corRGBA);
        }

        float[] result = doHybridEyeTracking(irRGBA != null ? irRGBA.getNativeObjAddr() : 0, corRGBA != null ? corRGBA.getNativeObjAddr() : 0, Heyebrid3CActivity.CORNEAL_WIDTH / (Heyebrid3CActivity.IR_WIDTH * 1.0));

        //check if the pupil was tracked
        if (result[1] > 0 && result[2] > 0) {
            PupilPoint pp = new PupilPoint(new Point(result[7], result[8]), new Point(result[1], result[2]), result[5], new Point(result[3], result[4]), result[6], (long) result[9], result[0]);

            //check if we have a mapped pupil to corneal image
            if (result[9] > 0 && result[10] > 0 & result[11] > 0 && result[12] > 0) {

                CornealPoint cp = new CornealPoint(new Point(result[9], result[10]), (int) result[11], (int) result[12], corTS, new Point(result[13], result[14]));

                Mat cropped_flip = corRGBA;

                Bitmap resultIR = Bitmap.createBitmap(irRGBA.width(), irRGBA.height(), Bitmap.Config.ARGB_8888);
                org.opencv.android.Utils.matToBitmap(irRGBA, resultIR);

                Bitmap cornealFlipped = Bitmap.createBitmap(cropped_flip.cols(), cropped_flip.rows(), Bitmap.Config.ARGB_8888);
                org.opencv.android.Utils.matToBitmap(cropped_flip, cornealFlipped);

                if (doMapping && scene != null) {
                    Mat mRgbaScene = new Mat(scene.getHeight(), scene.getWidth(), CvType.CV_8UC4);
                    org.opencv.android.Utils.bitmapToMat(scene, mRgbaScene);

                    // compute the homography matrix
                    Mat[] transformationResult = computeHomography(cropped_flip, mRgbaScene, debug);

                    if (transformationResult[0] != null) {
                        synchronized (mSyncHomography) {
                            mHomography = transformationResult[0];
                        }
                        setDoHomography(false);

                        Bitmap resultFeature = null;

                        if (debug && mCallback != null && transformationResult[1] != null) {
                            resultFeature = Bitmap.createBitmap(transformationResult[1].width(), transformationResult[1].height(), Bitmap.Config.ARGB_8888);
                            org.opencv.android.Utils.matToBitmap(transformationResult[1], resultFeature);

                        }

                        if (mCallback != null) {
                            mCallback.onHybridEyeTracked(resultIR, cornealFlipped, new DataFrame(pp, cp, gazeMapping(cp)));
                        }
                    }
                } else {
                    // we do not have to compute a new mapping, just transform it directly
                    if (mCallback != null) {
                        mCallback.onHybridEyeTracked(resultIR, cornealFlipped, new DataFrame(pp, cp, gazeMapping(cp)));
                    }
                }
            } else {
                //we only got new pupil points
                if (mCallback != null) {
                    Bitmap resultIR = Bitmap.createBitmap(irRGBA.width(), irRGBA.height(), Bitmap.Config.ARGB_8888);
                    org.opencv.android.Utils.matToBitmap(irRGBA, resultIR);

                    mCallback.onHybridEyeTracked(resultIR, null, new DataFrame(pp));
                }
            }
        }
    }

    /**
     * Inner Class providing the Tracker object for feature tracking
     */
    private class Tracker {

        /**
         * Nearest-neighbour matching ratio
         */
        private final double nn_match_ratio = 0.8f;

        /**
         * RANSAC inlier threshold
         */
        private final double ransac_thresh = 2.5f;

        private Feature2D detector;
        private DescriptorMatcher matcher;

        private Mat first_frame, first_desc, object_bb;
        private MatOfKeyPoint first_kp;

        public Tracker(Feature2D _detector, DescriptorMatcher _matcher) {
            detector = _detector;
            matcher = _matcher;

            first_desc = new Mat();
            first_kp = new MatOfKeyPoint();
        }

        public void setFirstFrame(Mat frame) {
            first_frame = frame.clone();
            detector.detectAndCompute(first_frame, new Mat(), first_kp, first_desc);
            object_bb = new Mat(4, 1, CvType.CV_32FC2);

            object_bb.put(0, 0, new double[]{10.0, 10.0});
            object_bb.put(1, 0, new double[]{frame.cols() - 10.0, 10.0});
            object_bb.put(2, 0, new double[]{frame.cols() - 10.0, frame.rows() - 10.0});
            object_bb.put(3, 0, new double[]{10.0, frame.rows() - 10.0});

            //TODO:debug
            Utils.drawBoundingBox(first_frame, object_bb);
        }

        public Mat[] process(Mat frame, boolean debug) {
            Mat homography = null;
            Mat outputImage = null;
            Mat desc = new Mat();

            MatOfKeyPoint kp = new MatOfKeyPoint();

            detector.detectAndCompute(frame, new Mat(), kp, desc);

            LinkedList<MatOfDMatch> matches = new LinkedList<MatOfDMatch>();
            LinkedList<KeyPoint> matched1_kps = new LinkedList<KeyPoint>();
            LinkedList<KeyPoint> matched2_kps = new LinkedList<KeyPoint>();

            LinkedList<Point> matched1 = new LinkedList<Point>();
            LinkedList<Point> matched2 = new LinkedList<Point>();

            matcher.knnMatch(first_desc, desc, matches, 2);
            for (MatOfDMatch m : matches) {
                if (m.toArray()[0].distance < nn_match_ratio * m.toArray()[1].distance) {
                    matched1.addLast(first_kp.toArray()[m.toArray()[0].queryIdx].pt);
                    matched2.addLast(kp.toArray()[m.toArray()[0].trainIdx].pt);

                    matched1_kps.addLast(first_kp.toArray()[m.toArray()[0].queryIdx]);
                    matched2_kps.addLast(kp.toArray()[m.toArray()[0].trainIdx]);
                }
            }

            MatOfPoint2f obj = new MatOfPoint2f();
            obj.fromList(matched1);

            MatOfPoint2f scene = new MatOfPoint2f();
            scene.fromList(matched2);

            Mat inlierMask = new Mat();

            LinkedList<KeyPoint> inliers1 = new LinkedList<KeyPoint>();
            LinkedList<KeyPoint> inliers2 = new LinkedList<KeyPoint>();
            LinkedList<DMatch> inlier_matches = new LinkedList<DMatch>();

            int keypoints = matched1_kps.size();
            int matchesNum = matched1.size();
            int inliersNum = 0;

            if (matched1.size() > 4) {

                homography = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, ransac_thresh, inlierMask, 2000, 0.995);

                for (int i = 0; i < matched1.size(); i++) {
                    if (inlierMask.get(i, 0)[0] == 1) {
                        int new_i = inliers1.size();
                        inliers1.addLast(matched1_kps.get(i));
                        inliers2.addLast(matched2_kps.get(i));
                        inlier_matches.addLast(new DMatch(new_i, new_i, 0));
                    }
                }

                inliersNum = inliers1.size();

                if (debug) {

                    Mat new_bb = new Mat(4, 1, CvType.CV_32FC2);

                    Core.perspectiveTransform(object_bb, new_bb, homography);

                    //rectangle for debug drawing
                    Mat frame_with_bb = frame.clone();
                    Utils.drawBoundingBox(frame_with_bb, new_bb);

                    //convert to mats
                    MatOfKeyPoint in1 = new MatOfKeyPoint();
                    in1.fromList(inliers1);
                    MatOfKeyPoint in2 = new MatOfKeyPoint();
                    in2.fromList(inliers2);
                    MatOfDMatch inm = new MatOfDMatch();
                    inm.fromList(inlier_matches);

                    Mat res = new Mat();
                    Mat q_frame = new Mat();
                    Mat t_frame = new Mat();
                    Imgproc.cvtColor(first_frame, q_frame, Imgproc.COLOR_RGBA2RGB);
                    Imgproc.cvtColor(frame_with_bb, t_frame, Imgproc.COLOR_RGBA2RGB);
                    Features2d.drawMatches(q_frame, in1, t_frame, in2, inm, res);

                    outputImage = new Mat();
                    Imgproc.cvtColor(res, outputImage, Imgproc.COLOR_RGB2RGBA);
                }

            }

            Log.d("Processing", "AKAZE stats: keypoints:" + keypoints + ", matches: " + matchesNum + ", inliners: " + inliersNum);

            return new Mat[]{homography, outputImage};
        }

    }

}
