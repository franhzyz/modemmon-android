package ru.franh.modemmon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bg
import ru.franh.modemmon.ui.theme.Muted

/**
 * Пункт панели.
 * [luci] — путь в LuCI для разделов, которые в приложении ещё не реализованы.
 */
data class NavItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val luci: String? = null
)

data class NavSection(val title: String, val items: List<NavItem>)

/**
 * Структура повторяет меню LuCI на роутере (пять групп со скриншотов)
 * плюс наша группа «Приложение».
 * Реализованные экраны открываются внутри приложения, остальные — ссылкой в LuCI.
 */
val NAV_SECTIONS = listOf(
    NavSection(
        "Приложение", listOf(
            NavItem("app", "Модем и сигнал", Icons.Filled.Home)
        )
    ),
    NavSection(
        "Статус", listOf(
            NavItem("overview", "Обзор", Icons.Filled.Info),
            NavItem("log", "Системный журнал", Icons.Filled.List),
            NavItem("procs", "Процессы", Icons.Filled.List),
            NavItem("channels", "Анализ каналов", Icons.Filled.Search),
            NavItem("cell", "Сотовая сеть", Icons.Filled.Place),
            NavItem("awg", "AmneziaWG", Icons.Filled.Lock),
            NavItem("wg", "WireGuard", Icons.Filled.Lock)
        )
    ),
    NavSection(
        "Система", listOf(
            NavItem("system", "Система", Icons.Filled.Info),
            NavItem("term", "Терминал", Icons.Filled.Edit),
            NavItem("cron", "Задачи по расписанию", Icons.Filled.DateRange),
            NavItem("leds", "LED", Icons.Filled.Star),
            NavItem("cpu", "Частота CPU", Icons.Filled.Info),
            NavItem("backup", "Резервная копия", Icons.Filled.Refresh),
            NavItem("reboot", "Перезагрузка", Icons.Filled.Warning)
        )
    ),
    NavSection(
        "Модем", listOf(
            NavItem("modem", "Модемы", Icons.Filled.Build),
            NavItem("bands", "Диапазоны", Icons.Filled.Star),
            NavItem("at", "AT-команды", Icons.Filled.Edit),
            NavItem("sms", "SMS", Icons.Filled.Email),
            NavItem("ussd", "USSD", Icons.Filled.Call),
            NavItem("modemcfg", "Настройки", Icons.Filled.Settings)
        )
    ),
    NavSection(
        "Сеть", listOf(
            NavItem("ifaces", "Интерфейсы", Icons.Filled.Share),
            NavItem("wifi", "Wireless", Icons.Filled.Notifications),
            NavItem("nroutes", "Маршруты", Icons.Filled.Share),
            NavItem("clients", "DHCP", Icons.Filled.Person),
            NavItem("dns", "DNS", Icons.Filled.Search),
            NavItem("ping", "Диагностика", Icons.Filled.Search),
            NavItem("speed", "Speed test", Icons.Filled.PlayArrow),
            NavItem("wol", "Wake-on-LAN", Icons.Filled.PlayArrow),
            NavItem("nfw", "Firewall", Icons.Filled.Lock)
        )
    ),
    NavSection(
        "Службы", listOf(
            NavItem("forkop", "Forkop", Icons.Filled.Share),
            NavItem("travelmate", "Travelmate", Icons.Filled.Send)
        )
    ),
    NavSection(
        "Приложение", listOf(
            NavItem("settings", "Опции", Icons.Filled.Settings),
            NavItem("menucfg", "Настройка меню", Icons.Filled.List)
        )
    )
)

private val ALL_ITEMS = NAV_SECTIONS.flatMap { it.items }

fun navItem(id: String): NavItem =
    ALL_ITEMS.firstOrNull { it.id == id } ?: ALL_ITEMS.first()

@Composable
fun AppDrawer(
    current: String,
    hidden: Set<String> = emptySet(),
    onSelect: (String) -> Unit
) {
    // Раскрыта только та группа, в которой находится текущий экран.
    // Остальные свёрнуты — панель занимает мало места.
    var expanded by remember { mutableStateOf(sectionOf(current)) }

    LaunchedEffect(current) {
        expanded = sectionOf(current)
    }

    // Скрытые разделы убираем из бокового меню (как «отключённые экраны» в RouteriCh).
    val sections = NAV_SECTIONS.map { s -> s.copy(items = s.items.filterNot { it.id in hidden }) }
        .filter { it.items.isNotEmpty() }

    ModalDrawerSheet(drawerContainerColor = Bg) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            Text(
                "ModemMon",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Accent
            )

            sections.forEach { section ->
                val open = section.title == expanded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (open) "" else section.title }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        section.title.uppercase(),
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (open) Accent else Muted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        if (open) "−" else "+",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (open) Accent else Muted
                    )
                }

                if (open) {
                    section.items.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.title, fontSize = 14.sp) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            selected = current == item.id,
                            onClick = { onSelect(item.id) },
                            modifier = Modifier.padding(horizontal = 10.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Accent.copy(alpha = 0.14f),
                                selectedTextColor = Accent,
                                selectedIconColor = Accent,
                                unselectedTextColor = Muted,
                                unselectedIconColor = Muted
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Название группы, в которой лежит экран. */
private fun sectionOf(id: String): String =
    NAV_SECTIONS.firstOrNull { s -> s.items.any { it.id == id } }?.title.orEmpty()
