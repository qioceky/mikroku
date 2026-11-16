package id.mikroku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.api.MikrotikClient
import id.mikroku.data.DnsInfo
import id.mikroku.data.FirewallRule
import id.mikroku.data.NetworkRepo
import id.mikroku.data.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Menu fitur jaringan (tab Menu di layar router). */
@Composable
fun NetworkMenuGrid(onOpen: (String) -> Unit) {
    val items = listOf(
        Triple("ip", "Alamat IP", "IP yang terpasang di tiap interface"),
        Triple("dhcp", "Server DHCP", "Server DHCP + pool alamat"),
        Triple("lease", "Lease DHCP", "Perangkat yang sedang dapat alamat"),
        Triple("route", "Routing", "Tabel rute router"),
        Triple("arp", "Tabel ARP", "Pasangan IP ↔ MAC di jaringan lokal"),
        Triple("firewall", "Firewall", "Filter, NAT, mangle — lihat + aktif/nonaktif"),
        Triple("hotspot", "Hotspot", "Server, user, dan sesi aktif"),
        Triple("pppoe", "PPPoE", "Secret PPP + sesi aktif"),
        Triple("queue", "Queue", "Simple queue + queue tree"),
        Triple("tools", "Tools", "Ping & traceroute dari router"),
        Triple("dns", "DNS", "Server DNS, entri static, dan cache")
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((key, title, sub) in pair) {
                    NeoCard(Modifier.weight(1f), onClick = { onOpen(key) }) {
                        Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(sub, color = Muted, fontSize = 11.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Pengarah sub-layar jaringan berdasarkan kunci section. */
@Composable
fun NetworkSectionScreen(section: String, manager: SessionManager, onBack: () -> Unit) {
    when (section) {
        "ip" -> IpAddressScreen(manager, onBack)
        "dhcp" -> DhcpServerScreen(manager, onBack)
        "lease" -> DhcpLeaseScreen(manager, onBack)
        "route" -> RouteScreen(manager, onBack)
        "arp" -> ArpScreen(manager, onBack)
        "firewall" -> FirewallScreen(manager, onBack)
        "hotspot" -> HotspotScreen(manager, onBack)
        "pppoe" -> PppScreen(manager, onBack)
        "queue" -> QueueScreen(manager, onBack)
        "tools" -> ToolsScreen(manager, onBack)
        "dns" -> DnsScreen(manager, onBack)
        else -> IpAddressScreen(manager, onBack)
    }
}

@Composable
fun SectionHeader(title: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("← Menu", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
        Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text("Muat Ulang", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.clickable { onRefresh() })
    }
}

/**
 * Kerangka layar daftar: polling berkala + muat ulang manual + status error.
 * Data terakhir tetap tampil bila refresh gagal.
 */
@Composable
private fun <T> PollingSection(
    title: String,
    manager: SessionManager,
    onBack: () -> Unit,
    intervalMs: Long = 10_000,
    fetch: suspend (MikrotikClient) -> List<T>,
    emptyText: String,
    itemKey: (T) -> String,
    itemContent: @Composable (T) -> Unit
) {
    var rows by remember { mutableStateOf<List<T>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(manager, tick) {
        while (isActive) {
            try {
                rows = manager.run { fetch(it) }
                error = null
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            delay(intervalMs)
        }
    }

    Column {
        SectionHeader(title, onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        error?.let {
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        val list = rows
        when {
            list == null -> Text(
                if (error != null) "Gagal memuat data." else "Memuat…",
                fontWeight = FontWeight.Bold
            )
            list.isEmpty() -> Text(emptyText, color = Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { itemKey(it) }) { entry -> itemContent(entry) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun StateTags(vararg tags: Pair<String, Boolean>) {
    val shown = tags.filter { it.second }.map { it.first }
    if (shown.isNotEmpty()) {
        Text(shown.joinToString(" • "), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun IpAddressScreen(manager: SessionManager, onBack: () -> Unit) {
    PollingSection(
        title = "Alamat IP",
        manager = manager,
        onBack = onBack,
        fetch = { NetworkRepo.fetchIpAddresses(it) },
        emptyText = "Tidak ada alamat IP terpasang.",
        itemKey = { it.id }
    ) { e ->
        NeoCard {
            Row {
                StatusDot(!e.disabled && !e.invalid, Modifier.padding(top = 4.dp, end = 7.dp))
                Column {
                    Text(e.address, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(
                        "di ${e.iface}${if (e.network.isNotBlank()) " • network ${e.network}" else ""}",
                        color = Muted, fontSize = 12.sp
                    )
                    StateTags("nonaktif" to e.disabled, "dinamis" to e.dynamic, "invalid" to e.invalid)
                    if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DhcpServerScreen(manager: SessionManager, onBack: () -> Unit) {
    PollingSection(
        title = "Server DHCP",
        manager = manager,
        onBack = onBack,
        fetch = { NetworkRepo.fetchDhcpServers(it) },
        emptyText = "Tidak ada server DHCP.",
        itemKey = { it.id }
    ) { e ->
        NeoCard {
            Row {
                StatusDot(!e.disabled && !e.invalid, Modifier.padding(top = 4.dp, end = 7.dp))
                Column {
                    Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    val meta = buildString {
                        append("di ${e.iface}")
                        if (e.addressPool.isNotBlank()) append(" • pool ${e.addressPool}")
                        if (e.leaseTime.isNotBlank()) append(" • sewa ${e.leaseTime}")
                    }
                    Text(meta, color = Muted, fontSize = 12.sp)
                    StateTags("nonaktif" to e.disabled, "dinamis" to e.dynamic, "invalid" to e.invalid)
                }
            }
        }
    }
}

@Composable
private fun DhcpLeaseScreen(manager: SessionManager, onBack: () -> Unit) {
    PollingSection(
        title = "Lease DHCP",
        manager = manager,
        onBack = onBack,
        fetch = { NetworkRepo.fetchDhcpLeases(it) },
        emptyText = "Belum ada lease aktif.",
        itemKey = { it.id }
    ) { e ->
        NeoCard {
            Row {
                StatusDot(e.status == "bound" && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                Column {
                    Text(
                        e.hostName.ifBlank { e.address },
                        fontWeight = FontWeight.Black, fontSize = 15.sp
                    )
                    Text(e.address, color = Muted, fontSize = 12.sp)
                    if (e.macAddress.isNotBlank()) Text(e.macAddress, color = Muted, fontSize = 12.sp)
                    val meta = buildString {
                        if (e.status.isNotBlank()) append(e.status)
                        if (e.expiresAfter.isNotBlank()) append(" • sisa ${e.expiresAfter}")
                        if (e.server.isNotBlank()) append(" • server ${e.server}")
                    }
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                    StateTags("dinamis" to e.dynamic, "nonaktif" to e.disabled)
                    if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun RouteScreen(manager: SessionManager, onBack: () -> Unit) {
    PollingSection(
        title = "Routing",
        manager = manager,
        onBack = onBack,
        fetch = { NetworkRepo.fetchRoutes(it) },
        emptyText = "Tabel rute kosong.",
        itemKey = { it.id }
    ) { e ->
        NeoCard {
            Row {
                StatusDot(e.active && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                Column {
                    Text(e.dstAddress, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    if (e.gateway.isNotBlank()) {
                        Text("via ${e.gateway}", color = Muted, fontSize = 12.sp)
                    }
                    val meta = buildString {
                        if (e.distance.isNotBlank()) append("distance ${e.distance}")
                        if (e.active) append(" • aktif")
                    }
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                    StateTags("dinamis" to e.dynamic, "nonaktif" to e.disabled)
                    if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ArpScreen(manager: SessionManager, onBack: () -> Unit) {
    PollingSection(
        title = "Tabel ARP",
        manager = manager,
        onBack = onBack,
        fetch = { NetworkRepo.fetchArp(it) },
        emptyText = "Tabel ARP kosong.",
        itemKey = { it.id }
    ) { e ->
        NeoCard {
            Row {
                StatusDot(e.complete && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                Column {
                    Text(e.address, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    if (e.macAddress.isNotBlank()) Text(e.macAddress, color = Muted, fontSize = 12.sp)
                    if (e.iface.isNotBlank()) Text("di ${e.iface}", color = Muted, fontSize = 12.sp)
                    StateTags("dinamis" to e.dynamic, "nonaktif" to e.disabled, "tidak lengkap" to !e.complete)
                }
            }
        }
    }
}

@Composable
fun SmallButton(
    text: String,
    fill: Color = Color.White,
    textColor: Color = Ink,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .border(2.dp, Ink, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 12.sp, color = textColor)
    }
}

@Composable
private fun DnsScreen(manager: SessionManager, onBack: () -> Unit) {
    var info by remember { mutableStateOf<DnsInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(manager, tick) {
        while (isActive) {
            try {
                info = manager.run { NetworkRepo.fetchDns(it) }
                error = null
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            delay(30_000)
        }
    }

    Column {
        SectionHeader("DNS", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        error?.let {
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        val d = info
        when {
            d == null -> Text(
                if (error != null) "Gagal memuat data." else "Memuat…",
                fontWeight = FontWeight.Bold
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    NeoCard {
                        Text("PENGATURAN DNS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                        Spacer(Modifier.height(4.dp))
                        if (d.servers.isNotBlank()) {
                            Text("Server: ${d.servers}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (d.dynamicServers.isNotBlank()) {
                            Text("Server dinamis: ${d.dynamicServers}", color = Muted, fontSize = 12.sp)
                        }
                        if (d.servers.isBlank() && d.dynamicServers.isBlank()) {
                            Text("Tidak ada server DNS terpasang.", color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Terima request DNS dari jaringan: ${if (d.allowRemoteRequests) "ya" else "tidak"}",
                            color = Muted, fontSize = 12.sp
                        )
                        if (d.cacheSize.isNotBlank()) {
                            Text(
                                "Cache terpakai: ${d.cacheUsed} / ${d.cacheSize} KiB • ${d.cacheEntries} entri",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                    }
                }
                item {
                    Text("DNS Static (${d.staticEntries.size})", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                if (d.staticEntries.isEmpty()) {
                    item { Text("Tidak ada entri DNS static.", color = Muted) }
                } else {
                    items(d.staticEntries, key = { it.id }) { e ->
                        NeoCard {
                            Row {
                                StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                                Column {
                                    Text(e.name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    if (e.address.isNotBlank()) {
                                        Text(e.address, color = Muted, fontSize = 12.sp)
                                    }
                                    val meta = buildString {
                                        if (e.ttl.isNotBlank()) append("TTL ${e.ttl}")
                                        if (e.disabled) append(" • nonaktif")
                                    }
                                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                                    if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun FirewallScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("filter") }
    var rules by remember { mutableStateOf<List<FirewallRule>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var confirmId by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manager, kind, tick) {
        while (isActive) {
            try {
                rules = manager.run { NetworkRepo.fetchFirewall(it, kind) }
                error = null
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            delay(10_000)
        }
    }

    Column {
        SectionHeader("Firewall", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, label) in listOf("filter" to "Filter", "nat" to "NAT", "mangle" to "Mangle")) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (kind == k) MikroYellow else Color.White)
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                        .clickable {
                            kind = k
                            rules = null
                            confirmId = null
                            actionMsg = null
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        actionMsg?.let {
            Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        error?.let {
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        val list = rules
        when {
            list == null -> Text(
                if (error != null) "Gagal memuat data." else "Memuat…",
                fontWeight = FontWeight.Bold
            )
            list.isEmpty() -> Text("Tidak ada rule $kind.", color = Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id.ifBlank { it.hashCode().toString() } }) { rule ->
                    NeoCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.weight(1f)) {
                                StatusDot(
                                    !rule.disabled && !rule.invalid,
                                    Modifier.padding(top = 4.dp, end = 7.dp)
                                )
                                Column {
                                    Text(
                                        "${rule.chain} • ${rule.action}",
                                        fontWeight = FontWeight.Black, fontSize = 14.sp
                                    )
                                    rule.detailLines.forEach {
                                        Text(it, color = Muted, fontSize = 12.sp)
                                    }
                                    if (rule.comment.isNotBlank()) {
                                        Text(rule.comment, color = Muted, fontSize = 11.sp)
                                    }
                                    Text(
                                        "${formatBytes(rule.bytes)} • ${rule.packets} paket",
                                        color = Muted, fontSize = 11.sp
                                    )
                                    StateTags(
                                        "nonaktif" to rule.disabled,
                                        "dinamis" to rule.dynamic,
                                        "invalid" to rule.invalid
                                    )
                                }
                            }
                            if (!rule.dynamic && rule.id.isNotBlank() && confirmId != rule.id) {
                                SmallButton(if (rule.disabled) "Aktifkan" else "Nonaktifkan") {
                                    confirmId = rule.id
                                    actionMsg = null
                                }
                            }
                        }
                        if (confirmId == rule.id) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Yakin ${if (rule.disabled) "aktifkan" else "nonaktifkan"} rule ini?",
                                fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("Batal") { confirmId = null }
                                SmallButton(
                                    "Ya, ${if (rule.disabled) "Aktifkan" else "Nonaktifkan"}",
                                    fill = MikroRed, textColor = Color.White
                                ) {
                                    scope.launch {
                                        try {
                                            manager.run {
                                                NetworkRepo.setFirewallDisabled(
                                                    it, kind, rule.id, !rule.disabled
                                                )
                                            }
                                            actionMsg =
                                                "Rule ${rule.chain}/${rule.action} ${if (rule.disabled) "diaktifkan" else "dinonaktifkan"}."
                                            tick++
                                        } catch (e: Exception) {
                                            actionMsg = friendlyError(e)
                                        }
                                        confirmId = null
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
