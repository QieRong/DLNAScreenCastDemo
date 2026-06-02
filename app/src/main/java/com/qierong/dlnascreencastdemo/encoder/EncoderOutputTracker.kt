package com.qierong.dlnascreencastdemo.encoder

data class EncoderRuntimeStats(
    val hasCsd0: Boolean = false,
    val hasCsd1: Boolean = false,
    val codecConfigBufferCount: Long = 0,
    val encodedFrameCount: Long = 0,
    val hasFirstMediaFrame: Boolean = false,
    val hasFirstKeyFrame: Boolean = false,
)

data class EncoderOutputEvent(
    val isCodecConfig: Boolean,
    val isFirstMediaFrame: Boolean,
    val isFirstKeyFrame: Boolean,
    val stats: EncoderRuntimeStats,
)

class EncoderOutputTracker {
    private var stats = EncoderRuntimeStats()

    @Synchronized
    fun recordOutputFormat(hasCsd0: Boolean, hasCsd1: Boolean): EncoderRuntimeStats {
        stats = stats.copy(hasCsd0 = hasCsd0, hasCsd1 = hasCsd1)
        return stats
    }

    @Synchronized
    fun recordBuffer(isCodecConfig: Boolean, isKeyFrame: Boolean): EncoderOutputEvent {
        if (isCodecConfig) {
            stats = stats.copy(codecConfigBufferCount = stats.codecConfigBufferCount + 1)
            return EncoderOutputEvent(
                isCodecConfig = true,
                isFirstMediaFrame = false,
                isFirstKeyFrame = false,
                stats = stats,
            )
        }
        val isFirstMediaFrame = !stats.hasFirstMediaFrame
        val isFirstKeyFrame = isKeyFrame && !stats.hasFirstKeyFrame
        stats = stats.copy(
            encodedFrameCount = stats.encodedFrameCount + 1,
            hasFirstMediaFrame = true,
            hasFirstKeyFrame = stats.hasFirstKeyFrame || isKeyFrame,
        )
        return EncoderOutputEvent(
            isCodecConfig = false,
            isFirstMediaFrame = isFirstMediaFrame,
            isFirstKeyFrame = isFirstKeyFrame,
            stats = stats,
        )
    }

    @Synchronized
    fun snapshot(): EncoderRuntimeStats = stats
}
