package com.qierong.dlnascreencastdemo.encoder

import com.qierong.dlnascreencastdemo.capture.CaptureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderConfigSelectorTest {
    private val selector = EncoderConfigSelector()

    @Test
    fun select_prefersLandscape1080pCanvasAndCbr() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 2670, height = 1200, densityDpi = 440),
            capabilities = listOf(FakeEncoderCapabilities()),
        )

        requireNotNull(selected)
        assertEquals(1920, selected.config.width)
        assertEquals(1080, selected.config.height)
        assertEquals(8_000_000, selected.config.videoBitrate)
        assertEquals(BitrateMode.CBR, selected.config.bitrateMode)
        assertFalse(selected.isDegraded)
    }

    @Test
    fun select_prefersPortrait1080pCanvas() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 1200, height = 2670, densityDpi = 440),
            capabilities = listOf(FakeEncoderCapabilities()),
        )

        requireNotNull(selected)
        assertEquals(1080, selected.config.width)
        assertEquals(1920, selected.config.height)
    }

    @Test
    fun select_skipsCandidateWhenHardwareAlignmentIsNotSatisfied() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 2670, height = 1200, densityDpi = 440),
            capabilities = listOf(
                FakeEncoderCapabilities(
                    widthAlignment = 128,
                    heightAlignment = 16,
                ),
            ),
        )

        requireNotNull(selected)
        assertEquals(1280, selected.config.width)
        assertEquals(720, selected.config.height)
        assertTrue(selected.isDegraded)
    }

    @Test
    fun select_skipsCandidateWhenSizeAndRateAreUnsupported() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 2670, height = 1200, densityDpi = 440),
            capabilities = listOf(
                FakeEncoderCapabilities(
                    supportedSizes = setOf(1280 to 720),
                ),
            ),
        )

        requireNotNull(selected)
        assertEquals(1280, selected.config.width)
        assertEquals(720, selected.config.height)
    }

    @Test
    fun select_clampsBitrateToCapabilityRange() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 2670, height = 1200, densityDpi = 440),
            capabilities = listOf(
                FakeEncoderCapabilities(bitrateRange = 100_000..4_000_000),
            ),
        )

        requireNotNull(selected)
        assertEquals(4_000_000, selected.config.videoBitrate)
        assertTrue(selected.isDegraded)
    }

    @Test
    fun select_usesDefaultBitrateModeWhenCbrIsUnavailable() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 2670, height = 1200, densityDpi = 440),
            capabilities = listOf(FakeEncoderCapabilities(supportsCbr = false)),
        )

        requireNotNull(selected)
        assertEquals(BitrateMode.DEFAULT, selected.config.bitrateMode)
        assertTrue(selected.isDegraded)
    }

    @Test
    fun select_returnsNullWhenNoCandidateIsSupported() {
        val selected = selector.select(
            sourceConfig = CaptureConfig(width = 2670, height = 1200, densityDpi = 440),
            capabilities = listOf(FakeEncoderCapabilities(supportedSizes = emptySet())),
        )

        assertNull(selected)
    }

    private data class FakeEncoderCapabilities(
        override val codecName: String = "test.avc.encoder",
        override val bitrateRange: IntRange = 100_000..10_000_000,
        override val widthAlignment: Int = 2,
        override val heightAlignment: Int = 2,
        override val supportsCbr: Boolean = true,
        private val supportedSizes: Set<Pair<Int, Int>> = setOf(
            1920 to 1080,
            1080 to 1920,
            1280 to 720,
            720 to 1280,
            854 to 480,
            480 to 854,
            640 to 360,
            360 to 640,
        ),
    ) : AvcEncoderCapabilities {
        override fun areSizeAndRateSupported(width: Int, height: Int, frameRate: Double): Boolean =
            frameRate == 30.0 && (width to height) in supportedSizes
    }
}
