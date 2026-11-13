package id.mikroku.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.BuildConfig
import id.mikroku.data.CrashLogger

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Panduan Koneksi", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("← Kembali", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(14.dp))

        StepCard(
            "1", "Aktifkan service API di router",
            "Buka Winbox → IP → Services. Aktifkan \"api\" (port 8728) atau lebih baik \"api-ssl\" (port 8729). " +
                "Isi kolom Available From dengan subnet LAN kamu (mis. 192.168.88.0/24) supaya API tidak terbuka ke jaringan lain."
        )
        StepCard(
            "2", "Buat user khusus untuk MikroKu",
            "Di Winbox → System → Users, buat group baru dengan policy: read + api (tambah write hanya bila nanti memakai fitur ubah). " +
                "Lalu buat user baru di group itu dengan password kuat. Jangan pakai user admin utama untuk aplikasi."
        )
        StepCard(
            "3", "Khusus API-SSL (8729)",
            "api-ssl butuh sertifikat di router (System → Certificates) yang ditunjuk di IP → Services → api-ssl → Certificate. " +
                "Tanpa sertifikat, api-ssl tetap bisa jalan tetapi koneksi tidak terverifikasi; MikroKu akan memberi tahu. " +
                "Saat pertama kali tersambung via SSL, MikroKu menyimpan sidik jari sertifikat dan akan menolak bila sidik jari berubah."
        )
        StepCard(
            "4", "Tambahkan router di MikroKu",
            "Ketuk \"+ Tambah Router\", isi alamat IP router (mis. 192.168.88.1), port sesuai service yang aktif, username + password user API dari langkah 2, lalu Tes Koneksi."
        )
        StepCard(
            "5", "Akses dari luar rumah",
            "Jangan pernah membuka port 8728/8729 langsung ke internet. Bila perlu akses dari luar, hubungkan HP ke jaringan rumah lewat VPN (WireGuard/Tailscale) lalu pakai alamat IP LAN router seperti biasa."
        )
        Spacer(Modifier.height(10.dp))
        NeoCard(fill = MikroBlue) {
            Text("Catatan keamanan login", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sejak RouterOS 6.43, login API mengirim password tanpa enkripsi di dalam koneksi. " +
                    "Di port 8728 artinya password bisa terbaca bila ada yang menyadap LAN. Gunakan api-ssl (8729) atau VPN bila jaringan tidak sepenuhnya tepercaya.",
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepCard(number: String, title: String, body: String) {
    NeoCard(Modifier.padding(bottom = 12.dp)) {
        Text("Langkah $number — $title", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = Muted)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashLogger.read(context)) }
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tentang", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("← Kembali", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
        }
        Spacer(Modifier.height(14.dp))

        NeoCard {
            Text("MikroKu", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Panel admin MikroTik RouterOS v6 dari HP: monitoring dulu, fitur admin penuh menyusul bertahap. " +
                    "Tanpa iklan, tanpa analytics, tanpa server tengah — HP tersambung langsung ke router kamu dan kredensial tersimpan terenkripsi di HP.",
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        NeoCard {
            Text("Log crash terakhir", fontWeight = FontWeight.Black, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            val log = crashLog
            if (log == null) {
                Text("Tidak ada crash tercatat. Semoga tetap begitu.", color = Muted, fontSize = 13.sp)
            } else {
                Text(log.take(1200), fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoButton(if (copied) "Tersalin ✓" else "Salin Log", Modifier.weight(1f)) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MikroKu crash log", log))
                        copied = true
                    }
                    NeoButton("Hapus Log", Modifier.weight(1f), fill = androidx.compose.ui.graphics.Color.White) {
                        CrashLogger.clear(context)
                        crashLog = null
                        copied = false
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
