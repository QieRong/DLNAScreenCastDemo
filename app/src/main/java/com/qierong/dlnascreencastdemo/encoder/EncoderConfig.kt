package com.qierong.dlnascreencastdemo.encoder

data class EncoderConfig(
    val width: Int,
    val height: Int,
    val videoBitrate: Int,
    val frameRate: Int = DEFAULT_FRAME_RATE,
    val iFrameIntervalSeconds: Int = DEFAULT_I_FRAME_INTERVAL_SECONDS,
    val mimeType: String = MIME_TYPE_AVC,
    val bitrateMode: BitrateMode,
) {
    init {
        require(width > 0) { "Encoder width must be positive" }
        require(height > 0) { "Encoder height must be positive" }
        require(videoBitrate > 0) { "Encoder bitrate must be positive" }
        require(frameRate > 0) { "Encoder frameRate must be positive" }
        require(iFrameIntervalSeconds > 0) { "Encoder iFrameIntervalSeconds must be positive" }
        require(mimeType.isNotBlank()) { "Encoder mimeType must not be blank" }
    }

    companion object {
        const val MIME_TYPE_AVC = "video/avc"
        const val TARGET_VIDEO_BITRATE = 8_000_000
        const val DEFAULT_FRAME_RATE = 30
        const val DEFAULT_I_FRAME_INTERVAL_SECONDS = 1
    }
}

enum class BitrateMode {
    CBR,
    DEFAULT,
}

data class ActiveEncoderConfig(
    val codecName: String,
    val config: EncoderConfig,
    val isDegraded: Boolean,
)
