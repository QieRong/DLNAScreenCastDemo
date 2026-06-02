package com.qierong.dlnascreencastdemo.capture

class LatestValueDebouncer<T : Any>(
    private val schedule: (Runnable) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val consume: (T) -> Unit,
) {
    private var pendingValue: T? = null
    private val applyPending = Runnable {
        val value = synchronized(this) {
            val pending = pendingValue
            pendingValue = null
            pending
        }
        if (value != null) consume(value)
    }

    @Synchronized
    fun submit(value: T) {
        pendingValue = value
        cancel(applyPending)
        schedule(applyPending)
    }

    @Synchronized
    fun cancel() {
        pendingValue = null
        cancel(applyPending)
    }
}
