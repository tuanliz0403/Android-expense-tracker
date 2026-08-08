package com.example.spendtracker.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.spendtracker.domain.model.DailySpending
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun DailySpendingChart(days: List<DailySpending>, modifier: Modifier = Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var viewportStart by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val leftPadding = with(density) { 58.dp.toPx() }
    val rightPadding = with(density) { 12.dp.toPx() }
    val topPadding = with(density) { 16.dp.toPx() }
    val bottomPadding = with(density) { 36.dp.toPx() }
    val labelSize = with(density) { 11.dp.toPx() }
    val domain = (days.size - 1).coerceAtLeast(1).toFloat()
    val maximumZoom = domain.coerceIn(1f, 12f)

    LaunchedEffect(days.firstOrNull()?.date, days.lastOrNull()?.date) {
        zoom = 1f
        viewportStart = 0f
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pinch to zoom · drag to pan", style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = { zoom = 1f; viewportStart = 0f },
                enabled = zoom > 1.01f
            ) { Text("Reset view") }
        }

        val lineColor = MaterialTheme.colorScheme.primary
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val chartDescription = if (days.isEmpty()) {
            "No daily spending data"
        } else {
            "Daily spending line graph from ${days.first().date} to ${days.last().date}"
        }

        Canvas(
            Modifier.fillMaxWidth().height(340.dp)
                .semantics { contentDescription = chartDescription }
                .onSizeChanged { canvasWidth = it.width }
                .pointerInput(days.size, canvasWidth) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        if (days.size < 2) return@detectTransformGestures
                        val plotWidth = (canvasWidth - leftPadding - rightPadding).coerceAtLeast(1f)
                        val oldSpan = domain / zoom
                        val fraction = ((centroid.x - leftPadding) / plotWidth).coerceIn(0f, 1f)
                        val anchor = viewportStart + fraction * oldSpan
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, maximumZoom)
                        val newSpan = domain / newZoom
                        val maximumStart = (domain - newSpan).coerceAtLeast(0f)
                        viewportStart = (anchor - fraction * newSpan - (pan.x / plotWidth) * newSpan)
                            .coerceIn(0f, maximumStart)
                        zoom = newZoom
                    }
                }
        ) {
            if (days.isEmpty()) return@Canvas

            val plotLeft = leftPadding
            val plotRight = size.width - rightPadding
            val plotTop = topPadding
            val plotBottom = size.height - bottomPadding
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
            val visibleSpan = domain / zoom
            val visibleEnd = viewportStart + visibleSpan
            val firstIndex = floor(viewportStart).toInt().coerceIn(0, days.lastIndex)
            val lastIndex = ceil(visibleEnd).toInt().coerceIn(firstIndex, days.lastIndex)
            val visibleDays = days.subList(firstIndex, lastIndex + 1)
            val visibleMaximum = (visibleDays.maxOfOrNull { it.totalCents } ?: 0L).coerceAtLeast(1L)
            val yMaximum = visibleMaximum * 1.12f
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor.toArgb()
                textSize = labelSize
            }

            fun xFor(index: Int): Float = plotLeft + ((index - viewportStart) / visibleSpan) * plotWidth
            fun yFor(cents: Long): Float = plotBottom - (cents / yMaximum) * plotHeight

            repeat(4) { level ->
                val fraction = level / 3f
                val y = plotBottom - fraction * plotHeight
                drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), 1.dp.toPx())
                val value = (yMaximum * fraction).toLong()
                drawContext.canvas.nativeCanvas.drawText(compactAud(value), 0f, y + labelSize * 0.35f, textPaint)
            }

            val area = Path()
            val line = Path()
            for (index in firstIndex..lastIndex) {
                val x = xFor(index)
                val y = yFor(days[index].totalCents)
                if (index == firstIndex) {
                    line.moveTo(x, y)
                    area.moveTo(x, plotBottom)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(xFor(lastIndex), plotBottom)
            area.close()
            drawPath(area, lineColor.copy(alpha = 0.14f))
            drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx()))
            for (index in firstIndex..lastIndex) {
                drawCircle(lineColor, 4.dp.toPx(), Offset(xFor(index), yFor(days[index].totalCents)))
            }

            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("en-AU"))
            val tickIndexes = (0..3).map { tick ->
                (viewportStart + visibleSpan * tick / 3f).roundToInt().coerceIn(0, days.lastIndex)
            }.distinct()
            tickIndexes.forEach { index ->
                val label = days[index].date.format(formatter)
                val measured = textPaint.measureText(label)
                val rawX = xFor(index) - measured / 2f
                val labelX = rawX.coerceIn(plotLeft, plotRight - measured)
                drawContext.canvas.nativeCanvas.drawText(label, labelX, size.height - 8.dp.toPx(), textPaint)
            }
        }
    }
}

private fun compactAud(cents: Long): String {
    val dollars = cents / 100f
    return when {
        dollars >= 1_000f -> "$${"%.1f".format(Locale.US, dollars / 1_000f)}k"
        dollars >= 10f -> "$${dollars.roundToInt()}"
        else -> "$${"%.2f".format(Locale.US, dollars)}"
    }
}
