package uz.reklami.zevs

import android.app.Application
import com.wireguard.android.backend.GoBackend

class ZevsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GoBackend.setAlwaysOnCallback {
            Thread {
                runCatching { WireGuardController.connectSaved(applicationContext) }
            }.start()
        }
    }
}
