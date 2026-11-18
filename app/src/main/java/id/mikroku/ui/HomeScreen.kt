package id.mikroku.ui

import android.content.Context
import android.net.wifi.WifiManager
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.BuildConfig
import id.mikroku.api.DiscoveredRouter
import id.mikroku.api.MikrotikClient
import id.mikroku.api.MndpDiscovery
import id.mikroku.data.RouterProfile
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Layar awal model aplikasi resmi (dipilih user untuk v0.1.1):
 * form login langsung + tab MASUK / TERSIMPAN / DITEMUKAN,
 * gaya tetap soft neobrutalism MikroKu.
 */
@Composable
fun HomeScreen(
    routers: List<RouterProfile>,
    statuses: Map<String, Boolean>,
    prefill: RouterProfile?,
    prefillRequest: Int,
    onConnect: (session: RouterProfile, saveAs: RouterProfile?) -> Unit,
    onSaveOnly: (RouterProfile) -> Unit,
    onOpenSaved: (RouterProfile) -> Unit,
    onEditSaved: (RouterProfile) -> Unit,
    onRefreshSaved: () -> Unit,
    onGuide: () -> Unit,
    onAbout: () -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 Masuk, 1 Tersimpan, 2 Ditemukan

    // Form login
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var keepPassword by remember { mutableStateOf(true) }
    var useSsl by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(prefillRequest) {
        if (prefill != null) {
            address = if (prefill.port == prefill.defaultPort) prefill.host
            else "${prefill.host}:${prefill.port}"
            username = prefill.username
            if (prefill.password.isNotBlank()) password = prefill.password
            useSsl = prefill.useSsl
            tab = 0
            formError = null
        }
    }

    fun buildSession(): RouterProfile? {
        val raw = address.trim()
        if (raw.isEmpty()) {
            formError = "Isi alamat router dulu (mis. 192.168.88.1)."
            return null
        }
        if (username.isBlank()) {
            formError = "Isi username dulu."
            return null
        }
        var host = raw
        var port = if (useSsl) MikrotikClient.DEFAULT_PORT_SSL else MikrotikClient.DEFAULT_PORT_PLAIN
        val colon = raw.lastIndexOf(':')
        if (colon > 0) {
            val maybePort = raw.substring(colon + 1).toIntOrNull()
            if (maybePort != null) {
                host = raw.substring(0, colon)
                port = maybePort
            }
        }
        formError = null
        val existing = routers.firstOrNull {
            it.host == host && it.port == port && it.username == username.trim()
        }
        return RouterProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = existing?.name ?: host,
            host = host,
            port = port,
            useSsl = useSsl,
            username = username.trim(),
            password = password,
            note = existing?.note ?: "",
            certFingerprint = existing?.certFingerprint,
            sshFingerprint = existing?.sshFingerprint
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tile logo: kotak kuning khas MikroKu berikon menara siar FA.
            Box(Modifier.padding(end = 3.dp, bottom = 3.dp)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(x = 3.dp, y = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Shadow)
                )
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MikroYellow)
                        .border(2.5.dp, Ink, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    FaIcon(Fa.TOWER, size = 20.sp, color = Color(0xFF161616))
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("MikroKu", fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text("v${BuildConfig.VERSION_NAME} • RouterOS v6", color = Muted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallHeaderButton("Panduan") { onGuide() }
                SmallHeaderButton("Tentang") { onAbout() }
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeTab("MASUK", tab == 0, Modifier.weight(1f)) { tab = 0 }
            HomeTab("TERSIMPAN", tab == 1, Modifier.weight(1f)) { tab = 1 }
            HomeTab("DITEMUKAN", tab == 2, Modifier.weight(1f)) { tab = 2 }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> NeoCard {
                LoginForm(
                address = address, onAddress = { address = it },
                username = username, onUsername = { username = it },
                password = password, onPassword = { password = it },
                passwordVisible = passwordVisible, onToggleVisible = { passwordVisible = !passwordVisible },
                keepPassword = keepPassword, onKeep = { keepPassword = it },
                useSsl = useSsl, onUseSsl = { useSsl = it },
                error = formError, savedNote = savedNote,
                onSave = {
                    val session = buildSession() ?: return@LoginForm
                    val toSave = if (keepPassword) session else session.copy(password = "")
                    onSaveOnly(toSave)
                    savedNote = "Tersimpan sebagai \"${toSave.name}\" — lihat tab TERSIMPAN."
                },
                onConnect = {
                    val session = buildSession() ?: return@LoginForm
                    val saveAs = if (keepPassword) session else null
                    onConnect(session, saveAs)
                }
                )
            }
            1 -> SavedTab(
                routers = routers,
                statuses = statuses,
                onOpen = onOpenSaved,
                onEdit = onEditSaved,
                onRefresh = onRefreshSaved,
                onGoLogin = { tab = 0 }
            )
            2 -> DiscoveredTab(
                onPick = { d ->
                    address = d.ipAddress
                    formError = null
                    savedNote = "Router \"${d.identity}\" dipilih — isi username & password, lalu Sambungkan."
                    tab = 0
                }
            )
        }
    }
}

@Composable
private fun HomeTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MikroYellow else CardFill)
            .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun LoginForm(
    address: String, onAddress: (String) -> Unit,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    passwordVisible: Boolean, onToggleVisible: () -> Unit,
    keepPassword: Boolean, onKeep: (Boolean) -> Unit,
    useSsl: Boolean, onUseSsl: (Boolean) -> Unit,
    error: String?, savedNote: String?,
    onSave: () -> Unit, onConnect: () -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        FormField("Address (IP router, boleh tambah :port)") {
            NeoField(address, onAddress, Fa.SERVER, placeholder = "192.168.88.1")
        }
        FormField("Username") {
            NeoField(username, onUsername, Fa.USER)
        }
        FormField("Password") {
            NeoField(
                password, onPassword, Fa.LOCK,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    Text(
                        if (passwordVisible) "Sembunyi" else "Lihat",
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onToggleVisible() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = keepPassword, onCheckedChange = onKeep)
            Text("Simpan password (terenkripsi di HP)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeTab("API 8728", !useSsl, Modifier.weight(1f)) { onUseSsl(false) }
            HomeTab("API-SSL 8729", useSsl, Modifier.weight(1f)) { onUseSsl(true) }
        }
        if (!useSsl) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Mode API polos: password terkirim tanpa enkripsi — hanya LAN tepercaya/VPN.",
                color = MikroRed, fontSize = 11.sp
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MikroRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        savedNote?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MikroGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton("SIMPAN", Modifier.weight(1f), fill = CardFill) { onSave() }
            NeoButton("SAMBUNGKAN", Modifier.weight(1f)) { onConnect() }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Router harus sudah mengaktifkan service API (Winbox → IP → Services) dan user ber-policy api. Lihat Panduan untuk langkah lengkapnya.",
            color = Muted, fontSize = 12.sp
        )
    }
}

/** Kolom isian gaya MikroKu: ikon FA di kiri, border tinta, latar Paper. */
@Composable
private fun NeoField(
    value: String,
    onValueChange: (String) -> Unit,
    icon: String,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value, onValueChange,
        Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink,
            unfocusedBorderColor = Ink.copy(alpha = 0.4f),
            focusedContainerColor = Paper,
            unfocusedContainerColor = Paper,
            cursorColor = Ink
        ),
        placeholder = if (placeholder != null) ({ Text(placeholder, color = Muted) }) else null,
        leadingIcon = { FaIcon(icon, size = 15.sp, color = Muted) },
        visualTransformation = visualTransformation,
        trailingIcon = trailing
    )
}

@Composable
private fun FormField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        content()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SavedTab(
    routers: List<RouterProfile>,
    statuses: Map<String, Boolean>,
    onOpen: (RouterProfile) -> Unit,
    onEdit: (RouterProfile) -> Unit,
    onRefresh: () -> Unit,
    onGoLogin: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (routers.isEmpty()) {
            NeoCard {
                Text("Belum ada router tersimpan", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Isi form di tab MASUK lalu ketuk SIMPAN, atau pilih router dari tab DITEMUKAN.", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            NeoButton("Ke Form Masuk") { onGoLogin() }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(routers, key = { it.id }) { profile ->
                    RouterCard(
                        profile = profile,
                        online = statuses[profile.id],
                        onOpen = { onOpen(profile) },
                        onEdit = { onEdit(profile) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            NeoButton("Segarkan Status", fill = CardFill) { onRefresh() }
        }
    }
}

@Composable
private fun DiscoveredTab(onPick: (DiscoveredRouter) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DiscoveredRouter>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    val multicastLock = remember {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("mikroku-mndp").apply { setReferenceCounted(false) }
    }
    DisposableEffect(Unit) {
        onDispose { try { if (multicastLock.isHeld) multicastLock.release() } catch (_: Exception) {} }
    }

    fun scan() {
        if (scanning) return
        scanning = true
        message = null
        scope.launch {
            try {
                if (!multicastLock.isHeld) multicastLock.acquire()
                results = MndpDiscovery.discover()
                message = if (results.isEmpty())
                    "Tidak ada router ditemukan. Pastikan HP tersambung ke WiFi yang sama dengan router dan penemuan tetangga (neighbor discovery) router aktif."
                else "Ditemukan ${results.size} router."
            } catch (e: Exception) {
                message = if (e is java.net.BindException ||
                    (e.message?.contains("already in use", ignoreCase = true) == true)
                ) {
                    "Port penemuan (UDP 5678) sedang dipakai aplikasi lain — tutup aplikasi " +
                        "MikroTik/WinBox lain di HP ini, lalu pindai ulang."
                } else {
                    "Gagal memindai: ${e.message}"
                }
            }
            scanning = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Router MikroTik di jaringan WiFi yang sama (protokol MNDP, sama seperti WinBox). Ketuk router untuk mengisi form masuk.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        if (scanning) {
            Text("Memindai jaringan…", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        message?.let { Text(it, fontSize = 12.sp, color = Muted); Spacer(Modifier.height(10.dp)) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results, key = { it.key }) { d ->
                NeoCard(onClick = { onPick(d) }) {
                    Row {
                        StatusDot(true, Modifier.padding(top = 5.dp, end = 8.dp))
                        Column {
                            Text(d.identity, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("${d.ipAddress} • ${d.macAddress}", color = Muted, fontSize = 12.sp)
                            val meta = listOf(d.board, d.version, d.interfaceName).filter { it.isNotBlank() }.joinToString(" • ")
                            if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        NeoButton(if (scanning) "Memindai…" else "Pindai Ulang", fill = CardFill, enabled = !scanning) { scan() }
    }
}
