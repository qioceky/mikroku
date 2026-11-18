package id.mikroku.data

import id.mikroku.api.ApiException
import id.mikroku.api.ApiTrapException
import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WirelessEntry(
    val id: String,
    val name: String,
    val ssid: String,
    val mode: String,
    val band: String,
    val frequency: String,
    val channelWidth: String,
    val running: Boolean,
    val disabled: Boolean,
    val comment: String
)

data class WirelessClientEntry(
    val id: String,
    val macAddress: String,
    val interfaceName: String,
    val signalStrength: String,
    val txRate: String,
    val rxRate: String,
    val uptime: String,
    val lastIp: String
)

data class BridgeEntry(
    val id: String,
    val name: String,
    val protocolMode: String,
    val macAddress: String,
    val running: Boolean,
    val disabled: Boolean,
    val comment: String
)

data class BridgePortEntry(
    val id: String,
    val interfaceName: String,
    val bridge: String,
    val pvid: String,
    val status: String,
    val disabled: Boolean,
    val comment: String
)

data class VlanEntry(
    val id: String,
    val name: String,
    val vlanId: String,
    val interfaceName: String,
    val running: Boolean,
    val disabled: Boolean,
    val comment: String
)

data class SecurityProfileEntry(
    val id: String,
    val name: String,
    val mode: String,
    val authTypes: String,
    /** Password TIDAK pernah diambil untuk ditampilkan — hanya status terisi/kosong. */
    val hasWpa2Key: Boolean,
    val isDefault: Boolean,
    val comment: String
)

data class PoolEntry(
    val id: String,
    val name: String,
    val ranges: String,
    val nextPool: String,
    val comment: String
)

data class DhcpClientEntry(
    val id: String,
    val interfaceName: String,
    val address: String,
    val gateway: String,
    val status: String,
    val expiresAfter: String,
    val dhcpServer: String,
    val primaryDns: String,
    val disabled: Boolean,
    val comment: String
)

data class ServiceEntry(
    val id: String,
    val name: String,
    val port: String,
    val address: String,
    val disabled: Boolean
)

data class NeighborEntry(
    val id: String,
    val interfaceName: String,
    val address: String,
    val macAddress: String,
    val identity: String,
    val version: String,
    val board: String,
    val uptime: String
)

/** Repo fitur lanjutan (v0.6): wireless, bridge, VLAN, bandwidth test, konsol. */
object AdvancedRepo {

    suspend fun fetchWireless(client: MikrotikClient): List<WirelessEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/interface/wireless/print").map { r ->
                WirelessEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    ssid = r["ssid"] ?: "",
                    mode = r["mode"] ?: "",
                    band = r["band"] ?: "",
                    frequency = r["frequency"] ?: "",
                    channelWidth = r["channel-width"] ?: "",
                    running = r["running"] == "true",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchWirelessClients(client: MikrotikClient): List<WirelessClientEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/interface/wireless/registration-table/print").map { r ->
                WirelessClientEntry(
                    id = r[".id"] ?: r["mac-address"] ?: "",
                    macAddress = r["mac-address"] ?: "-",
                    interfaceName = r["interface"] ?: "",
                    signalStrength = r["signal-strength"] ?: "",
                    txRate = r["tx-rate"] ?: "",
                    rxRate = r["rx-rate"] ?: "",
                    uptime = r["uptime"] ?: "",
                    lastIp = r["last-ip"] ?: ""
                )
            }
        } }

    suspend fun fetchBridges(client: MikrotikClient): List<BridgeEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/interface/bridge/print").map { r ->
                BridgeEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    protocolMode = r["protocol-mode"] ?: "",
                    macAddress = r["mac-address"] ?: "",
                    running = r["running"] == "true",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchBridgePorts(client: MikrotikClient): List<BridgePortEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/interface/bridge/port/print").map { r ->
                BridgePortEntry(
                    id = r[".id"] ?: "",
                    interfaceName = r["interface"] ?: "-",
                    bridge = r["bridge"] ?: "",
                    pvid = r["pvid"] ?: "",
                    status = r["status"] ?: "",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchVlans(client: MikrotikClient): List<VlanEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/interface/vlan/print").map { r ->
                VlanEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    vlanId = r["vlan-id"] ?: "",
                    interfaceName = r["interface"] ?: "",
                    running = r["running"] == "true",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    /**
     * Bandwidth test DARI router ke server btest (router MikroTik lain dengan
     * bandwidth-server aktif). Mengembalikan baris hasil terakhir (rata-rata
     * total ada di atribut tx-total-average / rx-total-average, satuan bit/s).
     */
    suspend fun runBandwidthTest(
        client: MikrotikClient,
        address: String,
        durationSec: Int,
        protocol: String,
        direction: String
    ): Map<String, String> =
        withContext(Dispatchers.IO) { synchronized(client) {
            val rows = client.run(
                "/tool/bandwidth-test",
                "=address=$address",
                "=duration=${durationSec}s",
                "=protocol=$protocol",
                "=direction=$direction"
            )
            rows.lastOrNull() ?: emptyMap()
        } }

    // ------------------------------------------------------------ v0.7

    suspend fun fetchSecurityProfiles(client: MikrotikClient): List<SecurityProfileEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/interface/wireless/security-profiles/print").map { r ->
                SecurityProfileEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    mode = r["mode"] ?: "",
                    authTypes = r["authentication-types"] ?: "",
                    hasWpa2Key = (r["wpa2-pre-shared-key"] ?: "").isNotEmpty(),
                    isDefault = r["name"] == "default",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchPools(client: MikrotikClient): List<PoolEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/pool/print").map { r ->
                PoolEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    ranges = r["ranges"] ?: "",
                    nextPool = r["next-pool"] ?: "",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchDhcpClients(client: MikrotikClient): List<DhcpClientEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/dhcp-client/print").map { r ->
                DhcpClientEntry(
                    id = r[".id"] ?: "",
                    interfaceName = r["interface"] ?: "-",
                    address = r["address"] ?: "",
                    gateway = r["gateway"] ?: "",
                    status = r["status"] ?: "",
                    expiresAfter = r["expires-after"] ?: "",
                    dhcpServer = r["dhcp-server"] ?: "",
                    primaryDns = r["primary-dns"] ?: "",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun renewDhcpClient(client: MikrotikClient, id: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/ip/dhcp-client/renew", "=.id=$id")
        } }

    suspend fun releaseDhcpClient(client: MikrotikClient, id: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/ip/dhcp-client/release", "=.id=$id")
        } }

    suspend fun fetchServices(client: MikrotikClient): List<ServiceEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/service/print").map { r ->
                ServiceEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    port = r["port"] ?: "",
                    address = r["address"] ?: "",
                    disabled = r["disabled"] == "true"
                )
            }
        } }

    /**
     * Siapkan API-SSL sekali klik: service api-ssl bawaan RouterOS tidak punya
     * sertifikat (certificate=none) sehingga jabat tangan TLS selalu ditolak.
     * Fungsi ini membuat sertifikat self-signed 'mikroku-api' (kunci 1024-bit
     * agar cepat dibuat di router kecil), menandatanganinya, memasangnya ke
     * service api-ssl, dan mengaktifkannya. Aman diulang: langkah yang sudah
     * beres dilewati. Mengembalikan pesan status untuk ditampilkan ke user.
     */
    suspend fun setupApiSsl(client: MikrotikClient): String =
        withContext(Dispatchers.IO) { synchronized(client) {
            val svc = client.run("/ip/service/print").firstOrNull { it["name"] == "api-ssl" }
                ?: throw ApiException("Service api-ssl tidak ditemukan di router")
            val svcId = svc[".id"] ?: throw ApiException("ID service api-ssl tidak terbaca")
            val port = svc["port"] ?: "8729"
            // Mulai ulang service api-ssl agar ia MEMUAT sertifikat yang
            // terpasang — service yang sudah terlanjur jalan sejak sebelum
            // ada sertifikat bisa terus menolak jabat tangan TLS.
            fun restartApiSsl() {
                try {
                    client.runCommand("/ip/service/set", "=.id=$svcId", "=disabled=yes")
                } catch (_: Exception) {}
                Thread.sleep(700)
                client.runCommand("/ip/service/set", "=.id=$svcId", "=disabled=no")
            }
            val currentCert = (svc["certificate"] ?: "none").ifBlank { "none" }
            if (currentCert != "none") {
                restartApiSsl()
                return@synchronized "API-SSL sudah siap: sertifikat '$currentCert' terpasang " +
                    "dan service api-ssl dimulai ulang di port $port agar memuatnya. " +
                    "Sambung ulang dengan mode API-SSL $port."
            }
            val certName = "mikroku-api"
            var cert = client.run("/certificate/print").firstOrNull { it["name"] == certName }
            if (cert == null) {
                client.runCommand(
                    "/certificate/add",
                    "=name=$certName",
                    "=common-name=$certName",
                    "=days-valid=3650",
                    "=key-size=1024",
                    "=key-usage=key-cert-sign,crl-sign,tls-server"
                )
                cert = client.run("/certificate/print").firstOrNull { it["name"] == certName }
                    ?: throw ApiException("Sertifikat gagal dibuat di router")
            }
            val certId = cert[".id"] ?: throw ApiException("ID sertifikat tidak terbaca")
            // Sertifikat dianggap berlaku bila sudah punya masa berlaku / nomor
            // seri / status CA — template yang belum ditandatangani tidak bisa
            // dipasang ke service ("input does not match any value of certificate").
            fun certBerlaku(c: Map<String, String>): Boolean =
                !c["invalid-after"].isNullOrBlank() || c["ca"] == "true" ||
                    !c["serial-number"].isNullOrBlank()
            if (!certBerlaku(cert)) {
                try {
                    client.runCommand("/certificate/sign", "=number=$certId", "=ca-crl-host=127.0.0.1")
                } catch (e: ApiTrapException) {
                    // Mungkin sedang ditandatangani dari ketukan sebelumnya — lanjut menunggu.
                }
                // Penandatanganan berjalan di LATAR router (perintah sign langsung
                // selesai, sertifikat baru berlaku belakangan) — tunggu & cek berkala.
                val deadline = System.currentTimeMillis() + 120_000
                var berlaku = false
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(2_000)
                    val now = client.run("/certificate/print").firstOrNull { it["name"] == certName }
                    if (now != null && certBerlaku(now)) { berlaku = true; break }
                }
                if (!berlaku) {
                    throw ApiException(
                        "Sertifikat masih dibuat router — di router kecil proses ini bisa " +
                            "1–2 menit. Tunggu sebentar lalu ketuk 'Siapkan API-SSL' lagi; " +
                            "langkah yang sudah beres akan dilewati."
                    )
                }
            }
            client.runCommand(
                "/ip/service/set", "=.id=$svcId",
                "=certificate=$certName", "=disabled=no"
            )
            val after = client.run("/ip/service/print").firstOrNull { it["name"] == "api-ssl" }
            if (after?.get("certificate") != certName) {
                throw ApiException("Gagal memasang sertifikat ke service api-ssl")
            }
            restartApiSsl()
            "API-SSL siap: sertifikat '$certName' dibuat & terpasang, service dimulai " +
                "ulang di port $port agar memuatnya. Putuskan koneksi ini, lalu sambung " +
                "ulang dengan mode API-SSL $port."
        } }

    suspend fun fetchNeighbors(client: MikrotikClient): List<NeighborEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/neighbor/print").map { r ->
                NeighborEntry(
                    id = r[".id"] ?: "",
                    interfaceName = r["interface"] ?: "",
                    address = r["address"] ?: "",
                    macAddress = r["mac-address"] ?: "",
                    identity = r["identity"] ?: "",
                    version = r["version"] ?: "",
                    board = r["board"] ?: "",
                    uptime = r["uptime"] ?: ""
                )
            }
        } }

    /** Perintah API mentah untuk Konsol: path utuh + kata argumen apa adanya. */
    suspend fun runRaw(
        client: MikrotikClient,
        path: String,
        args: List<String>
    ): List<Map<String, String>> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run(path, *args.toTypedArray())
        } }
}
