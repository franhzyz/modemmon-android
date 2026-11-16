package ru.franh.modemmon.data

/**
 * Speed test: замер канала роутера через speed.cloudflare.com.
 *
 * Тест выполняется НА РОУТЕРЕ (/bin/sh -c через file exec), поэтому меряется
 * именно модемный канал устройства, а не телефон. Трафик теста списывается
 * с тарифа — объём задаётся параметрами (downMb/upMb).
 *
 * Скрипт намеренно без shell-переменных: все числа подставляет Kotlin,
 * а разбор вывода идёт здесь по текстовым секциям:
 *   ping_start..ping_end / down_start..down_end / up_start..up_end / iface_start..iface_end
 */
object SpeedTest {

    data class Result(
        val pingMs: Double?,
        val downMbps: Double?,
        val upMbps: Double?,
        val iface: String,
        val downBytes: Long,
        val upBytes: Long,
        val seconds: Int
    ) {
        /** Сколько трафика съел тест, байт. */
        val usedBytes: Long get() = downBytes + upBytes
    }

    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val UP_URL = "https://speed.cloudflare.com/__up"
    private const val PING_HOST = "1.1.1.1"

    /** Сборка скрипта. Числа подставляются напрямую — shell-переменных нет. */
    fun script(downBytes: Long, upBytes: Long, withUp: Boolean): String {
        val upBlocks = upBytes / 1048576L
        val sb = StringBuilder()
        sb.append("echo ping_start\n")
        sb.append("ping -c 4 -W 2 ").append(PING_HOST).append(" 2>/dev/null | tail -n 1\n")
        sb.append("echo ping_end\n")
        sb.append("echo down_start\n")
        sb.append("curl -o /dev/null -sS -w '%{speed_download} %{size_download}' --max-time 240 '")
            .append(DOWN_URL).append(downBytes).append("'\n")
        sb.append("echo down_end\n")
        if (withUp && upBlocks > 0) {
            sb.append("echo up_start\n")
            sb.append("dd if=/dev/zero bs=1048576 count=").append(upBlocks)
                .append(" 2>/dev/null | curl -o /dev/null -sS -w '%{speed_upload} %{size_upload}'")
                .append(" --max-time 240 --data-binary @- '").append(UP_URL).append("'\n")
            sb.append("echo up_end\n")
        }
        sb.append("echo iface_start\n")
        sb.append("ip route get ").append(PING_HOST).append(" 2>/dev/null | head -n 1\n")
        sb.append("echo iface_end\n")
        return sb.toString()
    }

    /** Разбор вывода скрипта. */
    fun parse(out: String, downBytes: Long, upBytes: Long, seconds: Int): Result {
        var ping: Double? = null
        var down: Double? = null
        var up: Double? = null
        var iface = ""
        var dBytes = downBytes
        var uBytes = if (upBytes > 0) upBytes else 0L
        var section = ""

        for (raw in out.lines()) {
            val line = raw.trim()
            when (line) {
                "ping_start" -> section = "ping"
                "down_start" -> section = "down"
                "up_start" -> section = "up"
                "iface_start" -> section = "iface"
                "ping_end", "down_end", "up_end", "iface_end" -> section = ""
                else -> when (section) {
                    "ping" -> {
                        // "round-trip min/avg/max = 73.044/80.688/84.317 ms" → avg
                        if (ping == null && line.contains("=")) {
                            ping = line.substringAfterLast("=").trim()
                                .split("/").getOrNull(1)?.trim()?.trimEnd('m', 's', ' ')
                                ?.toDoubleOrNull()
                        }
                    }
                    "down" -> {
                        // "2503341.000000 5000000" → байт/с и фактически скачано
                        if (down == null) {
                            val parts = line.split(Regex("\\s+"))
                            val speed = parts.getOrNull(0)?.toDoubleOrNull()
                            if (speed != null && speed > 0) {
                                down = speed * 8.0 / 1_000_000.0
                                parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { dBytes = it }
                            }
                        }
                    }
                    "up" -> {
                        if (up == null) {
                            val parts = line.split(Regex("\\s+"))
                            val speed = parts.getOrNull(0)?.toDoubleOrNull()
                            if (speed != null && speed > 0) {
                                up = speed * 8.0 / 1_000_000.0
                                parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { uBytes = it }
                            }
                        }
                    }
                    "iface" -> {
                        // "1.1.1.1 via 100.79.26.37 dev wwan0 src ... uid 0"
                        if (iface.isBlank()) {
                            val m = Regex(""" dev ([a-zA-Z0-9.@_-]+) """).find(line)
                            iface = m?.groupValues?.get(1).orEmpty()
                        }
                    }
                }
            }
        }
        return Result(ping, down, up, iface, dBytes, uBytes, seconds)
    }

    /**
     * Запуск теста на роутере. Блокирующий — вызывать из Dispatchers.IO.
     * Возвращает (результат, ошибка).
     */
    fun run(
        baseUrl: String,
        token: String,
        downMb: Int,
        upMb: Int,
        withUp: Boolean,
        startedAtMs: Long
    ): Pair<Result?, String?> {
        val downBytes = downMb.toLong() * 1024L * 1024L
        val upBytes = if (withUp) upMb.toLong() * 1024L * 1024L else 0L
        val r = SystemInfo.execFull(
            baseUrl, token, "/bin/sh",
            listOf("-c", script(downBytes, upBytes, withUp)),
            300_000
        )
        if (!r.ok) return null to (r.error ?: "Ошибка выполнения на роутере")
        val out = r.stdout.orEmpty()
        if (out.isBlank()) {
            return null to (r.stderr?.trim()?.takeIf { it.isNotBlank() } ?: "Роутер не ответил")
        }
        val seconds = ((System.currentTimeMillis() - startedAtMs) / 1000).toInt().coerceIn(1, 599)
        val res = parse(out, downBytes, upBytes, seconds)
        if (res.downMbps == null && res.upMbps == null) {
            return null to "Замер не удался: speed.cloudflare.com недоступен (проверьте канал)"
        }
        return res to null
    }
}
