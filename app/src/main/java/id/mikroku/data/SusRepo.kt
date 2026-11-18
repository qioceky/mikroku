package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SusSeverity { TINGGI, SEDANG, PERIKSA }

enum class SusAction { NONAKTIFKAN_ENTRI, MATIKAN_DNS_REMOTE, TIDAK_ADA }

/**
 * Satu temuan pemindaian SUS versi API. Semua temuan adalah INDIKASI
 * heuristik MikroKu — bukan vonis dan bukan algoritma SUS resmi MikroTik.
 */
data class SusFinding(
    val key: String,
    val severity: SusSeverity,
    val title: String,
    val detail: String,
    val action: SusAction = SusAction.TIDAK_ADA,
    /** Path API entri (untuk NONAKTIFKAN_ENTRI), mis. "/system/script". */
    val actionPath: String = "",
    val actionId: String = ""
)

/**
 * SUS versi API: pemindaian heuristik tanda-tanda router terkompromi dari
 * data yang bisa dibaca API — script/scheduler mencurigakan, proxy/SOCKS
 * terbuka, user asing berhak penuh, DNS statis asing, service terbuka.
 * JUJUR: ini heuristik kita; SUS resmi MikroTik tidak dipublikasikan.
 */
object SusRepo {

    private val suspiciousUserNames = setOf(
        "backdoor", "hack", "hacker", "root", "support", "mikrotik",
        "admin1", "admin2", "user", "test", "guest"
    )

    suspend fun scan(client: MikrotikClient, currentUsername: String): List<SusFinding> =
        withContext(Dispatchers.IO) { synchronized(client) {
            val findings = mutableListOf<SusFinding>()

            // --- Script: mengambil/mengirim data ke internet = pola umum malware RouterOS.
            try {
                for (r in client.run("/system/script/print")) {
                    val source = (r["source"] ?: "").lowercase()
                    val name = r["name"] ?: "(tanpa nama)"
                    val id = r[".id"] ?: ""
                    if (source.contains("fetch") || source.contains("http://") ||
                        source.contains("https://")
                    ) {
                        findings += SusFinding(
                            key = "script-net-$id",
                            severity = SusSeverity.TINGGI,
                            title = "Script '$name' berkomunikasi ke internet",
                            detail = "Isi script mengandung fetch/URL — pola umum " +
                                "malware RouterOS. Kenali script ini? Bila tidak, nonaktifkan.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/system/script",
                            actionId = id
                        )
                    } else if (r["dont-require-permissions"] == "true") {
                        findings += SusFinding(
                            key = "script-perm-$id",
                            severity = SusSeverity.SEDANG,
                            title = "Script '$name' berjalan tanpa perlu izin",
                            detail = "dont-require-permissions aktif — script bisa " +
                                "dijalankan konteks apa pun. Periksa isinya.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/system/script",
                            actionId = id
                        )
                    }
                }
            } catch (e: Exception) { /* menu tidak terbaca — lewati */ }

            // --- Scheduler: menjalankan fetch, atau berjalan sangat sering.
            try {
                for (r in client.run("/system/scheduler/print")) {
                    val onEvent = (r["on-event"] ?: "").lowercase()
                    val name = r["name"] ?: "(tanpa nama)"
                    val id = r[".id"] ?: ""
                    if (onEvent.contains("fetch") || onEvent.contains("http")) {
                        findings += SusFinding(
                            key = "sched-net-$id",
                            severity = SusSeverity.TINGGI,
                            title = "Scheduler '$name' menjalankan unduhan/fetch",
                            detail = "on-event mengandung fetch/URL — pasangan umum " +
                                "script berbahaya agar tetap hidup. Periksa isinya.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/system/scheduler",
                            actionId = id
                        )
                    }
                }
            } catch (e: Exception) { /* lewati */ }

            // --- Proxy & SOCKS terbuka: tanda klasik router dibajak jadi proxy.
            try {
                val socks = client.run("/ip/socks/print").firstOrNull()
                if (socks?.get("enabled") == "true") {
                    findings += SusFinding(
                        key = "socks-on",
                        severity = SusSeverity.TINGGI,
                        title = "SOCKS proxy AKTIF",
                        detail = "Router dibajak umumnya menyalakan SOCKS untuk " +
                            "dijadikan proxy pihak lain. Matikan dari Konsol API " +
                            "(/ip/socks set enabled=no) bila kamu tidak memakainya."
                    )
                }
            } catch (e: Exception) { /* lewati */ }
            try {
                val proxy = client.run("/ip/proxy/print").firstOrNull()
                if (proxy?.get("enabled") == "true") {
                    findings += SusFinding(
                        key = "proxy-on",
                        severity = SusSeverity.SEDANG,
                        title = "Web proxy AKTIF",
                        detail = "Web proxy bawaan menyala. Bila tidak kamu pakai, " +
                            "matikan dari Konsol API (/ip/proxy set enabled=no)."
                    )
                }
            } catch (e: Exception) { /* lewati */ }

            // --- User asing: berhak penuh selain akun yang sedang dipakai.
            try {
                for (r in client.run("/user/print")) {
                    val name = r["name"] ?: ""
                    val group = r["group"] ?: ""
                    val id = r[".id"] ?: ""
                    val disabled = r["disabled"] == "true"
                    if (disabled || name.isBlank()) continue
                    if (name.lowercase() in suspiciousUserNames) {
                        findings += SusFinding(
                            key = "user-susp-$id",
                            severity = SusSeverity.TINGGI,
                            title = "User mencurigakan: '$name'",
                            detail = "Nama user ini pola umum akun sisipan. Bila " +
                                "bukan buatanmu, nonaktifkan dan selidiki.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/user",
                            actionId = id
                        )
                    } else if (group == "full" &&
                        !name.equals(currentUsername, ignoreCase = true)
                    ) {
                        findings += SusFinding(
                            key = "user-full-$id",
                            severity = SusSeverity.SEDANG,
                            title = "User berhak penuh selain akunmu: '$name'",
                            detail = "Group 'full' = akses total. Wajar bila ini " +
                                "akun admin lain yang kamu kenal; asing = bahaya.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/user",
                            actionId = id
                        )
                    }
                }
            } catch (e: Exception) { /* lewati */ }

            // --- DNS: remote requests terbuka + entri statis nama publik.
            try {
                val dns = client.run("/ip/dns/print").firstOrNull()
                if (dns?.get("allow-remote-requests") == "true") {
                    findings += SusFinding(
                        key = "dns-remote",
                        severity = SusSeverity.SEDANG,
                        title = "DNS menerima request dari luar (open resolver)",
                        detail = "allow-remote-requests aktif — router bisa " +
                            "disalahgunakan untuk serangan amplifikasi DNS. " +
                            "Matikan bila klienmu tidak memakai DNS router ini.",
                        action = SusAction.MATIKAN_DNS_REMOTE
                    )
                }
            } catch (e: Exception) { /* lewati */ }
            try {
                for (r in client.run("/ip/dns/static/print")) {
                    val name = r["name"] ?: ""
                    val id = r[".id"] ?: ""
                    if (r["disabled"] == "true") continue
                    val publik = name.contains(".") &&
                        !name.endsWith(".local") && !name.endsWith(".lan") &&
                        !name.endsWith(".home") && !name.endsWith(".internal")
                    if (publik) {
                        findings += SusFinding(
                            key = "dns-static-$id",
                            severity = SusSeverity.PERIKSA,
                            title = "DNS statis nama publik: '$name'",
                            detail = "Mengarah ke ${r["address"] ?: "?"}. Wajar " +
                                "untuk split-DNS buatanmu; asing = bisa membelokkan " +
                                "akses pengguna jaringan.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/ip/dns/static",
                            actionId = id
                        )
                    }
                }
            } catch (e: Exception) { /* lewati */ }

            // --- Service terbuka ke semua alamat.
            try {
                for (r in client.run("/ip/service/print")) {
                    val name = r["name"] ?: ""
                    val id = r[".id"] ?: ""
                    if (r["disabled"] == "true") continue
                    if (name in setOf("telnet", "ftp", "www") &&
                        (r["address"] ?: "").isBlank()
                    ) {
                        findings += SusFinding(
                            key = "svc-$id",
                            severity = SusSeverity.PERIKSA,
                            title = "Service '$name' terbuka ke semua alamat",
                            detail = "Service lama tanpa enkripsi dan tanpa " +
                                "pembatasan alamat sumber. Batasi dari menu IP " +
                                "Service atau nonaktifkan bila tidak dipakai.",
                            action = SusAction.NONAKTIFKAN_ENTRI,
                            actionPath = "/ip/service",
                            actionId = id
                        )
                    }
                }
            } catch (e: Exception) { /* lewati */ }

            findings.sortedBy { it.severity.ordinal }
        } }

    /** Jalankan aksi perbaikan satu temuan (nonaktifkan entri / matikan DNS remote). */
    suspend fun applyAction(client: MikrotikClient, finding: SusFinding) =
        withContext(Dispatchers.IO) { synchronized(client) {
            when (finding.action) {
                SusAction.NONAKTIFKAN_ENTRI -> client.runCommand(
                    "${finding.actionPath}/set",
                    "=.id=${finding.actionId}",
                    "=disabled=yes"
                )
                SusAction.MATIKAN_DNS_REMOTE -> client.runCommand(
                    "/ip/dns/set",
                    "=allow-remote-requests=no"
                )
                SusAction.TIDAK_ADA -> Unit
            }
        } }
}
