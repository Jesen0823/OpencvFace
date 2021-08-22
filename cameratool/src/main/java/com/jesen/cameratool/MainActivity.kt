package com.jesen.cameratool

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.jesen.cameratool.camera2.Camera2Helper
import com.jesen.cameratool.camera2.HelperCallback
import com.jesen.cameratool.view.CameraPreviewView

private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity() , HelperCallback {

    private var camera2Helper: Camera2Helper? =null
    private lateinit var thumbnailView:ImageView
    private lateinit var startCamera:ImageButton
    private lateinit var switchCamera:ImageButton
    private lateinit var preView:CameraPreviewView

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thumbnailView = findViewById(R.id.thumbnail_view)
        preView = findViewById(R.id.preview)
        startCamera = findViewById(R.id.capture_image_btn)
        switchCamera = findViewById(R.id.switch_camera)

        camera2Helper = Camera2Helper(this,preView)
        // 初始化camera2Helper
        camera2Helper?.initHelper()
        camera2Helper?.setHelperCallback(this)
        startCamera.setOnTouchListener(CaptureImageButtonListener(this))

        switchCamera.setOnClickListener {
            camera2Helper?.switchCamera()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()
        camera2Helper?.startCamera()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onPause() {
        super.onPause()
        camera2Helper?.onPause()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStop() {
        super.onStop()
        camera2Helper?.endPreView()
        camera2Helper?.endCamera()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onDestroy() {
        super.onDestroy()
        camera2Helper?.onDestroy()
    }

    override fun saveImageResult(thumb: Bitmap?) {
        Log.d(TAG,"currentThread:${Thread.currentThread().name}")
        thumbnailView.setImageBitmap(thumb)
        thumbnailView.scaleX = 0.8F
        thumbnailView.scaleY = 0.8F
        thumbnailView.animate().setDuration(50).scaleX(1.0F).scaleY(1.0F)
            .start()
    }

    override fun yuvResult(image: Image) {
        val planes = image.planes
        val yPlane = planes[0]
        Log.d("MainActivity","yuvResult, Y: ${yPlane.buffer}")
    }

    private inner class CaptureImageButtonListener(context: Context) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {

        private val gestureDetector: GestureDetector = GestureDetector(context, this)
        private var isLongPressed: Boolean = false

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP && isLongPressed) {
                onLongPressUp()
            }
            return gestureDetector.onTouchEvent(event)
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            Toast.makeText(this@MainActivity,"单击拍照",Toast.LENGTH_SHORT).show()
            camera2Helper?.captureImage()
            return true
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onLongPress(event: MotionEvent) {
            Toast.makeText(this@MainActivity,"开始录像",Toast.LENGTH_SHORT).show()
            isLongPressed = true
            camera2Helper?.startCaptureImageContinuously()
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun onLongPressUp() {
            Toast.makeText(this@MainActivity,"录像停止",Toast.LENGTH_SHORT).show()
            isLongPressed = false
            camera2Helper?.stopCaptureImageContinuously()
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onDoubleTap(event: MotionEvent): Boolean {
            Toast.makeText(this@MainActivity,"连拍5张",Toast.LENGTH_SHORT).show()
            camera2Helper?.captureImageBurst(5)
            return true
        }

    }

}