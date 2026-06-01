package com.qierong.dlnascreencastdemo.dlna

import android.util.Log

interface DiscoveryLogger {
    fun debug(message: String)
    fun warn(message: String, throwable: Throwable? = null)
}

object AndroidDiscoveryLogger : DiscoveryLogger {
    override fun debug(message: String) {
        Log.d(TAG, message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        Log.w(TAG, message, throwable)
    }

    private const val TAG = "DLNA-Demo"
}

object NoOpDiscoveryLogger : DiscoveryLogger {
    override fun debug(message: String) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}

internal fun Throwable.discoveryLogType(): String =
    this::class.java.simpleName.ifBlank { this::class.java.name }

internal fun Throwable.discoveryLogReason(): String =
    message
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .ifBlank { "无详细原因" }
        .take(MAX_LOG_REASON_LENGTH)

private const val MAX_LOG_REASON_LENGTH = 160
