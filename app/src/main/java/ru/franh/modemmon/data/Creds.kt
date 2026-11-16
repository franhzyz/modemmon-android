package ru.franh.modemmon.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Пароль ubus, сохранённый по явному согласию пользователя.
 * Лежит в приватном хранилище приложения (MODE_PRIVATE): другие приложения
 * его не прочитают, но при бэкапе/root-доступе он доступен.
 * Не сохраняется, пока пользователь не поставил галочку.
 */
class Creds(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("modemmon", Context.MODE_PRIVATE)

    /** Пара «пользователь + пароль» или null, если ничего не сохранено. */
    fun saved(): Pair<String, String>? {
        val user = sp.getString(KEY_USER, null) ?: return null
        val pass = sp.getString(KEY_PASS, null) ?: return null
        return user to pass
    }

    fun save(user: String, password: String) {
        sp.edit().putString(KEY_USER, user).putString(KEY_PASS, password).apply()
    }

    fun clear() {
        sp.edit().remove(KEY_USER).remove(KEY_PASS).apply()
    }

    companion object {
        private const val KEY_USER = "ubus_user"
        private const val KEY_PASS = "ubus_pass"
    }
}
