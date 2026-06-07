package com.qierong.dlnascreencastdemo.stream

import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

class LocalStreamServer(
    private val port: Int = DEFAULT_PORT,
    private val bindAddress: InetAddress? = null,
) : AutoCloseable {
    private val lock = Any()
    private val replayLock = Any()
    private val sessions = CopyOnWriteArraySet<StreamSession>()
    private val pendingSockets = CopyOnWriteArraySet<Socket>()
    private val replayChunks = mutableListOf<ByteArray>()
    private val publishedChunkCount = AtomicLong(0L)
    private val sessionWriteFailureCount = AtomicLong(0L)
    private var replayBytes = 0
    private var lastSummaryLogTimeMs = 0L

    @Volatile
    private var listener: ServerSocket? = null

    val isRunning: Boolean
        get() = listener?.isClosed == false

    init {
        require(port in 0..MAX_PORT) { "Port must be between 0 and $MAX_PORT" }
    }

    fun start(): Int =
        synchronized(lock) {
            listener?.takeUnless(ServerSocket::isClosed)?.localPort ?: openListener().also { serverSocket ->
                listener = serverSocket
                Thread(
                    { acceptLoop(serverSocket) },
                    "LocalStreamServer-Accept",
                ).apply {
                    isDaemon = true
                    start()
                }
            }.localPort
        }

    fun publish(tsChunk: ByteArray, replayOnConnect: Boolean = false) {
        require(tsChunk.isNotEmpty()) { "TS chunk must not be empty" }
        updateReplayChunks(tsChunk, replayOnConnect)
        publishedChunkCount.incrementAndGet()
        var writeFailures = 0L
        sessions.forEach { session ->
            if (!session.write(tsChunk)) {
                sessions.remove(session)
                writeFailures++
            }
        }
        if (writeFailures > 0) sessionWriteFailureCount.addAndGet(writeFailures)
        logSummaryIfNeeded()
    }

    fun clearReplayChunk() {
        synchronized(replayLock) {
            replayChunks.clear()
            replayBytes = 0
        }
    }

    fun stop() {
        val activeListener = synchronized(lock) {
            listener.also { listener = null }
        }
        runCatching { activeListener?.close() }
        pendingSockets.forEach { it.closeQuietly() }
        pendingSockets.clear()
        sessions.forEach(StreamSession::close)
        sessions.clear()
        clearReplayChunk()
    }

    internal fun diagnosticSnapshot(): LocalStreamServerDiagnosticSnapshot =
        LocalStreamServerDiagnosticSnapshot(
            isRunning = isRunning,
            publishedChunkCount = publishedChunkCount.get(),
            currentSessionCount = sessions.size,
            pendingSocketCount = pendingSockets.size,
            pendingPacketCount = sessions.sumOf(StreamSession::pendingChunkCount),
            maxSessionPendingPacketCount = sessions.maxOfOrNull(StreamSession::pendingChunkCount) ?: 0,
            sessionWriteFailureCount = sessionWriteFailureCount.get(),
            replayChunkCount = synchronized(replayLock) { replayChunks.size },
            replayBytes = synchronized(replayLock) { replayBytes },
        )

    override fun close() {
        stop()
    }

    private fun openListener(): ServerSocket =
        ServerSocket().apply {
            reuseAddress = true
            bind(
                if (bindAddress == null) {
                    InetSocketAddress(port)
                } else {
                    InetSocketAddress(bindAddress, port)
                },
            )
        }

    private fun acceptLoop(serverSocket: ServerSocket) {
        try {
            while (isActive(serverSocket)) {
                val socket = serverSocket.accept()
                if (!isActive(serverSocket)) {
                    socket.closeQuietly()
                    continue
                }
                pendingSockets += socket
                Thread(
                    { openSession(serverSocket, socket) },
                    "LocalStreamServer-Handshake",
                ).apply {
                    isDaemon = true
                    start()
                }
            }
        } catch (_: IOException) {
            if (isActive(serverSocket)) stop()
        }
    }

    private fun openSession(serverSocket: ServerSocket, socket: Socket) {
        try {
            val session = StreamSession.open(socket) { readySession ->
                if (isActive(serverSocket)) {
                    sessions += readySession
                    replayChunksSnapshot().forEach(readySession::write)
                } else {
                    readySession.close()
                }
            } ?: return
            if (!isActive(serverSocket)) {
                sessions -= session
                session.close()
            }
        } finally {
            pendingSockets -= socket
        }
    }

    private fun isActive(serverSocket: ServerSocket): Boolean =
        listener === serverSocket && !serverSocket.isClosed

    private fun updateReplayChunks(tsChunk: ByteArray, replayOnConnect: Boolean) {
        synchronized(replayLock) {
            if (replayOnConnect) {
                replayChunks.clear()
                replayBytes = 0
            }
            if (replayChunks.isEmpty() && !replayOnConnect) return
            if (replayBytes + tsChunk.size > MAX_REPLAY_BYTES ||
                replayChunks.size >= MAX_REPLAY_CHUNKS
            ) {
                replayChunks.clear()
                replayBytes = 0
                return
            }
            replayChunks += tsChunk.copyOf()
            replayBytes += tsChunk.size
        }
    }

    private fun replayChunksSnapshot(): List<ByteArray> =
        synchronized(replayLock) {
            replayChunks.toList()
        }

    private fun Socket.closeQuietly() {
        runCatching(::close)
    }

    private fun logSummaryIfNeeded() {
        val now = System.currentTimeMillis()
        val snapshot = synchronized(lock) {
            if (now - lastSummaryLogTimeMs < SUMMARY_LOG_INTERVAL_MS) {
                null
            } else {
                lastSummaryLogTimeMs = now
                diagnosticSnapshot()
            }
        } ?: return
        Log.i(TAG, "server diagnostics: ${snapshot.toLogLine()}")
    }

    companion object {
        private const val TAG = "StreamServer"
        const val DEFAULT_PORT = 8080
        private const val MAX_PORT = 65_535
        private const val MAX_REPLAY_BYTES = 8 * 1024 * 1024
        private const val MAX_REPLAY_CHUNKS = 384
        private const val SUMMARY_LOG_INTERVAL_MS = 5_000L
    }
}
