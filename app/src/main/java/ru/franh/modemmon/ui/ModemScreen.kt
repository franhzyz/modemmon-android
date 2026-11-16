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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.data.MedveModem
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemUiState
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.SmsUiState
import ru.franh.modemmon.vm.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModemDevicesScreen(state: UiState, vm: ModemViewModel) {
    val m = state.modem
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && m.devices.isEmpty()) vm.loadDevices()
    }
    ScreenShell(connected = state.ubus.connected) {
        ChannelsCard(state, vm)
        Spacer(Modifier.height(12.dp))
        DevicesCard(m, vm)
        Spacer(Modifier.height(12.dp))
        RadioCard(m)
    }
}

@Composable
fun AtScreen(state: UiState, vm: ModemViewModel) {
    val m = state.modem
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && m.devices.isEmpty()) vm.loadDevices()
    }
    ScreenShell(connected = state.ubus.connected) { AtCard(m, vm) }
}

@Composable
fun SmsScreen(state: UiState, vm: ModemViewModel) {
    val m = state.modem
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && m.devices.isEmpty()) vm.loadDevices()
    }
    ScreenShell(connected = state.ubus.connected) { SmsCard(m, state.sms, vm) }
}

@Composable
fun UssdScreen(state: UiState, vm: ModemViewModel) {
    val m = state.modem
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && m.devices.isEmpty()) vm.loadDevices()
    }
    ScreenShell(connected = state.ubus.connected) { UssdCard(m, state.sms, vm) }
}

@Composable
private fun DevicesCard(m: ModemUiState, vm: ModemViewModel) {
    CardBox {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel("Модемы · ${m.devices.size}")
            TextButton(onClick = { vm.loadDevices() }, enabled = !m.loading) {
                Text(if (m.loading) "Обновляю…" else "Обновить", fontSize = 12.sp, color = Accent)
            }
        }

        m.error?.let {
            Text(it, color = Bad, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (m.devices.isEmpty() && !m.loading) {
            Text("Модемы не найдены", color = Muted, fontSize = 12.sp)
        }

        m.devices.forEach { d ->
            val active = d.id == m.selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Accent.copy(alpha = 0.10f) else SurfaceHigh)
                    .border(
                        1.dp,
                        if (active) Accent.copy(alpha = 0.5f) else Outline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { vm.selectDevice(d.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (d.ok) Good else Outline)
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        d.title,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        buildString {
                            append(d.vidpid)
                            d.iface?.let { append(" · $it") }
                            if (!d.ok && d.reason != null) append(" · ${d.reason}")
                        },
                        color = Muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun BandsScreen(state: UiState, vm: ModemViewModel) {
    val m = state.modem
    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && m.devices.isEmpty()) vm.loadDevices()
    }
    ScreenShell(connected = state.ubus.connected) {
        val r = m.radio
        if (r == null) {
            CardBox {
                SectionLabel("Диапазоны")
                Text(m.radioError ?: "Нет данных — выберите модем", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            return@ScreenShell
        }

        // Состояние применения и кнопки подтверждения
        m.changeState?.let { st ->
            CardBox(color = SurfaceHigh) {
                SectionLabel("Применение настроек")
                Text(
                    when (st) {
                        "applying" -> "Модем применяет настройки…"
                        "waiting" -> "Проверяю, вернулась ли сеть. Если нет — настройки откатятся сами."
                        "applied" -> "Применено. Связи не было и до изменения — проверять нечего."
                        "kept" -> "Новые настройки оставлены."
                        "rolling_back" -> "Возвращаю прежние настройки…"
                        "rolled_back" -> "Прежние настройки возвращены — сеть не поднялась."
                        "failed" -> "Не удалось применить настройки."
                        "rollback_failed" -> "Не удалось вернуть настройки. Проверьте модем вручную."
                        else -> st
                    },
                    color = if (st == "rolled_back" || st == "failed" || st == "rollback_failed") Bad else Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (st == "waiting" || st == "applied" || st == "kept") {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.confirmBands("keep") },
                            modifier = Modifier.weight(1f),
                            enabled = !m.radioBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Оставить") }
                        Button(
                            onClick = { vm.confirmBands("rollback") },
                            modifier = Modifier.weight(1f),
                            enabled = !m.radioBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Muted)
                        ) { Text("Вернуть") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        CardBox {
            SectionLabel("Диапазоны · ${r.bands.size} активных")
            Text(
                "Режимы: " + r.modes.joinToString(", ").ifBlank { "—" },
                color = Muted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))

            if (m.bandsDraft == null) {
                Text(
                    r.bands.take(30).joinToString(" ") { bandLabel(it) },
                    color = Accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.startBandsEdit() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = r.writable,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
                ) { Text(if (r.writable) "Изменить" else "Модем не позволяет менять диапазоны") }
            } else {
                val draft = m.bandsDraft
                Text(
                    "Выбрано ${draft.size} из ${r.supportedBands.size}",
                    color = Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "После применения модем перерегистрируется в сети: связь пропадёт на 10–60 секунд. " +
                        "Если интернет не вернётся за 2 минуты, настройки откатятся сами.",
                    color = Warn,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))

                listOf("eutran-" to "LTE", "nr-" to "5G", "utran-" to "3G").forEach { (prefix, title) ->
                    val group = r.supportedBands.filter { it.startsWith(prefix) }
                    if (group.isEmpty()) {
                        if (prefix == "nr-") {
                            Text(
                                "5G · модем не сообщает NR-диапазоны (прошивка foxconn-sdx их не отдаёт)",
                                color = Warn,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        return@forEach
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$title · ${group.count { it in draft }}/${group.size}",
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            TextButton(onClick = { vm.toggleBandOn(group) }) {
                                Text("все", fontSize = 10.sp, color = Accent)
                            }
                            TextButton(onClick = { vm.toggleBandOff(group) }) {
                                Text("ничего", fontSize = 10.sp, color = Muted)
                            }
                        }
                    }
                    CompactChips(
                        items = group,
                        selected = draft,
                        onToggle = { vm.toggleBand(it) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.applyBands() },
                        modifier = Modifier.weight(1f),
                        enabled = !m.radioBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text(if (m.radioBusy) "Применяю…" else "Применить") }
                    Button(
                        onClick = { vm.cancelBandsEdit() },
                        modifier = Modifier.weight(1f),
                        enabled = !m.radioBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Muted)
                    ) { Text("Отмена") }
                }

                m.radioError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Bad, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

/** Плотная сетка диапазонов: 8 в ряд, компактные плашки. */
@Composable
private fun CompactChips(items: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column {
        items.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { band ->
                    val on = band in selected
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Accent.copy(alpha = 0.18f) else SurfaceHigh)
                            .border(
                                1.dp,
                                if (on) Accent.copy(alpha = 0.6f) else Outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggle(band) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            bandLabel(band),
                            fontSize = 10.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) Accent else Muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun bandLabel(band: String): String = when {
    band.startsWith("eutran-") -> "B" + band.removePrefix("eutran-")
    band.startsWith("nr-") -> "n" + band.removePrefix("nr-")
    band.startsWith("utran-") -> "U" + band.removePrefix("utran-")
    else -> band
}

@Composable
private fun RadioCard(m: ModemUiState) {
    CardBox {
        SectionLabel("Диапазоны")
        val r = m.radio
        if (r == null) {
            Text(
                m.radioError ?: "Нет данных",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            return@CardBox
        }
        Text(
            "Режимы: " + if (r.modes.isEmpty()) "—" else r.modes.joinToString(", "),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(6.dp))
        Text("Активных диапазонов: ${r.bands.size}", color = Muted, fontSize = 12.sp)
        if (r.bands.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                r.bands.take(24).joinToString(" "),
                color = Accent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (r.writable) "Изменение диапазонов доступно, но пока не включено в приложении."
            else "Модем не позволяет менять диапазоны.",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AtCard(m: ModemUiState, vm: ModemViewModel) {
    var cmd by remember { mutableStateOf("AT+CSQ") }

    CardBox {
        SectionLabel("AT-команды")
        val sel = m.selected
        Text(
            when {
                sel == null -> "Модем не выбран"
                sel.ttyPorts.isEmpty() -> "AT-порт не найден — этот модем не управляется командами"
                else -> "Порт: ${sel.ttyPorts.first()}"
            },
            color = Muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = cmd,
            onValueChange = { cmd = it },
            label = { Text("Команда") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.sendAt(cmd) },
                modifier = Modifier.weight(1f),
                enabled = !m.atBusy && m.selectedId != null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (m.atBusy) "Отправляю…" else "Отправить", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { vm.clearAtOutput() },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Muted)
            ) { Text("Очистить") }
        }

        if (m.history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Недавние", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                m.history.take(4).forEach { h ->
                    MetricChip(title = h, selected = h == cmd, color = Accent) { cmd = h }
                }
            }
        }

        if (m.atOutput.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ConsoleBox(m.atOutput)
        }
    }
}

@Composable
private fun ConsoleBox(lines: List<String>) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF0B0F14))
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            lines.forEach { line ->
                Text(
                    line,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        line.startsWith("> ") -> Accent
                        line.startsWith("Ошибка") -> Bad
                        else -> Muted
                    }
                )
            }
        }
    }
}

@Composable
private fun SmsCard(m: ModemUiState, s: SmsUiState, vm: ModemViewModel) {
    var number by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(m.selectedId) { if (m.selectedId != null) vm.loadSms() }

    CardBox {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel("Сообщения · ${s.messages.size}")
            TextButton(onClick = { vm.loadSms() }, enabled = !s.loading) {
                Text(if (s.loading) "Читаю…" else "Обновить", fontSize = 12.sp, color = Accent)
            }
        }

        s.error?.let {
            Text(it, color = Bad, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (s.messages.isEmpty() && !s.loading && s.error == null) {
            Text("Ящик пуст или модем не отдаёт сообщения", color = Muted, fontSize = 12.sp)
        }

        s.messages.forEach { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceHigh)
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        msg.number ?: "неизвестный номер",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        msg.text ?: "(без текста)",
                        fontSize = 12.sp,
                        color = Muted,
                        lineHeight = 17.sp
                    )
                    Text(
                        buildString {
                            msg.epoch?.let { append(fmtEpoch(it)) }
                            msg.delivery?.let { if (isNotEmpty()) append(" · "); append(it) }
                        },
                        fontSize = 10.sp,
                        color = Muted
                    )
                }
                TextButton(onClick = { vm.deleteSms(msg.id) }) {
                    Text("Удалить", fontSize = 11.sp, color = Bad)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("Новое сообщение", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text("Номер") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                vm.sendSms(number, text)
                text = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = number.isNotBlank() && text.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Отправить", fontWeight = FontWeight.SemiBold)
        }

        s.sendResult?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = if (it == "Отправлено") Good else Muted)
        }
    }
}

@Composable
private fun UssdCard(m: ModemUiState, s: SmsUiState, vm: ModemViewModel) {
    var code by remember { mutableStateOf("*100#") }

    CardBox {
        SectionLabel("USSD")
        Text(
            "Запрос уходит оператору — это может быть платно или менять услуги.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Код") },
            placeholder = { Text("*100#") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.sendUssd(code) },
                modifier = Modifier.weight(1f),
                enabled = !s.ussdBusy && code.isNotBlank() && m.selectedId != null,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (s.ussdBusy) "Запрашиваю…" else "Отправить", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { vm.refreshUssd() },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Muted)
            ) { Text("Статус") }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Состояние: ${s.ussdState}",
            fontSize = 12.sp,
            color = if (s.ussdState == "idle") Muted else Accent,
            fontFamily = FontFamily.Monospace
        )

        s.ussdReply?.let {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceHigh)
                    .border(1.dp, Outline, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = Accent)
            }
        }

        s.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Bad, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private fun fmtEpoch(sec: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(sec * 1000))
