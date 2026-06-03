package com.qierong.dlnascreencastdemo.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.qierong.dlnascreencastdemo.capture.CaptureConfig
import com.qierong.dlnascreencastdemo.capture.CaptureSessionInfo
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlUiState
import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.BitrateMode
import com.qierong.dlnascreencastdemo.encoder.EncoderConfig
import com.qierong.dlnascreencastdemo.feature.device.DeviceDiscoveryStatus
import com.qierong.dlnascreencastdemo.feature.device.DeviceListUiState
import com.qierong.dlnascreencastdemo.ui.theme.DLNAScreenCastDemoTheme

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DLNAScreenCastDemoTheme {
        HomeScreen(
            state = HomeUiState(),
            deviceState = DeviceListUiState(status = DeviceDiscoveryStatus.Empty),
            captureState = CaptureState.Idle,
            dlnaControlState = DlnaControlUiState(),
            onSearchDevices = {},
            onStartCapture = {},
            onStopCapture = {},
            onSelectRenderer = {},
            onSendToRenderer = {},
            onPauseRenderer = {},
            onStopRenderer = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    DLNAScreenCastDemoTheme(darkTheme = true) {
        HomeScreen(
            state = HomeUiState(),
            deviceState = DeviceListUiState(
                status = DeviceDiscoveryStatus.Success(count = 1),
                devices = listOf(
                    DlnaDevice(
                        id = "uuid:kodi",
                        udn = "uuid:kodi",
                        friendlyName = "客厅 Kodi",
                        manufacturer = "Kodi Foundation",
                        modelName = "Kodi",
                        ipAddress = "192.168.1.20",
                        descriptionUrl = "http://192.168.1.20/device.xml",
                        avTransportControlUrl = "http://192.168.1.20/AVTransport/control",
                    ),
                ),
            ),
            captureState = CaptureState.Capturing(
                CaptureSessionInfo(
                    sourceConfig = CaptureConfig(width = 1080, height = 2400, densityDpi = 440),
                    encoderConfig = ActiveEncoderConfig(
                        codecName = "preview.avc.encoder",
                        config = EncoderConfig(
                            width = 1080,
                            height = 1920,
                            videoBitrate = 8_000_000,
                            bitrateMode = BitrateMode.CBR,
                        ),
                        isDegraded = false,
                    ),
                ),
            ),
            dlnaControlState = DlnaControlUiState(
                selectedDevice = DlnaDevice(
                    id = "uuid:kodi",
                    udn = "uuid:kodi",
                    friendlyName = "客厅 Kodi",
                    manufacturer = "Kodi Foundation",
                    modelName = "Kodi",
                    ipAddress = "192.168.1.20",
                    descriptionUrl = "http://192.168.1.20/device.xml",
                    avTransportControlUrl = "http://192.168.1.20/AVTransport/control",
                ),
            ),
            onSearchDevices = {},
            onStartCapture = {},
            onStopCapture = {},
            onSelectRenderer = {},
            onSendToRenderer = {},
            onPauseRenderer = {},
            onStopRenderer = {},
        )
    }
}
