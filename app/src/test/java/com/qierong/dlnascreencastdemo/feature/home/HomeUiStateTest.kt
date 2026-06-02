package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksCapturePhaseAndMetricsAsUnmeasured() {
        val state = HomeUiState()

        assertEquals("PR 3 / 7：MediaProjection 屏幕采集骨架", state.currentPhase)
        assertTrue(state.metricsNotice.contains("尚未实测"))
    }
}
