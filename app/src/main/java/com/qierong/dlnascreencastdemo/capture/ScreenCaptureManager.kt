package com.qierong.dlnascreencastdemo.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

internal class ScreenCaptureManager(
    private val mediaProjection: MediaProjection,
    private val configProvider: AndroidCaptureConfigProvider,
    private val onConfigChanged: (CaptureConfig) -> Unit,
    private val onReleased: () -> Unit,
) {
    private val released = AtomicBoolean(false)
    private val releaseNotified = AtomicBoolean(false)
    private val workerThread = HandlerThread("ScreenCaptureFrames").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private var callbackRegistered = false
    private var frameSurface: DiscardingFrameSurface? = null
    private val virtualDisplayLifecycle = VirtualDisplayLifecycle<Surface, VirtualDisplay>(
        createDisplay = { config, surface ->
            mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                config.width,
                config.height,
                config.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                workerHandler,
            )
        },
        resizeDisplay = { display, config ->
            display.resize(config.width, config.height, config.densityDpi)
        },
        setDisplaySurface = VirtualDisplay::setSurface,
        releaseDisplay = VirtualDisplay::release,
    )
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "系统停止屏幕采集")
            release(stopProjection = false)
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            val densityDpi = configProvider.current().densityDpi
            resize(CaptureConfig(width, height, densityDpi))
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.d(TAG, "采集内容可见性变化：$isVisible")
        }
    }

    fun start(initialConfig: CaptureConfig) {
        check(!released.get()) { "屏幕采集会话已释放" }
        val initialFrameSurface = DiscardingFrameSurface.create(initialConfig, workerHandler)
        frameSurface = initialFrameSurface
        mediaProjection.registerCallback(projectionCallback, workerHandler)
        callbackRegistered = true
        check(virtualDisplayLifecycle.create(initialConfig, initialFrameSurface.surface)) {
            "无法创建 VirtualDisplay"
        }
        Log.i(TAG, "屏幕采集已启动：${initialConfig.width}x${initialConfig.height}")
        onConfigChanged(initialConfig)
    }

    fun refreshConfig() {
        resize(configProvider.current())
    }

    fun stop() {
        release(stopProjection = true)
    }

    @Synchronized
    private fun resize(config: CaptureConfig) {
        if (released.get()) return
        if (!virtualDisplayLifecycle.needsResize(config)) return
        val replacement = runCatching {
            DiscardingFrameSurface.create(config, workerHandler)
        }.getOrElse { exception ->
            Log.e(TAG, "创建替换 Surface 失败", exception)
            return
        }
        val resized = runCatching {
            virtualDisplayLifecycle.resize(config, replacement.surface)
        }.getOrElse { exception ->
            Log.e(TAG, "调整屏幕采集尺寸失败", exception)
            replacement.stopConsuming()
            replacement.close()
            return
        }
        if (!resized) {
            replacement.stopConsuming()
            replacement.close()
            return
        }
        val previous = frameSurface
        frameSurface = replacement
        previous?.stopConsuming()
        previous?.close()
        Log.i(TAG, "屏幕采集尺寸已更新：${config.width}x${config.height}")
        onConfigChanged(config)
    }

    private fun release(stopProjection: Boolean) {
        if (!released.compareAndSet(false, true)) return
        val cleanup = CaptureResourceCleanup(
            stopFrameConsumer = { frameSurface?.stopConsuming() },
            releaseVirtualDisplay = virtualDisplayLifecycle::release,
            closeFrameSurface = {
                frameSurface?.close()
                frameSurface = null
            },
            unregisterProjectionCallback = {
                if (callbackRegistered) {
                    mediaProjection.unregisterCallback(projectionCallback)
                    callbackRegistered = false
                }
            },
            stopProjection = mediaProjection::stop,
            stopWorkerThread = workerThread::quitSafely,
        )
        cleanup.release(stopProjection)
        Log.i(TAG, "屏幕采集资源释放完成")
        if (releaseNotified.compareAndSet(false, true)) onReleased()
    }

    companion object {
        private const val TAG = "ScreenCapture"
        private const val VIRTUAL_DISPLAY_NAME = "DLNAScreenCastDemo"
    }
}
