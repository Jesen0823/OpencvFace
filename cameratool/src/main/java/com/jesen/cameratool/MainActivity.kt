package com.jesen.cameratool

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.annotation.RequiresApi
import com.jesen.cameratool.camera2.Camera2Helper
import com.jesen.cameratool.view.CameraPreviewView

class MainActivity : AppCompatActivity() {

    private  var camera2Helper: Camera2Helper? =null
    private lateinit var startPreView:Button
    private lateinit var startCamera:Button
    private lateinit var preView:CameraPreviewView

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startPreView = findViewById(R.id.preViewBtn)
        preView = findViewById(R.id.preview)
        startCamera = findViewById(R.id.startBtn)

        camera2Helper = Camera2Helper(this,preView)
        startCamera.setOnClickListener {
            camera2Helper?.startCamera()
        }
        startPreView.setOnClickListener {
            camera2Helper?.startPreView()

        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStop() {
        super.onStop()
        camera2Helper?.endPreView()
        camera2Helper?.endCamera()
    }


}