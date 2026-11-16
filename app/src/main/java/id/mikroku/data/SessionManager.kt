package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Memegang satu sesi API ke router dan menyembuhkannya sendiri.
 *
 * Koneksi API bisa putus kapan saja (router reboot, WiFi terputus sesaat,
 * service API di router restart). Bila sebuah perintah gagal karena
 * koneksi mati (IOException: broken pipe, connection reset, EOF, timeout),
 * sesi lama dibuang, dibuat sesi baru (konek + login ulang), lalu perintah
 * diulang sekali. Bila pengulangan juga gagal, sesi ditandai kosong agar
 * polling berikutnya mencoba menyambung dari awal — jadi layar monitoring
 * pulih sendiri tanpa pengguna keluar-masuk.
 *
 * ApiTrapException TIDAK memicu sambung ulang: itu penolakan sah dari
 * router dan koneksinya masih sehat.
 */
class SessionManager(
    private val profile: RouterProfile,
    private val onFingerprintLearned: (String) -> Unit = {}
) : AutoCloseable {

    private val mutex = Mutex()
    private var session: RouterSession? = null
    private var learnedFingerprint: String? = profile.certFingerprint

    /** Klien aktif; menyambung dulu bila belum ada sesi. */
    suspend fun client(): MikrotikClient = ensure().client

    /**
     * Jalankan [block] dengan klien aktif. Pada kegagalan koneksi,
     * sambung ulang dan ulangi [block] satu kali.
     */
    suspend fun <T> run(block: suspend (MikrotikClient) -> T): T {
        val first = ensure()
        return try {
            block(first.client)
        } catch (e: IOException) {
            val second = reconnect()
            try {
                block(second.client)
            } catch (e2: IOException) {
                invalidate()
                throw e2
            }
        }
    }

    private suspend fun ensure(): RouterSession = mutex.withLock {
        session ?: openLocked()
    }

    private suspend fun reconnect(): RouterSession = mutex.withLock {
        closeLocked()
        openLocked()
    }

    private suspend fun invalidate() = mutex.withLock {
        closeLocked()
    }

    private fun closeLocked() {
        session?.let { try { it.close() } catch (_: Exception) {} }
        session = null
    }

    private suspend fun openLocked(): RouterSession {
        // Pakai sidik jari yang dipelajari sesi sebelumnya agar pemeriksaan
        // TOFU pada sambung ulang tetap menegakkan sidik jari yang sama.
        val fp = learnedFingerprint
        val effective = if (fp != null && profile.certFingerprint == null)
            profile.copy(certFingerprint = fp) else profile
        val s = MonitorRepo.openSession(effective)
        val newFp = s.certFingerprint
        if (newFp != null && learnedFingerprint == null) {
            learnedFingerprint = newFp
            onFingerprintLearned(newFp)
        }
        session = s
        return s
    }

    override fun close() {
        session?.let { try { it.close() } catch (_: Exception) {} }
        session = null
    }
}
