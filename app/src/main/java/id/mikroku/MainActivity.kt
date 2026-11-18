package id.mikroku

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import id.mikroku.ui.MikroKuApp

/**
 * FragmentActivity (bukan ComponentActivity polos) agar BiometricPrompt
 * untuk Kunci Aplikasi bisa dipakai dari Compose.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MikroKuApp() }
    }
}
