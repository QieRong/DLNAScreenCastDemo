package com.qierong.dlnascreencastdemo.feature.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qierong.dlnascreencastdemo.dlna.AndroidDiscoveryLogger
import com.qierong.dlnascreencastdemo.dlna.AndroidNetworkStatusProvider
import com.qierong.dlnascreencastdemo.dlna.DefaultDeviceRepository
import com.qierong.dlnascreencastdemo.dlna.discovery.DeviceDescriptionClient
import com.qierong.dlnascreencastdemo.dlna.discovery.SsdpDiscoveryClient

class DeviceListViewModelFactory(
    context: Context,
) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DeviceListViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        val connectivityManager =
            applicationContext.getSystemService(ConnectivityManager::class.java)
        val repository = DefaultDeviceRepository(
            locationDiscovery = SsdpDiscoveryClient(wifiManager),
            descriptionFetcher = DeviceDescriptionClient(),
            networkStatusProvider = AndroidNetworkStatusProvider(connectivityManager),
            logger = AndroidDiscoveryLogger,
        )

        @Suppress("UNCHECKED_CAST")
        return DeviceListViewModel(repository) as T
    }
}
