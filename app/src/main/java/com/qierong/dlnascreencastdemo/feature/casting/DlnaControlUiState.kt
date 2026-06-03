package com.qierong.dlnascreencastdemo.feature.casting

import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportResult
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportStage

data class DlnaControlUiState(
    val selectedDevice: DlnaDevice? = null,
    val status: DlnaControlStatus = DlnaControlStatus.NoRendererSelected,
) {
    val canSendToRenderer: Boolean
        get() = selectedDevice?.avTransportControlUrl != null &&
            status !is DlnaControlStatus.InProgress

    val canPause: Boolean
        get() = selectedDevice?.avTransportControlUrl != null &&
            status is DlnaControlStatus.Playing

    val canStopRemotePlayback: Boolean
        get() = selectedDevice?.avTransportControlUrl != null &&
            (status is DlnaControlStatus.Playing || status is DlnaControlStatus.Paused)
}

sealed interface DlnaControlStatus {
    data object NoRendererSelected : DlnaControlStatus
    data object DeviceNotControllable : DlnaControlStatus
    data object LocalStreamNotStarted : DlnaControlStatus
    data class InProgress(val stage: AvTransportStage) : DlnaControlStatus
    data object UriSet : DlnaControlStatus
    data object Playing : DlnaControlStatus
    data object Paused : DlnaControlStatus
    data object Stopped : DlnaControlStatus
    data class Failed(
        val reason: DlnaControlFailureReason,
        val detail: String,
        val result: AvTransportResult.Failure? = null,
    ) : DlnaControlStatus
}

enum class DlnaControlFailureReason {
    NoRendererSelected,
    DeviceNotControllable,
    LocalStreamNotStarted,
    SetUri,
    Play,
    Pause,
    Stop,
    Network,
    SoapFault,
}
