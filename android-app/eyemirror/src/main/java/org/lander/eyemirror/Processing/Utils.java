package org.lander.eyemirror.Processing;

import android.graphics.Bitmap;

import org.lander.eyemirror.Utils.DataFrame;
import org.lander.eyemirror.Utils.GazePoint;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Created by christian on 01/03/2017.
 */
public class Utils {

    public static void drawBoundingBox(Mat img, Mat bb) {
        for (int i = 0; i < bb.rows() - 1; i++) {
            Imgproc.line(img, new Point(bb.get(i, 0)), new Point(bb.get(i+1, 0)), new Scalar(0, 255, 0), 4);
        }
        Imgproc.line(img, new Point(bb.get(bb.rows()-1, 0)), new Point(bb.get(0, 0)), new Scalar(0, 255, 0), 4);
    }

    public static Bitmap drawPoint(Bitmap bmp, GazePoint gp) {
        Mat mRgbaScene = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC4);
        org.opencv.android.Utils.bitmapToMat(bmp, mRgbaScene);

        Imgproc.circle(mRgbaScene, gp.gazePoint, 15, new Scalar(0, 0, 255), -1);

        org.opencv.android.Utils.matToBitmap(mRgbaScene, bmp);
        return bmp;
    }
}
