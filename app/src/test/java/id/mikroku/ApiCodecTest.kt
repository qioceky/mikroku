package id.mikroku

import id.mikroku.api.ApiCodec
import id.mikroku.api.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

/**
 * Uji JVM untuk codec protokol API biner MikroTik — bagian paling
 * mendasar MikroKu: bila encoding panjang word meleset, semua fitur di
 * atasnya rusak. Dijalankan CI pada setiap push (testDebugUnitTest).
 */
class ApiCodecTest {

    private fun encode(words: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        ApiCodec(ByteArrayInputStream(ByteArray(0)), out).writeSentence(words)
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): List<String> =
        ApiCodec(ByteArrayInputStream(bytes), ByteArrayOutputStream()).readSentence()

    private fun roundtrip(words: List<String>): List<String> = decode(encode(words))

    @Test
    fun kalimatSederhanaBolakBalik() {
        val words = listOf("/login", "=name=admin", "=password=rahasia")
        assertEquals(words, roundtrip(words))
    }

    @Test
    fun banyakWordDanKarakterKhas() {
        val words = listOf(
            "/ip/firewall/filter/print",
            "=.proplist=.id,comment",
            "?chain=forward",
            "=comment=halo dunia! @#\$%^&*()",
            "=unicode=✓ café — naïve"
        )
        assertEquals(words, roundtrip(words))
    }

    @Test
    fun batasPanjangWord() {
        // Ambang encoding variabel: 0x7F/0x80, 0x3FFF/0x4000, dan satu
        // ukuran menengah di wilayah 3 byte.
        for (len in listOf(1, 0x7F, 0x80, 0xFF, 0x3FFF, 0x4000, 0x1FFFF)) {
            val w = "x".repeat(len)
            assertEquals("panjang $len", listOf(w), roundtrip(listOf(w)))
        }
    }

    @Test
    fun wordKosongAdalahPenutup() {
        // Sentence diakhiri word panjang 0 — pembaca berhenti di sana dan
        // byte sesudahnya milik sentence berikutnya.
        val bytes = encode(listOf("/system/identity/print")) + encode(listOf("!done"))
        val first = ApiCodec(ByteArrayInputStream(bytes), ByteArrayOutputStream())
        assertEquals(listOf("/system/identity/print"), first.readSentence())
        assertEquals(listOf("!done"), first.readSentence())
    }

    @Test
    fun aliranTerpotongMelemparEof() {
        val full = encode(listOf("/system/resource/print", "=panjang=" + "y".repeat(500)))
        val truncated = full.copyOf(full.size - 100)
        assertThrows(EOFException::class.java) { decode(truncated) }
    }

    @Test
    fun wordRaksasaDitolak() {
        // Header panjang format 5 byte (0xF0 + 4 byte) untuk 2.000.000 byte —
        // melewati MAX_WORD_BYTES (1 MB) sehingga harus ditolak SEBELUM dibaca.
        val header = byteArrayOf(
            0xF0.toByte(),
            (2_000_000 shr 24).toByte(),
            (2_000_000 shr 16).toByte(),
            (2_000_000 shr 8).toByte(),
            (2_000_000).toByte()
        )
        assertThrows(ApiException::class.java) { decode(header) }
    }

    @Test
    fun byteKontrolTakDikenalDitolak() {
        // 0xF8 bukan penanda panjang yang sah.
        assertThrows(ApiException::class.java) { decode(byteArrayOf(0xF8.toByte(), 0, 0)) }
    }
}
