package com.qierong.dlnascreencastdemo.dlna.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoapRequestBuilderTest {
    private val builder = SoapRequestBuilder()

    @Test
    fun setAvTransportUri_escapesStreamUrlAndUsesEmptyMetadata() {
        val request = builder.setAvTransportUri(
            streamUrl = "http://192.168.1.8:8080/live.ts?token=a&b=<c>",
        )

        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            request.soapActionHeader,
        )
        assertTrue(request.body.contains("<InstanceID>0</InstanceID>"))
        assertTrue(
            request.body.contains(
                "<CurrentURI>http://192.168.1.8:8080/live.ts?token=a&amp;b=&lt;c&gt;</CurrentURI>",
            ),
        )
        assertTrue(request.body.contains("<CurrentURIMetaData></CurrentURIMetaData>"))
        assertFalse(request.body.contains("token=a&b=<c>"))
    }

    @Test
    fun play_usesSpeedOne() {
        val request = builder.play()

        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            request.soapActionHeader,
        )
        assertTrue(request.body.contains("<InstanceID>0</InstanceID>"))
        assertTrue(request.body.contains("<Speed>1</Speed>"))
    }

    @Test
    fun pauseAndStop_useExpectedSoapActions() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Pause\"",
            builder.pause().soapActionHeader,
        )
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"",
            builder.stop().soapActionHeader,
        )
    }
}
