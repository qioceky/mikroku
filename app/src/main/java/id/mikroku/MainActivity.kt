package id.mikroku

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.mikroku.ui.AppLockAuth
import id.mikroku.ui.MikroKuApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MikroKuApp() }
    }

    @Deprecated("Hasil kunci perangkat memang lewat jalur klasik ini")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppLockAuth.REQUEST_CODE) {
            AppLockAuth.deliver(resultCode == Activity.RESULT_OK)
        }
    }
}
