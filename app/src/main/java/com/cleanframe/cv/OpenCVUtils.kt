package com.cleanframe.cv

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PathMeasure
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

object OpenCVUtils {

    /**

     */
    fun inpaintImage(
        original: Bitmap,
        maskPath: Path,
        strokeWidth: Float
    ): Bitmap {
        val srcRgba = Mat()
        val safeBitmap = if (original.config != Bitmap.Config.ARGB_8888) {
            original.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            original
        }
        Utils.bitmapToMat(safeBitmap, srcRgba)


        val srcBgr = Mat()
        Imgproc.cvtColor(srcRgba, srcBgr, Imgproc.COLOR_RGBA2BGR)


        val mask = Mat.zeros(srcBgr.size(), CvType.CV_8UC1)
        drawAndroidPathOnMask(mask, maskPath, strokeWidth)

        val dstBgr = Mat()
        Photo.inpaint(srcBgr, mask, dstBgr, 3.0, Photo.INPAINT_TELEA)


        val dstRgba = Mat()
        Imgproc.cvtColor(dstBgr, dstRgba, Imgproc.COLOR_BGR2RGBA)

        val out = Bitmap.createBitmap(dstRgba.cols(), dstRgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dstRgba, out)

        srcRgba.release()
        srcBgr.release()
        mask.release()
        dstBgr.release()
        dstRgba.release()

        return out
    }

    /**

     */
    private fun drawAndroidPathOnMask(mask: Mat, path: Path, strokeWidth: Float) {
        val pm = PathMeasure(path, false)
        val pos = FloatArray(2)
        val prev = FloatArray(2)

        val thickness = strokeWidth.coerceAtLeast(1f).toInt()

        do {
            val length = pm.length
            if (length <= 0f) continue

            var distance = 0f
            pm.getPosTan(0f, prev, null)


            while (distance <= length) {
                pm.getPosTan(distance, pos, null)
                Imgproc.line(
                    mask,
                    Point(prev[0].toDouble(), prev[1].toDouble()),
                    Point(pos[0].toDouble(), pos[1].toDouble()),
                    Scalar(255.0),
                    thickness,
                    Imgproc.LINE_AA
                )
                prev[0] = pos[0]
                prev[1] = pos[1]
                distance += 1f
            }
        } while (pm.nextContour())
    }
}
