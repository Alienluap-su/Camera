package com.example.samcam

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "press"
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) Color(0x55FFFFFF) else if (selected) Color(0x38FFFFFF) else Color.Transparent,
        animationSpec = tween(150), label = "pillbg"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun IconBox(onClick: () -> Unit, selected: Boolean, content: @Composable BoxScope.() -> Unit) {
    PressBox(onClick, Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp), selected, content)
}

@Composable
fun ModePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (selected) Color(0x40FFFFFF) else if (pressed) Color(0x22FFFFFF) else Color.Transparent,
        animationSpec = tween(220), label = "modebg"
    )
    val tc by animateColorAsState(
        targetValue = if (selected) YELLOW else Color(0xCCFFFFFF),
        animationSpec = tween(220), label = "modetxt"
    )
    Box(
        modifier
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = tc, fontSize = 14.sp, maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ModeStrip(pos: () -> Float, order: List<Mode>, itemW: Dp, onTap: (Mode) -> Unit) {
    val itemPx = with(LocalDensity.current) { itemW.toPx() }
    Box(Modifier.fillMaxWidth().height(46.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(itemW).height(34.dp).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)))
        order.forEachIndexed { i, m ->
            Box(
                Modifier.width(itemW).height(46.dp)
                    .graphicsLayer {
                        val d = i - pos()
                        translationX = d * itemPx
                        val dist = abs(d)
                        alpha = (1.2f - dist * 0.34f).coerceIn(0f, 1f)
                        val sc = 0.9f + 0.1f * (1f - dist).coerceIn(0f, 1f)
                        scaleX = sc
                        scaleY = sc
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap(m) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    m.label, color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 1,
                    modifier = Modifier.graphicsLayer { alpha = 1f - (1f - abs(i - pos())).coerceIn(0f, 1f) }
                )
                Text(
                    m.label, color = YELLOW, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer { alpha = (1f - abs(i - pos())).coerceIn(0f, 1f) }
                )
            }
        }
    }
}

@Composable
fun ShutterButton(video: Boolean, capturing: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val ringScale by animateFloatAsState(targetValue = if (pressed) 0.93f else 1f, label = "ring")
    val inner by animateDpAsState(
        targetValue = if (capturing) 30.dp else if (pressed) 52.dp else 62.dp,
        animationSpec = tween(160), label = "inner"
    )
    val corner by animateDpAsState(
        targetValue = if (capturing) 7.dp else 40.dp,
        animationSpec = tween(200), label = "corner"
    )
    val col by animateColorAsState(
        targetValue = if (video || capturing) Color(0xFFE53935) else Color.White,
        animationSpec = tween(200), label = "shutcol"
    )
    Box(
        Modifier.size(78.dp)
            .graphicsLayer { scaleX = ringScale; scaleY = ringScale }
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(col))
    }
}

@Composable
fun GearIcon() {
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension / 2
        val ctr = center
        val sw = r * 0.16f
        drawCircle(Color.White, radius = r * 0.36f, center = ctr, style = Stroke(sw))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = Math.cos(a).toFloat()
            val dy = Math.sin(a).toFloat()
            drawLine(
                Color.White,
                Offset(ctr.x + dx * r * 0.58f, ctr.y + dy * r * 0.58f),
                Offset(ctr.x + dx * r * 0.88f, ctr.y + dy * r * 0.88f),
                strokeWidth = sw * 1.6f, cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FlashIcon(state: Int) {
    val col = if (state == 0) Color.White else YELLOW
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val p = Path()
            p.moveTo(w * 0.60f, h * 0.05f)
            p.lineTo(w * 0.22f, h * 0.56f)
            p.lineTo(w * 0.46f, h * 0.56f)
            p.lineTo(w * 0.38f, h * 0.95f)
            p.lineTo(w * 0.78f, h * 0.40f)
            p.lineTo(w * 0.54f, h * 0.40f)
            p.close()
            drawPath(p, col, style = Stroke(w * 0.08f))
            if (state == 0) {
                drawLine(Color.White, Offset(w * 0.1f, h * 0.9f), Offset(w * 0.9f, h * 0.1f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            }
        }
        if (state == 1) {
            Text("A", color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
fun TimerIcon(seconds: Int) {
    val col = if (seconds == 0) Color.White else YELLOW
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            val ctr = Offset(size.width / 2, size.height * 0.56f)
            val sw = r * 0.14f
            drawCircle(col, radius = r * 0.78f, center = ctr, style = Stroke(sw))
            drawLine(col, Offset(size.width * 0.4f, size.height * 0.06f), Offset(size.width * 0.6f, size.height * 0.06f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, ctr, Offset(ctr.x + r * 0.3f, ctr.y - r * 0.35f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        if (seconds > 0) {
            Text("$seconds", color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
fun LiveIcon(active: Boolean) {
    val col = if (active) YELLOW else Color.White
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension / 2
        drawCircle(col, radius = r * 0.88f, style = Stroke(r * 0.12f))
        drawCircle(col, radius = r * 0.55f, style = Stroke(r * 0.12f))
        drawCircle(col, radius = r * 0.2f)
        if (!active) {
            drawLine(Color.White, Offset(size.width * 0.1f, size.height * 0.9f), Offset(size.width * 0.9f, size.height * 0.1f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun TextIcon(label: String, active: Boolean) {
    val col = if (active) YELLOW else Color.White
    Box(Modifier.border(1.5.dp, col, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FlipIcon() {
    Canvas(Modifier.size(28.dp)) {
        val r = size.minDimension * 0.34f
        val ctr = center
        val sw = size.minDimension * 0.08f
        val head = size.minDimension * 0.16f
        val topLeft = Offset(ctr.x - r, ctr.y - r)
        val arcSize = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        for (k in 0..1) {
            val start = 200f + 180f * k
            val sweep = 130f
            drawArc(
                color = Color.White, startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            val a = Math.toRadians((start + sweep).toDouble())
            val nx = Math.cos(a).toFloat()
            val ny = Math.sin(a).toFloat()
            val px = ctr.x + r * nx
            val py = ctr.y + r * ny
            val tx = -ny
            val ty = nx
            val path = Path()
            path.moveTo(px + tx * head, py + ty * head)
            path.lineTo(px + nx * head * 0.7f, py + ny * head * 0.7f)
            path.lineTo(px - nx * head * 0.7f, py - ny * head * 0.7f)
            path.close()
            drawPath(path, Color.White)
        }
    }
}

@Composable
fun ProSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).height(28.dp))
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, color = YELLOW, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
}

@Composable
fun OptionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = YELLOW, fontSize = 15.sp)
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(sub, color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = YELLOW)
        )
    }
}

@Composable
fun TextRow(title: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = YELLOW, fontSize = 16.sp),
            cursorBrush = SolidColor(YELLOW),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)).padding(12.dp)
        )
    }
}

@Composable
fun RecTimer(elapsed: () -> Long, paused: Boolean) {
    Text(fmtTime(elapsed()), color = if (paused) YELLOW else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun QrPill(label: String, onTap: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(YELLOW).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Color.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 210.dp).clickable { onTap() }.padding(vertical = 11.dp)
        )
        PressBox(onClose, Modifier.size(34.dp)) { Text("✕", color = Color.Black, fontSize = 15.sp) }
    }
}

@Composable
fun InfoPill(text: String, good: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x77000000)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (good) Color(0xFF4CAF50) else YELLOW))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BigZoom(zoom: () -> Float, visible: Boolean) {
    if (visible) {
        Text(fmtZoom(zoom()), color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun ZoomBar(
    zoom: () -> Float,
    target: () -> Float,
    minZ: Float,
    maxZ: Float,
    tele: Float?,
    rulerShown: Boolean,
    onPreset: (Float) -> Unit,
    onSet: (Float) -> Unit,
    onBump: () -> Unit
) {
    val presets = remember(minZ, maxZ, tele) {
        val l = ArrayList<Float>()
        if (minZ < 0.95f) l.add(minZ)
        l.add(1f)
        if (tele != null && tele <= maxZ) l.add(tele) else if (maxZ >= 1.95f) l.add(2f)
        if (maxZ >= 4.9f) l.add(5f)
        if (maxZ >= 9.9f) l.add(10f)
        l
    }
    val z = zoom()
    val lnSpan = ln(maxZ) - ln(minZ)
    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
        if (rulerShown) {
            ZoomRuler(zoom, target, minZ, maxZ, onSet, onBump)
        } else {
            var active = 0
            presets.forEachIndexed { i, p -> if (z >= p - 0.02f) active = i }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .pointerInput(minZ, maxZ) {
                        detectHorizontalDragGestures(onDragStart = { onBump() }) { change, dx ->
                            change.consume()
                            onSet(exp(ln(target()) + dx * lnSpan / 420f).coerceIn(minZ, maxZ))
                            onBump()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presets.forEachIndexed { i, p ->
                    val sel = i == active
                    PressBox({ onPreset(p) }, Modifier.defaultMinSize(minWidth = 38.dp, minHeight = 38.dp), sel) {
                        Text(
                            if (sel) fmtZoom(z) else fmtPreset(p),
                            color = if (sel) YELLOW else Color.White, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomRuler(zoom: () -> Float, target: () -> Float, minZ: Float, maxZ: Float, onSet: (Float) -> Unit, onBump: () -> Unit) {
    val lnMin = ln(minZ)
    val lnMax = ln(maxZ)
    var widthPx by remember { mutableFloatStateOf(1f) }
    Canvas(
        Modifier.fillMaxWidth(0.7f).height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(minZ, maxZ) {
                detectHorizontalDragGestures(onDragStart = { onBump() }) { change, dx ->
                    change.consume()
                    val next = ln(target()) + dx / widthPx * (lnMax - lnMin) * 1.4f
                    onSet(exp(next).coerceIn(minZ, maxZ))
                    onBump()
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val n = 41
        for (i in 0 until n) {
            val x = w * i / (n - 1)
            val major = i % 5 == 0
            val half = if (major) 14f else 9f
            drawLine(
                Color.White.copy(alpha = if (major) 0.95f else 0.6f),
                Offset(x, h * 0.5f - half), Offset(x, h * 0.5f + half), strokeWidth = 3f
            )
        }
        val f = ((ln(zoom()) - lnMin) / (lnMax - lnMin)).coerceIn(0f, 1f)
        drawLine(YELLOW, Offset(w * f, h * 0.5f - 22f), Offset(w * f, h * 0.5f + 22f), strokeWidth = 7f, cap = StrokeCap.Round)
    }
}

@Composable
fun LogoMark(dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = this.size.width
        drawRoundRect(Color(0xFFF4F4F4), cornerRadius = CornerRadius(w * 0.24f))
        val c = Offset(w * 0.5f, w * 0.5f)
        drawCircle(Color(0xFF2A2A2C), radius = w * 0.34f, center = c)
        drawCircle(Color(0xFF111113), radius = w * 0.29f, center = c)
        drawCircle(Color(0xFF3A3A3E), radius = w * 0.21f, center = c)
        drawCircle(Color(0xFF050506), radius = w * 0.185f, center = c)
        drawCircle(Color(0xFF2E3A4A), radius = w * 0.09f, center = c)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.032f, center = Offset(w * 0.44f, w * 0.43f))
        drawCircle(Color(0xFFE53935), radius = w * 0.046f, center = Offset(w * 0.8f, w * 0.2f))
    }
}

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(110.dp)
            Spacer(Modifier.height(16.dp))
            Text("Camera", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            "developed by Alienluap", color = Color(0x88FFFFFF), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        )
    }
}
