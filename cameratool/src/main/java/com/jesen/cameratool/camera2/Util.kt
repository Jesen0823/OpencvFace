package com.jesen.cameratool.camera2

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.media.ExifInterface
import android.media.Image
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Util {

    companion object {

        @RequiresApi(Build.VERSION_CODES.O)
        fun generateThumbnail(jpegPath: String): Bitmap? {
            val exifInterface = ExifInterface(jpegPath)
            val orientationFlag = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val orientation = when (orientationFlag) {
                ExifInterface.ORIENTATION_NORMAL -> 0.0F
                ExifInterface.ORIENTATION_ROTATE_90 -> 90.0F
                ExifInterface.ORIENTATION_ROTATE_180 -> 180.0F
                ExifInterface.ORIENTATION_ROTATE_270 -> 270.0F
                else -> 0.0F
            }

            var thumbnail = if (exifInterface.hasThumbnail()) {
                exifInterface.thumbnailBitmap
            } else {
                val options = BitmapFactory.Options()
                options.inSampleSize = 16
                BitmapFactory.decodeFile(jpegPath, options)
            }

            if (orientation != 0.0F && thumbnail != null) {
                val matrix = Matrix()
                matrix.setRotate(orientation)
                thumbnail = Bitmap.createBitmap(
                    thumbnail,
                    0,
                    0,
                    thumbnail.width,
                    thumbnail.height,
                    matrix,
                    true
                )
            }

            return thumbnail
        }

        /**
         * 将图片插入MediaStore，返回缩略图
         * */
        @RequiresApi(Build.VERSION_CODES.O)
        fun insertImage2MediaStore(
            context: Context,
            image: Image,
            location: Location?,
            orientation: Int?
        ): Bitmap? {
            image.use {
                val dateFormat = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault())
                val cameraDir: String = "${
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DCIM
                    )
                }/Camera"
                val date = System.currentTimeMillis()
                val title = "IMG_${dateFormat.format(date)}"// e.g. IMG_20190211100833786
                val displayName = "$title.jpeg"  // e.g. IMG_20190211100833786.jpeg
                val path =
                    "$cameraDir/$displayName"    // e.g. /sdcard/DCIM/Camera/IMG_20190211100833786.jpeg
                val longitude = location?.longitude ?: 0.0
                val latitude = location?.latitude ?: 0.0
                val imageByteBuffer =
                    image.planes[0].buffer       // Jpeg image data only occupy the planes[0].
                val imageByteArray = ByteArray(imageByteBuffer.remaining())
                imageByteBuffer.get(imageByteArray)
                // Write the jpeg data into the specified file.
                File(path).writeBytes(imageByteArray)

                // Insert the image information into the media store.
                val values = ContentValues()
                values.put(MediaStore.Images.ImageColumns.TITLE, title)
                values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, displayName)
                values.put(MediaStore.Images.ImageColumns.DATA, path)
                values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, date)
                values.put(MediaStore.Images.ImageColumns.WIDTH, image.width)
                values.put(MediaStore.Images.ImageColumns.HEIGHT, image.height)
                values.put(MediaStore.Images.ImageColumns.ORIENTATION, orientation)
                values.put(MediaStore.Images.ImageColumns.LONGITUDE, longitude)
                values.put(MediaStore.Images.ImageColumns.LATITUDE, latitude)
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                // Refresh the thumbnail of image.
                return generateThumbnail(path)
            }
        }
    }
}
