package com.qierong.dlnascreencastdemo.capture

import java.util.concurrent.atomic.AtomicBoolean

internal class CaptureResourceCleanup(
    private val releaseVirtualDisplay: () -> Unit,
    private val releaseVideoEncoder: () -> Unit,
    private val releaseStreamServer: () -> Unit,
    private val unregisterProjectionCallback: () -> Unit,
    private val stopProjection: () -> Unit,
    private val stopWorkerThread: () -> Unit,
) {
    private val released = AtomicBoolean(false)

    fun release(stopProjection: Boolean) {
        if (!released.compareAndSet(false, true)) return
        safely(releaseVirtualDisplay)
        safely(releaseVideoEncoder)
        safely(releaseStreamServer)
        safely(unregisterProjectionCallback)
        if (stopProjection) safely(this.stopProjection)
        safely(stopWorkerThread)
    }

    private fun safely(action: () -> Unit) {
        runCatching(action)
    }
}
