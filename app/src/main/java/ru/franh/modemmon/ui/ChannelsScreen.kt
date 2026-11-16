package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.data.Wifi
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

@Composable
fun ChannelsScreen(state: UiState, vm: ModemViewModel) {
    val s = state.scan

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && s.aps.isEmpty() && !s.loading) vm.runScan()
    }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Анализ каналов")
                TextButton(onClick = { vm.runScan() }, enabled = !s.loading) {
                    Text(if (s.loading) "Сканирую…" else "Сканировать", fontSize = 12.sp, color = Accent)
                }
            }
            s.device?.let {
                Text(it, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            if (s.loading) {
                Spacer(Modifier.height(10.dp))
                Text("Опрос эфира занимает несколько секунд…", color = Muted, fontSize = 12.sp)
            }
            s.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Bad, fontSize = 12.sp)
            }
        }

        if (s.aps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ChannelLoad("Загрузка · 2.4 ГГц", s.aps.filter { it.band == 2 }, (1..13).toList())

            Spacer(Modifier.height(8.dp))
            val fiveChannels = s.aps.filter { it.band != 2 }.map { it.channel }.distinct().sorted()
            if (fiveChannels.isNotEmpty()) {
                ChannelLoad("Загрузка · 5 ГГц", s.aps.filter { it.band != 2 }, fiveChannels)
                Spacer(Modifier.height(8.dp))
            }

            CardBox {
                SectionLabel("Сети · ${s.aps.size}")
                s.aps.forEach { ap ->
                    ApRow(ap)
                }
            }
        }
    }
}

/** Столбики загрузки по каналам. */
@Composable
private fun ChannelLoad(title: String, aps: List<Wifi.Ap>, channels: List<Int>) {
    CardBox {
        SectionLabel(title)
        val counts = channels.associateWith { ch -> aps.count { it.channel == ch } }
        val max = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        channels.forEach { ch ->
            val count = counts[ch] ?: 0
            val frac = if (max == 0) 0f else count.toFloat() / max
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$ch",
                    modifier = Modifier.width(26.dp),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceHigh)
                ) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(loadColor(count))
                        )
                    }
                }
                Text(
                    if (count == 0) "" else "$count",
                    modifier = Modifier.width(22.dp),
                    fontSize = 11.sp,
                    color = loadColor(count),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Чем короче полоса — тем свободнее канал",
            color = Muted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ApRow(ap: Wifi.Ap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                ap.ssid,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                "ch ${ap.channel} · ${ap.band} ГГц" + if (ap.secure) " · 🔒" else "",
                color = Muted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        SignalBars(ap.signal)
        Spacer(Modifier.width(8.dp))
        Text(
            "${ap.signal}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = signalColor(ap.signal)
        )
    }
    Spacer(Modifier.height(5.dp))
}

/** Пять столбиков уровня сигнала. */
@Composable
private fun SignalBars(signal: Int) {
    val filled = when {
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
                    .background(if (i < filled) signalColor(signal) else Outline)
            )
        }
    }
}

private fun loadColor(count: Int) = when {
    count == 0 -> Outline
    count <= 2 -> Good
    count <= 4 -> Warn
    else -> Bad
}

private fun signalColor(signal: Int) = when {
    signal >= -60 -> Good
    signal >= -75 -> Warn
    else -> Bad
}
