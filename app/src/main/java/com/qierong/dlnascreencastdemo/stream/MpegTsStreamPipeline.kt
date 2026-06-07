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
    private var lastSummaryLogTimeMs = 0L
    private var encodedVideoFrameCount = 0L
    private var publishedVideoFrameCount = 0L
    private var droppedVideoFrameCount = 0L
    private var receivedAudioFrameCount = 0L
    private var publishedAudioFrameCount = 0L
    private var droppedAudioFrameCount = 0L
    private var audioOffsetRejectedCount = 0L
    private val videoPtsDeltaStats = PtsDeltaStats()
    private val audioPtsDeltaStats = PtsDeltaStats()

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
            encodedVideoFrameCount++
            val annexB = normalizer.normalizeForStreaming(data, isKeyFrame) ?: run {
                droppedVideoFrameCount++
                return@synchronized null
            }
            if (waitingForKeyFrame && !isKeyFrame) {
                droppedVideoFrameCount++
                return@synchronized null
            }
            
            if (globalBaseTimeUs == -1L && isKeyFrame) {
                globalBaseTimeUs = presentationTimeUs
            }
            
            if (globalBaseTimeUs == -1L) return@synchronized null // Should not happen due to above check
            
            val relativePts = (presentationTimeUs - globalBaseTimeUs).coerceAtLeast(0L)
            val monotonicPts = ensureMonotonicUs(relativePts, lastVideoPtsUs, "Video")
            if (monotonicPts == -1L) {
                droppedVideoFrameCount++
                return@synchronized null
            }
            
            videoPtsDeltaStats.record(monotonicPts, lastVideoPtsUs)
            lastVideoPtsUs = monotonicPts
            publishedVideoFrameCount++
            
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
        logSummaryIfNeeded()
    }

    fun onAudioAccessUnit(data: ByteArray, presentationTimeUs: Long): Boolean {
        if (!includeAudio) return false
        
        var rejectedAudioOffsetUs: Long? = null
        val tsPackets = synchronized(pipelineLock) {
            receivedAudioFrameCount++
            if (waitingForKeyFrame) {
                droppedAudioFrameCount++
                return@synchronized null
            }
            if (globalBaseTimeUs == -1L) {
                droppedAudioFrameCount++
                return@synchronized null
            }
            
            val audioOffsetUs = presentationTimeUs - globalBaseTimeUs
            if (audioOffsetUs < -MAX_AUDIO_INITIAL_OFFSET_US) {
                audioOffsetRejectedCount++
                droppedAudioFrameCount++
                rejectedAudioOffsetUs = audioOffsetUs
                return@synchronized null
            }
            val relativePts = (presentationTimeUs - globalBaseTimeUs).coerceAtLeast(0L)
            val monotonicPts = ensureMonotonicUs(relativePts, lastAudioPtsUs, "Audio")
            if (monotonicPts == -1L) {
                droppedAudioFrameCount++
                return@synchronized null
            }
            
            audioPtsDeltaStats.record(monotonicPts, lastAudioPtsUs)
            lastAudioPtsUs = monotonicPts
            publishedAudioFrameCount++
            muxer.muxAudioAccessUnit(data, monotonicPts)
        }
        
        rejectedAudioOffsetUs?.let(::logAudioOffsetRejection)
        if (tsPackets != null) {
            publish(tsPackets, false)
            logSummaryIfNeeded()
            return true
        }
        logSummaryIfNeeded()
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
            lastSummaryLogTimeMs = 0L
            encodedVideoFrameCount = 0L
            publishedVideoFrameCount = 0L
            droppedVideoFrameCount = 0L
            receivedAudioFrameCount = 0L
            publishedAudioFrameCount = 0L
            droppedAudioFrameCount = 0L
            audioOffsetRejectedCount = 0L
            videoPtsDeltaStats.reset()
            audioPtsDeltaStats.reset()
        }
    }

    internal fun ensureMonotonicUs(currentPtsUs: Long, lastPtsUs: Long, track: String): Long {
        if (lastPtsUs == -1L) return currentPtsUs
        if (currentPtsUs > lastPtsUs) return currentPtsUs
        
        val diff = lastPtsUs - currentPtsUs
        val now = System.currentTimeMillis()
        
        if (diff > 1_000_000L) { // > 1 second jump
            if (now - lastLogTimeMs > 5000 || dropLogCount < 5) {
                Log.w(TAG, "[$track] Large PTS backwards jump detected. Dropping frame. Diff: ${diff}us")
                lastLogTimeMs = now
                dropLogCount++
            }
            return -1L
        }
        
        if (now - lastLogTimeMs > 5000 || clampLogCount < 5) {
            Log.w(TAG, "[$track] PTS non-monotonic (diff: ${diff}us). Clamping to lastPts + 12us.")
            lastLogTimeMs = now
            clampLogCount++
        }
        return lastPtsUs + 12L
    }

    internal fun diagnosticSnapshot(): StreamPipelineDiagnosticSnapshot =
        synchronized(pipelineLock) {
            diagnosticSnapshotLocked()
        }

    private fun logSummaryIfNeeded() {
        val snapshot = synchronized(pipelineLock) {
            val now = System.currentTimeMillis()
            if (now - lastSummaryLogTimeMs < SUMMARY_LOG_INTERVAL_MS) {
                null
            } else {
                lastSummaryLogTimeMs = now
                diagnosticSnapshotLocked()
            }
        } ?: return
        Log.i(TAG, "pipeline diagnostics: ${snapshot.toLogLine()}")
    }

    private fun logAudioOffsetRejection(audioOffsetUs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastLogTimeMs > SUMMARY_LOG_INTERVAL_MS || audioOffsetRejectedCount <= INITIAL_LOG_LIMIT) {
            Log.w(
                TAG,
                "[Audio] initial offset rejected: ${audioOffsetUs}us, " +
                    "limit=${MAX_AUDIO_INITIAL_OFFSET_US}us",
            )
            lastLogTimeMs = now
        }
    }

    private fun diagnosticSnapshotLocked(): StreamPipelineDiagnosticSnapshot =
        StreamPipelineDiagnosticSnapshot(
            includeAudio = includeAudio,
            waitingForKeyFrame = waitingForKeyFrame,
            encodedVideoFrameCount = encodedVideoFrameCount,
            publishedVideoFrameCount = publishedVideoFrameCount,
            droppedVideoFrameCount = droppedVideoFrameCount,
            receivedAudioFrameCount = receivedAudioFrameCount,
            publishedAudioFrameCount = publishedAudioFrameCount,
            droppedAudioFrameCount = droppedAudioFrameCount,
            audioOffsetRejectedCount = audioOffsetRejectedCount,
            videoPtsDeltaUsMin = videoPtsDeltaStats.minOrNull(),
            videoPtsDeltaUsMax = videoPtsDeltaStats.maxOrNull(),
            videoPtsDeltaUsAvg = videoPtsDeltaStats.averageOrNull(),
            audioPtsDeltaUsMin = audioPtsDeltaStats.minOrNull(),
            audioPtsDeltaUsMax = audioPtsDeltaStats.maxOrNull(),
            audioPtsDeltaUsAvg = audioPtsDeltaStats.averageOrNull(),
            lastVideoPtsUs = lastVideoPtsUs,
            lastAudioPtsUs = lastAudioPtsUs,
        )

    companion object {
        private const val TAG = "StreamPipeline"
        private const val SUMMARY_LOG_INTERVAL_MS = 5_000L
        private const val INITIAL_LOG_LIMIT = 5
        private const val MAX_AUDIO_INITIAL_OFFSET_US = 2_000_000L
    }
}
