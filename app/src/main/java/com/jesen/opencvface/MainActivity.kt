package com.jesen.opencvface

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import com.jesen.cod.camerafunction.utils.Outil
import com.jesen.opencvface.databinding.ActivityMainBinding
import com.jesen.opencvface.utils.Camera2Helper
import com.jesen.opencvface.utils.FileUtil
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mCamera2Helper: Camera2Helper
    private lateinit var mOpencvHelp:OpencvHelp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mOpencvHelp = OpencvHelp();
        mCamera2Helper = Camera2Helper(this, mBinding.textureView)
        mBinding.captureBtn.setOnClickListener { mCamera2Helper.takePic() }
        mBinding.changeCamera.setOnClickListener { mCamera2Helper.changeCamera() }

        FileUtil.copyAssets(this,"lbpcascade_frontalface.xml");

    }

    override fun onResume() {
        super.onResume()

        val path =  File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"lbpcascade_frontalface.xml").absolutePath
        Outil.log("onResume path = $path")
        mOpencvHelp.init(path)
    }
}