package com.qierong.dlnascreencastdemo.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpegTsStreamPipelineTest {

    @Test
    fun testEnsureMonotonicUs_NormalIncrease() {
        val pipeline = MpegTsStreamPipeline { _, _ -> }
        val result = pipeline.ensureMonotonicUs(100L, 50L, "Test")
        assertEquals(100L, result)
    }

    @Test
    fun testEnsureMonotonicUs_SmallBackwardsJump_ClampsTo12us() {
        val pipeline = MpegTsStreamPipeline { _, _ -> }
        val lastPtsUs = 1000L
        val currentPtsUs = 900L // Went backwards
        
        val result = pipeline.ensureMonotonicUs(currentPtsUs, lastPtsUs, "Test")
        assertEquals(lastPtsUs + 12L, result)
        
        val lastTicks = (lastPtsUs * 90000) / 1000000
        val resultTicks = (result * 90000) / 1000000
        assertTrue("Ticks must strictly increase", resultTicks > lastTicks)
    }

    @Test
    fun testEnsureMonotonicUs_LargeBackwardsJump_DropsFrame() {
        val pipeline = MpegTsStreamPipeline { _, _ -> }
        val lastPtsUs = 2_000_000L
        val currentPtsUs = 500_000L // Jumped backwards
        
        val result = pipeline.ensureMonotonicUs(currentPtsUs, lastPtsUs, "Test")
        assertEquals(-1L, result)
    }

    @Test
    fun testVideoOnly_FirstKeyFrameEstablishesBaseTime() {
        var callbackCalled = false
        val pipeline = MpegTsStreamPipeline(includeAudio = false) { _, _ -> 
            callbackCalled = true 
        }
        
        // Dummy SPS/PPS for keyframe (Annex B)
        val dummyData = byteArrayOf(0, 0, 0, 1, 103, 0, 0, 0, 1, 104, 0, 0, 0, 1, 101)
        pipeline.onCodecConfig(byteArrayOf(0, 0, 0, 1, 103))
        pipeline.onOutputFormat(null, null)
        
        pipeline.onAccessUnit(dummyData, 10000L, true)
        assertTrue(callbackCalled)
    }

    @Test
    fun testAudioBeforeVideoKeyframe_Dropped() {
        val pipeline = MpegTsStreamPipeline(includeAudio = true) { _, _ -> }
        val dummyAudio = byteArrayOf(-1, -15, 80, 128.toByte(), 1, 63, -4, 0, 0) // Dummy ADTS header
        
        val accepted = pipeline.onAudioAccessUnit(dummyAudio, 5000L)
        assertFalse("Audio should be dropped before video keyframe", accepted)
    }

    @Test
    fun testVideoFirstThenAudio_RetainsRelativePts() {
        var receivedPackets = 0
        val pipeline = MpegTsStreamPipeline(includeAudio = true) { _, _ -> 
            receivedPackets++
        }
        
        pipeline.onCodecConfig(byteArrayOf(0, 0, 0, 1, 103, 0, 0, 0, 1, 104))
        
        val dummyVideo = byteArrayOf(0, 0, 0, 1, 101)
        pipeline.onAccessUnit(dummyVideo, 10000L, true) // Base time is 10000
        val packetsAfterVideo = receivedPackets
        assertTrue("Video keyframe should produce output", packetsAfterVideo > 0)
        
        val dummyAudio = byteArrayOf(-1, -15, 80, 128.toByte(), 1, 63, -4, 0, 0)
        val accepted = pipeline.onAudioAccessUnit(dummyAudio, 15000L) // Relative time 5000
        assertTrue("Audio should be accepted after video keyframe", accepted)
        assertTrue("Audio should produce output", receivedPackets > packetsAfterVideo)
    }

    @Test
    fun testReset_ClearsStateAndWaitsForKeyframe() {
        var callbackCount = 0
        val pipeline = MpegTsStreamPipeline(includeAudio = false) { _, _ -> 
            callbackCount++
        }
        
        pipeline.onCodecConfig(byteArrayOf(0, 0, 0, 1, 103, 0, 0, 0, 1, 104))
        
        val dummyKey = byteArrayOf(0, 0, 0, 1, 101)
        val dummyNonKey = byteArrayOf(0, 0, 0, 1, 65)
        
        pipeline.onAccessUnit(dummyKey, 10000L, true)
        val countAfterFirstKey = callbackCount
        assertTrue("First keyframe should produce output", countAfterFirstKey > 0)
        
        pipeline.reset()
        
        pipeline.onCodecConfig(byteArrayOf(0, 0, 0, 1, 103, 0, 0, 0, 1, 104))
        
        // After reset, non-keyframe should be dropped
        pipeline.onAccessUnit(dummyNonKey, 20000L, false)
        assertEquals("Non-keyframe after reset should not produce output", countAfterFirstKey, callbackCount)
        
        // Next keyframe should be accepted
        pipeline.onAccessUnit(dummyKey, 30000L, true)
        assertTrue("Second keyframe should produce output", callbackCount > countAfterFirstKey)
    }
}
