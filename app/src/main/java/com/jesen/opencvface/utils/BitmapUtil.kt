package com.jesen.cod.camerafunction.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.jesen.opencvface.utils.FileUtil
import java.io.ByteArrayOutputStream

import okio.buffer
import okio.sink


object BitmapUtil {
    fun toByteArray(bitmap: Bitmap): ByteArray {
        var os = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
        return os.toByteArray()
    }

    fun mirror(rawBitmap: Bitmap): Bitmap {
        var matrix = Matrix()
        matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    }

    fun rotate(rawBitmap: Bitmap, degree: Float): Bitmap {
        var matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    }

    fun decodeBitmap(bitmap: Bitmap, reqWidth: Int, reqHeight: Int): Bitmap {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        var bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.size(), options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.size(), options)
    }

    fun decodeBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    fun decodeBitmapFromResource(res: Resources, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(res, resId, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        var inSampleSize = 1

        if (rawWidth > reqWidth || rawHeight > reqHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2

            while ((halfWidth / inSampleSize) > reqWidth && (halfHeight / inSampleSize) > reqHeight) {
                inSampleSize *= 2
                //设置inSampleSize为2的幂是因为解码器最终还是会对非2的幂的数进行向下处理，获取到最靠近2的幂的数
            }
        }
        return inSampleSize
    }

    fun savePic(context: Context, data: ByteArray?, isMirror: Boolean = false, onSuccess: (savedPath: String, time: String) -> Unit, onFailed: (msg: String) -> Unit) {
        Thread {
            try {
                val temp = System.currentTimeMillis()
                val picFile = FileUtil.createCameraFile("camera2", context)
                if (picFile != null && data != null) {

                    val rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    val resultBitmap = if (isMirror) mirror(rawBitmap) else rawBitmap
                    picFile.sink().buffer().write(toByteArray(resultBitmap)).close()
                    onSuccess("${picFile.absolutePath}", "${System.currentTimeMillis() - temp}")

                    Outil.log("savePic, img is saved! use time：" +
                            "${System.currentTimeMillis() - temp} , path = ${picFile.absolutePath}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onFailed("${e.message}")
            }
        }.start()
    }
}