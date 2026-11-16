package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DashboardData(
    val identity: String,
    val boardName: String,
    val version: String,
    val cpuLoad: Int?,
    val freeMemory: Long?,
    val totalMemory: Long?,
    val uptime: String,
    val clockTime: String,
    val clockDate: String,
    val temperature: String?,
    val voltage: String?,
    val hotspotActive: Int? = null,
    val leaseCount: Int? = null
)

data class InterfaceData(
    val name: String,
    val type: String,
    val running: Boolean,
    val disabled: Boolean,
    val rxBytes: Long,
    val txBytes: Long,
    val ipAddresses: List<String>,
    val comment: String
)

/** Hasil koneksi: klien yang sudah login + sidik jari sertifikat (mode SSL). */
class RouterSession(
    val client: MikrotikClient,
    val certFingerprint: String?
) : AutoCloseable {
    override fun close() = client.close()
}

object MonitorRepo {

    suspend fun openSession(profile: RouterProfile): RouterSession = withContext(Dispatchers.IO) {
        val client = MikrotikClient.connect(
            host = profile.host,
            port = profile.port,
            useSsl = profile.useSsl,
            expectedFingerprint = profile.certFingerprint
        )
        try {
            client.login(profile.username, profile.password)
        } catch (e: Exception) {
            client.close()
            throw e
        }
        RouterSession(client, client.serverCertFingerprint)
    }

    suspend fun fetchDashboard(client: MikrotikClient): DashboardData = withContext(Dispatchers.IO) { synchronized(client) {
        val resource = client.run("/system/resource/print").firstOrNull() ?: emptyMap()
        val identity = client.run("/system/identity/print").firstOrNull() ?: emptyMap()
        val clock = client.run("/system/clock/print").firstOrNull() ?: emptyMap()
        val health = try {
            client.run("/system/health/print").firstOrNull() ?: emptyMap()
        } catch (e: Exception) {
            emptyMap() // tidak semua board punya sensor health
        }
        val hotspotActive = try {
            client.run("/ip/hotspot/active/print").size
        } catch (e: Exception) {
            null // hotspot belum tentu terpasang
        }
        val leaseCount = try {
            client.run("/ip/dhcp-server/lease/print").size
        } catch (e: Exception) {
            null
        }
        DashboardData(
            identity = identity["name"] ?: "-",
            boardName = resource["board-name"] ?: "-",
            version = resource["version"] ?: "-",
            cpuLoad = resource["cpu-load"]?.toIntOrNull(),
            freeMemory = resource["free-memory"]?.toLongOrNull(),
            totalMemory = resource["total-memory"]?.toLongOrNull(),
            uptime = resource["uptime"] ?: "-",
            clockTime = clock["time"] ?: "-",
            clockDate = clock["date"] ?: "-",
            temperature = health["temperature"],
            voltage = health["voltage"],
            hotspotActive = hotspotActive,
            leaseCount = leaseCount
        )
    } }

    suspend fun fetchInterfaces(client: MikrotikClient): List<InterfaceData> = withContext(Dispatchers.IO) { synchronized(client) {
        val ifaces = client.run("/interface/print")
        val addrs = try {
            client.run("/ip/address/print")
        } catch (e: Exception) {
            emptyList()
        }
        val byInterface = addrs.groupBy { it["interface"] ?: "" }
        ifaces.map { f ->
            val name = f["name"] ?: "?"
            InterfaceData(
                name = name,
                type = f["type"] ?: "",
                running = f["running"] == "true",
                disabled = f["disabled"] == "true",
                rxBytes = f["rx-byte"]?.toLongOrNull() ?: 0L,
                txBytes = f["tx-byte"]?.toLongOrNull() ?: 0L,
                ipAddresses = byInterface[name]?.mapNotNull { it["address"] } ?: emptyList(),
                comment = f["comment"] ?: ""
            )
        }
    } }

    suspend fun reboot(client: MikrotikClient) = withContext(Dispatchers.IO) {
        synchronized(client) { client.runCommand("/system/reboot") }
    }

    suspend fun testConnection(profile: RouterProfile): String? = withContext(Dispatchers.IO) {
        openSession(profile).use { it.certFingerprint }
    }
}
