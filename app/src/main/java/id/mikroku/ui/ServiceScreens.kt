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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.api.MikrotikClient
import id.mikroku.data.ServiceRepo
import id.mikroku.data.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
private fun KindChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((k, label) in options) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected == k) MikroYellow else Color.White)
                    .border(2.dp, Ink, RoundedCornerShape(8.dp))
                    .clickable { onSelect(k) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

/** Tombol aksi tulis dengan konfirmasi dua langkah + pesan hasil. */
@Composable
private fun ConfirmActionButton(
    label: String,
    run: suspend () -> Unit,
    onDone: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column {
        if (!confirm) {
            SmallButton(label) { confirm = true; msg = null }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Batal") { confirm = false }
                SmallButton("Ya, $label", fill = MikroRed, textColor = Color.White) {
                    scope.launch {
                        try {
                            run()
                            onDone()
                        } catch (e: Exception) {
                            msg = friendlyError(e)
                        }
                        confirm = false
                    }
                }
            }
        }
        msg?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MikroRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Daftar kartu dengan polling 10 detik untuk layar layanan.
 * [resetKey] mengganti isi (mis. pindah sub-tab); [tick] memicu muat ulang.
 */
@Composable
private fun <T> PollingCards(
    manager: SessionManager,
    resetKey: Any,
    tick: Int,
    onChanged: () -> Unit,
    fetch: suspend (MikrotikClient) -> List<T>,
    emptyText: String,
    itemKey: (T) -> String,
    itemContent: @Composable (T, () -> Unit) -> Unit
) {
    var rows by remember(resetKey) { mutableStateOf<List<T>?>(null) }
    var error by remember(resetKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(manager, resetKey, tick) {
        while (isActive) {
            try {
                rows = manager.run { fetch(it) }
                error = null
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            delay(10_000)
        }
    }

    Column {
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
                items(list, key = { itemKey(it) }) { entry -> itemContent(entry, onChanged) }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ---------------- Hotspot ----------------

@Composable
fun HotspotScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("aktif") }
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("Hotspot", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(
            listOf("aktif" to "Sesi Aktif", "user" to "User", "server" to "Server"),
            kind
        ) { kind = it }
        Spacer(Modifier.height(10.dp))
        when (kind) {
            "server" -> PollingCards(
                manager = manager, resetKey = "hs-server", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchHotspotServers(it) },
                emptyText = "Tidak ada server hotspot.",
                itemKey = { it.id }
            ) { e, _ ->
                NeoCard {
                    Row {
                        StatusDot(!e.disabled && !e.invalid, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                append("di ${e.iface}")
                                if (e.addressPool.isNotBlank()) append(" • pool ${e.addressPool}")
                                if (e.profile.isNotBlank()) append(" • profil ${e.profile}")
                            }
                            Text(meta, color = Muted, fontSize = 12.sp)
                            StateTags("nonaktif" to e.disabled, "invalid" to e.invalid)
                        }
                    }
                }
            }
            "user" -> PollingCards(
                manager = manager, resetKey = "hs-user", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchHotspotUsers(it) },
                emptyText = "Belum ada user hotspot.",
                itemKey = { it.id }
            ) { e, onChanged ->
                NeoCard {
                    Row {
                        StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                if (e.profile.isNotBlank()) append("profil ${e.profile}")
                                if (e.uptime.isNotBlank()) append(" • total online ${e.uptime}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                            Text(
                                "masuk ${formatBytes(e.bytesIn)} • keluar ${formatBytes(e.bytesOut)}",
                                color = Muted, fontSize = 11.sp
                            )
                            StateTags("nonaktif" to e.disabled)
                            if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                        manager.run { ServiceRepo.setHotspotUserDisabled(it, e.id, !e.disabled) }
                    }, onDone = onChanged)
                }
            }
            else -> PollingCards(
                manager = manager, resetKey = "hs-aktif", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchHotspotActive(it) },
                emptyText = "Tidak ada sesi hotspot aktif.",
                itemKey = { it.id }
            ) { e, onChanged ->
                NeoCard {
                    Row {
                        StatusDot(true, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.user, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            if (e.address.isNotBlank()) Text(e.address, color = Muted, fontSize = 12.sp)
                            if (e.macAddress.isNotBlank()) Text(e.macAddress, color = Muted, fontSize = 12.sp)
                            val meta = buildString {
                                if (e.server.isNotBlank()) append("server ${e.server}")
                                if (e.uptime.isNotBlank()) append(" • online ${e.uptime}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                            Text(
                                "masuk ${formatBytes(e.bytesIn)} • keluar ${formatBytes(e.bytesOut)}",
                                color = Muted, fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton("Putuskan", run = {
                        manager.run { ServiceRepo.removeHotspotActive(it, e.id) }
                    }, onDone = onChanged)
                }
            }
        }
    }
}

// ---------------- PPPoE / PPP ----------------

@Composable
fun PppScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("aktif") }
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("PPPoE / PPP", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(listOf("aktif" to "Sesi Aktif", "secret" to "Secret"), kind) { kind = it }
        Spacer(Modifier.height(10.dp))
        if (kind == "secret") {
            PollingCards(
                manager = manager, resetKey = "ppp-secret", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchPppSecrets(it) },
                emptyText = "Belum ada PPP secret.",
                itemKey = { it.id }
            ) { e, onChanged ->
                NeoCard {
                    Row {
                        StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                if (e.service.isNotBlank()) append(e.service)
                                if (e.profile.isNotBlank()) append(" • profil ${e.profile}")
                                if (e.remoteAddress.isNotBlank()) append(" • remote ${e.remoteAddress}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                            if (e.lastLoggedOut.isNotBlank()) {
                                Text("terakhir keluar: ${e.lastLoggedOut}", color = Muted, fontSize = 11.sp)
                            }
                            StateTags("nonaktif" to e.disabled)
                            if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                        manager.run { ServiceRepo.setPppSecretDisabled(it, e.id, !e.disabled) }
                    }, onDone = onChanged)
                }
            }
        } else {
            PollingCards(
                manager = manager, resetKey = "ppp-aktif", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchPppActive(it) },
                emptyText = "Tidak ada sesi PPP aktif.",
                itemKey = { it.id }
            ) { e, onChanged ->
                NeoCard {
                    Row {
                        StatusDot(true, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                if (e.service.isNotBlank()) append(e.service)
                                if (e.address.isNotBlank()) append(" • ${e.address}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                            val meta2 = buildString {
                                if (e.callerId.isNotBlank()) append("dari ${e.callerId}")
                                if (e.uptime.isNotBlank()) append(" • online ${e.uptime}")
                            }
                            if (meta2.isNotBlank()) Text(meta2, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ConfirmActionButton("Putuskan", run = {
                        manager.run { ServiceRepo.removePppActive(it, e.id) }
                    }, onDone = onChanged)
                }
            }
        }
    }
}

// ---------------- Queue ----------------

@Composable
fun QueueScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("simple") }
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("Queue", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(listOf("simple" to "Simple Queue", "tree" to "Queue Tree"), kind) { kind = it }
        Spacer(Modifier.height(10.dp))
        if (kind == "tree") {
            PollingCards(
                manager = manager, resetKey = "q-tree", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchQueueTree(it) },
                emptyText = "Queue tree kosong.",
                itemKey = { it.id }
            ) { e, _ ->
                NeoCard {
                    Row {
                        StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            val meta = buildString {
                                if (e.parent.isNotBlank()) append("induk ${e.parent}")
                                if (e.packetMark.isNotBlank()) append(" • mark ${e.packetMark}")
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                            val meta2 = buildString {
                                if (e.maxLimit.isNotBlank() && e.maxLimit != "0/0") {
                                    append("maks ${e.maxLimit}")
                                }
                                append(" • total ${formatBytes(e.bytes)}")
                            }
                            Text(meta2, color = Muted, fontSize = 11.sp)
                            StateTags("nonaktif" to e.disabled)
                        }
                    }
                }
            }
        } else {
            PollingCards(
                manager = manager, resetKey = "q-simple", tick = tick, onChanged = { tick++ },
                fetch = { ServiceRepo.fetchSimpleQueues(it) },
                emptyText = "Belum ada simple queue.",
                itemKey = { it.id }
            ) { e, onChanged ->
                NeoCard {
                    Row {
                        StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                        Column {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            if (e.target.isNotBlank()) {
                                Text("target ${e.target}", color = Muted, fontSize = 12.sp)
                            }
                            val meta = buildString {
                                if (e.maxLimit.isNotBlank() && e.maxLimit != "0/0") {
                                    append("maks ${e.maxLimit}")
                                }
                                if (e.rate.isNotBlank() && e.rate != "0/0") {
                                    if (isNotEmpty()) append(" • ")
                                    append("rate ${e.rate}")
                                }
                            }
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                            if (e.bytesIn != null || e.bytesOut != null) {
                                Text(
                                    "masuk ${formatBytes(e.bytesIn ?: 0)} • keluar ${formatBytes(e.bytesOut ?: 0)}",
                                    color = Muted, fontSize = 11.sp
                                )
                            }
                            StateTags("nonaktif" to e.disabled, "dinamis" to e.dynamic)
                            if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                        }
                    }
                    if (!e.dynamic) {
                        Spacer(Modifier.height(8.dp))
                        ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                            manager.run { ServiceRepo.setSimpleQueueDisabled(it, e.id, !e.disabled) }
                        }, onDone = onChanged)
                    }
                }
            }
        }
    }
}

// ---------------- Tools ----------------

@Composable
fun ToolsScreen(manager: SessionManager, onBack: () -> Unit) {
    var target by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun runTool(pingMode: Boolean) {
        val addr = target.trim()
        if (addr.isBlank() || running) return
        scope.launch {
            running = true
            error = null
            try {
                val res = manager.run {
                    if (pingMode) ServiceRepo.ping(it, addr) else ServiceRepo.traceroute(it, addr)
                }
                resultTitle = if (pingMode) "Ping ke $addr" else "Traceroute ke $addr"
                lines = res
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            running = false
        }
    }

    Column {
        SectionHeader("Tools", onBack) { }
        Spacer(Modifier.height(10.dp))
        Text(
            "Ping dan traceroute dijalankan DARI router — berguna untuk cek jangkauan dari sisi router. " +
                "Mungkin perlu policy test pada user API.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            target,
            { target = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Alamat tujuan (IP / hostname)") }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                "Ping", Modifier.weight(1f),
                enabled = !running && target.isNotBlank()
            ) { runTool(true) }
            NeoButton(
                "Traceroute", Modifier.weight(1f), fill = Color.White,
                enabled = !running && target.isNotBlank()
            ) { runTool(false) }
        }
        Spacer(Modifier.height(12.dp))
        if (running) {
            Text("Menjalankan dari router…", fontWeight = FontWeight.Bold)
        }
        error?.let {
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            NeoCard {
                Text(resultTitle ?: "Hasil", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                lines.forEach { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}
