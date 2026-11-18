package id.mikroku.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Menyimpan daftar router sebagai JSON terenkripsi AES-256-GCM.
 * Kunci hidup di Android Keystore (tidak pernah keluar dari perangkat),
 * file ciphertext di filesDir/routers.enc. Tidak ada password plaintext
 * yang ditulis ke disk.
 *
 * Ketahanan data (audit v1.2.3):
 * - Penulisan ATOMIK: tulis ke file .tmp lalu rename; salinan file baik
 *   sebelumnya disimpan sebagai routers.enc.bak sebelum diganti. Aplikasi
 *   yang mati di tengah penulisan tidak lagi merusak satu-satunya salinan.
 * - Kegagalan baca TIDAK ditelan diam-diam: [lastLoadError] terisi dan UI
 *   memberi tahu user. Bila file utama rusak tapi cadangan terbaca, daftar
 *   dari cadangan yang dipakai (dengan pemberitahuan).
 */
class RouterStore(context: Context) {

    private val dir = context.filesDir
    private val file = File(dir, "routers.enc")
    private val backupFile = File(dir, "routers.enc.bak")
    private val tmpFile = File(dir, "routers.enc.tmp")

    /** Pesan kegagalan baca terakhir; null bila baca terakhir baik-baik saja. */
    var lastLoadError: String? = null
        private set

    companion object {
        private const val KEY_ALIAS = "mikroku_router_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun decode(f: File): List<RouterProfile>? = try {
        val raw = f.readText()
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) {
            null
        } else {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = String(cipher.doFinal(data), Charsets.UTF_8)
            val arr = JSONArray(json)
            List(arr.length()) { i -> RouterProfile.fromJson(arr.getJSONObject(i)) }
        }
    } catch (e: Exception) {
        null
    }

    fun load(): List<RouterProfile> {
        lastLoadError = null
        if (!file.exists() && !backupFile.exists()) return emptyList()
        if (file.exists()) {
            decode(file)?.let { return it }
        }
        // File utama hilang/rusak — coba salinan cadangan sebelum menyerah.
        if (backupFile.exists()) {
            decode(backupFile)?.let {
                lastLoadError = "Penyimpanan router utama tidak terbaca — daftar router " +
                    "dipulihkan dari salinan cadangan terakhir."
                return it
            }
        }
        lastLoadError = "Data router tersimpan TIDAK bisa dibaca (file penyimpanan rusak " +
            "atau kunci enkripsi perangkat berubah, mis. setelah ganti kunci layar). " +
            "Daftar router yang tampil mungkin kosong — data lama tidak ditimpa sampai " +
            "kamu menyimpan perubahan baru."
        return emptyList()
    }

    fun save(routers: List<RouterProfile>) {
        val arr = JSONArray()
        routers.forEach { arr.put(it.toJson()) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        // Tulis atomik: tmp → (utama lama jadi cadangan) → rename.
        tmpFile.writeText(payload)
        if (file.exists()) {
            try { file.copyTo(backupFile, overwrite = true) } catch (_: Exception) {}
        }
        if (!tmpFile.renameTo(file)) {
            // rename gagal (jarang) — tulis langsung sebagai jalan terakhir.
            file.writeText(payload)
            try { tmpFile.delete() } catch (_: Exception) {}
        }
    }
}
