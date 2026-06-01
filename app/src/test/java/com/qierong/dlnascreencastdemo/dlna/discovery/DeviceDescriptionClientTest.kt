package com.qierong.dlnascreencastdemo.dlna.discovery

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI

class DeviceDescriptionClientTest {
    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun fetch_followsAtMostOneHttpRedirect() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/redirect") { exchange ->
                exchange.responseHeaders.add("Location", "/device.xml")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/device.xml") { exchange ->
                val body = "<root />".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        val body = DeviceDescriptionClient().fetch(serverUri("/redirect"))

        assertArrayEquals("<root />".toByteArray(), body)
    }

    @Test
    fun fetch_rejectsSecondRedirect() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/first") { exchange ->
                exchange.responseHeaders.add("Location", "/second")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/second") { exchange ->
                exchange.responseHeaders.add("Location", "/third")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            start()
        }

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDescriptionClient().fetch(serverUri("/first"))
        }
    }

    @Test
    fun fetch_rejectsUnsupportedScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceDescriptionClient().fetch(URI("file:///tmp/device.xml"))
        }
    }

    @Test
    fun fetch_rejectsBodyLargerThanConfiguredLimit() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/large") { exchange ->
                val body = ByteArray(9)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        assertThrows(IllegalArgumentException::class.java) {
            DeviceDescriptionClient(maxBodyBytes = 8).fetch(serverUri("/large"))
        }
    }

    private fun serverUri(path: String): URI {
        val address = requireNotNull(server).address
        return URI("http://127.0.0.1:${address.port}$path")
    }
}
