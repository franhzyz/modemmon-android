package ru.franh.modemmon.ui

import ru.franh.modemmon.util.L10n

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import ru.franh.modemmon.vm.UiState

/** Монохромный блок для длинных текстовых выводов. */
@Composable
private fun TextBlock(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0F14))
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            text.lines().forEach { line ->
                Text(
                    line,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun SimpleRow(left: String, right: String, rightColor: Color = Muted, badge: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badge != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (badge) Good else Outline)
                    .padding(4.dp)
            )
            Spacer(Modifier.padding(4.dp))
        }
        Text(left, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(right, fontSize = 11.sp, color = rightColor, fontFamily = FontFamily.Monospace)
    }
    Spacer(Modifier.height(5.dp))
}

// ---------------- Пакеты ----------------

@Composable
fun PackagesScreen(state: UiState, vm: ModemViewModel) {
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.packages.isEmpty()) vm.loadPackages()
    }
    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            SectionLabel(L10n.s("Программы · ${state.sys.packages.size}", "Packages · ${state.sys.packages.size}"))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(L10n.s("Поиск", "Search")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            state.sys.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
            state.sys.actionResult?.let {
                Text(it, fontSize = 11.sp, color = if (it.startsWith(L10n.s("Ошибка", "Error"))) Bad else Good)
                Spacer(Modifier.height(6.dp))
            }
            val filtered = state.sys.packages.filter {
                query.isBlank() || it.first.contains(query, true)
            }
            Text(
                if (filtered.isEmpty()) L10n.s("Ничего не найдено", "Nothing found") else L10n.s("Нажмите на пакет для установки или удаления", "Tap a package to install or remove it"),
                color = Muted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            filtered.take(300).forEach { (name, ver) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceHigh)
                        .clickable { pending = name to false }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(ver, fontSize = 11.sp, color = Muted, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }

    pending?.let { (name, _) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(name) },
            text = { Text(L10n.s("Установить пакет или удалить его с роутера?", "Install the package or remove it from the router?")) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    vm.packageAction(name, install = true)
                }) { Text(L10n.s("Установить", "Install"), color = Accent) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pending = null }) { Text(L10n.s("Отмена", "Cancel"), color = Muted) }
                    TextButton(onClick = {
                        pending = null
                        vm.packageAction(name, install = false)
                    }) { Text(L10n.s("Удалить", "Delete"), color = Bad) }
                }
            }
        )
    }
}

// ---------------- Автозапуск ----------------

@Composable
fun ServicesScreen(state: UiState, vm: ModemViewModel) {
    var pending by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.services.isEmpty()) vm.loadServices()
    }
    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(L10n.s("Автозапуск · ${state.sys.services.size}", "Autostart · ${state.sys.services.size}"))
                TextButton(onClick = { vm.loadServices() }) {
                    Text(L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }
            state.sys.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
            state.sys.actionResult?.let {
                Text(it, fontSize = 11.sp, color = if (it.startsWith(L10n.s("Ошибка", "Error"))) Bad else Good)
                Spacer(Modifier.height(6.dp))
            }
            state.sys.services.forEach { (name, enabled, index) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceHigh)
                        .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 13.sp)
                        Text(L10n.s("порядок $index", "order $index"), color = Muted, fontSize = 10.sp)
                    }
                    TextButton(onClick = {
                        pending = Triple(name, "restart", L10n.s("Перезапустить службу $name?", "Restart service $name?"))
                    }) { Text("↻", fontSize = 16.sp, color = Accent) }
                    TextButton(onClick = {
                        pending = Triple(name, "stop", L10n.s("Остановить службу $name? Это может нарушить работу роутера.", "Stop service $name? This may disrupt the router."))
                    }) { Text("■", fontSize = 14.sp, color = Muted) }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            pending = Triple(
                                name,
                                if (on) "enable" else "disable",
                                if (on) L10n.s("Включить $name в автозапуск?", "Enable $name autostart?") else L10n.s("Отключить $name из автозапуска?", "Disable $name autostart?")
                            )
                        }
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }

    pending?.let { (name, act, question) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(L10n.s("Подтверждение", "Confirm")) },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    vm.serviceAction(name, act)
                }) { Text(L10n.s("Выполнить", "Execute"), color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(L10n.s("Отмена", "Cancel"), color = Muted) }
            }
        )
    }
}

// ---------------- LED ----------------

@Composable
fun LedsScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.leds.isEmpty()) vm.loadLeds()
    }
    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            SectionLabel("LED · ${state.sys.leds.size}")
            state.sys.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
            if (state.sys.leds.isEmpty()) Text(L10n.s("Индикаторов нет", "No indicators"), color = Muted, fontSize = 12.sp)
            state.sys.leds.forEach { SimpleRow(it, "") }
        }
    }
}

// ---------------- Процессы ----------------

@Composable
fun ProcessesScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.processes.isEmpty()) vm.loadProcesses()
    }
    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(L10n.s("Процессы · ${state.sys.processes.size}", "Processes · ${state.sys.processes.size}"))
                TextButton(onClick = { vm.loadProcesses() }) {
                    Text(L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }
            state.sys.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
            if (state.sys.processes.isNotEmpty()) {
                TextBlock(state.sys.processes.joinToString("\n"))
            }
        }
    }
}

// ---------------- CPU ----------------

@Composable
fun CpuScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.cpu == null) vm.loadCpu()
    }
    ScreenShell(connected = state.ubus.connected) {
        val s = state.sys
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(L10n.s("Частота CPU", "CPU frequency"))
                TextButton(onClick = { vm.loadCpu() }) {
                    Text(L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }

            s.cpuModel?.let {
                Text(L10n.s("Ядро: $it", "Core: $it"), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            s.cpuGovernor?.let {
                Spacer(Modifier.height(4.dp))
                Text("Governor: $it", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            if (s.cpuCores.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val max = if (s.cpuMaxMhz > 0) s.cpuMaxMhz else (s.cpuCores.maxOrNull() ?: 1f)
                val avg = s.cpuCores.average().toInt()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        value = "$avg",
                        caption = L10n.s("МГц сейчас", "MHz now"),
                        color = Accent,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${max.toInt()}",
                        caption = L10n.s("МГц максимум", "MHz max"),
                        color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(L10n.s("По ядрам", "Per core"), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))

                s.cpuCores.forEachIndexed { i, mhz ->
                    val frac = (mhz / max).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            L10n.s("ядро $i", "core $i"),
                            modifier = Modifier.width(58.dp),
                            fontSize = 11.sp,
                            color = Muted,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceHigh)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(frac)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            frac > 0.85f -> Bad
                                            frac > 0.5f -> Warn
                                            else -> Good
                                        }
                                    )
                            )
                        }
                        Text(
                            "${mhz.toInt()}",
                            modifier = Modifier.width(46.dp),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Muted
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.s("Полоса показывает загрузку частоты относительно максимума", "The bar shows frequency load relative to the maximum"),
                    color = Muted,
                    fontSize = 10.sp
                )
            } else if (s.error != null) {
                Text(s.error, color = Bad, fontSize = 12.sp)
            } else {
                Text(L10n.s("Данных нет", "No data"), color = Muted, fontSize = 12.sp)
            }
        }

        if (s.cpu != null) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                SectionLabel(L10n.s("Процессор", "Processor"))
                TextBlock(s.cpu)
            }
        }
    }
}

// ---------------- Задачи по расписанию ----------------

@Composable
fun CrontabScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.crontab == null) vm.loadCrontab()
    }
    ScreenShell(connected = state.ubus.connected) {
        var text by remember(state.sys.crontab) { mutableStateOf(state.sys.crontab.orEmpty()) }

        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(L10n.s("Задачи по расписанию", "Scheduled tasks"))
                TextButton(onClick = { vm.loadCrontab() }) {
                    Text(L10n.s("Перечитать", "Reload"), fontSize = 12.sp, color = Accent)
                }
            }
            Text(
                L10n.s("/etc/crontabs/root · формат: минута час день месяц день_недели команда", "/etc/crontabs/root · format: minute hour day month weekday command"),
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            state.sys.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Bad, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(L10n.s("Содержимое", "Content")) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.saveCrontab(text) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.sys.loading,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (state.sys.loading) L10n.s("Сохраняю…", "Saving…") else L10n.s("Сохранить", "Save"), fontWeight = FontWeight.SemiBold)
            }

            state.sys.actionResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = if (it.startsWith(L10n.s("Ошибка", "Error"))) Bad else Good
                )
            }
        }
    }
}

// ---------------- Wi-Fi ----------------

@Composable
fun WifiScreen(state: UiState, vm: ModemViewModel) {
    val w = state.wifi2

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && w.radios.isEmpty()) vm.loadWifi2()
    }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Wireless · ${w.radios.size}")
                TextButton(onClick = { vm.loadWifi2() }, enabled = !w.loading) {
                    Text(if (w.loading) L10n.s("Читаю…", "Reading…") else L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }
            w.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
            if (w.radios.isEmpty() && !w.loading) {
                Text(L10n.s("Wi-Fi не найден", "No Wi-Fi found"), color = Muted, fontSize = 12.sp)
            }
        }

        w.radios.forEach { radio ->
            Spacer(Modifier.height(8.dp))
            CardBox {
                val info = radio.info
                if (info == null) {
                    Text(radio.device, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(L10n.s("нет данных", "no data"), color = Muted, fontSize = 12.sp)
                    return@CardBox
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (radio.clients.isNotEmpty()) Good else Outline)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        info.ssid,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Badge(info.band, Accent)
                    Spacer(Modifier.size(4.dp))
                    Badge(info.htmode, Muted)
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    info.bssid,
                    color = Muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        value = info.channel.toString(),
                        caption = L10n.s("канал", "channel"),
                        color = Accent,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${info.frequency}",
                        caption = L10n.s("МГц", "MHz"),
                        color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = radio.clients.size.toString(),
                        caption = L10n.s("клиентов", "clients"),
                        color = if (radio.clients.isEmpty()) Muted else Good,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))
                InfoRow(L10n.s("Шифрование", "Encryption"), info.encryption)
                InfoRow(L10n.s("Режим", "Mode"), "${info.hwmode} / ${info.htmode}")
                InfoRow(L10n.s("Мощность", "Power"), if (info.txpower > 0) L10n.s("${info.txpower} дБм", "${info.txpower} dBm") else "—")

                Spacer(Modifier.height(10.dp))
                Text(L10n.s("Клиенты", "Clients"), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                if (radio.clients.isEmpty()) {
                    Text(L10n.s("Никто не подключён", "Nobody connected"), color = Muted, fontSize = 12.sp)
                }
                radio.clients.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceHigh)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.mac,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                fmtConn(c.connectedSec) +
                                    if (c.authorized) "" else L10n.s(" · не авторизован", " · not authorized"),
                                color = if (c.authorized) Muted else Warn,
                                fontSize = 10.sp
                            )
                        }
                        WifiBars(c.signal)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (c.signal == 0) "—" else "${c.signal}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                c.signal == 0 -> Muted
                                c.signal >= -60 -> Good
                                c.signal >= -75 -> Warn
                                else -> Bad
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun WifiBars(signal: Int) {
    // 0 — драйвер не сообщает уровень сигнала
    val filled = when {
        signal == 0 -> 0
        signal >= -50 -> 5
        signal >= -60 -> 4
        signal >= -70 -> 3
        signal >= -80 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((5 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (i < filled) {
                            when {
                                signal >= -60 -> Good
                                signal >= -75 -> Warn
                                else -> Bad
                            }
                        } else Outline
                    )
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun fmtConn(sec: Long): String = when {
    sec <= 0 -> L10n.s("только что", "just now")
    sec < 60 -> L10n.s("$sec с", "$sec s")
    sec < 3600 -> L10n.s("${sec / 60} мин", "${sec / 60} min")
    sec < 86400 -> L10n.s("${sec / 3600} ч ${(sec % 3600) / 60} м", "${sec / 3600} h ${(sec % 3600) / 60} m")
    else -> L10n.s("${sec / 86400} дн", "${sec / 86400} d")
}

// ---------------- Перезагрузка ----------------

@Composable
fun RebootScreen(state: UiState, vm: ModemViewModel) {
    var confirm by remember { mutableStateOf(false) }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            SectionLabel(L10n.s("Перезагрузка", "Reboot"))
            Text(
                L10n.s("Роутер уйдёт в перезагрузку и пропадёт из сети примерно на минуту. ", "The router will reboot and disappear from the network for about a minute. ") +
                    L10n.s("Модемы переподключатся сами.", "Modems will reconnect on their own."),
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Bad.copy(alpha = 0.18f), contentColor = Bad)
            ) {
                Text(L10n.s("Перезагрузить роутер", "Reboot router"), fontWeight = FontWeight.SemiBold)
            }
            state.sys.actionResult?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = if (it.contains(L10n.s("перезагружается", "rebooting"))) Good else Warn)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(L10n.s("Перезагрузить роутер?", "Reboot the router?")) },
            text = { Text(L10n.s("Связь с роутером прервётся. Уверены?", "Connection to the router will drop. Sure?")) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.rebootRouter()
                }) { Text(L10n.s("Перезагрузить", "Reboot"), color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(L10n.s("Отмена", "Cancel"), color = Muted) }
            }
        )
    }
}

// ---------------- Обзор ----------------

@Composable
fun OverviewScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && state.sys.overview.isEmpty()) vm.loadOverview()
    }
    ScreenShell(connected = state.ubus.connected) {
        val s = state.sys
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(L10n.s("Обзор", "Overview"))
                TextButton(onClick = { vm.loadOverview() }, enabled = !s.loading) {
                    Text(if (s.loading) L10n.s("Читаю…", "Reading…") else L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = s.uptimeText ?: "—",
                    caption = L10n.s("работает", "running"),
                    color = Good,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = if (s.cores > 0) s.cores.toString() else "—",
                    caption = L10n.s("ядер", "cores"),
                    color = Accent,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = s.ramPercent?.let { "$it%" } ?: "—",
                    caption = L10n.s("занято RAM", "RAM used"),
                    color = when {
                        (s.ramPercent ?: 0) >= 90 -> Bad
                        (s.ramPercent ?: 0) >= 70 -> Warn
                        else -> Good
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = s.routerTemp?.let { "%.1f°".format(it) } ?: "—",
                    caption = L10n.s("роутер", "router"),
                    color = tempColor(s.routerTemp),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = s.modemTemp?.let { "%.0f°".format(it) } ?: "—",
                    caption = L10n.s("модем", "modem"),
                    color = tempColor(s.modemTemp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            s.ramPercent?.let { pct ->
                ProgressRow(
                    label = L10n.s("Память", "Memory"),
                    value = s.ramText ?: "$pct%",
                    fraction = pct / 100f,
                    color = when {
                        pct >= 90 -> Bad
                        pct >= 70 -> Warn
                        else -> Good
                    }
                )
            }
            s.loadPercent?.let { pct ->
                ProgressRow(
                    label = L10n.s("Нагрузка CPU", "CPU load"),
                    value = "$pct%",
                    fraction = pct / 100f,
                    color = when {
                        pct >= 90 -> Bad
                        pct >= 70 -> Warn
                        else -> Accent
                    }
                )
            }

            if (s.overview.isEmpty() && !s.loading) {
                Text(L10n.s("Нет данных", "No data"), color = Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (s.overview.isNotEmpty()) {
            CardBox {
                val model = s.overview.firstOrNull { it.first == L10n.s("Модель", "Model") }?.second
                if (model != null) {
                    Text(model, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(L10n.s("роутер", "router"), color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(10.dp))
                }
                s.overview.filterNot { it.first == L10n.s("Модель", "Model") }.forEach { (label, value) ->
                    SimpleRow(label, value)
                }
            }
        }

        ClientsCard(state, vm)
    }
}

/** Цвет по температуре: до 60 °C норма, до 75 °C внимание, выше — плохо. */
private fun tempColor(t: Double?): Color = when {
    t == null -> Muted
    t >= 75 -> Bad
    t >= 60 -> Warn
    else -> Good
}

// ---------------- Резервная копия ----------------

@Composable
fun BackupScreen(state: UiState, vm: ModemViewModel) {
    val b = state.backup

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && b.files.isEmpty()) vm.loadBackups()
    }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(L10n.s("Резервная копия", "Backup"))
                TextButton(onClick = { vm.loadBackups() }, enabled = !b.loading) {
                    Text(L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }
            Text(
                L10n.s("Архив конфигурации создаётся на роутере в /tmp. ", "The config archive is created on the router in /tmp. ") +
                    L10n.s("Скачать его можно через веб-интерфейс роутера.", "Download it via the router web interface."),
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.createBackup() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !b.loading,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (b.loading) L10n.s("Создаю…", "Creating…") else L10n.s("Создать копию", "Create backup"), fontWeight = FontWeight.SemiBold)
            }
            b.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = if (it.startsWith(L10n.s("Ошибка", "Error"))) Bad else Good
                )
            }
            b.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Bad, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        CardBox {
            SectionLabel(L10n.s("Архивы · ${b.files.size}", "Archives · ${b.files.size}"))
            if (b.files.isEmpty() && !b.loading) {
                Text(L10n.s("Архивов пока нет", "No archives yet"), color = Muted, fontSize = 12.sp)
            }
            b.files.forEach { (name, size) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceHigh)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📦", fontSize = 13.sp)
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        name,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        fmtSize(size),
                        fontSize = 11.sp,
                        color = Accent,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

private fun fmtSize(b: Long): String {
    if (b <= 0) return L10n.s("0 Б", "0 B")
    val units = listOf(L10n.s("Б", "B"), L10n.s("КиБ", "KiB"), L10n.s("МиБ", "MiB"))
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$b ${units[0]}" else String.format("%.1f %s", v, units[i])
}

// ---------------- Команда с выводом ----------------

@Composable
fun ExecScreen(
    state: UiState,
    vm: ModemViewModel,
    title: String,
    command: String,
    params: List<String>,
    hint: String
) {
    LaunchedEffect(state.ubus.connected, command) {
        if (state.ubus.connected && state.sys.execTitle != title) {
            vm.runExec(title, command, params)
        }
    }
    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(title)
                TextButton(onClick = { vm.runExec(title, command, params) }) {
                    Text(L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
                }
            }
            Text(hint, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))
            state.sys.execOut?.let { TextBlock(it) }
        }
    }
}

@Composable
fun NotReadyScreen(state: UiState, title: String, luciPath: String) {
    val context = LocalContext.current
    ScreenShell(connected = true) {
        CardBox {
            SectionLabel(title)
            Text(
                L10n.s("Этот раздел пока не реализован в приложении — он есть только в веб-интерфейсе LuCI. ", "This section is not implemented in the app yet — it exists only in the LuCI web interface. ") +
                    L10n.s("Открыть можно в браузере.", "Open it in a browser."),
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val base = state.settings.baseUrl.trimEnd('/')
                    val url = "$base/cgi-bin/luci/admin$luciPath"
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
            ) {
                Text(L10n.s("Открыть в LuCI", "Open in LuCI"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
