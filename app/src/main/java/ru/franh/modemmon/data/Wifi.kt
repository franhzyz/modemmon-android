package ru.franh.modemmon.data

import org.json.JSONObject

/** Сканирование Wi-Fi: точки вокруг и загрузка каналов. */
object Wifi {

    data class Ap(
        val ssid: String,
        val bssid: String,
        val band: Int,
        val channel: Int,
        val signal: Int,
        val secure: Boolean
    )

    /** Параметры точки доступа. */
    data class RadioInfo(
        val ssid: String,
        val bssid: String,
        val channel: Int,
        val frequency: Int,
        val txpower: Int,
        val mode: String,
        val hwmode: String,
        val htmode: String,
        val encryption: String,
        val country: String
    ) {
        val band: String get() = if (frequency >= 5000) "5 ГГц" else "2.4 ГГц"
    }

    /** Подключённый клиент. */
    data class Client(
        val mac: String,
        val signal: Int,
        val connectedSec: Long,
        val authorized: Boolean
    )

    fun info(baseUrl: String, token: String, device: String): Pair<RadioInfo?, String?> {
        val reply = Ubus.call(
            baseUrl, token, "iwinfo", "info",
            JSONObject().put("device", device), 10_000
        )
        if (!reply.ok) return null to reply.error
        val d = reply.data ?: return null to "Нет данных"
        val enc = d.optJSONObject("encryption")
        val encText = if (enc?.optBoolean("enabled") == true) {
            val wpa = enc.optJSONArray("wpa")?.let { a ->
                (0 until a.length()).map { a.optInt(it) }.maxOrNull()
            }
            val auth = enc.optJSONArray("authentication")?.optString(0)?.uppercase() ?: ""
            buildString {
                append(if (wpa != null && wpa > 0) "WPA$wpa" else "WPA")
                if (auth.isNotBlank()) append(" $auth")
            }
        } else {
            "открытая"
        }
        return RadioInfo(
            ssid = d.optString("ssid").ifBlank { "(без имени)" },
            bssid = d.optString("bssid"),
            channel = d.optInt("channel"),
            frequency = d.optInt("frequency"),
            txpower = d.optInt("txpower"),
            mode = d.optString("mode"),
            hwmode = d.optString("hwmode"),
            htmode = d.optString("htmode"),
            encryption = encText,
            country = d.optString("country")
        ) to null
    }

    fun assoc(baseUrl: String, token: String, device: String): List<Client> {
        val reply = Ubus.call(
            baseUrl, token, "iwinfo", "assoclist",
            JSONObject().put("device", device), 10_000
        )
        if (!reply.ok) return emptyList()
        val arr = reply.data?.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Client(
                mac = o.optString("mac"),
                signal = o.optInt("signal"),
                connectedSec = o.optLong("connected_time"),
                authorized = o.optBoolean("authorized")
            )
        }.sortedByDescending { it.signal }
    }

    /** Беспроводные устройства (phy). */
    fun devices(baseUrl: String, token: String): Pair<List<String>, String?> {
        val reply = Ubus.call(baseUrl, token, "iwinfo", "devices", JSONObject())
        if (!reply.ok) return emptyList<String>() to reply.error
        val arr = reply.data?.optJSONArray("devices")
            ?: return emptyList<String>() to "Нет Wi-Fi"
        return (0 until arr.length()).map { arr.optString(it) } to null
    }

    /** Точки вокруг. Сканирование занимает несколько секунд. */
    fun scan(baseUrl: String, token: String, device: String): Pair<List<Ap>, String?> {
        val reply = Ubus.call(
            baseUrl, token, "iwinfo", "scan",
            JSONObject().put("device", device), 25_000
        )
        if (!reply.ok) return emptyList<Ap>() to reply.error
        val arr = reply.data?.optJSONArray("results")
            ?: return emptyList<Ap>() to "Ничего не найдено"
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val channel = o.optInt("channel", 0)
            if (channel <= 0) return@mapNotNull null
            Ap(
                ssid = o.optString("ssid").ifBlank { "(скрытая)" },
                bssid = o.optString("bssid"),
                band = o.optInt("band", if (channel <= 14) 2 else 5),
                channel = channel,
                signal = o.optInt("signal", -100),
                secure = o.optJSONObject("encryption")?.optBoolean("enabled") ?: false
            )
        }.sortedByDescending { it.signal }
        return list to null
    }
}
