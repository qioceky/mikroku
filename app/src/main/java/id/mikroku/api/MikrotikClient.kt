package id.mikroku.api

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Klien API biner MikroTik untuk RouterOS v6 (dan v7).
 *
 * - Port 8728: TCP polos. Port 8729: api-ssl (TLS).
 * - Untuk TLS, sertifikat self-signed diverifikasi dengan pola TOFU:
 *   sidik jari SHA-256 sertifikat dibandingkan dengan yang tersimpan di
 *   profil router; koneksi pertama (belum ada sidik jari) menerimanya dan
 *   mengembalikan sidik jarinya agar disimpan aplikasi.
 * - Login: metode post-6.43 (satu sentence /login berisi name+password),
 *   dengan fallback otomatis ke challenge-response MD5 pre-6.43 bila
 *   router membalas =ret= (challenge).
 */
class MikrotikClient private constructor(
    private val socket: Socket,
    private val codec: ApiCodec,
    /** Sidik jari sertifikat server (hanya mode SSL), format hex uppercase tanpa titik dua. */
    val serverCertFingerprint: String?
) : Closeable {

    companion object {
        const val DEFAULT_PORT_PLAIN = 8728
        const val DEFAULT_PORT_SSL = 8729
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000

        fun connect(
            host: String,
            port: Int,
            useSsl: Boolean,
            expectedFingerprint: String?
        ): MikrotikClient {
            var capturedFingerprint: String? = null
            val socket: Socket = if (useSsl) {
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        val cert = chain?.firstOrNull() ?: throw ApiException("Router tidak mengirim sertifikat")
                        capturedFingerprint = sha256Hex(cert.encoded)
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(trustManager), SecureRandom())
                val s = ctx.socketFactory.createSocket() as SSLSocket
                // Kompatibilitas TLS RouterOS:
                // - Cipher: RouterOS v6 hanya menawarkan cipher lama (keluarga
                //   RSA) yang tidak selalu aktif bawaan di Android baru —
                //   nyalakan semua cipher yang didukung soket.
                // - Protokol: kunci ke TLS 1.2 SAJA. API-SSL RouterOS (v6 & v7)
                //   berbicara TLS 1.2, dan tumpukan TLS v6 menolak ClientHello
                //   Android baru yang menawarkan TLS 1.3 (handshake_failure).
                try { s.enabledCipherSuites = s.supportedCipherSuites } catch (_: Exception) {}
                try {
                    s.enabledProtocols =
                        if (s.supportedProtocols.contains("TLSv1.2")) arrayOf("TLSv1.2")
                        else s.supportedProtocols
                } catch (_: Exception) {}
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                s.startHandshake()
                val fp = capturedFingerprint
                    ?: throw ApiException("Sidik jari sertifikat tidak terbaca")
                if (expectedFingerprint != null && !expectedFingerprint.equals(fp, ignoreCase = true)) {
                    try { s.close() } catch (_: Exception) {}
                    throw ApiException(
                        "Sidik jari sertifikat router BERUBAH (tersimpan: $expectedFingerprint, sekarang: $fp). " +
                            "Koneksi ditolak demi keamanan — bila router memang diganti/diinstal ulang, hapus sidik jari di profil router."
                    )
                }
                s
            } else {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                s
            }
            return MikrotikClient(socket, ApiCodec(socket.getInputStream(), socket.getOutputStream()), capturedFingerprint)
        }

        private fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02X".format(it) }
        }
    }

    /** Login dua mode; melempar ApiTrapException/ApiException bila gagal. */
    fun login(username: String, password: String) {
        // Coba metode post-6.43 dulu.
        codec.writeSentence(listOf("/login", "=name=$username", "=password=$password"))
        val first = readReply()
        if (first.done) {
            val challenge = first.doneAttrs["ret"]
            if (challenge == null) return // login modern berhasil
            // Router lama: balasan !done berisi challenge -> metode MD5.
            val response = md5ChallengeResponse(password, challenge)
            codec.writeSentence(listOf("/login", "=name=$username", "=response=$response"))
            val second = readReply()
            if (second.done) return
            throw ApiException("Login gagal tanpa keterangan dari router")
        }
        throw ApiException("Login gagal tanpa keterangan dari router")
    }

    private fun md5ChallengeResponse(password: String, challengeHex: String): String {
        val challenge = ByteArray(challengeHex.length / 2) { i ->
            challengeHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val md = MessageDigest.getInstance("MD5")
        md.update(0)
        md.update(password.toByteArray(Charsets.UTF_8))
        md.update(challenge)
        val hash = md.digest()
        return "00" + hash.joinToString("") { "%02x".format(it) }
    }

    private class Reply(
        val rows: List<Map<String, String>>,
        val done: Boolean,
        val doneAttrs: Map<String, String>
    )

    private fun readReply(): Reply {
        val rows = mutableListOf<Map<String, String>>()
        var trap: ApiTrapException? = null
        while (true) {
            val sentence = codec.readSentence()
            if (sentence.isEmpty()) throw ApiException("Koneksi ditutup router")
            val type = sentence[0]
            val attrs = parseAttrs(sentence.drop(1))
            when (type) {
                "!re" -> rows.add(attrs)
                "!done" -> {
                    // RouterOS selalu menutup balasan !trap dengan !done.
                    // Trap baru dilempar SETELAH !done terbaca, supaya aliran
                    // balasan tetap sinkron untuk perintah berikutnya.
                    trap?.let { throw it }
                    return Reply(rows, true, attrs)
                }
                "!trap" -> if (trap == null) {
                    trap = ApiTrapException(
                        attrs["message"] ?: "Perintah ditolak router",
                        attrs["category"]
                    )
                }
                "!fatal" -> throw ApiException(attrs["message"] ?: "Koneksi ditolak router (!fatal) — periksa user/password dan policy API")
                else -> { /* abaikan sentence tak dikenal */ }
            }
        }
    }

    private fun parseAttrs(words: List<String>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (w in words) {
            if (w.startsWith("=")) {
                val rest = w.substring(1)
                val idx = rest.indexOf('=')
                if (idx >= 0) map[rest.substring(0, idx)] = rest.substring(idx + 1)
                else map[rest] = ""
            }
        }
        return map
    }

    /**
     * Jalankan perintah dan kembalikan baris !re sebagai daftar map atribut.
     * Contoh: run("/system/resource/print")
     */
    fun run(vararg words: String): List<Map<String, String>> {
        codec.writeSentence(words.toList())
        return readReply().rows
    }

    /** Perintah yang tidak mengembalikan data (mis. /system/reboot). */
    fun runCommand(vararg words: String) {
        codec.writeSentence(words.toList())
        readReply()
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
