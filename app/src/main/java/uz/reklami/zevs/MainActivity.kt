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
    val vpnConsent = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            ContextCompat.startForegroundService(context, Intent(context, DnsFilterVpnService::class.java))
            protection = true
        }
    }
    fun setProtection(enabled: Boolean) {
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
    var vpn by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var importedProfile by remember { mutableStateOf<String?>(null) }
    var profileValid by remember { mutableStateOf(false) }
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось прочитать файл")
            }.onSuccess { config ->
                val valid = config.contains("[Interface]") &&
                    config.contains("[Peer]") &&
                    config.contains(Regex("(?m)^\\s*PrivateKey\\s*=")) &&
                    config.contains(Regex("(?m)^\\s*PublicKey\\s*=")) &&
                    config.contains(Regex("(?m)^\\s*Endpoint\\s*="))
                profileValid = valid
                importedProfile = if (valid) (uri.lastPathSegment?.substringAfterLast('/') ?: "WireGuard-профиль") else null
                Toast.makeText(context, if (valid) "Конфигурация проверена. Туннель пока не активирован." else "Файл не похож на полный WireGuard-профиль.", Toast.LENGTH_LONG).show()
            }.onFailure {
                profileValid = false
                importedProfile = null
                Toast.makeText(context, "Не удалось прочитать конфигурацию.", Toast.LENGTH_LONG).show()
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
                item { HeroPower(protection) { setProtection(it) } }
                item { MetricsCard() }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureToggle("Блокировка рекламы", "DNS-фильтр", protection, { setProtection(it) }, Icons.Rounded.Shield, Modifier.weight(1f))
                        FeatureToggle("VPN Польша", "WireGuard · позже", vpn, { vpn = it }, Icons.Rounded.FlashOn, Modifier.weight(1f))
                    }
                }
                item { QuickCard(Icons.Rounded.FilterAlt, "Фильтры", "Списки рекламы и трекеров", "Настроить") { tab = 1 } }
                item { QuickCard(Icons.Rounded.QueryStats, "Статистика", "Запросы и приложения", "Открыть") { tab = 2 } }
                item { WireGuardProfileCard(importedProfile, profileValid) { profilePicker.launch(arrayOf("*/*")) } }
                item { TrafficCard() }
                item {
                    Text(
                        "ПРЕДВАРИТЕЛЬНАЯ ВЕРСИЯ · СЕТЕВОЙ ДВИЖОК ЕЩЁ НЕ ПОДКЛЮЧЁН",
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
private fun WireGuardProfileCard(profileName: String?, valid: Boolean, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF251344), Panel)))
        .border(1.dp, Violet.copy(alpha=.4f), RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🇵🇱", fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("VPN · Польша", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (valid) "Профиль выбран · готов к подключению после интеграции" else "WireGuard · профиль не добавлен", color = Muted, fontSize = 10.sp)
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
        Text("Сейчас выполняется проверка формата файла. Реальное VPN-подключение будет включено после подключения сетевого движка.", color = Muted, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
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
