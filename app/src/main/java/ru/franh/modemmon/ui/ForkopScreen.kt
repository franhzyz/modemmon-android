package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import ru.franh.modemmon.data.Forkop
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemViewModel

/**
 * Экран Forkop (sing-box): статус, дашборд Clash, диагностика CLI,
 * редактор секций правил со списками и ссылками-подключениями.
 * Загрузка самостоятельная: экран дёргает CLI через file exec (vm.ubusCall).
 */
@Composable
fun ForkopScreen(state: ru.franh.modemmon.vm.UiState, vm: ModemViewModel) {
    val base = state.settings.baseUrl.trimEnd('/')
    val connected = state.ubus.connected

    var statusRaw by remember { mutableStateOf("") }
    var statusRunning by remember { mutableStateOf<Boolean?>(null) }
    var downTotal by remember { mutableStateOf(0L) }
    var upTotal by remember { mutableStateOf(0L) }
    var connCount by remember { mutableStateOf(0) }
    var diag by remember { mutableStateOf<List<Forkop.Check>?>(null) }
    var diagBusy by remember { mutableStateOf(false) }
    var sections by remember { mutableStateOf<List<Forkop.SectionData>>(emptyList()) }
    var linksAll by remember { mutableStateOf<List<Forkop.LinkData>>(emptyList()) }
    var sectionsError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun execScript(script: String, onDone: (String) -> Unit) {
        val args = JSONArray().also { a ->
            a.put("-c")
            a.put(script)
        }
        val req = JSONObject().put("command", "/bin/sh").put("params", args)
        vm.ubusCall("file", "exec", req) { reply ->
            onDone(reply.data?.optString("stdout").orEmpty())
        }
    }

    fun loadAll() {
        execScript(Forkop.statusScript()) { raw ->
            val st = Forkop.parseStatus(raw)
            statusRaw = st.raw
            statusRunning = st.running
        }
        execScript(Forkop.summaryScript()) { raw ->
            val s = Forkop.parseSummary(raw)
            downTotal = s.downloadTotal
            upTotal = s.uploadTotal
            connCount = s.connections
        }
        execScript(Forkop.sectionsScript()) { raw ->
            val (rules, links) = Forkop.parseSections(raw)
            sections = rules
            linksAll = links
            sectionsError = if (rules.isEmpty() && links.isEmpty()) "Секции не найдены" else null
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadAll()
    }
    LaunchedEffect(connected) {
        while (connected) {
            delay(3000)
            execScript(Forkop.summaryScript()) { raw ->
                val s = Forkop.parseSummary(raw)
                downTotal = s.downloadTotal
                upTotal = s.uploadTotal
                connCount = s.connections
            }
        }
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp)) {

        item {
            SectionLabel("Статус")
            CardBox {
                if (statusRunning != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (statusRunning == true) Good else Bad)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (statusRunning == true) "Forkop работает" else "Forkop остановлен",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        statusRaw.take(300),
                        fontSize = 10.sp, color = Muted,
                        fontFamily = FontFamily.Monospace, lineHeight = 14.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("restart", "start", "stop").forEach { act ->
                            Button(
                                onClick = { vm.serviceAction("forkop", act) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceHigh, contentColor = Accent
                                )
                            ) { Text(act, fontSize = 12.sp) }
                        }
                    }
                } else {
                    Text("Загрузка…", color = Muted, fontSize = 13.sp)
                }
            }
        }

        item {
            SectionLabel("Дашборд")
            CardBox {
                Text("Всего ↓ ${fmtBytes(downTotal)}   ↑ ${fmtBytes(upTotal)}", fontSize = 14.sp, color = Accent)
                Spacer(Modifier.height(4.dp))
                Text("Соединений: $connCount", fontSize = 13.sp, color = Muted)
            }
        }

        item {
            SectionLabel("Тест секций")
            CardBox {
                Button(
                    onClick = {
                        testing = true
                        val statuses = mutableMapOf<String, String>()
                        val labels = sections.map { it.label }
                        var pending = labels.size
                        labels.forEach { group ->
                            statuses[group] = "тестирую…"
                            testStatus = statuses.toMap()
                            execScript(Forkop.testGroupScript(group)) { raw ->
                                val delays = Forkop.parseTestGroup(raw)
                                statuses[group] = if (delays.isNotEmpty()) {
                                    "ok · ${delays.values.min()}мс (из ${delays.size})"
                                } else {
                                    "нет ответа"
                                }
                                testStatus = statuses.toMap()
                                pending--
                                if (pending <= 0) testing = false
                            }
                        }
                    },
                    enabled = !testing,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
                ) { Text(if (testing) "Тестирую…" else "Тест всех секций", fontSize = 13.sp) }

                Spacer(Modifier.height(8.dp))

                if (testing || testStatus.isNotEmpty()) {
                    sections.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.label, fontSize = 12.sp, color = Accent, modifier = Modifier.weight(1f))
                            val st = testStatus[s.label]
                            Text(
                                st ?: if (testing) "…" else "",
                                fontSize = 11.sp,
                                color = when {
                                    st?.startsWith("ok") == true -> Good
                                    st == "нет ответа" -> Warn
                                    else -> Muted
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("Диагностика")
            CardBox {
                Button(
                    onClick = {
                        diagBusy = true
                        val collected = mutableListOf<Forkop.Check>()
                        Forkop.diagScripts().forEach { (name, script) ->
                            execScript(script) { raw ->
                                synchronized(collected) {
                                    collected.add(Forkop.Check(name, Forkop.parseCheck(raw), raw.take(300)))
                                    if (collected.size == Forkop.diagScripts().size) {
                                        diag = collected.toList()
                                        diagBusy = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !diagBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
                ) { Text(if (diagBusy) "Проверяю…" else "Запустить диагностику", fontSize = 13.sp) }

                diag?.forEach { c ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (c.ok) {
                                        true -> Good
                                        false -> Bad
                                        null -> Muted
                                    }
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(c.name, fontSize = 13.sp, color = Accent)
                    }
                    Text(
                        c.raw.take(260),
                        fontSize = 10.sp, color = Muted,
                        fontFamily = FontFamily.Monospace, lineHeight = 13.sp
                    )
                }
            }
        }

        item {
            SectionLabel("Секции правил")
            CardBox {
                Button(
                    onClick = {
                        execScript(Forkop.addSectionScript("Новая секция", emptyList())) {
                            execScript(Forkop.sectionsScript()) { raw ->
                                val (rules, links) = Forkop.parseSections(raw)
                                sections = rules
                                linksAll = links
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
                ) { Text("Добавить секцию", fontSize = 13.sp) }

                Spacer(Modifier.height(10.dp))

                sections.forEach { s ->
                    var label by remember(s.id) { mutableStateOf(s.label) }
                    var lists by remember(s.id) { mutableStateOf(s.lists.joinToString(" ")) }
                    var enabled by remember(s.id) { mutableStateOf(s.enabled) }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceHigh)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = label,
                                onValueChange = { label = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Accent,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }

                        TextField(
                            value = lists,
                            onValueChange = { lists = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp, color = Accent, fontFamily = FontFamily.Monospace
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            placeholder = { Text("списки через пробел", fontSize = 12.sp, color = Muted) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    execScript(
                                        Forkop.saveSectionScript(
                                            s.id, label, enabled,
                                            lists.split(" ", ",").map { it.trim() }.filter { it.isNotEmpty() }
                                        )
                                    ) { /* сохранено */ }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Accent)
                            ) { Text("Сохранить", fontSize = 12.sp) }

                            Button(
                                onClick = {
                                    execScript(Forkop.deleteSectionScript(s.id)) { /* удалено */ }
                                    execScript(Forkop.sectionsScript()) { raw ->
                                        val (rules, links) = Forkop.parseSections(raw)
                                        sections = rules
                                        linksAll = links
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHigh, contentColor = Bad)
                            ) { Text("Удалить", fontSize = 12.sp, color = Bad) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                sectionsError?.let { Text(it, fontSize = 12.sp, color = Bad) }
            }
        }

        item {
            SectionLabel("Ссылки-подключения")
            CardBox {
                linksAll.forEach { l ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(l.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                        Text(
                            l.uri.take(70) + if (l.uri.length > 70) "…" else "",
                            fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (linksAll.isEmpty()) Text("—", fontSize = 13.sp, color = Muted)
            }
        }
    }
}

/** Порядок секций для теста: как в дашборде — сверху вниз. */
private fun groupsOrder(sections: List<Forkop.SectionData>, each: (String) -> Unit) {
    sections.forEach { each(it.id) }
}

private fun fmtBytes(b: Long): String {
    val v = b.toDouble()
    return when {
        v >= 1_000_000_000 -> String.format("%.2f ГБ", v / 1_000_000_000)
        v >= 1_000_000 -> String.format("%.2f МБ", v / 1_000_000)
        v >= 1_000 -> String.format("%.1f КБ", v / 1_000)
        else -> "$b Б"
    }
}
