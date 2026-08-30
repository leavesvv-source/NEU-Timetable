package com.lingion.sleepy.data.jw

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** 仅负责把东北大学教务解析结果原子写入本地数据库。 */
class JwImportViewModel(application: Application) : AndroidViewModel(application) {

    private fun toCourseEntities(
        courses: List<JwCourse>,
        tableId: Long
    ): List<CourseEntity> {
        val nameToGroup = mutableMapOf<String, String>()
        return courses.map { course ->
            CourseEntity(
                id = 0,
                groupId = nameToGroup.getOrPut(course.name) { UUID.randomUUID().toString() },
                tableId = tableId,
                courseName = course.name.ifBlank { "未命名课程" },
                teacher = course.teacher,
                room = course.room,
                day = course.day.coerceIn(1, 7),
                startNode = course.startNode.coerceAtLeast(1),
                step = (course.endNode - course.startNode + 1).coerceAtLeast(1),
                startWeek = course.startWeek.coerceAtLeast(1),
                endWeek = course.endWeek.coerceAtLeast(course.startWeek),
                type = course.type,
                color = "#FF6750A4"
            )
        }
    }

    suspend fun importAsNewTable(
        courses: List<JwCourse>,
        tableName: String,
        startDate: String? = null,
        timeJson: String = "",
        nodesPerDay: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        require(courses.isNotEmpty()) { "东北大学教务没有返回课程" }
        val db = AppDatabase.get(getApplication())
        db.withTransaction {
            val resolvedStartDate = startDate?.takeIf(String::isNotBlank) ?: currentSemesterStart()
            val maxNode = nodesPerDay.takeIf { it > 0 }
                ?: courses.maxOf { maxOf(it.startNode, it.endNode) }
            val tableId = db.timeTableDao().insert(
                TimeTableEntity(
                    id = 0,
                    name = tableName.ifBlank { "东北大学课表" },
                    startDate = resolvedStartDate,
                    timeJson = timeJson.ifBlank { TimeTableUtils.DEFAULT_TIME_JSON },
                    nodesPerDay = maxNode,
                    isDefault = true
                )
            )
            db.timeTableDao().setDefault(tableId)
            db.courseDao().insertAll(toCourseEntities(courses, tableId))
            tableId
        }
    }

    private fun currentSemesterStart(): String {
        val today = LocalDate.now()
        val year = if (today.monthValue in 8..12) today.year else today.year - 1
        val month = if (today.monthValue in 8..12) 9 else 2
        return LocalDate.of(year, month, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))
            .toString()
    }
}
