package ru.franh.modemmon.data

import android.content.Context
import android.content.SharedPreferences

/** Пользовательские настройки источника данных. */
data class Settings(
    val baseUrl: String = "http://192.168.2.1",
    val path: String = "/cgi-bin/modem-telemetry",
    val intervalMs: Int = 2000,
    val demo: Boolean = false,
    val sound: Boolean = false,
    val vibrate: Boolean = false,
    val metric: String = "sinr"
) {
    val fullUrl: String
        get() {
            val base = baseUrl.trim().trimEnd('/').ifBlank { "http://192.168.2.1" }
            val withScheme = if (base.startsWith("http://") || base.startsWith("https://")) base else "http://$base"
            val p = path.trim().ifBlank { "/" }
            return withScheme + (if (p.startsWith("/")) p else "/$p")
        }
}

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("modemmon", Context.MODE_PRIVATE)

    fun load(): Settings {
        val savedPath = sp.getString("path", null)
        val path = when (savedPath) {
            null, "/cgi-bin/modem" -> "/cgi-bin/modem-telemetry"
            else -> savedPath
        }
        return Settings(
            baseUrl = sp.getString("base_url", "http://192.168.2.1") ?: "http://192.168.2.1",
            path = path,
            intervalMs = sp.getInt("interval", 2000),
            demo = sp.getBoolean("demo", false),
            sound = sp.getBoolean("sound", false),
            vibrate = sp.getBoolean("vibrate", false),
            metric = sp.getString("metric", "sinr") ?: "sinr"
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString("base_url", s.baseUrl)
            .putString("path", s.path)
            .putInt("interval", s.intervalMs)
            .putBoolean("demo", s.demo)
            .putBoolean("sound", s.sound)
            .putBoolean("vibrate", s.vibrate)
            .putString("metric", s.metric)
            .apply()
    }
}
