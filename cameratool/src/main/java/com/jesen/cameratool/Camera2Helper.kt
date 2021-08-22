package com.jesen.cameratool

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat

@RequiresApi(Build.VERSION_CODES.M)
class Camera2Helper(activity: Activity) :Handler.Callback{

    private val mActivity = activity
    private val context = GlobaApp.getApplication().applicationContext

    private val cameraManager:CameraManager by lazy {
        context.getSystemService(CameraManager::class.java)
    }
    private  var frontCameraId:String? = null
    private  var backCameraId:String? =null
    private var frontCharacteristics:CameraCharacteristics? = null
    private var backCharacteristics:CameraCharacteristics? = null

    private var cameraThread: HandlerThread? = null
    private var mCameraDevice: CameraDevice? = null

    //获取相机列表
    private val cameraIdList = cameraManager.cameraIdList
    private data class OpenCameraMessage(val cameraId: String, val cameraStateCallback: CameraStateCallback)
    private var cameraHandler: Handler? = null

    companion object{
        private const val TAG = "Camera2Helper"
        private const val REQUIRED_SUPPORTED_HARDWARE_LEVEL: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        private const val MSG_OPEN_CAMERA: Int = 0x01
        private const val MSG_CLOSE_CAMERA: Int = 0x02
        private const val REQUEST_PERMISSION_CODE: Int = 1
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

    }
    init {
        cameraIdList.forEach { cameraId ->
            Log.d(TAG,"foreach , cameraId: $cameraId")
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            if (cameraCharacteristics.isHardwareLevelSupported(REQUIRED_SUPPORTED_HARDWARE_LEVEL)){
                Log.d(TAG,"if , support: $REQUIRED_SUPPORTED_HARDWARE_LEVEL")
                if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT){
                    frontCameraId = cameraId
                    frontCharacteristics = cameraCharacteristics
                }else if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK){
                    backCameraId = cameraId
                    backCharacteristics = cameraCharacteristics
                }
            }
        }
    }

    /**
     * 判断权限是否被授予，只要有一个没有授权，我们都会返回 false，并且进行权限申请操作。
     *
     * @return true 权限都被授权
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkRequiredPermissions(): Boolean {
        val deniedPermissions = mutableListOf<String>()
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permission)
            }
        }
        if (deniedPermissions.isEmpty().not()) {
            requestPermissions(mActivity,deniedPermissions.toTypedArray(),
                REQUEST_PERMISSION_CODE
            )
        }
        return deniedPermissions.isEmpty()
    }

    fun startCamera(){
        if (checkRequiredPermissions()) {
            startCameraThread()
            openCamera()
        }
    }

    fun endCamera(){
        closeCamera()
        stopCameraThread()
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread")
        cameraThread!!.start()
        cameraHandler = Handler(cameraThread!!.looper, this)
    }

    fun stopCameraThread() {
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    private inner class CameraStateCallback:CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            Log.d(TAG,"相机已开启")
        }

        override fun onDisconnected(camera: CameraDevice) {
            mCameraDevice = camera
            closeCamera();
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG,"相机 onError, code:$error")
            mCameraDevice = camera
            closeCamera()
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            mCameraDevice = null
            Log.d(TAG,"相机已关闭")
        }

    }

    @SuppressLint("MissingPermission")
    override fun handleMessage(msg: Message): Boolean {
        Log.d(TAG,"message:$msg")

        when(msg.what){
            MSG_OPEN_CAMERA ->{
                val openCameraMessage = msg.obj as OpenCameraMessage
                val cameraId = openCameraMessage.cameraId
                val cameraStateCallback = openCameraMessage.cameraStateCallback
                cameraManager.openCamera(cameraId,cameraStateCallback,cameraHandler)
            }
            MSG_CLOSE_CAMERA -> mCameraDevice?.close()
        }
        return false
    }

    private fun openCamera(){
        val cameraId = backCameraId?:frontCameraId
        if (cameraId != null){
            val openCameraMessage = OpenCameraMessage(cameraId, CameraStateCallback())
            cameraHandler?.obtainMessage(MSG_OPEN_CAMERA, openCameraMessage)?.sendToTarget()
        }else{
            throw RuntimeException("Camera id is null.")
        }
    }

    private fun closeCamera(){
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_CAMERA)
    }
}