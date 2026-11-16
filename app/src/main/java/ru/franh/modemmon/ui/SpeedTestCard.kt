package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
 * Спидтест через роутер — Яндекс.Интернетометр (yandex.ru/internet API).
 * Замер запускается В ФОНЕ на роутере (результат в /tmp/mm_speed_result),
 * приложение опрашивает статус раз в 2 с — короткие вызовы не упираются
 * в таймаут ubus (8 с). DNS-фейки forkop обходятся через --resolve с
 * настоящим IPv4, поэтому замер идёт напрямую по выбранному каналу.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedTestCard(state: UiState, vm: ModemViewModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var downMbps by remember { mutableStateOf<Double?>(null) }
    var upMbps by remember { mutableStateOf<Double?>(null) }
    var pingMs by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var iface by remember { mutableStateOf("") } // "" = авто (маршрут по умолчанию)
    var ifaces by remember { mutableStateOf(listOf<String>()) }

    // Список аплинков роутера: pppoe*/wan* — кабель, wwan*/usb*/qmi* — модем
    LaunchedEffect(Unit) {
        val listReq = JSONObject().put("command", "/bin/sh").put(
            "params", JSONArray().put("-c").put(
                "ip -o link show | awk -F': ' '{print \$2}' | " +
                "grep -E '^(pppoe|wwan|usb|qmi|wan)' | grep -v '@'; exit 0"
            )
        )
        vm.ubusCall("file", "exec", listReq) { reply ->
            val names = reply.data?.optString("stdout").orEmpty()
                .trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (names.isNotEmpty()) ifaces = names.distinct().take(4)
        }
    }

    fun ifaceLabel(n: String): String = when {
        n.startsWith("wwan") || n.startsWith("qmi") -> "Модем ($n)"
        n.startsWith("usb") || n.startsWith("pppoe") || n.contains("wan") -> "Кабель ($n)"
        else -> n
    }

    fun runTest() {
        busy = true
        downMbps = null; upMbps = null; pingMs = null; error = null
        phase = "Запуск…"

        val safeIface = iface.replace(Regex("[^a-zA-Z0-9._-]"), "")
        val bind = if (safeIface.isBlank()) "" else "--interface $safeIface "

        val script =
            "rm -f /tmp/mm_speed_phase /tmp/mm_speed_result; " +
            "{ " +
            "echo \"down\" > /tmp/mm_speed_phase; " +
            "j=\$(curl -s -m 8 -H 'Referer: https://yandex.ru/internet/' " +
            "\"https://yandex.ru/internet/api/v0/get-probes?t=\$(date +%s000)\"); " +
            "durl=\$(echo \"\$j\" | grep -o 'https://[^\"]*probes/50mb[^\"]*' | head -1); " +
            "uurl=\$(echo \"\$j\" | grep -o 'https://[^\"]*upload[^\"]*' | head -1); " +
            "purl=\$(echo \"\$j\" | grep -o 'https://[^\"]*/ping[^\"]*' | head -1); " +
            "if [ -z \"\$durl\" ]; then echo err > /tmp/mm_speed_result; exit 0; fi; " +
            // res(): настоящий IPv4 — nslookup, при неудаче DoH 1.1.1.1 (DNS на роутере бывает нестабилен)
            "res() { r=\$(nslookup \"\$1\" 77.88.8.8 2>/dev/null | awk '/Address/{print \$NF}' | " +
            "grep -E '^[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+\$' | head -1); " +
            "[ -n \"\$r\" ] && { echo \"\$r\"; return; }; " +
            "r=\$(curl -s -m 6 \"https://1.1.1.1/dns-query?name=\$1&type=A\" -H 'accept: application/dns-json' | " +
            "grep -o '\"data\":\"[0-9.]*\"' | head -1 | cut -d'\"' -f4); " +
            "[ -n \"\$r\" ] && { echo \"\$r\"; return; }; }; " +
            "dhost=\$(echo \"\$durl\" | sed -e 's|https://||' -e 's|/.*||'); " +
            "uhost=\$(echo \"\$uurl\" | sed -e 's|https://||' -e 's|/.*||'); " +
            "phost=\$(echo \"\$purl\" | sed -e 's|https://||' -e 's|/.*||'); " +
            "dip=\$(res \"\$dhost\"); uip=\$(res \"\$uhost\"); pip=\$(res \"\$phost\"); " +
            // --resolve только если IP получен; иначе обычный путь (fake-ip через Clash)
            "dargs=\"\"; [ -n \"\$dip\" ] && dargs=\"--resolve \$dhost:443:\$dip\"; " +
            "uargs=\"\"; [ -n \"\$uip\" ] && uargs=\"--resolve \$uhost:443:\$uip\"; " +
            "pargs=\"\"; [ -n \"\$pip\" ] && pargs=\"--resolve \$phost:443:\$pip\"; " +
            "d=\$(curl -s -o /dev/null --max-time 10 " + bind + "\$dargs " +
            "-w '%{speed_download}' \"\$durl&rid=mm\$(date +%s)\" 2>/dev/null); " +
            "echo \"up\" > /tmp/mm_speed_phase; " +
            "head -c 10485760 /dev/zero > /tmp/mm_up.bin 2>/dev/null; " +
            "u=\$(curl -s -o /dev/null --max-time 15 " + bind + "\$uargs " +
            "-X POST -H 'Content-Type: application/octet-stream' " +
            "--data-binary @/tmp/mm_up.bin -w '%{speed_upload}' " +
            "\"\$uurl&rid=mm\$(date +%s)\" 2>/dev/null); " +
            "rm -f /tmp/mm_up.bin; " +
            "echo \"ping\" > /tmp/mm_speed_phase; " +
            "p=\$(curl -s -o /dev/null -m 3 " + bind + "\$pargs " +
            "-w '%{time_starttransfer}' \"\$purl&rid=mm1\" 2>/dev/null); " +
            "echo \"\$d \$u \$p\" > /tmp/mm_speed_result; " +
            "} >/dev/null 2>&1 </dev/null & echo started"

        val startReq = JSONObject().put("command", "/bin/sh").put(
            "params", JSONArray().put("-c").put(script)
        )

        vm.ubusCall("file", "exec", startReq) { reply ->
            if (!reply.ok) {
                error = reply.error
                phase = "ошибка"
                busy = false
                return@ubusCall
            }
            // Опрос: короткие exec-вызовы раз в 2 с, до ~50 с
            scope.launch {
                var attempts = 0
                while (busy && attempts < 25) {
                    delay(2000)
                    attempts++
                    val pollReq = JSONObject().put("command", "/bin/sh").put(
                        "params", JSONArray().put("-c").put(
                            "cat /tmp/mm_speed_phase 2>/dev/null; echo ---; " +
                            "cat /tmp/mm_speed_result 2>/dev/null; exit 0"
                        )
                    )
                    vm.ubusCall("file", "exec", pollReq) { r ->
                        val out = r.data?.optString("stdout").orEmpty()
                        val idx = out.indexOf("---")
                        val ph = (if (idx >= 0) out.substring(0, idx) else out).trim()
                        val res = (if (idx >= 0) out.substring(idx + 3) else "").trim()
                        if (ph.isNotEmpty()) phase = when (ph) {
                            "down" -> "Замер скачивания…"
                            "up" -> "Замер отдачи…"
                            "ping" -> "Замер пинга…"
                            else -> ph
                        }
                        if (res.isNotEmpty()) {
                            if (res == "err") {
                                error = "Роутер не получил серверы замера"
                            } else {
                                val pr = res.trim().split(Regex("\\s+"))
                                downMbps = pr.getOrNull(0)?.toDoubleOrNull()?.let { it * 8 / 1_000_000 }
                                upMbps = pr.getOrNull(1)?.toDoubleOrNull()?.let { it * 8 / 1_000_000 }
                                pingMs = pr.getOrNull(2)?.toDoubleOrNull()?.let { it * 1000 }
                            }
                            busy = false
                        }
                    }
                }
                if (busy) {
                    error = "Не дождались результата замера (таймаут)"
                    busy = false
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Спидтест (Яндекс)")
            Spacer(Modifier.weight(1f))
            if (busy) Text(phase, fontSize = 12.sp, color = Muted)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { iface = "" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (iface.isEmpty()) Accent else SurfaceHigh,
                    contentColor = if (iface.isEmpty()) Color.Black else Accent
                )
            ) { Text("Авто", fontSize = 12.sp) }
            ifaces.forEach { n ->
                Button(
                    onClick = { iface = n },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (iface == n) Accent else SurfaceHigh,
                        contentColor = if (iface == n) Color.Black else Accent
                    )
                ) { Text(ifaceLabel(n), fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { runTest() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Запустить спидтест", fontWeight = FontWeight.SemiBold) }

        if (downMbps != null || upMbps != null || pingMs != null) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceHigh)
                    .padding(10.dp)
            ) {
                ResultRow("Скачивание", downMbps?.let { "%.1f Мбит/с".format(it) }, Good)
                Spacer(Modifier.height(4.dp))
                ResultRow("Отдача", upMbps?.let { "%.1f Мбит/с".format(it) }, Accent)
                pingMs?.let {
                    Spacer(Modifier.height(4.dp))
                    ResultRow("Пинг", "%.0f мс".format(it), Accent)
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 12.sp, color = Bad)
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Замер Яндекс.Интернетометра с роутера по выбранному каналу. ~20-40 с.",
            fontSize = 11.sp, color = Muted
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String?, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Muted, modifier = Modifier.width(110.dp))
        Text(
            value ?: "—",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
