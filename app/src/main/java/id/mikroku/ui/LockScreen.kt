package id.mikroku.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import id.mikroku.data.SettingsStore

/**
 * Layar Kunci Aplikasi: tampil sebelum daftar router bila user mengaktifkan
 * kunci. Buka dengan PIN master (hash tersimpan di SettingsStore) atau
 * sidik jari (BiometricPrompt) bila perangkat mendukung.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val biometricAvailable = remember {
        try {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    fun tryBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        error = errString.toString()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka MikroKu")
            .setSubtitle("Pakai sidik jari untuk membuka kunci aplikasi")
            .setNegativeButtonText("Pakai PIN")
            .build()
        prompt.authenticate(info)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        FaIcon(Fa.LOCK, size = 34.sp)
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
            }
        }
        if (biometricAvailable) {
            Spacer(Modifier.height(10.dp))
            NeoButton("Pakai Sidik Jari", fill = CardFill) { tryBiometric() }
        }
    }
}
