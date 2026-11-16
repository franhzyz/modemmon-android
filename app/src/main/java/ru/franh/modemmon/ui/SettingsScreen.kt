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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.data.Settings
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

@Composable
fun SettingsScreen(state: UiState, vm: ModemViewModel) {
    var baseUrl by remember(state.settings.baseUrl) { mutableStateOf(state.settings.baseUrl) }
    var path by remember(state.settings.path) { mutableStateOf(state.settings.path) }
    var demo by remember(state.settings.demo) { mutableStateOf(state.settings.demo) }
    var sound by remember(state.settings.sound) { mutableStateOf(state.settings.sound) }
    var vibrate by remember(state.settings.vibrate) { mutableStateOf(state.settings.vibrate) }
    var interval by remember(state.settings.intervalMs) { mutableStateOf(state.settings.intervalMs) }

    fun persist() {
        vm.updateSettings(
            state.settings.copy(
                baseUrl = baseUrl.trim(),
                path = path.trim(),
                demo = demo,
                sound = sound,
                vibrate = vibrate,
                intervalMs = interval
            )
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        CardBox {
            SectionLabel("Источник данных")
            Text(
                "Адрес коллектора на роутере OpenWrt, который отдаёт JSON с параметрами модема.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Адрес роутера") },
                placeholder = { Text("192.168.2.1") },
                singleLine = true,
                enabled = !demo,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Путь к JSON") },
                placeholder = { Text("/cgi-bin/modem-telemetry") },
                singleLine = true,
                enabled = !demo,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Итог: ${state.settings.copy(baseUrl = baseUrl, path = path).fullUrl}",
                color = Accent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(8.dp))

        ConnectSection(state, vm)

        Spacer(Modifier.height(8.dp))

        CardBox {
            SectionLabel("Опрос")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1000 to "1 с", 2000 to "2 с", 3000 to "3 с", 5000 to "5 с").forEach { (ms, label) ->
                    MetricChip(
                        title = label,
                        selected = interval == ms,
                        color = Accent,
                        onClick = { interval = ms }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Демо-режим", "Имитация сигнала без роутера", demo) { demo = it }
            SwitchRow("Звук-парктроник", "Писк учащается у пика сигнала", sound) { sound = it }
            SwitchRow("Вибрация", "Короткий виброотклик при каждом писке", vibrate) { vibrate = it }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    persist()
                    vm.test(
                        state.settings.copy(baseUrl = baseUrl, path = path).fullUrl
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
            ) {
                Text("Проверить", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { persist() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Сохранить", fontWeight = FontWeight.SemiBold)
            }
        }

        state.testResult?.let {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (it.startsWith("OK")) Good.copy(alpha = 0.12f) else Outline)
                    .padding(12.dp)
            ) {
                Text(it, fontSize = 12.sp, color = Muted)
            }
        }

        Spacer(Modifier.height(8.dp))

        CardBox {
            SectionLabel("Сброс")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Пики и график сессии", color = Muted, fontSize = 13.sp)
                Button(
                    onClick = { vm.resetPeaks() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceHigh,
                        contentColor = Muted
                    )
                ) { Text("Сбросить") }
            }
        }

        Spacer(Modifier.height(8.dp))

        CardBox {
            SectionLabel("Как это работает")
            Text(
                "Приложение опрашивает JSON-эндпоинт на роутере. " +
                    "Коллектор modem-telemetry.sh на OpenWrt читает ModemManager и QMI " +
                    "и отдаёт совместимый контракт schema_version 1 или 2 через /cgi-bin/modem-telemetry.\n\n" +
                    "Если отдельный параметр недоступен, сервер возвращает null, а приложение показывает прочерк.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
