package com.qierong.dlnascreencastdemo.feature.casting

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportClient
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportController
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportResult
import com.qierong.dlnascreencastdemo.dlna.control.AvTransportStage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DlnaControlViewModel(
    private val controller: AvTransportController = AvTransportClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DlnaControlUiState())
    val uiState: StateFlow<DlnaControlUiState> = _uiState.asStateFlow()

    fun selectDevice(device: DlnaDevice) {
        logInfo("选择 Renderer：name=${device.friendlyName} ip=${device.ipAddress}")
        val status = if (device.avTransportControlUrl == null) {
            DlnaControlStatus.DeviceNotControllable
        } else {
            DlnaControlStatus.Stopped
        }
        _uiState.update { it.copy(selectedDevice = device, status = status) }
    }

    fun sendToRenderer(captureState: CaptureState) {
        val target = validatedTarget(captureState) ?: return
        viewModelScope.launch(dispatcher) {
            runCatching {
                logInfo(
                    "准备发送到 Renderer：name=${target.device.friendlyName} " +
                        "ip=${target.device.ipAddress} controlURL=${target.controlUrl} " +
                        "streamUrl=${target.streamUrl}",
                )
                _uiState.update {
                    it.copy(status = DlnaControlStatus.InProgress(AvTransportStage.SetUri))
                }
                val setUriResult = controller.setAvTransportUri(target.controlUrl, target.streamUrl)
                if (setUriResult is AvTransportResult.Failure) {
                    _uiState.update { it.copy(status = setUriResult.toFailureStatus()) }
                    return@launch
                }
                _uiState.update { it.copy(status = DlnaControlStatus.UriSet) }

                _uiState.update {
                    it.copy(status = DlnaControlStatus.InProgress(AvTransportStage.Play))
                }
                val playResult = controller.play(target.controlUrl)
                _uiState.update {
                    it.copy(
                        status = when (playResult) {
                            is AvTransportResult.Success -> DlnaControlStatus.Playing
                            is AvTransportResult.Failure -> playResult.toFailureStatus()
                        },
                    )
                }
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
                _uiState.update {
                    it.copy(
                        status = DlnaControlStatus.Failed(
                            reason = DlnaControlFailureReason.Network,
                            detail = exception.message ?: "DLNA 网络请求失败",
                        ),
                    )
                }
            }
        }
    }

    fun pause() {
        val target = selectedControlUrlOrFail() ?: return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(status = DlnaControlStatus.InProgress(AvTransportStage.Pause)) }
            val result = controller.pause(target)
            _uiState.update {
                it.copy(
                    status = when (result) {
                        is AvTransportResult.Success -> DlnaControlStatus.Paused
                        is AvTransportResult.Failure -> result.toFailureStatus()
                    },
                )
            }
        }
    }

    fun stopRemotePlayback() {
        val target = selectedControlUrlOrFail() ?: return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(status = DlnaControlStatus.InProgress(AvTransportStage.Stop)) }
            val result = controller.stop(target)
            _uiState.update {
                it.copy(
                    status = when (result) {
                        is AvTransportResult.Success -> DlnaControlStatus.Stopped
                        is AvTransportResult.Failure -> result.toFailureStatus()
                    },
                )
            }
        }
    }

    private fun validatedTarget(captureState: CaptureState): ControlTarget? {
        val device = _uiState.value.selectedDevice ?: run {
            fail(DlnaControlFailureReason.NoRendererSelected, "未选择 Renderer")
            return null
        }
        val controlUrl = device.avTransportControlUrl ?: run {
            fail(DlnaControlFailureReason.DeviceNotControllable, "设备缺少 AVTransport controlURL")
            return null
        }
        val sessionInfo = when (captureState) {
            is CaptureState.Capturing -> captureState.sessionInfo
            is CaptureState.Reconfiguring -> captureState.sessionInfo
            CaptureState.Idle,
            CaptureState.RequestingPermission,
            CaptureState.Starting,
            CaptureState.Stopping,
            CaptureState.PermissionDenied,
            is CaptureState.Error,
            -> null
        }
        val streamUrl = sessionInfo
            ?.streamUrl
            ?.takeIf(String::isNotBlank)
            ?: run {
                fail(DlnaControlFailureReason.LocalStreamNotStarted, "本地 /live.ts 流未启动")
                return null
            }
        return ControlTarget(device, controlUrl, streamUrl)
    }

    private fun selectedControlUrlOrFail(): String? {
        val device = _uiState.value.selectedDevice ?: run {
            fail(DlnaControlFailureReason.NoRendererSelected, "未选择 Renderer")
            return null
        }
        return device.avTransportControlUrl ?: run {
            fail(DlnaControlFailureReason.DeviceNotControllable, "设备缺少 AVTransport controlURL")
            null
        }
    }

    private fun fail(reason: DlnaControlFailureReason, detail: String) {
        _uiState.update {
            it.copy(status = DlnaControlStatus.Failed(reason = reason, detail = detail))
        }
    }

    private fun AvTransportResult.Failure.toFailureStatus(): DlnaControlStatus.Failed {
        val reason = when {
            stage == AvTransportStage.Network -> DlnaControlFailureReason.Network
            soapFault != null -> DlnaControlFailureReason.SoapFault
            stage == AvTransportStage.SetUri -> DlnaControlFailureReason.SetUri
            stage == AvTransportStage.Play -> DlnaControlFailureReason.Play
            stage == AvTransportStage.Pause -> DlnaControlFailureReason.Pause
            stage == AvTransportStage.Stop -> DlnaControlFailureReason.Stop
            else -> DlnaControlFailureReason.Network
        }
        return DlnaControlStatus.Failed(
            reason = reason,
            detail = "${stage.label} 失败：$summary",
            result = this,
        )
    }

    private data class ControlTarget(
        val device: DlnaDevice,
        val controlUrl: String,
        val streamUrl: String,
    )

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private companion object {
        const val TAG = "DlnaControl"
    }
}
