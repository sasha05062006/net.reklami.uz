package uz.reklami.zevs

import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Void = Color(0xFF070914)
private val Surface = Color(0xFF11182A)
private val Surface2 = Color(0xFF172239)
private val Neon = Color(0xFF8B72FF)
private val Aqua = Color(0xFF53E6D2)
private val Soft = Color(0xFFA7B5D0)
private val White = Color(0xFFF5F7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZevsApp() }
    }
}

@Composable
private fun ZevsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("zevs_runtime", android.content.Context.MODE_PRIVATE) }
    var connected by remember { mutableStateOf(WireGuardController.isConnected(context)) }
    var busy by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf(if (ProfileVault.load(context) != null) "WireGuard профиль сохранён" else "Профиль не добавлен") }
    var profileReady by remember { mutableStateOf(ProfileVault.load(context) != null) }
    var autoStart by remember { mutableStateOf(WireGuardController.autoConnectEnabled(context)) }
    var rx by remember { mutableLongStateOf(0L) }
    var tx by remember { mutableLongStateOf(0L) }
    var pendingConnect by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && pendingConnect) {
            pendingConnect = false
            val profile = ProfileVault.load(context)
            if (profile != null) scope.launch { connectNow(context, profile, { busy = it }, { connected = it }) }
        } else {
            pendingConnect = false
            Toast.makeText(context, "Разрешение VPN не предоставлено", Toast.LENGTH_SHORT).show()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val config = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось прочитать файл")
                WireGuardController.validate(config)
                ProfileVault.save(context, config)
                profileName = uri.lastPathSegment?.substringAfterLast('/') ?: "WireGuard профиль"
                profileReady = true
                Toast.makeText(context, "Профиль безопасно сохранён на устройстве", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "Не удалось импортировать профиль", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleVpn() {
        if (busy) return
        if (connected) {
            busy = true
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { WireGuardController.disconnect(context) } }
                busy = false
                connected = result.isFailure.not()
                if (result.isSuccess) Toast.makeText(context, "VPN отключён", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, result.exceptionOrNull()?.message ?: "Ошибка отключения", Toast.LENGTH_LONG).show()
            }
            return
        }
        val profile = ProfileVault.load(context)
        if (profile == null) {
            Toast.makeText(context, "Сначала импортируй WireGuard .conf", Toast.LENGTH_LONG).show()
            return
        }
        val permission = VpnService.prepare(context)
        if (permission != null) {
            pendingConnect = true
            consent.launch(permission)
        } else {
            scope.launch { connectNow(context, profile, { busy = it }, { connected = it }) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected = WireGuardController.isConnected(context)
            val stats = withContext(Dispatchers.IO) { WireGuardController.statistics(context) }
            rx = stats.first
            tx = stats.second
            autoStart = WireGuardController.autoConnectEnabled(context)
            delay(1200)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Void, surface = Surface, primary = Neon,
            secondary = Aqua, onBackground = White, onSurface = White
        )
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10172B), Void, Color(0xFF090B16))))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { TopBar() }
                item { MainTunnelCard(connected, busy, ::toggleVpn) }
                item { ConnectionInfoCard(connected, profileReady, profileName) }
                item { TrafficStatsCard(rx, tx, connected) }
                item {
                    SettingsCard(
                        autoStart = autoStart,
                        onAutoStart = {
                            autoStart = it
                            WireGuardController.setAutoConnect(context, it)
                        },
                        onSystemSettings = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { Toast.makeText(context, "Открой настройки Android → VPN", Toast.LENGTH_LONG).show() }
                        }
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (profileReady) "Заменить .conf" else "Добавить .conf")
                        }
                        Button(
                            onClick = ::toggleVpn,
                            enabled = profileReady && !busy,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (connected) Color(0xFFB64E72) else Neon)
                        ) {
                            Icon(if (connected) Icons.Rounded.PowerSettingsNew else Icons.Rounded.FlashOn, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (busy) "Подожди…" else if (connected) "Отключить" else "Подключить")
                        }
                    }
                }
                item {
                    Text(
                        "WIREGUARD CORE  •  FULL DEVICE VPN  •  DNS ИЗ ТВОЕГО ПРОФИЛЯ",
                        color = Soft.copy(alpha = .7f), fontSize = 9.sp, letterSpacing = 1.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private suspend fun connectNow(
    context: android.content.Context,
    profile: String,
    setBusy: (Boolean) -> Unit,
    setConnected: (Boolean) -> Unit
) {
    setBusy(true)
    val result = runCatching { withContext(Dispatchers.IO) { WireGuardController.connect(context, profile) } }
    setBusy(false)
    setConnected(result.isSuccess)
    if (result.isFailure) {
        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Не удалось подключиться", Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(context, "WireGuard подключён · весь трафик идёт через профиль", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun TopBar() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Neon, Color(0xFF304D83))))
                .border(1.dp, Aqua.copy(alpha = .7f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Shield, null, tint = White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ZEVS", fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("PRIVATE NETWORK", color = Soft, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Icon(Icons.Rounded.VerifiedUser, null, tint = Aqua, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MainTunnelCard(connected: Boolean, busy: Boolean, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "tunnel")
    val pulse by transition.animateFloat(
        initialValue = .78f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A2542), Color(0xFF111629), Color(0xFF11101F))))
            .border(1.dp, Neon.copy(alpha = .38f), RoundedCornerShape(30.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ЗАЩИЩЁННОЕ СОЕДИНЕНИЕ", color = Aqua, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Text(if (connected) "Туннель активен" else "Туннель выключен", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.clip(CircleShape).background(if (connected) Aqua.copy(alpha = .13f) else Soft.copy(alpha = .12f)).padding(horizontal = 11.dp, vertical = 7.dp)) {
                Text(if (connected) "ONLINE" else "OFFLINE", color = if (connected) Aqua else Soft, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(218.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val base = size.minDimension * .31f
                drawCircle(Brush.radialGradient(listOf(Neon.copy(alpha = if (connected) .30f else .12f), Color.Transparent), center, base * 2.1f), radius = base * 2.1f, center = center)
                drawCircle(Neon.copy(alpha = .18f), base * 1.58f, center, style = Stroke(1.2.dp.toPx()))
                drawCircle(Aqua.copy(alpha = .25f), base * 1.34f, center, style = Stroke(1.dp.toPx()))
                drawCircle(Brush.sweepGradient(listOf(Neon, Aqua, Color.Transparent, Neon)), base * pulse, center, style = Stroke(3.dp.toPx()))
                drawCircle(Neon.copy(alpha = .13f), base * .92f, center)
            }
            Box(
                Modifier.size(126.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF354A78), Color(0xFF1B2140), Color(0xFF12172B))))
                    .border(2.dp, Brush.sweepGradient(listOf(Aqua, Neon, Color(0xFF425B9C), Aqua)), CircleShape)
                    .clickable(enabled = !busy, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, tint = if (connected) Aqua else White, modifier = Modifier.size(43.dp))
                    Text(if (busy) "WAIT" else if (connected) "STOP VPN" else "START VPN", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        Text(
            if (connected) "Трафик устройства направляется через WireGuard" else "Импортируй конфигурацию и запусти защищённый туннель",
            color = Soft, fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectionInfoCard(connected: Boolean, profileReady: Boolean, profileName: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.VpnKey, null, tint = Neon, modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("WireGuard профиль", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (profileReady) profileName else "Добавь файл .conf от VPN-провайдера", color = Soft, fontSize = 10.sp)
        }
        Icon(if (connected) Icons.Rounded.CheckCircle else if (profileReady) Icons.Rounded.CheckCircleOutline else Icons.Rounded.AddCircleOutline,
            null, tint = if (connected) Aqua else Soft)
    }
}

@Composable
private fun TrafficStatsCard(rx: Long, tx: Long, connected: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.QueryStats, null, tint = Aqua)
            Spacer(Modifier.width(8.dp))
            Text("ТРАФИК ТУННЕЛЯ", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text(if (connected) "LIVE" else "PAUSED", color = if (connected) Aqua else Soft, fontSize = 9.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrafficValue("↓", "Получено", formatBytes(rx), Modifier.weight(1f), Aqua)
            TrafficValue("↑", "Отправлено", formatBytes(tx), Modifier.weight(1f), Neon)
        }
    }
}

@Composable
private fun TrafficValue(arrow: String, label: String, value: String, modifier: Modifier, color: Color) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Surface2).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(arrow, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text(label, color = Soft, fontSize = 10.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsCard(
    autoStart: Boolean,
    onAutoStart: (Boolean) -> Unit,
    onSystemSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Text("АВТОМАТИЗАЦИЯ", color = Aqua, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.RestartAlt, null, tint = Neon, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Подключать после перезагрузки", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("Zevs восстановит сохранённый туннель", color = Soft, fontSize = 10.sp)
            }
            Switch(checked = autoStart, onCheckedChange = onAutoStart)
        }
        HorizontalDivider(color = Color.White.copy(alpha = .07f), modifier = Modifier.padding(vertical = 10.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSystemSettings).padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SettingsSuggest, null, tint = Aqua, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Системные настройки VPN", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text("Always-on VPN и блокировка без VPN", color = Soft, fontSize = 10.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Soft)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}
