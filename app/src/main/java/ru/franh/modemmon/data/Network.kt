package ru.franh.modemmon.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Сеть, клиенты, журнал и диагностика — через ubus.
 * Только чтение и ping; изменения конфигурации сюда не входят.
 */
object Network {

    /** Сетевой интерфейс роутера. */
    data class Iface(
        val name: String,
        val up: Boolean,
        val device: String,
        val proto: String,
        val metric: Int,
        val ip: String?,
        val uptime: Long,
        val rx: Long,
        val tx: Long
    )

    /** Аренда DHCP. */
    data class Lease(
        val hostname: String?,
        val mac: String,
        val ip: String,
        val expires: Long
    )

    fun interfaces(baseUrl: String, token: String): Pair<List<Iface>, String?> {
        val reply = Ubus.call(baseUrl, token, "network.interface", "dump", JSONObject())
        if (!reply.ok) return emptyList<Iface>() to reply.error
        val arr = reply.data?.optJSONArray("interface")
            ?: return emptyList<Iface>() to "Нет списка интерфейсов"
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("interface").ifBlank { return@mapNotNull null }
            val device = o.optString("l3_device")
            val stats = if (device.isNotBlank()) deviceStats(baseUrl, token, device) else null
            Iface(
                name = name,
                up = o.optBoolean("up"),
                device = device,
                proto = o.optString("proto"),
                metric = o.optInt("metric"),
                ip = o.optJSONArray("ipv4-address")
                    ?.optJSONObject(0)
                    ?.optString("address")
                    ?.ifBlank { null },
                uptime = o.optLong("uptime"),
                rx = stats?.first ?: 0L,
                tx = stats?.second ?: 0L
            )
        }.sortedWith(compareByDescending<Iface> { it.up }.thenBy { it.metric })
        return list to null
    }

    /** Счётчики устройства: принято/передано байт. */
    private fun deviceStats(baseUrl: String, token: String, device: String): Pair<Long, Long>? {
        val reply = Ubus.call(
            baseUrl, token, "network.device", "status",
            JSONObject().put("name", device), 8_000
        )
        if (!reply.ok) return null
        val st = reply.data?.optJSONObject("statistics") ?: return null
        return st.optLong("rx_bytes") to st.optLong("tx_bytes")
    }

    /** Перезапуск подключения интерфейса (renew для DHCP, reconnect для остальных). */
    fun renew(baseUrl: String, token: String, iface: String): String? {
        val reply = Ubus.call(
            baseUrl, token, "network.interface.$iface", "renew", JSONObject(), 40_000
        )
        return if (reply.ok) null else reply.error
    }

    fun leases(baseUrl: String, token: String): Pair<List<Lease>, String?> {
        val reply = Ubus.call(baseUrl, token, "luci-rpc", "getDHCPLeases", JSONObject())
        if (!reply.ok) return emptyList<Lease>() to reply.error
        val arr = reply.data?.optJSONArray("dhcp_leases")
            ?: return emptyList<Lease>() to "Нет аренд DHCP"
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ip = o.optString("ipaddr").ifBlank { return@mapNotNull null }
            Lease(
                hostname = o.optString("hostname").ifBlank { null },
                mac = o.optString("macaddr"),
                ip = ip,
                expires = o.optLong("expires")
            )
        }
        return list to null
    }

    /**
     * Запуск ping через rpcd (file exec).
     * [iface] пустой — по таблице маршрутизации.
     */
    fun ping(
        baseUrl: String,
        token: String,
        target: String,
        iface: String?,
        count: Int = 3
    ): Pair<String?, String?> {
        val params = JSONArray().put("-c").put(count.toString())
            .put("-W").put("2")
        if (!iface.isNullOrBlank()) params.put("-I").put(iface)
        params.put(target)
        val args = JSONObject().put("command", "/bin/ping").put("params", params)
        val reply = Ubus.call(baseUrl, token, "file", "exec", args, timeoutMs = 20_000)
        if (!reply.ok) return null to reply.error
        val out = reply.data?.optString("stdout").orEmpty()
        val err = reply.data?.optString("stderr").orEmpty()
        if (out.isBlank() && err.isNotBlank()) return null to err.trim()
        return out.trim() to null
    }

    /** Последние строки системного журнала. */
    fun log(baseUrl: String, token: String, lines: Int = 100): Pair<List<String>, String?> {
        val args = JSONObject()
            .put("lines", lines)
            .put("oneshot", true)
            .put("stream", false)
        val reply = Ubus.call(baseUrl, token, "log", "read", args, timeoutMs = 10_000)
        if (!reply.ok) return emptyList<String>() to reply.error
        val arr = reply.data?.optJSONArray("log")
            ?: return emptyList<String>() to "Журнал пуст"
        val list = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("msg")?.ifBlank { null }
        }
        return list.reversed() to null
    }
}
