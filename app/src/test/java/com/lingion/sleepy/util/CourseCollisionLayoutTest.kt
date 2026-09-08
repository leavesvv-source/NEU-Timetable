package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseCollisionLayoutTest {
    @Test
    fun keepsNonOverlappingCoursesFullWidth() {
        val placements = CourseCollisionLayout.arrange(
            listOf(course(1, "A", 1, 1, 2), course(2, "B", 1, 3, 2))
        )
        assertEquals(listOf(1, 1), placements.map { it.laneCount })
        assertEquals(listOf(0, 0), placements.map { it.lane })
    }

    @Test
    fun splitsExactCollisionIntoTwoLanes() {
        val placements = CourseCollisionLayout.arrange(
            listOf(course(1, "本科课", 2, 3, 2), course(2, "研究生课", 2, 3, 2))
        )
        assertEquals(listOf(2, 2), placements.map { it.laneCount })
        assertEquals(setOf(0, 1), placements.map { it.lane }.toSet())
    }

    @Test
    fun handlesTransitivePartialOverlapWithoutCoveringCards() {
        val placements = CourseCollisionLayout.arrange(
            listOf(
                course(1, "A", 3, 1, 2),
                course(2, "B", 3, 2, 2),
                course(3, "C", 3, 3, 2)
            )
        )
        assertEquals(listOf(2, 2, 2), placements.map { it.laneCount })
        assertEquals(listOf(0, 1, 0), placements.map { it.lane })
    }

    @Test
    fun isolatesCollisionsByDay() {
        val placements = CourseCollisionLayout.arrange(
            listOf(course(1, "周一", 1, 1, 2), course(2, "周二", 2, 1, 2))
        )
        assertEquals(listOf(1, 1), placements.map { it.laneCount })
    }

    private fun course(id: Long, name: String, day: Int, start: Int, step: Int) = CourseEntity(
        id = id,
        groupId = name,
        tableId = 1,
        courseName = name,
        day = day,
        startNode = start,
        step = step,
        startWeek = 1,
        endWeek = 18,
        color = "#FF6750A4"
    )
}
