package com.qierong.dlnascreencastdemo.feature.device

import com.qierong.dlnascreencastdemo.dlna.DeviceRepository
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.dlna.WifiNotConnectedException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchDevices_publishesSuccessWithDevices() = runTest(testDispatcher) {
        val device = sampleDevice()
        val viewModel = DeviceListViewModel(FakeDeviceRepository { listOf(device) })

        viewModel.searchDevices()
        assertEquals(DeviceDiscoveryStatus.Searching, viewModel.uiState.value.status)
        advanceUntilIdle()

        assertEquals(DeviceDiscoveryStatus.Success(count = 1), viewModel.uiState.value.status)
        assertEquals(listOf(device), viewModel.uiState.value.devices)
    }

    @Test
    fun searchDevices_publishesEmptyWhenNoRendererIsFound() = runTest(testDispatcher) {
        val viewModel = DeviceListViewModel(FakeDeviceRepository { emptyList() })

        viewModel.searchDevices()
        advanceUntilIdle()

        assertEquals(DeviceDiscoveryStatus.Empty, viewModel.uiState.value.status)
    }

    @Test
    fun searchDevices_publishesWifiDisconnected() = runTest(testDispatcher) {
        val viewModel = DeviceListViewModel(
            FakeDeviceRepository { throw WifiNotConnectedException() },
        )

        viewModel.searchDevices()
        advanceUntilIdle()

        assertEquals(DeviceDiscoveryStatus.WifiDisconnected, viewModel.uiState.value.status)
    }

    @Test
    fun searchDevices_publishesNetworkError() = runTest(testDispatcher) {
        val viewModel = DeviceListViewModel(
            FakeDeviceRepository { throw IOException("SSDP socket failed") },
        )

        viewModel.searchDevices()
        advanceUntilIdle()

        assertEquals(
            DeviceDiscoveryStatus.Error("SSDP socket failed"),
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun onPermissionDenied_publishesPermissionDenied() {
        val viewModel = DeviceListViewModel(FakeDeviceRepository { emptyList() })

        viewModel.onPermissionDenied()

        assertEquals(DeviceDiscoveryStatus.PermissionDenied, viewModel.uiState.value.status)
    }

    private fun sampleDevice() = DlnaDevice(
        id = "uuid:kodi",
        udn = "uuid:kodi",
        friendlyName = "客厅 Kodi",
        manufacturer = "Kodi Foundation",
        modelName = "Kodi",
        ipAddress = "192.168.1.20",
        descriptionUrl = "http://192.168.1.20/device.xml",
        avTransportControlUrl = "/avtransport",
    )

    private fun interface DiscoveryAction {
        suspend fun run(): List<DlnaDevice>
    }

    private class FakeDeviceRepository(
        private val action: DiscoveryAction,
    ) : DeviceRepository {
        override suspend fun discoverRenderers(): List<DlnaDevice> = action.run()
    }
}
