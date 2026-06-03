package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksDlnaControlPhaseAndMetricsBoundary() {
        val state = HomeUiState()

        assertEquals("PR 8：指标演示与动态测试页", state.currentPhase)
        assertTrue(state.metricsNotice.contains("可复现测试入口"))
        assertTrue(state.metricsNotice.contains("AAC 音频未实现"))
        assertTrue(state.metricsNotice.contains("延迟仍未实测"))
    }
}
