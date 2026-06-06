package com.qierong.dlnascreencastdemo.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * AAC-LC 音频编码器。
 *
 * PR14 起该编码器只接收外部 PCM 输入，不再生成 App 内 1kHz 测试音。
 * PCM 来源应为 AudioPlaybackCapture；编码异常仅降级音频链路，不中断视频投屏。
 */
class AudioEncoder(
    private val onAudioFrame: (adtsFrame: ByteArray, presentationTimeUs: Long) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val started = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val outputFrameCount = AtomicLong(0L)

    private var codec: MediaCodec? = null
    private val workerThread = HandlerThread("AudioEncoderWorker").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return true
        return try {
            codec = createAndStartCodec()
            Log.i(
                TAG,
                "AAC 编码器已启动：mime=${AudioEncoderConfig.MIME_TYPE}，" +
                    "rate=${AudioEncoderConfig.SAMPLE_RATE}，" +
                    "ch=${AudioEncoderConfig.CHANNEL_COUNT}，" +
                    "bitrate=${AudioEncoderConfig.BIT_RATE}",
            )
            true
        } catch (exception: Exception) {
            Log.e(TAG, "AAC 编码器启动失败：${exception.message}", exception)
            releaseCodecSafely()
            onError("AAC 编码器启动失败：${exception.message}")
            false
        }
    }

    fun queuePcmFrame(pcm: ByteArray, presentationTimeUs: Long) {
        if (!started.get() || released.get()) return
        workerHandler.post {
            if (released.get()) return@post
            feedPcmFrame(pcm, presentationTimeUs)
            drainAvailableOutput()
        }
    }

    fun stop() {
        if (!released.compareAndSet(false, true)) return
        workerHandler.removeCallbacksAndMessages(null)
        releaseCodecSafely()
        workerThread.quitSafely()
        Log.i(TAG, "AAC 编码器已停止，共输出 ${outputFrameCount.get()} 帧")
    }

    private fun createAndStartCodec(): MediaCodec {
        val format = MediaFormat.createAudioFormat(
            AudioEncoderConfig.MIME_TYPE,
            AudioEncoderConfig.SAMPLE_RATE,
            AudioEncoderConfig.CHANNEL_COUNT,
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AudioEncoderConfig.BIT_RATE)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
        }
        val codec = MediaCodec.createEncoderByType(AudioEncoderConfig.MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        return codec
    }

    private fun feedPcmFrame(pcm: ByteArray, presentationTimeUs: Long) {
        val currentCodec = codec ?: return
        val inputIndex = runCatching {
            currentCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        }.getOrElse { exception ->
            Log.w(TAG, "dequeueInputBuffer 异常：${exception.message}")
            return
        }
        if (inputIndex < 0) return

        val inputBuffer: ByteBuffer = currentCodec.getInputBuffer(inputIndex) ?: run {
            currentCodec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
            return
        }
        inputBuffer.clear()
        val writeSize = minOf(pcm.size, inputBuffer.remaining())
        inputBuffer.put(pcm, 0, writeSize)
        currentCodec.queueInputBuffer(inputIndex, 0, writeSize, presentationTimeUs, 0)
    }

    private fun drainAvailableOutput() {
        val currentCodec = codec ?: return
        while (true) {
            val outputIndex = runCatching {
                currentCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            }.getOrElse { exception ->
                Log.w(TAG, "dequeueOutputBuffer 异常：${exception.message}")
                onError("AAC 编码失败：${exception.message}")
                return
            }
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.i(TAG, "AAC 编码器输出格式已更新")
                }
                outputIndex >= 0 -> {
                    val isCodecConfig =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (isCodecConfig) {
                        Log.d(TAG, "跳过 AAC codec_config 帧（csd-0）")
                        currentCodec.releaseOutputBuffer(outputIndex, false)
                        continue
                    }

                    val size = bufferInfo.size
                    if (size > 0) {
                        val rawAac = currentCodec.getOutputBuffer(outputIndex)
                            ?.let { buf ->
                                ByteArray(size).also { bytes ->
                                    buf.position(bufferInfo.offset)
                                    buf.limit(bufferInfo.offset + size)
                                    buf.get(bytes)
                                }
                            }
                        if (rawAac != null) {
                            val adtsFrame = buildAdtsFrame(rawAac)
                            val count = outputFrameCount.incrementAndGet()
                            if (count == 1L) {
                                Log.i(
                                    TAG,
                                    "输出第一帧 AAC，pts=${bufferInfo.presentationTimeUs} μs",
                                )
                            }
                            onAudioFrame(adtsFrame, bufferInfo.presentationTimeUs)
                        }
                    }
                    currentCodec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun buildAdtsFrame(rawAac: ByteArray): ByteArray {
        val totalSize = AudioEncoderConfig.ADTS_HEADER_SIZE + rawAac.size
        val profile = 1
        val freqIdx = AudioEncoderConfig.ADTS_SAMPLE_RATE_INDEX
        val chanCfg = AudioEncoderConfig.CHANNEL_COUNT

        val header = byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            ((profile shl 6) or (freqIdx shl 2) or (chanCfg shr 2)).toByte(),
            (((chanCfg and 0x3) shl 6) or ((totalSize shr 11) and 0x3)).toByte(),
            ((totalSize shr 3) and 0xFF).toByte(),
            (((totalSize and 0x7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
        return header + rawAac
    }

    private fun releaseCodecSafely() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    companion object {
        private const val TAG = "AudioEncoder"
        private const val DEQUEUE_TIMEOUT_US = 0L
    }
}
