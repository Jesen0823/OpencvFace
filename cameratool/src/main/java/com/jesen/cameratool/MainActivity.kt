package com.jesen.cameratool

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.jesen.cameratool.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var camera2Helper: Camera2Helper

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        camera2Helper = Camera2Helper(this);
        findViewById<Button>(R.id.startBtn).setOnClickListener {
            camera2Helper.startCamera()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStop() {
        super.onStop()
        camera2Helper.endCamera()
    }


}