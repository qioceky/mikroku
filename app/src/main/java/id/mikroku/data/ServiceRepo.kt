package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HotspotServerEntry(
    val id: String,
    val name: String,
    val iface: String,
    val addressPool: String,
    val profile: String,
    val disabled: Boolean,
    val invalid: Boolean
)

data class HotspotUserEntry(
    val id: String,
    val name: String,
    val profile: String,
    val uptime: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val disabled: Boolean,
    val comment: String
)

data class HotspotActiveEntry(
    val id: String,
    val user: String,
    val address: String,
    val macAddress: String,
    val server: String,
    val uptime: String,
    val bytesIn: Long,
    val bytesOut: Long
)

data class PppSecretEntry(
    val id: String,
    val name: String,
    val service: String,
    val profile: String,
    val remoteAddress: String,
    val lastLoggedOut: String,
    val disabled: Boolean,
    val comment: String
)

data class PppActiveEntry(
    val id: String,
    val name: String,
    val service: String,
    val callerId: String,
    val address: String,
    val uptime: String
)

data class SimpleQueueEntry(
    val id: String,
    val name: String,
    val target: String,
    val maxLimit: String,
    val rate: String,
    val bytesIn: Long?,
    val bytesOut: Long?,
    val disabled: Boolean,
    val dynamic: Boolean,
    val comment: String
)

data class QueueTreeEntry(
    val id: String,
    val name: String,
    val parent: String,
    val packetMark: String,
    val maxLimit: String,
    val bytes: Long,
    val disabled: Boolean
)

/** Repo layanan (v0.3): Hotspot, PPP, Queue, dan Tools jaringan. */
object ServiceRepo {

    // ---------- Hotspot ----------

    suspend fun fetchHotspotServers(client: MikrotikClient): List<HotspotServerEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/hotspot/print").map { r ->
                HotspotServerEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    iface = r["interface"] ?: "",
                    addressPool = r["address-pool"] ?: "",
                    profile = r["profile"] ?: "",
                    disabled = r["disabled"] == "true",
                    invalid = r["invalid"] == "true"
                )
            }
        } }

    suspend fun fetchHotspotUsers(client: MikrotikClient): List<HotspotUserEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/hotspot/user/print").map { r ->
                HotspotUserEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    profile = r["profile"] ?: "",
                    uptime = r["uptime"] ?: "",
                    bytesIn = r["bytes-in"]?.toLongOrNull() ?: 0L,
                    bytesOut = r["bytes-out"]?.toLongOrNull() ?: 0L,
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    /** Nama-nama profil user hotspot (untuk pilihan di generator voucher). */
    suspend fun fetchHotspotProfiles(client: MikrotikClient): List<String> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/hotspot/user/profile/print").mapNotNull { it["name"] }
        } }

    suspend fun setHotspotUserDisabled(client: MikrotikClient, userId: String, disabled: Boolean) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand(
                "/ip/hotspot/user/set",
                "=.id=$userId",
                "=disabled=${if (disabled) "yes" else "no"}"
            )
        } }

    suspend fun fetchHotspotActive(client: MikrotikClient): List<HotspotActiveEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/hotspot/active/print").map { r ->
                HotspotActiveEntry(
                    id = r[".id"] ?: (r["user"] ?: ""),
                    user = r["user"] ?: "-",
                    address = r["address"] ?: "",
                    macAddress = r["mac-address"] ?: "",
                    server = r["server"] ?: "",
                    uptime = r["uptime"] ?: "",
                    bytesIn = r["bytes-in"]?.toLongOrNull() ?: 0L,
                    bytesOut = r["bytes-out"]?.toLongOrNull() ?: 0L
                )
            }
        } }

    /** Putuskan satu sesi hotspot aktif (remove entri active). */
    suspend fun removeHotspotActive(client: MikrotikClient, activeId: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/ip/hotspot/active/remove", "=.id=$activeId")
        } }

    // ---------- PPP (PPPoE dll) ----------

    suspend fun fetchPppSecrets(client: MikrotikClient): List<PppSecretEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            // Password sengaja TIDAK dibaca/ditampilkan.
            client.run("/ppp/secret/print").map { r ->
                PppSecretEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    service = r["service"] ?: "",
                    profile = r["profile"] ?: "",
                    remoteAddress = r["remote-address"] ?: "",
                    lastLoggedOut = r["last-logged-out"] ?: "",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun setPppSecretDisabled(client: MikrotikClient, secretId: String, disabled: Boolean) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand(
                "/ppp/secret/set",
                "=.id=$secretId",
                "=disabled=${if (disabled) "yes" else "no"}"
            )
        } }

    suspend fun fetchPppActive(client: MikrotikClient): List<PppActiveEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ppp/active/print").map { r ->
                PppActiveEntry(
                    id = r[".id"] ?: (r["name"] ?: ""),
                    name = r["name"] ?: "-",
                    service = r["service"] ?: "",
                    callerId = r["caller-id"] ?: "",
                    address = r["address"] ?: "",
                    uptime = r["uptime"] ?: ""
                )
            }
        } }

    /** Putuskan satu sesi PPP aktif (remove entri active). */
    suspend fun removePppActive(client: MikrotikClient, activeId: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/ppp/active/remove", "=.id=$activeId")
        } }

    // ---------- Queue ----------

    suspend fun fetchSimpleQueues(client: MikrotikClient): List<SimpleQueueEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/queue/simple/print").map { r ->
                SimpleQueueEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    target = r["target"] ?: "",
                    maxLimit = r["max-limit"] ?: "",
                    rate = r["rate"] ?: "",
                    bytesIn = r["bytes-in"]?.toLongOrNull(),
                    bytesOut = r["bytes-out"]?.toLongOrNull(),
                    disabled = r["disabled"] == "true",
                    dynamic = r["dynamic"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun setSimpleQueueDisabled(client: MikrotikClient, queueId: String, disabled: Boolean) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand(
                "/queue/simple/set",
                "=.id=$queueId",
                "=disabled=${if (disabled) "yes" else "no"}"
            )
        } }

    suspend fun fetchQueueTree(client: MikrotikClient): List<QueueTreeEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/queue/tree/print").map { r ->
                QueueTreeEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    parent = r["parent"] ?: "",
                    packetMark = r["packet-mark"] ?: "",
                    maxLimit = r["max-limit"] ?: "",
                    bytes = r["bytes"]?.toLongOrNull() ?: 0L,
                    disabled = r["disabled"] == "true"
                )
            }
        } }

    // ---------- Tools ----------

    /** Wake-on-LAN dari router: kirim magic packet ke [mac] (lewat [iface] bila diisi). */
    suspend fun wakeOnLan(client: MikrotikClient, mac: String, iface: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            val words = mutableListOf("/tool/wol", "=mac=$mac")
            if (iface.isNotBlank()) words.add("=interface=$iface")
            client.runCommand(*words.toTypedArray())
        } }

    /**
     * Ping DARI router ke [address] (5 paket). Mengembalikan baris hasil
     * siap tampil. Mungkin perlu policy test di user API.
     */
    suspend fun ping(client: MikrotikClient, address: String): List<String> =
        withContext(Dispatchers.IO) { synchronized(client) {
            val rows = client.run("/ping", "=address=$address", "=count=5")
            val lines = mutableListOf<String>()
            for (r in rows) {
                val seq = r["seq"]
                val summarySent = r["sent"]
                when {
                    seq != null -> {
                        val status = r["status"]
                        val time = r["time"]
                        val ttl = r["ttl"]
                        lines.add(
                            buildString {
                                append("#$seq ${r["host"] ?: address}")
                                if (status != null) append(" — $status")
                                else if (time != null) append(" — $time")
                                if (ttl != null) append(" (TTL $ttl)")
                            }
                        )
                    }
                    summarySent != null -> {
                        val received = r["received"] ?: "?"
                        val loss = r["packet-loss"] ?: "?"
                        val min = r["min-rtt"] ?: "-"
                        val avg = r["avg-rtt"] ?: "-"
                        val max = r["max-rtt"] ?: "-"
                        lines.add("Ringkasan: terkirim $summarySent, diterima $received, hilang $loss%")
                        lines.add("RTT min/rata-rata/maks: $min / $avg / $max")
                    }
                }
            }
            if (lines.isEmpty()) lines.add("Tidak ada balasan dari router.")
            lines
        } }

    /** Traceroute DARI router ke [address] (1 probe per hop). */
    suspend fun traceroute(client: MikrotikClient, address: String): List<String> =
        withContext(Dispatchers.IO) { synchronized(client) {
            val rows = client.run("/tool/traceroute", "=address=$address", "=count=1")
            val lines = mutableListOf<String>()
            rows.forEachIndexed { i, r ->
                val hop = r["address"] ?: ""
                val last = r["last"] ?: ""
                val avg = r["avg"] ?: ""
                val loss = r["loss"] ?: ""
                val status = r["status"] ?: ""
                lines.add(
                    buildString {
                        append("${i + 1}. ${hop.ifBlank { "*" }}")
                        if (last.isNotBlank()) append(" — $last")
                        if (avg.isNotBlank() && avg != last) append(" (rata-rata $avg)")
                        if (loss.isNotBlank() && loss != "0") append(" • hilang $loss%")
                        if (status.isNotBlank()) append(" • $status")
                    }
                )
            }
            if (lines.isEmpty()) lines.add("Tidak ada hasil traceroute.")
            lines
        } }
}
