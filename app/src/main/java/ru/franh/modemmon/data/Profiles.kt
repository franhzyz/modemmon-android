package ru.franh.modemmon.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Сохранённый роутер: имя для списка, адрес и логин для ubus. */
data class RouterProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val user: String = "root"
)

/**
 * Профили роутеров в SharedPreferences (тот же файл, что и настройки).
 * Пароль здесь не хранится: он нужен только для session.login и живёт
 * в памяти процесса.
 */
class Profiles(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("modemmon", Context.MODE_PRIVATE)

    fun list(): List<RouterProfile> {
        val raw = sp.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                RouterProfile(
                    id = id,
                    name = o.optString("name").ifBlank { o.optString("baseUrl") },
                    baseUrl = o.optString("baseUrl"),
                    user = o.optString("user").ifBlank { "root" }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<RouterProfile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("user", p.user)
            )
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** Добавляет или заменяет профиль по id. */
    fun upsert(profile: RouterProfile) {
        val cur = list().toMutableList()
        val idx = cur.indexOfFirst { it.id == profile.id }
        if (idx >= 0) cur[idx] = profile else cur.add(profile)
        save(cur)
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
        if (activeId() == id) sp.edit().remove(KEY_ACTIVE).apply()
    }

    fun activeId(): String? = sp.getString(KEY_ACTIVE, null)

    fun setActive(id: String) {
        sp.edit().putString(KEY_ACTIVE, id).apply()
    }

    companion object {
        private const val KEY_LIST = "profiles"
        private const val KEY_ACTIVE = "profile_active"
    }
}
