package com.qierong.dlnascreencastdemo.feature.casting

import com.qierong.dlnascreencastdemo.capture.CaptureConfig
import com.qierong.dlnascreencastdemo.capture.CaptureSessionInfo
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportController
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportResult
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportStage
import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.BitrateMode
import com.qierong.dlnascreencastdemo.encoder.EncoderConfig
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DlnaControlViewModelTest {
    @Test
    fun sendToRenderer_requiresSelectedDevice() = runTest {
        val viewModel = DlnaControlViewModel(
            controller = FakeController(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.sendToRenderer(capturingState())
        advanceUntilIdle()

        val failure = assertIsInstance<DlnaControlStatus.Failed>(viewModel.uiState.value.status)
        assertEquals(DlnaControlFailureReason.NoRendererSelected, failure.reason)
    }

    @Test
    fun sendToRenderer_rejectsDeviceWithoutControlUrl() = runTest {
        val viewModel = DlnaControlViewModel(
            controller = FakeController(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        viewModel.selectDevice(device(controlUrl = null))

        viewModel.sendToRenderer(capturingState())
        advanceUntilIdle()

        val failure = assertIsInstance<DlnaControlStatus.Failed>(viewModel.uiState.value.status)
        assertEquals(DlnaControlFailureReason.DeviceNotControllable, failure.reason)
    }

    @Test
    fun sendToRenderer_requiresActiveStreamUrl() = runTest {
        val viewModel = DlnaControlViewModel(
            controller = FakeController(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        viewModel.selectDevice(device())

        viewModel.sendToRenderer(CaptureState.Idle)
        advanceUntilIdle()

        val failure = assertIsInstance<DlnaControlStatus.Failed>(viewModel.uiState.value.status)
        assertEquals(DlnaControlFailureReason.LocalStreamNotStarted, failure.reason)
    }

    @Test
    fun sendToRenderer_setsUriThenPlays() = runTest {
        val controller = FakeController()
        val viewModel = DlnaControlViewModel(
            controller = controller,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        viewModel.selectDevice(device())

        viewModel.sendToRenderer(capturingState())
        advanceUntilIdle()

        assertEquals(listOf("setUri", "play"), controller.calls)
        assertIsInstance<DlnaControlStatus.Playing>(viewModel.uiState.value.status)
    }

    @Test
    fun pauseAndStop_updateRemotePlaybackStatusOnly() = runTest {
        val controller = FakeController()
        val viewModel = DlnaControlViewModel(
            controller = controller,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        viewModel.selectDevice(device())

        viewModel.pause()
        advanceUntilIdle()
        assertIsInstance<DlnaControlStatus.Paused>(viewModel.uiState.value.status)

        viewModel.stopRemotePlayback()
        advanceUntilIdle()
        assertIsInstance<DlnaControlStatus.Stopped>(viewModel.uiState.value.status)
        assertEquals(listOf("pause", "stop"), controller.calls)
    }

    private class FakeController : AvTransportController {
        val calls = mutableListOf<String>()

        override suspend fun setAvTransportUri(
            controlUrl: String,
            streamUrl: String,
        ): AvTransportResult {
            calls += "setUri"
            return AvTransportResult.Success(AvTransportStage.SetUri, 200)
        }

        override suspend fun play(controlUrl: String): AvTransportResult {
            calls += "play"
            return AvTransportResult.Success(AvTransportStage.Play, 200)
        }

        override suspend fun pause(controlUrl: String): AvTransportResult {
            calls += "pause"
            return AvTransportResult.Success(AvTransportStage.Pause, 200)
        }

        override suspend fun stop(controlUrl: String): AvTransportResult {
            calls += "stop"
            return AvTransportResult.Success(AvTransportStage.Stop, 200)
        }
    }

    private fun device(controlUrl: String? = "http://192.168.1.20/AVTransport/control") =
        DlnaDevice(
            id = "uuid:kodi",
            udn = "uuid:kodi",
            friendlyName = "Kodi",
            manufacturer = "Kodi",
            modelName = "Renderer",
            ipAddress = "192.168.1.20",
            descriptionUrl = "http://192.168.1.20/device.xml",
            avTransportControlUrl = controlUrl,
        )

    private fun capturingState(streamUrl: String? = "http://192.168.1.8:8080/live.ts") =
        CaptureState.Capturing(
            CaptureSessionInfo(
                sourceConfig = CaptureConfig(width = 1080, height = 2400, densityDpi = 440),
                encoderConfig = ActiveEncoderConfig(
                    codecName = "test.avc",
                    config = EncoderConfig(
                        width = 1080,
                        height = 1920,
                        videoBitrate = 8_000_000,
                        bitrateMode = BitrateMode.CBR,
                    ),
                    isDegraded = false,
                ),
                streamUrl = streamUrl,
            ),
        )

    private inline fun <reified T> assertIsInstance(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName}, got $value", value is T)
        return value as T
    }
}
