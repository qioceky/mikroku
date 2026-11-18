package id.mikroku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import id.mikroku.data.SessionManager
import id.mikroku.data.SusAction
import id.mikroku.data.SusFinding
import id.mikroku.data.SusRepo
import id.mikroku.data.SusSeverity
import kotlinx.coroutines.launch

/**
 * SUS versi API: pemindaian heuristik tanda router terkompromi.
 * Dilabeli jujur sebagai INDIKASI — bukan SUS resmi MikroTik.
 */
@Composable
fun SusScreen(manager: SessionManager, onBack: () -> Unit) {
    val username = remember { manager.connectionProfile().username }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var findings by remember { mutableStateOf<List<SusFinding>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun scan() {
        if (scanning) return
        scanning = true
        msg = null
        scope.launch {
            try {
                findings = manager.run { SusRepo.scan(it, username) }
                scanned = true
            } catch (e: Exception) {
                msg = friendlyError(e)
            }
            scanning = false
        }
    }

    Column {
        SectionHeader("SUS — Cek Router Terkompromi", onBack) { tick++; if (scanned) scan() }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NeoCard(fill = MikroYellow) {
                Text(
                    "Ini pemindaian HEURISTIK MikroKu (SUS versi API) — bukan " +
                        "algoritma SUS resmi MikroTik yang tidak dipublikasikan. " +
                        "Temuan adalah INDIKASI untuk diperiksa, bukan vonis. " +
                        "Bila router benar terkompromi, satu-satunya jalan bersih " +
                        "adalah Netinstall + ganti semua password.",
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = Color(0xFF161616)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Yang diperiksa: script & scheduler yang berkomunikasi ke " +
                    "internet, SOCKS/web proxy menyala, user asing berhak " +
                    "penuh, DNS terbuka & entri statis nama publik, service " +
                    "lama terbuka ke semua alamat.",
                color = Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            NeoButton(
                if (scanning) "Memindai…" else if (scanned) "Pindai Ulang" else "Mulai Pindai",
                fill = CardFill, enabled = !scanning
            ) { scan() }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            if (scanned) {
                if (findings.isEmpty()) {
                    NeoCard {
                        Text(
                            "Tidak ada indikasi ditemukan",
                            fontWeight = FontWeight.Black, fontSize = 15.sp
                        )
                        Text(
                            "Pemeriksaan heuristik bersih. Ini bukan jaminan 100% — " +
                                "tetap jaga password & update RouterOS.",
                            color = Muted, fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        "${findings.size} INDIKASI DITEMUKAN",
                        fontWeight = FontWeight.Black, fontSize = 12.sp, color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    for (f in findings) {
                        var done by remember(f.key, tick) { mutableStateOf(false) }
                        NeoCard(Modifier.fillMaxWidth()) {
                            val (label, color) = when (f.severity) {
                                SusSeverity.TINGGI -> "TINGGI" to MikroRed
                                SusSeverity.SEDANG -> "SEDANG" to Color(0xFFB47600)
                                SusSeverity.PERIKSA -> "PERIKSA" to Muted
                            }
                            Text(label, fontWeight = FontWeight.Black, fontSize = 11.sp, color = color)
                            Text(f.title, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Text(f.detail, color = Muted, fontSize = 12.sp)
                            if (f.action != SusAction.TIDAK_ADA) {
                                Spacer(Modifier.height(8.dp))
                                if (done) {
                                    Text(
                                        "Sudah dinonaktifkan/dimatikan.",
                                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                        color = MikroGreen
                                    )
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ConfirmActionButton(
                                            if (f.action == SusAction.MATIKAN_DNS_REMOTE) "Matikan" else "Nonaktifkan",
                                            run = {
                                                manager.runNoRetry { SusRepo.applyAction(it, f) }
                                            },
                                            onDone = { done = true }
                                        )
                                    }
                                }
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
