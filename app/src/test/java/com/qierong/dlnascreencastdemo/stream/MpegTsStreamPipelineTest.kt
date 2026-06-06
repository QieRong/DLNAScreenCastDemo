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

    @Test
    fun onAudioAccessUnit_videoOnlyPipelineIgnoresAudioFrames() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline(includeAudio = false) { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 0, isKeyFrame = true)

        val publishedAudio = pipeline.onAudioAccessUnit(fakeAdtsFrame(), presentationTimeUs = 21_333)

        assertEquals(false, publishedAudio)
        assertEquals(1, published.size)
    }

    @Test
    fun onAudioAccessUnit_includeAudioPublishesAfterKeyFrame() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline(includeAudio = true) { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 0, isKeyFrame = true)

        val publishedAudio = pipeline.onAudioAccessUnit(fakeAdtsFrame(), presentationTimeUs = 21_333)

        assertEquals(true, publishedAudio)
        assertEquals(2, published.size)
        assertTrue(published.last().data.allPacketsStartWithSyncByte())
    }

    @Test
    fun outputPts_usesStreamRelativeTimelineForVideoAndAudio() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline(includeAudio = true) { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))

        pipeline.onAccessUnit(
            annexB(idr),
            presentationTimeUs = 17_712_160_800L,
            isKeyFrame = true,
        )
        pipeline.onAudioAccessUnit(fakeAdtsFrame(), presentationTimeUs = 81_578_656L)

        assertEquals(0L, published[0].data.firstPacketForPid(VIDEO_PID).pesPts())
        assertEquals(0L, published[1].data.firstPacketForPid(AUDIO_PID).pesPts())
    }

    private fun annexB(vararg nalUnits: ByteArray): ByteArray =
        nalUnits.flatMap { START_CODE.asIterable() + it.asIterable() }.toByteArray()

    private fun fakeAdtsFrame(): ByteArray =
        byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            0x50.toByte(),
            0x40.toByte(),
            0x03.toByte(),
            0x7F.toByte(),
            0xFC.toByte(),
        ) +
            ByteArray(20)

    private fun ByteArray.allPacketsStartWithSyncByte(): Boolean =
        size % TS_PACKET_SIZE == 0 &&
            toList().chunked(TS_PACKET_SIZE).all { it.first().toInt() and 0xff == SYNC_BYTE }

    private fun ByteArray.firstPacketForPid(pid: Int): ByteArray =
        toList()
            .chunked(TS_PACKET_SIZE)
            .map(List<Byte>::toByteArray)
            .first { it.pid() == pid }

    private fun ByteArray.pid(): Int = ((this[1].unsigned() and 0x1f) shl 8) or this[2].unsigned()

    private fun ByteArray.pesPts(): Long {
        val payloadOffset = payloadOffset()
        val ptsOffset = payloadOffset + 9
        return ((this[ptsOffset].unsigned().toLong() shr 1 and 0x07) shl 30) or
            (this[ptsOffset + 1].unsigned().toLong() shl 22) or
            ((this[ptsOffset + 2].unsigned().toLong() shr 1) shl 15) or
            (this[ptsOffset + 3].unsigned().toLong() shl 7) or
            (this[ptsOffset + 4].unsigned().toLong() shr 1)
    }

    private fun ByteArray.payloadOffset(): Int =
        if (hasAdaptationField()) 5 + this[4].unsigned() else 4

    private fun ByteArray.hasAdaptationField(): Boolean = this[3].unsigned() and 0x20 != 0

    private fun Byte.unsigned(): Int = toInt() and 0xff

    private data class PublishedChunk(
        val data: ByteArray,
        val isBootstrap: Boolean,
    )

    companion object {
        private val START_CODE = byteArrayOf(0, 0, 0, 1)
        private const val TS_PACKET_SIZE = 188
        private const val SYNC_BYTE = 0x47
        private const val VIDEO_PID = 0x0100
        private const val AUDIO_PID = 0x0101
    }
}
