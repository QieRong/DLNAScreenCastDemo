package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksDiscoveryPhaseAndMetricsAsUnmeasured() {
        val state = HomeUiState()

        assertEquals("PR 2 / 7：DLNA / UPnP Renderer 设备发现", state.currentPhase)
        assertTrue(state.metricsNotice.contains("尚未实测"))
    }
}
