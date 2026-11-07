package com.example.samcam

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PanoArrow(right: Boolean, color: Color) {
    Canvas(Modifier.size(34.dp, 30.dp)) {
        val w = size.width
        val h = size.height
        val p = Path()
        if (right) {
            p.moveTo(0f, h * 0.1f)
            p.lineTo(w, h * 0.5f)
            p.lineTo(0f, h * 0.9f)
            p.lineTo(w * 0.28f, h * 0.5f)
        } else {
            p.moveTo(w, h * 0.1f)
            p.lineTo(0f, h * 0.5f)
            p.lineTo(w, h * 0.9f)
            p.lineTo(w * 0.72f, h * 0.5f)
        }
        p.close()
        drawPath(p, color)
    }
}

@Composable
fun PanoGuide(capturing: Boolean, right: Boolean, thumb: Bitmap?, drift: () -> Float, msg: String) {
    val dd = abs(drift())
    val lineColor = when {
        !capturing -> Color(0xCCFFFFFF)
        dd < 2.5f -> Color(0xFF4CAF50)
        dd < 5f -> YELLOW
        else -> Color(0xFFFF5252)
    }
    val text = when {
        !capturing -> "Press the shutter and slowly move\ntap to change direction"
        msg.isNotEmpty() -> msg
        else -> "Move slowly"
    }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 10.dp)
                .offset { IntOffset(0, (drift() * 7f).coerceIn(-140f, 140f).roundToInt()) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val box: @Composable () -> Unit = {
                Box(
                    Modifier.width(80.dp).height(54.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.5.dp, Color.White, RoundedCornerShape(5.dp))
                        .background(Color(0x66000000))
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(), contentDescription = null,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (right) {
                box()
                Spacer(Modifier.width(8.dp))
                PanoArrow(true, Color.White)
                Box(Modifier.weight(1f).height(2.dp).background(lineColor))
            } else {
                Box(Modifier.weight(1f).height(2.dp).background(lineColor))
                PanoArrow(false, Color.White)
                Spacer(Modifier.width(8.dp))
                box()
            }
        }
        Text(
            text,
            modifier = Modifier.align(Alignment.Center).offset(y = 64.dp).padding(horizontal = 24.dp),
            style = TextStyle(
                color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)
            )
        )
    }
}
