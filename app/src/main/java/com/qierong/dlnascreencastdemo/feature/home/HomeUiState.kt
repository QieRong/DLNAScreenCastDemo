package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 5 / 7：本地 MPEG-TS HTTP 流服务与 PC 播放测试",
    val metricsNotice: String =
        "已接入 H.264 视频本地流；AAC 音频未实现，延迟仍未实测。",
    val plannedFeatures: List<String> = listOf(
        "已完成：局域网 Renderer 发现与安全描述解析",
        "已完成：MediaProjection 授权、采集状态与资源释放",
        "已完成：H.264 编码参数选择、实际编码与参数展示",
        "本 PR：本地 MPEG-TS HTTP 流与 PC 播放测试",
        "后续：DLNA 播放控制",
        "后续：测试报告与 Release APK",
    ),
)
