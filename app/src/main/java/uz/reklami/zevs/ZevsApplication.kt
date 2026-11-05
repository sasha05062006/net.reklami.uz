package uz.reklami.zevs

import android.app.Application
import com.wireguard.android.backend.GoBackend

class ZevsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Migrate profiles from the previous Zevs build's plain preferences into Android Keystore encryption.
        val legacy = getSharedPreferences("zevs", MODE_PRIVATE)
        val oldProfile = legacy.getString("wg_profile", null)
        if (oldProfile != null) {
            if (ProfileVault.load(this) == null) {
                runCatching {
                    WireGuardController.validate(oldProfile)
                    ProfileVault.save(this, oldProfile)
                }
            }
            legacy.edit().clear().apply()
        }

        GoBackend.setAlwaysOnCallback {
            Thread {
                runCatching { WireGuardController.connectSaved(applicationContext, force = true) }
            }.start()
        }
    }
}
