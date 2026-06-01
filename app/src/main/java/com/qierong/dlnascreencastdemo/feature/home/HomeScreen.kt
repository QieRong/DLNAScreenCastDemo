package com.qierong.dlnascreencastdemo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.feature.device.DeviceDiscoveryStatus
import com.qierong.dlnascreencastdemo.feature.device.DeviceListUiState
import com.qierong.dlnascreencastdemo.ui.theme.DLNAScreenCastDemoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    deviceState: DeviceListUiState,
    onSearchDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "DLNA 手机投屏 Demo") })
        },
    ) { contentPadding ->
        HomeContent(
            state = state,
            deviceState = deviceState,
            onSearchDevices = onSearchDevices,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    deviceState: DeviceListUiState,
    onSearchDevices: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "可演示技术原型",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            StatusCard(title = "当前阶段", detail = state.currentPhase)
        }
        item {
            StatusCard(title = "性能指标", detail = state.metricsNotice)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSearchDevices,
                    enabled = !deviceState.isSearching,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = if (deviceState.isSearching) "正在搜索设备..." else "搜索 DLNA 设备")
                }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "开始投屏（后续 PR 实现）")
                }
            }
        }
        item {
            DiscoveryStatusCard(status = deviceState.status)
        }
        if (deviceState.status == DeviceDiscoveryStatus.Empty) {
            item {
                EmptyStateCard()
            }
        }
        if (deviceState.devices.isNotEmpty()) {
            item {
                Text(
                    text = "发现的 Renderer",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(
                items = deviceState.devices,
                key = DlnaDevice::id,
            ) { device ->
                DeviceCard(device = device)
            }
        }
        item {
            Text(
                text = "阶段范围",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(state.plannedFeatures) { feature ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = feature,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoveryStatusCard(
    status: DeviceDiscoveryStatus,
    modifier: Modifier = Modifier,
) {
    val detail = when (status) {
        DeviceDiscoveryStatus.Idle -> "尚未搜索。当前 PR 仅实现设备发现，不实现投屏播放。"
        DeviceDiscoveryStatus.Searching -> "正在通过 SSDP 搜索局域网 Renderer，请稍候。"
        DeviceDiscoveryStatus.Empty -> "未发现 Renderer，请查看下方排查说明。"
        is DeviceDiscoveryStatus.Success -> "已发现 ${status.count} 个 Renderer。"
        DeviceDiscoveryStatus.PermissionDenied -> "附近设备权限被拒绝，无法搜索局域网 Renderer。"
        DeviceDiscoveryStatus.WifiDisconnected -> "未检测到 Wi-Fi，请先连接局域网后重试。"
        is DeviceDiscoveryStatus.Error -> "设备搜索失败：${status.detail}"
    }
    StatusCard(title = "设备发现状态", detail = detail, modifier = modifier)
}

@Composable
private fun EmptyStateCard(modifier: Modifier = Modifier) {
    StatusCard(
        title = "搜索不到设备时请检查",
        detail = """
            1. 手机和电脑或电视需连接同一 Wi-Fi。
            2. Kodi 需要开启 UPnP / DLNA。
            3. Windows 防火墙可能拦截局域网发现。
            4. 路由器 AP 隔离可能导致搜索不到。
            5. 当前 PR 仅实现设备发现，不实现投屏播放。
        """.trimIndent(),
        modifier = modifier,
    )
}

@Composable
private fun DeviceCard(
    device: DlnaDevice,
    modifier: Modifier = Modifier,
) {
    val avTransportStatus = device.avTransportControlUrl
        ?: "不可用于后续播控：未提供 AVTransport controlURL"
    StatusCard(
        title = device.friendlyName,
        detail = """
            厂商：${device.manufacturer ?: "未提供"}
            型号：${device.modelName ?: "未提供"}
            IP：${device.ipAddress}
            描述地址：${device.descriptionUrl}
            AVTransport：$avTransportStatus
        """.trimIndent(),
        modifier = modifier,
    )
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DLNAScreenCastDemoTheme {
        HomeScreen(
            state = HomeUiState(),
            deviceState = DeviceListUiState(status = DeviceDiscoveryStatus.Empty),
            onSearchDevices = {},
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
                        avTransportControlUrl = null,
                    ),
                ),
            ),
            onSearchDevices = {},
        )
    }
}
