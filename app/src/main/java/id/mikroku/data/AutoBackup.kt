package id.mikroku.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pekerja latar Backup Otomatis: buat backup + export di router tersimpan,
 * lalu unduh keduanya ke folder HP pilihan user lewat SFTP.
 *
 * Hasil setiap jalan dicatat di SettingsStore (autoBackupLastRun) supaya
 * bisa dibaca user di layar Backup. Kegagalan tidak di-retry otomatis —
 * jadwal berikutnya mencoba lagi.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        try {
            val routerId = SettingsStore.autoBackupRouterId(ctx)
            val treeUri = SettingsStore.autoBackupTreeUri(ctx)
            if (routerId == null || treeUri == null) return Result.success()

            val store = RouterStore(ctx)
            val allRouters = store.load()
            var effectiveProfile = allRouters.firstOrNull { it.id == routerId }
            val profile = effectiveProfile
            if (profile == null) {
                SettingsStore.setAutoBackupLastRun(
                    ctx, "$stamp — Gagal: router tidak lagi ada di daftar tersimpan"
                )
                return Result.success()
            }
            if (profile.password.isBlank()) {
                SettingsStore.setAutoBackupLastRun(
                    ctx, "$stamp — Gagal: password router tidak tersimpan di aplikasi"
                )
                return Result.success()
            }

            val nameStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val base = "mikroku-auto-$nameStamp"
            val manager = SessionManager(profile)
            try {
                manager.runNoRetry { SystemRepo.createBackup(it, base) }
                manager.runNoRetry { SystemRepo.createExport(it, base) }
            } finally {
                manager.close()
            }

            val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri))
            if (tree == null || !tree.canWrite()) {
                SettingsStore.setAutoBackupLastRun(
                    ctx, "$stamp — Gagal: folder tujuan di HP tidak bisa ditulis"
                )
                return Result.success()
            }
            // Sidik jari SSH pertama yang dipelajari disimpan ke profil —
            // kecuali bacaan penyimpanan bermasalah (jangan menimpa data lama).
            val learnFp: (String) -> Unit = { fp ->
                val current = effectiveProfile
                if (store.lastLoadError == null && current != null && current.sshFingerprint == null) {
                    val updated = current.copy(sshFingerprint = fp)
                    effectiveProfile = updated
                    try {
                        store.save(allRouters.map { if (it.id == updated.id) updated else it })
                    } catch (_: Exception) {}
                }
            }
            var cleaned = 0
            for (fileName in listOf("$base.backup", "$base.rsc")) {
                val doc = tree.createFile("application/octet-stream", fileName)
                    ?: throw IllegalStateException("tidak bisa membuat $fileName di folder tujuan")
                val out = ctx.contentResolver.openOutputStream(doc.uri, "w")
                    ?: throw IllegalStateException("tidak bisa membuka file tujuan")
                val dlProfile = effectiveProfile ?: profile
                out.use { SftpRepo.download(dlProfile, fileName, it, onFingerprintLearned = learnFp) }
                // File di router DIHAPUS setelah salinannya aman di HP — tanpa
                // ini, backup terjadwal menumpuk file di penyimpanan router
                // yang kecil sampai penuh. Gagal hapus tidak menggagalkan backup.
                try {
                    SftpRepo.delete(effectiveProfile ?: profile, fileName, onFingerprintLearned = learnFp)
                    cleaned++
                } catch (_: Exception) {}
            }
            SettingsStore.setAutoBackupLastRun(
                ctx, "$stamp — Berhasil: $base.backup + $base.rsc tersimpan di HP" +
                    if (cleaned == 2) "; file di router dibersihkan" else ""
            )
        } catch (e: Exception) {
            SettingsStore.setAutoBackupLastRun(
                ctx, "$stamp — Gagal: ${e.message ?: "kesalahan tak dikenal"}"
            )
        }
        return Result.success()
    }
}

/** Penjadwalan Backup Otomatis via WorkManager (unik, diperbarui di tempat). */
object AutoBackupScheduler {

    private const val WORK_NAME = "mikroku-autobackup"

    fun schedule(context: Context) {
        val days = SettingsStore.autoBackupFreqDays(context).toLong().coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
