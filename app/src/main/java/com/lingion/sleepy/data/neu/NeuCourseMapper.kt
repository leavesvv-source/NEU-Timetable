package com.lingion.sleepy.data.neu

import com.lingion.sleepy.data.jw.JwCourse
import com.lingion.sleepy.util.TimeTableUtils

data class NeuImportPayload(
    val courses: List<JwCourse>,
    val startDate: String,
    val timeRows: List<TimeTableUtils.TimeSlotRow>,
    val termCode: String,
    val termName: String
)

object NeuCourseMapper {
    fun mapRows(rows: List<NeuCourseRow>): List<JwCourse> = buildList {
        for (row in rows) {
            val weeks = parseWeekNumbers(row.weeks)
            if (weeks.isEmpty()) continue
            for ((startWeek, endWeek, type) in compressWeeks(weeks)) {
                add(
                    JwCourse(
                        name = row.courseName,
                        room = row.location,
                        teacher = row.teacher,
                        day = row.dayOfWeek.coerceIn(1, 7),
                        startNode = row.beginSection.coerceAtLeast(1),
                        endNode = row.endSection.coerceAtLeast(row.beginSection),
                        startWeek = startWeek,
                        endWeek = endWeek,
                        type = type
                    )
                )
            }
        }
    }

    fun defaultTimeRows(rows: List<NeuCourseRow>): List<TimeTableUtils.TimeSlotRow> {
        val nanhuCount = rows.count { it.campus.contains("南湖") || it.location.contains("南湖") }
        val useNanhu = nanhuCount > rows.size / 2
        val times = if (useNanhu) NANHU_TIMES else HUNNAN_TIMES
        val maxNode = maxOf(12, rows.maxOfOrNull { it.endSection } ?: 12)
        return (1..maxNode).map { node ->
            val time = times[node]
            TimeTableUtils.TimeSlotRow(node, time?.first.orEmpty(), time?.second.orEmpty())
        }
    }

    internal fun parseWeekNumbers(raw: String): List<Int> {
        val normalized = raw
            .replace('，', '、')
            .replace(',', '、')
            .replace("（", "(")
            .replace("）", ")")
            .replace("周", "")
            .replace("(", "")
            .replace(")", "")
        val result = linkedSetOf<Int>()
        for (tokenRaw in normalized.split('、')) {
            val token = tokenRaw.trim()
            if (token.isBlank()) continue
            val oddOnly = token.endsWith("单")
            val evenOnly = token.endsWith("双")
            val numbers = Regex("\\d+").findAll(token).map { it.value.toInt() }.toList()
            if (numbers.isEmpty()) continue
            val candidates = if (numbers.size >= 2) numbers[0]..numbers[1] else numbers[0]..numbers[0]
            for (week in candidates) {
                if (oddOnly && week % 2 == 0) continue
                if (evenOnly && week % 2 != 0) continue
                result += week
            }
        }
        return result.sorted()
    }

    private fun compressWeeks(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.size >= 2 && (1 until weeks.size).all { weeks[it] - weeks[it - 1] == 2 }) {
            return listOf(Triple(weeks.first(), weeks.last(), if (weeks.first() % 2 == 1) 1 else 2))
        }
        val runs = mutableListOf<Triple<Int, Int, Int>>()
        var start = weeks.first()
        var previous = start
        for (week in weeks.drop(1)) {
            if (week == previous + 1) {
                previous = week
            } else {
                runs += Triple(start, previous, 0)
                start = week
                previous = week
            }
        }
        runs += Triple(start, previous, 0)
        return runs
    }

    private val NANHU_TIMES = mapOf(
        1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"),
        3 to ("10:00" to "10:45"), 4 to ("10:55" to "11:40"),
        5 to ("14:00" to "14:45"), 6 to ("14:55" to "15:40"),
        7 to ("16:00" to "16:45"), 8 to ("16:55" to "17:40"),
        9 to ("18:30" to "19:15"), 10 to ("19:25" to "20:10"),
        11 to ("20:20" to "21:05"), 12 to ("21:15" to "22:00")
    )

    private val HUNNAN_TIMES = NANHU_TIMES + mapOf(
        1 to ("08:30" to "09:15"), 2 to ("09:25" to "10:10"),
        3 to ("10:30" to "11:15"), 4 to ("11:25" to "12:10")
    )
}
