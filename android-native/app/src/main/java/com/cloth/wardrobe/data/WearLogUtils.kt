package com.cloth.wardrobe.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object WearLogUtils {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayDateStr(): String = LocalDate.now().format(fmt)

    fun wornToday(logs: List<WearLogEntity>, clothId: String, date: String = todayDateStr()): Boolean =
        logs.any { it.date == date && JsonHelpers.jsonToStringList(it.clothIdsJson).contains(clothId) }

    fun todayWearLogId(logs: List<WearLogEntity>, clothId: String, date: String = todayDateStr()): String? =
        logs.firstOrNull { it.date == date && JsonHelpers.jsonToStringList(it.clothIdsJson).contains(clothId) }?.id

    fun logsForDate(logs: List<WearLogEntity>, date: String = todayDateStr()): List<WearLogEntity> =
        logs.filter { it.date == date }

    fun countWears(clothId: String, logs: List<WearLogEntity>, startMs: Long?, endMs: Long): Int {
        var n = 0
        for (log in logs) {
            if (!logInRange(log, startMs, endMs)) continue
            if (JsonHelpers.jsonToStringList(log.clothIdsJson).contains(clothId)) n++
        }
        return n
    }

    fun lastWearDate(clothId: String, logs: List<WearLogEntity>): String? {
        var last = ""
        for (log in logs) {
            if (!JsonHelpers.jsonToStringList(log.clothIdsJson).contains(clothId)) continue
            if (log.date > last) last = log.date
        }
        return last.takeIf { it.isNotEmpty() }
    }

    fun resolvePeriod(key: String): Triple<Long?, Long, String> {
        val end = System.currentTimeMillis()
        return when (key) {
            "30d" -> Triple(end - 30L * 86400000, end, "近30天")
            "90d" -> Triple(end - 90L * 86400000, end, "近90天")
            "365d" -> Triple(end - 365L * 86400000, end, "近一年")
            else -> Triple(null, end, "全部")
        }
    }

    private fun logInRange(log: WearLogEntity, startMs: Long?, endMs: Long): Boolean {
        val parts = log.date.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return false
        val t = LocalDate.of(parts[0], parts[1], parts[2])
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (startMs != null && t < startMs) return false
        return t <= endMs
    }

    fun formatWearDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "从未穿着"
        if (dateStr == todayDateStr()) return "今天"
        return dateStr
    }
}
