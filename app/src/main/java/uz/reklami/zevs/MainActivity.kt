package uz.reklami.zevs

import android.os.Bundle
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Night = Color(0xFF080611)
private val Panel = Color(0xFF171126)
private val Violet = Color(0xFF9B42FF)
private val Pink = Color(0xFFE04CFF)
private val Muted = Color(0xFFB7A9D5)
private val Cyan = Color(0xFF4DE7E1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZevsApp() }
    }
}

@Composable
fun ZevsApp() {
    var protection by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var wgConnected by remember { mutableStateOf(false) }
    var wgBusy by remember { mutableStateOf(false) }
    val vpnConsent = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            ContextCompat.startForegroundService(context, Intent(context, DnsFilterVpnService::class.java))
            protection = true
        }
    }
    fun setProtection(enabled: Boolean) {
        if (enabled && wgConnected) {
            Toast.makeText(context, "При активном WireGuard блокировка DNS уже работает через AdGuard DNS.", Toast.LENGTH_LONG).show()
            return
        }
        if (enabled) {
            val consent = VpnService.prepare(context)
            if (consent != null) vpnConsent.launch(consent)
            else {
                ContextCompat.startForegroundService(context, Intent(context, DnsFilterVpnService::class.java))
                protection = true
            }
        } else {
            context.startService(Intent(context, DnsFilterVpnService::class.java).setAction(DnsFilterVpnService.ACTION_STOP))
            protection = false
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    val preferences = remember { context.getSharedPreferences("zevs", android.content.Context.MODE_PRIVATE) }
    val savedProfile = remember { preferences.getString("wg_profile", null) }
    var importedProfile by remember { mutableStateOf(if (savedProfile != null) "WireGuard-профиль" else null) }
    var profileValid by remember { mutableStateOf(savedProfile?.let { runCatching { WireGuardController.validate(it); true }.getOrDefault(false) } ?: false) }
    var selectedApps by remember { mutableStateOf(preferences.getStringSet("included_apps", emptySet())?.toSet() ?: emptySet()) }
    val installedApps = remember {
        context.packageManager.getInstalledApplications(0)
            .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .map { it.packageName to it.loadLabel(context.packageManager).toString() }
            .sortedBy { it.second.lowercase() }
    }
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось прочитать файл")
            }.onSuccess { config ->
                WireGuardController.validate(config)
                context.getSharedPreferences("zevs", android.content.Context.MODE_PRIVATE)
                    .edit().putString("wg_profile", config).apply()
                profileValid = true
                importedProfile = uri.lastPathSegment?.substringAfterLast('/') ?: "WireGuard-профиль"
                Toast.makeText(context, "WireGuard-профиль импортирован и сохранён.", Toast.LENGTH_LONG).show()
            }.onFailure {
                profileValid = false
                importedProfile = null
                Toast.makeText(context, "Не удалось прочитать конфигурацию.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun setWireGuard(enabled: Boolean) {
        if (wgBusy) return
        if (enabled) {
            val profile = context.getSharedPreferences("zevs", android.content.Context.MODE_PRIVATE)
                .getString("wg_profile", null)
            if (profile.isNullOrBlank()) {
                Toast.makeText(context, "Сначала импортируй WireGuard .conf", Toast.LENGTH_LONG).show()
                return
            }
            if (protection) {
                context.startService(Intent(context, DnsFilterVpnService::class.java).setAction(DnsFilterVpnService.ACTION_STOP))
                protection = false
            }
            wgBusy = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { WireGuardController.connect(context, profile, true, selectedApps) }
                }
                wgBusy = false
                result.onSuccess {
                    wgConnected = true
                    Toast.makeText(context, "WireGuard подключён. DNS-фильтрация AdGuard включена.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    wgConnected = false
                    Toast.makeText(context, "Ошибка VPN: ${it.message ?: "не удалось подключиться"}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            wgBusy = true
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { WireGuardController.disconnect() } }
                wgBusy = false
                result.onSuccess {
                    wgConnected = false
                    Toast.makeText(context, "WireGuard отключён.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Ошибка отключения: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Violet,
        secondary = Cyan, onBackground = Color.White, onSurface = Color.White
    )) {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF130B27), Night, Color(0xFF090512)))
        )) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Header() }
                item { HeroPower(protection || wgConnected) { if (wgConnected) setWireGuard(false) else setProtection(it) } }
                item { MetricsCard() }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureToggle("Блокировка рекламы", if (wgConnected) "AdGuard DNS через VPN" else "DNS-фильтр без VPN", protection || wgConnected, { setProtection(it) }, Icons.Rounded.Shield, Modifier.weight(1f))
                        FeatureToggle(if (wgBusy) "Подключение…" else "VPN Польша", "WireGuard", wgConnected, { setWireGuard(it) }, Icons.Rounded.FlashOn, Modifier.weight(1f))
                    }
                }
                item { QuickCard(Icons.Rounded.FilterAlt, "Фильтры", "Списки рекламы и трекеров", "Настроить") { tab = 1 } }
                item { QuickCard(Icons.Rounded.QueryStats, "Статистика", "Запросы и приложения", "Открыть") { tab = 2 } }
                item { AppRoutingCard(installedApps, selectedApps) { packageName ->
                    selectedApps = if (selectedApps.contains(packageName)) selectedApps - packageName else selectedApps + packageName
                    preferences.edit().putStringSet("included_apps", selectedApps).apply()
                } }
                item { WireGuardProfileCard(importedProfile, profileValid, wgConnected, wgBusy, { profilePicker.launch(arrayOf("*/*")) }) { setWireGuard(!wgConnected) } }
                item { TrafficCard() }
                item {
                    Text(
                        "WIREGUARD + DNS FILTER · ТЕСТОВАЯ СБОРКА",
                        color = Muted, fontSize = 10.sp, letterSpacing = 1.1.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
            BottomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Violet, Color(0xFF34116B))))
                .border(1.dp, Pink.copy(alpha = .8f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Text("Z", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Zevs", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("Блокирует рекламу. Дарит свободу.", color = Muted, fontSize = 11.sp)
        }
        IconButton(onClick = {}) { Icon(Icons.Rounded.NotificationsNone, null, tint = Muted) }
        IconButton(onClick = {}) { Icon(Icons.Rounded.Settings, null, tint = Muted) }
    }
}

@Composable
private fun HeroPower(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF28134C), Color(0xFF100B20), Color(0xFF080611))))
            .border(1.dp, Violet.copy(alpha = .55f), RoundedCornerShape(28.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Violet.copy(alpha = .24f), Color.Transparent)), radius = size.minDimension * .62f, center = Offset(size.width * .76f, size.height * .48f))
            drawCircle(Violet.copy(alpha = .18f), radius = size.minDimension * .39f, center = Offset(size.width * .76f, size.height * .48f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 22.dp).width(145.dp)) {
            Text("ЗАЩИТА СЕТИ", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Твоя сеть.\nТвои правила.", fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Управляй рекламой и приватностью", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(148.dp)
            .clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF442078), Color(0xFF120B22))))
            .border(3.dp, Brush.sweepGradient(listOf(Pink, Violet, Color(0xFF4B1A83), Pink)), CircleShape)
            .clickable { onToggle(!enabled) }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text(if (enabled) "ВКЛЮЧЕНО" else "ЗАПУСТИТЬ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Демо-режим", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MetricsCard() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, Violet.copy(alpha=.25f), RoundedCornerShape(22.dp)).padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Metric("0", "Заблокировано", Icons.Rounded.Shield)
        DividerLine()
        Metric("0", "Приложений", Icons.Rounded.Apps)
        DividerLine()
        Metric("0 МБ", "Сэкономлено", Icons.Rounded.Speed)
    }
}
@Composable private fun DividerLine() { Box(Modifier.width(1.dp).height(54.dp).background(Violet.copy(alpha=.35f))) }
@Composable private fun Metric(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun FeatureToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Violet.copy(alpha=.3f), RoundedCornerShape(20.dp)).padding(13.dp)) {
        Icon(icon, null, tint = if (checked) Cyan else Violet, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
        Text(subtitle, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Switch(checked, onChecked, modifier = Modifier.height(30.dp), colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Violet))
    }
}
@Composable
private fun QuickCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Violet.copy(alpha=.25f), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) }
        Text(action, color = Cyan, fontSize = 11.sp)
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun AppRoutingCard(
    apps: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    var showApps by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Panel).border(1.dp, Violet.copy(alpha = .3f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Apps, null, tint = Cyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Маршрутизация приложений", fontWeight = FontWeight.Bold)
                Text(
                    if (selected.isEmpty()) "Все приложения через VPN, кроме Zevs"
                    else "Через VPN: ${selected.size} · остальные напрямую",
                    color = Muted, fontSize = 10.sp
                )
            }
            TextButton(onClick = { showApps = true }) { Text("Выбрать") }
        }
    }
    if (showApps) {
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("Приложения через VPN") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Если ничего не выбрано — VPN используют все приложения, кроме Zevs. Если выбрать приложения — только они пойдут через VPN, остальные останутся напрямую.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    apps.forEach { (packageName, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(packageName) }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selected.contains(packageName), onCheckedChange = { onToggle(packageName) })
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 13.sp)
                                Text(packageName, color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("Готово") } }
        )
    }
}

@Composable
private fun WireGuardProfileCard(profileName: String?, valid: Boolean, connected: Boolean, busy: Boolean, onImport: () -> Unit, onConnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF251344), Panel)))
        .border(1.dp, Violet.copy(alpha=.4f), RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🇵🇱", fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("VPN · Польша", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (connected) "Подключено" else if (valid) "Профиль сохранён · можно подключаться" else "WireGuard · профиль не добавлен", color = if (connected) Cyan else Muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (valid && profileName != null) {
            Text("Импортирован: $profileName", color = Cyan, fontSize = 11.sp)
        } else {
            Text("Добавь .conf-файл прямо с телефона. Компьютер не нужен. Подойдёт конфигурация WireGuard от VPN-провайдера.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
            Icon(Icons.Rounded.FileOpen, null)
            Spacer(Modifier.width(8.dp))
            Text(if (valid) "Заменить конфигурацию" else "Импортировать WireGuard")
        }
        if (valid) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onConnect, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (connected) Color(0xFF8A2747) else Color(0xFF247A72))) {
                Text(if (busy) "Подожди…" else if (connected) "Отключить VPN" else "Подключить VPN и AdBlock")
            }
        }
        Text("Используется официальный WireGuard userspace backend. При подключении DNS переключается на AdGuard DNS для блокировки рекламы и трекеров.", color = Muted, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable
private fun TrafficCard() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, Violet.copy(alpha=.25f), RoundedCornerShape(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.ShowChart, null, tint = Pink); Spacer(Modifier.width(8.dp)); Text("Сетевой трафик", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.fillMaxWidth().height(65.dp)) {
            val points = listOf(.82f,.55f,.68f,.32f,.5f,.2f,.47f,.36f,.58f)
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, y ->
                val p = Offset(size.width * i / (points.size - 1), size.height * y)
                if (i == 0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
            }
            drawPath(path, Brush.horizontalGradient(listOf(Violet, Pink)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
        Text("График появится после подключения сетевого движка", color = Muted, fontSize = 10.sp)
    }
}
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(Icons.Rounded.Home to "Главная", Icons.Rounded.Shield to "Защита", Icons.Rounded.Apps to "Приложения", Icons.Rounded.BarChart to "Аналитика")
    Row(modifier.fillMaxWidth().background(Color(0xF20D0818)).border(1.dp, Violet.copy(alpha=.3f)).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEachIndexed { index, pair ->
            Column(Modifier.weight(1f).clickable { onSelect(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(pair.first, null, tint = if (selected == index) Pink else Muted)
                Text(pair.second, color = if (selected == index) Color.White else Muted, fontSize = 10.sp)
            }
        }
    }
}
