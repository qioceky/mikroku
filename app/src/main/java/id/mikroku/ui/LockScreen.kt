package id.mikroku.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mikroku.data.SettingsStore

/**
 * Jembatan hasil konfirmasi kunci perangkat: MainActivity meneruskan
 * onActivityResult klasik ke callback yang didaftarkan layar kunci.
 * Sengaja TANPA ActivityResultLauncher/FragmentActivity — kombinasi itu
 * yang membuat pemilih file crash di v1.5.0 (requestCode > 16 bit).
 */
object AppLockAuth {
    const val REQUEST_CODE = 9021

    private var pending: ((Boolean) -> Unit)? = null

    fun await(callback: (Boolean) -> Unit) {
        pending = callback
    }

    fun deliver(success: Boolean) {
        val cb = pending
        pending = null
        cb?.invoke(success)
    }
}

/**
 * Layar Kunci Aplikasi: tampil sebelum daftar router bila user mengaktifkan
 * kunci. Buka dengan PIN master MikroKu (hash di SettingsStore) atau kunci
 * perangkat Android (sidik jari/PIN/pola HP lewat KeyguardManager).
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Goyangan ikon gembok saat PIN salah.
    val shake = remember { Animatable(0f) }
    var shakeKey by remember { mutableStateOf(0) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            shake.snapTo(0f)
            shake.animateTo(-11f, tween(45))
            shake.animateTo(10f, tween(60))
            shake.animateTo(-6f, tween(55))
            shake.animateTo(0f, tween(50))
        }
    }

    val keyguard = remember {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }
    val deviceSecure = remember {
        try {
            keyguard?.isDeviceSecure == true
        } catch (e: Exception) {
            false
        }
    }

    fun tryDeviceUnlock() {
        val activity = context as? Activity ?: return
        val km = keyguard ?: return
        val intent = km.createConfirmDeviceCredentialIntent(
            "Buka MikroKu",
            "Konfirmasi kunci perangkat untuk membuka MikroKu"
        )
        if (intent == null) {
            error = "Kunci perangkat tidak tersedia di HP ini."
            return
        }
        AppLockAuth.await { success ->
            if (success) {
                onUnlocked()
            } else {
                error = "Konfirmasi kunci perangkat dibatalkan."
            }
        }
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, AppLockAuth.REQUEST_CODE)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        FaIcon(
            Fa.LOCK, size = 34.sp,
            modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }
        )
        Spacer(Modifier.height(12.dp))
        Text("MikroKu Terkunci", fontWeight = FontWeight.Black, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Masukkan PIN master untuk membuka daftar router. " +
                "Kunci ini lapisan pengaman di HP; kredensial router tetap " +
                "terenkripsi seperti biasa.",
            color = Muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            pin, { pin = it },
            Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("PIN Master") },
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Ink.copy(alpha = 0.4f),
                focusedContainerColor = CardFill,
                unfocusedContainerColor = CardFill,
                cursorColor = Ink
            )
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MikroRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        NeoButton("Buka dengan PIN") {
            if (SettingsStore.verifyAppLockPin(context, pin)) {
                onUnlocked()
            } else {
                error = "PIN salah — coba lagi."
                pin = ""
                shakeKey++
            }
        }
        if (deviceSecure) {
            Spacer(Modifier.height(10.dp))
            NeoButton("Pakai Kunci Perangkat (Sidik Jari/PIN HP)", fill = CardFill) {
                tryDeviceUnlock()
            }
        }
    }
}
