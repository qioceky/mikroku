package id.mikroku.api

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Codec protokol API biner MikroTik (RouterOS).
 *
 * Komunikasi berbasis "sentence": rangkaian "word" yang diakhiri word
 * berpanjang 0. Tiap word diawali panjang berkode variabel:
 *  - <= 0x7F       : 1 byte
 *  - <= 0x3FFF     : 2 byte (penanda 0x8000)
 *  - <= 0x1FFFFF   : 3 byte (penanda 0xC00000)
 *  - <= 0xFFFFFFF  : 4 byte (penanda 0xE0000000)
 *  - sisanya       : 5 byte (0xF0 + 4 byte panjang)
 * Sumber: dokumentasi resmi MikroTik "API" (help.mikrotik.com).
 */
class ApiCodec(input: InputStream, output: OutputStream) {

    private val inp = BufferedInputStream(input, 8192)
    private val out = BufferedOutputStream(output, 8192)

    companion object {
        /** Batas ukuran satu word dari server — anti hang/OOM. */
        const val MAX_WORD_BYTES = 1_048_576
    }

    fun writeSentence(words: List<String>) {
        for (w in words) writeWord(w)
        writeWord("")
        out.flush()
    }

    private fun writeWord(word: String) {
        val bytes = word.toByteArray(Charsets.UTF_8)
        writeLength(bytes.size)
        out.write(bytes)
    }

    private fun writeLength(len: Int) {
        when {
            len < 0x80 -> out.write(len)
            len < 0x4000 -> {
                val v = len or 0x8000
                out.write((v shr 8) and 0xFF)
                out.write(v and 0xFF)
            }
            len < 0x200000 -> {
                val v = len or 0xC00000
                out.write((v shr 16) and 0xFF)
                out.write((v shr 8) and 0xFF)
                out.write(v and 0xFF)
            }
            len < 0x10000000 -> {
                val v = len.toLong() or 0xE0000000L
                out.write(((v shr 24) and 0xFF).toInt())
                out.write(((v shr 16) and 0xFF).toInt())
                out.write(((v shr 8) and 0xFF).toInt())
                out.write((v and 0xFF).toInt())
            }
            else -> {
                out.write(0xF0)
                out.write((len shr 24) and 0xFF)
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
    }

    /** Membaca satu sentence; daftar kosong berarti koneksi ditutup router. */
    fun readSentence(): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val w = readWord()
            if (w.isEmpty()) break
            words.add(w)
        }
        return words
    }

    private fun readWord(): String {
        val len = readLength()
        if (len == 0) return ""
        if (len < 0 || len > MAX_WORD_BYTES) {
            throw ApiException("Ukuran word dari router tidak wajar: $len byte")
        }
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = inp.read(buf, off, len - off)
            if (n < 0) throw EOFException("Koneksi terputus di tengah word")
            off += n
        }
        return String(buf, Charsets.UTF_8)
    }

    private fun readLength(): Int {
        val first = inp.read()
        if (first < 0) throw EOFException("Koneksi terputus oleh router")
        return when {
            first and 0x80 == 0 -> first
            first and 0xC0 == 0x80 -> ((first and 0x3F) shl 8) or readByte()
            first and 0xE0 == 0xC0 -> ((first and 0x1F) shl 16) or (readByte() shl 8) or readByte()
            first and 0xF0 == 0xE0 -> ((first and 0x0F) shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
            first and 0xF8 == 0xF0 -> (readByte() shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
            else -> throw ApiException("Byte kontrol tidak dikenal dari router: 0x${first.toString(16)}")
        }
    }

    private fun readByte(): Int {
        val b = inp.read()
        if (b < 0) throw EOFException("Koneksi terputus oleh router")
        return b
    }
}

class ApiException(message: String) : Exception(message)

/** Balasan !trap dari router (perintah ditolak). */
class ApiTrapException(val trapMessage: String, val category: String?) :
    Exception(trapMessage.ifBlank { "Perintah ditolak router (!trap)" })
