package com.qierong.dlnascreencastdemo.stream

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSessionTest {
    @Test
    fun open_acceptsLiveTsRequestAndWritesPublishedBytes() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts HTTP/1.1")

            val session = StreamSession.open(pair.server)

            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(session!!.write(byteArrayOf(0x47, 0x11, 0x22)))
            assertArrayEquals(byteArrayOf(0x47, 0x11, 0x22), pair.client.readExactly(3))
            session.close()
        }
    }

    @Test
    fun open_rejectsUnknownPathWithNotFound() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /missing.ts HTTP/1.1")

            val session = StreamSession.open(pair.server)

            assertNull(session)
            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 404 Not Found\r\n"))
        }
    }

    @Test
    fun close_isIdempotentAndPreventsFurtherWrites() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts HTTP/1.1")
            val session = StreamSession.open(pair.server)!!
            pair.client.readHeader()

            session.close()
            session.close()

            assertFalse(session.write(byteArrayOf(0x47)))
        }
    }

    private fun socketPair(): SocketPair {
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val client = Socket(InetAddress.getLoopbackAddress(), listener.localPort).apply {
            soTimeout = SOCKET_TIMEOUT_MS
        }
        return SocketPair(
            client = client,
            server = listener.accept(),
            listener = listener,
        )
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

    private data class SocketPair(
        val client: Socket,
        val server: Socket,
        val listener: ServerSocket,
    ) : AutoCloseable {
        override fun close() {
            runCatching(client::close)
            runCatching(server::close)
            runCatching(listener::close)
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 2_000
    }
}
