package ru.franh.modemmon.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import ru.franh.modemmon.data.Actions
import ru.franh.modemmon.data.Creds
import ru.franh.modemmon.data.DemoSource
import ru.franh.modemmon.data.MedveModem
import ru.franh.modemmon.data.ModemApi
import ru.franh.modemmon.data.ModemSnapshot
import ru.franh.modemmon.data.Network
import ru.franh.modemmon.data.Prefs
import ru.franh.modemmon.data.Profiles
import ru.franh.modemmon.data.RouterProfile
import ru.franh.modemmon.data.Routes
import ru.franh.modemmon.data.Uplink
import ru.franh.modemmon.data.Settings
import ru.franh.modemmon.data.SystemInfo
import ru.franh.modemmon.data.Ubus
import ru.franh.modemmon.data.UciConfig
import ru.franh.modemmon.data.Wifi
import ru.franh.modemmon.data.Vpn
import ru.franh.modemmon.util.Metric

enum class Trend { UP, FLAT, DOWN }

/** Состояние канала управления (ubus). */
data class UbusState(
    val busy: Boolean = false,
    val message: String? = null,
    val ok: Boolean = false,
    val connected: Boolean = false
)

/** Состояние экрана «Модем»: список устройств, AT-консоль, диапазоны. */
data class ModemUiState(
    val devices: List<MedveModem.Device> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val radio: MedveModem.RadioInfo? = null,
    val radioError: String? = null,
    /** Черновик выбранных диапазонов; null — редактирование не начато. */
    val bandsDraft: Set<String>? = null,
    val radioBusy: Boolean = false,
    val changeState: String? = null,
    val atOutput: List<String> = emptyList(),
    val atBusy: Boolean = false,
    val history: List<String> = emptyList()
) {
    val selected: MedveModem.Device?
        get() = devices.firstOrNull { it.id == selectedId }
}

/** Состояние SMS и USSD выбранного модема. */
data class SmsUiState(
    val messages: List<MedveModem.Sms> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val ussdState: String = "idle",
    val ussdReply: String? = null,
    val ussdBusy: Boolean = false,
    val sendResult: String? = null
)

/** Состояние сетевого экрана. */
data class NetUiState(
    val ifaces: List<Network.Iface> = emptyList(),
    val leases: List<Network.Lease> = emptyList(),
    val log: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val pingOut: String? = null,
    val pingBusy: Boolean = false,
    val uplink: Uplink.State? = null,
    /** Устройство → код ответа generate_204 (204 = интернет есть). */
    val probes: Map<String, String> = emptyMap(),
    val uplinkBusy: Boolean = false
)

/** Состояние системных экранов. */
data class SysUiState(
    val packages: List<Pair<String, String>> = emptyList(),
    val services: List<Triple<String, Boolean, Int>> = emptyList(),
    val leds: List<String> = emptyList(),
    val processes: List<String> = emptyList(),
    val cpu: String? = null,
    val cpuModel: String? = null,
    val cpuCores: List<Float> = emptyList(),
    val cpuMaxMhz: Float = 0f,
    val cpuGovernor: String? = null,
    val crontab: String? = null,
    val wifi: List<String> = emptyList(),
    val wifiClients: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val actionResult: String? = null,
    /** Вывод произвольной команды (маршруты, firewall, wg, каналы). */
    val execOut: String? = null,
    val execTitle: String? = null,
    /** Сводка для экрана «Обзор». */
    val overview: List<Pair<String, String>> = emptyList(),
    val ramPercent: Int? = null,
    val loadPercent: Int? = null,
    val cores: Int = 0,
    val uptimeText: String? = null,
    val ramText: String? = null,
    val routerTemp: Double? = null,
    val modemTemp: Double? = null
)

/** Состояние экрана UCI-конфига. */
data class ConfigUiState(
    val name: String? = null,
    val sections: List<UciConfig.Section> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val service: SystemInfo.ServiceStatus? = null
)

/** Состояние анализа каналов. */
data class ScanUiState(
    val aps: List<Wifi.Ap> = emptyList(),
    val device: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/** Состояние экрана туннелей. */
data class VpnUiState(
    val kind: String = "",
    val instances: List<Vpn.Instance> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Состояние экрана маршрутов. */
data class RoutesUiState(
    val routes: List<Routes.Route> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Состояние экрана резервных копий. */
data class BackupUiState(
    val files: List<Pair<String, Long>> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

/** Радиомодуль: параметры точки и её клиенты. */
data class WifiRadio(
    val device: String,
    val info: Wifi.RadioInfo?,
    val clients: List<Wifi.Client>
)

/** Состояние экрана Wireless. */
data class WifiUiState(
    val radios: List<WifiRadio> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Одна строка терминала: команда, вывод, ошибка или служебная пометка. */
data class TermLine(
    val text: String,
    val isInput: Boolean = false,
    val isError: Boolean = false,
    val isMeta: Boolean = false
)

/** Состояние фронтендного терминала. */
data class TermUiState(
    val lines: List<TermLine> = emptyList(),
    val busy: Boolean = false,
    val cwd: String = "/",
    /** Команда запущена на роутере и пишет вывод; доступна кнопка «Стоп». */
    val streaming: Boolean = false,
    /** PID фонового процесса на роутере (для «Стоп»). */
    val pid: Int = 0
)

/** Состояние встроенного редактора файлов роутера. */
data class FileEditUiState(
    val open: Boolean = false,
    val path: String = "",
    val content: String = "",
    val original: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val aclDenied: Boolean = false
) {
    val dirty: Boolean get() = content != original
}

/** Настройка меню (как в RouteriCh): скрытые разделы + нижняя панель. */
data class MenuUiState(
    val hidden: Set<String> = emptySet(),
    val bottom: List<String> = DEFAULT_BOTTOM
) {
    companion object {
        /** Нижняя панель по умолчанию: 4 слота. */
        val DEFAULT_BOTTOM = listOf("app", "overview", "clients", "system")
    }
}

/** Цель Wake-on-LAN. */
data class WolTarget(val name: String, val mac: String)

/** Состояние Wake-on-LAN. */
data class WolUiState(
    val targets: List<WolTarget> = emptyList(),
    val iface: String = "br-lan",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class UiState(
    val settings: Settings = Settings(),
    val snap: ModemSnapshot? = null,
    val history: Map<Metric, List<Float>> = emptyMap(),
    val peaks: Map<Metric, Double> = emptyMap(),
    val trend: Trend = Trend.FLAT,
    val updatedAt: Long = 0L,
    val polling: Boolean = true,
    val testResult: String? = null,
    val profiles: List<RouterProfile> = emptyList(),
    val activeProfileId: String? = null,
    val ubus: UbusState = UbusState(),
    val modem: ModemUiState = ModemUiState(),
    val sms: SmsUiState = SmsUiState(),
    val net: NetUiState = NetUiState(),
    val sys: SysUiState = SysUiState(),
    val cfg: ConfigUiState = ConfigUiState(),
    val scan: ScanUiState = ScanUiState(),
    val vpn: VpnUiState = VpnUiState(),
    val routes: RoutesUiState = RoutesUiState(),
    val backup: BackupUiState = BackupUiState(),
    val wifi2: WifiUiState = WifiUiState(),
    val term: TermUiState = TermUiState(),
    val fileEdit: FileEditUiState = FileEditUiState(),
    val wol: WolUiState = WolUiState(),
    val menu: MenuUiState = MenuUiState(),
    val rememberUser: String? = null,
    val rememberPass: String? = null
)

/** Частые команды для чипов терминала. */
val TERM_PRESETS = listOf(
    "ubus call medvemodem uplink",
    "logread | tail -n 50",
    "ip route show default",
    "ifup mm0",
    "apk info medvemodem",
    "uci show medvemodem",
    "df -h",
    "free",
    "uptime",
    "ps w",
    "curl --interface wwan0 http://connectivitycheck.gstatic.com/generate_204 -w '%{http_code}'",
    "cat /sys/class/thermal/thermal_zone*/temp"
)

class ModemViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    init {
        ru.franh.modemmon.util.L10n.lang = prefs.load().lang
    }
    private val profilesRepo = Profiles(app)
    private val creds = Creds(app)

    // Хранилища фич, которые грузятся в init — объявлены до init-блока.
    private val wolPrefs = app.getSharedPreferences("modemmon_wol", Context.MODE_PRIVATE)
    private val menuPrefs = app.getSharedPreferences("modemmon_menu", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(
            settings = prefs.load(),
            profiles = profilesRepo.list(),
            activeProfileId = profilesRepo.activeId()
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Токен ubus: только в памяти процесса, на диск не пишется. */
    private var ubusToken: String? = null

    private val buffers = mutableMapOf<Metric, ArrayDeque<Float>>()
    private val maxPoints = 120
    private var job: Job? = null

    init {
        startPolling()
        startUbusKeepalive()
        loadWol()
        loadMenu()
        // Если пароль сохранён — входим в ubus сразу, без ручного ввода.
        creds.saved()?.let { (user, pass) ->
            _state.value = _state.value.copy(rememberUser = user, rememberPass = pass)
            ubusLogin(user, pass, remember = true, silent = true)
        }
    }

    fun startPolling() {
        job?.cancel()
        job = viewModelScope.launch {
            while (isActive) {
                val s = _state.value.settings
                if (_state.value.polling) poll(s)
                delay(s.intervalMs.coerceIn(500, 30_000).toLong())
            }
        }
    }

    private suspend fun poll(settings: Settings) {
        val snap = withContext(Dispatchers.IO) {
            if (settings.demo) DemoSource.next() else ModemApi.fetch(settings.fullUrl)
        }
        push(snap)
    }

    private fun push(snap: ModemSnapshot) {
        val cur = _state.value
        val values = metricsOf(snap)
        values.forEach { (metric, v) ->
            if (v != null) {
                val buf = buffers.getOrPut(metric) { ArrayDeque() }
                buf.addLast(v.toFloat())
                while (buf.size > maxPoints) buf.removeFirst()
            }
        }
        val peaks = cur.peaks.toMutableMap()
        values.forEach { (metric, v) ->
            if (v != null) {
                val prev = peaks[metric]
                if (prev == null || v > prev) peaks[metric] = v
            }
        }
        val metric = Metric.byKey(cur.settings.metric)
        val trend = computeTrend(metric, cur.settings.metric)
        _state.value = cur.copy(
            snap = snap,
            history = buffers.mapValues { it.value.toList() },
            peaks = peaks,
            trend = trend,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** Значение выбранной метрики с учётом 5G: для NR берём NR-метрики, если есть. */
    fun valueOf(snap: ModemSnapshot?, metric: Metric): Double? {
        if (snap == null) return null
        val nr = snap.nr
        val lte = snap.lte
        return when (metric) {
            Metric.SINR -> nr.sinr ?: lte.sinr
            Metric.RSRP -> nr.rsrp ?: lte.rsrp
            Metric.RSRQ -> nr.rsrq ?: lte.rsrq
            Metric.RSSI -> lte.rssi ?: nr.rssi
        }
    }

    private fun metricsOf(snap: ModemSnapshot): Map<Metric, Double?> = mapOf(
        Metric.SINR to (snap.nr.sinr ?: snap.lte.sinr),
        Metric.RSRP to (snap.nr.rsrp ?: snap.lte.rsrp),
        Metric.RSRQ to (snap.nr.rsrq ?: snap.lte.rsrq),
        Metric.RSSI to (snap.lte.rssi ?: snap.nr.rssi)
    )

    private fun computeTrend(metric: Metric, key: String): Trend {
        val buf = buffers[metric] ?: return Trend.FLAT
        if (buf.size < 6) return Trend.FLAT
        val list = buf.toList()
        val recent = list.takeLast(2).average()
        val before = list.dropLast(2).takeLast(3).average()
        val diff = recent - before
        val hysteresis = when (metric) {
            Metric.SINR -> 1.0
            Metric.RSRP -> 1.5
            Metric.RSRQ -> 0.6
            Metric.RSSI -> 1.5
        }
        return when {
            diff > hysteresis -> Trend.UP
            diff < -hysteresis -> Trend.DOWN
            else -> Trend.FLAT
        }
    }

    fun updateSettings(new: Settings) {
        val old = _state.value.settings
        val restart = new.intervalMs != old.intervalMs ||
            new.demo != old.demo ||
            new.baseUrl != old.baseUrl ||
            new.path != old.path
        prefs.save(new)
        ru.franh.modemmon.util.L10n.lang = new.lang
        _state.value = _state.value.copy(settings = new)
        if (restart) {
            if (new.demo != old.demo) resetPeaks()
            startPolling()
        }
    }

    fun resetPeaks() {
        buffers.clear()
        _state.value = _state.value.copy(
            history = emptyMap(),
            peaks = emptyMap(),
            trend = Trend.FLAT
        )
    }

    fun togglePolling() {
        _state.value = _state.value.copy(polling = !_state.value.polling)
    }

    fun test(url: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(testResult = "Проверяю…")
            val snap = withContext(Dispatchers.IO) { ModemApi.fetch(url) }
            val msg = if (snap.online) {
                "OK · ${snap.rat.ifBlank { "?" }} · ${snap.operator.ifBlank { "?" }}" +
                    if (snap.nr.sinr != null) " · NR SINR ${snap.nr.sinr}" else ""
            } else {
                "Ошибка: ${snap.error ?: "нет данных"}"
            }
            _state.value = _state.value.copy(testResult = msg)
        }
    }

    fun clearTest() {
        _state.value = _state.value.copy(testResult = null)
    }

    // ---------- Профили роутеров ----------

    fun saveProfile(profile: RouterProfile) {
        profilesRepo.upsert(profile)
        _state.value = _state.value.copy(profiles = profilesRepo.list())
    }

    fun deleteProfile(id: String) {
        profilesRepo.remove(id)
        _state.value = _state.value.copy(
            profiles = profilesRepo.list(),
            activeProfileId = profilesRepo.activeId()
        )
    }

    /** Делает профиль активным: адрес подставляется в настройки, опрос перезапускается. */
    fun selectProfile(id: String) {
        val profile = profilesRepo.list().firstOrNull { it.id == id } ?: return
        profilesRepo.setActive(id)
        _state.value = _state.value.copy(activeProfileId = id)
        updateSettings(_state.value.settings.copy(baseUrl = profile.baseUrl))
    }

    // ---------- Канал управления (ubus) ----------

    /**
     * Логин в rpcd.
     * [remember] — сохранить пароль в приватных prefs (только по согласию).
     * [silent] — не показывать «Подключаюсь…» при автовходе на старте.
     */
    fun ubusLogin(user: String, password: String, remember: Boolean = false, silent: Boolean = false) {
        val base = _state.value.settings.baseUrl
        if (remember) creds.save(user, password) else creds.clear()
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(ubus = UbusState(busy = true, message = "Подключаюсь…"))
            }
            val reply = withContext(Dispatchers.IO) { Ubus.login(base, user, password) }
            val token = reply.data?.optString("token").orEmpty()
            ubusToken = token.ifBlank { null }
            _state.value = _state.value.copy(
                ubus = UbusState(
                    busy = false,
                    ok = reply.ok,
                    connected = ubusToken != null,
                    message = reply.error ?: "Подключено"
                ),
                rememberUser = if (remember) user else null,
                rememberPass = if (remember) password else null
            )
            if (ubusToken != null) loadDevices()
        }
    }

    /** Тихое обновление токена раз в минуту, пока есть сохранённые реквизиты:
     *  если rpcd на роутере перезапустили, сессия восстановится максимум за минуту. */
    private fun startUbusKeepalive() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                val (user, pass) = creds.saved() ?: continue
                val base = _state.value.settings.baseUrl
                val reply = withContext(Dispatchers.IO) { Ubus.login(base, user, pass) }
                val token = reply.data?.optString("token").orEmpty()
                if (token.isNotBlank()) {
                    ubusToken = token
                    if (!_state.value.ubus.connected) {
                        _state.value = _state.value.copy(
                            ubus = _state.value.ubus.copy(connected = true, message = "Подключено")
                        )
                    }
                }
            }
        }
    }

    fun ubusLogout() {
        ubusToken = null
        creds.clear()
        _state.value = _state.value.copy(
            ubus = UbusState(),
            rememberUser = null,
            rememberPass = null,
            modem = ModemUiState()
        )
    }

    /** Вызов ubus с текущим токеном; используется будущими экранами управления. */
    fun ubusCall(
        obj: String,
        method: String,
        args: org.json.JSONObject? = null,
        onResult: (Ubus.Reply) -> Unit
    ) {
        val token = ubusToken
        if (token == null) {
            onResult(Ubus.Reply(null, "Нет сессии — войдите в ubus"))
            return
        }
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            var reply = withContext(Dispatchers.IO) { Ubus.call(base, token, obj, method, args) }
            // Сессия могла умереть (например, rpcd перезапускали на роутере) —
            // тихо перелогиниваемся сохранёнными реквизитами и повторяем один раз.
            if (!reply.ok && isAuthError(reply.error)) {
                val saved = creds.saved()
                if (saved != null) {
                    val (user, pass) = saved
                    val login = withContext(Dispatchers.IO) { Ubus.login(base, user, pass) }
                    val newToken = login.data?.optString("token").orEmpty()
                    if (newToken.isNotBlank()) {
                        ubusToken = newToken
                        _state.value = _state.value.copy(
                            ubus = _state.value.ubus.copy(connected = true, message = "Подключено")
                        )
                        reply = withContext(Dispatchers.IO) {
                            Ubus.call(base, newToken, obj, method, args)
                        }
                    }
                }
            }
            onResult(reply)
        }
    }

    /** Ошибка сессии/доступа: повод для тихого перелогина и повтора. */
    private fun isAuthError(error: String?): Boolean {
        val e = error ?: return false
        return e.contains("Доступ запрещён", true) ||
            e.contains("Access denied", true) ||
            e.contains("Permission denied", true)
    }

    // ---------- Экран «Модем» ----------

    /** Обновляет список модемов и, если ничего не выбрано, берёт первый рабочий. */
    fun loadDevices() {
        val token = ubusToken ?: run {
            _state.value = _state.value.copy(
                modem = _state.value.modem.copy(error = "Нет сессии — войдите в ubus")
            )
            return
        }
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(modem = _state.value.modem.copy(loading = true, error = null))
            val (list, err) = withContext(Dispatchers.IO) { MedveModem.devices(base, token) }
            val cur = _state.value.modem
            val keep = cur.selectedId?.takeIf { id -> list.any { it.id == id } }
            // По умолчанию берём рабочий модем с AT-портом: у HiLink его нет.
            val next = keep
                ?: list.firstOrNull { it.ok && it.ttyPorts.isNotEmpty() }?.id
                ?: list.firstOrNull { it.ok }?.id
                ?: list.firstOrNull()?.id
            _state.value = _state.value.copy(
                modem = cur.copy(devices = list, loading = false, error = err, selectedId = next)
            )
            if (next != null && next != cur.selectedId) loadRadio()
        }
    }

    fun selectDevice(id: String) {
        _state.value = _state.value.copy(
            modem = _state.value.modem.copy(
                selectedId = id, radio = null, radioError = null, atOutput = emptyList()
            )
        )
        loadRadio()
    }

    private fun loadRadio() {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            val (radio, err) = withContext(Dispatchers.IO) { MedveModem.radio(base, token, id) }
            val cur = _state.value.modem
            if (cur.selectedId != id) return@launch
            _state.value = _state.value.copy(
                modem = cur.copy(radio = radio, radioError = err)
            )
        }
    }

    /** Отправляет AT-команду выбранному модему. */
    fun sendAt(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        val token = ubusToken ?: run {
            _state.value = _state.value.copy(
                modem = _state.value.modem.copy(atOutput = listOf("Нет сессии — войдите в ubus"))
            )
            return
        }
        val id = _state.value.modem.selectedId ?: run {
            _state.value = _state.value.copy(
                modem = _state.value.modem.copy(atOutput = listOf("Сначала выберите модем"))
            )
            return
        }
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            val cur = _state.value.modem
            _state.value = _state.value.copy(modem = cur.copy(atBusy = true))
            val (reply, err) = withContext(Dispatchers.IO) {
                MedveModem.atCommand(base, token, id, cmd)
            }
            val lines = buildList {
                add("> $cmd")
                if (err != null) {
                    add("Ошибка: $err")
                } else if (reply != null) {
                    addAll(reply.lines)
                    add("[${reply.final.ifBlank { if (reply.ok) "OK" else "FAIL" }}] · ${reply.elapsedMs} мс · ${reply.port}")
                }
            }
            val m = _state.value.modem
            _state.value = _state.value.copy(
                modem = m.copy(
                    atBusy = false,
                    atOutput = m.atOutput + lines,
                    history = (listOf(cmd) + m.history).distinct().take(15)
                )
            )
        }
    }

    fun clearAtOutput() {
        _state.value = _state.value.copy(modem = _state.value.modem.copy(atOutput = emptyList()))
    }

    // ---------- SMS и USSD ----------

    fun loadSms() {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(sms = _state.value.sms.copy(loading = true, error = null))
            val (list, err) = withContext(Dispatchers.IO) { MedveModem.smsList(base, token, id) }
            _state.value = _state.value.copy(
                sms = _state.value.sms.copy(messages = list, loading = false, error = err)
            )
        }
    }

    fun sendSms(number: String, text: String) {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(sms = _state.value.sms.copy(sendResult = "Отправляю…"))
            val err = withContext(Dispatchers.IO) {
                MedveModem.smsSend(base, token, id, number.trim(), text)
            }
            _state.value = _state.value.copy(
                sms = _state.value.sms.copy(sendResult = err ?: "Отправлено")
            )
            if (err == null) loadSms()
        }
    }

    fun deleteSms(smsId: Int) {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { MedveModem.smsDelete(base, token, id, smsId) }
            if (err == null) {
                val left = _state.value.sms.messages.filterNot { it.id == smsId }
                _state.value = _state.value.copy(sms = _state.value.sms.copy(messages = left))
            } else {
                _state.value = _state.value.copy(sms = _state.value.sms.copy(error = err))
            }
        }
    }

    fun sendUssd(code: String) {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sms = _state.value.sms.copy(ussdBusy = true, ussdReply = null, error = null)
            )
            val (res, err) = withContext(Dispatchers.IO) { MedveModem.ussdStart(base, token, id, code.trim()) }
            _state.value = _state.value.copy(
                sms = _state.value.sms.copy(
                    ussdBusy = false,
                    ussdState = res?.state ?: "error",
                    ussdReply = res?.reply,
                    error = err
                )
            )
        }
    }

    fun refreshUssd() {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            val (res, err) = withContext(Dispatchers.IO) { MedveModem.ussdStatus(base, token, id) }
            _state.value = _state.value.copy(
                sms = _state.value.sms.copy(
                    ussdState = res?.state ?: _state.value.sms.ussdState,
                    ussdReply = res?.reply ?: _state.value.sms.ussdReply,
                    error = err
                )
            )
        }
    }

    // ---------- Диапазоны ----------

    fun startBandsEdit() {
        val r = _state.value.modem.radio ?: return
        _state.value = _state.value.copy(
            modem = _state.value.modem.copy(bandsDraft = r.bands.toSet())
        )
    }

    fun cancelBandsEdit() {
        _state.value = _state.value.copy(modem = _state.value.modem.copy(bandsDraft = null))
    }

    fun toggleBand(band: String) {
        val cur = _state.value.modem.bandsDraft ?: return
        val next = if (band in cur) cur - band else cur + band
        _state.value = _state.value.copy(modem = _state.value.modem.copy(bandsDraft = next))
    }

    /** Массово включить набор диапазонов (например, всю группу LTE). */
    fun toggleBandOn(bands: List<String>) {
        val cur = _state.value.modem.bandsDraft ?: return
        _state.value = _state.value.copy(modem = _state.value.modem.copy(bandsDraft = cur + bands))
    }

    /** Массово снять набор диапазонов. */
    fun toggleBandOff(bands: List<String>) {
        val cur = _state.value.modem.bandsDraft ?: return
        _state.value = _state.value.copy(modem = _state.value.modem.copy(bandsDraft = cur - bands.toSet()))
    }

    /** Применяет черновик. Движок сам откатит настройки, если сеть не вернётся. */
    fun applyBands() {
        val token = ubusToken ?: return
        val m = _state.value.modem
        val id = m.selectedId ?: return
        val draft = m.bandsDraft ?: return
        if (draft.isEmpty()) {
            _state.value = _state.value.copy(
                modem = m.copy(radioError = "Нельзя снять все диапазоны — модем останется без сети")
            )
            return
        }
        val modes = m.radio?.modes ?: emptyList()
        val preferred = m.radio?.preferred
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                modem = _state.value.modem.copy(radioBusy = true, radioError = null, changeState = "applying")
            )
            val (res, err) = withContext(Dispatchers.IO) {
                MedveModem.setRadio(base, token, id, modes, preferred, draft.toList())
            }
            _state.value = _state.value.copy(
                modem = _state.value.modem.copy(
                    radioBusy = false,
                    radio = res ?: _state.value.modem.radio,
                    bandsDraft = if (err == null) null else _state.value.modem.bandsDraft,
                    radioError = err,
                    changeState = res?.change ?: if (err != null) null else "applying"
                )
            )
            if (err == null) watchRadioChange(id, base, token)
        }
    }

    /** Опрашивает состояние применения, пока оно не станет окончательным. */
    private suspend fun watchRadioChange(id: String, base: String, token: String) {
        val terminal = setOf("kept", "rolled_back", "failed", "applied")
        repeat(20) {
            delay(3000)
            val (res, _) = withContext(Dispatchers.IO) { MedveModem.radio(base, token, id) }
            val cur = _state.value.modem
            if (cur.selectedId != id) return
            val st = res?.change
            _state.value = _state.value.copy(
                modem = cur.copy(radio = res ?: cur.radio, changeState = st)
            )
            if (st != null && st in terminal) return
        }
    }

    fun confirmBands(action: String) {
        val token = ubusToken ?: return
        val id = _state.value.modem.selectedId ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(modem = _state.value.modem.copy(radioBusy = true))
            val err = withContext(Dispatchers.IO) { MedveModem.confirmRadio(base, token, id, action) }
            val (res, _) = withContext(Dispatchers.IO) { MedveModem.radio(base, token, id) }
            _state.value = _state.value.copy(
                modem = _state.value.modem.copy(
                    radioBusy = false,
                    radio = res ?: _state.value.modem.radio,
                    changeState = res?.change,
                    radioError = err
                )
            )
        }
    }

    // ---------- Сеть, клиенты, журнал ----------

    fun loadNetwork() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(net = _state.value.net.copy(loading = true, error = null))
            val (ifaces, err1) = withContext(Dispatchers.IO) { Network.interfaces(base, token) }
            val (leases, err2) = withContext(Dispatchers.IO) { Network.leases(base, token) }
            _state.value = _state.value.copy(
                net = _state.value.net.copy(
                    ifaces = ifaces,
                    leases = leases,
                    loading = false,
                    error = err1 ?: err2
                )
            )
        }
    }

    fun loadLog() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            val (lines, err) = withContext(Dispatchers.IO) { Network.log(base, token, 120) }
            _state.value = _state.value.copy(
                net = _state.value.net.copy(log = lines, error = err ?: _state.value.net.error)
            )
        }
    }

    fun runPing(target: String, iface: String?) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                net = _state.value.net.copy(pingBusy = true, pingOut = "Проверяю…")
            )
            val (out, err) = withContext(Dispatchers.IO) { Network.ping(base, token, target, iface) }
            _state.value = _state.value.copy(
                net = _state.value.net.copy(pingBusy = false, pingOut = out ?: "Ошибка: $err")
            )
        }
    }

    // ---------- Системные экраны ----------

    private fun sysLoad(block: suspend (String, String) -> Unit) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(sys = _state.value.sys.copy(loading = true, error = null))
            block(base, token)
            _state.value = _state.value.copy(sys = _state.value.sys.copy(loading = false))
        }
    }

    fun loadPackages() = sysLoad { base, token ->
        val (list, err) = withContext(Dispatchers.IO) { SystemInfo.packages(base, token) }
        _state.value = _state.value.copy(sys = _state.value.sys.copy(packages = list, error = err))
    }

    fun loadServices() = sysLoad { base, token ->
        val (list, err) = withContext(Dispatchers.IO) { SystemInfo.services(base, token) }
        _state.value = _state.value.copy(sys = _state.value.sys.copy(services = list, error = err))
    }

    fun loadLeds() = sysLoad { base, token ->
        val (list, err) = withContext(Dispatchers.IO) { SystemInfo.leds(base, token) }
        _state.value = _state.value.copy(sys = _state.value.sys.copy(leds = list, error = err))
    }

    fun loadProcesses() = sysLoad { base, token ->
        val (list, err) = withContext(Dispatchers.IO) { SystemInfo.processes(base, token) }
        _state.value = _state.value.copy(sys = _state.value.sys.copy(processes = list, error = err))
    }

    fun loadCpu() = sysLoad { base, token ->
        val (text, err) = withContext(Dispatchers.IO) { SystemInfo.readFile(base, token, "/proc/cpuinfo") }
        val model = text?.lines()
            ?.firstOrNull { it.startsWith("CPU part") || it.startsWith("model name") || it.startsWith("Processor") }
            ?.substringAfter(":")?.trim()
        val (freq, freqErr) = withContext(Dispatchers.IO) { SystemInfo.cpuFreq(base, token) }
        _state.value = _state.value.copy(
            sys = _state.value.sys.copy(
                cpu = text,
                cpuModel = model,
                cpuCores = freq?.coresMhz ?: emptyList(),
                cpuMaxMhz = freq?.maxMhz ?: 0f,
                cpuGovernor = freq?.governor,
                error = err ?: freqErr
            )
        )
    }

    fun loadCrontab() = sysLoad { base, token ->
        val (text, err) = withContext(Dispatchers.IO) {
            SystemInfo.readFile(base, token, "/etc/crontabs/root")
        }
        _state.value = _state.value.copy(
            sys = _state.value.sys.copy(crontab = text ?: "Файл пуст или недоступен", error = err)
        )
    }

    /** Сохраняет crontab и перезапускает cron. */
    fun saveCrontab(text: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(actionResult = "Сохраняю…", loading = true)
            )
            val err = withContext(Dispatchers.IO) {
                SystemInfo.writeFile(base, token, "/etc/crontabs/root", text)
            }
            if (err == null) {
                withContext(Dispatchers.IO) {
                    SystemInfo.exec(base, token, "/etc/init.d/cron", listOf("restart"))
                }
            }
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(
                    crontab = text,
                    loading = false,
                    actionResult = err?.let { "Ошибка: $it" } ?: "Сохранено, cron перезапущен"
                )
            )
        }
    }

    fun loadWifi() = sysLoad { base, token ->
        val (devs, err) = withContext(Dispatchers.IO) { SystemInfo.wifiDevices(base, token) }
        val clients = if (devs.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            devs.flatMap { d -> SystemInfo.wifiClients(base, token, d).first }
        }
        _state.value = _state.value.copy(
            sys = _state.value.sys.copy(wifi = devs, wifiClients = clients, error = err)
        )
    }

    /** Перезагрузка. Вызывать только после подтверждения в диалоге. */
    fun rebootRouter() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(actionResult = "Отправляю команду…")
            )
            val err = withContext(Dispatchers.IO) { SystemInfo.reboot(base, token) }
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(
                    actionResult = err ?: "Роутер перезагружается — связь пропадёт на минуту"
                )
            )
        }
    }

    /** Состояние и управление службой (forkop, travelmate и т.п.). */
    fun loadServiceStatus(service: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            val (st, _) = withContext(Dispatchers.IO) {
                SystemInfo.serviceStatus(base, token, service)
            }
            _state.value = _state.value.copy(
                cfg = _state.value.cfg.copy(service = st)
            )
        }
    }

    fun serviceControl(service: String, action: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                cfg = _state.value.cfg.copy(message = "Выполняю…")
            )
            val err = withContext(Dispatchers.IO) {
                Actions.serviceAction(base, token, service, action)
            }
            _state.value = _state.value.copy(
                cfg = _state.value.cfg.copy(
                    message = err?.let { "Ошибка: $it" } ?: "Готово: $action"
                )
            )
            loadServiceStatus(service)
        }
    }

    // ---------- Uplink и автопереключение ----------

    /**
     * Проверяет состояние каналов и реальную доступность интернета через каждый.
     * Нужно, чтобы показать «основной канал без интернета, переключение выключено».
     */
    fun checkUplink() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(net = _state.value.net.copy(uplinkBusy = true))
            val (st, _) = withContext(Dispatchers.IO) { Uplink.status(base, token) }
            val probes = withContext(Dispatchers.IO) {
                st?.paths.orEmpty().associate { p ->
                    p.device to (Uplink.probe(base, token, p.device).first ?: "000")
                }
            }
            _state.value = _state.value.copy(
                net = _state.value.net.copy(uplink = st, probes = probes, uplinkBusy = false)
            )
        }
    }

    fun setFailover(on: Boolean) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                net = _state.value.net.copy(uplinkBusy = true, error = null)
            )
            val err = withContext(Dispatchers.IO) { Uplink.setFailover(base, token, on) }
            _state.value = _state.value.copy(
                net = _state.value.net.copy(
                    error = err?.let { "Не удалось переключить: $it" }
                )
            )
            checkUplink()
        }
    }

    // ---------- Wireless ----------

    fun loadWifi2() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                wifi2 = _state.value.wifi2.copy(loading = true, error = null)
            )
            val (devs, err) = withContext(Dispatchers.IO) { Wifi.devices(base, token) }
            val radios = withContext(Dispatchers.IO) {
                devs.map { dev ->
                    val (info, _) = Wifi.info(base, token, dev)
                    WifiRadio(dev, info, Wifi.assoc(base, token, dev))
                }
            }
            _state.value = _state.value.copy(
                wifi2 = WifiUiState(radios = radios, loading = false, error = err)
            )
        }
    }

    // ---------- Маршруты ----------

    fun loadRoutes() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                routes = _state.value.routes.copy(loading = true, error = null)
            )
            val (list, err) = withContext(Dispatchers.IO) { Routes.list(base, token) }
            _state.value = _state.value.copy(
                routes = RoutesUiState(routes = list, loading = false, error = err)
            )
        }
    }

    // ---------- Резервные копии ----------

    fun loadBackups() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                backup = _state.value.backup.copy(loading = true, error = null)
            )
            val (list, err) = withContext(Dispatchers.IO) { SystemInfo.backups(base, token) }
            _state.value = _state.value.copy(
                backup = _state.value.backup.copy(files = list, loading = false, error = err)
            )
        }
    }

    fun createBackup() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val name = "backup-$stamp.tar.gz"
        viewModelScope.launch {
            _state.value = _state.value.copy(
                backup = _state.value.backup.copy(loading = true, message = "Создаю архив…")
            )
            val err = withContext(Dispatchers.IO) { SystemInfo.createBackup(base, token, name) }
            val (list, _) = withContext(Dispatchers.IO) { SystemInfo.backups(base, token) }
            _state.value = _state.value.copy(
                backup = _state.value.backup.copy(
                    files = list,
                    loading = false,
                    message = err?.let { "Ошибка: $it" } ?: "Архив создан: $name",
                    error = null
                )
            )
        }
    }

    // ---------- Туннели ----------

    fun loadVpn(kind: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                vpn = VpnUiState(kind = kind, loading = true)
            )
            val (list, err) = withContext(Dispatchers.IO) { Vpn.instances(base, token, kind) }
            _state.value = _state.value.copy(
                vpn = VpnUiState(kind = kind, instances = list, loading = false, error = err)
            )
        }
    }

    // ---------- Анализ каналов ----------

    fun runScan() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                scan = _state.value.scan.copy(loading = true, error = null)
            )
            val (devs, devErr) = withContext(Dispatchers.IO) { Wifi.devices(base, token) }
            val dev = devs.firstOrNull()
            if (dev == null) {
                _state.value = _state.value.copy(
                    scan = ScanUiState(loading = false, error = devErr ?: "Нет беспроводного устройства")
                )
                return@launch
            }
            val (aps, err) = withContext(Dispatchers.IO) { Wifi.scan(base, token, dev) }
            _state.value = _state.value.copy(
                scan = ScanUiState(aps = aps, device = dev, loading = false, error = err)
            )
        }
    }

    // ---------- UCI-конфиги ----------

    fun loadConfig(name: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                cfg = ConfigUiState(name = name, loading = true)
            )
            val (sections, err) = withContext(Dispatchers.IO) { UciConfig.get(base, token, name) }
            _state.value = _state.value.copy(
                cfg = ConfigUiState(name = name, sections = sections, loading = false, error = err)
            )
        }
    }

    fun saveOption(
        config: String,
        section: String,
        option: String,
        value: String,
        isList: Boolean = false
    ) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(cfg = _state.value.cfg.copy(message = "Сохраняю…"))
            val err = withContext(Dispatchers.IO) {
                UciConfig.set(base, token, config, section, option, value, isList)
            }
            if (err == null) {
                loadConfig(config)
                _state.value = _state.value.copy(cfg = _state.value.cfg.copy(message = "Сохранено"))
            } else {
                _state.value = _state.value.copy(cfg = _state.value.cfg.copy(message = "Ошибка: $err"))
            }
        }
    }

    /** Выполнить команду и показать вывод (маршруты, firewall, wg, каналы). */
    fun runExec(title: String, command: String, params: List<String>) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(execTitle = title, execOut = null, error = null)
            )
            val (out, err) = withContext(Dispatchers.IO) {
                SystemInfo.exec(base, token, command, params)
            }
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(
                    execOut = out?.ifBlank { "Пусто" } ?: "Ошибка: $err",
                    error = null
                )
            )
        }
    }

    // ---------------- Терминал ----------------

    private val termHistory = mutableListOf<String>()
    private var termHistoryIdx = -1
    private var termStreamJob: Job? = null
    private var termStreamSeen = 0
    private var termStreamMisses = 0
    private var termStartLines = 0
    /** null = ещё не проверяли; true = на роутере есть /usr/bin/mmterm. */
    private var termScriptMode: Boolean? = null
    /** Незавершённая строка вывода (полл может отрезать строку посередине). */
    private var termStreamTail: String? = null

    private val termPrefs by lazy {
        getApplication<Application>().getSharedPreferences("modemmon_term", Context.MODE_PRIVATE)
    }

    private fun termHistoryLoad() {
        if (termHistory.isNotEmpty()) return
        val arr = JSONArray(termPrefs.getString("history", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) termHistory.add(s)
        }
        termHistoryIdx = termHistory.size
    }

    private fun termHistorySave() {
        val arr = JSONArray()
        termHistory.takeLast(50).forEach { arr.put(it) }
        termPrefs.edit().putString("history", arr.toString()).apply()
    }

    /** История для чипов в UI (свежие сверху). */
    fun termHistoryList(): List<String> {
        termHistoryLoad()
        return termHistory.asReversed().toList()
    }

    /** Подсказки по префиксу: история + частые команды. */
    fun termSuggestions(prefix: String): List<String> {
        termHistoryLoad()
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()
        return (termHistory.asReversed() + TERM_PRESETS)
            .distinct()
            .filter { it.startsWith(p, ignoreCase = true) && !it.equals(p, ignoreCase = true) }
            .take(3)
    }

    /** Опасные команды: требуют подтверждения в UI. */
    fun isDangerous(cmd: String): Boolean = dangerousRegex.containsMatchIn(cmd)

    /**
     * Выполнить команду терминала. Команда уезжает на роутер в base64 (кавычки и $ не ломают
     * обёртку), запускается в фоне, вывод пишется в /tmp/.mm_term_out и поллится. В конце
     * скрипт пишет __DONE=<код выхода>. «Стоп» убивает процесс и его детей через /proc.
     */
    fun runTerm(input: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        val cmd = input.trim()
        if (cmd.isEmpty() || _state.value.term.busy) return

        // Интерактивные редакторы через канал без TTY не работают — открываем встроенный
        val edMatch = Regex("^(?:nano|vi|vim|edit)\\s+(\\S+)", RegexOption.IGNORE_CASE).find(cmd)
        if (edMatch != null) {
            val p = edMatch.groupValues[1]
            pushLines(
                listOf(
                    TermLine(cmd, isInput = true),
                    TermLine(
                        "nano/vi — интерактивные, через этот канал не работают. " +
                            "Открыл встроенный редактор: $p",
                        isMeta = true
                    )
                )
            )
            openFileEdit(p)
            return
        }
        val tuiMatch = Regex("^(?:less|more|htop)\\b", RegexOption.IGNORE_CASE).find(cmd)
        if (tuiMatch != null) {
            pushLines(
                listOf(
                    TermLine(cmd, isInput = true),
                    TermLine(
                        "Интерактивная команда зависнет без TTY. " +
                            "Используй cat / | tail — или top (его можно остановить «Стоп»).",
                        isMeta = true
                    )
                )
            )
            return
        }

        termHistoryLoad()
        if (termHistory.isEmpty() || termHistory.last() != cmd) termHistory.add(cmd)
        while (termHistory.size > 50) termHistory.removeAt(0)
        termHistoryIdx = termHistory.size
        termHistorySave()

        val cwd = _state.value.term.cwd
        val displayCmd = cmd

        viewModelScope.launch {
            termStartLines = _state.value.term.lines.size + 1
            _state.value = _state.value.copy(
                term = _state.value.term.copy(
                    lines = _state.value.term.lines + TermLine(displayCmd, isInput = true),
                    busy = true
                )
            )
            termStreamTail = null
            val useScript = ensureTermBackend(base, token)
            val start: SystemInfo.ExecResult
            val pid: Int
            if (useScript) {
                // Основной путь: наш скрипт /usr/bin/mmterm ведёт сессию на роутере
                val b64 = Base64.getEncoder().encodeToString(cmd.toByteArray(Charsets.UTF_8))
                start = withContext(Dispatchers.IO) {
                    SystemInfo.execFull(base, token, "/usr/bin/mmterm", listOf("start", b64), 15_000)
                }
                pid = start.stdout?.lineSequence()
                    ?.firstOrNull { it.startsWith("started ") }
                    ?.removePrefix("started ")?.trim()?.toIntOrNull() ?: 0
            } else {
                // Fallback без скрипта: команда в base64, фон, маркер __PID
                val script = cmd + "\necho __DONE=\$?\n"
                val b64 = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_8))
                val runner = "echo $b64 | base64 -d > /tmp/.mm_cmd; " +
                    "/bin/sh /tmp/.mm_cmd > /tmp/.mm_term_out 2>&1 & echo __PID=\$!"
                start = withContext(Dispatchers.IO) {
                    SystemInfo.execFull(base, token, "/bin/sh", listOf("-c", runner), 15_000)
                }
                pid = start.stdout?.lineSequence()
                    ?.firstOrNull { it.startsWith("__PID=") }
                    ?.removePrefix("__PID=")?.trim()?.toIntOrNull() ?: 0
            }

            if (!start.ok || pid == 0) {
                val msg = start.error ?: start.stderr?.takeIf { it.isNotBlank() } ?: "нет ответа"
                pushLines(listOf(TermLine("Ошибка запуска: $msg", isError = true)))
                hintAcl(start.error ?: start.stderr)
                _state.value = _state.value.copy(term = _state.value.term.copy(busy = false))
                return@launch
            }

            termStreamSeen = 0
            termStreamMisses = 0
            _state.value = _state.value.copy(
                term = _state.value.term.copy(pid = pid, streaming = true)
            )
            termStreamJob = viewModelScope.launch {
                if (useScript) pollLoopScript(base, token)
                else pollLoopFallback(base, token, cwd, cmd)
            }
        }
    }

    /** Скрипт mmterm зашит в APK (assets/mmterm.sh) — приложение ставит его на роутер само. */
    private val termScriptB64: String by lazy {
        val text = getApplication<Application>().assets.open("mmterm.sh")
            .bufferedReader().use { it.readText() }
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Скриптовый бэкенд терминала: на роутере должен быть /usr/bin/mmterm версии v1.
     * Если его нет или версия другая — приложение ставит свою копию само:
     * exec /bin/sh разрешён ACL, содержимое скрипта уходит в base64.
     * Не получилось — работаем в упрощённом режиме без скрипта.
     */
    private suspend fun ensureTermBackend(base: String, token: String): Boolean {
        termScriptMode?.let { return it }
        val chk = withContext(Dispatchers.IO) {
            SystemInfo.execFull(base, token, "/usr/bin/mmterm", listOf("check"), 6_000)
        }
        if (chk.ok && chk.stdout?.contains("ok v1") == true) {
            termScriptMode = true
            return true
        }
        // Самоустановка: скрипт из assets → /usr/bin/mmterm + chmod 755
        val install = withContext(Dispatchers.IO) {
            SystemInfo.execFull(
                base, token, "/bin/sh",
                listOf(
                    "-c",
                    "echo $termScriptB64 | base64 -d > /usr/bin/mmterm && chmod 755 /usr/bin/mmterm"
                ),
                20_000
            )
        }
        val chk2 = withContext(Dispatchers.IO) {
            SystemInfo.execFull(base, token, "/usr/bin/mmterm", listOf("check"), 6_000)
        }
        val ok = install.ok && chk2.ok && chk2.stdout?.contains("ok v1") == true
        termScriptMode = ok
        if (!ok) {
            pushLines(
                listOf(
                    TermLine(
                        "mmterm поставить не удалось — терминал в упрощённом режиме (без памяти cd)",
                        isMeta = true
                    )
                )
            )
        }
        return ok
    }

    /** Поллинг через mmterm: скрип сам отдаёт только новые байты, состояние и настоящий cwd. */
    private suspend fun pollLoopScript(base: String, token: String) {
        while (coroutineContext.isActive) {
            delay(1200)
            val r = withContext(Dispatchers.IO) {
                SystemInfo.execFull(base, token, "/usr/bin/mmterm", listOf("poll"), 8_000)
            }
            if (!r.ok || r.stdout == null) {
                termStreamMisses++
                if (termStreamMisses >= 3) {
                    pushLines(
                        listOf(TermLine("Связь с роутером потеряна: ${r.error ?: "нет ответа"}", isError = true))
                    )
                    finishTerm(_state.value.term.cwd, null, interrupted = true)
                    return
                }
                continue
            }
            termStreamMisses = 0
            val text = stripAnsi(r.stdout)
            val idx = text.lastIndexOf("\n__MMTERM ")
            if (idx < 0) continue
            val chunk = text.substring(0, idx)
            if (chunk.isNotEmpty()) appendChunk(chunk)
            val parts = text.substring(idx + 1).trim().split(Regex("\\s+"))
            val cwdInMeta = parts.lastOrNull { it.startsWith("cwd=") }?.removePrefix("cwd=")
            if (!cwdInMeta.isNullOrBlank() && cwdInMeta != _state.value.term.cwd) {
                _state.value = _state.value.copy(term = _state.value.term.copy(cwd = cwdInMeta))
            }
            if (parts.getOrNull(1) == "done") {
                finishTerm(cwdInMeta ?: _state.value.term.cwd, parts.getOrNull(2)?.toIntOrNull())
                return
            }
        }
    }

    /** Поллинг без скрипта: читаем весь файл и режем по смещению на клиенте. */
    private suspend fun pollLoopFallback(base: String, token: String, cwd: String, cmd: String) {
        var newCwd = cwd
        val cd = Regex("^cd(?:\\s+(.*))?$").find(cmd)
        if (cd != null) newCwd = resolveCwd(cwd, cd.groupValues[1].trim())

        while (coroutineContext.isActive) {
            delay(1200)
            val (data, err) = withContext(Dispatchers.IO) {
                SystemInfo.readFile(base, token, "/tmp/.mm_term_out")
            }
            if (data == null) {
                termStreamMisses++
                if (termStreamMisses >= 3) {
                    pushLines(
                        listOf(TermLine("Связь с роутером потеряна: ${err ?: "нет ответа"}", isError = true))
                    )
                    finishTerm(newCwd, null, interrupted = true)
                    return
                }
                continue
            }
            termStreamMisses = 0
            val text = stripAnsi(data)
            if (text.length < termStreamSeen) termStreamSeen = 0
            if (text.length > termStreamSeen) {
                appendChunk(text.substring(termStreamSeen))
                termStreamSeen = text.length
            }
            if (text.contains("__DONE=")) {
                val code = Regex("__DONE=(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                finishTerm(newCwd, code)
                return
            }
        }
    }

    private fun finishTerm(newCwd: String, code: Int?, interrupted: Boolean = false) {
        termStreamJob?.cancel()
        termStreamJob = null
        // добиваем незавершённую строку вывода
        termStreamTail?.takeIf { it.isNotBlank() }?.let { tail ->
            pushLines(listOf(TermLine(tail.trimEnd('\r'))))
            termStreamTail = null
        }
        val extra = mutableListOf<TermLine>()
        val produced = _state.value.term.lines.size > termStartLines
        if (!produced && !interrupted && code != null && code == 0) {
            extra.add(TermLine("(пусто)", isMeta = true))
        }
        if (interrupted) extra.add(TermLine("(связь потеряна)", isMeta = true))
        if (code != null && code != 0) {
            extra.add(TermLine("[код выхода: $code]", isMeta = true, isError = true))
        }
        _state.value = _state.value.copy(
            term = _state.value.term.copy(
                busy = false, streaming = false, pid = 0, cwd = newCwd,
                lines = _state.value.term.lines + extra
            )
        )
    }

    /** «Стоп»: убить процесс команды и его детей, почистить временные файлы. */
    fun stopTerm() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        val pid = _state.value.term.pid
        val useScript = termScriptMode == true
        viewModelScope.launch {
            if (useScript) {
                withContext(Dispatchers.IO) {
                    SystemInfo.execFull(base, token, "/usr/bin/mmterm", listOf("stop"), 10_000)
                }
            } else if (pid > 0) {
                withContext(Dispatchers.IO) {
                    SystemInfo.execFull(
                        base, token, "/bin/sh",
                        listOf(
                            "-c",
                            "for d in /proc/[0-9]*; do " +
                                "pp=\$(awk '{print \$4}' \$d/stat 2>/dev/null); " +
                                "[ \"\$pp\" = \"$pid\" ] && kill \${d#/proc/} 2>/dev/null; done; " +
                                "kill $pid 2>/dev/null; " +
                                "rm -f /tmp/.mm_term_out /tmp/.mm_cmd; true"
                        ),
                        10_000
                    )
                }
            }
            termStreamJob?.cancel()
            termStreamJob = null
            termStreamTail?.takeIf { it.isNotBlank() }?.let { tail ->
                pushLines(listOf(TermLine(tail.trimEnd('\r'))))
                termStreamTail = null
            }
            _state.value = _state.value.copy(
                term = _state.value.term.copy(
                    busy = false, streaming = false, pid = 0,
                    lines = _state.value.term.lines + TermLine("(прервано)", isMeta = true)
                )
            )
        }
    }

    /** Очистить экран терминала. */
    fun clearTerm() {
        _state.value = _state.value.copy(term = _state.value.term.copy(lines = emptyList()))
    }

    /** Следующая/предыдущая команда из истории. */
    fun termHistoryUp(): String? {
        termHistoryLoad()
        if (termHistory.isEmpty() || termHistoryIdx <= 0) return null
        termHistoryIdx--
        return termHistory[termHistoryIdx]
    }

    fun termHistoryDown(): String? {
        termHistoryLoad()
        if (termHistory.isEmpty() || termHistoryIdx >= termHistory.size - 1) return null
        termHistoryIdx++
        return termHistory[termHistoryIdx]
    }

    // ---------------- Настройка меню ----------------

    /** Загрузить скрытые разделы и раскладку нижней панели. */
    fun loadMenu() {
        val hidden = menuPrefs.getStringSet("hidden", emptySet()) ?: emptySet()
        val bottom = try {
            val arr = JSONArray(menuPrefs.getString("bottom", "") ?: "")
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }.ifEmpty { MenuUiState.DEFAULT_BOTTOM }
        _state.value = _state.value.copy(menu = MenuUiState(hidden = hidden, bottom = bottom))
    }

    /** Скрыть/показать раздел бокового меню (как «отключённый экран» в RouteriCh). */
    fun toggleMenuSection(id: String) {
        val cur = _state.value.menu
        val hidden = if (cur.hidden.contains(id)) cur.hidden - id else cur.hidden + id
        menuPrefs.edit().putStringSet("hidden", hidden).apply()
        _state.value = _state.value.copy(menu = cur.copy(hidden = hidden))
    }

    /** Назначить экран в слот нижней панели (0..3). */
    fun setBottomSlot(slot: Int, id: String) {
        val cur = _state.value.menu
        val list = cur.bottom.toMutableList()
        while (list.size <= slot) list.add("")
        list[slot] = id
        val arr = JSONArray().apply { list.forEach { put(it) } }
        menuPrefs.edit().putString("bottom", arr.toString()).apply()
        _state.value = _state.value.copy(menu = cur.copy(bottom = list))
    }

    /** Применить настройку меню (вызывается кнопкой «Сохранить»). */
    fun applyMenu(hidden: Set<String>, bottom: List<String>) {
        val clean = bottom.map { it.trim() }.filter { it.isNotBlank() }.take(4)
        menuPrefs.edit()
            .putStringSet("hidden", hidden)
            .putString("bottom", JSONArray().apply { clean.forEach { put(it) } }.toString())
            .apply()
        _state.value = _state.value.copy(menu = MenuUiState(hidden = hidden, bottom = clean))
    }

    /** Показать все разделы снова и вернуть нижнюю панель к умолчанию. */
    fun resetMenu() {
        menuPrefs.edit().clear().apply()
        _state.value = _state.value.copy(menu = MenuUiState())
    }

    // ---------------- Wake-on-LAN ----------------

    /** Загрузить сохранённые цели и интерфейс. */
    fun loadWol() {
        val arr = JSONArray(wolPrefs.getString("targets", "[]") ?: "[]")
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mac = o.optString("mac").trim()
            if (mac.isBlank()) return@mapNotNull null
            WolTarget(o.optString("name").ifBlank { mac }, mac)
        }
        _state.value = _state.value.copy(
            wol = _state.value.wol.copy(
                targets = list,
                iface = wolPrefs.getString("iface", "br-lan") ?: "br-lan"
            )
        )
    }

    /** Интерфейс, в который шлётся броадкаст. */
    fun setWolIface(iface: String) {
        val v = iface.trim().ifBlank { "br-lan" }
        wolPrefs.edit().putString("iface", v).apply()
        _state.value = _state.value.copy(wol = _state.value.wol.copy(iface = v))
    }

    fun addWolTarget(name: String, mac: String) {
        val m = normalizeMac(mac)
        if (m == null) {
            _state.value = _state.value.copy(
                wol = _state.value.wol.copy(error = "MAC должен быть вида AA:BB:CC:DD:EE:FF")
            )
            return
        }
        val n = name.trim().ifBlank { m }
        val list = _state.value.wol.targets.filterNot { it.mac == m } + WolTarget(n, m)
        saveWolTargets(list)
        _state.value = _state.value.copy(wol = _state.value.wol.copy(targets = list, error = null))
    }

    fun removeWolTarget(mac: String) {
        val list = _state.value.wol.targets.filterNot { it.mac == mac }
        saveWolTargets(list)
        _state.value = _state.value.copy(wol = _state.value.wol.copy(targets = list))
    }

    private fun saveWolTargets(list: List<WolTarget>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("mac", it.mac)) }
        wolPrefs.edit().putString("targets", arr.toString()).apply()
    }

    /** Отправить magic packet через роутер (etherwake). Если его нет — тихо ставим из репозитория. */
    fun sendWol(mac: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        val m = normalizeMac(mac) ?: return
        val iface = _state.value.wol.iface
        viewModelScope.launch {
            _state.value = _state.value.copy(
                wol = _state.value.wol.copy(busy = true, error = null, message = null)
            )
            val have = withContext(Dispatchers.IO) {
                SystemInfo.execFull(
                    base, token, "/bin/sh",
                    listOf("-c", "command -v /usr/bin/etherwake >/dev/null && echo yes || echo no"),
                    6_000
                )
            }
            if (have.stdout?.contains("yes") != true) {
                withContext(Dispatchers.IO) {
                    SystemInfo.execFull(
                        base, token, "/bin/sh",
                        listOf("-c", "apk add etherwake >/dev/null 2>&1; echo done"),
                        90_000
                    )
                }
            }
            val send = withContext(Dispatchers.IO) {
                SystemInfo.execFull(base, token, "/usr/bin/etherwake", listOf("-i", iface, m), 10_000)
            }
            if (send.ok) {
                _state.value = _state.value.copy(
                    wol = _state.value.wol.copy(
                        busy = false,
                        message = "Magic packet отправлен: $m через $iface"
                    )
                )
            } else {
                _state.value = _state.value.copy(
                    wol = _state.value.wol.copy(
                        busy = false,
                        error = "Не удалось: ${send.error ?: "нет ответа"}"
                    )
                )
            }
        }
    }

    /** Привести MAC к виду AA:BB:CC:DD:EE:FF; null — если формат неверный. */
    private fun normalizeMac(s: String): String? {
        val cleaned = s.trim().replace('-', ':').replace(" ", "").uppercase()
        val re = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        return if (re.matches(cleaned)) cleaned else null
    }

    // ---------------- Редактор файлов ----------------

    /** Открыть файл роутера во встроенном редакторе. Пустой путь — открыть редактор без файла. */
    fun openFileEdit(path: String) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        val p = path.trim()
        if (p.isEmpty()) {
            _state.value = _state.value.copy(fileEdit = FileEditUiState(open = true))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                fileEdit = FileEditUiState(open = true, path = p, loading = true)
            )
            val (data, err) = withContext(Dispatchers.IO) {
                SystemInfo.readFile(base, token, p)
            }
            if (data == null) {
                _state.value = _state.value.copy(
                    fileEdit = _state.value.fileEdit.copy(
                        loading = false,
                        error = "Не удалось прочитать: ${err ?: "нет ответа"}"
                    )
                )
                return@launch
            }
            _state.value = _state.value.copy(
                fileEdit = _state.value.fileEdit.copy(
                    loading = false, error = null, content = data, original = data
                )
            )
        }
    }

    /** Правка содержимого из UI. */
    fun editFileContent(value: String) {
        _state.value = _state.value.copy(fileEdit = _state.value.fileEdit.copy(content = value))
    }

    /** Закрыть редактор без сохранения. */
    fun closeFileEdit() {
        _state.value = _state.value.copy(fileEdit = FileEditUiState())
    }

    /** Сохранить: сначала бэкап <path>.bak, затем запись через file write. */
    fun saveFileEdit() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        val fe = _state.value.fileEdit
        if (!fe.open || fe.saving || fe.loading || fe.path.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                fileEdit = fe.copy(saving = true, error = null, message = null)
            )
            val bak = withContext(Dispatchers.IO) {
                SystemInfo.execFull(
                    base, token, "/bin/cp", listOf("-p", fe.path, fe.path + ".bak"), 10_000
                )
            }
            val writeErr = withContext(Dispatchers.IO) {
                SystemInfo.writeFile(base, token, fe.path, fe.content)
            }
            if (writeErr == null) {
                val note = buildString {
                    append("сохранено: ${fe.path} (${fe.content.length} символов)")
                    if (bak.ok) append(", бэкап: ${fe.path}.bak")
                }
                _state.value = _state.value.copy(
                    fileEdit = _state.value.fileEdit.copy(
                        saving = false, message = note, aclDenied = false,
                        content = fe.content, original = fe.content
                    ),
                    term = _state.value.term.copy(
                        lines = _state.value.term.lines + TermLine("[редактор] $note", isMeta = true)
                    )
                )
            } else {
                val denied = writeErr.contains("Access denied", true) ||
                    writeErr.contains("Доступ запрещ", true) ||
                    writeErr.contains("Permission denied", true)
                _state.value = _state.value.copy(
                    fileEdit = _state.value.fileEdit.copy(
                        saving = false, error = "Ошибка записи: $writeErr", aclDenied = denied
                    )
                )
            }
        }
    }

    /** ACL зашит в APK — приложение чинит его само, если запись в конфиги запрещена. */
    private val aclB64: String by lazy {
        val text = getApplication<Application>().assets.open("acl-modemmon.json")
            .bufferedReader().use { it.readText() }
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Самолечение ACL: пишем наш modemmon.json в /usr/share/rpcd/acl.d/ и перезапускаем rpcd.
     * Сессия ubus при этом умирает — перелогиниваемся сохранёнными кредами.
     */
    fun fixFileAcl() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                fileEdit = _state.value.fileEdit.copy(saving = true, error = null)
            )
            val r = withContext(Dispatchers.IO) {
                SystemInfo.execFull(
                    base, token, "/bin/sh",
                    listOf(
                        "-c",
                        "echo $aclB64 | base64 -d > /usr/share/rpcd/acl.d/modemmon.json && " +
                            "/etc/init.d/rpcd restart 2>/dev/null; sleep 1; echo done"
                    ),
                    25_000
                )
            }
            if (r.ok) {
                val user = _state.value.rememberUser
                val pass = _state.value.rememberPass
                if (user != null && pass != null) {
                    ubusLogin(user, pass, remember = true, silent = true)
                }
                _state.value = _state.value.copy(
                    fileEdit = _state.value.fileEdit.copy(
                        saving = false, aclDenied = false,
                        message = "ACL обновлён, rpcd перезапущен — попробуй сохранить снова"
                    )
                )
            } else {
                _state.value = _state.value.copy(
                    fileEdit = _state.value.fileEdit.copy(
                        saving = false,
                        error = "Не удалось обновить ACL: ${r.error ?: "нет ответа"}"
                    )
                )
            }
        }
    }

    // --- служебное для терминала ---

    private fun pushLines(new: List<TermLine>) {
        val cur = _state.value.term
        var lines = cur.lines + new
        if (lines.size > 800) lines = lines.takeLast(800)
        _state.value = _state.value.copy(term = cur.copy(lines = lines))
    }

    private fun appendChunk(chunk: String) {
        var data = chunk
        // доклеиваем хвост предыдущего полла (строка могла быть отрезана посередине)
        termStreamTail?.let { data = it + data; termStreamTail = null }
        if (!data.endsWith("\n")) {
            val nl = data.lastIndexOf('\n')
            if (nl >= 0) {
                termStreamTail = data.substring(nl + 1)
                data = data.substring(0, nl + 1)
            } else {
                termStreamTail = data
                return
            }
        }
        val lines = data.split('\n')
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.contains("__DONE=") }
        if (lines.isNotEmpty()) pushLines(lines.map { TermLine(it) })
    }

    private fun hintAcl(err: String?) {
        val e = err ?: return
        val denied = e.contains("Access denied", true) ||
            e.contains("Доступ запрещ", true) ||
            e.contains("Permission denied", true)
        if (denied) {
            pushLines(
                listOf(
                    TermLine(
                        "Подсказка: rpcd не даёт exec. В /usr/share/rpcd/acl.d/ у секции access " +
                            "нужен \"file\": { \"exec\": ... } и путь команды в \"write\".",
                        isMeta = true
                    )
                )
            )
        }
    }

    /** cd: поддержаны cd, cd ~, cd .., cd /abs, cd rel. */
    private fun resolveCwd(current: String, arg: String): String {
        if (arg.isEmpty() || arg == "~") return "/root"
        if (arg == "-") return current
        var target = arg
        if (!target.startsWith("/")) target = current.trimEnd('/') + "/" + target
        val parts = mutableListOf<String>()
        target.split('/').forEach { p ->
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(p)
            }
        }
        return "/" + parts.joinToString("/")
    }

    private val ansiRegex = Regex(
        "\u001B\\[[0-9;]*[A-Za-z]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)"
    )

    private fun stripAnsi(s: String): String = s.replace(ansiRegex, "").replace('\r', ' ')

    private val dangerousRegex = Regex(
        "(?:^|[;&|\\s])(?:network\\s+reload|reboot|sysupgrade|firstboot|mkfs|dd\\s+if=|mtd\\s+write|rm\\s+-rf\\s+/(?:\\s|\$))",
        RegexOption.IGNORE_CASE
    )

    /** Сводка: модель, прошивка, uptime, память, нагрузка, температуры. */
    fun loadOverview() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        // Модем для опроса температуры: выбранный или первый с AT-портом
        val devId = _state.value.modem.selectedId
            ?: _state.value.modem.devices.firstOrNull { it.ok && it.ttyPorts.isNotEmpty() }?.id
        viewModelScope.launch {
            _state.value = _state.value.copy(sys = _state.value.sys.copy(loading = true, error = null))
            val rows = withContext(Dispatchers.IO) {
                val out = mutableListOf<Pair<String, String>>()
                val (board, err) = SystemInfo.board(base, token)
                if (board != null) {
                    // model — объект {id, name}, а не строка
                    val modelName = board.optJSONObject("model")?.optString("name")
                        ?.ifBlank { null }
                        ?: board.optString("model").takeIf { it.isNotBlank() && !it.startsWith("{") }
                    modelName?.let { out.add("Модель" to it) }

                    board.optString("hostname").takeIf { it.isNotBlank() }
                        ?.let { out.add("Имя" to it) }
                } else if (err != null) {
                    out.add("Ошибка" to err)
                }

                // Прошивка и ядро — отдельными файлами
                SystemInfo.readFile(base, token, "/etc/openwrt_release").first
                    ?.lines()
                    ?.firstOrNull { it.startsWith("DISTRIB_DESCRIPTION") }
                    ?.substringAfter("=")
                    ?.trim()?.trim('\'', '"')
                    ?.takeIf { it.isNotBlank() }
                    ?.let { out.add("Прошивка" to it) }

                SystemInfo.readFile(base, token, "/proc/sys/kernel/osrelease").first
                    ?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { out.add("Ядро" to it) }

                var uptimeText: String? = null
                val uptime = SystemInfo.readFile(base, token, "/proc/uptime").first
                uptime?.split(" ")?.firstOrNull()?.toDoubleOrNull()?.let { sec ->
                    val d = sec.toLong() / 86400
                    val h = (sec.toLong() % 86400) / 3600
                    val m = (sec.toLong() % 3600) / 60
                    uptimeText = "${d}д ${h}ч ${m}м"
                }

                var ramText: String? = null
                var ramPct: Int? = null
                val mem = SystemInfo.readFile(base, token, "/proc/meminfo").first
                if (mem != null) {
                    val total = mem.lines().firstOrNull { it.startsWith("MemTotal") }
                        ?.filter { it.isDigit() }?.toLongOrNull()
                    val avail = mem.lines().firstOrNull { it.startsWith("MemAvailable") }
                        ?.filter { it.isDigit() }?.toLongOrNull()
                    if (total != null && avail != null && total > 0) {
                        ramPct = (((total - avail) * 100) / total).toInt()
                        ramText = "${(total - avail) / 1024} из ${total / 1024} МБ"
                    }
                }

                var loadPct: Int? = null
                val cores = SystemInfo.readFile(base, token, "/proc/cpuinfo").first
                    ?.lines()?.count { it.startsWith("processor") } ?: 0
                SystemInfo.readFile(base, token, "/proc/loadavg").first
                    ?.split(" ")?.firstOrNull()?.toFloatOrNull()?.let { l1 ->
                        if (cores > 0) {
                            loadPct = ((l1 / cores) * 100).toInt().coerceIn(0, 100)
                            out.add("Load average" to "$l1 / $cores ядер")
                        } else {
                            out.add("Load average" to l1.toString())
                        }
                    }

                // Температура роутера: берём cpu-thermal, иначе самую горячую зону
                val zones = SystemInfo.thermals(base, token).first
                val routerTemp = zones["cpu-thermal"] ?: zones.values.maxOrNull()

                // Температура модема: проприетарная AT^TEMP? (Qualcomm)
                var modemTemp: Double? = null
                if (devId != null) {
                    val reply = MedveModem.atCommand(base, token, devId, "AT^TEMP?", 3000).first
                    val temps = reply?.lines.orEmpty().mapNotNull { line ->
                        Regex("(\\d+)\\s*C").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                    }
                    modemTemp = temps.maxOrNull()
                }

                OverviewData(out, ramPct, loadPct, cores, uptimeText, ramText, routerTemp, modemTemp)
            }
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(
                    overview = rows.rows,
                    ramPercent = rows.ramPercent,
                    loadPercent = rows.loadPercent,
                    cores = rows.cores,
                    uptimeText = rows.uptimeText,
                    ramText = rows.ramText,
                    routerTemp = rows.routerTemp,
                    modemTemp = rows.modemTemp,
                    loading = false
                )
            )
        }
    }

    private data class OverviewData(
        val rows: List<Pair<String, String>>,
        val ramPercent: Int?,
        val loadPercent: Int?,
        val cores: Int,
        val uptimeText: String?,
        val ramText: String?,
        val routerTemp: Double? = null,
        val modemTemp: Double? = null
    )

    /** Сохранить набор опций интерфейса (форма «Изменить»). */
    fun saveIface(iface: String, values: Map<String, Any>, apply: Boolean = true) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(cfg = _state.value.cfg.copy(message = "Сохраняю…"))
            val err = withContext(Dispatchers.IO) {
                UciConfig.setMany(base, token, "network", iface, values)
            }
            if (err == null && apply) {
                withContext(Dispatchers.IO) {
                    Ubus.call(base, token, "network", "reload", org.json.JSONObject(), 40_000)
                }
            }
            _state.value = _state.value.copy(
                cfg = _state.value.cfg.copy(
                    message = err?.let { "Ошибка: $it" } ?: "Сохранено и применено"
                )
            )
            loadNetwork()
        }
    }

    /** Применить изменения сети (после правки /etc/config/network). */
    fun applyNetwork() {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(cfg = _state.value.cfg.copy(message = "Применяю…"))
            val reply = withContext(Dispatchers.IO) {
                Ubus.call(base, token, "network", "reload", org.json.JSONObject(), 40_000)
            }
            _state.value = _state.value.copy(
                cfg = _state.value.cfg.copy(
                    message = if (reply.ok) "Сеть применена" else "Ошибка: ${reply.error}"
                )
            )
            loadNetwork()
        }
    }

    // ---------- Управление ----------

    private fun action(label: String, block: suspend (String, String) -> String?) {
        val token = ubusToken ?: return
        val base = _state.value.settings.baseUrl
        viewModelScope.launch {
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(actionResult = "$label…", loading = true)
            )
            val err = block(base, token)
            _state.value = _state.value.copy(
                sys = _state.value.sys.copy(
                    actionResult = err?.let { "Ошибка: $it" } ?: "$label — готово",
                    loading = false
                )
            )
        }
    }

    fun serviceAction(name: String, act: String) = action("$name: $act") { base, token ->
        Actions.serviceAction(base, token, name, act).also { if (it == null) loadServices() }
    }

    fun ifaceToggle(name: String, up: Boolean) = action(if (up) "$name поднять" else "$name опустить") { base, token ->
        Actions.ifaceUpDown(base, token, name, up).also { if (it == null) loadNetwork() }
    }

    fun setIfaceMetric(name: String, metric: Int) = action("$name: метрика $metric") { base, token ->
        Actions.setMetric(base, token, name, metric).also { if (it == null) loadNetwork() }
    }

    fun renewIface(name: String) = action("$name: перезапуск") { base, token ->
        Network.renew(base, token, name).also { if (it == null) loadNetwork() }
    }

    fun packageAction(name: String, install: Boolean) =
        action(if (install) "Установка $name" else "Удаление $name") { base, token ->
            val (out, err) = Actions.apk(
                base, token,
                if (install) listOf("add", name) else listOf("del", name)
            )
            if (err == null) loadPackages()
            err ?: (if (out.isNullOrBlank()) null else null)
        }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
