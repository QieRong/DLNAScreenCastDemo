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
