package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 4 / 7：H.264 编码参数与实际配置展示",
    val metricsNotice: String =
        "优先选择 1080P 编码画布，实际配置取决于设备 H.264 encoder capabilities；性能仍未实测。",
    val plannedFeatures: List<String> = listOf(
        "已完成：局域网 Renderer 发现与安全描述解析",
        "已完成：MediaProjection 授权、采集状态与资源释放",
        "本 PR：H.264 编码参数选择、实际编码与参数展示",
        "后续：本地 HTTP 流服务",
        "后续：DLNA 播放控制",
        "后续：测试报告与 Release APK",
    ),
)
