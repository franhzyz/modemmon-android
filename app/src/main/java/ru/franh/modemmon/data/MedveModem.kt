package ru.franh.modemmon.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Обёртка над ubus-объектом medvemodem.
 * Только чтение и AT-команды; изменение диапазонов требует записи в UCI
 * и radio_confirm, поэтому вынесено отдельно.
 */
object MedveModem {

    /** Модем, известный движку. */
    data class Device(
        val id: String,
        val product: String,
        val vidpid: String,
        val iface: String?,
        val ok: Boolean,
        val reason: String?,
        val ttyPorts: List<String>
    ) {
        val title: String get() = product.ifBlank { vidpid.ifBlank { id } }
    }

    /** Ответ на AT-команду. */
    data class AtReply(
        val ok: Boolean,
        val port: String,
        val final: String,
        val lines: List<String>,
        val elapsedMs: Int
    )

    /** Текущие режимы и диапазоны модема. */
    data class RadioInfo(
        val modes: List<String>,
        /** Приоритетная технология; движок требует её как часть комбинации. */
        val preferred: String?,
        val bands: List<String>,
        val supportedBands: List<String>,
        val writable: Boolean,
        /** Состояние применения: applying / waiting / applied / kept / rolled_back / failed … */
        val change: String?
    )

    /** Одно сообщение из ящика модема. */
    data class Sms(
        val id: Int,
        val number: String?,
        val text: String?,
        val epoch: Long?,
        val delivery: String?
    )

    /** Состояние USSD-сеанса. */
    data class Ussd(
        val state: String,
        val reply: String?
    )

    fun devices(baseUrl: String, token: String): Pair<List<Device>, String?> {
        val reply = Ubus.call(baseUrl, token, "medvemodem", "devices", JSONObject())
        if (!reply.ok) return emptyList<Device>() to reply.error
        val arr = reply.data?.optJSONArray("devices")
            ?: return emptyList<Device>() to "Нет списка устройств"
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("device_id").ifBlank { o.optString("port_id") }
            if (id.isBlank()) return@mapNotNull null
            val diag = o.optJSONObject("diagnosis")
            val session = o.optJSONObject("session")
            val tty = o.optJSONObject("ports")?.optJSONArray("tty")
            Device(
                id = id,
                product = o.optString("product"),
                vidpid = o.optString("vidpid"),
                iface = session?.optString("interface")?.ifBlank { null },
                ok = diag?.optBoolean("ok") ?: false,
                reason = diag?.optString("reason")?.ifBlank { null },
                ttyPorts = if (tty == null) emptyList() else
                    (0 until tty.length()).map { tty.optString(it) }.filter { it.isNotBlank() }
            )
        }
        return list to null
    }

    fun atCommand(
        baseUrl: String,
        token: String,
        deviceId: String,
        command: String,
        timeoutMs: Int = 5000
    ): Pair<AtReply?, String?> {
        val args = JSONObject()
            .put("id", deviceId)
            .put("command", command)
            .put("timeout_ms", timeoutMs)
        val reply = Ubus.call(baseUrl, token, "medvemodem", "at_command", args, timeoutMs + 4000)
        if (!reply.ok) return null to reply.error
        val d = reply.data ?: return null to "Пустой ответ"
        // HiLink и подобные модемы отвечают {"refused": "..."} — это не ошибка связи,
        // а объяснение, почему AT недоступны.
        d.optString("refused").takeIf { it.isNotBlank() }?.let { return null to explain(it) }
        val lines = d.optJSONArray("lines")
        return AtReply(
            ok = d.optBoolean("ok"),
            port = d.optString("port"),
            final = d.optString("final"),
            lines = if (lines == null) emptyList() else
                (0 until lines.length()).map { lines.optString(it) },
            elapsedMs = d.optInt("elapsed_ms")
        ) to null
    }

    fun radio(baseUrl: String, token: String, deviceId: String): Pair<RadioInfo?, String?> {
        val reply = Ubus.call(
            baseUrl, token, "medvemodem", "radio",
            JSONObject().put("id", deviceId)
        )
        if (!reply.ok) return null to reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return null to explain(it) }
        return parseRadio(reply.data?.optJSONObject("radio_settings"))
    }

    /**
     * Применение режимов и диапазонов.
     * Движок сам проверяет, вернулась ли сеть, и откатывает настройки,
     * если связи нет — но подтвердить результат всё равно нужно через [confirmRadio].
     */
    fun setRadio(
        baseUrl: String,
        token: String,
        deviceId: String,
        modes: List<String>,
        preferred: String?,
        bands: List<String>
    ): Pair<RadioInfo?, String?> {
        val modesObj = JSONObject()
            .put("allowed", JSONArray().also { a -> modes.forEach { a.put(it) } })
        if (preferred != null) modesObj.put("preferred", preferred)
        val args = JSONObject()
            .put("id", deviceId)
            // modes — объект {allowed:[…], preferred:…}: движок сверяет точную комбинацию
            .put("modes", modesObj)
            .put("bands", JSONArray().also { a -> bands.forEach { a.put(it) } })
        val reply = Ubus.call(baseUrl, token, "medvemodem", "radio_set", args, 90_000)
        if (!reply.ok) return null to reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return null to explain(it) }
        reply.data?.optString("error")?.takeIf { it.isNotBlank() }?.let { return null to it }
        return parseRadio(reply.data?.optJSONObject("radio_settings"))
    }

    /** keep — оставить новые настройки, rollback — вернуть прежние. */
    fun confirmRadio(baseUrl: String, token: String, deviceId: String, action: String): String? {
        val args = JSONObject().put("id", deviceId).put("action", action)
        val reply = Ubus.call(baseUrl, token, "medvemodem", "radio_confirm", args, 90_000)
        if (!reply.ok) return reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return explain(it) }
        return null
    }

    private fun parseRadio(rs: JSONObject?): Pair<RadioInfo?, String?> {
        if (rs == null) return null to "Нет radio_settings"
        fun arr(o: JSONObject?, key: String): List<String> {
            val a = o?.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }
        }
        return RadioInfo(
            modes = arr(rs.optJSONObject("modes"), "allowed"),
            preferred = rs.optJSONObject("modes")?.optString("preferred")?.ifBlank { null },
            bands = arr(rs, "bands"),
            supportedBands = arr(rs, "supported_bands"),
            writable = rs.optBoolean("writable"),
            change = rs.optJSONObject("change")?.optString("state")?.ifBlank { null }
        ) to null
    }

    /** Ящик сообщений модема. */
    fun smsList(baseUrl: String, token: String, deviceId: String): Pair<List<Sms>, String?> {
        val reply = Ubus.call(
            baseUrl, token, "medvemodem", "sms_list",
            JSONObject().put("id", deviceId), timeoutMs = 20_000
        )
        if (!reply.ok) return emptyList<Sms>() to reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return emptyList<Sms>() to explain(it) }
        val arr = reply.data?.optJSONArray("messages")
            ?: return emptyList<Sms>() to "Нет списка сообщений"
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Sms(
                id = o.optInt("id", -1),
                number = o.optString("number").ifBlank { null },
                text = o.optString("text").ifBlank { null },
                epoch = if (o.isNull("epoch")) null else o.optLong("epoch"),
                delivery = o.optString("delivery_state").ifBlank { null }
            )
        }
        return list to null
    }

    /** Отправка SMS. Возвращает текст ошибки или null при успехе. */
    fun smsSend(
        baseUrl: String,
        token: String,
        deviceId: String,
        number: String,
        text: String
    ): String? {
        val args = JSONObject().put("id", deviceId).put("number", number).put("text", text)
        val reply = Ubus.call(baseUrl, token, "medvemodem", "sms_send", args, timeoutMs = 40_000)
        if (!reply.ok) return reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return explain(it) }
        if (reply.data?.optBoolean("ok") == false) {
            return reply.data.optString("error").ifBlank { "Модем не подтвердил отправку" }
        }
        return null
    }

    fun smsDelete(baseUrl: String, token: String, deviceId: String, smsId: Int): String? {
        val args = JSONObject().put("id", deviceId).put("sms", smsId)
        val reply = Ubus.call(baseUrl, token, "medvemodem", "sms_delete", args, timeoutMs = 20_000)
        if (!reply.ok) return reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return explain(it) }
        return null
    }

    /** Запуск USSD-запроса. */
    fun ussdStart(baseUrl: String, token: String, deviceId: String, code: String): Pair<Ussd?, String?> {
        val args = JSONObject().put("id", deviceId).put("code", code)
        val reply = Ubus.call(baseUrl, token, "medvemodem", "ussd_start", args, timeoutMs = 40_000)
        if (!reply.ok) return null to reply.error
        reply.data?.optString("refused")?.takeIf { it.isNotBlank() }?.let { return null to explain(it) }
        val d = reply.data ?: return null to "Пустой ответ"
        return Ussd(
            state = d.optString("state").ifBlank { "unknown" },
            reply = d.optString("reply").ifBlank { null }
        ) to null
    }

    fun ussdStatus(baseUrl: String, token: String, deviceId: String): Pair<Ussd?, String?> {
        val reply = Ubus.call(
            baseUrl, token, "medvemodem", "ussd_status",
            JSONObject().put("id", deviceId)
        )
        if (!reply.ok) return null to reply.error
        val d = reply.data ?: return null to "Пустой ответ"
        return Ussd(
            state = d.optString("state").ifBlank { "unknown" },
            reply = d.optString("reply").ifBlank { null }
        ) to null
    }

    /** Переводит типовые отказы движка; незнакомый текст возвращает как есть. */
    private fun explain(raw: String): String = when {
        raw.contains("no AT port is known", ignoreCase = true) ->
            "У этого модема нет AT-порта — команды недоступны (HiLink-режим)."
        raw.contains("router holds the radio settings", ignoreCase = true) ->
            "Диапазонами управляет веб-интерфейс самого модема — из приложения недоступно."
        raw.contains("no-session", ignoreCase = true) ->
            "Модем без активной сессии — сначала подключите его."
        else -> raw
    }
}
