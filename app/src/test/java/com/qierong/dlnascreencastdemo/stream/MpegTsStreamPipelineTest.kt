package com.qierong.dlnascreencastdemo.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ─── PR15：bootstrap/replay 行为验证 ─────────────────────────────────────

    /**
     * 验证 P-frame 不会被标记为 bootstrap（isBootstrap = false）。
     *
     * LocalStreamServer 只把 isBootstrap=true 的 chunk 加入 replay 缓存。
     * 如果 P-frame 进入 replay，新客户端会从 P-frame 开始接收，导致 Kodi 黑屏。
     */
    @Test
    fun onAccessUnit_pFrameIsNotMarkedAsBootstrap() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        // 先发一个关键帧建立基线
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 0, isKeyFrame = true)
        // 再发 P-frame
        pipeline.onAccessUnit(annexB(delta), presentationTimeUs = 33_333, isKeyFrame = false)

        // 共发布 2 个 chunk
        assertEquals(2, published.size)
        // 第一个是关键帧，isBootstrap = true
        assertTrue("关键帧 chunk 必须标记为 bootstrap", published[0].isBootstrap)
        // 第二个是 P-frame，isBootstrap = false
        assertFalse("P-frame chunk 不能标记为 bootstrap，否则 replay 会从 P-frame 开始", published[1].isBootstrap)
    }

    /**
     * 验证 reset 后再次等到新关键帧前，旧 GOP 已被丢弃。
     *
     * encoder 重建后 reset() 被调用，waitingForKeyFrame 重置为 true；
     * 此时发出的 P-frame 不应被发布（旧 GOP 不进入新的 replay 窗口）。
     * 新的关键帧才应触发发布。
     */
    @Test
    fun reset_pFrameAfterResetIsDroppedUntilNewKeyFrame() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 0, isKeyFrame = true)
        val countBeforeReset = published.size // 应该是 1

        // 模拟 encoder 重建：reset 管道
        pipeline.reset()
        // reset 后发 P-frame（旧 GOP 遗留），应被丢弃
        pipeline.onAccessUnit(annexB(delta), presentationTimeUs = 33_333, isKeyFrame = false)

        // P-frame 不应发布：published 数量不增加
        assertEquals(
            "reset 后 P-frame 不应被发布（等待新的 IDR 关键帧）",
            countBeforeReset,
            published.size,
        )
    }

    /**
     * 验证 reset 后收到新关键帧（带 SPS/PPS）会触发发布，且标记为 bootstrap。
     *
     * 新客户端在连接后应能收到 SPS/PPS + IDR，而不是来自旧 encoder 的 SPS/PPS。
     */
    @Test
    fun reset_newKeyFrameAfterResetIsPublishedAndMarkedBootstrap() {
        val published = mutableListOf<PublishedChunk>()
        val pipeline = MpegTsStreamPipeline { data, isBootstrap ->
            published += PublishedChunk(data, isBootstrap)
        }
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 0, isKeyFrame = true)

        pipeline.reset()
        // reset 后提供新的 SPS/PPS，再发新的 IDR
        pipeline.onOutputFormat(annexB(sps), annexB(pps))
        pipeline.onAccessUnit(annexB(idr), presentationTimeUs = 66_666, isKeyFrame = true)

        // 应该总共有 2 个 bootstrap chunk：reset 前 1 个 + reset 后 1 个
        assertEquals("reset 前后各发布一个 IDR，共 2 个 chunk", 2, published.size)
        assertTrue("reset 后的新 IDR chunk 必须标记为 bootstrap", published.last().isBootstrap)
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
