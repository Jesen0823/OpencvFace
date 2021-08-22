package com.jesen.cameratool.camera2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.location.Location
import android.location.LocationManager
import android.media.Image
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.*
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.jesen.cameratool.GlobaApp
import com.jesen.cameratool.isHardwareLevelSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

@RequiresApi(Build.VERSION_CODES.M)
class Camera2Helper(activity: Activity, cameraPreview: TextureView) : Handler.Callback {

    companion object {
        private const val TAG = "Camera2Helper"
        private const val REQUIRED_SUPPORTED_HARDWARE_LEVEL: Int =
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        private const val MSG_OPEN_CAMERA: Int = 0x01
        private const val MSG_CLOSE_CAMERA: Int = 0x02
        private const val MSG_SET_PREVIEW_SIZE: Int = 0x03
        private const val MSG_CREATE_SESSION: Int = 0x04
        private const val MSG_START_PREVIEW: Int = 0x05
        private const val MSG_STOP_PREVIEW: Int = 0x06
        private const val MSG_CLOSE_SESSION: Int = 0x07
        private const val MSG_SET_IMAGE_SIZE: Int = 0x08
        private const val MSG_CAPTURE_IMAGE: Int = 0x09 // 单次拍照
        private const val MSG_CAPTURE_IMAGE_BURST: Int = 0x0A // 连拍多张
        private const val MSG_START_CAPTURE_IMAGE_CONTINUOUSLY: Int = 0x0B // 不间断获取预览
        private const val MSG_CREATE_REQUEST_BUILDERS: Int = 0x0C // 创建CaptureRequest

        private const val MAX_PREVIEW_WIDTH: Int = 1920
        private const val MAX_PREVIEW_HEIGHT: Int = 1080
        private const val MAX_IMAGE_WIDTH: Int = 1920
        private const val MAX_IMAGE_HEIGHT: Int = 1080

        private const val REQUEST_PERMISSION_CODE: Int = 1
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    // 播放音效
    private val mediaActionSound: MediaActionSound = MediaActionSound()
    private val deviceOrientationListener: DeviceOrientationListener by lazy {
        DeviceOrientationListener(context)
    }
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(CameraManager::class.java)
    }
    private val mCameraPreview = cameraPreview
    private val captureResults: BlockingQueue<CaptureResult> = LinkedBlockingDeque()
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var frontCameraId: String? = null
    private var backCameraId: String? = null
    private var frontCharacteristics: CameraCharacteristics? = null
    private var backCharacteristics: CameraCharacteristics? = null
    private var cameraDeviceFuture: SettableFuture<CameraDevice>? = null
    private var cameraCharacteristicsFuture: SettableFuture<CameraCharacteristics>? = null
    private var captureSessionFuture: SettableFuture<CameraCaptureSession>? = null
    private var previewSurfaceTextureFuture: SettableFuture<SurfaceTexture>? = null
    private var previewSurface: Surface? = null
    private var previewDataSurface: Surface? = null
    private var previewDataImageReader: ImageReader? = null
    private var mImageReader: ImageReader? = null
    private var mImageSurface: Surface? = null
    private var previewImageRequestBuilder: CaptureRequest.Builder? = null
    private var captureImageRequestBuilder: CaptureRequest.Builder? = null

    private var mHelperCallback: HelperCallback? = null
    private val mActivity = activity
    private val context = GlobaApp.getApplication().applicationContext


    @SuppressLint("MissingPermission")
    override fun handleMessage(msg: Message): Boolean {
        Log.d(TAG, "message:$msg")
        when (msg.what) {
            MSG_OPEN_CAMERA -> {
                Log.d(TAG, "msg [open camera]")
                cameraCharacteristicsFuture = SettableFuture()
                cameraDeviceFuture = SettableFuture()
                val cameraId = msg.obj as String
                val cameraStateCallback = CameraStateCallback()
                cameraManager.openCamera(cameraId, cameraStateCallback, mainHandler)
            }
            MSG_CLOSE_CAMERA -> {
                Log.d(TAG, "msg [close camera]")
                val cameraDevice = cameraDeviceFuture?.get()
                cameraDevice?.close()
                cameraDeviceFuture = null
                cameraCharacteristicsFuture = null
            }
            MSG_CREATE_SESSION -> {
                Log.d(TAG, "msg [create session]")
                val sessionStateCallback = SessionStateCallback()
                val outputs = mutableListOf<Surface>()
                val previewSurface = previewSurface
                val previewDataSurface = previewDataSurface
                val imageSurface = mImageSurface
                outputs.add(previewSurface!!)
                if (previewDataSurface != null) {
                    outputs.add(previewDataSurface)
                }
                if (imageSurface != null) {
                    outputs.add(imageSurface)
                }
                captureSessionFuture = SettableFuture()
                val cameraDevice = cameraDeviceFuture?.get()
                cameraDevice?.createCaptureSession(outputs, sessionStateCallback, mainHandler)
            }
            MSG_CLOSE_SESSION -> {
                Log.d(TAG, "msg [close session]")
                captureSessionFuture?.get()?.close()
                captureSessionFuture = null
            }
            MSG_SET_PREVIEW_SIZE -> {
                Log.d(TAG, "msg [set preview size]")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                if (cameraCharacteristics != null) {
                    // Get the optimal preview size according to the specified max width and max height.
                    val maxWidth = msg.arg1
                    val maxHeight = msg.arg2
                    val previewSize = selectOptimalSize(
                        cameraCharacteristics,
                        SurfaceTexture::class.java,
                        maxWidth,
                        maxHeight
                    )!!
                    // Set the SurfaceTexture's buffer size with preview size.
                    val previewSurfaceTexture = previewSurfaceTextureFuture!!.get()!!
                    previewSurfaceTexture.setDefaultBufferSize(
                        previewSize.width,
                        previewSize.height
                    )
                    previewSurface = Surface(previewSurfaceTexture)
                    // Set up an ImageReader to receive preview frame data if YUV_420_888 is supported.
                    val imageFormat = ImageFormat.YUV_420_888
                    val streamConfigurationMap =
                        cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                    if (streamConfigurationMap?.isOutputSupportedFor(imageFormat) == true) {
                        previewDataImageReader = ImageReader.newInstance(
                            previewSize.width, previewSize.height, imageFormat, 3
                        )
                        previewDataImageReader?.setOnImageAvailableListener(
                            OnPreviewDataAvailableListener(), cameraHandler
                        )
                        previewDataSurface = previewDataImageReader?.surface
                    }
                }
            }
            MSG_START_PREVIEW -> {
                Log.d(TAG, "msg [start preview]")
                val cameraDevice = cameraDeviceFuture?.get()
                val captureSession = captureSessionFuture?.get()
                val previewImageRequestBuilder = previewImageRequestBuilder!!
                val captureImageRequestBuilder = captureImageRequestBuilder!!
                if (cameraDevice != null && captureSession != null) {
                    Log.d(TAG, "Handle message: MSG_START_PREVIEW not null")

                    val previewSurface = previewSurface!!
                    val previewDataSurface = previewDataSurface
                    previewImageRequestBuilder.addTarget(previewSurface)
                    // Avoid missing preview frame while capturing image.
                    captureImageRequestBuilder.addTarget(previewSurface)
                    if (previewDataSurface != null) {
                        Log.d(TAG, "Handle message: MSG_START_PREVIEW ,previewDataSurface not null")

                        previewImageRequestBuilder.addTarget(previewDataSurface)
                        // Avoid missing preview data while capturing image.
                        captureImageRequestBuilder.addTarget(previewDataSurface)
                    }
                    val previewRequest = previewImageRequestBuilder.build()
                    captureSession.setRepeatingRequest(
                        previewRequest,
                        RepeatingCaptureStateCallback(),
                        mainHandler
                    )
                }
            }
            MSG_STOP_PREVIEW -> {
                Log.d(TAG, "msg [stop preview]")
                captureSessionFuture?.get()?.stopRepeating()
            }
            MSG_SET_IMAGE_SIZE -> {
                Log.d(TAG, "msg [set image size]")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val captureImageRequestBuilder = captureImageRequestBuilder
                if (cameraCharacteristics != null && captureImageRequestBuilder != null) {
                    // Create a JPEG ImageReader instance according to the image size.
                    val maxWidth = msg.arg1
                    val maxHeight = msg.arg2
                    val imageSize = selectOptimalSize(
                        cameraCharacteristics, ImageReader::class.java,
                        maxWidth, maxHeight
                    )!!
                    mImageReader = ImageReader.newInstance(
                        imageSize.width, imageSize.height,
                        ImageFormat.JPEG, 5
                    )
                    mImageReader?.setOnImageAvailableListener(
                        OnJpjAvailableListener(),
                        cameraHandler
                    )
                    mImageSurface = mImageReader?.surface
                    // Configure the thumbnail size if any suitable size found, no thumbnail will be generated if the thumbnail size is null.
                    val availableThumbnailSizes =
                        cameraCharacteristics[CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES]
                    val thumbnailSize = getOptimalSize(availableThumbnailSizes, maxWidth, maxHeight)
                    captureImageRequestBuilder[CaptureRequest.JPEG_THUMBNAIL_SIZE] = thumbnailSize
                }
            }
            MSG_CREATE_REQUEST_BUILDERS -> {
                Log.d(TAG, "msg [create request builders]")
                val cameraDevice = cameraDeviceFuture?.get()
                if (cameraDevice != null) {
                    previewImageRequestBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureImageRequestBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                }
            }
            MSG_CAPTURE_IMAGE -> {
                Log.d(TAG, "msg [capture single image]")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val captureSession = captureSessionFuture?.get()
                val captureImageRequestBuilder = captureImageRequestBuilder
                val jpegSurface = mImageSurface
                if (captureSession != null && captureImageRequestBuilder != null && jpegSurface != null && cameraCharacteristics != null) {
                    // Configure the jpeg orientation according to the device orientation.
                    val deviceOrientation = deviceOrientationListener.orientation
                    val imageOrientation =
                        getImageOrientation(cameraCharacteristics, deviceOrientation)
                    // 设置图片方向
                    captureImageRequestBuilder[CaptureRequest.JPEG_ORIENTATION] = imageOrientation

                    // Configure the location information.
                    val location = getCameraLocation()
                    captureImageRequestBuilder[CaptureRequest.JPEG_GPS_LOCATION] = location

                    // Configure the image quality.
                    captureImageRequestBuilder[CaptureRequest.JPEG_QUALITY] = 100

                    // Add the target surface to receive the jpeg image data.
                    captureImageRequestBuilder.addTarget(jpegSurface)

                    val captureImageRequest = captureImageRequestBuilder.build()
                    captureSession.capture(
                        captureImageRequest,
                        CaptureImageStateCallback(),
                        mainHandler
                    )
                }
            }
            MSG_CAPTURE_IMAGE_BURST -> {
                Log.d(TAG, "msg [capture image burst]")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val burstNumber = msg.arg1
                val captureSession = captureSessionFuture?.get()
                val captureImageRequestBuilder = captureImageRequestBuilder
                val jpegSurface = mImageSurface
                if (captureSession != null && captureImageRequestBuilder != null && jpegSurface != null && cameraCharacteristics != null) {
                    // Configure the jpeg orientation according to the device orientation.
                    val deviceOrientation = deviceOrientationListener.orientation
                    val imageOrientation =
                        getImageOrientation(cameraCharacteristics, deviceOrientation)
                    captureImageRequestBuilder[CaptureRequest.JPEG_ORIENTATION] = imageOrientation

                    // Configure the location information.
                    val location = getCameraLocation()
                    captureImageRequestBuilder[CaptureRequest.JPEG_GPS_LOCATION] = location

                    // Configure the image quality.
                    captureImageRequestBuilder[CaptureRequest.JPEG_QUALITY] = 100

                    // Add the target surface to receive the jpeg image data.
                    captureImageRequestBuilder.addTarget(jpegSurface)

                    // Use the burst mode to capture images sequentially.
                    val captureImageRequest = captureImageRequestBuilder.build()
                    val captureImageRequests = mutableListOf<CaptureRequest>()
                    for (i in 1..burstNumber) {
                        captureImageRequests.add(captureImageRequest)
                    }
                    captureSession.captureBurst(
                        captureImageRequests,
                        CaptureImageStateCallback(),
                        mainHandler
                    )
                }
            }
            MSG_START_CAPTURE_IMAGE_CONTINUOUSLY -> {
                Log.d(TAG, "msg [capture image continuously]")
                val cameraCharacteristics = cameraCharacteristicsFuture?.get()
                val captureSession = captureSessionFuture?.get()
                val captureImageRequestBuilder = captureImageRequestBuilder
                val jpegSurface = mImageSurface
                if (captureSession != null && captureImageRequestBuilder != null && jpegSurface != null && cameraCharacteristics != null) {
                    // Configure the jpeg orientation according to the device orientation.
                    val deviceOrientation = deviceOrientationListener.orientation
                    val imageOrientation =
                        getImageOrientation(cameraCharacteristics, deviceOrientation)
                    captureImageRequestBuilder[CaptureRequest.JPEG_ORIENTATION] = imageOrientation

                    // Configure the location information.
                    val location = getCameraLocation()
                    captureImageRequestBuilder[CaptureRequest.JPEG_GPS_LOCATION] = location

                    // Configure the image quality.
                    captureImageRequestBuilder[CaptureRequest.JPEG_QUALITY] = 100

                    // Add the target surface to receive the jpeg image data.
                    captureImageRequestBuilder.addTarget(jpegSurface)

                    // Use the repeating mode to capture image continuously.
                    val captureImageRequest = captureImageRequestBuilder.build()
                    captureSession.setRepeatingRequest(
                        captureImageRequest,
                        CaptureImageStateCallback(),
                        mainHandler
                    )
                }
            }
        }
        return false
    }

    fun initHelper() {
        //获取相机列表
        val cameraIdList = cameraManager.cameraIdList
        startCameraThread()
        cameraIdList.forEach { cameraId ->
            Log.d(TAG, "foreach , cameraId: $cameraId")
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            if (cameraCharacteristics.isHardwareLevelSupported(REQUIRED_SUPPORTED_HARDWARE_LEVEL)) {
                Log.d(TAG, "if , support: $REQUIRED_SUPPORTED_HARDWARE_LEVEL")
                if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId
                    frontCharacteristics = cameraCharacteristics
                } else if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = cameraId
                    backCharacteristics = cameraCharacteristics
                }
            }
        }
        previewSurfaceTextureFuture = SettableFuture()
        mCameraPreview.surfaceTextureListener = PreviewSurfaceTextureListener()
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
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_DENIED
            ) {
                deniedPermissions.add(permission)
            }
        }
        if (deniedPermissions.isEmpty().not()) {
            requestPermissions(
                mActivity, deniedPermissions.toTypedArray(),
                REQUEST_PERMISSION_CODE
            )
        }
        return deniedPermissions.isEmpty()
    }

    fun startCamera() {
        deviceOrientationListener.enable()
        if (checkRequiredPermissions()) {
            val cameraId = backCameraId ?: frontCameraId!!
            openCamera(cameraId)
            createCaptureRequestBuilders()
            setPreviewSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
            setImageSize(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            createSession()
            openPreview()
        }
    }

    @MainThread
    fun startPreView() {
        setPreviewSize(1440, 1080)
        createSession()
        openPreview()
    }

    fun endPreView() {
        closeSession()
        stopPreview()
    }

    fun endCamera() {
        mediaActionSound.release()
        closeCamera()
        stopCameraThread()
        previewDataImageReader?.close()
    }

    fun onPause() {
        deviceOrientationListener.disable()
        closeCamera()
        previewDataImageReader?.close()
        mImageReader?.close()
    }

    fun onDestroy() {
        stopCameraThread()
        mediaActionSound.release()
    }

    @MainThread
    fun switchCamera() {
        val cameraDevice = cameraDeviceFuture?.get()
        val oldCameraId = cameraDevice?.id
        val newCameraId = if (oldCameraId == frontCameraId) backCameraId else frontCameraId
        if (newCameraId != null) {
            closeCamera()
            openCamera(newCameraId)
            createCaptureRequestBuilders()
            setPreviewSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
            setImageSize(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            createSession()
            openPreview()
        }
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread")
        cameraThread!!.start()
        cameraHandler = Handler(cameraThread!!.looper, this)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    @MainThread
    private fun openCamera(cameraId: String) {
        cameraHandler?.obtainMessage(MSG_OPEN_CAMERA, cameraId)?.sendToTarget()
    }

    @MainThread
    private fun closeCamera() {
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_CAMERA)
    }

    @MainThread
    private fun setPreviewSize(maxWidth: Int, maxHeight: Int) {
        cameraHandler?.obtainMessage(MSG_SET_PREVIEW_SIZE, maxWidth, maxHeight)?.sendToTarget()
    }

    @MainThread
    private fun setImageSize(maxWidth: Int, maxHeight: Int) {
        cameraHandler?.obtainMessage(MSG_SET_IMAGE_SIZE, maxWidth, maxHeight)?.sendToTarget()
    }

    @MainThread
    private fun createSession() {
        cameraHandler?.sendEmptyMessage(MSG_CREATE_SESSION)
    }

    @MainThread
    private fun closeSession() {
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_SESSION)
    }

    @MainThread
    private fun createCaptureRequestBuilders() {
        cameraHandler?.sendEmptyMessage(MSG_CREATE_REQUEST_BUILDERS)
    }

    @MainThread
    private fun openPreview() {
        cameraHandler?.sendEmptyMessage(MSG_START_PREVIEW)
    }

    @MainThread
    private fun stopPreview() {
        cameraHandler?.sendEmptyMessage(MSG_STOP_PREVIEW)
    }

    // 拍照
    @MainThread
    fun captureImage() {
        cameraHandler?.sendEmptyMessage(MSG_CAPTURE_IMAGE)
    }

    // 照片连拍
    @MainThread
    fun captureImageBurst(burstNumber: Int) {
        cameraHandler?.obtainMessage(MSG_CAPTURE_IMAGE_BURST, burstNumber, 0)?.sendToTarget()
    }

    // 录像开始
    @MainThread
    fun startCaptureImageContinuously() {
        cameraHandler?.sendEmptyMessage(MSG_START_CAPTURE_IMAGE_CONTINUOUSLY)
    }

    // 录像停止
    @MainThread
    fun stopCaptureImageContinuously() {
        // Restart preview to stop the continuous image capture.
        openPreview()
    }

    /**
     * 在相机支持的尺寸范围内选取所需尺寸
     * */
    @WorkerThread
    private fun selectOptimalSize(
        cameraCharacteristics: CameraCharacteristics, clazz: Class<*>,
        maxWidth: Int, maxHeight: Int
    ): Size? {
        val streamConfigurationMap =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = streamConfigurationMap?.getOutputSizes(clazz)
        return getOptimalSize(supportedSizes, maxWidth, maxHeight)
    }

    @AnyThread
    private fun getOptimalSize(supportedSizes: Array<Size>?, maxWidth: Int, maxHeight: Int): Size? {
        val aspectRatio = maxWidth.toFloat() / maxHeight
        if (supportedSizes != null) {
            for (size in supportedSizes) {
                Log.d(TAG, "support size， width * height:${size.width} * ${size.height}")
                if (size.width.toFloat() / size.height == aspectRatio && size.height <= maxHeight && size.width <= maxWidth) {
                    return size
                }
            }
        }
        return null
    }

    private fun getDisplayRotation(cameraCharacteristics: CameraCharacteristics): Int {
        val degrees = when (mActivity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorOrientation = cameraCharacteristics[CameraCharacteristics.SENSOR_ORIENTATION]!!
        return if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
            (360 - (sensorOrientation + degrees) % 360) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
    }

    private fun getImageOrientation(
        cameraCharacteristics: CameraCharacteristics,
        deviceOrientation: Int
    ): Int {
        var myDeviceOrientation = deviceOrientation
        if (myDeviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 0
        }
        val sensorOrientation =
            cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        // Round device orientation to a multiple of 90
        myDeviceOrientation = (myDeviceOrientation + 45) / 90 * 90

        // Reverse device orientation for front-facing cameras
        val facingFront =
            cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        if (facingFront) {
            myDeviceOrientation = -myDeviceOrientation
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation + myDeviceOrientation + 360) % 360
    }

    @WorkerThread
    private fun getCameraLocation(): Location? {
        val locationManager = mActivity.getSystemService(LocationManager::class.java)
        if (locationManager != null && ContextCompat.checkSelfPermission(
                mActivity, android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            return locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }
        return null
    }

    private fun getCameraCharacteristics(cameraId: String): CameraCharacteristics? {
        return when (cameraId) {
            frontCameraId -> frontCharacteristics
            backCameraId -> backCharacteristics
            else -> null
        }
    }

    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        @MainThread
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "CameraStateCallback, onOpened")
            cameraDeviceFuture?.set(camera)
            cameraCharacteristicsFuture?.set(getCameraCharacteristics(camera.id))
        }

        @MainThread
        override fun onClosed(camera: CameraDevice) {
            Log.d(TAG, "CameraStateCallback, onClosed")
        }

        @MainThread
        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "CameraStateCallback, onDisconnected")
            cameraDeviceFuture?.set(camera)
            closeCamera()
        }

        @MainThread
        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "CameraStateCallback, onError")
            cameraDeviceFuture?.set(camera)
            closeCamera()
        }
    }

    private inner class SessionStateCallback : CameraCaptureSession.StateCallback() {
        @MainThread
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "SessionStateCallback, onConfigured")
            captureSessionFuture?.set(session)
        }

        @MainThread
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.d(TAG, "SessionStateCallback, onConfigureFailed")
            captureSessionFuture?.set(session)
        }

        @MainThread
        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            Log.d(TAG, "SessionStateCallback, onClosed")
        }
    }

    private inner class PreviewSurfaceTextureListener : TextureView.SurfaceTextureListener {
        @MainThread
        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture,
            width: Int, height: Int
        ) = Unit

        @MainThread
        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

        @MainThread
        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

        @MainThread
        override fun onSurfaceTextureAvailable(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "PreviewSurfaceTextureListener, onSurfaceTextureAvailable")
            previewSurfaceTextureFuture?.set(surfaceTexture)
        }
    }

    private inner class RepeatingCaptureStateCallback : CameraCaptureSession.CaptureCallback() {
        @MainThread
        override fun onCaptureStarted(
            session: CameraCaptureSession, request: CaptureRequest,
            timestamp: Long, frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            Log.d(TAG, "RepeatingCaptureStateCallback, onCaptureStarted")
        }

        @MainThread
        override fun onCaptureCompleted(
            session: CameraCaptureSession, request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            Log.d(TAG, "RepeatingCaptureStateCallback, onCaptureCompleted")
        }
    }

    private inner class OnPreviewDataAvailableListener : ImageReader.OnImageAvailableListener {

        /**
         * Called every time the preview frame data is available.
         */
        override fun onImageAvailable(imageReader: ImageReader) {
            Log.d(TAG, "OnPreviewDataAvailableListener, onImageAvailable")
            val image = imageReader.acquireNextImage()
            image?.use {
                val planes = image.planes
                val yPlane = planes[0]
                val uPlane = planes[1]
                val vPlane = planes[2]
                val yBuffer = yPlane.buffer // Frame data of Y channel
                val uBuffer = uPlane.buffer // Frame data of U channel
                val vBuffer = vPlane.buffer // Frame data of V channel

                mHelperCallback?.yuvResult(image)
            }
        }
    }

    /**
     * 拍照结果监听：得到CaptureResult
     * */
    private inner class CaptureImageStateCallback : CameraCaptureSession.CaptureCallback() {
        @MainThread
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest, result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            captureResults.put(result)
        }

        override fun onCaptureStarted(
            session: CameraCaptureSession, request: CaptureRequest,
            timestamp: Long, frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            cameraHandler?.post { mediaActionSound.play(MediaActionSound.SHUTTER_CLICK) }
        }
    }

    /**
     * 拍照结果监听：得到image
     * */
    private inner class OnJpjAvailableListener : ImageReader.OnImageAvailableListener {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onImageAvailable(imageReader: ImageReader) {
            val image = imageReader.acquireNextImage()
            val captureResult = captureResults.take()
            if (image != null && captureResult != null) {
                image.use {
                    val orientation = captureResult[CaptureResult.JPEG_ORIENTATION]
                    val location = captureResult[CaptureResult.JPEG_GPS_LOCATION]
                    val imageByteBuffer =
                        image.planes[0].buffer       // Jpeg image data only occupy the planes[0].
                    val imageByteArray = ByteArray(imageByteBuffer.remaining())
                    imageByteBuffer.get(imageByteArray)
                    val imgSize = Size(image.width, image.height)

                    GlobalScope.launch(Dispatchers.Main) {
                        val thumb = withContext(Dispatchers.IO) {
                                Util.insertImage2MediaStore(context, imageByteArray, imgSize, location, orientation)
                            }
                        Log.d(TAG, "currentThread:${Thread.currentThread().name}")
                        mHelperCallback?.saveImageResult(thumb)
                    }
                }
            }
        }
    }

    /**
     * 设备方向监听
     * */
    private inner class DeviceOrientationListener(context: Context) :
        OrientationEventListener(context) {

        var orientation: Int = 0
            private set

        @MainThread
        override fun onOrientationChanged(orientation: Int) {
            this.orientation = orientation
        }

    }

    fun setHelperCallback(callback: HelperCallback) {
        this.mHelperCallback = callback
    }
}


interface HelperCallback {
    fun saveImageResult(thumb: Bitmap?)
    fun yuvResult(image: Image)
}










