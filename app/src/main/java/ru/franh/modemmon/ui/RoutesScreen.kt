package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import ru.franh.modemmon.data.Routes
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

@Composable
fun RoutesScreen(state: UiState, vm: ModemViewModel) {
    val r = state.routes

    LaunchedEffect(state.ubus.connected) {
        if (state.ubus.connected && r.routes.isEmpty()) vm.loadRoutes()
    }

    ScreenShell(connected = state.ubus.connected) {
        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Маршруты · ${r.routes.size}")
                TextButton(onClick = { vm.loadRoutes() }, enabled = !r.loading) {
                    Text(if (r.loading) "Читаю…" else "Обновить", fontSize = 12.sp, color = Accent)
                }
            }
            r.error?.let { Text(it, color = Bad, fontSize = 12.sp) }
        }

        val defaults = r.routes.filter { it.isDefault }
        val others = r.routes.filterNot { it.isDefault }

        if (defaults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                SectionLabel("Шлюзы по умолчанию")
                Text(
                    "Через них уходит трафик. Меньше метрика — выше приоритет.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                defaults.forEachIndexed { i, route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceHigh)
                            .border(
                                1.dp,
                                if (i == 0) Good.copy(alpha = 0.5f) else Outline,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "default",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (i == 0) {
                                    Spacer(Modifier.padding(4.dp))
                                    Badge("основной", Good)
                                }
                            }
                            Text(
                                buildString {
                                    route.via?.let { append("via $it") }
                                    route.dev?.let { if (isNotEmpty()) append(" · "); append(it) }
                                },
                                color = Muted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        route.metric?.let {
                            Badge("m$it", Accent)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }

        if (others.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                SectionLabel("Прочие · ${others.size}")
                others.forEach { route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            route.dest,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        route.dev?.let {
                            Spacer(Modifier.padding(3.dp))
                            Badge(it, Accent)
                        }
                        route.metric?.let {
                            Spacer(Modifier.padding(3.dp))
                            Badge("m$it", Muted)
                        }
                    }
                }
            }
        }

        if (r.routes.isEmpty() && !r.loading) {
            Spacer(Modifier.height(8.dp))
            CardBox { Text("Маршрутов нет", color = Muted, fontSize = 12.sp) }
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
