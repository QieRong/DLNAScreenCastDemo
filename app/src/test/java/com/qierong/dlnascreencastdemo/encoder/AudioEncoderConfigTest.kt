package com.qierong.dlnascreencastdemo.encoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEncoderConfigTest {
    @Test
    fun mimeType_isAacLatm() {
        assertEquals("audio/mp4a-latm", AudioEncoderConfig.MIME_TYPE)
    }

    @Test
    fun bitRate_is128Kbps() {
        assertEquals(128_000, AudioEncoderConfig.BIT_RATE)
    }

    @Test
    fun sampleRate_is48000() {
        assertEquals(48_000, AudioEncoderConfig.SAMPLE_RATE)
    }

    @Test
    fun channelCount_isOne() {
        assertEquals(1, AudioEncoderConfig.CHANNEL_COUNT)
    }

    @Test
    fun samplesPerFrame_is1024() {
        // AAC-LC 标准帧长固定 1024 样本
        assertEquals(1_024, AudioEncoderConfig.SAMPLES_PER_FRAME)
    }

    @Test
    fun bytesPerFrame_matchesSamplesAndChannels() {
        // Int16 PCM：每样本 2 字节 × channels × samples
        val expected = AudioEncoderConfig.SAMPLES_PER_FRAME * 2 * AudioEncoderConfig.CHANNEL_COUNT
        assertEquals(expected, AudioEncoderConfig.BYTES_PER_FRAME)
    }

    @Test
    fun frameDurationUs_isPositiveAndReasonable() {
        // 1024 / 48000 ≈ 21333 μs；允许整数误差
        val expected = AudioEncoderConfig.SAMPLES_PER_FRAME * 1_000_000L / AudioEncoderConfig.SAMPLE_RATE
        assertEquals(expected, AudioEncoderConfig.FRAME_DURATION_US)
        assertTrue("帧时长应大于 0", AudioEncoderConfig.FRAME_DURATION_US > 0)
    }

    @Test
    fun testToneAmplitude_isLessThanOne() {
        // 测试音量不应超过满幅，避免爆音
        assertTrue(
            "测试音量 ${AudioEncoderConfig.TEST_TONE_AMPLITUDE} 不应超过 1.0",
            AudioEncoderConfig.TEST_TONE_AMPLITUDE < 1.0,
        )
    }

    @Test
    fun adtsHeaderSize_isSeven() {
        // ADTS 无 CRC 头部固定 7 字节
        assertEquals(7, AudioEncoderConfig.ADTS_HEADER_SIZE)
    }

    @Test
    fun adtsSampleRateIndex_is3For48000Hz() {
        // 标准 ADTS 采样率索引表：3 → 48000 Hz
        assertEquals(3, AudioEncoderConfig.ADTS_SAMPLE_RATE_INDEX)
    }
}
