package id.mikroku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.api.MikrotikClient
import id.mikroku.data.MonitorRepo
import id.mikroku.data.RouterProfile
import kotlinx.coroutines.launch

@Composable
fun RouterEditScreen(
    initial: RouterProfile?,
    onBack: () -> Unit,
    onSave: (RouterProfile) -> Unit,
    onDelete: (RouterProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var useSsl by remember { mutableStateOf(initial?.useSsl ?: false) }
    var portText by remember {
        mutableStateOf(
            (initial?.port ?: MikrotikClient.DEFAULT_PORT_PLAIN).toString()
        )
    }
    var username by remember { mutableStateOf(initial?.username ?: "admin") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun buildProfile(): RouterProfile? {
        val port = portText.toIntOrNull() ?: return null
        if (name.isBlank() || host.isBlank() || username.isBlank()) return null
        return RouterProfile(
            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            host = host.trim(),
            port = port,
            useSsl = useSsl,
            username = username.trim(),
            password = password,
            note = note.trim(),
            // Ganti mode/host membuat sidik jari lama tidak berlaku.
            certFingerprint = if (initial != null && initial.host == host.trim() && initial.useSsl == useSsl)
                initial.certFingerprint else null
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (initial == null) "Tambah Router" else "Ubah Router", fontSize = 22.sp, fontWeight = FontWeight.Black)
            NeoIconButton(Fa.ARROW_LEFT) { onBack() }
        }
        Spacer(Modifier.height(14.dp))

        Field("Nama router", name) { name = it }
        Field("Alamat IP / host", host) { host = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useSsl, onCheckedChange = {
                useSsl = it
                portText = if (it) MikrotikClient.DEFAULT_PORT_SSL.toString()
                else MikrotikClient.DEFAULT_PORT_PLAIN.toString()
            })
            Text("Gunakan API-SSL (port 8729, terenkripsi)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (!useSsl) {
            NeoCard(fill = MikroBlue) {
                Text(
                    "Mode API polos (8728): sejak RouterOS 6.43 password login terkirim tanpa enkripsi. " +
                        "Aman untuk LAN rumah/kantor tepercaya atau lewat VPN — JANGAN buka port 8728 ke internet.",
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Field("Port", portText) { portText = it.filter { c -> c.isDigit() } }
        Field("Username API", username) { username = it }
        Column {
            Text("Password", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(8.dp))
        }
        Field("Catatan (opsional)", note) { note = it }

        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(if (testing) "Menguji…" else "Tes Koneksi", Modifier.weight(1f), fill = CardFill, enabled = !testing) {
                val profile = buildProfile()
                if (profile == null) {
                    status = "Lengkapi nama, host, port, dan username dulu."
                    return@NeoButton
                }
                testing = true
                status = null
                scope.launch {
                    try {
                        MonitorRepo.testConnection(profile)
                        status = "Berhasil tersambung dan login ke ${profile.host}."
                    } catch (e: Exception) {
                        status = "Gagal: ${e.message}"
                    }
                    testing = false
                }
            }
            NeoButton("Simpan", Modifier.weight(1f)) {
                val profile = buildProfile()
                if (profile == null) {
                    status = "Lengkapi nama, host, port, dan username dulu."
                } else onSave(profile)
            }
        }

        if (initial != null) {
            Spacer(Modifier.height(18.dp))
            if (!confirmDelete) {
                NeoButton("Hapus Router Ini", fill = MikroRed, textColor = androidx.compose.ui.graphics.Color.White) {
                    confirmDelete = true
                }
            } else {
                NeoCard(fill = MikroRed) {
                    Text("Yakin hapus router ini dari aplikasi? (Konfigurasi di router tidak terpengaruh)", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoButton("Batal", Modifier.weight(1f), fill = CardFill) { confirmDelete = false }
                    NeoButton("Ya, Hapus", Modifier.weight(1f), fill = MikroRed, textColor = androidx.compose.ui.graphics.Color.White) { onDelete(initial) }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
    }
}
