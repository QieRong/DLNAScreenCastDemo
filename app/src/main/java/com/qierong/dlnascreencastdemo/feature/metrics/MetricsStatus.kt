package com.qierong.dlnascreencastdemo.feature.metrics

import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig

data class MetricStatusItem(
    val title: String,
    val target: String,
    val current: String,
    val evidence: String,
) {
    fun toDisplayText(): String =
        """
        目标：$target
        当前：$current
        证据：$evidence
        """.trimIndent()
}

fun buildMetricStatusItems(captureState: CaptureState): List<MetricStatusItem> {
    val encoderConfig = captureState.activeEncoderConfig()
    val audioStatus = captureState.activeAudioStatus()
    return listOf(
        MetricStatusItem(
            title = "延迟",
            target = "< 2 秒",
            current = "未实测",
            evidence = "缺少 3 次外部录像读数",
        ),
        MetricStatusItem(
            title = "分辨率",
            target = "1080P",
            current = encoderConfig?.let { "${it.config.width} x ${it.config.height}" } ?: "待开始采集",
            evidence = if (encoderConfig == null) {
                "开始采集后来自当前 CaptureState；也可用 ffprobe 样本复核"
            } else {
                "来自当前 CaptureState；后续可用 ffprobe 样本复核"
            },
        ),
        MetricStatusItem(
            title = "视频码率",
            target = "8 Mbps",
            current = "动态样本待测",
            evidence = "需 30 秒动态页面 curl 样本",
        ),
        MetricStatusItem(
            title = "音频",
            target = "AAC 128Kbps",
            current = audioStatus?.label ?: "待开始采集",
            evidence = audioStatus?.detail
                ?: "PR14 使用 AudioPlaybackCapture 采集真实系统播放音；" +
                    "ffprobe audio=aac 只证明音轨存在，logcat PCM peak/RMS + first AAC 才证明捕获并完成编码，PC/Kodi/ffplay 听到目标 App 声音才证明真实播放音链路跑通。",
        ),
    )
}

private fun CaptureState.activeEncoderConfig(): ActiveEncoderConfig? = when (this) {
    is CaptureState.Capturing -> sessionInfo.encoderConfig
    is CaptureState.Reconfiguring -> sessionInfo.encoderConfig
    CaptureState.Idle,
    CaptureState.PermissionDenied,
    CaptureState.RequestingPermission,
    CaptureState.Starting,
    CaptureState.Stopping,
    is CaptureState.Error,
    -> null
}

private fun CaptureState.activeAudioStatus() = when (this) {
    is CaptureState.Capturing -> sessionInfo.audioStatus
    is CaptureState.Reconfiguring -> sessionInfo.audioStatus
    CaptureState.Idle,
    CaptureState.PermissionDenied,
    CaptureState.RequestingPermission,
    CaptureState.Starting,
    CaptureState.Stopping,
    is CaptureState.Error,
    -> null
}
