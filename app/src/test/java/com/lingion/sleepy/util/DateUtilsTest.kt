package com.lingion.sleepy.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {
    @Test fun beforeSemesterNeverProducesNegativeWeek() {
        assertEquals(1, DateUtils.currentWeek("2026-09-07", LocalDate.parse("2026-08-24")))
        assertEquals(DateUtils.SemesterStatus.BEFORE_START, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2026-08-24")))
    }

    @Test fun detectsAfterSemester() {
        assertEquals(DateUtils.SemesterStatus.AFTER_END, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2027-02-01")))
    }

    // ---- semesterStatus 边界 ----

    @Test fun semesterStartDayIsInRange() {
        assertEquals(DateUtils.SemesterStatus.IN_RANGE, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2026-09-07")))
    }

    @Test fun lastDayOfMaxWeekIsStillInRange() {
        // 东大周次按周日到周六；传入旧版周一开学日也会归一到前一日周日。
        assertEquals(DateUtils.SemesterStatus.IN_RANGE, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2027-01-23")))
    }

    @Test fun dayAfterMaxWeekIsAfterEnd() {
        assertEquals(DateUtils.SemesterStatus.AFTER_END, DateUtils.semesterStatus("2026-09-07", 20, LocalDate.parse("2027-01-24")))
    }

    @Test fun invalidStartDateFallsBackToInRange() {
        assertEquals(DateUtils.SemesterStatus.IN_RANGE, DateUtils.semesterStatus("garbage", 20, LocalDate.parse("2026-08-24")))
    }

    // ---- currentWeek 钳制 ----

    @Test fun afterEndWeekIsClampedForBrowsing() {
        // 2027-02-01 真实周数=21, 浏览仍钳在 20
        assertEquals(20, DateUtils.currentWeek("2026-09-07", LocalDate.parse("2027-02-01")).coerceAtMost(20))
    }

    @Test fun firstDayIsWeekOne() {
        assertEquals(1, DateUtils.currentWeek("2026-09-07", LocalDate.parse("2026-09-07")))
    }

    @Test fun invalidStartFallbackIsWeekOne() {
        assertEquals(1, DateUtils.currentWeek("bad-date", LocalDate.parse("2026-08-24")))
    }

    @Test fun sundayIsFirstDisplayDay() {
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), DateUtils.sundayFirst(1..7))
    }

    @Test fun weekDatesRunFromSundayToSaturday() {
        assertEquals(LocalDate.parse("2026-09-06"), DateUtils.dateOfWeek("2026-09-07", 1, 7))
        assertEquals(LocalDate.parse("2026-09-07"), DateUtils.dateOfWeek("2026-09-07", 1, 1))
        assertEquals(LocalDate.parse("2026-09-12"), DateUtils.dateOfWeek("2026-09-07", 1, 6))
    }
}
