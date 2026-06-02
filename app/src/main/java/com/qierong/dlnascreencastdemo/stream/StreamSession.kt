package com.qierong.dlnascreencastdemo.stream

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

    private fun startWriting() {
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
        private const val LIVE_PATH = "/live.ts"
        private const val MAX_PENDING_CHUNKS = 64
        private const val MAX_HEADER_LINES = 64
        private const val MAX_LINE_BYTES = 4_096
        private const val REQUEST_TIMEOUT_MS = 2_000
        private const val WRITER_POLL_TIMEOUT_MS = 250L
        private val ASCII = StandardCharsets.US_ASCII

        fun open(
            socket: Socket,
            onReady: (StreamSession) -> Unit = {},
        ): StreamSession? {
            return try {
                socket.soTimeout = REQUEST_TIMEOUT_MS
                socket.tcpNoDelay = true
                val input = socket.getInputStream()
                val requestLine = readAsciiLine(input)
                    ?: return reject(socket, "400 Bad Request")
                if (!consumeHeaders(input)) return reject(socket, "400 Bad Request")
                val parts = requestLine.split(' ')
                when {
                    parts.size != 3 || !parts[2].startsWith("HTTP/") ->
                        reject(socket, "400 Bad Request")

                    parts[0] != "GET" ->
                        reject(socket, "405 Method Not Allowed")

                    parts[1] != LIVE_PATH ->
                        reject(socket, "404 Not Found")

                    else -> {
                        val session = StreamSession(socket)
                        onReady(session)
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
                        socket.soTimeout = 0
                        session.startWriting()
                        session
                    }
                }
            } catch (_: IOException) {
                runCatching(socket::close)
                null
            }
        }

        private fun reject(socket: Socket, status: String): StreamSession? {
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

        private fun consumeHeaders(input: InputStream): Boolean {
            repeat(MAX_HEADER_LINES) {
                val line = readAsciiLine(input) ?: return false
                if (line.isEmpty()) return true
            }
            return false
        }

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
