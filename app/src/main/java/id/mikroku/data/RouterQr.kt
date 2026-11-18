package id.mikroku.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.net.URLEncoder

/** Hasil urai QR router: cukup untuk mengisi form masuk. Password TIDAK pernah ikut. */
data class ParsedRouterQr(
    val name: String,
    val host: String,
    val port: Int?,
    val username: String,
    val useSsl: Boolean?
)

/**
 * QR tambah router ala MikroKu.
 *
 * Format kita: mikroku://add?name=..&host=..&port=..&user=..&ssl=1
 * Format asing yang diterima: winbox://host[:port]/user (tautan app resmi).
 * Password tidak pernah dimasukkan ke QR — selalu diketik manual.
 */
object RouterQr {

    fun buildContent(profile: RouterProfile): String {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        return "mikroku://add?name=${enc(profile.name)}&host=${enc(profile.host)}" +
            "&port=${profile.port}&user=${enc(profile.username)}" +
            "&ssl=${if (profile.useSsl) "1" else "0"}"
    }

    /** Urai isi QR/teks tempel. Null bila format tidak dikenali. */
    fun parse(raw: String): ParsedRouterQr? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("mikroku://add", ignoreCase = true)) {
            val query = text.substringAfter('?', "")
            if (query.isBlank()) return null
            val params = query.split('&').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null
                else part.substring(0, idx) to
                    URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            }.toMap()
            val host = params["host"]?.trim().orEmpty()
            if (host.isEmpty()) return null
            return ParsedRouterQr(
                name = params["name"]?.trim().orEmpty(),
                host = host,
                port = params["port"]?.toIntOrNull(),
                username = params["user"]?.trim().orEmpty(),
                useSsl = when (params["ssl"]) {
                    "1" -> true
                    "0" -> false
                    else -> null
                }
            )
        }
        if (text.startsWith("winbox://", ignoreCase = true)) {
            // winbox://host[:port]/user — user adalah segmen terakhir.
            val rest = text.substring("winbox://".length).trim().trim('/')
            if (rest.isEmpty()) return null
            val slash = rest.lastIndexOf('/')
            val hostPart = if (slash > 0) rest.substring(0, slash) else rest
            val userPart = if (slash > 0) rest.substring(slash + 1) else ""
            var host = hostPart
            var port: Int? = null
            val colon = hostPart.lastIndexOf(':')
            if (colon > 0) {
                hostPart.substring(colon + 1).toIntOrNull()?.let {
                    port = it
                    host = hostPart.substring(0, colon)
                }
            }
            if (host.isBlank()) return null
            return ParsedRouterQr(
                name = "",
                host = host,
                port = port,
                username = userPart.trim(),
                useSsl = null
            )
        }
        return null
    }

    /** Gambar QR (Bitmap hitam-putih) dari isi teks profil. */
    fun qrBitmap(profile: RouterProfile, size: Int = 560): Bitmap {
        val matrix = QRCodeWriter().encode(
            buildContent(profile), BarcodeFormat.QR_CODE, size, size
        )
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    /** Baca QR dari gambar yang dipilih user (galeri). Null bila tidak terbaca. */
    fun decodeFromImage(context: Context, uri: Uri): String? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            MultiFormatReader().decode(binary).text
        } catch (e: Exception) {
            null
        }
    }
}
