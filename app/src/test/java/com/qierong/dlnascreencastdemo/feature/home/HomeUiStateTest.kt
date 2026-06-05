package com.qierong.dlnascreencastdemo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksDlnaControlPhaseAndMetricsBoundary() {
        val state = HomeUiState()

        assertEquals("PR 13：禁用运行时默认测试音", state.currentPhase)
        assertTrue(state.metricsNotice.contains("1kHz 测试音"))
        assertTrue(state.metricsNotice.contains("video-only"))
        assertTrue(state.metricsNotice.contains("真实系统播放音待 PR14"))
        assertTrue(state.plannedFeatures.contains("历史：PR9 曾用 1kHz 测试音验证 AAC 封装链路"))
        assertTrue(state.plannedFeatures.contains("本 PR：禁用运行时默认测试音，默认 MPEG-TS 为 H.264 video-only"))
    }
}
