package ru.franh.modemmon.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Тонкий HTTP-клиент: без сторонних библиотек, с таймаутами и понятными ошибками. */
object ModemApi {

    fun fetch(url: String, timeoutMs: Int = 5000): ModemSnapshot {
        var conn: HttpURLConnection? = null
        return try {
            val u = URL(url)
            conn = RouterNet.open(u).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "ModemMon/2.0")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return ModemSnapshot.offline(
                    "HTTP $code" + if (body.isNotBlank()) ": ${body.take(120)}" else ""
                )
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) return ModemSnapshot.offline("Пустой ответ")
            try {
                ModemSnapshot.parse(text)
            } catch (e: Exception) {
                ModemSnapshot.offline("Не разобрал JSON: ${e.message}")
            }
        } catch (e: IOException) {
            ModemSnapshot.offline(describe(e))
        } catch (e: Exception) {
            ModemSnapshot.offline(describe(e))
        } finally {
            conn?.disconnect()
        }
    }

    private fun describe(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("timed out", true) || m.contains("timeout", true) -> "Таймаут: роутер не ответил"
            m.contains("refused", true) -> "Соединение отклонено"
            m.contains("Unable to resolve host", true) -> "Хост не найден"
            else -> m
        }
    }
}
