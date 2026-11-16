package ru.franh.modemmon.data

import org.json.JSONObject

/**
 * Forkop (sing-box) — сборка скриптов и разбор вывода CLI /usr/bin/forkop.
 * Чистые функции: скрипт исполняется на роутере через file exec,
 * вывод парсится здесь же. Без Android-зависимостей.
 */
object Forkop {

    data class Status(
        val raw: String,
        val running: Boolean?,
        val enabled: Boolean?,
        val version: String?
    )

    data class Summary(
        val status: Int,
        val downloadTotal: Long,
        val uploadTotal: Long,
        val connections: Int
    )

    data class Check(val name: String, val ok: Boolean?, val raw: String)

    data class SectionData(
        val id: String,
        val label: String,
        val enabled: Boolean,
        val lists: List<String>
    )

    data class LinkData(val id: String, val name: String, val uri: String, val sectionId: String)

    // MARK: скрипты для file exec

    fun statusScript() = "/usr/bin/forkop get_status 2>&1"
    fun summaryScript() = "/usr/bin/forkop clash_summary 2>&1"
    fun sectionsScript() = "uci show forkop 2>&1"

    fun diagScripts(): List<Pair<String, String>> = listOf(
        "Сервис" to "/usr/bin/forkop check_service 2>&1",
        "DNS" to "/usr/bin/forkop check_dns 2>&1",
        "Трафик роутера" to "/usr/bin/forkop check_traffic 2>&1",
        "Серверы" to "/usr/bin/forkop check_servers 2>&1"
    )

    fun testGroupScript(group: String): String {
        val path = java.net.URLEncoder.encode(
            "/group/$group/delay?url=https%3A%2F%2Fwww.gstatic.com%2Fgenerate_204&timeout=5000", "UTF-8"
        )
        return "/usr/bin/forkop clash_api GET '$path' 2>&1"
    }

    fun saveSectionScript(id: String, label: String, enabled: Boolean, lists: List<String>): String {
        val safeId = id.replace("'", "")
        val safeLabel = label.replace("'", "")
        val listPart = lists.map { "'${it.replace("'", "")}'" }.joinToString(" ").ifBlank { "''" }
        return "uci set forkop.$safeId.label='$safeLabel'; " +
            "uci set forkop.$safeId.enabled='${if (enabled) "1" else "0"}'; " +
            "uci set forkop.$safeId.community_lists='$listPart'; " +
            "uci commit forkop; echo saved"
    }

    fun deleteSectionScript(id: String): String {
        val safeId = id.replace("'", "")
        return "uci delete forkop.$safeId; uci commit forkop; echo deleted"
    }

    fun addSectionScript(label: String, lists: List<String>): String {
        val safeLabel = label.replace("'", "")
        val listPart = lists.map { "'${it.replace("'", "")}'" }.joinToString(" ").ifBlank { "''" }
        return "N=\$(uci add forkop section); uci set forkop.\$N.label='$safeLabel'; " +
            "uci set forkop.\$N.enabled='1'; uci set forkop.\$N.action='connection'; " +
            "uci set forkop.\$N.community_lists='$listPart'; uci commit forkop; echo added"
    }

    // MARK: разбор вывода

    private fun jsonOrNull(text: String): JSONObject? =
        if (text.startsWith("{")) runCatching { JSONObject(text) }.getOrNull() else null

    /** Обёртка {status, body:"json"} → вложенный JSON тела. */
    private fun unwrap(text: String): JSONObject? {
        val o = jsonOrNull(text) ?: return null
        val body = o.optString("body")
        return if (body.isNotBlank()) jsonOrNull(body) else o
    }

    fun parseStatus(raw: String): Status {
        val o = jsonOrNull(raw)
        // Значения бывают bool, числом (running: 1) и строкой "running & enabled"
        fun flag(key: String): Boolean? = when (val v = o?.opt(key)) {
            null -> null
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v == "1" || v.equals("true", true)
            else -> null
        }
        val statusText = o?.optString("status").orEmpty()
        val fromStatus = if (o != null && o.has("status")) statusText.contains("running") else null
        return Status(
            raw = raw,
            running = flag("running") ?: flag("active") ?: fromStatus,
            enabled = flag("enabled"),
            version = o?.optString("version")?.ifBlank { null }
        )
    }

    fun parseSummary(raw: String): Summary {
        val body = unwrap(raw)
        return Summary(
            status = body?.optInt("status", 0) ?: 0,
            downloadTotal = body?.optLong("downloadTotal", 0) ?: 0,
            uploadTotal = body?.optLong("uploadTotal", 0) ?: 0,
            connections = body?.optInt("connections", 0) ?: 0
        )
    }

    fun parseCheck(raw: String): Boolean? {
        val o = jsonOrNull(raw) ?: return null
        if (o.has("error") && o.optString("error").isNotBlank()) return false
        return true
    }

    /**
     * Разбор `uci show forkop`:
     *   forkop.section_x.label='1'
     *   forkop.section_x.community_lists='chatgpt' 'claude'
     *   forkop.section_x=section            (тип секции)
     */
    fun parseSections(raw: String): Pair<List<SectionData>, List<LinkData>> {
        val opts = linkedMapOf<String, MutableMap<String, String>>()
        for (rawLine in raw.split("\n")) {
            val line = rawLine.trim()
            if (!line.startsWith("forkop.") || !line.contains("=")) continue
            val rest = line.substring("forkop.".length)
            val eq = rest.indexOf('=')
            if (eq < 0) continue
            val keyPart = rest.substring(0, eq)
            val value = rest.substring(eq + 1)
            // forkop.<id>=<type>  ИЛИ  forkop.<id>.<option>=<value>
            // (id секции не содержит точек; id = до первой точки)
            val dot = keyPart.indexOf('.')
            val id: String
            val key: String
            if (dot < 0) {
                id = keyPart
                key = ".type"
            } else {
                id = keyPart.substring(0, dot)
                key = keyPart.substring(dot + 1)
            }
            // продолжение multiline-значения содержит лишние точки в пути — пропускаем
            if (dot >= 0 && keyPart.indexOf('.', dot + 1) >= 0) continue
            if (value == "'") continue                       // начало multiline-значения
            // списки: 'a' 'b' — элементы в кавычках через пробел
            val items: List<String> = if (value.contains("' '")) {
                value.split("' '").map { it.trim('\'') }.filter { it.isNotBlank() }
            } else if (value.startsWith("'") && value.endsWith("'") && value.length >= 2) {
                listOf(value.substring(1, value.length - 1))
            } else {
                listOf(value)
            }
            val s = opts.getOrPut(id) { mutableMapOf() }
            if (items.size > 1 || (items.size == 1 && value.contains("' '"))) {
                s[key] = items.joinToString(" ")
                s["$key.__islist"] = "1"
            } else {
                s[key] = items.firstOrNull() ?: ""
            }
        }

        val rules = mutableListOf<SectionData>()
        val links = mutableListOf<LinkData>()
        for ((id, s) in opts) {
            val type = s[".type"] ?: ""
            if (type == "section") {
                rules.add(SectionData(
                    id = id,
                    label = s["label"] ?: id,
                    enabled = s["enabled"] == "1",
                    lists = s["community_lists"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                ))
            } else if (type == "connection_link") {
                links.add(LinkData(
                    id = id,
                    name = s["name"] ?: id,
                    uri = s["uri"] ?: "",
                    sectionId = s["section"] ?: ""
                ))
            }
        }
        return rules.sortedBy { it.label } to links.sortedBy { it.name }
    }

    fun parseTestGroup(raw: String): Map<String, Int> {
        val body = unwrap(raw) ?: return emptyMap()
        val res = mutableMapOf<String, Int>()
        body.keys().asSequence().forEach { k -> res[k] = body.optInt(k, 0) }
        return res.filterValues { it > 0 }
    }
}
