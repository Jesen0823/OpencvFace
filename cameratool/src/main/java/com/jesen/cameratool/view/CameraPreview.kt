package com.jesen.cameratool.view

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class CameraPreviewView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int = 0):TextureView(context,attributeSet,defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(width, width / 3 * 4)
    }
}