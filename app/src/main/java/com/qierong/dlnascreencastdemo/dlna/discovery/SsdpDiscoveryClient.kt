package com.qierong.dlnascreencastdemo.dlna.discovery

import android.net.wifi.WifiManager
import com.qierong.dlnascreencastdemo.dlna.AndroidDiscoveryLogger
import com.qierong.dlnascreencastdemo.dlna.DiscoveryLogger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SsdpDiscoveryClient(
    private val wifiManager: WifiManager,
    private val logger: DiscoveryLogger = AndroidDiscoveryLogger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RendererLocationDiscovery {
    override suspend fun discoverDescriptionUrls(): List<URI> = withContext(ioDispatcher) {
        logger.debug("设备搜索开始")
        val multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }

        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(0))
                socket.soTimeout = RECEIVE_TIMEOUT_MILLIS
                sendSearchRequests(socket)
                receiveDescriptionUrls(socket)
            }
        } finally {
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
            logger.debug("SSDP 搜索结束")
        }
    }

    private suspend fun sendSearchRequests(socket: DatagramSocket) {
        val destination = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
        SsdpSearchRequestFactory.SEARCH_TARGETS.forEach { searchTarget ->
            currentCoroutineContext().ensureActive()
            val payload = SsdpSearchRequestFactory.create(searchTarget).toByteArray()
            socket.send(DatagramPacket(payload, payload.size, destination, SSDP_PORT))
            logger.debug("已发送 M-SEARCH：$searchTarget")
        }
    }

    private suspend fun receiveDescriptionUrls(socket: DatagramSocket): List<URI> {
        val deadlineNanos = System.nanoTime() + DISCOVERY_WINDOW_NANOS
        val descriptionUrls = linkedSetOf<URI>()

        while (System.nanoTime() < deadlineNanos) {
            currentCoroutineContext().ensureActive()
            val buffer = ByteArray(MAX_SSDP_RESPONSE_BYTES)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                val response = String(packet.data, packet.offset, packet.length)
                val descriptionUrl = SsdpResponseParser.parseDescriptionUrl(response)
                if (descriptionUrl != null && descriptionUrls.add(descriptionUrl)) {
                    logger.debug("收到 SSDP 响应：$descriptionUrl")
                }
            } catch (_: SocketTimeoutException) {
                // Short receive timeouts make cancellation checks responsive.
            }
        }
        return descriptionUrls.toList()
    }

    private companion object {
        const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val RECEIVE_TIMEOUT_MILLIS = 250
        const val DISCOVERY_WINDOW_NANOS = 4_000_000_000L
        const val MAX_SSDP_RESPONSE_BYTES = 64 * 1024
        const val MULTICAST_LOCK_TAG = "DLNA-Demo-SSDP"
    }
}
