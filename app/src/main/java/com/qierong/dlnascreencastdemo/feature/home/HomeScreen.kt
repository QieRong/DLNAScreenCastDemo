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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.capture.hasActiveSession
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlFailureReason
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlStatus
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlUiState
import com.qierong.dlnascreencastdemo.feature.device.DeviceDiscoveryStatus
import com.qierong.dlnascreencastdemo.feature.device.DeviceListUiState
import com.qierong.dlnascreencastdemo.feature.metrics.MetricsStatusCard
import com.qierong.dlnascreencastdemo.feature.metrics.buildMetricStatusItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    deviceState: DeviceListUiState,
    captureState: CaptureState,
    dlnaControlState: DlnaControlUiState,
    onOpenLatencyTest: () -> Unit,
    onOpenDynamicBitrateTest: () -> Unit,
    onSearchDevices: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSelectRenderer: (DlnaDevice) -> Unit,
    onSendToRenderer: () -> Unit,
    onPauseRenderer: () -> Unit,
    onStopRenderer: () -> Unit,
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
            captureState = captureState,
            dlnaControlState = dlnaControlState,
            onOpenLatencyTest = onOpenLatencyTest,
            onOpenDynamicBitrateTest = onOpenDynamicBitrateTest,
            onSearchDevices = onSearchDevices,
            onStartCapture = onStartCapture,
            onStopCapture = onStopCapture,
            onSelectRenderer = onSelectRenderer,
            onSendToRenderer = onSendToRenderer,
            onPauseRenderer = onPauseRenderer,
            onStopRenderer = onStopRenderer,
            contentPadding = contentPadding,
        )
    }
}
@Composable
private fun HomeContent(
    state: HomeUiState,
    deviceState: DeviceListUiState,
    captureState: CaptureState,
    dlnaControlState: DlnaControlUiState,
    onOpenLatencyTest: () -> Unit,
    onOpenDynamicBitrateTest: () -> Unit,
    onSearchDevices: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSelectRenderer: (DlnaDevice) -> Unit,
    onSendToRenderer: () -> Unit,
    onPauseRenderer: () -> Unit,
    onStopRenderer: () -> Unit,
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
            VerificationGuideCard(
                deviceState = deviceState,
                captureState = captureState,
                dlnaControlState = dlnaControlState,
            )
        }
        item {
            PrimaryActionCard(
                deviceState = deviceState,
                captureState = captureState,
                dlnaControlState = dlnaControlState,
                onSearchDevices = onSearchDevices,
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture,
                onSendToRenderer = onSendToRenderer,
                onPauseRenderer = onPauseRenderer,
                onStopRenderer = onStopRenderer,
            )
        }
        item {
            StatusCard(title = "性能指标", detail = state.metricsNotice)
        }
        item {
            MetricsStatusCard(items = buildMetricStatusItems(captureState))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "指标演示 / 验收辅助",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "页面只用于辅助录像和动态样本测试，不代表指标自动达成。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onOpenLatencyTest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "打开延迟测试页")
                    }
                    Button(
                        onClick = onOpenDynamicBitrateTest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "打开动态码率测试页")
                    }
                }
            }
        }
        item {
            CaptureStatusCard(state = captureState)
        }
        item {
            EncoderStatusCard(state = captureState)
        }
        item {
            StreamStatusCard(state = captureState)
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
                DeviceCard(
                    device = device,
                    isSelected = dlnaControlState.selectedDevice?.id == device.id,
                    onSelectRenderer = { onSelectRenderer(device) },
                )
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
private fun PrimaryActionCard(
    deviceState: DeviceListUiState,
    captureState: CaptureState,
    dlnaControlState: DlnaControlUiState,
    onSearchDevices: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSendToRenderer: () -> Unit,
    onPauseRenderer: () -> Unit,
    onStopRenderer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "按顺序操作",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = buildOperationStatus(deviceState, dlnaControlState),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSearchDevices,
                enabled = !deviceState.isSearching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (deviceState.isSearching) "正在搜索设备..." else "1. 搜索 DLNA 设备（先打开 Kodi）")
            }
            Button(
                onClick = onStartCapture,
                enabled = !captureState.hasActiveSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "3. 开始采集（录音 + 录屏授权）")
            }
            Button(
                onClick = onSendToRenderer,
                enabled = dlnaControlState.canSendToRenderer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "4. 发送到 Kodi / 开始播放")
            }
            Button(
                onClick = onPauseRenderer,
                enabled = dlnaControlState.canPause,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "暂停 Kodi")
            }
            Button(
                onClick = onStopRenderer,
                enabled = dlnaControlState.canStopRemotePlayback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "停止 Kodi 播放")
            }
            Button(
                onClick = onStopCapture,
                enabled = captureState.hasActiveSession &&
                    captureState != CaptureState.RequestingPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "停止采集")
            }
        }
    }
}

private fun buildOperationStatus(
    deviceState: DeviceListUiState,
    dlnaControlState: DlnaControlUiState,
): String {
    val discoveryLine = "设备发现：${deviceDiscoverySummary(deviceState.status)}"
    val rendererLine = dlnaControlState.selectedDevice?.let { device ->
        "目标 Renderer：${device.friendlyName}（${device.ipAddress}）"
    } ?: "目标 Renderer：未选择"
    val playbackLine = "Kodi 播放状态：${dlnaStatusSummary(dlnaControlState.status)}"
    return "$discoveryLine\n$rendererLine\n$playbackLine"
}

private fun deviceDiscoverySummary(status: DeviceDiscoveryStatus): String = when (status) {
    DeviceDiscoveryStatus.Idle -> "尚未搜索"
    DeviceDiscoveryStatus.Searching -> "正在搜索"
    DeviceDiscoveryStatus.Empty -> "未发现 Renderer"
    is DeviceDiscoveryStatus.Success -> "已发现 ${status.count} 个 Renderer"
    DeviceDiscoveryStatus.PermissionDenied -> "附近设备权限被拒绝"
    DeviceDiscoveryStatus.WifiDisconnected -> "Wi-Fi 未连接"
    is DeviceDiscoveryStatus.Error -> "搜索失败：${status.detail}"
}

private fun dlnaStatusSummary(status: DlnaControlStatus): String = when (status) {
    DlnaControlStatus.NoRendererSelected -> "未选择 Renderer"
    DlnaControlStatus.DeviceNotControllable -> "设备缺少 AVTransport controlURL"
    DlnaControlStatus.LocalStreamNotStarted -> "本地 /live.ts 未启动"
    is DlnaControlStatus.InProgress -> "正在执行 ${status.stage.label}"
    DlnaControlStatus.UriSet -> "已设置播放地址"
    DlnaControlStatus.Playing -> "命令已发送，需观察 Kodi 画面和声音"
    DlnaControlStatus.Paused -> "已暂停"
    DlnaControlStatus.Stopped -> "已停止"
    is DlnaControlStatus.Failed -> "失败：${status.reason.toDisplayName()}"
}

@Composable
private fun VerificationGuideCard(
    deviceState: DeviceListUiState,
    captureState: CaptureState,
    dlnaControlState: DlnaControlUiState,
    modifier: Modifier = Modifier,
) {
    StatusCard(
        title = "PR14 真机验证步骤",
        detail = """
            当前下一步：${buildVerificationNextStep(deviceState, captureState, dlnaControlState)}

            1. 电脑端先打开 Kodi，并确认 UPnP / DLNA Renderer 已开启。
            2. 手机先播放本地 MP4，优先选有倒计时、击掌或节奏点的视频，媒体音量不要为 0。
            3. 回到本 App，按按钮顺序：搜索 DLNA 设备 -> 选择 Renderer -> 开始采集 -> 发送到 Kodi；如果系统询问采集范围，必须选择整个屏幕，不要选择单个 App。
            4. 接收端听到目标 MP4 声音后，再用 vivo X80 录接收端画面和声音，做人工观察级音画同步判断。
            5. PR14 不验证手机端静音或远端独占出声；抖音只作为第二轮附加验证。
        """.trimIndent(),
        modifier = modifier,
    )
}

internal fun buildVerificationNextStep(
    deviceState: DeviceListUiState,
    captureState: CaptureState,
    dlnaControlState: DlnaControlUiState,
): String = when {
    deviceState.isSearching -> "等待搜索完成，看到 Kodi 后点“2. 选择这个 Renderer”。"
    dlnaControlState.selectedDevice == null -> "先打开 Kodi，然后点“1. 搜索 DLNA 设备（先打开 Kodi）”。"
    !captureState.hasActiveSession -> "先播放本地 MP4 并确认音量，然后点“3. 开始采集（录音 + 录屏授权）”。"
    captureState == CaptureState.RequestingPermission -> "在系统弹窗里允许录音和录屏权限。"
    captureState == CaptureState.Starting -> "等待采集启动并出现 /live.ts 地址。"
    dlnaControlState.status !is DlnaControlStatus.Playing -> "点“4. 发送到 Kodi / 开始播放”，让 Kodi 重新拉取当前 /live.ts。"
    else -> "观察 Kodi 是否播放当前手机画面和目标 MP4 声音，并录制接收端证据。"
}

@Composable
private fun CaptureStatusCard(
    state: CaptureState,
    modifier: Modifier = Modifier,
) {
    val detail = when (state) {
        CaptureState.Idle -> "未采集。点击“开始采集”后，系统会请求本次会话的录屏授权。"
        CaptureState.RequestingPermission -> "正在等待系统录屏授权。每次开始采集都必须重新授权。"
        CaptureState.Starting -> "授权成功，正在启动屏幕采集前台服务。"
        is CaptureState.Capturing -> state.sessionInfo.sourceConfig.run {
            "采集中：源画面 $width x $height px"
        }
        is CaptureState.Reconfiguring -> state.targetSourceConfig.run {
            "正在按最新尺寸重建 H.264 编码器：源画面 $width x $height px"
        }
        CaptureState.Stopping -> "正在停止采集并释放资源。"
        CaptureState.PermissionDenied -> "系统录屏授权已拒绝，未启动采集。"
        is CaptureState.Error -> "屏幕采集失败：${state.detail}"
    }
    StatusCard(title = "屏幕采集状态", detail = detail, modifier = modifier)
}

@Composable
private fun DiscoveryStatusCard(
    status: DeviceDiscoveryStatus,
    modifier: Modifier = Modifier,
) {
    val detail = when (status) {
        DeviceDiscoveryStatus.Idle -> "尚未搜索。已接入可控 Renderer 的 AVTransport 播放控制。"
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
            5. 搜索到设备后，仍需设备提供 AVTransport controlURL 才能播控。
        """.trimIndent(),
        modifier = modifier,
    )
}

@Composable
private fun DeviceCard(
    device: DlnaDevice,
    isSelected: Boolean,
    onSelectRenderer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avTransportStatus = device.avTransportControlUrl
        ?: "不可用于后续播控：未提供 AVTransport controlURL"
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isSelected) "${device.friendlyName}（已选择）" else device.friendlyName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = """
                    厂商：${device.manufacturer ?: "未提供"}
                    型号：${device.modelName ?: "未提供"}
                    IP：${device.ipAddress}
                    描述地址：${device.descriptionUrl}
                    AVTransport：$avTransportStatus
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onSelectRenderer,
                enabled = !isSelected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (isSelected) "已选择 Renderer" else "2. 选择这个 Renderer")
            }
        }
    }
}

@Composable
private fun DlnaControlCard(
    state: DlnaControlUiState,
    onSendToRenderer: () -> Unit,
    onPauseRenderer: () -> Unit,
    onStopRenderer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "DLNA 播放控制",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = dlnaControlDetail(state),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSendToRenderer,
                enabled = state.canSendToRenderer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "4. 发送到 Kodi / 开始播放")
            }
            Button(
                onClick = onPauseRenderer,
                enabled = state.canPause,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "暂停")
            }
            Button(
                onClick = onStopRenderer,
                enabled = state.canStopRemotePlayback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "停止播放")
            }
        }
    }
}

private fun dlnaControlDetail(state: DlnaControlUiState): String {
    val deviceLine = state.selectedDevice?.let { device ->
        "目标：${device.friendlyName}（${device.ipAddress}）"
    } ?: "目标：未选择 Renderer"
    val statusLine = when (val status = state.status) {
        DlnaControlStatus.NoRendererSelected -> "状态：未选择 Renderer。"
        DlnaControlStatus.DeviceNotControllable -> "状态：设备不可控，缺少 AVTransport controlURL。"
        DlnaControlStatus.LocalStreamNotStarted -> "状态：本地流未启动，请先开始采集生成 /live.ts。"
        is DlnaControlStatus.InProgress -> "状态：正在执行 ${status.stage.label}。"
        DlnaControlStatus.UriSet -> "状态：已设置播放地址，准备发送 Play。"
        DlnaControlStatus.Playing -> "状态：正在播放。命令成功不等于画面已实测成功。"
        DlnaControlStatus.Paused -> "状态：已暂停远端播放。"
        DlnaControlStatus.Stopped -> "状态：已停止远端播放。"
        is DlnaControlStatus.Failed ->
            "状态：失败（${status.reason.toDisplayName()}）：${status.detail}"
    }
    return "$deviceLine\n$statusLine"
}

private fun DlnaControlFailureReason.toDisplayName(): String = when (this) {
    DlnaControlFailureReason.NoRendererSelected -> "未选择 Renderer"
    DlnaControlFailureReason.DeviceNotControllable -> "设备不可控"
    DlnaControlFailureReason.LocalStreamNotStarted -> "本地流未启动"
    DlnaControlFailureReason.SetUri -> "SetURI"
    DlnaControlFailureReason.Play -> "Play"
    DlnaControlFailureReason.Pause -> "Pause"
    DlnaControlFailureReason.Stop -> "Stop"
    DlnaControlFailureReason.Network -> "网络"
    DlnaControlFailureReason.SoapFault -> "SOAP Fault"
}

@Composable
internal fun StatusCard(
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
