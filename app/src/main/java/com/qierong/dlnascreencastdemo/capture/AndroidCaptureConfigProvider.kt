package com.qierong.dlnascreencastdemo.capture

import android.content.Context
import android.os.Build
import android.view.WindowManager

class AndroidCaptureConfigProvider(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun current(): CaptureConfig {
        val resources = applicationContext.resources
        val densityDpi = resources.configuration.densityDpi
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applicationContext
                .getSystemService(WindowManager::class.java)
                .maximumWindowMetrics
                .bounds
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.run {
                android.graphics.Rect(0, 0, widthPixels, heightPixels)
            }
        }
        return CaptureConfig(
            width = bounds.width(),
            height = bounds.height(),
            densityDpi = densityDpi,
        )
    }
}
