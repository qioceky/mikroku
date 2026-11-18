package id.mikroku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.CrudRepo
import id.mikroku.data.KID_DAYS
import id.mikroku.data.KidControlRepo
import id.mikroku.data.KidDeviceEntry
import id.mikroku.data.KidUserEntry
import id.mikroku.data.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
private fun KcField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    hint: String = ""
) {
    Column {
        OutlinedTextField(
            value, onValue,
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(label) },
            placeholder = if (hint.isNotBlank()) ({ Text(hint) }) else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Ink.copy(alpha = 0.4f),
                focusedContainerColor = CardFill,
                unfocusedContainerColor = CardFill,
                cursorColor = Ink
            )
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** "00:00:00-24:00:00" → "00:00-24:00" untuk ditampilkan/diedit. */
private fun displayRange(raw: String): String {
    if (raw.isBlank()) return ""
    fun shortTime(t: String): String {
        val parts = t.trim().split(":")
        return if (parts.size == 3 && parts[2] == "00") "${parts[0]}:${parts[1]}" else t.trim()
    }
    return raw.split(",").joinToString(",") { range ->
        range.split("-").joinToString("-") { shortTime(it) }
    }
}

/**
 * Normalisasi input editor ("06:00-21:00", boleh beberapa rentang koma)
 * ke format RouterOS ("06:00:00-21:00:00"). Null = format tidak valid.
 * String kosong valid = blokir seharian.
 */
private fun normalizeRange(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    fun normTime(t: String): String? {
        val parts = t.trim().split(":")
        if (parts.size !in 2..3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val s = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else 0
        if (h !in 0..24 || m !in 0..59 || s !in 0..59) return null
        if (h == 24 && (m != 0 || s != 0)) return null
        return "%02d:%02d:%02d".format(h, m, s)
    }
    val out = mutableListOf<String>()
    for (range in trimmed.split(",")) {
        val ends = range.split("-")
        if (ends.size != 2) return null
        val a = normTime(ends[0]) ?: return null
        val b = normTime(ends[1]) ?: return null
        out.add("$a-$b")
    }
    return out.joinToString(",")
}

/** Ringkasan jadwal untuk kartu user: hanya hari yang dibatasi yang disebut. */
private fun scheduleSummary(days: Map<String, String>): String {
    val notes = mutableListOf<String>()
    for ((key, label) in KID_DAYS) {
        val raw = days[key] ?: ""
        when {
            raw.isBlank() -> notes.add("$label: blokir seharian")
            displayRange(raw) != "00:00-24:00" ->
                notes.add("$label: ${displayRange(raw)}")
        }
    }
    return if (notes.isEmpty()) "Setiap hari seharian penuh (00:00–24:00)"
    else notes.joinToString(" • ")
}

@Composable
fun KidControlScreen(manager: SessionManager, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("user") }
    var tick by remember { mutableStateOf(0) }
    var users by remember { mutableStateOf<List<KidUserEntry>?>(null) }
    var devices by remember { mutableStateOf<List<KidDeviceEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editUser by remember { mutableStateOf<KidUserEntry?>(null) }
    var addUser by remember { mutableStateOf(false) }
    var editDevice by remember { mutableStateOf<KidDeviceEntry?>(null) }
    var addDevice by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manager, tick) {
        while (isActive) {
            try {
                users = manager.run { KidControlRepo.fetchUsers(it) }
                devices = manager.run { KidControlRepo.fetchDevices(it) }
                error = null
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            delay(10_000)
        }
    }

    if (addUser || editUser != null) {
        KidUserForm(manager, editUser) {
            addUser = false
            editUser = null
            tick++
        }
        return
    }
    if (addDevice || editDevice != null) {
        KidDeviceForm(manager, editDevice, users?.map { it.name } ?: emptyList()) {
            addDevice = false
            editDevice = null
            tick++
        }
        return
    }

    Column {
        SectionHeader("Kid Control", onBack) { tick++ }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Batasi internet anak per perangkat: jadwal jam boleh internet " +
                    "per hari, batas kecepatan, dan pause kapan saja. Perangkat " +
                    "dikenali dari alamat MAC dan diikat ke satu pengguna.",
                color = Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            KindChips(
                listOf("user" to "Pengguna", "device" to "Perangkat"),
                tab
            ) { tab = it }
            Spacer(Modifier.height(12.dp))
            error?.let {
                Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            msg?.let {
                Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (tab == "user") {
                NeoButton("Tambah Pengguna", fill = CardFill) { addUser = true }
                Spacer(Modifier.height(12.dp))
                val list = users
                when {
                    list == null -> Text("Memuat…", fontWeight = FontWeight.Bold)
                    list.isEmpty() -> Text(
                        "Belum ada pengguna Kid Control. Tambah pengguna, lalu ikat perangkatnya di tab Perangkat.",
                        color = Muted, fontSize = 12.sp
                    )
                    else -> for (e in list) {
                        var pauseOpen by remember(e.id) { mutableStateOf(false) }
                        var pauseDuration by remember(e.id) { mutableStateOf("30m") }
                        NeoCard(Modifier.fillMaxWidth()) {
                            Text(e.name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            StateTags(
                                "nonaktif" to e.disabled,
                                "sedang terblokir" to e.blocked
                            )
                            if (e.rateLimit.isNotBlank()) {
                                Text("Batas kecepatan: ${e.rateLimit}", fontSize = 12.sp)
                            }
                            Text(
                                "Jadwal: ${scheduleSummary(e.days)}",
                                color = Muted, fontSize = 11.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("Ubah") { editUser = e }
                                SmallButton(if (e.disabled) "Aktifkan" else "Nonaktifkan") {
                                    scope.launch {
                                        try {
                                            manager.run {
                                                KidControlRepo.setUserDisabled(it, e.id, !e.disabled)
                                            }
                                            tick++
                                        } catch (err: Exception) {
                                            msg = friendlyError(err)
                                        }
                                    }
                                }
                                ConfirmActionButton("Hapus", run = {
                                    manager.runNoRetry { CrudRepo.remove(it, "/ip/kid-control", e.id) }
                                }, onDone = { tick++ })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton(if (pauseOpen) "Tutup Pause" else "Pause") {
                                    pauseOpen = !pauseOpen
                                }
                                SmallButton("Lanjutkan") {
                                    scope.launch {
                                        try {
                                            manager.runNoRetry { KidControlRepo.resumeUser(it, e.name) }
                                            msg = "Internet ${e.name} dilanjutkan."
                                            tick++
                                        } catch (err: Exception) {
                                            msg = friendlyError(err)
                                        }
                                    }
                                }
                            }
                            if (pauseOpen) {
                                Spacer(Modifier.height(8.dp))
                                Text("Durasi pause", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                KindChips(
                                    listOf(
                                        "30m" to "30 menit",
                                        "1h" to "1 jam",
                                        "2h" to "2 jam",
                                        "" to "Sampai dilanjutkan"
                                    ),
                                    pauseDuration
                                ) { pauseDuration = it }
                                Spacer(Modifier.height(8.dp))
                                NeoButton(
                                    "Pause Sekarang",
                                    fill = MikroRed, textColor = Color.White
                                ) {
                                    scope.launch {
                                        try {
                                            manager.runNoRetry {
                                                KidControlRepo.pauseUser(
                                                    it, e.name,
                                                    pauseDuration.ifBlank { null }
                                                )
                                            }
                                            msg = "Internet ${e.name} di-pause" +
                                                if (pauseDuration.isBlank()) " sampai dilanjutkan manual."
                                                else " selama $pauseDuration."
                                            pauseOpen = false
                                            tick++
                                        } catch (err: Exception) {
                                            msg = friendlyError(err)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                NeoButton("Tambah Perangkat", fill = CardFill) { addDevice = true }
                Spacer(Modifier.height(12.dp))
                val list = devices
                when {
                    list == null -> Text("Memuat…", fontWeight = FontWeight.Bold)
                    list.isEmpty() -> Text(
                        "Belum ada perangkat terdaftar. Tambah perangkat dengan alamat MAC-nya.",
                        color = Muted, fontSize = 12.sp
                    )
                    else -> for (e in list) {
                        NeoCard(Modifier.fillMaxWidth()) {
                            Text(
                                e.name.ifBlank { e.macAddress },
                                fontWeight = FontWeight.Black, fontSize = 14.sp
                            )
                            Text(
                                "${e.macAddress} • pengguna: ${e.user}",
                                color = Muted, fontSize = 11.sp
                            )
                            StateTags(
                                "sedang terblokir" to e.blocked,
                                "dinamis" to e.dynamic,
                                "nonaktif" to e.disabled
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("Ubah") { editDevice = e }
                                ConfirmActionButton("Hapus", run = {
                                    manager.runNoRetry {
                                        CrudRepo.remove(it, "/ip/kid-control/device", e.id)
                                    }
                                }, onDone = { tick++ })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KidUserForm(
    manager: SessionManager,
    existing: KidUserEntry?,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var rateLimit by remember { mutableStateOf(existing?.rateLimit ?: "") }
    val dayStates = KID_DAYS.map { (key, _) ->
        key to remember {
            mutableStateOf(
                if (existing == null) "00:00-24:00"
                else displayRange(existing.days[key] ?: "")
            )
        }
    }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        SectionHeader(
            if (existing == null) "Tambah Pengguna" else "Ubah Pengguna",
            onDone
        ) { }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            KcField(name, { name = it }, "Nama Pengguna", "mis. Budi")
            KcField(
                rateLimit, { rateLimit = it }, "Batas Kecepatan (opsional)",
                "mis. 1M/1M — kosong = tanpa batas"
            )
            Text(
                "Jam BOLEH internet per hari. Format: 06:00-21:00 (boleh " +
                    "beberapa rentang dipisah koma). 00:00-24:00 = seharian; " +
                    "kosongkan = blokir seharian.",
                color = Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            for ((key, label) in KID_DAYS) {
                val state = dayStates.first { it.first == key }.second
                KcField(state.value, { state.value = it }, label, "00:00-24:00")
            }
            NeoButton(if (busy) "Menyimpan…" else "Simpan", fill = CardFill, enabled = !busy) {
                if (name.isBlank()) {
                    msg = "Nama pengguna wajib diisi."
                    return@NeoButton
                }
                val days = mutableMapOf<String, String>()
                var invalidDay: String? = null
                for ((key, label) in KID_DAYS) {
                    val raw = dayStates.first { it.first == key }.second.value
                    val normalized = normalizeRange(raw)
                    if (normalized == null) {
                        invalidDay = label
                        break
                    }
                    days[key] = normalized
                }
                if (invalidDay != null) {
                    msg = "Format jam hari $invalidDay tidak valid — pakai format 06:00-21:00."
                    return@NeoButton
                }
                busy = true
                msg = null
                scope.launch {
                    try {
                        manager.runNoRetry {
                            KidControlRepo.saveUser(
                                it, existing?.id, name.trim(), rateLimit.trim(), days
                            )
                        }
                        onDone()
                    } catch (e: Exception) {
                        msg = friendlyError(e)
                    }
                    busy = false
                }
            }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KidDeviceForm(
    manager: SessionManager,
    existing: KidDeviceEntry?,
    userNames: List<String>,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var mac by remember { mutableStateOf(existing?.macAddress ?: "") }
    var user by remember {
        mutableStateOf(existing?.user ?: userNames.firstOrNull() ?: "")
    }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        SectionHeader(
            if (existing == null) "Tambah Perangkat" else "Ubah Perangkat",
            onDone
        ) { }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            KcField(name, { name = it }, "Nama Perangkat", "mis. HP Budi")
            KcField(mac, { mac = it }, "Alamat MAC", "AA:BB:CC:DD:EE:FF")
            if (userNames.isNotEmpty()) {
                Text("Pengguna", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                KindChips(userNames.map { it to it }, user) { user = it }
                Spacer(Modifier.height(8.dp))
            } else {
                KcField(user, { user = it }, "Pengguna", "nama pengguna Kid Control")
            }
            NeoButton(if (busy) "Menyimpan…" else "Simpan", fill = CardFill, enabled = !busy) {
                when {
                    mac.isBlank() -> msg = "Alamat MAC wajib diisi."
                    user.isBlank() -> msg = "Pengguna wajib dipilih/diisi — buat dulu di tab Pengguna."
                    else -> {
                        busy = true
                        msg = null
                        scope.launch {
                            try {
                                manager.runNoRetry {
                                    KidControlRepo.saveDevice(
                                        it, existing?.id, name.trim(),
                                        mac.trim(), user.trim()
                                    )
                                }
                                onDone()
                            } catch (e: Exception) {
                                msg = friendlyError(e)
                            }
                            busy = false
                        }
                    }
                }
            }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
