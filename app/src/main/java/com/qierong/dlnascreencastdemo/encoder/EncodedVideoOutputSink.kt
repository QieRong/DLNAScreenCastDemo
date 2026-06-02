package com.qierong.dlnascreencastdemo.encoder

import java.nio.ByteBuffer

interface EncodedVideoOutputSink {
    fun onOutputFormat(csd0: ByteArray?, csd1: ByteArray?)

    fun onCodecConfig(data: ByteArray)

    fun onAccessUnit(
        data: ByteArray,
        presentationTimeUs: Long,
        isKeyFrame: Boolean,
    )

    data object None : EncodedVideoOutputSink {
        override fun onOutputFormat(csd0: ByteArray?, csd1: ByteArray?) = Unit

        override fun onCodecConfig(data: ByteArray) = Unit

        override fun onAccessUnit(
            data: ByteArray,
            presentationTimeUs: Long,
            isKeyFrame: Boolean,
        ) = Unit
    }
}

internal fun ByteBuffer.copyEncodedBytes(offset: Int, size: Int): ByteArray {
    require(offset >= 0) { "Encoded buffer offset must not be negative" }
    require(size >= 0) { "Encoded buffer size must not be negative" }
    require(offset + size <= capacity()) { "Encoded buffer range exceeds capacity" }
    return ByteArray(size).also { bytes ->
        duplicate().apply {
            position(offset)
            limit(offset + size)
            get(bytes)
        }
    }
}

internal fun ByteBuffer.copyRemainingBytes(): ByteArray =
    duplicate().let { buffer ->
        ByteArray(buffer.remaining()).also(buffer::get)
    }
