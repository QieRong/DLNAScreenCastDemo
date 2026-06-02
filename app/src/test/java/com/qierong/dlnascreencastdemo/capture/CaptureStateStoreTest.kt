package com.qierong.dlnascreencastdemo.capture

import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.BitrateMode
import com.qierong.dlnascreencastdemo.encoder.EncoderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureStateStoreTest {
    @Test
    fun requestPermission_rejectsDuplicateStartWhileSessionIsActive() {
        val store = CaptureStateStore()

        assertTrue(store.requestPermission())
        store.markStarting()
        store.markCapturing(sessionInfo())

        assertFalse(store.requestPermission())
        assertEquals(
            CaptureState.Capturing(sessionInfo()),
            store.state.value,
        )
    }

    @Test
    fun markReconfiguring_keepsSessionActiveUntilReplacementEncoderStarts() {
        val store = CaptureStateStore()
        val target = CaptureConfig(width = 2670, height = 1200, densityDpi = 440)
        store.markCapturing(sessionInfo())

        store.markReconfiguring(sessionInfo(), target)

        assertEquals(CaptureState.Reconfiguring(sessionInfo(), target), store.state.value)
        assertTrue(store.state.value.hasActiveSession)
    }

    @Test
    fun markIdle_allowsStartingANewPermissionRequest() {
        val store = CaptureStateStore()
        store.requestPermission()
        store.markStarting()
        store.markStopping()
        store.markIdle()

        assertTrue(store.requestPermission())
        assertEquals(CaptureState.RequestingPermission, store.state.value)
    }

    @Test
    fun captureConfig_rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            CaptureConfig(width = 0, height = 2400, densityDpi = 440)
        }
    }

    private fun sessionInfo() = CaptureSessionInfo(
        sourceConfig = CaptureConfig(width = 1080, height = 2400, densityDpi = 440),
        encoderConfig = ActiveEncoderConfig(
            codecName = "test.avc.encoder",
            config = EncoderConfig(
                width = 1080,
                height = 1920,
                videoBitrate = 8_000_000,
                bitrateMode = BitrateMode.CBR,
            ),
            isDegraded = false,
        ),
    )
}
