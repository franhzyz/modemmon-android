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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.data.RouterProfile
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState
import java.util.UUID

/**
 * Профили роутеров и вход в ubus.
 * Пароль не сохраняется: он уходит только в session.login и живёт в памяти.
 */
@Composable
fun ConnectSection(state: UiState, vm: ModemViewModel) {
    var editing by remember { mutableStateOf<RouterProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

    CardBox {
        SectionLabel("Роутеры")
        Text(
            "Сохранённые адреса. Активный подставляется в опрос телеметрии и в ubus.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(10.dp))

        if (state.profiles.isEmpty()) {
            Text("Пока нет профилей — добавьте первый.", color = Muted, fontSize = 12.sp)
        } else {
            state.profiles.forEach { p ->
                ProfileRow(
                    profile = p,
                    active = p.id == state.activeProfileId,
                    onSelect = { vm.selectProfile(p.id) },
                    onEdit = { editing = p },
                    onDelete = { vm.deleteProfile(p.id) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
        ) {
            Text("Добавить роутер", fontWeight = FontWeight.SemiBold)
        }
    }

    Spacer(Modifier.height(8.dp))

    UbusLoginCard(state, vm)

    if (creating || editing != null) {
        ProfileDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { p ->
                vm.saveProfile(p)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: RouterProfile,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.10f) else SurfaceHigh)
            .border(1.dp, if (active) Accent.copy(alpha = 0.5f) else Outline, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) Accent else Outline)
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                profile.baseUrl,
                color = Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        TextButton(onClick = onEdit) { Text("Изменить", fontSize = 12.sp, color = Accent) }
        TextButton(onClick = onDelete) { Text("Удалить", fontSize = 12.sp, color = Bad) }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun UbusLoginCard(state: UiState, vm: ModemViewModel) {
    var user by remember(state.rememberUser) { mutableStateOf(state.rememberUser ?: "root") }
    var password by remember(state.rememberPass) { mutableStateOf(state.rememberPass.orEmpty()) }
    var remember by remember(state.rememberPass) { mutableStateOf(state.rememberPass != null) }

    CardBox {
        SectionLabel("Управление (ubus)")
        Text(
            "Нужно для AT-команд, SMS, USSD и настроек сети. Телеметрия работает и без входа.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (state.ubus.connected) Good else Outline)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    state.ubus.busy -> "Подключаюсь…"
                    state.ubus.connected -> "Сессия открыта"
                    else -> "Нет сессии"
                },
                fontSize = 12.sp,
                color = if (state.ubus.connected) Good else Muted
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Пользователь") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль роутера") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        state.ubus.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                fontSize = 12.sp,
                color = if (state.ubus.ok) Good else Bad
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { remember = !remember },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = remember, onCheckedChange = { remember = it })
            Spacer(Modifier.size(4.dp))
            Column {
                Text("Запомнить пароль", fontSize = 13.sp)
                Text(
                    "Хранится в приватном хранилище приложения и подставляется при запуске.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.ubusLogin(user.trim().ifBlank { "root" }, password, remember) },
                modifier = Modifier.weight(1f),
                enabled = !state.ubus.busy && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Войти", fontWeight = FontWeight.SemiBold)
            }
            if (state.ubus.connected) {
                Button(
                    onClick = {
                        password = ""
                        remember = false
                        vm.ubusLogout()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Muted)
                ) {
                    Text("Выйти")
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    initial: RouterProfile?,
    onDismiss: () -> Unit,
    onSave: (RouterProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "http://192.168.2.1") }
    var user by remember { mutableStateOf(initial?.user ?: "root") }

    val valid = baseUrl.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый роутер" else "Изменить роутер") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Домашний") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Адрес") },
                    placeholder = { Text("192.168.2.1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Пользователь ubus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = RouterProfile(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { baseUrl.trim() },
                        baseUrl = baseUrl.trim(),
                        user = user.trim().ifBlank { "root" }
                    )
                    onSave(p)
                },
                enabled = valid
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Muted) }
        }
    )
}
