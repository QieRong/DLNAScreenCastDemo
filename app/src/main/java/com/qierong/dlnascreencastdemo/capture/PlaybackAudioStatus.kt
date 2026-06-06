package com.qierong.dlnascreencastdemo.capture

sealed interface PlaybackAudioStatus {
    val label: String
    val detail: String

    data object PermissionNotGranted : PlaybackAudioStatus {
        override val label = "权限未授权"
        override val detail = "RECORD_AUDIO 未授权，本次会话降级为 video-only。"
    }

    data object ApiUnsupported : PlaybackAudioStatus {
        override val label = "API 不支持"
        override val detail = "AudioPlaybackCapture 仅支持 Android 10 / API 29+。"
    }

    data object AudioRecordStarted : PlaybackAudioStatus {
        override val label = "AudioRecord 已启动"
        override val detail = "已使用 MediaProjection 创建 AudioPlaybackCapture AudioRecord。"
    }

    data object FirstPcmFrame : PlaybackAudioStatus {
        override val label = "first PCM frame"
        override val detail = "已从 AudioRecord 读取到第一帧 PCM。"
    }

    data class NonSilentPcm(
        val peak: Int,
        val rms: Int,
    ) : PlaybackAudioStatus {
        override val label = "PCM peak/RMS 非静音"
        override val detail = "已读到非静音 PCM：peak=$peak，rms=$rms。"
    }

    data object FirstAacFrame : PlaybackAudioStatus {
        override val label = "first AAC frame"
        override val detail = "真实播放音 PCM 已送入 AAC 编码器并输出首帧。"
    }

    data object FirstAudioTsPacket : PlaybackAudioStatus {
        override val label = "first audio TS packet"
        override val detail = "首个 AAC 音频 access unit 已写入 MPEG-TS。"
    }

    data object PossiblySilentOrDisallowed : PlaybackAudioStatus {
        override val label = "可能静音 / 目标 App 不允许捕获"
        override val detail =
            "AudioRecord 正在运行，但 PCM 能量接近 0；目标 App 可能静音或禁止被 AudioPlaybackCapture 捕获。"
    }

    data class DegradedVideoOnly(
        val reason: String,
    ) : PlaybackAudioStatus {
        override val label = "已降级 video-only"
        override val detail = reason
    }
}
