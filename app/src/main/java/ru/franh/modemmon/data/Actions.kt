package ru.franh.modemmon.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Действия, меняющие состояние роутера.
 * Все методы возвращают null при успехе и текст ошибки иначе.
 * Вызывать только после подтверждения в UI.
 */
object Actions {

    /**
     * Управление службой: start / stop / restart / enable / disable.
     * Идём напрямую в /etc/init.d/<name>: объект `luci` с setInitAction
     * закрыт ACL для сессии, а init-скрипт — работает.
     */
    fun serviceAction(baseUrl: String, token: String, name: String, action: String): String? {
        if (name.isBlank() || name.contains('/')) return "Некорректное имя службы"
        val (out, err) = SystemInfo.exec(
            baseUrl, token, "/etc/init.d/$name", listOf(action), 40_000
        )
        if (err != null) return err
        // init-скрипты пишут результат в stderr, код возврата — в out
        return null
    }

    /** Поднять или опустить сетевой интерфейс. */
    fun ifaceUpDown(baseUrl: String, token: String, iface: String, up: Boolean): String? {
        val reply = Ubus.call(
            baseUrl, token, "network.interface.$iface",
            if (up) "up" else "down", JSONObject(), 30_000
        )
        return if (reply.ok) null else reply.error
    }

    /**
     * Смена метрики интерфейса (приоритет WAN) с последующим reload сети.
     * Меньше метрика — выше приоритет.
     */
    fun setMetric(baseUrl: String, token: String, iface: String, metric: Int): String? {
        val err = Ubus.uciSet(baseUrl, token, "network.$iface.metric", metric.toString())
        if (err != null) return err.error
        val reply = Ubus.call(baseUrl, token, "network", "reload", JSONObject(), 40_000)
        return if (reply.ok) null else reply.error
    }

    /** Запуск apk с аргументами: add / del / update. */
    fun apk(baseUrl: String, token: String, args: List<String>): Pair<String?, String?> {
        val params = JSONArray()
        args.forEach { params.put(it) }
        val req = JSONObject().put("command", "/usr/bin/apk").put("params", params)
        val reply = Ubus.call(baseUrl, token, "file", "exec", req, 120_000)
        if (!reply.ok) return null to reply.error
        val d = reply.data ?: return null to "Пустой ответ"
        val code = d.optInt("code", -1)
        val out = d.optString("stdout").orEmpty().trim()
        val errText = d.optString("stderr").orEmpty().trim()
        if (code != 0) return null to (errText.ifBlank { out }.ifBlank { "apk вернул код $code" })
        return (out.ifBlank { errText }) to null
    }
}
