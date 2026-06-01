package com.example.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksBootstrapAsReadyAndMetricsAsUnmeasured() {
        val state = HomeUiState()

        assertEquals("PR 1 / 7: Compose project bootstrap", state.currentPhase)
        assertTrue(state.metricsNotice.contains("Not measured"))
    }
}
