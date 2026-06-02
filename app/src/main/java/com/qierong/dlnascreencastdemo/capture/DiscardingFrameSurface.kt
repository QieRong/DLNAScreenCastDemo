package com.qierong.dlnascreencastdemo.capture

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.view.Surface

internal class DiscardingFrameSurface private constructor(
    private val imageReader: ImageReader,
) {
    val surface: Surface = imageReader.surface

    fun stopConsuming() {
        imageReader.setOnImageAvailableListener(null, null)
    }

    fun close() {
        imageReader.close()
        surface.release()
    }

    companion object {
        fun create(config: CaptureConfig, handler: Handler): DiscardingFrameSurface {
            val reader = ImageReader.newInstance(
                config.width,
                config.height,
                PixelFormat.RGBA_8888,
                2,
            )
            reader.setOnImageAvailableListener(
                { imageReader -> imageReader.acquireLatestImage()?.close() },
                handler,
            )
            return DiscardingFrameSurface(reader)
        }
    }
}
