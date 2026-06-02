package com.qierong.dlnascreencastdemo.stream

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArraySet

class LocalStreamServer(
    private val port: Int = DEFAULT_PORT,
    private val bindAddress: InetAddress? = null,
) : AutoCloseable {
    private val lock = Any()
    private val replayLock = Any()
    private val sessions = CopyOnWriteArraySet<StreamSession>()
    private val pendingSockets = CopyOnWriteArraySet<Socket>()
    private val replayChunks = mutableListOf<ByteArray>()
    private var replayBytes = 0

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
        sessions.forEach { session ->
            if (!session.write(tsChunk)) sessions.remove(session)
        }
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
            if (replayBytes + tsChunk.size > MAX_REPLAY_BYTES) {
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

    companion object {
        const val DEFAULT_PORT = 8080
        private const val MAX_PORT = 65_535
        private const val MAX_REPLAY_BYTES = 8 * 1024 * 1024
    }
}
