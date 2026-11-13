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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.DashboardData
import id.mikroku.data.InterfaceData
import id.mikroku.data.MonitorRepo
import id.mikroku.data.RouterProfile
import id.mikroku.data.RouterSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun RouterDetailScreen(
    profile: RouterProfile,
    onBack: () -> Unit,
    onFingerprintLearned: (String) -> Unit
) {
    var session by remember { mutableStateOf<RouterSession?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) } // 0 dashboard, 1 interface

    LaunchedEffect(profile.id) {
        connectError = null
        try {
            val s = MonitorRepo.openSession(profile)
            session = s
            val fp = s.certFingerprint
            if (fp != null && profile.certFingerprint == null) onFingerprintLearned(fp)
        } catch (e: Exception) {
            connectError = e.message ?: "Tidak bisa tersambung"
        }
    }
    DisposableEffect(Unit) {
        onDispose { session?.close() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← Kembali", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
            Text(profile.name, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text("${profile.host}:${profile.port}", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))

        val s = session
        when {
            connectError != null -> {
                NeoCard(fill = MikroRed) {
                    Text("Tidak bisa tersambung", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(connectError ?: "", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Periksa: HP satu jaringan dengan router, service API aktif (IP → Services), user punya policy api, alamat/port benar.",
                        color = Color.White, fontSize = 12.sp
                    )
                }
            }
            s == null -> Text("Menyambungkan ke ${profile.host}…", fontWeight = FontWeight.Bold)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabChip("Dashboard", tab == 0, Modifier.weight(1f)) { tab = 0 }
                    TabChip("Interface", tab == 1, Modifier.weight(1f)) { tab = 1 }
                }
                Spacer(Modifier.height(12.dp))
                if (tab == 0) DashboardTab(s, profile)
                else InterfacesTab(s)
            }
        }
    }
}

@Composable
private fun TabChip(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MikroYellow else Color.White)
            .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun DashboardTab(session: RouterSession, profile: RouterProfile) {
    var data by remember { mutableStateOf<DashboardData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReboot by remember { mutableStateOf(false) }
    var rebootMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session) {
        while (isActive) {
            try {
                data = MonitorRepo.fetchDashboard(session.client)
                error = null
            } catch (e: Exception) {
                error = e.message
            }
            delay(5_000)
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val d = data
            if (d == null) {
                Text(if (error != null) "Gagal memuat: $error" else "Memuat data router…", fontWeight = FontWeight.Bold)
            } else {
                NeoCard {
                    Row {
                        StatusDot(true, Modifier.padding(top = 5.dp, end = 8.dp))
                        Column {
                            Text(d.identity, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("${d.boardName} • RouterOS ${d.version}", color = Muted, fontSize = 12.sp)
                            Text("Jam router: ${d.clockTime} • ${d.clockDate}", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (data != null) {
            val d = data!!
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("CPU", d.cpuLoad?.let { "$it%" } ?: "-", Modifier.weight(1f), d.cpuLoad)
                    val usedPct = if (d.freeMemory != null && d.totalMemory != null && d.totalMemory > 0)
                        (100 - d.freeMemory * 100 / d.totalMemory).toInt() else null
                    StatCard(
                        "RAM Terpakai",
                        usedPct?.let { "$it%" } ?: "-",
                        Modifier.weight(1f), usedPct
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeoCard(Modifier.weight(1f)) {
                        Text("RAM BEBAS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                        Text(
                            if (d.freeMemory != null && d.totalMemory != null)
                                "${formatBytes(d.freeMemory)} / ${formatBytes(d.totalMemory)}" else "-",
                            fontWeight = FontWeight.Black, fontSize = 15.sp
                        )
                    }
                    NeoCard(Modifier.weight(1f)) {
                        Text("UPTIME", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                        Text(d.uptime, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
            if (d.temperature != null || d.voltage != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeoCard(Modifier.weight(1f)) {
                            Text("SUHU", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                            Text(d.temperature?.let { "$it °C" } ?: "-", fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                        NeoCard(Modifier.weight(1f)) {
                            Text("TEGANGAN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
                            Text(d.voltage?.let { "$it V" } ?: "-", fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(2.dp))
            rebootMsg?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            if (!confirmReboot) {
                NeoButton("Reboot Router", fill = MikroRed, textColor = Color.White) { confirmReboot = true }
            } else {
                NeoCard(fill = MikroRed) {
                    Text(
                        "Yakin reboot ${profile.name}? Semua koneksi lewat router ini akan putus sampai router menyala lagi.",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoButton("Batal", Modifier.weight(1f), fill = Color.White) { confirmReboot = false }
                    NeoButton("Ya, Reboot", Modifier.weight(1f), fill = MikroRed, textColor = Color.White) {
                        confirmReboot = false
                        rebootMsg = "Perintah reboot terkirim…"
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                MonitorRepo.reboot(session.client)
                            } catch (e: Exception) {
                                // Router menutup koneksi saat reboot — itu tanda berhasil, bukan gagal.
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, percent: Int?) {
    NeoCard(modifier) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Muted)
        Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp)
        if (percent != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .border(2.dp, Ink, RoundedCornerShape(99.dp))
                    .background(Color.White)
            ) {
                val barColor = when {
                    percent >= 85 -> MikroRed
                    percent >= 60 -> MikroYellow
                    else -> MikroGreen
                }
                Box(
                    Modifier
                        .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                        .height(10.dp)
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
private fun InterfacesTab(session: RouterSession) {
    var ifaces by remember { mutableStateOf<List<InterfaceData>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val prev = remember { mutableStateMapOf<String, Pair<Long, Long>>() } // name -> (rx, tx)
    val prevTime = remember { mutableStateOf(0L) }
    val rates = remember { mutableStateMapOf<String, Pair<Double, Double>>() }
    val history = remember { mutableStateListOf<Double>() } // total rx rate untuk spark

    LaunchedEffect(session) {
        while (isActive) {
            try {
                val now = System.currentTimeMillis()
                val list = MonitorRepo.fetchInterfaces(session.client)
                val last = prevTime.value
                if (last > 0) {
                    val dt = (now - last) / 1000.0
                    if (dt > 0) {
                        var totalRx = 0.0
                        list.forEach { f ->
                            val p = prev[f.name]
                            if (p != null) {
                                val rxRate = (f.rxBytes - p.first).coerceAtLeast(0) / dt
                                val txRate = (f.txBytes - p.second).coerceAtLeast(0) / dt
                                rates[f.name] = rxRate to txRate
                                if (f.running) totalRx += rxRate
                            }
                        }
                        history.add(totalRx)
                        if (history.size > 24) history.removeAt(0)
                    }
                }
                prev.clear()
                list.forEach { prev[it.name] = it.rxBytes to it.txBytes }
                prevTime.value = now
                ifaces = list
                error = null
            } catch (e: Exception) {
                error = e.message
            }
            delay(2_000)
        }
    }

    Column {
        Text("Diperbarui tiap 2 detik", color = Muted, fontSize = 12.sp)
        if (history.size > 1) {
            Spacer(Modifier.height(8.dp))
            SparkBars(history.toList())
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Gagal memuat interface: $it", color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ifaces, key = { it.name }) { f ->
                val r = rates[f.name]
                NeoCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Row {
                                StatusDot(f.running && !f.disabled, Modifier.padding(top = 4.dp, end = 7.dp))
                                Text(f.name, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            }
                            val sub = buildString {
                                if (f.type.isNotBlank()) append(f.type)
                                if (f.comment.isNotBlank()) append(" • ${f.comment}")
                                if (f.disabled) append(" • nonaktif")
                            }
                            if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 12.sp)
                            f.ipAddresses.forEach { Text(it, color = Muted, fontSize = 12.sp) }
                            Text("Total ↓ ${formatBytes(f.rxBytes)} • ↑ ${formatBytes(f.txBytes)}", color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("↓ ${formatRate(r?.first ?: 0.0)}", color = MikroGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text("↑ ${formatRate(r?.second ?: 0.0)}", color = Color(0xFFB47600), fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SparkBars(values: List<Double>) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Row(
        Modifier.fillMaxWidth().height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { v ->
            val frac = (v / max).toFloat().coerceIn(0.05f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height((34 * frac).dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(MikroGreen)
                    .border(1.5.dp, Ink, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            )
        }
    }
}
