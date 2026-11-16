package ru.franh.modemmon.util

import androidx.compose.ui.graphics.Color

/** Метрики, по которым можно наводить антенну. */
enum class Metric(val key: String, val title: String, val unit: String) {
    SINR("sinr", "SINR", "дБ"),
    RSRP("rsrp", "RSRP", "дБм"),
    RSRQ("rsrq", "RSRQ", "дБ"),
    RSSI("rssi", "RSSI", "дБм");

    companion object {
        fun byKey(key: String): Metric = entries.firstOrNull { it.key == key } ?: SINR
    }
}

data class Quality(val label: String, val color: Color, val percent: Int)

object SignalEval {

    val GOOD = Color(0xFF00E5A0)
    val LIME = Color(0xFF7ED957)
    val AMBER = Color(0xFFFFC857)
    val RED = Color(0xFFFF5A5F)
    val MUTED = Color(0xFF8B9AAB)

    fun quality(metric: Metric, v: Double): Quality = when (metric) {
        Metric.RSRP -> when {
            v >= -80 -> Quality("Отличный", GOOD, 100)
            v >= -90 -> Quality("Хороший", LIME, 80)
            v >= -100 -> Quality("Средний", AMBER, 50)
            else -> Quality("Плохой", RED, 15)
        }

        Metric.SINR -> when {
            v >= 20 -> Quality("Идеальный", GOOD, 100)
            v >= 13 -> Quality("Хороший", LIME, 75)
            v >= 0 -> Quality("Шумный", AMBER, 40)
            else -> Quality("Критичный", RED, 5)
        }

        Metric.RSSI -> when {
            v >= -65 -> Quality("Сильный", GOOD, 100)
            v >= -75 -> Quality("Нормальный", LIME, 75)
            v >= -85 -> Quality("Слабый", AMBER, 45)
            else -> Quality("Очень слабый", RED, 10)
        }

        Metric.RSRQ -> when {
            v >= -10 -> Quality("Отличный", GOOD, 100)
            v >= -15 -> Quality("Хороший", LIME, 75)
            v >= -20 -> Quality("Средний", AMBER, 45)
            else -> Quality("Плохой", RED, 12)
        }
    }

    /**
     * Процент «здоровья» канала: кусочно-линейная интерполяция по опорным
     * точкам. Нужен для прогресс-бара и звука — реагирует на 1–2 дБ.
     */
    fun percent(metric: Metric, v: Double): Float {
        val curve: List<Pair<Double, Double>> = when (metric) {
            Metric.RSRP -> listOf(-125.0 to 0.0, -100.0 to 50.0, -90.0 to 80.0, -80.0 to 100.0)
            Metric.SINR -> listOf(-10.0 to 0.0, 0.0 to 40.0, 13.0 to 75.0, 20.0 to 100.0)
            Metric.RSRQ -> listOf(-20.0 to 0.0, -15.0 to 60.0, -10.0 to 100.0)
            Metric.RSSI -> listOf(-110.0 to 0.0, -85.0 to 45.0, -75.0 to 75.0, -65.0 to 100.0)
        }
        if (v <= curve.first().first) return 0f
        if (v >= curve.last().first) return 100f
        for (i in 0 until curve.size - 1) {
            val (x1, y1) = curve[i]
            val (x2, y2) = curve[i + 1]
            if (v in x1..x2) {
                val k = (v - x1) / (x2 - x1)
                return (y1 + k * (y2 - y1)).toFloat()
            }
        }
        return 0f
    }

    fun axisRange(metric: Metric): ClosedFloatingPointRange<Float> = when (metric) {
        Metric.RSRP -> -140f..-44f
        Metric.RSSI -> -120f..-20f
        Metric.RSRQ -> -25f..-3f
        Metric.SINR -> -20f..40f
    }

    /** «Лучше/хуже» — для всех четырёх метрик больше значит лучше. */
    fun isBetter(metric: Metric, a: Double, b: Double): Boolean = a > b

    fun tips(metric: Metric, v: Double, sinr: Double?, rsrp: Double?, hasNr: Boolean): List<String> {
        val out = mutableListOf<String>()
        if (sinr != null && sinr < 5) {
            out += "SINR низкий — соты мешают друг другу. Сместите антенну в сторону или поднимите выше."
        }
        if (rsrp != null && rsrp < -110) {
            out += "Очень слабый RSRP: антенна далеко от вышки либо смотрит не туда."
        }
        if (rsrp != null && rsrp > -85 && sinr != null && sinr < 10) {
            out += "Сигнал сильный, но грязный — типичный признак переотражений. Попробуйте изменить поляризацию."
        }
        if (hasNr && metric == Metric.SINR && v < 10) {
            out += "Для 5G NR держите SINR выше 13 дБ — иначе скорость падает до LTE."
        }
        if (out.isEmpty()) {
            out += "Показатели ровные. Крутите антенну небольшими шагами по 10–15° и следите за «Δ до пика»."
        }
        return out
    }
}
