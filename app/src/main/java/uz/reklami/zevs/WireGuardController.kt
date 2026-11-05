package uz.reklami.zevs

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * Owns the single Android VPN slot through the official WireGuard userspace backend.
 * Imported private keys remain in app-private preferences and are never logged.
 */
object WireGuardController {
    private const val TUNNEL_NAME = "zevs"
    private var backend: GoBackend? = null
    private var activeTunnel: Tunnel? = null

    @Synchronized
    fun validate(profile: String) {
        require(profile.length in 40..65536) { "Некорректный размер WireGuard-профиля" }
        val parsed = Config.parse(ByteArrayInputStream(profile.toByteArray(Charsets.UTF_8)))
        require(parsed.peers.isNotEmpty()) { "В профиле нет секции [Peer]" }
    }

    @Synchronized
    @Throws(Exception::class)
    fun connect(context: Context, profile: String, adBlocking: Boolean, includedApps: Set<String> = emptySet()) {
        val dnsConfigured = if (adBlocking) withAdGuardDns(profile) else profile
        val configured = withAppRouting(dnsConfigured, includedApps, context.packageName)
        val parsed = Config.parse(ByteArrayInputStream(configured.toByteArray(Charsets.UTF_8)))
        require(parsed.peers.isNotEmpty()) { "В профиле нет WireGuard-сервера" }
        val engine = backend ?: GoBackend(context.applicationContext).also { backend = it }
        val tunnel = activeTunnel ?: object : Tunnel {
            override fun getName(): String = TUNNEL_NAME
            override fun onStateChange(newState: Tunnel.State) = Unit
        }.also { activeTunnel = it }
        engine.setState(tunnel, Tunnel.State.UP, parsed)
    }

    @Synchronized
    @Throws(Exception::class)
    fun disconnect() {
        val engine = backend ?: return
        val tunnel = activeTunnel ?: return
        engine.setState(tunnel, Tunnel.State.DOWN, null)
    }


    private fun withAppRouting(profile: String, includedApps: Set<String>, ownPackage: String): String {
        val withoutOldRules = profile.replace(
            Regex("(?im)^\\s*(IncludedApplications|ExcludedApplications)\\s*=.*(?:\\r?\\n|$)"),
            ""
        )
        val rule = if (includedApps.isNotEmpty()) {
            "IncludedApplications = ${includedApps.sorted().joinToString(",")}"
        } else {
            "ExcludedApplications = $ownPackage"
        }
        return withoutOldRules.replaceFirst(Regex("(?im)^\\s*\\[Interface\\]\\s*$")) {
            "${it.value}\n$rule"
        }
    }

    private fun withAdGuardDns(profile: String): String {
        val dns = "DNS = 94.140.14.14, 94.140.15.15"
        val pattern = Regex("(?im)^\\s*DNS\\s*=.*$")
        return if (pattern.containsMatchIn(profile)) {
            pattern.replace(profile, dns)
        } else {
            profile.replaceFirst(Regex("(?im)^\\s*\\[Interface\\]\\s*$")) { "${it.value}\n$dns" }
        }
    }
}
