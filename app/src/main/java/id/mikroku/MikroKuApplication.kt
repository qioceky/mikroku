package id.mikroku

import android.app.Application
import id.mikroku.data.CrashLogger

class MikroKuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
