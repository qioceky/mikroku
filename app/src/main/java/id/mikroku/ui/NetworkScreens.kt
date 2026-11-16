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
import id.mikroku.data.CrudRepo
import id.mikroku.data.DhcpLeaseEntry
import id.mikroku.data.DnsInfo
import id.mikroku.data.FirewallRule
import id.mikroku.data.IpAddressEntry
import id.mikroku.data.NetworkRepo
import id.mikroku.data.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class MenuItem(val key: String, val icon: String, val title: String, val sub: String)

/** Menu fitur dikelompokkan per kategori; ikon Font Awesome. */
private val menuCategories: List<Pair<String, List<MenuItem>>> = listOf(
    "Setup" to listOf(
        MenuItem("quickset", Fa.BOLT, "QuickSet", "Setup cepat AP rumahan: WiFi, DHCP, NAT")
    ),
    "Jaringan" to listOf(
        MenuItem("ip", Fa.GLOBE, "Alamat IP", "IP yang terpasang di tiap interface"),
        MenuItem("dhcp", Fa.SERVER, "Server DHCP", "Server DHCP + pool alamat"),
        MenuItem("lease", Fa.TICKET, "Lease DHCP", "Perangkat yang sedang dapat alamat"),
        MenuItem("dhcp-client", Fa.CLOUD_DOWN, "DHCP Client", "IP dari ISP, gateway, dan statusnya"),
        MenuItem("pool", Fa.DATABASE, "IP Pool", "Kumpulan alamat untuk DHCP/hotspot/PPP"),
        MenuItem("dns", Fa.BOOK, "DNS", "Server DNS, entri static, dan cache"),
        MenuItem("route", Fa.ROUTE, "Routing", "Tabel rute router"),
        MenuItem("arp", Fa.ARROWS_LR, "Tabel ARP", "Pasangan IP ↔ MAC di jaringan lokal"),
        MenuItem("neighbor", Fa.EYE, "Neighbors", "Perangkat MikroTik lain yang terdeteksi")
    ),
    "Keamanan" to listOf(
        MenuItem("firewall", Fa.SHIELD, "Firewall", "Filter, NAT, mangle — lihat + ubah"),
        MenuItem("addrlist", Fa.ADDRESS_BOOK, "Address List", "Daftar alamat untuk firewall"),
        MenuItem("koneksi", Fa.DIAGRAM, "Koneksi Aktif", "Tracking koneksi yang sedang lewat"),
        MenuItem("service", Fa.BELL, "IP Service", "Service router: api, winbox, www, ssh")
    ),
    "Layanan" to listOf(
        MenuItem("hotspot", Fa.WIFI, "Hotspot", "Server, user, voucher, dan sesi aktif"),
        MenuItem("pppoe", Fa.PLUG, "PPPoE", "Secret PPP + sesi aktif"),
        MenuItem("queue", Fa.LIST_OL, "Queue", "Simple queue + queue tree"),
        MenuItem("wireless", Fa.TOWER, "Wireless", "SSID, mode, dan perangkat terhubung"),
        MenuItem("bridge", Fa.SITEMAP, "Bridge", "Bridge dan port-portnya"),
        MenuItem("vlan", Fa.TAGS, "VLAN", "Interface VLAN 802.1Q")
    ),
    "Sistem & Alat" to listOf(
        MenuItem("sistem", Fa.GEAR, "Sistem", "User, scheduler, script, log, file, backup"),
        MenuItem("tools", Fa.WRENCH, "Tools", "Ping, traceroute, bandwidth test, WoL"),
        MenuItem("konsol", Fa.TERMINAL, "Konsol API", "Jalankan perintah API mentah")
    )
)

@Composable
private fun MenuCard(item: MenuItem, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    NeoCard(modifier, onClick = { onOpen(item.key) }) {
        FaIcon(item.icon, size = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text(item.title, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        Text(item.sub, color = Muted, fontSize = 11.sp)
    }
}

/** Menu fitur jaringan (tab Menu di layar router) — berkelompok + pencarian. */
@Composable
fun NetworkMenuGrid(onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val flat = menuCategories.flatMap { it.second }
    val shown = if (!searching) emptyList() else flat.filter {
        it.title.contains(query, ignoreCase = true) || it.sub.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize()) {
        // Kolom pencarian DI LUAR area scroll + daftar diberi weight(1f):
        // pencarian menempel tetap di atas, hanya kartu menu yang bergeser.
        androidx.compose.material3.OutlinedTextField(
            query, { query = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Cari fitur…") },
            placeholder = { Text("mis. voucher, backup, firewall") }
        )
        Spacer(Modifier.height(12.dp))
        if (searching) {
            if (shown.isEmpty()) {
                Text("Tidak ada fitur yang cocok dengan \"$query\".", color = Muted)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (item in pair) MenuCard(item, Modifier.weight(1f), onOpen)
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((category, catItems) in menuCategories) {
                    item(key = "cat-$category") {
                        Text(
                            category.uppercase(),
                            fontWeight = FontWeight.Black, fontSize = 12.sp, color = Muted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    items(catItems.chunked(2), key = { "row-" + it.first().key }) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (item in pair) MenuCard(item, Modifier.weight(1f), onOpen)
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
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
        "addrlist" -> AddressListScreen(manager, onBack)
        "koneksi" -> ConnectionsScreen(manager, onBack)
        "hotspot" -> HotspotScreen(manager, onBack)
        "pppoe" -> PppScreen(manager, onBack)
        "queue" -> QueueScreen(manager, onBack)
        "tools" -> ToolsScreen(manager, onBack)
        "sistem" -> SystemScreen(manager, onBack)
        "wireless" -> WirelessScreen(manager, onBack)
        "bridge" -> BridgeScreen(manager, onBack)
        "vlan" -> VlanScreen(manager, onBack)
        "dns" -> DnsScreen(manager, onBack)
        "pool" -> PoolScreen(manager, onBack)
        "dhcp-client" -> DhcpClientScreen(manager, onBack)
        "service" -> ServiceScreen(manager, onBack)
        "neighbor" -> NeighborScreen(manager, onBack)
        "konsol" -> ConsoleScreen(manager, onBack)
        "quickset" -> QuickSetScreen(manager, onBack)
        else -> IpAddressScreen(manager, onBack)
    }
}

@Composable
fun SectionHeader(title: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        NeoIconButton(Fa.ARROW_LEFT) { onBack() }
        Text(
            title, fontWeight = FontWeight.Black, fontSize = 17.sp,
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
        NeoIconButton(Fa.ROTATE_RIGHT) { onRefresh() }
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

private fun ipAddressForm(existing: IpAddressEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Alamat IP" else "Tambah Alamat IP",
        isEdit = edit,
        fields = listOf(
            CrudField("address", "Alamat IP + prefix", existing?.address ?: "",
                hint = "mis. 192.168.88.1/24", required = true),
            CrudField("interface", "Interface", existing?.iface ?: "",
                hint = "mis. ether2, bridge1, wlan1", required = true),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/ip/address", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/ip/address", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun IpAddressScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("Alamat IP", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        NeoButton("+ Tambah Alamat IP", fill = CardFill) { form = ipAddressForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "ip", tick = tick, onChanged = { tick++ },
            fetch = { NetworkRepo.fetchIpAddresses(it) },
            emptyText = "Tidak ada alamat IP terpasang.",
            itemKey = { it.id }
        ) { e, onChanged ->
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
                if (!e.dynamic) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = ipAddressForm(e) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/ip/address", e.id) }
                        }, onDone = onChanged)
                    }
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
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("Lease DHCP", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "lease", tick = tick, onChanged = { tick++ },
            fetch = { NetworkRepo.fetchDhcpLeases(it) },
            emptyText = "Belum ada lease aktif.",
            itemKey = { it.id }
        ) { e, onChanged ->
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.dynamic) {
                        ConfirmActionButton("Jadikan Statis", run = {
                            manager.runNoRetry { CrudRepo.makeLeaseStatic(it, e.id) }
                        }, onDone = onChanged)
                    }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/ip/dhcp-server/lease", e.id) }
                    }, onDone = onChanged)
                }
            }
        }
    }
}

private fun routeForm(existing: id.mikroku.data.RouteEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Rute" else "Tambah Rute Statis",
        isEdit = edit,
        fields = listOf(
            CrudField("dst-address", "Jaringan tujuan", existing?.dstAddress ?: "",
                hint = "mis. 0.0.0.0/0 atau 10.10.10.0/24", required = true),
            CrudField("gateway", "Gateway", existing?.gateway ?: "",
                hint = "IP gateway atau nama interface", required = true),
            CrudField("distance", "Distance", existing?.distance ?: "",
                hint = "kosong = 1"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/ip/route", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/ip/route", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun RouteScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("Routing", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        NeoButton("+ Tambah Rute", fill = CardFill) { form = routeForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "route", tick = tick, onChanged = { tick++ },
            fetch = { NetworkRepo.fetchRoutes(it) },
            emptyText = "Tabel rute kosong.",
            itemKey = { it.id }
        ) { e, onChanged ->
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
                if (!e.dynamic) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = routeForm(e) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/ip/route", e.id) }
                        }, onDone = onChanged)
                    }
                }
            }
        }
    }
}

private fun addressListForm(existing: id.mikroku.data.AddressListEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Address List" else "Tambah Address List",
        isEdit = edit,
        fields = listOf(
            CrudField("list", "Nama list", existing?.list ?: "",
                hint = "mis. blokir, lokal, vpn", required = true),
            CrudField("address", "Alamat IP / jaringan", existing?.address ?: "",
                hint = "mis. 192.168.88.10 atau 10.0.0.0/8", required = true),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/ip/firewall/address-list", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/ip/firewall/address-list", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun AddressListScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("Address List", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        NeoButton("+ Tambah Entri", fill = CardFill) { form = addressListForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "addrlist", tick = tick, onChanged = { tick++ },
            fetch = { NetworkRepo.fetchAddressList(it) },
            emptyText = "Address list kosong.",
            itemKey = { it.id }
        ) { e, onChanged ->
            NeoCard {
                Text(e.address, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("list: ${e.list}", color = Muted, fontSize = 12.sp)
                StateTags("nonaktif" to e.disabled, "dinamis" to e.dynamic)
                if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                if (!e.dynamic) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = addressListForm(e) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/ip/firewall/address-list", e.id) }
                        }, onDone = onChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionsScreen(manager: SessionManager, onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<id.mikroku.data.ConnectionEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var proto by remember { mutableStateOf("semua") }
    var cari by remember { mutableStateOf("") }

    LaunchedEffect(manager, tick) {
        while (isActive) {
            try {
                rows = manager.run { NetworkRepo.fetchConnections(it) }
                error = null
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            delay(10_000)
        }
    }

    val all = rows
    val filtered = all?.filter { e ->
        (proto == "semua" || e.protocol.equals(proto, ignoreCase = true)) &&
            (cari.isBlank() || e.srcAddress.contains(cari, true) || e.dstAddress.contains(cari, true))
    }

    Column {
        SectionHeader("Koneksi Aktif", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tabel tracking firewall — koneksi yang sedang lewat router (maks 500 entri pertama).",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(
            cari, { cari = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Cari alamat IP…") }
        )
        Spacer(Modifier.height(8.dp))
        KindChips(
            listOf("semua" to "Semua", "tcp" to "TCP", "udp" to "UDP", "icmp" to "ICMP"),
            proto
        ) { proto = it }
        Spacer(Modifier.height(10.dp))
        error?.let {
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        when {
            all == null -> Text(
                if (error != null) "Gagal memuat data." else "Memuat…",
                fontWeight = FontWeight.Bold
            )
            filtered.isNullOrEmpty() -> Text("Tidak ada koneksi yang cocok.", color = Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { e ->
                    NeoCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                e.protocol.uppercase().ifBlank { "-" },
                                fontWeight = FontWeight.Black, fontSize = 13.sp
                            )
                            if (e.state.isNotBlank()) {
                                Text(e.state, color = Muted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${e.srcAddress} → ${e.dstAddress}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val meta = buildString {
                            append("↓ ${formatBytes(e.origBytes)} • ↑ ${formatBytes(e.replBytes)}")
                            if (e.timeout.isNotBlank()) append(" • sisa ${e.timeout}")
                        }
                        Text(meta, color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        ConfirmActionButton("Putus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/ip/firewall/connection", e.id) }
                        }, onDone = { tick++ })
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
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
    fill: Color = CardFill,
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

private fun firewallForm(kind: String, existing: FirewallRule?): CrudFormSpec {
    val edit = existing != null
    val chains = when (kind) {
        "nat" -> listOf("srcnat", "dstnat")
        "mangle" -> listOf("prerouting", "input", "forward", "output", "postrouting")
        else -> listOf("input", "forward", "output")
    }
    val actions = when (kind) {
        "nat" -> listOf("masquerade", "src-nat", "dst-nat", "redirect", "netmap", "accept")
        "mangle" -> listOf("mark-connection", "mark-packet", "mark-routing", "change-mss", "accept", "log")
        else -> listOf("accept", "drop", "reject", "tarpit", "log", "jump")
    }
    val fields = mutableListOf(
        CrudField("chain", "Chain", existing?.chain ?: chains.first(), required = true, options = chains),
        CrudField("action", "Action", existing?.action ?: actions.first(), required = true, options = actions),
        CrudField("src-address", "Src. Address", existing?.srcAddress ?: "", hint = "kosong = semua"),
        CrudField("dst-address", "Dst. Address", existing?.dstAddress ?: "", hint = "kosong = semua"),
        CrudField("protocol", "Protokol", existing?.protocol ?: "", options = listOf("tcp", "udp", "icmp")),
        CrudField("dst-port", "Dst. Port", existing?.dstPort ?: "", hint = "mis. 80, 443, 8000-9000"),
        CrudField("in-interface", "In. Interface", existing?.inInterface ?: ""),
        CrudField("out-interface", "Out. Interface", existing?.outInterface ?: "")
    )
    if (kind == "nat") {
        fields += CrudField("to-addresses", "To Addresses", existing?.toAddresses ?: "")
        fields += CrudField("to-ports", "To Ports", existing?.toPorts ?: "")
    }
    if (kind == "mangle") {
        fields += CrudField("new-connection-mark", "New Conn. Mark", existing?.newConnectionMark ?: "")
        fields += CrudField("new-packet-mark", "New Packet Mark", existing?.newPacketMark ?: "")
        fields += CrudField("new-routing-mark", "New Routing Mark", existing?.newRoutingMark ?: "")
    }
    fields += CrudField("comment", "Komentar", existing?.comment ?: "")
    val path = "/ip/firewall/$kind"
    val kindLabel = kind.replaceFirstChar { it.uppercase() }
    return CrudFormSpec(
        title = if (edit) "Edit Rule $kindLabel" else "Tambah Rule $kindLabel",
        isEdit = edit,
        fields = fields,
        submit = { client, v ->
            if (edit) {
                // Saat edit, kolom kosong (selain komentar) tidak dikirim:
                // mencegah trap dari properti enum yang tidak bisa dikosongkan.
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, path, existing!!.id, attrs)
            } else {
                CrudRepo.add(client, path, v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun FirewallScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("filter") }
    var rules by remember { mutableStateOf<List<FirewallRule>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var confirmId by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val scope = rememberCoroutineScope()

    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }

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
                        .background(if (kind == k) MikroYellow else CardFill)
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
        NeoButton("+ Tambah Rule", fill = CardFill) { form = firewallForm(kind, null) }
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
                        if (!rule.dynamic && rule.id.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("Edit") { form = firewallForm(kind, rule) }
                                ConfirmActionButton("Hapus", run = {
                                    manager.runNoRetry {
                                        CrudRepo.remove(it, "/ip/firewall/$kind", rule.id)
                                    }
                                }, onDone = { tick++ })
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
