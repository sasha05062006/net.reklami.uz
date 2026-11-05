package uz.reklami.zevs

import android.content.Context
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/** Single full-device WireGuard tunnel. The imported DNS settings are never overwritten. */
object WireGuardController {
    private const val TUNNEL_NAME = "zevs"
    private const val PREFS = "zevs_runtime"
    private const val AUTO_CONNECT = "auto_connect"
    @Volatile private var backend: GoBackend? = null
    @Volatile private var activeTunnel: Tunnel? = null

    private fun tunnel(context: Context): Tunnel =
        activeTunnel ?: object : Tunnel {
            override fun getName(): String = TUNNEL_NAME
            override fun onStateChange(newState: Tunnel.State) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean("connected", newState == Tunnel.State.UP).apply()
            }
        }.also { activeTunnel = it }

    private fun backend(context: Context): GoBackend =
        backend ?: GoBackend(context.applicationContext).also { backend = it }

    fun validate(profile: String) {
        require(profile.length in 40..65536) { "Некорректный размер WireGuard-профиля" }
        val parsed = Config.parse(ByteArrayInputStream(profile.toByteArray(Charsets.UTF_8)))
        require(parsed.peers.isNotEmpty()) { "В профиле нет секции [Peer]" }
        require(profile.contains(Regex("(?im)^\\s*AllowedIPs\\s*=.*(?:0\\.0\\.0\\.0/0|::/0)"))) {
            "Это не полный VPN-профиль: AllowedIPs должен содержать 0.0.0.0/0 или ::/0"
        }
    }

    private fun fullDeviceConfig(profile: String, ownPackage: String): Config {
        val clean = profile.replace(
            Regex("(?im)^\\s*(IncludedApplications|ExcludedApplications)\\s*=.*(?:\\r?\\n|$)"),
            ""
        )
        val routed = clean.replaceFirst(
            Regex("(?im)^\\s*\\[Interface\\]\\s*$"),
            "\$0\nExcludedApplications = $ownPackage"
        )
        return Config.parse(ByteArrayInputStream(routed.toByteArray(Charsets.UTF_8)))
    }

    @Synchronized
    @Throws(Exception::class)
    fun connect(context: Context, profile: String) {
        validate(profile)
        check(VpnService.prepare(context) == null) { "Сначала разреши Zevs создать VPN-подключение" }
        backend(context).setState(tunnel(context), Tunnel.State.UP, fullDeviceConfig(profile, context.packageName))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("connected", true).putBoolean(AUTO_CONNECT, true).apply()
    }

    @Synchronized
    @Throws(Exception::class)
    fun disconnect(context: Context) {
        val engine = backend ?: return
        val active = activeTunnel ?: return
        engine.setState(active, Tunnel.State.DOWN, null)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("connected", false).putBoolean(AUTO_CONNECT, false).apply()
    }

    fun setAutoConnect(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_CONNECT, enabled).apply()
    }

    fun autoConnectEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_CONNECT, false)

    fun connectSaved(context: Context, force: Boolean = false): Boolean {
        if (!force && !autoConnectEnabled(context)) return false
        val profile = ProfileVault.load(context) ?: return false
        if (VpnService.prepare(context) != null) return false
        connect(context, profile)
        return true
    }

    fun isConnected(context: Context): Boolean = runCatching {
        val engine = backend(context)
        engine.getState(tunnel(context)) == Tunnel.State.UP
    }.getOrDefault(false)

    fun statistics(context: Context): Pair<Long, Long> = runCatching {
        val engine = backend(context)
        val stats: Statistics = engine.getStatistics(tunnel(context))
        stats.totalRx() to stats.totalTx()
    }.getOrDefault(0L to 0L)

    fun backendVersion(context: Context): String = runCatching { backend(context).version }.getOrDefault("WireGuard")
}
