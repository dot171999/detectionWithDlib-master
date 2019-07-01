
package com.tzutalin.dlibtest;

import android.support.annotation.Keep;

/**
 * Utility class for manipulating images.
 **/
public class ImageUtils {
    private static final String TAG = ImageUtils.class.getSimpleName();

    @Keep
    public static native void convertYUV420ToARGB8888(
            byte[] y,
            byte[] u,
            byte[] v,
            int[] output,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            boolean halfSize);

}
