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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.MonitorRepo
import id.mikroku.data.RouterProfile
import id.mikroku.data.RouterStore
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class EditRouter(val profile: RouterProfile?) : Screen
    data class Detail(val profile: RouterProfile) : Screen
    data object Guide : Screen
    data object About : Screen
}

@Composable
fun MikroKuApp() {
    val context = LocalContext.current
    val store = remember { RouterStore(context) }
    var routers by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val statuses = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    var prefill by remember { mutableStateOf<RouterProfile?>(null) }
    var prefillRequest by remember { mutableStateOf(0) }

    fun persist(updated: List<RouterProfile>) {
        routers = updated
        store.save(updated)
    }

    fun upsert(profile: RouterProfile) {
        persist(
            if (routers.any { it.id == profile.id }) routers.map { if (it.id == profile.id) profile else it }
            else routers + profile
        )
    }

    fun checkStatuses() {
        routers.forEach { profile ->
            if (profile.password.isBlank()) {
                statuses[profile.id] = false
                return@forEach
            }
            scope.launch {
                val fp = try {
                    MonitorRepo.testConnection(profile)
                } catch (e: Exception) {
                    statuses[profile.id] = false
                    return@launch
                }
                statuses[profile.id] = true
                if (fp != null && profile.certFingerprint == null) {
                    persist(routers.map { if (it.id == profile.id) it.copy(certFingerprint = fp) else it })
                }
            }
        }
    }

    MikroKuTheme {
        // safeDrawingPadding: konten tidak lagi tertutup status bar / bar
        // navigasi (Android 15 menggambar edge-to-edge secara bawaan).
        Box(Modifier.fillMaxSize().background(Paper).safeDrawingPadding()) {
            when (val s = screen) {
                is Screen.Home -> {
                    LaunchedEffect(routers.size) { checkStatuses() }
                    HomeScreen(
                        routers = routers,
                        statuses = statuses,
                        prefill = prefill,
                        prefillRequest = prefillRequest,
                        onConnect = { session, saveAs ->
                            if (saveAs != null) upsert(saveAs)
                            screen = Screen.Detail(session)
                        },
                        onSaveOnly = { upsert(it) },
                        onOpenSaved = { profile ->
                            if (profile.password.isBlank()) {
                                // Password memang tidak disimpan — minta lewat form masuk.
                                prefill = profile
                                prefillRequest++
                            } else {
                                screen = Screen.Detail(profile)
                            }
                        },
                        onEditSaved = { screen = Screen.EditRouter(it) },
                        onRefreshSaved = { statuses.clear(); checkStatuses() },
                        onGuide = { screen = Screen.Guide },
                        onAbout = { screen = Screen.About }
                    )
                }
                is Screen.EditRouter -> RouterEditScreen(
                    initial = s.profile,
                    onBack = { screen = Screen.Home },
                    onSave = { saved ->
                        upsert(saved)
                        screen = Screen.Home
                    },
                    onDelete = { toDelete ->
                        persist(routers.filter { it.id != toDelete.id })
                        screen = Screen.Home
                    }
                )
                is Screen.Detail -> RouterDetailScreen(
                    profile = s.profile,
                    onBack = { screen = Screen.Home },
                    onFingerprintLearned = { fp ->
                        if (routers.any { it.id == s.profile.id }) {
                            persist(routers.map { if (it.id == s.profile.id) it.copy(certFingerprint = fp) else it })
                        }
                    }
                )
                is Screen.Guide -> GuideScreen(onBack = { screen = Screen.Home })
                is Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
            }
        }
    }
}

@Composable
fun SmallHeaderButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(2.dp, Ink, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RouterCard(
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
                    if (profile.password.isBlank()) {
                        Text("Password tidak disimpan — ketuk untuk login", color = Muted, fontSize = 12.sp)
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
