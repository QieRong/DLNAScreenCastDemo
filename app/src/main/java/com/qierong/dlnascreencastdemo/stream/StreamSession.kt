package com.qierong.dlnascreencastdemo.stream

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StreamSession private constructor(
    private val socket: Socket,
) : AutoCloseable {
    private val pendingChunks = ArrayBlockingQueue<ByteArray>(MAX_PENDING_CHUNKS)
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val writerThread = Thread(::writeLoop, "StreamSession-Writer").apply {
        isDaemon = true
    }

    fun write(tsChunk: ByteArray): Boolean {
        require(tsChunk.isNotEmpty()) { "TS chunk must not be empty" }
        if (closed.get()) return false
        return pendingChunks.offer(tsChunk.copyOf()).also { accepted ->
            if (!accepted) close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pendingChunks.clear()
        runCatching(socket::close)
        if (started.get() && Thread.currentThread() !== writerThread) writerThread.interrupt()
    }

    // 内部可见，供 open() 在握手成功后调用
    internal fun startWriting() {
        if (!closed.get() && started.compareAndSet(false, true)) writerThread.start()
    }

    private fun writeLoop() {
        try {
            while (!closed.get()) {
                val chunk = pendingChunks.poll(WRITER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: continue
                socket.getOutputStream().write(chunk)
                socket.getOutputStream().flush()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: IOException) {
            close()
        }
    }

    companion object {
        private const val TAG = "StreamSession"
        private const val LIVE_PATH = "/live.ts"
        private const val MAX_PENDING_CHUNKS = 512
        private const val MAX_HEADER_LINES = 64
        private const val MAX_LINE_BYTES = 4_096
        private const val REQUEST_TIMEOUT_MS = 2_000
        private const val WRITER_POLL_TIMEOUT_MS = 250L
        private val ASCII = StandardCharsets.US_ASCII

        /**
         * 从 [socket] 读取 HTTP 请求并握手。
         *
         * 握手成功时：
         * 1. 调用 [onReady]，将 session 加入活跃集合并允许 replay 先排队
         * 2. 写出 HTTP 200 响应头（含 Content-Type: video/mp2t）
         * 3. 清除 soTimeout，防止流传输期间超时断连
         * 4. 启动后台写线程
         *
         * 若握手失败（任何 IOException 或请求格式错误），返回 null 并关闭 socket。
         *
         * @param socket 已建立的 TCP 连接
         * @param onReady 请求校验完成、响应头写出前的回调
         * @return 握手成功时的 [StreamSession]，失败时为 null
         */
        fun open(
            socket: Socket,
            onReady: (StreamSession) -> Unit = {},
        ): StreamSession? {
            return try {
                // 握手阶段限时 2s，避免慢客户端长期占用线程
                socket.soTimeout = REQUEST_TIMEOUT_MS
                socket.tcpNoDelay = true
                val remoteAddr = socket.remoteSocketAddress.toString()
                val input = socket.getInputStream()

                val requestLine = readAsciiLine(input)
                    ?: return reject(socket, "400 Bad Request", remoteAddr, "请求行为空")
                if (!consumeHeaders(input)) {
                    return reject(socket, "400 Bad Request", remoteAddr, "请求头解析失败")
                }

                val parts = requestLine.split(' ')
                when {
                    parts.size != 3 || !parts[2].startsWith("HTTP/") ->
                        reject(socket, "400 Bad Request", remoteAddr, "请求行格式错误：$requestLine")

                    parts[0] != "GET" && parts[0] != "HEAD" ->
                        reject(socket, "405 Method Not Allowed", remoteAddr, "不支持的方法：${parts[0]}")

                    parts[1].substringBefore('?') != LIVE_PATH ->
                        reject(socket, "404 Not Found", remoteAddr, "未知路径：${parts[1]}")

                    parts[0] == "HEAD" -> {
                        Log.d(TAG, "[$remoteAddr] HEAD 探测：$requestLine")
                        writeOkHeader(socket)
                        runCatching(socket::close)
                        null
                    }

                    else -> {
                        val session = StreamSession(socket)
                        Log.d(TAG, "[$remoteAddr] 请求行：$requestLine")

                        // 先注册 session，但 writer 暂不启动，避免响应头与 TS 数据交错。
                        onReady(session)

                        // ① 写 HTTP 响应头，保证客户端能收到 200 OK
                        writeOkHeader(socket)

                        // ② 清除读超时，流传输期间不设 soTimeout
                        socket.soTimeout = 0

                        // ③ 启动后台写线程，准备好接收 TS 数据
                        session.startWriting()

                        Log.i(TAG, "[$remoteAddr] 握手成功，开始推流")
                        session
                    }
                }
            } catch (exception: IOException) {
                runCatching(socket::close)
                // IOException 不静默吞掉，记录实际异常类型方便定位根因
                // 最常见路径：SocketTimeoutException（握手期间超时）、连接重置等
                Log.w(
                    TAG,
                    "HTTP 握手失败（${runCatching { socket.remoteSocketAddress }.getOrNull()}）：" +
                        "${exception::class.simpleName} — ${exception.message}",
                )
                null
            }
        }

        /**
         * 向客户端返回错误响应并关闭连接，同时记录日志。
         *
         * @param socket 待关闭的 socket
         * @param status HTTP 状态描述，如 "404 Not Found"
         * @param remoteAddr 客户端地址，仅用于日志
         * @param reason 失败原因，仅用于日志
         */
        private fun reject(
            socket: Socket,
            status: String,
            remoteAddr: String,
            reason: String,
        ): StreamSession? {
            Log.d(TAG, "[$remoteAddr] 拒绝请求 → $status（$reason）")
            runCatching {
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 $status\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                            ).toByteArray(ASCII),
                    )
                    flush()
                }
            }
            runCatching(socket::close)
            return null
        }

        private fun writeOkHeader(socket: Socket) {
            socket.getOutputStream().apply {
                write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: video/mp2t\r\n" +
                            "Cache-Control: no-store\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
                        ).toByteArray(ASCII),
                )
                flush()
            }
        }

        /**
         * 消费 HTTP 请求头直到遇到空行，确保请求体之前的所有头被读完。
         *
         * @return 成功消费到空行返回 true，读取失败或超出限制返回 false
         */
        private fun consumeHeaders(input: InputStream): Boolean {
            repeat(MAX_HEADER_LINES) {
                val line = readAsciiLine(input) ?: return false
                if (line.isEmpty()) return true
            }
            return false
        }

        /**
         * 从输入流中读取一行 ASCII 文本（以 '\n' 或 "\r\n" 结尾）。
         *
         * @return 读取到的行内容（不含换行符），流关闭且无内容时返回 null
         * @throws IOException 读取过程中发生 IO 错误或行长度超限
         */
        private fun readAsciiLine(input: InputStream): String? {
            val output = ByteArrayOutputStream()
            while (true) {
                val next = input.read()
                if (next < 0) return if (output.size() == 0) null else throw IOException("Unexpected EOF")
                if (next == '\n'.code) {
                    val bytes = output.toByteArray()
                    val end = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                    return String(bytes, 0, end, ASCII)
                }
                if (output.size() >= MAX_LINE_BYTES) throw IOException("HTTP line too long")
                output.write(next)
            }
        }
    }
}
