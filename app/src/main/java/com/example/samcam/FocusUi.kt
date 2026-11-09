package com.example.samcam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ExposureHandle(
    cx: Float,
    cy: Float,
    areaW: Float,
    ev: Int,
    evMin: Int,
    evMax: Int,
    onEv: (Int) -> Unit,
    onTouch: () -> Unit
) {
    val density = LocalDensity.current
    val boxW = with(density) { 48.dp.toPx() }
    val lineH = with(density) { 150.dp.toPx() }
    val gap = with(density) { 46.dp.toPx() }
    val left = if (cx + gap + boxW / 2 > areaW) cx - gap - boxW / 2 else cx + gap - boxW / 2
    var evF by remember { mutableFloatStateOf(ev.toFloat()) }
    LaunchedEffect(ev) { if (abs(evF - ev) > 0.5f) evF = ev.toFloat() }
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), (cy - lineH / 2).roundToInt().coerceAtLeast(0)) }
            .size(48.dp, 150.dp)
            .pointerInput(evMin, evMax) {
                detectVerticalDragGestures(onDragStart = { onTouch() }) { change, dragY ->
                    change.consume()
                    val range = (evMax - evMin).coerceAtLeast(1)
                    evF = (evF - dragY * range / lineH).coerceIn(evMin.toFloat(), evMax.toFloat())
                    onEv(evF.roundToInt())
                    onTouch()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width / 2
            drawLine(
                YELLOW, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))
            )
            val frac = if (evMax > evMin) ((evF - evMin) / (evMax - evMin)).coerceIn(0f, 1f) else 0.5f
            val y = size.height * (1f - frac)
            drawCircle(YELLOW, radius = 8f, center = Offset(x, y))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val ux = Math.cos(a).toFloat()
                val uy = Math.sin(a).toFloat()
                drawLine(
                    YELLOW, Offset(x + ux * 13f, y + uy * 13f), Offset(x + ux * 19f, y + uy * 19f),
                    strokeWidth = 3f, cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun LockPill(onTap: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(YELLOW).clickable { onTap() }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text("AF/AE LOCK", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
