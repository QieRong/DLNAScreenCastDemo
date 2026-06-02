package com.qierong.dlnascreencastdemo.stream

import com.qierong.dlnascreencastdemo.encoder.EncodedVideoOutputSink

class MpegTsStreamPipeline(
    private val publish: (ByteArray, Boolean) -> Unit,
) : EncodedVideoOutputSink {
    private var normalizer = AvcAnnexBNormalizer()
    private var muxer = MpegTsMuxer()
    private var waitingForKeyFrame = true

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
        publish(muxer.muxVideoAccessUnit(annexB, presentationTimeUs, isKeyFrame), isKeyFrame)
        waitingForKeyFrame = false
    }

    @Synchronized
    fun reset() {
        normalizer = AvcAnnexBNormalizer()
        muxer = MpegTsMuxer()
        waitingForKeyFrame = true
    }
}
