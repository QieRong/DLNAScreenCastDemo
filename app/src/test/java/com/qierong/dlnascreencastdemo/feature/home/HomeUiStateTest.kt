package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksLocalStreamPhaseAndMetricsBoundary() {
        val state = HomeUiState()

        assertEquals("PR 5 / 7：本地 MPEG-TS HTTP 流服务与 PC 播放测试", state.currentPhase)
        assertTrue(state.metricsNotice.contains("AAC 音频未实现"))
        assertTrue(state.metricsNotice.contains("延迟仍未实测"))
    }
}
