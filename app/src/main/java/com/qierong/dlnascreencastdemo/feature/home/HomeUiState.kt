package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 2 / 7：DLNA / UPnP Renderer 设备发现",
    val metricsNotice: String = "目标指标尚未实测：1080P、8 Mbps、AAC 128 Kbps、延迟 < 2 秒",
    val plannedFeatures: List<String> = listOf(
        "本 PR：局域网 Renderer 发现与安全描述解析",
        "后续：MediaProjection 屏幕采集权限",
        "后续：H.264 编码参数",
        "后续：本地 HTTP 流服务",
        "后续：DLNA 播放控制",
        "后续：测试报告与 Release APK",
    ),
)
