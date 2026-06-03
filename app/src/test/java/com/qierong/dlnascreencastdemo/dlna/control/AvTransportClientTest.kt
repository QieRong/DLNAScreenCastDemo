package com.qierong.dlnascreencastdemo.dlna.control

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvTransportClientTest {
    @Test
    fun setAvTransportUri_postsToControlUrlWithTimeouts() = runTest {
        val transport = RecordingSoapTransport(
            response = SoapHttpResponse(statusCode = 200, body = "<ok />"),
        )
        val client = AvTransportClient(transport = transport)

        val result = client.setAvTransportUri(
            controlUrl = "http://192.168.1.20/AVTransport/control",
            streamUrl = "http://192.168.1.8:8080/live.ts",
        )

        assertIsInstance<AvTransportResult.Success>(result)
        assertEquals("http://192.168.1.20/AVTransport/control", transport.lastUrl)
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            transport.lastSoapAction,
        )
        assertEquals(5_000, transport.lastConnectTimeoutMs)
        assertEquals(5_000, transport.lastReadTimeoutMs)
    }

    @Test
    fun play_mapsHttpFailureToStageSpecificError() = runTest {
        val client = AvTransportClient(
            transport = RecordingSoapTransport(
                response = SoapHttpResponse(statusCode = 500, body = "device error"),
            ),
        )

        val result = client.play("http://192.168.1.20/AVTransport/control")

        val failure = assertIsInstance<AvTransportResult.Failure>(result)
        assertEquals(AvTransportStage.Play, failure.stage)
        assertEquals(500, failure.httpStatusCode)
        assertEquals("HTTP 500", failure.summary)
    }

    @Test
    fun setAvTransportUri_includesSoapFaultDetailsOnHttpFailure() = runTest {
        val client = AvTransportClient(
            transport = RecordingSoapTransport(
                response = SoapHttpResponse(
                    statusCode = 500,
                    body = """
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                          <s:Body>
                            <s:Fault>
                              <faultcode>s:Client</faultcode>
                              <faultstring>UPnPError</faultstring>
                              <detail>
                                <UPnPError>
                                  <errorCode>714</errorCode>
                                  <errorDescription>No such resource</errorDescription>
                                </UPnPError>
                              </detail>
                            </s:Fault>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent(),
                ),
            ),
        )

        val result = client.setAvTransportUri(
            controlUrl = "http://192.168.1.20/AVTransport/control",
            streamUrl = "http://192.168.1.8:8080/live.ts",
        )

        val failure = assertIsInstance<AvTransportResult.Failure>(result)
        assertEquals(
            "HTTP 500；SOAP Fault s:Client：UPnPError；UPnP 714：No such resource",
            failure.summary,
        )
    }

    @Test
    fun stop_mapsSoapFaultToDetailedError() = runTest {
        val client = AvTransportClient(
            transport = RecordingSoapTransport(
                response = SoapHttpResponse(
                    statusCode = 200,
                    body = """
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                          <s:Body>
                            <s:Fault>
                              <faultcode>s:Client</faultcode>
                              <faultstring>UPnPError</faultstring>
                              <detail>
                                <UPnPError>
                                  <errorCode>701</errorCode>
                                  <errorDescription>Transition not available</errorDescription>
                                </UPnPError>
                              </detail>
                            </s:Fault>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent(),
                ),
            ),
        )

        val result = client.stop("http://192.168.1.20/AVTransport/control")

        val failure = assertIsInstance<AvTransportResult.Failure>(result)
        assertEquals(AvTransportStage.Stop, failure.stage)
        assertEquals("SOAP Fault s:Client：UPnPError；UPnP 701：Transition not available", failure.summary)
    }

    @Test(expected = CancellationException::class)
    fun pause_rethrowsCancellation() = runTest {
        val client = AvTransportClient(
            transport = object : SoapTransport {
                override suspend fun post(
                    url: String,
                    soapAction: String,
                    body: String,
                    connectTimeoutMs: Int,
                    readTimeoutMs: Int,
                ): SoapHttpResponse {
                    throw CancellationException("cancelled")
                }
            },
        )

        client.pause("http://192.168.1.20/AVTransport/control")
    }

    private class RecordingSoapTransport(
        private val response: SoapHttpResponse,
    ) : SoapTransport {
        var lastUrl: String? = null
        var lastSoapAction: String? = null
        var lastConnectTimeoutMs: Int? = null
        var lastReadTimeoutMs: Int? = null

        override suspend fun post(
            url: String,
            soapAction: String,
            body: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
        ): SoapHttpResponse {
            lastUrl = url
            lastSoapAction = soapAction
            lastConnectTimeoutMs = connectTimeoutMs
            lastReadTimeoutMs = readTimeoutMs
            return response
        }
    }

    private inline fun <reified T> assertIsInstance(value: Any?): T {
        assertTrue("Expected ${T::class.java.simpleName}, got $value", value is T)
        return value as T
    }
}
