package com.qierong.dlnascreencastdemo.feature.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicBitrateTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val clock = rememberDemoClock()
    val colorIndex = ((clock.elapsedMs / 250) % bitrateColors.size).toInt()
    val scrollOffset = -((clock.elapsedMs / 3) % 900).toInt()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "动态码率测试页") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "返回")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                bitrateColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (index == colorIndex) color else color.copy(alpha = 0.45f)),
                    )
                }
            }
            ScrollingText(offset = scrollOffset)
            Box(modifier = Modifier.weight(1f)) {
                MovingPatternCanvas(
                    elapsedMs = clock.elapsedMs,
                    colorIndex = colorIndex,
                    modifier = Modifier.fillMaxSize(),
                )
                DynamicOverlay(
                    wallTimeMs = clock.wallTimeMs,
                    elapsedMs = clock.elapsedMs,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Text(text = "返回首页")
                }
            }
        }
    }
}

@Composable
private fun ScrollingText(
    offset: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.inverseSurface),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "DLNAScreenCastDemo dynamic bitrate sample 1080P 8Mbps target  ".repeat(4),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset { IntOffset(offset, 0) },
        )
    }
}

@Composable
private fun DynamicOverlay(
    wallTimeMs: Long,
    elapsedMs: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "$wallTimeMs ms",
            color = Color.White,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "测试时长：${formatStopwatch(elapsedMs)}",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MovingPatternCanvas(
    elapsedMs: Long,
    colorIndex: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.background(Color.Black)) {
        val cell = 72.dp.toPx()
        val shift = ((elapsedMs / 5) % cell.toLong()).toFloat()
        var y = -cell
        var row = 0
        while (y < size.height + cell) {
            var x = -cell
            var column = 0
            while (x < size.width + cell) {
                val isBright = (row + column + colorIndex) % 2 == 0
                drawRect(
                    color = if (isBright) Color.White else bitrateColors[colorIndex],
                    topLeft = Offset(x + shift, y - shift),
                    size = Size(cell, cell),
                    alpha = if (isBright) 0.92f else 0.82f,
                )
                x += cell
                column += 1
            }
            y += cell
            row += 1
        }

        repeat(8) { index ->
            val squareSize = (36 + index * 9).dp.toPx()
            val speed = 2 + index
            val x = ((elapsedMs / speed) % (size.width + squareSize).toLong()).toFloat() -
                squareSize
            val yOffset = ((elapsedMs / (speed + 3)) % (size.height + squareSize).toLong())
                .toFloat() - squareSize
            drawRect(
                color = bitrateColors[(colorIndex + index) % bitrateColors.size],
                topLeft = Offset(x, yOffset),
                size = Size(squareSize, squareSize),
                alpha = 0.78f,
            )
        }
    }
}
