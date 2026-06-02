package com.qierong.dlnascreencastdemo.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoder private constructor(
    val activeConfig: ActiveEncoderConfig,
    private val codec: MediaCodec,
    val inputSurface: Surface,
    private val workerThread: HandlerThread,
    private val onError: (String) -> Unit,
) {
    private val workerHandler = Handler(workerThread.looper)
    private val outputTracker = EncoderOutputTracker()
    private val draining = AtomicBoolean(true)
    private val released = AtomicBoolean(false)
    private val bufferInfo = MediaCodec.BufferInfo()
    private val drainRunnable = object : Runnable {
        override fun run() {
            if (!draining.get()) return
            runCatching {
                drainAvailableOutput()
            }.onFailure { exception ->
                Log.e(TAG, "H.264 编码输出处理失败", exception)
                if (!released.get()) onError(exception.message ?: "H.264 编码输出处理失败")
                return
            }
            if (draining.get()) workerHandler.postDelayed(this, DRAIN_INTERVAL_MS)
        }
    }
    private val releaseSequence = VideoEncoderRelease(
        signalEndOfInputStream = codec::signalEndOfInputStream,
        drainRemainingOutput = ::drainRemainingOutput,
        stopCodec = codec::stop,
        releaseCodec = codec::release,
        releaseInputSurface = inputSurface::release,
        stopWorkerThread = workerThread::quitSafely,
    )

    init {
        workerHandler.post(drainRunnable)
    }

    fun stop() {
        if (!released.compareAndSet(false, true)) return
        draining.set(false)
        workerHandler.removeCallbacks(drainRunnable)
        releaseSequence.release()
        Log.i(TAG, "H.264 编码器已释放：${statsSummary(outputTracker.snapshot())}")
    }

    private fun drainRemainingOutput() {
        val deadline = SystemClock.elapsedRealtime() + FINAL_DRAIN_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!drainAvailableOutput()) SystemClock.sleep(FINAL_DRAIN_SLEEP_MS)
        }
    }

    private fun drainAvailableOutput(): Boolean {
        var drainedAnyBuffer = false
        while (true) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return drainedAnyBuffer
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> logOutputFormat(codec.outputFormat)
                else -> {
                    if (outputIndex < 0) return drainedAnyBuffer
                    drainedAnyBuffer = true
                    val event = outputTracker.recordBuffer(
                        isCodecConfig =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0,
                        isKeyFrame =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0,
                    )
                    logOutputEvent(event)
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun logOutputFormat(format: MediaFormat) {
        val hasCsd0 = format.containsKey(CSD_0)
        val hasCsd1 = format.containsKey(CSD_1)
        outputTracker.recordOutputFormat(hasCsd0, hasCsd1)
        Log.i(
            TAG,
            "H.264 actual output format：" +
                "mime=${format.stringValue(MediaFormat.KEY_MIME)}，" +
                "width=${format.intValue(MediaFormat.KEY_WIDTH)}，" +
                "height=${format.intValue(MediaFormat.KEY_HEIGHT)}，" +
                "bitrate=${format.intValue(MediaFormat.KEY_BIT_RATE)}，" +
                "fps=${format.intValue(MediaFormat.KEY_FRAME_RATE)}，" +
                "colorFormat=${format.intValue(MediaFormat.KEY_COLOR_FORMAT)}，" +
                "bitrateMode=${format.intValue(MediaFormat.KEY_BITRATE_MODE)}，" +
                "csd-0=$hasCsd0，csd-1=$hasCsd1",
        )
    }

    private fun logOutputEvent(event: EncoderOutputEvent) {
        when {
            event.isCodecConfig -> Log.i(
                TAG,
                "收到 codec config buffer：count=${event.stats.codecConfigBufferCount}",
            )

            event.isFirstMediaFrame -> Log.i(TAG, "收到 first media frame")
        }
        if (event.isFirstKeyFrame) Log.i(TAG, "收到 first key frame")
    }

    private fun MediaFormat.intValue(key: String): String =
        if (containsKey(key)) getInteger(key).toString() else "未提供"

    private fun MediaFormat.stringValue(key: String): String =
        if (containsKey(key)) getString(key).orEmpty() else "未提供"

    companion object {
        private const val TAG = "Encoder"
        private const val CSD_0 = "csd-0"
        private const val CSD_1 = "csd-1"
        private const val DEQUEUE_TIMEOUT_US = 0L
        private const val DRAIN_INTERVAL_MS = 10L
        private const val FINAL_DRAIN_TIMEOUT_MS = 150L
        private const val FINAL_DRAIN_SLEEP_MS = 10L

        fun create(
            activeConfig: ActiveEncoderConfig,
            onError: (String) -> Unit,
        ): VideoEncoder {
            val config = activeConfig.config
            val format = MediaFormat.createVideoFormat(config.mimeType, config.width, config.height)
                .apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
                    if (config.bitrateMode == BitrateMode.CBR) {
                        setInteger(
                            MediaFormat.KEY_BITRATE_MODE,
                            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
                        )
                    }
                }
            Log.i(
                TAG,
                "启动 H.264 编码器：codec=${activeConfig.codecName}，" +
                    "request=${config.width}x${config.height}，" +
                    "bitrate=${config.videoBitrate}，fps=${config.frameRate}，" +
                    "iFrame=${config.iFrameIntervalSeconds}s，mode=${config.bitrateMode}",
            )
            val workerThread = HandlerThread("VideoEncoderOutput").apply { start() }
            val codec = MediaCodec.createByCodecName(activeConfig.codecName)
            return try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = codec.createInputSurface()
                codec.start()
                VideoEncoder(
                    activeConfig = activeConfig,
                    codec = codec,
                    inputSurface = inputSurface,
                    workerThread = workerThread,
                    onError = onError,
                )
            } catch (exception: Exception) {
                runCatching(codec::release)
                workerThread.quitSafely()
                throw exception
            }
        }

        private fun statsSummary(stats: EncoderRuntimeStats): String =
            "csd-0=${stats.hasCsd0}，csd-1=${stats.hasCsd1}，" +
                "codecConfigBuffers=${stats.codecConfigBufferCount}，" +
                "encodedFrames=${stats.encodedFrameCount}，" +
                "firstMediaFrame=${stats.hasFirstMediaFrame}，" +
                "firstKeyFrame=${stats.hasFirstKeyFrame}"
    }
}
