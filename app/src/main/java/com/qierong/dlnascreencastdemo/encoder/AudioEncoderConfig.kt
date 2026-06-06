package com.qierong.dlnascreencastdemo.encoder

/**
 * AAC-LC 编码目标参数。
 *
 * PR14 起这些参数用于真实 AudioPlaybackCapture PCM 的 AAC 编码。
 */
object AudioEncoderConfig {
    /** MIME 类型：AAC-LC Low Complexity */
    const val MIME_TYPE = "audio/mp4a-latm"

    /** 目标码率：128 Kbps */
    const val BIT_RATE = 128_000

    /** 采样率：48000 Hz */
    const val SAMPLE_RATE = 48_000

    /** 声道数：单声道，减少播放端兼容风险 */
    const val CHANNEL_COUNT = 1

    /**
     * 每帧 PCM 样本数。
     * AAC-LC 标准帧长固定为 1024 样本。
     */
    const val SAMPLES_PER_FRAME = 1_024

    /**
     * 每帧 PCM 字节数 = samples × 2 bytes/sample × channels。
     * Int16 PCM：每样本 2 字节。
     */
    const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 * CHANNEL_COUNT

    /**
     * 每帧时长（微秒）= samples × 1_000_000 / sampleRate。
     * 1024 / 48000 ≈ 21333 μs
     */
    const val FRAME_DURATION_US = SAMPLES_PER_FRAME * 1_000_000L / SAMPLE_RATE

    /** ADTS 头固定长度（字节），无 CRC 版本 */
    const val ADTS_HEADER_SIZE = 7

    /**
     * ADTS 采样率索引表对应 48000 Hz 的值。
     * 标准表：0→96k, 1→88.2k, 2→64k, 3→48k, 4→44.1k ...
     */
    const val ADTS_SAMPLE_RATE_INDEX = 3
}
