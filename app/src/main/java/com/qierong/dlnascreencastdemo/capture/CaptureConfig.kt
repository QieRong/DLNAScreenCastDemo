package com.qierong.dlnascreencastdemo.capture

data class CaptureConfig(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
) {
    init {
        require(width > 0) { "Capture width must be positive" }
        require(height > 0) { "Capture height must be positive" }
        require(densityDpi > 0) { "Capture densityDpi must be positive" }
    }
}
