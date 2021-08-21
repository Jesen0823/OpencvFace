package com.jesen.opencvface

import android.view.Surface

class OpencvHelp {

    companion object {
        init {
            System.loadLibrary("opencvface")
        }
    }

    external fun init(path:String);
    external fun setSurface(surface: Surface)
    external fun postData(data: ByteArray, width: Int, height: Int, cameraId: Int)
}