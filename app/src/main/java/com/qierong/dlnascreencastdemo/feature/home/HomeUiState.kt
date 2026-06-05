package com.qierong.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 13：禁用运行时默认测试音",
    val metricsNotice: String =
        "PR13 默认关闭 App 内 1kHz 测试音；AAC/ADTS/MPEG-TS 音频封装能力保留，当前默认流为 video-only。真实系统播放音待 PR14 实现和验证。",
    val plannedFeatures: List<String> = listOf(
        "已完成：局域网 Renderer 发现与安全描述解析",
        "已完成：MediaProjection 授权、采集状态与资源释放",
        "已完成：H.264 编码参数选择、实际编码与参数展示",
        "已完成：本地 MPEG-TS HTTP 流与 PC 播放测试",
        "已完成：DLNA AVTransport SetURI / Play / Pause / Stop",
        "已完成：最终测试报告与 v1.0.0-demo Release",
        "已完成：延迟测试页、动态码率测试页和指标测试指南",
        "历史：PR9 曾用 1kHz 测试音验证 AAC 封装链路",
        "本 PR：禁用运行时默认测试音，默认 MPEG-TS 为 H.264 video-only",
        "后续：AudioPlaybackCapture 真实播放音采集、低延迟优化和电视兼容矩阵",
    ),
)
