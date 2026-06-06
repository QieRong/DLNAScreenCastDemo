package com.qierong.dlnascreencastdemo.stream

import com.qierong.dlnascreencastdemo.encoder.EncodedVideoOutputSink

class MpegTsStreamPipeline(
    private val includeAudio: Boolean = false,
    private val publish: (ByteArray, Boolean) -> Unit,
) : EncodedVideoOutputSink {
    private var normalizer = AvcAnnexBNormalizer()
    private var muxer = MpegTsMuxer(includeAudio = includeAudio)
    private var waitingForKeyFrame = true
    private var videoBaseTimeUs: Long? = null
    private var audioBaseTimeUs: Long? = null

    @Synchronized
    override fun onOutputFormat(csd0: ByteArray?, csd1: ByteArray?) {
        normalizer.updateCodecSpecificData(csd0, csd1)
    }

    @Synchronized
    override fun onCodecConfig(data: ByteArray) {
        normalizer.updateCodecSpecificData(data, null)
    }

    @Synchronized
    override fun onAccessUnit(
        data: ByteArray,
        presentationTimeUs: Long,
        isKeyFrame: Boolean,
    ) {
        val annexB = normalizer.normalizeForStreaming(data, isKeyFrame) ?: return
        if (waitingForKeyFrame && !isKeyFrame) return
        publish(
            muxer.muxVideoAccessUnit(
                annexB = annexB,
                presentationTimeUs = normalizeVideoTime(presentationTimeUs),
                isKeyFrame = isKeyFrame,
            ),
            isKeyFrame,
        )
        waitingForKeyFrame = false
    }

    /**
     * 将一帧 ADTS 封装的 AAC 音频送入 TS 封装并发布。
     *
     * 音频帧与视频帧独立送入，本阶段不做复杂的音视频重排序队列。
     * 即使音频异常也不会影响视频 pipeline：异常由调用方捕获处理。
     *
     * @param data ADTS 头 + raw AAC ES 的完整帧
     * @param presentationTimeUs 以微秒为单位的 PTS
     */
    @Synchronized
    fun onAudioAccessUnit(data: ByteArray, presentationTimeUs: Long): Boolean {
        if (!includeAudio) return false
        if (waitingForKeyFrame) return false  // 等待视频关键帧后才开始推送，保持播放端同步
        val tsPackets = muxer.muxAudioAccessUnit(data, normalizeAudioTime(presentationTimeUs))
        publish(tsPackets, false)
        return true
    }

    @Synchronized
    fun reset() {
        normalizer = AvcAnnexBNormalizer()
        muxer = MpegTsMuxer(includeAudio = includeAudio)
        waitingForKeyFrame = true
        videoBaseTimeUs = null
        audioBaseTimeUs = null
    }

    private fun normalizeVideoTime(presentationTimeUs: Long): Long =
        normalizeStreamTime(
            presentationTimeUs = presentationTimeUs,
            currentBase = videoBaseTimeUs,
            updateBase = { videoBaseTimeUs = it },
        )

    private fun normalizeAudioTime(presentationTimeUs: Long): Long =
        normalizeStreamTime(
            presentationTimeUs = presentationTimeUs,
            currentBase = audioBaseTimeUs,
            updateBase = { audioBaseTimeUs = it },
        )

    private fun normalizeStreamTime(
        presentationTimeUs: Long,
        currentBase: Long?,
        updateBase: (Long) -> Unit,
    ): Long {
        val base = currentBase ?: presentationTimeUs.also(updateBase)
        return (presentationTimeUs - base).coerceAtLeast(0L)
    }
}
