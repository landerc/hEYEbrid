package org.lander.eyemirror.Utils;

import org.opencv.core.Point;

/**
 * Created by christian on 18.07.17.
 */

public class PupilPoint {
    public Point normPupil;
    public Point centerPupil;
    public float angle;
    public Point axes;
    public double timestamp;
    public float diameter;
    public float confidence;

    public PupilPoint(Point nP, Point p, float angle, Point axes, float dia, double ts, float conf) {
        normPupil = nP;
        centerPupil = p;
        timestamp = ts;
        this.angle = angle;
        this.axes = axes;
        this.diameter = dia;
        this.confidence = conf;
    }
}
