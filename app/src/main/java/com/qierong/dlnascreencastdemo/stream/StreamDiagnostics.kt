package com.qierong.dlnascreencastdemo.stream

internal class PtsDeltaStats {
    private var count = 0L
    private var total = 0L
    private var min = Long.MAX_VALUE
    private var max = Long.MIN_VALUE

    fun record(currentPtsUs: Long, lastPtsUs: Long) {
        if (lastPtsUs == -1L) return
        val delta = currentPtsUs - lastPtsUs
        count++
        total += delta
        min = minOf(min, delta)
        max = maxOf(max, delta)
    }

    fun reset() {
        count = 0L
        total = 0L
        min = Long.MAX_VALUE
        max = Long.MIN_VALUE
    }

    fun minOrNull(): Long? = if (count == 0L) null else min

    fun maxOrNull(): Long? = if (count == 0L) null else max

    fun averageOrNull(): Long? = if (count == 0L) null else total / count
}

internal data class StreamPipelineDiagnosticSnapshot(
    val includeAudio: Boolean,
    val waitingForKeyFrame: Boolean,
    val encodedVideoFrameCount: Long,
    val publishedVideoFrameCount: Long,
    val droppedVideoFrameCount: Long,
    val receivedAudioFrameCount: Long,
    val publishedAudioFrameCount: Long,
    val droppedAudioFrameCount: Long,
    val audioOffsetRejectedCount: Long,
    val videoPtsDeltaUsMin: Long?,
    val videoPtsDeltaUsMax: Long?,
    val videoPtsDeltaUsAvg: Long?,
    val audioPtsDeltaUsMin: Long?,
    val audioPtsDeltaUsMax: Long?,
    val audioPtsDeltaUsAvg: Long?,
    val lastVideoPtsUs: Long,
    val lastAudioPtsUs: Long,
) {
    fun toLogLine(): String =
        "includeAudio=$includeAudio, waitingForKeyFrame=$waitingForKeyFrame, " +
            "encodedVideoFrameCount=$encodedVideoFrameCount, " +
            "publishedVideoFrameCount=$publishedVideoFrameCount, " +
            "droppedVideoFrameCount=$droppedVideoFrameCount, " +
            "receivedAudioFrameCount=$receivedAudioFrameCount, " +
            "publishedAudioFrameCount=$publishedAudioFrameCount, " +
            "droppedAudioFrameCount=$droppedAudioFrameCount, " +
            "audioOffsetRejectedCount=$audioOffsetRejectedCount, " +
            "videoPtsDeltaUsMin=${videoPtsDeltaUsMin ?: "N/A"}, " +
            "videoPtsDeltaUsMax=${videoPtsDeltaUsMax ?: "N/A"}, " +
            "videoPtsDeltaUsAvg=${videoPtsDeltaUsAvg ?: "N/A"}, " +
            "audioPtsDeltaUsMin=${audioPtsDeltaUsMin ?: "N/A"}, " +
            "audioPtsDeltaUsMax=${audioPtsDeltaUsMax ?: "N/A"}, " +
            "audioPtsDeltaUsAvg=${audioPtsDeltaUsAvg ?: "N/A"}, " +
            "lastVideoPtsUs=$lastVideoPtsUs, lastAudioPtsUs=$lastAudioPtsUs"
}

internal data class LocalStreamServerDiagnosticSnapshot(
    val isRunning: Boolean,
    val publishedChunkCount: Long,
    val currentSessionCount: Int,
    val pendingSocketCount: Int,
    val pendingPacketCount: Int,
    val maxSessionPendingPacketCount: Int,
    val sessionWriteFailureCount: Long,
    val replayChunkCount: Int,
    val replayBytes: Int,
) {
    fun toLogLine(): String =
        "isRunning=$isRunning, publishedChunkCount=$publishedChunkCount, " +
            "currentSessionCount=$currentSessionCount, pendingSocketCount=$pendingSocketCount, " +
            "pendingPacketCount=$pendingPacketCount, " +
            "maxSessionPendingPacketCount=$maxSessionPendingPacketCount, " +
            "sessionWriteFailureCount=$sessionWriteFailureCount, " +
            "replayChunkCount=$replayChunkCount, replayBytes=$replayBytes"
}
