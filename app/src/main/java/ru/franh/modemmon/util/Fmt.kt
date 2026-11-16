package ru.franh.modemmon.util

import java.util.Locale

object Fmt {

    fun bytes(v: Long?): String {
        if (v == null || v < 0) return "—"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
        var value = v.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return if (i == 0) "${v} ${units[0]}"
        else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun rate(bps: Long?): String {
        if (bps == null || bps < 0) return "—"
        return when {
            bps >= 1_000_000_000 -> String.format(Locale.US, "%.2f Гбит/с", bps / 1e9)
            bps >= 1_000_000 -> String.format(Locale.US, "%.1f Мбит/с", bps / 1e6)
            bps >= 1_000 -> String.format(Locale.US, "%.0f кбит/с", bps / 1e3)
            else -> "$bps бит/с"
        }
    }

    fun duration(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return "—"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (d > 0) append("${d}д ")
            if (h > 0 || d > 0) append("${h}ч ")
            if (m > 0 || h > 0 || d > 0) append("${m}м ")
            append("${s}с")
        }
    }

    fun dbm(v: Double?): String =
        if (v == null) "—" else String.format(Locale.US, "%.0f", v)

    fun db(v: Double?): String =
        if (v == null) "—" else String.format(Locale.US, "%.1f", v)

    fun freq(mhz: Double?): String =
        if (mhz == null) "—" else String.format(Locale.US, "%.1f МГц", mhz)

    fun mhz(value: Double?): String =
        if (value == null) "—" else String.format(
            Locale.US,
            if (value % 1.0 == 0.0) "%.0f МГц" else "%.1f МГц",
            value
        )

    fun mhzText(value: String): String {
        val number = value.trim().replace(',', '.').toDoubleOrNull() ?: return "—"
        return mhz(number)
    }

    fun microseconds(value: Double?): String =
        if (value == null) "—" else String.format(
            Locale.US,
            if (value % 1.0 == 0.0) "%.0f мкс" else "%.1f мкс",
            value
        )

    fun withUnit(value: String, unit: String): String =
        if (value == "—") value else "$value $unit"

    fun band(raw: String): String {
        val b = raw.trim()
        if (b.isEmpty()) return "—"
        return when {
            b.startsWith("n", true) -> b.lowercase(Locale.US)
            b.startsWith("B", true) -> "B" + b.drop(1)
            else -> "B$b"
        }
    }

    /** EARFCN → частота DL для диапазонов LTE, поддерживаемых T99W175. */
    fun lteFreq(earfcn: Int?): Double? {
        val n = earfcn ?: return null
        return when (n) {
            in 0..599 -> 2110.0 + 0.1 * n                 // B1
            in 600..1199 -> 1930.0 + 0.1 * (n - 600)     // B2
            in 1200..1949 -> 1805.0 + 0.1 * (n - 1200)  // B3
            in 1950..2399 -> 2110.0 + 0.1 * (n - 1950)  // B4
            in 2400..2649 -> 869.0 + 0.1 * (n - 2400)   // B5
            in 2750..3449 -> 2620.0 + 0.1 * (n - 2750)  // B7
            in 3450..3799 -> 925.0 + 0.1 * (n - 3450)   // B8
            in 5010..5179 -> 729.0 + 0.1 * (n - 5010)   // B12
            in 5180..5279 -> 746.0 + 0.1 * (n - 5180)   // B13
            in 5280..5379 -> 758.0 + 0.1 * (n - 5280)   // B14
            in 5730..5849 -> 734.0 + 0.1 * (n - 5730)   // B17
            in 5850..5999 -> 860.0 + 0.1 * (n - 5850)   // B18
            in 6000..6149 -> 875.0 + 0.1 * (n - 6000)   // B19
            in 6150..6449 -> 791.0 + 0.1 * (n - 6150)   // B20
            in 8040..8689 -> 1930.0 + 0.1 * (n - 8040)  // B25
            in 8690..9039 -> 859.0 + 0.1 * (n - 8690)   // B26
            in 9210..9659 -> 758.0 + 0.1 * (n - 9210)   // B28
            in 9660..9769 -> 717.0 + 0.1 * (n - 9660)   // B29
            in 9770..9869 -> 2350.0 + 0.1 * (n - 9770)  // B30
            in 9920..10359 -> 1452.0 + 0.1 * (n - 9920) // B32
            in 36200..36349 -> 2010.0 + 0.1 * (n - 36200) // B34
            in 37750..38249 -> 2570.0 + 0.1 * (n - 37750) // B38
            in 38250..38649 -> 1880.0 + 0.1 * (n - 38250) // B39
            in 38650..39649 -> 2300.0 + 0.1 * (n - 38650) // B40
            in 39650..41589 -> 2496.0 + 0.1 * (n - 39650) // B41
            in 41590..43589 -> 3400.0 + 0.1 * (n - 41590) // B42
            in 46790..54539 -> 5150.0 + 0.1 * (n - 46790) // B46
            in 55240..56739 -> 3550.0 + 0.1 * (n - 55240) // B48
            in 66436..67335 -> 2110.0 + 0.1 * (n - 66436) // B66
            in 68586..68935 -> 617.0 + 0.1 * (n - 68586)  // B71
            else -> null
        }
    }

    /** NR-ARFCN → частота DL (FR1 и начало FR2). */
    fun nrFreq(arfcn: Int?): Double? {
        val n = arfcn ?: return null
        return when (n) {
            in 0..599999 -> n * 0.005
            in 600000..2016666 -> 3000.0 + (n - 600000) * 0.015
            else -> null
        }
    }
}
