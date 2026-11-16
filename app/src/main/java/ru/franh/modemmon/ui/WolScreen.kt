package ru.franh.modemmon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.OnSurface
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/**
 * Wake-on-LAN: роутер отправляет magic packet в локальную сеть (etherwake),
 * компьютер с указанным MAC включается. Цели хранятся в приложении.
 * Если etherwake на роутере нет — приложение ставит его само из репозитория.
 */
@Composable
fun WolScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(Unit) { vm.loadWol() }
    val wol = state.wol
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            SectionLabel("Wake-on-LAN")
            Text(
                "Роутер отправляет magic packet в локальную сеть (br-lan), " +
                    "и компьютер с этим MAC включается. Цели хранятся в приложении.",
                fontSize = 12.sp,
                color = Muted,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(10.dp))

            // Интерфейс броадкаста
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Интерфейс:", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = wol.iface,
                    onValueChange = { vm.setWolIface(it) },
                    modifier = Modifier.width(140.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Новая цель
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("имя", fontSize = 12.sp, color = Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle = TextStyle(fontSize = 12.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors()
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    modifier = Modifier.weight(1.4f),
                    placeholder = { Text("AA:BB:CC:DD:EE:FF", fontSize = 12.sp, color = Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        vm.addWolTarget(name, mac)
                        if (state.wol.error == null) {
                            name = ""
                            mac = ""
                        }
                    },
                    enabled = mac.isNotBlank() && !wol.busy
                ) {
                    Text("Добавить цель", color = Accent)
                }
            }

            wol.error?.let { Text(it, fontSize = 11.sp, color = Bad) }
            wol.message?.let { Text(it, fontSize = 11.sp, color = Good) }

            Spacer(Modifier.height(6.dp))

            // Список целей
            if (wol.targets.isEmpty()) {
                Text(
                    "Пока нет целей — добавь MAC компьютера выше.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            wol.targets.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 13.sp, color = OnSurface)
                        Text(
                            t.mac,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Muted
                        )
                    }
                    TextButton(onClick = { vm.sendWol(t.mac) }, enabled = !wol.busy) {
                        Text("Разбудить", color = if (wol.busy) Muted else Good)
                    }
                    IconButton(onClick = { vm.removeWolTarget(t.mac) }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Удалить", tint = Muted)
                    }
                }
            }

            if (wol.busy) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("отправляю magic packet…", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun compactFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceHigh,
    unfocusedContainerColor = SurfaceHigh,
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurface,
    focusedIndicatorColor = Accent,
    unfocusedIndicatorColor = Outline,
    cursorColor = Accent
)
