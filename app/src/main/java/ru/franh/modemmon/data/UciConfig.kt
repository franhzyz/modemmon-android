package ru.franh.modemmon.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Чтение и запись произвольного UCI-конфига через ubus.
 * Один экран на все конфиги: system, dhcp, firewall, network, footstrap и т.д.
 */
object UciConfig {

    /** Опция конфига. [isList] — значение задано через `list` и хранится массивом. */
    data class Option(val key: String, val value: String, val isList: Boolean)

    /** Секция конфига: имя, тип и опции. */
    data class Section(
        val name: String,
        val type: String,
        val anonymous: Boolean,
        val options: List<Option>
    )

    private val META = setOf(".anonymous", ".type", ".name", ".index")

    fun get(baseUrl: String, token: String, config: String): Pair<List<Section>, String?> {
        val reply = Ubus.call(baseUrl, token, "uci", "get", JSONObject().put("config", config), 15_000)
        if (!reply.ok) return emptyList<Section>() to reply.error
        val values = reply.data?.optJSONObject("values")
            ?: return emptyList<Section>() to "Конфиг пуст"
        val list = values.keys().asSequence().mapNotNull { key ->
            val o = values.optJSONObject(key) ?: return@mapNotNull null
            val opts = o.keys().asSequence()
                .filterNot { it in META }
                .map { name ->
                    val raw = o.opt(name)
                    if (raw is JSONArray) {
                        // UCI list: показываем через пробел, без кавычек и скобок
                        Option(
                            key = name,
                            value = (0 until raw.length()).joinToString(" ") { raw.optString(it) },
                            isList = true
                        )
                    } else {
                        Option(key = name, value = o.optString(name), isList = false)
                    }
                }
                .sortedBy { it.key }
                .toList()
            Section(
                name = key,
                type = o.optString(".type").ifBlank { "section" },
                anonymous = o.optBoolean(".anonymous"),
                options = opts
            )
        }.sortedBy { it.name }.toList()
        return list to null
    }

    /**
     * Записать несколько опций одной секции и зафиксировать конфиг.
     * Значение — String либо List<String> (для list-опций).
     */
    fun setMany(
        baseUrl: String,
        token: String,
        config: String,
        section: String,
        values: Map<String, Any>
    ): String? {
        if (values.isEmpty()) return null
        val obj = JSONObject()
        values.forEach { (k, v) ->
            when (v) {
                is List<*> -> obj.put(k, JSONArray().also { a -> v.forEach { a.put(it) } })
                else -> obj.put(k, v)
            }
        }
        val args = JSONObject()
            .put("config", config)
            .put("section", section)
            .put("values", obj)
        val reply = Ubus.call(baseUrl, token, "uci", "set", args, 20_000)
        if (!reply.ok) return reply.error
        val commit = Ubus.call(baseUrl, token, "uci", "commit", JSONObject().put("config", config), 20_000)
        return if (commit.ok) null else commit.error
    }

    /**
     * Записать опцию и зафиксировать конфиг.
     * Списочные опции пишутся массивом, обычные — строкой.
     */
    fun set(
        baseUrl: String,
        token: String,
        config: String,
        section: String,
        option: String,
        value: String,
        isList: Boolean = false
    ): String? {
        val payload: Any = if (isList) {
            JSONArray().also { arr ->
                value.split(" ", ",").map { it.trim() }.filter { it.isNotEmpty() }
                    .forEach { arr.put(it) }
            }
        } else {
            value
        }
        val args = JSONObject()
            .put("config", config)
            .put("section", section)
            .put("values", JSONObject().put(option, payload))
        val reply = Ubus.call(baseUrl, token, "uci", "set", args, 15_000)
        if (!reply.ok) return reply.error
        val commit = Ubus.call(baseUrl, token, "uci", "commit", JSONObject().put("config", config), 20_000)
        return if (commit.ok) null else commit.error
    }
}
