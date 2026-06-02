package com.qierong.dlnascreencastdemo.encoder

import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoderRelease(
    private val signalEndOfInputStream: () -> Unit,
    private val drainRemainingOutput: () -> Unit,
    private val stopCodec: () -> Unit,
    private val releaseCodec: () -> Unit,
    private val releaseInputSurface: () -> Unit,
    private val stopWorkerThread: () -> Unit,
) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (!released.compareAndSet(false, true)) return
        safely(signalEndOfInputStream)
        safely(drainRemainingOutput)
        safely(stopCodec)
        safely(releaseCodec)
        safely(releaseInputSurface)
        safely(stopWorkerThread)
    }

    private fun safely(action: () -> Unit) {
        runCatching(action)
    }
}
