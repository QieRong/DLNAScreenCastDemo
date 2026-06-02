package com.qierong.dlnascreencastdemo.stream

class AvcAnnexBNormalizer {
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    fun clearParameterSets() {
        sps = null
        pps = null
    }

    fun updateCodecSpecificData(csd0: ByteArray?, csd1: ByteArray?) {
        listOfNotNull(csd0, csd1)
            .flatMap(::splitNalUnits)
            .forEach(::saveParameterSet)
    }

    fun normalizeForStreaming(data: ByteArray, isKeyFrame: Boolean): ByteArray? {
        val nalUnits = splitNalUnits(data)
        nalUnits.forEach(::saveParameterSet)
        val mediaNalUnits = nalUnits.filterNot(::isParameterSet)
        if (!isKeyFrame) return annexB(mediaNalUnits)
        val currentSps = sps ?: return null
        val currentPps = pps ?: return null
        return annexB(listOf(currentSps, currentPps) + mediaNalUnits)
    }

    private fun saveParameterSet(nalUnit: ByteArray) {
        when (nalUnit.type()) {
            SPS_TYPE -> sps = nalUnit.copyOf()
            PPS_TYPE -> pps = nalUnit.copyOf()
        }
    }

    private fun isParameterSet(nalUnit: ByteArray): Boolean =
        nalUnit.type() == SPS_TYPE || nalUnit.type() == PPS_TYPE

    private fun ByteArray.type(): Int = firstOrNull()?.toInt()?.and(0x1f) ?: -1

    companion object {
        private const val SPS_TYPE = 7
        private const val PPS_TYPE = 8
        private val START_CODE = byteArrayOf(0, 0, 0, 1)

        internal fun splitNalUnits(data: ByteArray): List<ByteArray> {
            if (data.isEmpty()) return emptyList()
            val startCodes = findStartCodes(data)
            if (startCodes.isNotEmpty()) {
                return startCodes.mapIndexedNotNull { index, entry ->
                    val start = entry.first + entry.second
                    val end = startCodes.getOrNull(index + 1)?.first ?: data.size
                    data.copyOfRange(start, end).takeIf(ByteArray::isNotEmpty)
                }
            }
            val avcc = splitAvcc(data)
            return avcc ?: listOf(data.copyOf())
        }

        private fun findStartCodes(data: ByteArray): List<Pair<Int, Int>> {
            val result = mutableListOf<Pair<Int, Int>>()
            var index = 0
            while (index <= data.size - 3) {
                val length = when {
                    data[index] != 0.toByte() || data[index + 1] != 0.toByte() -> 0
                    data[index + 2] == 1.toByte() -> 3
                    index <= data.size - 4 &&
                        data[index + 2] == 0.toByte() &&
                        data[index + 3] == 1.toByte() -> 4
                    else -> 0
                }
                if (length > 0) {
                    result += index to length
                    index += length
                } else {
                    index++
                }
            }
            return result
        }

        private fun splitAvcc(data: ByteArray): List<ByteArray>? {
            val result = mutableListOf<ByteArray>()
            var offset = 0
            while (offset < data.size) {
                if (offset + 4 > data.size) return null
                val length =
                    ((data[offset].toInt() and 0xff) shl 24) or
                        ((data[offset + 1].toInt() and 0xff) shl 16) or
                        ((data[offset + 2].toInt() and 0xff) shl 8) or
                        (data[offset + 3].toInt() and 0xff)
                offset += 4
                if (length <= 0 || offset + length > data.size) return null
                result += data.copyOfRange(offset, offset + length)
                offset += length
            }
            return result
        }

        private fun annexB(nalUnits: List<ByteArray>): ByteArray =
            nalUnits
                .flatMap { START_CODE.asIterable() + it.asIterable() }
                .toByteArray()
    }
}
