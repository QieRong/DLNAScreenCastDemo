package com.qierong.dlnascreencastdemo.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qierong.dlnascreencastdemo.dlna.DeviceRepository
import com.qierong.dlnascreencastdemo.dlna.WifiNotConnectedException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceListViewModel(
    private val deviceRepository: DeviceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceListUiState())
    val uiState: StateFlow<DeviceListUiState> = _uiState.asStateFlow()

    fun searchDevices() {
        if (_uiState.value.isSearching) return
        _uiState.update { it.copy(status = DeviceDiscoveryStatus.Searching) }

        viewModelScope.launch {
            val status = try {
                val devices = deviceRepository.discoverRenderers()
                _uiState.update { it.copy(devices = devices) }
                if (devices.isEmpty()) {
                    DeviceDiscoveryStatus.Empty
                } else {
                    DeviceDiscoveryStatus.Success(devices.size)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: WifiNotConnectedException) {
                DeviceDiscoveryStatus.WifiDisconnected
            } catch (exception: Exception) {
                DeviceDiscoveryStatus.Error(exception.message ?: "设备搜索失败")
            }
            _uiState.update { it.copy(status = status) }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(status = DeviceDiscoveryStatus.PermissionDenied) }
    }
}
