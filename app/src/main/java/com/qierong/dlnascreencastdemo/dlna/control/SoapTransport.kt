package com.qierong.dlnascreencastdemo.dlna.control

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SoapTransport {
    suspend fun post(
        url: String,
        soapAction: String,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): SoapHttpResponse
}

class UrlConnectionSoapTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SoapTransport {
    override suspend fun post(
        url: String,
        soapAction: String,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): SoapHttpResponse = withContext(ioDispatcher) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", soapAction)
        }
        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            SoapHttpResponse(
                statusCode = statusCode,
                body = responseStream?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }.orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
