package com.jesen.cameratool

import android.hardware.camera2.CameraCharacteristics
import android.util.Log

/**
 * 判断相机的 Hardware Level 是否大于等于指定的 Level。
 */
fun CameraCharacteristics.isHardwareLevelSupported(requiredLevel: Int): Boolean {
    val sortedLevels = intArrayOf(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
    )
    val deviceLevel = this[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]
    Log.d("CameraCharacteristics","ext , deviceLevel: $deviceLevel")
    if (requiredLevel == deviceLevel) {
        return true
    }
    for (sortedLevel in sortedLevels) {
        Log.d("CameraCharacteristics","ext , sortedLevel: $sortedLevel")
        if (requiredLevel == sortedLevel) {
            return true
        }
    }
    return false
}