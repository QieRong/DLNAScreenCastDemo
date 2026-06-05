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
            current = "默认 video-only；测试音运行时已禁用",
            evidence = "PR9 曾用 App 内 1kHz 测试音验证 AAC 封装链路；" +
                "PR13 起默认不输出测试音。真实系统播放音待 PR14 实现和验证。",
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
