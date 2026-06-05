package com.qierong.dlnascreencastdemo.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/**
 * AAC-LC 音频编码器。
 *
 * 内置 1kHz 正弦波 PCM 生成器，通过 MediaCodec 编码为 AAC-LC，
 * 每帧输出前补全 ADTS 头，再通过 [onAudioFrame] 回调传出。
 *
 * 历史测试音来源：App 内生成测试音轨，不代表系统内录或麦克风采集。
 * PR13 起该编码器不再由投屏运行时默认启动。
 *
 * 音频失败策略：编码异常仅记录日志，不中断视频投屏链路。
 */
class AudioEncoder(
    private val onAudioFrame: (adtsFrame: ByteArray, presentationTimeUs: Long) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val started = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    /** 已生成的 PCM 样本总数，用于计算 PTS */
    private val totalSamplesProduced = AtomicLong(0L)

    /** 已输出的 AAC 帧数，用于日志 */
    private val outputFrameCount = AtomicLong(0L)

    private var codec: MediaCodec? = null
    private val workerThread = HandlerThread("AudioEncoderWorker").apply { start() }
    private val workerHandler = Handler(workerThread.looper)

    /** MediaCodec.BufferInfo，仅在 workerThread 上使用，无需线程安全 */
    private val bufferInfo = MediaCodec.BufferInfo()

    /** 持续推送 PCM 帧的 Runnable，每帧结束后自行 post 延迟下一帧 */
    private val feedRunnable = object : Runnable {
        override fun run() {
            if (released.get()) return
            feedOnePcmFrame()
            drainAvailableOutput()
            if (!released.get()) {
                // 以帧时长为周期推送下一帧，不积压
                workerHandler.postDelayed(this, FEED_INTERVAL_MS)
            }
        }
    }

    /**
     * 启动编码器：初始化 MediaCodec，开始生成测试音轨。
     * 必须在调用线程中执行，返回后编码在后台 [workerThread] 持续运行。
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            val codec = createAndStartCodec()
            this.codec = codec
            Log.i(TAG, "AAC 编码器已启动：mime=${AudioEncoderConfig.MIME_TYPE}，" +
                "rate=${AudioEncoderConfig.SAMPLE_RATE}，" +
                "ch=${AudioEncoderConfig.CHANNEL_COUNT}，" +
                "bitrate=${AudioEncoderConfig.BIT_RATE}")
            workerHandler.post(feedRunnable)
        } catch (exception: Exception) {
            Log.e(TAG, "AAC 编码器启动失败：${exception.message}", exception)
            releaseCodecSafely()
            onError("AAC 编码器启动失败：${exception.message}")
        }
    }

    /**
     * 停止编码器并释放所有资源。
     * 可安全重复调用。
     */
    fun stop() {
        if (!released.compareAndSet(false, true)) return
        workerHandler.removeCallbacks(feedRunnable)
        releaseCodecSafely()
        workerThread.quitSafely()
        Log.i(TAG, "AAC 编码器已停止，共输出 ${outputFrameCount.get()} 帧")
    }

    // ── 私有实现 ──────────────────────────────────────────────────────────────

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

    /**
     * 生成一帧 1kHz 正弦波 PCM，送入 MediaCodec 输入缓冲区。
     * presentationTimeUs 基于已生成样本数计算，保证单调递增。
     */
    private fun feedOnePcmFrame() {
        val currentCodec = codec ?: return
        val sampleOffset = totalSamplesProduced.get()
        val presentationTimeUs = sampleOffset * 1_000_000L / AudioEncoderConfig.SAMPLE_RATE

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

        // 生成 1kHz 正弦波 PCM（Int16，小端序）
        val pcmData = generateSineWavePcm(sampleOffset)
        inputBuffer.put(pcmData)

        currentCodec.queueInputBuffer(
            inputIndex,
            0,
            pcmData.size,
            presentationTimeUs,
            0,
        )
        totalSamplesProduced.addAndGet(AudioEncoderConfig.SAMPLES_PER_FRAME.toLong())
    }

    /**
     * 生成从 [sampleOffset] 开始的一帧正弦波 PCM 数据。
     * 幅度为 [AudioEncoderConfig.TEST_TONE_AMPLITUDE] 倍的 Short.MAX_VALUE。
     *
     * @param sampleOffset 全局样本偏移，保证跨帧相位连续
     * @return Int16 PCM 字节数组（小端序，CHANNEL_COUNT=1）
     */
    private fun generateSineWavePcm(sampleOffset: Long): ByteArray {
        val angularFreq = 2.0 * PI * AudioEncoderConfig.TEST_TONE_FREQUENCY_HZ /
            AudioEncoderConfig.SAMPLE_RATE
        val amplitude = AudioEncoderConfig.TEST_TONE_AMPLITUDE * Short.MAX_VALUE
        val pcm = ByteArray(AudioEncoderConfig.BYTES_PER_FRAME)
        val byteBuffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = byteBuffer.asShortBuffer()
        for (i in 0 until AudioEncoderConfig.SAMPLES_PER_FRAME) {
            val sample = (sin(angularFreq * (sampleOffset + i)) * amplitude).toInt().toShort()
            shortBuffer.put(sample)
        }
        return pcm
    }

    /**
     * 轮询 MediaCodec 输出缓冲区，处理已编码的 AAC 帧。
     *
     * 跳过 BUFFER_FLAG_CODEC_CONFIG（csd-0 不写入 TS）。
     * 每帧输出前拼接 7 字节 ADTS 头。
     */
    private fun drainAvailableOutput() {
        val currentCodec = codec ?: return
        while (true) {
            val outputIndex = runCatching {
                currentCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            }.getOrElse { exception ->
                Log.w(TAG, "dequeueOutputBuffer 异常：${exception.message}")
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
                        // csd-0 是解码器初始化数据，不写入 TS 流
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
                                Log.i(TAG, "输出第一帧 AAC，pts=${bufferInfo.presentationTimeUs} μs")
                            }
                            onAudioFrame(adtsFrame, bufferInfo.presentationTimeUs)
                        }
                    }
                    currentCodec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    /**
     * 在 raw AAC ES 帧前拼接 7 字节 ADTS 头（无 CRC）。
     *
     * ADTS 字段布局（按 ISO 14496-3）：
     * - syncword (12b) = 0xFFF
     * - ID (1b) = 0 (MPEG-4)
     * - layer (2b) = 00
     * - protection_absent (1b) = 1
     * - profile_ObjectType (2b) = 01 (AAC-LC)
     * - sampling_frequency_index (4b) = 3 (48000 Hz)
     * - private_bit (1b) = 0
     * - channel_configuration (3b) = 001 (mono)
     * - originality/copy/home/copyright (4b) = 0000
     * - aac_frame_length (13b) = 7 + rawAac.size
     * - adts_buffer_fullness (11b) = 0x7FF (VBR)
     * - number_of_raw_data_blocks (2b) = 0
     *
     * @param rawAac 裸 AAC ES 字节（不含 ADTS 头）
     * @return ADTS 头 + rawAac 的完整帧
     */
    private fun buildAdtsFrame(rawAac: ByteArray): ByteArray {
        val totalSize = AudioEncoderConfig.ADTS_HEADER_SIZE + rawAac.size
        val profile = 1          // AAC-LC：profile_ObjectType = 1
        val freqIdx = AudioEncoderConfig.ADTS_SAMPLE_RATE_INDEX  // 3 → 48000 Hz
        val chanCfg = AudioEncoderConfig.CHANNEL_COUNT            // 1 = mono

        val header = byteArrayOf(
            // Byte 0: sync high 8 bits
            0xFF.toByte(),
            // Byte 1: sync[3:0]=1111, ID=0, layer=00, protection_absent=1 → 0xF1
            0xF1.toByte(),
            // Byte 2: profile[1:0], freq_idx[3:0], private=0, chan_cfg[2]
            ((profile shl 6) or (freqIdx shl 2) or (chanCfg shr 2)).toByte(),
            // Byte 3: chan_cfg[1:0], orig=0, home=0, copy=0, copy_start=0, frame_len[12:11]
            (((chanCfg and 0x3) shl 6) or ((totalSize shr 11) and 0x3)).toByte(),
            // Byte 4: frame_len[10:3]
            ((totalSize shr 3) and 0xFF).toByte(),
            // Byte 5: frame_len[2:0], buffer_fullness[10:6]=11111
            (((totalSize and 0x7) shl 5) or 0x1F).toByte(),
            // Byte 6: buffer_fullness[5:0]=111111, num_raw_blocks=00 → 0xFC
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
        /** dequeueInputBuffer / dequeueOutputBuffer 超时：非阻塞 */
        private const val DEQUEUE_TIMEOUT_US = 0L
        /** 每次 feedRunnable 的间隔（毫秒），略小于 AAC 帧时长（≈21ms）避免积压 */
        private const val FEED_INTERVAL_MS = 20L
    }
}
