package id.mikroku.data

import android.content.Context

/** Pengaturan aplikasi non-kredensial (tema + backup otomatis) di SharedPreferences. */
object SettingsStore {

    private const val PREFS = "mikroku-settings"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Tema ----------

    /** 0 = Ikuti Sistem, 1 = Terang, 2 = Gelap. */
    fun themeMode(context: Context): Int = prefs(context).getInt("theme_mode", 0)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt("theme_mode", mode).apply()
    }

    // ---------- Backup otomatis ----------

    fun autoBackupEnabled(context: Context): Boolean =
        prefs(context).getBoolean("autobak_enabled", false)

    /** Frekuensi dalam hari: 1 (harian) atau 7 (mingguan). */
    fun autoBackupFreqDays(context: Context): Int =
        prefs(context).getInt("autobak_freq_days", 1)

    /** URI folder tujuan (SAF tree) di HP. */
    fun autoBackupTreeUri(context: Context): String? =
        prefs(context).getString("autobak_tree_uri", null)

    /** Id profil router yang dibackup terjadwal. */
    fun autoBackupRouterId(context: Context): String? =
        prefs(context).getString("autobak_router_id", null)

    /** Ringkasan hasil jalan terakhir (ditulis worker, dibaca UI). */
    fun autoBackupLastRun(context: Context): String? =
        prefs(context).getString("autobak_last_run", null)

    fun setAutoBackup(
        context: Context,
        enabled: Boolean,
        freqDays: Int,
        treeUri: String?,
        routerId: String?
    ) {
        prefs(context).edit()
            .putBoolean("autobak_enabled", enabled)
            .putInt("autobak_freq_days", freqDays)
            .putString("autobak_tree_uri", treeUri)
            .putString("autobak_router_id", routerId)
            .apply()
    }

    fun setAutoBackupLastRun(context: Context, text: String) {
        prefs(context).edit().putString("autobak_last_run", text).apply()
    }

    // ---------- Kunci aplikasi (master PIN) ----------
    // PIN tidak disimpan mentah: hanya garam acak + SHA-256(garam:PIN).
    // Ini lapisan kunci di sisi HP; kredensial router tetap di penyimpanan
    // terenkripsi Keystore seperti biasa.

    fun appLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean("applock_enabled", false)

    fun hasAppLockPin(context: Context): Boolean =
        prefs(context).getString("applock_hash", null) != null

    private fun sha256Hex(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Pasang/ganti PIN kunci aplikasi dan aktifkan kuncinya. */
    fun setAppLockPin(context: Context, pin: String) {
        val saltBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(saltBytes)
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit()
            .putString("applock_salt", salt)
            .putString("applock_hash", sha256Hex("$salt:$pin"))
            .putBoolean("applock_enabled", true)
            .apply()
    }

    fun verifyAppLockPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString("applock_salt", null) ?: return false
        val hash = p.getString("applock_hash", null) ?: return false
        return sha256Hex("$salt:$pin") == hash
    }

    fun disableAppLock(context: Context) {
        prefs(context).edit()
            .remove("applock_salt")
            .remove("applock_hash")
            .putBoolean("applock_enabled", false)
            .apply()
    }
}
