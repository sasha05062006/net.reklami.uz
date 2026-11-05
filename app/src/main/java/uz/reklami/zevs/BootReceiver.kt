package uz.reklami.zevs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!WireGuardController.autoConnectEnabled(context)) return
                val pending = goAsync()
                Thread {
                    try {
                        WireGuardController.connectSaved(context.applicationContext)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
