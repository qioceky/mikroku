package id.mikroku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.AdvancedRepo
import id.mikroku.data.BridgeEntry
import id.mikroku.data.BridgePortEntry
import id.mikroku.data.PoolEntry
import id.mikroku.data.SecurityProfileEntry
import id.mikroku.data.ServiceEntry
import id.mikroku.data.SessionManager
import id.mikroku.data.VlanEntry
import id.mikroku.data.WirelessEntry
import id.mikroku.data.CrudRepo
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Wireless

private fun wirelessForm(existing: WirelessEntry): CrudFormSpec {
    return CrudFormSpec(
        title = "Edit Wireless ${existing.name}",
        isEdit = true,
        fields = listOf(
            CrudField("ssid", "SSID", existing.ssid, required = true),
            CrudField("mode", "Mode", existing.mode.ifBlank { "ap-bridge" },
                options = listOf("ap-bridge", "station", "station-bridge", "bridge", "wds-slave")),
            CrudField("comment", "Komentar", existing.comment)
        ),
        submit = { client, v ->
            val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
            CrudRepo.set(client, "/interface/wireless", existing.id, attrs)
        }
    )
}

@Composable
fun WirelessScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("interface") }
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("Wireless", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(
            listOf("interface" to "Interface", "terhubung" to "Terhubung", "keamanan" to "Keamanan"),
            kind
        ) { kind = it }
        Spacer(Modifier.height(10.dp))
        if (kind == "interface") {
            PollingCards(
                manager = manager, resetKey = "wl-if", tick = tick, onChanged = { tick++ },
                fetch = { AdvancedRepo.fetchWireless(it) },
                emptyText = "Tidak ada interface wireless (paket wireless tidak aktif?).",
                itemKey = { it.id }
            ) { e, changed ->
                NeoCard {
                    Row {
                        StatusDot(e.running && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text("SSID: ${e.ssid.ifBlank { "-" }}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val meta = buildString {
                                if (e.mode.isNotBlank()) append(e.mode)
                                if (e.band.isNotBlank()) append(" • ${e.band}")
                                if (e.frequency.isNotBlank()) append(" • ${e.frequency} MHz")
                                if (e.channelWidth.isNotBlank()) append(" • ${e.channelWidth}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                            StateTags("nonaktif" to e.disabled, "tidak jalan" to !e.running)
                            if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                        manager.run {
                            CrudRepo.set(it, "/interface/wireless", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                        }
                    }, onDone = changed)
                    Spacer(Modifier.height(8.dp))
                    SmallButton("Edit SSID / Mode") { form = wirelessForm(e) }
                }
            }
        } else if (kind == "terhubung") {
            PollingCards(
                manager = manager, resetKey = "wl-reg", tick = tick, onChanged = { tick++ },
                fetch = { AdvancedRepo.fetchWirelessClients(it) },
                emptyText = "Belum ada perangkat yang terhubung.",
                itemKey = { it.id }
            ) { e, _ ->
                NeoCard {
                    Text(e.macAddress, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    val meta = buildString {
                        if (e.interfaceName.isNotBlank()) append(e.interfaceName)
                        if (e.signalStrength.isNotBlank()) append(" • sinyal ${e.signalStrength}")
                        if (e.lastIp.isNotBlank()) append(" • IP ${e.lastIp}")
                    }
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                    val rates = buildString {
                        if (e.txRate.isNotBlank()) append("TX ${e.txRate}")
                        if (e.rxRate.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("RX ${e.rxRate}")
                        }
                        if (e.uptime.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("terhubung ${e.uptime}")
                        }
                    }
                    if (rates.isNotBlank()) Text(rates, color = Muted, fontSize = 11.sp)
                }
            }
        } else {
            NeoButton("+ Tambah Profil", fill = Color.White) { form = securityProfileForm(null) }
            Spacer(Modifier.height(10.dp))
            PollingCards(
                manager = manager, resetKey = "wl-sec", tick = tick, onChanged = { tick++ },
                fetch = { AdvancedRepo.fetchSecurityProfiles(it) },
                emptyText = "Tidak ada profil keamanan.",
                itemKey = { it.id }
            ) { e, changed ->
                NeoCard {
                    Text(
                        e.name + if (e.isDefault) " (default)" else "",
                        fontWeight = FontWeight.Black, fontSize = 15.sp
                    )
                    val meta = buildString {
                        if (e.mode.isNotBlank()) append(e.mode)
                        if (e.authTypes.isNotBlank()) append(" • ${e.authTypes}")
                    }
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                    Text(
                        "Password WPA2: ${if (e.hasWpa2Key) "sudah diset" else "belum diset"}",
                        color = Muted, fontSize = 11.sp
                    )
                    if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = securityProfileForm(e) }
                        if (!e.isDefault) {
                            ConfirmActionButton("Hapus", run = {
                                manager.runNoRetry {
                                    CrudRepo.remove(it, "/interface/wireless/security-profiles", e.id)
                                }
                            }, onDone = changed)
                        }
                    }
                }
            }
        }
    }
}

private fun securityProfileForm(existing: SecurityProfileEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Profil Keamanan" else "Tambah Profil Keamanan",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama Profil", existing?.name ?: "", required = true),
            CrudField("mode", "Mode", existing?.mode ?: "dynamic-keys",
                options = listOf("dynamic-keys", "static-keys-optional", "static-keys-required", "none")),
            CrudField("authentication-types", "Autentikasi", existing?.authTypes ?: "wpa2-psk",
                options = listOf("wpa2-psk", "wpa-psk,wpa2-psk", "wpa-psk")),
            CrudField("wpa2-pre-shared-key", "Password WPA2", "",
                hint = if (edit) "Kosongkan bila tidak diubah" else "Password WiFi (min. 8 karakter)",
                skipBlankOnEdit = true),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/interface/wireless/security-profiles", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/interface/wireless/security-profiles", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

// ---------------------------------------------------------------- Bridge

private fun bridgeForm(existing: BridgeEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Bridge" else "Tambah Bridge",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama Bridge", existing?.name ?: "", required = true),
            CrudField("protocol-mode", "Protocol Mode", existing?.protocolMode ?: "rstp",
                options = listOf("rstp", "stp", "mstp", "none")),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/interface/bridge", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/interface/bridge", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

private fun bridgePortForm(existing: BridgePortEntry?): CrudFormSpec {
    val edit = existing != null
    val fields = mutableListOf<CrudField>()
    if (!edit) {
        fields += CrudField("interface", "Interface", "", required = true,
            hint = "nama interface, mis. ether2")
    }
    fields += CrudField("bridge", "Bridge", existing?.bridge ?: "", required = true)
    fields += CrudField("pvid", "PVID", existing?.pvid ?: "", hint = "kosong = 1 (VLAN ID port)")
    fields += CrudField("comment", "Komentar", existing?.comment ?: "")
    return CrudFormSpec(
        title = if (edit) "Edit Port Bridge" else "Tambah Port ke Bridge",
        isEdit = edit,
        fields = fields,
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/interface/bridge/port", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/interface/bridge/port", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
fun BridgeScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("bridge") }
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("Bridge", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(listOf("bridge" to "Bridge", "port" to "Port"), kind) { kind = it }
        Spacer(Modifier.height(10.dp))
        if (kind == "bridge") {
            NeoButton("+ Tambah Bridge", fill = Color.White) { form = bridgeForm(null) }
            Spacer(Modifier.height(10.dp))
            PollingCards(
                manager = manager, resetKey = "br", tick = tick, onChanged = { tick++ },
                fetch = { AdvancedRepo.fetchBridges(it) },
                emptyText = "Belum ada bridge.",
                itemKey = { it.id }
            ) { e, changed ->
                NeoCard {
                    Row {
                        StatusDot(e.running && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                if (e.protocolMode.isNotBlank()) append("protokol ${e.protocolMode}")
                                if (e.macAddress.isNotBlank()) append(" • ${e.macAddress}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                            StateTags("nonaktif" to e.disabled, "tidak jalan" to !e.running)
                            if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                        manager.run {
                            CrudRepo.set(it, "/interface/bridge", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                        }
                    }, onDone = changed)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = bridgeForm(e) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/interface/bridge", e.id) }
                        }, onDone = changed)
                    }
                }
            }
        } else {
            NeoButton("+ Tambah Port", fill = Color.White) { form = bridgePortForm(null) }
            Spacer(Modifier.height(10.dp))
            PollingCards(
                manager = manager, resetKey = "br-port", tick = tick, onChanged = { tick++ },
                fetch = { AdvancedRepo.fetchBridgePorts(it) },
                emptyText = "Belum ada port bridge.",
                itemKey = { it.id }
            ) { e, changed ->
                NeoCard {
                    Row {
                        StatusDot(!e.disabled && e.status != "inactive", Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.interfaceName, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                if (e.bridge.isNotBlank()) append("bridge ${e.bridge}")
                                if (e.pvid.isNotBlank() && e.pvid != "1") append(" • PVID ${e.pvid}")
                                if (e.status.isNotBlank()) append(" • ${e.status}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                            StateTags("nonaktif" to e.disabled)
                            if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                        manager.run {
                            CrudRepo.set(it, "/interface/bridge/port", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                        }
                    }, onDone = changed)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = bridgePortForm(e) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/interface/bridge/port", e.id) }
                        }, onDone = changed)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- VLAN

private fun vlanForm(existing: VlanEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit VLAN" else "Tambah VLAN",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama VLAN", existing?.name ?: "", required = true),
            CrudField("vlan-id", "VLAN ID", existing?.vlanId ?: "", required = true,
                hint = "1–4094"),
            CrudField("interface", "Interface Induk", existing?.interfaceName ?: "", required = true,
                hint = "mis. bridge1 atau ether1"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/interface/vlan", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/interface/vlan", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
fun VlanScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("VLAN", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        NeoButton("+ Tambah VLAN", fill = Color.White) { form = vlanForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "vlan", tick = tick, onChanged = { tick++ },
            fetch = { AdvancedRepo.fetchVlans(it) },
            emptyText = "Belum ada interface VLAN.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Row {
                    StatusDot(e.running && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                    Column {
                        Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        val meta = buildString {
                            if (e.vlanId.isNotBlank()) append("VLAN ID ${e.vlanId}")
                            if (e.interfaceName.isNotBlank()) append(" • di ${e.interfaceName}")
                        }
                        if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                        StateTags("nonaktif" to e.disabled, "tidak jalan" to !e.running)
                        if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                    manager.run {
                        CrudRepo.set(it, "/interface/vlan", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                    }
                }, onDone = changed)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Edit") { form = vlanForm(e) }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/interface/vlan", e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Konsol API

@Composable
fun ConsoleScreen(manager: SessionManager, onBack: () -> Unit) {
    var command by remember { mutableStateOf("/system/resource/print") }
    var argsText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun execute() {
        val path = command.trim()
        if (busy) return
        if (!path.startsWith("/")) {
            error = "Perintah harus diawali / — contoh: /system/resource/print"
            return
        }
        val words = argsText.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
            when {
                line.startsWith("=") || line.startsWith("?") || line.startsWith(".") -> line
                line.contains("=") -> "=$line"
                else -> line
            }
        }
        scope.launch {
            busy = true
            error = null
            output = null
            try {
                val rows = manager.runNoRetry { AdvancedRepo.runRaw(it, path, words) }
                output = if (rows.isEmpty()) {
                    "(perintah selesai — tidak ada data balasan)"
                } else {
                    rows.mapIndexed { i, row ->
                        buildString {
                            append("— baris ${i + 1} —")
                            row.forEach { (k, v) -> append("\n$k = $v") }
                        }
                    }.joinToString("\n\n")
                }
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            busy = false
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("Konsol API", onBack) { }
        Spacer(Modifier.height(10.dp))
        Text(
            "Jalankan perintah API RouterOS apa adanya, memakai user API yang tersimpan. " +
                "Hati-hati: perintah tulis (add/set/remove/reboot) langsung dijalankan.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            command, { command = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Perintah") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            argsText, { argsText = it },
            Modifier.fillMaxWidth(), minLines = 3,
            label = { Text("Argumen (satu per baris, key=value)") },
            supportingText = { Text("contoh: .proplist=name,address — kosongkan bila tidak perlu") }
        )
        Spacer(Modifier.height(10.dp))
        NeoButton("Jalankan", enabled = !busy && command.isNotBlank()) { execute() }
        if (busy) {
            Spacer(Modifier.height(10.dp))
            Text("Menjalankan…", fontWeight = FontWeight.Bold)
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        output?.let {
            Spacer(Modifier.height(10.dp))
            NeoCard {
                Text("Hasil", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- IP Pool (v0.7)

private fun poolForm(existing: PoolEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit IP Pool" else "Tambah IP Pool",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama Pool", existing?.name ?: "", required = true),
            CrudField("ranges", "Ranges", existing?.ranges ?: "", required = true,
                hint = "mis. 192.168.88.10-192.168.88.100"),
            CrudField("next-pool", "Next Pool", existing?.nextPool ?: "", hint = "opsional"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/ip/pool", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/ip/pool", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
fun PoolScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("IP Pool", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        NeoButton("+ Tambah Pool", fill = Color.White) { form = poolForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "pool", tick = tick, onChanged = { tick++ },
            fetch = { AdvancedRepo.fetchPools(it) },
            emptyText = "Belum ada IP pool.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                if (e.ranges.isNotBlank()) {
                    Text(e.ranges, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                if (e.nextPool.isNotBlank() && e.nextPool != "none") {
                    Text("next pool: ${e.nextPool}", color = Muted, fontSize = 11.sp)
                }
                if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Edit") { form = poolForm(e) }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/ip/pool", e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- DHCP Client (v0.7)

@Composable
fun DhcpClientScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("DHCP Client", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "dhcp-client", tick = tick, onChanged = { tick++ },
            fetch = { AdvancedRepo.fetchDhcpClients(it) },
            emptyText = "Tidak ada DHCP client di router ini.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Row {
                    StatusDot(e.status == "bound" && !e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                    Column {
                        Text(e.interfaceName, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        if (e.address.isNotBlank()) {
                            Text(e.address, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        val meta = buildString {
                            if (e.status.isNotBlank()) append(e.status)
                            if (e.gateway.isNotBlank()) append(" • gateway ${e.gateway}")
                            if (e.expiresAfter.isNotBlank()) append(" • sisa ${e.expiresAfter}")
                        }
                        if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                        val meta2 = buildString {
                            if (e.dhcpServer.isNotBlank()) append("server ${e.dhcpServer}")
                            if (e.primaryDns.isNotBlank()) append(" • DNS ${e.primaryDns}")
                        }
                        if (meta2.isNotBlank()) Text(meta2, color = Muted, fontSize = 11.sp)
                        StateTags("nonaktif" to e.disabled)
                        if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                    manager.run {
                        CrudRepo.set(it, "/ip/dhcp-client", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                    }
                }, onDone = changed)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfirmActionButton("Perbarui", run = {
                        manager.runNoRetry { AdvancedRepo.renewDhcpClient(it, e.id) }
                    }, onDone = changed)
                    ConfirmActionButton("Lepaskan", run = {
                        manager.runNoRetry { AdvancedRepo.releaseDhcpClient(it, e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- IP Service (v0.7)

private fun serviceForm(existing: ServiceEntry): CrudFormSpec {
    return CrudFormSpec(
        title = "Edit Service ${existing.name}",
        isEdit = true,
        fields = listOf(
            CrudField("port", "Port", existing.port, required = true),
            CrudField("address", "Alamat Diizinkan (address)", existing.address,
                hint = "kosong = dari semua alamat, mis. 192.168.88.0/24")
        ),
        submit = { client, v ->
            val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "address" }
            CrudRepo.set(client, "/ip/service", existing.id, attrs)
        }
    )
}

@Composable
fun ServiceScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("IP Service", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        NeoCard(fill = MikroRed) {
            Text(
                "Hati-hati: menonaktifkan service api atau mengubah port-nya bisa memutus " +
                    "koneksi MikroKu ke router ini.",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "ip-service", tick = tick, onChanged = { tick++ },
            fetch = { AdvancedRepo.fetchServices(it) },
            emptyText = "Tidak ada service.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Row {
                    StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                    Column {
                        Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        val meta = buildString {
                            if (e.port.isNotBlank()) append("port ${e.port}")
                            if (e.address.isNotBlank()) append(" • dari ${e.address}")
                        }
                        if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                        StateTags("nonaktif" to e.disabled)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                    manager.run {
                        CrudRepo.set(it, "/ip/service", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                    }
                }, onDone = changed)
                Spacer(Modifier.height(8.dp))
                SmallButton("Edit Port / Alamat") { form = serviceForm(e) }
            }
        }
    }
}

// ---------------------------------------------------------------- Neighbors (v0.7)

@Composable
fun NeighborScreen(manager: SessionManager, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("Neighbors", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "neighbor", tick = tick, onChanged = { tick++ },
            fetch = { AdvancedRepo.fetchNeighbors(it) },
            emptyText = "Belum ada neighbor terdeteksi.",
            itemKey = { it.id.ifBlank { (it.address + it.macAddress).hashCode().toString() } }
        ) { e, _ ->
            NeoCard {
                Text(
                    e.identity.ifBlank { e.address.ifBlank { "-" } },
                    fontWeight = FontWeight.Black, fontSize = 15.sp
                )
                val line1 = buildString {
                    if (e.address.isNotBlank()) append(e.address)
                    if (e.macAddress.isNotBlank()) append(" • ${e.macAddress}")
                }
                if (line1.isNotBlank()) Text(line1, color = Muted, fontSize = 11.sp)
                val line2 = buildString {
                    if (e.interfaceName.isNotBlank()) append("via ${e.interfaceName}")
                    if (e.board.isNotBlank()) append(" • ${e.board}")
                    if (e.version.isNotBlank()) append(" • ROS ${e.version}")
                    if (e.uptime.isNotBlank()) append(" • uptime ${e.uptime}")
                }
                if (line2.isNotBlank()) Text(line2, color = Muted, fontSize = 11.sp)
            }
        }
    }
}
