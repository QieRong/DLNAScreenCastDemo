package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 10：最终指标证据补强",
    val metricsNotice: String =
        "PR10 补强 ffprobe、动态码率和延迟证据；AAC 测试音轨已接入，是否被识别以真机样本为准；延迟仍需外部录像实测。",
    val plannedFeatures: List<String> = listOf(
        "已完成：局域网 Renderer 发现与安全描述解析",
        "已完成：MediaProjection 授权、采集状态与资源释放",
        "已完成：H.264 编码参数选择、实际编码与参数展示",
        "已完成：本地 MPEG-TS HTTP 流与 PC 播放测试",
        "已完成：DLNA AVTransport SetURI / Play / Pause / Stop",
        "已完成：最终测试报告与 v1.0.0-demo Release",
        "已完成：延迟测试页、动态码率测试页和指标测试指南",
        "已完成：AAC 128Kbps App 内测试音轨封装",
        "本 PR：补充 ffprobe、动态码率、延迟读数和证据矩阵",
        "后续：低延迟优化、真实音频采集和电视兼容矩阵",
    ),
)
