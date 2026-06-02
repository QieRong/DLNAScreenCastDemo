package com.qierong.dlnascreencastdemo.encoder

import org.junit.Assert.assertThrows
import org.junit.Test

class EncoderConfigTest {
    @Test
    fun config_rejectsNonPositiveVideoBitrate() {
        assertThrows(IllegalArgumentException::class.java) {
            EncoderConfig(
                width = 1920,
                height = 1080,
                videoBitrate = 0,
                bitrateMode = BitrateMode.CBR,
            )
        }
    }
}
