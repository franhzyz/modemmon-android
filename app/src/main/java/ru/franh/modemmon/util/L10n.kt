package ru.franh.modemmon.util

/**
 * Язык интерфейса: «ru» — исходные строки, «en» — английский.
 * Устанавливается из настроек при старте приложения (MainActivity).
 */
object L10n {
    @Volatile var lang: String = "ru"

    /** Возвращает английский вариант, если выбран EN, иначе исходную строку. */
    fun s(ru: String, en: String): String = if (lang == "en") en else ru
}
