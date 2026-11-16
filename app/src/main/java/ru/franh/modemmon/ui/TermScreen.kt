package ru.franh.modemmon.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Bg
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.OnSurface
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.TERM_PRESETS
import ru.franh.modemmon.vm.UiState

/**
 * Фронтендный терминал: команда уходит через ubus file exec на роутер и работает
 * в фоне (вывод в /tmp/.mm_term_out, поллинг каждые ~1.2 c). Отсюда:
 *  • «Стоп» убивает процесс и его детей — ping/top/logread -f не блокируют экран;
 *  • код выхода показывается после команды ([код выхода: N]);
 *  • ANSI-коды вырезаются, история команд переживает перезапуск приложения;
 *  • опасные команды (network reload, reboot, sysupgrade, rm -rf /) — с подтверждением;
 *  • чипы частых команд и истории, автодополнение по префиксу, поиск по выводу.
 */
@Composable
fun TermScreen(state: UiState, vm: ModemViewModel) {
    // Редактор файлов — отдельный режим экрана
    if (state.fileEdit.open) {
        FileEditor(state, vm)
        return
    }
    val term = state.term
    var input by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var searchOn by remember { mutableStateOf(false) }
    var pendingDanger by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    val shown = remember(term.lines, search) {
        if (search.isBlank()) term.lines
        else term.lines.filter { it.text.contains(search, ignoreCase = true) }
    }

    // Автоскролл вниз при новых строках
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty()) listState.animateScrollToItem(shown.size - 1)
    }

    val canRun = state.ubus.connected && !term.busy
    val submit: () -> Unit = {
        val cmd = input.trim()
        if (cmd.isNotEmpty() && canRun) {
            if (vm.isDangerous(cmd)) {
                pendingDanger = cmd
            } else {
                vm.runTerm(cmd)
                input = ""
                keyboard?.hide()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .imePadding()
    ) {
        Spacer(Modifier.height(10.dp))

        // Заголовок: промпт, индикатор работы, «Стоп», поиск, копирование, очистка
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "root@openwrt",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Accent
                )
                Text(
                    ":${term.cwd}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted
                )
                if (term.busy) {
                    Spacer(Modifier.size(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Accent
                    )
                    Spacer(Modifier.size(4.dp))
                    TextButton(
                        onClick = { vm.stopTerm() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Bad),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp, vertical = 0.dp
                        )
                    ) {
                        Text("Стоп", fontSize = 12.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.openFileEdit("") }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Редактор файлов",
                        tint = Muted
                    )
                }
                IconButton(onClick = { searchOn = !searchOn; if (!searchOn) search = "" }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Поиск по выводу",
                        tint = if (searchOn) Accent else Muted
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(term.lines.joinToString("\n") { it.text }))
                    },
                    enabled = term.lines.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Скопировать вывод",
                        tint = if (term.lines.isEmpty()) Outline else Muted
                    )
                }
                IconButton(onClick = { vm.clearTerm() }, enabled = term.lines.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Очистить",
                        tint = if (term.lines.isEmpty()) Outline else Muted
                    )
                }
            }
        }

        // Поле поиска по выводу
        if (searchOn) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                placeholder = { Text("фильтр строк…", fontSize = 12.sp, color = Muted) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Сбросить", tint = Muted)
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceHigh,
                    unfocusedContainerColor = SurfaceHigh,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    focusedIndicatorColor = Accent,
                    unfocusedIndicatorColor = Outline,
                    cursorColor = Accent
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        // Область вывода
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bg)
                .border(1.dp, Outline, RoundedCornerShape(12.dp))
        ) {
            if (term.lines.isEmpty()) {
                Text(
                    "Введите команду и нажмите Enter.\n" +
                        "Или выбери частую команду чипом ниже.\n" +
                        "Например: ps, logread | tail, ip route show, uci show network",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted,
                    lineHeight = 16.sp
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp)
                ) {
                    items(shown) { line ->
                        val color = when {
                            line.isInput -> Accent
                            line.isError -> Bad
                            line.isMeta -> Muted
                            else -> OnSurface.copy(alpha = 0.85f)
                        }
                        Text(
                            line.text,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Чипы частых команд
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TERM_PRESETS) { p ->
                TermChip(p) { input = p }
            }
        }

        // Чипы истории (свежие сверху)
        val hist = remember(term.busy) { vm.termHistoryList().take(8) }
        if (hist.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(hist) { h ->
                    TermChip(h) { input = h }
                }
            }
        }

        // Автодополнение по префиксу
        val suggestions = if (input.trim().length >= 2) vm.termSuggestions(input) else emptyList()
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions) { s ->
                    TermChip(s) { input = s }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Поле ввода команды
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = canRun,
            placeholder = {
                Text(
                    if (state.ubus.connected) "команда… (Enter — выполнить)" else "нужен вход в ubus",
                    fontSize = 13.sp,
                    color = Muted
                )
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            trailingIcon = {
                if (input.isNotBlank() && canRun) {
                    IconButton(onClick = submit) {
                        Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = Accent)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceHigh,
                unfocusedContainerColor = SurfaceHigh,
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface,
                focusedIndicatorColor = Accent,
                unfocusedIndicatorColor = Outline,
                cursorColor = Accent
            )
        )

        Spacer(Modifier.height(20.dp))
    }

    // Подтверждение опасной команды
    pendingDanger?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            title = { Text("Опасная команда") },
            text = {
                Text(
                    "«$cmd» может нарушить работу роутера.\n\n" +
                        "На этой плате network reload подвешивает Wi-Fi (UWE5622), " +
                        "reboot и sysupgrade прерывают связь. Выполнить?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDanger = null
                    vm.runTerm(cmd)
                    input = ""
                    keyboard?.hide()
                }) {
                    Text("Выполнить", color = Bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDanger = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/** Чип команды: тап — подставить в поле ввода. */
@Composable
private fun TermChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceHigh,
        border = BorderStroke(1.dp, Outline)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = OnSurface.copy(alpha = 0.9f),
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

/** Частые файлы для чипов редактора. */
private val EDIT_PATHS = listOf(
    "/etc/config/network",
    "/etc/config/wireless",
    "/etc/config/firewall",
    "/etc/config/dhcp",
    "/etc/config/medvemodem",
    "/etc/config/system",
    "/etc/crontabs/root"
)

/**
 * Встроенный редактор файлов роутера: открыть (file read) → править → сохранить
 * (file write, перед записью автоматический бэкап <path>.bak).
 * Замена nano/vi, которые не работают через канал без TTY.
 */
@Composable
private fun FileEditor(state: UiState, vm: ModemViewModel) {
    val fe = state.fileEdit
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .imePadding()
    ) {
        Spacer(Modifier.height(10.dp))

        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Редактор файла",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = Accent
            )
            IconButton(onClick = { vm.closeFileEdit() }) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть редактор", tint = Muted)
            }
        }

        // Путь + кнопка «Открыть»
        var pathInput by remember(fe.path) { mutableStateOf(fe.path) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pathInput,
                onValueChange = { pathInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("путь, напр. /etc/config/network", fontSize = 12.sp, color = Muted) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceHigh,
                    unfocusedContainerColor = SurfaceHigh,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    focusedIndicatorColor = Accent,
                    unfocusedIndicatorColor = Outline,
                    cursorColor = Accent
                )
            )
            TextButton(
                onClick = { vm.openFileEdit(pathInput) },
                enabled = pathInput.isNotBlank() && !fe.loading
            ) {
                Text("Открыть", fontSize = 12.sp, color = Accent)
            }
        }

        // Чипы частых конфигов
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(EDIT_PATHS) { p ->
                TermChip(p) { vm.openFileEdit(p) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Содержимое файла
        Box(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bg)
                .border(1.dp, Outline, RoundedCornerShape(12.dp))
        ) {
            if (fe.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Accent
                )
            } else {
                OutlinedTextField(
                    value = fe.content,
                    onValueChange = { vm.editFileContent(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = OnSurface
                    ),
                    minLines = 30,
                    maxLines = 30,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Accent
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Статус
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                when {
                    fe.loading -> "читаю…"
                    fe.saving -> "сохраняю…"
                    fe.dirty -> "изменён"
                    else -> "без изменений"
                },
                fontSize = 11.sp,
                color = when {
                    fe.dirty -> Warn
                    else -> Muted
                }
            )
            fe.message?.let {
                Text(it, fontSize = 11.sp, color = Good, modifier = Modifier.weight(1f))
            }
        }
        fe.error?.let {
            Text(it, fontSize = 11.sp, color = Bad, lineHeight = 14.sp)
        }

        // Кнопки
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { vm.saveFileEdit() },
                enabled = fe.dirty && !fe.saving && !fe.loading && fe.path.isNotBlank()
            ) {
                Text("Сохранить", color = if (fe.dirty && !fe.saving) Good else Muted)
            }
            TextButton(onClick = { vm.closeFileEdit() }) {
                Text("Закрыть", color = Muted)
            }
            if (fe.aclDenied) {
                TextButton(onClick = { vm.fixFileAcl() }, enabled = !fe.saving) {
                    Text("Исправить ACL", color = Warn)
                }
            }
        }

        // Подсказка про применение конфигов
        if (fe.path.startsWith("/etc/config/")) {
            Text(
                "После сохранения конфиг нужно применить: сеть — ifup <имя> (например ifup mm0), " +
                    "службы — /etc/init.d/<имя> restart. network reload на этой плате запрещён.",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = Muted
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}
