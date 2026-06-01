package com.qierong.dlnascreencastdemo.dlna.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class SsdpResponseParserTest {
    @Test
    fun parseDescriptionUrl_acceptsCaseInsensitiveLocationHeader() {
        val response = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            LoCaTiOn: http://192.168.1.20:1400/xml/device.xml
        """.trimIndent()

        assertEquals(
            URI("http://192.168.1.20:1400/xml/device.xml"),
            SsdpResponseParser.parseDescriptionUrl(response),
        )
    }

    @Test
    fun parseDescriptionUrl_normalizesPathBeforeDeduplication() {
        val response = """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.20:1400/xml/../device.xml
        """.trimIndent()

        assertEquals(
            URI("http://192.168.1.20:1400/device.xml"),
            SsdpResponseParser.parseDescriptionUrl(response),
        )
    }

    @Test
    fun parseDescriptionUrl_ignoresMissingOrUnsupportedLocation() {
        assertNull(SsdpResponseParser.parseDescriptionUrl("HTTP/1.1 200 OK"))
        assertNull(
            SsdpResponseParser.parseDescriptionUrl(
                "HTTP/1.1 200 OK\r\nLOCATION: file:///tmp/device.xml",
            ),
        )
        assertNull(
            SsdpResponseParser.parseDescriptionUrl(
                "HTTP/1.1 200 OK\r\nLOCATION: http:/device.xml",
            ),
        )
    }
}
