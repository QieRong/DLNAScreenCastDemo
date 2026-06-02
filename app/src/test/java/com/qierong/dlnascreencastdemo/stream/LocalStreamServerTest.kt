package com.qierong.dlnascreencastdemo.stream

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStreamServerTest {
    @Test
    fun publish_writesTsBytesToLiveClient() {
        LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress()).use { server ->
            val port = server.start()
            Socket(InetAddress.getLoopbackAddress(), port).use { client ->
                client.soTimeout = SOCKET_TIMEOUT_MS
                client.writeRequest("GET /live.ts HTTP/1.1")

                assertTrue(client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
                server.publish(byteArrayOf(0x47, 0x33, 0x44))

                assertArrayEquals(byteArrayOf(0x47, 0x33, 0x44), client.readExactly(3))
            }
        }
    }

    @Test
    fun publish_writesTsBytesToEveryLiveClient() {
        LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress()).use { server ->
            val port = server.start()
            Socket(InetAddress.getLoopbackAddress(), port).use { first ->
                Socket(InetAddress.getLoopbackAddress(), port).use { second ->
                    first.soTimeout = SOCKET_TIMEOUT_MS
                    second.soTimeout = SOCKET_TIMEOUT_MS
                    first.writeRequest("GET /live.ts HTTP/1.1")
                    second.writeRequest("GET /live.ts HTTP/1.1")
                    first.readHeader()
                    second.readHeader()

                    server.publish(byteArrayOf(0x47, 0x55))

                    assertArrayEquals(byteArrayOf(0x47, 0x55), first.readExactly(2))
                    assertArrayEquals(byteArrayOf(0x47, 0x55), second.readExactly(2))
                }
            }
        }
    }

    @Test
    fun stop_closesListenerAndConnectedSessions() {
        val server = LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress())
        val port = server.start()
        Socket(InetAddress.getLoopbackAddress(), port).use { client ->
            client.soTimeout = SOCKET_TIMEOUT_MS
            client.writeRequest("GET /live.ts HTTP/1.1")
            client.readHeader()

            server.stop()
            server.stop()

            assertFalse(server.isRunning)
            assertTrue(client.getInputStream().read() < 0)
        }
    }

    @Test
    fun publish_replaysLatestBootstrapChunkToNewClient() {
        LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress()).use { server ->
            val port = server.start()
            server.publish(byteArrayOf(0x47, 0x11), replayOnConnect = true)

            Socket(InetAddress.getLoopbackAddress(), port).use { client ->
                client.soTimeout = SOCKET_TIMEOUT_MS
                client.writeRequest("GET /live.ts HTTP/1.1")

                assertTrue(client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
                assertArrayEquals(byteArrayOf(0x47, 0x11), client.readExactly(2))
            }
        }
    }

    @Test
    fun clearReplayChunk_preventsReplayingPreviousEncoderBootstrap() {
        LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress()).use { server ->
            val port = server.start()
            server.publish(byteArrayOf(0x47, 0x11), replayOnConnect = true)
            server.clearReplayChunk()

            Socket(InetAddress.getLoopbackAddress(), port).use { client ->
                client.soTimeout = SOCKET_TIMEOUT_MS
                client.writeRequest("GET /live.ts HTTP/1.1")

                assertTrue(client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
                client.soTimeout = SHORT_SOCKET_TIMEOUT_MS
                assertTrue(runCatching { client.getInputStream().read() }.isFailure)
            }
        }
    }

    @Test
    fun stop_clearsReplayChunkBeforeRestart() {
        val server = LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress())
        server.start()
        server.publish(byteArrayOf(0x47, 0x11), replayOnConnect = true)
        server.stop()

        server.use {
            val restartedPort = server.start()
            Socket(InetAddress.getLoopbackAddress(), restartedPort).use { client ->
                client.soTimeout = SOCKET_TIMEOUT_MS
                client.writeRequest("GET /live.ts HTTP/1.1")

                assertTrue(client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
                client.soTimeout = SHORT_SOCKET_TIMEOUT_MS
                assertTrue(runCatching { client.getInputStream().read() }.isFailure)
            }
        }
    }

    @Test
    fun publish_replaysLatestBootstrapAndFollowingChunksToNewClient() {
        LocalStreamServer(port = 0, bindAddress = InetAddress.getLoopbackAddress()).use { server ->
            val port = server.start()
            server.publish(byteArrayOf(0x47, 0x11), replayOnConnect = true)
            server.publish(byteArrayOf(0x47, 0x22))

            Socket(InetAddress.getLoopbackAddress(), port).use { client ->
                client.soTimeout = SOCKET_TIMEOUT_MS
                client.writeRequest("GET /live.ts HTTP/1.1")

                assertTrue(client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
                assertArrayEquals(
                    byteArrayOf(0x47, 0x11, 0x47, 0x22),
                    client.readExactly(4),
                )
            }
        }
    }

    private fun Socket.writeRequest(requestLine: String) {
        getOutputStream().apply {
            write("$requestLine\r\nHost: localhost\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            flush()
        }
    }

    private fun Socket.readHeader(): String {
        val output = ByteArrayOutputStream()
        var matched = 0
        val delimiter = "\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        while (matched < delimiter.size) {
            val byte = getInputStream().read()
            check(byte >= 0) { "Socket closed before HTTP header completed" }
            output.write(byte)
            matched = if (byte.toByte() == delimiter[matched]) matched + 1 else 0
        }
        return output.toString(StandardCharsets.US_ASCII.name())
    }

    private fun Socket.readExactly(byteCount: Int): ByteArray =
        ByteArray(byteCount).also { bytes ->
            var offset = 0
            while (offset < byteCount) {
                val count = getInputStream().read(bytes, offset, byteCount - offset)
                check(count >= 0) { "Socket closed before $byteCount bytes were read" }
                offset += count
            }
        }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 2_000
        private const val SHORT_SOCKET_TIMEOUT_MS = 250
    }
}
