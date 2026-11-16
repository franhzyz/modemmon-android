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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.util.Fmt
import ru.franh.modemmon.util.Metric
import ru.franh.modemmon.util.SignalEval
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.Trend
import ru.franh.modemmon.vm.UiState
import java.util.Locale

@Composable
fun MonitorScreen(state: UiState, vm: ModemViewModel, nowTick: Long) {
    val snap = state.snap
    val metric = Metric.byKey(state.settings.metric)
    val value = vm.valueOf(snap, metric)
    val quality = value?.let { SignalEval.quality(metric, it) }
    val percent = value?.let { SignalEval.percent(metric, it) } ?: 0f
    val peak = state.peaks[metric]
    val stale = state.updatedAt > 0 && nowTick - state.updatedAt > state.settings.intervalMs * 3L + 2000

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusPill(
                online = snap?.online == true && !stale,
                text = when {
                    state.settings.demo -> "Демо-режим"
                    snap == null -> "Подключение…"
                    stale -> "Нет свежих данных"
                    snap.online -> "Онлайн"
                    else -> "Нет связи"
                }
            )
            Text(
                text = listOf(snap?.operator.orEmpty(), snap?.rat.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "—" },
                color = Muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.entries.forEach { m ->
                MetricChip(
                    title = m.title,
                    selected = m == metric,
                    color = SignalEval.quality(
                        m,
                        vm.valueOf(snap, m) ?: 0.0
                    ).color,
                    onClick = { vm.updateSettings(state.settings.copy(metric = m.key)) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = metric.title,
                        color = Muted,
                        fontSize = 12.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = when (metric) {
                                Metric.RSRP, Metric.RSSI -> Fmt.dbm(value)
                                else -> Fmt.db(value)
                            },
                            color = quality?.color ?: Muted,
                            fontSize = 62.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 66.sp
                        )
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(
                            text = metric.unit,
                            color = Muted,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    TrendArrow(state.trend)
                    Text(
                        text = quality?.label ?: "—",
                        color = quality?.color ?: Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            QualityBar(percent = percent / 100f, color = quality?.color ?: Outline)

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "пик ${formatValue(metric, peak)} ${metric.unit}",
                    color = Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                val delta = if (peak != null && value != null) peak - value else null
                Text(
                    text = if (delta == null || delta < 0.05) "на пике" else
                        "Δ до пика ${String.format(Locale.US, "%.1f", delta)} ${metric.unit}",
                    color = if (delta == null || delta < 0.05) Good else Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Sparkline(
            values = state.history[metric].orEmpty(),
            color = quality?.color ?: Muted,
            peak = peak?.toFloat()
        )

        Spacer(Modifier.height(12.dp))

        val tips = if (value != null && snap != null) {
            SignalEval.tips(
                metric = metric,
                v = value,
                sinr = snap.nr.sinr ?: snap.lte.sinr,
                rsrp = snap.nr.rsrp ?: snap.lte.rsrp,
                hasNr = snap.hasNr
            )
        } else {
            listOf("Нет данных с модема. Проверьте адрес коллектора на вкладке «Настройки» или включите демо-режим.")
        }

        CardBox {
            SectionLabel("Совет")
            tips.forEach {
                Text(
                    text = it,
                    color = Color(0xFFD7E2EC),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        // Diagnosis from the MedveModem engine: explains why the link is down
        // ("whitelist only", "no SIM", …) or flags a warning (DNS interception).
        val engine = snap?.engine
        val diagReason = engine?.diagnosisReason.orEmpty()
        val warnReason = engine?.warningReason.orEmpty()
        if (!warnReason.isNullOrBlank() || diagReason.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            CardBox {
                SectionLabel("Диагностика движка")
                if (warnReason.isNotBlank()) {
                    Text(
                        text = diagnosisLine(warnReason),
                        color = Color(0xFFFFC777),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    val detail = engine?.warningDetail.orEmpty()
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                if (diagReason.isNotBlank()) {
                    Text(
                        text = diagnosisLine(diagReason),
                        color = Color(0xFFFF8A8D),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    val detail = engine?.diagnosisDetail.orEmpty()
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        snap?.error?.let {
            Spacer(Modifier.height(12.dp))
            CardBox {
                SectionLabel("Ошибка источника")
                Text(it, color = Color(0xFFFF8A8D), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun QualityBar(percent: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(SurfaceHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

private fun formatValue(metric: Metric, v: Double?): String = when {
    v == null -> "—"
    metric == Metric.RSRP || metric == Metric.RSSI -> Fmt.dbm(v)
    else -> Fmt.db(v)
}

/** Переводит код диагностики движка в человеческую строку. */
private fun diagnosisLine(reason: String): String = when (reason.trim().lowercase()) {
    "dns-intercepted" -> "DNS перехватывается оператором: часть доменов может не открываться."
    "whitelist" , "only-whitelist", "whitelist-only" -> "Тариф «только белые списки»: доступны лишь разрешённые сайты."
    "no-sim" -> "SIM-карта не найдена. Проверьте, вставлена ли она."
    "sim-locked" -> "SIM заблокирована (PIN/PUK)."
    "no-service", "no-service-registration" -> "Нет регистрации в сети. Проверьте антенну и покрытие."
    "registration-denied" -> "Оператор отклонил регистрацию."
    "roaming-not-allowed" -> "Роуминг запрещён."
    "no-bearer", "bearer-failed" -> "Не удалось поднять сессию данных."
    "apn-rejected" -> "Оператор отклонил APN."
    "limited-service" -> "Ограниченное обслуживание: только экстренные вызовы."
    else -> reason
}
