package ru.franh.modemmon.util

import ru.franh.modemmon.util.L10n

import androidx.compose.ui.graphics.Color

/** Метрики, по которым можно наводить антенну. */
enum class Metric(val key: String, val title: String, val unit: String) {
    SINR("sinr", "SINR", L10n.s("дБ", "dB")),
    RSRP("rsrp", "RSRP", L10n.s("дБм", "dBm")),
    RSRQ("rsrq", "RSRQ", L10n.s("дБ", "dB")),
    RSSI("rssi", "RSSI", L10n.s("дБм", "dBm"));

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
            v >= -80 -> Quality(L10n.s("Отличный", "Excellent"), GOOD, 100)
            v >= -90 -> Quality(L10n.s("Хороший", "Good"), LIME, 80)
            v >= -100 -> Quality(L10n.s("Средний", "Fair"), AMBER, 50)
            else -> Quality(L10n.s("Плохой", "Poor"), RED, 15)
        }

        Metric.SINR -> when {
            v >= 20 -> Quality(L10n.s("Идеальный", "Ideal"), GOOD, 100)
            v >= 13 -> Quality(L10n.s("Хороший", "Good"), LIME, 75)
            v >= 0 -> Quality(L10n.s("Шумный", "Noisy"), AMBER, 40)
            else -> Quality(L10n.s("Критичный", "Critical"), RED, 5)
        }

        Metric.RSSI -> when {
            v >= -65 -> Quality(L10n.s("Сильный", "Strong"), GOOD, 100)
            v >= -75 -> Quality(L10n.s("Нормальный", "Normal"), LIME, 75)
            v >= -85 -> Quality(L10n.s("Слабый", "Weak"), AMBER, 45)
            else -> Quality(L10n.s("Очень слабый", "Very weak"), RED, 10)
        }

        Metric.RSRQ -> when {
            v >= -10 -> Quality(L10n.s("Отличный", "Excellent"), GOOD, 100)
            v >= -15 -> Quality(L10n.s("Хороший", "Good"), LIME, 75)
            v >= -20 -> Quality(L10n.s("Средний", "Fair"), AMBER, 45)
            else -> Quality(L10n.s("Плохой", "Poor"), RED, 12)
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
            out += L10n.s("SINR низкий — соты мешают друг другу. Сместите антенну в сторону или поднимите выше.", "Low SINR — cells interfere with each other. Move the antenna sideways or raise it higher.")
        }
        if (rsrp != null && rsrp < -110) {
            out += L10n.s("Очень слабый RSRP: антенна далеко от вышки либо смотрит не туда.", "Very weak RSRP: the antenna is far from the tower or pointing the wrong way.")
        }
        if (rsrp != null && rsrp > -85 && sinr != null && sinr < 10) {
            out += L10n.s("Сигнал сильный, но грязный — типичный признак переотражений. Попробуйте изменить поляризацию.", "Strong but dirty signal — a typical sign of reflections. Try changing the polarization.")
        }
        if (hasNr && metric == Metric.SINR && v < 10) {
            out += L10n.s("Для 5G NR держите SINR выше 13 дБ — иначе скорость падает до LTE.", "For 5G NR keep SINR above 13 dB — otherwise speed drops to LTE levels.")
        }
        if (out.isEmpty()) {
            out += L10n.s("Показатели ровные. Крутите антенну небольшими шагами по 10–15° и следите за «Δ до пика».", "Readings are steady. Turn the antenna in small 10–15° steps and watch “Δ to peak”.")
        }
        return out
    }
}
