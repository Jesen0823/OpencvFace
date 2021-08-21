package com.jesen.opencvface

import android.hardware.Camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.widget.TextView
import com.jesen.cod.camerafunction.utils.Outil
import com.jesen.opencvface.databinding.ActivityMainBinding
import com.jesen.opencvface.utils.Camera2Helper
import com.jesen.opencvface.utils.CameraHelper
import com.jesen.opencvface.utils.FileUtil
import java.io.File

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mCameraHelper: CameraHelper
    private lateinit var mOpencvHelp: OpencvHelp
    var cameraId: Int = Camera.CameraInfo.CAMERA_FACING_FRONT


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mOpencvHelp = OpencvHelp();
        mBinding.surfaceView.holder.addCallback(this)
        mCameraHelper = CameraHelper(cameraId)
        mCameraHelper.setPreviewCallback(this)

        mBinding.changeCamera.setOnClickListener {
            mCameraHelper.switchCamera()
            cameraId = mCameraHelper.cameraId
        }

        FileUtil.copyAssets(this, "lbpcascade_frontalface.xml");
    }

    override fun onResume() {
        super.onResume()
        val path = File(
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "lbpcascade_frontalface.xml"
        ).absolutePath
        Outil.log("onResume path = $path")
        mCameraHelper.startPreview()
        mOpencvHelp.init(path)
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mOpencvHelp.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        mOpencvHelp.postData(data, CameraHelper.WIDTH, CameraHelper.HEIGHT, cameraId)
    }
}