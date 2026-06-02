package com.qierong.dlnascreencastdemo.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureResourceCleanupTest {
    @Test
    fun release_runsInRequiredOrderOnlyOnce() {
        val actions = mutableListOf<String>()
        val cleanup = CaptureResourceCleanup(
            stopFrameConsumer = { actions += "stopFrameConsumer" },
            releaseVirtualDisplay = { actions += "releaseVirtualDisplay" },
            closeFrameSurface = { actions += "closeFrameSurface" },
            unregisterProjectionCallback = { actions += "unregisterProjectionCallback" },
            stopProjection = { actions += "stopProjection" },
            stopWorkerThread = { actions += "stopWorkerThread" },
        )

        cleanup.release(stopProjection = true)
        cleanup.release(stopProjection = true)

        assertEquals(
            listOf(
                "stopFrameConsumer",
                "releaseVirtualDisplay",
                "closeFrameSurface",
                "unregisterProjectionCallback",
                "stopProjection",
                "stopWorkerThread",
            ),
            actions,
        )
    }

    @Test
    fun release_fromProjectionCallback_doesNotStopProjectionAgain() {
        val actions = mutableListOf<String>()
        val cleanup = CaptureResourceCleanup(
            stopFrameConsumer = { actions += "stopFrameConsumer" },
            releaseVirtualDisplay = { actions += "releaseVirtualDisplay" },
            closeFrameSurface = { actions += "closeFrameSurface" },
            unregisterProjectionCallback = { actions += "unregisterProjectionCallback" },
            stopProjection = { actions += "stopProjection" },
            stopWorkerThread = { actions += "stopWorkerThread" },
        )

        cleanup.release(stopProjection = false)

        assertEquals(
            listOf(
                "stopFrameConsumer",
                "releaseVirtualDisplay",
                "closeFrameSurface",
                "unregisterProjectionCallback",
                "stopWorkerThread",
            ),
            actions,
        )
    }
}
