package com.qierong.dlnascreencastdemo.feature.home

import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlStatus
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlUiState
import com.qierong.dlnascreencastdemo.feature.device.DeviceDiscoveryStatus
import com.qierong.dlnascreencastdemo.feature.device.DeviceListUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun initialHomeState_marksDlnaControlPhaseAndMetricsBoundary() {
        val state = HomeUiState()

        assertEquals("PR 14：AudioPlaybackCapture 真实播放音采集", state.currentPhase)
        assertTrue(state.metricsNotice.contains("Android 10+"))
        assertTrue(state.metricsNotice.contains("capture policy"))
        assertTrue(state.metricsNotice.contains("手机端自动静音未证明"))
        assertTrue(state.plannedFeatures.contains("历史：PR9 曾用 1kHz 测试音验证 AAC 封装链路"))
        assertTrue(
            state.plannedFeatures.contains(
                "本 PR：AudioPlaybackCapture + AudioRecord + AAC + MPEG-TS 音频 PID",
            ),
        )
    }

    @Test
    fun verificationNextStep_guidesUserToOpenKodiAndSearchFirst() {
        val nextStep = buildVerificationNextStep(
            deviceState = DeviceListUiState(),
            captureState = CaptureState.Idle,
            dlnaControlState = DlnaControlUiState(),
        )

        assertTrue(nextStep.contains("先打开 Kodi"))
        assertTrue(nextStep.contains("搜索 DLNA 设备"))
    }

    @Test
    fun verificationNextStep_afterRendererSelectedGuidesUserToPlayMp4AndStartCapture() {
        val nextStep = buildVerificationNextStep(
            deviceState = DeviceListUiState(status = DeviceDiscoveryStatus.Success(count = 1)),
            captureState = CaptureState.Idle,
            dlnaControlState = DlnaControlUiState(
                selectedDevice = kodiDevice(),
                status = DlnaControlStatus.Stopped,
            ),
        )

        assertTrue(nextStep.contains("本地 MP4"))
        assertTrue(nextStep.contains("开始采集"))
    }

    private fun kodiDevice() = DlnaDevice(
        id = "uuid:kodi",
        udn = "uuid:kodi",
        friendlyName = "Kodi",
        manufacturer = "XBMC Foundation",
        modelName = "Kodi",
        ipAddress = "192.168.137.1",
        descriptionUrl = "http://192.168.137.1:8080/rootDesc.xml",
        avTransportControlUrl = "http://192.168.137.1:8080/AVTransport/control",
    )
}
