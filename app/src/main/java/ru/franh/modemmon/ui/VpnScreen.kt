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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.data.Vpn
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/**
 * Экран туннелей. Один и тот же для WireGuard и AmneziaWG —
 * различие только в источнике данных.
 */
@Composable
fun VpnScreen(
    state: UiState,
    vm: ModemViewModel,
    kind: String,
    title: String
) {
    val v = state.vpn

    LaunchedEffect(kind, state.ubus.connected) {
        if (state.ubus.connected && (v.kind != kind || v.instances.isEmpty())) vm.loadVpn(kind)
    }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(title)
                TextButton(onClick = { vm.loadVpn(kind) }, enabled = !v.loading) {
                    Text(if (v.loading) "Читаю…" else "Обновить", fontSize = 12.sp, color = Accent)
                }
            }
            v.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
            if (v.instances.isEmpty() && !v.loading && v.error == null) {
                Text("Туннелей нет", color = Muted, fontSize = 12.sp)
            }
        }

        v.instances.forEach { inst ->
            Spacer(Modifier.height(8.dp))
            InstanceCard(inst)
        }
    }
}

@Composable
private fun InstanceCard(inst: Vpn.Instance) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (inst.running) Good else Outline)
            )
            Spacer(Modifier.width(10.dp))
            Text(inst.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Badge(if (inst.running) "активен" else "выключен", if (inst.running) Good else Muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "порт ${inst.listenPort} · ${inst.peers.size} пир(ов)",
            color = Muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        if (inst.publicKey.isNotBlank()) {
            Text(
                "ключ ${inst.publicKey.take(16)}…",
                color = Muted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        inst.peers.forEach { peer ->
            Spacer(Modifier.height(10.dp))
            PeerCard(peer)
        }
    }
}

@Composable
private fun PeerCard(peer: Vpn.Peer) {
    val age = if (peer.handshake > 0)
        (System.currentTimeMillis() / 1000 - peer.handshake).coerceAtLeast(0) else -1L

    val statusColor = when {
        age < 0 -> Muted
        age < 180 -> Good
        age < 600 -> Warn
        else -> Bad
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                peer.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                if (age < 0) "нет связи" else "рукопожатие ${fmtAge(age)}",
                fontSize = 10.sp,
                color = statusColor
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(peer.endpoint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        if (peer.allowedIps.isNotEmpty()) {
            Text(
                peer.allowedIps.joinToString(", "),
                fontSize = 11.sp,
                color = Muted,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("↓ ${fmtBytes(peer.rx)}", color = Good, fontSize = 12.sp)
                Text("принято", color = Muted, fontSize = 10.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("↑ ${fmtBytes(peer.tx)}", color = Accent, fontSize = 12.sp)
                Text("передано", color = Muted, fontSize = 10.sp)
            }
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

private fun fmtAge(sec: Long): String = when {
    sec < 60 -> "$sec с назад"
    sec < 3600 -> "${sec / 60} мин назад"
    sec < 86400 -> "${sec / 3600} ч назад"
    else -> "${sec / 86400} дн назад"
}

private fun fmtBytes(b: Long): String {
    if (b <= 0) return "0"
    val units = listOf("Б", "КиБ", "МиБ", "ГиБ", "ТиБ")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$b ${units[0]}" else String.format("%.1f %s", v, units[i])
}
