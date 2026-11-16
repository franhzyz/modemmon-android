package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.franh.modemmon.data.SpeedTest
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/**
 * Speed test: замер канала роутера (модема) через speed.cloudflare.com.
 * Тест идёт на роутере, трафик списывается с тарифа — объём выбирается в экране.
 *
 * [SpeedTestCard] — карточка (используется в том числе на экране «Диагностика»),
 * [SpeedTestScreen] — отдельный пункт меню.
 */
@Composable
fun SpeedTestCard(state: UiState, vm: ModemViewModel) {
    var downMb by remember { mutableIntStateOf(25) }
    var withUp by remember { mutableStateOf(true) }
    var upMb by remember { mutableIntStateOf(10) }
    var busy by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf<SpeedTest.Result?>(null) }
    var history by remember { mutableStateOf(listOf<SpeedTest.Result>()) }

    LaunchedEffect(busy) {
        elapsed = 0
        while (busy) {
            delay(1000)
            elapsed++
        }
    }

    Column {
        CardBox {
            SectionLabel("Speed test · канал роутера")
            Text(
                "Замер выполняется на роутере через speed.cloudflare.com — меряется именно " +
                    "модемный канал, а не телефон. Трафик теста списывается с тарифа.",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))

            Text("Объём загрузки", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 25, 50, 100).forEach { mb ->
                    MetricChip(
                        title = "$mb МБ",
                        selected = downMb == mb,
                        color = Accent
                    ) { if (!busy) downMb = mb }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricChip(
                    title = if (withUp) "Отдача: вкл" else "Отдача: выкл",
                    selected = withUp,
                    color = Accent
                ) { if (!busy) withUp = !withUp }
                if (withUp) {
                    listOf(5, 10, 20).forEach { mb ->
                        MetricChip(
                            title = "$mb",
                            selected = upMb == mb,
                            color = Accent
                        ) { if (!busy) upMb = mb }
                    }
                    Text("МБ", color = Muted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    vm.speedTest(downMb, upMb, withUp) { res, err ->
                        busy = false
                        if (res != null) {
                            last = res
                            history = (listOf(res) + history).take(10)
                        } else {
                            error = err
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && state.ubus.connected,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    if (busy) "Замер… $elapsed с" else "Запустить тест",
                    fontWeight = FontWeight.SemiBold
                )
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Bad, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        last?.let { r ->
            CardBox {
                SectionLabel("Результат")
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.downMbps?.let { "%.1f".format(it) } ?: "—",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Good
                        )
                        Text("Мбит/с · загрузка ↓", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(0.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            r.upMbps?.let { "%.1f".format(it) } ?: "—",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent
                        )
                        Text("Мбит/с · отдача ↑", color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                InfoLine("Задержка (ping)", r.pingMs?.let { "%.0f мс".format(it) } ?: "—")
                InfoLine("Интерфейс", r.iface.ifBlank { "—" })
                InfoLine("Трафик теста", fmtBytes(r.usedBytes))
                InfoLine("Длительность", "${r.seconds} с")
            }
            Spacer(Modifier.height(12.dp))
        }

        if (history.size > 1) {
            CardBox {
                SectionLabel("История · ${history.size}")
                history.forEachIndexed { i, r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (i == 0) SurfaceHigh else SurfaceHigh.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "↓ %.1f".format(r.downMbps ?: 0.0),
                            color = Good,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "↑ %.1f".format(r.upMbps ?: 0.0),
                            color = Accent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            r.pingMs?.let { "%.0f мс".format(it) } ?: "—",
                            color = Muted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            fmtBytes(r.usedBytes),
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        CardBox {
            SectionLabel("Как читать результат")
            Text(
                "· Загрузка ниже тарифа — проверьте уровень сигнала в «Модемах».\n" +
                    "· Отдача у LTE обычно в 2–5 раз ниже загрузки — это норма.\n" +
                    "· Если тест стабильно медленный на одном модеме — сравните с другим " +
                    "через «Диагностику» и автопереключение.",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Внимание: тест занимает канал — во время замера скорость у других " +
                    "устройств просядет.",
                color = Warn,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

/** Отдельный пункт меню: та же карточка в оболочке экрана. */
@Composable
fun SpeedTestScreen(state: UiState, vm: ModemViewModel) {
    ScreenShell(connected = state.ubus.connected) { SpeedTestCard(state, vm) }
}

private fun fmtBytes(b: Long): String {
    if (b <= 0) return "0 Б"
    val units = listOf("Б", "КиБ", "МиБ", "ГиБ")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$b ${units[0]}" else "%.1f %s".format(v, units[i])
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
