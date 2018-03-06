package org.lander.eyemirror.Utils;

import org.lander.eyemirror.activities.Heyebrid3CActivity;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

/**
 * Created by christian on 10.07.17.
 */
public class DataFrame {

    public PupilPoint mPupilPoint;
    public CornealPoint mCornealPoint;
    public GazePoint mGazePoint;

    public DataFrame(PupilPoint pp) {
        mPupilPoint = pp;
    }

    public DataFrame(PupilPoint pp, CornealPoint cp) {
        mPupilPoint = pp;
        mCornealPoint = cp;
    }

    public DataFrame(PupilPoint pp, CornealPoint cp, GazePoint gp) {
        mPupilPoint = pp;
        mCornealPoint = cp;
        mGazePoint = gp;
    }
}
