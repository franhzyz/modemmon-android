package ru.franh.modemmon.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Системные данные роутера: пакеты, службы, LED, процессы, файлы, Wi-Fi.
 * Только чтение; единственное действие — перезагрузка (с подтверждением в UI).
 */
object SystemInfo {

    fun packages(baseUrl: String, token: String): Pair<List<Pair<String, String>>, String?> {
        val reply = Ubus.call(baseUrl, token, "rpc-sys", "packagelist", JSONObject(), 20_000)
        if (!reply.ok) return emptyList<Pair<String, String>>() to reply.error
        val obj = reply.data?.optJSONObject("packages")
            ?: return emptyList<Pair<String, String>>() to "Нет списка пакетов"
        val list = obj.keys().asSequence()
            .map { it to obj.optString(it) }
            .sortedBy { it.first }
            .toList()
        return list to null
    }

    /** Службы автозапуска: имя, включена, порядок. */
    fun services(baseUrl: String, token: String): Pair<List<Triple<String, Boolean, Int>>, String?> {
        val reply = Ubus.call(baseUrl, token, "luci", "getInitList", JSONObject(), 15_000)
        if (!reply.ok) return emptyList<Triple<String, Boolean, Int>>() to reply.error
        val d = reply.data ?: return emptyList<Triple<String, Boolean, Int>>() to "Пустой ответ"
        val list = d.keys().asSequence().map { name ->
            val o = d.optJSONObject(name)
            Triple(name, o?.optBoolean("enabled") ?: false, o?.optInt("index") ?: 0)
        }.sortedBy { it.third }.toList()
        return list to null
    }

    fun leds(baseUrl: String, token: String): Pair<List<String>, String?> {
        val reply = Ubus.call(baseUrl, token, "luci", "getLEDs", JSONObject())
        if (!reply.ok) return emptyList<String>() to reply.error
        val d = reply.data ?: return emptyList<String>() to "Пустой ответ"
        return d.keys().asSequence().sorted().toList() to null
    }

    /** Таблица процессов: строки вывода ps, первая — заголовок. */
    fun processes(baseUrl: String, token: String): Pair<List<String>, String?> {
        val args = JSONObject().put("command", "/bin/ps").put("params", JSONArray().put("w"))
        val reply = Ubus.call(baseUrl, token, "file", "exec", args, 10_000)
        if (!reply.ok) return emptyList<String>() to reply.error
        val out = reply.data?.optString("stdout").orEmpty()
        if (out.isBlank()) return emptyList<String>() to "Пустой вывод ps"
        return out.trim().lines() to null
    }

    fun readFile(baseUrl: String, token: String, path: String): Pair<String?, String?> {
        val args = JSONObject().put("path", path).put("base64", false)
        val reply = Ubus.call(baseUrl, token, "file", "read", args, 10_000)
        if (!reply.ok) return null to reply.error
        return reply.data?.optString("data") to null
    }

    /** Полная перезапись файла (используется для /etc/crontabs/root). */
    fun writeFile(baseUrl: String, token: String, path: String, data: String): String? {
        val args = JSONObject()
            .put("path", path)
            .put("data", data)
            .put("append", false)
            .put("base64", false)
        val reply = Ubus.call(baseUrl, token, "file", "write", args, 15_000)
        return if (reply.ok) null else reply.error
    }

    /** Беспроводные устройства (phy). */
    fun wifiDevices(baseUrl: String, token: String): Pair<List<String>, String?> {
        val reply = Ubus.call(baseUrl, token, "iwinfo", "devices", JSONObject())
        if (!reply.ok) return emptyList<String>() to reply.error
        val arr = reply.data?.optJSONArray("devices")
            ?: return emptyList<String>() to "Нет Wi-Fi"
        return (0 until arr.length()).map { arr.optString(it) } to null
    }

    /** Подключённые к точке клиенты. */
    fun wifiClients(baseUrl: String, token: String, device: String): Pair<List<String>, String?> {
        val reply = Ubus.call(
            baseUrl, token, "iwinfo", "assoclist",
            JSONObject().put("device", device), 10_000
        )
        if (!reply.ok) return emptyList<String>() to reply.error
        val arr = reply.data?.optJSONArray("results")
            ?: return emptyList<String>() to "Нет клиентов"
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mac = o.optString("mac").ifBlank { return@mapNotNull null }
            "$mac · ${o.optInt("signal")} dBm"
        }
        return list to null
    }

    /** Произвольная команда через rpcd (file exec). */
    fun exec(
        baseUrl: String,
        token: String,
        command: String,
        params: List<String> = emptyList(),
        timeoutMs: Int = 20_000
    ): Pair<String?, String?> {
        val arr = JSONArray()
        params.forEach { arr.put(it) }
        val req = JSONObject().put("command", command).put("params", arr)
        val reply = Ubus.call(baseUrl, token, "file", "exec", req, timeoutMs)
        if (!reply.ok) return null to reply.error
        val d = reply.data ?: return null to "Пустой ответ"
        val out = d.optString("stdout").orEmpty()
        val errText = d.optString("stderr").orEmpty()
        if (out.isBlank() && errText.isNotBlank()) return null to errText.trim()
        return out to null
    }

    /** Полный результат file exec: stdout, stderr и код возврата процесса. */
    data class ExecResult(
        val stdout: String?,
        val stderr: String?,
        val code: Int?,
        val error: String?
    ) {
        val ok: Boolean get() = error == null
    }

    /** exec без потери stderr и с кодом возврата (у exec() stderr пропадает при непустом stdout). */
    fun execFull(
        baseUrl: String,
        token: String,
        command: String,
        params: List<String> = emptyList(),
        timeoutMs: Int = 20_000
    ): ExecResult {
        val arr = JSONArray()
        params.forEach { arr.put(it) }
        val req = JSONObject().put("command", command).put("params", arr)
        val reply = Ubus.call(baseUrl, token, "file", "exec", req, timeoutMs)
        if (!reply.ok) return ExecResult(null, null, null, reply.error)
        val d = reply.data ?: return ExecResult(null, null, null, "Пустой ответ")
        return ExecResult(
            stdout = d.optString("stdout").orEmpty(),
            stderr = d.optString("stderr").orEmpty(),
            code = if (d.has("code")) d.optInt("code") else null,
            error = null
        )
    }

    /** Температуры thermal-зон: тип → °C. */
    fun thermals(baseUrl: String, token: String): Pair<Map<String, Double>, String?> {
        val script = "for z in /sys/class/thermal/thermal_zone*; do " +
            "printf '%s=%s\\n' \"$(cat \$z/type 2>/dev/null)\" \"$(cat \$z/temp 2>/dev/null)\"; done"
        val (out, err) = exec(baseUrl, token, "/bin/sh", listOf("-c", script), 10_000)
        if (out == null) return emptyMap<String, Double>() to err
        val map = out.lines().mapNotNull { line ->
            val parts = line.split("=")
            if (parts.size != 2) return@mapNotNull null
            val name = parts[0].trim()
            val milli = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            name to milli / 1000.0
        }.toMap()
        return map to null
    }

    /** Частоты ядер и governor из cpufreq (в /proc/cpuinfo их нет на ARM). */
    data class CpuFreq(
        val governor: String,
        val maxMhz: Float,
        val minMhz: Float,
        val coresMhz: List<Float>
    )

    fun cpuFreq(baseUrl: String, token: String): Pair<CpuFreq?, String?> {
        val script = "echo gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null); " +
            "echo max=$(cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null); " +
            "echo min=$(cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq 2>/dev/null); " +
            "for f in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq; do " +
            "echo cur=$(cat \$f 2>/dev/null); done"
        val (out, err) = exec(baseUrl, token, "/bin/sh", listOf("-c", script), 10_000)
        if (out == null) return null to err

        var gov = ""
        var max = 0f
        var min = 0f
        val cores = mutableListOf<Float>()
        out.lines().forEach { line ->
            when {
                line.startsWith("gov=") -> gov = line.removePrefix("gov=").trim()
                line.startsWith("max=") -> max = line.removePrefix("max=").trim()
                    .toFloatOrNull()?.div(1000f) ?: 0f
                line.startsWith("min=") -> min = line.removePrefix("min=").trim()
                    .toFloatOrNull()?.div(1000f) ?: 0f
                line.startsWith("cur=") -> line.removePrefix("cur=").trim()
                    .toFloatOrNull()?.div(1000f)?.let { cores.add(it) }
            }
        }
        if (cores.isEmpty()) {
            // Виртуалка (QEMU и т.п.) без cpufreq: текущая частота из /proc/cpuinfo
            val (ci, _) = exec(baseUrl, token, "/bin/sh", listOf("-c",
                "awk '/^cpu MHz/{printf \"cur=%.0f\\n\", \$4}' /proc/cpuinfo"), 10_000)
            ci?.lines()?.forEach { l ->
                if (l.startsWith("cur=")) l.removePrefix("cur=").trim()
                    .toFloatOrNull()?.let { cores.add(it) }
            }
            if (cores.isEmpty()) return null to "cpufreq недоступен"
            return CpuFreq("", 0f, 0f, cores) to null
        }
        return CpuFreq(gov, max, min, cores) to null
    }

    /** Состояние службы: работает ли и включён ли автозапуск. */
    data class ServiceStatus(val running: Boolean, val enabled: Boolean)

    fun serviceStatus(baseUrl: String, token: String, name: String): Pair<ServiceStatus?, String?> {
        val script = "S=\$(/etc/init.d/$name status 2>&1); " +
            "case \"\$S\" in *running*) echo run=1;; *) echo run=0;; esac; " +
            "/etc/init.d/$name enabled >/dev/null 2>&1 && echo en=1 || echo en=0"
        val (out, err) = exec(baseUrl, token, "/bin/sh", listOf("-c", script), 10_000)
        if (out == null) return null to err
        var running = false
        var enabled = false
        out.lines().forEach { line ->
            when {
                line.startsWith("run=") -> running = line.endsWith("1")
                line.startsWith("en=") -> enabled = line.endsWith("1")
            }
        }
        return ServiceStatus(running, enabled) to null
    }

    /** Архивы конфигурации в /tmp: имя и размер в байтах. */
    fun backups(baseUrl: String, token: String): Pair<List<Pair<String, Long>>, String?> {
        val (out, err) = exec(baseUrl, token, "/bin/ls", listOf("-l", "/tmp"))
        if (out == null) return emptyList<Pair<String, Long>>() to err
        val list = out.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 9) return@mapNotNull null
            val name = parts.last()
            if (!name.endsWith(".tar.gz") && !name.endsWith(".bin")) return@mapNotNull null
            val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            name to size
        }.sortedByDescending { it.second }
        return list to null
    }

    /** Создать архив конфигурации: sysupgrade -b /tmp/<name>. */
    fun createBackup(baseUrl: String, token: String, name: String): String? {
        val (out, err) = exec(
            baseUrl, token, "/sbin/sysupgrade", listOf("-b", "/tmp/$name"), 60_000
        )
        if (err != null) return err
        if (out.isNullOrBlank()) return null
        return null
    }

    /** Сведения о железе и прошивке. */
    fun board(baseUrl: String, token: String): Pair<JSONObject?, String?> {
        val reply = Ubus.call(baseUrl, token, "luci-rpc", "getBoardJSON", JSONObject(), 10_000)
        if (!reply.ok) return null to reply.error
        return reply.data to null
    }

    /** Перезагрузка роутера. Опасное действие — подтверждается в UI. */
    fun reboot(baseUrl: String, token: String): String? {
        val reply = Ubus.call(baseUrl, token, "rpc-sys", "reboot", JSONObject(), 10_000)
        return if (reply.ok) null else reply.error
    }
}
