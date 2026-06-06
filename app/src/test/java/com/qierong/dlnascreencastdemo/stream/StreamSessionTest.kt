package com.qierong.dlnascreencastdemo.stream

import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    // ─── 基础行为测试 ─────────────────────────────────────────────────────────

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

    // ─── 新增：时序与错误处理验证 ─────────────────────────────────────────────

    /**
     * 验证 onReady 被调用时，session 已可接收排队数据。
     *
     * 正确时序：onReady 注册 session → 写头 → startWriting。
     * 这样 LocalStreamServer 在客户端刚读到 header 后立即 publish 时不会丢首包。
     */
    @Test
    fun open_allowsQueueingChunkInOnReadyBeforeHeaderIsVisible() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts HTTP/1.1")

            var writeSucceededInOnReady = false

            val session = StreamSession.open(pair.server) { ready ->
                writeSucceededInOnReady = ready.write(byteArrayOf(0x47, 0x66))
            }

            assertTrue("onReady 调用时 session.write() 应可排队", writeSucceededInOnReady)
            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
            assertArrayEquals(byteArrayOf(0x47, 0x66), pair.client.readExactly(2))
            session?.close()
        }
    }

    @Test
    fun open_acceptsLiveTsRequestWithQueryString() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts?dlna=123 HTTP/1.1")

            val session = StreamSession.open(pair.server)

            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(session!!.write(byteArrayOf(0x47, 0x11)))
            assertArrayEquals(byteArrayOf(0x47, 0x11), pair.client.readExactly(2))
            session.close()
        }
    }

    @Test
    fun open_acceptsHeadProbeWithoutCreatingSession() {
        socketPair().use { pair ->
            pair.client.writeRequest("HEAD /live.ts?dlna=123 HTTP/1.1")

            val session = StreamSession.open(pair.server)

            assertNull(session)
            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(pair.client.getInputStream().read() < 0)
        }
    }

    /**
     * 验证 writer 在响应头写出后启动，避免 TS 数据跑到 HTTP header 前面。
     *
     * onReady 中排队的数据必须在完整 HTTP 头之后到达客户端。
     */
    @Test
    fun open_writesHeaderBeforeQueuedTsBytes() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts HTTP/1.1")

            val session = StreamSession.open(pair.server) { ready ->
                ready.write(byteArrayOf(0x47, 0x77))
            }

            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 200 OK\r\n"))
            assertArrayEquals(byteArrayOf(0x47, 0x77), pair.client.readExactly(2))
            session?.close()
        }
    }

    /**
     * 验证客户端在握手阶段触发 SocketTimeoutException 时，open() 返回 null 且 socket 被关闭。
     *
     * 模拟方式：向服务端发送不完整请求（无空行结尾），使服务端读取头超时。
     * 因为测试环境没有 android.util.Log，本用例只验证行为（返回 null + socket 关闭），
     * 不断言日志输出。
     */
    @Test
    fun open_returnsNullAndClosesSocketOnHandshakeTimeout() {
        socketPair().use { pair ->
            // 发送不完整请求：只有请求行，没有结束的空行，使服务端 consumeHeaders 超时
            pair.client.getOutputStream().apply {
                write("GET /live.ts HTTP/1.1\r\nHost: localhost\r\n".toByteArray(StandardCharsets.US_ASCII))
                // 注意：故意不发送最后的 "\r\n"（空行），服务端会等待直到超时
                flush()
            }

            // open() 内部 soTimeout = 2000ms，等待超时后应返回 null
            val session = StreamSession.open(pair.server)

            // 行为验证 1：返回 null（握手失败）
            assertNull("握手超时时 open() 应返回 null", session)

            // 行为验证 2：服务端 socket 已关闭，客户端应收到 EOF
            pair.client.soTimeout = SOCKET_TIMEOUT_MS
            val eof = pair.client.getInputStream().read()
            assertTrue("握手失败后服务端 socket 应关闭，客户端收到 EOF(-1)", eof == -1)
        }
    }

    /**
     * 验证对非 GET 方法的请求返回 405 且 session 为 null。
     */
    @Test
    fun open_rejectsNonGetMethodWith405() {
        socketPair().use { pair ->
            pair.client.writeRequest("POST /live.ts HTTP/1.1")

            val session = StreamSession.open(pair.server)

            assertNull(session)
            assertTrue(pair.client.readHeader().startsWith("HTTP/1.1 405 Method Not Allowed\r\n"))
        }
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

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
        // 单元测试的 socket 超时比握手超时稍长，避免测试本身超时
        private const val SOCKET_TIMEOUT_MS = 3_000
    }
}
