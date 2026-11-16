package ru.franh.modemmon.data

import org.json.JSONObject

/** Состояние uplink из MedveModem: каналы и автопереключение. */
object Uplink {

    data class Path(
        val device: String,
        val role: String,
        val metric: Int,
        val kernelMetric: Int,
        val steered: Boolean
    )

    data class State(
        val internet: String,
        val defaultDevice: String?,
        val failoverEnabled: Boolean,
        val failoverActive: Boolean,
        val paths: List<Path>
    )

    fun status(baseUrl: String, token: String): Pair<State?, String?> {
        val reply = Ubus.call(baseUrl, token, "medvemodem", "uplink", JSONObject(), 15_000)
        if (!reply.ok) return null to reply.error
        val d = reply.data ?: return null to "Пустой ответ"

        val fo = d.optJSONObject("failover")
        val routes = fo?.optJSONArray("routes")
        val steeredByDevice = mutableMapOf<String, Boolean>()
        val metricByDevice = mutableMapOf<String, Int>()
        val baseByDevice = mutableMapOf<String, Int>()
        if (routes != null) {
            for (i in 0 until routes.length()) {
                val r = routes.optJSONObject(i) ?: continue
                val dev = r.optString("device")
                steeredByDevice[dev] = r.optBoolean("steered_away")
                metricByDevice[dev] = r.optInt("metric")
                baseByDevice[dev] = r.optInt("base")
            }
        }

        val pathsArr = d.optJSONArray("paths")
        val paths = if (pathsArr == null) emptyList() else (0 until pathsArr.length()).mapNotNull { i ->
            val p = pathsArr.optJSONObject(i) ?: return@mapNotNull null
            val dev = p.optString("device")
            if (dev.isBlank()) return@mapNotNull null
            Path(
                device = dev,
                role = p.optString("role"),
                metric = metricByDevice[dev] ?: p.optInt("metric"),
                kernelMetric = p.optInt("kernel_metric", metricByDevice[dev] ?: 0),
                steered = steeredByDevice[dev] ?: false
            )
        }

        return State(
            internet = d.optString("internet").ifBlank { "unknown" },
            defaultDevice = d.optString("default_device").ifBlank { null },
            failoverEnabled = fo?.optBoolean("enabled") ?: false,
            failoverActive = fo?.optBoolean("active") ?: false,
            paths = paths
        ) to null
    }

    /** Включить или выключить автопереключение. */
    fun setFailover(baseUrl: String, token: String, on: Boolean): String? {
        val err = UciConfig.setMany(
            baseUrl, token, "medvemodem", "core",
            mapOf("failover" to if (on) "1" else "0")
        )
        return err
    }

    /**
     * Реальная проверка интернета через конкретное устройство.
     * 204 — интернет есть; 302/200 — заглушка оператора; 000 — не проходит.
     */
    fun probe(baseUrl: String, token: String, device: String): Pair<String?, String?> {
        val (out, err) = SystemInfo.exec(
            baseUrl, token, "/usr/bin/curl",
            listOf(
                "-s", "-m", "6", "--interface", device,
                "-o", "/dev/null", "-w", "%{http_code}",
                "http://connectivitycheck.gstatic.com/generate_204"
            ),
            12_000
        )
        if (out == null) return null to err
        return out.trim() to null
    }
}
