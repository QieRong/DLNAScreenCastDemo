package com.qierong.dlnascreencastdemo.stream

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlProviderTest {
    @Test
    fun resolve_prefersSiteLocalIpv4Address() {
        val provider = StreamUrlProvider {
            sequenceOf(
                NetworkAddress("lo", InetAddress.getByName("127.0.0.1")),
                NetworkAddress("rmnet_data5", InetAddress.getByName("10.52.168.147")),
                NetworkAddress("wlan0", InetAddress.getByName("192.168.137.155")),
            )
        }

        assertEquals("http://192.168.137.155:8080/live.ts", provider.resolve(port = 8080))
    }

    @Test
    fun resolve_returnsNullWhenNoSiteLocalIpv4AddressExists() {
        val provider = StreamUrlProvider {
            sequenceOf(
                NetworkAddress("lo", InetAddress.getByName("127.0.0.1")),
                NetworkAddress("rmnet_data5", InetAddress.getByName("10.52.168.147")),
            )
        }

        assertNull(provider.resolve(port = 8080))
    }
}
