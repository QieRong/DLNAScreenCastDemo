package com.qierong.dlnascreencastdemo.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestValueDebouncerTest {
    @Test
    fun submit_replacesPendingValueAndConsumesOnlyLatestTarget() {
        val scheduled = mutableListOf<Runnable>()
        val consumed = mutableListOf<String>()
        val debouncer = LatestValueDebouncer<String>(
            schedule = { runnable -> scheduled += runnable },
            cancel = { runnable -> scheduled.remove(runnable) },
            consume = { value -> consumed += value },
        )

        debouncer.submit("portrait")
        debouncer.submit("landscape")
        scheduled.single().run()

        assertEquals(listOf("landscape"), consumed)
    }
}
