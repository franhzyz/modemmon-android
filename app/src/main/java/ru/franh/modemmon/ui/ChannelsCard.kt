package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/**
 * Переключение между модемами: какой канал держит маршрут по умолчанию
 * (основной / резервный). Переключение — через uci-метрики интерфейсов
 * (network.<sec>.metric 90/110) + reload: демон medvemodem следует за
 * метриками и автопереключение продолжает работать. Перезвон меняющегося
 * канала занимает ~20-40 с.
 */
@Composable
fun ChannelsCard(state: UiState, vm: ModemViewModel) {
    var channels by remember { mutableStateOf(listOf<Pair<Int, String>>()) } // (metric, device)
    var switching by remember { mutableStateOf<String?>(null) }             // целевое устройство
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    val statusReq = JSONObject().put("command", "/bin/sh").put(
        "params", JSONArray().put("-c").put(
            "ip route show default | while read -r line; do " +
            "dev=\$(echo \"\$line\" | sed -n \"s/.* dev \\\\([^ ]*\\\\).*/\\\\1/p\"); " +
            "met=\$(echo \"\$line\" | sed -n \"s/.* metric \\\\([0-9]*\\\\).*/\\\\1/p\"); " +
            "echo \"\${met:-0} \$dev\"; done | sort -n; exit 0"
        )
    )

    fun load() {
        vm.ubusCall("file", "exec", statusReq) { reply ->
            if (!reply.ok) return@ubusCall
            val list = reply.data?.optString("stdout").orEmpty()
                .trim().split("\n")
                .mapNotNull { l ->
                    val p = l.trim().split(Regex("\\s+"))
                    val m = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val d = p.getOrNull(1) ?: return@mapNotNull null
                    m to d
                }
                .sortedBy { it.first }
            if (list.isNotEmpty()) {
                channels = list
                loaded = true
            }
        }
    }

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected) load()
    }

    // Опрос после запуска переключения: ждём смену основного канала
    LaunchedEffect(switching) {
        val target = switching ?: return@LaunchedEffect
        var waited = 0
        while (waited < 90 && switching != null) {
            delay(3000)
            waited += 3
            load()
            if (channels.firstOrNull()?.second == target) {
                switching = null
            }
        }
        if (switching != null && waited >= 90) {
            error = "Переключение затянулось — проверь статус каналов"
            switching = null
        }
    }

    fun switchTo(dev: String) {
        val safe = dev.replace(Regex("[^a-zA-Z0-9._-]"), "")
        error = null
        switching = dev
        val script =
            "TARGET=$safe; j=\$(ubus call network.interface dump); " +
            "for d in \$(ip route show default | sed -n \"s/.* dev \\\\([^ ]*\\\\).*/\\\\1/p\" | sort -u); do " +
            "s=\$(jsonfilter -s \"\$j\" -e \"@.interface[@.l3_device=\\\"\$d\\\"].interface\" | head -1); " +
            "[ -n \"\$s\" ] || continue; " +
            "if [ \"\$d\" = \"\$TARGET\" ]; then uci set network.\$s.metric=\"90\"; " +
            "else uci set network.\$s.metric=\"110\"; fi; done; " +
            "uci commit network; /etc/init.d/network reload >/dev/null 2>&1; echo switched"
        val req = JSONObject().put("command", "/bin/sh").put(
            "params", JSONArray().put("-c").put(script)
        )
        vm.ubusCall("file", "exec", req) { reply ->
            if (!reply.ok) {
                error = reply.error
                switching = null
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Переключение модемов")
            Spacer(Modifier.weight(1f))
            if (switching != null) {
                Text(
                    "Переключаю на ${devLabel(switching!!)}… (~20-40 с)",
                    fontSize = 12.sp, color = Muted
                )
            }
        }

        if (!loaded) {
            Text("Загрузка…", fontSize = 12.sp, color = Muted)
        } else {
            channels.forEachIndexed { idx, (metric, dev) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        devLabel(dev),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (idx == 0) "основной" else "резерв",
                        fontSize = 11.sp,
                        color = if (idx == 0) Good else Muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (idx != 0) {
                        Button(
                            onClick = { switchTo(dev) },
                            enabled = switching == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color.Black
                            )
                        ) { Text("Сделать основным", fontSize = 11.sp) }
                    }
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 12.sp, color = Bad)
        }

        Text(
            "Переключение меняет приоритет каналов (метрики). Автопереключение при " +
                "пропадании интернета продолжает работать. Канал при смене перезванивает.",
            fontSize = 11.sp, color = Muted
        )
    }
}

internal fun devLabel(dev: String): String = when {
    dev.startsWith("wwan") || dev.startsWith("qmi") -> "Модем LTE ($dev)"
    dev.startsWith("usb") -> "USB-модем ($dev)"
    dev.startsWith("pppoe") || dev.contains("wan") || dev.startsWith("eth") -> "Кабель ($dev)"
    else -> dev
}
