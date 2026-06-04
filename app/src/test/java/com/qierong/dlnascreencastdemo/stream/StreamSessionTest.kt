package com.qierong.dlnascreencastdemo.stream

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
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
     * 验证 onReady 被调用时，HTTP 响应头已写出到输出流。
     *
     * 正确时序：写头 → startWriting → onReady。
     * 若 onReady 在写头之前调用，headerWrittenBeforeOnReady 会是 false，测试失败。
     */
    @Test
    fun open_writesHttpHeaderBeforeCallingOnReady() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts HTTP/1.1")

            var headerWrittenBeforeOnReady = false

            // onReady 被调用时，尝试从客户端读取响应头
            // 若此时头已写出，readHeader 能立刻完成；否则会在 SOCKET_TIMEOUT_MS 内超时
            val session = StreamSession.open(pair.server) { _ ->
                // 在 onReady 回调内，读取客户端侧是否已能看到 HTTP 头
                // 设置一个短超时来检测头是否已到达
                val saved = pair.client.soTimeout
                pair.client.soTimeout = 500
                try {
                    val header = pair.client.readHeader()
                    headerWrittenBeforeOnReady = header.startsWith("HTTP/1.1 200 OK")
                } catch (_: SocketTimeoutException) {
                    headerWrittenBeforeOnReady = false
                } finally {
                    pair.client.soTimeout = saved
                }
            }

            assertTrue("onReady 调用时 HTTP 响应头应已写出", headerWrittenBeforeOnReady)
            session?.close()
        }
    }

    /**
     * 验证 onReady 被调用时，writer 线程已经启动（session.write() 不应返回 false）。
     *
     * 若 writer 在 onReady 之后启动，session 加入活跃集合后立即 write() 可能在
     * writerThread 启动前执行，存在数据丢失风险。
     */
    @Test
    fun open_startsWriterBeforeCallingOnReady() {
        socketPair().use { pair ->
            pair.client.writeRequest("GET /live.ts HTTP/1.1")
            // 在 onReady 时预先消费掉 HTTP 头，避免 readHeader 阻塞
            pair.client.soTimeout = SOCKET_TIMEOUT_MS

            var writeSucceededInOnReady = false

            val session = StreamSession.open(pair.server) { s ->
                // 消费响应头
                pair.client.readHeader()
                // writer 应已启动，write 应返回 true
                writeSucceededInOnReady = s.write(byteArrayOf(0x47))
            }

            assertTrue("onReady 调用时 writer 应已启动，write() 应返回 true", writeSucceededInOnReady)
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
