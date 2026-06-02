package com.qierong.dlnascreencastdemo.feature.casting

import androidx.lifecycle.ViewModel
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.capture.CaptureStateStore
import kotlinx.coroutines.flow.StateFlow

class ScreenCaptureViewModel(
    private val stateStore: CaptureStateStore = CaptureStateStore.global,
) : ViewModel() {
    val uiState: StateFlow<CaptureState> = stateStore.state

    fun requestCapturePermission(): Boolean = stateStore.requestPermission()

    fun onProjectionPermissionGranted() {
        stateStore.markStarting()
    }

    fun onProjectionPermissionDenied() {
        stateStore.markPermissionDenied()
    }

    fun onServiceStartFailed(detail: String) {
        stateStore.markError(detail)
    }
}
