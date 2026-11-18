package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IpAddressEntry(
    val id: String,
    val address: String,
    val network: String,
    val iface: String,
    val disabled: Boolean,
    val dynamic: Boolean,
    val invalid: Boolean,
    val comment: String
)

data class DhcpServerEntry(
    val id: String,
    val name: String,
    val iface: String,
    val addressPool: String,
    val leaseTime: String,
    val disabled: Boolean,
    val invalid: Boolean,
    val dynamic: Boolean
)

data class DhcpLeaseEntry(
    val id: String,
    val address: String,
    val macAddress: String,
    val hostName: String,
    val server: String,
    val status: String,
    val expiresAfter: String,
    val dynamic: Boolean,
    val disabled: Boolean,
    val comment: String
)

data class RouteEntry(
    val id: String,
    val dstAddress: String,
    val gateway: String,
    val distance: String,
    val active: Boolean,
    val dynamic: Boolean,
    val disabled: Boolean,
    val comment: String
)

data class AddressListEntry(
    val id: String,
    val list: String,
    val address: String,
    val comment: String,
    val disabled: Boolean,
    val dynamic: Boolean
)

data class ConnectionEntry(
    val id: String,
    val protocol: String,
    val srcAddress: String,
    val dstAddress: String,
    val state: String,
    val timeout: String,
    val origBytes: Long,
    val replBytes: Long
)

data class ArpEntry(
    val id: String,
    val address: String,
    val macAddress: String,
    val iface: String,
    val complete: Boolean,
    val dynamic: Boolean,
    val disabled: Boolean
)

data class DnsInfo(
    val servers: String,
    val dynamicServers: String,
    val allowRemoteRequests: Boolean,
    val cacheUsed: String,
    val cacheSize: String,
    val cacheEntries: Int,
    val staticEntries: List<DnsStaticEntry>
)

data class DnsStaticEntry(
    val id: String,
    val name: String,
    val address: String,
    val ttl: String,
    val disabled: Boolean,
    val comment: String
)

data class FirewallRule(
    val id: String,
    val chain: String,
    val action: String,
    val disabled: Boolean,
    val dynamic: Boolean,
    val invalid: Boolean,
    val comment: String,
    val bytes: Long,
    val packets: Long,
    /** Baris ringkasan siap tampil (src/dst, protokol, interface, mark, dll). */
    val detailLines: List<String>,
    // Nilai mentah untuk form edit (v0.4).
    val srcAddress: String = "",
    val dstAddress: String = "",
    val protocol: String = "",
    val dstPort: String = "",
    val inInterface: String = "",
    val outInterface: String = "",
    val toAddresses: String = "",
    val toPorts: String = "",
    val newConnectionMark: String = "",
    val newPacketMark: String = "",
    val newRoutingMark: String = ""
)

/** Repo baca/tulis ringan untuk inventaris jaringan (v0.2). */
object NetworkRepo {

    suspend fun fetchIpAddresses(client: MikrotikClient): List<IpAddressEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/address/print").map { r ->
                IpAddressEntry(
                    id = r[".id"] ?: r["address"] ?: "",
                    address = r["address"] ?: "-",
                    network = r["network"] ?: "",
                    iface = r["interface"] ?: "",
                    disabled = r["disabled"] == "true",
                    dynamic = r["dynamic"] == "true",
                    invalid = r["invalid"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchDhcpServers(client: MikrotikClient): List<DhcpServerEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/dhcp-server/print").map { r ->
                DhcpServerEntry(
                    id = r[".id"] ?: r["name"] ?: "",
                    name = r["name"] ?: "-",
                    iface = r["interface"] ?: "",
                    addressPool = r["address-pool"] ?: "",
                    leaseTime = r["lease-time"] ?: "",
                    disabled = r["disabled"] == "true",
                    invalid = r["invalid"] == "true",
                    dynamic = r["dynamic"] == "true"
                )
            }
        } }

    suspend fun fetchDhcpLeases(client: MikrotikClient): List<DhcpLeaseEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/dhcp-server/lease/print").map { r ->
                DhcpLeaseEntry(
                    id = r[".id"] ?: (r["address"] ?: ""),
                    address = r["active-address"] ?: r["address"] ?: "-",
                    macAddress = r["active-mac-address"] ?: r["mac-address"] ?: "",
                    hostName = r["host-name"] ?: "",
                    server = r["server"] ?: "",
                    status = r["status"] ?: "",
                    expiresAfter = r["expires-after"] ?: "",
                    dynamic = r["dynamic"] == "true",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchRoutes(client: MikrotikClient): List<RouteEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/route/print").map { r ->
                RouteEntry(
                    id = r[".id"] ?: (r["dst-address"] ?: ""),
                    dstAddress = r["dst-address"] ?: "-",
                    gateway = (r["gateway"] ?: "").ifBlank { r["gateway-status"] ?: "" },
                    distance = r["distance"] ?: "",
                    active = r["active"] == "true",
                    dynamic = r["dynamic"] == "true",
                    disabled = r["disabled"] == "true",
                    comment = r["comment"] ?: ""
                )
            }
        } }

    suspend fun fetchAddressList(client: MikrotikClient): List<AddressListEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/firewall/address-list/print").map { r ->
                AddressListEntry(
                    id = r[".id"] ?: (r["address"] ?: ""),
                    list = r["list"] ?: "",
                    address = r["address"] ?: "-",
                    comment = r["comment"] ?: "",
                    disabled = r["disabled"] == "true",
                    dynamic = r["dynamic"] == "true"
                )
            }
        } }

    /** Koneksi aktif dari tabel tracking firewall (dibatasi 500 entri pertama). */
    suspend fun fetchConnections(client: MikrotikClient): List<ConnectionEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run(
                "/ip/firewall/connection/print",
                "=.proplist=.id,protocol,src-address,dst-address,tcp-state,connection-state,timeout,orig-bytes,repl-bytes"
            ).take(500).map { r ->
                ConnectionEntry(
                    id = r[".id"] ?: "",
                    protocol = r["protocol"] ?: "",
                    srcAddress = r["src-address"] ?: "-",
                    dstAddress = r["dst-address"] ?: "-",
                    state = r["tcp-state"] ?: (r["connection-state"] ?: ""),
                    timeout = r["timeout"] ?: "",
                    origBytes = r["orig-bytes"]?.toLongOrNull() ?: 0L,
                    replBytes = r["repl-bytes"]?.toLongOrNull() ?: 0L
                )
            }
        } }

    suspend fun fetchArp(client: MikrotikClient): List<ArpEntry> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/arp/print").map { r ->
                ArpEntry(
                    id = r[".id"] ?: (r["address"] ?: ""),
                    address = r["address"] ?: "-",
                    macAddress = r["mac-address"] ?: "",
                    iface = r["interface"] ?: "",
                    complete = r["complete"] == "true",
                    dynamic = r["dynamic"] == "true",
                    disabled = r["disabled"] == "true"
                )
            }
        } }

    suspend fun fetchDns(client: MikrotikClient): DnsInfo =
        withContext(Dispatchers.IO) { synchronized(client) {
            val settings = client.run("/ip/dns/print").firstOrNull() ?: emptyMap()
            val statics = try {
                client.run("/ip/dns/static/print")
            } catch (e: Exception) {
                emptyList()
            }
            val cacheCount = try {
                client.run("/ip/dns/cache/print").size
            } catch (e: Exception) {
                0
            }
            DnsInfo(
                servers = settings["servers"] ?: "",
                dynamicServers = settings["dynamic-servers"] ?: "",
                allowRemoteRequests = settings["allow-remote-requests"] == "true",
                cacheUsed = settings["cache-used"] ?: "",
                cacheSize = settings["cache-size"] ?: "",
                cacheEntries = cacheCount,
                staticEntries = statics.map { r ->
                    DnsStaticEntry(
                        id = r[".id"] ?: (r["name"] ?: ""),
                        name = r["name"] ?: r["regexp"] ?: "-",
                        address = r["address"] ?: "",
                        ttl = r["ttl"] ?: "",
                        disabled = r["disabled"] == "true",
                        comment = r["comment"] ?: ""
                    )
                }
            )
        } }

    /** [kind]: "filter", "nat", atau "mangle". */
    suspend fun fetchFirewall(client: MikrotikClient, kind: String): List<FirewallRule> =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.run("/ip/firewall/$kind/print").map { r ->
                FirewallRule(
                    id = r[".id"] ?: "",
                    chain = r["chain"] ?: "",
                    action = r["action"] ?: "",
                    disabled = r["disabled"] == "true",
                    dynamic = r["dynamic"] == "true",
                    invalid = r["invalid"] == "true",
                    comment = r["comment"] ?: "",
                    bytes = r["bytes"]?.toLongOrNull() ?: 0L,
                    packets = r["packets"]?.toLongOrNull() ?: 0L,
                    detailLines = firewallDetailLines(kind, r),
                    srcAddress = r["src-address"] ?: "",
                    dstAddress = r["dst-address"] ?: "",
                    protocol = r["protocol"] ?: "",
                    dstPort = r["dst-port"] ?: "",
                    inInterface = r["in-interface"] ?: "",
                    outInterface = r["out-interface"] ?: "",
                    toAddresses = r["to-addresses"] ?: "",
                    toPorts = r["to-ports"] ?: "",
                    newConnectionMark = r["new-connection-mark"] ?: "",
                    newPacketMark = r["new-packet-mark"] ?: "",
                    newRoutingMark = r["new-routing-mark"] ?: ""
                )
            }
        } }

    /** Aktif/nonaktifkan satu rule firewall. Perlu policy write di user API. */
    suspend fun setFirewallDisabled(
        client: MikrotikClient,
        kind: String,
        ruleId: String,
        disabled: Boolean
    ) = withContext(Dispatchers.IO) { synchronized(client) {
        client.runCommand(
            "/ip/firewall/$kind/set",
            "=.id=$ruleId",
            "=disabled=${if (disabled) "yes" else "no"}"
        )
    } }

    private fun firewallDetailLines(kind: String, r: Map<String, String>): List<String> {
        val lines = mutableListOf<String>()
        val src = r["src-address"] ?: ""
        val dst = r["dst-address"] ?: ""
        if (src.isNotBlank() || dst.isNotBlank()) {
            lines.add("${src.ifBlank { "semua" }} → ${dst.ifBlank { "semua" }}")
        }
        val proto = r["protocol"] ?: ""
        val dport = r["dst-port"] ?: ""
        val sport = r["src-port"] ?: ""
        if (proto.isNotBlank() || dport.isNotBlank() || sport.isNotBlank()) {
            val ports = when {
                sport.isNotBlank() && dport.isNotBlank() -> " port $sport → $dport"
                dport.isNotBlank() -> " port $dport"
                sport.isNotBlank() -> " port $sport"
                else -> ""
            }
            lines.add((proto.ifBlank { "ip" }) + ports)
        }
        val inIf = r["in-interface"] ?: ""
        val outIf = r["out-interface"] ?: ""
        if (inIf.isNotBlank() || outIf.isNotBlank()) {
            lines.add("in: ${inIf.ifBlank { "-" }} • out: ${outIf.ifBlank { "-" }}")
        }
        when (kind) {
            "nat" -> {
                val toAddr = r["to-addresses"] ?: ""
                val toPorts = r["to-ports"] ?: ""
                if (toAddr.isNotBlank() || toPorts.isNotBlank()) {
                    lines.add("ke: ${toAddr.ifBlank { "-" }}${if (toPorts.isNotBlank()) ":$toPorts" else ""}")
                }
            }
            "mangle" -> {
                val marks = listOf(
                    "new-connection-mark" to "conn-mark",
                    "new-packet-mark" to "packet-mark",
                    "new-routing-mark" to "routing-mark"
                )
                for ((key, label) in marks) {
                    val v = r[key] ?: ""
                    if (v.isNotBlank()) lines.add("$label: $v")
                }
            }
        }
        return lines
    }
}
