package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.data.UciConfig
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/**
 * Универсальный редактор UCI-конфига.
 * Один экран обслуживает system, dhcp, firewall, network, footstrap и прочие файлы.
 */
@Composable
fun ConfigScreen(
    state: UiState,
    vm: ModemViewModel,
    config: String,
    title: String,
    onlySection: String? = null,
    service: String? = null
) {
    val c = state.cfg
    var filter by remember(config, onlySection) { mutableStateOf("") }

    LaunchedEffect(config, onlySection, state.ubus.connected) {
        if (state.ubus.connected && c.name != config) vm.loadConfig(config)
    }
    LaunchedEffect(service, state.ubus.connected) {
        if (service != null && state.ubus.connected) vm.loadServiceStatus(service)
    }

    ScreenShell(connected = state.ubus.connected) {
        if (service != null) {
            ServiceCard(state, vm, service, title)
            Spacer(Modifier.height(8.dp))
        }

        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(title)
                TextButton(onClick = { vm.loadConfig(config) }, enabled = !c.loading) {
                    Text(if (c.loading) "Читаю…" else "Обновить", fontSize = 12.sp, color = Accent)
                }
            }
            Text(
                "/etc/config/$config · выключатели и поля меняются сразу",
                color = Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Поиск опции") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            c.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Bad, fontSize = 12.sp)
            }
            c.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = if (it.startsWith("Ошибка")) Bad else Good
                )
            }
            if (config == "network") {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.applyNetwork() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Применить изменения сети", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val base = if (onlySection == null) c.sections
        else c.sections.filter { it.name == onlySection }

        val visible = base.mapNotNull { s ->
            val opts = if (filter.isBlank()) s.options
            else s.options.filter { it.key.contains(filter, true) }
            if (opts.isEmpty() && filter.isNotBlank()) null else s.copy(options = opts)
        }

        if (visible.isEmpty() && !c.loading) {
            CardBox {
                Text(
                    if (filter.isBlank()) "Секций нет или конфиг недоступен" else "Ничего не найдено",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        visible.forEach { section ->
            CardBox {
                Text(
                    section.type,
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                if (!section.anonymous) {
                    Text(section.name, color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))

                if (section.options.isEmpty()) {
                    Text("Без опций", color = Muted, fontSize = 11.sp)
                }
                section.options.forEach { opt ->
                    OptionRow(opt) { value ->
                        vm.saveOption(config, section.name, opt.key, value, opt.isList)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Карточка управления службой: состояние и кнопки. */
@Composable
private fun ServiceCard(state: UiState, vm: ModemViewModel, service: String, title: String) {
    val st = state.cfg.service

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (st?.running == true) Good else Outline)
            )
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Badge(if (st?.running == true) "работает" else "остановлена", if (st?.running == true) Good else Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "служба $service · автозапуск " + if (st?.enabled == true) "включён" else "выключен",
            color = Muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.serviceControl(service, if (st?.running == true) "stop" else "start")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (st?.running == true) Warn.copy(alpha = 0.16f) else Good.copy(alpha = 0.16f),
                    contentColor = if (st?.running == true) Warn else Good
                )
            ) {
                Text(if (st?.running == true) "Остановить" else "Запустить", fontSize = 12.sp)
            }
            Button(
                onClick = { vm.serviceControl(service, "restart") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent.copy(alpha = 0.16f),
                    contentColor = Accent
                )
            ) { Text("Перезапустить", fontSize = 12.sp) }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Автозапуск", fontSize = 13.sp)
                Text("запускать при загрузке роутера", color = Muted, fontSize = 10.sp)
            }
            Switch(
                checked = st?.enabled == true,
                onCheckedChange = { on ->
                    vm.serviceControl(service, if (on) "enable" else "disable")
                }
            )
        }

        state.cfg.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                fontSize = 12.sp,
                color = if (it.startsWith("Ошибка")) Bad else Good
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

/** Человеческая подпись для ключа вида enable_yacd → «Enable yacd». */
private fun pretty(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun OptionRow(opt: UciConfig.Option, onSave: (String) -> Unit) {
    val key = opt.key
    val value = opt.value
    var editing by remember { mutableStateOf(false) }
    val isBool = !opt.isList && (value == "0" || value == "1")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .clickable(enabled = !isBool) { editing = true }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pretty(key), fontSize = 12.sp, color = Muted)
                if (opt.isList) {
                    Spacer(Modifier.width(6.dp))
                    Text("список", fontSize = 9.sp, color = Accent)
                }
            }
            if (!isBool) {
                Text(
                    value.ifBlank { "—" },
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (isBool) {
            Switch(
                checked = value == "1",
                onCheckedChange = { onSave(if (it) "1" else "0") }
            )
        }
    }
    Spacer(Modifier.height(5.dp))

    if (editing) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(key, fontSize = 15.sp) },
            text = {
                Column {
                    if (opt.isList) {
                        Text(
                            "Элементы списка — через пробел или запятую.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    onSave(text)
                }) { Text("Сохранить", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Отмена", color = Muted) }
            }
        )
    }
}

/** Список конфигов, доступных из панели. */
val CONFIG_SCREENS: List<Pair<String, String>> = listOf(
    "system" to "system",
    "dhcp" to "dhcp",
    "firewall" to "firewall",
    "network" to "network",
    "footstrap" to "footstrap",
    "medvemodem" to "medvemodem",
    "forkop" to "forkop",
    "travelmate" to "travelmate",
    "uhttpd" to "uhttpd"
)
