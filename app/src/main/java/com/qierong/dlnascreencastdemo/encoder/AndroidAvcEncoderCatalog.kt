package com.qierong.dlnascreencastdemo.encoder

import android.media.MediaCodecInfo
import android.media.MediaCodecList

class AndroidAvcEncoderCatalog {
    fun listCapabilities(): List<AvcEncoderCapabilities> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filter(MediaCodecInfo::isEncoder)
            .filter { info ->
                info.supportedTypes.any { type ->
                    type.equals(EncoderConfig.MIME_TYPE_AVC, ignoreCase = true)
                }
            }
            .mapNotNull { info ->
                runCatching {
                    AndroidAvcEncoderCapabilities(
                        codecName = info.name,
                        codecCapabilities = info.getCapabilitiesForType(EncoderConfig.MIME_TYPE_AVC),
                    )
                }.getOrNull()
            }
            .filter { capabilities -> capabilities.supportsSurfaceInput }
            .toList()
}

private class AndroidAvcEncoderCapabilities(
    override val codecName: String,
    codecCapabilities: MediaCodecInfo.CodecCapabilities,
) : AvcEncoderCapabilities {
    private val videoCapabilities = requireNotNull(codecCapabilities.videoCapabilities)
    private val encoderCapabilities = requireNotNull(codecCapabilities.encoderCapabilities)
    val supportsSurfaceInput: Boolean =
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in codecCapabilities.colorFormats

    override val bitrateRange: IntRange =
        videoCapabilities.bitrateRange.lower..videoCapabilities.bitrateRange.upper
    override val widthAlignment: Int = videoCapabilities.widthAlignment
    override val heightAlignment: Int = videoCapabilities.heightAlignment
    override val supportsCbr: Boolean = encoderCapabilities.isBitrateModeSupported(
        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
    )

    override fun areSizeAndRateSupported(width: Int, height: Int, frameRate: Double): Boolean =
        videoCapabilities.areSizeAndRateSupported(width, height, frameRate)
}
