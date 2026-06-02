package com.qierong.dlnascreencastdemo.capture

import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig

data class CaptureSessionInfo(
    val sourceConfig: CaptureConfig,
    val encoderConfig: ActiveEncoderConfig,
)
