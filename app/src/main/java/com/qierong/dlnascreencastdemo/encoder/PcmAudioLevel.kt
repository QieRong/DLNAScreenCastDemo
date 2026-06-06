package com.qierong.dlnascreencastdemo.encoder

import kotlin.math.abs
import kotlin.math.sqrt

data class PcmAudioLevel(
    val peak: Int,
    val rms: Int,
) {
    val isProbablySilent: Boolean
        get() = peak < PEAK_SILENCE_THRESHOLD && rms < RMS_SILENCE_THRESHOLD

    companion object {
        const val PEAK_SILENCE_THRESHOLD = 512
        const val RMS_SILENCE_THRESHOLD = 128
    }
}

object PcmAudioLevelAnalyzer {
    fun analyzePcm16LittleEndian(pcm: ByteArray): PcmAudioLevel {
        if (pcm.size < 2) return PcmAudioLevel(peak = 0, rms = 0)
        var peak = 0
        var sumSquares = 0.0
        var sampleCount = 0
        var index = 0
        while (index + 1 < pcm.size) {
            val low = pcm[index].toInt() and 0xff
            val high = pcm[index + 1].toInt()
            val sample = (high shl 8) or low
            val amplitude = abs(sample)
            if (amplitude > peak) peak = amplitude
            sumSquares += sample.toDouble() * sample.toDouble()
            sampleCount++
            index += 2
        }
        if (sampleCount == 0) return PcmAudioLevel(peak = 0, rms = 0)
        return PcmAudioLevel(
            peak = peak,
            rms = sqrt(sumSquares / sampleCount).toInt(),
        )
    }
}
