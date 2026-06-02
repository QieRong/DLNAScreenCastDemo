package com.qierong.dlnascreencastdemo.capture

sealed interface CaptureState {
    data object Idle : CaptureState
    data object RequestingPermission : CaptureState
    data object Starting : CaptureState
    data class Capturing(val sessionInfo: CaptureSessionInfo) : CaptureState
    data class Reconfiguring(
        val sessionInfo: CaptureSessionInfo,
        val targetSourceConfig: CaptureConfig,
    ) : CaptureState
    data object Stopping : CaptureState
    data object PermissionDenied : CaptureState
    data class Error(val detail: String) : CaptureState
}

val CaptureState.hasActiveSession: Boolean
    get() = when (this) {
        CaptureState.RequestingPermission,
        CaptureState.Starting,
        is CaptureState.Capturing,
        is CaptureState.Reconfiguring,
        CaptureState.Stopping,
        -> true

        CaptureState.Idle,
        CaptureState.PermissionDenied,
        is CaptureState.Error,
        -> false
    }
