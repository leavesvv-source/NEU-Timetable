package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.CourseSource
import com.lingion.sleepy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TableMergeUtilsTest {
    @Test
    fun keepsSourcesAndAlignsWeeksWithoutChangingInputs() {
        val undergraduate = table(1, "本科课表", "2026-09-06")
        val graduate = table(2, "研究生课表", "2026-09-13")
        val undergraduateCourse = course(10, 1, "高等数学", 1, 1, CourseSource.UNDERGRADUATE)
        val graduateCourse = course(20, 2, "机器学习", 1, 1, CourseSource.GRADUATE)

        val plan = TableMergeUtils.plan(
            undergraduate,
            listOf(undergraduateCourse),
            graduate,
            listOf(graduateCourse)
        )

        assertEquals("2026-09-06", plan.startDate)
        assertEquals(listOf(1, 2), plan.courses.map { it.startWeek })
        assertEquals(listOf(CourseSource.UNDERGRADUATE, CourseSource.GRADUATE), plan.courses.map { it.source })
        assertEquals(2, plan.courses.last().type)
        assertEquals(1, graduateCourse.startWeek)
        assertEquals(2, graduateCourse.tableId)
        assertNotEquals(undergraduateCourse.groupId, plan.courses.first().groupId)
    }

    private fun table(id: Long, name: String, startDate: String) = TimeTableEntity(
        id = id,
        name = name,
        startDate = startDate,
        maxWeek = 18,
        nodesPerDay = 12
    )

    private fun course(
        id: Long,
        tableId: Long,
        name: String,
        startWeek: Int,
        type: Int,
        source: String
    ) = CourseEntity(
        id = id,
        groupId = "group-$tableId",
        tableId = tableId,
        courseName = name,
        source = source,
        day = 1,
        startNode = 1,
        step = 2,
        startWeek = startWeek,
        endWeek = 16,
        type = type,
        color = "#FF6750A4"
    )
}
