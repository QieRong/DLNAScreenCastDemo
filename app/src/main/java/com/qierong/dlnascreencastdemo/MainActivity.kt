package com.qierong.dlnascreencastdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    if (isGranted) {
                        deviceListViewModel.searchDevices()
                    } else {
                        deviceListViewModel.onPermissionDenied()
                    }
                }

                HomeScreen(
                    state = HomeUiState(),
                    deviceState = deviceState,
                    onSearchDevices = {
                        val needsNearbyWifiPermission =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.NEARBY_WIFI_DEVICES,
                                ) != PackageManager.PERMISSION_GRANTED
                        if (needsNearbyWifiPermission) {
                            permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                        } else {
                            deviceListViewModel.searchDevices()
                        }
                    },
                )
            }
        }
    }
}
