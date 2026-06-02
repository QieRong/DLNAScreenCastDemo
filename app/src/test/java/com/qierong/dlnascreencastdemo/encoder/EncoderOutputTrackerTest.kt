package com.qierong.dlnascreencastdemo.encoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderOutputTrackerTest {
    @Test
    fun codecConfigBuffer_isNotCountedAsMediaFrame() {
        val tracker = EncoderOutputTracker()

        val event = tracker.recordBuffer(isCodecConfig = true, isKeyFrame = true)

        assertTrue(event.isCodecConfig)
        assertFalse(event.isFirstMediaFrame)
        assertFalse(event.isFirstKeyFrame)
        assertEquals(1, event.stats.codecConfigBufferCount)
        assertEquals(0, event.stats.encodedFrameCount)
    }

    @Test
    fun mediaFrames_reportFirstMediaFrameAndFirstKeyFrameSeparately() {
        val tracker = EncoderOutputTracker()

        val firstFrame = tracker.recordBuffer(isCodecConfig = false, isKeyFrame = false)
        val firstKeyFrame = tracker.recordBuffer(isCodecConfig = false, isKeyFrame = true)

        assertTrue(firstFrame.isFirstMediaFrame)
        assertFalse(firstFrame.isFirstKeyFrame)
        assertFalse(firstKeyFrame.isFirstMediaFrame)
        assertTrue(firstKeyFrame.isFirstKeyFrame)
        assertEquals(2, firstKeyFrame.stats.encodedFrameCount)
    }

    @Test
    fun outputFormat_tracksCsdPresenceWithoutBinaryContent() {
        val tracker = EncoderOutputTracker()

        val stats = tracker.recordOutputFormat(hasCsd0 = true, hasCsd1 = false)

        assertTrue(stats.hasCsd0)
        assertFalse(stats.hasCsd1)
    }
}
