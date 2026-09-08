package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class TableMergePlan(
    val startDate: String,
    val maxWeek: Int,
    val nodesPerDay: Int,
    val courses: List<CourseEntity>
)

/** 把两张课表对齐到较早的第一周；节次时间仍由第一张课表提供。 */
object TableMergeUtils {
    fun plan(
        firstTable: TimeTableEntity,
        firstCourses: List<CourseEntity>,
        secondTable: TimeTableEntity,
        secondCourses: List<CourseEntity>
    ): TableMergePlan {
        require(firstTable.id != secondTable.id) { "请选择两张不同的课表" }
        val firstDate = parseStartDate(firstTable)
        val secondDate = parseStartDate(secondTable)
        val mergedDate = minOf(firstDate, secondDate)
        val firstShift = weekShift(mergedDate, firstDate)
        val secondShift = weekShift(mergedDate, secondDate)
        val groupIds = mutableMapOf<String, String>()

        fun shifted(table: TimeTableEntity, courses: List<CourseEntity>, shift: Int) =
            courses.map { course ->
                val originalGroup = course.groupId.ifBlank {
                    course.courseName.trim().replace(Regex("\\s+"), " ").lowercase()
                }
                val newGroup = groupIds.getOrPut("${table.id}:$originalGroup") {
                    UUID.randomUUID().toString()
                }
                course.copy(
                    id = 0,
                    groupId = newGroup,
                    tableId = 0,
                    startWeek = course.startWeek + shift,
                    endWeek = course.endWeek + shift,
                    type = shiftParity(course.type, shift)
                )
            }

        val courses = (shifted(firstTable, firstCourses, firstShift) +
            shifted(secondTable, secondCourses, secondShift))
            .distinctBy(::courseKey)
        val maxWeek = maxOf(
            firstTable.maxWeek + firstShift,
            secondTable.maxWeek + secondShift,
            courses.maxOfOrNull { it.endWeek } ?: 1
        )
        val nodesPerDay = maxOf(
            firstTable.nodesPerDay,
            secondTable.nodesPerDay,
            courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 1
        )
        return TableMergePlan(mergedDate.toString(), maxWeek, nodesPerDay, courses)
    }

    private fun parseStartDate(table: TimeTableEntity): LocalDate = runCatching {
        LocalDate.parse(table.startDate)
    }.getOrElse { throw IllegalArgumentException("课表“${table.name}”的开学日期不正确") }

    private fun weekShift(base: LocalDate, source: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(base, source)
        require(days % 7L == 0L) { "两张课表的第一周日期没有按整周对齐，暂时无法融合" }
        return (days / 7L).toInt()
    }

    private fun shiftParity(type: Int, shift: Int): Int = when {
        shift % 2 == 0 -> type
        type == 1 -> 2
        type == 2 -> 1
        else -> type
    }

    private fun courseKey(course: CourseEntity): List<Any> = listOf(
        course.courseName,
        course.teacher,
        course.room,
        course.note,
        course.source,
        course.day,
        course.startNode,
        course.step,
        course.startWeek,
        course.endWeek,
        course.type,
        course.ownTime,
        course.startTime,
        course.endTime
    )
}
