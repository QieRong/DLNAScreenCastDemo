package com.qierong.dlnascreencastdemo.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptureStateStore {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    @Synchronized
    fun requestPermission(): Boolean {
        if (_state.value.hasActiveSession) return false
        _state.value = CaptureState.RequestingPermission
        return true
    }

    fun markStarting() {
        _state.value = CaptureState.Starting
    }

    fun markCapturing(sessionInfo: CaptureSessionInfo) {
        _state.value = CaptureState.Capturing(sessionInfo)
    }

    fun markReconfiguring(
        sessionInfo: CaptureSessionInfo,
        targetSourceConfig: CaptureConfig,
    ) {
        _state.value = CaptureState.Reconfiguring(sessionInfo, targetSourceConfig)
    }

    fun markStopping() {
        _state.value = CaptureState.Stopping
    }

    fun markPermissionDenied() {
        _state.value = CaptureState.PermissionDenied
    }

    fun markError(detail: String) {
        _state.value = CaptureState.Error(detail)
    }

    fun markIdle() {
        _state.value = CaptureState.Idle
    }

    companion object {
        val global = CaptureStateStore()
    }
}
