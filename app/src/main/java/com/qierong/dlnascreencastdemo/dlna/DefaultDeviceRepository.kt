package com.qierong.dlnascreencastdemo.dlna

import com.qierong.dlnascreencastdemo.dlna.discovery.DeviceDescriptionFetcher
import com.qierong.dlnascreencastdemo.dlna.discovery.DeviceDescriptionParser
import com.qierong.dlnascreencastdemo.dlna.discovery.RendererLocationDiscovery
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DefaultDeviceRepository(
    private val locationDiscovery: RendererLocationDiscovery,
    private val descriptionFetcher: DeviceDescriptionFetcher,
    private val networkStatusProvider: NetworkStatusProvider,
    private val descriptionParser: DeviceDescriptionParser = DeviceDescriptionParser(),
    private val logger: DiscoveryLogger = NoOpDiscoveryLogger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceRepository {
    override suspend fun discoverRenderers(): List<DlnaDevice> = withContext(ioDispatcher) {
        if (!networkStatusProvider.isWifiConnected()) {
            throw WifiNotConnectedException()
        }

        val devices = buildList {
            locationDiscovery
                .discoverDescriptionUrls()
                .distinct()
                .forEach { descriptionUrl ->
                    currentCoroutineContext().ensureActive()
                    parseDeviceOrNull(descriptionUrl)?.let(::add)
                }
        }

        devices
            .distinctBy(DlnaDevice::id)
            .sortedBy { it.friendlyName.lowercase() }
            .also { devices ->
                logger.debug("设备搜索结束：发现 ${devices.size} 个 Renderer")
            }
    }

    private fun parseDeviceOrNull(descriptionUrl: URI): DlnaDevice? {
        return try {
            val xml = descriptionFetcher.fetch(descriptionUrl).toString(Charsets.UTF_8)
            descriptionParser.parse(descriptionUrl, xml)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warn("跳过无法解析的设备描述：$descriptionUrl", exception)
            null
        }
    }
}
