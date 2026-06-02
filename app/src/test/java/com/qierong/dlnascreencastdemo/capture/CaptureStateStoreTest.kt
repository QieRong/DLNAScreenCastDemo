package com.qierong.dlnascreencastdemo.capture

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
        store.markCapturing(CaptureConfig(width = 1080, height = 2400, densityDpi = 440))

        assertFalse(store.requestPermission())
        assertEquals(
            CaptureState.Capturing(CaptureConfig(width = 1080, height = 2400, densityDpi = 440)),
            store.state.value,
        )
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
}
