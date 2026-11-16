package id.mikroku.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Ikon Font Awesome Free Solid 6.5.2 — font dibundel lokal di
 * assets/fonts/fa-solid-900.ttf (offline, tanpa CDN).
 * Lisensi Font Awesome Free: ikon CC BY 4.0 — atribusi ada di layar Tentang.
 * Semua kode glif diverifikasi dari cmap font itu sendiri.
 */
object Fa {
    const val BOLT = "⚡"
    const val GLOBE = "\uF0AC"
    const val SERVER = ""
    const val TICKET = ""
    const val ROUTE = ""
    const val ARROWS_LR = ""
    const val SHIELD = ""
    const val ADDRESS_BOOK = ""
    const val WIFI = ""
    const val PLUG = ""
    const val LIST_OL = ""
    const val WRENCH = ""
    const val GEAR = "⚙"
    const val TOWER = ""
    const val SITEMAP = ""
    const val TAGS = ""
    const val BOOK = ""
    const val DATABASE = ""
    const val CLOUD_DOWN = ""
    const val BELL = ""
    const val EYE = ""
    const val TERMINAL = ""
    const val DIAGRAM = ""
    const val GAUGE = ""
    const val GRIP = ""
    const val NETWORK_WIRED = ""
    const val BOX = ""
    const val POWER = "⏻"
    const val FLOPPY = ""
}

private object FaTypeface {
    @Volatile
    private var cached: Typeface? = null

    fun get(context: Context): Typeface =
        cached ?: synchronized(this) {
            cached ?: Typeface.createFromAsset(
                context.applicationContext.assets, "fonts/fa-solid-900.ttf"
            ).also { cached = it }
        }
}

/** Satu glif Font Awesome sebagai teks ber-font FA. */
@Composable
fun FaIcon(
    icon: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    color: Color = Ink
) {
    val context = LocalContext.current
    val typeface = remember { FaTypeface.get(context) }
    Text(
        icon,
        modifier = modifier,
        fontFamily = FontFamily(typeface),
        fontSize = size,
        color = color
    )
}
