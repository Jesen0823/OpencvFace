package com.jesen.cod.camerafunction.utils

import android.util.Log

object Outil {
    fun logI(msg:String){
        Log.i("jesenLog", msg)
    }

    @JvmStatic
    fun log(msg: String){
        Log.d("jesenLog", msg)
    }
}
