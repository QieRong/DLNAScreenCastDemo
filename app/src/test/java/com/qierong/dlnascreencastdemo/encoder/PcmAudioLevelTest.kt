package com.qierong.dlnascreencastdemo.encoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmAudioLevelTest {
    @Test
    fun analyzePcm16LittleEndian_silenceIsProbablySilent() {
        val level = PcmAudioLevelAnalyzer.analyzePcm16LittleEndian(ByteArray(2048))

        assertEquals(0, level.peak)
        assertEquals(0, level.rms)
        assertTrue(level.isProbablySilent)
    }

    @Test
    fun analyzePcm16LittleEndian_nonSilentFrameReportsPeakAndRms() {
        val pcm = ByteArray(8)
        writeSample(pcm, offset = 0, sample = 10_000)
        writeSample(pcm, offset = 2, sample = -10_000)
        writeSample(pcm, offset = 4, sample = 5_000)
        writeSample(pcm, offset = 6, sample = -5_000)

        val level = PcmAudioLevelAnalyzer.analyzePcm16LittleEndian(pcm)

        assertEquals(10_000, level.peak)
        assertFalse(level.isProbablySilent)
        assertTrue(level.rms > PcmAudioLevel.RMS_SILENCE_THRESHOLD)
    }

    private fun writeSample(pcm: ByteArray, offset: Int, sample: Int) {
        pcm[offset] = (sample and 0xff).toByte()
        pcm[offset + 1] = ((sample shr 8) and 0xff).toByte()
    }
}
