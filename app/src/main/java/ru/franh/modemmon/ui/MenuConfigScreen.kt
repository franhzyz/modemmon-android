package ru.franh.modemmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bg
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.OnSurface
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.ModemViewModel
import ru.franh.modemmon.vm.UiState

/**
 * Настройка меню — как в RouteriCh:
 *  • НИЖНЯЯ ПАНЕЛЬ — 4 слота быстрого доступа (нажми слот → выбери экран);
 *  • БОКОВОЕ МЕНЮ — какие разделы показывать (галочка = скрыть/показать).
 * Изменения копятся в черновике и применяются по кнопке «Сохранить».
 */
@Composable
fun MenuConfigScreen(state: UiState, vm: ModemViewModel) {
    LaunchedEffect(Unit) { vm.loadMenu() }

    val applied = state.menu
    var hidden by remember(applied.hidden) { mutableStateOf(applied.hidden) }
    var bottom by remember(applied.bottom) { mutableStateOf(applied.bottom) }
    var pickSlot by remember { mutableStateOf(-1) }

    val items = NAV_SECTIONS.flatMap { it.items }
    val titleOf: (String) -> String = { id -> items.firstOrNull { it.id == id }?.title ?: id }

    ScreenShell(connected = true) {
        CardBox {
            SectionLabel("Настройка меню")
            Text(
                "Выбери, что показывать в нижней панели и боковом меню. " +
                    "Отключённый экран скрывается из обоих меню.",
                fontSize = 12.sp,
                color = Muted,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(14.dp))

            // ---------- НИЖНЯЯ ПАНЕЛЬ ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("НИЖНЯЯ ПАНЕЛЬ", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${bottom.count { it.isNotBlank() }} / 4",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { slot ->
                    val id = bottom.getOrNull(slot).orEmpty()
                    SlotChip(
                        title = if (id.isBlank()) "—" else titleOf(id),
                        filled = id.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = { pickSlot = slot }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Нажми на кнопку, чтобы выбрать открываемый экран.",
                fontSize = 11.sp,
                color = Muted
            )

            Spacer(Modifier.height(18.dp))

            // ---------- БОКОВОЕ МЕНЮ ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("БОКОВОЕ МЕНЮ", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${items.size} / ${items.size}",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Сними галочку, чтобы скрыть раздел из бокового меню.",
                fontSize = 11.sp,
                color = Muted
            )
            Spacer(Modifier.height(8.dp))

            NAV_SECTIONS.forEach { section ->
                Text(
                    section.title.uppercase(),
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                section.items.forEach { item ->
                    if (item.id == "menucfg") return@forEach
                    val shown = item.id !in hidden
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hidden = if (shown) hidden + item.id else hidden - item.id }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CheckMark(shown)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.title,
                            fontSize = 14.sp,
                            color = if (shown) OnSurface else Muted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    hidden = emptySet()
                    bottom = ru.franh.modemmon.vm.MenuUiState.DEFAULT_BOTTOM
                }) {
                    Text("Сбросить", color = Muted)
                }
                TextButton(
                    onClick = {
                        val clean = bottom.map { it.trim() }.filter { it.isNotBlank() }.take(4)
                        vm.applyMenu(hidden, clean)
                    }
                ) {
                    Text("Сохранить", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Диалог выбора экрана для слота нижней панели.
    if (pickSlot >= 0) {
        AlertDialog(
            onDismissRequest = { pickSlot = -1 },
            containerColor = Bg,
            title = { Text("Экран для кнопки ${pickSlot + 1}", color = OnSurface, fontSize = 16.sp) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    items.filter { it.id != "menucfg" }.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val list = bottom.toMutableList()
                                    while (list.size < 4) list.add("")
                                    list[pickSlot] = item.id
                                    bottom = list
                                    pickSlot = -1
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.title, fontSize = 14.sp, color = OnSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickSlot = -1 }) { Text("Отмена", color = Muted) }
            }
        )
    }
}

@Composable
private fun SlotChip(
    title: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Accent.copy(alpha = 0.14f) else SurfaceHigh)
            .border(
                1.dp,
                if (filled) Accent.copy(alpha = 0.5f) else Outline,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            fontSize = 12.sp,
            color = if (filled) Accent else Muted,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2
        )
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Accent.copy(alpha = 0.16f) else SurfaceHigh)
            .border(
                1.dp,
                if (checked) Accent.copy(alpha = 0.6f) else Outline,
                RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Text("✓", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
    }
}
