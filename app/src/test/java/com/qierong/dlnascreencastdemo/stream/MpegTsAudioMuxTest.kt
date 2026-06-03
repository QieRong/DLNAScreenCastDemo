package com.qierong.dlnascreencastdemo.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpegTsAudioMuxTest {
    @Test
    fun muxAudioAccessUnit_emitsFixedSizePacketsWithSyncBytes() {
        val adtsFrame = fakeAdtsFrame(rawAacSize = 100)
        val output = MpegTsMuxer().muxAudioAccessUnit(
            adtsFrame = adtsFrame,
            presentationTimeUs = 0,
        )

        assertEquals(0, output.size % TS_PACKET_SIZE)
        assertTrue(output.isNotEmpty())
        assertTrue(output.tsPackets().all { it.size == TS_PACKET_SIZE })
        assertTrue(output.tsPackets().all { it[0].unsigned() == SYNC_BYTE })
    }

    @Test
    fun muxAudioAccessUnit_firstPacketHasPayloadUnitStartAndCorrectPid() {
        val adtsFrame = fakeAdtsFrame(rawAacSize = 50)
        val packets = MpegTsMuxer().muxAudioAccessUnit(
            adtsFrame = adtsFrame,
            presentationTimeUs = 21_333,
        ).tsPackets()

        val firstAudioPacket = packets.first()

        // 第一个包的 PID 必须是 AUDIO_PID (0x0101)
        assertEquals(AUDIO_PID, firstAudioPacket.pid())
        // payload_unit_start_indicator 必须置 1
        assertTrue("payload_unit_start_indicator 应为 1", firstAudioPacket.hasPayloadUnitStart())
    }

    @Test
    fun muxAudioAccessUnit_allPacketsHaveAudioPid() {
        // 大帧会拆成多个 TS 包
        val adtsFrame = fakeAdtsFrame(rawAacSize = 500)
        val packets = MpegTsMuxer().muxAudioAccessUnit(
            adtsFrame = adtsFrame,
            presentationTimeUs = 0,
        ).tsPackets()

        assertTrue("音频帧应产生至少 1 个 TS 包", packets.isNotEmpty())
        assertTrue(
            "所有 TS 包的 PID 应为 AUDIO_PID",
            packets.all { it.pid() == AUDIO_PID },
        )
    }

    @Test
    fun muxAudioAccessUnit_ptsUsing90KhzClock() {
        // presentationTimeUs = 1_000_000 μs → 90kHz = 90_000 ticks
        val pts = MpegTsMuxer().muxAudioAccessUnit(
            adtsFrame = fakeAdtsFrame(rawAacSize = 30),
            presentationTimeUs = 1_000_000L,
        ).tsPackets().first().audioPacketPts()

        assertEquals(90_000L, pts)
    }

    @Test
    fun muxAudioAccessUnit_pmtCrcIsValidAfterAudioExtension() {
        // 验证扩展后的 PMT CRC 仍然正确（回归：PMT_SECTION_LENGTH 改变后 CRC 必须重算）
        val packets = MpegTsMuxer().muxVideoAccessUnit(
            annexB = byteArrayOf(0, 0, 0, 1, 0x65) + ByteArray(20),
            presentationTimeUs = 0,
            isKeyFrame = false,
        ).tsPackets()

        val pat = packets.single { it.pid() == PAT_PID }.psiSection()
        val pmtPid = pat.sectionPid(entryOffset = 10)
        val pmt = packets.single { it.pid() == pmtPid }.psiSection()

        assertEquals(
            "扩展后的 PMT CRC32 应为 0（验残差为 0 代表正确）",
            0,
            pmt.sectionBytes().mpegCrc32(),
        )
    }

    @Test
    fun muxAudioAccessUnit_continuityCounterIncrements() {
        val muxer = MpegTsMuxer()
        val firstPackets = muxer.muxAudioAccessUnit(
            adtsFrame = fakeAdtsFrame(rawAacSize = 50),
            presentationTimeUs = 0,
        ).tsPackets()
        val secondPackets = muxer.muxAudioAccessUnit(
            adtsFrame = fakeAdtsFrame(rawAacSize = 50),
            presentationTimeUs = 21_333,
        ).tsPackets()

        val firstCounter = firstPackets.first().continuityCounter()
        val secondCounter = secondPackets.first().continuityCounter()
        // 连续两帧的第一个包的连续性计数器应该递增
        assertEquals((firstCounter + 1) and 0x0f, secondCounter)
    }

    // ── 辅助方法 ─────────────────────────────────────────────────────────────

    /**
     * 构造一个合法的最小 ADTS 帧（7 字节头 + rawAac）。
     * 此处只需要非空字节数组即可，内容仅用于封装测试。
     */
    private fun fakeAdtsFrame(rawAacSize: Int): ByteArray {
        val totalSize = 7 + rawAacSize
        val profile = 1
        val freqIdx = 3
        val chanCfg = 1
        val header = byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            ((profile shl 6) or (freqIdx shl 2) or (chanCfg shr 2)).toByte(),
            (((chanCfg and 0x3) shl 6) or ((totalSize shr 11) and 0x3)).toByte(),
            ((totalSize shr 3) and 0xFF).toByte(),
            (((totalSize and 0x7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
        return header + ByteArray(rawAacSize) { it.toByte() }
    }

    /**
     * 解析音频 TS 包中 PES 的 PTS（假设无 adaptation field，PES 从 byte 4 开始）。
     * PTS 位于 PES header 中：start_code(3) + stream_id(1) + length(2) + flags(3) + PTS(5)
     */
    private fun ByteArray.audioPacketPts(): Long {
        val pesOffset = TS_HEADER_SIZE  // 音频包无 adaptation field
        val ptsOffset = pesOffset + 9   // start_code(3)+stream_id(1)+length(2)+marker(1)+pts_flag(1)+hdr_len(1)
        return ((this[ptsOffset].unsigned().toLong() shr 1 and 0x07) shl 30) or
            (this[ptsOffset + 1].unsigned().toLong() shl 22) or
            ((this[ptsOffset + 2].unsigned().toLong() shr 1) shl 15) or
            (this[ptsOffset + 3].unsigned().toLong() shl 7) or
            (this[ptsOffset + 4].unsigned().toLong() shr 1)
    }

    private fun ByteArray.tsPackets(): List<ByteArray> =
        toList().chunked(TS_PACKET_SIZE).map(List<Byte>::toByteArray)

    private fun ByteArray.pid(): Int = ((this[1].unsigned() and 0x1f) shl 8) or this[2].unsigned()

    private fun ByteArray.continuityCounter(): Int = this[3].unsigned() and 0x0f

    private fun ByteArray.hasPayloadUnitStart(): Boolean = this[1].unsigned() and 0x40 != 0

    private fun ByteArray.psiSection(): ByteArray {
        val payloadOffset = if (this[3].unsigned() and 0x20 != 0) 5 + this[4].unsigned() else 4
        val pointerField = this[payloadOffset].unsigned()
        return copyOfRange(payloadOffset + 1 + pointerField, size)
    }

    private fun ByteArray.sectionPid(entryOffset: Int): Int =
        ((this[entryOffset].unsigned() and 0x1f) shl 8) or this[entryOffset + 1].unsigned()

    private fun ByteArray.sectionBytes(): ByteArray = copyOfRange(0, sectionEnd())

    private fun ByteArray.sectionEnd(): Int =
        3 + (((this[1].unsigned() and 0x0f) shl 8) or this[2].unsigned())

    private fun ByteArray.mpegCrc32(): Int {
        var crc = -1
        forEach { byte ->
            crc = crc xor (byte.unsigned() shl 24)
            repeat(Byte.SIZE_BITS) {
                crc = if (crc and Int.MIN_VALUE != 0) (crc shl 1) xor CRC_POLYNOMIAL else crc shl 1
            }
        }
        return crc
    }

    private fun Byte.unsigned(): Int = toInt() and 0xff

    companion object {
        private const val TS_PACKET_SIZE = 188
        private const val TS_HEADER_SIZE = 4
        private const val SYNC_BYTE = 0x47
        private const val PAT_PID = 0x0000
        private const val AUDIO_PID = 0x0101
        private const val CRC_POLYNOMIAL = 0x04c11db7
    }
}
