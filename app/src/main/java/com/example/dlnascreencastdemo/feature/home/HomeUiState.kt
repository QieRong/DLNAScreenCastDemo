package com.example.dlnascreencastdemo.feature.home

data class HomeUiState(
    val currentPhase: String = "PR 1 / 7: Compose project bootstrap",
    val metricsNotice: String = "Not measured: latency, resolution, bitrate, and audio capture",
    val plannedFeatures: List<String> = listOf(
        "DLNA / UPnP device discovery",
        "Screen capture permission flow",
        "H.264 encoder configuration",
        "Local HTTP stream service",
        "DLNA playback control",
        "Demo report and release APK",
    ),
)
