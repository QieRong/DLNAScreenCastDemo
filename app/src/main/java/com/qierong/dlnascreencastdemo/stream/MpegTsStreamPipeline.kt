package com.qierong.dlnascreencastdemo.stream

import android.util.Log
import com.qierong.dlnascreencastdemo.encoder.EncodedVideoOutputSink

class MpegTsStreamPipeline(
    private val includeAudio: Boolean = false,
    private val publish: (ByteArray, Boolean) -> Unit,
) : EncodedVideoOutputSink {
    private val pipelineLock = Any()
    
    private var normalizer = AvcAnnexBNormalizer()
    private var muxer = MpegTsMuxer(includeAudio = includeAudio)
    private var waitingForKeyFrame = true
    
    private var globalBaseTimeUs: Long = -1L
    private var lastVideoPtsUs: Long = -1L
    private var lastAudioPtsUs: Long = -1L
    
    private var clampLogCount = 0
    private var dropLogCount = 0
    private var lastLogTimeMs = 0L

    override fun onOutputFormat(csd0: ByteArray?, csd1: ByteArray?) {
        synchronized(pipelineLock) {
            normalizer.updateCodecSpecificData(csd0, csd1)
        }
    }

    override fun onCodecConfig(data: ByteArray) {
        synchronized(pipelineLock) {
            normalizer.updateCodecSpecificData(data, null)
        }
    }

    override fun onAccessUnit(
        data: ByteArray,
        presentationTimeUs: Long,
        isKeyFrame: Boolean,
    ) {
        val tsPackets = synchronized(pipelineLock) {
            val annexB = normalizer.normalizeForStreaming(data, isKeyFrame) ?: return@synchronized null
            if (waitingForKeyFrame && !isKeyFrame) return@synchronized null
            
            if (globalBaseTimeUs == -1L && isKeyFrame) {
                globalBaseTimeUs = presentationTimeUs
            }
            
            if (globalBaseTimeUs == -1L) return@synchronized null // Should not happen due to above check
            
            val relativePts = (presentationTimeUs - globalBaseTimeUs).coerceAtLeast(0L)
            val monotonicPts = ensureMonotonicUs(relativePts, lastVideoPtsUs, "Video")
            if (monotonicPts == -1L) return@synchronized null // Drop frame
            
            lastVideoPtsUs = monotonicPts
            
            val packets = muxer.muxVideoAccessUnit(
                annexB = annexB,
                presentationTimeUs = monotonicPts,
                isKeyFrame = isKeyFrame,
            )
            waitingForKeyFrame = false
            packets
        }
        
        if (tsPackets != null) {
            publish(tsPackets, isKeyFrame)
        }
    }

    fun onAudioAccessUnit(data: ByteArray, presentationTimeUs: Long): Boolean {
        if (!includeAudio) return false
        
        val tsPackets = synchronized(pipelineLock) {
            if (waitingForKeyFrame) return@synchronized null
            if (globalBaseTimeUs == -1L) return@synchronized null // Wait for video keyframe base time
            
            val relativePts = (presentationTimeUs - globalBaseTimeUs).coerceAtLeast(0L)
            val monotonicPts = ensureMonotonicUs(relativePts, lastAudioPtsUs, "Audio")
            if (monotonicPts == -1L) return@synchronized null // Drop frame
            
            lastAudioPtsUs = monotonicPts
            muxer.muxAudioAccessUnit(data, monotonicPts)
        }
        
        if (tsPackets != null) {
            publish(tsPackets, false)
            return true
        }
        return false
    }

    fun reset() {
        synchronized(pipelineLock) {
            normalizer = AvcAnnexBNormalizer()
            muxer = MpegTsMuxer(includeAudio = includeAudio)
            waitingForKeyFrame = true
            globalBaseTimeUs = -1L
            lastVideoPtsUs = -1L
            lastAudioPtsUs = -1L
            clampLogCount = 0
            dropLogCount = 0
        }
    }

    internal fun ensureMonotonicUs(currentPtsUs: Long, lastPtsUs: Long, track: String): Long {
        if (lastPtsUs == -1L) return currentPtsUs
        if (currentPtsUs > lastPtsUs) return currentPtsUs
        
        val diff = lastPtsUs - currentPtsUs
        val now = System.currentTimeMillis()
        
        if (diff > 1_000_000L) { // > 1 second jump
            if (now - lastLogTimeMs > 5000 || dropLogCount < 5) {
                Log.w("StreamPipeline", "[$track] Large PTS backwards jump detected. Dropping frame. Diff: ${diff}us")
                lastLogTimeMs = now
                dropLogCount++
            }
            return -1L
        }
        
        if (now - lastLogTimeMs > 5000 || clampLogCount < 5) {
            Log.w("StreamPipeline", "[$track] PTS non-monotonic (diff: ${diff}us). Clamping to lastPts + 12us.")
            lastLogTimeMs = now
            clampLogCount++
        }
        return lastPtsUs + 12L
    }
}
