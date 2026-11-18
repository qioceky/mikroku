package id.mikroku.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Status mode gelap aktif. Diatur dari App berdasarkan pilihan tema user
 * (Terang/Gelap/Ikuti Sistem). Semua warna palet di bawah adalah getter
 * yang membaca status ini, jadi seluruh UI berbalik tema seketika.
 */
var mikroDark by mutableStateOf(false)

/** Pilihan tema user: 0 = Ikuti Sistem, 1 = Terang, 2 = Gelap. */
object ThemeState {
    var mode by mutableStateOf(0)
}

/** Palet soft neobrutalism MikroKu (sama dengan RDPfread Mobile & RuangKlon). */
val Paper: Color get() = if (mikroDark) Color(0xFF171611) else Color(0xFFFFFDF7)
val Ink: Color get() = if (mikroDark) Color(0xFFF4F0E4) else Color(0xFF161616)
val MikroYellow = Color(0xFFFFD02E)
val MikroGreen = Color(0xFF2FBF71)
val MikroRed = Color(0xFFFF5A5A)
val MikroBlue: Color get() = if (mikroDark) Color(0xFF2A3D55) else Color(0xFFDCEEFF)
val Muted: Color get() = if (mikroDark) Color(0xFFA49E8D) else Color(0xFF666660)
/** Isi kartu & tombol sekunder (putih di terang, abu hangat di gelap). */
val CardFill: Color get() = if (mikroDark) Color(0xFF23221B) else Color.White
/** Warna bayangan offset khas neobrutal. */
val Shadow: Color get() = if (mikroDark) Color(0xFF000000) else Color(0xFF161616)
/** Isi tombol nonaktif. */
val DisabledFill: Color get() = if (mikroDark) Color(0xFF3A382E) else Color(0xFFDDDDD5)

@Composable
fun MikroKuTheme(content: @Composable () -> Unit) {
    val scheme = if (mikroDark) {
        darkColorScheme(
            primary = MikroYellow,
            onPrimary = Color(0xFF161616),
            background = Paper,
            onBackground = Ink,
            surface = CardFill,
            onSurface = Ink,
            error = MikroRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = MikroYellow,
            onPrimary = Ink,
            background = Paper,
            onBackground = Ink,
            surface = CardFill,
            onSurface = Ink,
            error = MikroRed,
            onError = Color.White
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Grafik garis mini (sparkline) dari deretan nilai; digambar manual via Canvas. */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float? = null
) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        if (started) 1f else 0f, tween(700), label = "sparkProgress"
    )
    LaunchedEffect(Unit) { started = true }
    Canvas(modifier.fillMaxWidth().height(38.dp)) {
        clipRect(right = size.width * progress) {
        if (values.size < 2) return@Canvas
        val max = (maxValue ?: (values.maxOrNull() ?: 1f)).coerceAtLeast(1f)
        val pad = 2.dp.toPx()
        val stepX = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - pad - (v.coerceIn(0f, max) / max) * (size.height - pad * 2)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path()
        fill.addPath(line)
        fill.lineTo(size.width, size.height)
        fill.lineTo(0f, size.height)
        fill.close()
        drawPath(fill, color.copy(alpha = 0.22f))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** Kartu khas neobrutal: border tebal + bayangan offset keras. */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    fill: Color = CardFill,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        if (pressed && onClick != null) 3.dp else 0.dp, tween(90), label = "cardSink"
    )
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp - sink, y = 4.dp - sink)
                .clip(RoundedCornerShape(14.dp))
                .background(Shadow)
        )
        val inner = Modifier
            .fillMaxWidth()
            .offset(x = sink, y = sink)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(2.5.dp, Ink, RoundedCornerShape(14.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction, indication = null
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(12.dp)
        Column(inner, content = content)
    }
}

@Composable
fun NeoButton(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = MikroYellow,
    textColor: Color = Ink,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        if (pressed && enabled) 3.dp else 0.dp, tween(90), label = "btnSink"
    )
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp - sink, y = 4.dp - sink)
                .clip(RoundedCornerShape(12.dp))
                .background(Shadow)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .offset(x = sink, y = sink)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) fill else DisabledFill)
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interaction, indication = null
                        ) { onClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 11.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(text, fontWeight = FontWeight.Black, fontSize = 15.sp, color = textColor)
        }
    }
}

/** Tombol ikon persegi khas neobrutal — untuk navigasi (kembali, muat ulang, dll). */
@Composable
fun NeoIconButton(icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        if (pressed) 2.dp else 0.dp, tween(90), label = "iconSink"
    )
    Box(modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 3.dp - sink, y = 3.dp - sink)
                .clip(RoundedCornerShape(10.dp))
                .background(Shadow)
        )
        Box(
            Modifier
                .size(38.dp)
                .offset(x = sink, y = sink)
                .clip(RoundedCornerShape(10.dp))
                .background(CardFill)
                .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            FaIcon(icon, size = 15.sp)
        }
    }
}

@Composable
fun StatusDot(online: Boolean?, modifier: Modifier = Modifier) {
    val base = when (online) {
        true -> MikroGreen
        false -> MikroRed
        null -> Color(0xFFCCCCCC)
    }
    // Titik hijau berdenyut pelan tanda router hidup & terpantau.
    val pulseTransition = rememberInfiniteTransition(label = "dotPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "dotPulseValue"
    )
    val color = if (online == true) base.copy(alpha = pulse) else base
    Box(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(2.dp, Ink, RoundedCornerShape(50))
            .padding(4.5.dp)
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "${bytes} B" else String.format("%.1f %s", v, units[i])
}

fun formatRate(bytesPerSec: Double): String {
    if (bytesPerSec <= 0) return "0 Mbps"
    val mbps = bytesPerSec * 8 / 1_000_000.0
    return if (mbps >= 1) String.format("%.1f Mbps", mbps)
    else String.format("%.0f Kbps", bytesPerSec * 8 / 1_000.0)
}
