package com.qierong.dlnascreencastdemo.encoder

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class EncodedBufferReaderTest {
    @Test
    fun copyEncodedBytes_readsOnlyRequestedOffsetAndSize() {
        val buffer = ByteBuffer.wrap(byteArrayOf(0x01, 0x02, 0x65, 0x11, 0x22, 0x03))

        val output = buffer.copyEncodedBytes(offset = 2, size = 3)

        assertArrayEquals(byteArrayOf(0x65, 0x11, 0x22), output)
    }
}
