package com.qierong.dlnascreencastdemo.capture

import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig

data class CaptureSessionInfo(
    val sourceConfig: CaptureConfig,
    val encoderConfig: ActiveEncoderConfig,
    val streamUrl: String? = null,
    val audioStatus: PlaybackAudioStatus = PlaybackAudioStatus.DegradedVideoOnly(
        "真实系统播放音尚未启用，当前为 video-only。",
    ),
)
