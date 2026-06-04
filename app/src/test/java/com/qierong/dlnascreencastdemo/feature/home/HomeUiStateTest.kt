package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksDlnaControlPhaseAndMetricsBoundary() {
        val state = HomeUiState()

        assertEquals("PR 10：最终指标证据补强", state.currentPhase)
        assertTrue(state.metricsNotice.contains("ffprobe"))
        assertTrue(state.metricsNotice.contains("AAC 测试音轨已接入"))
        assertTrue(state.metricsNotice.contains("延迟仍需外部录像实测"))
        assertTrue(state.plannedFeatures.contains("已完成：AAC 128Kbps App 内测试音轨封装"))
        assertTrue(state.plannedFeatures.contains("本 PR：补充 ffprobe、动态码率、延迟读数和证据矩阵"))
    }
}
