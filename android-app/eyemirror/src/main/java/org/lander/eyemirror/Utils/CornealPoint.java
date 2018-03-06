package org.lander.eyemirror.Utils;

import org.lander.eyemirror.activities.Heyebrid3CActivity;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

/**
 * Created by christian on 18.07.17.
 */

public class CornealPoint {
    public Point cornealPupil;
    public int mRectW, mRectH;
    public Point topLeft;
    public double mTimeStamp;

    public CornealPoint(Point cP, int rectW, int rectH, double time, Point tl) {
        cornealPupil = cP;
        mRectH = rectH;
        mRectW = rectW;
        mTimeStamp = time;
        topLeft = tl;
    }

    public MatOfPoint2f getCornealPupil() {
        MatOfPoint2f mop = new MatOfPoint2f();
        mop.fromArray(cornealPupil);
        return mop;
    }

    public double getGazeXNorm() {
        return cornealPupil.x / (Heyebrid3CActivity.CORNEAL_WIDTH * 1.0);
    }

    public double getGazeYNorm() {
        return cornealPupil.y / (Heyebrid3CActivity.CORNEAL_HEIGHT * 1.0);
    }
}
