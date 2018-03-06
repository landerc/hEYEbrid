package org.lander.eyemirror.Utils;

import android.graphics.Bitmap;

/**
 * Created by christian on 18.07.17.
 */

public class ImageData {

    public Bitmap bmp;
    public double timestamp;

    public ImageData(Bitmap bmp, double ts) {
        this.bmp = bmp;
        timestamp = ts;
    }
}
