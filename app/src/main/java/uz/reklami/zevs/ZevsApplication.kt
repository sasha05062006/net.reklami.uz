package uz.reklami.zevs

import android.app.Application
import com.wireguard.android.backend.GoBackend

class ZevsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Migrate legacy plaintext profile only after a successful encrypted save.
        val legacy = getSharedPreferences("zevs", MODE_PRIVATE)
        val oldProfile = legacy.getString("wg_profile", null)
        if (oldProfile != null) {
            val alreadyEncrypted = ProfileVault.load(this) != null
            val migrated = alreadyEncrypted || runCatching {
                WireGuardController.validate(oldProfile)
                ProfileVault.save(this, oldProfile)
                true
            }.getOrDefault(false)
            if (migrated) legacy.edit().clear().apply()
        }

        GoBackend.setAlwaysOnCallback {
            Thread {
                runCatching { WireGuardController.connectSaved(applicationContext, force = true) }
            }.start()
        }
    }
}
