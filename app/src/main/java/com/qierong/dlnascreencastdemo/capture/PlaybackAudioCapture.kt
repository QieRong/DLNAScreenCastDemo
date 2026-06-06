package com.qierong.dlnascreencastdemo.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.qierong.dlnascreencastdemo.encoder.AudioEncoderConfig
import com.qierong.dlnascreencastdemo.encoder.PcmAudioLevel
import com.qierong.dlnascreencastdemo.encoder.PcmAudioLevelAnalyzer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal class PlaybackAudioCapture(
    private val mediaProjection: MediaProjection,
    private val onPcmFrame: (ByteArray, Long, PcmAudioLevel) -> Unit,
    private val onStatus: (PlaybackAudioStatus) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var workerThread: Thread? = null
    private var capturedSamples = 0L
    private var firstPcmReported = false
    private var nonSilentReported = false
    private var silentFrames = 0

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onStatus(PlaybackAudioStatus.ApiUnsupported)
            return false
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioEncoderConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            onError("AudioRecord 最小缓冲区不可用：$minBufferSize")
            return false
        }
        val bufferSize = max(minBufferSize * 2, AudioEncoderConfig.BYTES_PER_FRAME * 4)
        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioEncoderConfig.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val record = runCatching {
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()
        }.getOrElse { exception ->
            onError("AudioRecord 初始化失败：${exception.message}")
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError("AudioRecord 初始化失败：state=${record.state}")
            return false
        }
        runCatching { record.startRecording() }.onFailure { exception ->
            record.release()
            onError("AudioRecord 启动失败：${exception.message}")
            return false
        }
        audioRecord = record
        running.set(true)
        onStatus(PlaybackAudioStatus.AudioRecordStarted)
        workerThread = Thread(::readLoop, "PlaybackAudioCapture").apply { start() }
        Log.i(TAG, "AudioPlaybackCapture 已启动：usage=MEDIA/GAME/UNKNOWN")
        return true
    }

    fun stop() {
        val wasRunning = running.getAndSet(false)
        if (wasRunning) workerThread?.interrupt()
        workerThread = null
        releaseRecord()
        Log.i(TAG, "AudioPlaybackCapture 已停止")
    }

    private fun readLoop() {
        val record = audioRecord ?: return
        val buffer = ByteArray(AudioEncoderConfig.BYTES_PER_FRAME)
        while (running.get()) {
            val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            when {
                read > 0 -> handlePcm(buffer.copyOf(read))
                read == 0 -> Unit
                else -> {
                    onError("AudioRecord 读取失败：$read")
                    running.set(false)
                }
            }
        }
    }

    private fun handlePcm(pcm: ByteArray) {
        val presentationTimeUs = capturedSamples * 1_000_000L / AudioEncoderConfig.SAMPLE_RATE
        capturedSamples += pcm.size / BYTES_PER_SAMPLE / AudioEncoderConfig.CHANNEL_COUNT
        if (!firstPcmReported) {
            firstPcmReported = true
            Log.i(TAG, "first PCM frame，pts=$presentationTimeUs μs")
            onStatus(PlaybackAudioStatus.FirstPcmFrame)
        }
        val level = PcmAudioLevelAnalyzer.analyzePcm16LittleEndian(pcm)
        if (!level.isProbablySilent) {
            silentFrames = 0
            if (!nonSilentReported) {
                nonSilentReported = true
                Log.i(TAG, "PCM peak/RMS 非静音：peak=${level.peak}，rms=${level.rms}")
                onStatus(PlaybackAudioStatus.NonSilentPcm(level.peak, level.rms))
            }
        } else if (!nonSilentReported) {
            silentFrames++
            if (silentFrames == SILENT_FRAME_REPORT_THRESHOLD) {
                Log.w(TAG, "PCM 能量接近 0，可能静音或目标 App 不允许捕获")
                onStatus(PlaybackAudioStatus.PossiblySilentOrDisallowed)
            }
        }
        onPcmFrame(pcm, presentationTimeUs, level)
    }

    private fun releaseRecord() {
        val record = audioRecord ?: return
        runCatching { record.stop() }
        runCatching { record.release() }
        audioRecord = null
    }

    companion object {
        private const val TAG = "AudioCapture"
        private const val BYTES_PER_SAMPLE = 2
        private const val SILENT_FRAME_REPORT_THRESHOLD = 96
    }
}
