package com.qierong.dlnascreencastdemo.encoder

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEncoderReleaseTest {
    @Test
    fun release_runsEveryActionInOrderOnlyOnceEvenWhenOneFails() {
        val actions = mutableListOf<String>()
        val release = VideoEncoderRelease(
            signalEndOfInputStream = { actions += "signalEndOfInputStream" },
            drainRemainingOutput = {
                actions += "drainRemainingOutput"
                error("drain failed")
            },
            stopCodec = { actions += "stopCodec" },
            releaseCodec = { actions += "releaseCodec" },
            releaseInputSurface = { actions += "releaseInputSurface" },
            stopWorkerThread = { actions += "stopWorkerThread" },
        )

        release.release()
        release.release()

        assertEquals(
            listOf(
                "signalEndOfInputStream",
                "drainRemainingOutput",
                "stopCodec",
                "releaseCodec",
                "releaseInputSurface",
                "stopWorkerThread",
            ),
            actions,
        )
    }
}
