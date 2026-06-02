package com.qierong.dlnascreencastdemo.encoder

import com.qierong.dlnascreencastdemo.capture.CaptureConfig

interface AvcEncoderCapabilities {
    val codecName: String
    val bitrateRange: IntRange
    val widthAlignment: Int
    val heightAlignment: Int
    val supportsCbr: Boolean

    fun areSizeAndRateSupported(width: Int, height: Int, frameRate: Double): Boolean
}

class EncoderConfigSelector {
    fun select(
        sourceConfig: CaptureConfig,
        capabilities: List<AvcEncoderCapabilities>,
    ): ActiveEncoderConfig? {
        val candidates = if (sourceConfig.width >= sourceConfig.height) {
            LANDSCAPE_CANDIDATES
        } else {
            LANDSCAPE_CANDIDATES.map { (width, height) -> height to width }
        }
        capabilities.forEach { capability ->
            candidates.forEach { (width, height) ->
                if (!isAligned(width, capability.widthAlignment)) return@forEach
                if (!isAligned(height, capability.heightAlignment)) return@forEach
                if (!capability.areSizeAndRateSupported(width, height, FRAME_RATE)) {
                    return@forEach
                }
                val bitrate = EncoderConfig.TARGET_VIDEO_BITRATE.coerceIn(capability.bitrateRange)
                val bitrateMode = if (capability.supportsCbr) BitrateMode.CBR else BitrateMode.DEFAULT
                val config = EncoderConfig(
                    width = width,
                    height = height,
                    videoBitrate = bitrate,
                    bitrateMode = bitrateMode,
                )
                return ActiveEncoderConfig(
                    codecName = capability.codecName,
                    config = config,
                    isDegraded = config.width != candidates.first().first ||
                        config.height != candidates.first().second ||
                        config.videoBitrate != EncoderConfig.TARGET_VIDEO_BITRATE ||
                        config.bitrateMode != BitrateMode.CBR,
                )
            }
        }
        return null
    }

    private fun isAligned(value: Int, alignment: Int): Boolean =
        alignment > 0 && value % alignment == 0

    private companion object {
        const val FRAME_RATE = EncoderConfig.DEFAULT_FRAME_RATE.toDouble()
        val LANDSCAPE_CANDIDATES = listOf(
            1920 to 1080,
            1280 to 720,
            854 to 480,
            640 to 360,
        )
    }
}
