package com.example.util

import com.example.data.model.DateFilter
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val shortDateFormatter = SimpleDateFormat("dd/MM/yy", Locale.ENGLISH)
    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    fun roundMoney(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun multiplyMoney(qty: Double, rate: Double): Double {
        if (qty.isNaN() || rate.isNaN()) return 0.0
        return BigDecimal.valueOf(qty)
            .multiply(BigDecimal.valueOf(rate))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun formatRupee(amount: Double): String {
        val rounded = roundMoney(amount)
        val negative = rounded < 0
        val absVal = kotlin.math.abs(rounded)
        val whole = absVal.toLong()
        val frac = ((absVal - whole) * 100 + 0.5).toInt()
        val indianWhole = formatIndianInteger(whole)
        val prefix = if (negative) "-₹" else "₹"
        return if (frac == 0) {
            "$prefix$indianWhole"
        } else {
            "$prefix$indianWhole.${frac.toString().padStart(2, '0')}"
        }
    }

    private fun formatIndianInteger(number: Long): String {
        val s = number.toString()
        if (s.length <= 3) return s
        val lastThree = s.takeLast(3)
        val rest = s.dropLast(3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count == 2 && i != 0) {
                sb.append(',')
                count = 0
            }
        }
        return sb.reverse().toString() + "," + lastThree
    }

    fun formatQty(value: Double): String {
        val rounded = roundMoney(value)
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            DecimalFormat("#.##").format(rounded)
        }
    }

    fun formatDate(timestamp: Long): String = dateFormatter.format(Date(timestamp))
    fun formatShortDate(timestamp: Long): String = shortDateFormatter.format(Date(timestamp))
    fun formatDateTime(timestamp: Long): String = dateTimeFormatter.format(Date(timestamp))

    fun matchesSearch(query: String, vararg fields: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return true
        return fields.any { it.lowercase(Locale.ROOT).contains(q) }
    }

    fun hashPin(pin: String): String {
        if (pin.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("vyapardesk_salt_v1:$pin").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun dateRangeFor(
        filter: DateFilter,
        customStart: Long? = null,
        customEnd: Long? = null
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val endOfToday = cal.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return when (filter) {
            DateFilter.TODAY -> startOfToday to endOfToday
            DateFilter.YESTERDAY -> {
                val yStart = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    add(Calendar.DAY_OF_YEAR, -1)
                }.timeInMillis
                val yEnd = startOfToday - 1L
                yStart to yEnd
            }
            DateFilter.THIS_WEEK -> {
                val wStart = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    add(Calendar.DAY_OF_YEAR, -6)
                }.timeInMillis
                wStart to endOfToday
            }
            DateFilter.THIS_MONTH -> {
                val mStart = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
                mStart to endOfToday
            }
            DateFilter.LAST_MONTH -> {
                val lmStart = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }.timeInMillis
                val lmEnd = Calendar.getInstance().apply {
                    timeInMillis = startOfToday
                    set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis - 1L
                lmStart to lmEnd
            }
            DateFilter.ALL_TIME -> 0L to Long.MAX_VALUE
            DateFilter.CUSTOM -> {
                val s = customStart ?: startOfToday
                val e = customEnd ?: endOfToday
                minOf(s, e) to maxOf(s, e)
            }
        }
    }
}
