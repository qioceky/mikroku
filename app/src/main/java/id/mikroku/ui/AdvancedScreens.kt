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
        KindChips(listOf("interface" to "Interface", "terhubung" to "Terhubung"), kind) { kind = it }
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
        } else {
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
        }
    }
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
