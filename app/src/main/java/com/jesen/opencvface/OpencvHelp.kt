package com.jesen.opencvface

class OpencvHelp {

    companion object {
        init {
            System.loadLibrary("opencvface")
        }
    }

    external fun init(path:String);
}