package com.qierong.dlnascreencastdemo.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.qierong.dlnascreencastdemo.encoder.AndroidAvcEncoderCatalog
import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.EncoderConfigSelector
import com.qierong.dlnascreencastdemo.encoder.VideoEncoder
import com.qierong.dlnascreencastdemo.stream.LocalStreamServer
import com.qierong.dlnascreencastdemo.stream.MpegTsStreamPipeline
import com.qierong.dlnascreencastdemo.stream.StreamUrlProvider
import java.util.concurrent.atomic.AtomicBoolean

internal class ScreenCaptureManager(
    private val mediaProjection: MediaProjection,
    private val configProvider: AndroidCaptureConfigProvider,
    private val onSessionChanged: (CaptureSessionInfo) -> Unit,
    private val onReconfiguring: (CaptureSessionInfo, CaptureConfig) -> Unit,
    private val onError: (String) -> Unit,
    private val onReleased: () -> Unit,
    private val encoderCatalog: AndroidAvcEncoderCatalog = AndroidAvcEncoderCatalog(),
    private val encoderConfigSelector: EncoderConfigSelector = EncoderConfigSelector(),
    private val streamServer: LocalStreamServer = LocalStreamServer(),
    private val streamUrlProvider: StreamUrlProvider = StreamUrlProvider(),
) {
    private val released = AtomicBoolean(false)
    private val releaseNotified = AtomicBoolean(false)
    private val workerThread = HandlerThread("ScreenCaptureFrames").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private var callbackRegistered = false
    private var videoEncoder: VideoEncoder? = null
    private var sessionInfo: CaptureSessionInfo? = null
    private var streamUrl: String? = null
    private val resizeDebouncer = LatestValueDebouncer<CaptureConfig>(
        schedule = { runnable -> workerHandler.postDelayed(runnable, RESIZE_DEBOUNCE_MS) },
        cancel = workerHandler::removeCallbacks,
        consume = ::applyResize,
    )
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
            resizeDebouncer.submit(CaptureConfig(width, height, densityDpi))
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.d(TAG, "采集内容可见性变化：$isVisible")
        }
    }

    fun start(initialConfig: CaptureConfig) {
        check(!released.get()) { "屏幕采集会话已释放" }
        val streamPort = streamServer.start()
        streamUrl = requireNotNull(streamUrlProvider.resolve(streamPort)) {
            "未检测到可用于 PC 播放的局域网 IPv4 地址"
        }
        Log.i(STREAM_TAG, "本地流地址：$streamUrl")
        val initialEncoder = createVideoEncoder(initialConfig)
        videoEncoder = initialEncoder
        mediaProjection.registerCallback(projectionCallback, workerHandler)
        callbackRegistered = true
        check(
            virtualDisplayLifecycle.create(
                initialConfig.toDisplayConfig(initialEncoder.activeConfig),
                initialEncoder.inputSurface,
            ),
        ) {
            "无法创建 VirtualDisplay"
        }
        val initialSession = CaptureSessionInfo(initialConfig, initialEncoder.activeConfig, streamUrl)
        sessionInfo = initialSession
        Log.i(
            TAG,
            "屏幕采集已启动：source=${initialConfig.width}x${initialConfig.height}，" +
                "encoder=${initialEncoder.activeConfig.config.width}x" +
                "${initialEncoder.activeConfig.config.height}",
        )
        onSessionChanged(initialSession)
    }

    fun refreshConfig() {
        resizeDebouncer.submit(configProvider.current())
    }

    fun stop() {
        release(stopProjection = true)
    }

    @Synchronized
    private fun applyResize(config: CaptureConfig) {
        if (released.get()) return
        val currentSession = sessionInfo ?: return
        val targetEncoderConfig = runCatching {
            selectEncoderConfig(config)
        }.getOrElse { exception ->
            failAndRelease(exception.message ?: "无法选择 H.264 编码参数")
            return
        }
        if (currentSession.encoderConfig.hasSameCanvas(targetEncoderConfig)) {
            val updatedSession = CaptureSessionInfo(config, currentSession.encoderConfig, streamUrl)
            sessionInfo = updatedSession
            Log.d(TAG, "编码画布未变化，忽略重复重建请求")
            onSessionChanged(updatedSession)
            return
        }
        onReconfiguring(currentSession, config)
        val replacement = runCatching {
            createVideoEncoder(targetEncoderConfig)
        }.getOrElse { exception ->
            failAndRelease(exception.message ?: "无法重建 H.264 编码器")
            return
        }
        val displayConfig = config.toDisplayConfig(replacement.activeConfig)
        val resized = runCatching {
            virtualDisplayLifecycle.resize(displayConfig, replacement.inputSurface)
        }.getOrElse { exception ->
            replacement.stop()
            failAndRelease(exception.message ?: "无法替换 H.264 编码 Surface")
            return
        }
        if (!resized) {
            replacement.stop()
            failAndRelease("无法替换 H.264 编码 Surface")
            return
        }
        val previous = videoEncoder
        videoEncoder = replacement
        previous?.stop()
        val updatedSession = CaptureSessionInfo(config, replacement.activeConfig, streamUrl)
        sessionInfo = updatedSession
        Log.i(
            TAG,
            "H.264 编码器已按尺寸变化重建：source=${config.width}x${config.height}，" +
                "encoder=${displayConfig.width}x${displayConfig.height}",
        )
        onSessionChanged(updatedSession)
    }

    private fun createVideoEncoder(sourceConfig: CaptureConfig): VideoEncoder =
        createVideoEncoder(selectEncoderConfig(sourceConfig))

    private fun createVideoEncoder(activeConfig: ActiveEncoderConfig): VideoEncoder =
        VideoEncoder.create(
            activeConfig = activeConfig,
            onError = ::failAndRelease,
            outputSink = MpegTsStreamPipeline { data, replayOnConnect ->
                streamServer.publish(data, replayOnConnect)
            }.also {
                streamServer.clearReplayChunk()
            },
        )

    private fun selectEncoderConfig(sourceConfig: CaptureConfig): ActiveEncoderConfig =
        requireNotNull(
            encoderConfigSelector.select(sourceConfig, encoderCatalog.listCapabilities()),
        ) {
            "当前设备没有支持标准编码画布的 H.264 Surface encoder"
        }

    private fun CaptureConfig.toDisplayConfig(encoderConfig: ActiveEncoderConfig): CaptureConfig =
        CaptureConfig(
            width = encoderConfig.config.width,
            height = encoderConfig.config.height,
            densityDpi = densityDpi,
        )

    private fun ActiveEncoderConfig.hasSameCanvas(other: ActiveEncoderConfig): Boolean =
        config.width == other.config.width && config.height == other.config.height

    private fun failAndRelease(detail: String) {
        Log.e(TAG, detail)
        release(stopProjection = true, notifyReleased = false)
        onError(detail)
    }

    private fun release(stopProjection: Boolean, notifyReleased: Boolean = true) {
        if (!released.compareAndSet(false, true)) return
        resizeDebouncer.cancel()
        val cleanup = CaptureResourceCleanup(
            releaseVirtualDisplay = virtualDisplayLifecycle::release,
            releaseVideoEncoder = {
                videoEncoder?.stop()
                videoEncoder = null
            },
            releaseStreamServer = {
                streamServer.stop()
                Log.i(STREAM_TAG, "本地流服务已停止")
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
        if (notifyReleased && releaseNotified.compareAndSet(false, true)) onReleased()
    }

    companion object {
        private const val TAG = "ScreenCapture"
        private const val STREAM_TAG = "StreamServer"
        private const val VIRTUAL_DISPLAY_NAME = "DLNAScreenCastDemo"
        private const val RESIZE_DEBOUNCE_MS = 250L
    }
}
