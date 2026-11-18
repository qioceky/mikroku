package id.mikroku.data

import id.mikroku.api.ApiException
import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kunci hari RouterOS Kid Control, urutan tampil Senin→Minggu. */
val KID_DAYS: List<Pair<String, String>> = listOf(
    "mon" to "Senin",
    "tue" to "Selasa",
    "wed" to "Rabu",
    "thu" to "Kamis",
    "fri" to "Jumat",
    "sat" to "Sabtu",
    "sun" to "Minggu"
)

data class KidUserEntry(
    val id: String,
    val name: String,
    val rateLimit: String,
    val disabled: Boolean,
    val blocked: Boolean,
    /** Rentang jam BOLEH internet per hari (format RouterOS, mis. "00:00:00-24:00:00"). */
    val days: Map<String, String>
)

data class KidDeviceEntry(
    val id: String,
    val name: String,
    val macAddress: String,
    val user: String,
    val blocked: Boolean,
    val dynamic: Boolean,
    val disabled: Boolean
)

/**
 * Kid Control RouterOS (/ip/kid-control): batasi internet per anak —
 * jadwal mingguan (rentang jam boleh internet per hari), rate limit,
 * pause/resume. Tersedia di RouterOS v6 ≥6.41 dan v7.
 */
object KidControlRepo {

    suspend fun fetchUsers(client: MikrotikClient): List<KidUserEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/kid-control/print").map { r ->
                KidUserEntry(
                    id = r[".id"] ?: "",
                    name = r["name"] ?: "",
                    rateLimit = r["rate-limit"] ?: "",
                    disabled = r["disabled"] == "true",
                    blocked = r["blocked"] == "true",
                    days = KID_DAYS.associate { (key, _) -> key to (r[key] ?: "") }
                )
            }
        } }

    suspend fun fetchDevices(client: MikrotikClient): List<KidDeviceEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/kid-control/device/print").map { r ->
                KidDeviceEntry(
                    id = r[".id"] ?: "",
                    name = r["name"] ?: "",
                    macAddress = r["mac-address"] ?: "",
                    user = r["user"] ?: "",
                    blocked = r["blocked"] == "true",
                    dynamic = r["dynamic"] == "true",
                    disabled = r["disabled"] == "true"
                )
            }
        } }

    /** Tambah (id null) atau ubah user Kid Control beserta jadwal 7 harinya. */
    suspend fun saveUser(
        client: MikrotikClient,
        id: String?,
        name: String,
        rateLimit: String,
        days: Map<String, String>
    ) = withContext(Dispatchers.IO) { synchronized(client) {
        val args = mutableListOf("=name=$name", "=rate-limit=$rateLimit")
        for ((key, _) in KID_DAYS) {
            args.add("=$key=${days[key] ?: ""}")
        }
        if (id == null) {
            client.runCommand("/ip/kid-control/add", *args.toTypedArray())
        } else {
            client.runCommand("/ip/kid-control/set", "=.id=$id", *args.toTypedArray())
        }
    } }

    suspend fun setUserDisabled(client: MikrotikClient, id: String, disabled: Boolean) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand(
                "/ip/kid-control/set", "=.id=$id",
                "=disabled=${if (disabled) "yes" else "no"}"
            )
        } }

    /**
     * Pause internet satu user. [duration] format RouterOS ("30m", "1h",
     * "2h"); null = pause tanpa batas waktu sampai dilanjutkan manual.
     */
    suspend fun pauseUser(client: MikrotikClient, name: String, duration: String?) =
        withContext(Dispatchers.IO) { synchronized(client) {
            val args = mutableListOf("=user=$name")
            if (!duration.isNullOrBlank()) args.add("=duration=$duration")
            try {
                client.runCommand("/ip/kid-control/pause", *args.toTypedArray())
            } catch (e: Exception) {
                throw ApiException("Perintah pause ditolak router: ${e.message}")
            }
        } }

    /** Lanjutkan internet user yang sedang di-pause. */
    suspend fun resumeUser(client: MikrotikClient, name: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/ip/kid-control/resume", "=user=$name")
        } }

    /** Tambah (id null) atau ubah perangkat Kid Control. */
    suspend fun saveDevice(
        client: MikrotikClient,
        id: String?,
        name: String,
        macAddress: String,
        user: String
    ) = withContext(Dispatchers.IO) { synchronized(client) {
        val args = arrayOf("=name=$name", "=mac-address=$macAddress", "=user=$user")
        if (id == null) {
            client.runCommand("/ip/kid-control/device/add", *args)
        } else {
            client.runCommand("/ip/kid-control/device/set", "=.id=$id", *args)
        }
    } }
}
