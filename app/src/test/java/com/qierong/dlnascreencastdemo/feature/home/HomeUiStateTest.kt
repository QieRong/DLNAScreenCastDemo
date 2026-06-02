package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksEncoderPhaseAndMetricsAsUnmeasured() {
        val state = HomeUiState()

        assertEquals("PR 4 / 7：H.264 编码参数与实际配置展示", state.currentPhase)
        assertTrue(state.metricsNotice.contains("优先选择 1080P 编码画布"))
        assertTrue(state.metricsNotice.contains("性能仍未实测"))
    }
}
