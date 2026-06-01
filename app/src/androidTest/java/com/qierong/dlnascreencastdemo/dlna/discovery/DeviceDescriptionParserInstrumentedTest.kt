package com.qierong.dlnascreencastdemo.dlna.discovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDescriptionParserInstrumentedTest {
    private val parser = DeviceDescriptionParser()

    @Test
    fun parse_readsKodiStyleDefaultNamespaceRendererOnAndroid() {
        val device = parser.parse(
            descriptionUrl = URI("http://192.168.137.1:1932/"),
            xml = """
                <root xmlns="urn:schemas-upnp-org:device-1-0">
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>Kodi</friendlyName>
                    <UDN>uuid:kodi-renderer</UDN>
                    <serviceList>
                      <service>
                        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                        <controlURL>/AVTransport/control.xml</controlURL>
                      </service>
                    </serviceList>
                  </device>
                </root>
            """.trimIndent(),
        )

        requireNotNull(device)
        assertEquals("Kodi", device.friendlyName)
        assertEquals("uuid:kodi-renderer", device.udn)
        assertEquals(
            "http://192.168.137.1:1932/AVTransport/control.xml",
            device.avTransportControlUrl,
        )
    }

    @Test
    fun parse_rejectsDoctypeOnAndroid() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                descriptionUrl = URI("http://192.168.137.1:1932/"),
                xml = """
                    <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                    <root>
                      <device>
                        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                        <friendlyName>&xxe;</friendlyName>
                      </device>
                    </root>
                """.trimIndent(),
            )
        }

        assertEquals("XML 安全限制：禁止 DOCTYPE 声明", exception.message)
    }
}
