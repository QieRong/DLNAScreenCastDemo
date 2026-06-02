package com.qierong.dlnascreencastdemo.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.BitrateMode

@Composable
internal fun EncoderStatusCard(
    state: CaptureState,
    modifier: Modifier = Modifier,
) {
    val sessionInfo = when (state) {
        is CaptureState.Capturing -> state.sessionInfo
        is CaptureState.Reconfiguring -> state.sessionInfo
        else -> null
    }
    val detail = sessionInfo?.encoderConfig?.toUiText()
        ?: """
            优先选择 1080P 编码画布，实际配置取决于设备 H.264 encoder capabilities。
            当前尚未启动 H.264 编码。性能仍未实测。
        """.trimIndent()
    StatusCard(title = "视频编码参数", detail = detail, modifier = modifier)
}

@Composable
internal fun StreamStatusCard(
    state: CaptureState,
    modifier: Modifier = Modifier,
) {
    val streamUrl = when (state) {
        is CaptureState.Capturing -> state.sessionInfo.streamUrl
        is CaptureState.Reconfiguring -> state.sessionInfo.streamUrl
        else -> null
    }
    val detail = if (streamUrl == null) {
        "尚未启动本地流。开始采集后可在同一局域网 PC 使用 curl、ffprobe 或 ffplay 访问 /live.ts。"
    } else {
        """
            当前视频流：$streamUrl
            格式：MPEG-TS + H.264 视频
            说明：AAC 音频未实现，延迟仍未实测。
        """.trimIndent()
    }
    StatusCard(title = "本地流地址", detail = detail, modifier = modifier)
}

private fun ActiveEncoderConfig.toUiText(): String {
    val bitrateModeText = when (config.bitrateMode) {
        BitrateMode.CBR -> "CBR"
        BitrateMode.DEFAULT -> "默认 / 非 CBR"
    }
    return """
        编码器：$codecName
        实际编码画布：${config.width} x ${config.height}
        已配置视频码率：${config.videoBitrate / 1_000_000.0} Mbps
        码率模式：$bitrateModeText
        帧率：${config.frameRate} fps
        关键帧间隔：${config.iFrameIntervalSeconds} 秒
        降级：${if (isDegraded) "是" else "否"}
        说明：优先选择 1080P 编码画布，实际配置取决于设备 H.264 encoder capabilities；性能仍未实测。
    """.trimIndent()
}
