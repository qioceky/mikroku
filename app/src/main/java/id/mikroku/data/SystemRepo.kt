package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RouterUserEntry(
    val id: String,
    val name: String,
    val group: String,
    val address: String,
    val lastLoggedIn: String,
    val disabled: Boolean,
    val comment: String
)

data class UserGroupEntry(
    val id: String,
    val name: String,
    val policy: String
)

data class SchedulerEntry(
    val id: String,
    val name: String,
    val startDate: String,
    val startTime: String,
    val interval: String,
    val nextRun: String,
    val runCount: String,
    val onEvent: String,
    val disabled: Boolean,
    val comment: String
)

data class ScriptEntry(
    val id: String,
    val name: String,
    val owner: String,
    val lastStarted: String,
    val runCount: String,
    val source: String,
    val disabled: Boolean,
    val comment: String
)

data class LogEntry(
    val id: String,
    val time: String,
    val topics: String,
    val message: String
)

data class FileEntry(
    val id: String,
    val name: String,
    val type: String,
    val size: Long,
    val creationTime: String
)

data class PackageEntry(
    val id: String,
    val name: String,
    val version: String,
    val disabled: Boolean
)

/** Repo administrasi sistem (v0.5). */
object SystemRepo {

    /** Daftar paket RouterOS terpasang + versinya. */
    suspend fun fetchPackages(client: MikrotikClient): List<PackageEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/system/package/print").map { r ->
                PackageEntry(
                    id = r[".id"] ?: (r["name"] ?: ""),
                    name = r["name"] ?: "-",
                    version = r["version"] ?: "",
                    disabled = r["disabled"] == "true"
                )
            }
        } }

    /** Info RouterBOOT: firmware terpasang vs yang tersedia untuk upgrade. */
    suspend fun fetchRouterboard(client: MikrotikClient): RouterboardInfo? =
        withContext(Dispatchers.IO) { synchronized(client) {
            val r = client.run("/system/routerboard/print").firstOrNull()
                ?: return@synchronized null
            RouterboardInfo(
                model = r["model"] ?: r["board-name"] ?: "",
                serialNumber = r["serial-number"] ?: "",
                factoryFirmware = r["factory-firmware"] ?: "",
                currentFirmware = r["current-firmware"] ?: "",
                upgradeFirmware = r["upgrade-firmware"] ?: ""
            )
        } }

    /** Minta RouterBOOT memakai upgrade-firmware — berlaku setelah reboot. */
    suspend fun upgradeRouterboard(client: MikrotikClient) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/routerboard/upgrade")
        } }

    /** Status update RouterOS terakhir yang diketahui router. */
    suspend fun fetchPackageUpdate(client: MikrotikClient): PackageUpdateInfo? =
        withContext(Dispatchers.IO) { synchronized(client) {
            val r = client.run("/system/package/update/print").firstOrNull()
                ?: return@synchronized null
            PackageUpdateInfo(
                channel = r["channel"] ?: "",
                installedVersion = r["installed-version"] ?: "",
                latestVersion = r["latest-version"] ?: "",
                statusText = r["status"] ?: ""
            )
        } }

    /** Cek update ke server MikroTik (router butuh internet + DNS), lalu baca statusnya. */
    suspend fun checkForUpdates(client: MikrotikClient): PackageUpdateInfo? =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/package/update/check-for-updates")
            val r = client.run("/system/package/update/print").firstOrNull()
                ?: return@synchronized null
            PackageUpdateInfo(
                channel = r["channel"] ?: "",
                installedVersion = r["installed-version"] ?: "",
                latestVersion = r["latest-version"] ?: "",
                statusText = r["status"] ?: ""
            )
        } }

    /** Unduh paket update saja (belum dipasang, tanpa reboot). */
    suspend fun downloadUpdate(client: MikrotikClient) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/package/update/download")
        } }

    /** Pasang update: router mengunduh lalu REBOOT untuk menerapkannya. */
    suspend fun installUpdate(client: MikrotikClient) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/package/update/install")
        } }

    suspend fun fetchUsers(client: MikrotikClient): List<RouterUserEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/user/print").map { r ->
                RouterUserEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    group = r["group"] ?: "",
                    address = r["address"] ?: "",
                    lastLoggedIn = r["last-logged-in"] ?: "",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchGroups(client: MikrotikClient): List<UserGroupEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/user/group/print").map { r ->
                UserGroupEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    policy = r["policy"] ?: ""
                )
            }
        } }

    suspend fun fetchSchedulers(client: MikrotikClient): List<SchedulerEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/system/scheduler/print").map { r ->
                SchedulerEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    startDate = r["start-date"] ?: "",
                    startTime = r["start-time"] ?: "",
                    interval = r["interval"] ?: "",
                    nextRun = r["next-run"] ?: "",
                    runCount = r["run-count"] ?: "",
                    onEvent = r["on-event"] ?: "",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun runScheduler(client: MikrotikClient, schedulerId: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/scheduler/run", "=.id=$schedulerId")
        } }

    suspend fun fetchScripts(client: MikrotikClient): List<ScriptEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/system/script/print").map { r ->
                ScriptEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    owner = r["owner"] ?: "",
                    lastStarted = r["last-started"] ?: "",
                    runCount = r["run-count"] ?: "",
                    source = r["source"] ?: "",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun runScript(client: MikrotikClient, scriptId: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/script/run", "=.id=$scriptId")
        } }

    /** Log terbaru dulu, dibatasi [limit] entri terakhir. */
    suspend fun fetchLogs(client: MikrotikClient, limit: Int = 100): List<LogEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/log/print", "=.proplist=.id,time,topics,message")
                .takeLast(limit).reversed().map { r ->
                LogEntry(
                    id = r[".id"] ?: "",
                    time = r["time"] ?: "",
                    topics = r["topics"] ?: "",
                    message = r["message"] ?: ""
                )
            }
        } }

    suspend fun fetchFiles(client: MikrotikClient): List<FileEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/file/print").map { r ->
                FileEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    type = r["type"] ?: "",
                    size = r["size"]?.toLongOrNull() ?: 0L,
                    creationTime = r["creation-time"] ?: ""
                )
            }
        } }

    suspend fun setIdentity(client: MikrotikClient, name: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/identity/set", "=name=$name")
        } }

    /** Buat file backup biner <name>.backup di router. */
    suspend fun createBackup(client: MikrotikClient, name: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/backup/save", "=name=$name")
        } }

    /** Buat file export konfigurasi <name>.rsc di router. */
    suspend fun createExport(client: MikrotikClient, name: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/export", "=file=$name")
        } }

    // ------------------------------------------------------------ v0.7: jam & NTP

    suspend fun fetchClock(client: MikrotikClient): ClockInfo =
        withContext(Dispatchers.IO) { synchronized(client) {
            val r = client.run("/system/clock/print").firstOrNull() ?: emptyMap()
            ClockInfo(
                time = r["time"] ?: "",
                date = r["date"] ?: "",
                timeZoneName = r["time-zone-name"] ?: "",
                dstActive = r["dst-active"] == "true"
            )
        } }

    suspend fun fetchNtpClient(client: MikrotikClient): NtpInfo =
        withContext(Dispatchers.IO) { synchronized(client) {
            val r = client.run("/system/ntp/client/print").firstOrNull() ?: emptyMap()
            NtpInfo(
                enabled = r["enabled"] == "true",
                mode = r["mode"] ?: "",
                primaryNtp = r["primary-ntp"] ?: "",
                secondaryNtp = r["secondary-ntp"] ?: "",
                status = r["status"] ?: "",
                activeServer = r["active-server"] ?: ""
            )
        } }

    suspend fun setClockTimezone(client: MikrotikClient, timeZoneName: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/system/clock/set", "=time-zone-name=$timeZoneName")
        } }

    suspend fun setNtpClient(
        client: MikrotikClient,
        enabled: Boolean,
        primaryNtp: String,
        secondaryNtp: String
    ) = withContext(Dispatchers.IO) { synchronized(client) {
        val words = mutableListOf("=enabled=${if (enabled) "yes" else "no"}")
        if (primaryNtp.isNotBlank()) words += "=primary-ntp=$primaryNtp"
        if (secondaryNtp.isNotBlank()) words += "=secondary-ntp=$secondaryNtp"
        client.runCommand("/system/ntp/client/set", *words.toTypedArray())
    } }
}

data class RouterboardInfo(
    val model: String,
    val serialNumber: String,
    val factoryFirmware: String,
    val currentFirmware: String,
    val upgradeFirmware: String
) {
    val updateAvailable: Boolean
        get() = upgradeFirmware.isNotBlank() && upgradeFirmware != currentFirmware
}

data class PackageUpdateInfo(
    val channel: String,
    val installedVersion: String,
    val latestVersion: String,
    val statusText: String
) {
    val updateAvailable: Boolean
        get() = latestVersion.isNotBlank() && installedVersion.isNotBlank() &&
            latestVersion != installedVersion
}

data class ClockInfo(
    val time: String,
    val date: String,
    val timeZoneName: String,
    val dstActive: Boolean
)

data class NtpInfo(
    val enabled: Boolean,
    val mode: String,
    val primaryNtp: String,
    val secondaryNtp: String,
    val status: String,
    val activeServer: String
)
