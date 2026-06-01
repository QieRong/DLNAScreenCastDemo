package com.qierong.dlnascreencastdemo.dlna.discovery

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class DeviceDescriptionClient(
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
) : DeviceDescriptionFetcher {
    override fun fetch(descriptionUrl: URI): ByteArray = fetch(descriptionUrl, redirectCount = 0)

    private fun fetch(descriptionUrl: URI, redirectCount: Int): ByteArray {
        requireSupportedUri(descriptionUrl)
        val connection = descriptionUrl.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.instanceFollowRedirects = false

        try {
            val responseCode = connection.responseCode
            if (responseCode in REDIRECT_STATUS_CODES) {
                require(redirectCount < MAX_REDIRECTS) { "设备描述重定向次数超过限制" }
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalArgumentException("设备描述重定向缺少 Location")
                return fetch(descriptionUrl.resolve(location), redirectCount + 1)
            }
            if (responseCode !in 200..299) {
                throw IOException("设备描述请求失败，HTTP $responseCode")
            }

            val contentLength = connection.contentLengthLong
            require(contentLength <= maxBodyBytes || contentLength < 0) {
                "设备描述响应体超过限制"
            }
            return connection.inputStream.use(::readLimitedBytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedBytes(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes < 0) break
            totalBytes += readBytes
            require(totalBytes <= maxBodyBytes) { "设备描述响应体超过限制" }
            output.write(buffer, 0, readBytes)
        }
        return output.toByteArray()
    }

    private fun requireSupportedUri(uri: URI) {
        require(
            uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true),
        ) {
            "设备描述仅允许 HTTP(S)"
        }
        require(!uri.host.isNullOrBlank()) { "设备描述 URL 缺少主机名" }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 3_000
        const val DEFAULT_MAX_BODY_BYTES = 512 * 1024
        const val MAX_REDIRECTS = 1
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}
