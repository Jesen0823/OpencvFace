package com.jesen.opencvface.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

object FileUtil {

    @JvmStatic
    fun copyAssets(context: Context, name: String?) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
        if (file.exists()) {
            file.delete()
        }
        try {
            val fos = FileOutputStream(file)
            val `is` = context.assets.open(file.absolutePath)
            var len: Int
            val b = ByteArray(2048)
            while (`is`.read(b).also { len = it } != -1) {
                fos.write(b, 0, len)
            }
            fos.close()
            `is`.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun getStoragePath(context: Context, type: String?): String {
        var baseDir: String
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED == state) {
            var baseDirFile = context.getExternalFilesDir(type)
            baseDir = if (baseDirFile != null) {
                baseDirFile.absolutePath
            } else {
                context.filesDir.absolutePath
            }
        } else {
            baseDir = context.filesDir.absolutePath;
        }
        return baseDir
    }

    @JvmStatic
    fun createCameraFile(folderName: String = "camera", context: Context): File? {
        return try {
            val rootFile = File(getStoragePath(context, Environment.DIRECTORY_DCIM)
                    + File.separator + folderName)
            if (!rootFile.exists())
                rootFile.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val fileName = "IMG_$timeStamp.jpg"
            File(rootFile.absolutePath + File.separator + fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}