package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import ru.franh.modemmon.util.L10n
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/** Клиент в локальной сети: DHCP-аренда и/или ARP-запись и/или Wi-Fi-ассоциация. */
data class MmClient(
    val mac: String,
    val ips: List<String>,
    val host: String?,
    val iface: String?,
    val dhcp: Boolean,
    val wifi: Boolean
)

/**
 * Все клиенты локальной сети (как «Аренды DHCP» в LuCI, но шире):
 * DHCP-аренды + ARP-таблица + Wi-Fi-ассоциации, склеенные по MAC.
 */
@Composable
fun ClientsCard(state: UiState, vm: ModemViewModel) {
    var clients by remember { mutableStateOf(listOf<MmClient>()) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        busy = true
        val script =
            "echo \"[leases]\"; cat /tmp/dhcp.leases 2>/dev/null | awk '{print \$2\" \"\$3\" \"\$4}'; " +
            "echo \"[arp]\"; ip neigh show 2>/dev/null | awk '\$0 ~ /lladdr/ {ip=\$1; dev=\$3; mac=\"\"; for(i=1;i<=NF;i++) if(\$i==\"lladdr\") mac=\$(i+1); print mac\" \"\$1\" \"\$3; exit 0'; " +
            "echo \"[wifi]\"; for d in \$(iwinfo 2>/dev/null | awk '/ESSID/{print \$1}'); do iwinfo \"\$d\" assoclist 2>/dev/null | awk -v d=\"\$d\" '/^[0-9A-Fa-f][0-9A-Fa-f]:/{print \$1\" \"d}'; done; exit 0"
        val req = JSONObject().put("command", "/bin/sh").put(
            "params", JSONArray().put("-c").put(script)
        )
        vm.ubusCall("file", "exec", req) { reply ->
            busy = false
            val out = reply.data?.optString("stdout").orEmpty()
            val lease = mutableMapOf<String, Pair<String, String>>() // mac -> ip, host
            val arp = mutableMapOf<String, Pair<MutableSet<String>, String>>() // mac -> ips, dev
            val wifi = mutableMapOf<String, String>() // mac -> dev
            var sec = ""
            out.lines().forEach { raw ->
                val line = raw.trim()
                when {
                    line == "[leases]" -> sec = "leases"
                    line == "[arp]" -> sec = "arp"
                    line == "[wifi]" -> sec = "wifi"
                    line.isBlank() -> {}
                    sec == "leases" -> {
                        val p = line.split(" ")
                        if (p.size >= 2) lease[p[0].uppercase()] = p[1] to p.getOrElse(2) { "" }.removeSuffix(".lan")
                    }
                    sec == "arp" -> {
                        val p = line.split(" ")
                        if (p.size >= 2) {
                            val cur = arp[p[0].uppercase()] ?: Pair(mutableSetOf<String>(), "")
                            cur.first.add(p[1])
                            arp[p[0].uppercase()] = cur.first to p.getOrElse(2) { "" }
                        }
                    }
                    sec == "wifi" -> {
                        val p = line.split(" ")
                        if (p.size >= 1) wifi[p[0].uppercase()] = p.getOrElse(1) { "" }
                    }
                }
            }
            val macs = (lease.keys + arp.keys + wifi.keys).distinct()
            clients = macs.map { m ->
                val l = lease[m]
                val a = arp[m]
                val ips = ((a?.first ?: emptySet()) + listOfNotNull(l?.first)).filter { it.isNotBlank() }.distinct()
                MmClient(
                    mac = m,
                    ips = ips,
                    host = l?.second?.takeIf { it.isNotBlank() && it != "*" },
                    iface = wifi[m]?.takeIf { it.isNotBlank() } ?: a?.second?.takeIf { it.isNotBlank() },
                    dhcp = l != null,
                    wifi = wifi.containsKey(m)
                )
            }.sortedWith(compareBy({ it.host == null }, { it.host ?: it.ips.firstOrNull() ?: it.mac }))
        }
    }

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && clients.isEmpty()) load()
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(L10n.s("Клиенты в сети", "Network clients"))
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            TextButton(onClick = { load() }, enabled = !busy) {
                Text(L10n.s("Обновить", "Refresh"), fontSize = 12.sp, color = Accent)
            }
        }

        if (clients.isEmpty() && !busy) {
            Text(L10n.s("Клиентов нет", "No clients"), fontSize = 12.sp, color = Muted)
        }

        clients.forEach { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceHigh)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.host ?: c.ips.firstOrNull() ?: c.mac,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        modifier = Modifier.weight(1f)
                    )
                    if (c.wifi) {
                        Text(
                            "Wi-Fi",
                            fontSize = 10.sp,
                            color = Good,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceHigh)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        c.iface ?: "",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Muted
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    c.ips.joinToString(", ").ifBlank { "—" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted
                )
                Text(
                    c.mac,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Muted
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Text(
            L10n.s("DHCP-аренды + ARP-таблица + Wi-Fi-ассоциации, склеены по MAC.", "DHCP leases + ARP table + Wi-Fi associations, merged by MAC."),
            fontSize = 10.sp,
            color = Muted
        )
    }
}
