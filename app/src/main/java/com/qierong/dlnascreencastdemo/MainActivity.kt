package com.qierong.dlnascreencastdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.remember
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

                HomeScreen(
                    state = HomeUiState(),
                    deviceState = deviceState,
                    captureState = captureState,
                    dlnaControlState = dlnaControlState,
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
                            val needsNotificationPermission =
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                            capturePermissionCoordinator.start(
                                needsNotificationPermission = needsNotificationPermission,
                                requestNotificationPermission = {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                },
                            )
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
            }
        }
    }

    private fun createScreenCaptureIntent(
        projectionManager: MediaProjectionManager,
    ): Intent {
        Log.i(TAG, "申请系统录屏授权")
        return projectionManager.createScreenCaptureIntent()
    }

    private companion object {
        const val TAG = "ScreenCapture"
    }
}
