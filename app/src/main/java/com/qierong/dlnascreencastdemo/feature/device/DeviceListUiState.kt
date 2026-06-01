package com.qierong.dlnascreencastdemo.feature.device

import com.qierong.dlnascreencastdemo.dlna.DlnaDevice

data class DeviceListUiState(
    val status: DeviceDiscoveryStatus = DeviceDiscoveryStatus.Idle,
    val devices: List<DlnaDevice> = emptyList(),
) {
    val isSearching: Boolean
        get() = status == DeviceDiscoveryStatus.Searching
}

sealed interface DeviceDiscoveryStatus {
    data object Idle : DeviceDiscoveryStatus
    data object Searching : DeviceDiscoveryStatus
    data object Empty : DeviceDiscoveryStatus
    data class Success(val count: Int) : DeviceDiscoveryStatus
    data object PermissionDenied : DeviceDiscoveryStatus
    data object WifiDisconnected : DeviceDiscoveryStatus
    data class Error(val detail: String) : DeviceDiscoveryStatus
}
