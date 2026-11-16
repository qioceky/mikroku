package id.mikroku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.api.MikrotikClient
import id.mikroku.data.SessionManager
import kotlinx.coroutines.launch

/** Satu kolom pada form tambah/edit. */
data class CrudField(
    /** Nama atribut RouterOS, mis. "address", "max-limit". */
    val key: String,
    val label: String,
    val initial: String = "",
    val hint: String = "",
    val required: Boolean = false,
    /** Pilihan cepat (chips) di bawah kolom; nilai tetap bisa diketik bebas. */
    val options: List<String> = emptyList(),
    /** Saat edit: nilai kosong berarti JANGAN diubah (mis. password). */
    val skipBlankOnEdit: Boolean = false
)

/** Spesifikasi satu form tambah/edit beserta cara mengirimnya ke router. */
class CrudFormSpec(
    val title: String,
    val isEdit: Boolean,
    val fields: List<CrudField>,
    /** Kirim nilai form (sudah disaring) ke router; lempar exception bila gagal. */
    val submit: suspend (MikrotikClient, Map<String, String>) -> Unit
)

/**
 * Layar form generik untuk tambah/edit entri (v0.4).
 * Pengiriman memakai runNoRetry: aksi tulis tidak diulang otomatis.
 */
@Composable
fun CrudFormScreen(
    spec: CrudFormSpec,
    manager: SessionManager,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val values = remember(spec) {
        mutableStateMapOf<String, String>().apply {
            spec.fields.forEach { put(it.key, it.initial) }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save() {
        if (saving) return
        val missing = spec.fields.firstOrNull { it.required && (values[it.key] ?: "").isBlank() }
        if (missing != null) {
            error = "Kolom \"${missing.label}\" wajib diisi."
            return
        }
        // Saat edit, kolom skipBlankOnEdit yang kosong dibuang dari payload
        // (artinya: jangan diubah). Kolom lain tetap dikirim walau kosong
        // (artinya: kosongkan di router).
        val effective: Map<String, String> = if (spec.isEdit) {
            values.filter { (k, v) ->
                v.isNotBlank() ||
                    spec.fields.firstOrNull { it.key == k }?.skipBlankOnEdit != true
            }
        } else {
            values.toMap()
        }
        scope.launch {
            saving = true
            error = null
            try {
                manager.runNoRetry { spec.submit(it, effective) }
                onDone()
            } catch (e: Exception) {
                error = friendlyError(e)
            }
            saving = false
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← Batal", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onCancel() })
            Text(spec.title, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(spec.fields, key = { it.key }) { field ->
                Column {
                    OutlinedTextField(
                        values[field.key] ?: "",
                        { values[field.key] = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(field.label + if (field.required) " *" else "") },
                        supportingText = { if (field.hint.isNotBlank()) Text(field.hint) }
                    )
                    if (field.options.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (opt in field.options) {
                                val active = (values[field.key] ?: "") == opt
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MikroYellow else Color.White)
                                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
                                        .clickable { values[field.key] = opt }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(opt, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                error?.let {
                    Text(it, color = MikroRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoButton("Batal", Modifier.weight(1f), fill = Color.White) { onCancel() }
                    NeoButton(
                        if (saving) "Menyimpan…" else "Simpan",
                        Modifier.weight(1f),
                        enabled = !saving
                    ) { save() }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
