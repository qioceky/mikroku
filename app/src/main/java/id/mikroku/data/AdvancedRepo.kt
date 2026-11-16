package id.mikroku.data

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
