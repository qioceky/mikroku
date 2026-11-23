package id.mikroku.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.api.MikrotikClient
import id.mikroku.data.AdvancedRepo
import id.mikroku.data.CrudRepo
import id.mikroku.data.HotspotUserEntry
import id.mikroku.data.PppSecretEntry
import id.mikroku.data.ServiceRepo
import id.mikroku.data.SessionManager
import id.mikroku.data.SimpleQueueEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun KindChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((k, label) in options) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected == k) MikroYellow else CardFill)
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
fun ConfirmActionButton(
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
fun <T> PollingCards(
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

private fun hotspotUserForm(
    existing: HotspotUserEntry?,
    profileNames: List<String> = emptyList()
): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit User Hotspot" else "Tambah User Hotspot",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Username", existing?.name ?: "", required = true),
            CrudField("password", "Password", existing?.password ?: "",
                hint = if (edit) "Password saat ini dari router — ubah bila perlu" else "Boleh kosong",
                skipBlankOnEdit = true, isPassword = true),
            CrudField(
                "profile", "Profil", existing?.profile ?: "",
                hint = "kosong = profil default — atau ketuk profil di bawah",
                options = profileNames
            ),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/ip/hotspot/user", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/ip/hotspot/user", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
fun HotspotScreen(manager: SessionManager, onBack: () -> Unit) {
    // Daftar profil user hotspot dari router — jadi pilihan chip di form
    // user (tetap bisa ketik manual). Gagal muat = form seperti semula.
    var profileNames by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(manager) {
        profileNames = try {
            manager.run { ServiceRepo.fetchHotspotProfiles(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    var kind by remember { mutableStateOf("aktif") }
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("Hotspot", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(
            listOf("aktif" to "Sesi Aktif", "user" to "User", "server" to "Server", "voucher" to "Voucher"),
            kind
        ) { kind = it }
        Spacer(Modifier.height(10.dp))
        when (kind) {
            "voucher" -> VoucherSection(manager)
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
            "user" -> Column {
                NeoButton("+ Tambah User", fill = CardFill) { form = hotspotUserForm(null, profileNames) }
                Spacer(Modifier.height(10.dp))
                PollingCards(
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = hotspotUserForm(e, profileNames) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/ip/hotspot/user", e.id) }
                        }, onDone = onChanged)
                    }
                }
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

/**
 * Generator voucher hotspot: buat banyak user sekaligus dengan kode acak
 * (username = password = kode), lalu salin/bagikan hasilnya sebagai teks.
 */
@Composable
private fun VoucherSection(manager: SessionManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var profile by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("10") }
    var lengthText by remember { mutableStateOf("8") }
    var prefix by remember { mutableStateOf("") }
    var limitUptime by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<String>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manager) {
        try {
            profiles = manager.run { ServiceRepo.fetchHotspotProfiles(it) }
            if (profile.isBlank()) profile = profiles.firstOrNull() ?: "default"
        } catch (e: Exception) {
            msg = friendlyError(e)
        }
    }

    fun generate() {
        val count = countText.toIntOrNull()?.coerceIn(1, 200) ?: return
        val len = lengthText.toIntOrNull()?.coerceIn(4, 16) ?: return
        if (busy) return
        scope.launch {
            busy = true
            msg = null
            copied = false
            val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
            val rnd = java.security.SecureRandom()
            val created = mutableListOf<String>()
            var failed = 0
            try {
                manager.runNoRetry { client ->
                    repeat(count) {
                        val code = prefix.trim() +
                            (1..len).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
                        try {
                            val attrs = mutableMapOf("name" to code, "password" to code)
                            if (profile.isNotBlank()) attrs["profile"] = profile
                            if (limitUptime.isNotBlank()) attrs["limit-uptime"] = limitUptime.trim()
                            attrs["comment"] = "voucher MikroKu"
                            CrudRepo.add(client, "/ip/hotspot/user", attrs)
                            created.add(code)
                        } catch (e: Exception) {
                            failed++
                        }
                    }
                }
            } catch (e: Exception) {
                msg = friendlyError(e)
            }
            result = created
            if (failed > 0) {
                msg = "$failed voucher gagal dibuat (kemungkinan kode kembar) — silakan buat lagi."
            }
            busy = false
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "Buat banyak user hotspot sekaligus. Username = password = kode voucher.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Text("Profil", fontWeight = FontWeight.Black, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (p in profiles) {
                val active = profile == p
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MikroYellow else CardFill)
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                        .clickable { profile = p }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(p, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                countText, { countText = it.filter { c -> c.isDigit() } },
                Modifier.weight(1f), singleLine = true,
                label = { Text("Jumlah (maks 200)") }
            )
            OutlinedTextField(
                lengthText, { lengthText = it.filter { c -> c.isDigit() } },
                Modifier.weight(1f), singleLine = true,
                label = { Text("Panjang kode") }
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                prefix, { prefix = it },
                Modifier.weight(1f), singleLine = true,
                label = { Text("Awalan (opsional)") },
                supportingText = { Text("mis. VC-") }
            )
            OutlinedTextField(
                limitUptime, { limitUptime = it },
                Modifier.weight(1f), singleLine = true,
                label = { Text("Batas uptime (opsional)") },
                supportingText = { Text("mis. 1h / 1d; kosong = ikut profil") }
            )
        }
        Spacer(Modifier.height(12.dp))
        NeoButton(if (busy) "Membuat…" else "Buat Voucher", enabled = !busy) { generate() }
        msg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            NeoCard {
                Text(
                    "${result.size} voucher dibuat • profil $profile",
                    fontWeight = FontWeight.Black, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    result.joinToString("\n"),
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoButton(if (copied) "Tersalin ✓" else "Salin", Modifier.weight(1f), fill = CardFill) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("voucher", result.joinToString("\n")))
                        copied = true
                    }
                    NeoButton("Bagikan", Modifier.weight(1f)) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result.joinToString("\n"))
                        }
                        context.startActivity(Intent.createChooser(intent, "Bagikan voucher"))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------- PPPoE / PPP ----------------

private fun pppSecretForm(existing: PppSecretEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit PPP Secret" else "Tambah PPP Secret",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Username", existing?.name ?: "", required = true),
            CrudField("password", "Password", existing?.password ?: "",
                hint = if (edit) "Password saat ini dari router — ubah bila perlu" else "Boleh kosong",
                skipBlankOnEdit = true, isPassword = true),
            CrudField("service", "Service", existing?.service ?: "any",
                options = listOf("any", "pppoe", "pptp", "l2tp", "sstp", "ovpn")),
            CrudField("profile", "Profil", existing?.profile ?: "", hint = "kosong = profil default"),
            CrudField("remote-address", "Remote Address", existing?.remoteAddress ?: "",
                hint = "IP tetap untuk user ini (opsional)"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/ppp/secret", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/ppp/secret", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
fun PppScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("aktif") }
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
    Column {
        SectionHeader("PPPoE / PPP", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(listOf("aktif" to "Sesi Aktif", "secret" to "Secret"), kind) { kind = it }
        Spacer(Modifier.height(10.dp))
        if (kind == "secret") {
            Column {
                NeoButton("+ Tambah Secret", fill = CardFill) { form = pppSecretForm(null) }
                Spacer(Modifier.height(10.dp))
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Edit") { form = pppSecretForm(e) }
                        ConfirmActionButton("Hapus", run = {
                            manager.runNoRetry { CrudRepo.remove(it, "/ppp/secret", e.id) }
                        }, onDone = onChanged)
                    }
                }
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

private fun simpleQueueForm(existing: SimpleQueueEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Simple Queue" else "Tambah Simple Queue",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama", existing?.name ?: "", required = true),
            CrudField("target", "Target", existing?.target ?: "", required = true,
                hint = "IP/subnet (mis. 192.168.88.0/24) atau nama interface"),
            CrudField("max-limit", "Max Limit (naik/turun)",
                existing?.maxLimit?.takeIf { it != "0/0" } ?: "",
                hint = "mis. 1M/2M — kosong = tanpa batas"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/queue/simple", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/queue/simple", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
fun QueueScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("simple") }
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; tick++ }, onCancel = { form = null })
        return
    }
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
            Column {
                NeoButton("+ Tambah Queue", fill = CardFill) { form = simpleQueueForm(null) }
                Spacer(Modifier.height(10.dp))
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
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("Edit") { form = simpleQueueForm(e) }
                            ConfirmActionButton("Hapus", run = {
                                manager.runNoRetry { CrudRepo.remove(it, "/queue/simple", e.id) }
                            }, onDone = onChanged)
                        }
                    }
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
    var btAddress by remember { mutableStateOf("") }
    var btDuration by remember { mutableStateOf("5") }
    var btProtocol by remember { mutableStateOf("tcp") }
    var btDirection by remember { mutableStateOf("both") }
    var btRunning by remember { mutableStateOf(false) }
    var btError by remember { mutableStateOf<String?>(null) }
    var btResult by remember { mutableStateOf<Map<String, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun runBtest() {
        val addr = btAddress.trim()
        val dur = btDuration.trim().toIntOrNull()?.coerceIn(3, 30) ?: 5
        if (addr.isBlank() || btRunning) return
        scope.launch {
            btRunning = true
            btError = null
            btResult = null
            try {
                btResult = manager.runNoRetry {
                    AdvancedRepo.runBandwidthTest(it, addr, dur, btProtocol, btDirection)
                }
            } catch (e: Exception) {
                btError = friendlyError(e)
            }
            btRunning = false
        }
    }

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

    Column(Modifier.verticalScroll(rememberScrollState())) {
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
                "Traceroute", Modifier.weight(1f), fill = CardFill,
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
        Spacer(Modifier.height(18.dp))
        Text("Bandwidth Test", fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tes kecepatan DARI router ke server btest (router MikroTik lain yang " +
                "bandwidth-server-nya aktif). Perlu policy test.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            btAddress, { btAddress = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Alamat server btest") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            btDuration, { btDuration = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Durasi (detik, 3–30)") }
        )
        Spacer(Modifier.height(8.dp))
        KindChips(listOf("tcp" to "TCP", "udp" to "UDP"), btProtocol) { btProtocol = it }
        Spacer(Modifier.height(8.dp))
        KindChips(
            listOf("both" to "Dua arah", "transmit" to "Kirim (TX)", "receive" to "Terima (RX)"),
            btDirection
        ) { btDirection = it }
        Spacer(Modifier.height(10.dp))
        NeoButton(
            "Mulai Bandwidth Test", fill = CardFill,
            enabled = !btRunning && btAddress.isNotBlank()
        ) { runBtest() }
        if (btRunning) {
            Spacer(Modifier.height(8.dp))
            Text("Menjalankan tes dari router…", fontWeight = FontWeight.Bold)
        }
        btError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        btResult?.let { res ->
            Spacer(Modifier.height(10.dp))
            NeoCard {
                Text("Hasil Bandwidth Test", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                val tx = res["tx-total-average"]?.toLongOrNull()
                val rx = res["rx-total-average"]?.toLongOrNull()
                Text("TX rata-rata: ${tx?.let { formatBps(it) } ?: (res["tx-total-average"] ?: "-")}", fontSize = 13.sp)
                Text("RX rata-rata: ${rx?.let { formatBps(it) } ?: (res["rx-total-average"] ?: "-")}", fontSize = 13.sp)
                res["status"]?.let { Text("Status: $it", color = Muted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Wake-on-LAN", fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Bangunkan perangkat yang tidur dari router (magic packet). Perangkat tujuan " +
                "harus mendukung WoL dan terhubung kabel ke jaringan router.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        var wolMac by remember { mutableStateOf("") }
        var wolIface by remember { mutableStateOf("") }
        var wolBusy by remember { mutableStateOf(false) }
        var wolMsg by remember { mutableStateOf<String?>(null) }
        OutlinedTextField(
            wolMac, { wolMac = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("MAC address perangkat") },
            supportingText = { Text("format AA:BB:CC:DD:EE:FF") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            wolIface, { wolIface = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Interface (opsional)") },
            supportingText = { Text("kosong = broadcast ke semua interface") }
        )
        Spacer(Modifier.height(10.dp))
        NeoButton(
            if (wolBusy) "Mengirim…" else "Bangunkan",
            fill = CardFill,
            enabled = !wolBusy && wolMac.isNotBlank()
        ) {
            wolBusy = true
            wolMsg = null
            scope.launch {
                try {
                    manager.runNoRetry { ServiceRepo.wakeOnLan(it, wolMac.trim(), wolIface.trim()) }
                    wolMsg = "Magic packet terkirim ke ${wolMac.trim()}."
                } catch (e: Exception) {
                    wolMsg = friendlyError(e)
                }
                wolBusy = false
            }
        }
        wolMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        var diInfo by remember { mutableStateOf<id.mikroku.data.DetectInternetInfo?>(null) }
        var diLoaded by remember { mutableStateOf(false) }
        var diBusy by remember { mutableStateOf(false) }
        var diMsg by remember { mutableStateOf<String?>(null) }
        var diTick by remember { mutableStateOf(0) }
        LaunchedEffect(manager, diTick) {
            diInfo = try {
                manager.run { AdvancedRepo.fetchDetectInternet(it) }
            } catch (e: Exception) {
                null
            }
            diLoaded = true
        }
        if (diLoaded && diInfo != null) {
            val info = diInfo!!
            Text("Internet Detect", fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Deteksi otomatis RouterOS: tiap interface dilabeli internet / wan / " +
                    "lan / no-link. Statusnya tampil sebagai kartu di Dashboard. " +
                    "Matikan bila router ini tidak perlu deteksi (mis. semua interface " +
                    "sengaja diatur manual).",
                color = Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Status: " + if (info.enabled) "Aktif (detect-interface-list: ${info.detectList})" else "Tidak aktif",
                fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
            for ((n, s) in info.states) {
                Text("• $n → $s", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            NeoButton(
                if (diBusy) "Memproses…" else if (info.enabled) "Matikan Internet Detect" else "Aktifkan Internet Detect",
                fill = CardFill,
                enabled = !diBusy
            ) {
                diBusy = true
                diMsg = null
                scope.launch {
                    try {
                        manager.runNoRetry { AdvancedRepo.setDetectInternet(it, !info.enabled) }
                        diMsg = if (info.enabled) {
                            "Internet Detect dimatikan."
                        } else {
                            "Internet Detect diaktifkan — status per interface muncul " +
                                "di Dashboard setelah router selesai mendeteksi (beberapa detik)."
                        }
                        diTick++
                    } catch (e: Exception) {
                        diMsg = friendlyError(e)
                    }
                    diBusy = false
                }
            }
            diMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
        Text("API-SSL (Port 8729)", fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Service api-ssl bawaan RouterOS belum punya sertifikat — tanpa itu koneksi " +
                "API-SSL selalu gagal jabat tangan (handshake). Tombol ini membuat sertifikat " +
                "self-signed 'mikroku-api' di router, menandatanganinya, memasangnya ke " +
                "api-ssl, dan mengaktifkannya. Jalankan saat tersambung lewat API 8728; " +
                "perlu policy write. Kunci 1024-bit dipakai agar cepat dibuat di router " +
                "kecil — koneksimu tetap diamankan sidik jari sertifikat (TOFU) oleh MikroKu. " +
                "Bila sempat timeout di router lambat, ketuk lagi: langkah yang sudah beres dilewati.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        var sslBusy by remember { mutableStateOf(false) }
        var sslMsg by remember { mutableStateOf<String?>(null) }
        NeoButton(
            if (sslBusy) "Menyiapkan…" else "Siapkan API-SSL",
            fill = CardFill,
            enabled = !sslBusy
        ) {
            sslBusy = true
            sslMsg = null
            scope.launch {
                try {
                    sslMsg = manager.runNoRetry { AdvancedRepo.setupApiSsl(it) }
                } catch (e: Exception) {
                    sslMsg = friendlyError(e)
                }
                sslBusy = false
            }
        }
        sslMsg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        var sslResetConfirm by remember { mutableStateOf(false) }
        NeoButton(
            when {
                sslBusy -> "Mengembalikan…"
                sslResetConfirm -> "Yakin? Ketuk Sekali Lagi untuk Reset"
                else -> "Kembalikan API-SSL ke Default"
            },
            fill = if (sslResetConfirm) MikroRed else CardFill,
            textColor = if (sslResetConfirm) Color.White else Ink,
            enabled = !sslBusy
        ) {
            if (!sslResetConfirm) {
                sslResetConfirm = true
                return@NeoButton
            }
            sslResetConfirm = false
            sslBusy = true
            sslMsg = null
            scope.launch {
                try {
                    sslMsg = manager.runNoRetry { AdvancedRepo.resetApiSsl(it) }
                } catch (e: Exception) {
                    sslMsg = friendlyError(e)
                }
                sslBusy = false
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

internal fun formatBps(bps: Long): String = when {
    bps >= 1_000_000_000L -> "%.2f Gbps".format(bps / 1_000_000_000.0)
    bps >= 1_000_000L -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000L -> "%.1f Kbps".format(bps / 1_000.0)
    else -> "$bps bps"
}
