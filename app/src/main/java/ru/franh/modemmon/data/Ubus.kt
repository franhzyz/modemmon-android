package ru.franh.modemmon.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Клиент ubus JSON-RPC (OpenWrt rpcd, endpoint /ubus).
 *
 * Зачем: телеметрия читается своим лёгким эндпоинтом, а для управления
 * (uci, AT, SMS, USSD, сеть) нужен ubus. Прямой вызов лучше, чем
 * /cgi-bin/luci/admin/ubus: не требует установленного LuCI и его куки.
 *
 * Формат запроса:
 *   {"jsonrpc":"2.0","id":1,"method":"call","params":["<token>","<obj>","<method>",{...}]}
 * Формат ответа:
 *   {"jsonrpc":"2.0","id":1,"result":[<код>,<данные>]}
 * Код 0 — успех, иначе — ошибка ubus (см. [statusText]).
 */
object Ubus {

    /** Результат вызова: либо данные, либо текст ошибки с указанием стадии. */
    data class Reply(val data: JSONObject?, val error: String?) {
        val ok: Boolean get() = error == null
    }

    private const val TOKEN_NONE = "00000000000000000000000000000000"

    /**
     * Логин в rpcd. Возвращает токен сессии или ошибку.
     * Требует пакет rpcd-mod-luci и заданный пароль root.
     */
    fun login(
        baseUrl: String,
        user: String,
        password: String,
        timeoutMs: Int = 6000
    ): Reply {
        val args = JSONObject().put("username", user).put("password", password)
        val reply = post(baseUrl, TOKEN_NONE, "session", "login", args, timeoutMs)
        val token = reply.data?.optString("ubus_rpc_session").orEmpty()
        if (!reply.ok) return reply
        if (token.isBlank()) return Reply(null, "rpcd не вернул токен сессии")
        return Reply(JSONObject().put("token", token), null)
    }

    /** Произвольный вызов ubus с готовым токеном. */
    fun call(
        baseUrl: String,
        token: String,
        obj: String,
        method: String,
        args: JSONObject? = null,
        timeoutMs: Int = 8000
    ): Reply = post(baseUrl, token, obj, method, args, timeoutMs)

    /**
     * Установка опции UCI и commit.
     * Сигнатура rpcd: set {config, section, values:{option:value}}, затем commit {config}.
     */
    fun uciSet(baseUrl: String, token: String, key: String, value: String): Reply {
        val parts = key.split('.')
        if (parts.size != 3) return Reply(null, "Ключ должен быть вида config.section.option")
        val (cfg, sec, opt) = parts
        val args = JSONObject()
            .put("config", cfg)
            .put("section", sec)
            .put("values", JSONObject().put(opt, value))
        val set = call(baseUrl, token, "uci", "set", args)
        if (!set.ok) return set
        return call(baseUrl, token, "uci", "commit", JSONObject().put("config", cfg))
    }

    private fun post(
        baseUrl: String,
        token: String,
        obj: String,
        method: String,
        args: JSONObject?,
        timeoutMs: Int
    ): Reply {
        val url = rpcUrl(baseUrl) ?: return Reply(null, "Некорректный адрес роутера")
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "call")
            .put("params", org.json.JSONArray().put(token).put(obj).put(method).put(args ?: JSONObject()))
            .toString()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ModemMon/2.2")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                return Reply(null, "HTTP $code" + if (text.isNotBlank()) ": ${text.take(100)}" else "")
            }
            parse(text)
        } catch (e: IOException) {
            Reply(null, describe(e))
        } catch (e: Exception) {
            Reply(null, describe(e))
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(text: String): Reply {
        if (text.isBlank()) return Reply(null, "Пустой ответ rpcd")
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return Reply(null, "rpcd ответил не JSON")
        }
        root.optJSONObject("error")?.let {
            return Reply(null, it.optString("message").ifBlank { "Ошибка JSON-RPC" })
        }
        val result = root.optJSONArray("result") ?: return Reply(null, "Нет поля result")
        if (result.length() < 1) return Reply(null, "Пустой result")
        val code = result.optInt(0, -1)
        if (code != 0) return Reply(null, statusText(code))
        val data = result.optJSONObject(1)
        return Reply(data ?: JSONObject(), null)
    }

    private fun rpcUrl(baseUrl: String): String? {
        val base = baseUrl.trim().trimEnd('/').ifBlank { return null }
        val withScheme = if (base.startsWith("http://") || base.startsWith("https://")) base else "http://$base"
        return "$withScheme/ubus"
    }

    /** Коды ubus, которые реально встречаются при работе с rpcd. */
    private fun statusText(code: Int): String = when (code) {
        2 -> "Неверный аргумент"
        3 -> "Метод не найден"
        4 -> "Объект не найден"
        5 -> "Не поддерживается"
        6 -> "Доступ запрещён — проверьте логин и пароль"
        7 -> "Нет данных"
        8 -> "Таймаут операции"
        9 -> "Не поддерживается"
        10 -> "Ошибка ввода-вывода"
        11 -> "Нет ресурсов"
        else -> "Ошибка ubus (код $code)"
    }

    private fun describe(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("timed out", true) || m.contains("timeout", true) -> "Таймаут: роутер не ответил"
            m.contains("refused", true) -> "Соединение отклонено — rpcd не слушает"
            m.contains("Unable to resolve host", true) -> "Хост не найден"
            m.contains("Network unreachable", true) -> "Сеть недоступна"
            else -> m
        }
    }
}
