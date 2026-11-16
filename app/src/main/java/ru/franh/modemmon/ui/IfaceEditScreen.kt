package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/** Протоколы, которые предлагаем в выпадашке. */
private val PROTOS = listOf(
    "static" to "Статический адрес",
    "dhcp" to "DHCP-клиент",
    "pppoe" to "PPPoE",
    "modemmanager" to "Модем (ModemManager)",
    "none" to "Без протокола",
    "wireguard" to "WireGuard",
    "amneziawg" to "AmneziaWG"
)

/**
 * Форма редактирования интерфейса — как в LuCI: подписи, выпадашка протокола,
 * поля по типу протокола, переключатели. Сырые опции доступны кнопкой
 * «Все параметры».
 */
@Composable
fun IfaceEditScreen(
    state: UiState,
    vm: ModemViewModel,
    iface: String,
    onRaw: () -> Unit,
    onBack: () -> Unit
) {
    val c = state.cfg
    LaunchedEffect(iface, state.ubus.connected) {
        if (state.ubus.connected && c.name != "network") vm.loadConfig("network")
    }

    val section: UciConfig.Section? = c.sections.firstOrNull { it.name == iface }
    fun opt(key: String): String =
        section?.options?.firstOrNull { it.key == key }?.value.orEmpty()

    var proto by remember(section) { mutableStateOf(opt("proto").ifBlank { "static" }) }
    var ipaddr by remember(section) { mutableStateOf(opt("ipaddr").substringBefore("/")) }
    var netmask by remember(section) {
        mutableStateOf(prefixToMask(opt("ipaddr").substringAfter("/", "24").toIntOrNull() ?: 24))
    }
    var gateway by remember(section) { mutableStateOf(opt("gateway")) }
    var dns by remember(section) { mutableStateOf(opt("dns")) }
    var disabled by remember(section) { mutableStateOf(opt("disabled") == "1") }
    var auto by remember(section) { mutableStateOf(opt("auto") != "0") }
    var pickProto by remember { mutableStateOf(false) }

    // PPPoE
    var username by remember(section) { mutableStateOf(opt("username")) }
    var password by remember(section) { mutableStateOf(opt("password")) }
    // Модем
    var apn by remember(section) { mutableStateOf(opt("apn")) }
    var metric by remember(section) { mutableStateOf(opt("metric")) }

    ScreenShell(connected = state.ubus.connected) {
        if (section == null && !c.loading) {
            CardBox {
                Text("Секция $iface не найдена в /etc/config/network", color = Bad, fontSize = 12.sp)
            }
            return@ScreenShell
        }

        CardBox {
            Text(
                iface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                section?.type ?: "",
                color = Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(14.dp))
            Label("ПРОТОКОЛ")
            PickerField(
                value = PROTOS.firstOrNull { it.first == proto }?.second ?: proto
            ) { pickProto = true }

            when (proto) {
                "static" -> {
                    Label("IPV4 АДРЕС")
                    Field(ipaddr) { ipaddr = it }
                    Label("МАСКА ПОДСЕТИ")
                    Field(netmask) { netmask = it }
                    Label("ШЛЮЗ")
                    Field(gateway) { gateway = it }
                    Label("DNS (через запятую)")
                    Field(dns) { dns = it }
                }
                "pppoe" -> {
                    Label("ИМЯ ПОЛЬЗОВАТЕЛЯ")
                    Field(username) { username = it }
                    Label("ПАРОЛЬ")
                    Field(password) { password = it }
                }
                "modemmanager" -> {
                    Label("APN")
                    Field(apn) { apn = it }
                    Label("МЕТРИКА")
                    Field(metric) { metric = it.filter { ch -> ch.isDigit() } }
                }
                else -> {
                    Text(
                        "Для этого протокола полей немного — при необходимости " +
                            "откройте «Все параметры».",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SwitchRow("Отключить интерфейс", disabled) { disabled = it }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Запускать при старте", auto) { auto = it }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val values = mutableMapOf<String, Any>()
                    values["proto"] = proto
                    values["disabled"] = if (disabled) "1" else "0"
                    values["auto"] = if (auto) "1" else "0"
                    if (proto == "static") {
                        if (ipaddr.isNotBlank()) {
                            values["ipaddr"] = listOf("$ipaddr/${maskToPrefix(netmask)}")
                        }
                        values["gateway"] = gateway
                        values["dns"] = dns.split(",", " ")
                            .map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    if (proto == "pppoe") {
                        values["username"] = username
                        values["password"] = password
                    }
                    if (proto == "modemmanager") {
                        values["apn"] = apn
                        if (metric.isNotBlank()) values["metric"] = metric
                    }
                    vm.saveIface(iface, values)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !c.loading,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(if (c.loading) "Сохраняю…" else "Сохранить", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRaw,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
                ) { Text("Все параметры", fontSize = 12.sp) }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Muted)
                ) { Text("Назад", fontSize = 12.sp) }
            }

            c.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = if (it.startsWith("Ошибка")) Bad else Good
                )
            }
            c.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Bad, fontSize = 12.sp)
            }
        }
    }

    if (pickProto) {
        AlertDialog(
            onDismissRequest = { pickProto = false },
            title = { Text("Протокол") },
            text = {
                Column {
                    PROTOS.forEach { (id, title) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    proto = id
                                    pickProto = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                title,
                                fontSize = 14.sp,
                                color = if (id == proto) Accent else Muted
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickProto = false }) { Text("Закрыть", color = Muted) }
            }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 4.dp, top = 6.dp)
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PickerField(value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("⌄", fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun maskToPrefix(mask: String): Int {
    val parts = mask.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return 24
    var bits = 0
    for (p in parts) {
        var v = p
        while (v and 0x80 != 0) {
            bits++
            v = v shl 1 and 0xFF
        }
    }
    return bits
}

private fun prefixToMask(prefix: Int): String {
    val p = prefix.coerceIn(0, 32)
    var mask = 0L
    repeat(p) { mask = mask shl 1 or 1L }
    mask = mask shl (32 - p)
    return listOf(24, 16, 8, 0).joinToString(".") { shift ->
        ((mask shr shift) and 0xFF).toString()
    }
}
