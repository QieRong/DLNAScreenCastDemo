package com.qierong.dlnascreencastdemo.dlna.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URI

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
    fun parse_rejectsDoctypeAndExternalEntities() {
        val unsafeXml = """
            <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>&xxe;</friendlyName>
              </device>
            </root>
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(URI("http://192.168.1.24/device.xml"), unsafeXml)
        }
    }

    private fun rendererXml(
        udn: String = "uuid:test-renderer",
        friendlyName: String = "测试 Renderer",
        manufacturer: String? = null,
        modelName: String? = null,
        controlUrl: String? = "/avtransport",
    ): String = """
        <root>
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
