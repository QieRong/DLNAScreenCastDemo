package com.qierong.dlnascreencastdemo.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpegTsStreamPipelineTest {
    private val sps = byteArrayOf(0x67, 0x64, 0x00, 0x1f)
    private val pps = byteArrayOf(0x68, 0x01, 0x02)
    private val idr = byteArrayOf(0x65, 0x11, 0x22)
    private val delta = byteArrayOf(0x41, 0x33, 0x44)

    @Test
    fun onAccessUnit_waitsForKeyFrameBeforePublishingTransportStream() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))

        pipeline.onAccessUnit(annexB(delta), presentationTimeUs = 0, isKeyFrame = false)
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 33_333, isKeyFrame = true)

        assertEquals(1, published.size)
        assertTrue(published.single().data.allPacketsStartWithSyncByte())
        assertTrue(published.single().isBootstrap)
    }

    @Test
    fun reset_waitsForNewParameterSetsBeforePublishingReconfiguredEncoder() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 0, isKeyFrame = true)

        pipeline.reset()
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 33_333, isKeyFrame = true)
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 66_666, isKeyFrame = true)

        assertEquals(2, published.size)
    }

    private fun annexB(vararg nalUnits: ByteArray): ByteArray =
        nalUnits.flatMap { START_CODE.asIterable() + it.asIterable() }.toByteArray()

    private fun ByteArray.allPacketsStartWithSyncByte(): Boolean =
        size % TS_PACKET_SIZE == 0 &&
            toList().chunked(TS_PACKET_SIZE).all { it.first().toInt() and 0xff == SYNC_BYTE }

    private data class PublishedChunk(
        val data: ByteArray,
        val isBootstrap: Boolean,
    )

    companion object {
        private val START_CODE = byteArrayOf(0, 0, 0, 1)
        private const val TS_PACKET_SIZE = 188
        private const val SYNC_BYTE = 0x47
    }
}
