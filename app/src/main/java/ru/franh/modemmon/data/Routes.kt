package ru.franh.modemmon.data

/** Таблица маршрутизации ядра. */
object Routes {

    data class Route(
        val dest: String,
        val via: String?,
        val dev: String?,
        val metric: Int?,
        val proto: String?,
        val src: String?
    ) {
        val isDefault: Boolean get() = dest == "default"
    }

    fun list(baseUrl: String, token: String): Pair<List<Route>, String?> {
        val (out, err) = SystemInfo.exec(baseUrl, token, "/sbin/ip", listOf("route", "show"))
        if (out == null) return emptyList<Route>() to err

        val routes = out.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull null
            val parts = t.split(Regex("\\s+"))
            val dest = parts.firstOrNull() ?: return@mapNotNull null

            var via: String? = null
            var dev: String? = null
            var metric: Int? = null
            var proto: String? = null
            var src: String? = null

            var i = 1
            while (i < parts.size - 1) {
                when (parts[i]) {
                    "via" -> via = parts[i + 1]
                    "dev" -> dev = parts[i + 1]
                    "metric" -> metric = parts[i + 1].toIntOrNull()
                    "proto" -> proto = parts[i + 1]
                    "src" -> src = parts[i + 1]
                }
                i++
            }
            Route(dest, via, dev, metric, proto, src)
        }
        // Сначала default, потом по метрике
        return routes.sortedWith(
            compareByDescending<Route> { it.isDefault }.thenBy { it.metric ?: 9999 }
        ) to null
    }
}
