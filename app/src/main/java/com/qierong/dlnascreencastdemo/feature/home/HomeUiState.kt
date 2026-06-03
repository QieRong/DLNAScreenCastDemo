package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 8：指标演示与动态测试页",
    val metricsNotice: String =
        "PR8 提供可复现测试入口，不宣称指标达成；AAC 音频未实现，延迟仍未实测。",
    val plannedFeatures: List<String> = listOf(
        "已完成：局域网 Renderer 发现与安全描述解析",
        "已完成：MediaProjection 授权、采集状态与资源释放",
        "已完成：H.264 编码参数选择、实际编码与参数展示",
        "已完成：本地 MPEG-TS HTTP 流与 PC 播放测试",
        "已完成：DLNA AVTransport SetURI / Play / Pause / Stop",
        "已完成：最终测试报告与 v1.0.0-demo Release",
        "本 PR：延迟测试页、动态码率测试页和指标测试指南",
        "后续：AAC 128Kbps 测试音轨、低延迟优化和电视兼容矩阵",
    ),
)
