package com.qierong.dlnascreencastdemo.dlna

import com.qierong.dlnascreencastdemo.dlna.discovery.DeviceDescriptionFetcher
import com.qierong.dlnascreencastdemo.dlna.discovery.RendererLocationDiscovery
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.URI

class DefaultDeviceRepositoryTest {
    @Test
    fun discoverRenderers_stopsBeforeScanningWhenWifiIsDisconnected() = runTest {
        val discovery = FakeRendererLocationDiscovery(emptyList())
        val repository = DefaultDeviceRepository(
            locationDiscovery = discovery,
            descriptionFetcher = FakeDescriptionFetcher(emptyMap()),
            networkStatusProvider = NetworkStatusProvider { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            repository.discoverRenderers()
            fail("Expected WifiNotConnectedException")
        } catch (_: WifiNotConnectedException) {
        }
        assertEquals(0, discovery.callCount)
    }

    @Test
    fun discoverRenderers_deduplicatesDescriptionUrlsAndSkipsBrokenDevices() = runTest {
        val validUrl = URI("http://192.168.1.20/device.xml")
        val brokenUrl = URI("http://192.168.1.21/device.xml")
        val fetcher = FakeDescriptionFetcher(
            mapOf(
                validUrl to rendererXml("uuid:kodi", "客厅 Kodi"),
                brokenUrl to IOException("timeout"),
            ),
        )
        val logger = RecordingDiscoveryLogger()
        val repository = DefaultDeviceRepository(
            locationDiscovery = FakeRendererLocationDiscovery(
                listOf(validUrl, validUrl, brokenUrl),
            ),
            descriptionFetcher = fetcher,
            networkStatusProvider = NetworkStatusProvider { true },
            logger = logger,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val devices = repository.discoverRenderers()

        assertEquals(listOf("uuid:kodi"), devices.map(DlnaDevice::id))
        assertEquals(1, fetcher.callCountByUrl.getValue(validUrl))
        assertEquals(1, fetcher.callCountByUrl.getValue(brokenUrl))
        assertTrue(logger.warningMessage.contains("url=$brokenUrl"))
        assertTrue(logger.warningMessage.contains("type=IOException"))
        assertTrue(logger.warningMessage.contains("reason=timeout"))
        assertFalse(logger.warningMessage.contains(rendererXml("uuid:kodi", "客厅 Kodi").decodeToString()))
        assertNull(logger.warningThrowable)
    }

    private fun rendererXml(udn: String, friendlyName: String): ByteArray = """
        <root>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>$friendlyName</friendlyName>
            <UDN>$udn</UDN>
          </device>
        </root>
    """.trimIndent().toByteArray()

    private class FakeRendererLocationDiscovery(
        private val locations: List<URI>,
    ) : RendererLocationDiscovery {
        var callCount: Int = 0

        override suspend fun discoverDescriptionUrls(): List<URI> {
            callCount += 1
            return locations
        }
    }

    private class FakeDescriptionFetcher(
        private val results: Map<URI, Any>,
    ) : DeviceDescriptionFetcher {
        val callCountByUrl = mutableMapOf<URI, Int>()

        override fun fetch(descriptionUrl: URI): ByteArray {
            callCountByUrl[descriptionUrl] = callCountByUrl.getOrDefault(descriptionUrl, 0) + 1
            return when (val result = results.getValue(descriptionUrl)) {
                is ByteArray -> result
                is Exception -> throw result
                else -> error("Unsupported fake result")
            }
        }
    }

    private class RecordingDiscoveryLogger : DiscoveryLogger {
        var warningMessage: String = ""
        var warningThrowable: Throwable? = null

        override fun debug(message: String) = Unit

        override fun warn(message: String, throwable: Throwable?) {
            warningMessage = message
            warningThrowable = throwable
        }
    }
}
