package com.qierong.dlnascreencastdemo.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpegTsMuxerTest {
    @Test
    fun muxVideoAccessUnit_emitsFixedSizePacketsWithSyncBytes() {
        val output = MpegTsMuxer().muxVideoAccessUnit(
            annexB = annexBPayload(size = 500),
            presentationTimeUs = 0,
            isKeyFrame = false,
        )

        assertEquals(0, output.size % TS_PACKET_SIZE)
        assertTrue(output.isNotEmpty())
        assertTrue(output.tsPackets().all { it.size == TS_PACKET_SIZE })
        assertTrue(output.tsPackets().all { it[0].unsigned() == SYNC_BYTE })
    }

    @Test
    fun muxVideoAccessUnit_defaultPmtDeclaresVideoOnlyBeforeFirstFrame() {
        val packets = MpegTsMuxer().muxVideoAccessUnit(
            annexB = annexBPayload(),
            presentationTimeUs = 0,
            isKeyFrame = false,
        ).tsPackets()

        val pat = packets.single { it.pid() == PAT_PID }.psiSection()
        val pmtPid = pat.sectionPid(entryOffset = 10)
        val pmt = packets.single { it.pid() == pmtPid }.psiSection()

        // PMT 表 ID 正确
        assertEquals(PMT_TABLE_ID, pmt[0].unsigned())
        // 视频 PID 和流类型
        assertEquals(VIDEO_PID, pmt.sectionPid(entryOffset = 8))
        val streamTypes = pmt.declaredStreamTypes()
        assertEquals(listOf(H264_STREAM_TYPE), streamTypes)
    }

    @Test
    fun muxVideoAccessUnit_includeAudioDeclaresAacPidInPmt() {
        val packets = MpegTsMuxer(includeAudio = true).muxVideoAccessUnit(
            annexB = annexBPayload(),
            presentationTimeUs = 0,
            isKeyFrame = false,
        ).tsPackets()

        val pat = packets.single { it.pid() == PAT_PID }.psiSection()
        val pmtPid = pat.sectionPid(entryOffset = 10)
        val pmt = packets.single { it.pid() == pmtPid }.psiSection()
        val streamTypes = pmt.declaredStreamTypes()

        assertTrue(streamTypes.contains(H264_STREAM_TYPE))
        assertTrue(streamTypes.contains(AAC_ADTS_STREAM_TYPE))
        assertEquals(listOf(H264_STREAM_TYPE, AAC_ADTS_STREAM_TYPE), streamTypes)
    }

    @Test
    fun muxVideoAccessUnit_emitsValidPatAndPmtCrcs() {
        val packets = MpegTsMuxer().muxVideoAccessUnit(
            annexB = annexBPayload(),
            presentationTimeUs = 0,
            isKeyFrame = false,
        ).tsPackets()

        val pat = packets.single { it.pid() == PAT_PID }.psiSection()
        val pmtPid = pat.sectionPid(entryOffset = 10)
        val pmt = packets.single { it.pid() == pmtPid }.psiSection()

        assertEquals(0, pat.sectionBytes().mpegCrc32())
        assertEquals(0, pmt.sectionBytes().mpegCrc32())
    }

    @Test
    fun muxVideoAccessUnit_writesPtsAndPcrUsing90KhzClock() {
        val presentationTimeUs = 1_500_000L
        val videoPacket = MpegTsMuxer().muxVideoAccessUnit(
            annexB = annexBPayload(),
            presentationTimeUs = presentationTimeUs,
            isKeyFrame = false,
        ).tsPackets().first { it.pid() == VIDEO_PID }

        assertTrue(videoPacket.hasPcr())
        assertEquals(135_000L, videoPacket.pcrBase())
        assertEquals(135_000L, videoPacket.pesPts())
    }

    @Test
    fun muxVideoAccessUnit_incrementsContinuityCountersIndependentlyPerPid() {
        val muxer = MpegTsMuxer()
        val packets = (
            muxer.muxVideoAccessUnit(annexBPayload(size = 500), 0, isKeyFrame = false) +
                muxer.muxVideoAccessUnit(annexBPayload(size = 20), 33_333, isKeyFrame = false) +
                muxer.muxVideoAccessUnit(annexBPayload(size = 20), 66_666, isKeyFrame = true)
            ).tsPackets()

        assertEquals(listOf(0, 1), packets.countersFor(PAT_PID))
        assertEquals(listOf(0, 1), packets.countersFor(PMT_PID))
        assertEquals(
            packets.count { it.pid() == VIDEO_PID }.modulo16Sequence(),
            packets.countersFor(VIDEO_PID),
        )
    }

    @Test
    fun muxVideoAccessUnit_repeatsTablesAndMarksRandomAccessBeforeKeyFrame() {
        val muxer = MpegTsMuxer()
        muxer.muxVideoAccessUnit(annexBPayload(), 0, isKeyFrame = false)

        val deltaPackets = muxer.muxVideoAccessUnit(
            annexB = annexBPayload(),
            presentationTimeUs = 33_333,
            isKeyFrame = false,
        ).tsPackets()
        val keyPackets = muxer.muxVideoAccessUnit(
            annexB = annexBPayload(),
            presentationTimeUs = 66_666,
            isKeyFrame = true,
        ).tsPackets()

        assertFalse(deltaPackets.any { it.pid() == PAT_PID || it.pid() == PMT_PID })
        assertEquals(listOf(PAT_PID, PMT_PID), keyPackets.take(2).map { it.pid() })
        assertTrue(keyPackets.first { it.pid() == VIDEO_PID }.hasRandomAccessIndicator())
    }

    private fun annexBPayload(size: Int = 20): ByteArray =
        byteArrayOf(0, 0, 0, 1, 0x65) + ByteArray(size) { it.toByte() }

    private fun ByteArray.tsPackets(): List<ByteArray> =
        toList()
            .chunked(TS_PACKET_SIZE)
            .map(List<Byte>::toByteArray)

    private fun ByteArray.pid(): Int = ((this[1].unsigned() and 0x1f) shl 8) or this[2].unsigned()

    private fun ByteArray.continuityCounter(): Int = this[3].unsigned() and 0x0f

    private fun List<ByteArray>.countersFor(pid: Int): List<Int> =
        filter { it.pid() == pid }.map { it.continuityCounter() }

    private fun Int.modulo16Sequence(): List<Int> = List(this) { it and 0x0f }

    private fun ByteArray.psiSection(): ByteArray {
        val payloadOffset = payloadOffset()
        val pointerField = this[payloadOffset].unsigned()
        return copyOfRange(payloadOffset + 1 + pointerField, size)
    }

    private fun ByteArray.sectionPid(entryOffset: Int): Int =
        ((this[entryOffset].unsigned() and 0x1f) shl 8) or this[entryOffset + 1].unsigned()

    private fun ByteArray.declaredStreamTypes(): List<Int> {
        val sectionEnd = sectionEnd()
        val crcOffset = sectionEnd - 4
        val streamTypes = mutableListOf<Int>()
        var offset = 12 + (((this[10].unsigned() and 0x0f) shl 8) or this[11].unsigned())
        while (offset < crcOffset) {
            streamTypes += this[offset].unsigned()
            offset += 5 + (((this[offset + 3].unsigned() and 0x0f) shl 8) or this[offset + 4].unsigned())
        }
        return streamTypes
    }

    private fun ByteArray.sectionBytes(): ByteArray = copyOfRange(0, sectionEnd())

    private fun ByteArray.sectionEnd(): Int =
        3 + (((this[1].unsigned() and 0x0f) shl 8) or this[2].unsigned())

    private fun ByteArray.mpegCrc32(): Int {
        var crc = -1
        forEach { byte ->
            crc = crc xor (byte.unsigned() shl 24)
            repeat(Byte.SIZE_BITS) {
                crc = if (crc and Int.MIN_VALUE != 0) {
                    (crc shl 1) xor CRC_POLYNOMIAL
                } else {
                    crc shl 1
                }
            }
        }
        return crc
    }

    private fun ByteArray.hasPcr(): Boolean =
        hasAdaptationField() && this[5].unsigned() and PCR_FLAG != 0

    private fun ByteArray.hasRandomAccessIndicator(): Boolean =
        hasAdaptationField() && this[5].unsigned() and RANDOM_ACCESS_FLAG != 0

    private fun ByteArray.pcrBase(): Long {
        require(hasPcr())
        return (this[6].unsigned().toLong() shl 25) or
            (this[7].unsigned().toLong() shl 17) or
            (this[8].unsigned().toLong() shl 9) or
            (this[9].unsigned().toLong() shl 1) or
            (this[10].unsigned().toLong() shr 7)
    }

    private fun ByteArray.pesPts(): Long {
        val payloadOffset = payloadOffset()
        assertEquals(listOf(0, 0, 1, VIDEO_STREAM_ID), slice(payloadOffset..payloadOffset + 3).map { it.unsigned() })
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

    companion object {
        private const val TS_PACKET_SIZE = 188
        private const val SYNC_BYTE = 0x47
        private const val PAT_PID = 0x0000
        private const val PMT_PID = 0x1000
        private const val VIDEO_PID = 0x0100
        private const val AUDIO_PID = 0x0101
        private const val PMT_TABLE_ID = 0x02
        private const val H264_STREAM_TYPE = 0x1b
        private const val AAC_ADTS_STREAM_TYPE = 0x0f
        private const val AAC_LATM_STREAM_TYPE = 0x11
        private const val VIDEO_STREAM_ID = 0xe0
        private const val RANDOM_ACCESS_FLAG = 0x40
        private const val PCR_FLAG = 0x10
        private const val CRC_POLYNOMIAL = 0x04c11db7
    }
}
