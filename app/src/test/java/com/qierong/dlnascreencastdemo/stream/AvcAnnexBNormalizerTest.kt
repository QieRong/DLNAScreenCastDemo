package com.qierong.dlnascreencastdemo.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvcAnnexBNormalizerTest {
    private val sps = byteArrayOf(0x67, 0x64, 0x00, 0x1f)
    private val pps = byteArrayOf(0x68, 0x01, 0x02)
    private val idr = byteArrayOf(0x65, 0x11, 0x22)

    @Test
    fun normalizeForStreaming_insertsSavedParameterSetsBeforeAvccIdr() {
        val normalizer = AvcAnnexBNormalizer()
        normalizer.updateCodecSpecificData(annexB(sps), annexB(pps))

        val output = normalizer.normalizeForStreaming(avcc(idr), isKeyFrame = true)

        assertEquals(listOf(7, 8, 5), requireNotNull(output).nalUnitTypes())
    }

    @Test
    fun normalizeForStreaming_deduplicatesRepeatedParameterSets() {
        val normalizer = AvcAnnexBNormalizer()
        normalizer.updateCodecSpecificData(annexB(sps, sps), annexB(pps, pps))

        val output = normalizer.normalizeForStreaming(annexB(sps, pps, idr), isKeyFrame = true)

        assertEquals(listOf(7, 8, 5), requireNotNull(output).nalUnitTypes())
    }

    @Test
    fun normalizeForStreaming_waitsWhenIdrHasNoParameterSets() {
        val normalizer = AvcAnnexBNormalizer()

        assertNull(normalizer.normalizeForStreaming(annexB(idr), isKeyFrame = true))
    }

    private fun annexB(vararg nalUnits: ByteArray): ByteArray =
        nalUnits.flatMap { START_CODE.asIterable() + it.asIterable() }.toByteArray()

    private fun avcc(nalUnit: ByteArray): ByteArray =
        byteArrayOf(
            0,
            0,
            0,
            nalUnit.size.toByte(),
            *nalUnit,
        )

    private fun ByteArray.nalUnitTypes(): List<Int> =
        AvcAnnexBNormalizer.splitNalUnits(this).map { it[0].toInt() and 0x1f }

    companion object {
        private val START_CODE = byteArrayOf(0, 0, 0, 1)
    }
}
