package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 3 / 7：MediaProjection 屏幕采集骨架",
    val metricsNotice: String = "目标指标尚未实测：1080P、8 Mbps、AAC 128 Kbps、延迟 < 2 秒",
    val plannedFeatures: List<String> = listOf(
        "已完成：局域网 Renderer 发现与安全描述解析",
        "本 PR：MediaProjection 授权、采集状态与资源释放",
        "后续：H.264 编码参数",
        "后续：本地 HTTP 流服务",
        "后续：DLNA 播放控制",
        "后续：测试报告与 Release APK",
    ),
)
