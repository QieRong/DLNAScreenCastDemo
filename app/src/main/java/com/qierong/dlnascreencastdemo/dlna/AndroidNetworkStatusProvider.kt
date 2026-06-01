package com.qierong.dlnascreencastdemo.dlna

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidNetworkStatusProvider(
    private val connectivityManager: ConnectivityManager,
) : NetworkStatusProvider {
    override fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
