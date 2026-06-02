package com.qierong.dlnascreencastdemo.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturePermissionCoordinatorTest {
    @Test
    fun start_requestsNotificationPermissionBeforeProjectionWhenNeeded() {
        val actions = mutableListOf<String>()
        val coordinator = CapturePermissionCoordinator(
            requestProjectionPermission = { actions += "projection" },
        )

        coordinator.start(
            needsNotificationPermission = true,
            requestNotificationPermission = { actions += "notification" },
        )

        assertEquals(listOf("notification"), actions)
    }

    @Test
    fun notificationPermissionResult_continuesProjectionRequestEvenWhenDenied() {
        val actions = mutableListOf<String>()
        val coordinator = CapturePermissionCoordinator(
            requestProjectionPermission = { actions += "projection" },
        )

        coordinator.start(
            needsNotificationPermission = true,
            requestNotificationPermission = { actions += "notification" },
        )
        coordinator.onNotificationPermissionResult(isGranted = false)

        assertEquals(listOf("notification", "projection"), actions)
    }
}
