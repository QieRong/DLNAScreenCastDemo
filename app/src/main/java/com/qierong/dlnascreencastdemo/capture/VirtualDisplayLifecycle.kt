package com.qierong.dlnascreencastdemo.capture

internal class VirtualDisplayLifecycle<TSurface, TDisplay>(
    private val createDisplay: (CaptureConfig, TSurface) -> TDisplay?,
    private val resizeDisplay: (TDisplay, CaptureConfig) -> Unit,
    private val setDisplaySurface: (TDisplay, TSurface) -> Unit,
    private val releaseDisplay: (TDisplay) -> Unit,
) {
    private var display: TDisplay? = null
    private var currentConfig: CaptureConfig? = null

    @Synchronized
    fun create(config: CaptureConfig, surface: TSurface): Boolean {
        if (display != null) return false
        display = createDisplay(config, surface)
        if (display != null) currentConfig = config
        return display != null
    }

    @Synchronized
    fun needsResize(config: CaptureConfig): Boolean =
        display != null && currentConfig != config

    @Synchronized
    fun resize(config: CaptureConfig, surface: TSurface): Boolean {
        val currentDisplay = display ?: return false
        if (currentConfig == config) return true
        resizeDisplay(currentDisplay, config)
        setDisplaySurface(currentDisplay, surface)
        currentConfig = config
        return true
    }

    @Synchronized
    fun release() {
        display?.let(releaseDisplay)
        display = null
        currentConfig = null
    }
}
