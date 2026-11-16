package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuickSetConfig(
    val wanInterface: String,
    val bridgeName: String,
    val lanPorts: List<String>,
    val ssid: String,
    val wifiPassword: String,
    val lanAddress: String,
    val poolRanges: String
)

data class QuickSetDefaults(
    val etherNames: List<String>,
    val wirelessNames: List<String>
)

/**
 * QuickSet ala MikroKu (v0.8): setup cepat AP rumahan bergaya gateway.
 * Setiap langkah memeriksa dulu — yang sudah ada dilewati/diselaraskan,
 * tidak ada yang dihapus. Hasil tiap langkah dikembalikan sebagai log.
 */
object QuickSetRepo {

    suspend fun fetchDefaults(client: MikrotikClient): QuickSetDefaults =
        withContext(Dispatchers.IO) { synchronized(client) {
            val ifaces = client.run("/interface/print")
            QuickSetDefaults(
                etherNames = ifaces.filter { it["type"] == "ether" }.mapNotNull { it["name"] },
                wirelessNames = ifaces.filter { it["type"] == "wlan" }.mapNotNull { it["name"] }
            )
        } }

    suspend fun apply(client: MikrotikClient, cfg: QuickSetConfig): List<String> =
        withContext(Dispatchers.IO) { synchronized(client) {
            val log = mutableListOf<String>()

            fun step(name: String, block: () -> String) {
                try {
                    log += "✓ $name — ${block()}"
                } catch (e: Exception) {
                    log += "✗ $name — GAGAL: ${e.message ?: "kesalahan tak dikenal"}"
                }
            }

            // 1) Bridge LAN
            step("Bridge ${cfg.bridgeName}") {
                val exists = client.run("/interface/bridge/print")
                    .any { it["name"] == cfg.bridgeName }
                if (exists) "sudah ada, dipakai apa adanya"
                else {
                    client.runCommand(
                        "/interface/bridge/add",
                        "=name=${cfg.bridgeName}", "=protocol-mode=rstp"
                    )
                    "dibuat baru"
                }
            }

            // 2) Port-port LAN masuk bridge
            step("Port LAN → bridge") {
                val ports = client.run("/interface/bridge/port/print")
                val byInterface = ports.associateBy { it["interface"] ?: "" }
                val added = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                for (port in cfg.lanPorts) {
                    val existing = byInterface[port]
                    when {
                        existing == null -> {
                            client.runCommand(
                                "/interface/bridge/port/add",
                                "=interface=$port", "=bridge=${cfg.bridgeName}"
                    )
                            added += port
                        }
                        existing["bridge"] == cfg.bridgeName -> skipped += "$port (sudah)"
                        else -> skipped += "$port (di bridge ${existing["bridge"]}, dilewati)"
                    }
                }
                buildString {
                    if (added.isNotEmpty()) append("ditambahkan: ${added.joinToString(", ")}")
                    if (skipped.isNotEmpty()) {
                        if (isNotEmpty()) append("; ")
                        append("dilewati: ${skipped.joinToString(", ")}")
                    }
                    if (isEmpty()) append("tidak ada port LAN dipilih")
                }
            }

            // 3) WiFi: profil keamanan + SSID
            step("WiFi ${cfg.ssid}") {
                val wlans = client.run("/interface/wireless/print")
                if (wlans.isEmpty()) {
                    "tidak ada interface wireless — langkah WiFi dilewati"
                } else {
                    val profiles = client.run("/interface/wireless/security-profiles/print")
                    val prof = profiles.firstOrNull { it["name"] == "mikroku-wifi" }
                    if (prof == null) {
                        client.runCommand(
                            "/interface/wireless/security-profiles/add",
                            "=name=mikroku-wifi", "=mode=dynamic-keys",
                            "=authentication-types=wpa2-psk",
                            "=wpa2-pre-shared-key=${cfg.wifiPassword}"
                        )
                    } else {
                        client.runCommand(
                            "/interface/wireless/security-profiles/set",
                            "=.id=${prof[".id"]}", "=mode=dynamic-keys",
                            "=authentication-types=wpa2-psk",
                            "=wpa2-pre-shared-key=${cfg.wifiPassword}"
                        )
                    }
                    for (w in wlans) {
                        client.runCommand(
                            "/interface/wireless/set",
                            "=.id=${w[".id"]}", "=ssid=${cfg.ssid}",
                            "=mode=ap-bridge", "=security-profile=mikroku-wifi",
                            "=disabled=no"
                        )
                    }
                    "SSID + password WPA2 diterapkan ke ${wlans.size} interface"
                }
            }

            // 4) Alamat IP LAN di bridge
            step("IP LAN ${cfg.lanAddress}") {
                val exists = client.run("/ip/address/print").any {
                    it["address"] == cfg.lanAddress && it["interface"] == cfg.bridgeName
                }
                if (exists) "sudah ada"
                else {
                    client.runCommand(
                        "/ip/address/add",
                        "=address=${cfg.lanAddress}", "=interface=${cfg.bridgeName}"
                    )
                    "ditambahkan di ${cfg.bridgeName}"
                }
            }

            // 5) Pool DHCP
            step("Pool mikroku-pool") {
                val pools = client.run("/ip/pool/print")
                val pool = pools.firstOrNull { it["name"] == "mikroku-pool" }
                when {
                    pool == null -> {
                        client.runCommand(
                            "/ip/pool/add", "=name=mikroku-pool", "=ranges=${cfg.poolRanges}"
                        )
                        "dibuat: ${cfg.poolRanges}"
                    }
                    pool["ranges"] != cfg.poolRanges -> {
                        client.runCommand(
                            "/ip/pool/set", "=.id=${pool[".id"]}", "=ranges=${cfg.poolRanges}"
                        )
                        "ranges diperbarui: ${cfg.poolRanges}"
                    }
                    else -> "sudah sesuai"
                }
            }

            // 6) Jaringan DHCP (gateway/DNS)
            val net = networkOf(cfg.lanAddress)
            step("Jaringan DHCP") {
                if (net == null) {
                    "IP LAN tidak valid — langkah dilewati"
                } else {
                    val (network, host) = net
                    val exists = client.run("/ip/dhcp-server/network/print")
                        .any { it["address"] == network }
                    if (exists) "sudah ada untuk $network"
                    else {
                        client.runCommand(
                            "/ip/dhcp-server/network/add",
                            "=address=$network", "=gateway=$host", "=dns-server=$host"
                        )
                        "dibuat untuk $network (gateway $host)"
                    }
                }
            }

            // 7) Server DHCP di bridge
            step("Server DHCP") {
                val servers = client.run("/ip/dhcp-server/print")
                val srv = servers.firstOrNull { it["name"] == "mikroku-dhcp" }
                if (srv == null) {
                    client.runCommand(
                        "/ip/dhcp-server/add",
                        "=name=mikroku-dhcp", "=interface=${cfg.bridgeName}",
                        "=address-pool=mikroku-pool", "=lease-time=1d", "=disabled=no"
                    )
                    "dibuat di ${cfg.bridgeName}"
                } else {
                    client.runCommand(
                        "/ip/dhcp-server/set",
                        "=.id=${srv[".id"]}", "=interface=${cfg.bridgeName}",
                        "=address-pool=mikroku-pool", "=disabled=no"
                    )
                    "diselaraskan ke ${cfg.bridgeName}"
                }
            }

            // 8) DHCP client di WAN (IP dari ISP)
            step("DHCP Client WAN ${cfg.wanInterface}") {
                val exists = client.run("/ip/dhcp-client/print")
                    .any { it["interface"] == cfg.wanInterface }
                if (exists) "sudah ada"
                else {
                    client.runCommand(
                        "/ip/dhcp-client/add",
                        "=interface=${cfg.wanInterface}", "=disabled=no"
                    )
                    "ditambahkan"
                }
            }

            // 9) NAT masquerade keluar WAN
            step("NAT masquerade") {
                val exists = client.run("/ip/firewall/nat/print").any {
                    it["chain"] == "srcnat" && it["action"] == "masquerade" &&
                        it["out-interface"] == cfg.wanInterface
                }
                if (exists) "sudah ada"
                else {
                    client.runCommand(
                        "/ip/firewall/nat/add",
                        "=chain=srcnat", "=action=masquerade",
                        "=out-interface=${cfg.wanInterface}"
                    )
                    "ditambahkan (srcnat → ${cfg.wanInterface})"
                }
            }

            log
        } }

    /** "192.168.88.1/24" → ("192.168.88.0/24", "192.168.88.1"); null bila tak valid. */
    private fun networkOf(cidr: String): Pair<String, String>? {
        val parts = cidr.trim().split("/")
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val octets = parts[0].split(".").map { it.toIntOrNull() ?: return null }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        var ip = 0L
        for (o in octets) ip = (ip shl 8) or o.toLong()
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val net = ip and mask
        val netStr = listOf(24, 16, 8, 0).joinToString(".") { ((net shr it) and 0xFF).toString() }
        return "$netStr/$prefix" to parts[0]
    }
}
