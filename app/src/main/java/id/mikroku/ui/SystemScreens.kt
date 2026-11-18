package id.mikroku.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.CrudRepo
import id.mikroku.data.DashboardData
import id.mikroku.data.FileEntry
import id.mikroku.data.MonitorRepo
import id.mikroku.data.RouterUserEntry
import id.mikroku.data.SchedulerEntry
import id.mikroku.data.ScriptEntry
import id.mikroku.data.AutoBackupScheduler
import id.mikroku.data.SessionManager
import id.mikroku.data.SettingsStore
import id.mikroku.data.SftpRepo
import id.mikroku.data.SystemRepo
import kotlinx.coroutines.launch

/** Hub administrasi sistem (v0.5). */
@Composable
fun SystemScreen(manager: SessionManager, onBack: () -> Unit) {
    var kind by remember { mutableStateOf("identitas") }
    var tick by remember { mutableStateOf(0) }
    Column {
        SectionHeader("Sistem", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        KindChips(
            listOf(
                "identitas" to "Identitas",
                "user" to "User",
                "grup" to "Grup",
                "scheduler" to "Scheduler",
                "script" to "Script",
                "log" to "Log",
                "file" to "File",
                "backup" to "Backup",
                "paket" to "Paket"
            ),
            kind
        ) { kind = it }
        Spacer(Modifier.height(10.dp))
        when (kind) {
            "identitas" -> IdentitySection(manager, tick)
            "user" -> UsersSection(manager, tick) { tick++ }
            "grup" -> GroupsSection(manager, tick)
            "scheduler" -> SchedulerSection(manager, tick) { tick++ }
            "script" -> ScriptsSection(manager, tick) { tick++ }
            "log" -> LogsSection(manager, tick)
            "file" -> FilesSection(manager, tick) { tick++ }
            "backup" -> BackupSection(manager)
            "paket" -> PackagesSection(manager, tick)
        }
    }
}

@Composable
private fun IdentitySection(manager: SessionManager, tick: Int) {
    var localTick by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<DashboardData?>(null) }
    var clock by remember { mutableStateOf<id.mikroku.data.ClockInfo?>(null) }
    var ntp by remember { mutableStateOf<id.mikroku.data.NtpInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; localTick++ }, onCancel = { form = null })
        return
    }
    LaunchedEffect(manager, tick, localTick) {
        try {
            data = manager.run { MonitorRepo.fetchDashboard(it) }
            error = null
        } catch (e: Exception) {
            error = friendlyError(e)
        }
        clock = try { manager.run { SystemRepo.fetchClock(it) } } catch (e: Exception) { null }
        ntp = try { manager.run { SystemRepo.fetchNtpClient(it) } } catch (e: Exception) { null }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        error?.let {
            Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        val d = data
        if (d == null) {
            Text(if (error != null) "Gagal memuat data." else "Memuat…", fontWeight = FontWeight.Bold)
        } else {
            NeoCard {
                Text("IDENTITAS ROUTER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                Text(d.identity, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("${d.boardName} • RouterOS ${d.version}", color = Muted, fontSize = 12.sp)
                Text("Uptime ${d.uptime} • jam ${d.clockTime} ${d.clockDate}", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            NeoButton("Edit Identitas", fill = CardFill) {
                form = CrudFormSpec(
                    title = "Edit Identitas",
                    isEdit = true,
                    fields = listOf(
                        CrudField("name", "Nama Router (identity)", d.identity, required = true)
                    ),
                    submit = { client, v ->
                        SystemRepo.setIdentity(client, (v["name"] ?: "").trim())
                    }
                )
            }
            clock?.let { c ->
                Spacer(Modifier.height(10.dp))
                NeoCard {
                    Text("JAM ROUTER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                    Text("${c.time} • ${c.date}", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(
                        "Zona waktu: ${c.timeZoneName.ifBlank { "-" }}${if (c.dstActive) " (DST aktif)" else ""}",
                        color = Muted, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                NeoButton("Edit Zona Waktu", fill = CardFill) {
                    form = CrudFormSpec(
                        title = "Edit Zona Waktu",
                        isEdit = true,
                        fields = listOf(
                            CrudField("time-zone-name", "Zona Waktu", c.timeZoneName, required = true,
                                hint = "mis. Asia/Jakarta, Asia/Makassar, Asia/Jayapura")
                        ),
                        submit = { client, v ->
                            SystemRepo.setClockTimezone(client, (v["time-zone-name"] ?: "").trim())
                        }
                    )
                }
            }
            ntp?.let { n ->
                Spacer(Modifier.height(10.dp))
                NeoCard {
                    Text("NTP CLIENT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                    Text(
                        if (n.enabled) "Aktif • ${n.status.ifBlank { "-" }}" else "Nonaktif",
                        fontWeight = FontWeight.Black, fontSize = 15.sp
                    )
                    val meta = buildString {
                        if (n.primaryNtp.isNotBlank()) append("utama ${n.primaryNtp}")
                        if (n.secondaryNtp.isNotBlank()) append(" • cadangan ${n.secondaryNtp}")
                        if (n.activeServer.isNotBlank()) append(" • server aktif ${n.activeServer}")
                    }
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                NeoButton("Edit NTP", fill = CardFill) {
                    form = CrudFormSpec(
                        title = "Edit NTP Client",
                        isEdit = true,
                        fields = listOf(
                            CrudField("enabled", "NTP Aktif", if (n.enabled) "yes" else "no",
                                options = listOf("yes", "no")),
                            CrudField("primary-ntp", "Server NTP Utama", n.primaryNtp,
                                hint = "mis. id.pool.ntp.org atau 202.162.32.12"),
                            CrudField("secondary-ntp", "Server NTP Cadangan", n.secondaryNtp)
                        ),
                        submit = { client, v ->
                            SystemRepo.setNtpClient(
                                client,
                                v["enabled"] == "yes",
                                (v["primary-ntp"] ?: "").trim(),
                                (v["secondary-ntp"] ?: "").trim()
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun routerUserForm(existing: RouterUserEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit User RouterOS" else "Tambah User RouterOS",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Username", existing?.name ?: "", required = true),
            CrudField("password", if (edit) "Password Baru" else "Password", "",
                hint = if (edit) "Password saat ini terpasang di router tapi TIDAK bisa dibaca — RouterOS tidak pernah mengirimkannya keluar (di WinBox pun kolom ini kosong). Kosongkan = tidak diubah; isi = ganti baru." else "Boleh kosong",
                skipBlankOnEdit = true, isPassword = true),
            CrudField("group", "Grup", existing?.group ?: "read",
                hint = "full / read / write atau nama grup kustom",
                options = listOf("full", "read", "write")),
            CrudField("address", "Alamat Diizinkan", existing?.address ?: "",
                hint = "kosong = dari mana saja (mis. 192.168.88.0/24)"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/user", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/user", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun UsersSection(manager: SessionManager, tick: Int, onChanged: () -> Unit) {
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; onChanged() }, onCancel = { form = null })
        return
    }
    Column {
        NeoButton("+ Tambah User", fill = CardFill) { form = routerUserForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "sys-user", tick = tick, onChanged = onChanged,
            fetch = { SystemRepo.fetchUsers(it) },
            emptyText = "Tidak ada user.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Row {
                    StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                    Column {
                        Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        val meta = buildString {
                            if (e.group.isNotBlank()) append("grup ${e.group}")
                            if (e.address.isNotBlank()) append(" • dari ${e.address}")
                        }
                        if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                        if (e.lastLoggedIn.isNotBlank()) {
                            Text("login terakhir: ${e.lastLoggedIn}", color = Muted, fontSize = 11.sp)
                        }
                        StateTags("nonaktif" to e.disabled)
                        if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                    manager.run {
                        CrudRepo.set(it, "/user", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                    }
                }, onDone = changed)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Edit") { form = routerUserForm(e) }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/user", e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

@Composable
private fun GroupsSection(manager: SessionManager, tick: Int) {
    PollingCards(
        manager = manager, resetKey = "sys-grup", tick = tick, onChanged = {},
        fetch = { SystemRepo.fetchGroups(it) },
        emptyText = "Tidak ada grup user.",
        itemKey = { it.id }
    ) { e, _ ->
        NeoCard {
            Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
            if (e.policy.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    e.policy.replace(",", ", "),
                    color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun schedulerForm(existing: SchedulerEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Scheduler" else "Tambah Scheduler",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama", existing?.name ?: "", required = true),
            CrudField("interval", "Interval", existing?.interval ?: "",
                hint = "mis. 1d, 12h, 30m — kosong = sekali jalan"),
            CrudField("start-time", "Waktu Mulai", existing?.startTime ?: "",
                hint = "mis. startup atau 06:00:00"),
            CrudField("on-event", "Perintah (on-event)", existing?.onEvent ?: "", required = true,
                hint = "satu baris perintah RouterOS"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/system/scheduler", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/system/scheduler", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun SchedulerSection(manager: SessionManager, tick: Int, onChanged: () -> Unit) {
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; onChanged() }, onCancel = { form = null })
        return
    }
    Column {
        NeoButton("+ Tambah Scheduler", fill = CardFill) { form = schedulerForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "sys-scheduler", tick = tick, onChanged = onChanged,
            fetch = { SystemRepo.fetchSchedulers(it) },
            emptyText = "Belum ada scheduler.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Row {
                    StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                    Column {
                        Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        val meta = buildString {
                            if (e.interval.isNotBlank() && e.interval != "0s") append("interval ${e.interval}")
                            if (e.nextRun.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("berikutnya ${e.nextRun}")
                            }
                            if (e.runCount.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("sudah ${e.runCount}x jalan")
                            }
                        }
                        if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                        if (e.onEvent.isNotBlank()) {
                            Text(
                                e.onEvent.take(90) + if (e.onEvent.length > 90) "…" else "",
                                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                        StateTags("nonaktif" to e.disabled)
                        if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                    manager.run {
                        CrudRepo.set(it, "/system/scheduler", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                    }
                }, onDone = changed)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfirmActionButton("Jalankan", run = {
                        manager.runNoRetry { SystemRepo.runScheduler(it, e.id) }
                    }, onDone = changed)
                    SmallButton("Edit") { form = schedulerForm(e) }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/system/scheduler", e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

private fun scriptForm(existing: ScriptEntry?): CrudFormSpec {
    val edit = existing != null
    return CrudFormSpec(
        title = if (edit) "Edit Script" else "Tambah Script",
        isEdit = edit,
        fields = listOf(
            CrudField("name", "Nama", existing?.name ?: "", required = true),
            CrudField("source", "Isi Script (source)", existing?.source ?: "", required = true,
                hint = "satu baris perintah RouterOS; multi-baris lebih nyaman via WinBox"),
            CrudField("comment", "Komentar", existing?.comment ?: "")
        ),
        submit = { client, v ->
            if (edit) {
                val attrs = v.filter { (k, value) -> value.isNotBlank() || k == "comment" }
                CrudRepo.set(client, "/system/script", existing!!.id, attrs)
            } else {
                CrudRepo.add(client, "/system/script", v.filterValues { it.isNotBlank() })
            }
        }
    )
}

@Composable
private fun ScriptsSection(manager: SessionManager, tick: Int, onChanged: () -> Unit) {
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; onChanged() }, onCancel = { form = null })
        return
    }
    Column {
        NeoButton("+ Tambah Script", fill = CardFill) { form = scriptForm(null) }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "sys-script", tick = tick, onChanged = onChanged,
            fetch = { SystemRepo.fetchScripts(it) },
            emptyText = "Belum ada script.",
            itemKey = { it.id }
        ) { e, changed ->
            var showSource by remember { mutableStateOf(false) }
            NeoCard {
                Row {
                    StatusDot(!e.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                    Column {
                        Text(e.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        val meta = buildString {
                            if (e.owner.isNotBlank()) append("pemilik ${e.owner}")
                            if (e.runCount.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("sudah ${e.runCount}x jalan")
                            }
                            if (e.lastStarted.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("terakhir ${e.lastStarted}")
                            }
                        }
                        if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                        StateTags("nonaktif" to e.disabled)
                        if (e.comment.isNotBlank()) Text(e.comment, color = Muted, fontSize = 11.sp)
                    }
                }
                if (showSource) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        e.source.ifBlank { "(kosong)" },
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (showSource) "Tutup Isi" else "Lihat Isi") { showSource = !showSource }
                    ConfirmActionButton("Jalankan", run = {
                        manager.runNoRetry { SystemRepo.runScript(it, e.id) }
                    }, onDone = changed)
                }
                Spacer(Modifier.height(8.dp))
                ConfirmActionButton(if (e.disabled) "Aktifkan" else "Nonaktifkan", run = {
                    manager.run {
                        CrudRepo.set(it, "/system/script", e.id, mapOf("disabled" to if (e.disabled) "no" else "yes"))
                    }
                }, onDone = changed)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Edit") { form = scriptForm(e) }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/system/script", e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

@Composable
private fun LogsSection(manager: SessionManager, tick: Int) {
    PollingCards(
        manager = manager, resetKey = "sys-log", tick = tick, onChanged = {},
        fetch = { SystemRepo.fetchLogs(it) },
        emptyText = "Log kosong.",
        itemKey = { it.id.ifBlank { (it.time + it.message).hashCode().toString() } }
    ) { e, _ ->
        NeoCard {
            Row {
                Text(e.time, fontWeight = FontWeight.Black, fontSize = 12.sp)
                if (e.topics.isNotBlank()) {
                    Text(" • ${e.topics}", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(e.message, fontSize = 12.sp)
        }
    }
}

private fun fileRenameForm(existing: FileEntry): CrudFormSpec =
    CrudFormSpec(
        title = "Ganti Nama File",
        isEdit = true,
        fields = listOf(
            CrudField("name", "Nama Baru", existing.name, required = true)
        ),
        submit = { client, v ->
            CrudRepo.set(client, "/file", existing.id, mapOf("name" to (v["name"] ?: "")))
        }
    )

private fun queryDisplayName(context: Context, uri: Uri): String? =
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }

private fun sftpFriendlyError(e: Exception): String {
    val raw = e.message ?: return "Transfer file gagal"
    return when {
        raw.contains("auth", ignoreCase = true) ||
            raw.contains("password", ignoreCase = true) ->
            "Login SSH ditolak — periksa username/password; grup user perlu policy ssh + ftp " +
                "(grup full bawaan sudah termasuk)."
        raw.contains("permission", ignoreCase = true) ||
            raw.contains("denied", ignoreCase = true) ->
            "Izin transfer ditolak router — grup user perlu policy ftp untuk SFTP."
        raw.contains("connect", ignoreCase = true) ||
            raw.contains("refused", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) ->
            "Tidak bisa menjangkau SSH router (port 22) — pastikan service ssh aktif " +
                "di IP → Services."
        else -> friendlyError(e)
    }
}

@Composable
private fun FilesSection(manager: SessionManager, tick: Int, onChanged: () -> Unit) {
    var form by remember { mutableStateOf<CrudFormSpec?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingDownload by remember { mutableStateOf<FileEntry?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null && !busy) {
            scope.launch {
                busy = true
                msg = null
                progressText = null
                var lastShown = ""
                try {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan di HP")
                    out.use {
                        SftpRepo.download(
                            manager.sftpProfile(), entry.name, it,
                            onProgress = { done, total ->
                                val text = if (total > 0)
                                    "Mengunduh ${entry.name}: ${formatBytes(done)} / ${formatBytes(total)}"
                                else "Mengunduh ${entry.name}: ${formatBytes(done)}"
                                if (text != lastShown) {
                                    lastShown = text
                                    progressText = text
                                }
                            },
                            onFingerprintLearned = { fp -> manager.learnSshFingerprint(fp) }
                        )
                    }
                    msg = "Terunduh ke HP: ${entry.name}"
                } catch (e: Exception) {
                    msg = sftpFriendlyError(e)
                }
                progressText = null
                busy = false
            }
        }
    }

    var uploadTarget by remember { mutableStateOf<String?>(null) }
    var pickedUpload by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUpload = uri
            pickedName = queryDisplayName(context, uri) ?: "file-unggahan"
        }
    }

    fun doUpload() {
        val uri = pickedUpload ?: return
        val remoteName = uploadTarget ?: pickedName
        if (busy || remoteName.isBlank()) return
        scope.launch {
            busy = true
            msg = null
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Tidak bisa membaca file pilihan dari HP")
                input.use {
                    SftpRepo.upload(
                        manager.sftpProfile(), it, remoteName,
                        onFingerprintLearned = { fp -> manager.learnSshFingerprint(fp) }
                    )
                }
                msg = "Terunggah ke router: $remoteName"
                pickedUpload = null
                uploadTarget = null
                onChanged()
            } catch (e: Exception) {
                msg = sftpFriendlyError(e)
            }
            busy = false
        }
    }

    val f = form
    if (f != null) {
        CrudFormScreen(f, manager, onDone = { form = null; onChanged() }, onCancel = { form = null })
        return
    }

    Column {
        NeoCard {
            Text(
                "Unduh/unggah berjalan lewat SFTP (service SSH router, port 22) dengan " +
                    "username & password yang sama. Grup user perlu policy ssh + ftp.",
                fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        NeoButton("+ Unggah File ke Router", fill = CardFill, enabled = !busy) {
            uploadTarget = null
            uploadLauncher.launch(arrayOf("*/*"))
        }
        pickedUpload?.let {
            Spacer(Modifier.height(10.dp))
            NeoCard {
                val target = uploadTarget
                Text(
                    if (target != null) {
                        "Ganti isi file \"$target\" dengan \"$pickedName\"? Isi lama tertimpa."
                    } else {
                        "Unggah \"$pickedName\" ke router?"
                    },
                    fontWeight = FontWeight.Black, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Batal") { pickedUpload = null; uploadTarget = null }
                    SmallButton(
                        if (target != null) "Ya, Ganti" else "Ya, Unggah",
                        fill = MikroRed, textColor = Color.White
                    ) { doUpload() }
                }
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Text(progressText ?: "Mentransfer file…", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        msg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        PollingCards(
            manager = manager, resetKey = "sys-file", tick = tick, onChanged = onChanged,
            fetch = { SystemRepo.fetchFiles(it) },
            emptyText = "Tidak ada file.",
            itemKey = { it.id }
        ) { e, changed ->
            NeoCard {
                Text(e.name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                val meta = buildString {
                    if (e.type.isNotBlank()) append(e.type)
                    append(" • ${formatBytes(e.size)}")
                    if (e.creationTime.isNotBlank()) append(" • ${e.creationTime}")
                }
                Text(meta, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                if (e.type != "directory") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("Unduh") {
                            if (!busy) {
                                pendingDownload = e
                                downloadLauncher.launch(e.name)
                            }
                        }
                        SmallButton("Ganti File") {
                            if (!busy) {
                                uploadTarget = e.name
                                uploadLauncher.launch(arrayOf("*/*"))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Ganti Nama") { form = fileRenameForm(e) }
                    ConfirmActionButton("Hapus", run = {
                        manager.runNoRetry { CrudRepo.remove(it, "/file", e.id) }
                    }, onDone = changed)
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(manager: SessionManager, tick: Int) {
    var board by remember { mutableStateOf<id.mikroku.data.RouterboardInfo?>(null) }
    var update by remember { mutableStateOf<id.mikroku.data.PackageUpdateInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var localTick by remember { mutableStateOf(0) }
    var confirmInstall by remember { mutableStateOf(false) }
    var confirmFw by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manager, tick, localTick) {
        try { board = manager.run { SystemRepo.fetchRouterboard(it) } } catch (e: Exception) { }
        try { update = manager.run { SystemRepo.fetchPackageUpdate(it) } } catch (e: Exception) { }
        loaded = true
    }

    Column {
        Text("PEMBARUAN", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
        if (!loaded) {
            Text("Memuat info pembaruan…", fontWeight = FontWeight.Bold)
        } else {
            NeoCard(Modifier.fillMaxWidth()) {
                Text("RouterOS", fontWeight = FontWeight.Black, fontSize = 14.sp)
                val u = update
                if (u == null) {
                    Text(
                        "Status update tidak terbaca dari router.",
                        color = Muted, fontSize = 12.sp
                    )
                } else {
                    Text(
                        "Terpasang: ${u.installedVersion.ifBlank { "-" }}" +
                            if (u.channel.isNotBlank()) " • channel ${u.channel}" else "",
                        fontSize = 12.sp
                    )
                    if (u.latestVersion.isNotBlank()) {
                        Text("Terbaru: ${u.latestVersion}", fontSize = 12.sp)
                    }
                    if (u.statusText.isNotBlank()) {
                        Text(u.statusText, color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                NeoButton(
                    if (busy) "Memproses…" else "Cek Update",
                    fill = CardFill, enabled = !busy
                ) {
                    busy = true
                    msg = null
                    scope.launch {
                        try {
                            val fresh = manager.runNoRetry { SystemRepo.checkForUpdates(it) }
                            if (fresh != null) update = fresh
                            msg = when {
                                fresh == null -> "Status update tidak terbaca."
                                fresh.updateAvailable ->
                                    "Ada versi baru: ${fresh.latestVersion} (terpasang ${fresh.installedVersion})."
                                fresh.latestVersion.isNotBlank() ->
                                    "RouterOS sudah versi terbaru (${fresh.installedVersion})."
                                else -> "Pengecekan selesai — router belum melaporkan versi terbaru, coba lagi sebentar lagi."
                            }
                            localTick++
                        } catch (e: Exception) {
                            msg = friendlyError(e) +
                                " (cek update butuh internet + DNS yang jalan di router)"
                        }
                        busy = false
                    }
                }
                if (update?.updateAvailable == true) {
                    Spacer(Modifier.height(8.dp))
                    NeoButton(
                        "Unduh Saja (Tanpa Pasang)",
                        fill = CardFill, enabled = !busy
                    ) {
                        busy = true
                        msg = null
                        scope.launch {
                            try {
                                manager.runNoRetry { SystemRepo.downloadUpdate(it) }
                                msg = "Paket update terunduh ke router. Pasang kapan pun " +
                                    "dengan tombol Pasang — router akan reboot saat pemasangan."
                            } catch (e: Exception) {
                                msg = friendlyError(e)
                            }
                            busy = false
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!confirmInstall) {
                        NeoButton(
                            "Pasang Update & Reboot",
                            fill = MikroRed, textColor = Color.White, enabled = !busy
                        ) { confirmInstall = true }
                    } else {
                        Text(
                            "Yakin pasang update sekarang? Router akan mengunduh lalu " +
                                "REBOOT — semua koneksi putus sampai router menyala lagi " +
                                "dengan versi baru.",
                            fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MikroRed
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeoButton("Batal", Modifier.weight(1f), fill = CardFill) {
                                confirmInstall = false
                            }
                            NeoButton(
                                "Ya, Pasang", Modifier.weight(1f),
                                fill = MikroRed, textColor = Color.White
                            ) {
                                confirmInstall = false
                                busy = true
                                scope.launch {
                                    try {
                                        manager.runNoRetry { SystemRepo.installUpdate(it) }
                                    } catch (e: Exception) {
                                        // Router biasanya langsung reboot — koneksi putus di tengah jalan itu wajar.
                                    }
                                    msg = "Perintah pasang terkirim — router akan reboot dan " +
                                        "memasang update. Tunggu beberapa menit, lalu sambung lagi " +
                                        "dan cek versi di Dashboard."
                                    busy = false
                                }
                            }
                        }
                    }
                }
            }
            board?.let { b ->
                Spacer(Modifier.height(12.dp))
                NeoCard(Modifier.fillMaxWidth()) {
                    Text("RouterBOOT (Firmware Bootloader)", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(
                        "${b.model}${if (b.serialNumber.isNotBlank()) " • SN ${b.serialNumber}" else ""}",
                        color = Muted, fontSize = 11.sp
                    )
                    Text("Firmware sekarang: ${b.currentFirmware.ifBlank { "-" }}", fontSize = 12.sp)
                    Text("Firmware upgrade: ${b.upgradeFirmware.ifBlank { "-" }}", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    if (b.updateAvailable) {
                        if (!confirmFw) {
                            NeoButton("Upgrade RouterBOOT", fill = CardFill, enabled = !busy) {
                                confirmFw = true
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeoButton("Batal", Modifier.weight(1f), fill = CardFill) {
                                    confirmFw = false
                                }
                                NeoButton(
                                    "Ya, Upgrade", Modifier.weight(1f),
                                    fill = MikroRed, textColor = Color.White
                                ) {
                                    confirmFw = false
                                    busy = true
                                    scope.launch {
                                        try {
                                            manager.runNoRetry { SystemRepo.upgradeRouterboard(it) }
                                            msg = "RouterBOOT ditandai untuk upgrade — REBOOT " +
                                                "router agar firmware baru diterapkan (tombol " +
                                                "Reboot ada di Dashboard)."
                                            localTick++
                                        } catch (e: Exception) {
                                            msg = friendlyError(e)
                                        }
                                        busy = false
                                    }
                                }
                            }
                        }
                    } else {
                        Text("RouterBOOT sudah versi terbaru.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PackagesSection(manager: SessionManager, tick: Int) {
    Column {
        UpdateSection(manager, tick)
        Spacer(Modifier.height(14.dp))
        Text("PAKET TERPASANG", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
        PollingCards(
        manager = manager, resetKey = "sys-paket", tick = tick, onChanged = {},
        fetch = { SystemRepo.fetchPackages(it) },
        emptyText = "Tidak ada paket terbaca.",
        itemKey = { it.id }
    ) { e, _ ->
        NeoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                if (e.version.isNotBlank()) {
                    Text("v${e.version}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            StateTags("nonaktif" to e.disabled)
        }
        }
    }
}

@Composable
private fun BackupSection(manager: SessionManager) {
    var backupName by remember { mutableStateOf("backup-mikroku") }
    var exportName by remember { mutableStateOf("export-mikroku") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runAction(backup: Boolean) {
        if (busy) return
        val name = (if (backup) backupName else exportName).trim()
        if (name.isBlank()) return
        scope.launch {
            busy = true
            msg = null
            try {
                if (backup) {
                    manager.runNoRetry { SystemRepo.createBackup(it, name) }
                    msg = "Backup tersimpan di router: $name.backup — lihat di sub-menu File."
                } else {
                    manager.runNoRetry { SystemRepo.createExport(it, name) }
                    msg = "Export tersimpan di router: $name.rsc — lihat di sub-menu File."
                }
            } catch (e: Exception) {
                msg = friendlyError(e)
            }
            busy = false
        }
    }

    Column {
        Text(
            "Keduanya menyimpan SELURUH konfigurasi router:\n" +
                "• Backup (.backup) — salinan biner lengkap 1:1, termasuk user & password, " +
                "untuk restore di router yang sama.\n" +
                "• Export (.rsc) — seluruh settingan sebagai script teks yang bisa dibaca " +
                "dan dipindah ke router lain; password user & isi file tidak ikut.\n\n" +
                "File tersimpan di penyimpanan router — unduh ke HP dari sub-menu File " +
                "(tombol Unduh, lewat SFTP), atau biarkan Backup Otomatis di bawah yang mengunduhnya.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            backupName, { backupName = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Nama file backup") }
        )
        Spacer(Modifier.height(8.dp))
        NeoButton(
            "Buat Backup (.backup)",
            enabled = !busy && backupName.isNotBlank()
        ) { runAction(true) }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            exportName, { exportName = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Nama file export") }
        )
        Spacer(Modifier.height(8.dp))
        NeoButton(
            "Buat Export (.rsc)", fill = CardFill,
            enabled = !busy && exportName.isNotBlank()
        ) { runAction(false) }
        msg?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(20.dp))
        AutoBackupCard(manager)
    }
}

/**
 * Kartu Backup Otomatis ke HP: jadwal berkala (WorkManager) yang membuat
 * backup + export di router ini lalu mengunduhnya ke folder HP via SFTP.
 */
@Composable
private fun AutoBackupCard(manager: SessionManager) {
    val context = LocalContext.current
    val profile = remember(manager) { manager.connectionProfile() }
    var enabled by remember { mutableStateOf(SettingsStore.autoBackupEnabled(context)) }
    var freqDays by remember { mutableStateOf(SettingsStore.autoBackupFreqDays(context)) }
    var treeUri by remember { mutableStateOf(SettingsStore.autoBackupTreeUri(context)) }
    val lastRun = SettingsStore.autoBackupLastRun(context)

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Izin persisten gagal — URI tetap disimpan; worker akan melaporkan bila tak bisa menulis.
            }
            treeUri = uri.toString()
            if (enabled) {
                SettingsStore.setAutoBackup(context, true, freqDays, treeUri, profile.id)
            }
        }
    }

    fun apply(newEnabled: Boolean) {
        SettingsStore.setAutoBackup(context, newEnabled, freqDays, treeUri, profile.id)
        if (newEnabled) AutoBackupScheduler.schedule(context) else AutoBackupScheduler.cancel(context)
        enabled = newEnabled
    }

    NeoCard {
        Text("Backup Otomatis ke HP", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sesuai jadwal, aplikasi membuat backup + export di router ini lalu mengunduhnya " +
                "ke folder HP lewat SFTP — aplikasi tidak perlu dibuka. Hasil terakhir tampil di sini.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Folder tujuan: " + (treeUri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "belum dipilih"),
            fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        SmallButton("Pilih Folder di HP") { folderLauncher.launch(null) }
        Spacer(Modifier.height(12.dp))
        Text("Frekuensi", fontWeight = FontWeight.Black, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((days, label) in listOf(1 to "Harian", 7 to "Mingguan")) {
                val active = freqDays == days
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MikroYellow else CardFill)
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                        .clickable {
                            freqDays = days
                            if (enabled) {
                                SettingsStore.setAutoBackup(context, true, days, treeUri, profile.id)
                                AutoBackupScheduler.schedule(context)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
        if (profile.password.isBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Password router ini tidak tersimpan di aplikasi — jadwal tidak akan bisa login. " +
                    "Simpan password lewat tombol Ubah di daftar router.",
                color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        lastRun?.let {
            Spacer(Modifier.height(10.dp))
            Text("Terakhir jalan: $it", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (enabled) {
            NeoButton("Matikan Jadwal", fill = CardFill) { apply(false) }
        } else {
            NeoButton(
                "Aktifkan Jadwal",
                enabled = treeUri != null && profile.password.isNotBlank()
            ) { apply(true) }
        }
    }
}
