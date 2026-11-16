package id.mikroku.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStream
import java.io.OutputStream
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
 */
object SftpRepo {

    private const val SSH_PORT = 22

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

    private fun connect(profile: RouterProfile): SSHClient {
        val ssh = SSHClient()
        ssh.connectTimeout = 12_000
        ssh.timeout = 30_000
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(profile.host.substringBefore(":"), SSH_PORT)
        ssh.authPassword(profile.username, profile.password)
        return ssh
    }

    /** Unduh [remoteName] dari penyimpanan router ke [out]. */
    suspend fun download(profile: RouterProfile, remoteName: String, out: OutputStream) {
        withContext(Dispatchers.IO) {
            val ssh = connect(profile)
            try {
                ssh.newSFTPClient().use { sftp ->
                    val remote = sftp.open(remoteName, EnumSet.of(OpenMode.READ))
                    try {
                        val buffer = ByteArray(32 * 1024)
                        var offset = 0L
                        while (true) {
                            val n = remote.read(offset, buffer, 0, buffer.size)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            offset += n
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
    suspend fun upload(profile: RouterProfile, input: InputStream, remoteName: String) {
        withContext(Dispatchers.IO) {
            val ssh = connect(profile)
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
}
