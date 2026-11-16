package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.NetUiState
import ru.franh.modemmon.vm.UiState

@Composable
fun IfacesScreen(state: UiState, vm: ModemViewModel, onOpen: (String) -> Unit = {}) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.net.ifaces.isEmpty()) vm.loadNetwork()
    }
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.net.uplink == null) vm.checkUplink()
    }
    ScreenShell(connected = state.ubus.connected) {
        UplinkBanner(state, vm)
        IfaceCard(state.net, vm, onOpen)
    }
}

/**
 * Плашка о состоянии автопереключения.
 * Показывается, когда основной канал без интернета, резервный рабочий,
 * а переключение выключено.
 */
@Composable
private fun UplinkBanner(state: UiState, vm: ModemViewModel) {
    val up = state.net.uplink ?: return
    val probes = state.net.probes

    val primary = up.defaultDevice
    val primaryCode = primary?.let { probes[it] }
    val primaryDead = primaryCode != null && primaryCode != "204"
    val backupAlive = up.paths.any { it.device != primary && probes[it.device] == "204" }

    if (!up.failoverEnabled && primaryDead && backupAlive) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Warn)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Основной канал без интернета",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Warn
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Резервный канал работает, но автопереключение выключено — " +
                    "трафик продолжает идти в мёртвый канал. Ответ $primary на проверку: $primaryCode.",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.setFailover(true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.net.uplinkBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    if (state.net.uplinkBusy) "Включаю…" else "Включить переключение",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        return
    }

    if (up.failoverEnabled) {
        val steered = up.paths.filter { it.steered }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Good)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Автопереключение включено",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Good,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.setFailover(false) }, enabled = !state.net.uplinkBusy) {
                    Text("Выключить", fontSize = 11.sp, color = Muted)
                }
            }
            if (steered.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    steered.joinToString(", ") { "${it.device} отодвинут (${it.metric})" },
                    color = Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Все каналы в порядке, вмешательство не требуется",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ClientsScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.net.leases.isEmpty()) vm.loadNetwork()
    }
    ScreenShell(connected = state.ubus.connected) { LeaseCard(state.net, vm) }
}

@Composable
fun LogScreen(state: UiState, vm: ModemViewModel) {
    ScreenShell(connected = state.ubus.connected) { LogCard(state.net, vm) }
}

@Composable
fun PingScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.net.ifaces.isEmpty()) vm.loadNetwork()
    }
    ScreenShell(connected = state.ubus.connected) {
        SpeedTestCard(state, vm)
        PingCard(state.net, vm)
    }
}

@Composable
private fun IfaceCard(n: NetUiState, vm: ModemViewModel, onOpen: (String) -> Unit) {
    var pending by remember { mutableStateOf<Triple<String, Boolean, String>?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel("Интерфейсы · ${n.ifaces.size}")
            TextButton(onClick = { vm.loadNetwork() }, enabled = !n.loading) {
                Text(if (n.loading) "Обновляю…" else "Обновить", fontSize = 12.sp, color = Accent)
            }
        }

        n.error?.let {
            Text(it, color = Bad, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        n.ifaces.forEach { f ->
            val open = expanded == f.name
            CardBox {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (open) null else f.name },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (f.up) Good.copy(alpha = 0.14f) else SurfaceHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            f.name.take(2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (f.up) Good else Muted
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(8.dp))
                            Badge(if (f.up) "up" else "down", if (f.up) Good else Muted)
                        }
                        Text(
                            "${f.proto.ifBlank { "—" }} · ${f.ip ?: "—"}",
                            color = Muted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        if (open) "⌃" else "⌄",
                        fontSize = 15.sp,
                        color = Muted
                    )
                }

                if (open) {
                    Spacer(Modifier.height(12.dp))
                    InfoLine("Устройство", f.device.ifBlank { "—" })
                    InfoLine("Протокол", f.proto.ifBlank { "—" })
                    InfoLine("IP адрес", f.ip ?: "—")
                    InfoLine("Метрика", if (f.metric > 0) f.metric.toString() else "—")
                    InfoLine("Время работы", fmtUptime(f.uptime))

                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("↓ ${fmtBytes(f.rx)}", color = Good, fontSize = 13.sp)
                            Text("Получено", color = Muted, fontSize = 10.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("↑ ${fmtBytes(f.tx)}", color = Accent, fontSize = 13.sp)
                            Text("Передано", color = Muted, fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.renewIface(f.name) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Good.copy(alpha = 0.16f),
                                contentColor = Good
                            )
                        ) { Text("Перезапуск", fontSize = 12.sp) }

                        Button(
                            onClick = {
                                pending = if (f.up) {
                                    Triple(f.name, false, "Остановить ${f.name}? Если через него идёт трафик — связь прервётся.")
                                } else {
                                    Triple(f.name, true, "Запустить ${f.name}?")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (f.up) Warn.copy(alpha = 0.16f) else Accent.copy(alpha = 0.16f),
                                contentColor = if (f.up) Warn else Accent
                            )
                        ) { Text(if (f.up) "Остановить" else "Запустить", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onOpen(f.name) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent.copy(alpha = 0.16f),
                            contentColor = Accent
                        )
                    ) { Text("Изменить настройки", fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    pending?.let { (name, up, question) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Подтверждение") },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    vm.ifaceToggle(name, up)
                }) { Text("Выполнить", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Отмена", color = Muted) }
            }
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun fmtUptime(sec: Long): String {
    if (sec <= 0) return "—"
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    return when {
        d > 0 -> "${d}д ${h}ч ${m}м"
        h > 0 -> "${h}ч ${m}м"
        else -> "${m}м"
    }
}

private fun fmtBytes(b: Long): String {
    if (b <= 0) return "0"
    val units = listOf("Б", "КиБ", "МиБ", "ГиБ", "ТиБ")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "${b} ${units[0]}" else String.format("%.1f %s", v, units[i])
}

@Composable
private fun LeaseCard(n: NetUiState, vm: ModemViewModel) {
    CardBox {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel("Клиенты · ${n.leases.size}")
            TextButton(onClick = { vm.loadNetwork() }, enabled = !n.loading) {
                Text("Обновить", fontSize = 12.sp, color = Accent)
            }
        }

        if (n.leases.isEmpty() && !n.loading) {
            Text("Аренд нет", color = Muted, fontSize = 12.sp)
        }

        n.leases.forEach { l ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        l.hostname ?: "без имени",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        l.mac,
                        color = Muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    l.ip,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Accent
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LogCard(n: NetUiState, vm: ModemViewModel) {
    CardBox {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel("Журнал")
            TextButton(onClick = { vm.loadLog() }) {
                Text("Загрузить", fontSize = 12.sp, color = Accent)
            }
        }

        if (n.log.isEmpty()) {
            Text("Нажмите «Загрузить» — покажем последние 120 строк.", color = Muted, fontSize = 12.sp)
            return@CardBox
        }

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF0B0F14))
                .border(1.dp, Outline, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                n.log.forEach { line ->
                    Text(
                        line,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            line.contains("err", true) || line.contains("fail", true) -> Bad
                            else -> Muted
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PingCard(n: NetUiState, vm: ModemViewModel) {
    var target by remember { mutableStateOf("8.8.8.8") }
    var iface by remember { mutableStateOf<String?>(null) }

    CardBox {
        SectionLabel("Проверка связи")
        Text(
            "Можно выбрать интерфейс — полезно, когда WAN несколько и надо понять, через какой идёт трафик.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Адрес") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text("Через интерфейс", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricChip(title = "авто", selected = iface == null, color = Accent) { iface = null }
            n.ifaces.filter { it.up }.take(4).forEach { f ->
                MetricChip(title = f.name, selected = iface == f.name, color = Accent) {
                    iface = f.name
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { vm.runPing(target, iface) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !n.pingBusy && target.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(if (n.pingBusy) "Проверяю…" else "Пинг", fontWeight = FontWeight.SemiBold)
        }

        n.pingOut?.let {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF0B0F14))
                    .border(1.dp, Outline, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    it,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (it.startsWith("Ошибка")) Bad else Muted
                )
            }
        }
    }
}
