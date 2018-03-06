package org.lander.eyemirror.Utils;

import org.lander.eyemirror.activities.Heyebrid3CActivity;
import org.opencv.core.Point;

/**
 * Created by christian on 18.07.17.
 */

public class GazePoint {
    public Point gazePoint;

    public GazePoint(Point gP) {
        gazePoint = gP;
    }

    public double getGazeX() {
        return gazePoint.x;
    }

    public double getGazeY() {
        return gazePoint.y;
    }

    public double getGazeXNorm() {
        return gazePoint.x / (Heyebrid3CActivity.SCENE_WIDTH * 1.0);
    }

    public double getGazeYNorm() {
        return gazePoint.y / (Heyebrid3CActivity.SCENE_HEIGHT * 1.0);
    }

    public String dump() {
        return "Gaze: " + getGazeX() + ", " + getGazeY() + " - normalized: " + getGazeXNorm() + ", "  +getGazeYNorm();
    }
}
