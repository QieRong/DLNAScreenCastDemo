package com.qierong.dlnascreencastdemo.feature.metrics

import com.qierong.dlnascreencastdemo.capture.CaptureConfig
import com.qierong.dlnascreencastdemo.capture.CaptureSessionInfo
import com.qierong.dlnascreencastdemo.capture.CaptureState
import com.qierong.dlnascreencastdemo.capture.PlaybackAudioStatus
import com.qierong.dlnascreencastdemo.encoder.ActiveEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.BitrateMode
import com.qierong.dlnascreencastdemo.encoder.EncoderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsStatusTest {
    @Test
    fun buildMetricStatusItems_idle_keepsUnverifiedBoundaries() {
        val items = buildMetricStatusItems(CaptureState.Idle)

        assertEquals("未实测", items.requireMetric("延迟").current)
        assertEquals("缺少 3 次外部录像读数", items.requireMetric("延迟").evidence)
        assertEquals("待开始采集", items.requireMetric("分辨率").current)
        assertEquals("动态样本待测", items.requireMetric("视频码率").current)
        assertEquals("需 30 秒动态页面 curl 样本", items.requireMetric("视频码率").evidence)
        assertEquals("待开始采集", items.requireMetric("音频").current)
        assertTrue(
            "音频证据说明应包含'ffprobe'",
            items.requireMetric("音频").evidence.contains("ffprobe"),
        )
        assertTrue(
            "音频证据说明应包含'PC/Kodi/ffplay'",
            items.requireMetric("音频").evidence.contains("PC/Kodi/ffplay"),
        )

    }

    @Test
    fun buildMetricStatusItems_capturing_usesActiveEncoderCanvas() {
        val items = buildMetricStatusItems(
            CaptureState.Capturing(
                CaptureSessionInfo(
                    sourceConfig = CaptureConfig(
                        width = 1200,
                        height = 2670,
                        densityDpi = 440,
                    ),
                    encoderConfig = ActiveEncoderConfig(
                        codecName = "test.avc.encoder",
                        config = EncoderConfig(
                            width = 1080,
                            height = 1920,
                            videoBitrate = 8_000_000,
                            bitrateMode = BitrateMode.CBR,
                        ),
                        isDegraded = false,
                    ),
                    streamUrl = "http://192.168.1.8:8080/live.ts",
                    audioStatus = PlaybackAudioStatus.FirstAudioTsPacket,
                ),
            ),
        )

        val resolution = items.requireMetric("分辨率")
        val audio = items.requireMetric("音频")

        assertEquals("1080 x 1920", resolution.current)
        assertTrue(resolution.evidence.contains("当前 CaptureState"))
        assertEquals("first audio TS packet", audio.current)
        assertTrue(audio.evidence.contains("首个 AAC 音频 access unit"))
    }

    @Test
    fun metricDisplayText_keepsTargetCurrentEvidenceLabels() {
        val displayText = MetricStatusItem(
            title = "视频码率",
            target = "8 Mbps",
            current = "动态样本待测",
            evidence = "需 30 秒动态页面 curl 样本",
        ).toDisplayText()

        assertTrue(displayText.contains("目标：8 Mbps"))
        assertTrue(displayText.contains("当前：动态样本待测"))
        assertTrue(displayText.contains("证据：需 30 秒动态页面 curl 样本"))
    }

    private fun List<MetricStatusItem>.requireMetric(title: String): MetricStatusItem =
        first { it.title == title }
}
