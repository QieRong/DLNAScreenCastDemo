package com.qierong.dlnascreencastdemo.stream

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

data class NetworkAddress(
    val interfaceName: String,
    val address: InetAddress,
)

class StreamUrlProvider(
    private val addressProvider: () -> Sequence<NetworkAddress> = ::networkAddresses,
) {
    fun resolve(port: Int): String? =
        addressProvider()
            .firstOrNull { networkAddress ->
                networkAddress.interfaceName.startsWith(WIFI_INTERFACE_PREFIX) &&
                    networkAddress.address is Inet4Address &&
                    networkAddress.address.isSiteLocalAddress &&
                    !networkAddress.address.isLoopbackAddress &&
                    !networkAddress.address.isLinkLocalAddress
            }
            ?.address
            ?.hostAddress
            ?.let { host -> "http://$host:$port/live.ts" }

    companion object {
        private const val WIFI_INTERFACE_PREFIX = "wlan"

        private fun networkAddresses(): Sequence<NetworkAddress> =
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .flatMap { networkInterface ->
                    Collections.list(networkInterface.inetAddresses)
                        .asSequence()
                        .map { address -> NetworkAddress(networkInterface.name, address) }
                }
    }
}
