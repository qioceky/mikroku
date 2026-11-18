package id.mikroku.data

import id.mikroku.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.security.Security
import java.util.EnumSet

/**
 * Transfer file router lewat SFTP (service SSH RouterOS, port 22).
 *
 * API biner RouterOS tidak bisa memindahkan ISI file (hanya metadata),
 * jadi unduh/unggah berjalan lewat SSH dengan kredensial yang sama
 * seperti login API. Syarat di router: service ssh aktif dan grup user
 * memiliki policy `ssh` (untuk login) + `ftp` (untuk transfer file) —
 * grup `full` bawaan sudah mencakup keduanya.
 *
 * Kunci host SSH diverifikasi dengan pola TOFU (trust on first use),
 * sama seperti sidik jari sertifikat API-SSL: koneksi pertama mencatat
 * sidik jarinya ke profil router; koneksi berikutnya DITOLAK bila sidik
 * jari berubah — password router tidak pernah dikirim ke server SSH
 * yang tidak dikenal.
 */
object SftpRepo {

    private const val SSH_PORT = 22

    /** Batas ukuran satu file unduhan — file router wajarnya jauh di bawah ini. */
    const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024

    init {
        // sshj di Android memerlukan BouncyCastle versi lengkap dari library;
        // provider "BC" bawaan Android adalah versi terpangkas.
        try {
            if (Security.getProvider("BC")?.javaClass != BouncyCastleProvider::class.java) {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
        } catch (e: Exception) {
            // Abaikan — koneksi tetap dicoba dengan provider yang ada.
        }
    }

    private fun connect(
        profile: RouterProfile,
        onFingerprintLearned: (String) -> Unit = {}
    ): SSHClient {
        val ssh = SSHClient()
        ssh.connectTimeout = 12_000
        ssh.timeout = 30_000
        val expected = profile.sshFingerprint
        var mismatchMessage: String? = null
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fp = SecurityUtils.getFingerprint(key)
                return when {
                    expected == null -> {
                        onFingerprintLearned(fp)
                        true
                    }
                    fp.equals(expected, ignoreCase = true) -> true
                    else -> {
                        mismatchMessage =
                            "Sidik jari SSH router BERUBAH (tersimpan: $expected, sekarang: $fp). " +
                                "Transfer ditolak demi keamanan — bila router memang diganti atau " +
                                "diinstal ulang, hapus sidik jari SSH di profil router (menu Ubah)."
                        false
                    }
                }
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
                emptyList()
        })
        try {
            ssh.connect(profile.host.substringBefore(":"), SSH_PORT)
            ssh.authPassword(profile.username, profile.password)
        } catch (e: Exception) {
            try { ssh.disconnect() } catch (_: Exception) {}
            mismatchMessage?.let { throw ApiException(it) }
            throw e
        }
        return ssh
    }

    /**
     * Unduh [remoteName] dari penyimpanan router ke [out].
     * [onProgress] dipanggil berkala dengan (byte terunduh, total byte).
     */
    suspend fun download(
        profile: RouterProfile,
        remoteName: String,
        out: OutputStream,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onFingerprintLearned: (String) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            val ssh = connect(profile, onFingerprintLearned)
            try {
                ssh.newSFTPClient().use { sftp ->
                    val total = try { sftp.lstat(remoteName).size } catch (_: Exception) { -1L }
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw ApiException(
                            "File '$remoteName' terlalu besar untuk diunduh " +
                                "(${total / (1024 * 1024)} MB; batas 200 MB)."
                        )
                    }
                    val remote = sftp.open(remoteName, EnumSet.of(OpenMode.READ))
                    try {
                        val buffer = ByteArray(32 * 1024)
                        var offset = 0L
                        while (true) {
                            val n = remote.read(offset, buffer, 0, buffer.size)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            offset += n
                            if (offset > MAX_DOWNLOAD_BYTES) {
                                throw ApiException("Unduhan melewati batas 200 MB — dibatalkan.")
                            }
                            onProgress(offset, total)
                        }
                        out.flush()
                    } finally {
                        remote.close()
                    }
                }
            } finally {
                ssh.disconnect()
            }
        }
    }

    /** Unggah isi [input] ke router sebagai [remoteName] (menimpa bila sudah ada). */
    suspend fun upload(
        profile: RouterProfile,
        input: InputStream,
        remoteName: String,
        onFingerprintLearned: (String) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            val ssh = connect(profile, onFingerprintLearned)
            try {
                ssh.newSFTPClient().use { sftp ->
                    val remote = sftp.open(
                        remoteName,
                        EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
                    )
                    try {
                        val buffer = ByteArray(32 * 1024)
                        var offset = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            remote.write(offset, buffer, 0, n)
                            offset += n
                        }
                    } finally {
                        remote.close()
                    }
                }
            } finally {
                ssh.disconnect()
            }
        }
    }

    /** Hapus file [remoteName] dari penyimpanan router. */
    suspend fun delete(
        profile: RouterProfile,
        remoteName: String,
        onFingerprintLearned: (String) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            val ssh = connect(profile, onFingerprintLearned)
            try {
                ssh.newSFTPClient().use { sftp -> sftp.rm(remoteName) }
            } finally {
                ssh.disconnect()
            }
        }
    }
}
