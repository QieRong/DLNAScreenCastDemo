package com.qierong.dlnascreencastdemo.dlna.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoapResponseParserTest {
    private val parser = SoapResponseParser()

    @Test
    fun parseFault_returnsFaultCodeStringAndUpnpError() {
        val fault = parser.parseFault(
            """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <s:Fault>
                      <faultcode>s:Client</faultcode>
                      <faultstring>UPnPError</faultstring>
                      <detail>
                        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                          <errorCode>714</errorCode>
                          <errorDescription>No such resource</errorDescription>
                        </UPnPError>
                      </detail>
                    </s:Fault>
                  </s:Body>
                </s:Envelope>
            """.trimIndent(),
        )

        assertEquals(
            SoapFault(
                faultCode = "s:Client",
                faultString = "UPnPError",
                upnpErrorCode = "714",
                upnpErrorDescription = "No such resource",
            ),
            fault,
        )
    }

    @Test
    fun parseFault_returnsNullWhenBodyHasNoFault() {
        assertNull(
            parser.parseFault(
                """
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                      <s:Body>
                        <u:PlayResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1" />
                      </s:Body>
                    </s:Envelope>
                """.trimIndent(),
            ),
        )
    }
}
