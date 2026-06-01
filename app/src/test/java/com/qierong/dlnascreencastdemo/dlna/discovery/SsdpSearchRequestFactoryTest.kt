package com.qierong.dlnascreencastdemo.dlna.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpSearchRequestFactoryTest {
    @Test
    fun searchTargets_areRestrictedToRendererV1AndSsdpAll() {
        assertEquals(
            listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "ssdp:all",
            ),
            SsdpSearchRequestFactory.SEARCH_TARGETS,
        )
    }

    @Test
    fun create_includesRequiredSsdpHeaders() {
        val request = SsdpSearchRequestFactory.create("ssdp:all")

        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(request.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(request.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(request.contains("MX: 2\r\n"))
        assertTrue(request.contains("ST: ssdp:all\r\n"))
        assertTrue(request.endsWith("\r\n\r\n"))
    }
}
