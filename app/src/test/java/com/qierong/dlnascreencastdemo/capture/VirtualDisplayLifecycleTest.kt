package com.qierong.dlnascreencastdemo.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDisplayLifecycleTest {
    @Test
    fun create_allowsOnlyOneVirtualDisplayPerProjectionSession() {
        var createCount = 0
        val lifecycle = lifecycle(
            onCreate = {
                createCount += 1
                "display"
            },
        )

        assertTrue(lifecycle.create(CaptureConfig(1080, 2400, 440), "surface-1"))
        assertFalse(lifecycle.create(CaptureConfig(2400, 1080, 440), "surface-2"))
        assertEquals(1, createCount)
    }

    @Test
    fun resize_updatesExistingDisplayAndSurfaceWithoutCreatingAnotherDisplay() {
        val actions = mutableListOf<String>()
        val lifecycle = lifecycle(
            onCreate = {
                actions += "create"
                "display"
            },
            onResize = { _, config -> actions += "resize:${config.width}x${config.height}" },
            onSetSurface = { _, surface -> actions += "surface:$surface" },
        )
        lifecycle.create(CaptureConfig(1080, 2400, 440), "surface-1")

        lifecycle.resize(CaptureConfig(2400, 1080, 440), "surface-2")

        assertEquals(listOf("create", "resize:2400x1080", "surface:surface-2"), actions)
    }

    @Test
    fun resize_ignoresDuplicateConfigWithoutReplacingSurface() {
        val actions = mutableListOf<String>()
        val lifecycle = lifecycle(
            onCreate = {
                actions += "create"
                "display"
            },
            onResize = { _, config -> actions += "resize:${config.width}x${config.height}" },
            onSetSurface = { _, surface -> actions += "surface:$surface" },
        )
        lifecycle.create(CaptureConfig(1080, 2400, 440), "surface-1")

        lifecycle.resize(CaptureConfig(1080, 2400, 440), "surface-2")

        assertEquals(listOf("create"), actions)
    }

    private fun lifecycle(
        onCreate: (String) -> String? = { "display" },
        onResize: (String, CaptureConfig) -> Unit = { _, _ -> },
        onSetSurface: (String, String) -> Unit = { _, _ -> },
    ) = VirtualDisplayLifecycle(
        createDisplay = { _, surface -> onCreate(surface) },
        resizeDisplay = onResize,
        setDisplaySurface = onSetSurface,
        releaseDisplay = {},
    )
}
