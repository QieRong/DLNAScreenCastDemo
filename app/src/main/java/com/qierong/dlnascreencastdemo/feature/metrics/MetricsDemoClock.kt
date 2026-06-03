package com.qierong.dlnascreencastdemo.feature.metrics

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

enum class MetricsDemoPage {
    Latency,
    DynamicBitrate,
}

@Composable
internal fun rememberDemoClock(): DemoClock {
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var clock by remember {
        mutableStateOf(
            DemoClock(
                wallTimeMs = System.currentTimeMillis(),
                elapsedMs = 0L,
            ),
        )
    }
    LaunchedEffect(startedAt) {
        while (true) {
            withFrameMillis {
                clock = DemoClock(
                    wallTimeMs = System.currentTimeMillis(),
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                )
            }
        }
    }
    return clock
}

@Composable
internal fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

internal data class DemoClock(
    val wallTimeMs: Long,
    val elapsedMs: Long,
)

internal fun formatStopwatch(elapsedMs: Long): String {
    val minutes = elapsedMs / 60_000
    val seconds = (elapsedMs / 1_000) % 60
    val millis = elapsedMs % 1_000
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal val latencyColors = listOf(
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFFFDD835),
)

internal val bitrateColors = listOf(
    Color(0xFFFF1744),
    Color(0xFF00E676),
    Color(0xFF2979FF),
    Color(0xFFFFEA00),
    Color(0xFFD500F9),
    Color(0xFFFF9100),
)
