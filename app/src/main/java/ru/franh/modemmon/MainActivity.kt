package ru.franh.modemmon

import ru.franh.modemmon.util.L10n

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.franh.modemmon.ui.AppDrawer
import ru.franh.modemmon.ui.AtScreen
import ru.franh.modemmon.ui.BackupScreen
import ru.franh.modemmon.ui.BandsScreen
import ru.franh.modemmon.ui.CellScreen
import ru.franh.modemmon.ui.WolScreen
import ru.franh.modemmon.ui.ChannelsScreen
import ru.franh.modemmon.ui.ClientsScreen
import ru.franh.modemmon.ui.ConfigScreen
import ru.franh.modemmon.ui.ForkopScreen
import ru.franh.modemmon.ui.CpuScreen
import ru.franh.modemmon.ui.CrontabScreen
import ru.franh.modemmon.ui.ExecScreen
import ru.franh.modemmon.ui.IfacesScreen
import ru.franh.modemmon.ui.IfaceEditScreen
import ru.franh.modemmon.ui.LedsScreen
import ru.franh.modemmon.ui.LogScreen
import ru.franh.modemmon.ui.ModemDevicesScreen
import ru.franh.modemmon.ui.MenuConfigScreen
import ru.franh.modemmon.ui.MonitorScreen
import ru.franh.modemmon.ui.OverviewScreen
import ru.franh.modemmon.ui.PackagesScreen
import ru.franh.modemmon.ui.PingScreen
import ru.franh.modemmon.ui.ProcessesScreen
import ru.franh.modemmon.ui.RebootScreen
import ru.franh.modemmon.ui.RoutesScreen
import ru.franh.modemmon.ui.ServicesScreen
import ru.franh.modemmon.ui.SettingsScreen
import ru.franh.modemmon.ui.SmsScreen
import ru.franh.modemmon.ui.TermScreen
import ru.franh.modemmon.ui.TrafficScreen
import ru.franh.modemmon.ui.UssdScreen
import ru.franh.modemmon.ui.VpnScreen
import ru.franh.modemmon.ui.WifiScreen
import ru.franh.modemmon.ui.navItem
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bg
import ru.franh.modemmon.ui.theme.ModemMonTheme
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.OnSurface
import ru.franh.modemmon.ui.theme.Surface
import ru.franh.modemmon.util.Metric
import ru.franh.modemmon.util.SignalEval
import ru.franh.modemmon.vm.ModemViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ModemMonTheme {
                App()
            }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

/** Вкладки прежнего приложения: живут вместе на одном экране внутри панели. */
// Функция (не val) — чтобы подписи пересчитывались при смене языка.
private fun tabs() = listOf(
    Tab(L10n.s("Монитор", "Monitor"), Icons.Filled.Home),
    Tab(L10n.s("Сота", "Cell"), Icons.Filled.Place),
    Tab(L10n.s("Трафик", "Traffic"), Icons.Filled.List)
)

@Composable
private fun LegacyTabs(state: ru.franh.modemmon.vm.UiState, vm: ModemViewModel, now: Long) {
    var tab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> MonitorScreen(state, vm, now)
                1 -> CellScreen(state)
                else -> TrafficScreen(state)
            }
        }
        NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
            tabs().forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(t.icon, contentDescription = t.title) },
                    label = { Text(t.title, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent,
                        selectedTextColor = Accent,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted,
                        indicatorColor = Accent.copy(alpha = 0.12f)
                    )
                )
            }
        }
    }
}@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val vm: ModemViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("app") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Звук-парктроник: чем ближе к пику, тем чаще одинаковый писк + вибрация.
    val latest by rememberUpdatedState(state)
    val context = LocalContext.current
    LaunchedEffect(state.settings.sound, state.settings.vibrate, state.settings.metric) {
        if (!state.settings.sound && !state.settings.vibrate) return@LaunchedEffect
        val tg = if (state.settings.sound) try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (_: Exception) {
            null
        } else null
        val vibrator = if (state.settings.vibrate) try {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) {
            null
        } else null
        val hasVibrator = vibrator?.hasVibrator() == true
        var smoothedInterval = 1000.0
        try {
            while (isActive) {
                val s = latest
                val metric = Metric.byKey(s.settings.metric)
                val v = vm.valueOf(s.snap, metric)
                if (v == null) {
                    delay(800)
                    continue
                }
                val pct = SignalEval.percent(metric, v).coerceIn(0f, 100f)
                // Целевой интервал: 1000 мс при 0 %, 120 мс при 100 %
                val target = 1000.0 - pct * 8.8
                // EMA-сглаживание (коэффициент 0.25) — плавный переход без рывков
                smoothedInterval += (target - smoothedInterval) * 0.25
                val interval = smoothedInterval.toLong().coerceIn(120, 1200)
                if (state.settings.sound && tg != null) {
                    try {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                    } catch (_: Exception) {}
                }
                if (state.settings.vibrate && hasVibrator) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    25,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        } else {
                            vibrator.vibrate(25)
                        }
                    } catch (_: Exception) {}
                }
                delay(interval)
            }
        } finally {
            try {
                tg?.release()
            } catch (_: Exception) {}
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(current, hidden = state.menu.hidden) { id ->
                current = id
                scope.launch { drawerState.close() }
            }
        }
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                val slots = state.menu.bottom.filter { it.isNotBlank() }
                // На экране «app» своя нижняя панель (Монитор/Сота/Трафик) — не дублируем.
                if (slots.isNotEmpty() && current != "app") {
                    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                        slots.forEach { id ->
                            val item = navItem(id)
                            NavigationBarItem(
                                selected = current == id,
                                onClick = { current = id },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = {
                                    Text(
                                        item.title,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = Accent,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                    indicatorColor = Accent.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                current.startsWith("ifaceedit:") ->
                                    L10n.s("Изменить · ", "Edit · ") + current.removePrefix("ifaceedit:")
                                current.startsWith("ifacecfg:") ->
                                    L10n.s("Параметры · ", "Options · ") + current.removePrefix("ifacecfg:")
                                else -> navItem(current).title
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = L10n.s("Меню", "Menu"))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Bg,
                        titleContentColor = OnSurface,
                        navigationIconContentColor = Accent
                    )
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (current) {
                    "app" -> LegacyTabs(state, vm, now)
                    "cell" -> CellScreen(state)

                    "overview" -> OverviewScreen(state, vm)
                    "routes" -> ExecScreen(
                        state, vm, L10n.s("Маршруты", "Routes"), "/sbin/ip", listOf("route", "show"),
                        L10n.s("Таблица маршрутизации ядра", "Kernel routing table")
                    )
                    "statusfw" -> ExecScreen(
                        state, vm, L10n.s("Firewall (статус)", "Firewall (status)"), "/sbin/fw4", listOf("print"),
                        L10n.s("Действующие правила nftables", "Active nftables rules")
                    )
                    "channels" -> ChannelsScreen(state, vm)
                    "wol" -> WolScreen(state, vm)
                    "menucfg" -> MenuConfigScreen(state, vm)
                    "awg" -> VpnScreen(state, vm, "awg", "AmneziaWG")
                    "wg" -> VpnScreen(state, vm, "wg", "WireGuard")
                    "log" -> LogScreen(state, vm)
                    "procs" -> ProcessesScreen(state, vm)

                    "system" -> ConfigScreen(state, vm, "system", L10n.s("Система", "System"))
                    "term" -> TermScreen(state, vm)
                    "cron" -> CrontabScreen(state, vm)
                    "leds" -> LedsScreen(state, vm)
                    "cpu" -> CpuScreen(state, vm)
                    "backup" -> BackupScreen(state, vm)
                    "reboot" -> RebootScreen(state, vm)

                    "modem" -> ModemDevicesScreen(state, vm)
                    "bands" -> BandsScreen(state, vm)
                    "at" -> AtScreen(state, vm)
                    "sms" -> SmsScreen(state, vm)
                    "ussd" -> UssdScreen(state, vm)
                    "modemcfg" -> ConfigScreen(state, vm, "medvemodem", L10n.s("Настройки MedveModem", "MedveModem settings"))

                    "ifaces" -> IfacesScreen(state, vm) { name -> current = "ifaceedit:$name" }
                    "wifi" -> WifiScreen(state, vm)
                    "nroutes" -> RoutesScreen(state, vm)
                    "clients" -> ClientsScreen(state, vm)
                    "dns" -> ConfigScreen(state, vm, "dhcp", L10n.s("DHCP и DNS", "DHCP & DNS"))
                    "ping" -> PingScreen(state, vm)
                    "nfw" -> ConfigScreen(state, vm, "firewall", "Firewall")

                    "forkop" -> ForkopScreen(state, vm)
                    "travelmate" -> ConfigScreen(
                        state, vm, "travelmate", "Travelmate",
                        onlySection = "global", service = "travelmate"
                    )

                    else -> when {
                        current.startsWith("ifaceedit:") -> {
                            val name = current.removePrefix("ifaceedit:")
                            IfaceEditScreen(
                                state, vm, name,
                                onRaw = { current = "ifacecfg:$name" },
                                onBack = { current = "ifaces" }
                            )
                        }
                        current.startsWith("ifacecfg:") -> {
                            val name = current.removePrefix("ifacecfg:")
                            ConfigScreen(
                                state, vm, "network", L10n.s("Интерфейс $name", "Interface $name"),
                                onlySection = name
                            )
                        }
                        else -> SettingsScreen(state, vm)
                    }
                }
            }
        }
    }
}
