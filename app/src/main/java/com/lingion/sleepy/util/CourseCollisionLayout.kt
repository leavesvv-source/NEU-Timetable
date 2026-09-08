package com.lingion.sleepy.util

import com.lingion.sleepy.data.entity.CourseEntity

data class CourseCollisionPlacement(
    val course: CourseEntity,
    val lane: Int,
    val laneCount: Int
)

/** 为同一天发生节次重叠的课程分配并排栏位。 */
object CourseCollisionLayout {
    fun arrange(courses: List<CourseEntity>): List<CourseCollisionPlacement> =
        courses.groupBy { it.day }.values.flatMap(::arrangeDay)

    private fun arrangeDay(courses: List<CourseEntity>): List<CourseCollisionPlacement> {
        val sorted = courses.sortedWith(
            compareBy<CourseEntity>({ it.startNode }, { it.startNode + it.step }, { it.courseName }, { it.id })
        )
        val result = mutableListOf<CourseCollisionPlacement>()
        val cluster = mutableListOf<CourseEntity>()
        var clusterEnd = -1

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val laneEnds = mutableListOf<Int>()
            val assigned = cluster.map { course ->
                val start = course.startNode
                val end = course.startNode + course.step.coerceAtLeast(1) - 1
                val reusableLane = laneEnds.indexOfFirst { it < start }
                val lane = if (reusableLane >= 0) reusableLane else laneEnds.size.also { laneEnds += -1 }
                laneEnds[lane] = end
                course to lane
            }
            val laneCount = laneEnds.size.coerceAtLeast(1)
            result += assigned.map { (course, lane) -> CourseCollisionPlacement(course, lane, laneCount) }
            cluster.clear()
        }

        sorted.forEach { course ->
            val start = course.startNode
            val end = course.startNode + course.step.coerceAtLeast(1) - 1
            if (cluster.isNotEmpty() && start > clusterEnd) flushCluster()
            cluster += course
            clusterEnd = maxOf(clusterEnd, end)
        }
        flushCluster()
        return result
    }
}
