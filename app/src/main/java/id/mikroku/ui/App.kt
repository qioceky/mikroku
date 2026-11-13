package id.mikroku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.MonitorRepo
import id.mikroku.data.RouterProfile
import id.mikroku.data.RouterStore
import kotlinx.coroutines.launch

sealed interface Screen {
    data object RouterList : Screen
    data class EditRouter(val profile: RouterProfile?) : Screen
    data class Detail(val routerId: String) : Screen
    data object Guide : Screen
    data object About : Screen
}

@Composable
fun MikroKuApp() {
    val context = LocalContext.current
    val store = remember { RouterStore(context) }
    var routers by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.RouterList) }
    val statuses = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    fun persist(updated: List<RouterProfile>) {
        routers = updated
        store.save(updated)
    }

    fun checkStatuses() {
        routers.forEach { profile ->
            scope.launch {
                val fp = try {
                    MonitorRepo.testConnection(profile)
                } catch (e: Exception) {
                    statuses[profile.id] = false
                    return@launch
                }
                statuses[profile.id] = true
                // Simpan sidik jari sertifikat (TOFU) hasil koneksi SSL pertama.
                if (fp != null && profile.certFingerprint == null) {
                    persist(routers.map { if (it.id == profile.id) it.copy(certFingerprint = fp) else it })
                }
            }
        }
    }

    MikroKuTheme {
        Box(Modifier.fillMaxSize().background(Paper)) {
            when (val s = screen) {
                is Screen.RouterList -> {
                    LaunchedEffect(routers.size) { checkStatuses() }
                    RouterListScreen(
                        routers = routers,
                        statuses = statuses,
                        onAdd = { screen = Screen.EditRouter(null) },
                        onOpen = { screen = Screen.Detail(it.id) },
                        onEdit = { screen = Screen.EditRouter(it) },
                        onGuide = { screen = Screen.Guide },
                        onAbout = { screen = Screen.About },
                        onRefresh = { statuses.clear(); checkStatuses() }
                    )
                }
                is Screen.EditRouter -> RouterEditScreen(
                    initial = s.profile,
                    onBack = { screen = Screen.RouterList },
                    onSave = { saved ->
                        persist(
                            if (routers.any { it.id == saved.id }) routers.map { if (it.id == saved.id) saved else it }
                            else routers + saved
                        )
                        screen = Screen.RouterList
                    },
                    onDelete = { toDelete ->
                        persist(routers.filter { it.id != toDelete.id })
                        screen = Screen.RouterList
                    }
                )
                is Screen.Detail -> {
                    val profile = routers.firstOrNull { it.id == s.routerId }
                    if (profile == null) {
                        screen = Screen.RouterList
                    } else {
                        RouterDetailScreen(
                            profile = profile,
                            onBack = { screen = Screen.RouterList },
                            onFingerprintLearned = { fp ->
                                persist(routers.map { if (it.id == profile.id) it.copy(certFingerprint = fp) else it })
                            }
                        )
                    }
                }
                is Screen.Guide -> GuideScreen(onBack = { screen = Screen.RouterList })
                is Screen.About -> AboutScreen(onBack = { screen = Screen.RouterList })
            }
        }
    }
}

@Composable
private fun RouterListScreen(
    routers: List<RouterProfile>,
    statuses: Map<String, Boolean>,
    onAdd: () -> Unit,
    onOpen: (RouterProfile) -> Unit,
    onEdit: (RouterProfile) -> Unit,
    onGuide: () -> Unit,
    onAbout: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MikroKu", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallHeaderButton("Panduan") { onGuide() }
                SmallHeaderButton("Tentang") { onAbout() }
            }
        }
        Text("Panel admin MikroTik RouterOS v6", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        if (routers.isEmpty()) {
            NeoCard {
                Text("Belum ada router", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tambahkan router pertamamu. Pastikan service API (port 8728/8729) sudah aktif di router — lihat Panduan bila belum.",
                    color = Muted, fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(routers, key = { it.id }) { profile ->
                    RouterCard(
                        profile = profile,
                        online = statuses[profile.id],
                        onOpen = { onOpen(profile) },
                        onEdit = { onEdit(profile) }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton("Segarkan", Modifier.weight(1f), fill = androidx.compose.ui.graphics.Color.White) { onRefresh() }
            NeoButton("+ Tambah Router", Modifier.weight(2f)) { onAdd() }
        }
    }
}

@Composable
private fun SmallHeaderButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .border(2.dp, Ink, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RouterCard(
    profile: RouterProfile,
    online: Boolean?,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    NeoCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                StatusDot(online, Modifier.padding(top = 5.dp, end = 8.dp))
                Column {
                    Text(profile.name, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        "${profile.host}:${profile.port} • ${if (profile.useSsl) "API-SSL" else "API"} • ${profile.username}",
                        color = Muted, fontSize = 12.sp
                    )
                    if (profile.note.isNotBlank()) {
                        Text(profile.note, color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Text(
                when (online) {
                    true -> "Online"
                    false -> "Offline"
                    null -> "…"
                },
                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                color = when (online) {
                    true -> MikroGreen
                    false -> MikroRed
                    null -> Muted
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ketuk kartu untuk membuka dashboard • ", color = Muted, fontSize = 11.sp)
            Text(
                "Ubah",
                color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onEdit() }
            )
        }
        if (!profile.useSsl) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Mode API polos (8728): password terkirim tanpa enkripsi — hanya untuk LAN tepercaya/VPN.",
                color = MikroRed, fontSize = 11.sp
            )
        }
    }
}
