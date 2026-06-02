package com.qierong.dlnascreencastdemo.stream

import java.io.ByteArrayOutputStream

class MpegTsMuxer {
    private val continuityCounters = mutableMapOf<Int, Int>()
    private var emittedTables = false

    fun muxVideoAccessUnit(
        annexB: ByteArray,
        presentationTimeUs: Long,
        isKeyFrame: Boolean,
    ): ByteArray {
        require(annexB.isNotEmpty()) { "Annex-B access unit must not be empty" }
        require(presentationTimeUs >= 0) { "Presentation time must not be negative" }

        val output = ByteArrayOutputStream()
        if (!emittedTables || isKeyFrame) {
            output.write(psiPacket(PAT_PID, patSection()))
            output.write(psiPacket(PMT_PID, pmtSection()))
            emittedTables = true
        }

        val timestamp90Khz = to90Khz(presentationTimeUs)
        packetizeVideo(
            pes = videoPes(annexB, timestamp90Khz),
            pcrBase90Khz = timestamp90Khz,
            isKeyFrame = isKeyFrame,
        ).forEach(output::write)
        return output.toByteArray()
    }

    private fun psiPacket(pid: Int, section: ByteArray): ByteArray {
        require(section.size + PSI_POINTER_FIELD_SIZE <= TS_PAYLOAD_SIZE)
        val packet = ByteArray(TS_PACKET_SIZE) { STUFFING_BYTE }
        writeHeader(
            packet = packet,
            pid = pid,
            payloadUnitStart = true,
            hasAdaptationField = false,
        )
        packet[TS_HEADER_SIZE] = 0
        section.copyInto(packet, TS_HEADER_SIZE + PSI_POINTER_FIELD_SIZE)
        return packet
    }

    private fun packetizeVideo(
        pes: ByteArray,
        pcrBase90Khz: Long,
        isKeyFrame: Boolean,
    ): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        var firstPacket = true
        while (offset < pes.size) {
            val minimumAdaptationLength = if (firstPacket) PCR_ADAPTATION_LENGTH else FLAGS_ONLY_ADAPTATION_LENGTH
            val payloadSize = minOf(
                pes.size - offset,
                TS_PACKET_SIZE - TS_HEADER_SIZE - ADAPTATION_LENGTH_SIZE - minimumAdaptationLength,
            )
            val adaptationLength = TS_PACKET_SIZE - TS_HEADER_SIZE - ADAPTATION_LENGTH_SIZE - payloadSize
            val packet = ByteArray(TS_PACKET_SIZE) { STUFFING_BYTE }
            writeHeader(
                packet = packet,
                pid = VIDEO_PID,
                payloadUnitStart = firstPacket,
                hasAdaptationField = true,
            )
            packet[TS_HEADER_SIZE] = adaptationLength.toByte()
            packet[TS_HEADER_SIZE + ADAPTATION_LENGTH_SIZE] = when {
                firstPacket && isKeyFrame -> (PCR_FLAG or RANDOM_ACCESS_FLAG).toByte()
                firstPacket -> PCR_FLAG.toByte()
                else -> 0
            }
            if (firstPacket) {
                encodePcr(pcrBase90Khz).copyInto(packet, PCR_OFFSET)
            }
            val payloadOffset = TS_HEADER_SIZE + ADAPTATION_LENGTH_SIZE + adaptationLength
            pes.copyInto(packet, payloadOffset, offset, offset + payloadSize)
            offset += payloadSize
            firstPacket = false
            packets += packet
        }
        return packets
    }

    private fun writeHeader(
        packet: ByteArray,
        pid: Int,
        payloadUnitStart: Boolean,
        hasAdaptationField: Boolean,
    ) {
        packet[0] = SYNC_BYTE
        packet[1] = (
            (if (payloadUnitStart) PAYLOAD_UNIT_START_FLAG else 0) or
                ((pid shr 8) and PID_HIGH_MASK)
            ).toByte()
        packet[2] = pid.toByte()
        packet[3] = (
            (if (hasAdaptationField) ADAPTATION_AND_PAYLOAD_FLAGS else PAYLOAD_ONLY_FLAGS) or
                nextContinuityCounter(pid)
            ).toByte()
    }

    private fun nextContinuityCounter(pid: Int): Int {
        val current = continuityCounters[pid] ?: 0
        continuityCounters[pid] = (current + 1) and CONTINUITY_COUNTER_MASK
        return current
    }

    private fun videoPes(annexB: ByteArray, timestamp90Khz: Long): ByteArray =
        byteArrayOf(
            0,
            0,
            1,
            VIDEO_STREAM_ID,
            0,
            0,
            PES_MARKER_BITS,
            PTS_ONLY_FLAG,
            PTS_SIZE.toByte(),
        ) + encodePts(timestamp90Khz) + annexB

    private fun patSection(): ByteArray =
        withCrc(
            byteArrayOf(
                PAT_TABLE_ID,
                SECTION_SYNTAX_HIGH_BITS,
                PAT_SECTION_LENGTH,
                0,
                TRANSPORT_STREAM_ID,
                CURRENT_NEXT_VERSION,
                0,
                0,
                0,
                PROGRAM_NUMBER,
                pidHigh(PMT_PID),
                PMT_PID.toByte(),
            ),
        )

    private fun pmtSection(): ByteArray =
        withCrc(
            byteArrayOf(
                PMT_TABLE_ID,
                SECTION_SYNTAX_HIGH_BITS,
                PMT_SECTION_LENGTH,
                0,
                PROGRAM_NUMBER,
                CURRENT_NEXT_VERSION,
                0,
                0,
                pidHigh(VIDEO_PID),
                VIDEO_PID.toByte(),
                NO_DESCRIPTORS_HIGH_BITS,
                0,
                H264_STREAM_TYPE,
                pidHigh(VIDEO_PID),
                VIDEO_PID.toByte(),
                NO_DESCRIPTORS_HIGH_BITS,
                0,
            ),
        )

    private fun pidHigh(pid: Int): Byte = (RESERVED_PID_BITS or ((pid shr 8) and PID_HIGH_MASK)).toByte()

    private fun withCrc(section: ByteArray): ByteArray {
        val crc = mpegCrc32(section)
        return section + byteArrayOf(
            (crc shr 24).toByte(),
            (crc shr 16).toByte(),
            (crc shr 8).toByte(),
            crc.toByte(),
        )
    }

    private fun mpegCrc32(data: ByteArray): Int {
        var crc = -1
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xff) shl 24)
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

    private fun encodePts(timestamp90Khz: Long): ByteArray {
        val pts = timestamp90Khz and TIMESTAMP_MASK
        return byteArrayOf(
            (PTS_PREFIX or (((pts shr 30).toInt() and 0x07) shl 1) or MARKER_BIT).toByte(),
            (pts shr 22).toByte(),
            ((((pts shr 15).toInt() and 0x7f) shl 1) or MARKER_BIT).toByte(),
            (pts shr 7).toByte(),
            (((pts.toInt() and 0x7f) shl 1) or MARKER_BIT).toByte(),
        )
    }

    private fun encodePcr(timestamp90Khz: Long): ByteArray {
        val pcrBase = timestamp90Khz and TIMESTAMP_MASK
        return byteArrayOf(
            (pcrBase shr 25).toByte(),
            (pcrBase shr 17).toByte(),
            (pcrBase shr 9).toByte(),
            (pcrBase shr 1).toByte(),
            (((pcrBase.toInt() and 0x01) shl 7) or PCR_RESERVED_BITS).toByte(),
            0,
        )
    }

    private fun to90Khz(presentationTimeUs: Long): Long =
        (presentationTimeUs / MICROSECONDS_PER_MILLISECOND) * TICKS_PER_MILLISECOND +
            (presentationTimeUs % MICROSECONDS_PER_MILLISECOND) * TICKS_PER_MILLISECOND /
            MICROSECONDS_PER_MILLISECOND

    companion object {
        private const val TS_PACKET_SIZE = 188
        private const val TS_HEADER_SIZE = 4
        private const val TS_PAYLOAD_SIZE = TS_PACKET_SIZE - TS_HEADER_SIZE
        private const val PSI_POINTER_FIELD_SIZE = 1
        private const val ADAPTATION_LENGTH_SIZE = 1
        private const val FLAGS_ONLY_ADAPTATION_LENGTH = 1
        private const val PCR_ADAPTATION_LENGTH = 7
        private const val PCR_OFFSET = TS_HEADER_SIZE + ADAPTATION_LENGTH_SIZE + FLAGS_ONLY_ADAPTATION_LENGTH
        private const val PTS_SIZE = 5

        private const val PAT_PID = 0x0000
        private const val PMT_PID = 0x1000
        private const val VIDEO_PID = 0x0100

        private const val PAT_TABLE_ID: Byte = 0x00
        private const val PMT_TABLE_ID: Byte = 0x02
        private const val H264_STREAM_TYPE: Byte = 0x1b
        private const val VIDEO_STREAM_ID: Byte = 0xe0.toByte()
        private const val PAT_SECTION_LENGTH: Byte = 0x0d
        private const val PMT_SECTION_LENGTH: Byte = 0x12
        private const val TRANSPORT_STREAM_ID: Byte = 0x01
        private const val PROGRAM_NUMBER: Byte = 0x01

        private const val SYNC_BYTE: Byte = 0x47
        private const val STUFFING_BYTE: Byte = 0xff.toByte()
        private const val SECTION_SYNTAX_HIGH_BITS: Byte = 0xb0.toByte()
        private const val CURRENT_NEXT_VERSION: Byte = 0xc1.toByte()
        private const val NO_DESCRIPTORS_HIGH_BITS: Byte = 0xf0.toByte()
        private const val RESERVED_PID_BITS = 0xe0
        private const val PAYLOAD_UNIT_START_FLAG = 0x40
        private const val PID_HIGH_MASK = 0x1f
        private const val CONTINUITY_COUNTER_MASK = 0x0f
        private const val PAYLOAD_ONLY_FLAGS = 0x10
        private const val ADAPTATION_AND_PAYLOAD_FLAGS = 0x30
        private const val RANDOM_ACCESS_FLAG = 0x40
        private const val PCR_FLAG = 0x10
        private const val PCR_RESERVED_BITS = 0x7e
        private const val PES_MARKER_BITS: Byte = 0x80.toByte()
        private const val PTS_ONLY_FLAG: Byte = 0x80.toByte()
        private const val PTS_PREFIX = 0x20
        private const val MARKER_BIT = 0x01
        private const val MICROSECONDS_PER_MILLISECOND = 1_000L
        private const val TICKS_PER_MILLISECOND = 90L
        private const val TIMESTAMP_MASK = (1L shl 33) - 1
        private const val CRC_POLYNOMIAL = 0x04c11db7
    }
}
