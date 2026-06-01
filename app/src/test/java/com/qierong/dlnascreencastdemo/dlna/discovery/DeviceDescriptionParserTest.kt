package com.qierong.dlnascreencastdemo.dlna.discovery

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class DeviceDescriptionParserTest {
    private val parser = DeviceDescriptionParser()

    @Test
    fun parse_readsRendererMetadataAndResolvesRelativeAvTransportUrl() {
        val device = parser.parse(
            descriptionUrl = URI("http://192.168.1.20:1400/xml/device.xml"),
            xml = rendererXml(
                udn = "uuid:kodi-living-room",
                friendlyName = "客厅 Kodi",
                manufacturer = "Kodi Foundation",
                modelName = "Kodi",
                controlUrl = "/upnp/control/avtransport",
            ),
        )

        requireNotNull(device)
        assertEquals("uuid:kodi-living-room", device.id)
        assertEquals("uuid:kodi-living-room", device.udn)
        assertEquals("客厅 Kodi", device.friendlyName)
        assertEquals("Kodi Foundation", device.manufacturer)
        assertEquals("Kodi", device.modelName)
        assertEquals("192.168.1.20", device.ipAddress)
        assertEquals(
            "http://192.168.1.20:1400/upnp/control/avtransport",
            device.avTransportControlUrl,
        )
    }

    @Test
    fun parse_usesDescriptionUrlAsIdWhenUdnIsMissing() {
        val descriptionUrl = URI("http://192.168.1.21/device.xml")

        val device = parser.parse(
            descriptionUrl = descriptionUrl,
            xml = rendererXml(udn = "", friendlyName = ""),
        )

        requireNotNull(device)
        assertEquals(descriptionUrl.toString(), device.id)
        assertNull(device.udn)
        assertEquals("未命名设备", device.friendlyName)
    }

    @Test
    fun parse_keepsRendererWithoutAvTransport() {
        val device = parser.parse(
            descriptionUrl = URI("http://192.168.1.22/device.xml"),
            xml = rendererXml(controlUrl = null),
        )

        requireNotNull(device)
        assertNull(device.avTransportControlUrl)
    }

    @Test
    fun parse_ignoresNonRendererDevice() {
        assertNull(
            parser.parse(
                descriptionUrl = URI("http://192.168.1.23/device.xml"),
                xml = """
                    <root>
                      <device>
                        <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
                        <friendlyName>媒体服务器</friendlyName>
                      </device>
                    </root>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun parse_readsDefaultNamespaceRendererMetadata() {
        val device = parser.parse(
            descriptionUrl = URI("http://192.168.1.25:1932/"),
            xml = rendererXml(
                namespace = "urn:schemas-upnp-org:device-1-0",
                udn = "uuid:kodi-default-namespace",
                friendlyName = "Kodi 默认命名空间",
                controlUrl = "/AVTransport/control.xml",
            ),
        )

        requireNotNull(device)
        assertEquals("uuid:kodi-default-namespace", device.udn)
        assertEquals("Kodi 默认命名空间", device.friendlyName)
        assertEquals(
            "http://192.168.1.25:1932/AVTransport/control.xml",
            device.avTransportControlUrl,
        )
    }

    @Test
    fun parse_rejectsMixedCaseDoctypeWithExplicitSecurityReason() {
        val unsafeXml = """
            <!DoCtYpE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>&xxe;</friendlyName>
              </device>
            </root>
        """.trimIndent()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            parser.parse(URI("http://192.168.1.24/device.xml"), unsafeXml)
        }
        assertEquals("XML 安全限制：禁止 DOCTYPE 声明", exception.message)
    }

    @Test
    fun parse_doesNotAccessExternalEntityUrl() {
        val requestCount = AtomicInteger()
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        ).apply {
            createContext("/external") { exchange ->
                requestCount.incrementAndGet()
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            start()
        }

        try {
            val unsafeXml = """
                <!DOCTYPE root [
                  <!ENTITY xxe SYSTEM "http://127.0.0.1:${server.address.port}/external">
                ]>
                <root>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>&xxe;</friendlyName>
                  </device>
                </root>
            """.trimIndent()

            assertThrows(IllegalArgumentException::class.java) {
                parser.parse(URI("http://192.168.1.26/device.xml"), unsafeXml)
            }
            assertEquals(0, requestCount.get())
        } finally {
            server.stop(0)
        }
    }

    private fun rendererXml(
        namespace: String? = null,
        udn: String = "uuid:test-renderer",
        friendlyName: String = "测试 Renderer",
        manufacturer: String? = null,
        modelName: String? = null,
        controlUrl: String? = "/avtransport",
    ): String = """
        <root${namespace?.let { """ xmlns="$it"""" }.orEmpty()}>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>$friendlyName</friendlyName>
            <manufacturer>${manufacturer.orEmpty()}</manufacturer>
            <modelName>${modelName.orEmpty()}</modelName>
            <UDN>$udn</UDN>
            <serviceList>
              ${
                controlUrl?.let {
                    """
                    <service>
                      <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                      <controlURL>$it</controlURL>
                    </service>
                    """.trimIndent()
                }.orEmpty()
            }
            </serviceList>
          </device>
        </root>
    """.trimIndent()
}
