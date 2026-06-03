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
import com.qierong.dlnascreencastdemo.encoder.AudioEncoder
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
    /** AAC 测试音轨编码器；音频失败不影响视频链路 */
    private var audioEncoder: AudioEncoder? = null
    /** 保存当前 pipeline 引用，供 AudioEncoder 回调使用 */
    private var streamPipeline: MpegTsStreamPipeline? = null
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
        val pipeline = MpegTsStreamPipeline { data, replayOnConnect ->
            streamServer.publish(data, replayOnConnect)
        }.also { streamServer.clearReplayChunk() }
        streamPipeline = pipeline
        val initialEncoder = createVideoEncoder(initialConfig, pipeline)
        videoEncoder = initialEncoder
        // 启动 AAC 测试音轨编码器（失败只记录日志，不中断视频链路）
        startAudioEncoder(pipeline)
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
            createVideoEncoder(targetEncoderConfig, requireNotNull(streamPipeline))
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

    private fun createVideoEncoder(sourceConfig: CaptureConfig, pipeline: MpegTsStreamPipeline): VideoEncoder =
        createVideoEncoder(selectEncoderConfig(sourceConfig), pipeline)

    /**
     * 创建 H.264 视频编码器，将输出接入指定的 [pipeline]。
     * 重用同一个 pipeline，避免分辨率变化时创建多个流服务实例。
     */
    private fun createVideoEncoder(activeConfig: ActiveEncoderConfig, pipeline: MpegTsStreamPipeline): VideoEncoder {
        pipeline.reset()
        return VideoEncoder.create(
            activeConfig = activeConfig,
            onError = ::failAndRelease,
            outputSink = pipeline,
        )
    }

    /**
     * 启动 AAC 测试音轨编码器。
     * 失败时仅记录日志，不中断视频投屏链路。
     *
     * @param pipeline 当前流媒体管道，音频帧将通过 [MpegTsStreamPipeline.onAudioAccessUnit] 送入
     */
    private fun startAudioEncoder(pipeline: MpegTsStreamPipeline) {
        val encoder = AudioEncoder(
            onAudioFrame = { adtsFrame, presentationTimeUs ->
                // 音频帧到达时封装并发布，异常不向上传播（不影响视频）
                runCatching {
                    pipeline.onAudioAccessUnit(adtsFrame, presentationTimeUs)
                }.onFailure { exception ->
                    Log.w(AUDIO_TAG, "AAC 帧封装异常（已忽略）：${exception.message}")
                }
            },
            onError = { detail ->
                // 音频编码失败：记录日志，降级为 video-only，不中断视频投屏
                Log.e(AUDIO_TAG, "AAC 编码器错误，降级为 video-only：$detail")
                audioEncoder = null
            },
        )
        audioEncoder = encoder
        encoder.start()
    }

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
        // 首先停止音频编码器，避免音频帧在流服务实例将要关闭时继续写入
        audioEncoder?.stop()
        audioEncoder = null
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
        private const val AUDIO_TAG = "AudioEncoder"
        private const val VIRTUAL_DISPLAY_NAME = "DLNAScreenCastDemo"
        private const val RESIZE_DEBOUNCE_MS = 250L
    }
}
