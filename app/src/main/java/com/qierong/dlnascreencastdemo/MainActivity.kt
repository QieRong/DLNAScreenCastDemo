package com.qierong.dlnascreencastdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qierong.dlnascreencastdemo.capture.CapturePermissionCoordinator
import com.qierong.dlnascreencastdemo.capture.ScreenCaptureService
import com.qierong.dlnascreencastdemo.feature.casting.DlnaControlViewModel
import com.qierong.dlnascreencastdemo.feature.casting.ScreenCaptureViewModel
import com.qierong.dlnascreencastdemo.feature.device.DeviceListViewModel
import com.qierong.dlnascreencastdemo.feature.device.DeviceListViewModelFactory
import com.qierong.dlnascreencastdemo.feature.home.HomeScreen
import com.qierong.dlnascreencastdemo.feature.home.HomeUiState
import com.qierong.dlnascreencastdemo.feature.metrics.DynamicBitrateTestScreen
import com.qierong.dlnascreencastdemo.feature.metrics.LatencyTestScreen
import com.qierong.dlnascreencastdemo.feature.metrics.MetricsDemoPage
import com.qierong.dlnascreencastdemo.ui.theme.DLNAScreenCastDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DLNAScreenCastDemoTheme {
                val deviceListViewModel: DeviceListViewModel = viewModel(
                    factory = DeviceListViewModelFactory(applicationContext),
                )
                val deviceState by deviceListViewModel.uiState.collectAsStateWithLifecycle()
                val captureViewModel: ScreenCaptureViewModel = viewModel()
                val captureState by captureViewModel.uiState.collectAsStateWithLifecycle()
                val dlnaControlViewModel: DlnaControlViewModel = viewModel()
                val dlnaControlState by dlnaControlViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(deviceState.devices, dlnaControlState.selectedDevice) {
                    val singleDevice = deviceState.devices.singleOrNull()
                    if (singleDevice?.avTransportControlUrl != null &&
                        dlnaControlState.selectedDevice == null
                    ) {
                        dlnaControlViewModel.selectDevice(singleDevice)
                    }
                }
                var metricsDemoPage by rememberSaveable {
                    mutableStateOf<MetricsDemoPage?>(null)
                }
                var audioPermissionRequestedThisSession by rememberSaveable {
                    mutableStateOf(false)
                }
                var pendingPlaybackAudioPermission by rememberSaveable {
                    mutableStateOf(false)
                }
                val projectionManager = remember {
                    getSystemService(MediaProjectionManager::class.java)
                }
                val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    if (isGranted) {
                        deviceListViewModel.searchDevices()
                    } else {
                        deviceListViewModel.onPermissionDenied()
                    }
                }
                val projectionPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val resultData = result.data
                    if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                        captureViewModel.onProjectionPermissionGranted()
                        runCatching {
                            ScreenCaptureService.start(
                                context = applicationContext,
                                resultCode = result.resultCode,
                                resultData = resultData,
                                playbackAudioPermissionGranted = pendingPlaybackAudioPermission,
                            )
                        }.onFailure { exception ->
                            captureViewModel.onServiceStartFailed(
                                exception.message ?: "无法启动屏幕采集服务",
                            )
                        }
                    } else {
                        captureViewModel.onProjectionPermissionDenied()
                    }
                }
                val capturePermissionCoordinator = remember {
                    CapturePermissionCoordinator(
                        requestProjectionPermission = {
                            projectionPermissionLauncher.launch(
                                createScreenCaptureIntent(projectionManager),
                            )
                        },
                    )
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    capturePermissionCoordinator.onNotificationPermissionResult(isGranted)
                }
                fun startProjectionFlow(playbackAudioPermissionGranted: Boolean) {
                    pendingPlaybackAudioPermission = playbackAudioPermissionGranted
                    val needsNotificationPermission =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                POST_NOTIFICATIONS_PERMISSION,
                            ) != PackageManager.PERMISSION_GRANTED
                    capturePermissionCoordinator.start(
                        needsNotificationPermission = needsNotificationPermission,
                        requestNotificationPermission = {
                                notificationPermissionLauncher.launch(
                                    POST_NOTIFICATIONS_PERMISSION,
                                )
                        },
                    )
                }
                val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    Log.i(TAG, "RECORD_AUDIO 权限结果：$isGranted")
                    startProjectionFlow(playbackAudioPermissionGranted = isGranted)
                }

                when (metricsDemoPage) {
                    null -> HomeScreen(
                        state = HomeUiState(),
                        deviceState = deviceState,
                        captureState = captureState,
                        dlnaControlState = dlnaControlState,
                        onOpenLatencyTest = {
                            metricsDemoPage = MetricsDemoPage.Latency
                        },
                        onOpenDynamicBitrateTest = {
                            metricsDemoPage = MetricsDemoPage.DynamicBitrate
                        },
                        onSearchDevices = {
                            val needsNearbyWifiPermission =
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.NEARBY_WIFI_DEVICES,
                                    ) != PackageManager.PERMISSION_GRANTED
                            if (needsNearbyWifiPermission) {
                                nearbyWifiPermissionLauncher.launch(
                                    Manifest.permission.NEARBY_WIFI_DEVICES,
                                )
                            } else {
                                deviceListViewModel.searchDevices()
                            }
                        },
                        onStartCapture = {
                            if (captureViewModel.requestCapturePermission()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    !hasPlaybackAudioPermission() &&
                                    !audioPermissionRequestedThisSession
                                ) {
                                    audioPermissionRequestedThisSession = true
                                    recordAudioPermissionLauncher.launch(
                                        Manifest.permission.RECORD_AUDIO,
                                    )
                                } else {
                                    startProjectionFlow(
                                        playbackAudioPermissionGranted = hasPlaybackAudioPermission(),
                                    )
                                }
                            }
                        },
                        onStopCapture = {
                            ScreenCaptureService.stop(applicationContext)
                        },
                        onSelectRenderer = dlnaControlViewModel::selectDevice,
                        onSendToRenderer = {
                            dlnaControlViewModel.sendToRenderer(captureState)
                        },
                        onPauseRenderer = dlnaControlViewModel::pause,
                        onStopRenderer = dlnaControlViewModel::stopRemotePlayback,
                    )

                    MetricsDemoPage.Latency -> LatencyTestScreen(
                        onBack = { metricsDemoPage = null },
                    )

                    MetricsDemoPage.DynamicBitrate -> DynamicBitrateTestScreen(
                        onBack = { metricsDemoPage = null },
                    )
                }
            }
        }
    }

    private fun createScreenCaptureIntent(
        projectionManager: MediaProjectionManager,
    ): Intent {
        Log.i(TAG, "申请系统录屏授权")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay(),
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
    }

    private fun hasPlaybackAudioPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "ScreenCapture"
        const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
